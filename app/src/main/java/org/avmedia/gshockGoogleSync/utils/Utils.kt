package org.avmedia.gshockGoogleSync.utils

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
                try {
                    apiCall(args)  // Pass the arguments as an array
                } catch (e: Exception) {
                    e.printStackTrace()
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

        fun shortenString(input: String, maxLength: Int, ellipsis: Char = '…'): String {
            require(maxLength >= 1) { "maxLength must be at least 1" }

            if (input.length <= maxLength) return input
            val shortenedLength = maxLength - 1 // Reserve space for the ellipsis
            return input.take(shortenedLength) + ellipsis
        }
    }
}