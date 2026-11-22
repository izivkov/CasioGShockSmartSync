package org.avmedia.gshockapi.io

/** Frame helpers shared by the setting codecs: frame byte = value index + 1 (byte 0 is the request code). */
internal object Frame {
    fun u8(
        frame: ByteArray,
        index: Int,
    ): Int? = frame.getOrNull(index + 1)?.let { it.toInt() and 0xFF }

    fun hasIndex(
        frame: ByteArray,
        code: Int,
        index: Int,
    ): Boolean = frame.isNotEmpty() && frame[0] == code.toByte() && frame.size > index + 1

    fun time(
        frame: ByteArray,
        hourIndex: Int,
    ): Pair<Int, Int>? {
        val hour = u8(frame, hourIndex) ?: return null
        val minute = u8(frame, hourIndex + 1) ?: return null
        return if (hour < 24 && minute < 60) hour to minute else null
    }

    fun withBytes(
        frame: ByteArray,
        changes: Map<Int, Int>,
    ): ByteArray = frame.copyOf().also { out -> changes.forEach { (index, value) -> out[index + 1] = value.toByte() } }

    fun bits(
        value: Int,
        mask: Int,
        on: Boolean,
    ): Int = if (on) value or mask else value and mask.inv()
}

/** Heart-rate measurement (0x41). Index 0 bits 0-1: 0 off, 1 during activities, 2 always. */
object HeartRateSettingCodec {
    const val CODE = 0x41
    private const val MODE_MASK = 0x03
    private const val MIN_INDEX = 3

    data class Setting(val mode: Int, val zone: Int, val lowBpm: Int, val highBpm: Int)

    fun parse(frame: ByteArray): Setting? {
        if (!Frame.hasIndex(frame, CODE, MIN_INDEX)) return null
        val flags = Frame.u8(frame, 0) ?: return null
        val mode = flags and MODE_MASK
        if (mode > 2) return null
        return Setting(mode, Frame.u8(frame, 1) ?: return null, Frame.u8(frame, 2) ?: return null, Frame.u8(frame, 3) ?: return null)
    }

    fun withMode(
        frame: ByteArray,
        mode: Int,
    ): ByteArray? {
        if (parse(frame) == null || mode !in 0..2) return null
        val flags = Frame.u8(frame, 0) ?: return null
        return Frame.withBytes(frame, mapOf(0 to ((flags and MODE_MASK.inv()) or mode)))
    }

    val MODE_BYTES: Set<Int> = setOf(1)
}

/** Physical settings (0x4F). Index 1, 2: preferred bedtime; 3, 4: preferred wake-up (hour, minute). */
object PhysicalSettingsCodec {
    const val CODE = 0x4F
    private const val BED_INDEX = 1
    private const val WAKE_INDEX = 3

    data class Sleep(val bedtime: Pair<Int, Int>, val wakeUp: Pair<Int, Int>)

    fun parse(frame: ByteArray): Sleep? {
        if (!Frame.hasIndex(frame, CODE, WAKE_INDEX + 1)) return null
        return Sleep(Frame.time(frame, BED_INDEX) ?: return null, Frame.time(frame, WAKE_INDEX) ?: return null)
    }

    fun withBedtime(
        frame: ByteArray,
        hour: Int,
        minute: Int,
    ): ByteArray? = withTime(frame, BED_INDEX, hour, minute)

    fun withWakeUp(
        frame: ByteArray,
        hour: Int,
        minute: Int,
    ): ByteArray? = withTime(frame, WAKE_INDEX, hour, minute)

    private fun withTime(
        frame: ByteArray,
        index: Int,
        hour: Int,
        minute: Int,
    ): ByteArray? {
        if (parse(frame) == null || hour !in 0..23 || minute !in 0..59) return null
        return Frame.withBytes(frame, mapOf(index to hour, index + 1 to minute))
    }

    val BEDTIME_BYTES: Set<Int> = setOf(BED_INDEX + 1, BED_INDEX + 2)
    val WAKE_BYTES: Set<Int> = setOf(WAKE_INDEX + 1, WAKE_INDEX + 2)
}

/**
 * Fields of the basic settings (0x13) that the health settings screen uses:
 * index 11 bit 0 length unit (which value is miles is likely 1, not confirmed), bit 1 step reminder;
 * index 12, 13 night window start hour and minute, index 14, 15 end hour and minute.
 */
object BasicHealthFieldsCodec {
    const val CODE = 0x13
    private const val FLAGS_INDEX = 11
    private const val NIGHT_START_INDEX = 12
    private const val NIGHT_END_INDEX = 14
    private const val UNIT_BIT = 0x01
    private const val REMINDER_BIT = 0x02

    data class Fields(
        val unitBit: Boolean,
        val stepReminder: Boolean,
        val nightStart: Pair<Int, Int>,
        val nightEnd: Pair<Int, Int>,
    )

    fun parse(frame: ByteArray): Fields? {
        if (!Frame.hasIndex(frame, CODE, NIGHT_END_INDEX + 1)) return null
        val flags = Frame.u8(frame, FLAGS_INDEX) ?: return null
        return Fields(
            unitBit = flags and UNIT_BIT != 0,
            stepReminder = flags and REMINDER_BIT != 0,
            nightStart = Frame.time(frame, NIGHT_START_INDEX) ?: return null,
            nightEnd = Frame.time(frame, NIGHT_END_INDEX) ?: return null,
        )
    }

    fun withStepReminder(
        frame: ByteArray,
        on: Boolean,
    ): ByteArray? = withFlag(frame, REMINDER_BIT, on)

    fun withUnitBit(
        frame: ByteArray,
        on: Boolean,
    ): ByteArray? = withFlag(frame, UNIT_BIT, on)

    private fun withFlag(
        frame: ByteArray,
        mask: Int,
        on: Boolean,
    ): ByteArray? {
        if (parse(frame) == null) return null
        val flags = Frame.u8(frame, FLAGS_INDEX) ?: return null
        return Frame.withBytes(frame, mapOf(FLAGS_INDEX to Frame.bits(flags, mask, on)))
    }

    fun withNightWindow(
        frame: ByteArray,
        start: Pair<Int, Int>,
        end: Pair<Int, Int>,
    ): ByteArray? {
        if (parse(frame) == null) return null
        if (listOf(start, end).any { it.first !in 0..23 || it.second !in 0..59 }) return null
        return Frame.withBytes(
            frame,
            mapOf(
                NIGHT_START_INDEX to start.first, NIGHT_START_INDEX + 1 to start.second,
                NIGHT_END_INDEX to end.first, NIGHT_END_INDEX + 1 to end.second,
            ),
        )
    }

    val FLAGS_BYTES: Set<Int> = setOf(FLAGS_INDEX + 1)
    val NIGHT_BYTES: Set<Int> = (NIGHT_START_INDEX..NIGHT_END_INDEX + 1).map { it + 1 }.toSet()
}

/** Step goal (0x43): index 0 to 2 are the daily steps, three bytes, low byte first. Other bytes are never touched. */
object StepGoalCodec {
    const val CODE = 0x43
    const val MIN_STEPS = 100
    const val MAX_STEPS = 200_000

    fun parse(frame: ByteArray): Int? {
        if (!Frame.hasIndex(frame, CODE, 2)) return null
        val steps = (0..2).sumOf { (Frame.u8(frame, it) ?: return null) shl (8 * it) }
        return steps.takeIf { it in MIN_STEPS..MAX_STEPS }
    }

    fun withSteps(
        frame: ByteArray,
        steps: Int,
    ): ByteArray? {
        if (parse(frame) == null || steps !in MIN_STEPS..MAX_STEPS) return null
        return Frame.withBytes(frame, mapOf(0 to (steps and 0xFF), 1 to ((steps shr 8) and 0xFF), 2 to ((steps shr 16) and 0xFF)))
    }

    val BYTES: Set<Int> = setOf(1, 2, 3)
}

/**
 * Profile (0x45): index 1, 2 height and 3, 4 weight (BCD, two bytes each), 5, 6 birth year (BCD), 7 month, 8 day.
 * The byte order of a BCD pair is not documented, so it is found from the height: the order that gives a
 * plausible height is used for every pair. When neither does, the profile is "not recognised" and is never
 * written. Only height and weight can be changed; the birth date is shown, never written.
 */
object ProfileCodec {
    const val CODE = 0x45
    private const val HEIGHT_INDEX = 1
    private const val WEIGHT_INDEX = 3
    private const val YEAR_INDEX = 5
    private val HEIGHT_RANGE = 100..250
    private val WEIGHT_RANGE = 20..300

    data class Profile(
        val heightCm: Int,
        val weightKg: Int,
        val birthYear: Int?,
        val birthMonth: Int?,
        val birthDay: Int?,
        val lowByteFirst: Boolean,
    )

    fun parse(frame: ByteArray): Profile? {
        if (!Frame.hasIndex(frame, CODE, 8)) return null
        for (lowFirst in listOf(true, false)) {
            val height = bcd(frame, HEIGHT_INDEX, lowFirst) ?: continue
            val weight = bcd(frame, WEIGHT_INDEX, lowFirst) ?: continue
            if (height !in HEIGHT_RANGE || weight !in WEIGHT_RANGE) continue
            val year = bcd(frame, YEAR_INDEX, lowFirst)?.takeIf { it in 1900..2100 }
            val month = Frame.u8(frame, 7)?.let { digits(it) }?.takeIf { it in 1..12 }
            val day = Frame.u8(frame, 8)?.let { digits(it) }?.takeIf { it in 1..31 }
            return Profile(height, weight, year, month, day, lowFirst)
        }
        return null
    }

    fun withHeightAndWeight(
        frame: ByteArray,
        heightCm: Int,
        weightKg: Int,
    ): ByteArray? {
        val current = parse(frame) ?: return null
        if (heightCm !in HEIGHT_RANGE || weightKg !in WEIGHT_RANGE) return null
        val changes = pair(HEIGHT_INDEX, heightCm, current.lowByteFirst) + pair(WEIGHT_INDEX, weightKg, current.lowByteFirst)
        return Frame.withBytes(frame, changes)
    }

    private fun pair(
        index: Int,
        value: Int,
        lowFirst: Boolean,
    ): Map<Int, Int> {
        val low = toBcd(value % 100)
        val high = toBcd(value / 100)
        return if (lowFirst) mapOf(index to high, index + 1 to low) else mapOf(index to low, index + 1 to high)
    }

    private fun toBcd(value: Int): Int = (value / 10) * 16 + value % 10

    private fun digits(byte: Int): Int? {
        val high = byte shr 4
        val low = byte and 0x0F
        return if (high > 9 || low > 9) null else high * 10 + low
    }

    private fun bcd(
        frame: ByteArray,
        index: Int,
        lowFirst: Boolean,
    ): Int? {
        val first = digits(Frame.u8(frame, index) ?: return null) ?: return null
        val second = digits(Frame.u8(frame, index + 1) ?: return null) ?: return null
        return if (lowFirst) first + second * 100 else first * 100 + second
    }

    val BYTES: Set<Int> = setOf(HEIGHT_INDEX + 1, HEIGHT_INDEX + 2, WEIGHT_INDEX + 1, WEIGHT_INDEX + 2)
}

/** Smart alarm (0x50), read only: index 0 hour, 1 minute, 2 bit 0 on and bit 1 snooze, 3 wake window in minutes. */
object SmartAlarmCodec {
    const val CODE = 0x50

    data class Setting(val hour: Int, val minute: Int, val on: Boolean, val snooze: Boolean, val windowMinutes: Int)

    fun parse(frame: ByteArray): Setting? {
        if (!Frame.hasIndex(frame, CODE, 3)) return null
        val (hour, minute) = Frame.time(frame, 0) ?: return null
        val flags = Frame.u8(frame, 2) ?: return null
        val window = Frame.u8(frame, 3) ?: return null
        return Setting(hour, minute, flags and 2 != 0, flags and 1 != 0, window)
    }
}
