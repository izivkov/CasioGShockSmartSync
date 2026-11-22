package org.avmedia.gshockGoogleSync.webhook

import org.avmedia.gshockapi.io.BasicHealthFieldsCodec
import org.avmedia.gshockapi.io.LengthUnit
import org.avmedia.gshockapi.io.HeartRateSettingCodec
import org.avmedia.gshockapi.io.PhysicalSettingsCodec
import org.avmedia.gshockapi.io.ProfileCodec
import org.avmedia.gshockapi.io.SmartAlarmCodec
import org.avmedia.gshockapi.io.StepGoalCodec
import org.avmedia.gshockapi.io.WatchFaceCodec

/**
 * Watch settings decoded from the raw setting cache ([RawSettings]): face, heart-rate measurement, bedtime,
 * night window, step reminder, unit, step goal, profile and smart alarm. One row per variable; the value
 * is null when the code was not read or its layout is not the one the app knows.
 */
object WatchSettingVariables {
    class Def(val name: String, val description: String, val value: (Map<String, ByteArray>) -> Any?)

    private fun hhmm(time: Pair<Int, Int>?): String? = time?.let { "%02d:%02d".format(it.first, it.second) }

    private fun <T> parsed(frames: Map<String, ByteArray>, code: Int, parse: (ByteArray) -> T?): T? =
        frames[RawSettings.name(code)]?.let(parse)

    private val HEART_MODES = listOf("off", "activities", "always")
    private const val UNCONFIRMED_SLOT = "UNCONFIRMED which slot of 1 to 4 is top right"

    val DEFS: List<Def> = listOf(
        Def("watch.face_index", "H5600 only: selected watch face, as the position in the watch's list (UNCONFIRMED which index is the custom face)") {
            parsed(it, 0x51, WatchFaceCodec::parse)?.face
        },
    ) + (1..WatchFaceCodec.SLOTS).map { slot ->
        Def("watch.face_item.$slot", "H5600 only: item $slot of the custom watch face ($UNCONFIRMED_SLOT)") {
            parsed(it, 0x51, WatchFaceCodec::parse)?.items?.get(slot - 1)?.let { code -> WatchFaceCodec.itemName(code) }
        }
    } + listOf(
        Def("watch.hr_measurement", "H5600 only: heart-rate measurement setting: off, activities or always") {
            parsed(it, 0x41, HeartRateSettingCodec::parse)?.mode?.let { m -> HEART_MODES[m] }
        },
        Def("watch.bedtime", "H5600 only: preferred bedtime, HH:MM") {
            hhmm(parsed(it, 0x4F, PhysicalSettingsCodec::parse)?.bedtime)
        },
        Def("watch.wake_up_time", "H5600 only: preferred wake-up time, HH:MM") {
            hhmm(parsed(it, 0x4F, PhysicalSettingsCodec::parse)?.wakeUp)
        },
        Def("watch.night_start", "H5600 only: start of the night window, HH:MM") {
            hhmm(parsed(it, 0x13, BasicHealthFieldsCodec::parse)?.nightStart)
        },
        Def("watch.night_end", "H5600 only: end of the night window, HH:MM") {
            hhmm(parsed(it, 0x13, BasicHealthFieldsCodec::parse)?.nightEnd)
        },
        Def("watch.step_reminder", "H5600 only: true when the step reminder is on") {
            parsed(it, 0x13, BasicHealthFieldsCodec::parse)?.stepReminder
        },
        Def("watch.length_unit", "H5600 only: unit of length, km or miles (UNCONFIRMED: bit set is read as miles)") {
            parsed(it, 0x13, BasicHealthFieldsCodec::parse)?.unit?.let { u -> if (u == LengthUnit.MILES) "miles" else "km" }
        },
        Def("watch.step_goal", "H5600 only: daily step goal read from the step goal setting") {
            parsed(it, 0x43, StepGoalCodec::parse)
        },
        Def("watch.profile_height_cm", "H5600 only: height of the profile on the watch, cm") {
            parsed(it, 0x45, ProfileCodec::parse)?.heightCm
        },
        Def("watch.profile_weight_kg", "H5600 only: weight of the profile on the watch, kg") {
            parsed(it, 0x45, ProfileCodec::parse)?.weightKg
        },
        Def("watch.smart_alarm_time", "H5600 only: smart alarm time, HH:MM (UNCONFIRMED: hour and minute are read in the order of the setting)") {
            parsed(it, 0x50, SmartAlarmCodec::parse)?.let { s -> hhmm(s.hour to s.minute) }
        },
        Def("watch.smart_alarm_on", "H5600 only: true when the smart alarm is on (UNCONFIRMED)") {
            parsed(it, 0x50, SmartAlarmCodec::parse)?.on
        },
        Def("watch.smart_alarm_window_min", "H5600 only: smart alarm wake window, minutes before the alarm time") {
            parsed(it, 0x50, SmartAlarmCodec::parse)?.windowMinutes
        },
    )

    /** Raw cache ("0x41" to "41 01 02") to frames; entries that are not hex are left out. */
    fun frames(raw: Map<String, String>): Map<String, ByteArray> =
        raw.mapNotNull { (name, hex) ->
            WatchDecoders.bytes(hex)?.let { bytes -> name to ByteArray(bytes.size) { bytes[it].toByte() } }
        }.toMap()
}
