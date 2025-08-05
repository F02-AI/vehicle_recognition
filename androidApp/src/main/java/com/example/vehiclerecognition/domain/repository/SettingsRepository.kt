package com.example.vehiclerecognition.domain.repository

import com.example.vehiclerecognition.data.models.DetectionMode
import kotlinx.coroutines.flow.StateFlow

/**
 * Interface for managing application settings persistence.
 * Specifically handles saving and retrieving the user's selected detection mode.
 * Android-specific version using data models DetectionMode.
 */
interface SettingsRepository {

    /**
     * Flow that emits detection mode changes
     */
    val detectionMode: StateFlow<DetectionMode>

    /**
     * Flow that emits secondary color search setting changes
     */
    val includeSecondaryColor: StateFlow<Boolean>

    /**
     * Saves the selected detection mode.
     *
     * @param mode The DetectionMode to save.
     */
    suspend fun saveDetectionMode(mode: DetectionMode)

    /**
     * Retrieves the saved detection mode.
     *
     * @return The saved DetectionMode, or a default mode (e.g., LP_ONLY) if none is saved.
     */
    suspend fun getDetectionMode(): DetectionMode

    /**
     * Saves the secondary color search setting.
     *
     * @param includeSecondary Whether to include secondary colors in color-based detection modes.
     */
    suspend fun saveIncludeSecondaryColor(includeSecondary: Boolean)

    /**
     * Retrieves the secondary color search setting.
     *
     * @return Whether secondary colors should be included in searches, defaults to false.
     */
    suspend fun getIncludeSecondaryColor(): Boolean
}