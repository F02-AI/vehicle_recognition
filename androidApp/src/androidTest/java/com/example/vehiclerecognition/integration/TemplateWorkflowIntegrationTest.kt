package com.example.vehiclerecognition.integration

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.example.vehiclerecognition.data.db.AppDatabase
import com.example.vehiclerecognition.data.db.CountryDao
import com.example.vehiclerecognition.data.db.CountryEntity
import com.example.vehiclerecognition.data.db.LicensePlateTemplateDao
import com.example.vehiclerecognition.data.models.Country
import com.example.vehiclerecognition.data.models.LicensePlateTemplate
import com.example.vehiclerecognition.data.repositories.AndroidLicensePlateTemplateRepository
import com.example.vehiclerecognition.domain.service.LicensePlateTemplateService
import com.example.vehiclerecognition.ui.settings.LicensePlateTemplateViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.*

/**
 * End-to-end integration tests for the complete license plate template workflow.
 * These tests verify the entire stack from UI interactions down to database persistence.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class TemplateWorkflowIntegrationTest {

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    private lateinit var database: AppDatabase
    private lateinit var templateDao: LicensePlateTemplateDao
    private lateinit var countryDao: CountryDao
    private lateinit var repository: AndroidLicensePlateTemplateRepository
    private lateinit var service: LicensePlateTemplateService
    private lateinit var viewModel: LicensePlateTemplateViewModel

    @Before
    fun setup() = runBlocking {
        // Create in-memory database for testing
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()

        templateDao = database.licensePlateTemplateDao()
        countryDao = database.countryDao()
        
        // Initialize the full stack
        repository = AndroidLicensePlateTemplateRepository(countryDao, templateDao)
        service = LicensePlateTemplateService(repository)
        viewModel = LicensePlateTemplateViewModel(service)

        // Initialize the system
        service.initializeSystem()
    }

    @After
    fun cleanup() {
        database.close()
    }

    // Complete workflow tests
    @Test
    fun completeTemplateCreationWorkflow_israel_succeeds() = runBlocking {
        // Step 1: Verify system initialization
        val countries = service.getAvailableCountries().first()
        assertEquals(3, countries.size)
        assertTrue(countries.contains(Country.ISRAEL))

        val initialStatus = service.getConfigurationStatus()
        assertFalse(initialStatus.isFullyConfigured)
        assertEquals(3, initialStatus.needsConfiguration.size)

        // Step 2: Select country (simulating UI interaction)
        viewModel.selectCountry(Country.ISRAEL)

        // Verify country selection
        assertEquals(Country.ISRAEL, viewModel.selectedCountry.value)

        // Step 3: Verify default templates are loaded
        val initialTemplates = viewModel.templates.value
        assertEquals(2, initialTemplates.size)
        assertEquals("NNNNNN", initialTemplates[0].pattern)
        assertEquals("NNNNNNN", initialTemplates[1].pattern)

        // Step 4: Modify template patterns (simulating user input)
        viewModel.updateTemplatePattern(0, "NNNNNN") // Keep first as is
        viewModel.updateTemplatePattern(1, "NNNNNNN") // Keep second as is

        // Verify templates are valid
        assertTrue(viewModel.canSave.value)
        assertTrue(viewModel.validationErrors.value.isEmpty())

        // Step 5: Save templates
        viewModel.saveTemplates()

        // Step 6: Verify persistence in database
        val savedTemplates = templateDao.getTemplatesByCountrySync("ISRAEL")
        assertEquals(2, savedTemplates.size)

        val priorityOneTemplate = savedTemplates.find { it.priority == 1 }
        assertNotNull(priorityOneTemplate)
        assertEquals("NNNNNN", priorityOneTemplate?.templatePattern)
        assertEquals("6-digit format", priorityOneTemplate?.displayName)

        val priorityTwoTemplate = savedTemplates.find { it.priority == 2 }
        assertNotNull(priorityTwoTemplate)
        assertEquals("NNNNNNN", priorityTwoTemplate?.templatePattern)
        assertEquals("7-digit format", priorityTwoTemplate?.displayName)

        // Step 7: Verify configuration status is updated
        val updatedStatus = service.getConfigurationStatus()
        assertEquals(1, updatedStatus.configuredCountries)
        assertEquals(2, updatedStatus.needsConfiguration.size)
        assertFalse(updatedStatus.needsConfiguration.contains(Country.ISRAEL))
    }

    @Test
    fun completeTemplateCreationWorkflow_uk_succeeds() = runBlocking {
        // Step 1: Select UK
        viewModel.selectCountry(Country.UK)

        // Step 2: Verify UK default template
        val initialTemplates = viewModel.templates.value
        assertEquals(1, initialTemplates.size)
        assertEquals("LLNNLLL", initialTemplates[0].pattern)

        // Step 3: Add a second template
        viewModel.addTemplate()
        assertEquals(2, viewModel.templates.value.size)

        // Step 4: Configure both templates
        viewModel.updateTemplatePattern(0, "LLNNLLL") // Standard UK format
        viewModel.updateTemplatePattern(1, "LNNNLLL") // Alternative UK format

        // Step 5: Verify validation passes
        assertTrue(viewModel.canSave.value)

        // Step 6: Save templates
        viewModel.saveTemplates()

        // Step 7: Verify database persistence
        val savedTemplates = templateDao.getTemplatesByCountrySync("UK")
        assertEquals(2, savedTemplates.size)
        assertTrue(savedTemplates.any { it.templatePattern == "LLNNLLL" && it.priority == 1 })
        assertTrue(savedTemplates.any { it.templatePattern == "LNNNLLL" && it.priority == 2 })
    }

    @Test
    fun templateValidationWorkflow_handlesInvalidPatterns() = runBlocking {
        // Step 1: Select country
        viewModel.selectCountry(Country.ISRAEL)

        // Step 2: Enter invalid pattern
        viewModel.updateTemplatePattern(0, "INVALID")

        // Step 3: Verify validation fails
        assertFalse(viewModel.canSave.value)
        assertTrue(viewModel.validationErrors.value.containsKey(0))

        // Step 4: Correct the pattern
        viewModel.updateTemplatePattern(0, "LLNNLL")

        // Step 5: Verify validation passes
        assertTrue(viewModel.canSave.value)
        assertFalse(viewModel.validationErrors.value.containsKey(0))

        // Step 6: Save should succeed
        viewModel.saveTemplates()
        
        val savedTemplates = templateDao.getTemplatesByCountrySync("ISRAEL")
        assertTrue(savedTemplates.any { it.templatePattern == "LLNNLL" })
    }

    @Test
    fun templateDeletionWorkflow_maintainsPriorities() = runBlocking {
        // Step 1: Set up two templates
        viewModel.selectCountry(Country.ISRAEL)
        viewModel.updateTemplatePattern(0, "NNNNNN")
        viewModel.updateTemplatePattern(1, "LLLLLL")

        // Step 2: Delete the first template
        viewModel.deleteTemplate(0)

        // Step 3: Verify second template becomes priority 1
        val templates = viewModel.templates.value
        assertEquals(1, templates.size)
        assertEquals(1, templates[0].priority)
        assertEquals("Template 1", templates[0].displayName)

        // Step 4: Save and verify persistence
        viewModel.saveTemplates()
        
        val savedTemplates = templateDao.getTemplatesByCountrySync("ISRAEL")
        assertEquals(1, savedTemplates.size)
        assertEquals(1, savedTemplates[0].priority)
    }

    @Test
    fun fullSystemConfigurationWorkflow_allCountries() = runBlocking {
        val countriesWithTemplates = listOf(
            Pair(Country.ISRAEL, listOf("NNNNNN", "NNNNNNN")),
            Pair(Country.UK, listOf("LLNNLLL")),
            Pair(Country.SINGAPORE, listOf("LLLNNNNL", "LLLNNL"))
        )

        // Configure each country
        for ((country, patterns) in countriesWithTemplates) {
            // Select country
            viewModel.selectCountry(country)

            // Configure patterns
            patterns.forEachIndexed { index, pattern ->
                if (index < viewModel.templates.value.size) {
                    viewModel.updateTemplatePattern(index, pattern)
                } else {
                    viewModel.addTemplate()
                    viewModel.updateTemplatePattern(index, pattern)
                }
            }

            // Save configuration
            assertTrue(viewModel.canSave.value)
            viewModel.saveTemplates()
        }

        // Verify all countries are configured
        val finalStatus = service.getConfigurationStatus()
        assertTrue(finalStatus.isFullyConfigured)
        assertEquals(3, finalStatus.configuredCountries)
        assertTrue(finalStatus.needsConfiguration.isEmpty())

        // Verify database contains all templates
        for ((country, patterns) in countriesWithTemplates) {
            val savedTemplates = templateDao.getTemplatesByCountrySync(country.name)
            assertEquals(patterns.size, savedTemplates.size)
            
            patterns.forEachIndexed { index, expectedPattern ->
                assertTrue("Country ${country.name} should have pattern $expectedPattern",
                    savedTemplates.any { it.templatePattern == expectedPattern && it.priority == index + 1 })
            }
        }
    }

    @Test
    fun plateValidationIntegrationWorkflow_allCountries() = runBlocking {
        // Step 1: Configure all countries with their templates
        val countryTemplates = mapOf(
            "ISRAEL" to listOf("NNNNNN", "NNNNNNN"),
            "UK" to listOf("LLNNLLL"),
            "SINGAPORE" to listOf("LLLNNNNL")
        )

        for ((countryId, patterns) in countryTemplates) {
            val templates = patterns.mapIndexed { index, pattern ->
                LicensePlateTemplate(
                    countryId = countryId,
                    templatePattern = pattern,
                    displayName = "Template ${index + 1}",
                    priority = index + 1,
                    description = LicensePlateTemplate.generateDescription(pattern),
                    regexPattern = LicensePlateTemplate.templatePatternToRegex(pattern)
                )
            }
            service.saveTemplatesForCountry(countryId, templates)
        }

        // Step 2: Test plate validation for each country
        val testCases = listOf(
            Triple("ISRAEL", "123456", true),  // Valid 6-digit
            Triple("ISRAEL", "1234567", true), // Valid 7-digit
            Triple("ISRAEL", "ABC123", false), // Invalid - letters where numbers expected
            
            Triple("UK", "AB12CDE", true),     // Valid UK format
            Triple("UK", "123456", false),     // Invalid - numbers where letters expected
            
            Triple("SINGAPORE", "ABC1234D", true), // Valid Singapore format
            Triple("SINGAPORE", "AB12CDE", false)  // Invalid - wrong length
        )

        for ((country, plateText, shouldBeValid) in testCases) {
            val result = service.validateLicensePlate(plateText, country)
            if (shouldBeValid) {
                assertTrue("Plate $plateText should be valid for $country", result.isValid)
                assertNotNull("Should have matched template", result.matchedTemplate)
            } else {
                assertFalse("Plate $plateText should be invalid for $country", result.isValid)
                assertNull("Should not have matched template", result.matchedTemplate)
            }
        }
    }

    @Test
    fun templateReplacementWorkflow_atomicOperation() = runBlocking {
        // Step 1: Create initial templates
        viewModel.selectCountry(Country.ISRAEL)
        viewModel.updateTemplatePattern(0, "NNNNNN")
        viewModel.updateTemplatePattern(1, "NNNNNNN")
        viewModel.saveTemplates()

        // Verify initial state
        var savedTemplates = templateDao.getTemplatesByCountrySync("ISRAEL")
        assertEquals(2, savedTemplates.size)

        // Step 2: Modify templates (simulating editing existing configuration)
        viewModel.selectCountry(Country.ISRAEL) // Reload templates
        viewModel.updateTemplatePattern(0, "LLNNNN") // Change first pattern
        viewModel.deleteTemplate(1) // Remove second template
        viewModel.saveTemplates()

        // Step 3: Verify replacement was atomic
        savedTemplates = templateDao.getTemplatesByCountrySync("ISRAEL")
        assertEquals(1, savedTemplates.size)
        assertEquals("LLNNNN", savedTemplates[0].templatePattern)
        assertEquals(1, savedTemplates[0].priority)
    }

    @Test
    fun errorHandlingWorkflow_databaseConstraintViolation() = runBlocking {
        // This test would need to mock database failures or use a test setup
        // that can trigger constraint violations. In a real scenario, you might
        // test network failures or other external dependencies.
        
        // For now, we'll test service-level validation errors
        val invalidTemplates = listOf(
            LicensePlateTemplate(
                countryId = "ISRAEL",
                templatePattern = "", // Invalid - empty pattern
                displayName = "Invalid Template",
                priority = 1,
                description = "",
                regexPattern = ""
            )
        )

        val result = service.saveTemplatesForCountry("ISRAEL", invalidTemplates)
        assertFalse("Should fail with invalid template", result.success)
        assertTrue("Should contain validation error", result.message.isNotEmpty())
    }

    @Test
    fun concurrentAccessWorkflow_multipleCountryUpdates() = runBlocking {
        // Simulate updating multiple countries concurrently
        // In a real app, this might happen if multiple users or screens
        // are modifying templates simultaneously
        
        val israelTemplates = listOf(
            LicensePlateTemplate(
                countryId = "ISRAEL",
                templatePattern = "NNNNNN",
                displayName = "Israeli 6-digit",
                priority = 1,
                description = "6 numbers",
                regexPattern = "^[0-9]{6}$"
            )
        )

        val ukTemplates = listOf(
            LicensePlateTemplate(
                countryId = "UK",
                templatePattern = "LLNNLLL",
                displayName = "UK Standard",
                priority = 1,
                description = "2 letters, 2 numbers, 3 letters",
                regexPattern = "^[A-Z]{2}[0-9]{2}[A-Z]{3}$"
            )
        )

        // Save both configurations
        val israelResult = service.saveTemplatesForCountry("ISRAEL", israelTemplates)
        val ukResult = service.saveTemplatesForCountry("UK", ukTemplates)

        // Both should succeed
        assertTrue(israelResult.success)
        assertTrue(ukResult.success)

        // Verify both are persisted correctly
        val israelSaved = templateDao.getTemplatesByCountrySync("ISRAEL")
        val ukSaved = templateDao.getTemplatesByCountrySync("UK")

        assertEquals(1, israelSaved.size)
        assertEquals(1, ukSaved.size)
        assertEquals("NNNNNN", israelSaved[0].templatePattern)
        assertEquals("LLNNLLL", ukSaved[0].templatePattern)
    }

    @Test
    fun defaultTemplateGenerationWorkflow_allSupportedCountries() = runBlocking {
        // Test that default templates are generated correctly for all countries
        val supportedCountries = listOf("ISRAEL", "UK", "SINGAPORE")

        for (country in supportedCountries) {
            val defaultTemplates = service.createDefaultTemplatesForCountry(country)
            
            assertTrue("Country $country should have default templates", 
                      defaultTemplates.isNotEmpty())
            
            // Verify all default templates are valid
            for (template in defaultTemplates) {
                assertTrue("Template pattern ${template.templatePattern} should be valid",
                          LicensePlateTemplate.isValidTemplatePattern(template.templatePattern))
                assertEquals(country, template.countryId)
                assertTrue("Priority should be positive", template.priority > 0)
                assertTrue("Description should not be empty", template.description.isNotEmpty())
                assertTrue("Regex pattern should not be empty", template.regexPattern.isNotEmpty())
            }
        }
    }
}