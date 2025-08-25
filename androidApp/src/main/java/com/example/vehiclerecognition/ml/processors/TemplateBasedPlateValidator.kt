package com.example.vehiclerecognition.ml.processors

import com.example.vehiclerecognition.data.models.Country
import com.example.vehiclerecognition.domain.service.LicensePlateTemplateService
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Template-based license plate validator that uses dynamic templates from the database
 */
@Singleton
class TemplateBasedPlateValidator @Inject constructor(
    private val templateService: LicensePlateTemplateService
) {
    
    /**
     * Validates and formats a license plate text according to country templates
     */
    fun validateAndFormatPlate(rawText: String, country: Country): String? {
        if (rawText.isEmpty()) return null
        
        return runBlocking {
            val validationResult = templateService.validateLicensePlate(rawText, country.name)
            validationResult.formattedPlate
        }
    }
    
    /**
     * Checks if a plate text is valid according to country templates
     */
    fun isValidFormat(plateText: String, country: Country): Boolean {
        return runBlocking {
            val validationResult = templateService.validateLicensePlate(plateText, country.name)
            validationResult.isValid
        }
    }
    
    /**
     * Extracts relevant characters and attempts to format according to country templates
     */
    fun extractRelevantCharacters(text: String, country: Country): String {
        // Clean the text to alphanumeric only
        val cleanText = text.replace(Regex("[^A-Z0-9]"), "").uppercase()
        
        // Try to validate and format using templates
        val formatted = validateAndFormatPlate(cleanText, country)
        return formatted ?: cleanText
    }
    
    /**
     * Gets format description for the specified country based on active templates
     */
    fun getFormatDescription(country: Country): String {
        return runBlocking {
            val templates = templateService.getTemplatesForCountry(country.name).first()
            
            if (templates.isEmpty()) {
                return@runBlocking "No templates configured for ${country.displayName}"
            }
            
            val descriptions = templates.sortedBy { it.priority }
                .map { "${it.displayName}: ${it.description}" }
                .joinToString(", ")
            
            "${country.displayName} formats: $descriptions"
        }
    }
    
    /**
     * Gets format hint for the specified country based on templates
     */
    fun getFormatHint(country: Country): String {
        return runBlocking {
            val templates = templateService.getTemplatesForCountry(country.name).first()
            
            if (templates.isEmpty()) {
                return@runBlocking "No templates configured"
            }
            
            templates.sortedBy { it.priority }
                .joinToString(", ") { it.templatePattern }
        }
    }
    
    /**
     * Checks if country has any configured templates
     */
    suspend fun hasConfiguredTemplates(country: Country): Boolean {
        return templateService.getTemplatesForCountry(country.name)
            .first().isNotEmpty()
    }
}