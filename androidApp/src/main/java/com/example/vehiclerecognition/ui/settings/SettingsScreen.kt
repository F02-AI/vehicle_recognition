package com.example.vehiclerecognition.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.vehiclerecognition.data.models.LicensePlateSettings
import com.example.vehiclerecognition.data.models.OcrModelType
import com.example.vehiclerecognition.model.DetectionMode

// Extension function to get the display string for DetectionMode
fun DetectionMode.toDisplayString(): String {
    return this.name.split('_').joinToString(" + ") { part ->
        when (part) {
            "LP" -> "LP"
            else -> part.lowercase().replaceFirstChar { it.uppercase() }
        }
    }
}

// Extension function to check if a detection mode involves color
fun DetectionMode.involvesColor(): Boolean {
    return when (this) {
        DetectionMode.COLOR,
        DetectionMode.LP_COLOR,
        DetectionMode.COLOR_TYPE,
        DetectionMode.LP_COLOR_TYPE -> true
        else -> false
    }
}

// Assuming a ViewModel instance is provided, e.g., via Hilt or a composable factory
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel() // In a real app, this would be injected (e.g. hiltViewModel())
) {
    val uiState by viewModel.uiState.collectAsState()
    val licensePlateSettings by viewModel.licensePlateSettings.collectAsState()
    val includeSecondaryColor by viewModel.includeSecondaryColor.collectAsState()
    
    when (val state = uiState) {
        is SettingsUiState.Loading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        is SettingsUiState.Success -> {
            SettingsContent(
                selectedMode = state.selectedMode,
                availableModes = state.availableModes,
                licensePlateSettings = licensePlateSettings,
                includeSecondaryColor = includeSecondaryColor,
                onModeSelected = { viewModel.selectDetectionMode(it) },
                onLicensePlateSettingsChanged = { viewModel.updateLicensePlateSettings(it) },
                onIncludeSecondaryColorChanged = { viewModel.updateIncludeSecondaryColor(it) }
            )
        }
        is SettingsUiState.Error -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Error loading settings: ${state.message}")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsContent(
    selectedMode: DetectionMode,
    availableModes: List<DetectionMode>,
    licensePlateSettings: LicensePlateSettings,
    includeSecondaryColor: Boolean,
    onModeSelected: (DetectionMode) -> Unit,
    onLicensePlateSettingsChanged: (LicensePlateSettings) -> Unit,
    onIncludeSecondaryColorChanged: (Boolean) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Settings") })
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .padding(16.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Vehicle Detection Mode Settings
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "Vehicle Detection Mode:",
                        style = MaterialTheme.typography.titleMedium
                    )

                    availableModes.forEach { mode ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = (mode == selectedMode),
                                    onClick = { onModeSelected(mode) }
                                )
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = (mode == selectedMode),
                                onClick = { onModeSelected(mode) }
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(text = mode.toDisplayString())
                        }
                    }
                }
            }

            // Secondary Color Search Setting (only show when color-based mode is selected)
            if (selectedMode.involvesColor()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "Color Detection Options:",
                            style = MaterialTheme.typography.titleMedium
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Include Secondary Color",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "Also search for secondary vehicle colors in addition to primary colors",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            
                            Spacer(modifier = Modifier.width(16.dp))
                            
                            Switch(
                                checked = includeSecondaryColor,
                                onCheckedChange = onIncludeSecondaryColorChanged
                            )
                        }
                    }
                }
            }
            
            // License Plate Recognition Settings - Hidden as requested
            // OcrModelSelector(
            //     settings = licensePlateSettings,
            //     onSettingsChanged = onLicensePlateSettingsChanged
            // )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun SettingsContentPreview() {
    // Dummy data for preview
    val modes = DetectionMode.values().toList()
    val selected = modes.first()
    val licensePlateSettings = LicensePlateSettings()
    
    MaterialTheme {
        SettingsContent(
            selectedMode = selected,
            availableModes = modes,
            licensePlateSettings = licensePlateSettings,
            includeSecondaryColor = false,
            onModeSelected = {},
            onLicensePlateSettingsChanged = {},
            onIncludeSecondaryColorChanged = {}
        )
    }
} 