package org.avmedia.gshockGoogleSync.ui.health

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import org.avmedia.gshockGoogleSync.R
import org.avmedia.gshockapi.model.H5600HealthData
import org.avmedia.gshockapi.model.SleepNight
import java.time.format.DateTimeFormatter
import java.util.Locale

private val CLOCK_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())
private val STAMP_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM HH:mm", Locale.getDefault())

/**
 * The newer LifeLog sections of a DW-H5600 read: sleep, blood oxygen and breathing, cardio load and daily
 * totals. A card appears only when the read has data for it, and a row only when it has a value. Meant to be
 * called inside the column of [DwH5600LifeLogCard], so the cards share its spacing.
 */
@Composable
fun DwH5600SleepAndBody(health: H5600HealthData) {
    health.sleep?.night?.takeUnless { it.isEmpty }?.let { SleepSection(it) }
    BloodOxygenSection(health)
    CardioSection(health)
    DailyTotalsSection(health)
}

@Composable
private fun SleepSection(night: SleepNight) {
    val asleep = night.fellAsleep
    val woke = night.wokeUp
    val window = if (asleep != null && woke != null) "${asleep.format(CLOCK_FORMAT)} \u2013 ${woke.format(CLOCK_FORMAT)}" else null
    val heartRate = listOfNotNull(night.heartRateNight, night.heartRateBaseline).takeIf { it.isNotEmpty() }
        ?.joinToString(" / ", postfix = " bpm")
    val breathing = listOfNotNull(night.breathingRate, night.breathingBaseline).takeIf { it.isNotEmpty() }
        ?.joinToString(" / ", postfix = " /min") { String.format(Locale.US, "%.1f", it) }

    Section(title = stringResource(id = R.string.card_sleep)) {
        OptionalRow(stringResource(id = R.string.sleep_score), night.sleepScore?.toString())
        OptionalRow(stringResource(id = R.string.sleep_total), night.totalSleepMinutes?.let(::formatMinutes))
        OptionalRow(stringResource(id = R.string.sleep_deep), night.deepMinutes?.let(::formatMinutes))
        OptionalRow(stringResource(id = R.string.sleep_light), night.lightMinutes?.let(::formatMinutes))
        OptionalRow(stringResource(id = R.string.sleep_rem), night.remMinutes?.let(::formatMinutes))
        OptionalRow(stringResource(id = R.string.sleep_awake), night.awakeMinutes?.let(::formatMinutes))
        OptionalRow(stringResource(id = R.string.sleep_window), window)
        OptionalRow(stringResource(id = R.string.sleep_heart_rate), heartRate)
        OptionalRow(stringResource(id = R.string.sleep_hrv), night.heartRateVariability?.toString())
        OptionalRow(stringResource(id = R.string.sleep_breathing), breathing)
        OptionalRow(stringResource(id = R.string.sleep_recharge_status), night.rechargeStatus?.toString())
        OptionalRow(stringResource(id = R.string.sleep_recharge_level), night.rechargeLevel?.toString())
    }
}

@Composable
private fun BloodOxygenSection(health: H5600HealthData) {
    val body = health.body ?: return
    val spo2 = spo2Summary(body.spo2)
    val sessions = body.breathingSessions
    if (spo2 == null && sessions.isEmpty()) return

    Section(title = stringResource(id = R.string.card_body)) {
        OptionalRow(
            stringResource(id = R.string.spo2_latest),
            spo2?.let { "${it.latest.percent} % (${it.latest.time.format(STAMP_FORMAT)})" },
        )
        OptionalRow(
            stringResource(id = R.string.spo2_range),
            spo2?.takeIf { it.count > 1 }?.let { "${it.min}\u2013${it.max} % (${it.count})" },
        )
        OptionalRow(
            stringResource(id = R.string.breathing_sessions),
            sessions.maxByOrNull { it.time }?.let { "${sessions.size} (${it.time.format(STAMP_FORMAT)})" },
        )
    }
}

@Composable
private fun CardioSection(health: H5600HealthData) {
    val cardio = health.body?.let(::cardioOf) ?: return
    Section(title = stringResource(id = R.string.card_cardio)) {
        OptionalRow(stringResource(id = R.string.cardio_load), cardio.loadRatio?.let { String.format(Locale.US, "%.2f", it) })
        OptionalRow(stringResource(id = R.string.cardio_status), cardio.feedback?.toString())
        OptionalRow(stringResource(id = R.string.cardio_strain), cardio.strain?.toString())
        OptionalRow(stringResource(id = R.string.cardio_tolerance), cardio.tolerance?.toString())
    }
}

@Composable
private fun DailyTotalsSection(health: H5600HealthData) {
    val summary = health.dailySummary
    val stairs = health.body?.let { stairsOfLatestDay(it.stairs) }
    if (summary == null && stairs == null) return

    Section(title = stringResource(id = R.string.card_daily)) {
        OptionalRow(stringResource(id = R.string.daily_calories), summary?.let { "${it.calories} kcal" })
        OptionalRow(stringResource(id = R.string.daily_resting_calories), summary?.restingCalories?.let { "$it kcal" })
        OptionalRow(stringResource(id = R.string.stairs_up_down), stairs?.let { "${it.first} / ${it.second}" })
    }
}

@Composable
private fun OptionalRow(
    label: String,
    value: String?,
) {
    if (value != null) InfoRow(label = label, value = value)
}
