package com.example.vehiclerecognition.ui.watchlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.vehiclerecognition.domain.repository.WatchlistRepository
import com.example.vehiclerecognition.domain.validation.LicensePlateValidator
import com.example.vehiclerecognition.model.VehicleColor
import com.example.vehiclerecognition.model.VehicleType
import com.example.vehiclerecognition.model.WatchlistEntry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the Watchlist screen.
 * Handles loading, adding, and deleting watchlist entries (FR 1.5, FR 1.6, FR 1.7).
 *
 * @property watchlistRepository Repository for watchlist data operations.
 * @property licensePlateValidator Validator for license plate formats.
 */
@HiltViewModel
class WatchlistViewModel @Inject constructor(
    private val watchlistRepository: WatchlistRepository,
    private val licensePlateValidator: LicensePlateValidator
) : ViewModel() {

    private val _uiState = MutableStateFlow<WatchlistUiState>(WatchlistUiState.Loading)
    val uiState: StateFlow<WatchlistUiState> = _uiState.asStateFlow()

    // For the Add Entry Dialog
    private val _showAddDialog = MutableStateFlow(false)
    val showAddDialog: StateFlow<Boolean> = _showAddDialog.asStateFlow()

    private val _newLicensePlate = MutableStateFlow("")
    val newLicensePlate: StateFlow<String> = _newLicensePlate.asStateFlow()

    private val _newVehicleType = MutableStateFlow(VehicleType.CAR) // Default
    val newVehicleType: StateFlow<VehicleType> = _newVehicleType.asStateFlow()

    private val _newVehicleColor = MutableStateFlow(VehicleColor.BLACK) // Default
    val newVehicleColor: StateFlow<VehicleColor> = _newVehicleColor.asStateFlow()

    private val _lpValidationError = MutableStateFlow<String?>(null)
    val lpValidationError: StateFlow<String?> = _lpValidationError.asStateFlow()

    // For Snackbar messages
    private val _errorEvent = MutableSharedFlow<String>()
    val errorEvent: SharedFlow<String> = _errorEvent.asSharedFlow()

    init {
        loadWatchlistEntries()
    }

    fun loadWatchlistEntries() {
        viewModelScope.launch {
            _uiState.value = WatchlistUiState.Loading
            try {
                val entries = watchlistRepository.getAllEntries()
                _uiState.value = WatchlistUiState.Success(entries)
                println("WatchlistViewModel: Loaded ${'$'}{entries.size} watchlist entries")
            } catch (e: Exception) {
                val errorMsg = "Error loading watchlist: ${e.localizedMessage}"
                _uiState.value = WatchlistUiState.Error(errorMsg)
                _errorEvent.emit(errorMsg)
                println("WatchlistViewModel: $errorMsg")
            }
        }
    }

    fun addWatchlistEntry() {
        val lp = _newLicensePlate.value.trim()
        if (!licensePlateValidator.isValid(lp)) {
            _lpValidationError.value = "Invalid LP format. Use NN-NNN-NN, NNN-NN-NNN, or N-NNNN-NN."
            println("WatchlistViewModel: Add failed - Invalid LP format: $lp")
            return
        }
        _lpValidationError.value = null

        val entry = WatchlistEntry(
            licensePlate = lp,
            vehicleType = _newVehicleType.value,
            vehicleColor = _newVehicleColor.value
        )
        viewModelScope.launch {
            val success = watchlistRepository.addEntry(entry)
            if (success) {
                loadWatchlistEntries() // Refresh list
                dismissAddDialog(true) // Reset fields and close
                println("WatchlistViewModel: Added entry - $lp")
            } else {
                val errorMsg = "Failed to add entry. LP might already exist or DB error."
                // _lpValidationError.value = errorMsg // Keep this for dialog specific error
                _errorEvent.emit(errorMsg) // Emit for Snackbar
                println("WatchlistViewModel: $errorMsg")
            }
        }
    }

    fun deleteWatchlistEntry(licensePlate: String) {
        viewModelScope.launch {
            val success = watchlistRepository.deleteEntry(licensePlate)
            if (success) {
                loadWatchlistEntries() // Refresh list
                println("WatchlistViewModel: Deleted entry - $licensePlate")
            } else {
                val errorMsg = "Failed to delete entry for $licensePlate."
                _errorEvent.emit(errorMsg) // Emit for Snackbar
                println("WatchlistViewModel: $errorMsg")
            }
        }
    }

    // Dialog and input field management
    fun onNewLicensePlateChange(lp: String) { _newLicensePlate.value = lp; _lpValidationError.value = null }
    fun onNewVehicleTypeChange(type: VehicleType) { _newVehicleType.value = type }
    fun onNewVehicleColorChange(color: VehicleColor) { _newVehicleColor.value = color }

    fun openAddDialog() { _showAddDialog.value = true }
    fun dismissAddDialog(resetFields: Boolean = true) {
        _showAddDialog.value = false
        if (resetFields) {
            _newLicensePlate.value = ""
            _newVehicleType.value = VehicleType.CAR
            _newVehicleColor.value = VehicleColor.BLACK
            _lpValidationError.value = null
        }
    }
}

sealed interface WatchlistUiState {
    object Loading : WatchlistUiState
    data class Success(val entries: List<WatchlistEntry>) : WatchlistUiState
    data class Error(val message: String) : WatchlistUiState // For potential future use
} 