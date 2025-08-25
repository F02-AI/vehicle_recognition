package com.example.vehiclerecognition.domain.service

import com.example.vehiclerecognition.data.models.Country
import com.example.vehiclerecognition.data.models.LicensePlateTemplate
import com.example.vehiclerecognition.domain.repository.LicensePlateTemplateRepository
import com.example.vehiclerecognition.domain.repository.PlateValidationResult
import com.example.vehiclerecognition.domain.repository.TemplateValidationResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertEquals
import kotlin.test.assertContains
import kotlin.test.assertNotNull
import kotlin.test.assertNull

// Mock implementation for testing
class MockLicensePlateTemplateRepository : LicensePlateTemplateRepository {
    private val countries = MutableStateFlow<List<Country>>(listOf(
        Country.ISRAEL,
        Country.UK,
        Country.SINGAPORE
    ))
    
    private val templates = mutableMapOf<String, MutableList<LicensePlateTemplate>>()
    private var isInitialized = false
    private var nextId = 1

    override fun getAllEnabledCountries(): Flow<List<Country>> = countries.asStateFlow()

    override suspend fun getCountryById(countryId: String): Country? {
        return countries.value.find { it.name == countryId }
    }

    override suspend fun isCountryEnabled(countryId: String): Boolean {
        return countries.value.any { it.name == countryId }
    }

    override suspend fun initializeDefaultCountries() {
        isInitialized = true
        // Initialize with empty templates for testing
        countries.value.forEach { country ->
            templates.putIfAbsent(country.name, mutableListOf())
        }
    }

    override fun getTemplatesByCountry(countryId: String): Flow<List<LicensePlateTemplate>> {
        return MutableStateFlow(templates[countryId] ?: emptyList()).asStateFlow()
    }

    override suspend fun getTemplatesByCountrySync(countryId: String): List<LicensePlateTemplate> {
        return templates[countryId] ?: emptyList()
    }

    override suspend fun getTemplateByCountryAndPriority(countryId: String, priority: Int): LicensePlateTemplate? {
        return templates[countryId]?.find { it.priority == priority }
    }

    override suspend fun hasActiveTemplatesForCountry(countryId: String): Boolean {
        return templates[countryId]?.any { it.isActive } ?: false
    }

    override suspend fun saveTemplate(template: LicensePlateTemplate): Long {
        val newId = nextId++
        val newTemplate = template.copy(id = newId)
        templates.getOrPut(template.countryId) { mutableListOf() }.add(newTemplate)
        return newId.toLong()
    }

    override suspend fun updateTemplate(template: LicensePlateTemplate) {
        val countryTemplates = templates[template.countryId] ?: return
        val index = countryTemplates.indexOfFirst { it.id == template.id }
        if (index >= 0) {
            countryTemplates[index] = template
        }
    }

    override suspend fun deleteTemplate(templateId: Int) {
        templates.values.forEach { templateList ->
            templateList.removeIf { it.id == templateId }
        }
    }

    override suspend fun replaceTemplatesForCountry(countryId: String, templates: List<LicensePlateTemplate>) {
        val countryTemplates = this.templates.getOrPut(countryId) { mutableListOf() }
        countryTemplates.clear()
        
        templates.forEach { template ->
            val newTemplate = if (template.id == 0) {
                template.copy(id = nextId++)
            } else template
            countryTemplates.add(newTemplate)
        }
    }

    override suspend fun getCountriesWithoutTemplates(): List<Country> {
        return countries.value.filter { country ->
            val countryTemplates = templates[country.name] ?: emptyList()
            countryTemplates.isEmpty() || countryTemplates.none { it.isActive }
        }
    }

    override suspend fun validateTemplatePattern(pattern: String): TemplateValidationResult {
        if (pattern.isEmpty()) {
            return TemplateValidationResult(false, "Template pattern cannot be empty")
        }
        
        if (pattern.length > 12) {
            return TemplateValidationResult(false, "Template pattern cannot exceed 12 characters")
        }
        
        if (!pattern.all { it == 'L' || it == 'N' }) {
            return TemplateValidationResult(false, "Template pattern contains invalid characters")
        }
        
        if (!pattern.contains('L') || !pattern.contains('N')) {
            return TemplateValidationResult(false, "Template pattern must contain both letters and numbers")
        }
        
        return TemplateValidationResult(true)
    }

    override suspend fun validatePlateAgainstTemplates(plateText: String, countryId: String): PlateValidationResult {
        val countryTemplates = templates[countryId] ?: emptyList()
        
        if (countryTemplates.isEmpty()) {
            return PlateValidationResult(false, errorMessage = "No templates configured for country: $countryId")
        }
        
        for (template in countryTemplates.sortedBy { it.priority }) {
            if (template.matches(plateText)) {
                return PlateValidationResult(
                    isValid = true,
                    matchedTemplate = template,
                    formattedPlate = template.formatPlateText(plateText)
                )
            }
        }
        
        return PlateValidationResult(false, errorMessage = "Plate text does not match any configured templates")
    }

    // Helper methods for testing
    fun addTemplate(template: LicensePlateTemplate) {
        templates.getOrPut(template.countryId) { mutableListOf() }.add(template)
    }

    fun getIsInitialized() = isInitialized
    
    fun clearTemplates() {
        templates.clear()
    }
}

class LicensePlateTemplateServiceTest {

    private fun createService(): Pair<LicensePlateTemplateService, MockLicensePlateTemplateRepository> {
        val repository = MockLicensePlateTemplateRepository()
        val service = LicensePlateTemplateService(repository)
        return Pair(service, repository)
    }

    // Initialization tests
    @Test
    fun `initializeSystem initializes repository`() = runBlocking {
        val (service, repository) = createService()
        
        service.initializeSystem()
        
        assertTrue(repository.getIsInitialized())
    }

    // Country retrieval tests
    @Test
    fun `getAvailableCountries returns enabled countries`() = runBlocking {
        val (service, _) = createService()
        
        val countries = service.getAvailableCountries().first()
        
        assertEquals(3, countries.size)
        assertTrue(countries.contains(Country.ISRAEL))
        assertTrue(countries.contains(Country.UK))
        assertTrue(countries.contains(Country.SINGAPORE))
    }

    // Template retrieval tests
    @Test
    fun `getTemplatesForCountry returns templates for specific country`() = runBlocking {
        val (service, repository) = createService()
        
        val template = createTestTemplate("ISRAEL", "NNNNNN", "6-digit", 1)
        repository.addTemplate(template)
        
        val templates = service.getTemplatesForCountry("ISRAEL").first()
        
        assertEquals(1, templates.size)
        assertEquals("NNNNNN", templates[0].templatePattern)
    }

    @Test
    fun `getTemplatesForCountry returns empty list for country with no templates`() = runBlocking {
        val (service, _) = createService()
        
        val templates = service.getTemplatesForCountry("ISRAEL").first()
        
        assertTrue(templates.isEmpty())
    }

    // Template saving tests - Happy paths
    @Test
    fun `saveTemplatesForCountry succeeds with valid single template`() = runBlocking {
        val (service, _) = createService()
        service.initializeSystem()
        
        val templates = listOf(createTestTemplate("ISRAEL", "NNNNNN", "6-digit", 1))
        
        val result = service.saveTemplatesForCountry("ISRAEL", templates)
        
        assertTrue(result.success)
        assertEquals("Templates saved successfully", result.message)
    }

    @Test
    fun `saveTemplatesForCountry succeeds with valid two templates`() = runBlocking {
        val (service, _) = createService()
        service.initializeSystem()
        
        val templates = listOf(
            createTestTemplate("ISRAEL", "NNNNNN", "6-digit", 1),
            createTestTemplate("ISRAEL", "NNNNNNN", "7-digit", 2)
        )
        
        val result = service.saveTemplatesForCountry("ISRAEL", templates)
        
        assertTrue(result.success)
        assertEquals("Templates saved successfully", result.message)
    }

    // Template saving tests - Error cases
    @Test
    fun `saveTemplatesForCountry fails for non-existent country`() = runBlocking {
        val (service, _) = createService()
        service.initializeSystem()
        
        val templates = listOf(createTestTemplate("INVALID", "NNNNNN", "Test", 1))
        
        val result = service.saveTemplatesForCountry("INVALID", templates)
        
        assertFalse(result.success)
        assertContains(result.message, "Country not found or disabled")
    }

    @Test
    fun `saveTemplatesForCountry fails with more than 2 templates`() = runBlocking {
        val (service, _) = createService()
        service.initializeSystem()
        
        val templates = listOf(
            createTestTemplate("ISRAEL", "NNNNNN", "Format 1", 1),
            createTestTemplate("ISRAEL", "NNNNNNN", "Format 2", 2),
            createTestTemplate("ISRAEL", "LLNNLL", "Format 3", 3)
        )
        
        val result = service.saveTemplatesForCountry("ISRAEL", templates)
        
        assertFalse(result.success)
        assertEquals("Maximum 2 templates allowed per country", result.message)
    }

    @Test
    fun `saveTemplatesForCountry fails with empty template list`() = runBlocking {
        val (service, _) = createService()
        service.initializeSystem()
        
        val result = service.saveTemplatesForCountry("ISRAEL", emptyList())
        
        assertFalse(result.success)
        assertEquals("At least one template is required per country", result.message)
    }

    @Test
    fun `saveTemplatesForCountry fails with invalid template pattern`() = runBlocking {
        val (service, _) = createService()
        service.initializeSystem()
        
        val templates = listOf(createTestTemplate("ISRAEL", "INVALID", "Invalid", 1))
        
        val result = service.saveTemplatesForCountry("ISRAEL", templates)
        
        assertFalse(result.success)
        assertContains(result.message, "Template 1:")
    }

    @Test
    fun `saveTemplatesForCountry fails with incorrect single template priority`() = runBlocking {
        val (service, _) = createService()
        service.initializeSystem()
        
        val templates = listOf(createTestTemplate("ISRAEL", "NNNNNN", "Wrong Priority", 2))
        
        val result = service.saveTemplatesForCountry("ISRAEL", templates)
        
        assertFalse(result.success)
        assertEquals("Single template must have priority 1", result.message)
    }

    @Test
    fun `saveTemplatesForCountry fails with incorrect two template priorities`() = runBlocking {
        val (service, _) = createService()
        service.initializeSystem()
        
        val templates = listOf(
            createTestTemplate("ISRAEL", "NNNNNN", "Wrong Priority 1", 1),
            createTestTemplate("ISRAEL", "NNNNNNN", "Wrong Priority 2", 3)
        )
        
        val result = service.saveTemplatesForCountry("ISRAEL", templates)
        
        assertFalse(result.success)
        assertEquals("Two templates must have priorities 1 and 2", result.message)
    }

    @Test
    fun `saveTemplatesForCountry fails with duplicate patterns`() = runBlocking {
        val (service, _) = createService()
        service.initializeSystem()
        
        val templates = listOf(
            createTestTemplate("ISRAEL", "NNNNNN", "Format 1", 1),
            createTestTemplate("ISRAEL", "NNNNNN", "Format 2", 2)
        )
        
        val result = service.saveTemplatesForCountry("ISRAEL", templates)
        
        assertFalse(result.success)
        assertEquals("Duplicate template patterns are not allowed", result.message)
    }

    // Plate validation tests
    @Test
    fun `validateLicensePlate succeeds with matching template`() = runBlocking {
        val (service, repository) = createService()
        
        val template = createTestTemplate("ISRAEL", "NNNNNN", "6-digit", 1)
        repository.addTemplate(template)
        
        val result = service.validateLicensePlate("123456", "ISRAEL")
        
        assertTrue(result.isValid)
        assertNotNull(result.matchedTemplate)
        assertEquals("123456", result.formattedPlate)
    }

    @Test
    fun `validateLicensePlate fails with no templates`() = runBlocking {
        val (service, _) = createService()
        
        val result = service.validateLicensePlate("123456", "ISRAEL")
        
        assertFalse(result.isValid)
        assertContains(result.errorMessage ?: "", "No templates configured")
    }

    @Test
    fun `validateLicensePlate fails with non-matching plate`() = runBlocking {
        val (service, repository) = createService()
        
        val template = createTestTemplate("ISRAEL", "NNNNNN", "6-digit", 1)
        repository.addTemplate(template)
        
        val result = service.validateLicensePlate("ABC123", "ISRAEL")
        
        assertFalse(result.isValid)
        assertContains(result.errorMessage ?: "", "does not match any configured templates")
    }

    // Configuration status tests
    @Test
    fun `getCountriesNeedingConfiguration returns countries without templates`() = runBlocking {
        val (service, _) = createService()
        service.initializeSystem()
        
        val countries = service.getCountriesNeedingConfiguration()
        
        assertEquals(3, countries.size) // All countries initially have no templates
        assertTrue(countries.contains(Country.ISRAEL))
        assertTrue(countries.contains(Country.UK))
        assertTrue(countries.contains(Country.SINGAPORE))
    }

    @Test
    fun `getCountriesNeedingConfiguration excludes configured countries`() = runBlocking {
        val (service, repository) = createService()
        service.initializeSystem()
        
        // Add template for ISRAEL
        val template = createTestTemplate("ISRAEL", "NNNNNN", "6-digit", 1)
        repository.addTemplate(template)
        
        val countries = service.getCountriesNeedingConfiguration()
        
        assertEquals(2, countries.size)
        assertFalse(countries.contains(Country.ISRAEL))
        assertTrue(countries.contains(Country.UK))
        assertTrue(countries.contains(Country.SINGAPORE))
    }

    @Test
    fun `isSystemConfigured returns false when countries need configuration`() = runBlocking {
        val (service, _) = createService()
        service.initializeSystem()
        
        val isConfigured = service.isSystemConfigured()
        
        assertFalse(isConfigured)
    }

    @Test
    fun `isSystemConfigured returns true when all countries configured`() = runBlocking {
        val (service, repository) = createService()
        service.initializeSystem()
        
        // Add templates for all countries
        repository.addTemplate(createTestTemplate("ISRAEL", "NNNNNN", "Israeli", 1))
        repository.addTemplate(createTestTemplate("UK", "LLNNLLL", "UK", 1))
        repository.addTemplate(createTestTemplate("SINGAPORE", "LLLNNNNL", "Singapore", 1))
        
        val isConfigured = service.isSystemConfigured()
        
        assertTrue(isConfigured)
    }

    @Test
    fun `getConfigurationStatus returns correct status`() = runBlocking {
        val (service, repository) = createService()
        service.initializeSystem()
        
        // Configure only ISRAEL
        repository.addTemplate(createTestTemplate("ISRAEL", "NNNNNN", "Israeli", 1))
        
        val status = service.getConfigurationStatus()
        
        assertEquals(3, status.totalCountries)
        assertEquals(1, status.configuredCountries)
        assertEquals(2, status.needsConfiguration.size)
        assertFalse(status.isFullyConfigured)
    }

    // Default template creation tests
    @Test
    fun `createDefaultTemplatesForCountry returns correct templates for ISRAEL`() {
        val (service, _) = createService()
        
        val templates = service.createDefaultTemplatesForCountry("ISRAEL")
        
        assertEquals(2, templates.size)
        assertEquals("NNNNNN", templates[0].templatePattern)
        assertEquals("NNNNNNN", templates[1].templatePattern)
        assertEquals(1, templates[0].priority)
        assertEquals(2, templates[1].priority)
    }

    @Test
    fun `createDefaultTemplatesForCountry returns correct templates for UK`() {
        val (service, _) = createService()
        
        val templates = service.createDefaultTemplatesForCountry("UK")
        
        assertEquals(1, templates.size)
        assertEquals("LLNNLLL", templates[0].templatePattern)
        assertEquals(1, templates[0].priority)
    }

    @Test
    fun `createDefaultTemplatesForCountry returns correct templates for SINGAPORE`() {
        val (service, _) = createService()
        
        val templates = service.createDefaultTemplatesForCountry("SINGAPORE")
        
        assertEquals(2, templates.size)
        assertEquals("LLLNNNNL", templates[0].templatePattern)
        assertEquals("LLLNNL", templates[1].templatePattern)
        assertEquals(1, templates[0].priority)
        assertEquals(2, templates[1].priority)
    }

    @Test
    fun `createDefaultTemplatesForCountry returns empty list for unknown country`() {
        val (service, _) = createService()
        
        val templates = service.createDefaultTemplatesForCountry("UNKNOWN")
        
        assertTrue(templates.isEmpty())
    }

    // Error handling tests
    @Test
    fun `saveTemplatesForCountry handles repository exceptions gracefully`() = runBlocking {
        val errorRepository = object : MockLicensePlateTemplateRepository() {
            override suspend fun replaceTemplatesForCountry(countryId: String, templates: List<LicensePlateTemplate>) {
                throw RuntimeException("Database error")
            }
        }
        errorRepository.initializeDefaultCountries()
        
        val service = LicensePlateTemplateService(errorRepository)
        val templates = listOf(createTestTemplate("ISRAEL", "NNNNNN", "Test", 1))
        
        val result = service.saveTemplatesForCountry("ISRAEL", templates)
        
        assertFalse(result.success)
        assertContains(result.message, "Failed to save templates")
        assertContains(result.message, "Database error")
    }

    // Integration tests
    @Test
    fun `complete workflow - initialize, save, validate`() = runBlocking {
        val (service, _) = createService()
        
        // Initialize system
        service.initializeSystem()
        
        // Save templates
        val templates = listOf(
            createTestTemplate("ISRAEL", "NNNNNN", "6-digit", 1),
            createTestTemplate("ISRAEL", "NNNNNNN", "7-digit", 2)
        )
        val saveResult = service.saveTemplatesForCountry("ISRAEL", templates)
        assertTrue(saveResult.success)
        
        // Validate plates
        val validResult1 = service.validateLicensePlate("123456", "ISRAEL")
        assertTrue(validResult1.isValid)
        
        val validResult2 = service.validateLicensePlate("1234567", "ISRAEL")
        assertTrue(validResult2.isValid)
        
        val invalidResult = service.validateLicensePlate("AB1234", "ISRAEL")
        assertFalse(invalidResult.isValid)
        
        // Check configuration status
        val status = service.getConfigurationStatus()
        assertEquals(1, status.configuredCountries)
        assertEquals(2, status.needsConfiguration.size)
    }

    // Helper function to create test templates
    private fun createTestTemplate(
        countryId: String, 
        pattern: String, 
        displayName: String, 
        priority: Int
    ): LicensePlateTemplate {
        return LicensePlateTemplate(
            countryId = countryId,
            templatePattern = pattern,
            displayName = displayName,
            priority = priority,
            description = LicensePlateTemplate.generateDescription(pattern),
            regexPattern = try {
                LicensePlateTemplate.templatePatternToRegex(pattern)
            } catch (e: Exception) {
                "invalid"
            }
        )
    }
}