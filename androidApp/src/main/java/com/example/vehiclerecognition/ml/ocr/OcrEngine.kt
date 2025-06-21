package com.example.vehiclerecognition.ml.ocr

import android.graphics.Bitmap
import com.example.vehiclerecognition.data.models.LicensePlateSettings
import com.example.vehiclerecognition.data.models.OcrResult

/**
 * Interface defining the contract for OCR engines
 */
interface OcrEngine {
    
    /**
     * Processes a bitmap image and returns OCR result
     */
    suspend fun processImage(bitmap: Bitmap): OcrResult
    
    /**
     * Initializes the OCR engine (loads models, etc.)
     */
    suspend fun initialize(): Boolean
    
    /**
     * Initializes the OCR engine with settings (for GPU acceleration support)
     */
    suspend fun initialize(settings: LicensePlateSettings): Boolean {
        // Default implementation for backward compatibility
        return initialize()
    }
    
    /**
     * Releases resources used by the OCR engine
     */
    fun release()
    
    /**
     * Returns true if the engine is initialized and ready to use
     */
    fun isReady(): Boolean
    
    /**
     * Gets the name/type of this OCR engine
     */
    fun getEngineName(): String
    
    /**
     * Gets whether this engine is currently using GPU acceleration
     */
    fun isUsingGpu(): Boolean = false
} 