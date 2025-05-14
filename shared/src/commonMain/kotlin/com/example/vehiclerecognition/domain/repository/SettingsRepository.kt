package com.example.vehiclerecognition.domain.repository

import com.example.vehiclerecognition.model.DetectionMode

/**
 * Interface for managing application settings persistence.
 * Specifically handles saving and retrieving the user's selected detection mode.
 * As per FR 1.10.
 */
interface SettingsRepository {

    /**
     * Saves the selected detection mode.
     *
     * @param mode The DetectionMode to save.
     */
    suspend fun saveDetectionMode(mode: DetectionMode)

    /**
     * Retrieves the saved detection mode.
     *
     * @return The saved DetectionMode, or a default mode (e.g., LP) if none is saved.
     */
    suspend fun getDetectionMode(): DetectionMode
} 