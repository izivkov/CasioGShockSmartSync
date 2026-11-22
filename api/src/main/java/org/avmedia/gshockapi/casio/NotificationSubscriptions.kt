package org.avmedia.gshockapi.casio

import java.util.UUID

/**
 * Which characteristics have been subscribed on the CURRENT link, and the first-90-seconds notification log.
 * Notifications are enabled per link: after a drop and reconnect the descriptor writes must be made again, so
 * [beginDiscovery] runs at every service discovery (the old code kept the set for the life of the manager, so the
 * second and later links never subscribed and the watch's `49 00` was never seen).
 */
object NotificationSubscriptions {
    const val LOG_WINDOW_MS = 90_000L

    private val subscribed = mutableSetOf<UUID>()

    @Volatile private var linkStartedAt = 0L

    /** A new service discovery: forget the old subscriptions and start the logging window. */
    @Synchronized
    fun beginDiscovery() {
        linkStartedAt = ConnectionKeepAlive.clock()
    }

    /** True when [uuid] still has to be subscribed on this link. */
    @Synchronized
    fun claim(uuid: UUID): Boolean = subscribed.add(uuid)

    @Synchronized
    fun clear() = subscribed.clear()

    /** Logs the characteristic and the first two bytes of a notification during the first 90 s of the link. */
    fun logNotification(characteristic: UUID, hex: String?) {
        val age = ConnectionKeepAlive.clock() - linkStartedAt
        if (linkStartedAt == 0L || age < 0 || age > LOG_WINDOW_MS) return
        val bytes = hex.orEmpty().removePrefix("0x").split(' ').filter { it.isNotBlank() }.map { it.removePrefix("0x") }
        ConnectionKeepAlive.log(
            "rx +${age / 1000}s ${characteristic.toString().substring(4, 8)}: ${bytes.take(2).joinToString(" ").ifEmpty { "(empty)" }}",
        )
    }
}
