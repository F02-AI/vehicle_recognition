package com.example.vehiclerecognition.domain.logic

import com.example.vehiclerecognition.domain.repository.WatchlistRepository
import com.example.vehiclerecognition.domain.validation.LicensePlateValidator
import com.example.vehiclerecognition.data.models.DetectionMode
import com.example.vehiclerecognition.data.models.Country
import com.example.vehiclerecognition.model.VehicleColor
import com.example.vehiclerecognition.model.VehicleType
import com.example.vehiclerecognition.model.WatchlistEntry
import kotlinx.coroutines.flow.first

/**
 * Implements the logic for matching detected vehicle attributes against the watchlist.
 * Adheres to FR 1.11, including license plate format validation for LP-based modes.
 * License plate matching compares only numeric digits, excluding dashes and formatting.
 * Now supports country-specific watchlist matching.
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
     * Checks if a detected vehicle matches any entry in the watchlist based on the current detection mode and country.
     *
     * @param detectedVehicle The attributes of the detected vehicle.
     * @param detectionMode The current detection mode to apply for matching.
     * @param country The country to filter watchlist entries by.
     * @return True if a match is found, false otherwise.
     */
    suspend fun findMatch(detectedVehicle: DetectedVehicle, detectionMode: DetectionMode, country: Country): Boolean {
        val watchlist = watchlistRepository.getEntriesByCountry(country).first()
        if (watchlist.isEmpty()) return false

        // FR 1.11: For modes that include 'LP', the detected License Plate MUST first be checked
        // to ensure it conforms to one of the required formats for the specified country.
        if (detectionModeRequiresLP(detectionMode)) {
            if (detectedVehicle.licensePlate == null) {
                return false // Missing LP for LP-dependent mode
            }
            
            // Validate with country-specific validation
            if (!licensePlateValidator.isValid(detectedVehicle.licensePlate)) {
                return false // Invalid format for the current country
            }
        }

        return watchlist.any { entry ->
            isMatch(detectedVehicle, entry, detectionMode)
        }
    }

    /**
     * Legacy method for backward compatibility - uses Israel as default country
     */
    suspend fun findMatch(detectedVehicle: DetectedVehicle, detectionMode: DetectionMode): Boolean {
        return findMatch(detectedVehicle, detectionMode, Country.ISRAEL)
    }

    private fun detectionModeRequiresLP(mode: DetectionMode): Boolean {
        return when (mode) {
            DetectionMode.LP_ONLY, DetectionMode.LP_COLOR, DetectionMode.LP_TYPE, DetectionMode.LP_COLOR_TYPE -> true
            DetectionMode.COLOR_ONLY, DetectionMode.COLOR_TYPE -> false
        }
    }

    /**
     * Extracts only numeric digits from a license plate string, removing dashes and other formatting
     * Used for Israeli license plates
     */
    private fun extractNumericDigits(licensePlate: String?): String {
        return licensePlate?.replace(Regex("[^0-9]"), "") ?: ""
    }

    /**
     * Extracts alphanumeric characters from a license plate string, removing dashes and other formatting
     * Used for UK license plates
     */
    private fun extractAlphanumericCharacters(licensePlate: String?): String {
        return licensePlate?.replace(Regex("[^A-Za-z0-9]"), "")?.uppercase() ?: ""
    }

    /**
     * Compares license plates based on their country-specific format
     */
    private fun licensePlatesMatch(detected: String?, entry: String?, country: Country): Boolean {
        if (detected.isNullOrEmpty() || entry.isNullOrEmpty()) return false
        
        return when (country) {
            Country.ISRAEL -> {
                // Compare numeric digits only for Israeli plates
                val detectedDigits = extractNumericDigits(detected)
                val entryDigits = extractNumericDigits(entry)
                detectedDigits.isNotEmpty() && detectedDigits == entryDigits
            }
            Country.UK -> {
                // Compare alphanumeric characters for UK plates
                val detectedChars = extractAlphanumericCharacters(detected)
                val entryChars = extractAlphanumericCharacters(entry)
                detectedChars.isNotEmpty() && detectedChars == entryChars
            }
        }
    }

    private fun isMatch(detected: DetectedVehicle, entry: WatchlistEntry, mode: DetectionMode): Boolean {
        return when (mode) {
            DetectionMode.LP_ONLY -> licensePlatesMatch(detected.licensePlate, entry.licensePlate, entry.country)
            DetectionMode.LP_COLOR -> licensePlatesMatch(detected.licensePlate, entry.licensePlate, entry.country) && detected.color == entry.vehicleColor
            DetectionMode.LP_TYPE -> licensePlatesMatch(detected.licensePlate, entry.licensePlate, entry.country) && detected.type == entry.vehicleType
            DetectionMode.LP_COLOR_TYPE -> licensePlatesMatch(detected.licensePlate, entry.licensePlate, entry.country) &&
                    detected.color == entry.vehicleColor &&
                    detected.type == entry.vehicleType
            DetectionMode.COLOR_TYPE -> detected.color == entry.vehicleColor && detected.type == entry.vehicleType
            DetectionMode.COLOR_ONLY -> detected.color == entry.vehicleColor
        }
    }
}