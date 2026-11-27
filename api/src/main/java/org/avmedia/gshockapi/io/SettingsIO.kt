package org.avmedia.gshockapi.io

import CachedIO
import android.os.Build
import androidx.annotation.RequiresApi
import com.google.gson.Gson
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.avmedia.gshockapi.WatchInfo
import org.avmedia.gshockapi.ble.Connection
import org.avmedia.gshockapi.ble.GetSetMode
import org.avmedia.gshockapi.casio.CasioConstants
import org.avmedia.gshockapi.model.Settings
import org.avmedia.gshockapi.model.SmartAlarm
import org.avmedia.gshockapi.model.SoundMode
import org.avmedia.gshockapi.model.WatchFaceItem
import org.avmedia.gshockapi.utils.Utils
import org.json.JSONObject
import timber.log.Timber
import kotlin.experimental.and
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
        EXTENDED,
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
    fun decode(settingString: String): Result<Settings> =
        runCatching {
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
                settings.language =
                    when (settingArray[5]) {
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
            arr[5] =
                when (settings.get("language")) {
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
     * The frame bytes ([0] is the code) that [applyTo] may change: the flags of byte 1, the light byte 2, the date
     * format 4, the language 5, the font 8 and the sound and vibration byte 12.
     */
    val OWNED_BYTES: Set<Int> = setOf(1, 2, 4, 5, 8, 12)

    /**
     * Pure: [frame] (as read from the watch) with the fields present in [settings] put in, and nothing else changed.
     * [encode] builds a whole frame from zeros, so bytes the settings model does not own are lost: on a DW-H5600 the
     * low bits of byte 12 (length unit, step reminder) and bytes 13 to 16 (night window) would be reset. A field that
     * [settings] (the settings model by field name) does not carry keeps the value of [frame].
     */
    fun applyTo(
        frame: IntArray,
        settings: Map<String, Any?>,
    ): IntArray {
        val out = frame.copyOf()
        val extended = out.size == 17

        fun flag(
            index: Int,
            mask: Int,
            on: Boolean,
        ) {
            out[index] = if (on) out[index] or mask else out[index] and mask.inv()
        }

        fun bool(name: String): Boolean? = settings[name] as? Boolean

        fun text(name: String): String? = settings[name] as? String

        // Byte 1: a set bit means "off" for tone, auto light, power saving and do not disturb, "on" for 24 hours.
        if (!WatchInfo.hasTimeFormat) {
            flag(1, MASK_24_HOURS, true)
        } else {
            text("timeFormat")?.let { flag(1, MASK_24_HOURS, it == "24h") }
        }
        bool("buttonTone")?.let { on ->
            flag(1, MASK_BUTTON_TONE_OFF, !on)
            if (extended) flag(12, SOUND_ONLY, on)
        }
        if (extended) {
            bool("keyVibration")?.let { flag(12, VIBRATION_ONLY, it) }
            bool("hourlyChime")?.let { flag(12, CHIME, it) }
        }
        if (WatchInfo.hasAutoLight) bool("autoLight")?.let { flag(1, MASK_AUTO_LIGHT_OFF, !it) }
        bool("powerSavingMode")?.let { flag(1, POWER_SAVING_MODE, !it) }
        if (WatchInfo.findButtonUserDefined) bool("DnD")?.let { flag(1, DO_NOT_DISTURB_OFF, !it) }

        // Byte 2: bit 0 is the long light duration; the other bits stay.
        text("lightDuration")?.let { flag(2, LIGHT_DURATION_LONG, it == "4s" || it == "3s") }

        if (extended && WatchInfo.hasMultipleFonts) text("font")?.let { flag(8, FONT_CLASSIC_MASK, it == "Classic") }
        if (WatchInfo.hasDateFormat) text("dateFormat")?.let { out[4] = if (it == "DD:MM") 1 else 0 }
        if (WatchInfo.weekLanguageSupported) {
            text("language")?.let {
                out[5] =
                    when (it) {
                        "English" -> 0
                        "Spanish" -> 1
                        "French" -> 2
                        "German" -> 3
                        "Italian" -> 4
                        "Russian" -> 5
                        else -> 0
                    }
            }
        }
        return out
    }

    /**
     * Pure command builder: Creates fetch command for settings.
     */
    fun buildFetchCommand(): ByteArray =
        Utils.byteArray(
            CasioConstants.CHARACTERISTICS.CASIO_SETTING_FOR_BASIC.code.toByte(),
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

    /**
     * DW-H5600: its settings are read and written by several watch exchanges each, in coroutines. They run one at a
     * time, in the order the calls were made (a coroutine started undispatched queues on the lock in call order), so
     * a read sent right after a save cannot reach the watch before the save and bring back the old values.
     */
    private val h5600Gate = Mutex()

    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun request(): Settings = CachedIO.request("GET_SETTINGS") { key -> getBasicSettings(key) }

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
                },
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
        if (H5600SettingsIO.isSupported()) {
            CoroutineScope(Dispatchers.IO).launch(start = CoroutineStart.UNDISPATCHED) {
                h5600Gate.withLock {
                    // The basic settings (0x13) first: time format, light, tone, vibration, chime, do not disturb...
                    // Without them the request fails, so no half-filled settings are cached as the watch's.
                    val settings =
                        runCatching { H5600SettingsIO.getBasicSettings() }.getOrElse { error ->
                            Timber.e(error, "Failed to read the basic settings (0x13)")
                            synchronized(this@SettingsIO) {
                                state.deferredResult?.completeExceptionally(error)
                                state = state.copy(deferredResult = null)
                            }
                            return@withLock
                        }
                    runCatching { settings.soundMode = H5600SettingsIO.getSoundMode()?.name }
                        .onFailure { Timber.w(it, "Failed to read soundMode") }
                    runCatching { settings.watchFaceNumber = H5600SettingsIO.getWatchFaceNumber() }
                        .onFailure { Timber.w(it, "Failed to read watchFaceNumber") }
                    runCatching { settings.watchFaceItem = H5600SettingsIO.getWatchFaceItem()?.name }
                        .onFailure { Timber.w(it, "Failed to read watchFaceItem") }
                    runCatching { settings.smartAlarm = H5600SettingsIO.getSmartAlarm() }
                        .onFailure { Timber.w(it, "Failed to read smartAlarm") }

                    synchronized(this@SettingsIO) {
                        state.deferredResult?.complete(settings)
                        state = state.copy(deferredResult = null)
                    }
                }
            }
            return
        }

        // Use pure function to build command, then execute
        IO.writeCmd(
            GetSetMode.GET,
            SettingsIOFunctional.buildFetchCommand(),
        )
    }

    fun sendToWatchSet(message: String) {
        val jsonObj = JSONObject(message)
        val valueObj = jsonObj.optJSONObject("value") ?: jsonObj

        if (H5600SettingsIO.isSupported()) {
            CoroutineScope(Dispatchers.IO).launch(start = CoroutineStart.UNDISPATCHED) {
                h5600Gate.withLock {
                    runCatching {
                        // The basic settings (0x13): only the bits the settings model owns are changed.
                        H5600SettingsIO.setBasicSettings(valueObj)
                    }.onFailure { Timber.e(it, "Failed to set the basic settings (0x13)") }
                    runCatching {
                        val soundModeStr = valueObj.optString("soundMode", "")
                        if (soundModeStr.isNotEmpty()) {
                            SoundMode.entries.firstOrNull { it.name.equals(soundModeStr, ignoreCase = true) }?.let { mode ->
                                H5600SettingsIO.setSoundMode(mode)
                            }
                        }
                        if (valueObj.has("watchFaceNumber")) {
                            val faceNum = valueObj.getInt("watchFaceNumber")
                            H5600SettingsIO.setWatchFaceNumber(faceNum)
                        }
                        val itemStr = valueObj.optString("watchFaceItem", "")
                        if (itemStr.isNotEmpty()) {
                            WatchFaceItem.entries.firstOrNull { it.name.equals(itemStr, ignoreCase = true) }?.let { item ->
                                H5600SettingsIO.setWatchFaceItem(item)
                            }
                        }
                        if (valueObj.has("smartAlarm")) {
                            val saJson = valueObj.optJSONObject("smartAlarm")
                            if (saJson != null) {
                                val smartAlarm =
                                    SmartAlarm(
                                        hour = saJson.optInt("hour", 0),
                                        minute = saJson.optInt("minute", 0),
                                        enabled = saJson.optBoolean("enabled", false),
                                        snooze = saJson.optBoolean("snooze", false),
                                        windowMin = saJson.optInt("windowMin", 0),
                                    )
                                H5600SettingsIO.setSmartAlarm(smartAlarm)
                            }
                        }
                    }.onFailure { error ->
                        Timber.e(error, "Failed to set DW-H5600 settings")
                    }
                }
            }
            return
        }

        // Use pure function to encode, then execute
        IO.writeCmd(GetSetMode.SET, SettingsIOFunctional.encode(valueObj))
    }
}
