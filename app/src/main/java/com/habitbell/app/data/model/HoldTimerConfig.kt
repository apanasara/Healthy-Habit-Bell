package com.habitbell.app.data.model

import org.json.JSONArray
import org.json.JSONObject

/**
 * # HoldTimerConfig
 *
 * Configuration aggregate governing parameters for the hands-free, voice-driven
 * Yoga & Physiotherapy Hold Timer subsystem in Habit Bell.
 *
 * ## Architectural Role & Component Relationships
 * - Embedded directly within [TimerProfile] when hold timing is enabled (`holdTimerConfig != null`).
 * - Ingested by `com.habitbell.app.holdtimer.HoldTimerEngine` upon session initialization.
 * - Dynamically mutated on-the-fly during active sessions via voice commands parsed by
 *   `com.habitbell.app.holdtimer.VoiceHoldTimerParser`.
 * - Validated against clinician-defined safety limits before and during session execution.
 *
 * ## Lifecycle & Concurrency
 * Immutable data configuration. Thread-safe across all coroutine dispatchers and background services.
 *
 * @property holdDurationSec Hold duration per round in seconds (must be >= 1).
 * @property restDurationSec Rest duration between consecutive rounds in seconds (>= 0).
 * @property repeatCount Total number of rounds to perform (must be >= 1).
 * @property roundNames Spoken ordinal names for rounds (e.g. "First", "Second", "Third", ...).
 * @property maxHoldSec Clinician-defined maximum hold threshold in seconds to prevent musculoskeletal strain.
 * @property ttsVoice Identifier or locale tag for the Text-To-Speech voice (default "en-US").
 * @property ttsSpeed Speech rate and cadence multiplier (default 1.0f, safe range 0.5f to 2.0f).
 * @property isCountAloudEnabled Whether the dual-cue engine counts each second out loud during holds and rests.
 * @property isHapticTickEnabled Whether tactile vibration pulses fire synchronously on each second tick.
 * @property voiceCueStyle Spoken voice guidance delivery style (Sanskrit, Bilingual, English).
 * @property voiceVolume Spoken vocal gain factor (0.15f..1.0f). Default 0.52f.
 */
data class HoldTimerConfig(
    val holdDurationSec: Int = 30,
    val restDurationSec: Int = 15,
    val repeatCount: Int = 4,
    val roundNames: List<String> = DEFAULT_ROUND_NAMES,
    val maxHoldSec: Int = 60,
    val ttsVoice: String = "en-US",
    val ttsSpeed: Float = 0.85f,
    val isCountAloudEnabled: Boolean = true,
    val isHapticTickEnabled: Boolean = true,
    val voiceCueStyle: VoiceCueStyle = VoiceCueStyle.BILINGUAL,
    val voiceVolume: Float = 0.52f
) {
    init {
        require(holdDurationSec >= 1) { "holdDurationSec must be at least 1 second (got $holdDurationSec)" }
        require(restDurationSec >= 0) { "restDurationSec cannot be negative (got $restDurationSec)" }
        require(repeatCount >= 1) { "repeatCount must be at least 1 round (got $repeatCount)" }
        require(maxHoldSec >= 1) { "maxHoldSec safety limit must be at least 1 second (got $maxHoldSec)" }
        require(ttsSpeed in 0.4f..2.5f) { "ttsSpeed must be within 0.4f..2.5f (got $ttsSpeed)" }
        require(voiceVolume in 0.15f..1.0f) { "voiceVolume must be within 0.15f..1.0f (got $voiceVolume)" }
    }

    /**
     * Total theoretical active duration of the entire session in seconds,
     * including hold phases and intervening rest periods (excluding terminal rest).
     */
    val totalEstimatedDurationSec: Int
        get() = (holdDurationSec * repeatCount) + (restDurationSec * (repeatCount - 1).coerceAtLeast(0))

    /**
     * Evaluates whether the configured hold duration is within the clinician-defined safety limit.
     *
     * @return `true` if hold duration does not exceed [maxHoldSec], `false` if safety alert is warranted.
     */
    fun isHoldDurationSafe(): Boolean = holdDurationSec <= maxHoldSec

    /**
     * Resolves the spoken ordinal or custom display name for a given round index (1-based).
     *
     * @param roundIndex 1-based round index (1..[repeatCount]).
     * @return Spoken name string, e.g. "First", "Second", or "Round N" fallback.
     */
    fun getRoundName(roundIndex: Int): String {
        val index = roundIndex - 1
        return if (index in roundNames.indices) {
            roundNames[index]
        } else {
            "Round $roundIndex"
        }
    }

    /**
     * Serializes this configuration instance into a standardized JSON representation.
     *
     * @return Formatted JSON string conforming to the HoldTimerConfig JSON schema.
     */
    fun toJson(): String {
        val json = JSONObject()
        json.put("holdDurationSec", holdDurationSec)
        json.put("restDurationSec", restDurationSec)
        json.put("repeatCount", repeatCount)
        val namesArray = JSONArray()
        roundNames.forEach { namesArray.put(it) }
        json.put("roundNames", namesArray)
        json.put("maxHoldSec", maxHoldSec)
        json.put("ttsVoice", ttsVoice)
        json.put("ttsSpeed", ttsSpeed.toDouble())
        json.put("isCountAloudEnabled", isCountAloudEnabled)
        json.put("isHapticTickEnabled", isHapticTickEnabled)
        json.put("voiceCueStyle", voiceCueStyle.name)
        json.put("voiceVolume", voiceVolume.toDouble())
        return json.toString(2)
    }

    companion object {
        /** Default spoken ordinal names for up to 12 rounds. */
        val DEFAULT_ROUND_NAMES: List<String> = listOf(
            "First", "Second", "Third", "Fourth", "Fifth",
            "Sixth", "Seventh", "Eighth", "Ninth", "Tenth",
            "Eleventh", "Twelfth"
        )

        /** Canonical preset for beginner yoga / physical therapy holds (4 rounds of 30s hold, 15s rest). */
        val DEFAULT_YOGA_PHYSIO = HoldTimerConfig(
            holdDurationSec = 30,
            restDurationSec = 15,
            repeatCount = 4,
            maxHoldSec = 60,
            ttsSpeed = 0.85f,
            voiceCueStyle = VoiceCueStyle.BILINGUAL,
            voiceVolume = 0.52f
        )

        /**
         * Parses a JSON string representation into an immutable [HoldTimerConfig] instance.
         *
         * @param jsonString Serialized JSON text.
         * @return Parsed [HoldTimerConfig] with defaults applied for missing optional fields.
         * @throws org.json.JSONException if the JSON is malformed.
         */
        @Throws(Exception::class)
        fun fromJson(jsonString: String): HoldTimerConfig {
            val json = JSONObject(jsonString)
            val holdSec = json.optInt("holdDurationSec", 30)
            val restSec = json.optInt("restDurationSec", 15)
            val repeats = json.optInt("repeatCount", 4)
            val maxHold = json.optInt("maxHoldSec", 60)
            val voice = json.optString("ttsVoice", "en-US")
            val speed = json.optDouble("ttsSpeed", 0.85).toFloat()
            val countAloud = json.optBoolean("isCountAloudEnabled", true)
            val haptic = json.optBoolean("isHapticTickEnabled", true)
            val styleStr = json.optString("voiceCueStyle", "BILINGUAL")
            val style = try { VoiceCueStyle.valueOf(styleStr) } catch (_: Exception) { VoiceCueStyle.BILINGUAL }
            val volume = json.optDouble("voiceVolume", 0.52).toFloat()

            val namesList = mutableListOf<String>()
            val namesArray = json.optJSONArray("roundNames")
            if (namesArray != null) {
                for (i in 0 until namesArray.length()) {
                    namesList.add(namesArray.getString(i))
                }
            } else {
                namesList.addAll(DEFAULT_ROUND_NAMES)
            }

            return HoldTimerConfig(
                holdDurationSec = holdSec,
                restDurationSec = restSec,
                repeatCount = repeats,
                roundNames = namesList,
                maxHoldSec = maxHold,
                ttsVoice = voice,
                ttsSpeed = speed,
                isCountAloudEnabled = countAloud,
                isHapticTickEnabled = haptic,
                voiceCueStyle = style,
                voiceVolume = volume
            )
        }
    }
}
