package org.avmedia.gshockGoogleSync.watchvalues

import android.content.Context
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import java.time.OffsetDateTime
import kotlinx.coroutines.runBlocking
import org.avmedia.gshockGoogleSync.ui.common.IWatchFeatureManager
import org.avmedia.gshockapi.IGShockAPI
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Real GBX-H5600 answers are the fixtures. */
class WatchDecodersTest {
    private val condition = "0x28 06 1D 01 00 FA 00 00 00 01"
    private val brussels = "0x1F 00 42 52 55 53 53 45 4C 53"
    private val garbage = "0x1F 01 6D 03 20 AA FF FF FF FF"
    private val dst = "0x1D 00 01 01 00 FA 00 00 00 FF FF FF FF FF"

    @Test
    fun conditionDecodesTemperatureAndSevenStepBattery() {
        val c = WatchDecoders.h5600Condition(condition)!!
        assertEquals(6, c.batteryLevel)
        assertEquals("HIGH", c.batteryLabel)
        assertEquals(100, c.batteryPercent)
        assertEquals(29, c.temperature)
    }

    @Test
    fun batteryDecodeTable() {
        val expected = listOf("CHG" to 0, "LOW" to 17, "1" to 33, "2" to 50, "3" to 67, "4" to 83, "HIGH" to 100)
        expected.forEachIndexed { level, (label, percent) ->
            val c = WatchDecoders.h5600Condition("0x28 %02X 14 00".format(level))!!
            assertEquals(level, c.batteryLevel)
            assertEquals(label, c.batteryLabel)
            assertEquals(percent, c.batteryPercent)
        }
    }

    @Test
    fun outOfRangeBatteryGivesNullNotABogusPercent() {
        for (bad in listOf(7, 8, 0x64, 0xFF)) {
            val c = WatchDecoders.h5600Condition("0x28 %02X 1D 01".format(bad))!!
            assertNull(c.batteryLevel)
            assertNull(c.batteryLabel)
            assertNull(c.batteryPercent)
            assertEquals(29, c.temperature)
        }
    }

    @Test
    fun shortWrongOrMissingAnswersGiveNull() {
        assertNull(WatchDecoders.h5600Condition(null))
        assertNull(WatchDecoders.h5600Condition(""))
        assertNull(WatchDecoders.h5600Condition("0x28 06"))
        assertNull(WatchDecoders.h5600Condition("0x29 06 1D 01"))
        assertNull(WatchDecoders.h5600Condition("0x28 zz 1D 01"))
    }

    @Test
    fun worldCityKeepsNamesOnlyAndDropsGarbage() {
        assertEquals("BRUSSELS", WatchDecoders.worldCityName(brussels, 0))
        assertNull(WatchDecoders.worldCityName(garbage, 1))
        assertNull(WatchDecoders.worldCityName("0x1F 6D 03 20 AA FF FF", 1))
        assertNull(WatchDecoders.worldCityName(brussels, 1)) // wrong slot
        assertNull(WatchDecoders.worldCityName("0x1F 02 00 00 00", 2))
        assertNull(WatchDecoders.worldCityName(null, 0))
    }

    @Test
    fun dstIsABooleanNotHex() {
        assertEquals(true, WatchDecoders.dstHomeOn(dst))
        assertEquals(false, WatchDecoders.dstHomeOn("0x1D 00 01 00 00 FA 00"))
        assertNull(WatchDecoders.dstHomeOn("0x1D 00"))
        assertNull(WatchDecoders.dstHomeOn("not hex"))
    }

    @Test
    fun rawHexDetection() {
        assertTrue(WatchDecoders.looksLikeRawHex("0x1D 00 01 01 00 FA"))
        assertTrue(WatchDecoders.looksLikeRawHex(" 0x28 06"))
        assertFalse(WatchDecoders.looksLikeRawHex("BRUSSELS"))
        assertFalse(WatchDecoders.looksLikeRawHex("0x is not data"))
        assertFalse(WatchDecoders.looksLikeRawHex("2 hours"))
    }

    private fun env(h5600: Boolean) = WatchVariables.Env(
        api = mockk<IGShockAPI>(relaxed = true).also { api ->
            coEvery { api.getDSTWatchState(any()) } returns dst
            for (i in 0..5) coEvery { api.getWorldCities(i) } returns (if (i == 0) brussels else garbage)
            coEvery { api.getHomeTime() } returns "N/A"
            coEvery { api.getBatteryLevel() } returns 42
        },
        features = mockk<IWatchFeatureManager>(relaxed = true).also {
            every { it.isFeatureSupported(any()) } returns true
            every { it.getAlarmCount() } returns 4
        },
        raw = RawRequester { code, _ -> if (code == 0x28) listOf(condition) else emptyList() },
        isH5600 = { h5600 },
        phoneBattery = { 80 },
        lastSync = { null },
    )

    @Test
    fun noVariableValueIsRawHexWithTheRecordedAnswers() = runBlocking {
        for (h5600 in listOf(true, false)) {
            val registry = WatchVariables.registry(env(h5600))
            val values = registry.resolve(VariableContext("test", OffsetDateTime.now(), true))
            val offenders = values.filterValues { it is String && it.trim().startsWith("0x", ignoreCase = true) }
            assertTrue("raw hex in $offenders", offenders.isEmpty())
            assertNull(values["watch.world_city.1"]) // garbage slot is left out
            assertNull(values["watch.home_city"]) // "N/A" is left out
            assertEquals(true, values["watch.dst.home"])
        }
    }

    @Test
    fun h5600VariablesComeFromTheRawConditionAndOthersFromTheLibrary() = runBlocking {
        val ctx = { VariableContext("test", OffsetDateTime.now(), true) }
        val h = WatchVariables.registry(env(true)).resolve(ctx())
        assertEquals(100, h["watch.battery"])
        assertEquals(6, h["watch.battery_level"])
        assertEquals("HIGH", h["watch.battery_label"])
        assertEquals(29, h["watch.temperature"])
        val other = WatchVariables.registry(env(false)).resolve(ctx())
        assertEquals(42, other["watch.battery"])
        assertNull(other["watch.battery_level"])
        assertNull(other["watch.battery_label"])
    }
}
