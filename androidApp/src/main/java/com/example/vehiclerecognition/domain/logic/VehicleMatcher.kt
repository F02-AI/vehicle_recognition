package com.example.vehiclerecognition.domain.logic

import android.util.Log
import com.example.vehiclerecognition.domain.repository.WatchlistRepository
import com.example.vehiclerecognition.domain.validation.CountryAwareLicensePlateValidator
import com.example.vehiclerecognition.domain.validation.LicensePlateValidator
import com.example.vehiclerecognition.domain.validation.PlateTextCandidateGenerator
import com.example.vehiclerecognition.ml.processors.CountryAwarePlateValidator
import com.example.vehiclerecognition.domain.service.LicensePlateTemplateService
import com.example.vehiclerecognition.ml.processors.TemplateAwareOcrEnhancer
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
    private val licensePlateValidator: LicensePlateValidator,
    private val templateService: LicensePlateTemplateService,
    private val templateAwareEnhancer: TemplateAwareOcrEnhancer
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
    suspend fun findMatch(
        detectedVehicle: DetectedVehicle,
        detectionMode: DetectionMode,
        country: Country,
        enablePlateCandidateGeneration: Boolean = true
    ): Boolean {
        Log.d("VehicleMatcher", "=== findMatch CALLED ===")
        Log.d("VehicleMatcher", "Input: LP='${detectedVehicle.licensePlate}', mode=$detectionMode, country=${country.displayName}")
        
        val watchlist = watchlistRepository.getEntriesByCountry(country).first()
        
        Log.d("VehicleMatcher", "FindMatch: detected='${detectedVehicle.licensePlate}', mode=$detectionMode, country=${country.displayName}")
        Log.d("VehicleMatcher", "Watchlist for ${country.displayName}: ${watchlist.size} entries: ${watchlist.map { it.licensePlate ?: "(${it.vehicleColor}+${it.vehicleType})" }}")
        
        // Debug: Also check if the plate exists in ANY country's watchlist
        val allWatchlistEntries = watchlistRepository.getAllEntries().first()
        val matchingEntries = allWatchlistEntries.filter { 
            it.licensePlate != null && cleanLicensePlateForMatching(it.licensePlate) == cleanLicensePlateForMatching(detectedVehicle.licensePlate ?: "")
        }
        if (matchingEntries.isNotEmpty()) {
            Log.d("VehicleMatcher", "DEBUG: Plate '${detectedVehicle.licensePlate}' found in OTHER countries: ${matchingEntries.map { "${it.licensePlate} (${it.country.displayName})" }}")
        }
        
        if (watchlist.isEmpty()) {
            Log.d("VehicleMatcher", "No watchlist entries for ${country.displayName}")
            return false
        }

        // If mode does not require LP, fall back to original behavior
        if (!detectionModeRequiresLP(detectionMode)) {
            for (entry in watchlist) {
                if (isMatch(detectedVehicle, entry, detectionMode)) {
                    return true
                }
            }
            return false
        }

        // LP-based modes: optionally generate candidate LPs from OCR and try matching any candidate
        val rawOcrText = detectedVehicle.licensePlate ?: return false
        Log.d("VehicleMatcher", "Processing OCR text: '$rawOcrText' for country ${country.displayName}")

        // Extract relevant characters per-country for baseline formatting
        val extracted = CountryAwarePlateValidator.extractRelevantCharacters(rawOcrText, country)
        Log.d("VehicleMatcher", "Extracted characters: '$extracted' from '$rawOcrText'")

        val candidateSet = LinkedHashSet<String>()
        if (enablePlateCandidateGeneration) {
            Log.d("VehicleMatcher", "Candidate generation ENABLED")
            // Derive patterns and generate ambiguity-resolved candidates
            val patterns = PlateTextCandidateGenerator.getPatternsForCountry(country)
            Log.d("VehicleMatcher", "Patterns for ${country.displayName}: $patterns")
            val formatted = CountryAwarePlateValidator.validateAndFormatPlate(extracted, country)
            Log.d("VehicleMatcher", "Formatted plate: $formatted")
            val generatedCandidates = PlateTextCandidateGenerator.generateCandidates(extracted, patterns)
            Log.d("VehicleMatcher", "Generated candidates: $generatedCandidates")
            candidateSet.add(rawOcrText)
            candidateSet.add(extracted)
            formatted?.let { candidateSet.add(it) }
            candidateSet.addAll(generatedCandidates)
        } else {
            Log.d("VehicleMatcher", "Candidate generation DISABLED")
            // Only use straightforward extracted and formatted values (no candidate expansion)
            candidateSet.add(rawOcrText)
            candidateSet.add(extracted)
            CountryAwarePlateValidator.validateAndFormatPlate(extracted, country)?.let { candidateSet.add(it) }
        }
        Log.d("VehicleMatcher", "All candidates before validation: $candidateSet")

        // Filter candidates by country-specific validity to reduce false positives (FR 1.11)
        val validCandidates = candidateSet.filter { cand ->
            val isValid = CountryAwarePlateValidator.isValidFormat(cand, country)
            Log.d("VehicleMatcher", "Candidate '$cand' valid for ${country.displayName}: $isValid")
            isValid
        }

        if (validCandidates.isEmpty()) {
            Log.d("VehicleMatcher", "No valid LP candidates after correction for ${country.displayName} from '$rawOcrText'")
            return false
        }

        // Try each candidate against the watchlist using existing attribute matching logic
        Log.d("VehicleMatcher", "Checking ${validCandidates.size} valid candidates against ${watchlist.size} watchlist entries")
        Log.d("VehicleMatcher", "Valid candidates: $validCandidates")
        for (candidate in validCandidates) {
            Log.d("VehicleMatcher", "Checking candidate: '$candidate'")
            val candidateVehicle = DetectedVehicle(
                licensePlate = candidate,
                color = detectedVehicle.color,
                type = detectedVehicle.type
            )
            for (entry in watchlist) {
                Log.d("VehicleMatcher", "Checking against watchlist entry: LP='${entry.licensePlate}', color=${entry.vehicleColor}, type=${entry.vehicleType}, country=${entry.country}")
                val matchResult = isMatch(candidateVehicle, entry, detectionMode)
                Log.d("VehicleMatcher", "isMatch result for candidate '$candidate' vs entry '${entry.licensePlate}': $matchResult")
                if (matchResult) {
                    Log.d("VehicleMatcher", "Match found with LP candidate: $candidate")
                    return true
                }
            }
        }

        return false
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
    private suspend fun licensePlatesMatch(detected: String?, entry: String?, country: Country): Boolean {
        Log.d("VehicleMatcher", "licensePlatesMatch: detected='$detected', entry='$entry', country=${country.displayName}")
        if (detected.isNullOrEmpty() || entry.isNullOrEmpty()) {
            Log.d("VehicleMatcher", "licensePlatesMatch: null or empty - detected empty=${detected.isNullOrEmpty()}, entry empty=${entry.isNullOrEmpty()}")
            return false
        }
        
        // First try template-aware matching if templates are configured
        val hasTemplates = templateAwareEnhancer.hasConfiguredTemplates(country)
        Log.d("VehicleMatcher", "Country ${country.displayName} has templates: $hasTemplates")
        
        if (hasTemplates) {
            // Use template-based matching with character confusion handling
            val result = templateBasedLicensePlateMatch(detected, entry, country)
            Log.d("VehicleMatcher", "Template-based match result: $result")
            return result
        } else {
            // Fall back to legacy matching logic
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
                else -> {
                    // Default to Israeli format for all other countries
                    val detectedDigits = extractNumericDigits(detected)
                    val entryDigits = extractNumericDigits(entry)
                    detectedDigits.isNotEmpty() && detectedDigits == entryDigits
                }
            }
        }
    }
    
    /**
     * Template-based license plate matching with substring support
     */
    private suspend fun templateBasedLicensePlateMatch(detected: String, entry: String, country: Country): Boolean {
        Log.d("VehicleMatcher", "templateBasedLicensePlateMatch: detected='$detected', entry='$entry', country=${country.displayName}")
        
        // Clean both strings for comparison
        val cleanedDetected = cleanLicensePlateForMatching(detected)
        val cleanedEntry = cleanLicensePlateForMatching(entry)
        
        Log.d("VehicleMatcher", "After cleaning: cleanedDetected='$cleanedDetected', cleanedEntry='$cleanedEntry'")
        
        // Try exact match first
        if (cleanedDetected == cleanedEntry) {
            Log.d("VehicleMatcher", "Exact template match: $detected == $entry")
            return true
        }
        
        // Try enhanced OCR matching with character confusion handling
        val enhancementResult = templateAwareEnhancer.enhanceOcrResult(detected, country)
        if (enhancementResult.isValidFormat && enhancementResult.formattedPlate != null) {
            val enhancedCleaned = cleanLicensePlateForMatching(enhancementResult.formattedPlate)
            if (enhancedCleaned == cleanedEntry) {
                Log.d("VehicleMatcher", "Enhanced OCR match: $detected -> ${enhancementResult.formattedPlate} == $entry")
                return true
            }
        }
        
        // Try substring matching if the detected text is longer than the entry
        if (cleanedDetected.length > cleanedEntry.length) {
            val isSubstringMatch = cleanedDetected.contains(cleanedEntry)
            if (isSubstringMatch) {
                Log.d("VehicleMatcher", "Substring match: $cleanedEntry found in $cleanedDetected")
                return true
            }
        }
        
        // Try reverse substring matching if the entry is longer than detected
        if (cleanedEntry.length > cleanedDetected.length) {
            val isReverseSubstringMatch = cleanedEntry.contains(cleanedDetected)
            if (isReverseSubstringMatch) {
                Log.d("VehicleMatcher", "Reverse substring match: $cleanedDetected found in $cleanedEntry")
                return true
            }
        }
        
        // Try enhanced substring matching - check if the enhanced result is a substring
        if (enhancementResult.formattedPlate != null) {
            val enhancedCleaned = cleanLicensePlateForMatching(enhancementResult.formattedPlate)
            
            if (enhancedCleaned.length != cleanedEntry.length) {
                val longerText = if (enhancedCleaned.length > cleanedEntry.length) enhancedCleaned else cleanedEntry
                val shorterText = if (enhancedCleaned.length > cleanedEntry.length) cleanedEntry else enhancedCleaned
                
                if (longerText.contains(shorterText)) {
                    Log.d("VehicleMatcher", "Enhanced substring match: $shorterText found in $longerText")
                    return true
                }
            }
        }
        
        Log.d("VehicleMatcher", "No template match found: $detected vs $entry")
        return false
    }
    
    /**
     * Cleans license plate text for matching by removing spaces, dashes, and normalizing case
     */
    private fun cleanLicensePlateForMatching(plateText: String): String {
        return plateText.replace(Regex("[^A-Z0-9]"), "").uppercase()
    }

    private suspend fun isMatch(detected: DetectedVehicle, entry: WatchlistEntry, mode: DetectionMode): Boolean {
        Log.d("VehicleMatcher", "Checking match: detected(LP='${detected.licensePlate}', color=${detected.color}, type=${detected.type}) vs entry(LP='${entry.licensePlate}', color=${entry.vehicleColor}, type=${entry.vehicleType})")
        
        val result = when (mode) {
            DetectionMode.LP_ONLY -> {
                val lpMatch = licensePlatesMatch(detected.licensePlate, entry.licensePlate, entry.country)
                Log.d("VehicleMatcher", "LP_ONLY match result: $lpMatch")
                lpMatch
            }
            DetectionMode.LP_COLOR -> {
                val lpMatch = licensePlatesMatch(detected.licensePlate, entry.licensePlate, entry.country)
                val colorMatch = detected.color == entry.vehicleColor
                val result = lpMatch && colorMatch
                Log.d("VehicleMatcher", "LP_COLOR match: LP=$lpMatch, Color=$colorMatch, Result=$result")
                result
            }
            DetectionMode.LP_TYPE -> {
                val lpMatch = licensePlatesMatch(detected.licensePlate, entry.licensePlate, entry.country)
                val typeMatch = detected.type == entry.vehicleType
                val result = lpMatch && typeMatch
                Log.d("VehicleMatcher", "LP_TYPE match: LP=$lpMatch, Type=$typeMatch, Result=$result")
                result
            }
            DetectionMode.LP_COLOR_TYPE -> {
                val lpMatch = licensePlatesMatch(detected.licensePlate, entry.licensePlate, entry.country)
                val colorMatch = detected.color == entry.vehicleColor
                val typeMatch = detected.type == entry.vehicleType
                val result = lpMatch && colorMatch && typeMatch
                Log.d("VehicleMatcher", "LP_COLOR_TYPE match: LP=$lpMatch, Color=$colorMatch, Type=$typeMatch, Result=$result")
                result
            }
            DetectionMode.COLOR_TYPE -> {
                val colorMatch = detected.color == entry.vehicleColor
                val typeMatch = detected.type == entry.vehicleType
                val result = colorMatch && typeMatch
                Log.d("VehicleMatcher", "COLOR_TYPE match: Color=$colorMatch, Type=$typeMatch, Result=$result")
                result
            }
            DetectionMode.COLOR_ONLY -> {
                val result = detected.color == entry.vehicleColor
                Log.d("VehicleMatcher", "COLOR_ONLY match result: $result")
                result
            }
        }
        
        Log.d("VehicleMatcher", "Final match result for mode $mode: $result")
        return result
    }
}