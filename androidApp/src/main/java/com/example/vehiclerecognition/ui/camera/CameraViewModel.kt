package com.example.vehiclerecognition.ui.camera

import android.graphics.Bitmap
import android.graphics.RectF
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
import com.example.vehiclerecognition.data.models.VehicleDetection
import com.example.vehiclerecognition.ml.processors.VehicleSegmentationProcessor
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
    private val licensePlateRepository: LicensePlateRepository,
    private val vehicleSegmentationProcessor: VehicleSegmentationProcessor
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

    private val _detectedVehicles = MutableStateFlow<List<VehicleDetection>>(emptyList())
    val detectedVehicles: StateFlow<List<VehicleDetection>> = _detectedVehicles.asStateFlow()

    private val _performanceMetrics = MutableStateFlow<Map<String, Long>>(emptyMap())
    val performanceMetrics: StateFlow<Map<String, Long>> = _performanceMetrics.asStateFlow()

    private val _vehiclePerformanceMetrics = MutableStateFlow<Map<String, Long>>(emptyMap())
    val vehiclePerformanceMetrics: StateFlow<Map<String, Long>> = _vehiclePerformanceMetrics.asStateFlow()

    private val _totalDetections = MutableStateFlow(0)
    val totalDetections: StateFlow<Int> = _totalDetections.asStateFlow()

    private val _totalVehicleDetections = MutableStateFlow(0)
    val totalVehicleDetections: StateFlow<Int> = _totalVehicleDetections.asStateFlow()

    private val _rawOutputLog = MutableStateFlow("")
    val rawOutputLog: StateFlow<String> = _rawOutputLog.asStateFlow()

    private val _vehicleRawOutputLog = MutableStateFlow("")
    val vehicleRawOutputLog: StateFlow<String> = _vehicleRawOutputLog.asStateFlow()

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
            var previousDetectionMode: DetectionMode? = null
            var isFirstCollection = true
            
            licensePlateRepository.settings.collect { settings ->
                _currentSettings.value = settings
                
                // Update zoom if it changed in settings (but not if it's the initial load)
                if (!isFirstCollection && settings.cameraZoomRatio != _desiredZoomRatio.value) {
                    _desiredZoomRatio.value = settings.cameraZoomRatio
                    Log.d("CameraViewModel", "Updated zoom ratio from settings: ${settings.cameraZoomRatio}")
                }
                
                // Initialize detector on first collection, reinitialize on subsequent GPU or detection mode changes
                val currentDetectionMode = settingsRepository.getDetectionMode()
                val shouldReinitialize = if (isFirstCollection) {
                    // On first collection, always initialize with actual saved settings
                    Log.d("CameraViewModel", "First settings collection: GPU=${settings.enableGpuAcceleration}, Mode=$currentDetectionMode, initializing detector")
                    true
                } else {
                    // On subsequent collections, reinitialize if GPU setting or detection mode changed
                    val gpuChanged = previousGpuSetting != settings.enableGpuAcceleration
                    val modeChanged = previousDetectionMode != currentDetectionMode
                    if (gpuChanged) {
                        Log.d("CameraViewModel", "GPU acceleration setting changed to ${settings.enableGpuAcceleration}, reinitializing detector")
                    }
                    if (modeChanged) {
                        Log.d("CameraViewModel", "Detection mode changed from $previousDetectionMode to $currentDetectionMode, reinitializing detector")
                    }
                    gpuChanged || modeChanged
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
                        // Use the detection mode we already retrieved above
                        val needsLpDetection = needsLicensePlateDetection(currentDetectionMode)
                        val needsVehicleDetection = needsVehicleDetection(currentDetectionMode)
                        
                        Log.d("CameraViewModel", "Initializing models based on mode $currentDetectionMode - LP: $needsLpDetection, Vehicle: $needsVehicleDetection")
                        
                        // Initialize license plate detection only if needed
                        val licensePlateSuccess = if (needsLpDetection) {
                            if (isFirstCollection) {
                                licensePlateRepository.initialize()
                            } else {
                                licensePlateRepository.reinitializeWithSettings(settings)
                            }
                        } else {
                            Log.d("CameraViewModel", "Skipping license plate model initialization (not needed for mode: $currentDetectionMode)")
                            true // Return success since it's not needed
                        }
                        
                        // Initialize vehicle segmentation processor only if needed
                        val vehicleSegmentationSuccess = if (needsVehicleDetection) {
                            if (isFirstCollection) {
                                vehicleSegmentationProcessor.initialize(settings)
                            } else {
                                vehicleSegmentationProcessor.reinitializeDetector(settings)
                            }
                        } else {
                            Log.d("CameraViewModel", "Skipping vehicle detection model initialization (not needed for mode: $currentDetectionMode)")
                            true // Return success since it's not needed
                        }
                        
                        if (licensePlateSuccess && vehicleSegmentationSuccess) {
                            val enabledModels = mutableListOf<String>()
                            if (needsLpDetection) enabledModels.add("LP")
                            if (needsVehicleDetection) enabledModels.add("Vehicle")
                            Log.d("CameraViewModel", "Models ${if (isFirstCollection) "initialized" else "reinitialized"} successfully: ${enabledModels.joinToString(", ")} (GPU: ${settings.enableGpuAcceleration})")
                        } else {
                            Log.w("CameraViewModel", "Failed to ${if (isFirstCollection) "initialize" else "reinitialize"} detectors - LP: $licensePlateSuccess, VS: $vehicleSegmentationSuccess")
                        }
                    } catch (e: Exception) {
                        Log.e("CameraViewModel", "Error during detector ${if (isFirstCollection) "initialization" else "reinitialization"}", e)
                    }
                }
                
                previousGpuSetting = settings.enableGpuAcceleration
                previousDetectionMode = currentDetectionMode
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
                
                // Get current detection mode to determine which models to run
                val currentDetectionMode = settingsRepository.getDetectionMode()
                Log.d("CameraViewModel", "Current detection mode: $currentDetectionMode")
                
                // Determine which models need to run based on detection mode
                val needsLicensePlateDetection = needsLicensePlateDetection(currentDetectionMode)
                val needsVehicleDetection = needsVehicleDetection(currentDetectionMode)
                
                Log.d("CameraViewModel", "Running models - LP: $needsLicensePlateDetection, Vehicle: $needsVehicleDetection")

                // Process only the required detection models based on settings
                val lpResult = if (needsLicensePlateDetection) {
                    licensePlateRepository.processFrame(bitmap, currentSettings)
                } else {
                    // Return empty result when license plate detection is not needed
                    com.example.vehiclerecognition.ml.processors.ProcessorResult(
                        detections = emptyList(),
                        performance = emptyMap(),
                        rawOutputLog = "Skipped (not needed for mode: $currentDetectionMode)"
                    )
                }
                
                val vehicleResult = if (needsVehicleDetection) {
                    vehicleSegmentationProcessor.processFrame(bitmap, currentSettings)
                } else {
                    // Return empty result when vehicle detection is not needed
                    com.example.vehiclerecognition.data.models.VehicleSegmentationResult(
                        detections = emptyList(),
                        performance = emptyMap(),
                        rawOutputLog = "Skipped (not needed for mode: $currentDetectionMode)"
                    )
                }

                Log.d("CameraViewModel", "LP Detection result: ${lpResult.detections.size} plates found")
                Log.d("CameraViewModel", "Vehicle Detection result: ${vehicleResult.detections.size} vehicles found")
                
                // Update GPU status for debug display
                _gpuStatus.value = licensePlateRepository.getGpuStatus()
                
                // Update license plate detections if any found
                if (lpResult.detections.isNotEmpty()) {
                    // Add timestamp to each detection for expiration tracking
                    val timestampedDetections = lpResult.detections.map { detection ->
                        detection.copy(detectionTime = currentTime)
                    }
                    _detectedPlates.value = timestampedDetections
                    _totalDetections.value = _totalDetections.value + lpResult.detections.size
                    
                    // Start automatic expiration job for these detections
                    detectionHideJob?.cancel()
                    detectionHideJob = launch {
                        delay(1000) // 1 second
                        clearExpiredDetections(System.currentTimeMillis())
                    }
                    
                    lpResult.detections.forEachIndexed { index, detection ->
                        Log.d("CameraViewModel", "LP Detection $index: bbox=${detection.boundingBox}, conf=${detection.confidence}")
                    }
                }
                
                // Update vehicle detections if any found
                if (vehicleResult.detections.isNotEmpty()) {
                    // Add timestamp to each detection for expiration tracking
                    val timestampedVehicleDetections = vehicleResult.detections.map { detection ->
                        detection.copy(detectionTime = currentTime)
                    }
                    _detectedVehicles.value = timestampedVehicleDetections
                    _totalVehicleDetections.value = _totalVehicleDetections.value + vehicleResult.detections.size
                    
                    vehicleResult.detections.forEachIndexed { index, detection ->
                        Log.d("CameraViewModel", "Vehicle Detection $index: ${detection.className} bbox=${detection.boundingBox}, conf=${detection.confidence}")
                    }
                }

                // Assign vehicle IDs to license plates only if both detections were run
                if (needsLicensePlateDetection && needsVehicleDetection) {
                    assignVehicleIdsToLicensePlates()
                    Log.d("CameraViewModel", "Vehicle ID assignment performed")
                } else {
                    Log.d("CameraViewModel", "Vehicle ID assignment skipped (LP needed: $needsLicensePlateDetection, Vehicle needed: $needsVehicleDetection)")
                }

                // Always show performance metrics in debug mode
                _performanceMetrics.value = lpResult.performance
                _rawOutputLog.value = lpResult.rawOutputLog
                _vehiclePerformanceMetrics.value = vehicleResult.performance
                _vehicleRawOutputLog.value = vehicleResult.rawOutputLog
                
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
        // Clear expired license plate detections
        val currentDetections = _detectedPlates.value
        if (currentDetections.isNotEmpty()) {
            val validDetections = currentDetections.filter { detection ->
                val age = currentTime - (detection.detectionTime ?: 0L)
                age < 1000 // Keep detections younger than 1 second
            }
            
            if (validDetections.size != currentDetections.size) {
                _detectedPlates.value = validDetections
                Log.d("CameraViewModel", "Cleared ${currentDetections.size - validDetections.size} expired plate detections")
            }
        }
        
        // Clear expired vehicle detections
        val currentVehicleDetections = _detectedVehicles.value
        if (currentVehicleDetections.isNotEmpty()) {
            val validVehicleDetections = currentVehicleDetections.filter { detection ->
                val age = currentTime - (detection.detectionTime ?: 0L)
                age < 1000 // Keep detections younger than 1 second
            }
            
            if (validVehicleDetections.size != currentVehicleDetections.size) {
                _detectedVehicles.value = validVehicleDetections
                Log.d("CameraViewModel", "Cleared ${currentVehicleDetections.size - validVehicleDetections.size} expired vehicle detections")
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
     * Assigns vehicle IDs to license plates that fall within vehicle bounding boxes
     */
    private fun assignVehicleIdsToLicensePlates() {
        val currentPlates = _detectedPlates.value
        val currentVehicles = _detectedVehicles.value
        
        if (currentPlates.isEmpty() || currentVehicles.isEmpty()) {
            return // Nothing to assign
        }
        
        // Create updated license plates with assigned vehicle IDs
        val updatedPlates = currentPlates.map { plate ->
            // Find the vehicle whose bounding box contains or intersects with this license plate
            val containingVehicle = currentVehicles.find { vehicle ->
                boundingBoxIntersects(plate.boundingBox, vehicle.boundingBox)
            }
            
            if (containingVehicle != null && plate.vehicleId != containingVehicle.id) {
                Log.d("CameraViewModel", "Assigning vehicle ID ${containingVehicle.id} to license plate at ${plate.boundingBox}")
                plate.copy(vehicleId = containingVehicle.id)
            } else {
                plate
            }
        }
        
        // Update the plates if any assignments were made
        if (updatedPlates != currentPlates) {
            _detectedPlates.value = updatedPlates
            val assignmentCount = updatedPlates.count { it.vehicleId != null }
            Log.d("CameraViewModel", "Vehicle ID assignment complete: $assignmentCount plates assigned to vehicles")
        }
    }

    /**
     * Determines if license plate detection is needed based on the current detection mode
     */
    private fun needsLicensePlateDetection(mode: DetectionMode): Boolean {
        return when (mode) {
            DetectionMode.LP,           // License Plate only
            DetectionMode.LP_COLOR,     // License Plate + Color
            DetectionMode.LP_TYPE,      // License Plate + Type
            DetectionMode.LP_COLOR_TYPE // License Plate + Color + Type
            -> true
            DetectionMode.COLOR_TYPE,   // Color + Type (no LP needed)
            DetectionMode.COLOR         // Color only (no LP needed)
            -> false
        }
    }

    /**
     * Determines if vehicle detection is needed based on the current detection mode
     */
    private fun needsVehicleDetection(mode: DetectionMode): Boolean {
        return when (mode) {
            DetectionMode.LP_COLOR,     // License Plate + Color
            DetectionMode.LP_TYPE,      // License Plate + Type
            DetectionMode.LP_COLOR_TYPE,// License Plate + Color + Type
            DetectionMode.COLOR_TYPE,   // Color + Type
            DetectionMode.COLOR         // Color only
            -> true
            DetectionMode.LP            // License Plate only (no vehicle detection needed)
            -> false
        }
    }

    /**
     * Checks if two bounding boxes intersect or if the first box is contained within the second
     */
    private fun boundingBoxIntersects(plateBox: RectF, vehicleBox: RectF): Boolean {
        // Check if the license plate bounding box intersects with or is contained within the vehicle bounding box
        // We use intersects() which returns true if the rectangles overlap in any way
        val intersects = RectF.intersects(plateBox, vehicleBox)
        
        // Additional check: if the license plate center point is within the vehicle box
        val plateCenterX = plateBox.centerX()
        val plateCenterY = plateBox.centerY()
        val centerWithinVehicle = vehicleBox.contains(plateCenterX, plateCenterY)
        
        val result = intersects || centerWithinVehicle
        
        if (result) {
            Log.d("CameraViewModel", "Bounding box match: Plate(${plateBox.left.toInt()},${plateBox.top.toInt()},${plateBox.right.toInt()},${plateBox.bottom.toInt()}) intersects with Vehicle(${vehicleBox.left.toInt()},${vehicleBox.top.toInt()},${vehicleBox.right.toInt()},${vehicleBox.bottom.toInt()})")
        }
        
        return result
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
        vehicleSegmentationProcessor.release() // Release vehicle segmentation processor resources
        Log.d("CameraViewModel", "CameraViewModel: onCleared")
    }
} 