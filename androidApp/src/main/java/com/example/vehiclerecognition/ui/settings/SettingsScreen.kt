package com.example.vehiclerecognition.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

// Assuming a ViewModel instance is provided, e.g., via Hilt or a composable factory
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel() // In a real app, this would be injected (e.g. hiltViewModel())
) {
    val uiState by viewModel.uiState.collectAsState()
    val licensePlateSettings by viewModel.licensePlateSettings.collectAsState()
    
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
                onModeSelected = { viewModel.selectDetectionMode(it) },
                onLicensePlateSettingsChanged = { viewModel.updateLicensePlateSettings(it) }
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
    onModeSelected: (DetectionMode) -> Unit,
    onLicensePlateSettingsChanged: (LicensePlateSettings) -> Unit
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
            
            // License Plate Recognition Settings
            OcrModelSelector(
                settings = licensePlateSettings,
                onSettingsChanged = onLicensePlateSettingsChanged
            )
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
            onModeSelected = {},
            onLicensePlateSettingsChanged = {}
        )
    }
} 