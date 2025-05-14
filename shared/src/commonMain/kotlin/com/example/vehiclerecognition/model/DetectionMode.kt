package com.example.vehiclerecognition.model

/**
 * Represents the detection modes for vehicle matching.
 * As per FR 1.9.
 */
enum class DetectionMode {
    LP,             // License Plate only
    LP_COLOR,       // License Plate + Color
    LP_TYPE,        // License Plate + Type
    LP_COLOR_TYPE,  // License Plate + Color + Type
    COLOR_TYPE,     // Color + Type
    COLOR           // Color only
} 