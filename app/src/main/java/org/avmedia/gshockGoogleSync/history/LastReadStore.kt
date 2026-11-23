package org.avmedia.gshockGoogleSync.history

import android.content.Context
import java.time.LocalDateTime
import org.avmedia.gshockapi.model.StepCounterData

/** The time and step goal of the last stored LifeLog read, for the stored day shown before a new read. */
object LastReadStore {
    private const val PREFS = "health_last_read"
    private const val KEY_AT = "at"
    private const val KEY_GOAL = "goal"

    fun save(context: Context, data: StepCounterData) {
        runCatching {
            val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            data.timestamp?.let { p.putString(KEY_AT, it.toString()) }
            data.h5600?.stepGoal?.let { p.putInt(KEY_GOAL, it) }
            p.apply()
        }
    }

    fun load(context: Context): StoredHealth.LastRead? = runCatching {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        StoredHealth.LastRead(
            at = p.getString(KEY_AT, null)?.let { LocalDateTime.parse(it) },
            stepGoal = if (p.contains(KEY_GOAL)) p.getInt(KEY_GOAL, 0) else null,
        )
    }.getOrNull()
}
