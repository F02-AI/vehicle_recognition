package com.example.vehiclerecognition.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.vehiclerecognition.domain.repository.SettingsRepository
import com.example.vehiclerecognition.data.models.DetectionMode
import com.example.vehiclerecognition.data.models.LicensePlateSettings
import com.example.vehiclerecognition.data.repositories.LicensePlateRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the Settings screen.
 * Handles loading and saving of the detection mode (FR 1.9, FR 1.10).
 *
 * @property settingsRepository Repository for accessing and persisting settings.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val licensePlateRepository: LicensePlateRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<SettingsUiState>(SettingsUiState.Loading)
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private val _licensePlateSettings = MutableStateFlow(LicensePlateSettings())
    val licensePlateSettings: StateFlow<LicensePlateSettings> = _licensePlateSettings.asStateFlow()

    // Expose the secondary color setting
    val includeSecondaryColor: StateFlow<Boolean> = settingsRepository.includeSecondaryColor

    init {
        loadSettings()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            // Load detection mode
            val currentMode = settingsRepository.getDetectionMode()
            _uiState.value = SettingsUiState.Success(currentMode, DetectionMode.values().toList())
            
            // Load license plate settings
            licensePlateRepository.settings.collect { settings ->
                _licensePlateSettings.value = settings
            }
        }
    }

    fun selectDetectionMode(mode: DetectionMode) {
        viewModelScope.launch {
            settingsRepository.saveDetectionMode(mode)
            // Update UI state to reflect the change immediately
            if (_uiState.value is SettingsUiState.Success) {
                _uiState.value = (_uiState.value as SettingsUiState.Success).copy(selectedMode = mode)
            }
        }
    }

    fun updateLicensePlateSettings(settings: LicensePlateSettings) {
        viewModelScope.launch {
            licensePlateRepository.updateSettings(settings)
        }
    }

    fun updateIncludeSecondaryColor(includeSecondary: Boolean) {
        viewModelScope.launch {
            settingsRepository.saveIncludeSecondaryColor(includeSecondary)
        }
    }
}

/**
 * Represents the UI state for the Settings screen.
 */
sealed interface SettingsUiState {
    object Loading : SettingsUiState
    data class Success(
        val selectedMode: DetectionMode,
        val availableModes: List<DetectionMode>
    ) : SettingsUiState
    data class Error(val message: String) : SettingsUiState // For potential future use
} 