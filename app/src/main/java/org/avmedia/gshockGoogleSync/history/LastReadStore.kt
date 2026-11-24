package org.avmedia.gshockGoogleSync.history

import android.content.Context
import java.time.LocalDateTime
import org.avmedia.gshockGoogleSync.watch.AppStores
import org.avmedia.gshockGoogleSync.watch.CurrentWatch
import org.avmedia.gshockapi.model.StepCounterData

/**
 * The time and step goal of the last stored LifeLog read of each watch, for the stored day shown before a new read.
 * The values of older versions (SharedPreferences, one watch) become the legacy owner's once.
 */
object LastReadStore {
    private const val PREFS = "health_last_read"
    private const val KEY_AT = "at"
    private const val KEY_GOAL = "goal"
    private const val W_AT = "health.last_read.at"
    private const val W_GOAL = "health.last_read.goal"

    fun save(context: Context, data: StepCounterData) {
        runCatching {
            val watch = CurrentWatch.key()
            val store = AppStores.watchStore(context)
            data.timestamp?.let { store.put(watch, W_AT, it.toString()) }
            data.h5600?.stepGoal?.let { store.put(watch, W_GOAL, it.toString()) }
        }
    }

    fun load(context: Context): StoredHealth.LastRead? = runCatching {
        val watch = CurrentWatch.keyOrLast(context)
        val store = AppStores.watchStore(context)
        if (store.get(watch, W_AT) == null && store.get(watch, W_GOAL) == null) migrate(context, store, watch)
        StoredHealth.LastRead(
            at = store.get(watch, W_AT)?.value?.let { LocalDateTime.parse(it) },
            stepGoal = store.get(watch, W_GOAL)?.value?.toIntOrNull(),
        )
    }.getOrNull()

    /** The one-watch values of older versions go to the legacy owner, once. */
    private fun migrate(context: Context, store: org.avmedia.gshockGoogleSync.watch.WatchStore, watch: String) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!p.contains(KEY_AT) && !p.contains(KEY_GOAL)) return
        if (!store.isLegacyOwner(watch)) return
        p.getString(KEY_AT, null)?.let { store.put(watch, W_AT, it, 0) }
        if (p.contains(KEY_GOAL)) store.put(watch, W_GOAL, p.getInt(KEY_GOAL, 0).toString(), 0)
        p.edit().clear().apply()
    }
}
