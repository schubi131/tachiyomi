package eu.kanade.tachiyomi.ui.reader

import android.os.Bundle
import eu.kanade.tachiyomi.data.cache.ChapterCache
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.data.database.models.History
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.database.models.MangaSync
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.mangasync.MangaSyncManager
import eu.kanade.tachiyomi.data.mangasync.UpdateMangaSyncService
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.data.source.Source
import eu.kanade.tachiyomi.data.source.SourceManager
import eu.kanade.tachiyomi.data.source.model.Page
import eu.kanade.tachiyomi.data.source.online.OnlineSource
import eu.kanade.tachiyomi.ui.base.presenter.BasePresenter
import eu.kanade.tachiyomi.util.SharedData
import rx.Observable
import rx.Subscription
import rx.android.schedulers.AndroidSchedulers
import rx.schedulers.Schedulers
import timber.log.Timber
import java.io.File
import java.util.*
import javax.inject.Inject

class ReaderPresenter : BasePresenter<ReaderActivity>() {

    @Inject lateinit var prefs: PreferencesHelper
    @Inject lateinit var db: DatabaseHelper
    @Inject lateinit var downloadManager: DownloadManager
    @Inject lateinit var syncManager: MangaSyncManager
    @Inject lateinit var sourceManager: SourceManager
    @Inject lateinit var chapterCache: ChapterCache

    lateinit var manga: Manga
        private set

    lateinit var chapter: ReaderChapter
        private set

    lateinit var source: Source
        private set

    private var nextChapter: ReaderChapter? = null
    private var previousChapter: ReaderChapter? = null
    private var mangaSyncList: List<MangaSync>? = null

    private val loader by lazy { ChapterLoader(downloadManager, manga, source) }

    private var appenderSubscription: Subscription? = null

    private val PREPARE_READER = 1
    private val GET_PAGE_LIST = 2
    private val GET_ADJACENT_CHAPTERS = 3
    private val GET_MANGA_SYNC = 4

    private val MANGA_KEY = "manga_key"
    private val CHAPTER_KEY = "chapter_key"

    override fun onCreate(savedState: Bundle?) {
        super.onCreate(savedState)

        if (savedState == null) {
            val event = SharedData.get(ReaderEvent::class.java) ?: return
            manga = event.manga
            chapter = event.chapter.toModel()
        } else {
            manga = savedState.getSerializable(MANGA_KEY) as Manga
            chapter = savedState.getSerializable(CHAPTER_KEY) as ReaderChapter
        }

        source = sourceManager.get(manga.source)!!

        restartableLatestCache(PREPARE_READER,
                { Observable.just(manga) },
                { view, manga -> view.onMangaOpen(manga) })

        startableLatestCache(GET_ADJACENT_CHAPTERS,
                { getAdjacentChaptersObservable() },
                { view, pair -> view.onAdjacentChapters(pair.first, pair.second) })

        restartable(GET_MANGA_SYNC,
                { getMangaSyncObservable().subscribe() })

        restartableLatestCache(GET_PAGE_LIST,
                { loadChapterObservable(chapter) },
                { view, chapter -> view.onChapterReady(this.chapter) },
                { view, error -> view.onChapterError(error) })

        if (savedState == null) {
            start(PREPARE_READER)
            loadChapter(chapter)
            if (prefs.autoUpdateMangaSync()) {
                start(GET_MANGA_SYNC)
            }
        }
    }

    override fun onSave(state: Bundle) {
        chapter.requestedPage = chapter.last_page_read
        onChapterLeft()
        state.putSerializable(MANGA_KEY, manga)
        state.putSerializable(CHAPTER_KEY, chapter)
        super.onSave(state)
    }

    override fun onDestroy() {
        loader.cleanup()
        super.onDestroy()
    }

    private fun Chapter.toModel(): ReaderChapter {
        val model = ReaderChapter(this)
        return model
    }

    private fun loadChapterObservable(chapter: ReaderChapter): Observable<ReaderChapter> {
        loader.restart()
        return loader.loadChapter(chapter)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
    }

    private fun getAdjacentChaptersObservable(): Observable<Pair<Chapter, Chapter>> {
        val strategy = getAdjacentChaptersStrategy()
        return Observable.zip(strategy.first, strategy.second) { prev, next -> Pair(prev, next) }
                .doOnNext { pair ->
                    previousChapter = pair.first.toModel()
                    nextChapter = pair.second.toModel()
                }
                .observeOn(AndroidSchedulers.mainThread())
    }

    private fun getAdjacentChaptersStrategy() = when (manga.sorting) {
        Manga.SORTING_NUMBER -> Pair(
                db.getPreviousChapter(chapter).asRxObservable().take(1),
                db.getNextChapter(chapter).asRxObservable().take(1))
        Manga.SORTING_SOURCE -> Pair(
                db.getPreviousChapterBySource(chapter).asRxObservable().take(1),
                db.getNextChapterBySource(chapter).asRxObservable().take(1))
        else -> throw AssertionError("Unknown sorting method")
    }

    private fun getMangaSyncObservable(): Observable<List<MangaSync>> {
        return db.getMangasSync(manga).asRxObservable()
                .take(1)
                .doOnNext { mangaSyncList = it }
    }

    // Loads the given chapter
    private fun loadChapter(chapter: ReaderChapter, requestedPage: Int = 0) {
        if (appenderSubscription != null)
            remove(appenderSubscription)

        this.chapter = chapter

        // If the chapter is partially read, set the starting page to the last the user read
        chapter.requestedPage = if (!chapter.read && chapter.last_page_read != 0)
            chapter.last_page_read
        else
            requestedPage

        // Reset next and previous chapter. They have to be fetched again
        nextChapter = null
        previousChapter = null

        start(GET_PAGE_LIST)
        start(GET_ADJACENT_CHAPTERS)
    }

    fun setActiveChapter(chapter: ReaderChapter) {
        onChapterLeft()
        this.chapter = chapter
        nextChapter = null
        previousChapter = null
        start(GET_ADJACENT_CHAPTERS)
    }

    fun appendNextChapter() {
        if (nextChapter == null)
            return

        if (appenderSubscription != null)
            remove(appenderSubscription)

        nextChapter?.let {
            if (appenderSubscription != null)
                remove(appenderSubscription)

            appenderSubscription = loader.loadChapter(it).subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .compose(deliverLatestCache<ReaderChapter>())
                    .subscribe(split({ view, chapter ->
                        view.onAppendChapter(chapter)
                    }, { view, error ->
                        view.onChapterAppendError()
                    }))

            add(appenderSubscription)

        }
    }

    fun retryPage(page: Page?) {
        if (page != null) {
            page.status = Page.QUEUE
            if (page.imagePath != null) {
                val file = File(page.imagePath)
                chapterCache.removeFileFromCache(file.name)
            }
            loader.retryPage(page)
        }
    }

    // Called before loading another chapter or leaving the reader. It allows to do operations
    // over the chapter read like saving progress
    fun onChapterLeft() {
        val pages = chapter.pages ?: return

        // Cache current page list progress for online chapters to allow a faster reopen
        if (!chapter.isDownloaded) {
            source.let { if (it is OnlineSource) it.savePageList(chapter, pages) }
        }

        // Save current progress of the chapter. Mark as read if the chapter is finished
        if (chapter.read) {
            // Check if remove after read is selected by user
            if (prefs.removeAfterRead()) {
                if (prefs.removeAfterReadPrevious() ) {
                    if (previousChapter != null) {
                        deleteChapter(previousChapter!!, manga)
                    }
                } else {
                    deleteChapter(chapter, manga)
                }
            }
        }
        db.updateChapterProgress(chapter).asRxObservable().subscribe()
        // Update last read data
        db.updateHistoryLastRead(History.create(chapter)
                .apply { last_read = Date().time })
                .asRxObservable()
                .doOnError { Timber.e(it.message) }
                .subscribe()
    }

    /**
     * Delete selected chapter
     * @param chapter chapter that is selected
     * *
     * @param manga manga that belongs to chapter
     */
    fun deleteChapter(chapter: Chapter, manga: Manga) {
        val source = sourceManager.get(manga.source)!!
        downloadManager.deleteChapter(source, manga, chapter)
    }

    // If the current chapter has been read, we check with this one
    // If not, we check if the previous chapter has been read
    // We know the chapter we have to check, but we don't know yet if an update is required.
    // This boolean is used to return 0 if no update is required
    fun getMangaSyncChapterToUpdate(): Int {
        if (chapter.pages == null || mangaSyncList == null || mangaSyncList!!.isEmpty())
            return 0

        var lastChapterReadLocal = 0
        if (chapter.read)
            lastChapterReadLocal = Math.floor(chapter.chapter_number.toDouble()).toInt()
        else if (previousChapter != null && previousChapter!!.read)
            lastChapterReadLocal = Math.floor(previousChapter!!.chapter_number.toDouble()).toInt()
        var hasToUpdate = false

        for (mangaSync in mangaSyncList!!) {
            if (lastChapterReadLocal > mangaSync.last_chapter_read) {
                mangaSync.last_chapter_read = lastChapterReadLocal
                mangaSync.update = true
                hasToUpdate = true
            }
        }
        return if (hasToUpdate) lastChapterReadLocal else 0
    }

    fun updateMangaSyncLastChapterRead() {
        for (mangaSync in mangaSyncList ?: emptyList()) {
            val service = syncManager.getService(mangaSync.sync_id) ?: continue
            if (service.isLogged && mangaSync.update) {
                UpdateMangaSyncService.start(context, mangaSync)
            }
        }
    }

    fun loadNextChapter(): Boolean {
        nextChapter?.let {
            onChapterLeft()
            loadChapter(it, 0)
            return true
        }
        return false
    }

    fun loadPreviousChapter(): Boolean {
        previousChapter?.let {
            onChapterLeft()
            loadChapter(it, if (it.read) -1 else 0)
            return true
        }
        return false
    }

    fun hasNextChapter(): Boolean {
        return nextChapter != null
    }

    fun hasPreviousChapter(): Boolean {
        return previousChapter != null
    }

    fun updateMangaViewer(viewer: Int) {
        manga.viewer = viewer
        db.insertManga(manga).executeAsBlocking()
    }

    fun onPageChanged(page: Page) {
        val chapter = page.chapter
        chapter.last_page_read = page.pageNumber
        if (chapter.pages!!.last() === page) {
            chapter.read = true
        }
        if (!chapter.isDownloaded && page.status == Page.QUEUE) {
            loader.loadPriorizedPage(page)
        }
    }
}
