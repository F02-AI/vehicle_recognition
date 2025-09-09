package com.example.vehiclerecognition.domain.service

import com.example.vehiclerecognition.data.models.CountryModel
import com.example.vehiclerecognition.data.models.LicensePlateTemplate
import com.example.vehiclerecognition.domain.repository.LicensePlateTemplateRepository
import com.example.vehiclerecognition.domain.repository.PlateValidationResult
import com.example.vehiclerecognition.domain.repository.TemplateValidationResult
import com.example.vehiclerecognition.domain.validation.TemplateValidationRules
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service layer for managing license plate templates with business logic
 */
@Singleton
class LicensePlateTemplateService @Inject constructor(
    private val repository: LicensePlateTemplateRepository
) {
    
    /**
     * Initializes the template system with default data
     */
    suspend fun initializeSystem() {
        repository.initializeDefaultCountries()
    }
    
    /**
     * Gets all countries available for template configuration
     */
    fun getAvailableCountries(): Flow<List<CountryModel>> {
        return repository.getAllEnabledCountries()
    }
    
    /**
     * Gets all templates for a specific country
     */
    fun getTemplatesForCountry(countryId: String): Flow<List<LicensePlateTemplate>> {
        return repository.getTemplatesByCountry(countryId)
    }
    
    /**
     * Saves or updates templates for a country with validation
     */
    suspend fun saveTemplatesForCountry(
        countryId: String, 
        templates: List<LicensePlateTemplate>
    ): TemplateOperationResult {
        
        // Validate country exists and is enabled
        if (!repository.isCountryEnabled(countryId)) {
            return TemplateOperationResult(false, "Country not found or disabled: $countryId")
        }
        
        // Validate template count (max 2)
        if (templates.size > 2) {
            return TemplateOperationResult(false, "Maximum 2 templates allowed per country")
        }
        
        // Validate at least one template is provided
        if (templates.isEmpty()) {
            return TemplateOperationResult(false, "At least one template is required per country")
        }
        
        // Validate each template
        val validationErrors = mutableListOf<String>()
        templates.forEachIndexed { index, template ->
            val validation = repository.validateTemplatePattern(template.templatePattern)
            if (!validation.isValid) {
                validationErrors.add("Template ${index + 1}: ${validation.errorMessage}")
            }
        }
        
        if (validationErrors.isNotEmpty()) {
            return TemplateOperationResult(false, validationErrors.joinToString("; "))
        }
        
        // Validate priorities are correct
        val sortedTemplates = templates.sortedBy { it.priority }
        if (sortedTemplates.size == 1 && sortedTemplates[0].priority != 1) {
            return TemplateOperationResult(false, "Single template must have priority 1")
        }
        
        if (sortedTemplates.size == 2) {
            if (sortedTemplates[0].priority != 1 || sortedTemplates[1].priority != 2) {
                return TemplateOperationResult(false, "Two templates must have priorities 1 and 2")
            }
        }
        
        // Check for duplicate patterns
        val patterns = templates.map { it.templatePattern }
        if (patterns.size != patterns.toSet().size) {
            return TemplateOperationResult(false, "Duplicate template patterns are not allowed")
        }
        
        try {
            // Replace all templates for the country atomically
            repository.replaceTemplatesForCountry(countryId, templates)
            return TemplateOperationResult(true, "Templates saved successfully")
        } catch (e: Exception) {
            return TemplateOperationResult(false, "Failed to save templates: ${e.message}")
        }
    }
    
    /**
     * Validates a license plate text against country templates
     */
    suspend fun validateLicensePlate(plateText: String, countryId: String): PlateValidationResult {
        return repository.validatePlateAgainstTemplates(plateText, countryId)
    }
    
    /**
     * Gets countries that have been configured with templates
     */
    suspend fun getConfiguredCountries(): List<CountryModel> {
        val allCountries = repository.getAllEnabledCountries().first()
        return allCountries.filter { country ->
            repository.hasActiveTemplatesForCountry(country.id)
        }
    }
    
    /**
     * Gets configuration status for UI display
     * Only considers countries that user has actively configured
     */
    suspend fun getConfigurationStatus(): ConfigurationStatus {
        val configuredCountries = getConfiguredCountries()
        
        return ConfigurationStatus(
            totalCountries = configuredCountries.size,
            configuredCountries = configuredCountries.size,
            needsConfiguration = emptyList(), // No warnings for unconfigured countries
            isFullyConfigured = true // Always considered configured since only voluntary configuration
        )
    }
    
    /**
     * Creates default templates for a country based on common patterns
     */
    fun createDefaultTemplatesForCountry(countryId: String): List<LicensePlateTemplate> {
        return when (countryId) {
            "ISRAEL" -> listOf(
                LicensePlateTemplate(
                    countryId = countryId,
                    templatePattern = "NNNNNN",
                    displayName = "6-digit format",
                    priority = 1,
                    description = TemplateValidationRules.generateDescription("NNNNNN"),
                    regexPattern = TemplateValidationRules.templatePatternToRegex("NNNNNN")
                ),
                LicensePlateTemplate(
                    countryId = countryId,
                    templatePattern = "NNNNNNN",
                    displayName = "7-digit format",
                    priority = 2,
                    description = TemplateValidationRules.generateDescription("NNNNNNN"),
                    regexPattern = TemplateValidationRules.templatePatternToRegex("NNNNNNN")
                )
            )
            "UK" -> listOf(
                LicensePlateTemplate(
                    countryId = countryId,
                    templatePattern = "LLNNLLL",
                    displayName = "Standard UK format",
                    priority = 1,
                    description = TemplateValidationRules.generateDescription("LLNNLLL"),
                    regexPattern = TemplateValidationRules.templatePatternToRegex("LLNNLLL")
                )
            )
            "SINGAPORE" -> listOf(
                LicensePlateTemplate(
                    countryId = countryId,
                    templatePattern = "LLLNNNNL",
                    displayName = "Standard format",
                    priority = 1,
                    description = TemplateValidationRules.generateDescription("LLLNNNNL"),
                    regexPattern = TemplateValidationRules.templatePatternToRegex("LLLNNNNL")
                ),
                LicensePlateTemplate(
                    countryId = countryId,
                    templatePattern = "LLLNNL",
                    displayName = "Alternative format",
                    priority = 2,
                    description = TemplateValidationRules.generateDescription("LLLNNL"),
                    regexPattern = TemplateValidationRules.templatePatternToRegex("LLLNNL")
                )
            )
            else -> emptyList()
        }
    }
}

/**
 * Result of template operation
 */
data class TemplateOperationResult(
    val success: Boolean,
    val message: String,
    val warnings: List<String> = emptyList()
)

/**
 * Configuration status of the template system
 */
data class ConfigurationStatus(
    val totalCountries: Int,
    val configuredCountries: Int,
    val needsConfiguration: List<CountryModel>,
    val isFullyConfigured: Boolean
)