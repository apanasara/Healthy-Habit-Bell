/**
 * # SuryaRoomDatabaseTest
 *
 * Unit test suite verifying Surya Namaskar Room database entities, type converters,
 * preset durations, and JSON payload serialization for companion watch synchronization.
 *
 * ## Architectural Role & Component Relationships
 * Tests the data layer components under `com.habitbell.app.data`:
 * - [com.habitbell.app.data.model.StepEntity]
 * - [com.habitbell.app.data.model.PresetEntity]
 * - [com.habitbell.app.data.model.SettingEntity]
 * - [com.habitbell.app.data.SuryaTypeConverters]
 * - [com.habitbell.app.util.JsonUtil]
 *
 * ## Concurrency & Thread Safety
 * Standard JUnit 4 runner executing on local JVM threads.
 */
package com.habitbell.app

import com.habitbell.app.audio.VoiceCueMode
import com.habitbell.app.data.SuryaTypeConverters
import com.habitbell.app.data.model.PresetEntity
import com.habitbell.app.data.model.SettingEntity
import com.habitbell.app.data.model.StepEntity
import com.habitbell.app.util.JsonUtil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Test class covering Surya Namaskar persistence entities and data conversions.
 */
class SuryaRoomDatabaseTest {

    private val converters = SuryaTypeConverters()

    /**
     * Verifies that [VoiceCueMode] properly converts to integer and back.
     */
    @Test
    fun testVoiceCueModeTypeConverter() {
        assertEquals(0, converters.fromVoiceCueMode(VoiceCueMode.NONE))
        assertEquals(1, converters.fromVoiceCueMode(VoiceCueMode.PRANIC))
        assertEquals(2, converters.fromVoiceCueMode(VoiceCueMode.STEP_NAME))
        assertEquals(3, converters.fromVoiceCueMode(VoiceCueMode.SLOKA))

        assertEquals(VoiceCueMode.NONE, converters.toVoiceCueMode(0))
        assertEquals(VoiceCueMode.PRANIC, converters.toVoiceCueMode(1))
        assertEquals(VoiceCueMode.STEP_NAME, converters.toVoiceCueMode(2))
        assertEquals(VoiceCueMode.SLOKA, converters.toVoiceCueMode(3))
        assertEquals(VoiceCueMode.NONE, converters.toVoiceCueMode(999)) // fallback
    }

    /**
     * Verifies that [VoiceCueMode] provides accurate user-facing display names for global selection.
     */
    @Test
    fun testVoiceCueModeDisplayName() {
        assertEquals("Silent / Bell", VoiceCueMode.NONE.displayName)
        assertEquals("Breath Flow", VoiceCueMode.PRANIC.displayName)
        assertEquals("Asana Name", VoiceCueMode.STEP_NAME.displayName)
        assertEquals("Solar Mantra", VoiceCueMode.SLOKA.displayName)
    }

    /**
     * Verifies creation and default fields of [StepEntity].
     */
    @Test
    fun testStepEntityCreation() {
        val step = StepEntity(
            id = 1L,
            name = "Pranamasana",
            orderIdx = 0,
            isEnabled = true,
            voiceCueMode = VoiceCueMode.STEP_NAME,
            audioCue = "surya_1",
            mantraEnabled = true,
            assetRef = "avd_yoga_pranamasana",
            durationSeconds = 5,
            repetition = 1
        )

        assertEquals(1L, step.id)
        assertEquals("Pranamasana", step.name)
        assertEquals(0, step.orderIdx)
        assertTrue(step.isEnabled)
        assertEquals(VoiceCueMode.STEP_NAME, step.voiceCueMode)
        assertEquals("surya_1", step.audioCue)
        assertTrue(step.mantraEnabled)
        assertEquals(5, step.durationSeconds)
    }

    /**
     * Verifies all 4 [PresetEntity] presets (slow, moderate, fast, custom) creation and JSON serialization.
     */
    @Test
    fun testPresetEntitySerialization() {
        val slow = PresetEntity("slow", JsonUtil.toJson(mapOf("default_duration" to 10)))
        val moderate = PresetEntity("moderate", JsonUtil.toJson(mapOf("default_duration" to 5)))
        val fast = PresetEntity("fast", JsonUtil.toJson(mapOf("default_duration" to 3)))
        val custom = PresetEntity("custom", JsonUtil.toJson(mapOf("default_duration" to 7)))

        assertEquals("slow", slow.name)
        assertEquals("moderate", moderate.name)
        assertEquals("fast", fast.name)
        assertEquals("custom", custom.name)

        assertTrue(slow.jsonMap.contains("\"default_duration\":10"))
        assertTrue(moderate.jsonMap.contains("\"default_duration\":5"))
        assertTrue(fast.jsonMap.contains("\"default_duration\":3"))
        assertTrue(custom.jsonMap.contains("\"default_duration\":7"))
    }

    /**
     * Verifies [SettingEntity] key-value pairs.
     */
    @Test
    fun testSettingEntity() {
        val setting = SettingEntity(
            key = "audio_mode",
            value = "sanskrit"
        )

        assertEquals("audio_mode", setting.key)
        assertEquals("sanskrit", setting.value)
    }

    /**
     * Verifies JSON payload serialization for companion sync.
     */
    @Test
    fun testCompanionSyncPayloadSerialization() {
        val steps = listOf(
            StepEntity(id = 1L, name = "Pranamasana", orderIdx = 0, durationSeconds = 5),
            StepEntity(id = 2L, name = "Hastauttanasana", orderIdx = 1, durationSeconds = 5)
        )
        val payload = mapOf("steps" to steps, "source" to "phone")
        val json = JsonUtil.toJson(payload)

        assertNotNull(json)
        assertTrue(json.contains("Pranamasana"))
        assertTrue(json.contains("Hastauttanasana"))
        assertTrue(json.contains("phone"))
    }
}
