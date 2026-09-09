/**
 * # SuryaDatabase
 *
 * Room database instance providing persistence for Surya Namaskar steps, presets, and settings.
 *
 * ## Architectural Role & Component Relationships
 * Core database component of the Surya Namaskar subsystem.
 * Exposes DAOs: [StepDao], [PresetDao], [SettingDao].
 * Singleton accessed via [getInstance] to guarantee a single connection pool per process.
 *
 * ## Concurrency & Thread Safety
 * Follows the double-checked locking singleton pattern for thread-safe access.
 * Room manages internal connection pooling and read/write thread dispatching.
 *
 * ## Lifecycle
 * Created lazily on first [getInstance] call, persists for the lifetime of the process.
 * Database name: `surya_database`. Uses [fallbackToDestructiveMigration] for schema evolution.
 */
package com.habitbell.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.habitbell.app.audio.VoiceCueMode
import kotlinx.coroutines.flow.first
import com.habitbell.app.data.dao.PresetDao
import com.habitbell.app.data.dao.SettingDao
import com.habitbell.app.data.dao.StepDao
import com.habitbell.app.data.model.PresetEntity
import com.habitbell.app.data.model.SettingEntity
import com.habitbell.app.data.model.StepEntity

/**
 * Room type converters for non-primitive entity fields.
 *
 * Converts [VoiceCueMode] enum to/from its ordinal integer for SQLite storage.
 */
class SuryaTypeConverters {
    /** Converts [VoiceCueMode] to its ordinal integer for SQLite storage. */
    @TypeConverter
    fun fromVoiceCueMode(mode: VoiceCueMode): Int = mode.mode

    /** Converts an ordinal integer back to [VoiceCueMode]. */
    @TypeConverter
    fun toVoiceCueMode(value: Int): VoiceCueMode =
        VoiceCueMode.entries.find { it.mode == value } ?: VoiceCueMode.NONE
}

/**
 * Room database class registering all Surya Namaskar entities and their DAO accessors.
 *
 * @see StepEntity
 * @see PresetEntity
 * @see SettingEntity
 */
@Database(
    entities = [StepEntity::class, PresetEntity::class, SettingEntity::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(SuryaTypeConverters::class)
abstract class SuryaDatabase : RoomDatabase() {

    /** DAO accessor for Surya Namaskar step (pose) entities. */
    abstract fun stepDao(): StepDao

    /** DAO accessor for speed preset entities (slow, moderate, fast). */
    abstract fun presetDao(): PresetDao

    /** DAO accessor for user setting key-value entities. */
    abstract fun settingDao(): SettingDao

    companion object {
        /** Volatile reference ensuring visibility across threads. */
        @Volatile
        private var INSTANCE: SuryaDatabase? = null

        /**
         * Returns the thread-safe singleton instance of [SuryaDatabase].
         *
         * Uses double-checked locking to avoid synchronized overhead on subsequent calls.
         *
         * @param context Application context used for Room database building.
         * @return The active [SuryaDatabase] instance.
         */
        fun getInstance(context: Context): SuryaDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    SuryaDatabase::class.java,
                    "surya_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }

        /**
         * Pre-populates the 12 classical Sun Salutation postures and speed presets if database table is empty.
         *
         * Architectural Role: Initializes the Surya Namaskar sequence database upon initial access.
         * Concurrency: Must be invoked within a coroutine context, querying and mutating Room on an IO dispatcher.
         *
         * @param context Application context used to obtain database instance.
         */
        suspend fun seedIfEmpty(context: Context) {
            val db = getInstance(context)
            val stepDao = db.stepDao()
            val existing = stepDao.getAllSteps().first()
            if (existing.isEmpty()) {
                val defaultPoses = com.habitbell.app.data.default.DefaultProfiles.SURYA_NAMASKAR.compoundConfig?.poses ?: emptyList()
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

                val presetDao = db.presetDao()
                val presets = listOf(
                    PresetEntity("slow", com.habitbell.app.util.JsonUtil.toJson(mapOf("default_duration" to 10))),
                    PresetEntity("moderate", com.habitbell.app.util.JsonUtil.toJson(mapOf("default_duration" to 5))),
                    PresetEntity("fast", com.habitbell.app.util.JsonUtil.toJson(mapOf("default_duration" to 3))),
                    PresetEntity("custom", com.habitbell.app.util.JsonUtil.toJson(mapOf("default_duration" to 7)))
                )
                presets.forEach { presetDao.insert(it) }
            }
        }
    }
}
