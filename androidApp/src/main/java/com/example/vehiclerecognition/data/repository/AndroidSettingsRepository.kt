package com.example.vehiclerecognition.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.example.vehiclerecognition.domain.repository.SettingsRepository
import com.example.vehiclerecognition.model.DetectionMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// Note: Actual SharedPreferences logic would be implemented here.
// This requires Android Context, typically injected.

private const val PREFS_NAME = "vehicle_recognition_prefs"
private const val KEY_DETECTION_MODE = "detection_mode"
private const val KEY_INCLUDE_SECONDARY_COLOR = "include_secondary_color"

class AndroidSettingsRepository(
    private val context: Context // Inject ApplicationContext here via DI
) : SettingsRepository {

    private val sharedPreferences: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    // StateFlow for detection mode changes
    private val _detectionMode = MutableStateFlow(DetectionMode.LP)
    override val detectionMode: StateFlow<DetectionMode> = _detectionMode.asStateFlow()

    // StateFlow for secondary color search setting changes
    private val _includeSecondaryColor = MutableStateFlow(true)
    override val includeSecondaryColor: StateFlow<Boolean> = _includeSecondaryColor.asStateFlow()

    init {
        // Load initial detection mode from SharedPreferences
        val savedModeName = sharedPreferences.getString(KEY_DETECTION_MODE, DetectionMode.LP.name)
        val initialMode = try {
            DetectionMode.valueOf(savedModeName ?: DetectionMode.LP.name)
        } catch (e: IllegalArgumentException) {
            println("AndroidSettingsRepository: Invalid mode name '$savedModeName' in SharedPreferences, defaulting to LP.")
            DetectionMode.LP // Default to LP if the saved value is somehow invalid
        }
        _detectionMode.value = initialMode
        println("AndroidSettingsRepository: Initialized with detection mode: $initialMode")

        // Load initial secondary color setting from SharedPreferences
        val initialIncludeSecondary = sharedPreferences.getBoolean(KEY_INCLUDE_SECONDARY_COLOR, true)
        _includeSecondaryColor.value = initialIncludeSecondary
        println("AndroidSettingsRepository: Initialized with include secondary color: $initialIncludeSecondary")
    }

    override suspend fun saveDetectionMode(mode: DetectionMode) {
        withContext(Dispatchers.IO) { // Perform disk I/O on a background thread
            sharedPreferences.edit()
                .putString(KEY_DETECTION_MODE, mode.name)
                .apply()
        }
        // Update the flow to notify observers
        _detectionMode.value = mode
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

    override suspend fun saveIncludeSecondaryColor(includeSecondary: Boolean) {
        withContext(Dispatchers.IO) { // Perform disk I/O on a background thread
            sharedPreferences.edit()
                .putBoolean(KEY_INCLUDE_SECONDARY_COLOR, includeSecondary)
                .apply()
        }
        // Update the flow to notify observers
        _includeSecondaryColor.value = includeSecondary
        println("AndroidSettingsRepository: Saved include secondary color to SharedPreferences: $includeSecondary")
    }

    override suspend fun getIncludeSecondaryColor(): Boolean {
        return withContext(Dispatchers.IO) { // Perform disk I/O on a background thread
            val includeSecondary = sharedPreferences.getBoolean(KEY_INCLUDE_SECONDARY_COLOR, true)
            println("AndroidSettingsRepository: Retrieved include secondary color from SharedPreferences: $includeSecondary")
            includeSecondary
        }
    }
} 