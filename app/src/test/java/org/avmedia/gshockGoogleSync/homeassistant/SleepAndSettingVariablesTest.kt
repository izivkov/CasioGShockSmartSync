package org.avmedia.gshockGoogleSync.homeassistant

import org.avmedia.gshockGoogleSync.watchvalues.HealthVariables
import org.avmedia.gshockGoogleSync.watchvalues.SleepVariables
import org.avmedia.gshockGoogleSync.watchvalues.WatchSettingVariables
import org.avmedia.gshockGoogleSync.watchvalues.WatchVariables
import java.time.LocalDate
import java.time.LocalDateTime
import org.avmedia.gshockapi.model.BodyReadings
import org.avmedia.gshockapi.model.CardioStatus
import org.avmedia.gshockapi.model.H5600HealthData
import org.avmedia.gshockapi.model.SleepData
import org.avmedia.gshockapi.model.SleepNight
import org.avmedia.gshockapi.model.SleepStage
import org.avmedia.gshockapi.model.Spo2Reading
import org.avmedia.gshockapi.model.StairsRecord
import org.avmedia.gshockapi.model.StepCounterData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Sleep, body and decoded setting variables and their Home Assistant sensors. */
class SleepAndSettingVariablesTest {
    private val start = LocalDateTime.of(2026, 11, 21, 23, 10)

    private val night = SleepNight(
        date = LocalDate.of(2026, 11, 21), zoneQuarterHours = -16, sleepScore = 82, rechargeStatus = 3, rechargeLevel = 4,
        fellAsleep = start, wokeUp = LocalDateTime.of(2026, 11, 22, 6, 22), totalSleepMinutes = 432, deepMinutes = 80,
        lightMinutes = 252, remMinutes = 100, awakeMinutes = 18, heartRateNight = 52, heartRateBaseline = 55,
        heartRateVariability = 48, breathingRate = 14.5f, breathingBaseline = 15f,
    )

    private val data = StepCounterData(
        hourlySteps = emptyList(), dailyHistory = emptyList(), currentDaySteps = null,
        h5600 = H5600HealthData(
            sleep = SleepData(night, listOf(night), listOf(SleepStage(start, "light"), SleepStage(start.plusMinutes(40), "deep")), start),
            body = BodyReadings(
                spo2 = listOf(Spo2Reading(LocalDateTime.of(2026, 11, 22, 7, 45), 97)),
                cardio = CardioStatus(LocalDate.of(2026, 11, 21), 1.25f, 2, 310, 420),
                stairs = listOf(StairsRecord(LocalDateTime.of(2026, 11, 22, 8, 0), 12, 9)),
            ),
        ),
    )

    @Test
    fun mapsTheNightToVariables() {
        val v = HealthVariables.values(data)
        assertEquals("2026-11-21", v["health.sleep_date"])
        assertEquals(82, v["health.sleep_score"])
        assertEquals(3, v["health.recharge_status"])
        assertEquals(4, v["health.recharge_level"])
        assertEquals("2026-11-21T23:10:00", v["health.sleep_fell_asleep"])
        assertEquals("2026-11-22T06:22:00", v["health.sleep_woke_up"])
        assertEquals(432, v["health.sleep_total_min"])
        assertEquals(80, v["health.sleep_deep_min"])
        assertEquals(252, v["health.sleep_light_min"])
        assertEquals(100, v["health.sleep_rem_min"])
        assertEquals(18, v["health.sleep_awake_min"])
        assertEquals(52, v["health.sleep_hr"])
        assertEquals(14.5f, v["health.sleep_breathing"])
        assertEquals(
            listOf(mapOf("time" to "2026-11-21T23:10:00", "stage" to "light"), mapOf("time" to "2026-11-21T23:50:00", "stage" to "deep")),
            v["health.sleep_stages"],
        )
    }

    @Test
    fun mapsBloodOxygenCardioAndStairs() {
        val v = HealthVariables.values(data)
        assertEquals(97, v["health.spo2"])
        assertEquals("2026-11-22T07:45:00", v["health.spo2_at"])
        assertEquals(1.25f, v["health.cardio_load_ratio"])
        assertEquals(2, v["health.cardio_feedback"])
        assertEquals(310, v["health.cardio_strain"])
        assertEquals(420, v["health.cardio_tolerance"])
        assertEquals(12, v["health.stairs_up"])
        assertEquals(9, v["health.stairs_down"])
    }

    @Test
    fun aTransferWithoutANightGivesNullsNotZeros() {
        val empty = data.copy(h5600 = H5600HealthData(sleep = SleepData(null, emptyList(), emptyList(), null), body = BodyReadings()))
        val v = HealthVariables.values(empty)
        SleepVariables.DESCRIPTIONS.keys.forEach { assertNull("$it must be null", v[it]) }
    }

    @Test
    fun theUncertainSleepTimesAreMarkedUnconfirmed() {
        for (name in listOf("health.sleep_fell_asleep", "health.sleep_woke_up", "health.sleep_stages", "health.sleep_date")) {
            assertTrue(name, HealthVariables.DESCRIPTIONS.getValue(name).contains("unconfirmed", ignoreCase = true))
        }
        assertEquals(HealthVariables.NAMES.toSet(), HealthVariables.DESCRIPTIONS.keys)
    }

    @Test
    fun sleepSensorsCarryStatesTimestampsAndStages() {
        val zone = java.time.ZoneId.of("Europe/Brussels")
        val values = HealthVariables.values(data)
        val states = HaSensors.states(values, zone = zone)
        assertEquals(82, states["sleep_score"])
        assertEquals("2026-11-21T23:10+01:00", states["sleep_fell_asleep"])
        assertEquals(432, states["sleep_total"])
        val attrs = HaSensors.attributes(values)
        assertEquals(2, (attrs.getValue("sleep_total")["stages"] as List<*>).size)
        assertEquals(4, attrs.getValue("recharge_status")["level"])
        assertEquals(310, attrs.getValue("cardio_load")["strain"])
    }

    @Test
    fun sleepSensorsAreLeftOutWithoutValues() {
        val states = HaSensors.states(HealthVariables.values(null))
        assertFalse(states.keys.any { it.startsWith("sleep_") || it == "spo2" || it == "cardio_load" })
    }

    @Test
    fun everyNewVariableIsInTheRegistry() {
        val names = WatchVariables.registry(fakeEnv()).names
        SleepVariables.DESCRIPTIONS.keys.forEach { assertTrue("$it missing", it in names) }
        WatchSettingVariables.DEFS.forEach { assertTrue("${it.name} missing", it.name in names) }
        HaSensors.ALL.forEach { assertTrue("${it.variable} is not a variable", it.variable in names) }
        HaSensors.ALL.forEach { s -> s.attributes.values.forEach { assertTrue("$it is not a variable", it in names) } }
    }

    // decoded settings

    private val raw = mapOf(
        "0x51" to "51 02 00 0A 0C 13",
        "0x41" to "41 16 02 78 82",
        "0x4F" to "4F 03 17 0F 06 2D 01 BE 3C 2D",
        "0x13" to "13 01 02 00 01 00 00 00 00 00 03 00 22 16 1E 06 0F",
        "0x43" to "43 10 27 00 09",
        "0x45" to "45 00 8F FE 8F FF 6F E6 FE FE",
        "0x50" to "50 06 1E 03 1E",
    )

    private fun value(name: String) = WatchSettingVariables.DEFS.first { it.name == name }.value(WatchSettingVariables.frames(raw))

    @Test
    fun decodesTheFaceAndItsItems() {
        assertEquals(2, value("watch.face_index"))
        assertEquals("Steps", value("watch.face_item.1"))
        assertEquals("Heart rate", value("watch.face_item.2"))
        assertEquals("Sleep score and level", value("watch.face_item.3"))
        assertEquals("Moon age", value("watch.face_item.4"))
    }

    @Test
    fun decodesTheHealthSettings() {
        assertEquals("always", value("watch.hr_measurement"))
        assertEquals("23:15", value("watch.bedtime"))
        assertEquals("06:45", value("watch.wake_up_time"))
        assertEquals("22:30", value("watch.night_start"))
        assertEquals("06:15", value("watch.night_end"))
        assertEquals(true, value("watch.step_reminder"))
        assertEquals("km", value("watch.length_unit"))
        assertEquals(10000, value("watch.step_goal"))
        assertEquals(170, value("watch.profile_height_cm"))
        assertEquals(70, value("watch.profile_weight_kg"))
        assertEquals("06:30", value("watch.smart_alarm_time"))
        assertEquals(true, value("watch.smart_alarm_on"))
        assertEquals(30, value("watch.smart_alarm_window_min"))
    }

    @Test
    fun aMissingOrForeignAnswerGivesNullNotAGuess() {
        val frames = WatchSettingVariables.frames(mapOf("0x41" to "41 07", "0x4F" to "13 01 02", "0x51" to "garbage"))
        WatchSettingVariables.DEFS.forEach { assertNull("${it.name}", it.value(frames)) }
    }

    @Test
    fun theDecodedSettingsAreOnTheSensorsToo() {
        val values = mapOf<String, Any?>("watch.face_index" to 2, "watch.face_item.1" to "Steps", "watch.bedtime" to "23:15", "watch.wake_up_time" to "06:45")
        assertEquals(2, HaSensors.states(values)["watch_face"])
        assertEquals("Steps", HaSensors.attributes(values).getValue("watch_face")["slot_1"])
        assertEquals("06:45", HaSensors.attributes(values).getValue("bedtime")["wake_up"])
    }

    private fun fakeEnv(): WatchVariables.Env = WatchVariables.Env(
        api = io.mockk.mockk(relaxed = true),
        features = io.mockk.mockk(relaxed = true),
        raw = io.mockk.mockk(relaxed = true),
        isH5600 = { true },
        phoneBattery = { 50 },
        lastSync = { null },
    )
}
