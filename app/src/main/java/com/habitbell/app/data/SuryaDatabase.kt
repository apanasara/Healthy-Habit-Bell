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
    }
}
