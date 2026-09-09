/**
 * # StepDao
 *
 * Data Access Object for accessing and mutating [StepEntity] records in Room.
 *
 * ## Architectural Role & Component Relationships
 * Data persistence layer interface for the Surya Namaskar 12-step sequence.
 * Used by [com.habitbell.app.data.SuryaDatabase] and [com.habitbell.app.viewmodel.SuryaTimerViewModel].
 *
 * ## Concurrency & Thread Safety
 * Room executes query methods on background dispatchers, returning reactive Coroutines Flows.
 */
package com.habitbell.app.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.habitbell.app.data.model.StepEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO interface for accessing [StepEntity] records.
 */
@Dao
interface StepDao {
    /**
     * Inserts a step into the database, replacing on conflict.
     * @param step The step entity to insert.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(step: StepEntity)

    /**
     * Updates an existing step.
     * @param step The step entity with updated fields.
     */
    @Update
    suspend fun update(step: StepEntity)

    /**
     * Deletes a step.
     * @param step The step entity to delete.
     */
    @Delete
    suspend fun delete(step: StepEntity)

    /**
     * Retrieves all steps ordered by their sequence index.
     * @return Flow emitting the ordered list of steps.
     */
    @Query("SELECT * FROM steps ORDER BY orderIdx ASC")
    fun getAllSteps(): Flow<List<StepEntity>>

    /**
     * Retrieves a single step by primary key.
     * @param id Step ID.
     * @return The [StepEntity], or null if not found.
     */
    @Query("SELECT * FROM steps WHERE id = :id LIMIT 1")
    suspend fun getStepById(id: Long): StepEntity?
}
