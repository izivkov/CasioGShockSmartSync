package org.avmedia.gshockGoogleSync.ui.watchhealth

import org.avmedia.gshockapi.io.ProfileCodec
import org.junit.Assert.assertEquals
import org.junit.Test

class BodyUnitsTest {
    private fun p(imperial: Boolean, lb: Boolean) =
        ProfileCodec.Profile(70, 180, 1985, 3, 14, true, imperialLength = imperial, weightNotKg = lb)

    @Test fun metricFlagsShowCmAndKg() {
        assertEquals("70 cm", BodyUnits.heightText(p(false, false)))
        assertEquals("180 kg", BodyUnits.weightText(p(false, false)))
    }

    @Test fun imperialFlagsShowInchesAndPounds() {
        assertEquals("70 in", BodyUnits.heightText(p(true, true)))
        assertEquals("180 lb", BodyUnits.weightText(p(true, true)))
    }

    @Test fun theEditRangesFollowTheFlags() {
        assertEquals(39..98, BodyUnits.heightRange(p(true, false)))
        assertEquals(100..250, BodyUnits.heightRange(p(false, false)))
        assertEquals(44..661, BodyUnits.weightRange(p(false, true)))
        assertEquals(20..300, BodyUnits.weightRange(p(false, false)))
    }
}
