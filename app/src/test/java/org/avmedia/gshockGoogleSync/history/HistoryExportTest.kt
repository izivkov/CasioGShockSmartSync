package org.avmedia.gshockGoogleSync.history

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryExportTest {
    private val days = listOf(DayRow("2026-11-20", steps = 5000, kcal = 310), DayRow("2026-11-21"))
    private val hours = listOf(HourRow("2026-11-20", 8, steps = 800))
    private val nights = listOf(NightRow("2026-11-20", score = 82, stages = "2026-11-20T00:10:00,light\n2026-11-20T01:00:00,deep"))

    @Test fun csvHasThreeTablesAndGapsAreEmptyCells() {
        val lines = HistoryExport.csv(days, hours, nights).lines()
        assertEquals("# days", lines[0])
        assertEquals("date,steps,distance_m,kcal,active_min,walk_sec,run_sec,still_sec,hr_rest,hr_min,hr_max,spo2,stairs_up,stairs_down", lines[1])
        assertEquals("2026-11-20,5000,,310,,,,,,,,,,", lines[2])
        assertEquals("2026-11-21,,,,,,,,,,,,,", lines[3])
        assertEquals("", lines[4])
        assertEquals("# hours", lines[5])
        assertEquals("date,hour,steps,hr", lines[6])
        assertEquals("2026-11-20,8,800,", lines[7])
        assertEquals("# nights", lines[9])
        assertTrue(lines.any { it.startsWith("2026-11-20,82,,,,,,,,,,,,\"2026-11-20T00:10:00,light") })
    }

    @Test fun jsonKeepsGapsAsNull() {
        val o = JsonParser.parseString(HistoryExport.json(days, hours, nights)).asJsonObject
        assertEquals(2, o.getAsJsonArray("days").size())
        val second = o.getAsJsonArray("days")[1].asJsonObject
        assertTrue(second.get("steps").isJsonNull)
        assertEquals(5000, o.getAsJsonArray("days")[0].asJsonObject.get("steps").asInt)
        assertEquals(1, o.getAsJsonArray("hours").size())
        assertEquals(82, o.getAsJsonArray("nights")[0].asJsonObject.get("score").asInt)
    }
}
