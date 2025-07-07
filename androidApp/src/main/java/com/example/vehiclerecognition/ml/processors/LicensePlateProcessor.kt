package com.example.vehiclerecognition.ml.processors

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.RectF
import com.example.vehiclerecognition.data.models.LicensePlateSettings
import com.example.vehiclerecognition.data.models.OcrModelType
import com.example.vehiclerecognition.data.models.PlateDetection
import com.example.vehiclerecognition.ml.detection.LicensePlateDetector
import com.example.vehiclerecognition.ml.ocr.FastPlateOcrEngine
import com.example.vehiclerecognition.ml.ocr.MLKitOcrEngine
import com.example.vehiclerecognition.ml.ocr.OcrEngine
import com.example.vehiclerecognition.ml.ocr.PaddleOcrEngine
import com.example.vehiclerecognition.ml.ocr.TesseractOcrEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Data class to hold the final processing result, including detections and performance stats.
 */
data class ProcessorResult(
    val detections: List<PlateDetection>,
    val performance: Map<String, Long>,
    val rawOutputLog: String
)

/**
 * Main processor for license plate detection and OCR
 * Coordinates the detection and OCR pipeline similar to Python script's DetectionWorker
 */
@Singleton
class LicensePlateProcessor @Inject constructor(
    private val licensePlateDetector: LicensePlateDetector,
    private val fastPlateOcrEngine: FastPlateOcrEngine,
    private val mlKitOcrEngine: MLKitOcrEngine,
    private val tesseractOcrEngine: TesseractOcrEngine,
    private val paddleOcrEngine: PaddleOcrEngine
) {
    
    companion object {
        // ML Kit OCR recommended resolution guidelines
        // Based on ML Kit documentation: https://developers.google.com/ml-kit/vision/text-recognition/v2/android
        private const val MIN_CHAR_SIZE_PX = 16 // Minimum 16x16 pixels per character
        private const val MAX_CHAR_SIZE_PX = 24 // Maximum 24x24 pixels per character (no benefit beyond this)
        private const val OPTIMAL_HEIGHT_MIN = 32 // Minimum height for license plate OCR
        private const val OPTIMAL_HEIGHT_MAX = 128 // Maximum height for optimal performance
        private const val OPTIMAL_WIDTH_MIN = 128 // Minimum width for license plate OCR  
        private const val OPTIMAL_WIDTH_MAX = 512 // Maximum width for optimal performance
    }
    
    private val _detectedPlates = MutableStateFlow<List<PlateDetection>>(emptyList())
    val detectedPlates: StateFlow<List<PlateDetection>> = _detectedPlates.asStateFlow()
    
    private val _latestRecognizedText = MutableStateFlow<String?>(null)
    val latestRecognizedText: StateFlow<String?> = _latestRecognizedText.asStateFlow()
    
    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()
    
    private var isInitialized = false
    
    suspend fun initialize(settings: LicensePlateSettings): Boolean = withContext(Dispatchers.IO) {
        try {
            val detectorReady = licensePlateDetector.initialize(settings)
            // Initialize OCR engines with GPU acceleration settings
            val fastPlateReady = fastPlateOcrEngine.initialize(settings)
            val mlKitReady = mlKitOcrEngine.initialize(settings)
            val tesseractReady = tesseractOcrEngine.initialize(settings)
            val paddleReady = paddleOcrEngine.initialize(settings)
            
            isInitialized = detectorReady && fastPlateReady && mlKitReady && tesseractReady && paddleReady
            isInitialized
        } catch (e: Exception) {
            isInitialized = false
            false
        }
    }
    
    /**
     * Reinitializes the detector and OCR engines with new settings (for GPU acceleration changes)
     */
    suspend fun reinitializeDetector(settings: LicensePlateSettings): Boolean = withContext(Dispatchers.IO) {
        try {
            // Reset initialization state before reinitializing
            isInitialized = false
            
            // Clear any existing state
            _detectedPlates.value = emptyList()
            _latestRecognizedText.value = null
            _isProcessing.value = false
            
            // Reinitialize both detector and OCR engines for GPU acceleration changes
            val detectorReady = licensePlateDetector.initialize(settings)
            val fastPlateReady = fastPlateOcrEngine.initialize(settings)
            val mlKitReady = mlKitOcrEngine.initialize(settings)
            val tesseractReady = tesseractOcrEngine.initialize(settings)
            val paddleReady = paddleOcrEngine.initialize(settings)
            
            val allReady = detectorReady && fastPlateReady && mlKitReady && tesseractReady && paddleReady
            
            // Update the initialization flag based on success
            isInitialized = allReady
            
            return@withContext allReady
        } catch (e: Exception) {
            isInitialized = false
            false
        }
    }
    
    /**
     * Process frame for license plate detection and OCR
     * Enhanced with intelligent image scaling for optimal OCR accuracy
     */
    suspend fun processFrame(
        bitmap: Bitmap,
        settings: LicensePlateSettings
    ): ProcessorResult = withContext(Dispatchers.Default) {
        
        if (!isInitialized) return@withContext ProcessorResult(emptyList(), emptyMap(), "")
        
        _isProcessing.value = true
        
        try {
            // Step 1: Detect license plates using YOLO model
            val detectorResult = licensePlateDetector.detectLicensePlates(bitmap)
            val detectionResults = detectorResult.detections
            val performanceStats = detectorResult.performance
            val rawOutputLog = detectorResult.rawOutputLog
            
            // Step 2: Convert detection results to PlateDetection objects
            val plateDetections = detectionResults.map { detection ->
                PlateDetection(
                    boundingBox = detection.boundingBox,
                    confidence = detection.confidence
                )
            }
            
            // Step 3: Process OCR on ALL detections in parallel if OCR is enabled
            if (plateDetections.isNotEmpty() && settings.enableOcr) {
                // Get the appropriate OCR engine
                val ocrEngine = getOcrEngine(settings.selectedOcrModel)
                
                if (ocrEngine?.isReady() == true) {
                    // Process all detections in parallel using async
                    val ocrJobs = plateDetections.map { detection ->
                        async {
                            // Crop and scale each license plate region
                            val optimizedCroppedBitmap = cropAndScaleLicensePlateForOCR(bitmap, detection.boundingBox)
                            
                            if (optimizedCroppedBitmap != null) {
                                try {
                                    val ocrResult = ocrEngine.processImage(optimizedCroppedBitmap)
                                    
                                    // Return updated detection with OCR results
                                    detection.copy(
                                        recognizedText = ocrResult.formattedText ?: ocrResult.text,
                                        isValidFormat = ocrResult.isValidFormat,
                                        processingTimeMs = ocrResult.processingTimeMs
                                    )
                                } catch (e: Exception) {
                                    // If OCR fails for this plate, return original detection
                                    detection
                                }
                            } else {
                                // If cropping fails, return original detection
                                detection
                            }
                        }
                    }
                    
                    // Wait for all OCR jobs to complete
                    val updatedDetections = ocrJobs.awaitAll()
                    
                    // Update latest recognized text with the best valid result
                    val bestValidText = updatedDetections
                        .filter { it.recognizedText?.isNotBlank() == true && it.isValidFormat }
                        .maxByOrNull { it.confidence }
                        ?.recognizedText
                        ?: updatedDetections
                            .filter { it.recognizedText?.isNotBlank() == true }
                            .maxByOrNull { it.confidence }
                            ?.recognizedText
                    
                    _latestRecognizedText.value = bestValidText
                    _detectedPlates.value = updatedDetections
                    return@withContext ProcessorResult(updatedDetections, performanceStats, rawOutputLog)
                }
            } else if (plateDetections.isNotEmpty() && !settings.enableOcr) {
                // OCR is disabled, just return detections without text recognition
                _latestRecognizedText.value = null
            }
            
            _detectedPlates.value = plateDetections
            return@withContext ProcessorResult(plateDetections, performanceStats, rawOutputLog)
            
        } catch (e: Exception) {
            return@withContext ProcessorResult(emptyList(), emptyMap(), "Error in processor")
        } finally {
            _isProcessing.value = false
        }
    }
    
    /**
     * Enhanced cropping and scaling method for optimal OCR performance
     * Based on ML Kit's recommended resolution guidelines:
     * - Minimum: 16x16 pixels per character
     * - Maximum: 24x24 pixels per character (no benefit beyond this)
     * - Optimal: Keep between recommended min and max resolutions
     */
    private fun cropAndScaleLicensePlateForOCR(bitmap: Bitmap, boundingBox: RectF): Bitmap? {
        try {
            val imageWidth = bitmap.width.toFloat()
            val imageHeight = bitmap.height.toFloat()
            
            // Calculate center and dimensions
            val centerX = (boundingBox.left + boundingBox.right) / 2f
            val centerY = (boundingBox.top + boundingBox.bottom) / 2f
            val width = boundingBox.width()
            val height = boundingBox.height()
            
            // Expand by 10% for better OCR context (similar to Python script's 1.1 factor)
            val newWidth = width * 1.1f
            val newHeight = height * 1.1f
            
            // Calculate new bounds
            val newLeft = (centerX - newWidth / 2f).coerceAtLeast(0f)
            val newTop = (centerY - newHeight / 2f).coerceAtLeast(0f)
            val newRight = (centerX + newWidth / 2f).coerceAtMost(imageWidth)
            val newBottom = (centerY + newHeight / 2f).coerceAtMost(imageHeight)
            
            val cropWidth = (newRight - newLeft).toInt()
            val cropHeight = (newBottom - newTop).toInt()
            
            if (cropWidth <= 0 || cropHeight <= 0) {
                return null
            }
            
            // Step 1: Crop the license plate region from the MAX RESOLUTION image
            val croppedBitmap = Bitmap.createBitmap(
                bitmap,
                newLeft.toInt(),
                newTop.toInt(),
                cropWidth,
                cropHeight
            )
            
            // Step 2: Apply intelligent scaling based on ML Kit guidelines
            return scaleImageForOptimalOCR(croppedBitmap)
            
        } catch (e: Exception) {
            // Return null if cropping fails
            return null
        }
    }
    
    /**
     * Scales the cropped image to optimal resolution for ML Kit OCR
     * Following ML Kit's recommendations:
     * - If too small: upscale to minimum recommended size
     * - If too large: downscale to maximum recommended size  
     * - If optimal: keep as is
     */
    private fun scaleImageForOptimalOCR(croppedBitmap: Bitmap): Bitmap {
        val originalWidth = croppedBitmap.width
        val originalHeight = croppedBitmap.height
        
        // Determine scaling factor based on ML Kit guidelines
        val scaleFactor = when {
            // Case 1: Too small - upscale to minimum recommended resolution
            originalHeight < OPTIMAL_HEIGHT_MIN || originalWidth < OPTIMAL_WIDTH_MIN -> {
                val heightScale = if (originalHeight < OPTIMAL_HEIGHT_MIN) {
                    OPTIMAL_HEIGHT_MIN.toFloat() / originalHeight
                } else 1f
                
                val widthScale = if (originalWidth < OPTIMAL_WIDTH_MIN) {
                    OPTIMAL_WIDTH_MIN.toFloat() / originalWidth
                } else 1f
                
                // Use the larger scale to ensure both dimensions meet minimum requirements
                maxOf(heightScale, widthScale)
            }
            
            // Case 2: Too large - downscale to maximum recommended resolution
            originalHeight > OPTIMAL_HEIGHT_MAX || originalWidth > OPTIMAL_WIDTH_MAX -> {
                val heightScale = if (originalHeight > OPTIMAL_HEIGHT_MAX) {
                    OPTIMAL_HEIGHT_MAX.toFloat() / originalHeight
                } else 1f
                
                val widthScale = if (originalWidth > OPTIMAL_WIDTH_MAX) {
                    OPTIMAL_WIDTH_MAX.toFloat() / originalWidth
                } else 1f
                
                // Use the smaller scale to ensure both dimensions stay within maximum limits
                minOf(heightScale, widthScale)
            }
            
            // Case 3: Within optimal range - keep as is
            else -> 1f
        }
        
        // Apply scaling if needed
        return if (scaleFactor != 1f) {
            val newWidth = (originalWidth * scaleFactor).toInt()
            val newHeight = (originalHeight * scaleFactor).toInt()
            
            // Use high-quality scaling for better OCR results
            val matrix = Matrix().apply {
                setScale(scaleFactor, scaleFactor)
            }
            
            Bitmap.createBitmap(croppedBitmap, 0, 0, originalWidth, originalHeight, matrix, true)
        } else {
            // No scaling needed, return original cropped bitmap
            croppedBitmap
        }
    }
    
    /**
     * Gets the appropriate OCR engine based on the selected model type
     */
    private fun getOcrEngine(modelType: OcrModelType): OcrEngine? {
        return when (modelType) {
            OcrModelType.FAST_PLATE_OCR -> fastPlateOcrEngine
            OcrModelType.ML_KIT -> mlKitOcrEngine
            OcrModelType.TESSERACT -> tesseractOcrEngine
            OcrModelType.PADDLE_OCR -> paddleOcrEngine
        }
    }
    
    /**
     * Releases all resources
     */
    fun release() {
        licensePlateDetector.release()
        fastPlateOcrEngine.release()
        mlKitOcrEngine.release()
        tesseractOcrEngine.release()
        paddleOcrEngine.release()
        isInitialized = false
    }
    
    fun isReady(): Boolean = isInitialized
    
    /**
     * Gets GPU status information for debug display
     */
    fun getGpuStatus(): Map<String, Boolean> {
        return mapOf(
            "Detection" to licensePlateDetector.isUsingGpu(),
            "FastPlate OCR" to fastPlateOcrEngine.isUsingGpu(),
            "ML Kit OCR" to mlKitOcrEngine.isUsingGpu(),
            "Tesseract OCR" to tesseractOcrEngine.isUsingGpu(),
            "Paddle OCR" to paddleOcrEngine.isUsingGpu()
        )
    }
} 