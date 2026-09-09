/**
 * # SettingDao
 *
 * Data Access Object for accessing and mutating [SettingEntity] records in Room.
 *
 * ## Architectural Role & Component Relationships
 * Data persistence layer interface for Surya Namaskar timer settings.
 * Used by [com.habitbell.app.data.SuryaDatabase] and repository/sync managers.
 *
 * ## Concurrency & Thread Safety
 * Room operations run on background dispatchers (Dispatchers.IO).
 */
package com.habitbell.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.habitbell.app.data.model.SettingEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO interface for Surya Namaskar key-value preferences.
 */
@Dao
interface SettingDao {
    /**
     * Inserts a key-value setting, replacing on conflict.
     * @param setting The setting entity to insert.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(setting: SettingEntity)

    /**
     * Retrieves a setting by key.
     * @param key The setting identifier.
     * @return The [SettingEntity], or null if not found.
     */
    @Query("SELECT * FROM settings WHERE `key` = :key LIMIT 1")
    suspend fun getSetting(key: String): SettingEntity?

    /**
     * Observes all settings reactive stream.
     * @return Flow emitting list of [SettingEntity].
     */
    @Query("SELECT * FROM settings")
    fun getAllSettings(): Flow<List<SettingEntity>>
}
