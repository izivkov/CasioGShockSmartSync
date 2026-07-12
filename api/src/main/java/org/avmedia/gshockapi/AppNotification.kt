package org.avmedia.gshockapi

enum class NotificationType(val value: Int) {
    GENERIC(0),
    PHONE_CALL_URGENT(1),
    PHONE_CALL(2),
    EMAIL(3),
    MESSAGE(4),
    CALENDAR(5),
    EMAIL_SMS(6)
}

data class AppNotification(
    val type: NotificationType,
    val timestamp: String,
    val app: String,
    val title: String,
    val text: String,
    val shortText: String = ""
) {
    companion object {
        private const val MAX_TEXT_BYTES = 193
        private const val MAX_SHORT_TEXT_BYTES = 40
        private const val MAX_COMBINED_BYTES = 206

        fun create(
            type: NotificationType,
            timestamp: String,
            app: String,
            title: String,
            text: String,
            shortText: String = ""
        ): AppNotification {
            val truncatedShortText = truncateUtf8Bytes(shortText, MAX_SHORT_TEXT_BYTES)
            val truncatedText = truncateUtf8Bytes(text, MAX_TEXT_BYTES)
            val adjustedText = adjustTextLength(truncatedText, truncatedShortText)

            return AppNotification(
                type = type,
                timestamp = timestamp,
                app = app,
                title = title,
                text = adjustedText,
                shortText = truncatedShortText
            )
        }

        private fun truncateUtf8Bytes(input: String, maxBytes: Int): String {
            val bytes = input.encodeToByteArray()
            return if (bytes.size <= maxBytes) input
            else bytes.copyOf(maxBytes).toString(Charsets.UTF_8)
        }

        private fun adjustTextLength(text: String, shortText: String): String {
            val textBytes = text.encodeToByteArray()
            val shortTextBytes = shortText.encodeToByteArray()
            val totalBytes = textBytes.size + shortTextBytes.size

            return if (totalBytes > MAX_COMBINED_BYTES) {
                val allowedTextBytes = (MAX_COMBINED_BYTES - shortTextBytes.size).coerceAtLeast(0)
                textBytes.copyOf(allowedTextBytes).toString(Charsets.UTF_8)
            } else text
        }
    }

    fun toMap(): Map<String, Any> = mapOf(
        "type" to type,
        "timestamp" to timestamp,
        "app" to app,
        "title" to title,
        "text" to text,
        "short_text" to shortText
    )
}
