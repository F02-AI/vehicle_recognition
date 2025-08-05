package com.example.vehiclerecognition.ui.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.vehiclerecognition.data.models.LicensePlateSettings
import com.example.vehiclerecognition.data.models.OcrModelType
import com.example.vehiclerecognition.data.models.Country
import com.example.vehiclerecognition.data.models.DetectionMode

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
        DetectionMode.COLOR_ONLY,
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

            // Country Selection Settings
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "Country Settings:",
                        style = MaterialTheme.typography.titleMedium
                    )
                    
                    Text(
                        text = "Select country for license plate format validation",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    CountryDropdown(
                        selectedCountry = licensePlateSettings.selectedCountry,
                        onCountrySelected = { country ->
                            onLicensePlateSettingsChanged(
                                licensePlateSettings.copy(selectedCountry = country)
                            )
                        }
                    )
                }
            }
            
            // Vehicle Color Detection Advanced Settings (only show when color-based mode is selected)
            if (selectedMode.involvesColor()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            "Advanced Color Settings:",
                            style = MaterialTheme.typography.titleMedium
                        )

                        // Enable Gray Filtering Toggle
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Enable Gray Filtering",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "Filter out gray colors to improve color detection accuracy",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            
                            Spacer(modifier = Modifier.width(16.dp))
                            
                            Switch(
                                checked = licensePlateSettings.enableGrayFiltering,
                                onCheckedChange = { enabled ->
                                    onLicensePlateSettingsChanged(
                                        licensePlateSettings.copy(enableGrayFiltering = enabled)
                                    )
                                }
                            )
                        }

                        // Gray Exclusion Threshold Slider (only show when gray filtering is enabled)
                        if (licensePlateSettings.enableGrayFiltering) {
                            Column {
                                Text(
                                    text = "Gray Exclusion Threshold: ${licensePlateSettings.grayExclusionThreshold.toInt()}%",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "Exclude gray color if less than this percentage of vehicle pixels are gray",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                
                                Spacer(modifier = Modifier.height(8.dp))
                                
                                Slider(
                                    value = licensePlateSettings.grayExclusionThreshold,
                                    onValueChange = { threshold ->
                                        // Round to nearest 5% for exact steps
                                        val roundedThreshold = (threshold / 5f).toInt() * 5f
                                        onLicensePlateSettingsChanged(
                                            licensePlateSettings.copy(grayExclusionThreshold = roundedThreshold)
                                        )
                                    },
                                    valueRange = 5f..95f,
                                    steps = 17, // Creates exact 5% steps: 5, 10, 15, 20, 25, 30, 35, 40, 45, 50, 55, 60, 65, 70, 75, 80, 85, 90, 95
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }

                        // Secondary Color Detection Toggle
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Secondary Color Detection",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "Detect and display secondary vehicle colors",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            
                            Spacer(modifier = Modifier.width(16.dp))
                            
                            Switch(
                                checked = licensePlateSettings.enableSecondaryColorDetection,
                                onCheckedChange = { enabled ->
                                    onLicensePlateSettingsChanged(
                                        licensePlateSettings.copy(enableSecondaryColorDetection = enabled)
                                    )
                                }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CountryDropdown(
    selectedCountry: Country,
    onCountrySelected: (Country) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            value = selectedCountry.displayName,
            onValueChange = { },
            readOnly = true,
            label = { Text("Country") },
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
            leadingIcon = {
                val flagResourceId = context.resources.getIdentifier(
                    selectedCountry.flagResourceId,
                    "drawable",
                    context.packageName
                )
                if (flagResourceId != 0) {
                    Image(
                        painter = painterResource(id = flagResourceId),
                        contentDescription = "${selectedCountry.displayName} flag",
                        modifier = Modifier.size(24.dp)
                    )
                }
            },
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth()
        )
        
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            Country.values().forEach { country ->
                DropdownMenuItem(
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            val flagResourceId = context.resources.getIdentifier(
                                country.flagResourceId,
                                "drawable",
                                context.packageName
                            )
                            if (flagResourceId != 0) {
                                Image(
                                    painter = painterResource(id = flagResourceId),
                                    contentDescription = "${country.displayName} flag",
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Text(country.displayName)
                        }
                    },
                    onClick = {
                        onCountrySelected(country)
                        expanded = false
                    }
                )
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