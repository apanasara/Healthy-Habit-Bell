package com.habitbell.app.holdtimer

import com.habitbell.app.data.model.HoldTimerConfig
import java.util.Locale

/**
 * # VoiceHoldCommand
 *
 * Sealed hierarchy representing all intent-bearing actions recognized by [VoiceHoldTimerParser].
 */
sealed class VoiceHoldCommand {
    /**
     * Create or reconfigure a hold timer with new timing parameters.
     *
     * @property config The resolved [HoldTimerConfig].
     */
    data class CreateTimer(val config: HoldTimerConfig) : VoiceHoldCommand()

    /**
     * Adjust active hold duration on-the-fly during an ongoing session.
     *
     * @property seconds New hold duration in seconds.
     */
    data class AdjustHoldDuration(val seconds: Int) : VoiceHoldCommand()

    /**
     * Adjust total scheduled rounds on-the-fly during an ongoing session.
     *
     * @property repeats New target repeat count.
     */
    data class AdjustRepeatCount(val repeats: Int) : VoiceHoldCommand()

    /**
     * Increment or decrement TTS counting cadence / speech rate.
     *
     * @property speedDelta Positive to speed up, negative to slow down.
     */
    data class AdjustPace(val speedDelta: Float) : VoiceHoldCommand()

    /** Pause the active countdown timer. */
    object Pause : VoiceHoldCommand()

    /** Resume the paused countdown timer. */
    object Resume : VoiceHoldCommand()

    /** Immediately advance to the next round. */
    object NextRound : VoiceHoldCommand()

    /** Terminate the active session. */
    object Stop : VoiceHoldCommand()

    /**
     * Export recorded session telemetry data.
     *
     * @property format Export format ("csv" or "json").
     */
    data class ExportData(val format: String = "csv") : VoiceHoldCommand()

    /**
     * Fallback for utterances that could not be mapped to any known command grammar.
     *
     * @property rawUtterance Original unparsed voice text.
     */
    data class Unknown(val rawUtterance: String) : VoiceHoldCommand()
}

/**
 * # VoiceHoldTimerParser
 *
 * Deterministic Natural Language Parser for hands-free voice commands governing
 * the Yoga & Physiotherapy Hold Timer subsystem.
 *
 * ## Architectural Role & Component Relationships
 * - Ingests raw voice text streams from `HoldTimerVoiceListener` / Android `SpeechRecognizer`.
 * - Emits type-safe [VoiceHoldCommand] instructions consumed by `HoldTimerEngine`.
 * - Parses both timer creation utterances and in-session dynamic adjustments without network connectivity.
 *
 * ## Lifecycle & Concurrency
 * Stateless utility parser. Thread-safe across all coroutine dispatchers.
 */
object VoiceHoldTimerParser {

    private val NUMBER_WORDS = mapOf(
        "zero" to 0, "one" to 1, "two" to 2, "three" to 3, "four" to 4,
        "five" to 5, "six" to 6, "seven" to 7, "eight" to 8, "nine" to 9,
        "ten" to 10, "eleven" to 11, "twelve" to 12, "thirteen" to 13,
        "fourteen" to 14, "fifteen" to 15, "sixteen" to 16, "seventeen" to 17,
        "eighteen" to 18, "nineteen" to 19, "twenty" to 20, "thirty" to 30,
        "forty" to 40, "fifty" to 50, "sixty" to 60, "seventy" to 70,
        "eighty" to 80, "ninety" to 90, "hundred" to 100
    )

    /**
     * Normalizes a spoken sentence by converting English number words into numeric digits
     * and stripping punctuation and wake phrases ("Hey Yoga").
     *
     * @param input Raw spoken sentence text.
     * @return Normalized string with numeric digits.
     */
    fun normalizeUtterance(input: String): String {
        var text = input.lowercase(Locale.US)
            .replace("hey yoga", "")
            .replace("yoga", "")
            .replace(",", " ")
            .replace(":", " ")
            .replace("-", " ")
            .trim()

        // Replace common compound numbers like "forty five" -> "45"
        text = text.replace(Regex("\\bforty\\s+five\\b"), "45")
            .replace(Regex("\\bthirty\\s+five\\b"), "35")
            .replace(Regex("\\btwenty\\s+five\\b"), "25")
            .replace(Regex("\\bfifty\\s+five\\b"), "55")
            .replace(Regex("\\bsixty\\s+five\\b"), "65")

        // Replace single number words with digits
        for ((word, num) in NUMBER_WORDS) {
            text = text.replace(Regex("\\b$word\\b"), num.toString())
        }

        return text.replace(Regex("\\s+"), " ").trim()
    }

    /**
     * Parses a spoken utterance into a structured [VoiceHoldCommand].
     *
     * @param utterance Spoken speech text.
     * @return Dispatched [VoiceHoldCommand].
     */
    fun parse(utterance: String): VoiceHoldCommand {
        val normalized = normalizeUtterance(utterance)

        // 1. Session Transport Commands
        if (matchesPattern(normalized, listOf("pause", "pause timer", "hold timer", "wait"))) {
            return VoiceHoldCommand.Pause
        }
        if (matchesPattern(normalized, listOf("resume", "resume timer", "continue", "start timer", "start"))) {
            return VoiceHoldCommand.Resume
        }
        if (matchesPattern(normalized, listOf("next round", "skip round", "next", "advance"))) {
            return VoiceHoldCommand.NextRound
        }
        if (matchesPattern(normalized, listOf("stop", "stop timer", "end session", "cancel timer", "finish"))) {
            return VoiceHoldCommand.Stop
        }

        // 2. Data Export Commands
        if (normalized.contains("export")) {
            val format = if (normalized.contains("json")) "json" else "csv"
            return VoiceHoldCommand.ExportData(format)
        }

        // 3. Adaptive Pace & Speed Adjustments
        // "Too fast" -> user thinks it's moving too fast, so app slows down (-0.15f)
        if (normalized.contains("too fast") || normalized.contains("slower") || normalized.contains("count slower") || normalized.contains("make the count slower")) {
            return VoiceHoldCommand.AdjustPace(-0.15f)
        }
        // "Too slow" -> user thinks it's too slow, so app speeds up (+0.15f)
        if (normalized.contains("too slow") || normalized.contains("faster") || normalized.contains("count faster") || normalized.contains("make the count faster")) {
            return VoiceHoldCommand.AdjustPace(+0.15f)
        }

        // 4. In-Session Parameter Adjustments
        // e.g. "set hold to 45 seconds", "change hold to 45", "hold 45 seconds"
        val holdAdjustRegex = Regex("(?:set|change|make)?\\s*hold\\s*(?:to|for)?\\s*(\\d+)")
        val holdMatch = holdAdjustRegex.find(normalized)
        if (holdMatch != null && !normalized.contains("repeat") && !normalized.contains("round")) {
            val sec = holdMatch.groupValues[1].toIntOrNull()
            if (sec != null && sec > 0) {
                return VoiceHoldCommand.AdjustHoldDuration(sec)
            }
        }

        // e.g. "change repeats to 5", "set repeats to 5", "set rounds to 5"
        val repeatAdjustRegex = Regex("(?:set|change|make)?\\s*(?:repeats?|rounds?)\\s*(?:to)?\\s*(\\d+)")
        val repeatMatch = repeatAdjustRegex.find(normalized)
        if (repeatMatch != null && !normalized.contains("hold") && !normalized.contains("second")) {
            val count = repeatMatch.groupValues[1].toIntOrNull()
            if (count != null && count > 0) {
                return VoiceHoldCommand.AdjustRepeatCount(count)
            }
        }

        // 5. Timer Creation Utterances
        // Variation A: "create a timer: hold 30 seconds, repeat 4 times, rest 15 seconds"
        // Variation B: "four rounds of 30-second holds with 15-second breaks"
        // Variation C: "hold 45 seconds repeat 3 rest 10"
        val createdConfig = parseTimerCreation(normalized)
        if (createdConfig != null) {
            return VoiceHoldCommand.CreateTimer(createdConfig)
        }

        return VoiceHoldCommand.Unknown(utterance)
    }

    /**
     * Internal extractor attempting to parse timer creation parameters from normalized voice text.
     *
     * @param normalized Cleaned utterance text.
     * @return Instantiated [HoldTimerConfig] or null if required timing parameters were missing.
     */
    private fun parseTimerCreation(normalized: String): HoldTimerConfig? {
        var holdSec: Int? = null
        var repeats: Int? = null
        var restSec: Int? = null

        // 1. Extract Hold Duration
        // Patterns: "hold 30", "hold for 30", "30 second holds", "30 seconds hold"
        val holdPatterns = listOf(
            Regex("hold(?:ing)?\\s*(?:for)?\\s*(\\d+)\\s*(?:seconds?|sec)?"),
            Regex("(\\d+)\\s*(?:seconds?|sec)?\\s*holds?")
        )
        for (pattern in holdPatterns) {
            val match = pattern.find(normalized)
            if (match != null) {
                holdSec = match.groupValues[1].toIntOrNull()
                break
            }
        }

        // 2. Extract Repeat / Round Count
        // Patterns: "repeat 4 times", "repeat 4", "4 rounds", "4 times"
        val repeatPatterns = listOf(
            Regex("(?:repeat|repeats)\\s*(\\d+)"),
            Regex("(\\d+)\\s*rounds?"),
            Regex("(\\d+)\\s*times")
        )
        for (pattern in repeatPatterns) {
            val match = pattern.find(normalized)
            if (match != null) {
                repeats = match.groupValues[1].toIntOrNull()
                break
            }
        }

        // 3. Extract Rest Duration
        // Patterns: "rest 15 seconds", "rest for 15", "15 second breaks", "15 second rest"
        val restPatterns = listOf(
            Regex("rest(?:ing)?\\s*(?:for)?\\s*(\\d+)\\s*(?:seconds?|sec)?"),
            Regex("(\\d+)\\s*(?:seconds?|sec)?\\s*(?:breaks?|rest)")
        )
        for (pattern in restPatterns) {
            val match = pattern.find(normalized)
            if (match != null) {
                restSec = match.groupValues[1].toIntOrNull()
                break
            }
        }

        // If at least holdSec or repeats is detected alongside timer creation context
        if (holdSec != null || (normalized.contains("timer") && repeats != null)) {
            val finalHold = holdSec ?: 30
            val finalRepeats = repeats ?: 4
            val finalRest = restSec ?: 15
            return HoldTimerConfig(
                holdDurationSec = finalHold.coerceAtLeast(1),
                restDurationSec = finalRest.coerceAtLeast(0),
                repeatCount = finalRepeats.coerceAtLeast(1)
            )
        }

        return null
    }

    private fun matchesPattern(text: String, patterns: List<String>): Boolean {
        return patterns.any { text == it || text.startsWith("$it ") || text.endsWith(" $it") }
    }
}
