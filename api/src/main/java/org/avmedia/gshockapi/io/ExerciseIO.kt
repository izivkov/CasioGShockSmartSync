package org.avmedia.gshockapi.io

import CachedIO
import android.os.Build
import androidx.annotation.RequiresApi
import kotlinx.coroutines.CompletableDeferred
import org.avmedia.gshockapi.ble.GetSetMode
import org.avmedia.gshockapi.casio.CasioConstants
import org.avmedia.gshockapi.model.ExerciseProfile
import org.avmedia.gshockapi.utils.Utils
import org.json.JSONObject

@RequiresApi(Build.VERSION_CODES.O)
object ExerciseIO {
    private const val OPCODE = 0x2D
    private const val CACHE_KEY = "GET_EXERCISE_PROFILE"

    data class State(
        val deferredResult: CompletableDeferred<ExerciseProfile>? = null,
        val lastProfile: ExerciseProfile = ExerciseProfile(),
    )

    private var state = State()

    fun encode(profile: ExerciseProfile): ByteArray {
        val stepGoalBytes = toBcdLe(profile.stepGoal, 4)
        val heightBytes = invert(toBcdLe(profile.height, 2))
        val weightBytes = invert(toBcdLe(profile.weight, 2))

        val payload = ByteArray(1 + 1 + stepGoalBytes.size + heightBytes.size + weightBytes.size)
        payload[0] = OPCODE.toByte()
        payload[1] = 0
        stepGoalBytes.copyInto(payload, 2)
        heightBytes.copyInto(payload, 2 + stepGoalBytes.size)
        weightBytes.copyInto(payload, 2 + stepGoalBytes.size + heightBytes.size)
        return payload
    }

    fun decode(payload: ByteArray): ExerciseProfile {
        require(payload.isNotEmpty() && payload[0].toInt() == OPCODE) { "not an exercise payload" }
        val stepGoal = bcdLe(payload.copyOfRange(2, 6))
        val height = bcdLe(invert(payload.copyOfRange(6, 8)))
        val weight = bcdLe(invert(payload.copyOfRange(8, 10)))
        return ExerciseProfile(stepGoal = stepGoal, height = height, weight = weight)
    }

    fun toJson(profile: ExerciseProfile): JSONObject = JSONObject()
        .put("stepGoal", profile.stepGoal)
        .put("height", profile.height)
        .put("weight", profile.weight)

    suspend fun request(): ExerciseProfile = CachedIO.request(CACHE_KEY) {
        val deferred = CompletableDeferred<ExerciseProfile>()
        synchronized(this) {
            state = state.copy(deferredResult = deferred)
        }
        IO.writeCmd(GetSetMode.GET, Utils.byteArray(CasioConstants.CHARACTERISTICS.CASIO_SETTING_FOR_EXERCISE.code.toByte()))
        deferred.await()
    }

    fun set(profile: ExerciseProfile) {
        synchronized(this) {
            state = state.copy(lastProfile = profile)
        }
        CachedIO.set(CACHE_KEY) {
            IO.writeCmd(GetSetMode.SET, encode(profile))
        }
    }

    fun sendToWatch(message: String) {
        IO.writeCmd(GetSetMode.GET, Utils.byteArray(CasioConstants.CHARACTERISTICS.CASIO_SETTING_FOR_EXERCISE.code.toByte()))
    }

    fun sendToWatchSet(message: String) {
        val value = JSONObject(message).optJSONObject("value") ?: JSONObject()
        val current = state.lastProfile
        val profile = ExerciseProfile(
            stepGoal = value.optInt("stepGoal", current.stepGoal),
            height = value.optInt("height", current.height),
            weight = value.optInt("weight", current.weight),
        )
        set(profile)
    }

    fun onReceived(data: String) {
        val payload = Utils.toIntArray(data)
            .map { it.toByte() }
            .toByteArray()
        val profile = decode(payload)
        synchronized(this) {
            state = state.copy(lastProfile = profile)
            val deferred = state.deferredResult
            state = state.copy(deferredResult = null)
            deferred?.complete(profile)
        }
    }

    private fun toBcdLe(value: Int, length: Int): ByteArray {
        var remaining = value.coerceAtLeast(0)
        val output = ByteArray(length)
        for (index in 0 until length) {
            val current = remaining % 100
            remaining /= 100
            val packed = ((current / 10) * 16) + (current % 10)
            output[index] = packed.toByte()
        }
        return output
    }

    private fun bcdLe(bytes: ByteArray): Int {
        var total = 0
        var multiplier = 1
        for (byte in bytes) {
            val unsignedValue = if (byte.toInt() < 0) byte.toInt() + 256 else byte.toInt()
            val highDigit = unsignedValue / 16
            val lowDigit = unsignedValue % 16
            total += ((highDigit * 10) + lowDigit) * multiplier
            multiplier *= 100
        }
        return total
    }

    private fun invert(bytes: ByteArray): ByteArray {
        val output = ByteArray(bytes.size)
        for (index in bytes.indices) {
            val unsignedValue = if (bytes[index].toInt() < 0) bytes[index].toInt() + 256 else bytes[index].toInt()
            output[index] = (255 - unsignedValue).toByte()
        }
        return output
    }
}
