package com.example.vehiclerecognition.ui.camera

import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.vehiclerecognition.data.repositories.LicensePlateRepository
import com.example.vehiclerecognition.domain.logic.VehicleMatcher
import com.example.vehiclerecognition.domain.platform.SoundAlertPlayer
import com.example.vehiclerecognition.domain.repository.SettingsRepository
import com.example.vehiclerecognition.model.DetectionMode
import com.example.vehiclerecognition.model.VehicleColor
import com.example.vehiclerecognition.model.VehicleType
import com.example.vehiclerecognition.data.models.PlateDetection
import com.example.vehiclerecognition.data.models.LicensePlateSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

/**
 * ViewModel for the Camera screen.
 * Handles camera operations, vehicle detection matching, license plate recognition, and alerts.
 * (FR 1.1, FR 1.3, FR 1.4, FR 1.11, FR 1.12)
 *
 * @property vehicleMatcher Logic for matching detected vehicles against the watchlist.
 * @property settingsRepository Repository to get the current detection mode.
 * @property soundAlertPlayer Player for audible alerts.
 * @property licensePlateRepository Repository for license plate recognition functionality.
 */
@HiltViewModel
class CameraViewModel @Inject constructor(
    private val vehicleMatcher: VehicleMatcher,
    private val settingsRepository: SettingsRepository,
    private val soundAlertPlayer: SoundAlertPlayer,
    private val licensePlateRepository: LicensePlateRepository
) : ViewModel() {

    // Stores the desired user-facing zoom RATIO (e.g., 1.0f for 1x, 2.0f for 2x)
    private val _desiredZoomRatio = MutableStateFlow(1.0f) 
    val desiredZoomRatio: StateFlow<Float> = _desiredZoomRatio.asStateFlow()

    // Placeholder for detected vehicle information (would come from camera frames)
    // This is just for demonstrating the matching call.
    private val _lastDetectedVehicle = MutableStateFlow<VehicleMatcher.DetectedVehicle?>(null)

    // Placeholder for match status
    private val _matchFound = MutableStateFlow<Boolean>(false)
    val matchFound: StateFlow<Boolean> = _matchFound.asStateFlow()

    // --- State for CameraScreen ---
    private val _detectedPlates = MutableStateFlow<List<PlateDetection>>(emptyList())
    val detectedPlates: StateFlow<List<PlateDetection>> = _detectedPlates.asStateFlow()

    private val _performanceMetrics = MutableStateFlow<Map<String, Long>>(emptyMap())
    val performanceMetrics: StateFlow<Map<String, Long>> = _performanceMetrics.asStateFlow()

    private val _totalDetections = MutableStateFlow(0)
    val totalDetections: StateFlow<Int> = _totalDetections.asStateFlow()

    private val _rawOutputLog = MutableStateFlow("")
    val rawOutputLog: StateFlow<String> = _rawOutputLog.asStateFlow()

    private val _frameWidth = MutableStateFlow(0)
    val frameWidth: StateFlow<Int> = _frameWidth.asStateFlow()

    private val _frameHeight = MutableStateFlow(0)
    val frameHeight: StateFlow<Int> = _frameHeight.asStateFlow()

    private val _frameRotation = MutableStateFlow(0)
    val frameRotation: StateFlow<Int> = _frameRotation.asStateFlow()

    // Video display dimensions (for debug video mode coordinate transformation)
    private val _videoDisplayWidth = MutableStateFlow(0)
    val videoDisplayWidth: StateFlow<Int> = _videoDisplayWidth.asStateFlow()
    
    private val _videoDisplayHeight = MutableStateFlow(0)
    val videoDisplayHeight: StateFlow<Int> = _videoDisplayHeight.asStateFlow()
    
    // GPU status for debug display
    private val _gpuStatus = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val gpuStatus: StateFlow<Map<String, Boolean>> = _gpuStatus.asStateFlow()
    
    // Camera preview display dimensions (for normal camera mode coordinate transformation)
    private val _cameraPreviewWidth = MutableStateFlow(0)
    val cameraPreviewWidth: StateFlow<Int> = _cameraPreviewWidth.asStateFlow()
    
    private val _cameraPreviewHeight = MutableStateFlow(0)
    val cameraPreviewHeight: StateFlow<Int> = _cameraPreviewHeight.asStateFlow()

    private val _showDebugInfo = MutableStateFlow(false)
    val showDebugInfo: StateFlow<Boolean> = _showDebugInfo.asStateFlow()
    // ---

    // Holds the latest settings collected from the repository
    private val _currentSettings = MutableStateFlow(LicensePlateSettings())

    // License plate detection states
    val latestRecognizedText = licensePlateRepository.latestRecognizedText
    val isProcessingLicensePlates = licensePlateRepository.isProcessing
    val licensePlateSettings = licensePlateRepository.settings

    private var hideMatchFoundJob: Job? = null // Job to auto-hide the "MATCH FOUND" message
    
    // Frame processing optimization variables
    private var isCurrentlyProcessing = false
    private var lastProcessingTime = 0L
    private var latestFrameToProcess: Bitmap? = null
    private var frameProcessingJob: Job? = null
    private var detectionHideJob: Job? = null

    init {
        Log.d("CameraViewModel","CameraViewModel initialized.")
        
        // License plate processor will be initialized when settings are first collected
        
        // Initialize zoom with saved value from settings
        viewModelScope.launch {
            val initialSettings = licensePlateRepository.settings.first()
            _desiredZoomRatio.value = initialSettings.cameraZoomRatio
            Log.d("CameraViewModel", "Initialized zoom ratio from saved settings: ${initialSettings.cameraZoomRatio}")
        }
        
        // Start collecting settings updates and reinitialize detector when GPU settings change
        viewModelScope.launch {
            var previousGpuSetting: Boolean? = null
            var isFirstCollection = true
            
            licensePlateRepository.settings.collect { settings ->
                _currentSettings.value = settings
                
                // Update zoom if it changed in settings (but not if it's the initial load)
                if (!isFirstCollection && settings.cameraZoomRatio != _desiredZoomRatio.value) {
                    _desiredZoomRatio.value = settings.cameraZoomRatio
                    Log.d("CameraViewModel", "Updated zoom ratio from settings: ${settings.cameraZoomRatio}")
                }
                
                // Initialize detector on first collection, reinitialize on subsequent GPU changes
                val shouldReinitialize = if (isFirstCollection) {
                    // On first collection, always initialize with actual saved settings
                    Log.d("CameraViewModel", "First settings collection: GPU=${settings.enableGpuAcceleration}, initializing detector")
                    true
                } else {
                    // On subsequent collections, reinitialize only if GPU setting changed
                    previousGpuSetting != settings.enableGpuAcceleration
                }
                
                if (shouldReinitialize) {
                    if (isFirstCollection) {
                        Log.d("CameraViewModel", "Initializing detector with saved settings: GPU=${settings.enableGpuAcceleration}")
                    } else {
                        Log.d("CameraViewModel", "GPU acceleration setting changed to ${settings.enableGpuAcceleration}, reinitializing detector")
                    }
                    
                    // Cancel any ongoing frame processing to prevent conflicts
                    frameProcessingJob?.cancel()
                    frameProcessingJob = null
                    
                    // Wait a bit for any ongoing processing to complete
                    delay(100)
                    
                    try {
                        val success = if (isFirstCollection) {
                            // Use initialize for first time
                            licensePlateRepository.initialize()
                        } else {
                            // Use reinitialize for subsequent changes
                            licensePlateRepository.reinitializeWithSettings(settings)
                        }
                        
                        if (success) {
                            Log.d("CameraViewModel", "Detector ${if (isFirstCollection) "initialized" else "reinitialized"} successfully with GPU setting: ${settings.enableGpuAcceleration}")
                        } else {
                            Log.w("CameraViewModel", "Failed to ${if (isFirstCollection) "initialize" else "reinitialize"} detector with GPU setting: ${settings.enableGpuAcceleration}")
                        }
                    } catch (e: Exception) {
                        Log.e("CameraViewModel", "Error during detector ${if (isFirstCollection) "initialization" else "reinitialization"}", e)
                    }
                }
                
                previousGpuSetting = settings.enableGpuAcceleration
                isFirstCollection = false
            }
        }
        
        // Monitor license plate detections for watchlist matching
        viewModelScope.launch {
            latestRecognizedText.collectLatest { recognizedText ->
                recognizedText?.let { plateText ->
                    // Process the recognized license plate for watchlist matching
                    processDetection(plateText, null, null)
                }
            }
        }
        
        // Start periodic cleanup job to ensure expired detections are cleared
        viewModelScope.launch {
            while (true) {
                delay(500) // Check every 500ms
                clearExpiredDetections(System.currentTimeMillis())
            }
        }
        
        // In a real app, camera initialization and frame processing would start here.
    }

    fun onZoomRatioChanged(newRatio: Float) {
        // Store the zoom ratio - coercion is handled by the camera view based on actual camera capabilities
        _desiredZoomRatio.value = newRatio
        Log.d("CameraViewModel","CameraViewModel: Desired zoom ratio changed to ${_desiredZoomRatio.value}")
        
        // Persist the zoom ratio to settings
        viewModelScope.launch {
            try {
                licensePlateRepository.updateCameraZoomRatio(newRatio)
                Log.d("CameraViewModel", "Zoom ratio persisted: $newRatio")
            } catch (e: Exception) {
                Log.e("CameraViewModel", "Failed to persist zoom ratio", e)
            }
        }
    }

    /**
     * Optimized camera frame processing with frame skipping and result timeout
     * 1. Don't remember old frames - only process the most recent
     * 2. Each detection expires after 1 second automatically
     * 3. Skip intermediate frames to only process the latest
     */
    fun processCameraFrame(bitmap: Bitmap, rotation: Int) {
        _frameWidth.value = bitmap.width
        _frameHeight.value = bitmap.height
        _frameRotation.value = rotation
        
        val currentTime = System.currentTimeMillis()
        
        // Clear any expired detections before processing new frame
        clearExpiredDetections(currentTime)
        
        // If currently processing, update the latest frame to process and return
        if (isCurrentlyProcessing) {
            // Recycle the previous latest frame if it exists
            latestFrameToProcess?.let { oldBitmap ->
                if (!oldBitmap.isRecycled) {
                    oldBitmap.recycle()
                }
            }
            // Store the new frame as the latest to process
            latestFrameToProcess = bitmap.copy(bitmap.config, false)
            bitmap.recycle() // Recycle the original since we made a copy
            Log.d("CameraViewModel", "Frame queued while processing, replaced latest frame")
            return
        }
        
        // Start processing this frame
        isCurrentlyProcessing = true
        lastProcessingTime = currentTime
        
        frameProcessingJob?.cancel() // Cancel any existing processing job
        frameProcessingJob = viewModelScope.launch {
            try {
                Log.d("CameraViewModel", "Processing frame: ${bitmap.width}x${bitmap.height}, rotation: $rotation")
                
                val currentSettings = _currentSettings.value
                Log.d("CameraViewModel", "Current settings: confidence=${currentSettings.minConfidenceThreshold}, gpu=${currentSettings.enableGpuAcceleration}")
                
                val result = licensePlateRepository.processFrame(bitmap, currentSettings)

                Log.d("CameraViewModel", "Detection result: ${result.detections.size} detections found")
                
                // Update GPU status for debug display
                _gpuStatus.value = licensePlateRepository.getGpuStatus()
                
                // Update detections if any found
                if (result.detections.isNotEmpty()) {
                    // Add timestamp to each detection for expiration tracking
                    val timestampedDetections = result.detections.map { detection ->
                        detection.copy(detectionTime = currentTime)
                    }
                    _detectedPlates.value = timestampedDetections
                    _totalDetections.value = _totalDetections.value + result.detections.size
                    
                    // Start automatic expiration job for these detections
                    detectionHideJob?.cancel()
                    detectionHideJob = launch {
                        delay(1000) // 1 second
                        clearExpiredDetections(System.currentTimeMillis())
                    }
                    
                    result.detections.forEachIndexed { index, detection ->
                        Log.d("CameraViewModel", "Detection $index: bbox=${detection.boundingBox}, conf=${detection.confidence}")
                    }
                }

                // Always show performance metrics in debug mode
                _performanceMetrics.value = result.performance
                _rawOutputLog.value = result.rawOutputLog
                
            } catch (e: Exception) {
                Log.e("CameraViewModel", "Error processing camera frame", e)
                _rawOutputLog.value = "Error: ${e.message}"
                _performanceMetrics.value = emptyMap()
            } finally {
                // Recycle the processed bitmap
                if (!bitmap.isRecycled) {
                    bitmap.recycle()
                    Log.d("CameraViewModel", "Processed bitmap recycled")
                }
                
                // Check if there's a newer frame waiting to be processed
                val nextFrame = latestFrameToProcess
                latestFrameToProcess = null
                isCurrentlyProcessing = false
                
                if (nextFrame != null) {
                    Log.d("CameraViewModel", "Processing queued frame")
                    processCameraFrame(nextFrame, rotation)
                }
            }
        }
    }
    
    /**
     * Clears detections that are older than 1 second
     */
    private fun clearExpiredDetections(currentTime: Long) {
        val currentDetections = _detectedPlates.value
        if (currentDetections.isNotEmpty()) {
            val validDetections = currentDetections.filter { detection ->
                val age = currentTime - (detection.detectionTime ?: 0L)
                age < 1000 // Keep detections younger than 1 second
            }
            
            if (validDetections.size != currentDetections.size) {
                _detectedPlates.value = validDetections
                Log.d("CameraViewModel", "Cleared ${currentDetections.size - validDetections.size} expired detections")
            }
        }
    }

    /**
     * Updates the video display dimensions for proper coordinate transformation in debug mode
     */
    fun updateVideoDisplayDimensions(width: Int, height: Int) {
        _videoDisplayWidth.value = width
        _videoDisplayHeight.value = height
        Log.d("CameraViewModel", "Video display dimensions updated: ${width}x${height}")
    }

    /**
     * Updates the camera preview display dimensions for proper coordinate transformation in normal mode
     */
    fun updateCameraPreviewDimensions(width: Int, height: Int) {
        _cameraPreviewWidth.value = width
        _cameraPreviewHeight.value = height
        Log.d("CameraViewModel", "Camera preview dimensions updated: ${width}x${height}")
    }

    /**
     * Processes a detected vehicle (either from actual camera frames or simulation).
     * Updates the match status based on the current detection mode.
     */
    fun processDetection(licensePlate: String?, color: VehicleColor?, type: VehicleType?) {
        val detectedVehicle = VehicleMatcher.DetectedVehicle(licensePlate, color, type)
        _lastDetectedVehicle.value = detectedVehicle
        Log.d("CameraViewModel", "Processing detection - LP: $licensePlate, Color: $color, Type: $type")

        viewModelScope.launch {
            val currentDetectionMode = settingsRepository.getDetectionMode()
            val isMatch = vehicleMatcher.findMatch(detectedVehicle, currentDetectionMode)
            _matchFound.value = isMatch
            if (isMatch) {
                Log.d("CameraViewModel", "MATCH FOUND based on mode $currentDetectionMode!")
                soundAlertPlayer.playAlert() // FR 1.12
            } else {
                Log.d("CameraViewModel", "No match found for mode $currentDetectionMode.")
            }
        }
    }

    // Example: Simulate a detection for testing purposes
    fun simulateCarDetection(lp: String, vColor: VehicleColor, vType: VehicleType) {
        processDetection(lp, vColor, vType)
    }

    /**
     * Simulates an LP detection, specifically for the "LP Match" and "No Match" buttons.
     * For "LP Match" (lp = "12-345-67"), it forces a match if the current mode is LP-based.
     * For "No Match" (lp = "00-000-00"), it forces no match.
     */
    fun simulateLPDetection(lp: String) {
        viewModelScope.launch {
            hideMatchFoundJob?.cancel() // Cancel any existing auto-hide job

            if (lp == "12-345-67") { // "Match" button pressed
                _matchFound.value = true
                soundAlertPlayer.playAlert()
                Log.d("CameraViewModel_Simulate", "Simulated Match: Displaying message and playing sound.")
                hideMatchFoundJob = launch {
                    delay(10000) // 10 seconds
                    _matchFound.value = false
                    Log.d("CameraViewModel_Simulate", "Auto-hiding 'MATCH FOUND' message after 10s.")
                }
            } else if (lp == "00-000-00") { // "No Match" button pressed
                 _matchFound.value = false
                 Log.d("CameraViewModel_Simulate", "Simulated No Match: Hiding message.")
            }
            // The actual detection mode is now irrelevant for these simulation buttons.
            // Generic processDetection call for other LPs is removed as it's not used by current UI.
        }
    }

    /**
     * Toggles the visibility of the debug information overlay.
     */
    fun toggleDebugInfo() {
        _showDebugInfo.value = !_showDebugInfo.value
    }

    override fun onCleared() {
        super.onCleared()
        hideMatchFoundJob?.cancel() // Ensure the job is cancelled if ViewModel is cleared
        frameProcessingJob?.cancel() // Cancel frame processing job
        detectionHideJob?.cancel() // Cancel detection hide job
        
        // Recycle any pending frame
        latestFrameToProcess?.let { bitmap ->
            if (!bitmap.isRecycled) {
                bitmap.recycle()
            }
        }
        latestFrameToProcess = null
        
        licensePlateRepository.release() // Release license plate processor resources
        Log.d("CameraViewModel", "CameraViewModel: onCleared")
    }
} 