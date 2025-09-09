package com.example.vehiclerecognition.domain.validation

import com.example.vehiclerecognition.data.models.Country
import com.example.vehiclerecognition.ml.processors.NumericPlateValidator
import com.example.vehiclerecognition.ml.processors.UKPlateValidator

/**
 * Interface for license plate validation
 */
interface LicensePlateValidator {
    fun isValid(licensePlate: String): Boolean
    fun validateAndFormat(licensePlate: String): String?
}

/**
 * Country-aware license plate validator implementation
 */
class CountryAwareLicensePlateValidator(
    private val selectedCountry: Country
) : LicensePlateValidator {
    
    override fun isValid(licensePlate: String): Boolean {
        return when (selectedCountry) {
            Country.ISRAEL -> NumericPlateValidator.isValidIsraeliFormat(licensePlate)
            Country.UK -> UKPlateValidator.isValidUkFormat(licensePlate)
            else -> NumericPlateValidator.isValidIsraeliFormat(licensePlate) // Default to Israeli format
        }
    }
    
    override fun validateAndFormat(licensePlate: String): String? {
        return when (selectedCountry) {
            Country.ISRAEL -> NumericPlateValidator.validateAndFormatPlate(licensePlate)
            Country.UK -> UKPlateValidator.validateAndFormatPlate(licensePlate)
            else -> NumericPlateValidator.validateAndFormatPlate(licensePlate) // Default to Israeli format
        }
    }
}

/**
 * Legacy object for backward compatibility
 * Defaults to Israeli validation for existing code
 */
object DefaultLicensePlateValidator : LicensePlateValidator {
    private val israeliValidator = CountryAwareLicensePlateValidator(Country.ISRAEL)
    
    override fun isValid(licensePlate: String): Boolean {
        return israeliValidator.isValid(licensePlate)
    }
    
    override fun validateAndFormat(licensePlate: String): String? {
        return israeliValidator.validateAndFormat(licensePlate)
    }
}