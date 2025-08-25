package com.example.vehiclerecognition.ui.settings

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.vehiclerecognition.data.models.Country
import com.example.vehiclerecognition.data.models.LicensePlateTemplate
import com.example.vehiclerecognition.domain.repository.TemplateValidationResult
import com.example.vehiclerecognition.domain.service.ConfigurationStatus
import com.example.vehiclerecognition.domain.service.LicensePlateTemplateService
import com.example.vehiclerecognition.domain.service.TemplateOperationResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for managing license plate template configuration
 */
@HiltViewModel
class LicensePlateTemplateViewModel @Inject constructor(
    private val templateService: LicensePlateTemplateService
) : ViewModel() {
    
    companion object {
        private const val TAG = "LPTemplateViewModel"
    }

    private val _uiState = MutableStateFlow<TemplateUiState>(TemplateUiState.Loading)
    val uiState: StateFlow<TemplateUiState> = _uiState.asStateFlow()
    
    private val _selectedCountry = MutableStateFlow<Country?>(null)
    val selectedCountry: StateFlow<Country?> = _selectedCountry.asStateFlow()
    
    private val _templates = MutableStateFlow<List<EditableTemplate>>(emptyList())
    val templates: StateFlow<List<EditableTemplate>> = _templates.asStateFlow()
    
    private val _configurationStatus = MutableStateFlow<ConfigurationStatus?>(null)
    val configurationStatus: StateFlow<ConfigurationStatus?> = _configurationStatus.asStateFlow()
    
    private val _validationErrors = MutableStateFlow<Map<Int, String>>(emptyMap())
    val validationErrors: StateFlow<Map<Int, String>> = _validationErrors.asStateFlow()
    
    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()
    
    // Combined state for UI to determine if save button should be enabled
    val canSave: StateFlow<Boolean> = combine(
        _templates,
        _validationErrors,
        _isSaving
    ) { templates, errors, saving ->
        !saving && 
        templates.isNotEmpty() && 
        templates.any { it.pattern.isNotBlank() } &&
        errors.isEmpty()
    }.stateIn(
        scope = viewModelScope,
        started = kotlinx.coroutines.flow.SharingStarted.Lazily,
        initialValue = false
    )

    init {
        loadInitialData()
    }

    private fun loadInitialData() {
        viewModelScope.launch {
            try {
                _uiState.value = TemplateUiState.Loading
                
                // Initialize the template system
                templateService.initializeSystem()
                
                // Load available countries and configuration status
                templateService.getAvailableCountries().collect { countries ->
                    val configStatus = templateService.getConfigurationStatus()
                    _configurationStatus.value = configStatus
                    
                    _uiState.value = TemplateUiState.Success(
                        availableCountries = countries,
                        configurationStatus = configStatus
                    )
                    
                    // Auto-select first country if none selected
                    if (_selectedCountry.value == null && countries.isNotEmpty()) {
                        selectCountry(countries.first())
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading initial data", e)
                _uiState.value = TemplateUiState.Error("Failed to load template data: ${e.message}")
            }
        }
    }

    fun selectCountry(country: Country) {
        if (_selectedCountry.value == country) return
        
        viewModelScope.launch {
            _selectedCountry.value = country
            loadTemplatesForCountry(country.name)
        }
    }

    private suspend fun loadTemplatesForCountry(countryId: String) {
        try {
            templateService.getTemplatesForCountry(countryId).collect { existingTemplates ->
                val editableTemplates = if (existingTemplates.isEmpty()) {
                    // Create default templates for the country
                    val defaults = templateService.createDefaultTemplatesForCountry(countryId)
                    defaults.map { template ->
                        EditableTemplate(
                            id = 0, // New template
                            pattern = template.templatePattern,
                            displayName = template.displayName,
                            priority = template.priority,
                            isValid = true,
                            validationMessage = null
                        )
                    }.ifEmpty {
                        // If no defaults available, create empty templates
                        listOf(
                            EditableTemplate(
                                id = 0,
                                pattern = "",
                                displayName = "Template 1",
                                priority = 1,
                                isValid = false,
                                validationMessage = null
                            )
                        )
                    }
                } else {
                    // Convert existing templates to editable format
                    existingTemplates.map { template ->
                        EditableTemplate(
                            id = template.id,
                            pattern = template.templatePattern,
                            displayName = template.displayName,
                            priority = template.priority,
                            isValid = true,
                            validationMessage = null
                        )
                    }
                }
                
                _templates.value = editableTemplates
                validateAllTemplates()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading templates for country: $countryId", e)
        }
    }

    fun updateTemplatePattern(index: Int, pattern: String) {
        val currentTemplates = _templates.value.toMutableList()
        if (index >= 0 && index < currentTemplates.size) {
            currentTemplates[index] = currentTemplates[index].copy(
                pattern = pattern.uppercase().take(12) // Limit to 12 characters and make uppercase
            )
            _templates.value = currentTemplates
            validateTemplate(index, pattern.uppercase())
        }
    }

    fun addTemplate() {
        val currentTemplates = _templates.value
        if (currentTemplates.size < 2) {
            val newPriority = currentTemplates.size + 1
            val newTemplate = EditableTemplate(
                id = 0,
                pattern = "",
                displayName = "Template $newPriority",
                priority = newPriority,
                isValid = false,
                validationMessage = null
            )
            _templates.value = currentTemplates + newTemplate
        }
    }

    fun deleteTemplate(index: Int) {
        val currentTemplates = _templates.value.toMutableList()
        if (index >= 0 && index < currentTemplates.size && currentTemplates.size > 1) {
            currentTemplates.removeAt(index)
            // Update priorities
            currentTemplates.forEachIndexed { i, template ->
                currentTemplates[i] = template.copy(
                    priority = i + 1,
                    displayName = "Template ${i + 1}"
                )
            }
            _templates.value = currentTemplates
            
            // Clear validation error for deleted template
            val currentErrors = _validationErrors.value.toMutableMap()
            currentErrors.remove(index)
            // Adjust error indices for remaining templates
            val adjustedErrors = mutableMapOf<Int, String>()
            currentErrors.forEach { (errorIndex, message) ->
                if (errorIndex < index) {
                    adjustedErrors[errorIndex] = message
                } else if (errorIndex > index) {
                    adjustedErrors[errorIndex - 1] = message
                }
            }
            _validationErrors.value = adjustedErrors
        }
    }

    private fun validateTemplate(index: Int, pattern: String) {
        viewModelScope.launch {
            val currentErrors = _validationErrors.value.toMutableMap()
            
            if (pattern.isBlank()) {
                currentErrors[index] = "Template pattern cannot be empty"
            } else {
                try {
                    // Create a temporary template for validation
                    val tempTemplate = LicensePlateTemplate(
                        id = 0,
                        countryId = _selectedCountry.value?.name ?: "",
                        templatePattern = pattern,
                        displayName = "Template ${index + 1}",
                        priority = index + 1,
                        description = LicensePlateTemplate.generateDescription(pattern),
                        regexPattern = LicensePlateTemplate.templatePatternToRegex(pattern)
                    )
                    
                    // Basic pattern validation
                    if (pattern.any { it !in listOf('L', 'N') }) {
                        currentErrors[index] = "Pattern can only contain 'L' (letter) and 'N' (number)"
                    } else if (pattern.length > 12) {
                        currentErrors[index] = "Pattern cannot exceed 12 characters"
                    } else {
                        currentErrors.remove(index)
                    }
                } catch (e: Exception) {
                    currentErrors[index] = "Invalid pattern: ${e.message}"
                }
            }
            
            _validationErrors.value = currentErrors
            
            // Update template validity
            val currentTemplates = _templates.value.toMutableList()
            if (index < currentTemplates.size) {
                val isValid = !currentErrors.containsKey(index)
                currentTemplates[index] = currentTemplates[index].copy(
                    isValid = isValid,
                    validationMessage = currentErrors[index]
                )
                _templates.value = currentTemplates
            }
        }
    }

    private fun validateAllTemplates() {
        _templates.value.forEachIndexed { index, template ->
            validateTemplate(index, template.pattern)
        }
    }

    fun saveTemplates() {
        val selectedCountry = _selectedCountry.value
        if (selectedCountry == null) {
            Log.w(TAG, "No country selected for saving templates")
            return
        }

        viewModelScope.launch {
            _isSaving.value = true
            
            try {
                val validTemplates = _templates.value.filter { 
                    it.pattern.isNotBlank() && it.isValid 
                }
                
                if (validTemplates.isEmpty()) {
                    Log.w(TAG, "No valid templates to save")
                    _isSaving.value = false
                    return@launch
                }
                
                val licenseTemplates = validTemplates.map { editableTemplate ->
                    LicensePlateTemplate(
                        id = editableTemplate.id,
                        countryId = selectedCountry.name,
                        templatePattern = editableTemplate.pattern,
                        displayName = editableTemplate.displayName,
                        priority = editableTemplate.priority,
                        description = LicensePlateTemplate.generateDescription(editableTemplate.pattern),
                        regexPattern = LicensePlateTemplate.templatePatternToRegex(editableTemplate.pattern)
                    )
                }
                
                val result = templateService.saveTemplatesForCountry(selectedCountry.name, licenseTemplates)
                
                if (result.success) {
                    Log.i(TAG, "Templates saved successfully: ${result.message}")
                    // Refresh configuration status
                    _configurationStatus.value = templateService.getConfigurationStatus()
                } else {
                    Log.e(TAG, "Failed to save templates: ${result.message}")
                    _uiState.value = TemplateUiState.Error("Failed to save templates: ${result.message}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error saving templates", e)
                _uiState.value = TemplateUiState.Error("Error saving templates: ${e.message}")
            } finally {
                _isSaving.value = false
            }
        }
    }

    fun getConfigurationWarningMessage(): String? {
        val status = _configurationStatus.value ?: return null
        
        return if (!status.isFullyConfigured) {
            val unconfiguredCountries = status.needsConfiguration.joinToString(", ") { it.displayName }
            "Template configuration incomplete for: $unconfiguredCountries"
        } else null
    }
}

/**
 * UI State for template configuration screen
 */
sealed interface TemplateUiState {
    object Loading : TemplateUiState
    data class Success(
        val availableCountries: List<Country>,
        val configurationStatus: ConfigurationStatus
    ) : TemplateUiState
    data class Error(val message: String) : TemplateUiState
}

/**
 * Editable template representation for UI
 */
data class EditableTemplate(
    val id: Int,
    val pattern: String,
    val displayName: String,
    val priority: Int,
    val isValid: Boolean,
    val validationMessage: String?
)