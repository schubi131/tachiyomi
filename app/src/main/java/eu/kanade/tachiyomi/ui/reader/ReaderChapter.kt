package eu.kanade.tachiyomi.ui.reader

import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.data.download.model.Download
import eu.kanade.tachiyomi.data.source.model.Page

class ReaderChapter(c: Chapter) : Chapter by c {

    var pages: List<Page>? = null

    var status: Int = 0

    val isDownloaded: Boolean
        get() = status == Download.DOWNLOADED
}