package com.example.vehiclerecognition.ml.processors

/**
 * Validator for numeric-only license plates with Israeli format validation
 * Ports the exact logic from python_yolo_fast_plate.py
 */
class NumericPlateValidator {
    
    companion object {
        /**
         * Validates and formats a license plate text according to Israeli format rules
         * Ported from _validate_and_format_plate method in Python script
         */
        fun validateAndFormatPlate(rawText: String): String? {
            if (rawText.isEmpty()) return null
            
            // Remove any padding characters (underscores) that might come from the model
            val cleanText = rawText.replace("_", "").trim()
            
            // Extract just numbers (should already be numeric, but just in case)
            val numericOnly = cleanText.replace(Regex("[^0-9]"), "")
            
            // CONSTRAINT: Must have exactly 7, 8, or 9 digits (discard less than 7, max 9)
            if (numericOnly.length < 7 || numericOnly.length > 9) {
                return null
            }
            
            // Apply numeric format rules based on length - same logic as Python script
            return validateAndFormatPlateByRules(cleanText, numericOnly)
        }
        
        /**
         * Validates the numeric plate text based on allowed lengths and formats it
         * according to predefined rules. Ported from _validate_and_format_plate_by_rules
         */
        private fun validateAndFormatPlateByRules(rawOcrText: String, numericPlateText: String): String? {
            val lenNumeric = numericPlateText.length
            
            return when (lenNumeric) {
                7 -> {
                    // For 7 digits, default to NN-NNN-NN format (most common)
                    // Since we only have pure numbers, we can't detect the intended format
                    "${numericPlateText.substring(0, 2)}-${numericPlateText.substring(2, 5)}-${numericPlateText.substring(5)}" // NN-NNN-NN
                }
                8 -> {
                    "${numericPlateText.substring(0, 3)}-${numericPlateText.substring(3, 5)}-${numericPlateText.substring(5)}" // NNN-NN-NNN
                }
                9 -> {
                    // For 9 digits, show both 8-digit options (without first and without last digit)
                    val option1 = numericPlateText.substring(1) // Remove first digit
                    val option2 = numericPlateText.substring(0, numericPlateText.length - 1) // Remove last digit
                    val formattedOption1 = "${option1.substring(0, 3)}-${option1.substring(3, 5)}-${option1.substring(5)}" // NNN-NN-NNN
                    val formattedOption2 = "${option2.substring(0, 3)}-${option2.substring(3, 5)}-${option2.substring(5)}" // NNN-NN-NNN
                    "$formattedOption1 or $formattedOption2"
                }
                else -> null
            }
        }
        
        /**
         * Applies format rules for numeric-only license plates (7-8 digits only)
         * Ported from _apply_numeric_format_rules method
         */
        fun applyNumericFormatRules(numericText: String): String {
            val lenNumeric = numericText.length
            
            return when (lenNumeric) {
                7 -> {
                    // NN-NNN-NN format (most common 7-digit format)
                    "${numericText.substring(0, 2)}-${numericText.substring(2, 5)}-${numericText.substring(5)}"
                }
                8 -> {
                    // NNN-NN-NNN format
                    "${numericText.substring(0, 3)}-${numericText.substring(3, 5)}-${numericText.substring(5)}"
                }
                else -> {
                    // Fallback (shouldn't reach here due to validation)
                    numericText
                }
            }
        }
        
        /**
         * Checks if a plate number is valid according to Israeli format rules
         */
        fun isValidIsraeliFormat(plateText: String): Boolean {
            val numericOnly = plateText.replace(Regex("[^0-9]"), "")
            return numericOnly.length in 7..8 // Valid Israeli plates have 7 or 8 digits
        }
        
        /**
         * Extracts only numeric characters from text
         */
        fun extractNumericOnly(text: String): String {
            return text.replace(Regex("[^0-9]"), "")
        }
    }
} 