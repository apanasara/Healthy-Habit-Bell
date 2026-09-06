package com.habitbell.app.data.model

/**
 * Voice guidance prompt delivery style for Pranayama phase transitions.
 */
enum class VoiceCueStyle(val displayName: String) {
    /** Traditional Sanskrit cues spoken by gentle lady voice ("Purak", "Kumbhak", "Rechak"). */
    SANSKRIT("Sanskrit (Purak)"),

    /** English cues spoken by gentle lady voice ("Inhale", "Hold", "Exhale", "Hold empty"). */
    ENGLISH("English (Inhale)"),

    /** Bilingual Sanskrit and English cues ("Purak • Inhale", "Kumbhak • Hold", etc.). */
    BILINGUAL("Bilingual")
}

/**
 * The four classical phases of yogic breath control (Chaturanga Pranayama).
 *
 * Grounded in classical Hatha Yoga literature (*Hatha Yoga Pradipika*, *Gheranda Samhita*),
 * this enum defines the four sacred limbs of the breath cycle:
 * 1. Puraka (पूरक - Inhalation)
 * 2. Antar Kumbhaka (अभ्यन्तर कुम्भक - Internal Retention)
 * 3. Rechaka (रेचक - Exhalation)
 * 4. Bahya Kumbhaka (बाह्य कुम्भक - External Retention / Shunya Void)
 *
 * @property displayName Short human-readable title for UI chips and indicators.
 * @property cue Extended instruction prompt displayed or spoken during phase transitions.
 * @property sanskritName Romanized Sanskrit title (Purak, Kumbhak, Rechak).
 * @property sanskritScript Devanagari script representation for traditional mindfulness immersion.
 */
enum class PranayamaPhase(
    val displayName: String,
    val cue: String,
    val sanskritName: String,
    val sanskritScript: String
) {
    /** Puraka: Controlled diaphragmatic inhalation drawing in cosmic Prana. */
    INHALE("Inhale", "Inhale deeply", "Purak", "पूरक"),

    /** Antar Kumbhaka: Internal breath retention awakening Sushumna Nadi with full lungs. */
    HOLD_IN("Hold In", "Hold breath", "Kumbhak", "अभ्यन्तर कुम्भक"),

    /** Rechaka: Slow, continuous exhalation releasing Apana and mental tension. */
    EXHALE("Exhale", "Exhale slowly", "Rechak", "रेचक"),

    /** Bahya Kumbhaka: External breath retention resting in Shunya (the primordial void). */
    HOLD_OUT("Hold Out", "Rest in emptiness", "Kumbhak", "बाह्य कुम्भक")
}

/**
 * Represents a single timed phase within a Pranayama breathwork cycle.
 *
 * @property phase The breathwork phase ([PranayamaPhase.INHALE], [PranayamaPhase.HOLD_IN], etc.).
 * @property durationSeconds Duration allocated for this breath phase in seconds (e.g., 4s, 16s, 8s).
 */
data class PranayamaStep(
    val phase: PranayamaPhase,
    val durationSeconds: Int
)

/**
 * Configuration aggregate for multi-interval breathwork routines.
 *
 * @property steps Ordered list of breath phases comprising one full breathing cycle.
 * @property targetRounds Number of cycles/repetitions to complete the full session (e.g. 20 rounds).
 * @property intervalBellRoundCadence Number of completed rounds between milestone interval chimes (default 5).
 * @property isVoiceGuidanceEnabled Whether the gentle lady voice guidance triggers at phase transitions.
 * @property voiceCueStyle Preferred linguistic style for spoken voice guidance ([VoiceCueStyle]).
 */
data class PranayamaConfig(
    val steps: List<PranayamaStep>,
    val targetRounds: Int,
    val intervalBellRoundCadence: Int = 5,
    val isVoiceGuidanceEnabled: Boolean = true,
    val voiceCueStyle: VoiceCueStyle = VoiceCueStyle.SANSKRIT
) {
    /** Duration of the Puraka (Inhale) phase in seconds. Defaults to 4 seconds. */
    val purakSeconds: Int
        get() = steps.find { it.phase == PranayamaPhase.INHALE }?.durationSeconds ?: 4

    /** Duration of the Antar Kumbhaka (Hold In) phase in seconds. Defaults to 16 seconds. */
    val antarKumbhakSeconds: Int
        get() = steps.find { it.phase == PranayamaPhase.HOLD_IN }?.durationSeconds ?: 16

    /** Duration of the Rechaka (Exhale) phase in seconds. Defaults to 8 seconds. */
    val rechakSeconds: Int
        get() = steps.find { it.phase == PranayamaPhase.EXHALE }?.durationSeconds ?: 8

    /** Duration of the Bahya Kumbhaka (Hold Out) phase in seconds. Defaults to 16 seconds. */
    val bahyaKumbhakSeconds: Int
        get() = steps.find { it.phase == PranayamaPhase.HOLD_OUT }?.durationSeconds ?: 16

    /** Total duration in seconds of a single 4-phase breathwork round. */
    val cycleDurationSeconds: Int
        get() = steps.sumOf { it.durationSeconds }

    /** Total duration in seconds for the entire multi-round breathwork session. */
    val totalSessionSeconds: Int
        get() = cycleDurationSeconds * targetRounds

    /**
     * Constructs a mutated copy of [PranayamaConfig] with adjusted step durations and round parameters.
     *
     * @param purak New duration for Puraka (Inhale) in seconds.
     * @param antar New duration for Antar Kumbhaka (Hold In) in seconds.
     * @param rechak New duration for Rechaka (Exhale) in seconds.
     * @param bahya New duration for Bahya Kumbhaka (Hold Out) in seconds.
     * @param rounds Optional updated target rounds (keeps current if null).
     * @param voiceEnabled Optional voice guidance toggle.
     * @param voiceStyle Optional voice cue linguistic style.
     * @return Updated [PranayamaConfig] instance.
     */
    fun withStepDurations(
        purak: Int = purakSeconds,
        antar: Int = antarKumbhakSeconds,
        rechak: Int = rechakSeconds,
        bahya: Int = bahyaKumbhakSeconds,
        rounds: Int? = null,
        voiceEnabled: Boolean? = null,
        voiceStyle: VoiceCueStyle? = null
    ): PranayamaConfig {
        val updatedSteps = listOf(
            PranayamaStep(PranayamaPhase.INHALE, purak.coerceAtLeast(1)),
            PranayamaStep(PranayamaPhase.HOLD_IN, antar.coerceAtLeast(0)),
            PranayamaStep(PranayamaPhase.EXHALE, rechak.coerceAtLeast(1)),
            PranayamaStep(PranayamaPhase.HOLD_OUT, bahya.coerceAtLeast(0))
        )
        return copy(
            steps = updatedSteps,
            targetRounds = rounds ?: targetRounds,
            isVoiceGuidanceEnabled = voiceEnabled ?: isVoiceGuidanceEnabled,
            voiceCueStyle = voiceStyle ?: this.voiceCueStyle
        )
    }
}
