package com.example.vehiclerecognition.ui.settings

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.example.vehiclerecognition.data.models.Country
import com.example.vehiclerecognition.data.models.LicensePlateTemplate
import com.example.vehiclerecognition.domain.service.ConfigurationStatus
import com.example.vehiclerecognition.domain.service.LicensePlateTemplateService
import com.example.vehiclerecognition.domain.service.TemplateOperationResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.*

@OptIn(ExperimentalCoroutinesApi::class)
class LicensePlateTemplateViewModelTest {

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    @Mock
    private lateinit var templateService: LicensePlateTemplateService

    private lateinit var viewModel: LicensePlateTemplateViewModel
    private val testDispatcher = UnconfinedTestDispatcher()

    private val testCountries = listOf(Country.ISRAEL, Country.UK, Country.SINGAPORE)
    private val testConfigurationStatus = ConfigurationStatus(
        totalCountries = 3,
        configuredCountries = 1,
        needsConfiguration = listOf(Country.UK, Country.SINGAPORE),
        isFullyConfigured = false
    )

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        Dispatchers.setMain(testDispatcher)

        // Default mock behavior
        whenever(templateService.getAvailableCountries()).thenReturn(MutableStateFlow(testCountries))
        runTest {
            whenever(templateService.getConfigurationStatus()).thenReturn(testConfigurationStatus)
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // Initialization tests
    @Test
    fun `initial state is Loading`() {
        // Act
        viewModel = LicensePlateTemplateViewModel(templateService)

        // Assert
        assertTrue(viewModel.uiState.value is TemplateUiState.Loading)
    }

    @Test
    fun `loadInitialData sets Success state with countries and configuration`() = runTest {
        // Act
        viewModel = LicensePlateTemplateViewModel(templateService)

        // Assert
        val uiState = viewModel.uiState.value
        assertTrue(uiState is TemplateUiState.Success)
        assertEquals(testCountries, (uiState as TemplateUiState.Success).availableCountries)
        assertEquals(testConfigurationStatus, uiState.configurationStatus)

        verify(templateService).initializeSystem()
        verify(templateService).getAvailableCountries()
        verify(templateService).getConfigurationStatus()
    }

    @Test
    fun `loadInitialData auto-selects first country when none selected`() = runTest {
        // Arrange
        whenever(templateService.getTemplatesForCountry(Country.ISRAEL.name))
            .thenReturn(MutableStateFlow(emptyList()))

        // Act
        viewModel = LicensePlateTemplateViewModel(templateService)

        // Assert
        assertEquals(Country.ISRAEL, viewModel.selectedCountry.value)
        verify(templateService).getTemplatesForCountry(Country.ISRAEL.name)
    }

    @Test
    fun `loadInitialData sets Error state when service throws exception`() = runTest {
        // Arrange
        whenever(templateService.initializeSystem()).thenThrow(RuntimeException("Service error"))

        // Act
        viewModel = LicensePlateTemplateViewModel(templateService)

        // Assert
        val uiState = viewModel.uiState.value
        assertTrue(uiState is TemplateUiState.Error)
        assertTrue((uiState as TemplateUiState.Error).message.contains("Service error"))
    }

    // Country selection tests
    @Test
    fun `selectCountry changes selected country and loads templates`() = runTest {
        // Arrange
        val ukTemplates = listOf(
            createTestTemplate("UK", "LLNNLLL", "UK Format", 1)
        )
        whenever(templateService.getTemplatesForCountry("UK"))
            .thenReturn(MutableStateFlow(ukTemplates))
        whenever(templateService.getTemplatesForCountry("ISRAEL"))
            .thenReturn(MutableStateFlow(emptyList()))

        viewModel = LicensePlateTemplateViewModel(templateService)

        // Act
        viewModel.selectCountry(Country.UK)

        // Assert
        assertEquals(Country.UK, viewModel.selectedCountry.value)
        val templates = viewModel.templates.value
        assertEquals(1, templates.size)
        assertEquals("LLNNLLL", templates[0].pattern)
        verify(templateService).getTemplatesForCountry("UK")
    }

    @Test
    fun `selectCountry does not reload if same country selected`() = runTest {
        // Arrange
        whenever(templateService.getTemplatesForCountry("ISRAEL"))
            .thenReturn(MutableStateFlow(emptyList()))

        viewModel = LicensePlateTemplateViewModel(templateService)
        clearInvocations(templateService)

        // Act
        viewModel.selectCountry(Country.ISRAEL) // Same country

        // Assert
        verify(templateService, never()).getTemplatesForCountry(any())
    }

    // Template loading tests
    @Test
    fun `loadTemplatesForCountry creates default templates when none exist`() = runTest {
        // Arrange
        val israelDefaults = listOf(
            createTestTemplate("ISRAEL", "NNNNNN", "6-digit", 1),
            createTestTemplate("ISRAEL", "NNNNNNN", "7-digit", 2)
        )
        whenever(templateService.getTemplatesForCountry("ISRAEL"))
            .thenReturn(MutableStateFlow(emptyList()))
        whenever(templateService.createDefaultTemplatesForCountry("ISRAEL"))
            .thenReturn(israelDefaults)

        // Act
        viewModel = LicensePlateTemplateViewModel(templateService)

        // Assert
        val templates = viewModel.templates.value
        assertEquals(2, templates.size)
        assertEquals("NNNNNN", templates[0].pattern)
        assertEquals("NNNNNNN", templates[1].pattern)
        assertEquals(1, templates[0].priority)
        assertEquals(2, templates[1].priority)
    }

    @Test
    fun `loadTemplatesForCountry loads existing templates`() = runTest {
        // Arrange
        val existingTemplates = listOf(
            createTestTemplate("ISRAEL", "NNNNNN", "Custom 6-digit", 1)
        )
        whenever(templateService.getTemplatesForCountry("ISRAEL"))
            .thenReturn(MutableStateFlow(existingTemplates))

        // Act
        viewModel = LicensePlateTemplateViewModel(templateService)

        // Assert
        val templates = viewModel.templates.value
        assertEquals(1, templates.size)
        assertEquals("NNNNNN", templates[0].pattern)
        assertEquals("Custom 6-digit", templates[0].displayName)
        assertEquals(existingTemplates[0].id, templates[0].id)
    }

    @Test
    fun `loadTemplatesForCountry creates empty template when no defaults available`() = runTest {
        // Arrange
        whenever(templateService.getTemplatesForCountry("UNKNOWN"))
            .thenReturn(MutableStateFlow(emptyList()))
        whenever(templateService.createDefaultTemplatesForCountry("UNKNOWN"))
            .thenReturn(emptyList())

        viewModel = LicensePlateTemplateViewModel(templateService)

        // Act
        viewModel.selectCountry(Country.SINGAPORE) // Assuming no specific defaults

        // Assert - Should create at least one empty template
        val templates = viewModel.templates.value
        assertTrue(templates.isNotEmpty())
    }

    // Template editing tests
    @Test
    fun `updateTemplatePattern updates pattern and validates`() = runTest {
        // Arrange
        whenever(templateService.getTemplatesForCountry("ISRAEL"))
            .thenReturn(MutableStateFlow(emptyList()))
        whenever(templateService.createDefaultTemplatesForCountry("ISRAEL"))
            .thenReturn(listOf(createTestTemplate("ISRAEL", "", "Template 1", 1)))

        viewModel = LicensePlateTemplateViewModel(templateService)

        // Act
        viewModel.updateTemplatePattern(0, "LLNNLLL")

        // Assert
        val templates = viewModel.templates.value
        assertEquals("LLNNLLL", templates[0].pattern)
        assertTrue(templates[0].isValid)
        assertTrue(viewModel.validationErrors.value.isEmpty())
    }

    @Test
    fun `updateTemplatePattern limits pattern length to 12 characters`() = runTest {
        // Arrange
        setupBasicViewModel()

        // Act
        viewModel.updateTemplatePattern(0, "LLNNLLLLLNNLLL") // 14 characters

        // Assert
        val templates = viewModel.templates.value
        assertEquals(12, templates[0].pattern.length)
    }

    @Test
    fun `updateTemplatePattern converts to uppercase`() = runTest {
        // Arrange
        setupBasicViewModel()

        // Act
        viewModel.updateTemplatePattern(0, "llnnlll")

        // Assert
        val templates = viewModel.templates.value
        assertEquals("LLNNLLL", templates[0].pattern)
    }

    @Test
    fun `updateTemplatePattern validates and shows errors for invalid pattern`() = runTest {
        // Arrange
        setupBasicViewModel()

        // Act
        viewModel.updateTemplatePattern(0, "INVALID")

        // Assert
        val templates = viewModel.templates.value
        assertFalse(templates[0].isValid)
        assertTrue(viewModel.validationErrors.value.containsKey(0))
        assertFalse(viewModel.canSave.value)
    }

    @Test
    fun `updateTemplatePattern shows error for empty pattern`() = runTest {
        // Arrange
        setupBasicViewModel()

        // Act
        viewModel.updateTemplatePattern(0, "")

        // Assert
        val errors = viewModel.validationErrors.value
        assertTrue(errors.containsKey(0))
        assertEquals("Template pattern cannot be empty", errors[0])
    }

    @Test
    fun `updateTemplatePattern shows error for invalid characters`() = runTest {
        // Arrange
        setupBasicViewModel()

        // Act
        viewModel.updateTemplatePattern(0, "LLNNXYZ")

        // Assert
        val errors = viewModel.validationErrors.value
        assertTrue(errors.containsKey(0))
        assertTrue(errors[0]?.contains("can only contain") == true)
    }

    // Template management tests
    @Test
    fun `addTemplate creates new template with correct priority`() = runTest {
        // Arrange
        setupBasicViewModel()

        // Act
        viewModel.addTemplate()

        // Assert
        val templates = viewModel.templates.value
        assertEquals(2, templates.size)
        assertEquals("Template 2", templates[1].displayName)
        assertEquals(2, templates[1].priority)
    }

    @Test
    fun `addTemplate does not exceed maximum of 2 templates`() = runTest {
        // Arrange
        setupViewModelWithTwoTemplates()

        // Act
        viewModel.addTemplate()

        // Assert
        val templates = viewModel.templates.value
        assertEquals(2, templates.size) // Should not add a third template
    }

    @Test
    fun `deleteTemplate removes template and adjusts priorities`() = runTest {
        // Arrange
        setupViewModelWithTwoTemplates()

        // Act
        viewModel.deleteTemplate(0) // Delete first template

        // Assert
        val templates = viewModel.templates.value
        assertEquals(1, templates.size)
        assertEquals(1, templates[0].priority) // Priority should be adjusted
        assertEquals("Template 1", templates[0].displayName) // Display name should be adjusted
    }

    @Test
    fun `deleteTemplate does not remove last template`() = runTest {
        // Arrange
        setupBasicViewModel() // Single template

        // Act
        viewModel.deleteTemplate(0)

        // Assert
        val templates = viewModel.templates.value
        assertEquals(1, templates.size) // Should not delete the last template
    }

    @Test
    fun `deleteTemplate adjusts validation errors indices`() = runTest {
        // Arrange
        setupViewModelWithTwoTemplates()
        viewModel.updateTemplatePattern(0, "") // Create error at index 0
        viewModel.updateTemplatePattern(1, "INVALID") // Create error at index 1

        // Act
        viewModel.deleteTemplate(0) // Delete template at index 0

        // Assert
        val errors = viewModel.validationErrors.value
        assertTrue(errors.containsKey(0)) // Error from index 1 should move to index 0
        assertFalse(errors.containsKey(1)) // Index 1 should no longer exist
    }

    // Saving tests
    @Test
    fun `saveTemplates succeeds with valid templates`() = runTest {
        // Arrange
        setupBasicViewModel()
        viewModel.updateTemplatePattern(0, "LLNNLLL")
        
        whenever(templateService.saveTemplatesForCountry(eq("ISRAEL"), any()))
            .thenReturn(TemplateOperationResult(true, "Success"))
        whenever(templateService.getConfigurationStatus())
            .thenReturn(testConfigurationStatus)

        // Act
        viewModel.saveTemplates()

        // Assert
        verify(templateService).saveTemplatesForCountry(eq("ISRAEL"), argThat { templates ->
            templates.size == 1 && templates[0].templatePattern == "LLNNLLL"
        })
        verify(templateService, times(2)).getConfigurationStatus() // Once during init, once after save
    }

    @Test
    fun `saveTemplates does not save when no country selected`() = runTest {
        // Arrange
        setupBasicViewModel()
        viewModel.updateTemplatePattern(0, "LLNNLLL")
        
        // Clear selected country
        val viewModelField = viewModel.javaClass.getDeclaredField("_selectedCountry")
        viewModelField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val selectedCountryFlow = viewModelField.get(viewModel) as MutableStateFlow<Country?>
        selectedCountryFlow.value = null

        // Act
        viewModel.saveTemplates()

        // Assert
        verify(templateService, never()).saveTemplatesForCountry(any(), any())
    }

    @Test
    fun `saveTemplates filters out invalid templates`() = runTest {
        // Arrange
        setupViewModelWithTwoTemplates()
        viewModel.updateTemplatePattern(0, "LLNNLLL") // Valid
        viewModel.updateTemplatePattern(1, "") // Invalid

        whenever(templateService.saveTemplatesForCountry(eq("ISRAEL"), any()))
            .thenReturn(TemplateOperationResult(true, "Success"))
        whenever(templateService.getConfigurationStatus())
            .thenReturn(testConfigurationStatus)

        // Act
        viewModel.saveTemplates()

        // Assert
        verify(templateService).saveTemplatesForCountry(eq("ISRAEL"), argThat { templates ->
            templates.size == 1 && templates[0].templatePattern == "LLNNLLL"
        })
    }

    @Test
    fun `saveTemplates does not save when no valid templates`() = runTest {
        // Arrange
        setupBasicViewModel()
        // Leave template with empty pattern (invalid)

        // Act
        viewModel.saveTemplates()

        // Assert
        verify(templateService, never()).saveTemplatesForCountry(any(), any())
    }

    @Test
    fun `saveTemplates handles service failure gracefully`() = runTest {
        // Arrange
        setupBasicViewModel()
        viewModel.updateTemplatePattern(0, "LLNNLLL")
        
        whenever(templateService.saveTemplatesForCountry(eq("ISRAEL"), any()))
            .thenReturn(TemplateOperationResult(false, "Save failed"))

        // Act
        viewModel.saveTemplates()

        // Assert
        val uiState = viewModel.uiState.value
        assertTrue(uiState is TemplateUiState.Error)
        assertTrue((uiState as TemplateUiState.Error).message.contains("Failed to save templates"))
    }

    @Test
    fun `saveTemplates handles exception gracefully`() = runTest {
        // Arrange
        setupBasicViewModel()
        viewModel.updateTemplatePattern(0, "LLNNLLL")
        
        whenever(templateService.saveTemplatesForCountry(eq("ISRAEL"), any()))
            .thenThrow(RuntimeException("Database error"))

        // Act
        viewModel.saveTemplates()

        // Assert
        val uiState = viewModel.uiState.value
        assertTrue(uiState is TemplateUiState.Error)
        assertTrue((uiState as TemplateUiState.Error).message.contains("Error saving templates"))
    }

    @Test
    fun `isSaving state is managed correctly during save operation`() = runTest {
        // Arrange
        setupBasicViewModel()
        viewModel.updateTemplatePattern(0, "LLNNLLL")
        
        whenever(templateService.saveTemplatesForCountry(eq("ISRAEL"), any()))
            .thenReturn(TemplateOperationResult(true, "Success"))
        whenever(templateService.getConfigurationStatus())
            .thenReturn(testConfigurationStatus)

        // Act & Assert
        assertFalse(viewModel.isSaving.value)
        viewModel.saveTemplates()
        assertFalse(viewModel.isSaving.value) // Should be false after completion
    }

    // CanSave state tests
    @Test
    fun `canSave returns false when templates are empty`() = runTest {
        // Arrange
        setupBasicViewModel()
        // Leave template empty

        // Assert
        assertFalse(viewModel.canSave.value)
    }

    @Test
    fun `canSave returns false when validation errors exist`() = runTest {
        // Arrange
        setupBasicViewModel()
        viewModel.updateTemplatePattern(0, "INVALID")

        // Assert
        assertFalse(viewModel.canSave.value)
    }

    @Test
    fun `canSave returns false when saving is in progress`() = runTest {
        // Arrange
        setupBasicViewModel()
        viewModel.updateTemplatePattern(0, "LLNNLLL")
        
        // Manually set saving state (simulating save in progress)
        val savingField = viewModel.javaClass.getDeclaredField("_isSaving")
        savingField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val savingFlow = savingField.get(viewModel) as MutableStateFlow<Boolean>
        savingFlow.value = true

        // Assert
        assertFalse(viewModel.canSave.value)
    }

    @Test
    fun `canSave returns true when conditions are met`() = runTest {
        // Arrange
        setupBasicViewModel()
        viewModel.updateTemplatePattern(0, "LLNNLLL")

        // Assert
        assertTrue(viewModel.canSave.value)
    }

    // Configuration warning tests
    @Test
    fun `getConfigurationWarningMessage returns message for incomplete configuration`() = runTest {
        // Arrange
        setupBasicViewModel()

        // Act
        val message = viewModel.getConfigurationWarningMessage()

        // Assert
        assertNotNull(message)
        assertTrue(message!!.contains("United Kingdom"))
        assertTrue(message.contains("Singapore"))
    }

    @Test
    fun `getConfigurationWarningMessage returns null for complete configuration`() = runTest {
        // Arrange
        val completeStatus = ConfigurationStatus(
            totalCountries = 3,
            configuredCountries = 3,
            needsConfiguration = emptyList(),
            isFullyConfigured = true
        )
        runTest {
            whenever(templateService.getConfigurationStatus()).thenReturn(completeStatus)
        }
        
        // Update configuration status in the view model
        val statusField = viewModel.javaClass.getDeclaredField("_configurationStatus")
        statusField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val statusFlow = statusField.get(viewModel) as MutableStateFlow<ConfigurationStatus?>
        statusFlow.value = completeStatus
        
        setupBasicViewModel()

        // Act
        val message = viewModel.getConfigurationWarningMessage()

        // Assert
        assertNull(message)
    }

    // Edge cases and error handling
    @Test
    fun `updateTemplatePattern handles invalid index gracefully`() = runTest {
        // Arrange
        setupBasicViewModel()

        // Act & Assert - Should not throw
        viewModel.updateTemplatePattern(-1, "LLNNLLL")
        viewModel.updateTemplatePattern(999, "LLNNLLL")

        // Templates should remain unchanged
        val templates = viewModel.templates.value
        assertEquals(1, templates.size)
    }

    @Test
    fun `deleteTemplate handles invalid index gracefully`() = runTest {
        // Arrange
        setupBasicViewModel()

        // Act & Assert - Should not throw
        viewModel.deleteTemplate(-1)
        viewModel.deleteTemplate(999)

        // Templates should remain unchanged
        val templates = viewModel.templates.value
        assertEquals(1, templates.size)
    }

    // Helper methods
    private suspend fun setupBasicViewModel() {
        whenever(templateService.getTemplatesForCountry("ISRAEL"))
            .thenReturn(MutableStateFlow(emptyList()))
        whenever(templateService.createDefaultTemplatesForCountry("ISRAEL"))
            .thenReturn(listOf(createTestTemplate("ISRAEL", "", "Template 1", 1)))
        
        viewModel = LicensePlateTemplateViewModel(templateService)
    }

    private suspend fun setupViewModelWithTwoTemplates() {
        whenever(templateService.getTemplatesForCountry("ISRAEL"))
            .thenReturn(MutableStateFlow(emptyList()))
        whenever(templateService.createDefaultTemplatesForCountry("ISRAEL"))
            .thenReturn(listOf(
                createTestTemplate("ISRAEL", "LLNNLLL", "Template 1", 1),
                createTestTemplate("ISRAEL", "NNNNNN", "Template 2", 2)
            ))
        
        viewModel = LicensePlateTemplateViewModel(templateService)
    }

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
            description = if (pattern.isNotEmpty()) LicensePlateTemplate.generateDescription(pattern) else "",
            regexPattern = if (pattern.isNotEmpty()) {
                try {
                    LicensePlateTemplate.templatePatternToRegex(pattern)
                } catch (e: Exception) {
                    "invalid"
                }
            } else ""
        )
    }
}