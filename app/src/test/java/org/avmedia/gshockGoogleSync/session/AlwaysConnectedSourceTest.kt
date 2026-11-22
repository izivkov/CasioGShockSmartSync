package org.avmedia.gshockGoogleSync.session

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The library's WatchInfo.isAlwaysConnected(name) says false for "GBX-H5600" (it resolves it to the
 * button-connect GBD), so a screen that asks the library hides the "Allow connection" switch and the
 * app waits forever on the first page (owner, 2026-11-22). App code must ask [WatchModelOverride].
 */
class AlwaysConnectedSourceTest {
    @Test
    fun appCodeAsksTheOverrideNotTheLibrary() {
        val root = File("src/main/java")
        val files = root.walkTopDown().filter { it.extension == "kt" }.toList()
        assertTrue("sources not found from ${File(".").absolutePath}", files.isNotEmpty())
        val offenders = files.flatMap { f ->
            f.readLines().mapIndexedNotNull { i, line ->
                if ("WatchInfo.isAlwaysConnected(" in line) "${f.path}:${i + 1}" else null
            }
        }
        assertTrue("use WatchModelOverride.isAlwaysConnected: $offenders", offenders.isEmpty())
    }
}
