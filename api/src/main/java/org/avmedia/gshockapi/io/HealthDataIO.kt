package org.avmedia.gshockapi.io

import CachedIO
import kotlinx.coroutines.CompletableDeferred
import org.avmedia.gshockapi.HealthData
import org.avmedia.gshockapi.ble.GetSetMode
import org.avmedia.gshockapi.casio.CasioConstants
import org.avmedia.gshockapi.utils.Utils
import timber.log.Timber

object HealthDataIO {

    private data class State(val deferredResult: CompletableDeferred<HealthData>? = null)

    private var state = State()

    suspend fun request(): HealthData {
        return CachedIO.request("HEALTH_DATA") {
            state = state.copy(deferredResult = CompletableDeferred())
            IO.writeCmd(
                    GetSetMode.GET,
                    byteArrayOf(CasioConstants.CHARACTERISTICS.CASIO_HEALTH_DATA.code.toByte())
            )
            state.deferredResult?.await() ?: HealthData(0, 0, 0, 0, "")
        }
    }

    fun onReceived(data: String) {
        state.deferredResult?.complete(HealthDataDecoder.decode(data))
        state = State()
    }

    fun sendToWatch(data: HealthData) {
        // Placeholder for sending health data to the watch if needed
        // Implementing based on pattern, though specific command format is unknown
        // Assuming we might send it similarly to notifications or just log it for now
        Timber.d("HealthDataIO: Sending health data to watch: $data")
    }

    object HealthDataDecoder {
        fun decode(data: String): HealthData {
            val intArr = Utils.toIntArray(data)
            // Example decoding - needs adjustment based on actual byte structure
            // Assuming: [Key, Steps(2), Calories(2), HeartRate(1), Distance(2),
            // Timestamp(String...)]

            if (intArr.size < 8) return HealthData(0, 0, 0, 0, "")

            // Placeholder decoding logic
            val steps = (intArr[1] shl 8) + intArr[2]
            val calories = (intArr[3] shl 8) + intArr[4]
            val heartRate = intArr[5]
            val distance = (intArr[6] shl 8) + intArr[7]
            val timestamp = "" // Parse remaining bytes as string if needed

            return HealthData(steps, calories, heartRate, distance, timestamp)
        }
    }
}
