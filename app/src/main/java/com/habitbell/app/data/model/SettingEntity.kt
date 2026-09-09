/**
 * # SettingEntity
 *
 * Stores generic key-value configuration settings used by the Surya Namaskar subsystem in Room.
 *
 * ## Architectural Role & Component Relationships
 * Belongs to the data persistence layer of the Surya Namaskar routine.
 * Interacts with [com.habitbell.app.data.dao.SettingDao] and [com.habitbell.app.data.SuryaDatabase].
 *
 * ## Concurrency & Thread Safety
 * Immutable data class, thread-safe for reading and writing across coroutine contexts.
 */
package com.habitbell.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity representing a key-value setting for Surya Namaskar timer preferences.
 *
 * @property key Unique setting key identifier. Primary key.
 * @property value Stored setting value represented as a string.
 */
@Entity(tableName = "settings")
data class SettingEntity(
    @PrimaryKey val key: String,
    val value: String
)
