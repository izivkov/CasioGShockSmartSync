package org.avmedia.gshockapi.io

import org.avmedia.gshockapi.model.SleepData
import org.avmedia.gshockapi.model.SleepNight
import org.avmedia.gshockapi.model.SleepStage
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Decodes the sleep sections of a DW-H5600 LifeLog: 0x53 (stage timeline, 2016 bytes per night) and
 * 0x5A (nightly record, 55 bytes per day). Pure: bytes in, values out. A value the watch marks "no data"
 * (all ones, or all ones minus one) comes out as null.
 *
 * UNCONFIRMED until a real night is checked: stage times are read as seconds from the record's start time,
 * and a night belongs to the date of its record.
 */
object H5600SleepDecoder {
    const val STAGE_RECORD_BYTES = 2016
    const val NIGHT_RECORD_BYTES = 55
    private const val STAGE_START = 8
    private const val STAGE_PAIRS = 250
    private const val UNUSED_TIME = 0xFFFFFFFEL
    private const val MAX_STAGE_SECONDS = 48L * 3600

    private const val STAGE_AWAKE = -2
    private const val STAGE_REM = -3
    private const val STAGE_LIGHT = -5
    private const val STAGE_DEEP = -6

    fun decode(
        stageBody: ByteArray?,
        nightBody: ByteArray?,
    ): SleepData {
        val nights = records(nightBody, NIGHT_RECORD_BYTES).mapNotNull { night(it) }.sortedBy { it.date }
        val timelines = records(stageBody, STAGE_RECORD_BYTES).mapNotNull { timeline(it) }
        val newest = timelines.maxByOrNull { it.first }
        return SleepData(
            night = nights.lastOrNull { !it.isEmpty },
            nights = nights,
            stages = newest?.second.orEmpty(),
            stagesStart = newest?.first,
        )
    }

    private fun records(
        body: ByteArray?,
        size: Int,
    ): List<ByteArray> = if (body == null) emptyList() else (0 until body.size / size).map { body.copyOfRange(it * size, (it + 1) * size) }

    private fun timeline(record: ByteArray): Pair<LocalDateTime, List<SleepStage>>? {
        val start = Bcd.dateTime(record, 0, withSeconds = true) ?: return null
        val buffer = ByteBuffer.wrap(record).order(ByteOrder.LITTLE_ENDIAN)
        val stages = mutableListOf<SleepStage>()
        for (pair in 0 until STAGE_PAIRS) {
            val at = STAGE_START + pair * 8
            val seconds = buffer.getInt(at).toLong() and 0xFFFFFFFFL
            if (seconds == UNUSED_TIME) break
            val name =
                when (buffer.getInt(at + 4)) {
                    STAGE_AWAKE -> "awake"
                    STAGE_REM -> "rem"
                    STAGE_LIGHT -> "light"
                    STAGE_DEEP -> "deep"
                    else -> null
                }
            if (name == null || seconds > MAX_STAGE_SECONDS) continue
            stages += SleepStage(start.plusSeconds(seconds), name)
        }
        return if (stages.isEmpty()) null else start to stages.sortedBy { it.time }
    }

    private fun night(record: ByteArray): SleepNight? {
        val date = Bcd.date(record, 1) ?: return null
        val buffer = ByteBuffer.wrap(record).order(ByteOrder.LITTLE_ENDIAN)
        fun u8(at: Int) = Bcd.validU8(record[at])
        fun u16(at: Int) = (buffer.getShort(at).toInt() and 0xFFFF).takeIf { it < 0xFFFE }
        fun float(at: Int) = buffer.getFloat(at).takeIf { it.isFinite() && it > 0f && it < MAX_BREATHING }
        fun minutes(at: Int): Int? {
            val hours = u8(at) ?: return null
            val minutes = u8(at + 1) ?: return null
            return if (minutes < 60) hours * 60 + minutes else null
        }
        fun clock(at: Int): LocalDateTime? {
            val hour = u8(at) ?: return null
            val minute = u8(at + 1) ?: return null
            return if (hour < 24 && minute < 60) date.atTime(hour, minute) else null
        }
        val fellAsleep = clock(48)
        var wokeUp = clock(50)
        if (fellAsleep != null && wokeUp != null && wokeUp.isBefore(fellAsleep)) wokeUp = wokeUp.plusDays(1)
        return SleepNight(
            date = date,
            zoneQuarterHours = record[3].toInt().takeIf { it != -1 && it != -2 },
            sleepScore = u8(31)?.takeIf { it in 1..100 },
            rechargeStatus = u8(5)?.takeIf { it in 1..5 },
            rechargeLevel = u8(6),
            fellAsleep = fellAsleep,
            wokeUp = wokeUp,
            totalSleepMinutes = minutes(34),
            deepMinutes = minutes(44),
            lightMinutes = minutes(46),
            remMinutes = minutes(42),
            awakeMinutes = u8(40),
            heartRateNight = u8(12)?.takeIf { it in 20..250 },
            heartRateBaseline = u8(13)?.takeIf { it in 20..250 },
            heartRateVariability = u16(18),
            breathingRate = float(22),
            breathingBaseline = float(26),
        )
    }

    private const val MAX_BREATHING = 80f
}

/** Binary-coded decimal bytes of the LifeLog and the "all ones" no-data marks. */
internal object Bcd {
    /** One BCD byte as 0..99, or null for a no-data mark or a byte with a digit above 9. */
    fun byte(value: Byte): Int? {
        val v = value.toInt() and 0xFF
        val high = v shr 4
        val low = v and 0x0F
        return if (high > 9 || low > 9) null else high * 10 + low
    }

    /** A plain byte, or null when it is the no-data mark (0xFF, or 0xFE). */
    fun validU8(value: Byte): Int? = (value.toInt() and 0xFF).takeIf { it < 0xFE }

    fun date(
        bytes: ByteArray,
        at: Int,
    ): LocalDate? {
        val year = byte(bytes[at]) ?: return null
        val month = byte(bytes[at + 1]) ?: return null
        val day = byte(bytes[at + 2]) ?: return null
        return runCatching { LocalDate.of(2000 + year, month, day) }.getOrNull()
    }

    /** Year, month, day, hour, minute (and second when [withSeconds]) as BCD bytes. */
    fun dateTime(
        bytes: ByteArray,
        at: Int,
        withSeconds: Boolean = false,
    ): LocalDateTime? {
        val date = date(bytes, at) ?: return null
        val hour = byte(bytes[at + 3]) ?: return null
        val minute = byte(bytes[at + 4]) ?: return null
        val second = if (withSeconds) byte(bytes[at + 5]) ?: return null else 0
        return runCatching { date.atTime(hour, minute, second) }.getOrNull()
    }
}
