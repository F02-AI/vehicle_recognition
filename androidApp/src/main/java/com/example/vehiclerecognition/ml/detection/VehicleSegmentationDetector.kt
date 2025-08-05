package com.example.vehiclerecognition.ml.detection

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.util.Log
import com.example.vehiclerecognition.data.models.LicensePlateSettings
import com.example.vehiclerecognition.data.models.VehicleDetection
import com.example.vehiclerecognition.data.models.VehicleSegmentationResult
import com.example.vehiclerecognition.data.models.VehicleClass
import com.example.vehiclerecognition.model.VehicleColor
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.GpuDelegate
import org.tensorflow.lite.support.common.ops.NormalizeOp
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.*
import java.util.Collections

/**
 * Extension function to format float values for logging
 */
private fun Float.format(digits: Int) = "%.${digits}f".format(this)
private fun Double.format(digits: Int) = "%.${digits}f".format(this)

/**
 * Data class to hold four values (like Triple but with four elements)
 */
data class Quadruple<A, B, C, D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D
)

/**
 * Vehicle segmentation detector using YOLO11 segmentation model
 * Provides both bounding box detection and pixel-level segmentation masks
 */
@Singleton
class VehicleSegmentationDetector @Inject constructor(
    @ApplicationContext private val context: Context
) {
    
    private var interpreter: Interpreter? = null
    private var gpuDelegate: GpuDelegate? = null
    private var isInitialized = false
    private val interpreterMutex = Mutex()
    
    // Performance tracking
    private val preprocessingTimes = Collections.synchronizedList(mutableListOf<Long>())
    private val inferenceTimes = Collections.synchronizedList(mutableListOf<Long>())
    private val postprocessingTimes = Collections.synchronizedList(mutableListOf<Long>())
    
    companion object {
        private const val TAG = "VehicleSegDetector"
        private const val MODEL_PATH = "models/vehicle_seg.tflite"
        private const val INPUT_SIZE = 640 // YOLO11 input size
        private const val CONFIDENCE_THRESHOLD = 0.3f // Lowered for debugging
        private const val IOU_THRESHOLD = 0.4f
        
        // YOLO11 segmentation output format:
        // outputs[0]: detections [1, num_detections, 4+num_classes] - bboxes + class scores
        // outputs[1]: masks [1, num_masks, mask_height, mask_width] - segmentation masks
        private const val NUM_CLASSES = 80 // COCO dataset classes
        private const val MASK_SIZE = 160 // Typical YOLO segmentation mask size
        
        // Vehicle class IDs from COCO dataset
        private val VEHICLE_CLASS_IDS = setOf(2, 3, 7) // car, motorcycle, truck
        
        // Debug info storage
        var lastDebugInfo: String = ""
        var isUsingGpu: Boolean = false
    }
    
    suspend fun initialize(settings: LicensePlateSettings): Boolean = withContext(Dispatchers.IO) {
        try {
            // Clean up any existing resources
            release()
            
            try {
                val modelBuffer = loadModelFile()
                val options = Interpreter.Options().apply {
                    setNumThreads(4)
                    setUseXNNPACK(true)
                    
                    // Add GPU delegate if enabled in settings
                    if (settings.enableGpuAcceleration) {
                        try {
                            Class.forName("org.tensorflow.lite.gpu.GpuDelegate")
                            Class.forName("org.tensorflow.lite.gpu.CompatibilityList")
                            
                            val compatClass = Class.forName("org.tensorflow.lite.gpu.CompatibilityList")
                            val compatList = compatClass.getDeclaredConstructor().newInstance()
                            val isSupportedMethod = compatClass.getMethod("isDelegateSupportedOnThisDevice")
                            val isSupported = isSupportedMethod.invoke(compatList) as Boolean
                            
                            Log.d(TAG, "GPU compatibility check: isSupported=$isSupported")
                            
                            if (isSupported) {
                                try {
                                    Log.d(TAG, "Creating GPU delegate for vehicle segmentation...")
                                    gpuDelegate = GpuDelegate()
                                    addDelegate(gpuDelegate!!)
                                    Log.d(TAG, "GPU delegate created successfully for vehicle segmentation")
                                } catch (e: Exception) {
                                    Log.w(TAG, "Failed to create GPU delegate for vehicle segmentation: ${e.message}")
                                    gpuDelegate?.close()
                                    gpuDelegate = null
                                    isUsingGpu = false
                                }
                            } else {
                                Log.w(TAG, "GPU delegate not supported for vehicle segmentation, using CPU")
                                isUsingGpu = false
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to enable GPU acceleration for vehicle segmentation: ${e.message}")
                            gpuDelegate?.close()
                            gpuDelegate = null
                            isUsingGpu = false
                        }
                    } else {
                        Log.d(TAG, "GPU acceleration disabled for vehicle segmentation")
                        isUsingGpu = false
                    }
                }
                
                // Create interpreter
                try {
                    interpreter = Interpreter(modelBuffer, options)
                    
                    if (settings.enableGpuAcceleration && gpuDelegate != null) {
                        isUsingGpu = true
                        Log.d(TAG, "Vehicle segmentation GPU acceleration enabled successfully")
                    } else {
                        isUsingGpu = false
                        Log.d(TAG, "Vehicle segmentation using CPU")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to create vehicle segmentation interpreter with GPU, falling back to CPU: ${e.message}")
                    gpuDelegate?.close()
                    gpuDelegate = null
                    isUsingGpu = false
                    
                    val cpuOptions = Interpreter.Options().apply {
                        setNumThreads(4)
                        setUseXNNPACK(true)
                    }
                    interpreter = Interpreter(modelBuffer, cpuOptions)
                    Log.d(TAG, "Successfully created vehicle segmentation interpreter with CPU fallback")
                }
                
                isInitialized = true
                return@withContext true
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load vehicle segmentation model, using simulated results: ${e.message}")
                // Fall back to simulated results
                isInitialized = true
                return@withContext true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize vehicle segmentation detector", e)
            release()
            isInitialized = false
            return@withContext false
        }
    }
    
    private fun loadModelFile(): ByteBuffer {
        val assetFileDescriptor = context.assets.openFd(MODEL_PATH)
        val inputStream = FileInputStream(assetFileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = assetFileDescriptor.startOffset
        val declaredLength = assetFileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }
    
    suspend fun detectVehicles(bitmap: Bitmap, settings: LicensePlateSettings = LicensePlateSettings()): VehicleSegmentationResult = withContext(Dispatchers.Default) {
        if (!isInitialized) {
            Log.d(TAG, "VehicleSegmentationDetector not initialized - returning empty result")
            return@withContext VehicleSegmentationResult(emptyList(), emptyMap(), "Detector not initialized")
        }
        
        Log.d(TAG, "Running vehicle detection on ${bitmap.width}x${bitmap.height} bitmap")
        
        try {
            val results = interpreter?.let { tfliteInterpreter ->
                interpreterMutex.withLock {
                    Log.d(TAG, "Running TensorFlow Lite inference for vehicle detection")
                    runTensorFlowLiteInference(bitmap, tfliteInterpreter, settings)
                }
            }
            
            // Otherwise, use simulated detection
            results ?: VehicleSegmentationResult(runSimulatedDetection(bitmap), emptyMap(), "Simulated | GPU: $isUsingGpu")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error during vehicle segmentation", e)
            VehicleSegmentationResult(emptyList(), emptyMap(), "Error")
        }
    }
    
    private fun runTensorFlowLiteInference(bitmap: Bitmap, interpreter: Interpreter, settings: LicensePlateSettings): VehicleSegmentationResult {
        val pre = System.currentTimeMillis()
        Log.d(TAG, "Starting vehicle segmentation inference on ${bitmap.width}x${bitmap.height} image")
        
        // Step 1: Preprocess the image
        val inputDetails = interpreter.getInputTensor(0)
        val inputShape = inputDetails.shape() // e.g., [1, 640, 640, 3]
        val inputHeight = inputShape[1]
        val inputWidth = inputShape[2]

        Log.d(TAG, "Vehicle segmentation model input shape: ${inputShape.contentToString()}")

        val imageProcessor = ImageProcessor.Builder()
            .add(ResizeOp(inputHeight, inputWidth, ResizeOp.ResizeMethod.BILINEAR))
            .add(NormalizeOp(0.0f, 255.0f))
            .build()

        var tensorImage = TensorImage(inputDetails.dataType())
        tensorImage.load(bitmap)
        tensorImage = imageProcessor.process(tensorImage)
        preprocessingTimes.add(System.currentTimeMillis() - pre)

        // Step 2: Prepare output buffers
        // YOLO11 segmentation typically has 2 outputs:
        // Output 0: detections [1, num_detections, 4+num_classes]
        // Output 1: masks [1, num_masks, mask_height, mask_width]
        val numOutputs = interpreter.outputTensorCount
        Log.d(TAG, "Vehicle segmentation model has $numOutputs outputs")
        
        val outputBuffers = mutableMapOf<Int, ByteBuffer>()
        val outputShapes = mutableMapOf<Int, IntArray>()
        
        for (i in 0 until numOutputs) {
            val outputTensor = interpreter.getOutputTensor(i)
            val shape = outputTensor.shape()
            outputShapes[i] = shape
            val buffer = ByteBuffer.allocateDirect(outputTensor.numBytes())
            buffer.order(java.nio.ByteOrder.nativeOrder())
            outputBuffers[i] = buffer
            Log.d(TAG, "Output $i shape: ${shape.contentToString()}")
        }

        // Step 3: Run inference
        val inf = System.currentTimeMillis()
        interpreter.runForMultipleInputsOutputs(arrayOf(tensorImage.buffer), outputBuffers as MutableMap<Int, Any>)
        inferenceTimes.add(System.currentTimeMillis() - inf)

        // Step 4: Post-process outputs
        val post = System.currentTimeMillis()
        val detections = postprocessOutputs(outputBuffers, outputShapes, bitmap.width, bitmap.height, bitmap, settings)
        postprocessingTimes.add(System.currentTimeMillis() - post)
        
        Log.d(TAG, "Vehicle segmentation post-processing found ${detections.size} detections")
        
        val rawOutputLog = "outputs: $numOutputs | GPU: $isUsingGpu"

        return VehicleSegmentationResult(detections, getPerformanceStats(), rawOutputLog)
    }
    
    private fun postprocessOutputs(
        outputBuffers: Map<Int, ByteBuffer>,
        outputShapes: Map<Int, IntArray>,
        originalWidth: Int,
        originalHeight: Int,
        originalBitmap: Bitmap,
        settings: LicensePlateSettings
    ): List<VehicleDetection> {
        
        // Extract detection output (bounding boxes + class scores)
        val detectionsBuffer = outputBuffers[0] ?: return emptyList()
        val detectionsShape = outputShapes[0] ?: return emptyList()
        
        detectionsBuffer.rewind()
        val detectionsArray = FloatArray(detectionsShape.reduce { acc, i -> acc * i })
        detectionsBuffer.asFloatBuffer().get(detectionsArray)
        
        // Extract mask output if available
        val masksBuffer = outputBuffers[1]
        val masksShape = outputShapes[1]
        var masksArray: FloatArray? = null
        
        if (masksBuffer != null && masksShape != null) {
            masksBuffer.rewind()
            masksArray = FloatArray(masksShape.reduce { acc, i -> acc * i })
            masksBuffer.asFloatBuffer().get(masksArray)
        }
        
        Log.d(TAG, "Processing detections: shape=${detectionsShape.contentToString()}")
        if (masksArray != null) {
            Log.d(TAG, "Processing masks: shape=${masksShape!!.contentToString()}")
        }
        
        // Parse detections similar to license plate detector
        val candidateDetections = parseDetections(detectionsArray, detectionsShape, originalWidth, originalHeight)
        
        // Apply NMS to remove overlapping detections
        val nmsDetections = applyNMS(candidateDetections)
        
        // Add segmentation masks if available
        return if (masksArray != null && masksShape != null) {
            addSegmentationMasks(nmsDetections, masksArray, masksShape, originalWidth, originalHeight, originalBitmap, settings)
        } else {
            nmsDetections.map { (bbox, confidence, classId) ->
                VehicleDetection(
                    id = generateVehicleId(classId, bbox, System.currentTimeMillis(), 0),
                    boundingBox = bbox,
                    confidence = confidence,
                    classId = classId,
                    className = VehicleClass.getDisplayName(classId),
                    detectedColor = null, // No color detection without mask
                    secondaryColor = null, // No secondary color without mask
                    detectionTime = System.currentTimeMillis()
                )
            }
        }
    }
    
    private fun parseDetections(
        detectionsArray: FloatArray,
        detectionsShape: IntArray,
        originalWidth: Int,
        originalHeight: Int
    ): List<Quadruple<RectF, Float, Int, FloatArray>> {
        
        val detections = mutableListOf<Quadruple<RectF, Float, Int, FloatArray>>()
        
        // YOLO11 segmentation format: [batch, values_per_detection, num_detections]
        // For YOLO11-seg: [1, 116, 8400] where 116 = 4(bbox) + 80(classes) + 32(mask_coeffs)
        val numDetections: Int
        val numValues: Int
        
        if (detectionsShape.size == 3) {
            // Handle the transposed format typical in YOLO11 segmentation
            if (detectionsShape[1] > detectionsShape[2]) {
                // Format: [batch, num_detections, values_per_detection] - rare for YOLO11-seg
                numDetections = detectionsShape[1]
                numValues = detectionsShape[2]
            } else {
                // Format: [batch, values_per_detection, num_detections] - typical for YOLO11-seg
                numValues = detectionsShape[1]
                numDetections = detectionsShape[2]
            }
        } else {
            // Fallback for 2D arrays
            numDetections = detectionsShape[0]
            numValues = detectionsShape[1]
        }
        
        Log.d(TAG, "Parsing $numDetections detections with $numValues values each")
        Log.d(TAG, "Original shape: ${detectionsShape.contentToString()}")
        
        // YOLO11 segmentation has 116 values per detection: 4(bbox) + 80(classes) + 32(mask_coeffs)
        val expectedValues = 4 + NUM_CLASSES + 32 // 4 + 80 + 32 = 116
        if (numValues != expectedValues) {
            Log.w(TAG, "Unexpected values per detection: $numValues, expected: $expectedValues")
        }
        
        for (i in 0 until numDetections) {
            // For transposed format [values_per_detection, num_detections], 
            // we need to access elements with stride
            val isTransposed = detectionsShape.size == 3 && detectionsShape[1] == numValues
            
            val centerX: Float
            val centerY: Float
            val width: Float
            val height: Float
            
            if (isTransposed) {
                // Transposed: access with stride
                centerX = detectionsArray[0 * numDetections + i]
                centerY = detectionsArray[1 * numDetections + i] 
                width = detectionsArray[2 * numDetections + i]
                height = detectionsArray[3 * numDetections + i]
            } else {
                // Sequential: access normally
                val offset = i * numValues
                centerX = detectionsArray[offset]
                centerY = detectionsArray[offset + 1]
                width = detectionsArray[offset + 2]
                height = detectionsArray[offset + 3]
            }
            
            // Extract class scores (skip first 4 values for bbox)
            var maxConfidence = 0f
            var bestClassId = -1
            
            for (classIdx in 0 until NUM_CLASSES) {
                val confidence = if (isTransposed) {
                    detectionsArray[(4 + classIdx) * numDetections + i]
                } else {
                    detectionsArray[i * numValues + 4 + classIdx]
                }
                
                if (confidence > maxConfidence) {
                    maxConfidence = confidence
                    bestClassId = classIdx
                }
            }
            
            // Debug: Log top detections even if they don't meet our criteria
            if (i < 10 && maxConfidence > 0.1f) { // Log first 10 detections with any reasonable confidence
                val className = if (bestClassId in 0 until NUM_CLASSES) {
                    if (bestClassId in VEHICLE_CLASS_IDS) VehicleClass.getDisplayName(bestClassId) else "Class$bestClassId"
                } else {
                    "InvalidClass$bestClassId"
                }
                Log.d(TAG, "Detection $i: class=$bestClassId ($className), conf=${maxConfidence.format(3)}, isVehicle=${bestClassId in VEHICLE_CLASS_IDS}")
            }
            
            // Only keep vehicle classes above threshold
            if (maxConfidence >= CONFIDENCE_THRESHOLD && bestClassId in VEHICLE_CLASS_IDS) {
                // Convert normalized coordinates to pixel coordinates
                val x1 = (centerX - width / 2) * originalWidth
                val y1 = (centerY - height / 2) * originalHeight
                val x2 = (centerX + width / 2) * originalWidth
                val y2 = (centerY + height / 2) * originalHeight
                
                // Extract mask coefficients (last 32 values)
                val maskCoeffs = FloatArray(32)
                for (coeffIdx in 0 until 32) {
                    maskCoeffs[coeffIdx] = if (isTransposed) {
                        detectionsArray[(4 + NUM_CLASSES + coeffIdx) * numDetections + i]
                    } else {
                        detectionsArray[i * numValues + 4 + NUM_CLASSES + coeffIdx]
                    }
                }
                
                detections.add(Quadruple(RectF(x1, y1, x2, y2), maxConfidence, bestClassId, maskCoeffs))
                
                Log.d(TAG, "Vehicle detected: class=$bestClassId (${VehicleClass.getDisplayName(bestClassId)}), conf=${maxConfidence.format(3)}, bbox=(${centerX.format(3)}, ${centerY.format(3)}, ${width.format(3)}, ${height.format(3)})")
            }
        }
        
        Log.d(TAG, "Found ${detections.size} vehicle detections above threshold")
        return detections
    }
    
    private fun applyNMS(detections: List<Quadruple<RectF, Float, Int, FloatArray>>): List<Quadruple<RectF, Float, Int, FloatArray>> {
        val sortedDetections = detections.sortedByDescending { it.second }
        val finalDetections = mutableListOf<Quadruple<RectF, Float, Int, FloatArray>>()
        val selected = BooleanArray(sortedDetections.size)

        for (i in sortedDetections.indices) {
            if (selected[i]) continue

            val currentDetection = sortedDetections[i]
            finalDetections.add(currentDetection)

            for (j in i + 1 until sortedDetections.size) {
                if (selected[j]) continue

                val otherDetection = sortedDetections[j]
                val iou = calculateIoU(currentDetection.first, otherDetection.first)

                if (iou > IOU_THRESHOLD) {
                    selected[j] = true
                }
            }
        }

        return finalDetections
    }
    
    private fun calculateIoU(box1: RectF, box2: RectF): Float {
        val intersectionLeft = maxOf(box1.left, box2.left)
        val intersectionTop = maxOf(box1.top, box2.top)
        val intersectionRight = minOf(box1.right, box2.right)
        val intersectionBottom = minOf(box1.bottom, box2.bottom)

        val intersectionWidth = maxOf(0f, intersectionRight - intersectionLeft)
        val intersectionHeight = maxOf(0f, intersectionBottom - intersectionTop)
        val intersectionArea = intersectionWidth * intersectionHeight

        val box1Area = (box1.right - box1.left) * (box1.bottom - box1.top)
        val box2Area = (box2.right - box2.left) * (box2.bottom - box2.top)
        val unionArea = box1Area + box2Area - intersectionArea

        return if (unionArea > 0) intersectionArea / unionArea else 0f
    }
    
    private fun addSegmentationMasks(
        detections: List<Quadruple<RectF, Float, Int, FloatArray>>,
        masksArray: FloatArray,
        masksShape: IntArray,
        originalWidth: Int,
        originalHeight: Int,
        originalBitmap: Bitmap,
        settings: LicensePlateSettings
    ): List<VehicleDetection> {
        
        Log.d(TAG, "Generating segmentation masks for ${detections.size} detections")
        Log.d(TAG, "Mask prototypes shape: ${masksShape.contentToString()}")
        Log.d(TAG, "Original image size: ${originalWidth}x${originalHeight}")
        
        // YOLO11 mask prototypes format: [1, 160, 160, 32]
        val batchSize = masksShape[0]
        val prototypeHeight = masksShape[1] // 160
        val prototypeWidth = masksShape[2] // 160  
        val numPrototypes = masksShape[3] // 32
        
        return detections.mapIndexed { index, (bbox, confidence, classId, maskCoeffs) ->
            
            // Generate unique ID for this detection
            val detectionId = generateVehicleId(classId, bbox, System.currentTimeMillis(), index)
            
            // Generate full mask at prototype resolution (160x160)
            val fullMask = generateMask(maskCoeffs, masksArray, prototypeHeight, prototypeWidth, numPrototypes)
            
            // Convert bounding box to mask coordinates using actual image dimensions
            val maskBbox = convertBboxToMaskCoords(bbox, originalWidth, originalHeight, prototypeWidth, prototypeHeight)
            
            Log.d(TAG, "Bbox in image coords: (${bbox.left.toInt()}, ${bbox.top.toInt()}) to (${bbox.right.toInt()}, ${bbox.bottom.toInt()})")
            Log.d(TAG, "Bbox in mask coords: (${maskBbox.left.toInt()}, ${maskBbox.top.toInt()}) to (${maskBbox.right.toInt()}, ${maskBbox.bottom.toInt()})")
            
            // Crop mask to bounding box area
            val croppedMask = cropMask(fullMask, maskBbox, prototypeWidth, prototypeHeight)
            
            // Validate and log mask quality
            val maskStats = validateMask(croppedMask)
            Log.d(TAG, "Mask stats for ${VehicleClass.getDisplayName(classId)}: pixels=${maskStats.first}, max=${maskStats.second.format(3)}, avg=${maskStats.third.format(3)}")
            
            // Detect vehicle colors from masked pixels
            val (detectedColor, secondaryColor) = detectVehicleColors(originalBitmap, bbox, croppedMask, settings)
            Log.d(TAG, "Detected colors for vehicle $detectionId: primary=$detectedColor, secondary=$secondaryColor")
            
            // Resize cropped mask to a good display resolution
            val targetMaskSize = 128 // Increased resolution for better quality
            val resizedMask = resizeMask(
                croppedMask, 
                croppedMask.size, 
                croppedMask[0].size, 
                targetMaskSize, 
                targetMaskSize
            )
            
            Log.d(TAG, "Generated mask for ${VehicleClass.getDisplayName(classId)} ID:$detectionId: cropped from ${croppedMask[0].size}x${croppedMask.size} to ${targetMaskSize}x${targetMaskSize}")
            
            VehicleDetection(
                id = detectionId,
                boundingBox = bbox,
                confidence = confidence,
                classId = classId,
                className = VehicleClass.getDisplayName(classId),
                detectedColor = detectedColor,
                secondaryColor = secondaryColor,
                segmentationMask = resizedMask,
                maskWidth = targetMaskSize,
                maskHeight = targetMaskSize,
                maskCoeffs = maskCoeffs,
                detectionTime = System.currentTimeMillis()
            )
        }
    }
    
    /**
     * Convert bounding box from image coordinates to mask coordinates
     */
    private fun convertBboxToMaskCoords(
        bbox: RectF,
        imageWidth: Int,
        imageHeight: Int,
        maskWidth: Int,
        maskHeight: Int
    ): RectF {
        // Convert from image coordinates to normalized coordinates (0-1)
        val normalizedLeft = bbox.left / imageWidth
        val normalizedTop = bbox.top / imageHeight
        val normalizedRight = bbox.right / imageWidth
        val normalizedBottom = bbox.bottom / imageHeight
        
        // Convert to mask coordinates
        return RectF(
            normalizedLeft * maskWidth,
            normalizedTop * maskHeight,
            normalizedRight * maskWidth,
            normalizedBottom * maskHeight
        )
    }
    
    /**
     * Crop mask to bounding box area
     */
    private fun cropMask(
        fullMask: Array<FloatArray>,
        maskBbox: RectF,
        maskWidth: Int,
        maskHeight: Int
    ): Array<FloatArray> {
        
        // Ensure bounds are within mask dimensions
        val left = maskBbox.left.toInt().coerceIn(0, maskWidth - 1)
        val top = maskBbox.top.toInt().coerceIn(0, maskHeight - 1)
        val right = maskBbox.right.toInt().coerceIn(left + 1, maskWidth)
        val bottom = maskBbox.bottom.toInt().coerceIn(top + 1, maskHeight)
        
        val cropWidth = right - left
        val cropHeight = bottom - top
        
        val croppedMask = Array(cropHeight) { FloatArray(cropWidth) }
        
        for (y in 0 until cropHeight) {
            for (x in 0 until cropWidth) {
                val sourceY = top + y
                val sourceX = left + x
                if (sourceY < maskHeight && sourceX < maskWidth) {
                    croppedMask[y][x] = fullMask[sourceY][sourceX]
                }
            }
        }
        
        return croppedMask
    }
    
    /**
     * Generate segmentation mask by combining mask coefficients with prototypes
     */
    private fun generateMask(
        coefficients: FloatArray,
        prototypes: FloatArray,
        prototypeHeight: Int,
        prototypeWidth: Int,
        numPrototypes: Int
    ): Array<FloatArray> {
        
        val mask = Array(prototypeHeight) { FloatArray(prototypeWidth) }
        
        // Combine coefficients with prototypes using matrix multiplication
        for (y in 0 until prototypeHeight) {
            for (x in 0 until prototypeWidth) {
                var pixelValue = 0f
                
                for (p in 0 until numPrototypes) {
                    // Access prototype value: prototypes[y, x, p] in flattened array
                    val prototypeIndex = y * prototypeWidth * numPrototypes + x * numPrototypes + p
                    if (prototypeIndex < prototypes.size) {
                        val prototypeValue = prototypes[prototypeIndex]
                        pixelValue += coefficients[p] * prototypeValue
                    }
                }
                
                // Apply sigmoid activation and normalize to 0-1 range
                mask[y][x] = sigmoid(pixelValue)
            }
        }
        
        // Post-process mask to enhance contrast
        return enhanceMask(mask, prototypeHeight, prototypeWidth)
    }
    
    /**
     * Enhance mask contrast and quality
     */
    private fun enhanceMask(
        mask: Array<FloatArray>,
        height: Int,
        width: Int
    ): Array<FloatArray> {
        
        // Find min and max values for normalization
        var minVal = Float.MAX_VALUE
        var maxVal = Float.MIN_VALUE
        
        for (y in 0 until height) {
            for (x in 0 until width) {
                val value = mask[y][x]
                if (value < minVal) minVal = value
                if (value > maxVal) maxVal = value
            }
        }
        
        // Normalize and enhance contrast
        val range = maxVal - minVal
        if (range > 0f) {
            for (y in 0 until height) {
                for (x in 0 until width) {
                    // Normalize to 0-1
                    var normalized = (mask[y][x] - minVal) / range
                    
                    // Apply contrast enhancement (gamma correction)
                    normalized = Math.pow(normalized.toDouble(), 0.7).toFloat() // Slightly enhance contrast
                    
                    mask[y][x] = normalized.coerceIn(0f, 1f)
                }
            }
        }
        
        return mask
    }
    
    /**
     * Improved sigmoid activation function
     */
    private fun sigmoid(x: Float): Float {
        return if (x > 0) {
            val exp = kotlin.math.exp(-x)
            1f / (1f + exp)
        } else {
            val exp = kotlin.math.exp(x)
            exp / (1f + exp)
        }
    }
    
    /**
     * Resize mask to target dimensions using nearest neighbor interpolation
     */
    private fun resizeMask(
        mask: Array<FloatArray>,
        originalHeight: Int,
        originalWidth: Int,
        targetHeight: Int,
        targetWidth: Int
    ): Array<FloatArray> {
        
        val resized = Array(targetHeight) { FloatArray(targetWidth) }
        
        val scaleY = originalHeight.toFloat() / targetHeight
        val scaleX = originalWidth.toFloat() / targetWidth
        
        for (y in 0 until targetHeight) {
            for (x in 0 until targetWidth) {
                val sourceY = y * scaleY
                val sourceX = x * scaleX
                
                // Nearest neighbor interpolation
                val nearestY = sourceY.toInt().coerceIn(0, originalHeight - 1)
                val nearestX = sourceX.toInt().coerceIn(0, originalWidth - 1)
                
                resized[y][x] = mask[nearestY][nearestX]
            }
        }
        
        return resized
    }
    
    /**
     * Validate mask and return statistics: (active_pixels, max_value, avg_value)
     */
    private fun validateMask(mask: Array<FloatArray>): Triple<Int, Float, Float> {
        var activePixels = 0
        var maxValue = 0f
        var totalValue = 0f
        var totalPixels = 0
        
        for (row in mask) {
            for (value in row) {
                if (value > 0.3f) activePixels++ // Count pixels above threshold
                if (value > maxValue) maxValue = value
                totalValue += value
                totalPixels++
            }
        }
        
        val avgValue = if (totalPixels > 0) totalValue / totalPixels else 0f
        return Triple(activePixels, maxValue, avgValue)
    }
    
    /**
     * Generate unique ID for vehicle detection
     */
    private fun generateVehicleId(classId: Int, bbox: RectF, timestamp: Long, index: Int): String {
        val className = VehicleClass.getDisplayName(classId).take(3).uppercase()
        val bboxHash = ((bbox.left + bbox.top + bbox.right + bbox.bottom) * 1000).toInt().toString(16)
        val timeHash = (timestamp % 10000).toString(16)
        return "${className}_${timeHash}_${bboxHash}_$index"
    }
    
    /**
     * Detect dominant vehicle colors from segmentation mask using K-means clustering
     * Returns a pair of (primary, secondary) colors
     */
    private fun detectVehicleColors(
        originalBitmap: Bitmap,
        bbox: RectF,
        mask: Array<FloatArray>,
        settings: LicensePlateSettings
    ): Pair<VehicleColor?, VehicleColor?> {
        try {
            val validPixels = mutableListOf<Triple<Int, Int, Int>>() // RGB values
            
            val maskHeight = mask.size
            val maskWidth = if (maskHeight > 0) mask[0].size else 0
            
            if (maskWidth == 0 || maskHeight == 0) return Pair(null, null)
            
            // Calculate scale factors
            val scaleX = (bbox.right - bbox.left) / maskWidth
            val scaleY = (bbox.bottom - bbox.top) / maskHeight
            
            // Optimized sampling: balance performance vs accuracy
            val sampleStep = maxOf(1, minOf(maskWidth, maskHeight) / 24) // Slightly more aggressive sampling
            val centerWeight = 0.7f // Give more weight to center pixels
            
            // First pass: count dark pixels to determine if black is predominant
            var totalSamples = 0
            var darkPixelCount = 0
            
            for (maskY in 0 until maskHeight step sampleStep) {
                for (maskX in 0 until maskWidth step sampleStep) {
                    val maskValue = mask[maskY][maskX]
                    
                    if (maskValue > 0.4f) {
                        val imageX = (bbox.left + maskX * scaleX).toInt()
                        val imageY = (bbox.top + maskY * scaleY).toInt()
                        
                        if (imageX in 0 until originalBitmap.width && imageY in 0 until originalBitmap.height) {
                            try {
                                val pixel = originalBitmap.getPixel(imageX, imageY)
                                val r = (pixel shr 16) and 0xFF
                                val g = (pixel shr 8) and 0xFF
                                val b = pixel and 0xFF
                                val brightness = (r + g + b) / 3
                                
                                // Filter out pure black/white in dark pixel analysis too
                                val isPureBlack = r <= 10 && g <= 10 && b <= 10
                                val isPureWhite = r >= 245 && g >= 245 && b >= 245
                                val isNaturalColor = !isPureBlack && !isPureWhite
                                
                                if (brightness in 20..220 && isNaturalColor) { // Valid natural color range
                                    totalSamples++
                                    if (brightness < 60) { // Dark pixel threshold
                                        darkPixelCount++
                                    }
                                }
                                
                            } catch (e: Exception) {
                                // Skip invalid pixels
                            }
                        }
                    }
                }
            }
            
            // Determine if black/dark is predominant (>60% of valid pixels)
            val blackIsPredominant = totalSamples > 0 && (darkPixelCount.toFloat() / totalSamples) > 0.6f
            
            Log.d(TAG, "Dark pixel analysis: $darkPixelCount/$totalSamples (${(darkPixelCount.toFloat() / totalSamples * 100).toInt()}%), blackIsPredominant=$blackIsPredominant")
            
            // Second pass: collect pixels for color analysis, excluding black unless predominant
            for (maskY in 0 until maskHeight step sampleStep) {
                for (maskX in 0 until maskWidth step sampleStep) {
                    val maskValue = mask[maskY][maskX]
                    
                    // Only process pixels above threshold (part of the vehicle)
                    if (maskValue > 0.4f) { // Slightly higher threshold for better quality
                        val imageX = (bbox.left + maskX * scaleX).toInt()
                        val imageY = (bbox.top + maskY * scaleY).toInt()
                        
                        if (imageX in 0 until originalBitmap.width && imageY in 0 until originalBitmap.height) {
                            try {
                                val pixel = originalBitmap.getPixel(imageX, imageY)
                                val r = (pixel shr 16) and 0xFF
                                val g = (pixel shr 8) and 0xFF
                                val b = pixel and 0xFF
                                
                                // Filter out extreme values (reflections, shadows) and pure black/white
                                val brightness = (r + g + b) / 3
                                val brightnessPasses = brightness in 20..220 // Exclude pure blacks and whites
                                
                                // Additional check: exclude pure black and pure white pixels that don't exist in nature
                                val isPureBlack = r <= 10 && g <= 10 && b <= 10 // Very dark pixels
                                val isPureWhite = r >= 245 && g >= 245 && b >= 245 // Very bright pixels
                                val isNaturalColor = !isPureBlack && !isPureWhite
                                
                                // More lenient dark pixel filtering - only exclude very dark pixels
                                val blackPixelCheck = if (blackIsPredominant) {
                                    brightnessPasses && isNaturalColor // Include natural colors if black is predominant
                                } else {
                                    brightnessPasses && brightness >= 40 && isNaturalColor // Exclude unnatural extremes
                                }
                                
                                if (blackPixelCheck) {
                                    // Add multiple copies of center pixels for weighting
                                    val centerDistanceX = kotlin.math.abs(maskX - maskWidth/2f) / (maskWidth/2f)
                                    val centerDistanceY = kotlin.math.abs(maskY - maskHeight/2f) / (maskHeight/2f)
                                    val centerDistance = (centerDistanceX + centerDistanceY) / 2f
                                    val weight = if (centerDistance < centerWeight) 2 else 1
                                    
                                    repeat(weight) {
                                        validPixels.add(Triple(r, g, b))
                                    }
                                }
                            } catch (e: Exception) {
                                // Skip invalid pixels
                            }
                        }
                    }
                }
            }
            
            if (validPixels.size < 20) return Pair(null, null) // Need sufficient samples
            
            // Debug: Show sample of collected RGB values
            Log.d(TAG, "Collected ${validPixels.size} valid pixels. Sample of first 10:")
            validPixels.take(10).forEachIndexed { index, (r, g, b) ->
                val brightness = (r + g + b) / 3
                Log.d(TAG, "  [$index] RGB($r,$g,$b) brightness=$brightness")
            }
            
            // Convert RGB pixels to HSV and calculate gray percentage efficiently
            val hsvPixels = validPixels.map { (r, g, b) -> rgbToHsv(r, g, b) }
            
            // Fast gray detection using HSV: low saturation indicates gray
            val grayPixelCount = hsvPixels.count { (_, s, _) -> s < 0.15f } // Low saturation = gray
            val grayPercentage = if (validPixels.isNotEmpty()) {
                (grayPixelCount.toFloat() / validPixels.size) * 100f
            } else {
                0f
            }
            val shouldExcludeGray = settings.enableGrayFiltering && grayPercentage < settings.grayExclusionThreshold
            
            Log.d(TAG, "HSV Gray analysis: $grayPixelCount/${validPixels.size} pixels (${grayPercentage.toInt()}%), shouldExcludeGray=$shouldExcludeGray")
            
            // Apply K-means clustering using pre-calculated HSV data
            val dominantColors = kMeansColorsOptimized(hsvPixels, validPixels, k = 3) // Find top 3 colors
            
            if (dominantColors.isEmpty()) return Pair(null, null)
            
            // Debug: Show all K-means cluster results
            Log.d(TAG, "K-means found ${dominantColors.size} clusters:")
            dominantColors.forEachIndexed { index, (r, g, b) ->
                val brightness = (r + g + b) / 3
                Log.d(TAG, "  Cluster $index: RGB($r,$g,$b) brightness=$brightness")
            }
            
            // Map the top 2 K-means colors to VehicleColor enums with gray filtering
            val primaryKmeansColor = dominantColors.first()
            var primaryColor = mapRgbToVehicleColorWithGrayFilter(
                primaryKmeansColor.first, 
                primaryKmeansColor.second, 
                primaryKmeansColor.third, 
                shouldExcludeGray
            )
            
            var secondaryKmeansUsed: Triple<Int, Int, Int>? = null
            val secondaryColor = if (settings.enableSecondaryColorDetection && dominantColors.size > 1) {
                // Try each subsequent K-means color until we find one that maps to a different VehicleColor
                var secondaryMappedColor: VehicleColor? = null
                for (i in 1 until dominantColors.size) {
                    val kmeansColor = dominantColors[i]
                    val mappedColor = mapRgbToVehicleColorWithGrayFilter(
                        kmeansColor.first, 
                        kmeansColor.second, 
                        kmeansColor.third, 
                        shouldExcludeGray
                    )
                    if (mappedColor != primaryColor) {
                        secondaryMappedColor = mappedColor
                        secondaryKmeansUsed = kmeansColor
                        break
                    }
                }
                secondaryMappedColor
            } else {
                null
            }
            
            Log.d(TAG, "K-means color detection: samples=${validPixels.size}, clusters=${dominantColors.size}, " +
                      "primary K-means RGB=(${primaryKmeansColor.first},${primaryKmeansColor.second},${primaryKmeansColor.third}) -> $primaryColor, " +
                      "secondary K-means RGB=${secondaryKmeansUsed?.let { "(${it.first},${it.second},${it.third})" } ?: "none"} -> $secondaryColor, " +
                      "blackFiltered=${!blackIsPredominant}")
            
            return primaryColor to secondaryColor
            
        } catch (e: Exception) {
            Log.w(TAG, "Error detecting vehicle color: ${e.message}")
            return Pair(null, null)
        }
    }
    
    /**
     * Map RGB color to the closest VehicleColor enum using perceptual distance
     */
    private fun mapRgbToVehicleColor(r: Int, g: Int, b: Int): VehicleColor {
        // More realistic vehicle color references based on common car colors
        // Adjusted to better distinguish white/black from gray
        val predefinedColors = listOf(
            // Red variations
            VehicleColor.RED to Triple(140, 50, 50),      // Dark red
            VehicleColor.RED to Triple(180, 60, 60),      // Medium red
            VehicleColor.RED to Triple(120, 30, 30),      // Very dark red
            
            // Blue variations  
            VehicleColor.BLUE to Triple(60, 80, 140),     // Standard blue
            VehicleColor.BLUE to Triple(40, 60, 120),     // Dark blue
            VehicleColor.BLUE to Triple(80, 100, 160),    // Lighter blue
            
            // Green variations
            VehicleColor.GREEN to Triple(60, 100, 60),    // Standard green
            VehicleColor.GREEN to Triple(40, 80, 40),     // Dark green
            VehicleColor.GREEN to Triple(80, 120, 80),    // Lighter green
            
            // White variations - expanded range to capture more white vehicles
            VehicleColor.WHITE to Triple(235, 235, 235),  // Pure white
            VehicleColor.WHITE to Triple(210, 210, 210),  // Off-white  
            VehicleColor.WHITE to Triple(250, 250, 250),  // Bright white
            VehicleColor.WHITE to Triple(190, 190, 190),  // Slightly dirty white
            VehicleColor.WHITE to Triple(225, 225, 225),  // Common car white
            
            // Black variations - expanded range to capture more black vehicles
            VehicleColor.BLACK to Triple(35, 35, 35),     // Standard black
            VehicleColor.BLACK to Triple(20, 20, 20),     // Very dark
            VehicleColor.BLACK to Triple(50, 50, 50),     // Dark gray-black
            VehicleColor.BLACK to Triple(15, 15, 15),     // Deep black
            VehicleColor.BLACK to Triple(65, 65, 65),     // Charcoal black
            
            // Gray variations - reduced to narrower mid-range to avoid overlap
            VehicleColor.GRAY to Triple(110, 110, 110),   // Medium gray
            VehicleColor.GRAY to Triple(130, 130, 130),   // Light-medium gray
            VehicleColor.GRAY to Triple(85, 85, 85),      // Dark-medium gray
            
            // Yellow/Beige variations
            VehicleColor.YELLOW to Triple(200, 180, 60),  // Golden yellow
            VehicleColor.YELLOW to Triple(180, 160, 80),  // Beige
            VehicleColor.YELLOW to Triple(220, 200, 40)   // Bright yellow
        )
        
        var closestColor = VehicleColor.GRAY // Default to gray instead of black
        var minDistance = Double.MAX_VALUE
        val colorDistances = mutableMapOf<VehicleColor, Double>()
        
        // Find the closest distance for each color category
        for ((vehicleColor, refRgb) in predefinedColors) {
            val distance = calculateColorDistance(r, g, b, refRgb.first, refRgb.second, refRgb.third)
            val currentBest = colorDistances[vehicleColor]
            if (currentBest == null || distance < currentBest) {
                colorDistances[vehicleColor] = distance
            }
            if (distance < minDistance) {
                minDistance = distance
                closestColor = vehicleColor
            }
        }
        
        // Debug logging to understand what's happening
        Log.d(TAG, "Color mapping for RGB($r,$g,$b): closest=$closestColor (distance=${minDistance.toInt()})")
        colorDistances.toList().sortedBy { it.second }.take(3).forEach { (color, dist) ->
            Log.d(TAG, "  $color: distance=${dist.toInt()}")
        }
        
        // Removed gray fallback to allow white/black detection even at higher distances
        // This prevents white/black cars from being misclassified as gray
        return closestColor
    }
    
    /**
     * Fast HSV-based gray detection
     */
    private fun isGrayColorHSV(r: Int, g: Int, b: Int): Boolean {
        val (_, s, _) = rgbToHsv(r, g, b)
        return s < 0.15f // Low saturation indicates gray/monochromatic color
    }
    
    /**
     * Map RGB color to the closest VehicleColor enum with optimized gray filtering
     * Uses HSV-based gray detection for consistency and speed
     */
    private fun mapRgbToVehicleColorWithGrayFilter(r: Int, g: Int, b: Int, shouldExcludeGray: Boolean): VehicleColor {
        // Fast HSV-based gray check
        if (shouldExcludeGray && isGrayColorHSV(r, g, b)) {
            // This pixel is gray and we should exclude gray - find the best non-gray alternative
            return mapRgbToVehicleColorNonGray(r, g, b)
        }
        
        // Use standard mapping (includes gray if not excluded)
        return mapRgbToVehicleColor(r, g, b)
    }
    
    /**
     * Map RGB to closest non-gray VehicleColor (optimized for when gray is excluded)
     */
    private fun mapRgbToVehicleColorNonGray(r: Int, g: Int, b: Int): VehicleColor {
        // Same predefined colors as the original method but exclude gray
        val nonGrayColors = listOf(
            // Red variations
            VehicleColor.RED to Triple(140, 50, 50),      // Dark red
            VehicleColor.RED to Triple(180, 60, 60),      // Medium red
            VehicleColor.RED to Triple(120, 30, 30),      // Very dark red
            
            // Blue variations  
            VehicleColor.BLUE to Triple(60, 80, 140),     // Standard blue
            VehicleColor.BLUE to Triple(40, 60, 120),     // Dark blue
            VehicleColor.BLUE to Triple(80, 100, 160),    // Lighter blue
            
            // Green variations
            VehicleColor.GREEN to Triple(60, 100, 60),    // Standard green
            VehicleColor.GREEN to Triple(40, 80, 40),     // Dark green
            VehicleColor.GREEN to Triple(80, 120, 80),    // Lighter green
            
            // White variations - expanded range
            VehicleColor.WHITE to Triple(235, 235, 235),  // Pure white
            VehicleColor.WHITE to Triple(210, 210, 210),  // Off-white  
            VehicleColor.WHITE to Triple(250, 250, 250),  // Bright white
            VehicleColor.WHITE to Triple(190, 190, 190),  // Slightly dirty white
            VehicleColor.WHITE to Triple(225, 225, 225),  // Common car white
            
            // Black variations - expanded range
            VehicleColor.BLACK to Triple(35, 35, 35),     // Standard black
            VehicleColor.BLACK to Triple(20, 20, 20),     // Very dark
            VehicleColor.BLACK to Triple(50, 50, 50),     // Dark gray-black
            VehicleColor.BLACK to Triple(15, 15, 15),     // Deep black
            VehicleColor.BLACK to Triple(65, 65, 65),     // Charcoal black
            
            // Yellow/Beige variations
            VehicleColor.YELLOW to Triple(200, 180, 60),  // Golden yellow
            VehicleColor.YELLOW to Triple(180, 160, 80),  // Beige
            VehicleColor.YELLOW to Triple(220, 200, 40)   // Bright yellow
        )
        
        var closestColor = VehicleColor.WHITE // Default to white when gray is excluded
        var minDistance = Double.MAX_VALUE
        
        // Find the closest non-gray color
        for ((vehicleColor, refRgb) in nonGrayColors) {
            val distance = calculateColorDistance(r, g, b, refRgb.first, refRgb.second, refRgb.third)
            if (distance < minDistance) {
                minDistance = distance
                closestColor = vehicleColor
            }
        }
        
        return closestColor
    }
    
    /**
     * Calculate perceptual color distance using improved color space
     */
    private fun calculateColorDistance(r1: Int, g1: Int, b1: Int, r2: Int, g2: Int, b2: Int): Double {
        // Convert both colors to HSV for better perceptual distance
        val hsv1 = rgbToHsv(r1, g1, b1)
        val hsv2 = rgbToHsv(r2, g2, b2)
        
        val (h1, s1, v1) = hsv1
        val (h2, s2, v2) = hsv2
        
        // Handle hue wraparound (circular distance)
        val deltaH = minOf(kotlin.math.abs(h1 - h2), 360f - kotlin.math.abs(h1 - h2))
        val deltaS = kotlin.math.abs(s1 - s2)
        val deltaV = kotlin.math.abs(v1 - v2)
        
        // Determine if we're dealing with achromatic colors (low saturation)
        val isAchromatic = s1 < 0.15f && s2 < 0.15f // Both colors have low saturation
        
        // Weighted distance with enhanced brightness sensitivity for white/black/gray distinction
        val hueWeight = if (s1 > 0.1f && s2 > 0.1f && !isAchromatic) deltaH / 360f * 3f else 0f
        val satWeight = deltaS * 2f // Saturation is important for color distinction
        val valWeight = if (isAchromatic) deltaV * 2.5f else deltaV * 1f // Increased brightness weight for achromatic colors
        
        return kotlin.math.sqrt(
            hueWeight * hueWeight +
            satWeight * satWeight +
            valWeight * valWeight
        ).toDouble()
    }
    
    /**
     * Optimized K-means clustering using pre-calculated HSV data
     * Filters out pure black and white pixels that don't exist in natural vehicle colors
     */
    private fun kMeansColorsOptimized(
        hsvPixels: List<Triple<Float, Float, Float>>, 
        rgbPixels: List<Triple<Int, Int, Int>>, 
        k: Int = 3, 
        maxIterations: Int = 10
    ): List<Triple<Int, Int, Int>> {
        // Filter out unnatural pure black and white pixels before clustering
        val naturalPixelIndices = rgbPixels.mapIndexedNotNull { index, (r, g, b) ->
            val isPureBlack = r <= 10 && g <= 10 && b <= 10
            val isPureWhite = r >= 245 && g >= 245 && b >= 245
            if (!isPureBlack && !isPureWhite) index else null
        }
        
        val filteredHsvPixels = naturalPixelIndices.map { hsvPixels[it] }
        val filteredRgbPixels = naturalPixelIndices.map { rgbPixels[it] }
        
        if (filteredHsvPixels.size < k) return filteredRgbPixels.distinct()
        
        Log.d(TAG, "K-means filtering: removed ${rgbPixels.size - filteredRgbPixels.size} unnatural black/white pixels, using ${filteredRgbPixels.size} natural colors")
        
        // Initialize centroids randomly from filtered natural color data
        val centroids = mutableListOf<Triple<Float, Float, Float>>()
        val indices = (0 until filteredHsvPixels.size).shuffled().take(k)
        indices.forEach { centroids.add(filteredHsvPixels[it]) }
        
        repeat(maxIterations) {
            // Assign pixels to nearest centroid
            val clusters = Array(k) { mutableListOf<Triple<Float, Float, Float>>() }
            
            filteredHsvPixels.forEach { pixel ->
                var minDistance = Float.MAX_VALUE
                var closestCluster = 0
                
                centroids.forEachIndexed { index, centroid ->
                    val distance = hsvDistance(pixel, centroid)
                    if (distance < minDistance) {
                        minDistance = distance
                        closestCluster = index
                    }
                }
                
                clusters[closestCluster].add(pixel)
            }
            
            // Update centroids
            val newCentroids = mutableListOf<Triple<Float, Float, Float>>()
            clusters.forEach { cluster ->
                if (cluster.isNotEmpty()) {
                    val avgH = cluster.map { it.first }.average().toFloat()
                    val avgS = cluster.map { it.second }.average().toFloat()
                    val avgV = cluster.map { it.third }.average().toFloat()
                    newCentroids.add(Triple(avgH, avgS, avgV))
                } else {
                    // Keep old centroid if cluster is empty
                    newCentroids.add(centroids[newCentroids.size])
                }
            }
            
            centroids.clear()
            centroids.addAll(newCentroids)
        }
        
        // Convert back to RGB and sort by cluster size
        val clusterSizes = mutableListOf<Pair<Triple<Int, Int, Int>, Int>>()
        centroids.forEachIndexed { index, centroid ->
            val rgb = hsvToRgb(centroid.first, centroid.second, centroid.third)
            // Count filtered pixels closest to this centroid
            val clusterSize = filteredHsvPixels.count { pixel ->
                var minDistance = Float.MAX_VALUE
                var closestIndex = -1
                centroids.forEachIndexed { i, c ->
                    val dist = hsvDistance(pixel, c)
                    if (dist < minDistance) {
                        minDistance = dist
                        closestIndex = i
                    }
                }
                closestIndex == index
            }
            clusterSizes.add(rgb to clusterSize)
        }
        
        return clusterSizes.sortedByDescending { it.second }.map { it.first }
    }
    
    /**
     * Original K-means clustering for compatibility (kept for fallback)
     */
    private fun kMeansColors(pixels: List<Triple<Int, Int, Int>>, k: Int = 3, maxIterations: Int = 10): List<Triple<Int, Int, Int>> {
        if (pixels.size < k) return pixels.map { it }.distinct()
        
        // Convert RGB to HSV for better perceptual clustering
        val hsvPixels = pixels.map { (r, g, b) -> rgbToHsv(r, g, b) }
        
        return kMeansColorsOptimized(hsvPixels, pixels, k, maxIterations)
    }
    
    /**
     * Convert RGB to HSV color space
     */
    private fun rgbToHsv(r: Int, g: Int, b: Int): Triple<Float, Float, Float> {
        val rf = r / 255f
        val gf = g / 255f
        val bf = b / 255f
        
        val max = maxOf(rf, gf, bf)
        val min = minOf(rf, gf, bf)
        val delta = max - min
        
        val h = when {
            delta == 0f -> 0f
            max == rf -> 60f * (((gf - bf) / delta) % 6f)
            max == gf -> 60f * ((bf - rf) / delta + 2f)
            else -> 60f * ((rf - gf) / delta + 4f)
        }
        
        val s = if (max == 0f) 0f else delta / max
        val v = max
        
        return Triple(h, s, v)
    }
    
    /**
     * Convert HSV to RGB color space
     */
    private fun hsvToRgb(h: Float, s: Float, v: Float): Triple<Int, Int, Int> {
        val c = v * s
        val x = c * (1f - kotlin.math.abs((h / 60f) % 2f - 1f))
        val m = v - c
        
        val (r1, g1, b1) = when ((h / 60f).toInt()) {
            0 -> Triple(c, x, 0f)
            1 -> Triple(x, c, 0f)
            2 -> Triple(0f, c, x)
            3 -> Triple(0f, x, c)
            4 -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        
        val r = ((r1 + m) * 255f).toInt().coerceIn(0, 255)
        val g = ((g1 + m) * 255f).toInt().coerceIn(0, 255)
        val b = ((b1 + m) * 255f).toInt().coerceIn(0, 255)
        
        return Triple(r, g, b)
    }
    
    /**
     * Calculate distance between two HSV colors
     */
    private fun hsvDistance(hsv1: Triple<Float, Float, Float>, hsv2: Triple<Float, Float, Float>): Float {
        val (h1, s1, v1) = hsv1
        val (h2, s2, v2) = hsv2
        
        // Handle hue wraparound (circular distance)
        val deltaH = minOf(kotlin.math.abs(h1 - h2), 360f - kotlin.math.abs(h1 - h2))
        val deltaS = kotlin.math.abs(s1 - s2)
        val deltaV = kotlin.math.abs(v1 - v2)
        
        // Weighted distance (hue is most important for color perception)
        val hueWeight = (deltaH / 360f * 2f)
        val satWeight = (deltaS * 1f)
        val valWeight = (deltaV * 0.5f)
        
        return kotlin.math.sqrt(
            hueWeight * hueWeight +
            satWeight * satWeight +
            valWeight * valWeight
        )
    }
    
    private fun runSimulatedDetection(bitmap: Bitmap): List<VehicleDetection> {
        // Simulate realistic vehicle detections for testing
        val detections = mutableListOf<VehicleDetection>()
        
        // Add a few simulated vehicles
        if (bitmap.width > 200 && bitmap.height > 200) {
            val timestamp = System.currentTimeMillis()
            
            // Car detection
            val carBbox = RectF(
                bitmap.width * 0.2f,
                bitmap.height * 0.3f,
                bitmap.width * 0.6f,
                bitmap.height * 0.7f
            )
            detections.add(
                VehicleDetection(
                    id = generateVehicleId(2, carBbox, timestamp, 0),
                    boundingBox = carBbox,
                    confidence = 0.85f,
                    classId = 2, // Car
                    className = "Car",
                    detectedColor = VehicleColor.BLUE, // Simulated blue color
                    secondaryColor = VehicleColor.WHITE, // Simulated secondary color
                    detectionTime = timestamp
                )
            )
            
            // Possible truck detection
            if (Math.random() > 0.5) {
                val truckBbox = RectF(
                    bitmap.width * 0.1f,
                    bitmap.height * 0.1f,
                    bitmap.width * 0.4f,
                    bitmap.height * 0.5f
                )
                detections.add(
                    VehicleDetection(
                        id = generateVehicleId(7, truckBbox, timestamp, 1),
                        boundingBox = truckBbox,
                        confidence = 0.72f,
                        classId = 7, // Truck
                        className = "Truck",
                        detectedColor = VehicleColor.RED, // Simulated red color
                        secondaryColor = VehicleColor.BLACK, // Simulated secondary color
                        detectionTime = timestamp
                    )
                )
            }
        }
        
        return detections
    }
    
    private fun getPerformanceStats(): Map<String, Long> {
        val stats = mutableMapOf<String, Long>()
        if (inferenceTimes.isNotEmpty()) {
            stats["VS_Inference"] = inferenceTimes.takeLast(10).average().toLong()
            stats["VS_Pre-process"] = preprocessingTimes.takeLast(10).average().toLong()
            stats["VS_Post-process"] = postprocessingTimes.takeLast(10).average().toLong()
        }
        stats["VS_Conf_Threshold"] = (CONFIDENCE_THRESHOLD * 100).toLong()
        
        // Limit lists to avoid memory leak
        if (inferenceTimes.size > 100) {
            inferenceTimes.removeAt(0)
            preprocessingTimes.removeAt(0)
            postprocessingTimes.removeAt(0)
        }
        return stats
    }
    
    fun release() {
        interpreter?.close()
        interpreter = null
        gpuDelegate?.close()
        gpuDelegate = null
        isInitialized = false
        isUsingGpu = false
    }
}