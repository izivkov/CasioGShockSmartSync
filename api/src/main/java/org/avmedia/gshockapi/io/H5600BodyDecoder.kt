package org.avmedia.gshockapi.io

import org.avmedia.gshockapi.model.BodyReadings
import org.avmedia.gshockapi.model.BreathingSession
import org.avmedia.gshockapi.model.CardioStatus
import org.avmedia.gshockapi.model.Spo2Reading
import org.avmedia.gshockapi.model.StairsRecord
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Decodes the LifeLog sections for blood oxygen (0x59), breathing exercises (0x58), cardio status (0x5B)
 * and stairs (0x57). Pure. The record shapes are read from the watch's own layout; which byte is which
 * inside a breathing record and the meaning of the two stairs counts are UNCONFIRMED.
 */
object H5600BodyDecoder {
    private const val SPO2_BYTES = 10
    private const val BREATHING_BYTES = 15
    private const val CARDIO_BYTES = 14
    private const val STAIRS_BYTES = 12
    private const val MAX_LOAD_RATIO = 9.99f
    private const val MAX_CARDIO_VALUE = 999
    private const val CARDIO_BLOCK_BYTES = 9

    fun decode(
        spo2: ByteArray?,
        breathing: ByteArray?,
        cardio: ByteArray?,
        stairs: ByteArray?,
    ): BodyReadings =
        BodyReadings(
            spo2 = records(spo2, SPO2_BYTES).mapNotNull { spo2Reading(it) }.sortedBy { it.time },
            breathingSessions = records(breathing, BREATHING_BYTES).mapNotNull { breathingSession(it) }.sortedBy { it.time },
            cardio = records(cardio, CARDIO_BYTES).mapNotNull { cardioStatus(it) }.maxByOrNull { it.date },
            stairs = records(stairs, STAIRS_BYTES).mapNotNull { stairsRecord(it) }.sortedBy { it.time },
        )

    private fun records(
        body: ByteArray?,
        size: Int,
    ): List<ByteArray> = if (body == null) emptyList() else (0 until body.size / size).map { body.copyOfRange(it * size, (it + 1) * size) }

    private fun spo2Reading(record: ByteArray): Spo2Reading? {
        val time = Bcd.dateTime(record, 0) ?: return null
        val percent = Bcd.validU8(record[6])?.takeIf { it in 50..100 } ?: return null
        return Spo2Reading(time, percent)
    }

    private fun breathingSession(record: ByteArray): BreathingSession? {
        val time = Bcd.dateTime(record, 0) ?: return null
        return BreathingSession(time, (6 until 14).map { Bcd.byte(record[it]) })
    }

    private fun cardioStatus(record: ByteArray): CardioStatus? {
        val date = Bcd.date(record, 0) ?: return null
        return cardioBlock(record, 4, date)
    }

    /**
     * The 9-byte cardio block at [at]: float load ratio (0 to 9.99), u8 feedback (1 to 4), u16 strain and u16
     * tolerance (each up to 999). Shared by the cardio status section 0x5B and the current values 0x65. Null when every value
     * is "no data" or the block does not fit.
     */
    internal fun cardioBlock(bytes: ByteArray, at: Int, date: java.time.LocalDate): CardioStatus? {
        if (at < 0 || bytes.size < at + CARDIO_BLOCK_BYTES) return null
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        fun u16(pos: Int) = (buffer.getShort(at + pos).toInt() and 0xFFFF).takeIf { it <= MAX_CARDIO_VALUE }
        val ratio = buffer.getFloat(at).takeIf { it.isFinite() && it >= 0f && it <= MAX_LOAD_RATIO }
        val feedback = Bcd.validU8(bytes[at + 4])?.takeIf { it in 1..4 }
        val status = CardioStatus(date, ratio, feedback, u16(5), u16(7))
        val empty = ratio == null && feedback == null && status.strain == null && status.tolerance == null
        return if (empty) null else status
    }

    private fun stairsRecord(record: ByteArray): StairsRecord? {
        val time = Bcd.dateTime(record, 0, withSeconds = true) ?: return null
        val buffer = ByteBuffer.wrap(record).order(ByteOrder.LITTLE_ENDIAN)
        val up = (buffer.getShort(8).toInt() and 0xFFFF).takeIf { it < 0xFFFE } ?: return null
        val down = (buffer.getShort(10).toInt() and 0xFFFF).takeIf { it < 0xFFFE } ?: return null
        return StairsRecord(time, up, down)
    }
}
