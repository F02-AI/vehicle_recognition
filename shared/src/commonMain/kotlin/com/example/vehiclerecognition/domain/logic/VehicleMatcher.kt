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
 * License plate matching compares only numeric digits, excluding dashes and formatting.
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
        // Modified to validate numeric digits (7-8 digits) rather than exact format matching.
        if (detectionModeRequiresLP(detectionMode)) {
            if (detectedVehicle.licensePlate == null) {
                return false // Missing LP for LP-dependent mode
            }
            
            // Check if the numeric portion has valid Israeli digit count (7-8 digits)
            val detectedDigits = extractNumericDigits(detectedVehicle.licensePlate)
            if (detectedDigits.length !in 7..8) {
                return false // Invalid digit count for Israeli license plates
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

    /**
     * Extracts only numeric digits from a license plate string, removing dashes and other formatting
     */
    private fun extractNumericDigits(licensePlate: String?): String {
        return licensePlate?.replace(Regex("[^0-9]"), "") ?: ""
    }

    /**
     * Compares license plates by numeric digits only, ignoring formatting like dashes
     */
    private fun licensePlatesMatch(detected: String?, entry: String?): Boolean {
        val detectedDigits = extractNumericDigits(detected)
        val entryDigits = extractNumericDigits(entry)
        return detectedDigits.isNotEmpty() && detectedDigits == entryDigits
    }

    private fun isMatch(detected: DetectedVehicle, entry: WatchlistEntry, mode: DetectionMode): Boolean {
        return when (mode) {
            DetectionMode.LP -> licensePlatesMatch(detected.licensePlate, entry.licensePlate)
            DetectionMode.LP_COLOR -> licensePlatesMatch(detected.licensePlate, entry.licensePlate) && detected.color == entry.vehicleColor
            DetectionMode.LP_TYPE -> licensePlatesMatch(detected.licensePlate, entry.licensePlate) && detected.type == entry.vehicleType
            DetectionMode.LP_COLOR_TYPE -> licensePlatesMatch(detected.licensePlate, entry.licensePlate) &&
                    detected.color == entry.vehicleColor &&
                    detected.type == entry.vehicleType
            DetectionMode.COLOR_TYPE -> detected.color == entry.vehicleColor && detected.type == entry.vehicleType
            DetectionMode.COLOR -> detected.color == entry.vehicleColor
        }
    }
} 