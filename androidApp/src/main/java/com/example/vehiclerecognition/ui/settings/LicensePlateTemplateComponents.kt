package com.example.vehiclerecognition.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.vehiclerecognition.data.models.Country
import com.example.vehiclerecognition.domain.service.ConfigurationStatus

/**
 * Main license plate template configuration screen
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LicensePlateTemplateScreen(
    viewModel: LicensePlateTemplateViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val selectedCountry by viewModel.selectedCountry.collectAsState()
    val templates by viewModel.templates.collectAsState()
    val validationErrors by viewModel.validationErrors.collectAsState()
    val isSaving by viewModel.isSaving.collectAsState()
    val canSave by viewModel.canSave.collectAsState()

    when (val state = uiState) {
        is TemplateUiState.Loading -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
        is TemplateUiState.Success -> {
            LicensePlateTemplateContent(
                availableCountries = state.availableCountries,
                configurationStatus = state.configurationStatus,
                selectedCountry = selectedCountry,
                templates = templates,
                validationErrors = validationErrors,
                isSaving = isSaving,
                canSave = canSave,
                onCountrySelected = viewModel::selectCountry,
                onTemplatePatternChanged = viewModel::updateTemplatePattern,
                onAddTemplate = viewModel::addTemplate,
                onDeleteTemplate = viewModel::deleteTemplate,
                onSaveTemplates = viewModel::saveTemplates
            )
        }
        is TemplateUiState.Error -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Error,
                        contentDescription = "Error",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = state.message,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LicensePlateTemplateContent(
    availableCountries: List<Country>,
    configurationStatus: ConfigurationStatus,
    selectedCountry: Country?,
    templates: List<EditableTemplate>,
    validationErrors: Map<Int, String>,
    isSaving: Boolean,
    canSave: Boolean,
    onCountrySelected: (Country) -> Unit,
    onTemplatePatternChanged: (Int, String) -> Unit,
    onAddTemplate: () -> Unit,
    onDeleteTemplate: (Int) -> Unit,
    onSaveTemplates: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("License Plate Templates") },
                actions = {
                    Button(
                        onClick = onSaveTemplates,
                        enabled = canSave && !isSaving,
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Text("Save")
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .padding(16.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Configuration status card
            ConfigurationStatusCard(configurationStatus)
            
            // Country selection
            CountrySelectionCard(
                availableCountries = availableCountries,
                selectedCountry = selectedCountry,
                onCountrySelected = onCountrySelected
            )
            
            // Template builder
            if (selectedCountry != null) {
                TemplateBuilderCard(
                    templates = templates,
                    validationErrors = validationErrors,
                    onTemplatePatternChanged = onTemplatePatternChanged,
                    onAddTemplate = onAddTemplate,
                    onDeleteTemplate = onDeleteTemplate,
                    maxTemplates = 2
                )
            }
        }
    }
}

@Composable
fun ConfigurationStatusCard(configurationStatus: ConfigurationStatus) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (configurationStatus.isFullyConfigured) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.errorContainer
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (configurationStatus.isFullyConfigured) {
                        Icons.Default.CheckCircle
                    } else {
                        Icons.Default.Warning
                    },
                    contentDescription = null,
                    tint = if (configurationStatus.isFullyConfigured) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onErrorContainer
                    },
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Configuration Status",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (configurationStatus.isFullyConfigured) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onErrorContainer
                    }
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "${configurationStatus.configuredCountries}/${configurationStatus.totalCountries} countries configured",
                style = MaterialTheme.typography.bodyMedium,
                color = if (configurationStatus.isFullyConfigured) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onErrorContainer
                }
            )
            
            if (!configurationStatus.isFullyConfigured) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Needs configuration: ${configurationStatus.needsConfiguration.joinToString(", ") { it.displayName }}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CountrySelectionCard(
    availableCountries: List<Country>,
    selectedCountry: Country?,
    onCountrySelected: (Country) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Select Country",
                style = MaterialTheme.typography.titleMedium
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "Choose a country to configure license plate templates",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Country selection buttons
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(availableCountries) { country ->
                    CountrySelectionButton(
                        country = country,
                        isSelected = selectedCountry == country,
                        onClick = { onCountrySelected(country) }
                    )
                }
            }
        }
    }
}

@Composable
fun CountrySelectionButton(
    country: Country,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    
    Card(
        modifier = Modifier
            .clickable { onClick() }
            .border(
                width = if (isSelected) 2.dp else 0.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = RoundedCornerShape(8.dp)
            ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isSelected) 8.dp else 4.dp
        ),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Column(
            modifier = Modifier
                .padding(12.dp)
                .width(100.dp),
            horizontalAlignment = Alignment.CenterHorizontally
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
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                )
            }
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Text(
                text = country.displayName,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                color = if (isSelected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )
        }
    }
}

@Composable
fun TemplateBuilderCard(
    templates: List<EditableTemplate>,
    validationErrors: Map<Int, String>,
    onTemplatePatternChanged: (Int, String) -> Unit,
    onAddTemplate: () -> Unit,
    onDeleteTemplate: (Int) -> Unit,
    maxTemplates: Int = 2
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Template Patterns",
                    style = MaterialTheme.typography.titleMedium
                )
                
                if (templates.size < maxTemplates) {
                    IconButton(
                        onClick = onAddTemplate,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add Template",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "Use 'L' for letters and 'N' for numbers (max 12 characters)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            templates.forEachIndexed { index, template ->
                TemplateEditor(
                    template = template,
                    index = index,
                    validationError = validationErrors[index],
                    onPatternChanged = { pattern -> onTemplatePatternChanged(index, pattern) },
                    onDelete = { onDeleteTemplate(index) },
                    canDelete = templates.size > 1
                )
                
                if (index < templates.size - 1) {
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
            
            // Pattern guide
            Spacer(modifier = Modifier.height(16.dp))
            PatternGuide()
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun TemplateEditor(
    template: EditableTemplate,
    index: Int,
    validationError: String?,
    onPatternChanged: (String) -> Unit,
    onDelete: () -> Unit,
    canDelete: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (validationError != null) {
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.1f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = template.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                
                if (canDelete) {
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete Template",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Pattern input with character buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Pattern display/input
                BasicTextField(
                    value = template.pattern,
                    onValueChange = onPatternChanged,
                    textStyle = TextStyle(
                        fontSize = 18.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = if (validationError != null) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    ),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { /* Keyboard will hide automatically */ }
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier
                        .weight(1f)
                        .background(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(4.dp)
                        )
                        .border(
                            width = 1.dp,
                            color = if (validationError != null) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.outline
                            },
                            shape = RoundedCornerShape(4.dp)
                        )
                        .padding(12.dp)
                )
                
                Spacer(modifier = Modifier.width(8.dp))
                
                // Pattern input buttons
                PatternInputButtons(
                    onLetterClick = { 
                        onPatternChanged(template.pattern + "L")
                    },
                    onNumberClick = { 
                        onPatternChanged(template.pattern + "N")
                    },
                    onClearClick = {
                        onPatternChanged("")
                    }
                )
            }
            
            // Validation error message
            AnimatedVisibility(
                visible = validationError != null,
                enter = slideInVertically() + fadeIn(),
                exit = slideOutVertically() + fadeOut()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Error,
                        contentDescription = "Error",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = validationError ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            
            // Pattern preview
            if (template.pattern.isNotBlank() && validationError == null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Example: ${generatePatternExample(template.pattern)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
fun PatternInputButtons(
    onLetterClick: () -> Unit,
    onNumberClick: () -> Unit,
    onClearClick: () -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Button(
            onClick = onLetterClick,
            modifier = Modifier.size(40.dp),
            contentPadding = PaddingValues(0.dp)
        ) {
            Text("L", fontSize = 12.sp)
        }
        
        Button(
            onClick = onNumberClick,
            modifier = Modifier.size(40.dp),
            contentPadding = PaddingValues(0.dp)
        ) {
            Text("N", fontSize = 12.sp)
        }
        
        OutlinedButton(
            onClick = onClearClick,
            modifier = Modifier.size(40.dp),
            contentPadding = PaddingValues(0.dp)
        ) {
            Text("×", fontSize = 16.sp)
        }
    }
}

@Composable
fun PatternGuide() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Text(
                text = "Pattern Guide",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "L = Letter",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "A, B, C...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "N = Number",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "0, 1, 2...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "Examples: NNNNNN (123456), LLNNLLL (AB12CDE)",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Generates an example license plate text based on the pattern
 */
fun generatePatternExample(pattern: String): String {
    return pattern.map { char ->
        when (char) {
            'L' -> ('A'..'Z').random()
            'N' -> ('0'..'9').random()
            else -> char
        }
    }.joinToString("")
}

@Preview(showBackground = true)
@Composable
fun PatternGuidePreview() {
    MaterialTheme {
        PatternGuide()
    }
}

@Preview(showBackground = true)
@Composable
fun TemplateEditorPreview() {
    MaterialTheme {
        TemplateEditor(
            template = EditableTemplate(
                id = 1,
                pattern = "LLNNLLL",
                displayName = "Template 1",
                priority = 1,
                isValid = true,
                validationMessage = null
            ),
            index = 0,
            validationError = null,
            onPatternChanged = {},
            onDelete = {},
            canDelete = true
        )
    }
}