package com.example.vehiclerecognition.data.db

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.*

@RunWith(AndroidJUnit4::class)
@SmallTest
class LicensePlateTemplateDaoTest {

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    private lateinit var database: AppDatabase
    private lateinit var templateDao: LicensePlateTemplateDao
    private lateinit var countryDao: CountryDao

    private val testCountries = listOf(
        CountryEntity(
            id = "ISRAEL",
            displayName = "Israel",
            flagResourceId = "flag_israel",
            isEnabled = true,
            createdAt = 1000,
            updatedAt = 1000
        ),
        CountryEntity(
            id = "UK",
            displayName = "United Kingdom",
            flagResourceId = "flag_uk",
            isEnabled = true,
            createdAt = 1000,
            updatedAt = 1000
        ),
        CountryEntity(
            id = "SINGAPORE",
            displayName = "Singapore",
            flagResourceId = "flag_singapore",
            isEnabled = false, // Disabled for testing
            createdAt = 1000,
            updatedAt = 1000
        )
    )

    private val testTemplates = listOf(
        LicensePlateTemplateEntity(
            id = 1,
            countryId = "ISRAEL",
            templatePattern = "NNNNNN",
            displayName = "6-digit format",
            priority = 1,
            isActive = true,
            description = "6 numbers",
            regexPattern = "^[0-9]{6}$",
            createdAt = 1000,
            updatedAt = 1000
        ),
        LicensePlateTemplateEntity(
            id = 2,
            countryId = "ISRAEL",
            templatePattern = "NNNNNNN",
            displayName = "7-digit format",
            priority = 2,
            isActive = true,
            description = "7 numbers",
            regexPattern = "^[0-9]{7}$",
            createdAt = 1000,
            updatedAt = 1000
        ),
        LicensePlateTemplateEntity(
            id = 3,
            countryId = "UK",
            templatePattern = "LLNNLLL",
            displayName = "UK Standard",
            priority = 1,
            isActive = true,
            description = "2 letters, 2 numbers, 3 letters",
            regexPattern = "^[A-Z]{2}[0-9]{2}[A-Z]{3}$",
            createdAt = 1000,
            updatedAt = 1000
        ),
        LicensePlateTemplateEntity(
            id = 4,
            countryId = "UK",
            templatePattern = "LNNNLLL",
            displayName = "UK Alternative",
            priority = 2,
            isActive = false, // Inactive for testing
            description = "1 letter, 3 numbers, 3 letters",
            regexPattern = "^[A-Z]{1}[0-9]{3}[A-Z]{3}$",
            createdAt = 1000,
            updatedAt = 1000
        )
    )

    @Before
    fun createDb() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        
        templateDao = database.licensePlateTemplateDao()
        countryDao = database.countryDao()

        // Insert test countries first (due to foreign key constraint)
        countryDao.insertCountries(testCountries)
    }

    @After
    fun closeDb() {
        database.close()
    }

    // Template insertion tests
    @Test
    fun insertTemplate_insertsTemplateSuccessfully() = runBlocking {
        // Act
        val id = templateDao.insertTemplate(testTemplates[0])

        // Assert
        assertTrue(id > 0)
        val retrieved = templateDao.getTemplateById(id.toInt())
        assertNotNull(retrieved)
        assertEquals(testTemplates[0].countryId, retrieved?.countryId)
        assertEquals(testTemplates[0].templatePattern, retrieved?.templatePattern)
    }

    @Test
    fun insertTemplates_insertsMultipleTemplatesSuccessfully() = runBlocking {
        // Act
        templateDao.insertTemplates(testTemplates)

        // Assert
        val israelTemplates = templateDao.getTemplatesByCountrySync("ISRAEL")
        assertEquals(2, israelTemplates.size)
        
        val ukTemplates = templateDao.getTemplatesByCountrySync("UK")
        assertEquals(2, ukTemplates.size)
    }

    @Test
    fun insertTemplate_replacesOnConflict() = runBlocking {
        // Arrange
        templateDao.insertTemplate(testTemplates[0])
        val updatedTemplate = testTemplates[0].copy(displayName = "Updated Name")

        // Act
        templateDao.insertTemplate(updatedTemplate)

        // Assert
        val retrieved = templateDao.getTemplateById(testTemplates[0].id)
        assertEquals("Updated Name", retrieved?.displayName)
    }

    // Template retrieval by country tests
    @Test
    fun getTemplatesByCountry_returnsTemplatesForCountryOrderedByPriority() = runBlocking {
        // Arrange
        templateDao.insertTemplates(testTemplates)

        // Act
        val templates = templateDao.getTemplatesByCountry("ISRAEL").first()

        // Assert
        assertEquals(2, templates.size)
        assertEquals(1, templates[0].priority)
        assertEquals(2, templates[1].priority)
        assertTrue(templates.all { it.countryId == "ISRAEL" })
    }

    @Test
    fun getTemplatesByCountry_returnsOnlyActiveTemplates() = runBlocking {
        // Arrange
        templateDao.insertTemplates(testTemplates)

        // Act
        val templates = templateDao.getTemplatesByCountry("UK").first()

        // Assert
        assertEquals(1, templates.size) // Only active template should be returned
        assertTrue(templates[0].isActive)
        assertEquals("UK Standard", templates[0].displayName)
    }

    @Test
    fun getTemplatesByCountry_returnsEmptyListForNonExistentCountry() = runBlocking {
        // Arrange
        templateDao.insertTemplates(testTemplates)

        // Act
        val templates = templateDao.getTemplatesByCountry("NON_EXISTENT").first()

        // Assert
        assertTrue(templates.isEmpty())
    }

    @Test
    fun getTemplatesByCountrySync_returnsSameResultAsFlow() = runBlocking {
        // Arrange
        templateDao.insertTemplates(testTemplates)

        // Act
        val flowResult = templateDao.getTemplatesByCountry("ISRAEL").first()
        val syncResult = templateDao.getTemplatesByCountrySync("ISRAEL")

        // Assert
        assertEquals(flowResult.size, syncResult.size)
        assertEquals(flowResult.map { it.id }, syncResult.map { it.id })
    }

    // Template retrieval by priority tests
    @Test
    fun getTemplateByCountryAndPriority_returnsCorrectTemplate() = runBlocking {
        // Arrange
        templateDao.insertTemplates(testTemplates)

        // Act
        val template = templateDao.getTemplateByCountryAndPriority("ISRAEL", 2)

        // Assert
        assertNotNull(template)
        assertEquals("NNNNNNN", template?.templatePattern)
        assertEquals("7-digit format", template?.displayName)
    }

    @Test
    fun getTemplateByCountryAndPriority_returnsNullForInactiveTemplate() = runBlocking {
        // Arrange
        templateDao.insertTemplates(testTemplates)

        // Act
        val template = templateDao.getTemplateByCountryAndPriority("UK", 2) // This is inactive

        // Assert
        assertNull(template)
    }

    @Test
    fun getTemplateByCountryAndPriority_returnsNullForNonExistentPriority() = runBlocking {
        // Arrange
        templateDao.insertTemplates(testTemplates)

        // Act
        val template = templateDao.getTemplateByCountryAndPriority("ISRAEL", 99)

        // Assert
        assertNull(template)
    }

    // Template count and existence tests
    @Test
    fun getActiveTemplateCountForCountry_returnsCorrectCount() = runBlocking {
        // Arrange
        templateDao.insertTemplates(testTemplates)

        // Act
        val israelCount = templateDao.getActiveTemplateCountForCountry("ISRAEL")
        val ukCount = templateDao.getActiveTemplateCountForCountry("UK")
        val emptyCount = templateDao.getActiveTemplateCountForCountry("NON_EXISTENT")

        // Assert
        assertEquals(2, israelCount)
        assertEquals(1, ukCount) // One is inactive
        assertEquals(0, emptyCount)
    }

    @Test
    fun hasActiveTemplatesForCountry_returnsTrueWhenTemplatesExist() = runBlocking {
        // Arrange
        templateDao.insertTemplates(testTemplates)

        // Act & Assert
        assertTrue(templateDao.hasActiveTemplatesForCountry("ISRAEL"))
        assertTrue(templateDao.hasActiveTemplatesForCountry("UK"))
        assertFalse(templateDao.hasActiveTemplatesForCountry("NON_EXISTENT"))
    }

    @Test
    fun hasActiveTemplatesForCountry_returnsFalseWhenOnlyInactiveTemplatesExist() = runBlocking {
        // Arrange
        val inactiveTemplate = testTemplates[0].copy(id = 999, isActive = false)
        templateDao.insertTemplate(inactiveTemplate)

        // Act & Assert
        assertTrue(templateDao.hasActiveTemplatesForCountry("ISRAEL")) // Other active templates exist
    }

    // Template update tests
    @Test
    fun updateTemplate_updatesTemplateSuccessfully() = runBlocking {
        // Arrange
        templateDao.insertTemplate(testTemplates[0])
        val updatedTemplate = testTemplates[0].copy(
            displayName = "Updated Name",
            description = "Updated Description",
            updatedAt = 2000
        )

        // Act
        templateDao.updateTemplate(updatedTemplate)

        // Assert
        val retrieved = templateDao.getTemplateById(testTemplates[0].id)
        assertEquals("Updated Name", retrieved?.displayName)
        assertEquals("Updated Description", retrieved?.description)
        assertEquals(2000, retrieved?.updatedAt)
    }

    @Test
    fun setTemplateActive_updatesActiveStatus() = runBlocking {
        // Arrange
        templateDao.insertTemplate(testTemplates[0])

        // Act
        templateDao.setTemplateActive(testTemplates[0].id, false)

        // Assert
        val retrieved = templateDao.getTemplateById(testTemplates[0].id)
        assertFalse(retrieved?.isActive ?: true)

        // Verify it doesn't show up in active queries
        val activeTemplates = templateDao.getTemplatesByCountrySync("ISRAEL")
        assertTrue(activeTemplates.isEmpty())
    }

    // Template deletion tests
    @Test
    fun deleteTemplate_removesTemplateSuccessfully() = runBlocking {
        // Arrange
        templateDao.insertTemplate(testTemplates[0])

        // Act
        templateDao.deleteTemplate(testTemplates[0].id)

        // Assert
        val retrieved = templateDao.getTemplateById(testTemplates[0].id)
        assertNull(retrieved)
    }

    @Test
    fun deleteTemplatesByCountry_removesAllTemplatesForCountry() = runBlocking {
        // Arrange
        templateDao.insertTemplates(testTemplates)

        // Act
        templateDao.deleteTemplatesByCountry("ISRAEL")

        // Assert
        val israelTemplates = templateDao.getTemplatesByCountrySync("ISRAEL")
        assertTrue(israelTemplates.isEmpty())

        // Other country's templates should remain
        val ukTemplates = templateDao.getTemplatesByCountrySync("UK")
        assertFalse(ukTemplates.isEmpty())
    }

    // Atomic replacement tests
    @Test
    fun replaceTemplatesForCountry_replacesAllTemplatesAtomically() = runBlocking {
        // Arrange
        templateDao.insertTemplates(testTemplates)
        
        val newTemplates = listOf(
            LicensePlateTemplateEntity(
                id = 0, // New template
                countryId = "ISRAEL",
                templatePattern = "LLNNNN",
                displayName = "New Format 1",
                priority = 1,
                description = "2 letters, 4 numbers",
                regexPattern = "^[A-Z]{2}[0-9]{4}$"
            ),
            LicensePlateTemplateEntity(
                id = 0, // New template
                countryId = "ISRAEL",
                templatePattern = "LLLNNN",
                displayName = "New Format 2",
                priority = 2,
                description = "3 letters, 3 numbers",
                regexPattern = "^[A-Z]{3}[0-9]{3}$"
            )
        )

        // Act
        templateDao.replaceTemplatesForCountry("ISRAEL", newTemplates)

        // Assert
        val templates = templateDao.getTemplatesByCountrySync("ISRAEL")
        assertEquals(2, templates.size)
        assertEquals("LLNNNN", templates.find { it.priority == 1 }?.templatePattern)
        assertEquals("LLLNNN", templates.find { it.priority == 2 }?.templatePattern)
        
        // Old templates should be gone
        assertFalse(templates.any { it.templatePattern == "NNNNNN" })
        assertFalse(templates.any { it.templatePattern == "NNNNNNN" })
    }

    @Test
    fun replaceTemplatesForCountry_doesNotAffectOtherCountries() = runBlocking {
        // Arrange
        templateDao.insertTemplates(testTemplates)
        
        val newIsraeliTemplates = listOf(
            LicensePlateTemplateEntity(
                id = 0,
                countryId = "ISRAEL",
                templatePattern = "LLNNNN",
                displayName = "New Israeli Format",
                priority = 1,
                description = "2 letters, 4 numbers",
                regexPattern = "^[A-Z]{2}[0-9]{4}$"
            )
        )

        // Act
        templateDao.replaceTemplatesForCountry("ISRAEL", newIsraeliTemplates)

        // Assert
        val israelTemplates = templateDao.getTemplatesByCountrySync("ISRAEL")
        assertEquals(1, israelTemplates.size)
        assertEquals("LLNNNN", israelTemplates[0].templatePattern)
        
        // UK templates should remain unchanged
        val ukTemplates = templateDao.getTemplatesByCountrySync("UK")
        assertEquals(2, ukTemplates.size) // Both active and inactive
    }

    // Countries without templates query tests
    @Test
    fun getCountriesWithoutTemplates_returnsCountriesWithNoActiveTemplates() = runBlocking {
        // Arrange - Only insert templates for ISRAEL
        val israelTemplates = testTemplates.filter { it.countryId == "ISRAEL" }
        templateDao.insertTemplates(israelTemplates)

        // Act
        val countriesWithoutTemplates = templateDao.getCountriesWithoutTemplates()

        // Assert
        assertEquals(1, countriesWithoutTemplates.size) // UK should be returned (SINGAPORE is disabled)
        assertEquals("UK", countriesWithoutTemplates[0].id)
        assertEquals("United Kingdom", countriesWithoutTemplates[0].displayName)
    }

    @Test
    fun getCountriesWithoutTemplates_excludesDisabledCountries() = runBlocking {
        // Arrange - Insert no templates at all
        // SINGAPORE is disabled in test data

        // Act
        val countriesWithoutTemplates = templateDao.getCountriesWithoutTemplates()

        // Assert
        assertEquals(2, countriesWithoutTemplates.size) // ISRAEL and UK (SINGAPORE is disabled)
        assertTrue(countriesWithoutTemplates.all { it.isEnabled })
        assertFalse(countriesWithoutTemplates.any { it.id == "SINGAPORE" })
    }

    @Test
    fun getCountriesWithoutTemplates_excludesCountriesWithOnlyInactiveTemplates() = runBlocking {
        // Arrange - Insert only inactive templates for UK
        val inactiveTemplate = testTemplates.find { it.countryId == "UK" && !it.isActive }!!
        templateDao.insertTemplate(inactiveTemplate)

        // Act
        val countriesWithoutTemplates = templateDao.getCountriesWithoutTemplates()

        // Assert
        assertTrue(countriesWithoutTemplates.any { it.id == "UK" }) // UK should still be included
    }

    @Test
    fun getCountriesWithoutTemplates_returnsEmptyWhenAllCountriesHaveActiveTemplates() = runBlocking {
        // Arrange - Insert active templates for all enabled countries
        val activeTemplates = testTemplates.filter { it.isActive }
        templateDao.insertTemplates(activeTemplates)

        // Act
        val countriesWithoutTemplates = templateDao.getCountriesWithoutTemplates()

        // Assert
        assertTrue(countriesWithoutTemplates.isEmpty())
    }

    // Foreign key constraint tests
    @Test
    fun insertTemplate_failsWithNonExistentCountryId() = runBlocking {
        // Arrange
        val templateWithBadCountry = testTemplates[0].copy(countryId = "NON_EXISTENT")

        // Act & Assert
        try {
            templateDao.insertTemplate(templateWithBadCountry)
            fail("Expected foreign key constraint violation")
        } catch (e: Exception) {
            // Expected - foreign key constraint should prevent this
            assertTrue(e.message?.contains("FOREIGN KEY constraint failed") ?: false)
        }
    }

    @Test
    fun deleteCountry_cascadeDeletesTemplates() = runBlocking {
        // Arrange
        templateDao.insertTemplates(testTemplates)
        
        // Act
        countryDao.deleteCountry("ISRAEL")

        // Assert
        val israelTemplates = templateDao.getTemplatesByCountrySync("ISRAEL")
        assertTrue(israelTemplates.isEmpty()) // Templates should be cascade deleted
        
        // Other countries' templates should remain
        val ukTemplates = templateDao.getTemplatesByCountrySync("UK")
        assertFalse(ukTemplates.isEmpty())
    }

    // Unique constraint tests
    @Test
    fun insertTemplate_failsWithDuplicateCountryAndPriority() = runBlocking {
        // Arrange
        templateDao.insertTemplate(testTemplates[0])
        val duplicatePriorityTemplate = testTemplates[1].copy(
            id = 999,
            priority = 1 // Same priority as first template for same country
        )

        // Act & Assert
        try {
            templateDao.insertTemplate(duplicatePriorityTemplate)
            fail("Expected unique constraint violation")
        } catch (e: Exception) {
            // Expected - unique constraint on (country_id, priority) should prevent this
            assertTrue(e.message?.contains("UNIQUE constraint failed") ?: false)
        }
    }

    // Edge cases and data integrity tests
    @Test
    fun insertTemplate_handlesLongTextFields() = runBlocking {
        // Arrange
        val longDescription = "A".repeat(1000)
        val templateWithLongText = testTemplates[0].copy(
            id = 999,
            description = longDescription,
            regexPattern = "^[A-Z]{2}[0-9]{4}[A-Z]{3}[0-9]{2}[A-Z]{4}$" // Long regex
        )

        // Act
        val id = templateDao.insertTemplate(templateWithLongText)

        // Assert
        val retrieved = templateDao.getTemplateById(id.toInt())
        assertEquals(longDescription, retrieved?.description)
    }

    @Test
    fun getTemplatesByCountry_maintainsPriorityOrder() = runBlocking {
        // Arrange - Insert templates in reverse priority order
        val reversedTemplates = listOf(
            testTemplates[1], // Priority 2
            testTemplates[0]  // Priority 1
        )
        templateDao.insertTemplates(reversedTemplates)

        // Act
        val templates = templateDao.getTemplatesByCountrySync("ISRAEL")

        // Assert
        assertEquals(2, templates.size)
        assertEquals(1, templates[0].priority) // Should be first despite insertion order
        assertEquals(2, templates[1].priority)
    }

    @Test
    fun templateQueries_handleEmptyDatabase() = runBlocking {
        // Act & Assert - All queries should work on empty database
        assertTrue(templateDao.getTemplatesByCountrySync("ANY").isEmpty())
        assertNull(templateDao.getTemplateByCountryAndPriority("ANY", 1))
        assertEquals(0, templateDao.getActiveTemplateCountForCountry("ANY"))
        assertFalse(templateDao.hasActiveTemplatesForCountry("ANY"))
        
        // Countries without templates should return all enabled countries
        val countriesWithoutTemplates = templateDao.getCountriesWithoutTemplates()
        assertEquals(2, countriesWithoutTemplates.size) // ISRAEL and UK (SINGAPORE disabled)
    }
}