package com.example.vehiclerecognition.ui.camera

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color as AndroidColor
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect as AndroidRect
import android.graphics.YuvImage
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview as CameraXPreview
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.ZoomState
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect as ComposeRect
import android.graphics.RectF as AndroidRectF
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.vehiclerecognition.data.models.PlateDetection
import com.example.vehiclerecognition.data.models.VehicleDetection
import com.example.vehiclerecognition.data.models.LicensePlateSettings
import com.example.vehiclerecognition.ml.detection.LicensePlateDetector
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.concurrent.Executors
import android.media.MediaPlayer
import android.net.Uri
import android.widget.VideoView
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.DisposableEffect
import android.widget.MediaController
import android.view.SurfaceView
import android.view.SurfaceHolder
import android.graphics.SurfaceTexture
import android.view.TextureView

/**
 * Converts an Android Graphics RectF to a Compose Geometry Rect.
 * This is a placeholder implementation and might need adjustments based on the
 * camera preview's scaling and cropping behavior (`ScaleType`).
 *
 * @param viewWidth The width of the view where the rectangle will be drawn.
 * @param viewHeight The height of the view where the rectangle will be drawn.
 * @param imageWidth The width of the image from which the rectangle was derived.
 * @param imageHeight The height of the image from which the rectangle was derived.
 * @param imageRotation The rotation of the image in degrees.
 * @return A [ComposeRect] scaled to the view's dimensions.
 */
fun AndroidRectF.toComposeRect(
    viewWidth: Float,
    viewHeight: Float,
    imageWidth: Int,
    imageHeight: Int,
    imageRotation: Int
): ComposeRect {
    if (imageWidth <= 0 || imageHeight <= 0) {
        return ComposeRect(0f, 0f, 0f, 0f)
    }

    // Since the image passed to the model is now always upright, the effective
    // image dimensions are the same as the actual image dimensions.
    val effectiveImageWidth = imageWidth
    val effectiveImageHeight = imageHeight

    // Determine the scale factors to fit the effective image into the view
    val scaleX = viewWidth / effectiveImageWidth.toFloat()
    val scaleY = viewHeight / effectiveImageHeight.toFloat()
    val scale = maxOf(scaleX, scaleY) // Use maxOf for FILL_CENTER scaling

    // Calculate the dimensions of the scaled, centered image within the view
    val scaledImageWidth = effectiveImageWidth.toFloat() * scale
    val scaledImageHeight = effectiveImageHeight.toFloat() * scale
    val offsetX = (viewWidth - scaledImageWidth) / 2f
    val offsetY = (viewHeight - scaledImageHeight) / 2f
    
    // The bounding box is already in the correct orientation.
    // We just need to scale and offset it.
    return ComposeRect(
        left = left * scale + offsetX,
        top = top * scale + offsetY,
        right = right * scale + offsetX,
        bottom = bottom * scale + offsetY
    )
}

/**
 * Converts an ImageProxy to a Bitmap.
 *
 * This function is necessary because the camera provides frames in ImageProxy format,
 * but the license plate detection model requires a Bitmap as input. It handles the
 * YUV_420_888 format by converting it to a JPEG, which is then decoded into a Bitmap.
 *
 * @param image The [ImageProxy] to convert.
 * @return The converted [Bitmap], or `null` if conversion fails.
 */
@ExperimentalGetImage
fun imageProxyToBitmap(image: ImageProxy): Bitmap? {
    if (image.format != ImageFormat.YUV_420_888) {
        Log.e("imageProxyToBitmap", "Unsupported image format: ${image.format}")
        return null
    }
    
    val yBuffer = image.planes[0].buffer
    val uBuffer = image.planes[1].buffer
    val vBuffer = image.planes[2].buffer

    val ySize = yBuffer.remaining()
    val uSize = uBuffer.remaining()
    val vSize = vBuffer.remaining()

    val nv21 = ByteArray(ySize + uSize + vSize)

    //U and V are swapped
    yBuffer.get(nv21, 0, ySize)
    vBuffer.get(nv21, ySize, vSize)
    uBuffer.get(nv21, ySize + vSize, uSize)

    val yuvImage = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
    val out = ByteArrayOutputStream()
    yuvImage.compressToJpeg(AndroidRect(0, 0, image.width, image.height), 100, out)
    val imageBytes = out.toByteArray()
    val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)

    // Rotate the bitmap according to the ImageProxy's rotation degrees.
    // This ensures the model always receives an upright image.
    val rotationDegrees = image.imageInfo.rotationDegrees
    return if (rotationDegrees != 0) {
        val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
        Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    } else {
        bitmap
    }
}

/**
 * Extension function to convert a ByteBuffer to a ByteArray.
 */
fun ByteBuffer.toByteArray(): ByteArray {
    rewind() // Rewind the buffer to start from the beginning
    val data = ByteArray(remaining())
    get(data) // Copy the buffer into a byte array
    return data // Return the byte array
}

/**
 * Debug video player component that plays a test video instead of camera feed
 */
@Composable
fun DebugVideoPlayer(
    cameraViewModel: CameraViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var videoWidth by remember { mutableStateOf(0) }
    var videoHeight by remember { mutableStateOf(0) }
    var containerWidth by remember { mutableStateOf(0) }
    var containerHeight by remember { mutableStateOf(0) }
    var displayWidth by remember { mutableStateOf(0) }
    var displayHeight by remember { mutableStateOf(0) }
    
    DisposableEffect(Unit) {
        onDispose {
            mediaPlayer?.release()
        }
    }
    
    // Calculate aspect ratio and dimensions
    val videoAspectRatio = if (videoWidth > 0 && videoHeight > 0) {
        videoWidth.toFloat() / videoHeight.toFloat()
    } else 16f / 9f // Default aspect ratio
    
    val containerAspectRatio = if (containerWidth > 0 && containerHeight > 0) {
        containerWidth.toFloat() / containerHeight.toFloat()
    } else 1f
    
    // Calculate the actual display size and position of the video within the container
    val (newDisplayWidth, newDisplayHeight) = if (videoAspectRatio > containerAspectRatio) {
        // Video is wider - fit to container width
        containerWidth to (containerWidth / videoAspectRatio).toInt()
    } else {
        // Video is taller - fit to container height
        (containerHeight * videoAspectRatio).toInt() to containerHeight
    }
    
    Log.d("DebugVideoPlayer", "Container: ${containerWidth}x${containerHeight}, Video: ${videoWidth}x${videoHeight}")
    Log.d("DebugVideoPlayer", "Video aspect: $videoAspectRatio, Container aspect: $containerAspectRatio")
    Log.d("DebugVideoPlayer", "Calculated display: ${newDisplayWidth}x${newDisplayHeight}")
    
    // Update display dimensions when they change
    LaunchedEffect(newDisplayWidth, newDisplayHeight, containerWidth, containerHeight) {
        if (newDisplayWidth > 0 && newDisplayHeight > 0) {
            displayWidth = newDisplayWidth
            displayHeight = newDisplayHeight
            // Notify the CameraViewModel about the actual video display dimensions
            cameraViewModel.updateVideoDisplayDimensions(displayWidth, displayHeight)
            Log.d("DebugVideoPlayer", "Updated display dimensions: ${displayWidth}x${displayHeight}")
        }
    }
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { coordinates ->
                containerWidth = coordinates.size.width
                containerHeight = coordinates.size.height
            },
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            factory = { ctx ->
                TextureView(ctx).apply {
                    var lastProcessTime = 0L
                    val processInterval = 500L // Process every 500ms
                    
                    surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                        override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                            Log.d("DebugVideoPlayer", "Surface texture available: ${width}x${height}")
                            mediaPlayer = setupMediaPlayer(ctx, android.view.Surface(surface)) { w, h ->
                                videoWidth = w
                                videoHeight = h
                            }
                        }
                        
                        override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
                            Log.d("DebugVideoPlayer", "Surface texture size changed: ${width}x${height}")
                        }
                        
                        override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                            Log.d("DebugVideoPlayer", "Surface texture destroyed")
                            mediaPlayer?.release()
                            mediaPlayer = null
                            return true
                        }
                        
                        override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
                            // Throttle frame processing to avoid overwhelming the system
                            val currentTime = System.currentTimeMillis()
                            if (currentTime - lastProcessTime >= processInterval) {
                                lastProcessTime = currentTime
                                
                                // Extract frame for license plate detection
                                val originalBitmap = getBitmap()
                                originalBitmap?.let { original ->
                                    // Create a copy of the bitmap to avoid recycling issues with TensorFlow Lite
                                    // The TensorFlow Lite processing happens asynchronously and the original bitmap
                                    // might be recycled before the processing is complete
                                    val bitmapCopy = original.copy(original.config ?: Bitmap.Config.ARGB_8888, false)
                                    
                                    Log.d("DebugVideoPlayer", "Processing frame: ${bitmapCopy.width}x${bitmapCopy.height}")
                                    cameraViewModel.processCameraFrame(bitmapCopy, 0)
                                    
                                    // Recycle the original bitmap from TextureView since we've made a copy
                                    original.recycle()
                                    
                                    // Note: The copy will be recycled by the TensorFlow Lite processing chain
                                    // after it's done with the inference
                                }
                            }
                        }
                    }
                }
            },
            modifier = Modifier.size(
                width = with(LocalDensity.current) { displayWidth.toDp() },
                height = with(LocalDensity.current) { displayHeight.toDp() }
            )
        )
    }
}

/**
 * Sets up MediaPlayer with the video file
 */
private fun setupMediaPlayer(
    context: Context, 
    surface: android.view.Surface, 
    onVideoSizeChanged: (Int, Int) -> Unit
): MediaPlayer? {
    return try {
        MediaPlayer().apply {
            setSurface(surface)
            
            // Load video from assets
            val assetFileDescriptor = context.assets.openFd("videos/test_video.mp4")
            setDataSource(
                assetFileDescriptor.fileDescriptor,
                assetFileDescriptor.startOffset,
                assetFileDescriptor.declaredLength
            )
            assetFileDescriptor.close()
            
            isLooping = true
            setVideoScalingMode(MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT)
            
            setOnPreparedListener { mp ->
                Log.d("DebugVideoPlayer", "MediaPlayer prepared")
                Log.d("DebugVideoPlayer", "Video size: ${mp.videoWidth}x${mp.videoHeight}")
                onVideoSizeChanged(mp.videoWidth, mp.videoHeight)
                mp.start()
            }
            
            setOnErrorListener { _, what, extra ->
                Log.e("DebugVideoPlayer", "MediaPlayer error: what=$what, extra=$extra")
                false
            }
            
            setOnVideoSizeChangedListener { _, width, height ->
                Log.d("DebugVideoPlayer", "Video size changed: ${width}x${height}")
                onVideoSizeChanged(width, height)
            }
            
            prepareAsync()
        }
    } catch (e: Exception) {
        Log.e("DebugVideoPlayer", "Error creating MediaPlayer", e)
        null
    }
}

// FR 1.1, FR 1.3, FR 1.4
@SuppressLint("ClickableViewAccessibility") // For PreviewView touch listener if added later
@androidx.camera.core.ExperimentalGetImage
@Suppress("UnsafeOptInUsageError")
@Composable
fun ActualCameraView(
    modifier: Modifier = Modifier,
    cameraViewModel: CameraViewModel // To pass zoom changes and receive frames
) {
    // Check if debug video mode is enabled
    val licensePlateSettings by cameraViewModel.licensePlateSettings.collectAsState(initial = LicensePlateSettings())
    
    if (licensePlateSettings.enableDebugVideo) {
        // Show debug video instead of camera
        Box(modifier = modifier.fillMaxSize()) {
            DebugVideoPlayer(
                cameraViewModel = cameraViewModel,
                modifier = Modifier.fillMaxSize()
            )
            
            // Still show the detection overlay
            val detectedPlates by cameraViewModel.detectedPlates.collectAsState()
            val detectedVehicles by cameraViewModel.detectedVehicles.collectAsState()
            val performanceMetrics by cameraViewModel.performanceMetrics.collectAsState()
            val vehiclePerformanceMetrics by cameraViewModel.vehiclePerformanceMetrics.collectAsState()
            val totalDetections by cameraViewModel.totalDetections.collectAsState()
            val totalVehicleDetections by cameraViewModel.totalVehicleDetections.collectAsState()
            val rawOutputLog by cameraViewModel.rawOutputLog.collectAsState()
            val vehicleRawOutputLog by cameraViewModel.vehicleRawOutputLog.collectAsState()
            val frameWidth by cameraViewModel.frameWidth.collectAsState()
            val frameHeight by cameraViewModel.frameHeight.collectAsState()
            val frameRotation by cameraViewModel.frameRotation.collectAsState()
            val videoDisplayWidth by cameraViewModel.videoDisplayWidth.collectAsState()
            val videoDisplayHeight by cameraViewModel.videoDisplayHeight.collectAsState()
            
            val showDebugInfo by cameraViewModel.showDebugInfo.collectAsState()

            if (showDebugInfo) {
                // Debug mode indicator
                Text(
                    text = "DEBUG VIDEO MODE",
                    color = Color.Red,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(16.dp)
                        .background(Color.Black.copy(alpha = 0.7f))
                        .padding(8.dp)
                )

                // Always show debug info in video mode
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp)
                        .background(Color.Black.copy(alpha = 0.8f))
                        .padding(8.dp)
                ) {
                    Text(
                        text = "Frame: ${frameWidth}x${frameHeight}",
                        color = Color.White,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "Display: ${videoDisplayWidth}x${videoDisplayHeight}",
                        color = Color.White,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "LP Detections: ${detectedPlates.size}",
                        color = Color.White,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "Vehicle Detections: ${detectedVehicles.size}",
                        color = Color.White,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "Total LP: $totalDetections",
                        color = Color.White,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "Total Vehicles: $totalVehicleDetections",
                        color = Color.White,
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (performanceMetrics.isNotEmpty()) {
                        Text(
                            text = "LP Performance:",
                            color = Color.Green,
                            style = MaterialTheme.typography.bodySmall
                        )
                        performanceMetrics.forEach { (key, value) ->
                            Text(
                                text = "  $key: ${value}ms",
                                color = Color.White,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    if (vehiclePerformanceMetrics.isNotEmpty()) {
                        Text(
                            text = "Vehicle Performance:",
                            color = Color.Blue,
                            style = MaterialTheme.typography.bodySmall
                        )
                        vehiclePerformanceMetrics.forEach { (key, value) ->
                            Text(
                                text = "  $key: ${value}ms",
                                color = Color.White,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    if (rawOutputLog.isNotEmpty()) {
                        Text(
                            text = "LP Output: $rawOutputLog",
                            color = if (rawOutputLog == "Error") Color.Red else Color.Yellow,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    if (vehicleRawOutputLog.isNotEmpty()) {
                        Text(
                            text = "Vehicle Output: $vehicleRawOutputLog",
                            color = if (vehicleRawOutputLog == "Error") Color.Red else Color.Cyan,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    // Add more debug info
                    Text(
                        text = "Settings: enableDebugVideo=${licensePlateSettings.enableDebugVideo}",
                        color = Color.Cyan,
                        style = MaterialTheme.typography.bodySmall
                    )

                    Text(
                        text = "Confidence: ${(licensePlateSettings.minConfidenceThreshold * 100).toInt()}%",
                        color = Color.Cyan,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            
            // Draw detection overlays - only if we have valid display dimensions
            if (videoDisplayWidth > 0 && videoDisplayHeight > 0) {
                Box(
                    modifier = Modifier
                        .size(
                            width = with(LocalDensity.current) { videoDisplayWidth.toDp() },
                            height = with(LocalDensity.current) { videoDisplayHeight.toDp() }
                        )
                        .align(Alignment.Center)
                        .onGloballyPositioned { coordinates ->
                            Log.d("DebugVideoPlayer", "Overlay Box size: ${coordinates.size.width}x${coordinates.size.height}")
                        }
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        Log.d("DebugVideoPlayer", "Canvas size: ${size.width}x${size.height}")
                        
                        // Draw license plate detections
                        detectedPlates.forEach { plate ->
                            // Use the actual video display dimensions for coordinate transformation
                            val rect = plate.boundingBox.toComposeRect(
                                size.width,
                                size.height,
                                frameWidth,
                                frameHeight,
                                frameRotation
                            )
                            
                            // Draw the bounding box in green for license plates
                            drawRect(
                                color = Color.Green,
                                topLeft = rect.topLeft,
                                size = rect.size,
                                style = Stroke(width = 3.dp.toPx())
                            )
                        }
                        
                        // Draw vehicle detections
                        detectedVehicles.forEach { vehicle ->
                            val rect = vehicle.boundingBox.toComposeRect(
                                size.width,
                                size.height,
                                frameWidth,
                                frameHeight,
                                frameRotation
                            )
                            
                            // Choose color based on vehicle type
                            val vehicleColor = when (vehicle.classId) {
                                2 -> Color.Blue    // Car - Blue
                                3 -> Color.Cyan    // Motorcycle - Cyan
                                5 -> Color.Yellow  // Bus - Yellow
                                7 -> Color.Red     // Truck - Red
                                else -> Color.Magenta // Unknown - Magenta
                            }
                            
                            // Draw the bounding box
                            drawRect(
                                color = vehicleColor,
                                topLeft = rect.topLeft,
                                size = rect.size,
                                style = Stroke(width = 4.dp.toPx())
                            )
                        }
                    }
                }
            }
        }
        return
    }
    
    // Original camera implementation
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var cameraProvider: ProcessCameraProvider? by remember { mutableStateOf(null) }
    val previewView = remember { PreviewView(context).apply { controller = null } } // Disable default controller
    val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
    val desiredZoomRatio by cameraViewModel.desiredZoomRatio.collectAsState() // Used for display and initial/rebind zoom
    var currentCamera: Camera? by remember { mutableStateOf(null) }

    // License plate detection states
    val detectedPlates by cameraViewModel.detectedPlates.collectAsState()
    val detectedVehicles by cameraViewModel.detectedVehicles.collectAsState()
    val performanceMetrics by cameraViewModel.performanceMetrics.collectAsState()
    val vehiclePerformanceMetrics by cameraViewModel.vehiclePerformanceMetrics.collectAsState()
    val totalDetections by cameraViewModel.totalDetections.collectAsState()
    val totalVehicleDetections by cameraViewModel.totalVehicleDetections.collectAsState()
    val rawOutputLog by cameraViewModel.rawOutputLog.collectAsState()
    val vehicleRawOutputLog by cameraViewModel.vehicleRawOutputLog.collectAsState()
    val frameWidth by cameraViewModel.frameWidth.collectAsState()
    val frameHeight by cameraViewModel.frameHeight.collectAsState()
    val frameRotation by cameraViewModel.frameRotation.collectAsState()

    // Effect to bind camera and set initial/rebind zoom
    LaunchedEffect(cameraProvider, cameraSelector, lifecycleOwner) { // Added lifecycleOwner
        val localCameraProvider = cameraProvider ?: return@LaunchedEffect

        Log.d("ActualCameraView", "Camera binding effect triggered. Provider: $localCameraProvider")
        localCameraProvider.unbindAll() // Unbind all use cases before rebinding

        val preview = CameraXPreview.Builder().build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }

        val imageAnalyzer = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also {
                it.setAnalyzer(Executors.newSingleThreadExecutor()) { imageProxy ->
                    // Convert ImageProxy to Bitmap and process for license plate detection
                    try {
                        // The imageProxyToBitmap function now handles rotation, so the bitmap
                        // is always upright. We pass 0 for rotationDegrees.
                        val bitmap = imageProxyToBitmap(imageProxy)
                        if (bitmap != null) {
                            cameraViewModel.processCameraFrame(bitmap, 0)
                        }
                    } catch (e: Exception) {
                        Log.e("ActualCameraView", "Error processing frame for license plate detection", e)
                    } finally {
                        imageProxy.close()
                    }
                }
            }
        
        val useCaseGroup = UseCaseGroup.Builder()
            .addUseCase(preview)
            .addUseCase(imageAnalyzer)
            .build()

        try {
            val camera = localCameraProvider.bindToLifecycle(
                lifecycleOwner, // Use the passed lifecycleOwner
                cameraSelector,
                useCaseGroup
            )
            currentCamera = camera // Store camera instance for gesture handling
            Log.d("ActualCameraView", "Camera bound successfully. Current camera set.")

            // Apply ViewModel's zoom ratio when camera is (re)bound
            camera.cameraInfo.zoomState.value?.let { zoomState ->
                val clampedZoomRatio = desiredZoomRatio.coerceIn(zoomState.minZoomRatio, zoomState.maxZoomRatio)
                camera.cameraControl.setZoomRatio(clampedZoomRatio)
                Log.d("ActualCameraView", "Applied initial/rebind zoom ratio: $clampedZoomRatio (desired: $desiredZoomRatio, minR: ${zoomState.minZoomRatio}, maxR: ${zoomState.maxZoomRatio})")
                
                // Update ViewModel if we had to clamp the zoom
                if (clampedZoomRatio != desiredZoomRatio) {
                    cameraViewModel.onZoomRatioChanged(clampedZoomRatio)
                }
            } ?: Log.d("ActualCameraView", "ZoomState not available at bind time for initial zoom.")

        } catch (exc: Exception) {
            Log.e("ActualCameraView", "Use case binding failed", exc)
            currentCamera = null // Reset on failure
        }
    }

    // Request CameraProvider
    LaunchedEffect(Unit) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
            Log.d("ActualCameraView", "CameraProvider obtained: $cameraProvider")
        }, ContextCompat.getMainExecutor(context))
    }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { coordinates ->
                    // Track the actual PreviewView dimensions for coordinate transformation
                    cameraViewModel.updateCameraPreviewDimensions(
                        coordinates.size.width,
                        coordinates.size.height
                    )
                    Log.d("ActualCameraView", "PreviewView size: ${coordinates.size.width}x${coordinates.size.height}")
                }
                .pointerInput(Unit) { // Pinch-to-Zoom gesture
                    detectTransformGestures { _, _, gestureZoomDelta, _ ->
                        currentCamera?.let { cam ->
                            cam.cameraInfo.zoomState.value?.let { currentZoomState ->
                                // Get current zoom ratio from camera state (more accurate than ViewModel)
                                val currentZoomRatio = currentZoomState.zoomRatio
                                
                                // Apply gesture delta to current zoom
                                val newZoomRatio = currentZoomRatio * gestureZoomDelta
                                
                                // Coerce to camera's actual capabilities
                                val clampedZoomRatio = newZoomRatio.coerceIn(
                                    currentZoomState.minZoomRatio, 
                                    currentZoomState.maxZoomRatio
                                )
                                
                                // Update ViewModel for UI display
                                cameraViewModel.onZoomRatioChanged(clampedZoomRatio)
                                
                                // Apply zoom directly using setZoomRatio (more direct than linear conversion)
                                cam.cameraControl.setZoomRatio(clampedZoomRatio)
                                
                                Log.d("ActualCameraView_Gesture", "Applied zoom ratio: $clampedZoomRatio (min: ${currentZoomState.minZoomRatio}, max: ${currentZoomState.maxZoomRatio})")
                            }
                        } ?: run {
                            // Fallback if camera state not available
                            Log.w("ActualCameraView_Gesture", "Camera or zoomState not available during gesture")
                            val currentRatio = cameraViewModel.desiredZoomRatio.value
                            val newRatio = (currentRatio * gestureZoomDelta).coerceIn(1.0f, 10.0f)
                            cameraViewModel.onZoomRatioChanged(newRatio)
                            currentCamera?.cameraControl?.setZoomRatio(newRatio)
                        }
                    }
                }
        )
        
        // Detection overlay
        val cameraPreviewWidth by cameraViewModel.cameraPreviewWidth.collectAsState()
        val cameraPreviewHeight by cameraViewModel.cameraPreviewHeight.collectAsState()
        val gpuStatus by cameraViewModel.gpuStatus.collectAsState()
        
        // Only show overlay if we have valid preview dimensions
        if (cameraPreviewWidth > 0 && cameraPreviewHeight > 0 && frameWidth > 0 && frameHeight > 0) {
            val showDebugInfo by cameraViewModel.showDebugInfo.collectAsState()
            DetectionOverlay(
                detectedPlates = detectedPlates,
                detectedVehicles = detectedVehicles,
                performanceMetrics = performanceMetrics,
                vehiclePerformanceMetrics = vehiclePerformanceMetrics,
                totalDetections = totalDetections,
                totalVehicleDetections = totalVehicleDetections,
                rawOutputLog = rawOutputLog,
                vehicleRawOutputLog = vehicleRawOutputLog,
                gpuStatus = gpuStatus,
                ocrEnabled = licensePlateSettings.enableOcr,
                modifier = Modifier.fillMaxSize(),
                imageWidth = frameWidth,
                imageHeight = frameHeight,
                imageRotation = frameRotation,
                showDebugInfo = showDebugInfo
            )
        }
    }
}

@OptIn(ExperimentalTextApi::class)
@Composable
fun DetectionOverlay(
    detectedPlates: List<PlateDetection>,
    detectedVehicles: List<VehicleDetection>,
    performanceMetrics: Map<String, Long>,
    vehiclePerformanceMetrics: Map<String, Long>,
    totalDetections: Int,
    totalVehicleDetections: Int,
    rawOutputLog: String,
    vehicleRawOutputLog: String,
    gpuStatus: Map<String, Boolean>,
    ocrEnabled: Boolean,
    modifier: Modifier = Modifier,
    imageWidth: Int,
    imageHeight: Int,
    imageRotation: Int,
    showDebugInfo: Boolean
) {
    val textMeasurer = rememberTextMeasurer()
    
    // Variables to store canvas dimensions for use in performance metrics
    var canvasWidth by remember { mutableStateOf(0f) }
    var canvasHeight by remember { mutableStateOf(0f) }

    Box(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            Log.d("DetectionOverlay", "Canvas size: ${size.width}x${size.height}")
            
            // Update canvas dimensions
            canvasWidth = size.width
            canvasHeight = size.height
            
            if (showDebugInfo) {
                // Draw corner markers to show canvas bounds
                val cornerSize = 20.dp.toPx()

                // Top-left corner
                drawRect(
                    color = Color.Red,
                    topLeft = androidx.compose.ui.geometry.Offset(0f, 0f),
                    size = androidx.compose.ui.geometry.Size(cornerSize, cornerSize),
                    style = Stroke(width = 3.dp.toPx())
                )

                // Top-right corner
                drawRect(
                    color = Color.Red,
                    topLeft = androidx.compose.ui.geometry.Offset(canvasWidth - cornerSize, 0f),
                    size = androidx.compose.ui.geometry.Size(cornerSize, cornerSize),
                    style = Stroke(width = 3.dp.toPx())
                )

                // Bottom-left corner
                drawRect(
                    color = Color.Red,
                    topLeft = androidx.compose.ui.geometry.Offset(0f, canvasHeight - cornerSize),
                    size = androidx.compose.ui.geometry.Size(cornerSize, cornerSize),
                    style = Stroke(width = 3.dp.toPx())
                )

                // Bottom-right corner
                drawRect(
                    color = Color.Red,
                    topLeft = androidx.compose.ui.geometry.Offset(canvasWidth - cornerSize, canvasHeight - cornerSize),
                    size = androidx.compose.ui.geometry.Size(cornerSize, cornerSize),
                    style = Stroke(width = 3.dp.toPx())
                )
            }
            
            // Draw license plate bounding boxes and recognized text
            detectedPlates.forEach { plate ->
                val originalRect = plate.boundingBox
                val rect = plate.boundingBox.toComposeRect(
                    size.width,
                    size.height,
                    imageWidth,
                    imageHeight,
                    imageRotation
                )
                
                // Draw the bounding box in green for license plates
                drawRect(
                    color = Color.Green,
                    topLeft = rect.topLeft,
                    size = rect.size,
                    style = Stroke(width = 2.dp.toPx())
                )

                // Draw the recognized text above the box
                plate.recognizedText?.let { text ->
                    val textLayoutResult = textMeasurer.measure(
                        text = AnnotatedString(text),
                        style = TextStyle(
                            color = Color.White,
                            fontSize = 16.sp,
                            background = Color.Black.copy(alpha = 0.7f)
                        )
                    )
                    drawText(
                        textLayoutResult = textLayoutResult,
                        topLeft = androidx.compose.ui.geometry.Offset(rect.left, rect.top - textLayoutResult.size.height - 4.dp.toPx())
                    )
                }
            }
            
            // Draw vehicle detection bounding boxes
            detectedVehicles.forEach { vehicle ->
                val rect = vehicle.boundingBox.toComposeRect(
                    size.width,
                    size.height,
                    imageWidth,
                    imageHeight,
                    imageRotation
                )
                
                // Choose color based on vehicle type
                val vehicleColor = when (vehicle.classId) {
                    2 -> Color.Blue    // Car - Blue
                    3 -> Color.Cyan    // Motorcycle - Cyan
                    5 -> Color.Yellow  // Bus - Yellow
                    7 -> Color.Red     // Truck - Red
                    else -> Color.Magenta // Unknown - Magenta
                }
                
                // Draw the bounding box
                drawRect(
                    color = vehicleColor,
                    topLeft = rect.topLeft,
                    size = rect.size,
                    style = Stroke(width = 3.dp.toPx())
                )

                // Draw the vehicle class label above the box
                val labelText = "${vehicle.className} (${(vehicle.confidence * 100).toInt()}%)"
                val textLayoutResult = textMeasurer.measure(
                    text = AnnotatedString(labelText),
                    style = TextStyle(
                        color = Color.White,
                        fontSize = 14.sp,
                        background = vehicleColor.copy(alpha = 0.8f)
                    )
                )
                drawText(
                    textLayoutResult = textLayoutResult,
                    topLeft = androidx.compose.ui.geometry.Offset(rect.left, rect.top - textLayoutResult.size.height - 4.dp.toPx())
                )
            }
        }

        // Draw performance metrics with coordinate debugging information
        if (showDebugInfo && (performanceMetrics.isNotEmpty() || vehiclePerformanceMetrics.isNotEmpty() || detectedPlates.isNotEmpty() || detectedVehicles.isNotEmpty())) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                verticalArrangement = Arrangement.Bottom,
                horizontalAlignment = Alignment.Start
            ) {
                val lpMetricsText = performanceMetrics.entries.joinToString("\n") {
                    "LP ${it.key}: ${it.value} ms"
                }
                val vehicleMetricsText = vehiclePerformanceMetrics.entries.joinToString("\n") {
                    "V ${it.key}: ${it.value} ms"
                }
                val debugInfo = LicensePlateDetector.lastDebugInfo
                
                // Camera feed coordinates
                val cameraFeedCoords = """
                    CAMERA FEED CORNERS:
                    Canvas Size: ${canvasWidth.toInt()}x${canvasHeight.toInt()}
                    TL: (0, 0)
                    TR: (${canvasWidth.toInt()}, 0)
                    BL: (0, ${canvasHeight.toInt()})
                    BR: (${canvasWidth.toInt()}, ${canvasHeight.toInt()})
                """.trimIndent()
                
                // License Plate Detection coordinates
                val lpDetectionCoords = if (detectedPlates.isNotEmpty()) {
                    val plate = detectedPlates.first()
                    val originalRect = plate.boundingBox
                    val actualTransformedRect = plate.boundingBox.toComposeRect(
                        canvasWidth,
                        canvasHeight,
                        imageWidth,
                        imageHeight,
                        imageRotation
                    )
                    """
                    
                    LP DETECTION COORDINATES:
                    Image: ${imageWidth}x${imageHeight} (rotation: ${imageRotation}°)
                    Original Rect: (${originalRect.left.format(1)}, ${originalRect.top.format(1)}) to (${originalRect.right.format(1)}, ${originalRect.bottom.format(1)})
                    Canvas Transformed: (${actualTransformedRect.left.format(1)}, ${actualTransformedRect.top.format(1)}) to (${actualTransformedRect.right.format(1)}, ${actualTransformedRect.bottom.format(1)})
                    Confidence: ${plate.confidence.format(3)}
                    Rect Size: ${(actualTransformedRect.right - actualTransformedRect.left).format(1)} x ${(actualTransformedRect.bottom - actualTransformedRect.top).format(1)}
                    """.trimIndent()
                } else {
                    "\n\nNO LP DETECTIONS"
                }
                
                // Vehicle Detection coordinates
                val vehicleDetectionCoords = if (detectedVehicles.isNotEmpty()) {
                    val vehicle = detectedVehicles.first()
                    val originalRect = vehicle.boundingBox
                    val actualTransformedRect = vehicle.boundingBox.toComposeRect(
                        canvasWidth,
                        canvasHeight,
                        imageWidth,
                        imageHeight,
                        imageRotation
                    )
                    """
                    
                    VEHICLE DETECTION COORDINATES:
                    Class: ${vehicle.className} (ID: ${vehicle.classId})
                    Original Rect: (${originalRect.left.format(1)}, ${originalRect.top.format(1)}) to (${originalRect.right.format(1)}, ${originalRect.bottom.format(1)})
                    Canvas Transformed: (${actualTransformedRect.left.format(1)}, ${actualTransformedRect.top.format(1)}) to (${actualTransformedRect.right.format(1)}, ${actualTransformedRect.bottom.format(1)})
                    Confidence: ${vehicle.confidence.format(3)}
                    Rect Size: ${(actualTransformedRect.right - actualTransformedRect.left).format(1)} x ${(actualTransformedRect.bottom - actualTransformedRect.top).format(1)}
                    """.trimIndent()
                } else {
                    "\n\nNO VEHICLE DETECTIONS"
                }
                
                // GPU Status Information
                val gpuStatusText = if (gpuStatus.isNotEmpty()) {
                    val gpuLines = gpuStatus.map { (engine, isUsing) -> 
                        "$engine: ${if (isUsing) "✓ GPU" else "✗ CPU"}"
                    }
                    "\n\nGPU ACCELERATION STATUS:\n" + gpuLines.joinToString("\n")
                } else {
                    "\n\nGPU STATUS: Not Available"
                }
                
                // OCR Status Information
                val ocrStatusText = "\n\nOCR STATUS: ${if (ocrEnabled) "✓ ENABLED" else "✗ DISABLED"}"
                
                val fullText = """
                    PERFORMANCE METRICS:
                    $lpMetricsText
                    $vehicleMetricsText
                    
                    DETECTION COUNTS:
                    Total LP Detections: $totalDetections
                    Total Vehicle Detections: $totalVehicleDetections
                    Debug: $debugInfo
                    
                    MODEL OUTPUTS:
                    LP TFLite: $rawOutputLog
                    Vehicle TFLite: $vehicleRawOutputLog
                    
                    $cameraFeedCoords
                    $lpDetectionCoords
                    $vehicleDetectionCoords
                    $gpuStatusText
                    $ocrStatusText
                """.trimIndent()
                
                Text(
                    text = fullText,
                    color = Color.White,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.8f))
                        .padding(8.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@androidx.camera.core.ExperimentalGetImage
@Suppress("UnsafeOptInUsageError")
@Composable
fun CameraScreen(
    viewModel: CameraViewModel // Injected
) {
    val matchFound by viewModel.matchFound.collectAsState()
    val desiredZoomRatio by viewModel.desiredZoomRatio.collectAsState()
    val context = LocalContext.current

    var hasCameraPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            hasCameraPermission = isGranted
            if (!isGranted) {
                Log.d("CameraScreen", "Camera permission denied by user.")
            }
        }
    )

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Vehicle Recognition") },
                actions = {
                    // Debug icon hidden as requested
                    // IconButton(onClick = { viewModel.toggleDebugInfo() }) {
                    //     Icon(
                    //         imageVector = if (viewModel.showDebugInfo.collectAsState().value) Icons.Filled.BugReport else Icons.Outlined.BugReport,
                    //         contentDescription = "Toggle Debug Info"
                    //     )
                    // }
                }
            )
        }
    ) { paddingValues ->
        Column(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
            if (hasCameraPermission) {
                Box(modifier = Modifier.weight(1f)) {
                    ActualCameraView(
                        cameraViewModel = viewModel
                    )
                    
                    // Display current zoom ratio from ViewModel for user feedback
                    Text(
                        text = "Zoom: " + "%.1f".format(desiredZoomRatio) + "x",
                        color = Color.White,
                        modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp).background(Color.Black.copy(alpha = 0.5f)).padding(4.dp)
                    )
                    

                }
            } else {
                Box(modifier = Modifier.weight(1f).fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Text("Camera permission is required to use this app.", modifier = Modifier.padding(16.dp))
                        Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                            Text("Grant Permission")
                        }
                        if (!hasCameraPermission && !shouldShowRequestPermissionRationale(context as android.app.Activity)) {
                            Text("If denied, please grant permission in app settings.", modifier = Modifier.padding(top = 8.dp))
                        }
                    }
                }
            }

            if (matchFound && hasCameraPermission) {
                Text(
                    "MATCH FOUND!",
                    color = Color.Red,
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(8.dp)
                )
            }


        }
    }
}

// Helper to check if rationale should be shown (simplified)
fun shouldShowRequestPermissionRationale(activity: android.app.Activity): Boolean {
    return androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.CAMERA)
}

@Preview(showBackground = true)
@Composable
fun CameraScreenPreview() {
    MaterialTheme {
        Box(modifier = Modifier.fillMaxSize().background(Color.DarkGray)) {
            Text("Camera Screen Preview Area", modifier = Modifier.align(Alignment.Center))
        }
    }
}

/**
 * Extension function to format Float to specified decimal places
 */
fun Float.format(digits: Int) = "%.${digits}f".format(this) 