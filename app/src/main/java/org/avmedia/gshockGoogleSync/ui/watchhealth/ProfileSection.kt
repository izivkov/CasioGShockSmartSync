package org.avmedia.gshockGoogleSync.ui.watchhealth

import android.app.DatePickerDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import org.avmedia.gshockapi.io.PhysicalSettingsCodec
import org.avmedia.gshockapi.io.ProfileCodec

/** Birth date text, "-" while unknown. */
internal fun birthText(p: ProfileCodec.Profile?): String =
    if (p?.birthYear == null || p.birthMonth == null || p.birthDay == null) "-"
    else "%04d-%02d-%02d".format(p.birthYear, p.birthMonth, p.birthDay)

/** The profile the watch keeps (0x45) and its body values (0x4F), near the top of the screen. Units follow the length unit. */
@Composable
internal fun ProfileSection(ui: WatchHealthUi, edit: (Editing) -> Unit) {
    val can = !ui.working
    val p = ui.profile
    val b = ui.body
    ValueRow("Height", p?.let { BodyUnits.heightText(it) } ?: "-", can && p != null) { edit(Editing.Height) }
    ValueRow("Weight", p?.let { BodyUnits.weightText(it) } ?: "-", can && p != null) { edit(Editing.Weight) }
    ValueRow(
        "Birth date", birthText(p), can && p != null && p.birthYear != null,
        note = if (p != null && p.birthYear == null) "The watch's date could not be read" else null,
    ) { edit(Editing.BirthDate) }
    ValueRow(
        "Sex", p?.let { "Value ${if (it.sexBit) 1 else 0}" } ?: "-", enabled = false,
        note = "Read from the watch. Which sex is 0: ${BodyUnits.CHECK.lowercase()}",
    )
    ValueRow("Training background", b?.let { PhysicalSettingsCodec.TRAINING_BACKGROUNDS[it.trainingBackground] } ?: "-", can && b != null) { edit(Editing.Background) }
    ValueRow(
        "Highest heart rate", b?.let { "${it.maxHr} bpm" } ?: "-", can && b != null,
        note = b?.let { if (it.maxHrByUser) "Set by you" else "Estimated by the watch" },
    ) { edit(Editing.MaxHr) }
    ValueRow(
        "Resting heart rate", b?.let { "${it.restingHr} bpm" } ?: "-", can && b != null,
        note = b?.let { if (it.restingHrByUser) "Set by you" else "Estimated by the watch" },
    ) { edit(Editing.RestingHr) }
    ValueRow(
        "VO2max", b?.let { "${it.vo2max}" } ?: "-", can && b != null,
        note = b?.let { if (it.vo2maxByUser) "Set by you" else "Estimated by the watch" },
    ) { edit(Editing.Vo2) }
}

/** The system date picker, opened on the watch's birth date. */
@Composable
internal fun BirthDateDialog(profile: ProfileCodec.Profile, close: () -> Unit, viewModel: WatchHealthSettingsViewModel) {
    val context = LocalContext.current
    DisposableEffect(Unit) {
        val dialog = DatePickerDialog(
            context,
            { _, y, m, d -> close(); viewModel.setBirthDate(y, m + 1, d) },
            profile.birthYear ?: 1990, (profile.birthMonth ?: 1) - 1, profile.birthDay ?: 1,
        )
        dialog.setOnDismissListener { close() }
        dialog.show()
        onDispose { dialog.setOnDismissListener(null); dialog.dismiss() }
    }
}
