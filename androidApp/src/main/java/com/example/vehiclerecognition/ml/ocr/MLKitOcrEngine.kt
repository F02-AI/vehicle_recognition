package com.example.vehiclerecognition.ml.ocr

import android.graphics.Bitmap
import com.example.vehiclerecognition.data.models.OcrResult
import com.example.vehiclerecognition.ml.processors.NumericPlateValidator
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
 */
@Singleton
class MLKitOcrEngine @Inject constructor() : OcrEngine {
    
    private val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private var isInitialized = false
    
    override suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        try {
            isInitialized = true
            true
        } catch (e: Exception) {
            isInitialized = false
            false
        }
    }
    
    override suspend fun processImage(bitmap: Bitmap): OcrResult = withContext(Dispatchers.Main) {
        val startTime = System.currentTimeMillis()
        
        try {
            val inputImage = InputImage.fromBitmap(bitmap, 0)
            
            val result = suspendCancellableCoroutine<String> { continuation ->
                textRecognizer.process(inputImage)
                    .addOnSuccessListener { visionText ->
                        // Extract all text and filter for numeric content
                        val allText = visionText.textBlocks.joinToString(" ") { block ->
                            block.lines.joinToString(" ") { line ->
                                line.elements.joinToString("") { element ->
                                    element.text
                                }
                            }
                        }
                        continuation.resume(allText)
                    }
                    .addOnFailureListener { exception ->
                        continuation.resume("")
                    }
            }
            
            // Apply numeric-only filtering
            val numericText = NumericPlateValidator.extractNumericOnly(result)
            val formattedText = NumericPlateValidator.validateAndFormatPlate(numericText)
            val isValidFormat = formattedText != null
            
            val processingTime = System.currentTimeMillis() - startTime
            
            OcrResult(
                text = numericText,
                confidence = if (numericText.isNotEmpty()) 0.75f else 0.0f,
                isValidFormat = isValidFormat,
                formattedText = formattedText,
                processingTimeMs = processingTime
            )
        } catch (e: Exception) {
            val processingTime = System.currentTimeMillis() - startTime
            OcrResult(
                text = "",
                confidence = 0.0f,
                isValidFormat = false,
                formattedText = null,
                processingTimeMs = processingTime
            )
        }
    }
    
    override fun release() {
        textRecognizer.close()
        isInitialized = false
    }
    
    override fun isReady(): Boolean = isInitialized
    
    override fun getEngineName(): String = "ML Kit"
} 