package com.example.vehiclerecognition.data.models

import android.graphics.RectF

/**
 * Data class representing a detected license plate
 */
data class PlateDetection(
    val boundingBox: RectF,
    val confidence: Float,
    val recognizedText: String? = null,
    val isValidFormat: Boolean = false,
    val processingTimeMs: Long = 0L,
    val detectionTime: Long? = null // Timestamp when detection was made for expiration tracking
)

/**
 * Data class representing OCR result
 */
data class OcrResult(
    val text: String,
    val confidence: Float,
    val isValidFormat: Boolean,
    val formattedText: String? = null,
    val processingTimeMs: Long
)

/**
 * Data class representing detection result from YOLO model
 */
data class DetectionResult(
    val boundingBox: RectF,
    val confidence: Float,
    val classId: Int
)

/**
 * Data class representing a bounding box
 */
data class BoundingBox(
    val x1: Float,
    val y1: Float,
    val x2: Float,
    val y2: Float,
    val confidence: Float
) {
    fun toRectF(): RectF = RectF(x1, y1, x2, y2)
    
    fun width(): Float = x2 - x1
    fun height(): Float = y2 - y1
    fun centerX(): Float = (x1 + x2) / 2f
    fun centerY(): Float = (y1 + y2) / 2f
} 