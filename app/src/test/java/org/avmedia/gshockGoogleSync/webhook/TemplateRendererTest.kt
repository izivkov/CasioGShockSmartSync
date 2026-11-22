package org.avmedia.gshockGoogleSync.webhook

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TemplateRendererTest {
    private val known = setOf("event", "watch.name", "watch.battery", "watch.settings.font", "alarms.list", "note")

    private fun json(template: String, values: Map<String, Any?>) =
        TemplateRenderer.render(template, values, known, json = true)

    private fun plain(template: String, values: Map<String, Any?>) =
        TemplateRenderer.render(template, values, known, json = false)

    @Test
    fun jsonModeQuotesStringsAndKeepsNumbers() {
        val r = json("""{"n": {{watch.name}}, "b": {{watch.battery}}}""", mapOf("watch.name" to "GW-B5600", "watch.battery" to 80))
        assertEquals("""{"n": "GW-B5600", "b": 80}""", r.text)
        assertTrue(r.unknown.isEmpty())
    }

    @Test
    fun missingValueIsNullInJsonAndEmptyInPlainText() {
        val values = mapOf<String, Any?>("watch.name" to null)
        val j = json("""{"n": {{watch.name}}}""", values)
        assertEquals("""{"n": null}""", j.text)
        assertEquals(listOf("watch.name"), j.missing)
        val p = plain("name=[{{watch.name}}]", values)
        assertEquals("name=[]", p.text)
        assertEquals(listOf("watch.name"), p.missing)
    }

    @Test
    fun quotesBackslashesAndNewlinesAreEscapedInJson() {
        val nasty = "He said \"hi\"\nline2\\end\ttab"
        val r = json("""{"note": {{note}}}""", mapOf("note" to nasty))
        // The result must be valid JSON that decodes back to the same string.
        val decoded = JsonParser.parseString(r.text).asJsonObject.get("note").asString
        assertEquals(nasty, decoded)
        assertTrue("raw newline must not appear", !r.text.contains('\n'))
    }

    @Test
    fun plainTextModeInsertsRawValue() {
        val r = plain("{{watch.name}} says \"{{note}}\"", mapOf("watch.name" to "DW-H5600", "note" to "a\nb"))
        assertEquals("DW-H5600 says \"a\nb\"", r.text)
    }

    @Test
    fun allIsNestedByDotsAndLeavesOutNulls() {
        val values = mapOf<String, Any?>(
            "event" to "sync",
            "watch.name" to "GW-B5600",
            "watch.settings.font" to "Classic",
            "watch.battery" to null,
        )
        val r = json("{{all}}", values)
        val obj = JsonParser.parseString(r.text).asJsonObject
        assertEquals("sync", obj.get("event").asString)
        val watch = obj.getAsJsonObject("watch")
        assertEquals("GW-B5600", watch.get("name").asString)
        assertEquals("Classic", watch.getAsJsonObject("settings").get("font").asString)
        assertTrue("null values are left out", !watch.has("battery"))
    }

    @Test
    fun allWorksInsideAnObjectTemplate() {
        val r = json("""{"merge_variables": {{all}}, "merge_strategy": "deep_merge"}""", mapOf("event" to "sync"))
        val obj = JsonParser.parseString(r.text).asJsonObject
        assertEquals("sync", obj.getAsJsonObject("merge_variables").get("event").asString)
        assertEquals("deep_merge", obj.get("merge_strategy").asString)
    }

    @Test
    fun listValuesBecomeJsonArrays() {
        val alarms = listOf(mapOf("time" to "07:30", "enabled" to true))
        val r = json("""{{alarms.list}}""", mapOf("alarms.list" to alarms))
        assertEquals("""[{"time":"07:30","enabled":true}]""", r.text)
    }

    @Test
    fun unknownVariableIsReportedAndRendersAsNullOrEmpty() {
        val j = json("""{"x": {{nope.nothing}}, "e": {{event}}}""", mapOf("event" to "test"))
        assertEquals("""{"x": null, "e": "test"}""", j.text)
        assertEquals(listOf("nope.nothing"), j.unknown)
        val p = plain("a{{nope}}b", emptyMap())
        assertEquals("ab", p.text)
        assertEquals(listOf("nope"), p.unknown)
    }

    @Test
    fun whitespaceInsidePlaceholderIsAllowed() {
        assertEquals("""{"e": "sync"}""", json("""{"e": {{ event }}}""", mapOf("event" to "sync")).text)
    }

    @Test
    fun namesInListsEachNameOnce() {
        assertEquals(listOf("event", "watch.name"), TemplateRenderer.namesIn("{{event}} {{watch.name}} {{event}}"))
    }

    @Test
    fun contentTypeDecidesJsonMode() {
        assertTrue(TemplateRenderer.isJsonContentType("application/json"))
        assertTrue(TemplateRenderer.isJsonContentType("application/json; charset=utf-8"))
        assertTrue(TemplateRenderer.isJsonContentType("application/vnd.api+json"))
        assertTrue(!TemplateRenderer.isJsonContentType("text/plain"))
    }
}
