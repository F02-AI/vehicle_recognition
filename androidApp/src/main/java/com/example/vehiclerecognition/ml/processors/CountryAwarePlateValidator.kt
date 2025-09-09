package com.example.vehiclerecognition.ml.processors

import com.example.vehiclerecognition.data.models.Country
import com.example.vehiclerecognition.domain.service.LicensePlateTemplateService
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * Unified validator that delegates to country-specific validators
 */
object CountryAwarePlateValidator {
    
    /**
     * Validates and formats a license plate text according to the specified country's format rules
     */
    fun validateAndFormatPlate(rawText: String, country: Country): String? {
        return when (country) {
            Country.ISRAEL -> NumericPlateValidator.validateAndFormatPlate(rawText)
            Country.UK -> UKPlateValidator.validateAndFormatPlate(rawText)
            else -> NumericPlateValidator.validateAndFormatPlate(rawText) // Default to Israeli format
        }
    }
    
    /**
     * Checks if a plate text is valid according to the specified country's format rules
     */
    fun isValidFormat(plateText: String, country: Country): Boolean {
        return when (country) {
            Country.ISRAEL -> NumericPlateValidator.isValidIsraeliFormat(plateText)
            Country.UK -> UKPlateValidator.isValidUkFormat(plateText)
            else -> NumericPlateValidator.isValidIsraeliFormat(plateText) // Default to Israeli format
        }
    }
    
    /**
     * Extracts relevant characters for the specified country format
     */
    fun extractRelevantCharacters(text: String, country: Country): String {
        return when (country) {
            Country.ISRAEL -> NumericPlateValidator.extractNumericOnly(text)
            Country.UK -> {
                // For UK, extract alphanumeric characters in sequence
                text.replace(Regex("[^A-Z0-9]"), "").uppercase()
            }
            else -> {
                // For other countries, preserve alphanumeric by default
                // This allows template-based countries to work properly
                text.replace(Regex("[^A-Z0-9]"), "").uppercase()
            }
        }
    }
    
    /**
     * Extracts relevant characters dynamically based on country templates
     * If templates contain letters (L), preserve alphanumeric. If only numbers (N), extract numeric only.
     */
    suspend fun extractRelevantCharactersDynamic(
        text: String, 
        country: Country, 
        templateService: LicensePlateTemplateService
    ): String {
        return when (country) {
            Country.ISRAEL -> NumericPlateValidator.extractNumericOnly(text)
            Country.UK -> {
                // For UK, extract alphanumeric characters in sequence
                text.replace(Regex("[^A-Z0-9]"), "").uppercase()
            }
            else -> {
                // For other countries, check templates to determine format
                val templates = templateService.getTemplatesForCountry(country.isoCode).first()
                
                val hasLetters = templates.any { template -> 
                    template.templatePattern.contains('L') 
                }
                
                android.util.Log.d("CountryAwarePlateValidator", 
                    "Dynamic extraction for ${country.displayName}: hasLetters=$hasLetters, templates=${templates.map { it.templatePattern }}")
                
                if (hasLetters) {
                    // Templates contain letters, preserve alphanumeric
                    val result = text.replace(Regex("[^A-Z0-9]"), "").uppercase()
                    android.util.Log.d("CountryAwarePlateValidator", "Preserving alphanumeric: '$text' -> '$result'")
                    result
                } else {
                    // Templates only have numbers, extract numeric only
                    val result = NumericPlateValidator.extractNumericOnly(text)
                    android.util.Log.d("CountryAwarePlateValidator", "Extracting numeric only: '$text' -> '$result'")
                    result
                }
            }
        }
    }
    
    /**
     * Gets format description for the specified country
     */
    fun getFormatDescription(country: Country): String {
        return when (country) {
            Country.ISRAEL -> "Israeli format: NN-NNN-NN, NNN-NN-NNN"
            Country.UK -> "UK format: LLNN-LLL (e.g., AB12-XYZ)"
            else -> "Israeli format: NN-NNN-NN, NNN-NN-NNN" // Default to Israeli format
        }
    }
    
    fun getFormatHint(country: Country): String {
        return when (country) {
            Country.ISRAEL -> "NN-NNN-NN, NNN-NN-NNN"
            Country.UK -> "LLNN-LLL"
            else -> "NN-NNN-NN, NNN-NN-NNN" // Default to Israeli format
        }
    }
}