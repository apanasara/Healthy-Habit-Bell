package com.habitbell.app

import com.habitbell.app.data.default.DefaultProfiles
import com.habitbell.app.data.default.DefaultReminders
import com.habitbell.app.data.model.TimerType
import com.habitbell.app.engine.SessionStatus
import com.habitbell.app.engine.TimerSessionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TimerEngineTest {

    @Test
    fun testEatingProfileDefaultValues() {
        val eating = DefaultProfiles.EATING
        assertEquals("Eating", eating.name)
        assertEquals(TimerType.LINEAR, eating.type)
        assertEquals(1200, eating.totalDurationSeconds) // 20 minutes
        assertEquals(30, eating.intervalDurationSeconds) // 30s bells
        assertTrue(eating.isFavorite)
    }

    @Test
    fun testReikiProfileDefaultValues() {
        val reiki = DefaultProfiles.REIKI
        assertEquals("Reiki", reiki.name)
        assertEquals(2700, reiki.totalDurationSeconds) // 45 minutes
        assertEquals(180, reiki.intervalDurationSeconds) // 3-minute hand transitions
    }

    @Test
    fun testPranayamaBoxBreathConfig() {
        val pranayama = DefaultProfiles.PRANAYAMA_BOX
        val config = pranayama.pranayamaConfig
        assertNotNull(config)
        assertEquals(4, config!!.steps.size)
        val roundSeconds = config.steps.sumOf { it.durationSeconds }
        assertEquals(16, roundSeconds) // 4s inhale + 4s hold + 4s exhale + 4s hold
        assertEquals(20, config.targetRounds)
    }

    @Test
    fun testSuryaNamaskar12Poses() {
        val surya = DefaultProfiles.SURYA_NAMASKAR
        val config = surya.compoundConfig
        assertNotNull(config)
        assertEquals(12, config!!.poses.size)
        assertEquals("Pranamasana", config.poses[0].name)
        assertEquals("Tadasana", config.poses[11].name)
        assertEquals(5, config.targetRounds)
    }

    @Test
    fun testSessionStateTimeFormatting() {
        val state = TimerSessionState(
            status = SessionStatus.IDLE,
            profile = DefaultProfiles.EATING,
            remainingSeconds = 1122, // 18m 42s
            nextBellSeconds = 20
        )
        assertEquals("18:42", state.formattedRemainingTime)
        assertEquals("00:20", state.formattedNextBellTime)
    }

    @Test
    fun testDefaultReminders() {
        val reminders = DefaultReminders.ALL_REMINDERS
        assertEquals(4, reminders.size)
        assertEquals("08:00", reminders[0].timeString)
        assertEquals("12:30", reminders[1].timeString)
        assertEquals("17:00", reminders[2].timeString)
        assertEquals("20:00", reminders[3].timeString)
    }
}
