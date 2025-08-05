package com.example.vehiclerecognition.data.repositories

import android.content.Context
import android.graphics.Bitmap
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.vehiclerecognition.data.models.LicensePlateSettings
import com.example.vehiclerecognition.data.models.OcrModelType
import com.example.vehiclerecognition.data.models.PlateDetection
import com.example.vehiclerecognition.data.models.Country
import com.example.vehiclerecognition.ml.processors.LicensePlateProcessor
import com.example.vehiclerecognition.ml.processors.ProcessorResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

val Context.licensePlateDataStore: DataStore<Preferences> by preferencesDataStore(name = "license_plate_settings")

/**
 * Repository for managing license plate recognition settings and coordination
 */
@Singleton
class LicensePlateRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val licensePlateProcessor: LicensePlateProcessor
) {
    
    private val dataStore = context.licensePlateDataStore
    
    companion object {
        private val OCR_MODEL_KEY = stringPreferencesKey("ocr_model")
        private val PROCESSING_INTERVAL_KEY = intPreferencesKey("processing_interval")
        private val MIN_CONFIDENCE_KEY = floatPreferencesKey("min_confidence")
        private val GPU_ACCELERATION_KEY = booleanPreferencesKey("gpu_acceleration")
        private val ENABLE_OCR_KEY = booleanPreferencesKey("enable_ocr")
        private val NUMERIC_ONLY_MODE_KEY = booleanPreferencesKey("numeric_only_mode")
        private val ISRAELI_FORMAT_VALIDATION_KEY = booleanPreferencesKey("israeli_format_validation")
        private val ENABLE_DEBUG_VIDEO_KEY = booleanPreferencesKey("enable_debug_video")
        private val CAMERA_ZOOM_RATIO_KEY = floatPreferencesKey("camera_zoom_ratio")
        private val SELECTED_COUNTRY_KEY = stringPreferencesKey("selected_country")
        
        // Vehicle Color Detection Settings
        private val ENABLE_GRAY_FILTERING_KEY = booleanPreferencesKey("enable_gray_filtering")
        private val GRAY_EXCLUSION_THRESHOLD_KEY = floatPreferencesKey("gray_exclusion_threshold")
        private val ENABLE_SECONDARY_COLOR_DETECTION_KEY = booleanPreferencesKey("enable_secondary_color_detection")
        private val FIRST_TIME_SETUP_COMPLETED_KEY = booleanPreferencesKey("first_time_setup_completed")
    }
    
    /**
     * Flow of license plate settings
     */
    val settings: Flow<LicensePlateSettings> = dataStore.data.map { preferences ->
        LicensePlateSettings(
            selectedOcrModel = try {
                OcrModelType.valueOf(
                    preferences[OCR_MODEL_KEY] ?: OcrModelType.ML_KIT.name
                )
            } catch (e: IllegalArgumentException) {
                OcrModelType.ML_KIT
            },
            processingInterval = preferences[PROCESSING_INTERVAL_KEY] ?: 1,
            minConfidenceThreshold = preferences[MIN_CONFIDENCE_KEY] ?: 0.5f,
            enableGpuAcceleration = preferences[GPU_ACCELERATION_KEY] ?: true,
            enableOcr = preferences[ENABLE_OCR_KEY] ?: true,
            enableNumericOnlyMode = preferences[NUMERIC_ONLY_MODE_KEY] ?: true,
            enableIsraeliFormatValidation = preferences[ISRAELI_FORMAT_VALIDATION_KEY] ?: true,
            enableDebugVideo = preferences[ENABLE_DEBUG_VIDEO_KEY] ?: false,
            cameraZoomRatio = preferences[CAMERA_ZOOM_RATIO_KEY] ?: 1.0f,
            selectedCountry = try {
                Country.valueOf(preferences[SELECTED_COUNTRY_KEY] ?: Country.ISRAEL.name)
            } catch (e: IllegalArgumentException) {
                Country.ISRAEL
            },
            
            // Vehicle Color Detection Settings
            enableGrayFiltering = preferences[ENABLE_GRAY_FILTERING_KEY] ?: true,
            grayExclusionThreshold = preferences[GRAY_EXCLUSION_THRESHOLD_KEY] ?: 50.0f,
            enableSecondaryColorDetection = preferences[ENABLE_SECONDARY_COLOR_DETECTION_KEY] ?: true
        )
    }
    
    /**
     * Updates license plate settings
     */
    suspend fun updateSettings(settings: LicensePlateSettings) {
        dataStore.edit { preferences ->
            preferences[OCR_MODEL_KEY] = settings.selectedOcrModel.name
            preferences[PROCESSING_INTERVAL_KEY] = settings.processingInterval
            preferences[MIN_CONFIDENCE_KEY] = settings.minConfidenceThreshold
            preferences[GPU_ACCELERATION_KEY] = settings.enableGpuAcceleration
            preferences[ENABLE_OCR_KEY] = settings.enableOcr
            preferences[NUMERIC_ONLY_MODE_KEY] = settings.enableNumericOnlyMode
            preferences[ISRAELI_FORMAT_VALIDATION_KEY] = settings.enableIsraeliFormatValidation
            preferences[ENABLE_DEBUG_VIDEO_KEY] = settings.enableDebugVideo
            preferences[CAMERA_ZOOM_RATIO_KEY] = settings.cameraZoomRatio
            preferences[SELECTED_COUNTRY_KEY] = settings.selectedCountry.name
            
            // Vehicle Color Detection Settings
            preferences[ENABLE_GRAY_FILTERING_KEY] = settings.enableGrayFiltering
            preferences[GRAY_EXCLUSION_THRESHOLD_KEY] = settings.grayExclusionThreshold
            preferences[ENABLE_SECONDARY_COLOR_DETECTION_KEY] = settings.enableSecondaryColorDetection
        }
    }
    
    /**
     * Updates only the camera zoom ratio for quick persistence
     */
    suspend fun updateCameraZoomRatio(zoomRatio: Float) {
        dataStore.edit { preferences ->
            preferences[CAMERA_ZOOM_RATIO_KEY] = zoomRatio
        }
    }
    
    /**
     * Initializes the license plate processor with actual saved settings
     */
    suspend fun initialize(): Boolean {
        // Load the actual saved settings instead of using defaults
        val actualSettings = settings.first()
        return licensePlateProcessor.initialize(actualSettings)
    }
    
    /**
     * Reinitializes the detector when settings change (for GPU acceleration)
     */
    suspend fun reinitializeWithSettings(settings: LicensePlateSettings): Boolean {
        return licensePlateProcessor.reinitializeDetector(settings)
    }
    
    /**
     * Gets detected plates state flow
     */
    val detectedPlates: StateFlow<List<PlateDetection>> = licensePlateProcessor.detectedPlates
    
    /**
     * Gets latest recognized text state flow
     */
    val latestRecognizedText: StateFlow<String?> = licensePlateProcessor.latestRecognizedText
    
    /**
     * Gets processing state flow
     */
    val isProcessing: StateFlow<Boolean> = licensePlateProcessor.isProcessing
    
    /**
     * Processes a camera frame for license plate detection with enhanced OCR
     * Features:
     * - GPU acceleration enabled by default for better performance
     * - Intelligent image scaling based on ML Kit recommendations
     * - High-resolution cropping from original camera frame for optimal OCR accuracy
     * - Only processes OCR when detection is present (performance optimization)
     */
    suspend fun processFrame(bitmap: Bitmap, settings: LicensePlateSettings): ProcessorResult {
        return licensePlateProcessor.processFrame(bitmap, settings)
    }
    
    /**
     * Releases processor resources
     */
    fun release() {
        licensePlateProcessor.release()
    }
    
    /**
     * Checks if processor is ready
     */
    fun isReady(): Boolean = licensePlateProcessor.isReady()
    
    /**
     * Gets GPU status for debug display
     */
    fun getGpuStatus(): Map<String, Boolean> = licensePlateProcessor.getGpuStatus()
    
    /**
     * Checks if first-time setup has been completed
     */
    val isFirstTimeSetupCompleted: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[FIRST_TIME_SETUP_COMPLETED_KEY] ?: false
    }
    
    /**
     * Marks first-time setup as completed
     */
    suspend fun completeFirstTimeSetup() {
        dataStore.edit { preferences ->
            preferences[FIRST_TIME_SETUP_COMPLETED_KEY] = true
        }
    }
} 