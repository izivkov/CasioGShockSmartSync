package org.avmedia.gshockapi

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicInteger

/** Ids and times of the notifications sent to the watch: every frame gets its own non-zero id. */
object NotificationIds {
    // Seeded from the clock so ids after an app restart do not repeat the last ones (seconds since 2026, 31 bits).
    private val last = AtomicInteger(((System.currentTimeMillis() / 1000 - 1_767_225_600L).coerceAtLeast(1) and 0x3FFF_FFFF).toInt())

    fun next(): Int {
        while (true) {
            val cur = last.get()
            val n = if (cur >= 0x7FFF_FFFE) 1 else cur + 1
            if (last.compareAndSet(cur, n)) return n
        }
    }

    /** [n] with an id of its own when it has none. */
    fun assign(n: AppNotification): AppNotification = if (n.id != 0) n else n.copy(id = next())

    private val TIME = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")

    /** The 15-character frame time, e.g. 20261123T183005 (zero padded). */
    fun timestamp(t: LocalDateTime): String = t.format(TIME)
}
