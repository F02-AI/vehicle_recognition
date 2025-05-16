package com.example.vehiclerecognition.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.vehiclerecognition.model.DetectionMode

// Extension function to get the display string for DetectionMode
fun DetectionMode.toDisplayString(): String {
    return this.name.split('_').joinToString(" + ") { part ->
        when (part) {
            "LP" -> "LP"
            else -> part.toLowerCase().capitalize()
        }
    }
}

// Assuming a ViewModel instance is provided, e.g., via Hilt or a composable factory
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel // In a real app, this would be injected (e.g. hiltViewModel())
) {
    val uiState by viewModel.uiState.collectAsState()

    when (val state = uiState) {
        is SettingsUiState.Loading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        is SettingsUiState.Success -> {
            SettingsContent(state.selectedMode, state.availableModes) {
                viewModel.selectDetectionMode(it)
            }
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
    onModeSelected: (DetectionMode) -> Unit
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
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Select Detection Mode:", style = MaterialTheme.typography.titleMedium)

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
}

@Preview(showBackground = true)
@Composable
fun SettingsContentPreview() {
    // Dummy data for preview
    val modes = DetectionMode.values().toList()
    val selected = modes.first()
    MaterialTheme {
        SettingsContent(selectedMode = selected, availableModes = modes, onModeSelected = {})
    }
} 