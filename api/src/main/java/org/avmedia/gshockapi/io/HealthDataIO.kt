package org.avmedia.gshockapi.io

import CachedIO
import kotlinx.coroutines.CompletableDeferred
import org.avmedia.gshockapi.HealthData
import org.avmedia.gshockapi.utils.Utils
import timber.log.Timber

object HealthDataIO {

    private data class State(val deferredResult: CompletableDeferred<HealthData>? = null)

    private var state = State()

    suspend fun request(): HealthData {

        return CachedIO.request("070000000000000000000000000000") { key ->
            state = state.copy(deferredResult = CompletableDeferred())
            IO.request(key)
            state.deferredResult?.await() ?: HealthData(0, 0, 0, 0, "")
        }
    }

    fun onReceived(data: String) {
        state.deferredResult?.complete(HealthDataDecoder.decode(data))
        state = State()
    }

    fun toJson(data: String): String {
        return HealthDataDecoder.decode(data).toJson()
    }

    fun sendToWatch(data: HealthData) {
        // Placeholder for sending health data to the watch if needed
        // Implementing based on pattern, though specific command format is unknown
        // Assuming we might send it similarly to notifications or just log it for now
        Timber.d("HealthDataIO: Sending health data to watch: $data")
    }

    object HealthDataDecoder {
        /**
         * Decodes the Health Data response from the watch.
         *
         * Example input (from logs): "05fff0ffffffe5fef9edf372fdffffffffffff21"
         *
         * Decodes to (XOR 0xFF): FA 00 0F 00 00 00 1A 01 06 12 0C 8D 02 00 00 00 00 00 00 DE
         *
         * Fields:
         * - 0: FA (Header)
         * - 6-10: Timestamp (Year, Month, Day, Hour, Minute) -> 2026-01-06T18:12
         * - 13-16: Steps
         * - 19-20: Calories
         */
        fun decode(data: String): HealthData {
            val intArr = Utils.toIntArray(data)
            if (intArr.isEmpty()) return HealthData(0, 0, 0, 0, "")

            // XOR Decode with 0xFF
            val decoded = intArr.map { it xor 0xFF }.toIntArray()

            // Check for Life Log Header (0xFA)
            // The raw data usually starts with 0x05 (Sequence), so we skip index 0
            // Decoded[0] (was 0x05) -> 0xFA
            if (decoded.size < 20 || decoded[0] != 0xFA) {
                // Return empty if not a valid life log packet
                return HealthData(0, 0, 0, 0, "")
            }

            // Timestamp (Offset 6, 5 bytes: YY MM DD HH MM)
            val year = 2000 + decoded[6]
            val month = decoded[7]
            val day = decoded[8]
            val hour = decoded[9]
            val minute = decoded[10]
            val timestamp =
                    String.format("%04d-%02d-%02dT%02d:%02d:00", year, month, day, hour, minute)

            // Steps (Offset 13, 4 bytes, Little Endian)
            val steps =
                    decoded[13] + (decoded[14] shl 8) + (decoded[15] shl 16) + (decoded[16] shl 24)

            // Calories (Offset 19, 2 bytes, Little Endian)
            val calories = decoded[19] + (decoded[20] shl 8)

            val heartRate = 0 // Not yet identified in this packet
            val distance = 0 // Not yet identified in this packet

            return HealthData(steps, calories, heartRate, distance, timestamp)
        }
    }
}
