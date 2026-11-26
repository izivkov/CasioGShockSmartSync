package org.avmedia.gshockGoogleSync.ui.health.history

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import org.avmedia.gshockGoogleSync.history.HistoryDb
import org.avmedia.gshockGoogleSync.history.WorkoutExport

/** Writes all workouts to a file in the share folder and hands it to the Android share sheet. Nothing is uploaded. */
internal object WorkoutShare {
    /** Call off the main thread. Returns false when there is nothing to share yet. */
    fun share(context: Context, json: Boolean): Boolean {
        val dao = HistoryDb.get(context).dao()
        val workouts = dao.allWorkouts()
        if (workouts.isEmpty()) return false
        val laps = dao.allLaps()
        val samples = dao.allSamples()
        val name = if (json) "workouts.json" else "workouts.csv"
        val text = if (json) WorkoutExport.json(workouts, laps, samples) else WorkoutExport.csv(workouts, laps, samples)
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
