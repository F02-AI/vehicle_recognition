package com.example.vehiclerecognition.data.models

/**
 * Data class representing license plate recognition settings
 */
data class LicensePlateSettings(
    val selectedOcrModel: OcrModelType = OcrModelType.ML_KIT,
    val processingInterval: Int = 1, // Process every Nth frame
    val minConfidenceThreshold: Float = 0.5f,
    val enableGpuAcceleration: Boolean = true, // Enable GPU by default
    val enableOcr: Boolean = true, // Enable/disable OCR processing
    val enableNumericOnlyMode: Boolean = true,
    val enableIsraeliFormatValidation: Boolean = true,
    val enableDebugVideo: Boolean = false, // Play test video instead of camera feed
    val cameraZoomRatio: Float = 1.0f // Remember the last camera zoom level
)

/**
 * Settings for individual detection modes
 */
enum class DetectionMode(val displayName: String) {
    LP_ONLY("License Plate Only"),
    LP_COLOR("License Plate + Color"),
    LP_TYPE("License Plate + Type"),
    LP_COLOR_TYPE("License Plate + Color + Type"),
    COLOR_TYPE("Color + Type"),
    COLOR_ONLY("Color Only")
} 