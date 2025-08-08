package com.example.vehiclerecognition.data.models

/**
 * Enum representing different OCR engine types available for license plate recognition
 */
enum class OcrModelType(
    val displayName: String,
    val description: String
) {
    ML_KIT(
        displayName = "ML Kit",
        description = "Google's mobile-optimized text recognition with alphanumeric support"
    )
} 