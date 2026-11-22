package org.avmedia.gshockGoogleSync.ui.health

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import org.avmedia.gshockGoogleSync.R
import org.avmedia.gshockapi.io.H5600RawDump
import timber.log.Timber
import java.io.File

/** Hands the raw bytes of the last LifeLog transfer to the Android share sheet, as a zip. Nothing is uploaded. */
internal object HealthLogShare {
    fun dir(context: Context): File = File(context.filesDir, "health-log")

    fun share(context: Context) {
        try {
            val folder = File(context.cacheDir, "health-log-share").apply { mkdirs() }
            val zip = H5600RawDump(dir(context)).zipLatest(File(folder, "lifelog.zip"))
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
            Timber.w(t, "Sharing the health log failed")
            Toast.makeText(context, R.string.health_log_none, Toast.LENGTH_LONG).show()
        }
    }
}
