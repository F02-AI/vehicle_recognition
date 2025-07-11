package com.example.vehiclerecognition.data.models

import android.graphics.RectF
import com.example.vehiclerecognition.model.VehicleColor

/**
 * Data class representing a detected vehicle with bounding box and segmentation
 */
data class VehicleDetection(
    val id: String, // Unique identifier for this detection
    val boundingBox: RectF,
    val confidence: Float,
    val classId: Int,
    val className: String,
    val detectedColor: VehicleColor? = null, // Primary detected vehicle color from predefined set
    val secondaryColor: VehicleColor? = null, // Secondary detected vehicle color (second closest match)
    val segmentationMask: Array<FloatArray>? = null,
    val maskWidth: Int = 0,
    val maskHeight: Int = 0,
    val maskCoeffs: FloatArray? = null, // Mask coefficients from YOLO11 segmentation
    val detectionTime: Long? = null // Timestamp when detection was made
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as VehicleDetection

        if (id != other.id) return false
        if (boundingBox != other.boundingBox) return false
        if (confidence != other.confidence) return false
        if (classId != other.classId) return false
        if (className != other.className) return false
        if (detectedColor != other.detectedColor) return false
        if (secondaryColor != other.secondaryColor) return false
        if (maskWidth != other.maskWidth) return false
        if (maskHeight != other.maskHeight) return false
        if (detectionTime != other.detectionTime) return false
        if (maskCoeffs != null) {
            if (other.maskCoeffs == null) return false
            if (!maskCoeffs.contentEquals(other.maskCoeffs)) return false
        } else if (other.maskCoeffs != null) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + boundingBox.hashCode()
        result = 31 * result + confidence.hashCode()
        result = 31 * result + classId
        result = 31 * result + className.hashCode()
        result = 31 * result + (detectedColor?.hashCode() ?: 0)
        result = 31 * result + (secondaryColor?.hashCode() ?: 0)
        result = 31 * result + maskWidth
        result = 31 * result + maskHeight
        result = 31 * result + (maskCoeffs?.contentHashCode() ?: 0)
        result = 31 * result + (detectionTime?.hashCode() ?: 0)
        return result
    }
}

/**
 * Data class representing vehicle segmentation result
 */
data class VehicleSegmentationResult(
    val detections: List<VehicleDetection>,
    val performance: Map<String, Long>,
    val rawOutputLog: String
)

/**
 * Vehicle class definitions matching the YOLO model
 */
enum class VehicleClass(val id: Int, val displayName: String) {
    CAR(2, "Car"),
    MOTORCYCLE(3, "Motorcycle"), 
    TRUCK(7, "Truck");
    
    companion object {
        fun fromId(id: Int): VehicleClass? = values().find { it.id == id }
        fun getDisplayName(id: Int): String = fromId(id)?.displayName ?: "Unknown"
    }
} 