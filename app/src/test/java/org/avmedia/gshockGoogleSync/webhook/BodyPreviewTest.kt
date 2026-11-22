package org.avmedia.gshockGoogleSync.webhook

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class BodyPreviewTest {
    private val known = setOf("watch.battery", "watch.name", "event")

    @Test fun knownValueIsFilledIn() {
        assertEquals("""{"b": 80}""", BodyPreview.render("""{"b": {{watch.battery}}}""", true, known, mapOf("watch.battery" to 80)))
    }

    @Test fun missingValueShowsItsNameInsteadOfAnError() {
        assertEquals("""{"b": "<watch.battery>"}""", BodyPreview.render("""{"b": {{watch.battery}}}""", true, known, emptyMap()))
        assertEquals("battery <watch.battery>", BodyPreview.render("battery {{watch.battery}}", false, known, emptyMap()))
    }

    @Test fun unknownNameIsAPlaceholderToo() {
        assertEquals("x <nope>", BodyPreview.render("x {{nope}}", false, known, emptyMap()))
        assertEquals("""{"x": "<nope>"}""", BodyPreview.render("""{"x": {{nope}}}""", true, known, emptyMap()))
    }

    @Test fun allUsesOnlyRealValues() {
        val out = BodyPreview.render("{{all}}", true, known, mapOf("watch.name" to "GBX"))
        assertEquals("""{"watch":{"name":"GBX"}}""", out)
        assertFalse(out.contains("<"))
    }

    @Test fun emptyTemplateIsEmpty() {
        assertEquals("", BodyPreview.render("", true, known, emptyMap()))
    }
}
