package com.habitbell.app.data.default

import com.habitbell.app.data.model.*

/**
 * Pre-configured wellness timer profiles based on scientifically validated health habits,
 * yogic traditions, and mindfulness practices.
 */
object DefaultProfiles {

    /**
     * Mindful Eating: 45-minute total meal duration with a 1-minute interval chime.
     * Encourages thorough chewing, paced eating, and improved digestive satiety signaling.
     */
    val EATING = TimerProfile(
        id = "eating-mindful-20",
        name = "Eating",
        type = TimerType.LINEAR,
        category = "Mindful Eating",
        iconName = "restaurant",
        totalDurationSeconds = 2700,      // 45 minutes total mealtime
        intervalDurationSeconds = 60,     // 1-minute bite pacing bell
        bellPattern = BellPattern.THREE_BELL,
        theme = ThemeMode.EYE_COMFORT,
        displayMode = true,
        pocketMode = false,
        isFavorite = true
    )

    /**
     * Reiki Energy Healing: 45-minute total session with a 3-minute transition bell.
     * Accompanying practitioners through 12 to 15 standard hand placement positions.
     */
    val REIKI = TimerProfile(
        id = "reiki-session-45",
        name = "Reiki",
        type = TimerType.LINEAR,
        category = "Energy Healing",
        iconName = "auto_awesome",
        totalDurationSeconds = 2700,      // 45 minutes
        intervalDurationSeconds = 180,    // 3-minute hand position transition
        bellPattern = BellPattern.THREE_BELL,
        theme = ThemeMode.AMOLED,
        displayMode = true,
        pocketMode = false,
        isFavorite = true
    )

    /**
     * Box Breathing (Sama Vritti): Equalized 4-4-4-4 ratio across 20 cycles.
     * Inhale 4s, Hold In 4s, Exhale 4s, Rest Empty 4s.
     * Clinically proven to regulate the autonomic nervous system and lower cortisol.
     */
    val PRANAYAMA_BOX = TimerProfile(
        id = "pranayama-box-breath",
        name = "Pranayama (Box Breath)",
        type = TimerType.MULTI_INTERVAL,
        category = "Breathwork",
        iconName = "self_improvement",
        theme = ThemeMode.AMOLED,
        displayMode = true,
        pocketMode = false,
        isFavorite = true,
        pranayamaConfig = PranayamaConfig(
            steps = listOf(
                PranayamaStep(PranayamaPhase.INHALE, 4),
                PranayamaStep(PranayamaPhase.HOLD_IN, 4),
                PranayamaStep(PranayamaPhase.EXHALE, 4),
                PranayamaStep(PranayamaPhase.HOLD_OUT, 4)
            ),
            targetRounds = 20
        )
    )

    /**
     * 4-7-8 Relaxing Breath: Dr. Andrew Weil technique across 15 cycles.
     * Inhale 4s, Hold In 7s, Exhale 8s, Rest 2s.
     * Activates the parasympathetic vagal response for deep tranquility and sleep preparation.
     */
    val PRANAYAMA_478 = TimerProfile(
        id = "pranayama-478-relax",
        name = "Pranayama (4-7-8 Deep Relax)",
        type = TimerType.MULTI_INTERVAL,
        category = "Deep Relaxation",
        iconName = "air",
        theme = ThemeMode.AMOLED,
        displayMode = true,
        pocketMode = false,
        isFavorite = false,
        pranayamaConfig = PranayamaConfig(
            steps = listOf(
                PranayamaStep(PranayamaPhase.INHALE, 4),
                PranayamaStep(PranayamaPhase.HOLD_IN, 7),
                PranayamaStep(PranayamaPhase.EXHALE, 8),
                PranayamaStep(PranayamaPhase.HOLD_OUT, 2)
            ),
            targetRounds = 15
        )
    )

    /**
     * Surya Namaskar (Sun Salutation): 12-asana compound sequence across 5 full rounds.
     * 5 seconds per posture with synchronized breath cues.
     */
    val SURYA_NAMASKAR = TimerProfile(
        id = "surya-namaskar-compound",
        name = "Surya Namaskar",
        type = TimerType.COMPOUND,
        category = "Yoga Sequences",
        iconName = "wb_sunny",
        theme = ThemeMode.EYE_COMFORT,
        displayMode = true,
        pocketMode = false,
        isFavorite = false,
        compoundConfig = CompoundConfig(
            poses = listOf(
                CompoundPose(1, "Pranamasana", "Prayer Pose", 5, "Inhale & Exhale gently"),
                CompoundPose(2, "Hastauttanasana", "Raised Arms Pose", 5, "Inhale, arch gently"),
                CompoundPose(3, "Hastapadasana", "Standing Forward Bend", 5, "Exhale, fold down"),
                CompoundPose(4, "Ashwa Sanchalanasana", "Equestrian Pose", 5, "Inhale right leg back"),
                CompoundPose(5, "Dandasana", "Plank Pose", 5, "Hold breath in plank"),
                CompoundPose(6, "Ashtanga Namaskara", "Salute with 8 Parts", 5, "Exhale chest to mat"),
                CompoundPose(7, "Bhujangasana", "Cobra Pose", 5, "Inhale lift chest"),
                CompoundPose(8, "Adho Mukha Svanasana", "Downward-Facing Dog", 5, "Exhale hips up"),
                CompoundPose(9, "Ashwa Sanchalanasana", "Equestrian Pose", 5, "Inhale left leg forward"),
                CompoundPose(10, "Hastapadasana", "Standing Forward Bend", 5, "Exhale fold to knees"),
                CompoundPose(11, "Hastauttanasana", "Raised Arms Pose", 5, "Inhale reach upward"),
                CompoundPose(12, "Tadasana", "Mountain Pose", 5, "Exhale hands to heart")
            ),
            targetRounds = 5
        )
    )

    /**
     * Mindful Walking: 15-minute walking meditation with 5-minute time intervals and 500-step check-in bells.
     * Dual-trigger mode completes at 2,000 steps or 15 minutes. Pocket mode enabled by default.
     */
    val MINDFUL_WALKING = TimerProfile(
        id = "mindful-walking-15",
        name = "Mindful Walking",
        type = TimerType.LINEAR,
        category = "Movement",
        iconName = "directions_walk",
        totalDurationSeconds = 900,      // 15 minutes
        intervalDurationSeconds = 300,   // 5 minutes
        bellPattern = BellPattern.THREE_BELL,
        theme = ThemeMode.AMOLED,
        displayMode = true,
        pocketMode = true,
        isFavorite = true,
        stepGoal = 2000,
        stepInterval = 500,
        stepTriggerMode = StepTriggerMode.TIME_OR_STEPS
    )

    /**
     * Step Meditation (3,000 Steps): Step-focused walking meditation with a soothing bell every 500 steps.
     * Step-exclusive trigger mode completes when the 3,000-step mindfulness goal is attained.
     */
    val STEP_WALK_MEDITATION = TimerProfile(
        id = "step-walk-3000",
        name = "Step Meditation (3k)",
        type = TimerType.LINEAR,
        category = "Movement",
        iconName = "hiking",
        totalDurationSeconds = 2700,     // 45 minutes maximum duration
        intervalDurationSeconds = 0,     // Driven primarily by step interval bells
        bellPattern = BellPattern.THREE_BELL,
        theme = ThemeMode.AMOLED,
        displayMode = true,
        pocketMode = true,
        isFavorite = false,
        stepGoal = 3000,
        stepInterval = 500,
        stepTriggerMode = StepTriggerMode.STEPS_ONLY
    )

    /**
     * Power Walking (5,000 Steps): High-cadence walking workout with a pace bell every 1,000 steps.
     * Dual-trigger mode completes at 5,000 steps or 60 minutes.
     */
    val POWER_STEP_WALK = TimerProfile(
        id = "power-step-walk-5000",
        name = "Power Walking (5k)",
        type = TimerType.LINEAR,
        category = "Movement",
        iconName = "directions_run",
        totalDurationSeconds = 3600,     // 60 minutes
        intervalDurationSeconds = 0,
        bellPattern = BellPattern.SINGLE,
        theme = ThemeMode.EYE_COMFORT,
        displayMode = true,
        pocketMode = true,
        isFavorite = false,
        stepGoal = 5000,
        stepInterval = 1000,
        stepTriggerMode = StepTriggerMode.TIME_OR_STEPS
    )

    /**
     * Mindful Reading: 30-minute focused study/reading sprint with 10-minute eye relief chimes.
     */
    val MINDFUL_READING = TimerProfile(
        id = "mindful-reading-30",
        name = "Mindful Reading",
        type = TimerType.LINEAR,
        category = "Focus",
        iconName = "menu_book",
        totalDurationSeconds = 1800,     // 30 minutes
        intervalDurationSeconds = 600,   // 10-minute eye/posture reset
        bellPattern = BellPattern.SINGLE,
        theme = ThemeMode.EYE_COMFORT,
        displayMode = true,
        pocketMode = false,
        isFavorite = false
    )

    /**
     * Hydration Habit: 60-minute recurring reminder chime encouraging regular water intake.
     */
    val HYDRATION = TimerProfile(
        id = "hydration-reminder-60",
        name = "Hydration Habit",
        type = TimerType.LINEAR,
        category = "Daily Health",
        iconName = "water_drop",
        totalDurationSeconds = 3600,     // 60 minutes
        intervalDurationSeconds = 3600,
        bellPattern = BellPattern.SINGLE,
        theme = ThemeMode.LIGHT,
        displayMode = false,
        pocketMode = true,
        isFavorite = false
    )

    /**
     * Complete list of all default preset wellness profiles.
     */
    val ALL_PRESETS = listOf(
        EATING,
        REIKI,
        PRANAYAMA_BOX,
        PRANAYAMA_478,
        SURYA_NAMASKAR,
        MINDFUL_WALKING,
        STEP_WALK_MEDITATION,
        POWER_STEP_WALK,
        MINDFUL_READING,
        HYDRATION
    )
}
