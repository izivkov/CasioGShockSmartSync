package org.avmedia.gshockapi.io

import CachedIO
import android.os.Build
import androidx.annotation.RequiresApi
import kotlinx.coroutines.CompletableDeferred
import org.avmedia.gshockapi.WatchInfo
import org.avmedia.gshockapi.ble.Connection
import org.avmedia.gshockapi.ble.GetSetMode
import org.avmedia.gshockapi.casio.CasioConstants
import org.avmedia.gshockapi.model.ExerciseProfile
import org.avmedia.gshockapi.model.ExerciseUnits
import org.avmedia.gshockapi.utils.Utils
import org.json.JSONObject
import kotlin.math.roundToInt

private const val CM_PER_INCH = 2.54
private const val KG_PER_LB = 0.45359237
private const val CACHE_KEY = "GET_EXERCISE_PROFILE"
private const val DEFAULT_STEP_GOAL = 10000
private const val DEFAULT_HEIGHT_CM = 170
private const val DEFAULT_WEIGHT_KG = 70

/** Height and weight in the metric units the watch uses (cm and kg). */
private data class WatchBody(val heightCm: Int, val weightKg: Int)

/** Converts the profile's entered values to the metric values the watch expects. */
private fun ExerciseProfile.toWatchBody(): WatchBody =
    if (units == ExerciseUnits.IMPERIAL) {
        WatchBody(
            heightCm = (height * CM_PER_INCH).roundToInt(),
            weightKg = (weight * KG_PER_LB).roundToInt(),
        )
    } else {
        WatchBody(heightCm = height, weightKg = weight)
    }

/** Converts metric watch values back to the given unit system's display values. */
private fun WatchBody.toDisplay(units: ExerciseUnits): Pair<Int, Int> =
    if (units == ExerciseUnits.IMPERIAL) {
        Pair(
            (heightCm / CM_PER_INCH).roundToInt(),
            (weightKg / KG_PER_LB).roundToInt(),
        )
    } else {
        Pair(heightCm, weightKg)
    }

/** The device stores each body value in one unsigned byte. */
private fun Int.toWireByte(): Byte = coerceIn(0, 255).toByte()

@RequiresApi(Build.VERSION_CODES.O)
object ExerciseIOFunctional {

    fun encode(profile: ExerciseProfile): ByteArray {
        val body = profile.toWatchBody()
        val goal = profile.stepGoal.coerceIn(0, 0xFFFF)

        return ByteArray(7).apply {
            this[0] = CasioConstants.CHARACTERISTICS.CASIO_SETTING_FOR_EXERCISE.code.toByte()
            this[1] = (goal and 0xFF).toByte()
            this[2] = ((goal ushr 8) and 0xFF).toByte()
            this[3] = body.heightCm.toWireByte()
            this[4] = body.weightKg.toWireByte()
            // Bytes 5 and 6 are unused and stay zero.
        }
    }

    fun decode(raw: ByteArray, units: ExerciseUnits): ExerciseProfile {
        val values = raw.map { it.toInt() and 0xFF }
        val (height, weight) = WatchBody(
            heightCm = values.getOrElse(3) { 0 },
            weightKg = values.getOrElse(4) { 0 },
        ).toDisplay(units)

        return ExerciseProfile(
            stepGoal = values.getOrElse(1) { 0 } + (values.getOrElse(2) { 0 } shl 8),
            height = height,
            weight = weight,
            units = units,
        )
    }

    fun decode(raw: String, units: ExerciseUnits): ExerciseProfile =
        decode(Utils.toIntArray(raw).map { it.toByte() }.toByteArray(), units)

    fun toJson(profile: ExerciseProfile): JSONObject = JSONObject().apply {
        put("stepGoal", profile.stepGoal)
        put("height", profile.height)
        put("weight", profile.weight)
        put("units", profile.units.name.lowercase())
    }
}

@RequiresApi(Build.VERSION_CODES.O)
object ExerciseIO {
    private data class State(
        val deferredResult: CompletableDeferred<ExerciseProfile>? = null,
        val lastProfile: ExerciseProfile? = null,
    )

    private val lock = Any()
    private var state = State()

    /** The profile the app last read or wrote, or a metric default if there is none yet. */
    private fun currentProfile(): ExerciseProfile =
        synchronized(lock) { state.lastProfile } ?: ExerciseProfile(
            stepGoal = DEFAULT_STEP_GOAL,
            height = DEFAULT_HEIGHT_CM,
            weight = DEFAULT_WEIGHT_KG,
            units = ExerciseUnits.METRIC,
        )

    suspend fun request(): ExerciseProfile {
        if (!WatchInfo.hasExerciseProfile) {
            return currentProfile()
        }

        return CachedIO.request(CACHE_KEY) { key ->
            val deferred = CompletableDeferred<ExerciseProfile>()
            synchronized(lock) {
                state = state.copy(deferredResult = deferred)
            }
            Connection.sendMessage("{ action: '$key'}")
            deferred.await()
        }
    }

    fun set(profile: ExerciseProfile) {
        synchronized(lock) {
            state = state.copy(lastProfile = profile)
        }

        // Replace the cached GET result so the next read doesn't return the old profile.
        CachedIO.remove(CACHE_KEY)
        CachedIO.put(CACHE_KEY, profile)

        IO.writeCmd(GetSetMode.SET, ExerciseIOFunctional.encode(profile))
    }

    fun onReceived(data: String) {
        val profile = ExerciseIOFunctional.decode(data, currentProfile().units)

        val deferred = synchronized(lock) {
            state = state.copy(lastProfile = profile)
            val pending = state.deferredResult
            state = state.copy(deferredResult = null)
            pending
        }
        deferred?.complete(profile)
    }

    fun sendToWatch(@Suppress("UNUSED_PARAMETER") message: String) {
        IO.writeCmd(
            GetSetMode.GET,
            byteArrayOf(CasioConstants.CHARACTERISTICS.CASIO_SETTING_FOR_EXERCISE.code.toByte())
        )
    }

    /**
     * Accepts either profile-unit keys (height, weight) or metric keys (heightCm, weightKg).
     * Metric input is converted to the stored profile's units before it is saved.
     */
    fun sendToWatchSet(message: String) {
        val json = JSONObject(message)
        val value = json.optJSONObject("value") ?: json
        val previous = currentProfile()
        val previousBody = previous.toWatchBody()

        val stepGoal = value.optInt("stepGoal", value.optInt("step_goal", previous.stepGoal))

        val profile = if (value.has("heightCm") || value.has("weightKg")) {
            val (height, weight) = WatchBody(
                heightCm = value.optInt("heightCm", previousBody.heightCm),
                weightKg = value.optInt("weightKg", previousBody.weightKg),
            ).toDisplay(previous.units)
            ExerciseProfile(stepGoal = stepGoal, height = height, weight = weight, units = previous.units)
        } else {
            ExerciseProfile(
                stepGoal = stepGoal,
                height = value.optInt("height", previous.height),
                weight = value.optInt("weight", previous.weight),
                units = previous.units,
            )
        }

        set(profile)
    }

    fun encode(profile: ExerciseProfile): ByteArray = ExerciseIOFunctional.encode(profile)

    fun decode(raw: ByteArray, units: ExerciseUnits): ExerciseProfile =
        ExerciseIOFunctional.decode(raw, units)

    fun toJson(profile: ExerciseProfile): JSONObject = ExerciseIOFunctional.toJson(profile)
}
