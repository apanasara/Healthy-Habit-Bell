/**
 * # SuryaSyncManager
 *
 * Handles synchronization of Surya Namaskar timer data between the phone and Wear OS companion watch.
 *
 * ## Architectural Role & Component Relationships
 * Bridges the Room persistence layer ([SuryaDatabase]) with Google Play Services [Wearable] DataClient.
 * Serializes entities via [JsonUtil] and pushes them over the Wearable Data Layer API.
 *
 * ## Concurrency & Thread Safety
 * All network and serialization operations run on Dispatchers.IO to maintain UI fluidity.
 */
package com.habitbell.app.sync

import android.content.Context
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.habitbell.app.data.SuryaDatabase
import com.habitbell.app.util.JsonUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.nio.charset.StandardCharsets

private const val TAG = "SuryaSyncManager"
private const val SYNC_PATH = "/surya_sync"

/**
 * Manages push synchronization from phone to companion Wear OS devices.
 *
 * @param context Application context used for DataClient.
 * @param database Instance of [SuryaDatabase].
 */
class SuryaSyncManager(
    private val context: Context,
    private val database: SuryaDatabase
) {
    private val scope = CoroutineScope(Dispatchers.IO)

    /**
     * Pushes the current timer configuration to the watch via Wearable DataClient.
     */
    fun pushSyncToWatch() {
        scope.launch {
            try {
                val steps = database.stepDao().getAllSteps().first()
                val preset = database.presetDao().getPresetByName("default")
                val settings = database.settingDao().getAllSettings().first()
                val payload = mapOf(
                    "steps" to steps,
                    "preset" to preset,
                    "settings" to settings
                )
                val json = JsonUtil.toJson(payload)
                val putDataReq = PutDataMapRequest.create(SYNC_PATH).apply {
                    dataMap.putByteArray("payload", json.toByteArray(StandardCharsets.UTF_8))
                }.asPutDataRequest().setUrgent()

                Tasks.await(Wearable.getDataClient(context).putDataItem(putDataReq))
                Log.d(TAG, "Sync payload pushed to watch (size=${json.length})")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to push Surya sync", e)
            }
        }
    }
}
