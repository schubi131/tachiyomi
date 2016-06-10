package eu.kanade.tachiyomi

import eu.kanade.tachiyomi.data.cache.ChapterCache
import eu.kanade.tachiyomi.data.cache.CoverCache
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.mangasync.MangaSyncManager
import eu.kanade.tachiyomi.data.network.NetworkHelper
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.data.source.SourceManager
import uy.kohesive.injekt.api.InjektModule
import uy.kohesive.injekt.api.InjektRegistrar
import uy.kohesive.injekt.api.addSingletonFactory

class AppModule(val app: App) : InjektModule {

    override fun InjektRegistrar.registerInjectables() {

            addSingletonFactory { PreferencesHelper(app) }

            addSingletonFactory { DatabaseHelper(app) }

            addSingletonFactory { ChapterCache(app) }

            addSingletonFactory { CoverCache(app) }

            addSingletonFactory { NetworkHelper(app) }

            addSingletonFactory { SourceManager(app) }

            addSingletonFactory { DownloadManager(app) }

            addSingletonFactory { MangaSyncManager(app) }

    }

}