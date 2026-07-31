package org.avmedia.gshockapi.io

import org.avmedia.gshockapi.AppNotification
import org.avmedia.gshockapi.NotificationType

// ============================================================================
// Pure Functional Core: Notification Encoding & Decoding
// ============================================================================

/**
 * Pure functional core for notification packet processing.
 * 
 * All methods are pure: no mutable state, no side effects.
 * Handles XOR encoding/decoding and length-prefixed string serialization.
 */
object AppNotificationIOFunctional {
    /**
     * Pure decoder: XOR decodes a hex string buffer.
     * 
     * Converts hex string to bytes and applies XOR operation with key.
     * No side effects - pure transformation.
     */
    fun xorDecodeBuffer(buffer: String, key: Int = 0xFF): ByteArray =
        buffer.chunked(2)
            .map { it.toInt(16).toByte() }
            .map { (it.toInt() xor key).toByte() }
            .toByteArray()

    /**
     * Pure encoder: XOR encodes byte array.
     * 
     * Applies XOR operation on all bytes with the given key.
     * No side effects - pure transformation.
     */
    fun xorEncodeBuffer(decodedBytes: ByteArray, key: Int = 0xFF): ByteArray =
        decodedBytes
            .map { (it.toInt() xor key).toByte() }
            .toByteArray()

    /**
     * Pure encoder: XOR encodes byte array to hex string.
     * 
     * Encodes bytes and converts result to hex string representation.
     * No side effects - pure transformation.
     */
    fun xorEncodeBufferStr(decodedBytes: ByteArray, key: Int = 0xFF): String =
        xorEncodeBuffer(decodedBytes, key)
            .joinToString("") { "%02x".format(it) }

    /**
     * Pure parser: Reads length-prefixed string from buffer at offset.
     * 
     * Format: [1 byte length][1 byte null][UTF-8 string content]
     * Returns the parsed string and the offset after the string.
     * No side effects - pure parsing.
     */
    fun readLengthPrefixedString(buf: ByteArray, offset: Int): Pair<String, Int> {
        require(offset + 2 <= buf.size) { "Not enough data to read length prefix" }
        require(buf[offset + 1] == 0x00.toByte()) { "Expected null second byte in length prefix" }

        val length = buf[offset].toUByte().toInt()

        val start = offset + 2
        val end = start + length
        require(end <= buf.size) { "String length exceeds buffer" }

        val string = buf.copyOfRange(start, end).toString(Charsets.UTF_8)
        return string to end
    }

    /**
     * Pure decoder: Decodes notification packet from byte array.
     * 
     * Parses protocol buffer format:
     * - Header (6 bytes): 00 00 00 00 00 01
     * - Type (1 byte), Timestamp (15 bytes), then 4 length-prefixed strings
     * No side effects - pure transformation.
     */
    fun decodeNotificationPacket(buf: ByteArray): AppNotification {
        var offset = 0
        require(buf.size >= 6) { "Buffer too short" }
        offset += 6

        val typeByte = buf[offset++].toInt()
        val notifyType = NotificationType.entries.getOrNull(typeByte)
            ?: throw IllegalArgumentException("Invalid NotificationType: $typeByte")

        val timestamp = buf.copyOfRange(offset, offset + 15).toString(Charsets.US_ASCII)
        offset += 15

        val (app, offset1) = readLengthPrefixedString(buf, offset)
        val (title, offset2) = readLengthPrefixedString(buf, offset1)
        val (shortText, offset3) = readLengthPrefixedString(buf, offset2)
        val (text, _) = readLengthPrefixedString(buf, offset3)

        return AppNotification(
            type = notifyType,
            timestamp = timestamp,
            app = app,
            title = title,
            shortText = shortText,
            text = text
        )
    }

    /**
     * Pure encoder: Encodes string to length-prefixed byte format.
     * 
     * Format: [1 byte length][1 byte null][UTF-8 string]
     * No side effects - pure transformation.
     * 
     * @throws IllegalArgumentException if UTF-8 encoded string exceeds 255 bytes
     */
    fun writeLengthPrefixedString(text: String): ByteArray {
        val encoded = text.toByteArray(Charsets.UTF_8)
        require(encoded.size <= 255) { "Encoded string too long" }
        return byteArrayOf(encoded.size.toByte(), 0x00) + encoded
    }

    /**
     * Pure encoder: Encodes AppNotification to protocol buffer format.
     * 
     * Builds complete notification packet:
     * - Header (6 bytes): 00 00 00 00 00 01
     * - Type (1 byte), Timestamp (15 bytes)
     * - App name, title, short text, and full text as length-prefixed strings
     * No side effects - pure transformation.
     * 
     * @throws IllegalArgumentException if any encoded string exceeds 255 bytes
     */
    fun encodeNotificationPacket(data: AppNotification): ByteArray =
        buildList {
            addAll(byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x00, 0x01).toList())
            add(data.type.value.toByte())
            addAll(data.timestamp.toByteArray(Charsets.US_ASCII).toList())
            addAll(writeLengthPrefixedString(data.app).toList())
            addAll(writeLengthPrefixedString(data.title).toList())
            addAll(writeLengthPrefixedString(data.shortText).toList())
            addAll(writeLengthPrefixedString(data.text).toList())
        }.toByteArray()
}

object AppNotificationIO {
    fun xorDecodeBuffer(buffer: String, key: Int = 0xFF): ByteArray =
        AppNotificationIOFunctional.xorDecodeBuffer(buffer, key)

    fun xorEncodeBuffer(decodedBytes: ByteArray, key: Int = 0xFF): ByteArray =
        AppNotificationIOFunctional.xorEncodeBuffer(decodedBytes, key)

    fun xorEncodeBufferStr(decodedBytes: ByteArray, key: Int = 0xFF): String =
        AppNotificationIOFunctional.xorEncodeBufferStr(decodedBytes, key)

    fun decodeNotificationPacket(buf: ByteArray): AppNotification =
        AppNotificationIOFunctional.decodeNotificationPacket(buf)

    fun encodeNotificationPacket(data: AppNotification): ByteArray =
        AppNotificationIOFunctional.encodeNotificationPacket(data)
}