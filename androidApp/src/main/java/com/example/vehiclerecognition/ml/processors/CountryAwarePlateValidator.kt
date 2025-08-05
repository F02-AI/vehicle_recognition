package com.example.vehiclerecognition.ml.processors

import com.example.vehiclerecognition.data.models.Country

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
        }
    }
    
    /**
     * Checks if a plate text is valid according to the specified country's format rules
     */
    fun isValidFormat(plateText: String, country: Country): Boolean {
        return when (country) {
            Country.ISRAEL -> NumericPlateValidator.isValidIsraeliFormat(plateText)
            Country.UK -> UKPlateValidator.isValidUkFormat(plateText)
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
        }
    }
    
    /**
     * Gets format description for the specified country
     */
    fun getFormatDescription(country: Country): String {
        return when (country) {
            Country.ISRAEL -> "Israeli format: NN-NNN-NN, NNN-NN-NNN"
            Country.UK -> "UK format: LLNN-LLL (e.g., AB12-XYZ)"
        }
    }
    
    fun getFormatHint(country: Country): String {
        return when (country) {
            Country.ISRAEL -> "NN-NNN-NN, NNN-NN-NNN"
            Country.UK -> "LLNN-LLL"
        }
    }
}