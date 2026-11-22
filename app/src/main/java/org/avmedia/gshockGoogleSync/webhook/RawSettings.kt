package org.avmedia.gshockGoogleSync.webhook

/**
 * Raw reads of Casio setting request codes, kept so the bytes can be matched against what the owner
 * changes on the watch. Not implemented yet.
 */
object RawSettings {
    /** Not filled yet. */
    val CODES: List<Int> = emptyList()

    fun name(code: Int): String = "0x%02X".format(code)

    fun variable(code: Int): String = "watch.raw.${name(code)}"

    val shared = RawSettingsReader()
}

class RawSettingsReader(
    private val clock: () -> Long = System::currentTimeMillis,
    private val busy: () -> Boolean = { false },
    private val intervalMs: Long = 30 * 60 * 1000L,
) {
    /** Reads every code that is due, one after the other. Never throws. */
    suspend fun refresh(raw: RawRequester) {}

    /** Code name ("0x41") to hex ("41 01 02"), for the codes that were read. */
    fun values(): Map<String, String> = emptyMap()

    /** Time of the last successful read of any code, epoch millis; null when none. */
    fun lastReadMillis(): Long? = null
}
