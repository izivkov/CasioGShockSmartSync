package org.avmedia.gshockapi.io

import org.avmedia.gshockapi.model.DailySummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class H5600DailySummaryTest {
    private fun body(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }

    @Test
    fun decodesStepsCaloriesAndTheLikelyRestingValue() {
        // u32 5306, u32 2400, u16 1700, little endian
        val b = body(0xBA, 0x14, 0, 0, 0x60, 0x09, 0, 0, 0xA4, 0x06)
        assertEquals(DailySummary(5306, 2400, 1700), H5600HealthDecoder.decodeDailySummary(b))
    }

    @Test
    fun withoutTheLastTwoBytesRestingIsUnknown() {
        assertEquals(DailySummary(1, 2, null), H5600HealthDecoder.decodeDailySummary(body(1, 0, 0, 0, 2, 0, 0, 0)))
    }

    @Test
    fun aShortOrMissingBodyGivesNothing() {
        assertNull(H5600HealthDecoder.decodeDailySummary(body(1, 2, 3)))
        assertNull(H5600HealthDecoder.decodeDailySummary(null))
    }
}
