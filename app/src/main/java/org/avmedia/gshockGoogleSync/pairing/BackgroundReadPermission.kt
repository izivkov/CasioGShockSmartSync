package org.avmedia.gshockGoogleSync.pairing

/**
 * A short window, per watch address, in which an always-connected watch may connect in the background.
 * A connection in the window reads the LifeLog and is released straight away. Held in memory.
 */
object BackgroundReadPermission {
    private val expiry = mutableMapOf<String, Long>()

    fun arm(address: String, windowMs: Long, now: Long = System.currentTimeMillis()) {
        synchronized(this) { expiry[address.uppercase()] = now + windowMs }
    }

    fun isArmed(address: String, now: Long = System.currentTimeMillis()): Boolean =
        synchronized(this) { (expiry[address.uppercase()] ?: 0L) > now }

    /** True once for a connection that starts inside the window; the window is then used up. */
    fun consume(address: String, now: Long = System.currentTimeMillis()): Boolean =
        synchronized(this) {
            val until = expiry.remove(address.uppercase()) ?: return false
            now < until
        }
}
