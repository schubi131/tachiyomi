package eu.kanade.tachiyomi.ui.setting

import android.content.Intent
import android.os.Bundle
import android.support.customtabs.CustomTabsIntent
import android.support.v7.preference.PreferenceCategory
import android.view.View
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.mangasync.MangaSyncManager
import eu.kanade.tachiyomi.data.mangasync.MangaSyncService
import eu.kanade.tachiyomi.data.mangasync.anilist.AnilistApi
import eu.kanade.tachiyomi.util.getResourceColor
import eu.kanade.tachiyomi.widget.preference.LoginPreference
import eu.kanade.tachiyomi.widget.preference.MangaSyncLoginDialog

class SettingsSyncFragment : SettingsNestedFragment() {

    companion object {
        const val SYNC_CHANGE_REQUEST = 121

        fun newInstance(resourcePreference: Int, resourceTitle: Int): SettingsNestedFragment {
            val fragment = SettingsSyncFragment()
            fragment.setArgs(resourcePreference, resourceTitle)
            return fragment
        }
    }

    val syncCategory by lazy { findPreference("pref_category_manga_sync_accounts") as PreferenceCategory }

    private val syncManager: MangaSyncManager
        get() = settingsActivity.syncManager

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        registerService(syncManager.myAnimeList)

        registerService(syncManager.aniList) {
            val intent = CustomTabsIntent.Builder()
                    .setToolbarColor(activity.theme.getResourceColor(R.attr.colorPrimary))
                    .build()
            intent.intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
            intent.launchUrl(activity, AnilistApi.authUrl())
        }
    }

    private fun <T : MangaSyncService> registerService(
            service: T,
            onPreferenceClick: (T) -> Unit = defaultOnPreferenceClick) {

        LoginPreference(preferenceManager.context).apply {
            key = preferences.keys.syncUsername(service.id)
            title = service.name

            setOnPreferenceClickListener {
                onPreferenceClick(service)
                true
            }

            syncCategory.addPreference(this)
        }
    }

    private val defaultOnPreferenceClick: (MangaSyncService) -> Unit
        get() = {
            val fragment = MangaSyncLoginDialog.newInstance(it)
            fragment.setTargetFragment(this, SYNC_CHANGE_REQUEST)
            fragment.show(fragmentManagerCompat, null)
        }

    override fun onResume() {
        super.onResume()
        // Manually refresh anilist holder
        updatePreference(syncManager.aniList.id)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == SYNC_CHANGE_REQUEST) {
            updatePreference(resultCode)
        }
    }

    private fun updatePreference(id: Int) {
        val pref = findPreference(preferences.keys.syncUsername(id)) as? LoginPreference
        pref?.notifyChanged()
    }

}
