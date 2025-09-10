package com.example.vehiclerecognition.ui.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.vehiclerecognition.data.models.LicensePlateSettings
import com.example.vehiclerecognition.data.models.OcrModelType
import com.example.vehiclerecognition.data.models.CountryModel
import com.example.vehiclerecognition.data.models.Country
import com.example.vehiclerecognition.data.models.WorldCountries
import com.example.vehiclerecognition.data.models.DetectionMode
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.style.TextAlign
import com.example.vehiclerecognition.domain.service.ConfigurationStatus

// Import template configuration components
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue

// Extension function to get the display string for DetectionMode with proper separators
fun DetectionMode.toDisplayString(): String {
    val parts: List<String> = when (this) {
        DetectionMode.LP_ONLY -> listOf("LP")
        DetectionMode.COLOR_ONLY -> listOf("Color")
        DetectionMode.COLOR_TYPE -> listOf("Color", "Type")
        DetectionMode.LP_COLOR -> listOf("LP", "Color")
        DetectionMode.LP_TYPE -> listOf("LP", "Type")
        DetectionMode.LP_COLOR_TYPE -> listOf("LP", "Color", "Type")
    }
    return if (parts.size == 1) parts.first() else parts.joinToString(" + ")
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
    val focusManager = LocalFocusManager.current
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
                .verticalScroll(rememberScrollState())
                .clickable {
                    focusManager.clearFocus()
                },
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
            
            // License Plate Template Configuration
            LicensePlateTemplateConfigurationCard(
                selectedCountry = licensePlateSettings.selectedCountry
            )
            
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

            // Advanced OCR Post-processing Settings
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "Advanced OCR Settings:",
                        style = MaterialTheme.typography.titleMedium
                    )

                    // Enable Plate Candidate Generation Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Robust LP correction",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Generate and validate multiple LP candidates by fixing common OCR confusions (e.g., 0/O, 1/I, 5/S)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Switch(
                            checked = licensePlateSettings.enablePlateCandidateGeneration,
                            onCheckedChange = { enabled ->
                                onLicensePlateSettingsChanged(
                                    licensePlateSettings.copy(enablePlateCandidateGeneration = enabled)
                                )
                            }
                        )
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
    
    // Convert current Country enum to CountryModel for display
    val selectedCountryModel = remember(selectedCountry) {
        when (selectedCountry) {
            Country.ISRAEL -> CountryModel(
                id = "IL",
                displayName = "Israel",
                flagResourceId = "flag_il", // Not used anymore, but keep for consistency
                isEnabled = true
            )
            Country.UK -> CountryModel(
                id = "GB", 
                displayName = "United Kingdom",
                flagResourceId = "flag_gb", // Not used anymore, but keep for consistency
                isEnabled = true
            )
            else -> CountryModel(
                id = selectedCountry.isoCode,
                displayName = selectedCountry.displayName,
                flagResourceId = selectedCountry.flagResourceId,
                isEnabled = true
            )
        }
    }
    
    // Get world countries in alphabetical order
    val worldCountries = remember {
        WorldCountries.allCountries.sortedBy { it.displayName }
    }
    
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            value = selectedCountryModel.displayName,
            onValueChange = { },
            readOnly = true,
            label = { Text("Country") },
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
            leadingIcon = {
                // Always show country code badge for consistency
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .background(
                            MaterialTheme.colorScheme.primary,
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = selectedCountryModel.id,
                        color = MaterialTheme.colorScheme.onPrimary,
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 8.sp
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
            worldCountries.forEach { countryModel ->
                DropdownMenuItem(
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Always show country code badge for consistency
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .background(
                                        MaterialTheme.colorScheme.primary,
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = countryModel.id,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 8.sp
                                )
                            }
                            
                            Text(countryModel.displayName)
                        }
                    },
                    onClick = {
                        // Convert CountryModel back to Country enum for compatibility
                        val country = when (countryModel.id) {
                            "IL" -> Country.ISRAEL
                            "GB" -> Country.UK
                            else -> Country.fromIsoCode(countryModel.id) ?: Country.ISRAEL // Use fromIsoCode or fallback to Israel
                        }
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

@Composable
fun LicensePlateTemplateConfigurationCard(
    selectedCountry: Country
) {
    val focusManager = LocalFocusManager.current
    var showFullConfiguration by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "License Plate Templates:",
                        style = MaterialTheme.typography.titleMedium
                    )
                    
                    Text(
                        text = "Configure license plate patterns for your detection needs",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Configure Templates",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            
            // Toggle to show/hide full configuration
            Button(
                onClick = { showFullConfiguration = !showFullConfiguration },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (showFullConfiguration) "Hide Configuration" else "Configure Templates")
            }
        }
    }
    
    // Show full interactive configuration when expanded
    if (showFullConfiguration) {
        Spacer(modifier = Modifier.height(16.dp))
        
        // Use the existing template configuration components without vertical scroll
        val templateViewModel: LicensePlateTemplateViewModel = hiltViewModel()
        val uiState by templateViewModel.uiState.collectAsState()
        val templateSelectedCountry by templateViewModel.selectedCountry.collectAsState()
        val templates by templateViewModel.templates.collectAsState()
        val validationErrors by templateViewModel.validationErrors.collectAsState()
        val isSaving by templateViewModel.isSaving.collectAsState()
        val isSaved by templateViewModel.isSaved.collectAsState()
        val canSave by templateViewModel.canSave.collectAsState()
        val deletionError by templateViewModel.deletionError.collectAsState()
        
        // Sync the main settings country with the template ViewModel
        LaunchedEffect(selectedCountry) {
            // Convert Country enum to CountryModel for the template ViewModel
            val countryModel = when (selectedCountry) {
                Country.ISRAEL -> CountryModel(
                    id = "IL",
                    displayName = "Israel",
                    flagResourceId = "flag_israel",
                    isEnabled = true
                )
                Country.UK -> CountryModel(
                    id = "GB", 
                    displayName = "United Kingdom",
                    flagResourceId = "flag_uk",
                    isEnabled = true
                )
                else -> CountryModel(
                    id = selectedCountry.isoCode,
                    displayName = selectedCountry.displayName,
                    flagResourceId = selectedCountry.flagResourceId,
                    isEnabled = true
                )
            }
            templateViewModel.selectCountry(countryModel)
        }

        when (val state = uiState) {
            is TemplateUiState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxWidth().height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            is TemplateUiState.Success -> {
                // Embed the template configuration content without scaffold/scrolling
                if (templateSelectedCountry != null) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Template builder - always shown when country is selected
                        TemplateBuilderCard(
                            templates = templates,
                            validationErrors = validationErrors,
                            onTemplatePatternChanged = templateViewModel::updateTemplatePattern,
                            onAddTemplate = templateViewModel::addTemplate,
                            onDeleteTemplate = templateViewModel::deleteTemplate,
                            maxTemplates = 2
                        )
                        
                        // Save button
                        Button(
                            onClick = { 
                                focusManager.clearFocus()
                                templateViewModel.saveTemplates()
                            },
                            enabled = canSave && !isSaving && !isSaved,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isSaved) {
                                    Color(0xFF4CAF50) // Green color for saved state
                                } else {
                                    MaterialTheme.colorScheme.primary
                                },
                                disabledContainerColor = if (isSaved) {
                                    Color(0xFF4CAF50) // Keep green when disabled in saved state
                                } else {
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                                }
                            )
                        ) {
                            when {
                                isSaving -> {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp,
                                            color = MaterialTheme.colorScheme.onPrimary
                                        )
                                        Text("Saving...")
                                    }
                                }
                                isSaved -> {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                            tint = Color.White
                                        )
                                        Text(
                                            "Saved",
                                            color = Color.White
                                        )
                                    }
                                }
                                else -> Text("Save Templates")
                            }
                        }
                    }
                } else {
                    // Show message when no country is selected
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Please select a country to configure license plate templates",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(32.dp)
                        )
                    }
                }
            }
            is TemplateUiState.Error -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Error,
                            contentDescription = "Error",
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = state.message,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
        
        // Show template deletion error dialog
        deletionError?.let { error ->
            TemplateDeletionErrorDialog(
                templatePattern = error.templatePattern,
                affectedLicensePlates = error.affectedLicensePlates,
                onDismiss = { templateViewModel.dismissDeletionError() }
            )
        }
    }
}

@Composable
fun TemplateDeletionErrorDialog(
    templatePattern: String,
    affectedLicensePlates: List<String>,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
                Text("Cannot Delete Template")
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Cannot delete template '$templatePattern' because it is currently being used by watchlist entries.",
                    style = MaterialTheme.typography.bodyMedium
                )
                
                Text(
                    text = "Please delete the following license plates from the watchlist first:",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        affectedLicensePlates.forEach { licensePlate ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Error,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = licensePlate,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Medium
                                    ),
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Understood")
            }
        }
    )
} 