package org.avmedia.gshockapi.io

import org.avmedia.gshockapi.AppNotification
import org.avmedia.gshockapi.NotificationType

object AppNotificationIO {

    fun xorDecodeBuffer(buffer: String, key: Int = 0xFF): ByteArray {
        val bufferBytes = buffer.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        return bufferBytes.map { (it.toInt() xor key).toByte() }.toByteArray()
    }

    fun xorEncodeBuffer(decodedBytes: ByteArray, key: Int = 0xFF): ByteArray {
        return decodedBytes.map { (it.toInt() xor key).toByte() }
            .toByteArray()
    }

    fun xorEncodeBufferStr(decodedBytes: ByteArray, key: Int = 0xFF): String {
        return xorEncodeBuffer(decodedBytes, key)
            .joinToString("") { "%02x".format(it) }
    }

    private fun readLengthPrefixedString(buf: ByteArray, offset: Int): Pair<String, Int> {
        if (offset + 2 > buf.size) throw IllegalArgumentException("Not enough data to read length prefix")
        val length = buf[offset].toUByte().toInt()
        if (buf[offset + 1] != 0x00.toByte()) throw IllegalArgumentException("Expected null second byte in length prefix")

        val start = offset + 2
        val end = start + length
        if (end > buf.size) throw IllegalArgumentException("String length exceeds buffer")

        val string = buf.sliceArray(start until end).toString(Charsets.UTF_8)
        return string to end
    }

    fun decodeNotificationPacket(buf: ByteArray): AppNotification {
        var offset = 0
        if (buf.size < 6) throw IllegalArgumentException("Buffer too short")
        offset += 6

        val typeByte = buf[offset++].toInt()
        val notifType = NotificationType.entries.getOrNull(typeByte)
            ?: throw IllegalArgumentException("Invalid NotificationType: $typeByte")

        val timestamp = buf.sliceArray(offset until offset + 15).toString(Charsets.US_ASCII)
        offset += 15

        val (app, offset1) = readLengthPrefixedString(buf, offset)
        val (title, offset2) = readLengthPrefixedString(buf, offset1)
        val (shortText, offset3) = readLengthPrefixedString(buf, offset2)
        val (text, _) = readLengthPrefixedString(buf, offset3)

        return AppNotification(
            type = notifType,
            timestamp = timestamp,
            app = app,
            title = title,
            shortText = shortText,
            text = text
        )
    }

    /**
     * Encodes a string into a length-prefixed byte array format.
     *
     * Format structure:
     * - Length byte (1 byte): Size of the UTF-8 encoded string (0-255)
     * - Null byte (1 byte): Always 0x00
     * - Content: UTF-8 encoded string bytes
     *
     * Example:
     * Input string "Hello" becomes:
     * [0x05, 0x00, 0x48, 0x65, 0x6C, 0x6C, 0x6F]
     *  │     │     │    │    │    │    └── 'o'
     *  │     │     │    │    │    └─── 'l'
     *  │     │     │    │    └──── 'l'
     *  │     │     │    └─────── 'e'
     *  │     │     └────────── 'H'
     *  │     └─────────────── null byte
     *  └─────────────────── length (5)
     *
     * @param text String to encode
     * @return ByteArray containing the length-prefixed encoded string
     * @throws IllegalArgumentException if the UTF-8 encoded string exceeds 255 bytes
     */
    private fun writeLengthPrefixedString(text: String): ByteArray {
        val encoded = text.toByteArray(Charsets.UTF_8)
        if (encoded.size > 255) throw IllegalArgumentException("Encoded string too long")
        return byteArrayOf(encoded.size.toByte(), 0x00) + encoded
    }

    /**
     * Encodes an AppNotification object into a byte array protocol buffer format.
     *
     * Buffer structure:
     * - Header (6 bytes): 00 00 00 00 00 01
     * - Type (1 byte): Notification type value (CALENDAR=1, EMAIL=2, etc.)
     * - Timestamp (15 bytes): Fixed length ASCII timestamp (format: YYYYMMDDTHHmmss)
     * - App name:
     *   - Length (1 byte): Size of app name string
     *   - Null byte (1 byte): Always 0x00
     *   - Content: UTF-8 encoded app name string
     * - Title:
     *   - Length (1 byte): Size of title string
     *   - Null byte (1 byte): Always 0x00
     *   - Content: UTF-8 encoded title string
     * - Short text:
     *   - Length (1 byte): Size of short text string
     *   - Null byte (1 byte): Always 0x00
     *   - Content: UTF-8 encoded short text string
     * - Full text:
     *   - Length (1 byte): Size of full text string
     *   - Null byte (1 byte): Always 0x00
     *   - Content: UTF-8 encoded full text string
     *
     * @param data AppNotification object containing notification details
     * @return ByteArray containing the encoded notification data
     * @throws IllegalArgumentException if any encoded string exceeds 255 bytes
     */
    fun encodeNotificationPacket(data: AppNotification): ByteArray {
        val header = byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x00, 0x01)
        val result = mutableListOf<Byte>()

        result.addAll(header.toList())
        result.add(data.type.value.toByte())
        result.addAll(data.timestamp.toByteArray(Charsets.US_ASCII).toList())
        result.addAll(writeLengthPrefixedString(data.app).toList())
        result.addAll(writeLengthPrefixedString(data.title).toList())
        result.addAll(writeLengthPrefixedString(data.shortText).toList())
        result.addAll(writeLengthPrefixedString(data.text).toList())

        return result.toByteArray()
    }
}