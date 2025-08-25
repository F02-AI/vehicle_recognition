package com.example.vehiclerecognition.data.repositories

import com.example.vehiclerecognition.data.db.CountryDao
import com.example.vehiclerecognition.data.db.CountryEntity
import com.example.vehiclerecognition.data.db.LicensePlateTemplateDao
import com.example.vehiclerecognition.data.db.LicensePlateTemplateEntity
import com.example.vehiclerecognition.data.models.Country
import com.example.vehiclerecognition.data.models.LicensePlateTemplate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.*

class AndroidLicensePlateTemplateRepositoryTest {

    @Mock
    private lateinit var countryDao: CountryDao
    
    @Mock
    private lateinit var templateDao: LicensePlateTemplateDao
    
    private lateinit var repository: AndroidLicensePlateTemplateRepository

    private val testCountries = listOf(
        CountryEntity(id = "ISRAEL", displayName = "Israel", flagResourceId = "flag_israel", isEnabled = true),
        CountryEntity(id = "UK", displayName = "United Kingdom", flagResourceId = "flag_uk", isEnabled = true),
        CountryEntity(id = "SINGAPORE", displayName = "Singapore", flagResourceId = "flag_singapore", isEnabled = true)
    )

    private val testTemplates = listOf(
        LicensePlateTemplateEntity(
            id = 1,
            countryId = "ISRAEL",
            templatePattern = "NNNNNN",
            displayName = "6-digit format",
            priority = 1,
            description = "6 numbers",
            regexPattern = "^[0-9][0-9][0-9][0-9][0-9][0-9]$"
        ),
        LicensePlateTemplateEntity(
            id = 2,
            countryId = "ISRAEL",
            templatePattern = "NNNNNNN",
            displayName = "7-digit format",
            priority = 2,
            description = "7 numbers",
            regexPattern = "^[0-9][0-9][0-9][0-9][0-9][0-9][0-9]$"
        ),
        LicensePlateTemplateEntity(
            id = 3,
            countryId = "UK",
            templatePattern = "LLNNLLL",
            displayName = "UK Standard",
            priority = 1,
            description = "2 letters, 2 numbers, 3 letters",
            regexPattern = "^[A-Z][A-Z][0-9][0-9][A-Z][A-Z][A-Z]$"
        )
    )

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        repository = AndroidLicensePlateTemplateRepository(countryDao, templateDao)
    }

    // Country retrieval tests
    @Test
    fun `getAllEnabledCountries returns flow of enabled countries`() = runBlocking {
        // Arrange
        val countryEntities = testCountries.filter { it.isEnabled }
        whenever(countryDao.getAllEnabledCountries()).thenReturn(MutableStateFlow(countryEntities))

        // Act
        val countries = repository.getAllEnabledCountries().first()

        // Assert
        assertEquals(3, countries.size)
        assertTrue(countries.any { it.name == "ISRAEL" })
        assertTrue(countries.any { it.name == "UK" })
        assertTrue(countries.any { it.name == "SINGAPORE" })
        verify(countryDao).getAllEnabledCountries()
    }

    @Test
    fun `getCountryById returns correct country`() = runBlocking {
        // Arrange
        val israelEntity = testCountries.find { it.id == "ISRAEL" }
        whenever(countryDao.getCountryById("ISRAEL")).thenReturn(israelEntity)

        // Act
        val country = repository.getCountryById("ISRAEL")

        // Assert
        assertNotNull(country)
        assertEquals(Country.ISRAEL, country)
        verify(countryDao).getCountryById("ISRAEL")
    }

    @Test
    fun `getCountryById returns null for non-existent country`() = runBlocking {
        // Arrange
        whenever(countryDao.getCountryById("INVALID")).thenReturn(null)

        // Act
        val country = repository.getCountryById("INVALID")

        // Assert
        assertNull(country)
        verify(countryDao).getCountryById("INVALID")
    }

    @Test
    fun `isCountryEnabled returns true for enabled country`() = runBlocking {
        // Arrange
        whenever(countryDao.isCountryEnabled("ISRAEL")).thenReturn(true)

        // Act
        val isEnabled = repository.isCountryEnabled("ISRAEL")

        // Assert
        assertTrue(isEnabled)
        verify(countryDao).isCountryEnabled("ISRAEL")
    }

    @Test
    fun `isCountryEnabled returns false for disabled country`() = runBlocking {
        // Arrange
        whenever(countryDao.isCountryEnabled("DISABLED")).thenReturn(false)

        // Act
        val isEnabled = repository.isCountryEnabled("DISABLED")

        // Assert
        assertFalse(isEnabled)
        verify(countryDao).isCountryEnabled("DISABLED")
    }

    // Initialization tests
    @Test
    fun `initializeDefaultCountries inserts countries and templates`() = runBlocking {
        // Arrange
        whenever(countryDao.getAllCountries()).thenReturn(MutableStateFlow(emptyList()))
        whenever(templateDao.getTemplatesByCountrySync(any())).thenReturn(emptyList())

        // Act
        repository.initializeDefaultCountries()

        // Assert
        verify(countryDao).insertCountries(any())
        verify(templateDao, atLeast(3)).getTemplatesByCountrySync(any())
        verify(templateDao, atLeast(3)).insertTemplates(any())
    }

    @Test
    fun `initializeDefaultCountries skips template creation when templates exist`() = runBlocking {
        // Arrange
        whenever(countryDao.getAllCountries()).thenReturn(MutableStateFlow(testCountries))
        whenever(templateDao.getTemplatesByCountrySync("ISRAEL")).thenReturn(testTemplates.filter { it.countryId == "ISRAEL" })
        whenever(templateDao.getTemplatesByCountrySync("UK")).thenReturn(testTemplates.filter { it.countryId == "UK" })
        whenever(templateDao.getTemplatesByCountrySync("SINGAPORE")).thenReturn(emptyList())

        // Act
        repository.initializeDefaultCountries()

        // Assert
        verify(countryDao).insertCountries(any())
        // Should create templates only for Singapore (which has no existing templates)
        verify(templateDao).insertTemplates(argThat { list ->
            list.any { it.countryId == "SINGAPORE" }
        })
    }

    // Template retrieval tests
    @Test
    fun `getTemplatesByCountry returns flow of templates for country`() = runBlocking {
        // Arrange
        val israelTemplates = testTemplates.filter { it.countryId == "ISRAEL" }
        whenever(templateDao.getTemplatesByCountry("ISRAEL")).thenReturn(MutableStateFlow(israelTemplates))

        // Act
        val templates = repository.getTemplatesByCountry("ISRAEL").first()

        // Assert
        assertEquals(2, templates.size)
        assertTrue(templates.all { it.countryId == "ISRAEL" })
        assertEquals("NNNNNN", templates[0].templatePattern)
        assertEquals("NNNNNNN", templates[1].templatePattern)
        verify(templateDao).getTemplatesByCountry("ISRAEL")
    }

    @Test
    fun `getTemplatesByCountrySync returns list of templates for country`() = runBlocking {
        // Arrange
        val israelTemplates = testTemplates.filter { it.countryId == "ISRAEL" }
        whenever(templateDao.getTemplatesByCountrySync("ISRAEL")).thenReturn(israelTemplates)

        // Act
        val templates = repository.getTemplatesByCountrySync("ISRAEL")

        // Assert
        assertEquals(2, templates.size)
        assertTrue(templates.all { it.countryId == "ISRAEL" })
        verify(templateDao).getTemplatesByCountrySync("ISRAEL")
    }

    @Test
    fun `getTemplateByCountryAndPriority returns correct template`() = runBlocking {
        // Arrange
        val expectedTemplate = testTemplates.find { it.countryId == "ISRAEL" && it.priority == 1 }
        whenever(templateDao.getTemplateByCountryAndPriority("ISRAEL", 1)).thenReturn(expectedTemplate)

        // Act
        val template = repository.getTemplateByCountryAndPriority("ISRAEL", 1)

        // Assert
        assertNotNull(template)
        assertEquals("NNNNNN", template?.templatePattern)
        assertEquals(1, template?.priority)
        verify(templateDao).getTemplateByCountryAndPriority("ISRAEL", 1)
    }

    @Test
    fun `hasActiveTemplatesForCountry returns true when templates exist`() = runBlocking {
        // Arrange
        whenever(templateDao.hasActiveTemplatesForCountry("ISRAEL")).thenReturn(true)

        // Act
        val hasTemplates = repository.hasActiveTemplatesForCountry("ISRAEL")

        // Assert
        assertTrue(hasTemplates)
        verify(templateDao).hasActiveTemplatesForCountry("ISRAEL")
    }

    @Test
    fun `hasActiveTemplatesForCountry returns false when no templates exist`() = runBlocking {
        // Arrange
        whenever(templateDao.hasActiveTemplatesForCountry("EMPTY")).thenReturn(false)

        // Act
        val hasTemplates = repository.hasActiveTemplatesForCountry("EMPTY")

        // Assert
        assertFalse(hasTemplates)
        verify(templateDao).hasActiveTemplatesForCountry("EMPTY")
    }

    // Template persistence tests
    @Test
    fun `saveTemplate inserts new template and returns ID`() = runBlocking {
        // Arrange
        val template = createTestTemplate("ISRAEL", "NNNNNN", "6-digit", 1)
        whenever(templateDao.insertTemplate(any())).thenReturn(42L)

        // Act
        val id = repository.saveTemplate(template)

        // Assert
        assertEquals(42L, id)
        verify(templateDao).insertTemplate(argThat { entity ->
            entity.countryId == "ISRAEL" && 
            entity.templatePattern == "NNNNNN" &&
            entity.priority == 1
        })
    }

    @Test
    fun `updateTemplate calls dao update`() = runBlocking {
        // Arrange
        val template = createTestTemplate("ISRAEL", "NNNNNN", "6-digit", 1).copy(id = 5)

        // Act
        repository.updateTemplate(template)

        // Assert
        verify(templateDao).updateTemplate(argThat { entity ->
            entity.id == 5 && 
            entity.countryId == "ISRAEL" && 
            entity.templatePattern == "NNNNNN"
        })
    }

    @Test
    fun `deleteTemplate calls dao delete`() = runBlocking {
        // Act
        repository.deleteTemplate(123)

        // Assert
        verify(templateDao).deleteTemplate(123)
    }

    @Test
    fun `replaceTemplatesForCountry calls dao replace with correct entities`() = runBlocking {
        // Arrange
        val templates = listOf(
            createTestTemplate("ISRAEL", "NNNNNN", "6-digit", 1),
            createTestTemplate("ISRAEL", "NNNNNNN", "7-digit", 2)
        )

        // Act
        repository.replaceTemplatesForCountry("ISRAEL", templates)

        // Assert
        verify(templateDao).replaceTemplatesForCountry(
            eq("ISRAEL"), 
            argThat { entities ->
                entities.size == 2 &&
                entities.all { it.countryId == "ISRAEL" } &&
                entities.any { it.templatePattern == "NNNNNN" } &&
                entities.any { it.templatePattern == "NNNNNNN" }
            }
        )
    }

    // Configuration status tests
    @Test
    fun `getCountriesWithoutTemplates returns countries without active templates`() = runBlocking {
        // Arrange
        val countriesWithoutTemplates = listOf(
            testCountries.find { it.id == "SINGAPORE" }!!
        )
        whenever(templateDao.getCountriesWithoutTemplates()).thenReturn(countriesWithoutTemplates)

        // Act
        val countries = repository.getCountriesWithoutTemplates()

        // Assert
        assertEquals(1, countries.size)
        assertEquals(Country.SINGAPORE, countries[0])
        verify(templateDao).getCountriesWithoutTemplates()
    }

    @Test
    fun `getCountriesWithoutTemplates returns empty list when all configured`() = runBlocking {
        // Arrange
        whenever(templateDao.getCountriesWithoutTemplates()).thenReturn(emptyList())

        // Act
        val countries = repository.getCountriesWithoutTemplates()

        // Assert
        assertTrue(countries.isEmpty())
        verify(templateDao).getCountriesWithoutTemplates()
    }

    // Template validation tests
    @Test
    fun `validateTemplatePattern returns valid for correct pattern`() = runBlocking {
        // Act
        val result = repository.validateTemplatePattern("LLNNLLL")

        // Assert
        assertTrue(result.isValid)
        assertTrue(result.errorMessage.isEmpty())
    }

    @Test
    fun `validateTemplatePattern returns invalid for empty pattern`() = runBlocking {
        // Act
        val result = repository.validateTemplatePattern("")

        // Assert
        assertFalse(result.isValid)
        assertEquals("Template pattern cannot be empty", result.errorMessage)
    }

    @Test
    fun `validateTemplatePattern returns invalid for pattern too long`() = runBlocking {
        // Arrange
        val longPattern = "L".repeat(13) // Exceeds MAX_TEMPLATE_LENGTH (12)

        // Act
        val result = repository.validateTemplatePattern(longPattern)

        // Assert
        assertFalse(result.isValid)
        assertTrue(result.errorMessage.contains("cannot exceed"))
    }

    @Test
    fun `validateTemplatePattern returns invalid for pattern with invalid characters`() = runBlocking {
        // Act
        val result = repository.validateTemplatePattern("LLNNXYZ")

        // Assert
        assertFalse(result.isValid)
        assertTrue(result.errorMessage.contains("invalid characters"))
    }

    @Test
    fun `validateTemplatePattern returns invalid for pattern without letters`() = runBlocking {
        // Act
        val result = repository.validateTemplatePattern("NNNNNNN")

        // Assert
        assertFalse(result.isValid)
        assertTrue(result.errorMessage.contains("at least one letter"))
    }

    @Test
    fun `validateTemplatePattern returns invalid for pattern without numbers`() = runBlocking {
        // Act
        val result = repository.validateTemplatePattern("LLLLLLL")

        // Assert
        assertFalse(result.isValid)
        assertTrue(result.errorMessage.contains("at least one number"))
    }

    @Test
    fun `validateTemplatePattern returns warnings for short pattern`() = runBlocking {
        // Act
        val result = repository.validateTemplatePattern("LLNN")

        // Assert
        assertTrue(result.isValid)
        assertTrue(result.warnings.any { it.contains("quite short") })
    }

    @Test
    fun `validateTemplatePattern returns warnings for long pattern`() = runBlocking {
        // Act
        val result = repository.validateTemplatePattern("LLLNNNLLLN") // 10 characters

        // Assert
        assertTrue(result.isValid)
        assertTrue(result.warnings.any { it.contains("quite long") })
    }

    // Plate validation tests
    @Test
    fun `validatePlateAgainstTemplates returns valid for matching plate`() = runBlocking {
        // Arrange
        val israelTemplates = testTemplates.filter { it.countryId == "ISRAEL" }
        whenever(templateDao.getTemplatesByCountrySync("ISRAEL")).thenReturn(israelTemplates)

        // Act
        val result = repository.validatePlateAgainstTemplates("123456", "ISRAEL")

        // Assert
        assertTrue(result.isValid)
        assertNotNull(result.matchedTemplate)
        assertEquals("123456", result.formattedPlate)
        assertEquals("NNNNNN", result.matchedTemplate?.templatePattern)
        verify(templateDao).getTemplatesByCountrySync("ISRAEL")
    }

    @Test
    fun `validatePlateAgainstTemplates returns valid for second priority template`() = runBlocking {
        // Arrange
        val israelTemplates = testTemplates.filter { it.countryId == "ISRAEL" }
        whenever(templateDao.getTemplatesByCountrySync("ISRAEL")).thenReturn(israelTemplates)

        // Act
        val result = repository.validatePlateAgainstTemplates("1234567", "ISRAEL")

        // Assert
        assertTrue(result.isValid)
        assertNotNull(result.matchedTemplate)
        assertEquals("1234567", result.formattedPlate)
        assertEquals("NNNNNNN", result.matchedTemplate?.templatePattern)
        assertEquals(2, result.matchedTemplate?.priority)
    }

    @Test
    fun `validatePlateAgainstTemplates returns invalid for no templates`() = runBlocking {
        // Arrange
        whenever(templateDao.getTemplatesByCountrySync("EMPTY")).thenReturn(emptyList())

        // Act
        val result = repository.validatePlateAgainstTemplates("123456", "EMPTY")

        // Assert
        assertFalse(result.isValid)
        assertTrue(result.errorMessage?.contains("No templates configured") == true)
        verify(templateDao).getTemplatesByCountrySync("EMPTY")
    }

    @Test
    fun `validatePlateAgainstTemplates returns invalid for non-matching plate`() = runBlocking {
        // Arrange
        val israelTemplates = testTemplates.filter { it.countryId == "ISRAEL" }
        whenever(templateDao.getTemplatesByCountrySync("ISRAEL")).thenReturn(israelTemplates)

        // Act
        val result = repository.validatePlateAgainstTemplates("ABC123", "ISRAEL")

        // Assert
        assertFalse(result.isValid)
        assertTrue(result.errorMessage?.contains("does not match any configured templates") == true)
    }

    @Test
    fun `validatePlateAgainstTemplates handles formatted plates correctly`() = runBlocking {
        // Arrange
        val israelTemplates = testTemplates.filter { it.countryId == "ISRAEL" }
        whenever(templateDao.getTemplatesByCountrySync("ISRAEL")).thenReturn(israelTemplates)

        // Act
        val result = repository.validatePlateAgainstTemplates("12-34-56", "ISRAEL")

        // Assert
        assertTrue(result.isValid)
        assertEquals("123456", result.formattedPlate)
    }

    // Entity mapping tests
    @Test
    fun `domain template maps to entity correctly`() = runBlocking {
        // Arrange
        val template = createTestTemplate("ISRAEL", "NNNNNN", "6-digit", 1)

        // Act
        repository.saveTemplate(template)

        // Assert
        verify(templateDao).insertTemplate(argThat { entity ->
            entity.countryId == template.countryId &&
            entity.templatePattern == template.templatePattern &&
            entity.displayName == template.displayName &&
            entity.priority == template.priority &&
            entity.isActive == template.isActive &&
            entity.description == template.description &&
            entity.regexPattern == template.regexPattern
        })
    }

    @Test
    fun `entity maps to domain template correctly`() = runBlocking {
        // Arrange
        val entity = testTemplates[0]
        whenever(templateDao.getTemplatesByCountrySync("ISRAEL")).thenReturn(listOf(entity))

        // Act
        val templates = repository.getTemplatesByCountrySync("ISRAEL")

        // Assert
        val template = templates[0]
        assertEquals(entity.id, template.id)
        assertEquals(entity.countryId, template.countryId)
        assertEquals(entity.templatePattern, template.templatePattern)
        assertEquals(entity.displayName, template.displayName)
        assertEquals(entity.priority, template.priority)
        assertEquals(entity.isActive, template.isActive)
        assertEquals(entity.description, template.description)
        assertEquals(entity.regexPattern, template.regexPattern)
    }

    // Error handling tests
    @Test
    fun `repository handles dao exceptions gracefully`() = runBlocking {
        // Arrange
        whenever(templateDao.getTemplatesByCountrySync("ISRAEL")).thenThrow(RuntimeException("Database error"))

        // Act & Assert
        try {
            repository.validatePlateAgainstTemplates("123456", "ISRAEL")
            fail("Expected exception to be thrown")
        } catch (e: RuntimeException) {
            assertEquals("Database error", e.message)
        }
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
            regexPattern = LicensePlateTemplate.templatePatternToRegex(pattern)
        )
    }
}