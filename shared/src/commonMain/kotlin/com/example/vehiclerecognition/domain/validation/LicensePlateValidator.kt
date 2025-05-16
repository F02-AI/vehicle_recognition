package com.example.vehiclerecognition.domain.validation

/**
 * Validator for Israeli license plate formats.
 * Supports formats: NN-NNN-NN, NNN-NN-NNN, N-NNNN-NN (N is a digit).
 * As per FR 1.6 and FR 1.11.
 */
object LicensePlateValidator {

    private val israeliPlateRegexes = listOf(
        Regex("^\\d{2}-\\d{3}-\\d{2}$"), // NN-NNN-NN
        Regex("^\\d{3}-\\d{2}-\\d{3}$"), // NNN-NN-NNN
        Regex("^\\d{1}-\\d{4}-\\d{2}$")  // N-NNNN-NN
    )

    /**
     * Validates if the given license plate string conforms to one of the specified
     * Israeli license plate formats.
     *
     * @param licensePlate The license plate string to validate.
     * @return True if the license plate is valid, false otherwise.
     */
    fun isValid(licensePlate: String): Boolean {
        if (licensePlate.isBlank()) {
            return false
        }
        return israeliPlateRegexes.any { it.matches(licensePlate) }
    }
} 