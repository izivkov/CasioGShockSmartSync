package org.avmedia.gshockGoogleSync.webhook

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RetryingHaTransportTest {
    private val waits = ArrayList<Long>()
    private val lines = ArrayList<String>()

    @Before fun install() {
        CallRetry.sleeper = { waits += it }
        CallRetry.writer = { _, text -> lines += text }
    }

    @After fun restore() {
        CallRetry.sleeper = { Thread.sleep(it) }
        CallRetry.writer = org.avmedia.gshockapi.log.AppLog::log
    }

    private class Fake(val answers: List<HaResponse>) : HaTransport {
        var posts = 0
        var gets = 0
        override fun post(url: String, body: String, bearer: String?) = answers[minOf(posts++, answers.size - 1)]
        override fun get(url: String, bearer: String?) = answers[minOf(gets++, answers.size - 1)]
    }

    private val sensors = """{"type":"update_sensor_states","data":[]}"""
    private val down = HaResponse(null, "UnknownHostException: ha.local")

    @Test fun networkErrorTwiceThenSuccess() {
        val inner = Fake(listOf(down, down, HaResponse(200, "{}")))
        assertEquals(200, RetryingHaTransport(inner).post("http://ha/api/webhook/ID", sensors, null).status)
        assertEquals(3, inner.posts)
        assertEquals(listOf("Home Assistant update sensors: sent after 3 tries"), lines)
    }

    @Test fun fourFailuresLogOneLineWithKindAndReasonNoUrlNoToken() {
        val inner = Fake(listOf(down))
        RetryingHaTransport(inner).post("http://ha.example/api/mobile_app/registrations", "{}", "TOKENSECRET")
        assertEquals(4, inner.posts)
        assertEquals(listOf("Home Assistant register: failed after 4 tries: host not reachable"), lines)
        assertFalse(lines.joinToString().contains("TOKENSECRET") || lines.joinToString().contains("ha.example"))
    }

    @Test fun http400IsOneAttempt() {
        val inner = Fake(listOf(HaResponse(400, "bad")))
        RetryingHaTransport(inner).post("http://ha/api/webhook/ID", """{"type":"fire_event"}""", null)
        assertEquals(1, inner.posts)
        assertEquals(listOf("Home Assistant fire event: failed after 1 try: HTTP 400"), lines)
    }

    @Test fun http410IsNotRetriedAndNotLoggedSoTheClientCanReRegister() {
        val inner = Fake(listOf(HaResponse(410, "")))
        val r = RetryingHaTransport(inner).post("http://ha/api/webhook/ID", sensors, null)
        assertEquals(410, r.status)
        assertEquals(1, inner.posts)
        assertTrue(waits.isEmpty() && lines.isEmpty())
    }

    @Test fun aGetIsOneTry() {
        val inner = Fake(listOf(down))
        RetryingHaTransport(inner).get("http://ha/api/", "t")
        assertEquals(1, inner.gets)
        assertTrue(waits.isEmpty())
    }

    @Test fun kindsNameTheCallNotTheUrl() {
        fun k(body: String, url: String = "http://ha/api/webhook/ID") = RetryingHaTransport.kindOf(url, body)
        assertEquals("register", k("{}", "http://ha/api/mobile_app/registrations"))
        assertEquals("register", k("""{"type":"register_sensor"}"""))
        assertEquals("update sensors", k(sensors))
        assertEquals("fire event", k("""{"type":"fire_event"}"""))
        assertEquals("action", k("{}", "http://ha/api/services/light/turn_on"))
    }
}
