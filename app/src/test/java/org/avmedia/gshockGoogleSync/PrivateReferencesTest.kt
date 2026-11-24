package org.avmedia.gshockGoogleSync

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Comments and docs describe the protocol in our own words, as behaviour seen on the watch. They never point at documents
 * a reader cannot see or describe code as copied from another app. Scans the app sources and docs; the library (gshockapi
 * submodule) has the same test in its own repository.
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
        "app/src/main/java/org/avmedia/gshockGoogleSync/pairing/CompanionDeviceCoordinator.kt" to "// Like the official app: while the watch is advertising, retry quickly. A refused",
    )

    /**
     * Where a decoding or a fix came from: owner or user logs, screenshots, dumps and hints, dated logs, recordings and
     * captures, decode dates, analysis script names. The comment says what the watch does, not who saw it when.
     */
    private val provenance = Regex(
        listOf(
            """\bowner'?s?\s+(log|logs|screenshot|screenshots|dump|hint|test|reads|watch|capture)\b""",
            """\b(owner|user)\b,?\s+20\d\d-\d\d-\d\d""", """\((owner|user),?\s*20\d\d""",
            """\blogs?\s+(of\s+)?20\d\d-\d\d-\d\d""", """\blogs?\s+\d\d:\d\d""",
            """\b(recording|recorded|capture|captured|decoded|matched|seen|measured|brief)\b(\s+\S+){0,3}\s+20\d\d-\d\d-\d\d""",
            """\b(recording|recorded|capture|captured|decoded)\b(\s+\S+){0,3}\s+\d{1,2} (January|February|March|April|May|June|July|August|September|October|November|December) 20\d\d""",
            """\buser'?s (capture|log|screenshot|watch)\b""", """\(20\d\d-\d\d-\d\d:""", """parse_\w+\.py""",
            """\bscreenshots? 20\d\d""",
        ).joinToString("|"),
        RegexOption.IGNORE_CASE,
    )

    /** Upstream text (izivkov) that names its own captures and decode dates, kept word for word. */
    private val upstreamProvenance = setOf(
    )

    private val textExtensions = setOf("kt", "kts", "java", "md", "yml", "yaml", "gradle", "txt", "json", "xml", "properties")

    @Test
    fun noSourceOrDocPointsAtPrivateNotesOrAnotherApp() {
        val hits = scan(pattern, upstream)
        assertTrue("Describe the protocol in our own words, without pointing at notes or another app:\n" +
            hits.joinToString("\n"), hits.isEmpty())
    }

    @Test
    fun noCommentSaysWhereADecodingCameFrom() {
        val hits = scan(provenance, upstreamProvenance)
        assertTrue("Say what the watch does, without owner/user logs, screenshots, recordings or decode dates:\n" +
            hits.joinToString("\n"), hits.isEmpty())
    }

    private fun scan(regex: Regex, allowed: Set<Pair<String, String>>): List<String> {
        val repo = File("..").canonicalFile
        val roots = listOf("app/src", "docs", "fastlane", ".github").map { File(repo, it) } +
            repo.listFiles().orEmpty().filter { it.isFile && it.extension in setOf("md", "gradle", "yml") }
        val self = File(repo, "app/src/test/java/org/avmedia/gshockGoogleSync/PrivateReferencesTest.kt").canonicalFile
        val files = roots.flatMap { r -> r.walkTopDown().filter { it.isFile && it.extension in textExtensions }.toList() }
        assertTrue("the scan found too few files, wrong working directory? ${repo.path}", files.size > 200)
        return files.filter { it.canonicalFile != self }.flatMap { f ->
            val rel = f.relativeTo(repo).invariantSeparatorsPath
            f.readLines().withIndex()
                .filter { (_, line) -> regex.containsMatchIn(line) && (rel to line.trim()) !in allowed }
                .map { (i, line) -> "$rel:${i + 1}: ${line.trim()}" }
        }
    }
}
