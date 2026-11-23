package org.avmedia.gshockGoogleSync

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Comments and docs describe the protocol in our own words, as behaviour seen on the watch. They never point at documents
 * a reader cannot see or describe code as copied from another app. Scans main, test and docs sources of both modules.
 */
class PrivateReferencesTest {
    private val pattern = Regex(
        listOf(
            """notes?,? section""", """\(notes""", """\bnotes [A-Z][0-9]?\b""", """the notes""", """casio-protocol-notes""",
            """Casio.s app""", """official app""", """decompil""", """jadx""", """\bDart\b""",
        ).joinToString("|"),
        RegexOption.IGNORE_CASE,
    )

    /** Upstream text (izivkov), kept word for word so merges from upstream stay clean. Path to the exact trimmed line. */
    private val upstream = setOf(
        "README.md" to "The app communicates directly with the watch via BLE. Connection is typically much faster than the official app (~3.5s vs 12s).",
        "fastlane/metadata/android/en-US/full_description.txt" to "1. Connection Screen: Handles the Bluetooth connection via BLE. Connection is typically much faster than the official app (~3.5s vs 12s).",
        "api/src/main/java/org/avmedia/gshockapi/io/AppInfoIO.kt" to "* The value is a hard-coded magic number (0x93), matching the official app behavior.",
        "api/src/main/java/org/avmedia/gshockapi/io/H5600HealthIO.kt" to "// The official app and the Python script write START before the first poll. The watch",
        "api/src/main/java/org/avmedia/gshockapi/io/H5600LifeLogSync.kt" to "// busy: the official app waits about 3.5 s before polling again",
        "api/src/main/java/org/avmedia/gshockapi/io/H5600LifeLogSync.kt" to "/** Setup requests, in the order the official app sends them. Each one is answered on STATUS. */",
        "api/src/main/java/org/avmedia/gshockapi/io/H5600LifeLogSync.kt" to "/** Replies the official app writes back unchanged to STATUS. */",
        "api/src/main/java/org/avmedia/gshockapi/io/H5600SettingsIO.kt" to "* DW-H5600 (module 3516 family) settings decoded from the official app's BLE traffic.",
        "api/src/main/java/org/avmedia/gshockapi/io/H5600SettingsIO.kt" to "/** CASIO_CURRENT_TIME_MANAGER. The official app requests it after connecting and writes the reply back. */",
        "api/src/main/java/org/avmedia/gshockapi/io/H5600SettingsIO.kt" to "* Without it the watch drops Smart Sync's connection after ~45 s; with the official app it stays",
        "api/src/main/java/org/avmedia/gshockapi/io/H5600SettingsIO.kt" to "/** Read-only: reads packets the official app reads on every connect, for logging/comparison. */",
        "api/src/main/java/org/avmedia/gshockapi/io/H5600SettingsIO.kt" to "* as the current official app writes it (user's capture 2026-11-16 23:08; izivkov's older capture",
        "api/src/main/java/org/avmedia/gshockapi/io/H5600SettingsIO.kt" to "* before the city names (1F) and the time (09): the exact place the official app writes 0x24,",
        "api/src/main/java/org/avmedia/gshockapi/model/H5600Settings.kt" to "/** DW-H5600 watch-face display item: bytes[2..5] of packet 0x51 (codes from the official app, 2026-11-16). */",
        "app/src/main/java/org/avmedia/gshockGoogleSync/pairing/CompanionDeviceCoordinator.kt" to "// Like the official app: while the watch is advertising, retry quickly. A refused",
    )

    private val textExtensions = setOf("kt", "kts", "java", "md", "yml", "yaml", "gradle", "txt", "json", "xml", "properties")

    @Test
    fun noSourceOrDocPointsAtPrivateNotesOrAnotherApp() {
        val repo = File("..").canonicalFile
        val roots = listOf("app/src", "api/src", "docs", "fastlane", ".github").map { File(repo, it) } +
            repo.listFiles().orEmpty().filter { it.isFile && it.extension in setOf("md", "gradle", "yml") }
        val self = File(repo, "app/src/test/java/org/avmedia/gshockGoogleSync/PrivateReferencesTest.kt").canonicalFile
        val files = roots.flatMap { r -> r.walkTopDown().filter { it.isFile && it.extension in textExtensions }.toList() }
        assertTrue("the scan found too few files, wrong working directory? ${repo.path}", files.size > 200)
        val hits = files.filter { it.canonicalFile != self }.flatMap { f ->
            val rel = f.relativeTo(repo).invariantSeparatorsPath
            f.readLines().withIndex()
                .filter { (_, line) -> pattern.containsMatchIn(line) && (rel to line.trim()) !in upstream }
                .map { (i, line) -> "$rel:${i + 1}: ${line.trim()}" }
        }
        assertTrue("Describe the protocol in our own words, without pointing at notes or another app:\n" +
            hits.joinToString("\n"), hits.isEmpty())
    }
}
