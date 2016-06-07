package eu.kanade.tachiyomi.ui.manga.chapter

import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.data.download.model.Download
import eu.kanade.tachiyomi.data.source.model.Page

class ChapterModel(c: Chapter) : Chapter by c {

    private var _status2: Int = 0

    var status2: Int
        get() = download?.status ?: _status2
        set(value) { _status2 = value }

    var download: Download? = null

    var pages2: List<Page>? = null

    val isDownloaded2: Boolean
        get() = status2 == Download.DOWNLOADED

}