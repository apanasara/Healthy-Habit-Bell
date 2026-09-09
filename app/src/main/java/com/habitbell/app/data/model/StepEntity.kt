/**
 * # StepEntity
 *
 * Represents a single pose step in the Surya Namaskar 12-step sequence stored in Room.
 *
 * ## Architectural Role & Component Relationships
 * Belongs to the data persistence layer of the Surya Namaskar routine.
 * Interacts with [com.habitbell.app.data.dao.StepDao] and [com.habitbell.app.data.SuryaDatabase].
 *
 * ## Concurrency & Thread Safety
 * Immutable data class, thread-safe for reading and writing across coroutine contexts.
 */
package com.habitbell.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.habitbell.app.audio.VoiceCueMode

/**
 * Represents a single pose step in the Surya Namaskar sequence.
 *
 * @property id Auto-generated primary key.
 * @property name Sanskrit pose name (e.g., "Pranamasana", "Hastauttanasana").
 * @property orderIdx Position index of the step within the 12-step sequence (0..11).
 * @property isEnabled Whether this step is active in the routine.
 * @property voiceCueMode Voice cue playback mode for the step.
 * @property audioCue Identifier of the voice cue audio asset.
 * @property mantraEnabled Whether the solar mantra audio is enabled for this step.
 * @property assetRef Drawable resource name for the vector illustration.
 * @property durationSeconds Duration of the step in seconds.
 * @property repetition Number of repetitions for the step (default 1).
 * @property puraka Duration of inhalation in seconds (optional, 0 if unused).
 * @property kumbhaka Duration of breath retention in seconds (optional, 0 if unused).
 * @property rekha Duration of exhalation in seconds (optional, 0 if unused).
 */
@Entity(tableName = "steps")
data class StepEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val orderIdx: Int,
    val isEnabled: Boolean = true,
    val voiceCueMode: VoiceCueMode = VoiceCueMode.NONE,
    val audioCue: String = "",
    val mantraEnabled: Boolean = false,
    val assetRef: String = "",
    val durationSeconds: Int = 0,
    val repetition: Int = 1,
    val puraka: Int = 0,
    val kumbhaka: Int = 0,
    val rekha: Int = 0
)
