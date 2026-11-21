package com.example.bike_.data.backup

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.room.withTransaction
import com.example.bike_.data.db.BikeDatabase
import com.example.bike_.data.settings.CyclingSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Creates a single JSON file containing all workouts, track points, laps, saved routes
 * and app settings, and can restore it after a reinstall (data survives package replacement).
 */
class BackupManager(
    private val context: Context,
    private val db: BikeDatabase,
    private val settings: CyclingSettings
) {
    private val workoutDao = db.workoutDao()
    private val pointDao = db.trackPointDao()
    private val lapDao = db.lapDao()
    private val routeDao = db.savedRouteDao()
    private val prefs = context.getSharedPreferences(CyclingSettings.PREFS_NAME, Context.MODE_PRIVATE)

    sealed interface BackupResult {
        data class Success(val path: String, val workoutCount: Int) : BackupResult
        data class Error(val message: String) : BackupResult
    }

    sealed interface RestoreResult {
        data class Success(val workoutCount: Int, val pointCount: Int) : RestoreResult
        data class Error(val message: String) : RestoreResult
    }

    suspend fun createBackup(): BackupResult = withContext(Dispatchers.IO) {
        try {
            val data = BackupCodec.BackupData(
                workouts = workoutDao.getAll(),
                points = pointDao.getAll(),
                laps = lapDao.getAll(),
                routes = routeDao.getAll(),
                settings = serializePrefs()
            )
            val json = BackupCodec.encode(data)
            val fileName = "bike_backup_" + SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date()) + ".json"
            val saved = saveToDownloads(fileName, json.toByteArray(Charsets.UTF_8))
                ?: return@withContext BackupResult.Error(context.getString(com.example.bike_.R.string.backup_error_save))
            BackupResult.Success(saved, data.workouts.size)
        } catch (e: Exception) {
            BackupResult.Error(e.message ?: context.getString(com.example.bike_.R.string.backup_error_save))
        }
    }

    suspend fun restore(json: String): RestoreResult = withContext(Dispatchers.IO) {
        try {
            val data = BackupCodec.decode(json)
            db.withTransaction {
                // Order matters: children first (and foreign keys cascade), then parents.
                pointDao.deleteAll()
                lapDao.deleteAll()
                workoutDao.deleteAll()
                routeDao.deleteAll()

                workoutDao.insertAllReplace(data.workouts)
                pointDao.insertAllReplace(data.points)
                lapDao.insertAllReplace(data.laps)
                routeDao.insertAllReplace(data.routes)
            }
            restorePrefs(data.settings)
            settings.reload()
            RestoreResult.Success(data.workouts.size, data.points.size)
        } catch (e: BackupCodec.InvalidBackupException) {
            RestoreResult.Error(e.message ?: context.getString(com.example.bike_.R.string.backup_restore_invalid))
        } catch (e: Exception) {
            RestoreResult.Error(e.message ?: context.getString(com.example.bike_.R.string.backup_restore_error))
        }
    }

    // ---- Settings (SharedPreferences) with explicit type tags ----

    private fun serializePrefs(): JSONObject {
        val out = JSONObject()
        for ((key, value) in prefs.all) {
            val wrapper = JSONObject()
            when (value) {
                is Int -> wrapper.put("t", "i").put("v", value)
                is Long -> wrapper.put("t", "l").put("v", value)
                is Float -> wrapper.put("t", "f").put("v", value.toDouble())
                is Boolean -> wrapper.put("t", "b").put("v", value)
                is String -> wrapper.put("t", "s").put("v", value)
                is Set<*> -> {
                    val arr = JSONArray()
                    value.forEach { arr.put(it.toString()) }
                    wrapper.put("t", "ss").put("v", arr)
                }
                else -> continue
            }
            out.put(key, wrapper)
        }
        return out
    }

    private fun restorePrefs(json: JSONObject) {
        val editor = prefs.edit()
        prefs.all.keys.forEach { editor.remove(it) }
        val keys = json.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val w = json.optJSONObject(key) ?: continue
            when (w.optString("t")) {
                "i" -> editor.putInt(key, w.getInt("v"))
                "l" -> editor.putLong(key, w.getLong("v"))
                "f" -> editor.putFloat(key, w.getDouble("v").toFloat())
                "b" -> editor.putBoolean(key, w.getBoolean("v"))
                "s" -> editor.putString(key, w.getString("v"))
                "ss" -> {
                    val arr = w.optJSONArray("v") ?: JSONArray()
                    val set = LinkedHashSet<String>()
                    for (i in 0 until arr.length()) set.add(arr.optString(i))
                    editor.putStringSet(key, set)
                }
            }
        }
        editor.commit() // synchronous, so settings.reload() below sees the restored values
    }

    // ---- File storage (mirrors WorkoutRepository.export behaviour) ----

    private fun saveToDownloads(fileName: String, bytes: ByteArray): String? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, "application/json")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Bike_")
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null
                resolver.openOutputStream(uri)?.use { it.write(bytes) } ?: run {
                    resolver.delete(uri, null, null)
                    return null
                }
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                "Downloads/Bike_/$fileName"
            } else {
                val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Bike_")
                if (!dir.exists()) dir.mkdirs()
                val outFile = File(dir, fileName)
                FileOutputStream(outFile).use { it.write(bytes) }
                outFile.absolutePath
            }
        } catch (_: Exception) {
            null
        }
    }
}
