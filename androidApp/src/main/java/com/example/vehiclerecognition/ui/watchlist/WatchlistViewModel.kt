package com.example.vehiclerecognition.ui.watchlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.vehiclerecognition.domain.repository.WatchlistRepository
import com.example.vehiclerecognition.domain.validation.LicensePlateValidator
import com.example.vehiclerecognition.domain.validation.CountryAwareLicensePlateValidator
import com.example.vehiclerecognition.model.VehicleColor
import com.example.vehiclerecognition.model.VehicleType
import com.example.vehiclerecognition.model.WatchlistEntry
import com.example.vehiclerecognition.data.models.Country
import com.example.vehiclerecognition.data.models.LicensePlateSettings
import com.example.vehiclerecognition.data.repositories.LicensePlateRepository
import com.example.vehiclerecognition.ml.processors.CountryAwarePlateValidator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
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
    private val licensePlateValidator: LicensePlateValidator,
    private val licensePlateRepository: LicensePlateRepository // Injected
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
    
    // Current country settings
    private val _licensePlateSettings = MutableStateFlow(LicensePlateSettings()) // To observe country

    init {
        loadWatchlistEntries()
        viewModelScope.launch {
            licensePlateRepository.settings.collect { settings ->
                _licensePlateSettings.value = settings
                // Reload watchlist entries when country changes
                loadWatchlistEntriesForCountry(settings.selectedCountry)
            }
        }
    }

    fun loadWatchlistEntries() {
        viewModelScope.launch {
            val currentSettings = licensePlateRepository.settings.first()
            loadWatchlistEntriesForCountry(currentSettings.selectedCountry)
        }
    }
    
    private fun loadWatchlistEntriesForCountry(country: Country) {
        viewModelScope.launch {
            _uiState.value = WatchlistUiState.Loading
            try {
                watchlistRepository.getEntriesByCountry(country).collect { entries ->
                    _uiState.value = WatchlistUiState.Success(entries)
                    println("WatchlistViewModel: Loaded ${entries.size} watchlist entries for ${country.displayName}")
                }
            } catch (e: Exception) {
                val errorMsg = "Error loading watchlist for ${country.displayName}: ${e.localizedMessage}"
                _uiState.value = WatchlistUiState.Error(errorMsg)
                _errorEvent.emit(errorMsg)
                println("WatchlistViewModel: $errorMsg")
            }
        }
    }

    fun addWatchlistEntry() {
        val lp = _newLicensePlate.value.trim()
        val currentCountry = _licensePlateSettings.value.selectedCountry // Get current country
        
        // For Color+Type and Color modes, license plate is optional
        val useLicensePlate = lp.isNotEmpty()
        
        // If license plate is provided, validate it with country-aware validator
        val countryAwareValidator = CountryAwareLicensePlateValidator(currentCountry)
        if (useLicensePlate && !countryAwareValidator.isValid(lp)) {
            _lpValidationError.value = "Invalid LP format for ${currentCountry.displayName}. Expected: ${CountryAwarePlateValidator.getFormatHint(currentCountry)}"
            println("WatchlistViewModel: Add failed - Invalid LP format: $lp")
            return
        }
        _lpValidationError.value = null

        val entry = WatchlistEntry(
            licensePlate = if (useLicensePlate) lp else null,
            vehicleType = _newVehicleType.value,
            vehicleColor = _newVehicleColor.value,
            country = currentCountry
        )
        viewModelScope.launch {
            val success = watchlistRepository.addEntry(entry)
            if (success) {
                loadWatchlistEntries() // Refresh list
                dismissAddDialog(true) // Reset fields and close
                println("WatchlistViewModel: Added entry for ${currentCountry.displayName} - ${entry.licensePlate ?: "no license plate (using color/type)"}")
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

    fun deleteEntryByColorAndType(color: VehicleColor, type: VehicleType) {
        viewModelScope.launch {
            watchlistRepository.getAllEntries().collect { entries ->
                // Find entries matching the color and type without a license plate
                val matchingEntries = entries.filter { 
                    it.vehicleColor == color && it.vehicleType == type && it.licensePlate == null
                }
                
                if (matchingEntries.isNotEmpty()) {
                    // For simplicity, we'll delete the first matching entry
                    // In a real app, you might need a more sophisticated approach
                    val entryToDelete = matchingEntries.first()
                    val success = (watchlistRepository as com.example.vehiclerecognition.data.repository.AndroidWatchlistRepository).deleteEntryByColorAndType(color, type)
                    
                    if (success) {
                        loadWatchlistEntries() // Refresh list
                        println("WatchlistViewModel: Deleted entry with Color: $color, Type: $type")
                    } else {
                        val errorMsg = "Failed to delete entry with Color: $color, Type: $type"
                        _errorEvent.emit(errorMsg) // Emit for Snackbar
                        println("WatchlistViewModel: $errorMsg")
                    }
                } else {
                    val errorMsg = "No matching entry found for Color: $color, Type: $type"
                    _errorEvent.emit(errorMsg) // Emit for Snackbar
                    println("WatchlistViewModel: $errorMsg")
                }
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