package com.example.vehiclerecognition.ml.processors

import android.graphics.Bitmap
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
            // Reinitialize both detector and OCR engines for GPU acceleration changes
            val detectorReady = licensePlateDetector.initialize(settings)
            val fastPlateReady = fastPlateOcrEngine.initialize(settings)
            val mlKitReady = mlKitOcrEngine.initialize(settings)
            val tesseractReady = tesseractOcrEngine.initialize(settings)
            val paddleReady = paddleOcrEngine.initialize(settings)
            
            return@withContext detectorReady && fastPlateReady && mlKitReady && tesseractReady && paddleReady
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Process frame for license plate detection and OCR
     * Similar to the frame processing logic in Python script
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
            
            // Step 3: Process OCR on the best detection if OCR is enabled
            val bestDetection = plateDetections.maxByOrNull { it.confidence }
            if (bestDetection != null && settings.enableOcr) {
                
                // Crop the license plate region with expansion (similar to Python script)
                val croppedBitmap = cropLicensePlateRegion(bitmap, bestDetection.boundingBox)
                
                if (croppedBitmap != null) {
                    // Get the appropriate OCR engine
                    val ocrEngine = getOcrEngine(settings.selectedOcrModel)
                    
                    if (ocrEngine?.isReady() == true) {
                        val ocrResult = ocrEngine.processImage(croppedBitmap)
                        
                        // Update the best detection with OCR results
                        val updatedDetection = bestDetection.copy(
                            recognizedText = ocrResult.formattedText ?: ocrResult.text,
                            isValidFormat = ocrResult.isValidFormat,
                            processingTimeMs = ocrResult.processingTimeMs
                        )
                        
                        // Update the plate detections with the OCR result
                        val updatedDetections = plateDetections.map { detection ->
                            if (detection == bestDetection) updatedDetection else detection
                        }
                        
                        // Update latest recognized text
                        _latestRecognizedText.value = ocrResult.formattedText ?: ocrResult.text
                        
                        _detectedPlates.value = updatedDetections
                        return@withContext ProcessorResult(updatedDetections, performanceStats, rawOutputLog)
                    }
                }
            } else if (bestDetection != null && !settings.enableOcr) {
                // OCR is disabled, just return detection without text recognition
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
     * Crops the license plate region from the full image with expansion
     * Similar to the cropping logic in Python script with 10% expansion
     */
    private fun cropLicensePlateRegion(bitmap: Bitmap, boundingBox: RectF): Bitmap? {
        try {
            val imageWidth = bitmap.width.toFloat()
            val imageHeight = bitmap.height.toFloat()
            
            // Calculate center and dimensions
            val centerX = (boundingBox.left + boundingBox.right) / 2f
            val centerY = (boundingBox.top + boundingBox.bottom) / 2f
            val width = boundingBox.width()
            val height = boundingBox.height()
            
            // Expand by 10% (similar to Python script's 1.1 factor)
            val newWidth = width * 1.1f
            val newHeight = height * 1.1f
            
            // Calculate new bounds
            val newLeft = (centerX - newWidth / 2f).coerceAtLeast(0f)
            val newTop = (centerY - newHeight / 2f).coerceAtLeast(0f)
            val newRight = (centerX + newWidth / 2f).coerceAtMost(imageWidth)
            val newBottom = (centerY + newHeight / 2f).coerceAtMost(imageHeight)
            
            val cropWidth = (newRight - newLeft).toInt()
            val cropHeight = (newBottom - newTop).toInt()
            
            if (cropWidth > 0 && cropHeight > 0) {
                return Bitmap.createBitmap(
                    bitmap,
                    newLeft.toInt(),
                    newTop.toInt(),
                    cropWidth,
                    cropHeight
                )
            }
        } catch (e: Exception) {
            // Return null if cropping fails
        }
        return null
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