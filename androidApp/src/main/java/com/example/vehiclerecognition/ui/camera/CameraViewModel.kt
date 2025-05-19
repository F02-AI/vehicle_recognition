package com.example.vehiclerecognition.ui.camera

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.vehiclerecognition.domain.logic.VehicleMatcher
import com.example.vehiclerecognition.domain.platform.SoundAlertPlayer
import com.example.vehiclerecognition.domain.repository.SettingsRepository
import com.example.vehiclerecognition.model.DetectionMode
import com.example.vehiclerecognition.model.VehicleColor
import com.example.vehiclerecognition.model.VehicleType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay

/**
 * ViewModel for the Camera screen.
 * Handles camera operations, vehicle detection matching, and alerts.
 * (FR 1.1, FR 1.3, FR 1.4, FR 1.11, FR 1.12)
 *
 * @property vehicleMatcher Logic for matching detected vehicles against the watchlist.
 * @property settingsRepository Repository to get the current detection mode.
 * @property soundAlertPlayer Player for audible alerts.
 */
@HiltViewModel
class CameraViewModel @Inject constructor(
    private val vehicleMatcher: VehicleMatcher,
    private val settingsRepository: SettingsRepository,
    private val soundAlertPlayer: SoundAlertPlayer
    // CameraX specific dependencies will be added later (e.g., for frame processing)
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

    private var hideMatchFoundJob: Job? = null // Job to auto-hide the "MATCH FOUND" message

    init {
        Log.d("CameraViewModel","CameraViewModel initialized.")
        // In a real app, camera initialization and frame processing would start here.
    }

    fun onZoomRatioChanged(newRatio: Float) {
        // Assume ActualCameraView will handle coercing this to the camera's actual min/max capabilities.
        // Here we just store the desired ratio, perhaps with a sensible app-level min/max.
        _desiredZoomRatio.value = newRatio.coerceIn(1.0f, 10.0f) // Example: Max 10x desired by user
        Log.d("CameraViewModel","CameraViewModel: Desired zoom ratio changed to ${_desiredZoomRatio.value}")
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

    override fun onCleared() {
        super.onCleared()
        hideMatchFoundJob?.cancel() // Ensure the job is cancelled if ViewModel is cleared
        // Release camera resources if any were held by ViewModel (though typically handled by lifecycle observers)
        // If SoundAlertPlayer holds significant resources and is ViewModel scoped, release here.
        // (soundAlertPlayer as? AndroidSoundAlertPlayer)?.release() // Example if it needs explicit release
        Log.d("CameraViewModel", "CameraViewModel: onCleared")
    }
} 