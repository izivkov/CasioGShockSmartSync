package org.avmedia.gshockGoogleSync.webhook

import java.time.OffsetDateTime

/** Whether the Home Assistant push socket is up right now and since when; read by the `ha_push_connected` sensor. */
object HaPushState {
    @Volatile var connected: Boolean = false
        private set
    @Volatile var changedAt: String = ""
        private set

    fun set(on: Boolean, now: OffsetDateTime = OffsetDateTime.now()) {
        if (on == connected && changedAt.isNotEmpty()) return
        connected = on
        changedAt = now.withNano(0).toString()
    }
}
