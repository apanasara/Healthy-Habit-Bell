/**
 * # SuryaTimerViewModel
 *
 * ViewModel orchestrating state management and persistence for the Surya Namaskar sequence editor.
 *
 * ## Architectural Role & Component Relationships
 * Presentation layer ViewModel bridging [com.habitbell.app.ui.SuryaTimerScreen] with:
 * - [com.habitbell.app.data.SuryaDatabase] and [com.habitbell.app.data.dao.StepDao] for Room persistence.
 * - [com.habitbell.app.sync.SuryaSyncManager] for pushing timer configurations to Wear OS watches.
 * - Pre-populates the 12 classical Sun Salutation postures upon first launch.
 *
 * ## Concurrency & Thread Safety
 * - State is exposed via immutable [StateFlow] pipelines safely collected on Android Main/UI threads.
 * - Mutation commands are launched on [viewModelScope] (Main.immediate) and dispatched to [Dispatchers.IO] by Room.
 *
 * ## Lifecycle
 * Tied to the lifecycle of the host Activity or navigation entry via [AndroidViewModel].
 */
package com.habitbell.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.habitbell.app.audio.VoiceCueMode
import com.habitbell.app.data.SuryaDatabase
import com.habitbell.app.data.dao.PresetDao
import com.habitbell.app.data.dao.StepDao
import com.habitbell.app.data.default.DefaultProfiles
import com.habitbell.app.data.model.CompoundConfig
import com.habitbell.app.data.model.CompoundPose
import com.habitbell.app.data.model.PresetEntity
import com.habitbell.app.data.model.StepEntity
import com.habitbell.app.data.model.TimerType
import com.habitbell.app.data.repository.TimerRepository
import com.habitbell.app.engine.CentralSessionHandler
import com.habitbell.app.sync.SuryaSyncManager
import com.habitbell.app.ui.model.StepUiModel
import com.habitbell.app.util.JsonUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel managing Surya Namaskar pose customization, speed presets, and companion device sync.
 *
 * @param application Process application context for database and sync service resolution.
 */
class SuryaTimerViewModel(application: Application) : AndroidViewModel(application) {

    /** Room database handle. */
    private val database: SuryaDatabase = SuryaDatabase.getInstance(application)

    /** Data access object for pose step entities. */
    private val stepDao: StepDao = database.stepDao()

    /** Data access object for speed preset entities. */
    private val presetDao: PresetDao = database.presetDao()

    /** Companion sync manager for Wear OS watch devices. */
    private val syncManager: SuryaSyncManager = SuryaSyncManager(application, database)

    /** Central timer repository handle for synchronized profile persistence. */
    private val repository: TimerRepository = TimerRepository(application)

    /** Central session handler for live timer engine synchronization. */
    private val sessionHandler: CentralSessionHandler = CentralSessionHandler.getInstance(application)

    init {
        // Pre-populate classical 12 poses and speed presets if database is freshly created
        seedDefaultDataIfEmpty()
    }

    /**
     * Synchronizes the updated list of Room step entities directly to the [TimerRepository]
     * and live [CentralSessionHandler.engine] so modifications immediately reflect in the running timer.
     *
     * @param steps Current list of [StepEntity] from Room.
     * @param voiceModeOverride Optional explicit [VoiceCueMode] to enforce.
     */
    private fun syncStepsToEngineAndRepository(steps: List<StepEntity>, voiceModeOverride: VoiceCueMode? = null) {
        val baseConfig = DefaultProfiles.SURYA_NAMASKAR.compoundConfig
        val targetRounds = repository.getProfileById("surya-namaskar-compound")?.compoundConfig?.targetRounds ?: 5
        val finalVoiceMode = voiceModeOverride ?: steps.firstOrNull()?.voiceCueMode ?: VoiceCueMode.STEP_NAME

        val poses = steps.mapIndexed { idx, s ->
            val defaultPose = baseConfig?.poses?.getOrNull(idx)
            CompoundPose(
                index = idx + 1,
                name = s.name,
                sanskritName = defaultPose?.sanskritName ?: s.name,
                durationSeconds = s.durationSeconds.coerceAtLeast(1),
                breathCue = defaultPose?.breathCue ?: "",
                mantra = defaultPose?.mantra ?: "",
                voiceCueMode = finalVoiceMode
            )
        }

        repository.updateSuryaSettings(
            profileId = "surya-namaskar-compound",
            poses = poses,
            targetRounds = targetRounds,
            voiceCueMode = finalVoiceMode
        )

        val currentProfile = sessionHandler.sessionState.value.profile
        if (currentProfile.id.contains("surya", ignoreCase = true) || currentProfile.type == TimerType.COMPOUND) {
            val updatedConfig = CompoundConfig(
                poses = poses,
                targetRounds = targetRounds,
                voiceCueMode = finalVoiceMode
            )
            sessionHandler.engine.loadProfile(
                currentProfile.copy(
                    compoundConfig = updatedConfig,
                    totalDurationSeconds = poses.sumOf { it.durationSeconds } * targetRounds
                )
            )
        }
    }

    /**
     * Public immutable reactive stream emitting the current list of Surya Namaskar step models.
     */
    val steps: StateFlow<List<StepUiModel>> = stepDao.getAllSteps()
        .map { entities ->
            entities.map { entity ->
                StepUiModel(
                    id = entity.id,
                    name = entity.name,
                    durationSeconds = entity.durationSeconds,
                    isEnabled = entity.isEnabled,
                    voiceCueMode = entity.voiceCueMode,
                    mantraEnabled = entity.mantraEnabled,
                    repetition = entity.repetition,
                    puraka = entity.puraka,
                    kumbhaka = entity.kumbhaka,
                    rekha = entity.rekha
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Pre-populates the 12 classical Surya Namaskar poses and presets if the database is uninitialized.
     */
    private fun seedDefaultDataIfEmpty() {
        viewModelScope.launch(Dispatchers.IO) {
            val existingSteps = stepDao.getAllSteps().first()
            if (existingSteps.isEmpty()) {
                val defaultPoses = DefaultProfiles.SURYA_NAMASKAR.compoundConfig?.poses ?: emptyList()
                defaultPoses.forEachIndexed { idx, pose ->
                    stepDao.insert(
                        StepEntity(
                            id = 0L,
                            name = "${pose.name} (${pose.sanskritName})",
                            orderIdx = idx,
                            isEnabled = true,
                            voiceCueMode = VoiceCueMode.STEP_NAME,
                            audioCue = "surya_${idx + 1}",
                            mantraEnabled = true,
                            assetRef = if (idx == 0 || idx == 11) "avd_yoga_pranamasana" else "",
                            durationSeconds = pose.durationSeconds,
                            repetition = 1,
                            puraka = 0,
                            kumbhaka = 0,
                            rekha = 0
                        )
                    )
                }

                // Seed presets (slow, moderate, fast, custom)
                val presets = listOf(
                    PresetEntity("slow", JsonUtil.toJson(mapOf("default_duration" to 10))),
                    PresetEntity("moderate", JsonUtil.toJson(mapOf("default_duration" to 5))),
                    PresetEntity("fast", JsonUtil.toJson(mapOf("default_duration" to 3))),
                    PresetEntity("custom", JsonUtil.toJson(mapOf("default_duration" to 7)))
                )
                presets.forEach { presetDao.insert(it) }
            }
        }
    }

    /**
     * Public reactive stream exposing the current unified voice cue mode across all steps.
     */
    val currentVoiceCueMode: StateFlow<VoiceCueMode> = steps
        .map { stepList ->
            stepList.firstOrNull()?.voiceCueMode ?: VoiceCueMode.STEP_NAME
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VoiceCueMode.STEP_NAME)

    /**
     * Updates an existing pose step in the persistent Room database.
     *
     * @param updatedModel UI model containing updated duration, voice cue mode, or flags.
     */
    fun updateStep(updatedModel: StepUiModel) {
        viewModelScope.launch(Dispatchers.IO) {
            val entity = stepDao.getStepById(updatedModel.id) ?: return@launch
            val updatedEntity = entity.copy(
                durationSeconds = updatedModel.durationSeconds,
                isEnabled = updatedModel.isEnabled,
                voiceCueMode = updatedModel.voiceCueMode,
                mantraEnabled = updatedModel.mantraEnabled,
                repetition = updatedModel.repetition,
                puraka = updatedModel.puraka,
                kumbhaka = updatedModel.kumbhaka,
                rekha = updatedModel.rekha
            )
            stepDao.update(updatedEntity)
            val allSteps = stepDao.getAllSteps().first()
            syncStepsToEngineAndRepository(allSteps)
        }
    }

    /**
     * Applies a uniform speed preset across all enabled steps in the routine.
     *
     * @param durationSeconds Duration per pose in seconds (e.g. 10s for Slow, 5s for Moderate, 3s for Fast).
     */
    fun applyPresetDuration(durationSeconds: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val allSteps = stepDao.getAllSteps().first()
            val updated = allSteps.map { step ->
                val modified = step.copy(durationSeconds = durationSeconds)
                stepDao.update(modified)
                modified
            }
            syncStepsToEngineAndRepository(updated)
        }
    }

    /**
     * Applies a uniform voice guidance mode across all steps in the sequence.
     * Ensures voice cues are not edited per-step, but uniformly across all 12 postures.
     *
     * @param mode Selected [VoiceCueMode] to apply globally to all steps.
     */
    fun applyVoiceCueModeToAllSteps(mode: VoiceCueMode) {
        viewModelScope.launch(Dispatchers.IO) {
            val allSteps = stepDao.getAllSteps().first()
            val updated = allSteps.map { step ->
                val modified = step.copy(voiceCueMode = mode)
                stepDao.update(modified)
                modified
            }
            syncStepsToEngineAndRepository(updated, voiceModeOverride = mode)
        }
    }

    /**
     * Pushes current sequence configuration to paired Wear OS watch devices.
     */
    fun syncWithWatch() {
        syncManager.pushSyncToWatch()
    }

    /**
     * Auditions a sample posture voice cue matching the studio Lata-style SwaraNeural audio profile.
     *
     * @param stepIndex 1-based index (1..12) of the posture to audition.
     * @param mode Optional override of [VoiceCueMode]. Defaults to current active mode.
     */
    fun auditionVoiceCue(stepIndex: Int = 1, mode: VoiceCueMode? = null) {
        val targetMode = mode ?: currentVoiceCueMode.value
        val defaultPose = DefaultProfiles.SURYA_NAMASKAR.compoundConfig?.poses?.getOrNull(stepIndex - 1)
            ?: CompoundPose(1, "Pranamasana", "Prayer Pose", 5, "Inhale & Exhale gently", "ॐ मित्राय नमः")
        sessionHandler.suryaVoicePlayer.auditionPoseCue(defaultPose, targetMode)
    }
}
