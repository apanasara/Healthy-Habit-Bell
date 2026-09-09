/**
 * # PresetDao
 *
 * Data Access Object for accessing and mutating [PresetEntity] records in Room.
 *
 * ## Architectural Role & Component Relationships
 * Data persistence layer interface for Surya Namaskar timer presets.
 * Used by [com.habitbell.app.data.SuryaDatabase] and repository/sync managers.
 *
 * ## Concurrency & Thread Safety
 * Room operations run on background dispatchers (Dispatchers.IO).
 */
package com.habitbell.app.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.habitbell.app.data.model.PresetEntity

/**
 * DAO interface for Surya Namaskar presets.
 */
@Dao
interface PresetDao {
    /**
     * Inserts a preset into the database, replacing on conflict.
     * @param preset The preset entity to insert.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(preset: PresetEntity)

    /**
     * Updates an existing preset.
     * @param preset The preset entity with updated values.
     */
    @Update
    suspend fun update(preset: PresetEntity)

    /**
     * Deletes a preset.
     * @param preset The preset entity to delete.
     */
    @Delete
    suspend fun delete(preset: PresetEntity)

    /**
     * Retrieves a preset by its name.
     * @param name The preset identifier name.
     * @return The [PresetEntity], or null if not found.
     */
    @Query("SELECT * FROM presets WHERE name = :name LIMIT 1")
    suspend fun getPresetByName(name: String): PresetEntity?
}
