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

        val atResult = intentParser.parse("set alarm at 6:30am")
        assertEquals(VoiceCommand.SetAlarm(6, 30), atResult)

        val apTypoResult = intentParser.parse("set alarm at 6:30ap")
        assertEquals(VoiceCommand.SetAlarm(6, 30), apTypoResult)
    }

    @Test
    fun testParseClearAllAlarms() {
        assertEquals(VoiceCommand.ClearAllAlarms, intentParser.parse("clear all alarms"))
        assertEquals(VoiceCommand.ClearAllAlarms, intentParser.parse("clear alarms"))
    }

    @Test
    fun testParseDisableAllAlarms() {
        assertEquals(VoiceCommand.DisableAllAlarms, intentParser.parse("disable all alarms"))
        assertEquals(VoiceCommand.DisableAllAlarms, intentParser.parse("disable alarms"))
        assertEquals(VoiceCommand.DisableAllAlarms, intentParser.parse("turn off all alarms"))
        assertEquals(VoiceCommand.DisableAllAlarms, intentParser.parse("stop alarms"))
    }

    @Test
    fun testParseSetTimer() {
        val minResult = intentParser.parse("set a timer for 5 minutes")
        assertEquals(VoiceCommand.SetTimer(0, 5, 0), minResult)

        val toResult = intentParser.parse("Set timer to 5 minutes")
        assertEquals(VoiceCommand.SetTimer(0, 5, 0), toResult)

        val hourResult = intentParser.parse("timer for 2 hours")
        assertEquals(VoiceCommand.SetTimer(2, 0, 0), hourResult)

        val secResult = intentParser.parse("set a timer for 30 seconds")
        assertEquals(VoiceCommand.SetTimer(0, 0, 30), secResult)
    }

    @Test
    fun testParseSetSetting() {
        val lightResult = intentParser.parse("enable auto light")
        assertEquals(VoiceCommand.SetSetting("auto light", "true"), lightResult)

        val turnOnResult = intentParser.parse("turn on auto light")
        assertEquals(VoiceCommand.SetSetting("auto light", "true"), turnOnResult)

        val setOnResult = intentParser.parse("Set auto light on")
        assertEquals(VoiceCommand.SetSetting("auto light", "true"), setOnResult)

        val powerResult = intentParser.parse("disable power saving")
        assertEquals(VoiceCommand.SetSetting("power saving", "false"), powerResult)

        val turnOffResult = intentParser.parse("turn off auto light")
        assertEquals(VoiceCommand.SetSetting("auto light", "false"), turnOffResult)

        val setOffResult = intentParser.parse("Set power saving off")
        assertEquals(VoiceCommand.SetSetting("power saving", "false"), setOffResult)
    }

    @Test
    fun testParseAddReminder() {
        val result = intentParser.parse("Set a reminder buy milk")
        assert(result is VoiceCommand.AddReminder)
        assertEquals("buy milk", (result as VoiceCommand.AddReminder).title)
    }

    @Test
    fun testParseHelp() {
        assertEquals(VoiceCommand.Help, intentParser.parse("help"))
        assertEquals(VoiceCommand.Help, intentParser.parse("Help"))
    }

    @Test
    fun testParseSetSettingsToDefault() {
        assertEquals(VoiceCommand.SetSettingsToDefault, intentParser.parse("set settings to default"))
        assertEquals(VoiceCommand.SetSettingsToDefault, intentParser.parse("reset settings to defaults"))
        assertEquals(VoiceCommand.SetSettingsToDefault, intentParser.parse("settings to default"))
        assertEquals(VoiceCommand.SetSettingsToDefault, intentParser.parse("reset settings"))
    }

    @Test
    fun testUnmatchedTextReturnsNull() {
        val result = intentParser.parse("hello world")
        assertNull(result)
    }
}
