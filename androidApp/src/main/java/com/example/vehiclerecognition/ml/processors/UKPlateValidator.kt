package com.example.vehiclerecognition.ml.processors

import java.util.regex.Pattern

/**
 * Validator for UK license plates with LLNN-LLL format validation
 * Format: Two letters, two numbers, dash, three letters (e.g., AB12-XYZ)
 */
class UKPlateValidator {
    
    companion object {
        // UK format: LLNN-LLL (Letter-Letter-Number-Number-Dash-Letter-Letter-Letter)
        private val UK_PLATE_PATTERN = Pattern.compile("^[A-Z]{2}[0-9]{2}-[A-Z]{3}$")
        private val UK_PLATE_PATTERN_WITHOUT_DASH = Pattern.compile("^[A-Z]{2}[0-9]{2}[A-Z]{3}$")
        
        /**
         * Validates and formats a license plate text according to UK format rules
         */
        fun validateAndFormatPlate(rawText: String): String? {
            if (rawText.isEmpty()) return null
            
            // Remove any padding characters and whitespace
            val cleanText = rawText.replace("_", "").replace(" ", "").trim().uppercase()
            
            // Check if it already matches the correct format
            if (UK_PLATE_PATTERN.matcher(cleanText).matches()) {
                return cleanText
            }
            
            // Check if it matches without dash and add dash
            if (UK_PLATE_PATTERN_WITHOUT_DASH.matcher(cleanText).matches()) {
                return "${cleanText.substring(0, 4)}-${cleanText.substring(4)}"
            }
            
            // Try to extract and format if we have the right character types in sequence
            val extracted = extractUkFormat(cleanText)
            if (extracted != null && isValidUkFormat(extracted)) {
                return extracted
            }
            
            return null
        }
        
        /**
         * Extracts UK format from messy OCR text
         */
        private fun extractUkFormat(text: String): String? {
            // Remove all non-alphanumeric characters
            val alphanumeric = text.replace(Regex("[^A-Z0-9]"), "")
            
            // Must have exactly 7 characters (2 letters + 2 numbers + 3 letters)
            if (alphanumeric.length != 7) return null
            
            // Check if pattern matches LLNNLLL
            val pattern = Regex("^[A-Z]{2}[0-9]{2}[A-Z]{3}$")
            if (!pattern.matches(alphanumeric)) return null
            
            // Format with dash: LLNN-LLL
            return "${alphanumeric.substring(0, 4)}-${alphanumeric.substring(4)}"
        }
        
        /**
         * Checks if a plate text is valid according to UK format rules
         */
        fun isValidUkFormat(plateText: String): Boolean {
            val cleanText = plateText.replace(" ", "").uppercase()
            return UK_PLATE_PATTERN.matcher(cleanText).matches()
        }
        
        /**
         * Checks if raw text could potentially be a UK license plate
         */
        fun couldBeUkPlate(rawText: String): Boolean {
            val cleanText = rawText.replace(Regex("[^A-Z0-9]"), "").uppercase()
            
            // Must have exactly 7 alphanumeric characters
            if (cleanText.length != 7) return false
            
            // Check if pattern could match LLNNLLL
            return Regex("^[A-Z]{2}[0-9]{2}[A-Z]{3}$").matches(cleanText)
        }
        
        /**
         * Extracts alphanumeric characters and checks if they could form a UK plate
         */
        fun extractAndValidateUkChars(text: String): String? {
            val alphanumeric = text.replace(Regex("[^A-Z0-9]"), "").uppercase()
            
            if (alphanumeric.length == 7 && couldBeUkPlate(alphanumeric)) {
                return "${alphanumeric.substring(0, 4)}-${alphanumeric.substring(4)}"
            }
            
            return null
        }
        
        /**
         * Normalizes UK plate for comparison (removes dash and converts to uppercase)
         */
        fun normalizeUkPlate(plateText: String): String {
            return plateText.replace("-", "").replace(" ", "").uppercase()
        }
    }
}