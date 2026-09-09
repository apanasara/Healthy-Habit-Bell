/**
 * # PresetEntity
 *
 * Stores preset mappings of step identifiers to default durations in the local Room database.
 *
 * ## Architectural Role & Component Relationships
 * Belongs to the data persistence layer of the Surya Namaskar subsystem.
 * Interacts with [com.habitbell.app.data.dao.PresetDao] and [com.habitbell.app.data.SuryaDatabase].
 *
 * ## Concurrency & Thread Safety
 * Plain data model, immutable once instantiated. Read/written via Room on IO dispatchers.
 */
package com.habitbell.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity representing a Surya Namaskar duration preset configuration.
 *
 * @property name Unique name of the preset (e.g., "slow", "moderate", "fast"). Primary key.
 * @property jsonMap Serialized JSON string mapping step indices or IDs to duration in seconds.
 */
@Entity(tableName = "presets")
data class PresetEntity(
    @PrimaryKey val name: String,
    val jsonMap: String
)
