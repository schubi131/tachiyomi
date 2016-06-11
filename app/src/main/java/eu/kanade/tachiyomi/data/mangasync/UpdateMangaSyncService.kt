package eu.kanade.tachiyomi.data.mangasync

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.MangaSync
import rx.Observable
import rx.android.schedulers.AndroidSchedulers
import rx.schedulers.Schedulers
import rx.subscriptions.CompositeSubscription
import uy.kohesive.injekt.injectLazy

class UpdateMangaSyncService : Service() {

    val syncManager: MangaSyncManager by injectLazy()
    val db: DatabaseHelper by injectLazy()

    private lateinit var subscriptions: CompositeSubscription

    override fun onCreate() {
        super.onCreate()
        subscriptions = CompositeSubscription()
    }

    override fun onDestroy() {
        subscriptions.unsubscribe()
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        val manga = intent.getSerializableExtra(EXTRA_MANGASYNC)
        if (manga != null) {
            updateLastChapterRead(manga as MangaSync, startId)
            return Service.START_REDELIVER_INTENT
        } else {
            stopSelf(startId)
            return Service.START_NOT_STICKY
        }
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    private fun updateLastChapterRead(mangaSync: MangaSync, startId: Int) {
        val sync = syncManager.getService(mangaSync.sync_id)
        if (sync == null) {
            stopSelf(startId)
            return
        }

        subscriptions.add(Observable.defer { sync.update(mangaSync) }
                .flatMap { db.insertMangaSync(mangaSync).asRxObservable() }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ stopSelf(startId) },
                        { stopSelf(startId) }))
    }

    companion object {

        private val EXTRA_MANGASYNC = "extra_mangasync"

        @JvmStatic
        fun start(context: Context, mangaSync: MangaSync) {
            val intent = Intent(context, UpdateMangaSyncService::class.java)
            intent.putExtra(EXTRA_MANGASYNC, mangaSync)
            context.startService(intent)
        }
    }

}
