package org.avmedia.gshockGoogleSync.h5600

import AppSwitch
import AppText
import AppTextLarge
import android.app.AlertDialog
import android.app.TimePickerDialog
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import org.avmedia.gshockGoogleSync.ui.common.AppButton
import org.avmedia.gshockGoogleSync.ui.common.AppChoiceButton
import org.avmedia.gshockGoogleSync.ui.common.AppCard
import org.avmedia.gshockapi.model.SoundMode
import org.avmedia.gshockapi.model.WatchFaceItem


/** Custom tab content for the DW-H5600: two cards, watch face first, then sound & notifications. */
@Composable
fun H5600Card(vm: H5600ViewModel = hiltViewModel()) {
    val s by vm.state.collectAsState()
    if (!s.supported) return
    Column {
        WatchFaceCard(vm)
        androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(top = 8.dp))
        SoundNotificationsCard(vm)
        androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(top = 8.dp))
        FindPhoneCard()
    }
}

/** Find Phone: ring sound (system picker), volume, and a 5-second test. Phone-side only. */
@Composable
private fun FindPhoneCard() {
    val context = LocalContext.current
    var title by remember { mutableStateOf(org.avmedia.gshockGoogleSync.ui.actions.FindPhoneSettings.soundTitle(context)) }
    var volume by remember { mutableStateOf(org.avmedia.gshockGoogleSync.ui.actions.FindPhoneSettings.volumePct(context).toFloat()) }
    val picker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode != android.app.Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val picked: android.net.Uri? = result.data?.let {
            androidx.core.content.IntentCompat.getParcelableExtra(it, android.media.RingtoneManager.EXTRA_RINGTONE_PICKED_URI, android.net.Uri::class.java)
        }
        // "Default" in the picker (or nothing picked) = the phone's default alarm sound.
        val isDefault = picked == null || picked == android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_ALARM)
        org.avmedia.gshockGoogleSync.ui.actions.FindPhoneSettings.setSoundUri(context, if (isDefault) null else picked)
        title = org.avmedia.gshockGoogleSync.ui.actions.FindPhoneSettings.soundTitle(context)
    }
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            AppTextLarge(text = "Find Phone")
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppText(text = "Sound: $title", modifier = Modifier.weight(1f))
                org.avmedia.gshockGoogleSync.ui.common.AppSmallButton(
                    text = "Choose",
                    onClick = {
                        val current = org.avmedia.gshockGoogleSync.ui.actions.FindPhoneSettings.soundUri(context)
                            ?: android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_ALARM)
                        picker.launch(
                            android.content.Intent(android.media.RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                                putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_TYPE,
                                    android.media.RingtoneManager.TYPE_ALARM or android.media.RingtoneManager.TYPE_RINGTONE)
                                putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_TITLE, "Find Phone sound")
                                putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                                putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                                putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI,
                                    android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_ALARM))
                                putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, current)
                            },
                        )
                    },
                )
            }
            AppText(text = "Volume: ${volume.toInt()} %", modifier = Modifier.padding(top = 8.dp))
            androidx.compose.material3.Slider(
                value = volume,
                onValueChange = { volume = it },
                onValueChangeFinished = {
                    org.avmedia.gshockGoogleSync.ui.actions.FindPhoneSettings.setVolumePct(context, volume.toInt())
                },
                valueRange = 10f..100f,
                steps = 8, // 10 % steps
            )
            var vibrate by remember { mutableStateOf(org.avmedia.gshockGoogleSync.ui.actions.FindPhoneSettings.vibrate(context)) }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppText(text = "Vibrate too")
                AppSwitch(checked = vibrate, onCheckedChange = {
                    vibrate = it
                    org.avmedia.gshockGoogleSync.ui.actions.FindPhoneSettings.setVibrate(context, it)
                })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                org.avmedia.gshockGoogleSync.ui.common.AppSmallButton(
                    text = "Test 5 s",
                    onClick = {
                        org.avmedia.gshockGoogleSync.ui.actions.FindPhoneSettings.setVolumePct(context, volume.toInt())
                        org.avmedia.gshockGoogleSync.ui.actions.PhoneFinder.preview(context)
                    },
                )
                org.avmedia.gshockGoogleSync.ui.common.AppSmallButton(
                    text = "Stop",
                    onClick = { org.avmedia.gshockGoogleSync.ui.actions.PhoneFinder.stop(context) },
                )
            }
            AppText(
                text = "Plays on the alarm channel, so it rings even in silent mode. Lift the phone to stop.",
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

/** Watch face: Sun data / Steps, dynamic sunset-step toggle, weather instead of sunrise, loc/time. */
@Composable
private fun WatchFaceCard(vm: H5600ViewModel) {
    val s by vm.state.collectAsState()
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            AppTextLarge(text = "Watch face")
            // Real values to compare with the watch (phone location, Open-Meteo)
            AppText(text = "Real today: ${s.realSun}", fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
            AppText(text = "Weather now: ${s.realWeather}", fontSize = 13.sp)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppText(text = "Watch should show: ${s.watchShould}", fontSize = 13.sp, modifier = Modifier.weight(1f))
                org.avmedia.gshockGoogleSync.ui.common.AppSmallButton(text = "Refresh weather", onClick = { vm.refreshWeatherOnly() })
            }
            // Watch face item
            // Face 1 = simple face with the weekday; face 2 = customisable face showing the item below.
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                if (s.faceNumber == null) org.avmedia.gshockGoogleSync.ui.common.LoadingSpinner()
                else {
                    AppChoiceButton(text = "Face 1 (weekday)", selected = s.faceNumber == 1,
                        onClick = { vm.setFaceNumber(1) })
                    AppChoiceButton(text = "Face 2 (custom)", selected = s.faceNumber == 2,
                        onClick = { vm.setFaceNumber(2) })
                }
            }
            val faceLabels = listOf(
                WatchFaceItem.SUNRISE_SUNSET to "Sun", WatchFaceItem.STEPS to "Steps", WatchFaceItem.MOON to "Moon",
                WatchFaceItem.HEART_RATE to "Heart rate", WatchFaceItem.SLEEP to "Sleep",
            )
            AppText(
                text = (if (s.faceNumber == 1) "Face 2 item (not shown on face 1): " else "Face 2 item: ") + (faceLabels.firstOrNull { it.first == s.watchFace }?.second
                    ?: s.watchFace?.name?.lowercase()?.replace('_', ' ')
                    ?: s.watchFaceCode?.let { "unknown (code 0x%02X)".format(it) } ?: "unknown"),
                modifier = Modifier.padding(top = 8.dp),
            )
            // Current item highlighted; greyed while syncing or while the dynamic toggle controls it.
            if (s.watchFace == null && s.watchFaceCode == null) org.avmedia.gshockGoogleSync.ui.common.LoadingSpinner()
            else faceLabels.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { (item, label) ->
                        AppChoiceButton(
                            text = label,
                            selected = s.watchFace == item,
                            enabled = !s.dynamicFace,
                            onClick = { vm.setWatchFace(item) },
                        )
                    }
                }
            }
            if (s.dynamicFace) {
                AppText(text = "Controlled by the dynamic sunset-step toggle below.", fontSize = 13.sp)
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppText(text = "Dynamic sunset-step toggle")
                AppSwitch(checked = s.dynamicFace, onCheckedChange = { vm.setDynamicFace(it) })
            }
            AppText(
                text = "Steps all day; Sun data from ${SunFace.LEAD_HOURS} h before sunset until " +
                    "${SunFace.TRAIL_MIN} min after. Today: ${s.sunsetText}",
                fontSize = 13.sp,
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppText(text = "Weather instead of sunrise")
                AppSwitch(checked = s.tempSun, onCheckedChange = { vm.setTempSun(it) })
            }
            AppText(
                text = "Sun screen top line shows W:TT — W = 1 sunny, 2 cloudy, 3 rain/snow, 4 extreme (heavy rain, storm, gusts ≥ 60 km/h); TT = °C (0–59). " +
                    "E.g. 1:18 = sunny 18 °C, 3:10 = rain 10 °C. Sunset stays real. " +
                    "Updates every ${TempSun.REFRESH_MIN} min while connected. ${s.tempText}",
                fontSize = 13.sp,
            )
            if (s.tempSun) {
                // Fine-tune for the watch's own sun formula (measured: top +2, sunset -3)
                listOf(
                    Triple("Top line fine-tune", s.riseCorr, true),
                    Triple("Sunset fine-tune", s.setCorr, false),
                ).forEach { (label, value, isRise) ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AppText(text = "$label: %+d min".format(value), fontSize = 13.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            org.avmedia.gshockGoogleSync.ui.common.AppSmallButton(text = "−", onClick = {
                                if (isRise) vm.adjustTempSun(-1, 0) else vm.adjustTempSun(0, -1)
                            })
                            org.avmedia.gshockGoogleSync.ui.common.AppSmallButton(text = "+", onClick = {
                                if (isRise) vm.adjustTempSun(1, 0) else vm.adjustTempSun(0, 1)
                            })
                        }
                    }
                }
                AppText(
                    text = "If the watch shows the top line 1 min too low, press + on top; if sunset is 1 min late, press − on sunset.",
                    fontSize = 12.sp,
                )
            }
            org.avmedia.gshockGoogleSync.ui.common.AppSmallButton(
                text = "Send loc/time",
                onClick = { vm.sendLocation() },
            )
        }
    }
}

/** Sound mode (+ raw packet), per-alarm note, last applied, and the notification app filter. */
@Composable
private fun SoundNotificationsCard(vm: H5600ViewModel) {
    val s by vm.state.collectAsState()
    val context = LocalContext.current
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            AppTextLarge(text = "Sound & notifications")
            // Sound mode
            AppText(
                text = "Sound mode: " + (s.soundMode?.name?.replace('_', ' ') ?: if (s.busy) "reading…" else "unknown"),
                modifier = Modifier.padding(top = 8.dp),
            )
            // All five modes; the current one is greyed out. Two rows so they fit on a phone.
            val modes = listOf(
                SoundMode.ALL_ON to "Sound + vibe", SoundMode.SOUND_ONLY to "Sound only", SoundMode.VIBE_ONLY to "Vibe only",
                SoundMode.ALL_OFF to "Off", SoundMode.CUSTOM to "Custom",
            )
            if (s.soundMode == null) org.avmedia.gshockGoogleSync.ui.common.LoadingSpinner() // never read
            else modes.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { (mode, label) ->
                        AppChoiceButton(
                            text = label,
                            selected = s.soundMode == mode,
                            onClick = { vm.setSoundMode(mode) },
                        )
                    }
                }
            }

            // Raw sound packet + re-read (used to map the per-function CUSTOM bits)
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppText(text = "0x42: " + s.soundHex.ifEmpty { "…" }, fontSize = 13.sp)
                org.avmedia.gshockGoogleSync.ui.common.AppSmallButton(text = "Re-read", onClick = { vm.refresh() })
            }
            AppText(text = "Alarm days and Vibe only / Sound are set per alarm in the Alarms tab.", fontSize = 13.sp, modifier = Modifier.padding(top = 12.dp))
            s.lastApplied?.let { AppText(text = "Last applied: $it", fontSize = 13.sp) }
            // Notification app filter
            var filter by remember { mutableStateOf(NotificationFilter.load(context)) }
            fun saveFilter(cfg: NotificationFilter.Config) {
                NotificationFilter.save(context, cfg)
                filter = cfg
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppText(text = "Notifications only from chosen apps")
                AppSwitch(checked = filter.enabled, onCheckedChange = { saveFilter(filter.copy(enabled = it)) })
            }
            org.avmedia.gshockGoogleSync.ui.common.AppSmallButton(
                text = "Choose apps (${filter.packages.size})",
                onClick = {
                    val apps = NotificationFilter.launchableApps(context)
                    val checked = BooleanArray(apps.size) { apps[it].packageName in filter.packages }
                    AlertDialog.Builder(context)
                        .setTitle("Forward notifications from")
                        .setMultiChoiceItems(apps.map { it.label }.toTypedArray(), checked) { _, i, on -> checked[i] = on }
                        .setPositiveButton("Save") { _, _ ->
                            saveFilter(filter.copy(packages = apps.filterIndexed { i, _ -> checked[i] }.map { it.packageName }.toSet()))
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                },
            )
            AppText(
                text = if (filter.enabled) "Only checked apps reach the watch." else "Off: every app's notifications reach the watch.",
                fontSize = 13.sp,
            )
        }
    }
}
