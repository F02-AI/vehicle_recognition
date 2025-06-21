package com.example.vehiclerecognition.ui.camera

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.vehiclerecognition.data.models.PlateDetection

/**
 * Overlay composable for displaying license plate detection boxes and recognized text
 * Shows real-time detection results on the camera preview
 */
@Composable
fun LicensePlateOverlay(
    detectedPlates: List<PlateDetection>,
    recognizedText: String?,
    overlayBounds: Rect,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        // Draw detection boxes
        Canvas(modifier = Modifier.fillMaxSize()) {
            detectedPlates.forEach { plate ->
                drawLicensePlateDetection(
                    plateDetection = plate,
                    overlayBounds = overlayBounds
                )
            }
        }
        
        // Display recognized text in upper-right corner
        recognizedText?.let { text ->
            PlateTextDisplay(
                text = text,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
            )
        }
    }
}

/**
 * Displays the recognized license plate text with formatting
 */
@Composable
private fun PlateTextDisplay(
    text: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(
                Color.Black.copy(alpha = 0.8f)
            )
            .padding(12.dp)
    ) {
        Text(
            text = "License: $text",
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

/**
 * Draws a license plate detection box with confidence indicator
 */
private fun DrawScope.drawLicensePlateDetection(
    plateDetection: PlateDetection,
    overlayBounds: Rect
) {
    val boundingBox = plateDetection.boundingBox
    
    // Scale bounding box to overlay coordinates
    val scaleX = size.width / overlayBounds.width
    val scaleY = size.height / overlayBounds.height
    
    val scaledLeft = boundingBox.left * scaleX
    val scaledTop = boundingBox.top * scaleY
    val scaledRight = boundingBox.right * scaleX
    val scaledBottom = boundingBox.bottom * scaleY
    
    // Determine box color based on confidence and validation
    val boxColor = when {
        plateDetection.isValidFormat -> Color.Green
        plateDetection.confidence > 0.7f -> Color.Yellow
        else -> Color.Red
    }
    
    // Draw main detection box
    drawRect(
        color = boxColor,
        topLeft = Offset(scaledLeft, scaledTop),
        size = Size(scaledRight - scaledLeft, scaledBottom - scaledTop),
        style = Stroke(width = 3.dp.toPx())
    )
    
    // Draw confidence indicator background
    val confidenceText = "${(plateDetection.confidence * 100).toInt()}%"
    val textSize = 12.sp.toPx()
    
    drawRect(
        color = boxColor.copy(alpha = 0.8f),
        topLeft = Offset(scaledLeft, scaledTop - textSize - 4.dp.toPx()),
        size = Size(60.dp.toPx(), textSize + 4.dp.toPx())
    )
    
    // Note: For now, we'll skip text drawing as it requires more complex setup in Compose
    // TODO: Implement proper text drawing using TextLayoutResult and drawText
    
    // Draw recognized text background if available
    plateDetection.recognizedText?.let { text ->
        val recognizedTextSize = 10.sp.toPx()
        
        drawRect(
            color = Color.Black.copy(alpha = 0.8f),
            topLeft = Offset(scaledLeft, scaledBottom),
            size = Size((text.length * 8).dp.toPx(), recognizedTextSize + 4.dp.toPx())
        )
        
        // TODO: Implement proper text drawing for recognized text
    }
} 