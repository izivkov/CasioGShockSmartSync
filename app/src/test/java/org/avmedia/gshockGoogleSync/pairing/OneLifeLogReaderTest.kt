package org.avmedia.gshockGoogleSync.pairing

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Before: after the upstream merge the coordinator read the LifeLog after every background connection and then
 * released the watch, next to our BackgroundHealthSync read of the same connection. The second read was refused as
 * "already running", counted as failed, and the coordinator disconnected the watch in the middle of the first one.
 * One reader starts background LifeLog reads (BackgroundHealthSync); the coordinator only connects.
 */
class OneLifeLogReaderTest {
    private val sources = File("src/main/java").walkTopDown().filter { it.extension == "kt" }.toList()

    private fun offenders(needle: String, allowed: Set<String>): List<String> =
        sources.filter { it.name !in allowed }.flatMap { f ->
            f.readLines().mapIndexedNotNull { i, line -> if (needle in line && !line.trimStart().startsWith("//")) "${f.path}:${i + 1}" else null }
        }

    @Test fun onlyTheKnownPlacesStartALifeLogRead() {
        assertTrue("sources not found from ${File(".").absolutePath}", sources.isNotEmpty())
        val allowed = setOf("BackgroundHealthSync.kt", "HealthViewModel.kt", "WatchVariables.kt", "ActionContainer.kt")
        val found = offenders("getStepCount(", allowed)
        assertTrue("start LifeLog reads through BackgroundHealthSync: $found", found.isEmpty())
    }

    @Test fun theCoordinatorNeverReleasesAConnectedWatch() {
        val coordinator = sources.single { it.name == "CompanionDeviceCoordinator.kt" }.readText()
        // The code (not the comments) between a successful connect and the first catch.
        val afterConnect = coordinator.substringAfter("repository.waitForConnection(normalizedAddress)").substringBefore("catch (")
            .lines().filterNot { it.trimStart().startsWith("//") }.joinToString("\n")
        assertTrue("no disconnect after a connection succeeded: $afterConnect", "disconnect(" !in afterConnect && "readLifeLogInBackground" !in afterConnect)
    }
}
