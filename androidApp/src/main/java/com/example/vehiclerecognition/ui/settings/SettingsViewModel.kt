package com.example.vehiclerecognition.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.vehiclerecognition.domain.repository.SettingsRepository
import com.example.vehiclerecognition.model.DetectionMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<SettingsUiState>(SettingsUiState.Loading)
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadCurrentDetectionMode()
    }

    private fun loadCurrentDetectionMode() {
        viewModelScope.launch {
            val currentMode = settingsRepository.getDetectionMode()
            _uiState.value = SettingsUiState.Success(currentMode, DetectionMode.values().toList())
            println("SettingsViewModel: Loaded detection mode - $currentMode")
        }
    }

    fun selectDetectionMode(mode: DetectionMode) {
        viewModelScope.launch {
            settingsRepository.saveDetectionMode(mode)
            // Update UI state to reflect the change immediately
            _uiState.value = SettingsUiState.Success(mode, DetectionMode.values().toList())
            println("SettingsViewModel: Selected and saved detection mode - $mode")
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