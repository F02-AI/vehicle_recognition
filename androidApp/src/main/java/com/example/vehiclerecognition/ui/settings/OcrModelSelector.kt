package com.example.vehiclerecognition.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.vehiclerecognition.data.models.LicensePlateSettings
import com.example.vehiclerecognition.data.models.OcrModelType

/**
 * OCR model selector component for settings screen
 * Allows users to choose between different OCR engines and configure settings
 */
@Composable
fun OcrModelSelector(
    settings: LicensePlateSettings,
    onSettingsChanged: (LicensePlateSettings) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "License Plate Recognition Settings",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            // OCR Engine (Fixed to ML Kit)
            Text(
                text = "OCR Engine: ${settings.selectedOcrModel.displayName}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            // Processing Interval Slider
            ProcessingIntervalSetting(
                interval = settings.processingInterval,
                onIntervalChanged = { interval ->
                    onSettingsChanged(settings.copy(processingInterval = interval))
                }
            )
            
            // Toggle Settings
            ToggleSettings(
                settings = settings,
                onSettingsChanged = onSettingsChanged
            )
        }
    }
}

/**
 * Dropdown for selecting OCR model
 */
@Composable
private fun OcrModelDropdown(
    selectedModel: OcrModelType,
    onModelSelected: (OcrModelType) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    
    Column(modifier = modifier) {
        Text(
            text = "OCR Engine",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
        
        Spacer(modifier = Modifier.height(4.dp))
        
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = selectedModel.displayName,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = selectedModel.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = "Dropdown"
                )
            }
        }
        
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            OcrModelType.entries.forEach { model ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(
                                text = model.displayName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (model == selectedModel) FontWeight.Bold else FontWeight.Normal
                            )
                            Text(
                                text = model.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    onClick = {
                        onModelSelected(model)
                        expanded = false
                    }
                )
            }
        }
    }
}

/**
 * Processing interval setting
 */
@Composable
private fun ProcessingIntervalSetting(
    interval: Int,
    onIntervalChanged: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = "Processing Interval",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
        
        Text(
            text = "Process OCR every $interval frames",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(1, 3, 5, 10).forEach { value ->
                OutlinedButton(
                    onClick = { onIntervalChanged(value) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = value.toString(),
                        fontWeight = if (value == interval) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
    }
}

/**
 * Toggle settings for various options
 */
@Composable
private fun ToggleSettings(
    settings: LicensePlateSettings,
    onSettingsChanged: (LicensePlateSettings) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SettingToggle(
            title = "Enable OCR",
            description = "Enable/disable OCR text recognition processing",
            checked = settings.enableOcr,
            onCheckedChanged = { checked ->
                onSettingsChanged(settings.copy(enableOcr = checked))
            }
        )
        
        SettingToggle(
            title = "Debug Video Mode",
            description = "Play test video instead of camera feed for debugging",
            checked = settings.enableDebugVideo,
            onCheckedChanged = { checked ->
                onSettingsChanged(settings.copy(enableDebugVideo = checked))
            }
        )
        
        SettingToggle(
            title = "GPU Acceleration",
            description = "Use GPU for faster processing",
            checked = settings.enableGpuAcceleration,
            onCheckedChanged = { checked ->
                onSettingsChanged(settings.copy(enableGpuAcceleration = checked))
            }
        )
        
        SettingToggle(
            title = "Numeric Only Mode",
            description = "Constrain OCR to numeric characters only",
            checked = settings.enableNumericOnlyMode,
            onCheckedChanged = { checked ->
                onSettingsChanged(settings.copy(enableNumericOnlyMode = checked))
            }
        )
        
        SettingToggle(
            title = "Israeli Format Validation",
            description = "Validate plates against Israeli format rules",
            checked = settings.enableIsraeliFormatValidation,
            onCheckedChanged = { checked ->
                onSettingsChanged(settings.copy(enableIsraeliFormatValidation = checked))
            }
        )
    }
}

/**
 * Individual setting toggle component
 */
@Composable
private fun SettingToggle(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChanged
        )
    }
} 