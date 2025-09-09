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
    val cameraZoomRatio: Float = 1.0f, // Remember the last camera zoom level
    val selectedCountry: Country = Country.ISRAEL, // Default to Israel for backward compatibility
    val enablePlateCandidateGeneration: Boolean = true, // Enable OCR candidate correction
    
    // Vehicle Color Detection Settings
    val enableGrayFiltering: Boolean = true, // Enable gray color filtering
    val grayExclusionThreshold: Float = 50.0f, // Percentage threshold to exclude gray (5-95)
    val enableSecondaryColorDetection: Boolean = true, // Enable detection of secondary vehicle colors
    
    // Debug logging callback (not persisted)
    val debugLogger: ((String) -> Unit)? = null
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