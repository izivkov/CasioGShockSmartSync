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
    private const val FLAGS_INDEX = 5
    private const val MAX_HR_INDEX = 6
    private const val RESTING_HR_INDEX = 7
    private const val VO2MAX_INDEX = 8

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

    /** Body values: training background, highest and resting heart rate, VO2max, with the "set by the user" flags. */
    data class Body(
        val trainingBackground: Int,
        val maxHr: Int,
        val restingHr: Int,
        val vo2max: Int,
        val maxHrByUser: Boolean,
        val restingHrByUser: Boolean,
        val vo2maxByUser: Boolean,
    )

    val TRAINING_BACKGROUNDS: List<String> = listOf("Not set", "Occasional", "Regular", "Frequent", "Heavy", "Semi-pro", "Pro")
    val MAX_HR_RANGE = 100..230
    val RESTING_HR_RANGE = 30..120
    val VO2MAX_RANGE = 10..90

    fun parseBody(frame: ByteArray): Body? {
        if (!Frame.hasIndex(frame, CODE, VO2MAX_INDEX)) return null
        val background = Frame.u8(frame, 0)?.takeIf { it in TRAINING_BACKGROUNDS.indices } ?: return null
        val flags = Frame.u8(frame, FLAGS_INDEX) ?: return null
        return Body(
            background, Frame.u8(frame, MAX_HR_INDEX) ?: return null, Frame.u8(frame, RESTING_HR_INDEX) ?: return null,
            Frame.u8(frame, VO2MAX_INDEX) ?: return null, flags and 1 != 0, flags and 2 != 0, flags and 4 != 0,
        )
    }

    fun withTrainingBackground(frame: ByteArray, value: Int): ByteArray? =
        if (parseBody(frame) == null || value !in TRAINING_BACKGROUNDS.indices) null else Frame.withBytes(frame, mapOf(0 to value))

    /** Sets the value and its "set by the user" flag; the other flag bits stay. */
    fun withMaxHeartRate(frame: ByteArray, bpm: Int): ByteArray? = withValue(frame, MAX_HR_INDEX, 1, bpm, MAX_HR_RANGE)

    fun withRestingHeartRate(frame: ByteArray, bpm: Int): ByteArray? = withValue(frame, RESTING_HR_INDEX, 2, bpm, RESTING_HR_RANGE)

    fun withVo2max(frame: ByteArray, value: Int): ByteArray? = withValue(frame, VO2MAX_INDEX, 4, value, VO2MAX_RANGE)

    private fun withValue(frame: ByteArray, index: Int, flag: Int, value: Int, range: IntRange): ByteArray? {
        if (parseBody(frame) == null || value !in range) return null
        val flags = Frame.u8(frame, FLAGS_INDEX) ?: return null
        return Frame.withBytes(frame, mapOf(index to value, FLAGS_INDEX to Frame.bits(flags, flag, true)))
    }

    val BACKGROUND_BYTES: Set<Int> = setOf(1)
    val MAX_HR_BYTES: Set<Int> = setOf(MAX_HR_INDEX + 1, FLAGS_INDEX + 1)
    val RESTING_HR_BYTES: Set<Int> = setOf(RESTING_HR_INDEX + 1, FLAGS_INDEX + 1)
    val VO2MAX_BYTES: Set<Int> = setOf(VO2MAX_INDEX + 1, FLAGS_INDEX + 1)

    val BEDTIME_BYTES: Set<Int> = setOf(BED_INDEX + 1, BED_INDEX + 2)
    val WAKE_BYTES: Set<Int> = setOf(WAKE_INDEX + 1, WAKE_INDEX + 2)
}

/**
 * Unit of length, bit 0 of 0x13 index 11: 0 kilometres, 1 miles (proven in the notes, round 4).
 */
enum class LengthUnit(val bit: Boolean, val label: String) {
    KM(false, "Kilometres"),
    MILES(true, "Miles"),
    ;

    companion object {
        fun of(bit: Boolean): LengthUnit = if (bit) MILES else KM
    }
}

/**
 * Fields of the basic settings (0x13) that the health settings screen uses:
 * index 11 bit 0 length unit (0 km, 1 miles), bit 1 step reminder;
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
        val unit: LengthUnit,
        val stepReminder: Boolean,
        val nightStart: Pair<Int, Int>,
        val nightEnd: Pair<Int, Int>,
    )

    fun parse(frame: ByteArray): Fields? {
        if (!Frame.hasIndex(frame, CODE, NIGHT_END_INDEX + 1)) return null
        val flags = Frame.u8(frame, FLAGS_INDEX) ?: return null
        return Fields(
            unit = LengthUnit.of(flags and UNIT_BIT != 0),
            stepReminder = flags and REMINDER_BIT != 0,
            nightStart = Frame.time(frame, NIGHT_START_INDEX) ?: return null,
            nightEnd = Frame.time(frame, NIGHT_END_INDEX) ?: return null,
        )
    }

    fun withStepReminder(
        frame: ByteArray,
        on: Boolean,
    ): ByteArray? = withFlag(frame, REMINDER_BIT, on)

    fun withUnit(
        frame: ByteArray,
        unit: LengthUnit,
    ): ByteArray? = withFlag(frame, UNIT_BIT, unit.bit)

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
 * Profile (0x45): index 0 flags (bit 0 sex field, bit 2 imperial length, bit 3 weight not kg, bit 4 energy not kcal),
 * index 1, 2 height and 3, 4 weight (BCD, lowest pair first), 5, 6 birth year (BCD, full year), 7 month, 8 day.
 * The pair order is still checked against a plausible height (low pair first is tried first). When nothing is
 * plausible the profile is "not recognised" and never written. Height, weight and the birth date are writable;
 * which sex is 0 and the scale of height and weight are not confirmed.
 */
object ProfileCodec {
    const val CODE = 0x45
    private const val HEIGHT_INDEX = 1
    private const val WEIGHT_INDEX = 3
    private const val YEAR_INDEX = 5
    private val HEIGHT_RANGE = 30..250
    private val WEIGHT_RANGE = 20..700

    /** The range a height may be written in: inches with the imperial flag, else centimetres. */
    fun heightRange(imperial: Boolean): IntRange = if (imperial) 39..98 else 100..250

    /** The range a weight may be written in: pounds with the flag, else kilograms. */
    fun weightRange(notKg: Boolean): IntRange = if (notKg) 44..661 else 20..300

    /**
     * [height] and [weight] are the numbers in the frame: inches and pounds with the imperial flags, else cm and kg
     * (the scale is likely, not confirmed). [sexBit] is the first value of the sex field; which sex is 0 is not known.
     */
    data class Profile(
        val height: Int,
        val weight: Int,
        val birthYear: Int?,
        val birthMonth: Int?,
        val birthDay: Int?,
        val lowByteFirst: Boolean,
        val sexBit: Boolean = false,
        val imperialLength: Boolean = false,
        val weightNotKg: Boolean = false,
        val energyNotKcal: Boolean = false,
    ) {
        val heightCm: Int get() = if (imperialLength) Math.round(height * 2.54).toInt() else height
        val weightKg: Int get() = if (weightNotKg) Math.round(weight / 2.2046226).toInt() else weight
    }

    fun parse(frame: ByteArray): Profile? {
        if (!Frame.hasIndex(frame, CODE, 8)) return null
        for (lowFirst in listOf(true, false)) {
            val height = bcd(frame, HEIGHT_INDEX, lowFirst) ?: continue
            val weight = bcd(frame, WEIGHT_INDEX, lowFirst) ?: continue
            if (height !in HEIGHT_RANGE || weight !in WEIGHT_RANGE) continue
            val year = bcd(frame, YEAR_INDEX, lowFirst)?.takeIf { it in 1900..2100 }
            val month = Frame.u8(frame, 7)?.let { digits(it) }?.takeIf { it in 1..12 }
            val day = Frame.u8(frame, 8)?.let { digits(it) }?.takeIf { it in 1..31 }
            val flags = Frame.u8(frame, 0) ?: 0
            return Profile(height, weight, year, month, day, lowFirst, flags and 1 != 0, flags and 4 != 0, flags and 8 != 0, flags and 16 != 0)
        }
        return null
    }

    /** Height and weight as the numbers in the frame's own units (see [Profile]); the flag byte stays. */
    fun withHeightAndWeight(
        frame: ByteArray,
        height: Int,
        weight: Int,
    ): ByteArray? {
        val current = parse(frame) ?: return null
        if (height !in heightRange(current.imperialLength) || weight !in weightRange(current.weightNotKg)) return null
        val changes = pair(HEIGHT_INDEX, height, current.lowByteFirst) + pair(WEIGHT_INDEX, weight, current.lowByteFirst)
        return Frame.withBytes(frame, changes)
    }

    /** The birth date (index 5, 6 year, 7 month, 8 day, BCD): only a real calendar date from 1900 to 2100 is written. */
    fun withBirthDate(
        frame: ByteArray,
        year: Int,
        month: Int,
        day: Int,
    ): ByteArray? {
        val current = parse(frame) ?: return null
        val valid = year in 1900..2100 && runCatching { java.time.LocalDate.of(year, month, day) }.isSuccess
        if (!valid) return null
        return Frame.withBytes(frame, pair(YEAR_INDEX, year, current.lowByteFirst) + mapOf(7 to toBcd(month), 8 to toBcd(day)))
    }

    private fun pair(
        index: Int,
        value: Int,
        lowFirst: Boolean,
    ): Map<Int, Int> {
        val low = toBcd(value % 100)
        val high = toBcd(value / 100)
        return if (lowFirst) mapOf(index to low, index + 1 to high) else mapOf(index to high, index + 1 to low)
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

    /** Frame bytes that [withBirthDate] may change. */
    val BIRTH_BYTES: Set<Int> = (YEAR_INDEX..8).map { it + 1 }.toSet()
}

/**
 * Smart alarm (0x50): index 0 hour, 1 minute (plain numbers), 2 bit 0 on and bit 1 snooze, 3 wake window in minutes
 * (15, 30, 45 or 60). Switching the alarm off also clears the snooze bit. Every other byte stays.
 */
object SmartAlarmCodec {
    const val CODE = 0x50

    data class Setting(val hour: Int, val minute: Int, val on: Boolean, val snooze: Boolean, val windowMinutes: Int)

    fun parse(frame: ByteArray): Setting? {
        if (!Frame.hasIndex(frame, CODE, 3)) return null
        val (hour, minute) = Frame.time(frame, 0) ?: return null
        val flags = Frame.u8(frame, 2) ?: return null
        val window = Frame.u8(frame, 3) ?: return null
        return Setting(hour, minute, flags and 1 != 0, flags and 2 != 0, window)
    }

    val WINDOWS: List<Int> = listOf(15, 30, 45, 60)

    fun withTime(frame: ByteArray, hour: Int, minute: Int): ByteArray? {
        if (parse(frame) == null || hour !in 0..23 || minute !in 0..59) return null
        return Frame.withBytes(frame, mapOf(0 to hour, 1 to minute))
    }

    /** On or off; off also clears the snooze bit. */
    fun withOn(frame: ByteArray, on: Boolean): ByteArray? {
        val flags = parse(frame)?.let { Frame.u8(frame, 2) } ?: return null
        val next = if (on) Frame.bits(flags, 1, true) else flags and 3.inv()
        return Frame.withBytes(frame, mapOf(2 to next))
    }

    /** Snooze can only be set while the alarm is on. */
    fun withSnooze(frame: ByteArray, snooze: Boolean): ByteArray? {
        val current = parse(frame) ?: return null
        if (snooze && !current.on) return null
        val flags = Frame.u8(frame, 2) ?: return null
        return Frame.withBytes(frame, mapOf(2 to Frame.bits(flags, 2, snooze)))
    }

    fun withWindow(frame: ByteArray, minutes: Int): ByteArray? {
        if (parse(frame) == null || minutes !in WINDOWS) return null
        return Frame.withBytes(frame, mapOf(3 to minutes))
    }

    val TIME_BYTES: Set<Int> = setOf(1, 2)
    val FLAG_BYTES: Set<Int> = setOf(3)
    val WINDOW_BYTES: Set<Int> = setOf(4)
}
