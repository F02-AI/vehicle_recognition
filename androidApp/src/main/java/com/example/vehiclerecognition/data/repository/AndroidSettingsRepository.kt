package com.example.vehiclerecognition.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.example.vehiclerecognition.domain.repository.SettingsRepository
import com.example.vehiclerecognition.model.DetectionMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Note: Actual SharedPreferences logic would be implemented here.
// This requires Android Context, typically injected.

private const val PREFS_NAME = "vehicle_recognition_prefs"
private const val KEY_DETECTION_MODE = "detection_mode"

class AndroidSettingsRepository(
    private val context: Context // Inject ApplicationContext here via DI
) : SettingsRepository {

    private val sharedPreferences: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    // In a real implementation, this would interact with SharedPreferences
    // For now, we'll use a simple in-memory placeholder.
    private var currentMode: DetectionMode = DetectionMode.LP // Default

    override suspend fun saveDetectionMode(mode: DetectionMode) {
        withContext(Dispatchers.IO) { // Perform disk I/O on a background thread
            sharedPreferences.edit()
                .putString(KEY_DETECTION_MODE, mode.name)
                .apply()
        }
        println("AndroidSettingsRepository: Saved detection mode to SharedPreferences: $mode")
    }

    override suspend fun getDetectionMode(): DetectionMode {
        return withContext(Dispatchers.IO) { // Perform disk I/O on a background thread
            val savedModeName = sharedPreferences.getString(KEY_DETECTION_MODE, DetectionMode.LP.name)
            val mode = try {
                DetectionMode.valueOf(savedModeName ?: DetectionMode.LP.name)
            } catch (e: IllegalArgumentException) {
                println("AndroidSettingsRepository: Invalid mode name '$savedModeName' in SharedPreferences, defaulting to LP.")
                DetectionMode.LP // Default to LP if the saved value is somehow invalid
            }
            println("AndroidSettingsRepository: Retrieved detection mode from SharedPreferences: $mode")
            mode
        }
    }
} 