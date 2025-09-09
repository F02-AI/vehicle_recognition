package com.example.vehiclerecognition.domain.repository

import com.example.vehiclerecognition.data.db.CountryEntity
import com.example.vehiclerecognition.data.models.CountryModel
import com.example.vehiclerecognition.data.models.LicensePlateTemplate
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for managing license plate templates
 */
interface LicensePlateTemplateRepository {
    
    // Country operations
    fun getAllEnabledCountries(): Flow<List<CountryModel>>
    suspend fun getCountryById(countryId: String): CountryModel?
    suspend fun isCountryEnabled(countryId: String): Boolean
    suspend fun initializeDefaultCountries()
    
    // Template operations
    fun getTemplatesByCountry(countryId: String): Flow<List<LicensePlateTemplate>>
    suspend fun getTemplatesByCountrySync(countryId: String): List<LicensePlateTemplate>
    suspend fun getTemplateByCountryAndPriority(countryId: String, priority: Int): LicensePlateTemplate?
    suspend fun hasActiveTemplatesForCountry(countryId: String): Boolean
    
    // Template management
    suspend fun saveTemplate(template: LicensePlateTemplate): Long
    suspend fun updateTemplate(template: LicensePlateTemplate)
    suspend fun deleteTemplate(templateId: Int)
    suspend fun replaceTemplatesForCountry(countryId: String, templates: List<LicensePlateTemplate>)
    
    // Validation and warnings
    suspend fun getCountriesWithoutTemplates(): List<CountryModel>
    suspend fun validateTemplatePattern(pattern: String): TemplateValidationResult
    
    // Template usage
    suspend fun validatePlateAgainstTemplates(plateText: String, countryId: String): PlateValidationResult
}

/**
 * Result of template pattern validation
 */
data class TemplateValidationResult(
    val isValid: Boolean,
    val errorMessage: String? = null,
    val warnings: List<String> = emptyList()
)

/**
 * Result of plate validation against templates
 */
data class PlateValidationResult(
    val isValid: Boolean,
    val matchedTemplate: LicensePlateTemplate? = null,
    val formattedPlate: String? = null,
    val errorMessage: String? = null
)