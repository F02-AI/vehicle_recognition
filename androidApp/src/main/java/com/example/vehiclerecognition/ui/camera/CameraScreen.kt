package com.example.vehiclerecognition.ui.camera

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview as CameraXPreview
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.ZoomState
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import java.util.concurrent.Executors

// FR 1.1, FR 1.3, FR 1.4
@SuppressLint("ClickableViewAccessibility") // For PreviewView touch listener if added later
@Composable
fun ActualCameraView(
    modifier: Modifier = Modifier,
    cameraViewModel: CameraViewModel // To pass zoom changes and receive frames
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var cameraProvider: ProcessCameraProvider? by remember { mutableStateOf(null) }
    val previewView = remember { PreviewView(context).apply { controller = null } } // Disable default controller
    val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
    val desiredZoomRatio by cameraViewModel.desiredZoomRatio.collectAsState() // Used for display and initial/rebind zoom
    var currentCamera: Camera? by remember { mutableStateOf(null) }

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
                    // TODO: Frame processing
                    imageProxy.close()
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
                val initialLinearZoom = ratioToLinear(desiredZoomRatio, zoomState.minZoomRatio, zoomState.maxZoomRatio)
                camera.cameraControl.setLinearZoom(initialLinearZoom)
                Log.d("ActualCameraView", "Applied initial/rebind linear zoom: $initialLinearZoom for ratio: $desiredZoomRatio (minR: ${zoomState.minZoomRatio}, maxR: ${zoomState.maxZoomRatio})")
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

    AndroidView(
        factory = { previewView },
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) { // Pinch-to-Zoom gesture
                detectTransformGestures { _, _, gestureZoomDelta, _ ->
                    currentCamera?.let { cam -> // Ensure camera is not null
                        cam.cameraInfo.zoomState.value?.let { currentZoomState ->
                            // Read the latest desired ratio from ViewModel as the base for delta calculation
                            val latestDesiredRatio = cameraViewModel.desiredZoomRatio.value 
                            var newRatio = latestDesiredRatio * gestureZoomDelta
                            
                            // Coerce based on actual camera capabilities
                            newRatio = newRatio.coerceIn(currentZoomState.minZoomRatio, currentZoomState.maxZoomRatio)
                            
                            // Update ViewModel state. This is important for the UI text and for
                            // the LaunchedEffect above to pick up the correct zoom if the camera rebinds.
                            cameraViewModel.onZoomRatioChanged(newRatio)

                            // Apply zoom directly to the camera control for real-time effect
                            val linearZoom = ratioToLinear(newRatio, currentZoomState.minZoomRatio, currentZoomState.maxZoomRatio)
                            cam.cameraControl.setLinearZoom(linearZoom)
                            // Log.d("ActualCameraView_Gesture", "Gesture applied linear zoom: $linearZoom for ratio: $newRatio")
                        }
                    } ?: run {
                        // Fallback if currentCamera or zoomState is not available yet
                        // This path should ideally not be hit frequently if camera binding is stable
                        Log.w("ActualCameraView_Gesture", "currentCamera or zoomState not available during gesture. Applying simple zoom.")
                        val currentRatio = cameraViewModel.desiredZoomRatio.value
                        // Apply a more generic coercion if specific limits aren't known
                        val newRatioFallback = (currentRatio * gestureZoomDelta).coerceIn(1.0f, 10.0f) 
                        cameraViewModel.onZoomRatioChanged(newRatioFallback)
                        // Optionally attempt to set zoom on currentCamera if it exists, even if zoomState was null
                        currentCamera?.cameraControl?.setZoomRatio(newRatioFallback)
                    }
                }
            }
    )
}

private fun ratioToLinear(ratio: Float, minRatio: Float, maxRatio: Float): Float {
    if (minRatio == maxRatio || ratio <= minRatio) return 0f
    if (ratio >= maxRatio) return 1f
    return (ratio - minRatio) / (maxRatio - minRatio)
}

@OptIn(ExperimentalMaterial3Api::class)
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
            TopAppBar(title = { Text("Vehicle Recognition") })
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

            if (hasCameraPermission) {
                Row(modifier = Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Button(onClick = { viewModel.simulateLPDetection("12-345-67") }) {
                        Text("Match")
                    }
                    Button(onClick = { viewModel.simulateLPDetection("00-000-00") }) {
                        Text("No Match")
                    }
                }
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