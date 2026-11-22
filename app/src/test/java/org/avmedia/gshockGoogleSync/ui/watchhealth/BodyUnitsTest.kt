package org.avmedia.gshockGoogleSync.ui.watchhealth

import org.avmedia.gshockapi.io.LengthUnit
import org.junit.Assert.assertEquals
import org.junit.Test

class BodyUnitsTest {
    @Test fun kilometresShowCmAndKg() {
        assertEquals("175 cm", BodyUnits.heightText(175, LengthUnit.KM))
        assertEquals("79 kg", BodyUnits.weightText(79, LengthUnit.KM))
    }

    @Test fun milesShowInchesAndPounds() {
        assertEquals("69 in", BodyUnits.heightText(175, LengthUnit.MILES))
        assertEquals("174 lb", BodyUnits.weightText(79, LengthUnit.MILES))
    }

    @Test fun editedValuesGoBackToCmAndKg() {
        assertEquals(175, BodyUnits.heightStored(175, LengthUnit.KM))
        assertEquals(175, BodyUnits.heightStored(69, LengthUnit.MILES))
        assertEquals(79, BodyUnits.weightStored(174, LengthUnit.MILES))
    }

    @Test fun theDisplayRangesMatchTheStoredRanges() {
        assertEquals(39..98, BodyUnits.heightRange(LengthUnit.MILES))
        assertEquals(44..661, BodyUnits.weightRange(LengthUnit.MILES))
        assertEquals(100..250, BodyUnits.heightRange(LengthUnit.KM))
    }
}
