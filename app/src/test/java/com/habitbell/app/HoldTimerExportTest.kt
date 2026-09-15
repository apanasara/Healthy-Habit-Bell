package com.habitbell.app

import com.habitbell.app.holdtimer.HoldTimerSessionLogger
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * # HoldTimerExportTest
 *
 * Unit test suite validating telemetry recording, CSV formatting, and JSON payload generation
 * in [HoldTimerSessionLogger].
 */
class HoldTimerExportTest {

    private lateinit var logger: HoldTimerSessionLogger

    @Before
    fun setUp() {
        logger = HoldTimerSessionLogger()
    }

    @Test
    fun testLogAndExportCsv() {
        logger.logRound(
            roundIndex = 1,
            targetHoldSec = 30,
            actualHoldSec = 30,
            restSec = 15,
            speedRating = 1.0f
        )
        logger.logRound(
            roundIndex = 2,
            targetHoldSec = 45,
            actualHoldSec = 42,
            restSec = 15,
            speedRating = 0.85f
        )

        val csv = logger.generateCsv()
        assertNotNull(csv)
        assertTrue(csv.contains("Round,TargetHoldSec,ActualHoldSec,RestSec,SpeedRating,TimestampISO"))
        assertTrue(csv.contains("1,30,30,15,1.00,"))
        assertTrue(csv.contains("2,45,42,15,0.85,"))
    }

    @Test
    fun testLogAndExportJson() {
        logger.logRound(
            roundIndex = 1,
            targetHoldSec = 30,
            actualHoldSec = 30,
            restSec = 15,
            speedRating = 1.0f
        )

        val jsonString = logger.generateJson()
        assertNotNull(jsonString)

        val json = JSONObject(jsonString)
        assertEquals(1, json.getInt("totalRoundsCompleted"))
        val rounds = json.getJSONArray("rounds")
        assertEquals(1, rounds.length())

        val round1 = rounds.getJSONObject(0)
        assertEquals(1, round1.getInt("roundIndex"))
        assertEquals(30, round1.getInt("targetHoldSec"))
        assertEquals(30, round1.getInt("actualHoldSec"))
        assertEquals(15, round1.getInt("restSec"))
        assertEquals(1.0, round1.getDouble("speedRating"), 0.01)
        assertTrue(round1.has("timestampMs"))
    }
}
