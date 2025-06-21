package com.example.vehiclerecognition.data.models

/**
 * Enum representing different OCR engine types available for license plate recognition
 */
enum class OcrModelType(
    val displayName: String,
    val description: String
) {
    FAST_PLATE_OCR(
        displayName = "FastPlateOCR",
        description = "Placeholder - Numeric only, optimized for license plates"
    ),
    TESSERACT(
        displayName = "Tesseract OCR",
        description = "Placeholder - Robust open-source OCR engine"
    ),
    ML_KIT(
        displayName = "ML Kit",
        description = "Google's mobile-optimized text recognition (Functional)"
    ),
    PADDLE_OCR(
        displayName = "PaddleOCR",
        description = "Placeholder - State-of-the-art lightweight OCR"
    )
} 