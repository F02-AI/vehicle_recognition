package com.example.vehiclerecognition.domain.logic

import com.example.vehiclerecognition.domain.repository.WatchlistRepository
import com.example.vehiclerecognition.domain.validation.LicensePlateValidator
import com.example.vehiclerecognition.model.DetectionMode
import com.example.vehiclerecognition.model.VehicleColor
import com.example.vehiclerecognition.model.VehicleType
import com.example.vehiclerecognition.model.WatchlistEntry

/**
 * Implements the logic for matching detected vehicle attributes against the watchlist.
 * Adheres to FR 1.11, including license plate format validation for LP-based modes.
 *
 * @property watchlistRepository Repository to access watchlist data.
 * @property licensePlateValidator Validator for license plate formats.
 */
class VehicleMatcher(
    private val watchlistRepository: WatchlistRepository,
    private val licensePlateValidator: LicensePlateValidator
) {

    /**
     * Represents the attributes of a detected vehicle.
     * Optional because not all attributes might be detected or relevant for all modes.
     */
    data class DetectedVehicle(
        val licensePlate: String? = null,
        val color: VehicleColor? = null,
        val type: VehicleType? = null
    )

    /**
     * Checks if a detected vehicle matches any entry in the watchlist based on the current detection mode.
     *
     * @param detectedVehicle The attributes of the detected vehicle.
     * @param detectionMode The current detection mode to apply for matching.
     * @return True if a match is found, false otherwise.
     */
    suspend fun findMatch(detectedVehicle: DetectedVehicle, detectionMode: DetectionMode): Boolean {
        val watchlist = watchlistRepository.getAllEntries()
        if (watchlist.isEmpty()) return false

        // FR 1.11: For modes that include 'LP', the detected License Plate MUST first be checked
        // to ensure it conforms to one of the required Israeli formats.
        if (detectionModeRequiresLP(detectionMode)) {
            if (detectedVehicle.licensePlate == null || !licensePlateValidator.isValid(detectedVehicle.licensePlate)) {
                return false // Invalid or missing LP for LP-dependent mode
            }
        }

        return watchlist.any { entry ->
            isMatch(detectedVehicle, entry, detectionMode)
        }
    }

    private fun detectionModeRequiresLP(mode: DetectionMode): Boolean {
        return when (mode) {
            DetectionMode.LP, DetectionMode.LP_COLOR, DetectionMode.LP_TYPE, DetectionMode.LP_COLOR_TYPE -> true
            else -> false
        }
    }

    private fun isMatch(detected: DetectedVehicle, entry: WatchlistEntry, mode: DetectionMode): Boolean {
        return when (mode) {
            DetectionMode.LP -> detected.licensePlate == entry.licensePlate
            DetectionMode.LP_COLOR -> detected.licensePlate == entry.licensePlate && detected.color == entry.vehicleColor
            DetectionMode.LP_TYPE -> detected.licensePlate == entry.licensePlate && detected.type == entry.vehicleType
            DetectionMode.LP_COLOR_TYPE -> detected.licensePlate == entry.licensePlate &&
                    detected.color == entry.vehicleColor &&
                    detected.type == entry.vehicleType
            DetectionMode.COLOR_TYPE -> detected.color == entry.vehicleColor && detected.type == entry.vehicleType
            DetectionMode.COLOR -> detected.color == entry.vehicleColor
        }
    }
} 