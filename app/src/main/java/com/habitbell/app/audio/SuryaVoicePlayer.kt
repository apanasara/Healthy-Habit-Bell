/**
 * # SuryaVoicePlayer
 *
 * Handles audio playback of voice cues for the Surya Namaskar timer.
 *
 * ## Architectural Role & Component Relationships
 * Audio subsystem component interfacing with Android [MediaPlayer] and the process-level
 * [BackgroundMusicManager]. Ducking is applied before speech starts, and volume is restored
 * upon cue completion.
 *
 * ## Acoustic Profile
 * - Voice Profile: hi-IN-SwaraNeural (+52Hz pitch shift, Lata Mangeshkar meditative tone)
 * - Lead Delay: 120ms anti-startle grace period after background music ducking begins
 * - Audio Ducking: 0.20f target volume factor over 350ms, restored over 500ms
 *
 * ## Concurrency & Thread Safety
 * Playback management is dispatched on the Main coroutine scope; background music ducking
 * operations are asynchronously animated.
 */
package com.habitbell.app.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import androidx.annotation.RawRes
import com.habitbell.app.engine.BackgroundMusicManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Enum representing the supported voice-cue modes for Surya Namaskar sequences.
 *
 * @property mode Numeric identifier persisted in SQLite Room database.
 */
enum class VoiceCueMode(val mode: Int) {
    NONE(0),
    PRANIC(1),
    STEP_NAME(2),
    SLOKA(3);

    /**
     * User-facing localized display name for UI chips and badges.
     */
    val displayName: String
        get() = when (this) {
            NONE -> "Silent / Bell"
            PRANIC -> "Breath Flow"
            STEP_NAME -> "Asana Name"
            SLOKA -> "Solar Mantra"
        }
}

/**
 * Voice cue player coordinating audio ducking and cue playback.
 *
 * @param context Android context used to access raw audio resources.
 * @param backgroundMusicManager Manager controlling ambient background music ducking.
 */
class SuryaVoicePlayer(
    private val context: Context,
    private val backgroundMusicManager: BackgroundMusicManager
) {
    private val scope = CoroutineScope(Dispatchers.Main)
    private var currentJob: Job? = null

    /**
     * Plays the voice cue for a given step with ducking and lead delay.
     *
     * @param resId Raw resource ID of the voice cue MP3.
     * @param mode Selected [VoiceCueMode].
     */
    fun playCue(@RawRes resId: Int, mode: VoiceCueMode) {
        currentJob?.cancel()
        if (mode == VoiceCueMode.NONE) return

        currentJob = scope.launch {
            // Duck ambient music smoothly
            backgroundMusicManager.duckVolume(duckedRatio = 0.20f, durationMs = 350L)
            // Anti-startle lead delay
            delay(120L)

            val player = MediaPlayer.create(context, resId)?.apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .build()
                )
                setVolume(0.52f, 0.52f)
            } ?: return@launch

            player.start()
            while (player.isPlaying) {
                delay(50L)
            }
            player.release()

            // Restore ambient music volume smoothly
            backgroundMusicManager.restoreVolume(durationMs = 500L)
        }
    }

    /**
     * Stops any currently playing cue and cancels pending jobs.
     */
    fun stop() {
        currentJob?.cancel()
        currentJob = null
        backgroundMusicManager.restoreVolume(durationMs = 300L)
    }
}
