package com.example.vehiclerecognition.data.repositories

import com.example.vehiclerecognition.data.db.CountryDao
import com.example.vehiclerecognition.data.db.CountryEntity
import com.example.vehiclerecognition.data.db.LicensePlateTemplateDao
import com.example.vehiclerecognition.data.db.LicensePlateTemplateEntity
import com.example.vehiclerecognition.data.models.CountryModel
import com.example.vehiclerecognition.data.models.LicensePlateTemplate
import com.example.vehiclerecognition.data.models.WorldCountries
import com.example.vehiclerecognition.domain.repository.LicensePlateTemplateRepository
import com.example.vehiclerecognition.domain.repository.PlateValidationResult
import com.example.vehiclerecognition.domain.repository.TemplateValidationResult
import com.example.vehiclerecognition.domain.validation.TemplateValidationRules
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Android implementation of LicensePlateTemplateRepository using Room database
 */
@Singleton
class AndroidLicensePlateTemplateRepository @Inject constructor(
    private val countryDao: CountryDao,
    private val templateDao: LicensePlateTemplateDao
) : LicensePlateTemplateRepository {
    
    override fun getAllEnabledCountries(): Flow<List<CountryModel>> {
        return countryDao.getAllEnabledCountries().map { entities ->
            entities.map { it.toDomainModel() }
        }
    }
    
    override suspend fun getCountryById(countryId: String): CountryModel? {
        return countryDao.getCountryById(countryId)?.toDomainModel()
    }
    
    override suspend fun isCountryEnabled(countryId: String): Boolean {
        return countryDao.isCountryEnabled(countryId)
    }
    
    override suspend fun initializeDefaultCountries() {
        val existingCountries = countryDao.getAllCountriesSync()
        
        // Initialize all world countries if not already present
        val worldCountriesToInsert = WorldCountries.allCountries
            .filter { worldCountry -> 
                existingCountries.none { it.id == worldCountry.id } 
            }
            .map { worldCountry ->
                CountryEntity(
                    id = worldCountry.id,
                    displayName = worldCountry.displayName,
                    flagResourceId = worldCountry.flagResourceId,
                    isEnabled = worldCountry.isEnabled
                )
            }
        
        if (worldCountriesToInsert.isNotEmpty()) {
            countryDao.insertCountries(worldCountriesToInsert)
        }
        
        // Initialize default templates for key countries only
        initializeDefaultTemplatesForCountry("IL", listOf("NNNNNN", "NNNNNNN"))
        initializeDefaultTemplatesForCountry("GB", listOf("LLNNLLL"))
        initializeDefaultTemplatesForCountry("SG", listOf("LLLNNNNL", "LLLNNL"))
        initializeDefaultTemplatesForCountry("US", listOf("LLLNNNN"))
    }
    
    private suspend fun initializeDefaultTemplatesForCountry(countryId: String, patterns: List<String>) {
        val existingTemplates = templateDao.getTemplatesByCountrySync(countryId)
        if (existingTemplates.isEmpty()) {
            val templates = patterns.mapIndexed { index, pattern ->
                LicensePlateTemplateEntity(
                    countryId = countryId,
                    templatePattern = pattern,
                    displayName = if (index == 0) "Primary Format" else "Alternative Format",
                    priority = index + 1,
                    description = TemplateValidationRules.generateDescription(pattern),
                    regexPattern = TemplateValidationRules.templatePatternToRegex(pattern)
                )
            }
            templateDao.insertTemplates(templates)
        }
    }
    
    override fun getTemplatesByCountry(countryId: String): Flow<List<LicensePlateTemplate>> {
        return templateDao.getTemplatesByCountry(countryId).map { entities ->
            entities.map { it.toDomainModel() }
        }
    }
    
    override suspend fun getTemplatesByCountrySync(countryId: String): List<LicensePlateTemplate> {
        return templateDao.getTemplatesByCountrySync(countryId).map { it.toDomainModel() }
    }
    
    override suspend fun getTemplateByCountryAndPriority(countryId: String, priority: Int): LicensePlateTemplate? {
        return templateDao.getTemplateByCountryAndPriority(countryId, priority)?.toDomainModel()
    }
    
    override suspend fun hasActiveTemplatesForCountry(countryId: String): Boolean {
        return templateDao.hasActiveTemplatesForCountry(countryId)
    }
    
    override suspend fun saveTemplate(template: LicensePlateTemplate): Long {
        val entity = template.toEntity()
        return templateDao.insertTemplate(entity)
    }
    
    override suspend fun updateTemplate(template: LicensePlateTemplate) {
        val entity = template.toEntity()
        templateDao.updateTemplate(entity)
    }
    
    override suspend fun deleteTemplate(templateId: Int) {
        templateDao.deleteTemplate(templateId)
    }
    
    override suspend fun replaceTemplatesForCountry(countryId: String, templates: List<LicensePlateTemplate>) {
        val entities = templates.map { it.toEntity() }
        templateDao.replaceTemplatesForCountry(countryId, entities)
    }
    
    override suspend fun getCountriesWithoutTemplates(): List<CountryModel> {
        return templateDao.getCountriesWithoutTemplates().map { it.toDomainModel() }
    }
    
    override suspend fun validateTemplatePattern(pattern: String): TemplateValidationResult {
        val warnings = mutableListOf<String>()
        
        // Basic validation
        if (pattern.isEmpty()) {
            return TemplateValidationResult(false, "Template pattern cannot be empty")
        }
        
        if (pattern.length > TemplateValidationRules.MAX_TEMPLATE_LENGTH) {
            return TemplateValidationResult(false, "Template pattern cannot exceed ${TemplateValidationRules.MAX_TEMPLATE_LENGTH} characters")
        }
        
        // Check for invalid characters
        val invalidChars = pattern.filter { it != 'L' && it != 'N' }
        if (invalidChars.isNotEmpty()) {
            return TemplateValidationResult(false, "Template pattern contains invalid characters: ${invalidChars.toSet()}")
        }
        
        // Check for minimum requirements
        if (pattern.length < TemplateValidationRules.MIN_TEMPLATE_LENGTH) {
            return TemplateValidationResult(false, "Template pattern must contain at least ${TemplateValidationRules.MIN_TEMPLATE_LENGTH} elements")
        }
        
        // Add warnings for unusual patterns
        if (pattern.length < 5) {
            warnings.add("Template pattern is quite short (${pattern.length} characters)")
        }
        
        if (pattern.length > 10) {
            warnings.add("Template pattern is quite long (${pattern.length} characters)")
        }
        
        return TemplateValidationResult(true, warnings = warnings)
    }
    
    override suspend fun validatePlateAgainstTemplates(plateText: String, countryId: String): PlateValidationResult {
        val templates = getTemplatesByCountrySync(countryId)
        
        if (templates.isEmpty()) {
            return PlateValidationResult(false, errorMessage = "No templates configured for country: $countryId")
        }
        
        // Try to match against templates in priority order
        for (template in templates.sortedBy { it.priority }) {
            val formattedPlate = TemplateValidationRules.formatPlateText(plateText)
            val regex = Regex(template.regexPattern)
            if (regex.matches(formattedPlate)) {
                return PlateValidationResult(
                    isValid = true,
                    matchedTemplate = template,
                    formattedPlate = formattedPlate
                )
            }
        }
        
        return PlateValidationResult(false, errorMessage = "Plate text does not match any configured templates")
    }
}

// Extension functions for mapping between domain and entity models
private fun CountryEntity.toDomainModel(): CountryModel {
    return CountryModel(
        id = id,
        displayName = displayName,
        flagResourceId = flagResourceId,
        isEnabled = isEnabled,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}

private fun LicensePlateTemplateEntity.toDomainModel(): LicensePlateTemplate {
    return LicensePlateTemplate(
        id = id,
        countryId = countryId,
        templatePattern = templatePattern,
        displayName = displayName,
        priority = priority,
        isActive = isActive,
        description = description,
        regexPattern = regexPattern,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}

private fun LicensePlateTemplate.toEntity(): LicensePlateTemplateEntity {
    return LicensePlateTemplateEntity(
        id = id,
        countryId = countryId,
        templatePattern = templatePattern,
        displayName = displayName,
        priority = priority,
        isActive = isActive,
        description = description,
        regexPattern = regexPattern,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}