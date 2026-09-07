package org.avmedia.gshockGoogleSync.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class IntentParserTest {

    private lateinit var intentParser: IntentParser

    @Before
    fun setUp() {
        intentParser = IntentParser()
    }

    @Test
    fun testParseSetAlarm() {
        val result = intentParser.parse("wake me up at 6:30 am")
        assertEquals(VoiceCommand.SetAlarm(6, 30), result)

        val pmResult = intentParser.parse("set alarm for 7:15 pm")
        assertEquals(VoiceCommand.SetAlarm(19, 15), pmResult)
    }

    @Test
    fun testParseSetTimer() {
        val minResult = intentParser.parse("set a timer for 5 minutes")
        assertEquals(VoiceCommand.SetTimer(0, 5, 0), minResult)

        val hourResult = intentParser.parse("timer for 2 hours")
        assertEquals(VoiceCommand.SetTimer(2, 0, 0), hourResult)

        val secResult = intentParser.parse("set a timer for 30 seconds")
        assertEquals(VoiceCommand.SetTimer(0, 0, 30), secResult)
    }

    @Test
    fun testParseSetSetting() {
        val lightResult = intentParser.parse("enable auto light")
        assertEquals(VoiceCommand.SetSetting("auto light", true), lightResult)

        val turnOnResult = intentParser.parse("turn on auto light")
        assertEquals(VoiceCommand.SetSetting("auto light", true), turnOnResult)

        val powerResult = intentParser.parse("disable power saving")
        assertEquals(VoiceCommand.SetSetting("power saving", false), powerResult)

        val turnOffResult = intentParser.parse("turn off power saving")
        assertEquals(VoiceCommand.SetSetting("power saving", false), turnOffResult)
    }

    @Test
    fun testUnmatchedTextReturnsNull() {
        val result = intentParser.parse("hello world")
        assertNull(result)
    }
}
