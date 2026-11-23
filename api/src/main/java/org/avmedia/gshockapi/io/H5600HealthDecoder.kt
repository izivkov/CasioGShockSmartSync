package org.avmedia.gshockapi.io

import android.os.Build
import androidx.annotation.RequiresApi
import org.avmedia.gshockapi.model.ActivityPeriod
import org.avmedia.gshockapi.model.ActivitySeconds
import org.avmedia.gshockapi.model.DailySummary
import org.avmedia.gshockapi.model.H5600HealthData
import org.avmedia.gshockapi.model.HeartRateSamples
import org.avmedia.gshockapi.model.HeartRateSummary
import org.avmedia.gshockapi.model.StepCounterData
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Decodes one complete DW-H5600 LifeLog transfer into the shared [StepCounterData] model.
 *
 * Pure functional core: no BLE, no I/O. The transfer driver collects the CONVOY notifications
 * and the DRSP index announcements, then calls [decode].
 *
 * Stream layout, from the transfer captures:
 * - Every bulk notification starts with 0x05. Each block is sent in chunks of 512 data bytes
 *   followed by a 2-byte checksum (see [lifeLogStream]). Prefix and checksums are removed, then
 *   every byte is XOR'd with 0xFF.
 * - The stream begins with a 20-byte header: magic `00 0F 00 00 00`, then the UTC time
 *   (year-2000, month, day, hour, minute), the payload length, the day's steps (u16 at 14,
 *   valid only when bytes 16-17 are zero) and the energy (u16 at 18, kcal provisional).
 * - Frames follow. Each frame is a type byte, a 4-byte LE length and a body. A frame is found by
 *   searching for its exact header, which uses the length announced for it.
 * - 5-minute step and energy slots (frames 0x50 and 0x51) are 4-byte records: u16 value, u16 flag.
 *   The newest slot is first; slot i is 5*i minutes before the header time.
 * - Current stats (0x65) hold the daily max, min and resting heart rate as u16 LE at body
 *   offsets 88, 90 and 92. Heart-rate samples (0x52) are 4-byte records whose first byte is bpm.
 */
@RequiresApi(Build.VERSION_CODES.O)
object H5600HealthDecoder {
    private const val BULK_PREFIX: Byte = 0x05
    private const val CHECKSUM_BYTES = 2
    private const val CHUNK_BYTES = 512
    private const val HEADER_SIZE = 20
    private const val FRAME_HEADER_SIZE = 5
    private const val RECORD_SIZE = 4
    private const val SLOT_MINUTES = 5L
    const val HR_SLOT_MINUTES = 5L
    private const val MAX_STEPS_PER_SLOT = 1_000
    private const val MAX_ENERGY_RAW_PER_SLOT = 5_000
    private const val MAX_DAY_STEPS = 100_000
    private const val MIN_BPM = 30
    private const val MAX_BPM = 220
    private const val HEART_RATE_OFFSET_MAX = 88
    private const val HEART_RATE_OFFSET_MIN = 90
    private const val HEART_RATE_OFFSET_RESTING = 92
    private const val MIN_SLOTS_FOR_FULL_DAY = 24
    private const val TAG_STEPS = 0x50
    private const val TAG_ENERGY = 0x51
    private const val TAG_HEART_RATE = 0x52
    private const val TAG_CURRENT_STATS = 0x65
    private const val TAG_SLEEP_STAGES = 0x53
    private const val TAG_STAIRS = 0x57
    private const val TAG_BREATHING = 0x58
    private const val TAG_SPO2 = 0x59
    private const val TAG_NIGHT = 0x5A
    private const val TAG_CARDIO = 0x5B
    private const val TAG_DAILY_SUMMARY = 0x5C
    // Activity times in the current-stats frame: each is hour, minute, second bytes (layout from
    // parse_lifelog.py, matched on 2026-10-26 and 2026-11-16).
    private const val ACTIVITY_RUN_OFFSET = 16
    private const val ACTIVITY_WALK_OFFSET = 19
    private const val ACTIVITY_CYCLE_OFFSET = 22
    private const val ACTIVITY_ZERO_OFFSET = 25
    private const val ACTIVITY_OTHER_OFFSET = 28
    private const val ACTIVITY_STILL_OFFSET = 31
    private const val ACTIVITY_ACTIVE_OFFSET = 34
    private const val WATCH_CONDITION_LIFELOG_BYTE = 9
    private const val WATCH_CONDITION_ACTIVITY_BYTE = 8
    private val HEADER_MAGIC = byteArrayOf(0x00, 0x0F, 0x00, 0x00, 0x00)
    private val FRAME_ORDER = intArrayOf(0x65, 0x50, 0x51, 0x52, 0x53, 0x56, 0x57, 0x58, 0x59, 0x5A, 0x5B, 0x5C, 0x63, 0x72)

    /**
     * Decodes a transfer.
     *
     * @param notifications CONVOY notifications received during the transfer, as received.
     * @param announcedLengths frame type to announced body length, from the DRSP announcements.
     * @param zone the watch's local time zone; slot and hour labels use it.
     * @param stepGoal the watch's step target, if already read from the setup replies.
     * @param watchCondition the last watch-condition (0x28) status message, if available.
     * @return the decoded data, or null when the stream does not start with a LifeLog header.
     */
    fun decode(
        notifications: List<ByteArray>,
        announcedLengths: Map<Int, Int>,
        zone: ZoneId,
        stepGoal: Int? = null,
        watchCondition: ByteArray? = null,
    ): StepCounterData? {
        val stream = lifeLogStream(notifications, announcedLengths)
        if (stream.size < HEADER_SIZE || !stream.copyOfRange(0, HEADER_MAGIC.size).contentEquals(HEADER_MAGIC)) {
            return null
        }

        val buffer = ByteBuffer.wrap(stream).order(ByteOrder.LITTLE_ENDIAN)
        val utcTime =
            OffsetDateTime.of(
                LocalDateTime.of(
                    2000 + unsigned(stream, 5),
                    unsigned(stream, 6),
                    unsigned(stream, 7),
                    unsigned(stream, 8),
                    unsigned(stream, 9),
                ),
                ZoneOffset.UTC,
            )
        val localTime = utcTime.atZoneSameInstant(zone).toLocalDateTime()

        val steps = buffer.getShort(14).toInt() and 0xFFFF
        val stepsReadable = stream[16] == 0.toByte() && stream[17] == 0.toByte() && steps <= MAX_DAY_STEPS
        val energyRaw = buffer.getShort(18).toInt() and 0xFFFF

        val spans = locateFrames(stream, announcedLengths)
        val warnings = mutableListOf("header step total is not verified against the app on every day")
        if (!stepsReadable) warnings += "header step total not reported: header layout not recognised"

        val stepRecords = frameRecords(stream, spans, TAG_STEPS)
        val energyRecords = frameRecords(stream, spans, TAG_ENERGY)
        val hourly = arrayOfNulls<Int>(HOURS)
        val intervals = mutableListOf<ActivityPeriod>()
        val slotCount = minOf(stepRecords.size, energyRecords.size)
        for (index in 0 until slotCount) {
            val slotSteps = stepRecords[index].first
            if (slotSteps > MAX_STEPS_PER_SLOT) continue
            val slotTime = utcTime.minusMinutes(SLOT_MINUTES * index).atZoneSameInstant(zone).toLocalDateTime()
            // Only the slots of the read's own day go into its hours: yesterday's 23:00 slot is not today's hour 23
            // (owner screenshot 2026-11-23 17:53: hour 23 = 179 before 18:00).
            if (slotTime.toLocalDate() == localTime.toLocalDate()) {
                hourly[slotTime.hour] = (hourly[slotTime.hour] ?: 0) + slotSteps
            }
            intervals += ActivityPeriod(index = index, steps = slotSteps, intensity = IntArray(0))
        }
        if (slotCount < MIN_SLOTS_FOR_FULL_DAY) warnings += "only recent slots were transferred; hourly values are partial"
        warnings += "header energy $energyRaw read as kcal (provisional: matched the app's day total on 2026-10-26 " +
            "and the watch display within 1 kcal on 2026-11-16; not yet compared at the same moment)"

        val currentStats = frameBody(stream, spans, TAG_CURRENT_STATS)
        val heartRate =
            currentStats?.let {
                HeartRateSummary(
                    maxBpm = validBpm(it, HEART_RATE_OFFSET_MAX),
                    minBpm = validBpm(it, HEART_RATE_OFFSET_MIN),
                    restingBpm = validBpm(it, HEART_RATE_OFFSET_RESTING),
                )
            }

        val activity = currentStats?.let { activityOf(it) }
        if (activity != null &&
            activity.walkSeconds + activity.cycleSeconds + activity.otherSeconds != activity.activeSeconds
        ) {
            warnings += "watch active time (${activity.activeSeconds} s) differs from its walking, cycling and other parts"
        }

        val watchFlags = watchCondition?.takeIf { it.size > WATCH_CONDITION_LIFELOG_BYTE }

        val health =
            H5600HealthData(
                energyKcal = energyRaw,
                energyIsProvisional = true,
                heartRate = heartRate,
                activity = activity,
                recentHeartRate = recentHeartRate(stream, spans),
                sleep =
                    H5600SleepDecoder.decode(
                        frameBody(stream, spans, TAG_SLEEP_STAGES),
                        frameBody(stream, spans, TAG_NIGHT),
                    ),
                body =
                    H5600BodyDecoder.decode(
                        spo2 = frameBody(stream, spans, TAG_SPO2),
                        breathing = frameBody(stream, spans, TAG_BREATHING),
                        cardio = frameBody(stream, spans, TAG_CARDIO),
                        stairs = frameBody(stream, spans, TAG_STAIRS),
                    ).copy(cardioNow = H5600CurrentValues.cardio(currentStats, localTime.toLocalDate())),
                heartRateSlots = heartRateSlots(stream, spans, utcTime, zone),
                dailySummary = decodeDailySummary(frameBody(stream, spans, TAG_DAILY_SUMMARY)),
                stepGoal = stepGoal,
                lifeLogPending = watchFlags?.let { it[WATCH_CONDITION_LIFELOG_BYTE].toInt() != 0 },
                activityLogPending = watchFlags?.let { it[WATCH_CONDITION_ACTIVITY_BYTE].toInt() != 0 },
            )

        return StepCounterData(
            timestamp = localTime,
            dayOfWeek = localTime.dayOfWeek.value,
            month = localTime.monthValue,
            dayOfMonth = localTime.dayOfMonth,
            hourlySteps = hourly.toList(),
            dailyHistory = emptyList(),
            currentDaySteps = if (stepsReadable) steps else null,
            hourlyByHour = hourly.toList(),
            hourlyIntervals = intervals,
            raw = stream,
            warnings = warnings,
            h5600 = health,
        )
    }

    /**
     * Concatenates the bulk payloads, drops their prefix and the chunk checksums, and XORs with 0xFF.
     *
     * The watch sends each block (the header, then one block per announced frame, in announcement
     * order) as chunks of [CHUNK_BYTES] data bytes, each followed by a [CHECKSUM_BYTES]-byte checksum.
     * A chunk spans several notifications, so the checksum is not at the end of a notification.
     * Block lengths come from the announcements; the header is [HEADER_SIZE] bytes unless announced
     * under index 0. Whatever follows the announced blocks is read as one more block of unknown length.
     */
    fun lifeLogStream(
        notifications: List<ByteArray>,
        announcedLengths: Map<Int, Int> = emptyMap(),
    ): ByteArray {
        val payload = java.io.ByteArrayOutputStream()
        for (notification in notifications) {
            if (notification.size > 1 && notification[0] == BULK_PREFIX) {
                payload.write(notification, 1, notification.size - 1)
            }
        }
        val raw = payload.toByteArray()
        val out = java.io.ByteArrayOutputStream()
        var pos = 0

        val lengths = listOf(announcedLengths[0] ?: HEADER_SIZE) + announcedLengths.filterKeys { it != 0 }.values
        for (length in lengths) {
            var remaining = length
            while (remaining > 0 && pos < raw.size) {
                val take = minOf(CHUNK_BYTES, remaining, raw.size - pos)
                out.write(raw, pos, take)
                pos += take + CHECKSUM_BYTES
                remaining -= take
            }
        }
        while (pos < raw.size) {
            val left = raw.size - pos
            val take = if (left > CHUNK_BYTES + CHECKSUM_BYTES) CHUNK_BYTES else maxOf(0, left - CHECKSUM_BYTES)
            out.write(raw, pos, take)
            pos += take + CHECKSUM_BYTES
        }
        return out.toByteArray().map { (it.toInt() xor 0xFF).toByte() }.toByteArray()
    }

    private fun locateFrames(
        stream: ByteArray,
        announcedLengths: Map<Int, Int>,
    ): Map<Int, IntRange> {
        val starts = linkedMapOf<Int, Int>()
        var searchFrom = HEADER_SIZE
        for (tag in FRAME_ORDER) {
            val length = announcedLengths[tag] ?: continue
            val needle =
                ByteBuffer.allocate(FRAME_HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN)
                    .put(tag.toByte()).putInt(length).array()
            val start = indexOf(stream, needle, searchFrom)
            if (start >= 0) {
                starts[tag] = start
                searchFrom = start + FRAME_HEADER_SIZE
            }
        }
        val ordered = starts.entries.sortedBy { it.value }
        return ordered.mapIndexed { i, (tag, start) ->
            val end = if (i + 1 < ordered.size) ordered[i + 1].value else stream.size
            tag to (start until end)
        }.toMap()
    }

    private fun frameBody(
        stream: ByteArray,
        spans: Map<Int, IntRange>,
        tag: Int,
    ): ByteArray? {
        val span = spans[tag] ?: return null
        val from = span.first + FRAME_HEADER_SIZE
        val to = span.last + 1
        return if (from <= to) stream.copyOfRange(from, to) else null
    }

    private fun frameRecords(
        stream: ByteArray,
        spans: Map<Int, IntRange>,
        tag: Int,
    ): List<Pair<Int, Int>> {
        val body = frameBody(stream, spans, tag) ?: return emptyList()
        return (0 until body.size - RECORD_SIZE + 1 step RECORD_SIZE).map { offset ->
            val buffer = ByteBuffer.wrap(body, offset, RECORD_SIZE).order(ByteOrder.LITTLE_ENDIAN)
            (buffer.getShort().toInt() and 0xFFFF) to (buffer.getShort().toInt() and 0xFFFF)
        }
    }

    private fun recentHeartRate(
        stream: ByteArray,
        spans: Map<Int, IntRange>,
    ): HeartRateSamples? {
        val body = frameBody(stream, spans, TAG_HEART_RATE) ?: return null
        val values =
            (0 until body.size step RECORD_SIZE)
                .map { body[it].toInt() and 0xFF }
                .filter { it in MIN_BPM..MAX_BPM }
        val min = values.minOrNull() ?: return null
        val max = values.maxOrNull() ?: return null
        return HeartRateSamples(
            count = values.size,
            minBpm = min,
            maxBpm = max,
            averageBpm = values.sum() / values.size,
        )
    }

    /**
     * Section 0x52 as one value per [HR_SLOT_MINUTES]: newest record first, record i starts i slots before the header time
     * rounded down to the slot. The 2 minutes are proven on the owner's reads of 2026-11-23 (logs a, c, f): each carried half
     * as many heart rate records as minutes of the one-minute section 0x56 of the same read. Oldest first, valid bpm only.
     */
    private fun heartRateSlots(
        stream: ByteArray,
        spans: Map<Int, IntRange>,
        utcTime: OffsetDateTime,
        zone: ZoneId,
    ): List<org.avmedia.gshockapi.model.HeartRateSlot> {
        val body = frameBody(stream, spans, TAG_HEART_RATE) ?: return emptyList()
        val first = utcTime.minusMinutes((utcTime.minute % HR_SLOT_MINUTES).toLong())
        return (0 until body.size / RECORD_SIZE).mapNotNull { i ->
            val bpm = body[i * RECORD_SIZE].toInt() and 0xFF
            if (bpm !in MIN_BPM..MAX_BPM) return@mapNotNull null
            val time = first.minusMinutes(HR_SLOT_MINUTES * i).atZoneSameInstant(zone).toLocalDateTime()
            org.avmedia.gshockapi.model.HeartRateSlot(time, bpm)
        }.reversed()
    }

    private fun activityOf(body: ByteArray): ActivitySeconds? {
        if (body.size < ACTIVITY_ACTIVE_OFFSET + 3) return null
        return ActivitySeconds(
            runSeconds = hmsAt(body, ACTIVITY_RUN_OFFSET),
            walkSeconds = hmsAt(body, ACTIVITY_WALK_OFFSET),
            cycleSeconds = hmsAt(body, ACTIVITY_CYCLE_OFFSET),
            zeroSeconds = hmsAt(body, ACTIVITY_ZERO_OFFSET),
            otherSeconds = hmsAt(body, ACTIVITY_OTHER_OFFSET),
            stillSeconds = hmsAt(body, ACTIVITY_STILL_OFFSET),
            activeSeconds = hmsAt(body, ACTIVITY_ACTIVE_OFFSET),
        )
    }

    /** 0x5C: u32 steps, u32 calories, u16 (likely resting calories). Null when the body is shorter than 8 bytes. */
    internal fun decodeDailySummary(body: ByteArray?): DailySummary? {
        if (body == null || body.size < 8) return null
        val b = ByteBuffer.wrap(body).order(ByteOrder.LITTLE_ENDIAN)
        val resting = if (body.size >= 10) b.getShort(8).toInt() and 0xFFFF else null
        return DailySummary(b.getInt(0).toLong() and 0xFFFFFFFFL, b.getInt(4).toLong() and 0xFFFFFFFFL, resting)
    }

    private fun hmsAt(body: ByteArray, offset: Int): Int =
        (body[offset].toInt() and 0xFF) * 3600 +
            (body[offset + 1].toInt() and 0xFF) * 60 +
            (body[offset + 2].toInt() and 0xFF)

    private fun validBpm(
        body: ByteArray,
        offset: Int,
    ): Int? {
        if (body.size < offset + 2) return null
        val value = ByteBuffer.wrap(body, offset, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt() and 0xFFFF
        return value.takeIf { it in MIN_BPM..MAX_BPM }
    }

    private fun unsigned(
        bytes: ByteArray,
        index: Int,
    ): Int = bytes[index].toInt() and 0xFF

    private fun indexOf(
        haystack: ByteArray,
        needle: ByteArray,
        from: Int,
    ): Int {
        for (start in from..haystack.size - needle.size) {
            if (needle.indices.all { haystack[start + it] == needle[it] }) return start
        }
        return -1
    }

    private const val HOURS = 24
}
