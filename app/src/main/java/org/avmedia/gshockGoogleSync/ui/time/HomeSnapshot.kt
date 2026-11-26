package org.avmedia.gshockGoogleSync.ui.time

import com.google.gson.Gson
import org.avmedia.gshockGoogleSync.watch.KeyValueStore
import org.avmedia.gshockGoogleSync.scratchpad.TimeSettingsStorage

/**
 * The last values the home screen showed for one watch, so the next open draws at once from them (marked as
 * updating) instead of waiting for the scratchpad, timer, home time, battery and temperature reads.
 */
data class HomeSnapshot(
    val timer: Int = 0,
    val homeTime: String = "",
    val batteryLevel: Int = 0,
    val temperature: Int = 0,
    val watchName: String = "",
    val timeZoneOption: String = TimeSettingsStorage.TimeZoneOption.SYSTEM.name,
    /** When it was saved, epoch millis; 0 in a snapshot of an older version. */
    val savedAtMs: Long = 0L,
)

/** Keeps one [HomeSnapshot] per watch address. Android-free. */
class HomeSnapshotStore(private val store: KeyValueStore, private val clock: () -> Long = System::currentTimeMillis) {
    private val gson = Gson()

    fun load(address: String): HomeSnapshot? =
        if (address.isBlank()) null
        else runCatching { gson.fromJson(store.get(KEY + address), HomeSnapshot::class.java) }.getOrNull()

    fun save(address: String, snapshot: HomeSnapshot) {
        if (address.isNotBlank()) store.put(KEY + address, gson.toJson(snapshot.copy(savedAtMs = clock())))
    }

    /** A re-paired watch has a new address: its last values go with it. */
    fun move(from: String, to: String) {
        if (from.isBlank() || to.isBlank() || from == to) return
        store.get(KEY + from)?.let { store.put(KEY + to, it); store.delete(KEY + from) }
    }

    companion object {
        private const val KEY = "home.snapshot."

        /** The first state of the screen: the stored values (none: the defaults), marked as updating. No watch request. */
        fun firstState(snapshot: HomeSnapshot?, base: TimeState = TimeState()): TimeState {
            val s = snapshot ?: return base.copy(updating = true)
            val option = TimeSettingsStorage.TimeZoneOption.entries.firstOrNull { it.name == s.timeZoneOption }
                ?: TimeSettingsStorage.TimeZoneOption.SYSTEM
            return base.copy(
                timer = s.timer, homeTime = s.homeTime, batteryLevel = s.batteryLevel, temperature = s.temperature,
                watchName = s.watchName, timeZoneOption = option, updating = true, fromStore = true, storedAtMs = s.savedAtMs,
            )
        }

        /** The watch did not answer: the stored values stay marked as stored ([mark]), never silently fresh. */
        fun afterFailedRead(state: TimeState): TimeState = state.copy(updating = false)

        /** The mark of the shown values ([org.avmedia.gshockGoogleSync.watch.StoredValue.mark]); null once the watch confirmed them. */
        fun mark(state: TimeState, nowMs: Long): String? =
            if (!state.fromStore && !state.updating) null
            else org.avmedia.gshockGoogleSync.watch.StoredValue(Unit, "", state.storedAtMs, confirmed = !state.fromStore).mark(nowMs, state.updating)

        fun of(state: TimeState) = HomeSnapshot(
            state.timer, state.homeTime, state.batteryLevel, state.temperature, state.watchName, state.timeZoneOption.name,
        )
    }
}
