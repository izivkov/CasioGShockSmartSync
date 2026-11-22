package org.avmedia.gshockapi.io

/** Activity codes of the settings (not the codes of the workout log). */
object ActivityNames {
    private val NAMES =
        mapOf(
            1 to "Running", 2 to "Walking", 3 to "Biking", 4 to "Treadmill running", 5 to "Treadmill walking", 6 to "Gym",
            7 to "Pool", 8 to "Open water", 9 to "Trail running", 10 to "Motion sensor running", 11 to "Motion sensor walking",
            12 to "Trekking", 13 to "Other", 14 to "Interval timer", 15 to "Fast walking", 16 to "Interval walking",
            17 to "Motion sensor fast walking", 18 to "Motion sensor interval walking",
        )

    fun name(code: Int): String = NAMES[code] ?: "Activity $code"

    fun known(code: Int): Boolean = code in NAMES
}

/** Activity list (0x53): up to 15 activity codes in order, 0 = unused. Read only. */
object ActivityListCodec {
    const val CODE = 0x53
    const val MAX = 15

    fun parse(frame: ByteArray): List<Int>? {
        if (frame.size < 2 || frame[0] != CODE.toByte()) return null
        return frame.drop(1).take(MAX).map { it.toInt() and 0xFF }.filter { it != 0 }
    }
}

/** Activity screens (0x4E + activity): index 0 the activity, then per screen three items (main, sub 1, sub 2). Read only. */
object ActivityFaceCodec {
    const val CODE = 0x4E

    val ITEMS: Map<Int, String> =
        mapOf(
            0 to "Empty", 1 to "Split time", 2 to "Distance", 3 to "Pace", 4 to "Lap time", 5 to "Lap distance", 6 to "Lap pace",
            7 to "Average pace", 8 to "Speed", 9 to "Average speed", 10 to "Calories", 11 to "Heart rate",
            12 to "Average heart rate", 13 to "Grade", 14 to "Altitude", 15 to "Ascent", 16 to "Descent",
            17 to "Vertical speed", 18 to "Steps", 19 to "Time", 20 to "Zone", 21 to "Cadence", 22 to "Stride",
        )

    /** Each screen as its three item codes; null when the frame is for another activity. A trailing partial group is dropped. */
    fun parse(frame: ByteArray, activity: Int): List<List<Int>>? {
        if (frame.size < 2 || frame[0] != CODE.toByte() || (frame[1].toInt() and 0xFF) != activity) return null
        return frame.drop(2).map { it.toInt() and 0xFF }.chunked(3).filter { it.size == 3 }
    }

    fun itemName(code: Int): String = ITEMS[code] ?: "Item $code"
}

/**
 * The per-activity wrapper (0x54). Read request: 54 00 activity class part. Answer: 54, activity, class, then the value
 * of that class (some answers may echo the leading 00 before the activity; both shapes are accepted and the shape
 * is kept when writing back). The wrapped value is handled with its own codec through [inner], a frame that starts with
 * the class byte, as if the class had been read on its own.
 */
object ActivityItemCodec {
    const val CODE = 0x54
    const val RUN = 0x40
    const val HEART_RATE = 0x41
    const val TRAINING_ALERT = 0x55
    const val FORM_ALERT = 0x56

    /** Where the wrapped value starts in the answer frame. */
    data class Item(val activity: Int, val cls: Int, val offset: Int)

    fun request(activity: Int, cls: Int, part: Int = 0): ByteArray =
        byteArrayOf(CODE.toByte(), 0, activity.toByte(), cls.toByte(), part.toByte())

    fun parse(frame: ByteArray, activity: Int, cls: Int): Item? {
        if (activity !in 1..0xFF || frame.size < 4 || frame[0] != CODE.toByte()) return null
        fun at(i: Int) = frame[i].toInt() and 0xFF
        if (at(1) == activity && at(2) == cls) return Item(activity, cls, 4)
        if (frame.size >= 5 && at(1) == 0 && at(2) == activity && at(3) == cls) return Item(activity, cls, 4)
        return null
    }

    /** The wrapped value as a frame that starts with the class byte. */
    fun inner(frame: ByteArray, item: Item): ByteArray = byteArrayOf(item.cls.toByte()) + frame.copyOfRange(item.offset, frame.size)

    /** [frame] with the wrapped value replaced by [newInner] (same class byte, same size); the bytes before it stay. */
    fun withInner(frame: ByteArray, item: Item, newInner: ByteArray?): ByteArray? {
        if (newInner == null || newInner.size != frame.size - item.offset + 1 || newInner[0] != item.cls.toByte()) return null
        return frame.copyOf().also { newInner.copyInto(it, item.offset, 1); it[it.size - 1] = 0 }
    }

    /** Frame positions for bytes of the inner frame (inner byte k >= 1 is frame byte offset + k - 1). */
    fun touched(item: Item, innerBytes: Set<Int>): Set<Int> = innerBytes.map { it + item.offset - 1 }.toSet()
}

/** Run measurement (0x40) of one activity. Index 0 bit 1 auto pause, bit 2 auto run detection, bit 3 use the phone's location; 1-2 auto lap distance (unit not confirmed); 4 auto lap screen. */
object RunMeasurementCodec {
    const val CODE = 0x40
    private const val MIN_INDEX = 4
    private const val PAUSE = 2
    private const val DETECT = 4
    private const val LOCATION = 8

    data class Setting(val autoPause: Boolean, val autoRunDetection: Boolean, val phoneLocation: Boolean, val lapDistanceRaw: Int, val lapScreen: Int)

    fun parse(frame: ByteArray): Setting? {
        if (!Frame.hasIndex(frame, CODE, MIN_INDEX)) return null
        val flags = Frame.u8(frame, 0) ?: return null
        val distance = (Frame.u8(frame, 1) ?: return null) or ((Frame.u8(frame, 2) ?: return null) shl 8)
        return Setting(flags and PAUSE != 0, flags and DETECT != 0, flags and LOCATION != 0, distance, Frame.u8(frame, 4) ?: return null)
    }

    fun withAutoPause(frame: ByteArray, on: Boolean): ByteArray? = withFlag(frame, PAUSE, on)

    fun withAutoRunDetection(frame: ByteArray, on: Boolean): ByteArray? = withFlag(frame, DETECT, on)

    fun withPhoneLocation(frame: ByteArray, on: Boolean): ByteArray? = withFlag(frame, LOCATION, on)

    private fun withFlag(frame: ByteArray, mask: Int, on: Boolean): ByteArray? {
        if (parse(frame) == null) return null
        val flags = Frame.u8(frame, 0) ?: return null
        return Frame.withBytes(frame, mapOf(0 to Frame.bits(flags, mask, on)))
    }

    val FLAG_BYTES: Set<Int> = setOf(1)
}

/** Heart-rate switches of one activity (0x41): index 0 bit 2 in-zone notice, bit 3 use target, bit 4 zone alert. The mode is [HeartRateSettingCodec]. */
object HeartRateFlagsCodec {
    private const val NOTICE = 4
    private const val TARGET = 8
    private const val ALERT = 16

    data class Flags(val inZoneNotice: Boolean, val useTarget: Boolean, val zoneAlert: Boolean)

    fun parse(frame: ByteArray): Flags? {
        if (HeartRateSettingCodec.parse(frame) == null) return null
        val flags = Frame.u8(frame, 0) ?: return null
        return Flags(flags and NOTICE != 0, flags and TARGET != 0, flags and ALERT != 0)
    }

    fun withInZoneNotice(frame: ByteArray, on: Boolean): ByteArray? = withFlag(frame, NOTICE, on)

    fun withUseTarget(frame: ByteArray, on: Boolean): ByteArray? = withFlag(frame, TARGET, on)

    fun withZoneAlert(frame: ByteArray, on: Boolean): ByteArray? = withFlag(frame, ALERT, on)

    private fun withFlag(frame: ByteArray, mask: Int, on: Boolean): ByteArray? {
        if (parse(frame) == null) return null
        val flags = Frame.u8(frame, 0) ?: return null
        return Frame.withBytes(frame, mapOf(0 to Frame.bits(flags, mask, on)))
    }

    val FLAG_BYTES: Set<Int> = setOf(1)
}

/**
 * Training alerts of one activity (0x55): 1-2, 3-4 time 1 and 2 interval (u16), 5 distance repeat, 6-7 and 8-9 distance
 * 1 and 2 (u16), 10 calories repeat, 11 calories interval, 12 altitude repeat, 13 altitude interval. Repeat: 0 off,
 * 1 once, 2 repeat. Units of the intervals and the meaning of index 0 are not confirmed: only the repeat values are written.
 */
object TrainingAlertCodec {
    const val CODE = 0x55
    private const val MIN_INDEX = 13
    val REPEATS: List<String> = listOf("Off", "Once", "Repeat")

    data class Setting(
        val time1: Int, val time2: Int, val distanceRepeat: Int, val distance1: Int, val distance2: Int,
        val caloriesRepeat: Int, val caloriesInterval: Int, val altitudeRepeat: Int, val altitudeInterval: Int,
    )

    fun parse(frame: ByteArray): Setting? {
        if (!Frame.hasIndex(frame, CODE, MIN_INDEX)) return null
        val u = (0..MIN_INDEX).map { Frame.u8(frame, it) ?: return null }
        if (listOf(u[5], u[10], u[12]).any { it !in REPEATS.indices }) return null
        return Setting(
            u[1] or (u[2] shl 8), u[3] or (u[4] shl 8), u[5], u[6] or (u[7] shl 8), u[8] or (u[9] shl 8),
            u[10], u[11], u[12], u[13],
        )
    }

    fun withDistanceRepeat(frame: ByteArray, value: Int): ByteArray? = withRepeat(frame, 5, value)

    fun withCaloriesRepeat(frame: ByteArray, value: Int): ByteArray? = withRepeat(frame, 10, value)

    fun withAltitudeRepeat(frame: ByteArray, value: Int): ByteArray? = withRepeat(frame, 12, value)

    private fun withRepeat(frame: ByteArray, index: Int, value: Int): ByteArray? {
        if (parse(frame) == null || value !in REPEATS.indices) return null
        return Frame.withBytes(frame, mapOf(index to value))
    }

    val DISTANCE_BYTES: Set<Int> = setOf(6)
    val CALORIES_BYTES: Set<Int> = setOf(11)
    val ALTITUDE_BYTES: Set<Int> = setOf(13)
}
