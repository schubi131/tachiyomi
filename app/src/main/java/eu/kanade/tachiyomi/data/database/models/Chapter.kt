package eu.kanade.tachiyomi.data.database.models

import eu.kanade.tachiyomi.data.download.model.Download
import eu.kanade.tachiyomi.data.source.model.Page
import java.io.Serializable

interface Chapter : Serializable {

    var id: Long?

    var manga_id: Long?

    var url: String

    var name: String

    var read: Boolean

    var last_page_read: Int

    var date_fetch: Long

    var date_upload: Long

    var chapter_number: Float

    var source_order: Int

    // TODO not a db field
    var status: Int

    // TODO not a db field
    var pages: List<Page>?

    // TODO shouldnt be available here
    val isDownloaded: Boolean
        get() = status == Download.DOWNLOADED

    val isRecognizedNumber: Boolean
        get() = chapter_number >= 0f

    companion object {

        fun create(): Chapter {
            val chapter = ChapterImpl()
            chapter.chapter_number = -1f
            return chapter
        }
    }
}