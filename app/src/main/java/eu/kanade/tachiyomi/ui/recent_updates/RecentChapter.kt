package eu.kanade.tachiyomi.ui.recent_updates

import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.data.database.models.MangaChapter
import eu.kanade.tachiyomi.data.download.model.Download

class RecentChapter(mc: MangaChapter) : Chapter by mc.chapter {

    val manga = mc.manga

    private var _status2: Int = 0

    var status2: Int
        get() = download?.status ?: _status2
        set(value) { _status2 = value }

    var download: Download? = null

    val isDownloaded2: Boolean
        get() = status2 == Download.DOWNLOADED

}