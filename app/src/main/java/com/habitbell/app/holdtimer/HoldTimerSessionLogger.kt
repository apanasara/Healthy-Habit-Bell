package com.habitbell.app.holdtimer

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * # HoldRoundRecord
 *
 * Immutable telemetry entry capturing performance metrics for a single completed hold round.
 *
 * @property roundIndex 1-based index of the round.
 * @property targetHoldSec Planned hold duration in seconds.
 * @property actualHoldSec Actual completed hold duration in seconds.
 * @property restSec Rest duration taken following the hold in seconds.
 * @property speedRating Effective speech/cadence rate multiplier during the round (e.g. 1.0f).
 * @property timestampMs Epoch timestamp in milliseconds when the round concluded.
 */
data class HoldRoundRecord(
    val roundIndex: Int,
    val targetHoldSec: Int,
    val actualHoldSec: Int,
    val restSec: Int,
    val speedRating: Float,
    val timestampMs: Long = System.currentTimeMillis()
)

/**
 * # HoldTimerSessionLogger
 *
 * Local telemetry logging and data export service for the Voice-Driven Yoga / Physiotherapy Hold Timer.
 *
 * ## Architectural Role & Component Relationships
 * - Records round completions dispatched by `HoldTimerEngine`.
 * - Formats session analytics into HIPAA/GDPR-compliant offline CSV and JSON payloads.
 * - Dispatches Android `Intent.ACTION_SEND` share sheets via `FileProvider` upon voice or UI triggers.
 *
 * ## Lifecycle & Concurrency
 * State container with synchronized thread-safe telemetry collection.
 */
class HoldTimerSessionLogger {

    private val records = mutableListOf<HoldRoundRecord>()

    /**
     * Records telemetry for a concluded round.
     *
     * @param roundIndex 1-based round index.
     * @param targetHoldSec Planned hold duration in seconds.
     * @param actualHoldSec Achieved hold duration in seconds.
     * @param restSec Rest interval in seconds.
     * @param speedRating Speech cadence multiplier.
     */
    @Synchronized
    fun logRound(
        roundIndex: Int,
        targetHoldSec: Int,
        actualHoldSec: Int,
        restSec: Int,
        speedRating: Float
    ) {
        records.add(
            HoldRoundRecord(
                roundIndex = roundIndex,
                targetHoldSec = targetHoldSec,
                actualHoldSec = actualHoldSec,
                restSec = restSec,
                speedRating = speedRating
            )
        )
    }

    /**
     * Clears all recorded telemetry in preparation for a new session.
     */
    @Synchronized
    fun reset() {
        records.clear()
    }

    /**
     * Retrieves an immutable snapshot of all logged round entries.
     *
     * @return List of [HoldRoundRecord] elements.
     */
    @Synchronized
    fun getRecords(): List<HoldRoundRecord> = records.toList()

    /**
     * Serializes session telemetry into standard Comma-Separated Values (CSV) format.
     *
     * @return Formatted CSV text string.
     */
    @Synchronized
    fun generateCsv(): String {
        val sb = StringBuilder()
        sb.append("Round,TargetHoldSec,ActualHoldSec,RestSec,SpeedRating,TimestampISO\n")
        val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        for (r in records) {
            val isoDate = dateFormat.format(Date(r.timestampMs))
            sb.append("${r.roundIndex},${r.targetHoldSec},${r.actualHoldSec},${r.restSec},${String.format(Locale.US, "%.2f", r.speedRating)},$isoDate\n")
        }
        return sb.toString()
    }

    /**
     * Serializes session telemetry into a standardized JSON payload.
     *
     * @return Pretty-printed JSON string conforming to session log schema.
     */
    @Synchronized
    fun generateJson(): String {
        val root = JSONObject()
        root.put("totalRoundsCompleted", records.size)
        val array = JSONArray()
        for (r in records) {
            val obj = JSONObject()
            obj.put("roundIndex", r.roundIndex)
            obj.put("targetHoldSec", r.targetHoldSec)
            obj.put("actualHoldSec", r.actualHoldSec)
            obj.put("restSec", r.restSec)
            obj.put("speedRating", r.speedRating.toDouble())
            obj.put("timestampMs", r.timestampMs)
            array.put(obj)
        }
        root.put("rounds", array)
        return root.toString(2)
    }

    /**
     * Writes exported session data to an application-private cache file.
     *
     * @param context Android application context for cache directory access.
     * @param format Export format ("csv" or "json").
     * @return Saved [File] handle ready for sharing.
     */
    @Throws(Exception::class)
    fun exportToFile(context: Context, format: String): File {
        val extension = if (format.equals("json", ignoreCase = true)) "json" else "csv"
        val fileName = "hold_timer_session_${System.currentTimeMillis()}.$extension"
        val exportDir = File(context.cacheDir, "session_exports").apply { mkdirs() }
        val targetFile = File(exportDir, fileName)

        val content = if (extension == "json") generateJson() else generateCsv()
        FileWriter(targetFile).use { writer ->
            writer.write(content)
        }
        return targetFile
    }

    /**
     * Creates an Android `Intent.ACTION_SEND` intent for sharing the exported session file.
     *
     * @param context Application context for resolving content URI.
     * @param file Target exported file.
     * @return Ready-to-launch [Intent] with read URI permissions.
     */
    fun createShareIntent(context: Context, file: File): Intent {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val mimeType = if (file.name.endsWith(".json")) "application/json" else "text/csv"
        return Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
