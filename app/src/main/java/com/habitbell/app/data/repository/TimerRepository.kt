package com.habitbell.app.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.habitbell.app.data.default.DefaultProfiles
import com.habitbell.app.data.default.DefaultReminders
import com.habitbell.app.data.model.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * Top-level DataStore extension property for local persistent preferences.
 */
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "habit_bell_prefs")

/**
 * Repository orchestrating access, modification, and persistence of wellness timer profiles,
 * favorite lists, recent session history, and scheduled habit reminders.
 *
 * Exposes immutable reactive [StateFlow] pipelines to provide single-source-of-truth state
 * updates across ViewModels, car automotive interfaces, and cast services.
 *
 * @param context Android context for DataStore initialization and disk operations.
 */
class TimerRepository(private val context: Context) {

    /** SharedPreferences handle for persisting user-customized profile durations and interval timings. */
    private val prefs = context.getSharedPreferences("habit_bell_settings", Context.MODE_PRIVATE)

    /** Coroutine scope on [Dispatchers.IO] dedicated to background data persistence. */
    private val scope = CoroutineScope(Dispatchers.IO)

    /** Mutable backing stream holding the complete catalog of preset and user-created profiles with persisted customizations. */
    private val _profiles = MutableStateFlow<List<TimerProfile>>(loadInitialProfiles())

    /**
     * Loads the default preset catalog and overlays any persistent user customizations
     * (total duration, interval timing) previously saved by the user.
     *
     * @return List of [TimerProfile] entities with user preferences restored.
     */
    private fun loadInitialProfiles(): List<TimerProfile> {
        return DefaultProfiles.ALL_PRESETS.map { defaultProfile ->
            val savedDuration = prefs.getInt("profile_duration_${defaultProfile.id}", -1)
            val savedInterval = prefs.getInt("profile_interval_${defaultProfile.id}", -1)

            val dur = if (savedDuration > 0) {
                savedDuration
            } else if (defaultProfile.id == "eating-mindful-20") {
                val aliasDur = prefs.getInt("profile_duration_eating", -1)
                if (aliasDur > 0) aliasDur else defaultProfile.totalDurationSeconds
            } else {
                defaultProfile.totalDurationSeconds
            }

            val inter = if (savedInterval >= 0) {
                savedInterval
            } else if (defaultProfile.id == "eating-mindful-20") {
                val aliasInter = prefs.getInt("profile_interval_eating", -1)
                if (aliasInter >= 0) aliasInter else defaultProfile.intervalDurationSeconds
            } else {
                defaultProfile.intervalDurationSeconds
            }

            // Restore any persistent Pranayama customizations
            val restoredPranayama = defaultProfile.pranayamaConfig?.let { baseConfig ->
                val pPurak = prefs.getInt("profile_pranayama_purak_${defaultProfile.id}", -1)
                val pAntar = prefs.getInt("profile_pranayama_antar_${defaultProfile.id}", -1)
                val pRechak = prefs.getInt("profile_pranayama_rechak_${defaultProfile.id}", -1)
                val pBahya = prefs.getInt("profile_pranayama_bahya_${defaultProfile.id}", -1)
                val pRounds = prefs.getInt("profile_pranayama_rounds_${defaultProfile.id}", -1)
                val pVoiceEnabled = prefs.getBoolean("profile_pranayama_voice_${defaultProfile.id}", baseConfig.isVoiceGuidanceEnabled)
                val pVoiceStyleStr = prefs.getString("profile_pranayama_voice_style_${defaultProfile.id}", baseConfig.voiceCueStyle.name)
                val pVoiceStyle = try {
                    VoiceCueStyle.valueOf(pVoiceStyleStr ?: baseConfig.voiceCueStyle.name)
                } catch (_: Exception) {
                    baseConfig.voiceCueStyle
                }
                val pTriBandhaVoice = prefs.getBoolean("profile_pranayama_tribandha_voice_${defaultProfile.id}", baseConfig.isTriBandhaVoiceEnabled)
                val pIntervalBellEnabled = prefs.getBoolean("profile_pranayama_interval_bell_${defaultProfile.id}", baseConfig.isIntervalBellEnabled)
                val pIntervalCadence = prefs.getInt("profile_pranayama_interval_cadence_${defaultProfile.id}", baseConfig.intervalBellRoundCadence)
                val pVoiceVolume = prefs.getFloat("profile_pranayama_voice_vol_${defaultProfile.id}", baseConfig.voiceVolume)

                if (pPurak > 0 || pAntar >= 0 || pRechak > 0 || pBahya >= 0 || pRounds > 0) {
                    baseConfig.withStepDurations(
                        purak = if (pPurak > 0) pPurak else baseConfig.purakSeconds,
                        antar = if (pAntar >= 0) pAntar else baseConfig.antarKumbhakSeconds,
                        rechak = if (pRechak > 0) pRechak else baseConfig.rechakSeconds,
                        bahya = if (pBahya >= 0) pBahya else baseConfig.bahyaKumbhakSeconds,
                        rounds = if (pRounds > 0) pRounds else baseConfig.targetRounds,
                        intervalEnabled = pIntervalBellEnabled,
                        cadence = pIntervalCadence,
                        voiceEnabled = pVoiceEnabled,
                        voiceStyle = pVoiceStyle,
                        tribandhaVoiceEnabled = pTriBandhaVoice,
                        voiceVolume = pVoiceVolume
                    )
                } else {
                    baseConfig.copy(
                        isIntervalBellEnabled = pIntervalBellEnabled,
                        intervalBellRoundCadence = pIntervalCadence,
                        isVoiceGuidanceEnabled = pVoiceEnabled,
                        voiceCueStyle = pVoiceStyle,
                        isTriBandhaVoiceEnabled = pTriBandhaVoice,
                        voiceVolume = pVoiceVolume
                    )
                }
            }

            // Restore any persistent Surya Namaskar customizations
            val restoredCompound = defaultProfile.compoundConfig?.let { baseConfig ->
                val sRounds = prefs.getInt("profile_surya_rounds_${defaultProfile.id}", -1)
                val sPreset = prefs.getString("profile_surya_preset_${defaultProfile.id}", baseConfig.speedPreset) ?: baseConfig.speedPreset
                val sVoiceModeStr = prefs.getString("profile_surya_voice_mode_${defaultProfile.id}", baseConfig.voiceCueMode.name)
                val sVoiceMode = try {
                    com.habitbell.app.audio.VoiceCueMode.valueOf(sVoiceModeStr ?: baseConfig.voiceCueMode.name)
                } catch (_: Exception) {
                    baseConfig.voiceCueMode
                }

                val restoredPoses = baseConfig.poses.mapIndexed { idx, basePose ->
                    val savedDuration = prefs.getInt("profile_surya_pose_dur_${defaultProfile.id}_$idx", -1)
                    val poseDur = if (savedDuration > 0) savedDuration else basePose.durationSeconds
                    basePose.copy(
                        durationSeconds = poseDur,
                        voiceCueMode = sVoiceMode
                    )
                }

                val finalRounds = if (sRounds > 0) sRounds else baseConfig.targetRounds
                baseConfig.copy(
                    poses = restoredPoses,
                    targetRounds = finalRounds,
                    speedPreset = sPreset,
                    voiceCueMode = sVoiceMode
                )
            }

            val finalTotalDuration = if (restoredCompound != null) {
                restoredCompound.poses.sumOf { it.durationSeconds } * restoredCompound.targetRounds
            } else {
                dur
            }

            defaultProfile.copy(
                totalDurationSeconds = finalTotalDuration,
                intervalDurationSeconds = inter,
                pranayamaConfig = restoredPranayama,
                compoundConfig = restoredCompound
            )
        }
    }

    /** Public read-only stream emitting the live list of available timer profiles. */
    val profiles: StateFlow<List<TimerProfile>> = _profiles.asStateFlow()

    /** Mutable backing stream tracking recently executed profile IDs (ordered most recent first). */
    private val _recentProfileIds = MutableStateFlow<List<String>>(listOf("eating-mindful-20", "pranayama-box-breath", "reiki-session-45"))

    /** Public read-only stream emitting list of recently used profile IDs. */
    val recentProfileIds: StateFlow<List<String>> = _recentProfileIds.asStateFlow()

    /** Mutable backing stream for active daily habit reminders. */
    private val _reminders = MutableStateFlow<List<RoutineReminder>>(DefaultReminders.ALL_REMINDERS)

    /** Public read-only stream emitting list of scheduled routine reminders. */
    val reminders: StateFlow<List<RoutineReminder>> = _reminders.asStateFlow()

    /**
     * Derived stream filtering profiles marked as favorite by the user.
     * Starts eagerly to ensure instantaneous UI population.
     */
    val favorites: StateFlow<List<TimerProfile>> = _profiles.map { list ->
        list.filter { it.isFavorite }
    }.stateIn(scope, SharingStarted.Eagerly, DefaultProfiles.ALL_PRESETS.filter { it.isFavorite })

    /**
     * Queries a profile by its unique ID.
     *
     * @param id The unique profile string identifier.
     * @return Matching [TimerProfile] if found, or null otherwise.
     */
    fun getProfileById(id: String): TimerProfile? {
        return _profiles.value.find { it.id == id }
    }

    /**
     * Toggles the favorite status for a given profile ID.
     *
     * @param profileId Unique ID of the profile whose favorite status will be inverted.
     */
    fun toggleFavorite(profileId: String) {
        _profiles.update { currentList ->
            currentList.map { profile ->
                if (profile.id == profileId) {
                    profile.copy(isFavorite = !profile.isFavorite)
                } else {
                    profile
                }
            }
        }
    }

    /**
     * Records the completion or execution of a session, moving its ID to the front of
     * the recent history list (capped at 5 recent profiles).
     *
     * @param profileId Unique ID of the completed profile.
     */
    fun recordSessionCompleted(profileId: String) {
        _recentProfileIds.update { list ->
            val updated = list.filter { it != profileId }.toMutableList()
            updated.add(0, profileId)
            if (updated.size > 5) updated.take(5) else updated
        }
    }

    /**
     * Saves a newly created or edited custom timer profile into the repository.
     * If a profile with the same ID already exists, it is replaced; otherwise appended.
     *
     * @param profile The [TimerProfile] instance to insert or update.
     */
    fun saveCustomProfile(profile: TimerProfile) {
        _profiles.update { current ->
            val existingIndex = current.indexOfFirst { it.id == profile.id }
            if (existingIndex >= 0) {
                current.toMutableList().apply { set(existingIndex, profile) }
            } else {
                current + profile
            }
        }
    }

    /**
     * Updates specific runtime settings of an existing profile without mutating its core identity.
     *
     * @param profileId Target profile identifier.
     * @param totalDuration Optional new total duration in seconds.
     * @param intervalDuration Optional new interval duration in seconds.
     * @param displayMode Optional toggle for keep-screen-on display mode.
     * @param pocketMode Optional toggle for proximity-sensor pocket mode.
     */
    fun updateProfileSettings(
        profileId: String,
        totalDuration: Int? = null,
        intervalDuration: Int? = null,
        displayMode: Boolean? = null,
        pocketMode: Boolean? = null,
        stepGoal: Int? = null,
        stepInterval: Int? = null,
        stepTriggerMode: com.habitbell.app.data.model.StepTriggerMode? = null
    ) {
        val editor = prefs.edit()
        if (totalDuration != null) {
            editor.putInt("profile_duration_$profileId", totalDuration)
            if (profileId == "eating-mindful-20" || profileId == "eating") {
                editor.putInt("profile_duration_eating", totalDuration)
                editor.putInt("profile_duration_eating-mindful-20", totalDuration)
            }
        }
        if (intervalDuration != null) {
            editor.putInt("profile_interval_$profileId", intervalDuration)
            if (profileId == "eating-mindful-20" || profileId == "eating") {
                editor.putInt("profile_interval_eating", intervalDuration)
                editor.putInt("profile_interval_eating-mindful-20", intervalDuration)
            }
        }
        if (stepGoal != null) {
            editor.putInt("profile_step_goal_$profileId", stepGoal)
        }
        if (stepInterval != null) {
            editor.putInt("profile_step_interval_$profileId", stepInterval)
        }
        editor.apply()

        _profiles.update { list ->
            list.map { profile ->
                if (profile.id == profileId || (profile.id == "eating-mindful-20" && profileId == "eating")) {
                    profile.copy(
                        totalDurationSeconds = totalDuration ?: profile.totalDurationSeconds,
                        intervalDurationSeconds = intervalDuration ?: profile.intervalDurationSeconds,
                        displayMode = displayMode ?: profile.displayMode,
                        pocketMode = pocketMode ?: profile.pocketMode,
                        stepGoal = stepGoal ?: profile.stepGoal,
                        stepInterval = stepInterval ?: profile.stepInterval,
                        stepTriggerMode = stepTriggerMode ?: profile.stepTriggerMode
                    )
                } else {
                    profile
                }
            }
        }
    }

    /**
     * Persists and updates Pranayama breathwork timing parameters, milestone interval chimes, and voice guidance.
     *
     * @param profileId Unique ID of the target Pranayama profile.
     * @param purakSeconds Duration for Puraka (Inhale) in seconds.
     * @param antarKumbhakSeconds Duration for Antar Kumbhaka (Hold In) in seconds.
     * @param rechakSeconds Duration for Rechaka (Exhale) in seconds.
     * @param bahyaKumbhakSeconds Duration for Bahya Kumbhaka (Hold Out) in seconds.
     * @param targetRounds Total cycles/repetitions configured for the session.
     * @param isIntervalBellEnabled Whether periodic milestone bells sound during the session (default false).
     * @param intervalBellCadence Number of rounds between milestone bells (e.g. 5).
     * @param isVoiceEnabled Whether gentle lady voice prompts are triggered on phase transitions.
     * @param voiceStyle Linguistic cue style ([VoiceCueStyle]).
     * @param isTriBandhaVoiceEnabled Whether gentle lady voice speaks the Tri-Bandha prompt during Kumbhaka.
     * @param voiceVolume Subdued voice guidance volume (0.15f..1.0f, default 0.52f).
     */
    fun updatePranayamaSettings(
        profileId: String,
        purakSeconds: Int,
        antarKumbhakSeconds: Int,
        rechakSeconds: Int,
        bahyaKumbhakSeconds: Int,
        targetRounds: Int,
        isIntervalBellEnabled: Boolean = false,
        intervalBellCadence: Int = 5,
        isVoiceEnabled: Boolean = true,
        voiceStyle: VoiceCueStyle = VoiceCueStyle.SANSKRIT,
        isTriBandhaVoiceEnabled: Boolean = true,
        voiceVolume: Float = 0.52f
    ) {
        prefs.edit()
            .putInt("profile_pranayama_purak_$profileId", purakSeconds)
            .putInt("profile_pranayama_antar_$profileId", antarKumbhakSeconds)
            .putInt("profile_pranayama_rechak_$profileId", rechakSeconds)
            .putInt("profile_pranayama_bahya_$profileId", bahyaKumbhakSeconds)
            .putInt("profile_pranayama_rounds_$profileId", targetRounds)
            .putBoolean("profile_pranayama_interval_bell_$profileId", isIntervalBellEnabled)
            .putInt("profile_pranayama_interval_cadence_$profileId", intervalBellCadence)
            .putBoolean("profile_pranayama_voice_$profileId", isVoiceEnabled)
            .putString("profile_pranayama_voice_style_$profileId", voiceStyle.name)
            .putBoolean("profile_pranayama_tribandha_voice_$profileId", isTriBandhaVoiceEnabled)
            .putFloat("profile_pranayama_voice_vol_$profileId", voiceVolume)
            .apply()

        _profiles.update { list ->
            list.map { profile ->
                if (profile.id == profileId) {
                    val updatedConfig = (profile.pranayamaConfig ?: PranayamaConfig(
                        steps = listOf(
                            PranayamaStep(PranayamaPhase.INHALE, purakSeconds),
                            PranayamaStep(PranayamaPhase.HOLD_IN, antarKumbhakSeconds),
                            PranayamaStep(PranayamaPhase.EXHALE, rechakSeconds),
                            PranayamaStep(PranayamaPhase.HOLD_OUT, bahyaKumbhakSeconds)
                        ),
                        targetRounds = targetRounds,
                        isIntervalBellEnabled = isIntervalBellEnabled,
                        intervalBellRoundCadence = intervalBellCadence,
                        isTriBandhaVoiceEnabled = isTriBandhaVoiceEnabled,
                        voiceVolume = voiceVolume
                    )).withStepDurations(
                        purak = purakSeconds,
                        antar = antarKumbhakSeconds,
                        rechak = rechakSeconds,
                        bahya = bahyaKumbhakSeconds,
                        rounds = targetRounds,
                        intervalEnabled = isIntervalBellEnabled,
                        cadence = intervalBellCadence,
                        voiceEnabled = isVoiceEnabled,
                        voiceStyle = voiceStyle,
                        tribandhaVoiceEnabled = isTriBandhaVoiceEnabled,
                        voiceVolume = voiceVolume
                    )
                    profile.copy(
                        pranayamaConfig = updatedConfig,
                        totalDurationSeconds = updatedConfig.totalSessionSeconds
                    )
                } else {
                    profile
                }
            }
        }
    }

    /**
     * Persists and updates Surya Namaskar sequence posture durations, target rounds, speed presets, and voice mode.
     *
     * @param profileId Unique ID of the target Surya Namaskar profile (e.g. "surya-namaskar-compound").
     * @param poses List of configured [CompoundPose] postures with custom or preset seconds.
     * @param targetRounds Total repetition cycles configured for the session.
     * @param speedPreset Active preset key ("slow", "moderate", "fast", "custom").
     * @param customPaceSeconds Uniform custom pace seconds per posture.
     * @param voiceCueMode Selected global voice guidance mode ([com.habitbell.app.audio.VoiceCueMode]).
     */
    fun updateSuryaSettings(
        profileId: String,
        poses: List<CompoundPose>,
        targetRounds: Int,
        speedPreset: String = "moderate",
        customPaceSeconds: Int = 7,
        voiceCueMode: com.habitbell.app.audio.VoiceCueMode = com.habitbell.app.audio.VoiceCueMode.STEP_NAME
    ) {
        val editor = prefs.edit()
        editor.putInt("profile_surya_rounds_$profileId", targetRounds)
        editor.putString("profile_surya_preset_$profileId", speedPreset)
        editor.putInt("profile_surya_custom_pace_$profileId", customPaceSeconds)
        editor.putString("profile_surya_voice_mode_$profileId", voiceCueMode.name)
        poses.forEachIndexed { idx, pose ->
            editor.putInt("profile_surya_pose_dur_${profileId}_$idx", pose.durationSeconds)
        }
        editor.apply()

        val totalSec = poses.sumOf { it.durationSeconds } * targetRounds
        _profiles.update { list ->
            list.map { profile ->
                if (profile.id == profileId) {
                    profile.copy(
                        totalDurationSeconds = totalSec,
                        compoundConfig = CompoundConfig(
                            poses = poses,
                            targetRounds = targetRounds,
                            speedPreset = speedPreset,
                            voiceCueMode = voiceCueMode
                        )
                    )
                } else {
                    profile
                }
            }
        }
    }
}
