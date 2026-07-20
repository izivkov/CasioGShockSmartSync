package org.avmedia.gshockGoogleSync.utils

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.avmedia.gshockGoogleSync.di.ApplicationContextEntryPoint
import timber.log.Timber
import kotlin.math.sin

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

        enum class ToneType(val value: Int) {
            LOW(ToneGenerator.TONE_CDMA_LOW_L),
            MEDIUM(ToneGenerator.TONE_CDMA_MED_L),
            HIGH(ToneGenerator.TONE_CDMA_HIGH_L)
        }

        fun beep(context: Context, toneType: ToneType, duration: Int = 100) {

            Handler(Looper.getMainLooper()).post {
                val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

                val audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()

                val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                    .setAudioAttributes(audioAttributes)
                    .build()
                val focusResult = audioManager.requestAudioFocus(focusRequest)
                Timber.d("beep(): audio focus request result: $focusResult")

                val toneGenerator = ToneGenerator(AudioManager.STREAM_ALARM, ToneGenerator.MAX_VOLUME)
                toneGenerator.startTone(toneType.value, duration)

                // startTone is async, so release after the tone finishes (duration + small buffer)
                Handler(Looper.getMainLooper()).postDelayed({
                    toneGenerator.release()
                    audioManager.abandonAudioFocusRequest(focusRequest)
                }, duration.toLong() + 50)
            }
        }
    }
}