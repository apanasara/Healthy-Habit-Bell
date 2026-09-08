package com.habitbell.app.data.model

/**
 * Voice guidance prompt delivery style for Pranayama phase transitions.
 */
enum class VoiceCueStyle(val displayName: String, val shortLabel: String) {
    /** Option 1: Authentic Sanskrit cues spoken in soothing Lata-style melodious swara ("Purak", "Kumbhak", "Rechak", "Bahya"). Default. */
    SANSKRIT("Option 1: Only Sanskrit (Purak, Kumbhak, Rechak)", "Only Sanskrit"),

    /** Option 2: Sanskrit + English bilingual guidance cues ("Purak... Inhale", "Kumbhak... Hold", "Rechak... Exhale"). */
    BILINGUAL("Option 2: Sanskrit + English (Purak... Inhale)", "Sanskrit + English"),

    /** Option 3: English only cues spoken with gentle mindfulness cadence ("Inhale", "Hold", "Exhale", "Rest"). */
    ENGLISH("Option 3: English (Inhale, Hold, Exhale)", "English")
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
 * @property bandhaScript Classical Sanskrit guidance for energetic bandhas (e.g. Tri-Bandha during Antar Kumbhaka).
 * @property bandhaEnglish English transliteration and guidance for energetic bandhas.
 */
enum class PranayamaPhase(
    val displayName: String,
    val cue: String,
    val sanskritName: String,
    val sanskritScript: String,
    val bandhaScript: String? = null,
    val bandhaEnglish: String? = null
) {
    /** Puraka: Controlled diaphragmatic inhalation drawing in cosmic Prana. */
    INHALE("Inhale", "Inhale deeply", "Purak", "पूरक"),

    /**
     * Antar Kumbhaka: Internal breath retention awakening Sushumna Nadi with full lungs.
     * Accompanied by classical Tri-Bandha (Mūla Bandha, Madhyama Uḍḍīyāna Bandha, and Kūpa/Jālandhara Bandha).
     */
    HOLD_IN(
        displayName = "Hold In",
        cue = "Hold breath with Tri-Bandha",
        sanskritName = "Kumbhak",
        sanskritScript = "अभ्यन्तर कुम्भक",
        bandhaScript = "त्रिबन्ध (मूलबन्ध • उड्डीयान बन्ध • कूपबन्ध)",
        bandhaEnglish = "Tri-Bandha: Mūla • Uḍḍīyāna • Kūpa"
    ),

    /** Rechaka: Slow, continuous exhalation releasing Apana and mental tension. */
    EXHALE("Exhale", "Exhale slowly", "Rechak", "रेचक"),

    /**
     * Bahya Kumbhaka: External breath retention resting in Shunya (the primordial void).
     * Accompanied by classical Tri-Bandha (Mūla Bandha, Pūrṇa Uḍḍīyāna Bandha, and Kūpa/Jālandhara Bandha).
     */
    HOLD_OUT(
        displayName = "Hold Out",
        cue = "Rest in emptiness with Tri-Bandha",
        sanskritName = "Kumbhak",
        sanskritScript = "बाह्य कुम्भक",
        bandhaScript = "त्रिबन्ध (मूलबन्ध • उड्डीयान बन्ध • कूपबन्ध)",
        bandhaEnglish = "Tri-Bandha: Mūla • Uḍḍīyāna • Kūpa"
    )
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
 * ## Yogic Literature Grounding
 * - **Target Rounds**: Defaults to **12 rounds**, the foundational *Adhama* (Junior standard) prescribed
 *   in *Hatha Yoga Pradipika* (2.12) and *Gheranda Samhita* (5.48-5.50). With the 4:16:8:16 ratio
 *   (44s per cycle), 12 rounds totals 528 seconds (8m 48s), establishing an optimal daily sadhana session.
 * - **Voice Guidance**: Defaults to **Option A (Traditional Sanskrit)** with spoken cues "Purak", "Kumbhak",
 *   "Rechak", "Kumbhak".
 * - **Interval Bell**: Defaults to **disabled (false)** so the practitioner's meditative state remains
 *   undisturbed. When enabled by user preference, a gentle, non-startling 432 Hz warm Tibetan singing bowl
 *   sounds every [intervalBellRoundCadence] rounds.
 *
 * @property steps Ordered list of breath phases comprising one full breathing cycle.
 * @property targetRounds Number of cycles/repetitions to complete the full session (default 12 rounds per HYP 2.12).
 * @property isIntervalBellEnabled Whether periodic milestone bells are active (default false per user meditative protection).
 * @property intervalBellRoundCadence Number of completed rounds between milestone interval chimes (default 5 when enabled).
 * @property isVoiceGuidanceEnabled Whether the gentle lady voice guidance triggers at phase transitions.
 * @property voiceCueStyle Preferred linguistic style for spoken voice guidance ([VoiceCueStyle]).
 * @property isTriBandhaVoiceEnabled Whether gentle lady voice speaks the Tri-Bandha guidance cue during Kumbhaka.
 * @property voiceVolume Subdued volume gain for voice guidance (0.15f..1.0f, default 0.52f).
 */
data class PranayamaConfig(
    val steps: List<PranayamaStep>,
    val targetRounds: Int = 12,
    val isIntervalBellEnabled: Boolean = false,
    val intervalBellRoundCadence: Int = 5,
    val isVoiceGuidanceEnabled: Boolean = true,
    val voiceCueStyle: VoiceCueStyle = VoiceCueStyle.SANSKRIT,
    val isTriBandhaVoiceEnabled: Boolean = true,
    val voiceVolume: Float = 0.52f
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
     * @param intervalEnabled Optional milestone interval bell enabled toggle.
     * @param cadence Optional milestone round cadence.
     * @param voiceEnabled Optional voice guidance toggle.
     * @param voiceStyle Optional voice cue linguistic style.
     * @param tribandhaVoiceEnabled Optional Tri-Bandha spoken voice cue toggle during Kumbhaka.
     * @param voiceVolume Optional voice guidance gain (0.15f..1.0f).
     * @return Updated [PranayamaConfig] instance.
     */
    fun withStepDurations(
        purak: Int = purakSeconds,
        antar: Int = antarKumbhakSeconds,
        rechak: Int = rechakSeconds,
        bahya: Int = bahyaKumbhakSeconds,
        rounds: Int? = null,
        intervalEnabled: Boolean? = null,
        cadence: Int? = null,
        voiceEnabled: Boolean? = null,
        voiceStyle: VoiceCueStyle? = null,
        tribandhaVoiceEnabled: Boolean? = null,
        voiceVolume: Float? = null
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
            isIntervalBellEnabled = intervalEnabled ?: isIntervalBellEnabled,
            intervalBellRoundCadence = cadence ?: intervalBellRoundCadence,
            isVoiceGuidanceEnabled = voiceEnabled ?: isVoiceGuidanceEnabled,
            voiceCueStyle = voiceStyle ?: this.voiceCueStyle,
            isTriBandhaVoiceEnabled = tribandhaVoiceEnabled ?: this.isTriBandhaVoiceEnabled,
            voiceVolume = voiceVolume ?: this.voiceVolume
        )
    }
}

/**
 * # PranayamaRatioStage
 *
 * Classical yogic breath ratio proportional stages.
 *
 * Grounded in classical Hatha Yoga literature (*Hatha Yoga Pradipika* & *Gheranda Samhita*),
 * practitioners advance through proportional stages when Bahya Kumbhaka (external void) is included:
 * - **Sama Vritti (Equalized/Box)**: 1 : 1 : 1 : 1 (e.g. 4s : 4s : 4s : 4s)
 * - **Madhya (Intermediate)**: 1 : 2 : 2 : 1 (e.g. 4s : 8s : 8s : 4s)
 * - **Visama Vritti (Classical Advanced)**: 1 : 4 : 2 : 4 (e.g. 4s : 16s : 8s : 16s) [Default]
 * - **Visama Vritti (Gentle Void)**: 1 : 4 : 2 : 1 (e.g. 4s : 16s : 8s : 4s)
 * - **Visama Vritti (Half Void)**: 1 : 4 : 2 : 2 (e.g. 4s : 16s : 8s : 8s)
 * - **Custom**: User-defined independent seconds
 *
 * @property title Human-readable stage title for the ratio dropdown.
 * @property ratioText Ratio formula description detailing proportional step counts.
 * @property purakRatio Puraka (Inhale) proportional scalar.
 * @property antarRatio Antar Kumbhaka (Hold In) proportional scalar.
 * @property rechakRatio Rechaka (Exhale) proportional scalar.
 * @property bahyaRatio Bahya Kumbhaka (Hold Out) proportional scalar.
 */
enum class PranayamaRatioStage(
    val title: String,
    val ratioText: String,
    val purakRatio: Int,
    val antarRatio: Int,
    val rechakRatio: Int,
    val bahyaRatio: Int
) {
    VISAMA_VRITTI_CLASSICAL(
        title = "Visama Vritti (Classical Advanced)",
        ratioText = "1 : 4 : 2 : 4 (Classical Hatha Yoga default)",
        purakRatio = 1,
        antarRatio = 4,
        rechakRatio = 2,
        bahyaRatio = 4
    ),
    MADHYA_INTERMEDIATE(
        title = "Madhya (Intermediate Stage)",
        ratioText = "1 : 2 : 2 : 1 (Balanced Void)",
        purakRatio = 1,
        antarRatio = 2,
        rechakRatio = 2,
        bahyaRatio = 1
    ),
    SAMA_VRITTI_BOX(
        title = "Sama Vritti (Equalized / Box)",
        ratioText = "1 : 1 : 1 : 1 (Equal Quadrants)",
        purakRatio = 1,
        antarRatio = 1,
        rechakRatio = 1,
        bahyaRatio = 1
    ),
    VISAMA_VRITTI_GENTLE(
        title = "Visama Vritti (Gentle Void)",
        ratioText = "1 : 4 : 2 : 1 (Mild Shunya Void)",
        purakRatio = 1,
        antarRatio = 4,
        rechakRatio = 2,
        bahyaRatio = 1
    ),
    VISAMA_VRITTI_HALF(
        title = "Visama Vritti (Half Void)",
        ratioText = "1 : 4 : 2 : 2 (Half-duration Shunya)",
        purakRatio = 1,
        antarRatio = 4,
        rechakRatio = 2,
        bahyaRatio = 2
    ),
    CUSTOM(
        title = "Custom User Ratios",
        ratioText = "Manual seconds entry",
        purakRatio = 0,
        antarRatio = 0,
        rechakRatio = 0,
        bahyaRatio = 0
    );

    companion object {
        /**
         * Detects the matching proportional ratio stage based on four given phase durations.
         *
         * @param purak Inhalation duration in seconds.
         * @param antar Internal retention duration in seconds.
         * @param rechak Exhalation duration in seconds.
         * @param bahya External retention duration in seconds.
         * @return Matching [PranayamaRatioStage] or [CUSTOM] if non-standard.
         */
        fun matchRatio(purak: Int, antar: Int, rechak: Int, bahya: Int): PranayamaRatioStage {
            if (purak <= 0) return CUSTOM
            val p = purak.toDouble()
            return when {
                antar == (p * 4).toInt() && rechak == (p * 2).toInt() && bahya == (p * 4).toInt() -> VISAMA_VRITTI_CLASSICAL
                antar == (p * 2).toInt() && rechak == (p * 2).toInt() && bahya == (p * 1).toInt() -> MADHYA_INTERMEDIATE
                antar == (p * 1).toInt() && rechak == (p * 1).toInt() && bahya == (p * 1).toInt() -> SAMA_VRITTI_BOX
                antar == (p * 4).toInt() && rechak == (p * 2).toInt() && bahya == (p * 1).toInt() -> VISAMA_VRITTI_GENTLE
                antar == (p * 4).toInt() && rechak == (p * 2).toInt() && bahya == (p * 2).toInt() -> VISAMA_VRITTI_HALF
                else -> CUSTOM
            }
        }
    }
}

