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
import com.example.vehiclerecognition.domain.repository.WatchlistRepository
import com.example.vehiclerecognition.data.models.DetectionMode
import com.example.vehiclerecognition.model.VehicleColor
import com.example.vehiclerecognition.model.VehicleType
import com.example.vehiclerecognition.data.models.PlateDetection
import com.example.vehiclerecognition.data.models.LicensePlateSettings
import com.example.vehiclerecognition.data.models.VehicleDetection
import com.example.vehiclerecognition.data.models.Country
import com.example.vehiclerecognition.ml.processors.VehicleSegmentationProcessor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
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
    private val vehicleSegmentationProcessor: VehicleSegmentationProcessor,
    private val watchlistRepository: WatchlistRepository
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

    // Track which specific detections matched the watchlist
    private val _matchedPlateIds = MutableStateFlow<Set<String>>(emptySet())
    val matchedPlateIds: StateFlow<Set<String>> = _matchedPlateIds.asStateFlow()

    private val _matchedVehicleIds = MutableStateFlow<Set<String>>(emptySet())
    val matchedVehicleIds: StateFlow<Set<String>> = _matchedVehicleIds.asStateFlow()

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
    
    // First-time setup state
    val isFirstTimeSetupCompleted = licensePlateRepository.isFirstTimeSetupCompleted

    private var hideMatchFoundJob: Job? = null // Job to auto-hide the "MATCH FOUND" message
    
    // Frame processing optimization variables
    private var isCurrentlyProcessing = false
    private var lastProcessingTime = 0L
    private var latestFrameToProcess: Bitmap? = null
    private var frameProcessingJob: Job? = null
    private var detectionHideJob: Job? = null
    
    // Alert management
    private var lastAlertTime = 0L
    private val alertCooldownMs = 5000L // 5 seconds between alerts
    private var watchlistMatchingJob: Job? = null
    private var soundAlertJob: Job? = null // Separate job for sound alert management
    
    // Model initialization status tracking
    private val _isInitializingModels = MutableStateFlow(false)
    val isInitializingModels: StateFlow<Boolean> = _isInitializingModels.asStateFlow()
    
    private val _initializationStatus = MutableStateFlow<String>("Ready")
    val initializationStatus: StateFlow<String> = _initializationStatus.asStateFlow()
    
    private val _modelsReady = MutableStateFlow(false)
    val modelsReady: StateFlow<Boolean> = _modelsReady.asStateFlow()

    init {
        Log.d("CameraViewModel","CameraViewModel initialized.")
        
        // License plate processor will be initialized when settings are first collected
        
        // Initialize zoom with saved value from settings
        viewModelScope.launch {
            try {
                val initialSettings = licensePlateRepository.settings.first()
                _desiredZoomRatio.value = initialSettings.cameraZoomRatio
                Log.d("CameraViewModel", "Initialized zoom ratio from saved settings: ${initialSettings.cameraZoomRatio}")
            } catch (e: Exception) {
                Log.e("CameraViewModel", "Error loading initial settings, using default zoom", e)
                _desiredZoomRatio.value = 1.0f
            }
        }
        
        // Observe both license plate settings AND detection mode changes for model reinitialization
        viewModelScope.launch {
            var previousGpuSetting: Boolean? = null
            var previousDetectionMode: DetectionMode? = null
            var previousCountry: Country? = null
            var isFirstCollection = true
            
            // Combine both flows to react to changes in either license plate settings or detection mode
            kotlinx.coroutines.flow.combine(
                licensePlateRepository.settings,
                settingsRepository.detectionMode
            ) { settings, detectionMode ->
                Pair(settings, detectionMode)
            }.collect { (settings, detectionMode) ->
                try {
                _currentSettings.value = settings
                
                // Update zoom if it changed in settings (but not if it's the initial load)
                if (!isFirstCollection && settings.cameraZoomRatio != _desiredZoomRatio.value) {
                    _desiredZoomRatio.value = settings.cameraZoomRatio
                    Log.d("CameraViewModel", "Updated zoom ratio from settings: ${settings.cameraZoomRatio}")
                }
                
                val shouldReinitialize = if (isFirstCollection) {
                    // On first collection, always initialize with actual saved settings
                    Log.d("CameraViewModel", "First settings collection: GPU=${settings.enableGpuAcceleration}, Mode=$detectionMode, Country=${settings.selectedCountry.displayName}, initializing detector")
                    true
                } else {
                    // On subsequent collections, reinitialize if GPU setting, detection mode, or country changed
                    val gpuChanged = previousGpuSetting != settings.enableGpuAcceleration
                    val modeChanged = previousDetectionMode != detectionMode
                    val countryChanged = previousCountry != settings.selectedCountry
                    if (gpuChanged) {
                        Log.d("CameraViewModel", "GPU acceleration setting changed to ${settings.enableGpuAcceleration}, reinitializing detector")
                    }
                    if (modeChanged) {
                        Log.d("CameraViewModel", "Detection mode changed from $previousDetectionMode to $detectionMode, reinitializing detector")
                    }
                    if (countryChanged) {
                        Log.d("CameraViewModel", "Country changed from ${previousCountry?.displayName} to ${settings.selectedCountry.displayName}, reinitializing OCR engine")
                    }
                    gpuChanged || modeChanged || countryChanged
                }
                
                if (shouldReinitialize) {
                    // Set initialization status
                    _isInitializingModels.value = true
                    _modelsReady.value = false
                    
                    if (isFirstCollection) {
                        Log.d("CameraViewModel", "Initializing detector with saved settings: GPU=${settings.enableGpuAcceleration}, Mode=$detectionMode")
                        _initializationStatus.value = "Initializing models..."
                    } else {
                        Log.d("CameraViewModel", "Reinitializing detector due to settings change: GPU=${settings.enableGpuAcceleration}, Mode=$detectionMode")
                        _initializationStatus.value = "Reinitializing models..."
                    }
                    
                    // Cancel any ongoing frame processing to prevent conflicts
                    frameProcessingJob?.cancel()
                    frameProcessingJob = null
                    
                    // Reset processing state to ensure frame processing can resume after reinitialization
                    isCurrentlyProcessing = false
                    
                    // Recycle any pending frame since we're reinitializing
                    latestFrameToProcess?.let { bitmap ->
                        if (!bitmap.isRecycled) {
                            bitmap.recycle()
                        }
                    }
                    latestFrameToProcess = null
                    
                    // Clear existing detection results to provide immediate visual feedback
                    _detectedPlates.value = emptyList()
                    _detectedVehicles.value = emptyList()
                    _performanceMetrics.value = emptyMap()
                    _vehiclePerformanceMetrics.value = emptyMap()
                    _rawOutputLog.value = "Reinitializing models..."
                    _vehicleRawOutputLog.value = "Reinitializing models..."
                    
                    // Wait a bit for any ongoing processing to complete
                    delay(100)
                    
                    try {
                        val needsLpDetection = needsLicensePlateDetection(detectionMode)
                        val needsVehicleDetection = needsVehicleDetection(detectionMode)
                        
                        Log.d("CameraViewModel", "Initializing models based on mode $detectionMode - LP: $needsLpDetection, Vehicle: $needsVehicleDetection")
                        
                        // Initialize license plate detection only if needed
                        val licensePlateSuccess = if (needsLpDetection) {
                            _initializationStatus.value = "Loading license plate model..."
                            if (isFirstCollection) {
                                licensePlateRepository.initialize()
                            } else {
                                licensePlateRepository.reinitializeWithSettings(settings)
                            }
                        } else {
                            Log.d("CameraViewModel", "Skipping license plate model initialization (not needed for mode: $detectionMode)")
                            true // Return success since it's not needed
                        }
                        
                        // Initialize vehicle segmentation processor only if needed
                        val vehicleSegmentationSuccess = if (needsVehicleDetection) {
                            _initializationStatus.value = "Loading vehicle detection model..."
                            if (isFirstCollection) {
                                vehicleSegmentationProcessor.initialize(settings)
                            } else {
                                vehicleSegmentationProcessor.reinitializeDetector(settings)
                            }
                        } else {
                            Log.d("CameraViewModel", "Skipping vehicle detection model initialization (not needed for mode: $detectionMode)")
                            true // Return success since it's not needed
                        }
                        
                        if (licensePlateSuccess && vehicleSegmentationSuccess) {
                            val enabledModels = mutableListOf<String>()
                            if (needsLpDetection) enabledModels.add("LP")
                            if (needsVehicleDetection) enabledModels.add("Vehicle")
                            Log.d("CameraViewModel", "Models ${if (isFirstCollection) "initialized" else "reinitialized"} successfully: ${enabledModels.joinToString(", ")} (GPU: ${settings.enableGpuAcceleration})")
                            
                            _initializationStatus.value = "Models ready (${enabledModels.joinToString(", ")})"
                            
                            // Give models a moment to stabilize after reinitialization
                            if (!isFirstCollection) {
                                delay(200)
                                Log.d("CameraViewModel", "Model stabilization period completed")
                            }
                            
                            _modelsReady.value = true
                            
                            // Clear the initialization status after a brief delay
                            delay(2000)
                            if (_modelsReady.value) { // Only clear if still ready (no new initialization started)
                                _initializationStatus.value = "Ready"
                            }
                        } else {
                            Log.w("CameraViewModel", "Failed to ${if (isFirstCollection) "initialize" else "reinitialize"} detectors - LP: $licensePlateSuccess, VS: $vehicleSegmentationSuccess")
                            _initializationStatus.value = "Initialization failed"
                            _modelsReady.value = false
                            
                            // Clear detection results on failed initialization
                            _detectedPlates.value = emptyList()
                            _detectedVehicles.value = emptyList()
                            _performanceMetrics.value = emptyMap()
                            _vehiclePerformanceMetrics.value = emptyMap()
                            _rawOutputLog.value = "Initialization failed"
                            _vehicleRawOutputLog.value = "Initialization failed"
                            
                            // Clear error status after delay
                            delay(3000)
                            _initializationStatus.value = "Ready"
                        }
                    } catch (e: Exception) {
                        Log.e("CameraViewModel", "Error during detector ${if (isFirstCollection) "initialization" else "reinitialization"}", e)
                        _initializationStatus.value = "Error: ${e.message}"
                        _modelsReady.value = false
                        
                        // Clear detection results on error
                        _detectedPlates.value = emptyList()
                        _detectedVehicles.value = emptyList()
                        _performanceMetrics.value = emptyMap()
                        _vehiclePerformanceMetrics.value = emptyMap()
                        _rawOutputLog.value = "Error: ${e.message}"
                        _vehicleRawOutputLog.value = "Error: ${e.message}"
                        
                        // Clear error status after delay
                        delay(3000)
                        _initializationStatus.value = "Ready"
                    } finally {
                        _isInitializingModels.value = false
                    }
                }
                
                previousGpuSetting = settings.enableGpuAcceleration
                previousDetectionMode = detectionMode
                previousCountry = settings.selectedCountry
                isFirstCollection = false
                } catch (e: Exception) {
                    Log.e("CameraViewModel", "Error in settings collection", e)
                    _initializationStatus.value = "Settings error: ${e.message}"
                    _modelsReady.value = false
                    _isInitializingModels.value = false
                }
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
                // Skip processing if models are currently initializing
                if (_isInitializingModels.value) {
                    Log.d("CameraViewModel", "Skipping frame processing - models are initializing")
                    return@launch
                }
                
                Log.d("CameraViewModel", "Processing frame: ${bitmap.width}x${bitmap.height}, rotation: $rotation")
                
                val currentSettings = _currentSettings.value
                Log.d("CameraViewModel", "Current settings: confidence=${currentSettings.minConfidenceThreshold}, gpu=${currentSettings.enableGpuAcceleration}")
                
                // Get current detection mode to determine which models to run
                val currentDetectionMode = settingsRepository.detectionMode.value
                Log.d("CameraViewModel", "Current detection mode: $currentDetectionMode")
                
                // Determine which models need to run based on detection mode
                val needsLicensePlateDetection = needsLicensePlateDetection(currentDetectionMode)
                val needsVehicleDetection = needsVehicleDetection(currentDetectionMode)
                
                Log.d("CameraViewModel", "Running models in parallel - LP: $needsLicensePlateDetection, Vehicle: $needsVehicleDetection")

                // Process all required detection models in parallel for maximum performance
                val lpResultDeferred = if (needsLicensePlateDetection) {
                    async {
                        Log.d("CameraViewModel", "Starting parallel license plate detection")
                        licensePlateRepository.processFrame(bitmap, currentSettings)
                    }
                } else {
                    async {
                        // Return empty result when license plate detection is not needed
                        com.example.vehiclerecognition.ml.processors.ProcessorResult(
                            detections = emptyList(),
                            performance = emptyMap(),
                            rawOutputLog = "Skipped (not needed for mode: $currentDetectionMode)"
                        )
                    }
                }
                
                val vehicleResultDeferred = if (needsVehicleDetection) {
                    async {
                        Log.d("CameraViewModel", "Starting parallel vehicle detection and color analysis")
                        vehicleSegmentationProcessor.processFrame(bitmap, currentSettings)
                    }
                } else {
                    async {
                        // Return empty result when vehicle detection is not needed
                        com.example.vehiclerecognition.data.models.VehicleSegmentationResult(
                            detections = emptyList(),
                            performance = emptyMap(),
                            rawOutputLog = "Skipped (not needed for mode: $currentDetectionMode)"
                        )
                    }
                }

                // Wait for both detection processes to complete
                val lpResult = lpResultDeferred.await()
                val vehicleResult = vehicleResultDeferred.await()

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
                
                // Perform comprehensive watchlist matching based on current detection mode
                performWatchlistMatching(currentDetectionMode)

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
     * Performs comprehensive watchlist matching based on current detections and detection mode
     */
    private fun performWatchlistMatching(detectionMode: DetectionMode) {
        watchlistMatchingJob?.cancel()
        watchlistMatchingJob = viewModelScope.launch {
            try {
                val currentTime = System.currentTimeMillis()
                val currentPlates = _detectedPlates.value
                val currentVehicles = _detectedVehicles.value
                
                Log.d("CameraViewModel", "=== WATCHLIST MATCHING DEBUG ===")
                Log.d("CameraViewModel", "Detection Mode: $detectionMode")
                Log.d("CameraViewModel", "Include Secondary Color: ${settingsRepository.includeSecondaryColor.value}")
                Log.d("CameraViewModel", "Plates: ${currentPlates.size}, Vehicles: ${currentVehicles.size}")
                Log.d("CameraViewModel", "=== VEHICLE DETAILS ===")
                currentVehicles.forEachIndexed { index, vehicle ->
                    Log.d("CameraViewModel", "Vehicle $index: ID=${vehicle.id}, Primary=${vehicle.detectedColor?.name}, Secondary=${vehicle.secondaryColor?.name}, Type=${vehicle.className}")
                }
                Log.d("CameraViewModel", "=== END VEHICLE DETAILS ===")
                
                Log.d("CameraViewModel", "Performing watchlist matching - Mode: $detectionMode, Plates: ${currentPlates.size}, Vehicles: ${currentVehicles.size}")
                
                // Debug: Show current watchlist (country-specific)
                val currentCountry = _currentSettings.value.selectedCountry
                val watchlist = watchlistRepository.getEntriesByCountry(currentCountry).first()
                Log.d("CameraViewModel", "Current watchlist for ${currentCountry.displayName} (${watchlist.size} entries):")
                watchlist.forEach { entry ->
                    Log.d("CameraViewModel", "  - LP: ${entry.licensePlate}, Color: ${entry.vehicleColor}, Type: ${entry.vehicleType}, Country: ${entry.country.displayName}")
                }
                
                // Clear previous matches
                val matchedPlates = mutableSetOf<String>()
                val matchedVehicles = mutableSetOf<String>()
                
                val hasMatch = when (detectionMode) {
                    DetectionMode.LP_ONLY -> {
                        Log.d("CameraViewModel", "Matching mode: LICENSE_PLATE_ONLY - checking plates only")
                        checkLicensePlateOnlyMatches(currentPlates, matchedPlates)
                    }
                    DetectionMode.COLOR_ONLY -> {
                        Log.d("CameraViewModel", "Matching mode: COLOR_ONLY - checking vehicle colors (including secondary if enabled)")
                        checkColorOnlyMatches(currentVehicles, matchedVehicles)
                    }
                    DetectionMode.COLOR_TYPE -> {
                        Log.d("CameraViewModel", "Matching mode: COLOR_TYPE - checking vehicle colors+types (including secondary if enabled)")
                        checkColorTypeMatches(currentVehicles, matchedVehicles)
                    }
                    DetectionMode.LP_COLOR -> {
                        Log.d("CameraViewModel", "Matching mode: LICENSE_PLATE_COLOR - checking plate+color combinations")
                        checkLicensePlateColorMatches(currentPlates, currentVehicles, matchedPlates, matchedVehicles)
                    }
                    DetectionMode.LP_TYPE -> {
                        Log.d("CameraViewModel", "Matching mode: LICENSE_PLATE_TYPE - checking plate+type combinations")
                        checkLicensePlateTypeMatches(currentPlates, currentVehicles, matchedPlates, matchedVehicles)
                    }
                    DetectionMode.LP_COLOR_TYPE -> {
                        Log.d("CameraViewModel", "Matching mode: LICENSE_PLATE_COLOR_TYPE - checking plate+color+type combinations")
                        checkLicensePlateColorTypeMatches(currentPlates, currentVehicles, matchedPlates, matchedVehicles)
                    }
                }
                
                // Update matched detection IDs
                _matchedPlateIds.value = matchedPlates
                _matchedVehicleIds.value = matchedVehicles
                
                Log.d("CameraViewModel", "=== MATCHING RESULTS ===")
                Log.d("CameraViewModel", "hasMatch: $hasMatch")
                Log.d("CameraViewModel", "matchedPlates: $matchedPlates")
                Log.d("CameraViewModel", "matchedVehicles: $matchedVehicles")
                Log.d("CameraViewModel", "StateFlow _matchedPlateIds: ${_matchedPlateIds.value}")
                Log.d("CameraViewModel", "StateFlow _matchedVehicleIds: ${_matchedVehicleIds.value}")
                Log.d("CameraViewModel", "=== END MATCHING RESULTS ===")
                
                Log.d("CameraViewModel", "Updated StateFlows - matchedPlates: $matchedPlates, matchedVehicles: $matchedVehicles")
                Log.d("CameraViewModel", "Current StateFlow values - _matchedPlateIds: ${_matchedPlateIds.value}, _matchedVehicleIds: ${_matchedVehicleIds.value}")
                
                if (hasMatch) {
                    // Always update visual state (green boxes) regardless of alert cooldown
                    _matchFound.value = true
                    
                    // Only trigger sound alert if not in cooldown
                    val timeSinceLastAlert = currentTime - lastAlertTime
                    val shouldPlaySound = timeSinceLastAlert >= alertCooldownMs
                    
                    Log.d("CameraViewModel", "=== SOUND ALERT DECISION ===")
                    Log.d("CameraViewModel", "Match found: true")
                    Log.d("CameraViewModel", "Current time: $currentTime")
                    Log.d("CameraViewModel", "Last alert time: $lastAlertTime") 
                    Log.d("CameraViewModel", "Time since last alert: ${timeSinceLastAlert}ms")
                    Log.d("CameraViewModel", "Cooldown period: ${alertCooldownMs}ms")
                    Log.d("CameraViewModel", "Should play sound: $shouldPlaySound")
                    Log.d("CameraViewModel", "=== END SOUND ALERT DECISION ===")
                    
                    if (shouldPlaySound) {
                        triggerWatchlistAlert()
                        Log.d("CameraViewModel", "WATCHLIST MATCH FOUND! Triggering sound alert")
                    } else {
                        Log.d("CameraViewModel", "WATCHLIST MATCH FOUND! Sound alert skipped (in cooldown: ${timeSinceLastAlert}ms < ${alertCooldownMs}ms), but visual state maintained")
                    }
                    
                    // Auto-hide visual match state after 10 seconds if no new matches
                    hideMatchFoundJob?.cancel()
                    hideMatchFoundJob = viewModelScope.launch {
                        delay(10000) // 10 seconds
                        // Only hide if there are currently no matches (avoid hiding active matches)
                        val currentDetectionMode = settingsRepository.detectionMode.value
                        val currentPlatesForCheck = _detectedPlates.value
                        val currentVehiclesForCheck = _detectedVehicles.value
                        
                        // Quick re-check if there are still active matches
                        val stillHasMatches = when (currentDetectionMode) {
                            DetectionMode.LP_ONLY -> currentPlatesForCheck.any { plate ->
                                plate.recognizedText?.let { plateText ->
                                    val detectedVehicle = VehicleMatcher.DetectedVehicle(licensePlate = plateText)
                                    vehicleMatcher.findMatch(detectedVehicle, DetectionMode.LP_ONLY, currentCountry)
                                } ?: false
                            }
                            else -> {
                                // For other modes, just check if we still have matched IDs
                                _matchedPlateIds.value.isNotEmpty() || _matchedVehicleIds.value.isNotEmpty()
                            }
                        }
                        
                        if (!stillHasMatches) {
                            _matchFound.value = false
                            _matchedPlateIds.value = emptySet()
                            _matchedVehicleIds.value = emptySet()
                            Log.d("CameraViewModel", "Visual match state auto-hidden after 10 seconds")
                        } else {
                            Log.d("CameraViewModel", "Visual match state maintained - still have active matches")
                        }
                    }
                } else {
                    // Clear current detection tracking, but DON'T immediately clear visual match state
                    // Let the 10-second auto-hide timer handle clearing the visual state
                    _matchedPlateIds.value = emptySet()
                    _matchedVehicleIds.value = emptySet()
                    // DO NOT clear _matchFound.value here - let the 10-second timer handle it
                    Log.d("CameraViewModel", "No current matches found - cleared detection tracking, but keeping visual state for timer")
                }
                
            } catch (e: Exception) {
                Log.e("CameraViewModel", "Error during watchlist matching", e)
                // Clear matches on error
                _matchedPlateIds.value = emptySet()
                _matchedVehicleIds.value = emptySet()
            }
        }
    }

    /**
     * Triggers the watchlist match sound alert only
     */
    private fun triggerWatchlistAlert() {
        val currentTime = System.currentTimeMillis()
        
        // Double-check cooldown (should already be checked before calling this function)
        if (currentTime - lastAlertTime < alertCooldownMs) {
            Log.d("CameraViewModel", "Sound alert skipped - still in cooldown period (${currentTime - lastAlertTime}ms < ${alertCooldownMs}ms)")
            return
        }
        
        // Update the last alert time BEFORE playing the sound to prevent race conditions
        lastAlertTime = currentTime
        
        Log.d("CameraViewModel", "PLAYING SOUND ALERT - watchlist match detected! Time since last alert: ${currentTime - (lastAlertTime - alertCooldownMs)}ms")
        
        // Play sound alert
        try {
            soundAlertPlayer.playAlert()
            Log.d("CameraViewModel", "Sound alert successfully triggered")
        } catch (e: Exception) {
            Log.e("CameraViewModel", "Error playing sound alert", e)
        }
    }

    /**
     * Check license plate only matches
     */
    private suspend fun checkLicensePlateOnlyMatches(plates: List<PlateDetection>, matchedPlates: MutableSet<String>): Boolean {
        if (plates.isEmpty()) return false
        
        val currentCountry = _currentSettings.value.selectedCountry
        var hasAnyMatch = false
        
        for (plate in plates) {
            plate.recognizedText?.let { plateText ->
                val detectedVehicle = VehicleMatcher.DetectedVehicle(licensePlate = plateText)
                if (vehicleMatcher.findMatch(
                        detectedVehicle,
                        DetectionMode.LP_ONLY,
                        currentCountry,
                        _currentSettings.value.enablePlateCandidateGeneration
                    )) {
                    Log.d("CameraViewModel", "LP-only match found: $plateText")
                    matchedPlates.add(plateText)
                    hasAnyMatch = true
                }
            }
        }
        
        return hasAnyMatch
    }

    /**
     * Check color only matches
     */
    private suspend fun checkColorOnlyMatches(vehicles: List<VehicleDetection>, matchedVehicles: MutableSet<String>): Boolean {
        if (vehicles.isEmpty()) return false
        
        val includeSecondary = settingsRepository.includeSecondaryColor.value
        var hasAnyMatch = false
        
        Log.d("CameraViewModel", "checkColorOnlyMatches: includeSecondary=$includeSecondary, vehicles=${vehicles.size}")
        
        for (vehicle in vehicles) {
            Log.d("CameraViewModel", "Checking vehicle ${vehicle.id}: Primary=${vehicle.detectedColor?.name}, Secondary=${vehicle.secondaryColor?.name}")
            
            // Check primary color
            vehicle.detectedColor?.let { color ->
                val detectedVehicle = VehicleMatcher.DetectedVehicle(color = color)
                val currentCountry = _currentSettings.value.selectedCountry
                Log.d("CameraViewModel", "About to check primary color $color against watchlist for vehicle ${vehicle.id}")
                if (vehicleMatcher.findMatch(detectedVehicle, DetectionMode.COLOR_ONLY, currentCountry)) {
                    Log.d("CameraViewModel", "Color-only match found (primary): $color for vehicle ${vehicle.id}")
                    matchedVehicles.add(vehicle.id)
                    hasAnyMatch = true
                    Log.d("CameraViewModel", "Added vehicle ${vehicle.id} to matched set. Current set: $matchedVehicles")
                } else {
                    Log.d("CameraViewModel", "No match for primary color $color for vehicle ${vehicle.id}")
                }
            }
            
            // Check secondary color if enabled
            if (includeSecondary) {
                vehicle.secondaryColor?.let { color ->
                    val detectedVehicle = VehicleMatcher.DetectedVehicle(color = color)
                    val currentCountry = _currentSettings.value.selectedCountry
                    Log.d("CameraViewModel", "About to check secondary color $color against watchlist for vehicle ${vehicle.id}")
                    if (vehicleMatcher.findMatch(detectedVehicle, DetectionMode.COLOR_ONLY, currentCountry)) {
                        Log.d("CameraViewModel", "Color-only match found (secondary): $color for vehicle ${vehicle.id}")
                        matchedVehicles.add(vehicle.id)
                        hasAnyMatch = true
                        Log.d("CameraViewModel", "Added vehicle ${vehicle.id} to matched set (secondary). Current set: $matchedVehicles")
                    } else {
                        Log.d("CameraViewModel", "No match for secondary color $color for vehicle ${vehicle.id}")
                    }
                } ?: Log.d("CameraViewModel", "Vehicle ${vehicle.id} has no secondary color")
            } else {
                Log.d("CameraViewModel", "Secondary color checking disabled")
            }
        }
        
        Log.d("CameraViewModel", "checkColorOnlyMatches result: hasAnyMatch=$hasAnyMatch, final matched set: $matchedVehicles")
        return hasAnyMatch
    }

    /**
     * Check color + type matches
     */
    private suspend fun checkColorTypeMatches(vehicles: List<VehicleDetection>, matchedVehicles: MutableSet<String>): Boolean {
        if (vehicles.isEmpty()) return false
        
        val currentCountry = _currentSettings.value.selectedCountry
        val includeSecondary = settingsRepository.includeSecondaryColor.value
        var hasAnyMatch = false
        
        Log.d("CameraViewModel", "checkColorTypeMatches: includeSecondary=$includeSecondary, vehicles=${vehicles.size}")
        
        for (vehicle in vehicles) {
            val type = convertClassIdToVehicleType(vehicle.classId)
            
            Log.d("CameraViewModel", "Checking vehicle ${vehicle.id}: Primary=${vehicle.detectedColor?.name}, Secondary=${vehicle.secondaryColor?.name}, Type=$type")
            
            if (type != null) {
                // Check primary color + type
                vehicle.detectedColor?.let { color ->
                    val detectedVehicle = VehicleMatcher.DetectedVehicle(color = color, type = type)
                    if (vehicleMatcher.findMatch(detectedVehicle, DetectionMode.COLOR_TYPE, currentCountry)) {
                        Log.d("CameraViewModel", "Color+Type match found (primary): $color + $type for vehicle ${vehicle.id}")
                        matchedVehicles.add(vehicle.id)
                        hasAnyMatch = true
                        Log.d("CameraViewModel", "Added vehicle ${vehicle.id} to matched set (primary color+type). Current set: $matchedVehicles")
                    }
                }
                
                // Check secondary color + type if enabled
                if (includeSecondary) {
                    vehicle.secondaryColor?.let { color ->
                        val detectedVehicle = VehicleMatcher.DetectedVehicle(color = color, type = type)
                        if (vehicleMatcher.findMatch(detectedVehicle, DetectionMode.COLOR_TYPE, currentCountry)) {
                            Log.d("CameraViewModel", "Color+Type match found (secondary): $color + $type for vehicle ${vehicle.id}")
                            matchedVehicles.add(vehicle.id)
                            hasAnyMatch = true
                            Log.d("CameraViewModel", "Added vehicle ${vehicle.id} to matched set (secondary color+type). Current set: $matchedVehicles")
                        } else {
                            Log.d("CameraViewModel", "No match for secondary color+type $color + $type for vehicle ${vehicle.id}")
                        }
                    } ?: Log.d("CameraViewModel", "Vehicle ${vehicle.id} has no secondary color for type matching")
                } else {
                    Log.d("CameraViewModel", "Secondary color+type checking disabled")
                }
            } else {
                Log.d("CameraViewModel", "Vehicle ${vehicle.id} has unknown type (classId=${vehicle.classId})")
            }
        }
        
        Log.d("CameraViewModel", "checkColorTypeMatches result: hasAnyMatch=$hasAnyMatch, final matched set: $matchedVehicles")
        return hasAnyMatch
    }

    /**
     * Check license plate + color matches (must belong to same vehicle)
     */
    private suspend fun checkLicensePlateColorMatches(plates: List<PlateDetection>, vehicles: List<VehicleDetection>, matchedPlates: MutableSet<String>, matchedVehicles: MutableSet<String>): Boolean {
        if (plates.isEmpty() || vehicles.isEmpty()) return false
        
        val currentCountry = _currentSettings.value.selectedCountry
        val includeSecondary = settingsRepository.includeSecondaryColor.value
        var hasAnyMatch = false
        
        for (plate in plates) {
            plate.vehicleId?.let { vehicleId ->
                // Find the vehicle with matching ID
                val matchingVehicle = vehicles.find { it.id == vehicleId }
                matchingVehicle?.let { vehicle ->
                    val plateText = plate.recognizedText
                    
                    if (plateText != null) {
                        // Check primary color + license plate
                        vehicle.detectedColor?.let { color ->
                            val detectedVehicle = VehicleMatcher.DetectedVehicle(
                                licensePlate = plateText,
                                color = color
                            )
                            if (vehicleMatcher.findMatch(
                                    detectedVehicle,
                                    DetectionMode.LP_COLOR,
                                    currentCountry,
                                    _currentSettings.value.enablePlateCandidateGeneration
                                )) {
                                Log.d("CameraViewModel", "LP+Color match found (primary): $plateText + $color (Vehicle: $vehicleId)")
                                matchedPlates.add(plateText)
                                matchedVehicles.add(vehicle.id)
                                hasAnyMatch = true
                            }
                        }
                        
                        // Check secondary color + license plate if enabled
                        if (includeSecondary) {
                            vehicle.secondaryColor?.let { color ->
                                val detectedVehicle = VehicleMatcher.DetectedVehicle(
                                    licensePlate = plateText,
                                    color = color
                                )
                                if (vehicleMatcher.findMatch(
                                        detectedVehicle,
                                        DetectionMode.LP_COLOR,
                                        currentCountry,
                                        _currentSettings.value.enablePlateCandidateGeneration
                                    )) {
                                    Log.d("CameraViewModel", "LP+Color match found (secondary): $plateText + $color (Vehicle: $vehicleId)")
                                    matchedPlates.add(plateText)
                                    matchedVehicles.add(vehicle.id)
                                    hasAnyMatch = true
                                }
                            }
                        }
                    }
                }
            }
        }
        
        return hasAnyMatch
    }

    /**
     * Check license plate + type matches (must belong to same vehicle)
     */
    private suspend fun checkLicensePlateTypeMatches(plates: List<PlateDetection>, vehicles: List<VehicleDetection>, matchedPlates: MutableSet<String>, matchedVehicles: MutableSet<String>): Boolean {
        if (plates.isEmpty() || vehicles.isEmpty()) return false
        
        val currentCountry = _currentSettings.value.selectedCountry
        var hasAnyMatch = false
        
        for (plate in plates) {
            plate.vehicleId?.let { vehicleId ->
                // Find the vehicle with matching ID
                val matchingVehicle = vehicles.find { it.id == vehicleId }
                matchingVehicle?.let { vehicle ->
                    val plateText = plate.recognizedText
                    val type = convertClassIdToVehicleType(vehicle.classId)
                    
                    if (plateText != null && type != null) {
                        val detectedVehicle = VehicleMatcher.DetectedVehicle(
                            licensePlate = plateText,
                            type = type
                        )
                        if (vehicleMatcher.findMatch(
                                detectedVehicle,
                                DetectionMode.LP_TYPE,
                                currentCountry,
                                _currentSettings.value.enablePlateCandidateGeneration
                            )) {
                            Log.d("CameraViewModel", "LP+Type match found: $plateText + $type (Vehicle: $vehicleId)")
                            matchedPlates.add(plateText)
                            matchedVehicles.add(vehicle.id)
                            hasAnyMatch = true
                        }
                    }
                }
            }
        }
        
        return hasAnyMatch
    }

    /**
     * Check license plate + color + type matches (must belong to same vehicle)
     */
    private suspend fun checkLicensePlateColorTypeMatches(plates: List<PlateDetection>, vehicles: List<VehicleDetection>, matchedPlates: MutableSet<String>, matchedVehicles: MutableSet<String>): Boolean {
        if (plates.isEmpty() || vehicles.isEmpty()) return false
        
        val currentCountry = _currentSettings.value.selectedCountry
        val includeSecondary = settingsRepository.includeSecondaryColor.value
        var hasAnyMatch = false
        
        for (plate in plates) {
            plate.vehicleId?.let { vehicleId ->
                // Find the vehicle with matching ID
                val matchingVehicle = vehicles.find { it.id == vehicleId }
                matchingVehicle?.let { vehicle ->
                    val plateText = plate.recognizedText
                    val type = convertClassIdToVehicleType(vehicle.classId)
                    
                    if (plateText != null && type != null) {
                        // Check primary color + license plate + type
                        vehicle.detectedColor?.let { color ->
                            val detectedVehicle = VehicleMatcher.DetectedVehicle(
                                licensePlate = plateText,
                                color = color,
                                type = type
                            )
                            if (vehicleMatcher.findMatch(
                                    detectedVehicle,
                                    DetectionMode.LP_COLOR_TYPE,
                                    currentCountry,
                                    _currentSettings.value.enablePlateCandidateGeneration
                                )) {
                                Log.d("CameraViewModel", "LP+Color+Type match found (primary): $plateText + $color + $type (Vehicle: $vehicleId)")
                                matchedPlates.add(plateText)
                                matchedVehicles.add(vehicle.id)
                                hasAnyMatch = true
                            }
                        }
                        
                        // Check secondary color + license plate + type if enabled
                        if (includeSecondary) {
                            vehicle.secondaryColor?.let { color ->
                                val detectedVehicle = VehicleMatcher.DetectedVehicle(
                                    licensePlate = plateText,
                                    color = color,
                                    type = type
                                )
                                if (vehicleMatcher.findMatch(
                                        detectedVehicle,
                                        DetectionMode.LP_COLOR_TYPE,
                                        currentCountry,
                                        _currentSettings.value.enablePlateCandidateGeneration
                                    )) {
                                    Log.d("CameraViewModel", "LP+Color+Type match found (secondary): $plateText + $color + $type (Vehicle: $vehicleId)")
                                    matchedPlates.add(plateText)
                                    matchedVehicles.add(vehicle.id)
                                    hasAnyMatch = true
                                }
                            }
                        }
                    }
                }
            }
        }
        
        return hasAnyMatch
    }

    /**
     * Converts vehicle class ID to VehicleType enum
     */
    private fun convertClassIdToVehicleType(classId: Int): VehicleType? {
        return when (classId) {
            2 -> VehicleType.CAR
            3 -> VehicleType.MOTORCYCLE  
            7 -> VehicleType.TRUCK
            else -> null
        }
    }

    /**
     * Determines if license plate detection is needed based on the current detection mode
     */
    private fun needsLicensePlateDetection(mode: DetectionMode): Boolean {
        return when (mode) {
            DetectionMode.LP_ONLY,           // License Plate only
            DetectionMode.LP_COLOR,     // License Plate + Color
            DetectionMode.LP_TYPE,      // License Plate + Type
            DetectionMode.LP_COLOR_TYPE // License Plate + Color + Type
            -> true
            DetectionMode.COLOR_TYPE,   // Color + Type (no LP needed)
            DetectionMode.COLOR_ONLY         // Color only (no LP needed)
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
            DetectionMode.COLOR_ONLY         // Color only
            -> true
            DetectionMode.LP_ONLY            // License Plate only (no vehicle detection needed)
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
            val currentDetectionMode = settingsRepository.detectionMode.value
            val currentCountry = _currentSettings.value.selectedCountry
                val isMatch = vehicleMatcher.findMatch(
                    detectedVehicle,
                    currentDetectionMode,
                    currentCountry,
                    _currentSettings.value.enablePlateCandidateGeneration
                )
            
            if (isMatch) {
                Log.d("CameraViewModel", "MATCH FOUND based on mode $currentDetectionMode!")
                
                // Use the same timing logic as performWatchlistMatching
                val currentTime = System.currentTimeMillis()
                val timeSinceLastAlert = currentTime - lastAlertTime
                val shouldPlaySound = timeSinceLastAlert >= alertCooldownMs
                
                _matchFound.value = true
                
                if (shouldPlaySound) {
                    triggerWatchlistAlert()
                    Log.d("CameraViewModel", "Sound alert triggered from processDetection")
                } else {
                    Log.d("CameraViewModel", "Sound alert skipped from processDetection (cooldown: ${timeSinceLastAlert}ms < ${alertCooldownMs}ms)")
                }
                
                // Start 10-second auto-hide timer
                hideMatchFoundJob?.cancel()
                hideMatchFoundJob = viewModelScope.launch {
                    delay(10000) // 10 seconds
                    _matchFound.value = false
                    Log.d("CameraViewModel", "Visual match state auto-hidden after 10 seconds (from processDetection)")
                }
            } else {
                Log.d("CameraViewModel", "No match found for mode $currentDetectionMode.")
                // Don't immediately clear _matchFound - let timer handle it
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
    
    /**
     * Completes first-time setup with selected country
     */
    fun completeFirstTimeSetup(selectedCountry: Country) {
        viewModelScope.launch {
            try {
                Log.d("CameraViewModel", "Starting first-time setup with country: ${selectedCountry.displayName}")
                
                // Update the country setting
                val currentSettings = licensePlateSettings.first()
                val updatedSettings = currentSettings.copy(selectedCountry = selectedCountry)
                Log.d("CameraViewModel", "Current country: ${currentSettings.selectedCountry.displayName}, updating to: ${selectedCountry.displayName}")
                
                licensePlateRepository.updateSettings(updatedSettings)
                
                // DON'T manually reinitialize here - let the automatic flow handle it
                // The settings change will trigger reinitialization in the combine flow
                Log.d("CameraViewModel", "Settings updated, automatic reinitialization will be triggered by settings change")
                
                // Mark first-time setup as completed
                licensePlateRepository.completeFirstTimeSetup()
                
                Log.d("CameraViewModel", "First-time setup completed successfully with country: ${selectedCountry.displayName}")
            } catch (e: Exception) {
                Log.e("CameraViewModel", "Error completing first-time setup", e)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        hideMatchFoundJob?.cancel() // Ensure the job is cancelled if ViewModel is cleared
        frameProcessingJob?.cancel() // Cancel frame processing job
        detectionHideJob?.cancel() // Cancel detection hide job
        soundAlertJob?.cancel() // Cancel sound alert job
        watchlistMatchingJob?.cancel() // Cancel watchlist matching job
        
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