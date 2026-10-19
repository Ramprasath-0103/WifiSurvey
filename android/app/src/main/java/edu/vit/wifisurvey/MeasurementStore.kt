package edu.vit.wifisurvey

import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * Crash-safe storage. Every finished measurement is appended to a JSON Lines
 * file in app storage the moment it completes, so losing the app mid-survey
 * costs at most the reading in progress.
 *
 * Plan Revision 2 used Room for this. A line-appended file gives the same
 * guarantee with none of the annotation processor build weight, and the export
 * format the importer expects is JSON either way.
 */
class MeasurementStore(private val ctx: Context) {

    private val file: File get() = File(ctx.filesDir, "measurements.jsonl")

    fun append(m: Measurement) {
        file.appendText(m.toJson().toString() + "\n")
    }

    fun count(): Int = if (file.exists()) file.readLines().count { it.isNotBlank() } else 0

    fun lines(): List<String> = if (file.exists()) file.readLines().filter { it.isNotBlank() } else emptyList()

    /** Wraps the loose lines into the object shape scripts/import_session.py expects. */
    fun exportJson(): String {
        val arr = org.json.JSONArray()
        lines().forEach { runCatching { arr.put(JSONObject(it)) } }
        return JSONObject()
            .put("exported_at", iso(Date()))
            .put("measurement_count", arr.length())
            .put("measurements", arr)
            .toString(2)
    }

    /**
     * Writes the export into the shared Downloads folder so it shows up in the
     * Files app and over USB without any storage permission.
     * Returns the display name written, or null on failure.
     */
    fun exportToDownloads(): String? {
        val name = "wifi_survey_" +
            SimpleDateFormat("yyyyMMdd_HHmm", Locale.UK).format(Date()) + ".json"
        return try {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, "application/json")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val resolver = ctx.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null
            resolver.openOutputStream(uri)?.use { it.write(exportJson().toByteArray()) }
            values.clear(); values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            name
        } catch (e: Exception) {
            null
        }
    }

    fun clear() { file.delete() }

    companion object {
        fun iso(d: Date): String =
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.UK).format(d)
    }
}
