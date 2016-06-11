package eu.kanade.tachiyomi.data.updater

import android.app.Notification
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.AsyncTask
import android.support.v4.app.NotificationCompat
import eu.kanade.tachiyomi.Constants
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.network.GET
import eu.kanade.tachiyomi.data.network.NetworkHelper
import eu.kanade.tachiyomi.data.network.ProgressListener
import eu.kanade.tachiyomi.data.network.newCallWithProgress
import eu.kanade.tachiyomi.util.notificationManager
import eu.kanade.tachiyomi.util.saveTo
import timber.log.Timber
import uy.kohesive.injekt.injectLazy
import java.io.File

class UpdateDownloader(private val context: Context) :
        AsyncTask<String, Int, UpdateDownloader.DownloadResult>() {

    companion object {
        /**
         * Prompt user with apk install intent
         * @param context context
         * @param file file of apk that is installed
         */
        fun installAPK(context: Context, file: File) {
            // Prompt install interface
            val intent = Intent(Intent.ACTION_VIEW)
            intent.setDataAndType(Uri.fromFile(file), "application/vnd.android.package-archive")
            // Without this flag android returned a intent error!
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
        }
    }

    val network: NetworkHelper by injectLazy()

    /**
     * Default download dir
     */
    private val apkFile = File(context.externalCacheDir, "update.apk")


    /**
     * Notification builder
     */
    private val notificationBuilder = NotificationCompat.Builder(context)

    /**
     * Id of the notification
     */
    private val notificationId: Int
        get() = Constants.NOTIFICATION_UPDATER_ID


    /**
     * Class containing download result
     * @param url url of file
     * @param successful status of download
     */
    class DownloadResult(var url: String, var successful: Boolean)

    /**
     * Called before downloading
     */
    override fun onPreExecute() {
        // Create download notification
        with(notificationBuilder) {
            setContentTitle(context.getString(R.string.update_check_notification_file_download))
            setContentText(context.getString(R.string.update_check_notification_download_in_progress))
            setSmallIcon(android.R.drawable.stat_sys_download)
        }
    }

    override fun doInBackground(vararg params: String?): DownloadResult {
        // Initialize information array containing path and url to file.
        val result = DownloadResult(params[0]!!, false)

        // Progress of the download
        var savedProgress = 0

        val progressListener = object : ProgressListener {
            override fun update(bytesRead: Long, contentLength: Long, done: Boolean) {
                val progress = (100 * bytesRead / contentLength).toInt()
                if (progress > savedProgress) {
                    savedProgress = progress
                    publishProgress(progress)
                }
            }
        }

        try {
            // Make the request and download the file
            val response = network.client.newCallWithProgress(GET(result.url), progressListener).execute()

            if (response.isSuccessful) {
                response.body().source().saveTo(apkFile)
                // Set download successful
                result.successful = true
            } else {
                response.close()
            }
        } catch (e: Exception) {
            Timber.e(e, e.message)
        }

        return result
    }

    /**
     * Called when progress is updated
     * @param values values containing progress
     */
    override fun onProgressUpdate(vararg values: Int?) {
        // Notify notification manager to update notification
        values.getOrNull(0)?.let {
            notificationBuilder.setProgress(100, it, false)
            // Displays the progress bar on notification
            context.notificationManager.notify(notificationId, notificationBuilder.build())
        }
    }

    /**
     * Called when download done
     * @param result string containing download information
     */
    override fun onPostExecute(result: DownloadResult) {
        with(notificationBuilder) {
            if (result.successful) {
                setContentTitle(context.getString(R.string.app_name))
                setContentText(context.getString(R.string.update_check_notification_download_complete))
                addAction(R.drawable.ic_system_update_grey_24dp_img, context.getString(R.string.action_install),
                        getInstallOnReceivedIntent(InstallOnReceived.INSTALL_APK, apkFile.absolutePath))
                addAction(R.drawable.ic_clear_grey_24dp_img, context.getString(R.string.action_cancel),
                        getInstallOnReceivedIntent(InstallOnReceived.CANCEL_NOTIFICATION))
            } else {
                setContentText(context.getString(R.string.update_check_notification_download_error))
                addAction(R.drawable.ic_refresh_grey_24dp_img, context.getString(R.string.action_retry),
                        getInstallOnReceivedIntent(InstallOnReceived.RETRY_DOWNLOAD, result.url))
                addAction(R.drawable.ic_clear_grey_24dp_img, context.getString(R.string.action_cancel),
                        getInstallOnReceivedIntent(InstallOnReceived.CANCEL_NOTIFICATION))
            }
            setSmallIcon(android.R.drawable.stat_sys_download_done)
            setProgress(0, 0, false)
        }
        val notification = notificationBuilder.build()
        notification.flags = Notification.FLAG_NO_CLEAR
        context.notificationManager.notify(notificationId, notification)
    }

    /**
     * Returns broadcast intent
     * @param action action name of broadcast intent
     * @param path path of file | url of file
     * @return broadcast intent
     */
    fun getInstallOnReceivedIntent(action: String, path: String = ""): PendingIntent {
        val intent = Intent(context, InstallOnReceived::class.java).apply {
            this.action = action
            putExtra(InstallOnReceived.FILE_LOCATION, path)
        }
        return PendingIntent.getBroadcast(context, 0, intent, 0)
    }


    /**
     * BroadcastEvent used to install apk or retry download
     */
    class InstallOnReceived : BroadcastReceiver() {
        companion object {
            // Install apk action
            const val INSTALL_APK = "eu.kanade.INSTALL_APK"

            // Retry download action
            const val RETRY_DOWNLOAD = "eu.kanade.RETRY_DOWNLOAD"

            // Retry download action
            const val CANCEL_NOTIFICATION = "eu.kanade.CANCEL_NOTIFICATION"

            // Absolute path of file || URL of file
            const val FILE_LOCATION = "file_location"
        }

        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                // Install apk.
                INSTALL_APK -> UpdateDownloader.installAPK(context, File(intent.getStringExtra(FILE_LOCATION)))
                // Retry download.
                RETRY_DOWNLOAD -> UpdateDownloader(context).execute(intent.getStringExtra(FILE_LOCATION))

                CANCEL_NOTIFICATION -> context.notificationManager.cancel(Constants.NOTIFICATION_UPDATER_ID)
            }
        }

    }
}
