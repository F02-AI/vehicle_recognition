package com.example.vehiclerecognition.ml.processors

import android.graphics.Bitmap
import com.example.vehiclerecognition.data.models.LicensePlateSettings
import com.example.vehiclerecognition.data.models.VehicleDetection
import com.example.vehiclerecognition.data.models.VehicleSegmentationResult
import com.example.vehiclerecognition.ml.detection.VehicleSegmentationDetector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Main processor for vehicle segmentation detection
 * Coordinates the vehicle detection pipeline with coordinate transforms
 */
@Singleton
class VehicleSegmentationProcessor @Inject constructor(
    private val vehicleSegmentationDetector: VehicleSegmentationDetector
) {
    
    private val _detectedVehicles = MutableStateFlow<List<VehicleDetection>>(emptyList())
    val detectedVehicles: StateFlow<List<VehicleDetection>> = _detectedVehicles.asStateFlow()
    
    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()
    
    private var isInitialized = false
    
    suspend fun initialize(settings: LicensePlateSettings): Boolean = withContext(Dispatchers.IO) {
        try {
            val detectorReady = vehicleSegmentationDetector.initialize(settings)
            isInitialized = detectorReady
            isInitialized
        } catch (e: Exception) {
            isInitialized = false
            false
        }
    }
    
    /**
     * Reinitializes the detector with new settings (for GPU acceleration changes)
     */
    suspend fun reinitializeDetector(settings: LicensePlateSettings): Boolean = withContext(Dispatchers.IO) {
        try {
            // Reset initialization state before reinitializing
            isInitialized = false
            
            // Clear any existing state
            _detectedVehicles.value = emptyList()
            _isProcessing.value = false
            
            // Reinitialize the detector
            val detectorReady = vehicleSegmentationDetector.initialize(settings)
            
            // Update the initialization flag based on success
            isInitialized = detectorReady
            
            return@withContext detectorReady
        } catch (e: Exception) {
            isInitialized = false
            false
        }
    }
    
    /**
     * Process frame for vehicle segmentation detection
     * Handles coordinate transforms between camera/video space and display space
     */
    suspend fun processFrame(
        bitmap: Bitmap,
        settings: LicensePlateSettings
    ): VehicleSegmentationResult = withContext(Dispatchers.Default) {
        
        if (!isInitialized) {
            android.util.Log.d("VehicleSegProc", "Vehicle segmentation processor not initialized - skipping detection")
            return@withContext VehicleSegmentationResult(emptyList(), emptyMap(), "Vehicle processor not initialized")
        }
        
        android.util.Log.d("VehicleSegProc", "Processing vehicle detection on frame: ${bitmap.width}x${bitmap.height}")
        _isProcessing.value = true
        
        try {
            // Step 1: Detect vehicles using YOLO11 segmentation model
            val detectorResult = vehicleSegmentationDetector.detectVehicles(bitmap)
            val vehicleDetections = detectorResult.detections
            val performanceStats = detectorResult.performance
            val rawOutputLog = detectorResult.rawOutputLog
            
            android.util.Log.d("VehicleSegProc", "Vehicle detection completed: ${vehicleDetections.size} vehicles found")
            vehicleDetections.forEachIndexed { index, vehicle ->
                android.util.Log.d("VehicleSegProc", "Vehicle $index: ${vehicle.className} at ${vehicle.boundingBox} (conf: ${vehicle.confidence})")
            }
            
            // Step 2: Update state with latest detections
            _detectedVehicles.value = vehicleDetections
            
            return@withContext VehicleSegmentationResult(vehicleDetections, performanceStats, rawOutputLog)
            
        } catch (e: Exception) {
            return@withContext VehicleSegmentationResult(emptyList(), emptyMap(), "Error in vehicle processor")
        } finally {
            _isProcessing.value = false
        }
    }
    
    /**
     * Transform vehicle detection coordinates from original image space to display space
     * This is important for correct overlay rendering in both camera and video modes
     */
    fun transformDetectionsToDisplaySpace(
        detections: List<VehicleDetection>,
        originalWidth: Int,
        originalHeight: Int,
        displayWidth: Int,
        displayHeight: Int,
        rotation: Int = 0
    ): List<VehicleDetection> {
        
        if (detections.isEmpty()) return detections
        
        val scaleX = displayWidth.toFloat() / originalWidth.toFloat()
        val scaleY = displayHeight.toFloat() / originalHeight.toFloat()
        
        return detections.map { detection ->
            val originalBox = detection.boundingBox
            
            // Apply coordinate transformation based on rotation and scaling
            val transformedBox = when (rotation) {
                90 -> {
                    // 90 degree rotation: swap coordinates and adjust
                    android.graphics.RectF(
                        originalBox.top * scaleX,
                        (originalWidth - originalBox.right) * scaleY,
                        originalBox.bottom * scaleX,
                        (originalWidth - originalBox.left) * scaleY
                    )
                }
                180 -> {
                    // 180 degree rotation: flip both axes
                    android.graphics.RectF(
                        (originalWidth - originalBox.right) * scaleX,
                        (originalHeight - originalBox.bottom) * scaleY,
                        (originalWidth - originalBox.left) * scaleX,
                        (originalHeight - originalBox.top) * scaleY
                    )
                }
                270 -> {
                    // 270 degree rotation: swap coordinates and adjust
                    android.graphics.RectF(
                        (originalHeight - originalBox.bottom) * scaleX,
                        originalBox.left * scaleY,
                        (originalHeight - originalBox.top) * scaleX,
                        originalBox.right * scaleY
                    )
                }
                else -> {
                    // No rotation: just scale
                    android.graphics.RectF(
                        originalBox.left * scaleX,
                        originalBox.top * scaleY,
                        originalBox.right * scaleX,
                        originalBox.bottom * scaleY
                    )
                }
            }
            
            detection.copy(boundingBox = transformedBox)
        }
    }
    
    fun release() {
        vehicleSegmentationDetector.release()
        isInitialized = false
    }
} 