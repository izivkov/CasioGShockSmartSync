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
        if (WatchInfo.hasTimeFormat) {
            settings.timeFormat = if (settingArray[1] and MASK_24_HOURS != 0) "24h" else "12h"
        } else {
            settings.timeFormat = "24h" // Default
        }

        // Button tone and vibration
        if (!WatchInfo.vibrate) {
            settings.buttonTone = settingArray[1] and MASK_BUTTON_TONE_OFF == 0
        } else if (settingType == SettingType.SHORT) {
            settings.buttonTone = settingArray[1] and MASK_BUTTON_TONE_OFF == 0
        } else {
            settings.buttonTone = settingArray[12] and SOUND_ONLY != 0
            settings.keyVibration = settingArray[12] and VIBRATION_ONLY != 0
            settings.hourlyChime = settingArray[12] and CHIME != 0
        }

        // Flags from byte 1
        if (WatchInfo.hasAutoLight) {
            settings.autoLight = settingArray[1] and MASK_AUTO_LIGHT_OFF == 0
        }
        settings.powerSavingMode = settingArray[1] and POWER_SAVING_MODE == 0
        if (WatchInfo.findButtonUserDefined) { // DnD is usually on watches with user defined buttons
            settings.DnD = settingArray[1] and DO_NOT_DISTURB_OFF == 0
        }

        // Date format (byte 4)
        if (WatchInfo.hasDateFormat) {
            settings.dateFormat = if (settingArray[4] == 1) "DD:MM" else "MM:DD"
        }

        // Language (byte 5)
        if (WatchInfo.weekLanguageSupported) {
            settings.language = when (settingArray[5]) {
                0 -> "English"
                1 -> "Spanish"
                2 -> "French"
                3 -> "German"
                4 -> "Italian"
                5 -> "Russian"
                else -> "English"
            }
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
     * Returns 17-byte array (or 12-byte for shorter modules) containing all settings encoded per protocol.
     */
    fun encode(settings: JSONObject): ByteArray {
        val size = WatchInfo.settingsSize
        val arr = ByteArray(size)

        // Command code
        arr[0] = CasioConstants.CHARACTERISTICS.CASIO_SETTING_FOR_BASIC.code.toByte()

        // Byte 1 - Multiple flags
        if (!WatchInfo.hasTimeFormat) {
            arr[1] = (arr[1] or MASK_24_HOURS.toByte()) // Always 24h if no format setting
        } else if (settings.get("timeFormat") == "24h") {
            arr[1] = (arr[1] or MASK_24_HOURS.toByte())
        }

        // Button tone and vibration settings
        if (settings.get("buttonTone") == false) {
            arr[1] = (arr[1] or MASK_BUTTON_TONE_OFF.toByte())
            if (size == 17) {
                arr[12] = (arr[12] and SOUND_ONLY.inv().toByte())
            }
        } else {
            if (size == 17) {
                arr[12] = (arr[12] or SOUND_ONLY.toByte())
            }
        }

        if (size == 17) {
            if (settings.get("keyVibration") == true) {
                arr[12] = (arr[12] or VIBRATION_ONLY.toByte())
            }

            if (settings.get("hourlyChime") == true) {
                arr[12] = (arr[12] or CHIME.toByte())
            }
        }

        // Additional byte 1 flags
        if (WatchInfo.hasAutoLight) {
            if (settings.get("autoLight") == false) {
                arr[1] = (arr[1] or MASK_AUTO_LIGHT_OFF.toByte())
            }
        }

        if (settings.get("powerSavingMode") == false) {
            arr[1] = (arr[1] or POWER_SAVING_MODE.toByte())
        }

        if (WatchInfo.findButtonUserDefined) {
            if (settings.get("DnD") == false) {
                arr[1] = (arr[1] or DO_NOT_DISTURB_OFF.toByte())
            }
        }

        // Byte 2 - Light duration flags
        var flags = RESET_VALUE
        if (settings["lightDuration"] == "4s" || settings["lightDuration"] == "3s") {
            flags = flags or LIGHT_DURATION_LONG
        }
        arr[2] = flags.toByte()

        // Byte 8 - Font flags
        if (size == 17) {
            var fontFlags = RESET_VALUE
            if (WatchInfo.hasMultipleFonts && settings["font"] == "Classic") {
                fontFlags = fontFlags or FONT_CLASSIC_MASK
            }
            arr[8] = fontFlags.toByte()
        }

        // Byte 4 - Date format
        if (WatchInfo.hasDateFormat) {
            if (settings.get("dateFormat") == "DD:MM") arr[4] = 1
        }

        // Byte 5 - Language
        if (WatchInfo.weekLanguageSupported) {
            arr[5] = when (settings.get("language")) {
                "English" -> 0
                "Spanish" -> 1
                "French" -> 2
                "German" -> 3
                "Italian" -> 4
                "Russian" -> 5
                else -> 0
            }.toByte()
        }

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
        val deferred = CompletableDeferred<Settings>()
        synchronized(this) {
            state = state.copy(deferredResult = deferred)
        }
        Connection.sendMessage("{ action: '$key'}")
        return deferred.await()
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
                    synchronized(this) {
                        state.deferredResult?.complete(model)
                        state = state.copy(deferredResult = null)
                    }
                },
                onFailure = { error ->
                    Timber.e("Failed to decode settings: ${error.message}")
                    synchronized(this) {
                        state.deferredResult?.completeExceptionally(error)
                        state = state.copy(deferredResult = null)
                    }
                }
            )
    }

    fun onRunError() {
        synchronized(this) {
            state.deferredResult?.complete(Settings())
            state = state.copy(deferredResult = null)
        }
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
