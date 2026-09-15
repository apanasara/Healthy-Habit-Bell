package com.habitbell.app

import com.habitbell.app.holdtimer.VoiceHoldCommand
import com.habitbell.app.holdtimer.VoiceHoldTimerParser
import org.junit.Assert.*
import org.junit.Test

/**
 * # VoiceHoldTimerParserTest
 *
 * Unit tests verifying natural speech grammar recognition for timer creation,
 * runtime parameter alterations, adaptive pacing, and session controls in [VoiceHoldTimerParser].
 */
class VoiceHoldTimerParserTest {

    @Test
    fun testParseTimerCreationCanonicalUtterance() {
        val utterance = "Hey Yoga, create a timer: hold 30 seconds, repeat 4 times, rest 15 seconds"
        val command = VoiceHoldTimerParser.parse(utterance)

        assertTrue(command is VoiceHoldCommand.CreateTimer)
        val config = (command as VoiceHoldCommand.CreateTimer).config
        assertEquals(30, config.holdDurationSec)
        assertEquals(4, config.repeatCount)
        assertEquals(15, config.restDurationSec)
    }

    @Test
    fun testParseTimerCreationConversationalFormat() {
        val utterance = "four rounds of 30-second holds with 15-second breaks"
        val command = VoiceHoldTimerParser.parse(utterance)

        assertTrue(command is VoiceHoldCommand.CreateTimer)
        val config = (command as VoiceHoldCommand.CreateTimer).config
        assertEquals(30, config.holdDurationSec)
        assertEquals(4, config.repeatCount)
        assertEquals(15, config.restDurationSec)
    }

    @Test
    fun testParseTimerCreationTerseWords() {
        val utterance = "hold forty-five seconds repeat three rest ten"
        val command = VoiceHoldTimerParser.parse(utterance)

        assertTrue(command is VoiceHoldCommand.CreateTimer)
        val config = (command as VoiceHoldCommand.CreateTimer).config
        assertEquals(45, config.holdDurationSec)
        assertEquals(3, config.repeatCount)
        assertEquals(10, config.restDurationSec)
    }

    @Test
    fun testParseInSessionHoldAdjustment() {
        val utterance1 = "Set hold to 45 seconds"
        val cmd1 = VoiceHoldTimerParser.parse(utterance1)
        assertTrue(cmd1 is VoiceHoldCommand.AdjustHoldDuration)
        assertEquals(45, (cmd1 as VoiceHoldCommand.AdjustHoldDuration).seconds)

        val utterance2 = "change hold to 60"
        val cmd2 = VoiceHoldTimerParser.parse(utterance2)
        assertTrue(cmd2 is VoiceHoldCommand.AdjustHoldDuration)
        assertEquals(60, (cmd2 as VoiceHoldCommand.AdjustHoldDuration).seconds)
    }

    @Test
    fun testParseInSessionRepeatAdjustment() {
        val utterance1 = "Change repeats to 5"
        val cmd1 = VoiceHoldTimerParser.parse(utterance1)
        assertTrue(cmd1 is VoiceHoldCommand.AdjustRepeatCount)
        assertEquals(5, (cmd1 as VoiceHoldCommand.AdjustRepeatCount).repeats)

        val utterance2 = "set rounds to 6"
        val cmd2 = VoiceHoldTimerParser.parse(utterance2)
        assertTrue(cmd2 is VoiceHoldCommand.AdjustRepeatCount)
        assertEquals(6, (cmd2 as VoiceHoldCommand.AdjustRepeatCount).repeats)
    }

    @Test
    fun testParseAdaptivePaceFeedback() {
        // "Too fast" -> user wants it slower (negative delta)
        val fastCmd = VoiceHoldTimerParser.parse("Too fast")
        assertTrue(fastCmd is VoiceHoldCommand.AdjustPace)
        assertTrue((fastCmd as VoiceHoldCommand.AdjustPace).speedDelta < 0f)

        // "Too slow" -> user wants it faster (positive delta)
        val slowCmd = VoiceHoldTimerParser.parse("Too slow")
        assertTrue(slowCmd is VoiceHoldCommand.AdjustPace)
        assertTrue((slowCmd as VoiceHoldCommand.AdjustPace).speedDelta > 0f)

        // "Make the count faster"
        val makeFaster = VoiceHoldTimerParser.parse("make the count faster")
        assertTrue(makeFaster is VoiceHoldCommand.AdjustPace)
        assertTrue((makeFaster as VoiceHoldCommand.AdjustPace).speedDelta > 0f)

        // "Make the count slower"
        val makeSlower = VoiceHoldTimerParser.parse("make the count slower")
        assertTrue(makeSlower is VoiceHoldCommand.AdjustPace)
        assertTrue((makeSlower as VoiceHoldCommand.AdjustPace).speedDelta < 0f)
    }

    @Test
    fun testParseTransportControls() {
        assertEquals(VoiceHoldCommand.Pause, VoiceHoldTimerParser.parse("Pause"))
        assertEquals(VoiceHoldCommand.Pause, VoiceHoldTimerParser.parse("pause timer"))

        assertEquals(VoiceHoldCommand.Resume, VoiceHoldTimerParser.parse("Resume"))
        assertEquals(VoiceHoldCommand.Resume, VoiceHoldTimerParser.parse("continue"))

        assertEquals(VoiceHoldCommand.NextRound, VoiceHoldTimerParser.parse("Next round"))
        assertEquals(VoiceHoldCommand.NextRound, VoiceHoldTimerParser.parse("skip round"))

        assertEquals(VoiceHoldCommand.Stop, VoiceHoldTimerParser.parse("stop timer"))
    }

    @Test
    fun testParseExportCommands() {
        val csvCmd = VoiceHoldTimerParser.parse("Export session data")
        assertTrue(csvCmd is VoiceHoldCommand.ExportData)
        assertEquals("csv", (csvCmd as VoiceHoldCommand.ExportData).format)

        val jsonCmd = VoiceHoldTimerParser.parse("Export as JSON")
        assertTrue(jsonCmd is VoiceHoldCommand.ExportData)
        assertEquals("json", (jsonCmd as VoiceHoldCommand.ExportData).format)
    }
}
