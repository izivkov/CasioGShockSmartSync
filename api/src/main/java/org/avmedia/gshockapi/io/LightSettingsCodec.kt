package org.avmedia.gshockapi.io

/**
 * The light fields of the basic settings (0x13): index 0 bit 2 auto light, set means off (the polarity of
 * [SettingsIO], unconfirmed for the DW-H5600 family); index 1 low nibble the light duration, 0 short, 1 long
 * (the two values [SettingsIO] knows; any other value is not understood and never written). The other bits of
 * both bytes and every other byte stay as they are.
 */
object LightSettingsCodec {
    const val CODE = 0x13
    private const val FLAGS_INDEX = 0
    private const val DURATION_INDEX = 1
    private const val AUTO_LIGHT_OFF = 0x04
    private const val DURATION_MASK = 0x0F

    /** The allowed durations, by their nibble value. */
    val DURATIONS: List<String> = listOf("short", "long")

    fun autoLight(frame: ByteArray): Boolean? {
        if (!Frame.hasIndex(frame, CODE, DURATION_INDEX)) return null
        return (Frame.u8(frame, FLAGS_INDEX) ?: return null) and AUTO_LIGHT_OFF == 0
    }

    fun withAutoLight(frame: ByteArray, on: Boolean): ByteArray? {
        if (autoLight(frame) == null) return null
        val flags = Frame.u8(frame, FLAGS_INDEX) ?: return null
        return Frame.withBytes(frame, mapOf(FLAGS_INDEX to Frame.bits(flags, AUTO_LIGHT_OFF, !on)))
    }

    /** "short" or "long"; null when the frame is not 0x13 or the nibble holds a value not known here. */
    fun duration(frame: ByteArray): String? {
        if (!Frame.hasIndex(frame, CODE, DURATION_INDEX)) return null
        val nibble = (Frame.u8(frame, DURATION_INDEX) ?: return null) and DURATION_MASK
        return DURATIONS.getOrNull(nibble)
    }

    fun withDuration(frame: ByteArray, duration: String): ByteArray? {
        val nibble = DURATIONS.indexOf(duration)
        if (nibble < 0 || !Frame.hasIndex(frame, CODE, DURATION_INDEX)) return null
        val byte = Frame.u8(frame, DURATION_INDEX) ?: return null
        return Frame.withBytes(frame, mapOf(DURATION_INDEX to ((byte and DURATION_MASK.inv()) or nibble)))
    }

    val AUTO_LIGHT_BYTES: Set<Int> = setOf(FLAGS_INDEX + 1)
    val DURATION_BYTES: Set<Int> = setOf(DURATION_INDEX + 1)
}
