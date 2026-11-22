package org.avmedia.gshockGoogleSync.ui.health.history

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import org.avmedia.gshockGoogleSync.history.HistoryDb
import org.avmedia.gshockGoogleSync.history.HistoryExport

/** Writes the whole history to a file in the share folder and hands it to the Android share sheet. Nothing is uploaded. */
internal object HistoryShare {
    /** The text of the export and its file name; null [json] means CSV. Pure apart from the database read. */
    fun render(context: Context, json: Boolean): Pair<String, String> {
        val dao = HistoryDb.get(context).dao()
        val days = dao.allDays()
        val hours = dao.allHours()
        val nights = dao.allNights()
        return if (json) "health-history.json" to HistoryExport.json(days, hours, nights)
        else "health-history.csv" to HistoryExport.csv(days, hours, nights)
    }

    /** Call off the main thread. Returns false when there is nothing to share yet. */
    fun share(context: Context, json: Boolean): Boolean {
        val dao = HistoryDb.get(context).dao()
        if (dao.allDays().isEmpty() && dao.allNights().isEmpty()) return false
        val (name, text) = render(context, json)
        val folder = File(context.cacheDir, "health-log-share").apply { mkdirs() }
        val file = File(folder, name).apply { writeText(text) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.healthlog", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = if (json) "application/json" else "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return true
    }
}
