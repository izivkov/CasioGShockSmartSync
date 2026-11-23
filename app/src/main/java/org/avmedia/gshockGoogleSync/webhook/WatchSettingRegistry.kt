package org.avmedia.gshockGoogleSync.webhook

import org.avmedia.gshockapi.io.ActivityListCodec
import org.avmedia.gshockapi.io.ActivityModels
import org.avmedia.gshockapi.io.BasicHealthFieldsCodec
import org.avmedia.gshockapi.io.HeartRateSettingCodec
import org.avmedia.gshockapi.io.LengthUnit
import org.avmedia.gshockapi.io.NotificationManagerCodec
import org.avmedia.gshockapi.io.NotificationSoundCodec
import org.avmedia.gshockapi.io.PhysicalSettingsCodec
import org.avmedia.gshockapi.io.ProfileCodec
import org.avmedia.gshockapi.io.SmartAlarmCodec
import org.avmedia.gshockapi.io.StepGoalCodec
import org.avmedia.gshockapi.io.WatchFaceCodec

/** What a setting accepts. Values are checked here, before anything is queued or written. */
sealed interface SettingType {
    /** One of [values] (lower case, words joined by `_`). */
    class Choice(val values: List<String>) : SettingType

    /** A whole number in [range]; when [only] is set, one of those. */
    class Whole(val range: IntRange, val only: Set<Int>? = null) : SettingType

    data object Bool : SettingType

    /** `HH:MM`, 24 hours. */
    data object Time : SettingType
}

/**
 * One watch setting Home Assistant may change. [read] and [change] only call the existing codecs; the write itself
 * is the app's checked write (read, change [touched] bytes, write, read back). Values are canonical: a Choice is its
 * lower-case word, a Number an Int, Bool a Boolean, Time `HH:MM`.
 */
class WatchSetting(
    val key: String,
    val type: SettingType,
    val code: Int,
    val touched: Set<Int>,
    val read: (ByteArray) -> Any?,
    val change: (ByteArray, Any) -> ByteArray?,
    /** What the watch returns after the write (0x3D rewrites its command byte). */
    val expectedBack: (before: ByteArray, written: ByteArray) -> ByteArray = { _, written -> written },
    /** How the read-back is compared with [expectedBack] (the activity list ignores its tail). */
    val matches: (back: ByteArray, expected: ByteArray) -> Boolean = { back, expected -> back.contentEquals(expected) },
) {
    /** The value in the raw cache [frames] (code name to frame), null when the code was not read or not understood. */
    fun valueIn(frames: Map<String, ByteArray>): Any? = frames[RawSettings.name(code)]?.let(read)
}

sealed interface SettingCheck {
    class Ok(val value: Any) : SettingCheck
    class Refused(val reason: String) : SettingCheck
}

object WatchSettingRegistry {
    fun word(text: String): String = text.lowercase().map { if (it.isLetterOrDigit()) it else '_' }.joinToString("").trim('_').replace(Regex("_+"), "_")

    private fun hhmm(t: Pair<Int, Int>?): String? = t?.let { "%02d:%02d".format(it.first, it.second) }

    private fun time(v: Any): Pair<Int, Int> = (v as String).split(":").let { it[0].toInt() to it[1].toInt() }


    private val FACES: Map<Int, String> = WatchFaceCodec.FACES.associateWith { word(WatchFaceCodec.faceName(it)) }
    private val ITEMS: List<String> = WatchFaceCodec.ITEM_NAMES.map(::word)
    private val BACKGROUNDS: List<String> = PhysicalSettingsCodec.TRAINING_BACKGROUNDS.map(::word)
    private val HEART_MODES = listOf("off", "activities", "always")
    private val SOUND_MODES = NotificationSoundCodec.MODES.map(::word)
    private val PATTERNS = NotificationSoundCodec.PATTERNS.map(::word)

    private fun number(key: String, range: IntRange, code: Int, touched: Set<Int>, only: Set<Int>? = null, read: (ByteArray) -> Int?, change: (ByteArray, Int) -> ByteArray?) =
        WatchSetting(key, SettingType.Whole(range, only), code, touched, read) { f, v -> change(f, v as Int) }

    private fun bool(key: String, code: Int, touched: Set<Int>, read: (ByteArray) -> Boolean?, change: (ByteArray, Boolean) -> ByteArray?) =
        WatchSetting(key, SettingType.Bool, code, touched, read) { f, v -> change(f, v as Boolean) }

    private fun clock(key: String, code: Int, touched: Set<Int>, read: (ByteArray) -> Pair<Int, Int>?, change: (ByteArray, Int, Int) -> ByteArray?) =
        WatchSetting(key, SettingType.Time, code, touched, { hhmm(read(it)) }) { f, v -> time(v).let { (h, m) -> change(f, h, m) } }

    private fun pick(key: String, values: List<String>, code: Int, touched: Set<Int>, read: (ByteArray) -> String?, change: (ByteArray, String) -> ByteArray?) =
        WatchSetting(key, SettingType.Choice(values), code, touched, read) { f, v -> change(f, v as String) }

    /** One on/off key per activity the H5600 offers (`activity_running`, ...): a bool, because the list codec toggles one code at a time. */
    private fun activity(code: Int) = WatchSetting(
        "activity_" + word(ActivityModels.name(ActivityModels.H5600, code)), SettingType.Bool, ActivityListCodec.CODE, (1..ActivityListCodec.MAX).toSet(),
        { f -> if (ActivityListCodec.layoutOk(f)) ActivityListCodec.parse(f)?.let { code in it } else null },
        { f, v -> ActivityListCodec.parse(f)?.let { ActivityListCodec.encode(f, ActivityListCodec.toggle(it, code, v as Boolean)) } },
        matches = ActivityListCodec::sameSlots,
    )

    private val ACTIVITY_SETTINGS: List<WatchSetting> = ActivityModels.offered(ActivityModels.H5600).map(::activity)

    val ALL: List<WatchSetting> = listOf(
        pick("watch_face", FACES.values.toList(), WatchFaceCodec.CODE, WatchFaceCodec.FACE_BYTES,
            { f -> WatchFaceCodec.parse(f)?.face?.let(FACES::get) },
            { f, v -> WatchFaceCodec.withFace(f, FACES.entries.first { it.value == v }.key) }),
        pick("custom_slot", WatchFaceCodec.OFFERED_ITEMS.map { ITEMS[it] }, WatchFaceCodec.CODE, WatchFaceCodec.TOP_RIGHT_BYTES,
            { f -> WatchFaceCodec.parse(f)?.items?.get(WatchFaceCodec.TOP_RIGHT_SLOT - 1)?.let { ITEMS.getOrNull(it) } },
            { f, v -> WatchFaceCodec.withTopRight(f, ITEMS.indexOf(v)) }),
        pick("hr_measurement", HEART_MODES, HeartRateSettingCodec.CODE, HeartRateSettingCodec.MODE_BYTES,
            { f -> HeartRateSettingCodec.parse(f)?.mode?.let(HEART_MODES::get) },
            { f, v -> HeartRateSettingCodec.withMode(f, HEART_MODES.indexOf(v)) }),
        clock("bedtime", PhysicalSettingsCodec.CODE, PhysicalSettingsCodec.BEDTIME_BYTES,
            { f -> PhysicalSettingsCodec.parse(f)?.bedtime }, PhysicalSettingsCodec::withBedtime),
        clock("wake_time", PhysicalSettingsCodec.CODE, PhysicalSettingsCodec.WAKE_BYTES,
            { f -> PhysicalSettingsCodec.parse(f)?.wakeUp }, PhysicalSettingsCodec::withWakeUp),
        clock("night_start", BasicHealthFieldsCodec.CODE, BasicHealthFieldsCodec.NIGHT_BYTES,
            { f -> BasicHealthFieldsCodec.parse(f)?.nightStart },
            { f, h, m -> BasicHealthFieldsCodec.parse(f)?.let { BasicHealthFieldsCodec.withNightWindow(f, h to m, it.nightEnd) } }),
        clock("night_end", BasicHealthFieldsCodec.CODE, BasicHealthFieldsCodec.NIGHT_BYTES,
            { f -> BasicHealthFieldsCodec.parse(f)?.nightEnd },
            { f, h, m -> BasicHealthFieldsCodec.parse(f)?.let { BasicHealthFieldsCodec.withNightWindow(f, it.nightStart, h to m) } }),
        bool("step_reminder", BasicHealthFieldsCodec.CODE, BasicHealthFieldsCodec.FLAGS_BYTES,
            { f -> BasicHealthFieldsCodec.parse(f)?.stepReminder }, BasicHealthFieldsCodec::withStepReminder),
        pick("length_unit", listOf("km", "miles"), BasicHealthFieldsCodec.CODE, BasicHealthFieldsCodec.FLAGS_BYTES,
            { f -> BasicHealthFieldsCodec.parse(f)?.unit?.let { if (it == LengthUnit.MILES) "miles" else "km" } },
            { f, v -> BasicHealthFieldsCodec.withUnit(f, if (v == "miles") LengthUnit.MILES else LengthUnit.KM) }),
        number("step_goal", StepGoalCodec.MIN_STEPS..StepGoalCodec.MAX_STEPS, StepGoalCodec.CODE, StepGoalCodec.BYTES,
            read = StepGoalCodec::parse, change = StepGoalCodec::withSteps),
        number("profile_height_cm", 100..250, ProfileCodec.CODE, ProfileCodec.BYTES,
            read = { f -> ProfileCodec.parse(f)?.heightCm },
            change = { f, cm ->
                ProfileCodec.parse(f)?.let { p ->
                    ProfileCodec.withHeightAndWeight(f, if (p.imperialLength) Math.round(cm / 2.54).toInt() else cm, p.weight)
                }
            }),
        number("profile_weight_kg", 20..300, ProfileCodec.CODE, ProfileCodec.BYTES,
            read = { f -> ProfileCodec.parse(f)?.weightKg },
            change = { f, kg ->
                ProfileCodec.parse(f)?.let { p ->
                    ProfileCodec.withHeightAndWeight(f, p.height, if (p.weightNotKg) Math.round(kg * 2.2046226).toInt() else kg)
                }
            }),
        pick("profile_sex", listOf("male", "female"), ProfileCodec.CODE, ProfileCodec.SEX_BYTES,
            { f -> ProfileCodec.parse(f)?.let { if (it.female) "female" else "male" } },
            { f, v -> ProfileCodec.withFemale(f, v == "female") }),
        bool("smart_alarm", SmartAlarmCodec.CODE, SmartAlarmCodec.FLAG_BYTES,
            { f -> SmartAlarmCodec.parse(f)?.on }, SmartAlarmCodec::withOn),
        clock("smart_alarm_time", SmartAlarmCodec.CODE, SmartAlarmCodec.TIME_BYTES,
            { f -> SmartAlarmCodec.parse(f)?.let { it.hour to it.minute } }, SmartAlarmCodec::withTime),
        bool("smart_alarm_snooze", SmartAlarmCodec.CODE, SmartAlarmCodec.FLAG_BYTES,
            { f -> SmartAlarmCodec.parse(f)?.snooze }, SmartAlarmCodec::withSnooze),
        number("smart_alarm_window_min", 15..60, SmartAlarmCodec.CODE, SmartAlarmCodec.WINDOW_BYTES, only = SmartAlarmCodec.WINDOWS.toSet(),
            read = { f -> SmartAlarmCodec.parse(f)?.windowMinutes }, change = SmartAlarmCodec::withWindow),
        pick("training_background", BACKGROUNDS, PhysicalSettingsCodec.CODE, PhysicalSettingsCodec.BACKGROUND_BYTES,
            { f -> PhysicalSettingsCodec.parseBody(f)?.trainingBackground?.let(BACKGROUNDS::get) },
            { f, v -> PhysicalSettingsCodec.withTrainingBackground(f, BACKGROUNDS.indexOf(v)) }),
        number("max_heart_rate", PhysicalSettingsCodec.MAX_HR_RANGE, PhysicalSettingsCodec.CODE, PhysicalSettingsCodec.MAX_HR_BYTES,
            read = { f -> PhysicalSettingsCodec.parseBody(f)?.maxHr }, change = PhysicalSettingsCodec::withMaxHeartRate),
        number("resting_heart_rate", PhysicalSettingsCodec.RESTING_HR_RANGE, PhysicalSettingsCodec.CODE, PhysicalSettingsCodec.RESTING_HR_BYTES,
            read = { f -> PhysicalSettingsCodec.parseBody(f)?.restingHr }, change = PhysicalSettingsCodec::withRestingHeartRate),
        number("vo2max", PhysicalSettingsCodec.VO2MAX_RANGE, PhysicalSettingsCodec.CODE, PhysicalSettingsCodec.VO2MAX_BYTES,
            read = { f -> PhysicalSettingsCodec.parseBody(f)?.vo2max }, change = PhysicalSettingsCodec::withVo2max),
        pick("notification_sound_mode", SOUND_MODES, NotificationSoundCodec.CODE, NotificationSoundCodec.MODE_BYTES,
            { f -> NotificationSoundCodec.parse(f)?.soundMode?.let(SOUND_MODES::getOrNull) },
            { f, v -> NotificationSoundCodec.withSoundMode(f, SOUND_MODES.indexOf(v)) }),
        pick("notification_vibration_mode", SOUND_MODES, NotificationSoundCodec.CODE, NotificationSoundCodec.MODE_BYTES,
            { f -> NotificationSoundCodec.parse(f)?.vibrationMode?.let(SOUND_MODES::getOrNull) },
            { f, v -> NotificationSoundCodec.withVibrationMode(f, SOUND_MODES.indexOf(v)) }),
        pick("notification_pattern", PATTERNS, NotificationSoundCodec.CODE, NotificationSoundCodec.PATTERN_BYTES,
            { f -> NotificationSoundCodec.parse(f)?.pattern?.let(PATTERNS::getOrNull) },
            { f, v -> NotificationSoundCodec.withPattern(f, PATTERNS.indexOf(v)) }),
        WatchSetting("notification_language", SettingType.Whole(0..3), NotificationManagerCodec.CODE, NotificationManagerCodec.LANGUAGE_BYTES,
            { f -> NotificationManagerCodec.parse(f)?.language },
            { f, v -> NotificationManagerCodec.withLanguage(f, v as Int) }, NotificationManagerCodec::expectedBack),
        WatchSetting("notification_off_at_night", SettingType.Whole(0..3), NotificationManagerCodec.CODE, NotificationManagerCodec.NIGHT_BYTES,
            { f -> NotificationManagerCodec.parse(f)?.offAtNight },
            { f, v -> NotificationManagerCodec.withOffAtNight(f, v as Int) }, NotificationManagerCodec::expectedBack),
    ) + ACTIVITY_SETTINGS

    private val BY_KEY = ALL.associateBy { it.key }

    init {
        require(BY_KEY.size == ALL.size) { "Duplicate setting key" }
    }

    fun find(key: String): WatchSetting? = BY_KEY[key]

    /** The codes the entries read (they must be in [RawSettings.DECODED_CODES]). */
    val CODES: Set<Int> = ALL.map { it.code }.toSet()

    /** Checks [raw] (as JSON gave it) against [setting]'s type; Ok holds the canonical value. */
    fun check(setting: WatchSetting, raw: Any?): SettingCheck {
        val t = setting.type
        return when (t) {
            is SettingType.Choice -> {
                val w = (raw as? String)?.let(::word)
                if (w != null && w in t.values) SettingCheck.Ok(w) else SettingCheck.Refused("allowed values: " + t.values.joinToString(", "))
            }
            is SettingType.Whole -> {
                val n = when (raw) {
                    is Number -> raw.toDouble().takeIf { it == Math.floor(it) && !it.isInfinite() }?.toInt()
                    is String -> raw.trim().toIntOrNull()
                    else -> null
                }
                when {
                    n == null -> SettingCheck.Refused("a whole number is needed")
                    n !in t.range -> SettingCheck.Refused("allowed range: ${t.range.first} to ${t.range.last}")
                    t.only != null && n !in t.only -> SettingCheck.Refused("allowed values: " + t.only.sorted().joinToString(", "))
                    else -> SettingCheck.Ok(n)
                }
            }
            SettingType.Bool -> when (if (raw is String) raw.trim().lowercase() else raw) {
                true, "true", "on" -> SettingCheck.Ok(true)
                false, "false", "off" -> SettingCheck.Ok(false)
                else -> SettingCheck.Refused("true or false is needed")
            }
            SettingType.Time -> {
                val m = (raw as? String)?.trim()?.let { Regex("""(\d{1,2}):(\d{2})""").matchEntire(it) }
                val h = m?.groupValues?.get(1)?.toInt()
                val min = m?.groupValues?.get(2)?.toInt()
                if (h != null && min != null && h in 0..23 && min in 0..59) SettingCheck.Ok("%02d:%02d".format(h, min)) else SettingCheck.Refused("a time like 07:30 is needed")
            }
        }
    }
}
