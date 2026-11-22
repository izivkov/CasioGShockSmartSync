package org.avmedia.gshockGoogleSync.history

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutExportTest {
    private val b = WorkoutMapper.map(WorkoutTestData.session(kcal = null), 1L)

    @Test fun csvHasThreeTablesAndGapsAreEmptyCells() {
        val lines = WorkoutExport.csv(listOf(b.workout), b.laps, b.samples).lines()
        assertEquals("# workouts", lines[0])
        assertTrue(lines[1].startsWith("start_utc_ms,sport,sport_name,end_utc_ms,total_sec,distance_unconfirmed,"))
        assertEquals("1763800000000,1,Running,1763801800000,1800,5.25,343,,,142,171,98,,,,2,false", lines[2])
        assertEquals("", lines[3])
        assertEquals("# laps", lines[4])
        assertEquals("1763800000000,1,0,600,true,1.75,340,,140,160,,,,", lines[6])
        assertEquals("# samples", lines[9])
        assertEquals("1763800000000,1,2,20,false,332,,,,,,,", lines[13])
    }

    @Test fun jsonKeepsGapsAsNullAndNamesTheUnconfirmedUnits() {
        val o = JsonParser.parseString(WorkoutExport.json(listOf(b.workout), b.laps, b.samples)).asJsonObject
        assertTrue(o.getAsJsonArray("workouts")[0].asJsonObject.get("kcal").isJsonNull)
        assertEquals(2, o.getAsJsonArray("laps").size())
        assertEquals(4, o.getAsJsonArray("samples").size())
        assertTrue(o.getAsJsonObject("unconfirmed").get("distance").asString.contains("unconfirmed"))
    }
}
