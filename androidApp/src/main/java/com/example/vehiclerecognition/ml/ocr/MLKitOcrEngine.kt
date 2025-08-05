package com.example.vehiclerecognition.ml.ocr

import android.graphics.Bitmap
import android.util.Log
import com.example.vehiclerecognition.data.models.OcrResult
import com.example.vehiclerecognition.data.models.LicensePlateSettings
import com.example.vehiclerecognition.ml.processors.NumericPlateValidator
import com.example.vehiclerecognition.ml.processors.CountryAwarePlateValidator
import com.example.vehiclerecognition.data.models.Country
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * ML Kit OCR engine implementation using Google's text recognition
 * Enhanced to work optimally with pre-scaled images for better accuracy
 */
@Singleton
class MLKitOcrEngine @Inject constructor() : OcrEngine {
    
    companion object {
        private const val TAG = "MLKitOcrEngine"
    }
    
    private val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private var isInitialized = false
    private var currentCountry = Country.ISRAEL // Default to Israel for backward compatibility
    
    override suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        try {
            isInitialized = true
            Log.d(TAG, "ML Kit OCR engine initialized successfully")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize ML Kit OCR engine", e)
            isInitialized = false
            false
        }
    }
    
    override suspend fun initialize(settings: LicensePlateSettings): Boolean {
        currentCountry = settings.selectedCountry
        Log.d(TAG, "ML Kit OCR engine initialized with country: ${currentCountry.displayName}")
        return initialize()
    }
    
    override suspend fun processImage(bitmap: Bitmap): OcrResult = withContext(Dispatchers.Main) {
        val startTime = System.currentTimeMillis()
        
        try {
            Log.d(TAG, "Processing image: ${bitmap.width}x${bitmap.height} pixels")
            
            // Create InputImage from the pre-scaled bitmap
            // The bitmap has already been optimally scaled by LicensePlateProcessor
            val inputImage = InputImage.fromBitmap(bitmap, 0)
            
            val result = suspendCancellableCoroutine<String> { continuation ->
                textRecognizer.process(inputImage)
                    .addOnSuccessListener { visionText ->
                        // Extract all text with improved processing for license plates
                        val allText = extractTextFromVisionResult(visionText)
                        Log.d(TAG, "ML Kit raw text result: '$allText'")
                        continuation.resume(allText)
                    }
                    .addOnFailureListener { exception ->
                        Log.e(TAG, "ML Kit text recognition failed", exception)
                        continuation.resume("")
                    }
            }
            
            // Apply country-specific validation
            val relevantText = CountryAwarePlateValidator.extractRelevantCharacters(result, currentCountry)
            val formattedText = CountryAwarePlateValidator.validateAndFormatPlate(relevantText, currentCountry)
            val isValidFormat = formattedText != null
            
            val processingTime = System.currentTimeMillis() - startTime
            
            Log.d(TAG, "OCR processing completed in ${processingTime}ms")
            Log.d(TAG, "Extracted text for ${currentCountry.displayName}: '$relevantText', formatted: '$formattedText', valid: $isValidFormat")
            
            OcrResult(
                text = relevantText,
                confidence = if (relevantText.isNotEmpty()) calculateConfidence(result, relevantText) else 0.0f,
                isValidFormat = isValidFormat,
                formattedText = formattedText,
                processingTimeMs = processingTime
            )
        } catch (e: Exception) {
            val processingTime = System.currentTimeMillis() - startTime
            Log.e(TAG, "OCR processing failed", e)
            OcrResult(
                text = "",
                confidence = 0.0f,
                isValidFormat = false,
                formattedText = null,
                processingTimeMs = processingTime
            )
        }
    }
    
    /**
     * Enhanced text extraction from ML Kit Vision result
     * Optimized for license plate recognition
     */
    private fun extractTextFromVisionResult(visionText: com.google.mlkit.vision.text.Text): String {
        // Strategy 1: Try to get text from text blocks with highest confidence
        val blockTexts = visionText.textBlocks.map { block ->
            block.lines.joinToString(" ") { line ->
                line.elements.joinToString("") { element ->
                    element.text
                }
            }
        }
        
        if (blockTexts.isNotEmpty()) {
            val combinedText = blockTexts.joinToString(" ")
            if (combinedText.isNotBlank()) {
                return combinedText.trim()
            }
        }
        
        // Strategy 2: Fallback to overall recognized text
        return visionText.text.trim()
    }
    
    /**
     * Calculate confidence score based on text quality and length
     * Higher confidence for longer, more complete license plate numbers
     */
    private fun calculateConfidence(rawText: String, numericText: String): Float {
        return when {
            numericText.isEmpty() -> 0.0f
            numericText.length >= 7 -> 0.9f // Full Israeli license plate
            numericText.length >= 5 -> 0.75f // Partial but substantial
            numericText.length >= 3 -> 0.6f // Some digits detected
            else -> 0.4f // Very few digits
        }
    }
    
    override fun release() {
        try {
            textRecognizer.close()
            isInitialized = false
            Log.d(TAG, "ML Kit OCR engine resources released")
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing ML Kit OCR engine resources", e)
        }
    }
    
    override fun isReady(): Boolean = isInitialized
    
    override fun getEngineName(): String = "ML Kit (Enhanced)"
} 