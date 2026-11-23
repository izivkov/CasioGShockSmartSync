package org.avmedia.gshockapi.casio

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/** Every request and write goes through the command table: only the queue wiring may touch the radio. */
class RequestSitesTest {
    private fun sources(): List<File> =
        listOf("src/main/java", "../app/src/main/java").flatMap { root ->
            File(root).walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        }

    private fun offenders(pattern: Regex, allowed: Set<String>): List<String> =
        sources().filter { it.name !in allowed }.filter { pattern.containsMatchIn(it.readText()) }.map { it.name }

    @Test fun sourcesAreFound() {
        assertTrue(sources().size > 50)
    }

    @Test fun noRawCharacteristicWriteOutsideTheManager() {
        val bad = offenders(Regex("""writeCharacteristic\("""), setOf("IGShockManager.kt"))
        assertTrue("raw GATT writes in: $bad", bad.isEmpty())
    }

    @Test fun nothingCallsConnectionWriteDirectly() {
        val bad = offenders(Regex("""Connection\.write\("""), emptySet())
        assertTrue("Connection.write used in: $bad", bad.isEmpty())
    }

    @Test fun theQueueIsEnteredOnlyFromTheFewFilesThatOwnIt() {
        val bad = offenders(Regex("""WatchLink\.(write|writeChecked)\("""), setOf("IO.kt", "ConnectionKeepAlive.kt", "NotificationSender.kt", "WatchQueue.kt"))
        assertTrue("WatchLink writes in: $bad", bad.isEmpty())
    }

    @Test fun theStandardLinkLossCharacteristicIsNeverUsed() {
        val bad = offenders(Regex("""LINK_LOSS_(SERVICE|ALERT)|GetSetMode\.LINK_LOSS|00002a06"""), setOf("RequestSitesTest.kt"))
        assertTrue("standard Link Loss characteristic used in: $bad", bad.isEmpty())
    }

    @Test fun everyWriteSiteNamesItsCharacteristicThroughGetSetMode() {
        val raw = Regex("""IO\.writeCmd\(\s*([A-Za-z.]+)\s*,""")
        val bad = sources().flatMap { f -> raw.findAll(f.readText()).map { f.name to it.groupValues[1] }.toList() }
            .filter { !it.second.startsWith("GetSetMode.") && it.second != "action.mode" }
        assertTrue("IO.writeCmd with another first argument: $bad", bad.isEmpty())
    }
}
