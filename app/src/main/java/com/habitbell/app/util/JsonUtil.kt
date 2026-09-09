/**
 * # JsonUtil
 *
 * Utility for JSON serialization and deserialization via Google Gson.
 *
 * ## Architectural Role & Component Relationships
 * Shared utility used by [com.habitbell.app.sync.SuryaSyncManager] to serialize
 * database entities for cross-device synchronization (e.g. Wear OS data layer).
 *
 * ## Concurrency & Thread Safety
 * [Gson] is thread-safe; this singleton object can be invoked across any coroutine dispatcher.
 */
package com.habitbell.app.util

import com.google.gson.Gson

object JsonUtil {
    @PublishedApi
    internal val gson: Gson = Gson()

    /**
     * Serializes any object to its JSON string representation.
     *
     * @param data The object instance to serialize.
     * @return Formatted JSON string.
     */
    fun toJson(data: Any): String = gson.toJson(data)

    /**
     * Deserializes a JSON string into an object of the specified reified type [T].
     *
     * @param json Valid JSON string.
     * @return Deserialized instance of type [T].
     */
    inline fun <reified T> fromJson(json: String): T = gson.fromJson(json, T::class.java)
}
