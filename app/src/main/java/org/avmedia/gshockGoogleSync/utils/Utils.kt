package org.avmedia.gshockGoogleSync.utils

import com.google.gson.GsonBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class Utils {
    companion object {
        fun AppHashCode(): String {
            val callingFunctionName = Thread.currentThread().stackTrace[3].methodName
            return callingFunctionName.hashCode().toString()
        }

        fun <T> runApi(apiCall: suspend (Array<out T>) -> Unit, vararg args: T) {
            CoroutineScope(Dispatchers.IO).launch {
                runCatching {
                    apiCall(args)  // Pass the arguments as an array
                }.onFailure {
                    it.printStackTrace()
                }
            }
        }

        fun shortenStringNewLine(
            input: String,
            wordMaxLength: Int,
            defaultString: String? = null
        ): String {
            // Split the input string into words
            val words = input.split(" ").filter { it.isNotBlank() }

            // Check if any word exceeds the max length
            if (words.any { it.length > wordMaxLength }) {
                // If defaultString is provided, return it
                defaultString?.let { return it }
            }

            // Otherwise, join words with a newline separator
            return words.joinToString("\n")
        }

        fun wrapString(input: String, maxLength: Int): String {
            val words = input.split(" ")
            val lines = StringBuilder()
            var currentLine = StringBuilder()

            for (word in words) {
                if (currentLine.isNotEmpty()) {
                    if (currentLine.length + 1 + word.length <= maxLength) {
                        currentLine.append(" ").append(word)
                    } else {
                        lines.append(currentLine.toString()).append("\n")
                        currentLine = StringBuilder(word)
                    }
                } else {
                    currentLine.append(word)
                }
            }
            if (currentLine.isNotEmpty()) {
                lines.append(currentLine.toString())
            }
            return lines.toString()
        }

        fun shortenString(input: String, maxLength: Int, ellipsis: Char = '…'): String {
            require(maxLength >= 1) { "maxLength must be at least 1" }

            if (input.length <= maxLength) return input
            val shortenedLength = maxLength - 1 // Reserve space for the ellipsis
            return input.take(shortenedLength) + ellipsis
        }

        private val MAC_REGEX_PATTERN = "[^A-F0-9]"
        private val COLON_SEPARATOR = ":"
        private val EXPECTED_MAC_LENGTH = 12

        /**
         * Sanitizes a MAC address string by:
         * 1. Converting to Uppercase.
         * 2. Removing all non-hex characters (spaces, dashes, colons).
         * 3. Re-inserting colons at the standard 2-character intervals.
         */
        fun sanitizeMacAddress(mac: String): String {
            // Clean the input: Uppercase and remove everything except 0-9 and A-F
            val cleanHex: String = mac.uppercase().replace(Regex(MAC_REGEX_PATTERN), "")

            // If it's not a full MAC address, just return the cleaned hex
            if (cleanHex.length != EXPECTED_MAC_LENGTH) {
                return cleanHex
            }

            // Use a builder to re-format with colons (e.g., AA:BB:CC:DD:EE:FF)
            val builder: StringBuilder = StringBuilder()
            for (i in cleanHex.indices) {
                builder.append(cleanHex[i])
                // Add a colon after every 2 characters, but not at the very end
                if ((i + 1) % 2 == 0 && i != cleanHex.length - 1) {
                    builder.append(COLON_SEPARATOR)
                }
            }

            return builder.toString()
        }
    }
}