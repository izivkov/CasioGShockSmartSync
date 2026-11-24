package org.avmedia.gshockGoogleSync.homeassistant

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Android's regex engine (ICU) rejects an unescaped `{` or `}` that is not a quantifier, while the JVM
 * accepts it, so a JVM unit test of the pattern itself can never see the crash. This scans the
 * regex literals in the Home Assistant sources instead.
 */
class RegexPortabilityTest {
    private val literal = Regex("""Regex\(\s*(?:\"\"\"(.*?)\"\"\"|"((?:[^"\\]|\\.)*)")""", RegexOption.DOT_MATCHES_ALL)

    /** Brace problems in [pattern]: a `{` or `}` that is not escaped and not a `{n}` / `{n,m}` quantifier. */
    internal fun problems(pattern: String): List<String> {
        val bad = ArrayList<String>()
        var i = 0
        while (i < pattern.length) {
            val c = pattern[i]
            when {
                c == '\\' -> i++ // skip the escaped character
                c == '{' -> {
                    val end = pattern.indexOf('}', i)
                    val quantifier = end > i && Regex("""\{\d+(,\d*)?\}""").matches(pattern.substring(i, end + 1))
                    if (quantifier) i = end else bad += "unescaped { at $i"
                }
                c == '}' -> bad += "unescaped } at $i"
            }
            i++
        }
        return bad
    }

    private fun haSources(): List<File> {
        // The watch values the plugins read.
        val dirs = listOf("src/main/java/org/avmedia/gshockGoogleSync/watchvalues").map(::File)
        val files = dirs.flatMap { dir -> dir.listFiles { f -> f.extension == "kt" }?.toList().orEmpty() }
        assertTrue("Home Assistant sources not found from ${File(".").absolutePath}", dirs.all { d -> files.any { it.parentFile == d } })
        return files
    }

    @Test
    fun checkerFlagsTheOldPlaceholderPatternAndAcceptsTheEscapedOne() {
        assertTrue(problems("""\{\{\s*([A-Za-z0-9_.\-]+)\s*}}""").isNotEmpty())
        assertTrue(problems("""\{\{\s*([A-Za-z0-9_.\-]+)\s*\}\}""").isEmpty())
        assertTrue(problems("""a{2,3}b{4}""").isEmpty())
    }

    @Test
    fun noRegexInTheHomeAssistantCodeHasAnUnescapedBrace() {
        val failures = ArrayList<String>()
        for (file in haSources()) {
            for (m in literal.findAll(file.readText())) {
                val pattern = m.groups[1]?.value ?: m.groups[2]?.value.orEmpty()
                problems(pattern).forEach { failures += "${file.name}: $it in /$pattern/" }
            }
        }
        assertTrue("Android (ICU) rejects these patterns:\n" + failures.joinToString("\n"), failures.isEmpty())
    }
}
