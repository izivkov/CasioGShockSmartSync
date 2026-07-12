package org.avmedia.gshockapi.io

import CachedIO
import android.os.Build
import androidx.annotation.RequiresApi
import com.google.gson.Gson
import kotlinx.coroutines.CompletableDeferred
import org.avmedia.gshockapi.Settings
import org.avmedia.gshockapi.WatchInfo
import org.avmedia.gshockapi.ble.Connection
import org.avmedia.gshockapi.ble.GetSetMode
import org.avmedia.gshockapi.casio.CasioConstants
import org.avmedia.gshockapi.utils.Utils
import org.json.JSONObject
import timber.log.Timber
import kotlin.experimental.and
import kotlin.experimental.inv
import kotlin.experimental.or

// ============================================================================
// Pure Functional Core: Settings Decoding & Encoding
// ============================================================================

/**
 * Pure functional core for settings processing.
 * 
 * All methods are pure: no mutable state, no side effects.
 * Handles all bitwise operations and transformations deterministically.
 */
@RequiresApi(Build.VERSION_CODES.O)
object SettingsIOFunctional {
    // Constants for bit masks (all preserved from original)
    private const val MASK_24_HOURS = 0b00000001
    private const val MASK_BUTTON_TONE_OFF = 0b00000010
    private const val MASK_AUTO_LIGHT_OFF = 0b00000100
    private const val POWER_SAVING_MODE = 0b00010000
    private const val DO_NOT_DISTURB_OFF = 0b01000000

    private const val LIGHT_DURATION_LONG = 0b00000001
    private const val RESET_VALUE = 0
    private const val FONT_CLASSIC_MASK = 0x20

    // Button tone and vibration settings (DW-H5600 specific)
    private const val SOUND_AND_VIBRATION = 0b1100 // Both sound and vibration (0xC)
    private const val VIBRATION_ONLY = 0b1000 // Vibration only (0x8)
    private const val SOUND_ONLY = 0b0100 // Sound only (0x4)
    private const val SILENT = 0b0000 // silent (0x0)

    private const val CHIME = 0b00100000

    enum class SettingType {
        SHORT,
        EXTENDED
    }

    /**
     * Pure decoder: Converts hex array to Settings model.
     * 
     * Extracts all settings from the raw byte array using bit masks.
     * No side effects - pure transformation.
     * 
     * Protocol format (17 bytes total for extended, 12 for short):
     * [0] - Command code (0x13)
     * [1] - Byte 1 (contains multiple flags)
     * [2] - Light duration and other flags
     * [4] - Date format (1 = DD:MM, 0 = MM:DD)
     * [5] - Language (0-5 for English, Spanish, French, German, Italian, Russian)
     * [8] - Font flags (0x20 = Classic)
     * [12] - Sound/vibration settings (DW-H5600 specific)
     */
    fun decode(settingString: String): Result<Settings> = runCatching {
        val settings = Settings()
        val settingArray = Utils.toIntArray(settingString)
        val settingType = if (settingArray.size == 17) SettingType.EXTENDED else SettingType.SHORT

        // Time format (bit 0 of byte 1)
        settings.timeFormat = if (settingArray[1] and MASK_24_HOURS != 0) "24h" else "12h"

        // Button tone and vibration
        if (settingType == SettingType.SHORT) {
            settings.buttonTone = settingArray[1] and MASK_BUTTON_TONE_OFF == 0
        } else {
            settings.buttonTone = settingArray[12] and SOUND_ONLY != 0
            settings.keyVibration = settingArray[12] and VIBRATION_ONLY != 0
            settings.hourlyChime = settingArray[12] and CHIME != 0
        }

        // Flags from byte 1
        settings.autoLight = settingArray[1] and MASK_AUTO_LIGHT_OFF == 0
        settings.powerSavingMode = settingArray[1] and POWER_SAVING_MODE == 0
        settings.DnD = settingArray[1] and DO_NOT_DISTURB_OFF == 0

        // Date format (byte 4)
        settings.dateFormat = if (settingArray[4] == 1) "DD:MM" else "MM:DD"

        // Language (byte 5)
        settings.language = when (settingArray[5]) {
            0 -> "English"
            1 -> "Spanish"
            2 -> "French"
            3 -> "German"
            4 -> "Italian"
            5 -> "Russian"
            else -> "English"
        }

        // Light duration (bit 0 of byte 2)
        val flags = settingArray[2]
        settings.lightDuration = if ((flags and LIGHT_DURATION_LONG) != 0) "4s" else "2s"

        // Font (byte 8)
        if (WatchInfo.hasMultipleFonts) {
            settings.font = if (settingArray[8] and FONT_CLASSIC_MASK != 0) "Classic" else "Standard"
        }

        settings
    }

    /**
     * Pure encoder: Converts Settings model to byte array.
     * 
     * Constructs the raw byte array with all necessary bit flags.
     * No side effects - pure transformation.
     * 
     * Returns 17-byte array containing all settings encoded per protocol.
     */
    fun encode(settings: JSONObject): ByteArray {
        val arr = ByteArray(17)

        // Command code
        arr[0] = CasioConstants.CHARACTERISTICS.CASIO_SETTING_FOR_BASIC.code.toByte()

        // Byte 1 - Multiple flags
        if (settings.get("timeFormat") == "24h") {
            arr[1] = (arr[1] or MASK_24_HOURS.toByte())
        }

        // Button tone and vibration settings
        if (settings.get("buttonTone") == false) {
            arr[1] = (arr[1] or MASK_BUTTON_TONE_OFF.toByte())
            arr[12] = (arr[12] and SOUND_ONLY.inv().toByte())
        } else {
            arr[12] = (arr[12] or SOUND_ONLY.toByte())
        }

        if (settings.get("keyVibration") == true) {
            arr[12] = (arr[12] or VIBRATION_ONLY.toByte())
        }

        if (settings.get("hourlyChime") == true) {
            arr[12] = (arr[12] or CHIME.toByte())
        }

        // Additional byte 1 flags
        if (settings.get("autoLight") == false) {
            arr[1] = (arr[1] or MASK_AUTO_LIGHT_OFF.toByte())
        }

        if (settings.get("powerSavingMode") == false) {
            arr[1] = (arr[1] or POWER_SAVING_MODE.toByte())
        }

        if (settings.get("DnD") == false) {
            arr[1] = (arr[1] or DO_NOT_DISTURB_OFF.toByte())
        }

        // Byte 2 - Light duration flags
        var flags = RESET_VALUE
        if (settings["lightDuration"] == "4s") {
            flags = flags or LIGHT_DURATION_LONG
        }
        arr[2] = flags.toByte()

        // Byte 8 - Font flags
        var fontFlags = RESET_VALUE
        if (WatchInfo.hasMultipleFonts && settings["font"] == "Classic") {
            fontFlags = fontFlags or FONT_CLASSIC_MASK
        }
        arr[8] = fontFlags.toByte()

        // Byte 4 - Date format
        if (settings.get("dateFormat") == "DD:MM") arr[4] = 1

        // Byte 5 - Language
        arr[5] = when (settings.get("language")) {
            "English" -> 0
            "Spanish" -> 1
            "French" -> 2
            "German" -> 3
            "Italian" -> 4
            "Russian" -> 5
            else -> 0
        }.toByte()

        return arr
    }

    /**
     * Pure command builder: Creates fetch command for settings.
     */
    fun buildFetchCommand(): ByteArray =
        Utils.byteArray(
            CasioConstants.CHARACTERISTICS.CASIO_SETTING_FOR_BASIC.code.toByte()
        )
}

// ============================================================================
// Imperative Shell: Side Effects & State Management
// ============================================================================

/**
 * Settings IO handler with state management.
 * 
 * Manages the asynchronous request/response cycle for settings data.
 * Uses pure functional core for all transformations.
 * 
 * Protocol reference (kept for documentation):
 * Time Format:
 *     24 h:   13 05 00 00 00 00 00 00 00 00 00 00
 *     12 h:   13 04 00 00 00 00 00 00 00 00 00 00
 * 
 * Date format:
 *     mm:dd   13 04 00 00 00 00 00 00 00 00 00 00
 *     dd:mm   13 04 00 00 01 00 00 00 00 00 00 00
 * 
 * Languages:
 *     english:13 04 00 00 00 00 00 00 00 00 00 00
 *     spanish:13 04 00 00 00 01 00 00 00 00 00 00
 *     fr:     13 04 00 00 00 02 00 00 00 00 00 00
 *     german: 13 04 00 00 00 03 00 00 00 00 00 00
 *     italian:13 04 00 00 00 04 00 00 00 00 00 00
 *     russian:13 04 00 00 00 05 00 00 00 00 00 00
 * 
 * Button Tone:
 *     on:     13 04 00 00 00 00 00 00 00 00 00 00
 *     off:    13 06 00 00 00 00 00 00 00 00 00 00
 * 
 * For GMW-BZ5000
 *     standard font:          13 05 01 00 01 00 00 00 00 00 00 00
 *     classic font:           13 05 00 00 01 00 00 00 20 00 00 00
 *     light duration 1.5s:    13 05 00 00 01 00 00 00 20 00 00 00
 *     light duration 3s:      13 05 01 00 01 00 00 00 20 00 00 00
 * 
 * For DW-H5600
 *     sound:          13 00 00 00 00 00 00 00 00 00 00 00 04 00 00 06 00
 *     vibrate:        13 00 00 00 00 00 00 00 00 00 00 00 08 00 00 06 00
 *     both:           13 00 00 00 00 00 00 00 00 00 00 00 0c 00 00 06 00
 *     silent:         13 04 00 00 00 00 00 00 00 00 00 00 00 00 00 06 2d
 * 
 * Auto Light:
 *     on:     13 00 00 00 00 00 00 00 00 00 00 00
 *     off:    13 04 00 00 00 00 00 00 00 00 00 00
 * 
 * Light Duration:
 *     2s      13 04 00 00 00 00 00 00 00 00 00 00
 *     4s      13 04 01 00 00 00 00 00 00 00 00 00
 * 
 * Power Saving:
 *     on      13 04 00 00 00 00 00 00 00 00 00 00
 *     off     13 14 00 00 00 00 00 00 00 00 00 00
 */
@RequiresApi(Build.VERSION_CODES.O)
object SettingsIO {
    private data class State(val deferredResult: CompletableDeferred<Settings>? = null)

    private var state = State()

    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun request(): Settings =
        CachedIO.request("GET_SETTINGS") { key -> getBasicSettings(key) }

    @RequiresApi(Build.VERSION_CODES.O)
    private suspend fun getBasicSettings(key: String): Settings {
        state = state.copy(deferredResult = CompletableDeferred())
        Connection.sendMessage("{ action: '$key'}")
        return state.deferredResult?.await() ?: error("No deferred result available")
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun set(settings: Settings) {
        settings.let { Gson().toJson(it) }.let { settingJson ->
            CachedIO.set("GET_SETTINGS") {
                Connection.sendMessage("{action: \"SET_SETTINGS\", value: $settingJson}")
            }
        }
    }

    fun onReceived(data: String) {
        // Use pure function to decode
        SettingsIOFunctional.decode(data)
            .fold(
                onSuccess = { model ->
                    state.deferredResult?.complete(model)
                    state = State() // Reset state after completion
                },
                onFailure = { error ->
                    Timber.e("Failed to decode settings: ${error.message}")
                    state.deferredResult?.completeExceptionally(error)
                    state = State()
                }
            )
    }

    @Suppress("UNUSED_PARAMETER")
    fun sendToWatch(message: String) {
        // Use pure function to build command, then execute
        IO.writeCmd(
            GetSetMode.GET,
            SettingsIOFunctional.buildFetchCommand()
        )
    }

    fun sendToWatchSet(message: String) {
        // Use pure function to encode, then execute
        val settings = JSONObject(message).get("value") as JSONObject
        IO.writeCmd(GetSetMode.SET, SettingsIOFunctional.encode(settings))
    }
}
