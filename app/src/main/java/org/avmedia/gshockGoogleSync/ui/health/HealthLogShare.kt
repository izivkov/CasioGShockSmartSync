package org.avmedia.gshockGoogleSync.ui.health

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import org.avmedia.gshockGoogleSync.R
import org.avmedia.gshockapi.log.AppLog
import timber.log.Timber
import java.io.File

/** Hands the App log to the Android share sheet as a zip (Settings > App log); it is the only log the app keeps. Nothing is uploaded. */
internal object HealthLogShare {
    fun dir(context: Context): File = File(context.filesDir, "health-log")

    /** Folders of the removed transfer exports (`lifelog-...`, `workout-...`); deleted once at start. */
    fun dropOldExports(context: Context) {
        dir(context).listFiles { f -> f.isDirectory && (f.name.startsWith("lifelog-") || f.name.startsWith("workout-")) }
            ?.forEach { it.deleteRecursively() }
    }

    fun share(context: Context) {
        try {
            val folder = File(context.cacheDir, "health-log-share").apply { mkdirs() }
            val zip = AppLog.zipTo(File(folder, "app-log.zip"))
            if (zip == null) {
                Toast.makeText(context, R.string.health_log_none, Toast.LENGTH_LONG).show()
                return
            }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.healthlog", zip)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "application/zip"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (t: Throwable) {
            Timber.w(t, "Sharing the App log failed")
            Toast.makeText(context, R.string.health_log_none, Toast.LENGTH_LONG).show()
        }
    }
}
