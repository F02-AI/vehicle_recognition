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
import com.example.vehiclerecognition.domain.service.LicensePlateTemplateService
import com.example.vehiclerecognition.ml.processors.TemplateAwareOcrEnhancer
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
    private val licensePlateRepository: LicensePlateRepository,
    private val templateService: LicensePlateTemplateService,
    private val templateAwareEnhancer: TemplateAwareOcrEnhancer
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
    
    private val _templateInfo = MutableStateFlow<List<String>>(emptyList())
    val templateInfo: StateFlow<List<String>> = _templateInfo.asStateFlow()
    
    private val _hasConfiguredTemplates = MutableStateFlow(false)
    val hasConfiguredTemplates: StateFlow<Boolean> = _hasConfiguredTemplates.asStateFlow()

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
                // Load template information for the selected country first
                loadTemplateInformation(settings.selectedCountry)
                // Then reload watchlist entries when country changes
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
    
    /**
     * Refreshes template information for the current country
     * Call this when returning from settings to check for new templates
     */
    fun refreshTemplateInformation() {
        val currentCountry = _licensePlateSettings.value.selectedCountry
        println("WatchlistViewModel: Manually refreshing template info for ${currentCountry.displayName}")
        loadTemplateInformation(currentCountry)
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
    
    private fun loadTemplateInformation(country: Country) {
        viewModelScope.launch {
            try {
                println("WatchlistViewModel: Loading template info for ${country.displayName} (${country.isoCode})")
                val hasTemplates = templateAwareEnhancer.hasConfiguredTemplates(country)
                println("WatchlistViewModel: Has templates for ${country.displayName}: $hasTemplates")
                _hasConfiguredTemplates.value = hasTemplates
                
                if (hasTemplates) {
                    val templateInfo = templateAwareEnhancer.getTemplateInfo(country)
                    _templateInfo.value = templateInfo
                    println("WatchlistViewModel: Loaded ${templateInfo.size} templates for ${country.displayName}")
                } else {
                    _templateInfo.value = listOf("No license plate templates configured for ${country.displayName}")
                    println("WatchlistViewModel: No templates found for ${country.displayName}")
                }
            } catch (e: Exception) {
                println("WatchlistViewModel: Error loading template info: ${e.message}")
                e.printStackTrace()
                _hasConfiguredTemplates.value = false
                _templateInfo.value = listOf("Error loading template information")
            }
        }
    }

    fun addWatchlistEntry() {
        val lp = _newLicensePlate.value.trim()
        val currentCountry = _licensePlateSettings.value.selectedCountry
        
        // For Color+Type and Color modes, license plate is optional
        val useLicensePlate = lp.isNotEmpty()
        
        // If license plate is provided, validate it using template-based validation
        if (useLicensePlate) {
            viewModelScope.launch {
                try {
                    val hasTemplates = templateAwareEnhancer.hasConfiguredTemplates(currentCountry)
                    
                    if (!hasTemplates) {
                        _lpValidationError.value = "No license plate templates configured for ${currentCountry.displayName}. Please configure templates in Settings."
                        return@launch
                    }
                    
                    // Convert to uppercase first, THEN remove non-alphanumeric chars
                    val formattedLP = lp.uppercase().replace(Regex("[^A-Z0-9]"), "")
                    
                    val validationResult = templateAwareEnhancer.validateUserInput(formattedLP, currentCountry)
                    
                    if (!validationResult.isValid) {
                        _lpValidationError.value = validationResult.message
                        return@launch
                    }
                    _lpValidationError.value = null
                    
                    // Proceed with adding the entry
                    addValidatedEntry(formattedLP, currentCountry)
                    
                } catch (e: Exception) {
                    _lpValidationError.value = "Error validating license plate: ${e.message}"
                    println("WatchlistViewModel: Error validating license plate: ${e.message}")
                }
            }
            return
        }
        
        _lpValidationError.value = null
        // Proceed with adding entry without license plate
        addValidatedEntry(null, currentCountry)
    }
    
    private fun addValidatedEntry(formattedLP: String?, currentCountry: Country) {
        viewModelScope.launch {
            // Check for duplicate license plate if one is provided
            if (formattedLP != null) {
                val existingEntries = watchlistRepository.getEntriesByCountry(currentCountry).first()
                val duplicateExists = existingEntries.any { 
                    it.licensePlate?.equals(formattedLP, ignoreCase = true) == true 
                }
                
                if (duplicateExists) {
                    _lpValidationError.value = "License plate '$formattedLP' already exists in the watchlist for ${currentCountry.displayName}. Please delete the existing entry first."
                    println("WatchlistViewModel: Duplicate license plate detected: $formattedLP")
                    return@launch
                }
            }
            
            val entry = WatchlistEntry(
                licensePlate = formattedLP,
                vehicleType = _newVehicleType.value,
                vehicleColor = _newVehicleColor.value,
                country = currentCountry
            )
            
            val success = watchlistRepository.addEntry(entry)
            if (success) {
                loadWatchlistEntries() // Refresh list
                dismissAddDialog(true) // Reset fields and close
                println("WatchlistViewModel: Added entry for ${currentCountry.displayName} - ${entry.licensePlate ?: "no license plate (using color/type)"}")
            } else {
                val errorMsg = "Failed to add entry due to database error."
                _errorEvent.emit(errorMsg) // Emit for Snackbar
                _lpValidationError.value = errorMsg
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