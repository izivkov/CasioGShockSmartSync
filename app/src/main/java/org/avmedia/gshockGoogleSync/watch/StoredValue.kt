package org.avmedia.gshockGoogleSync.watch

/**
 * THE helper for a value kept for one watch (owner rule 2026-11-24, "apply that globally"): the value, the watch it
 * belongs to (a [WatchKeys] key), when it was stored and whether the watch confirmed it on this link. Screens, cards and
 * Home Assistant take a stored value only through [forWatch], so another watch's value never reaches them, and show
 * [mark] next to it until the watch confirmed it. A second implementation of this is a defect.
 */
data class StoredValue<T>(
    val value: T,
    val watch: String,
    /** When the value was stored or confirmed, epoch millis; 0 = not known (a value kept by an older version). */
    val atMs: Long,
    /** True once the watch gave this value on the current link. */
    val confirmed: Boolean = false,
) {
    /** This value when it belongs to [key]; null for another watch's value or no watch. */
    fun forWatch(key: String): StoredValue<T>? = takeIf { key.isNotBlank() && key == watch }

    /** The watch gave the value on this link. */
    fun confirm(nowMs: Long): StoredValue<T> = copy(atMs = nowMs, confirmed = true)

    /** Null once confirmed; "updating" while the watch is asked; else "stored" with its age. Never silently fresh. */
    fun mark(nowMs: Long, reading: Boolean): String? = when {
        confirmed -> null
        reading -> UPDATING
        else -> storedText(atMs, nowMs)
    }

    companion object {
        const val UPDATING = "updating"

        /** The value of [stored] when it is [key]'s own, else null. */
        fun <T> valueFor(stored: StoredValue<T>?, key: String): T? = stored?.forWatch(key)?.value

        /** "stored, 2 h ago"; "stored" when the time is not known. */
        fun storedText(atMs: Long, nowMs: Long): String = if (atMs <= 0) "stored" else "stored, " + age(nowMs - atMs)

        /** "just now", "5 min ago", "2 h ago", "3 days ago". */
        fun age(ms: Long): String {
            val min = ms.coerceAtLeast(0) / 60_000
            return when {
                min < 1 -> "just now"
                min < 60 -> "$min min ago"
                min < 48 * 60 -> "${min / 60} h ago"
                else -> "${min / (24 * 60)} days ago"
            }
        }
    }
}
