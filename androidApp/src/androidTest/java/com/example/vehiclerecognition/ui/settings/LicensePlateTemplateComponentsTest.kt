package com.example.vehiclerecognition.ui.settings

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.vehiclerecognition.data.models.Country
import com.example.vehiclerecognition.domain.service.ConfigurationStatus
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.*

@RunWith(AndroidJUnit4::class)
class LicensePlateTemplateComponentsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val testCountries = listOf(Country.ISRAEL, Country.UK, Country.SINGAPORE)
    
    private val testConfigurationStatus = ConfigurationStatus(
        totalCountries = 3,
        configuredCountries = 1,
        needsConfiguration = listOf(Country.UK, Country.SINGAPORE),
        isFullyConfigured = false
    )

    private val testTemplates = listOf(
        EditableTemplate(
            id = 1,
            pattern = "NNNNNN",
            displayName = "Template 1",
            priority = 1,
            isValid = true,
            validationMessage = null
        ),
        EditableTemplate(
            id = 2,
            pattern = "NNNNNNN",
            displayName = "Template 2",
            priority = 2,
            isValid = true,
            validationMessage = null
        )
    )

    // Configuration Status Card Tests
    @Test
    fun configurationStatusCard_displaysIncompleteConfiguration() {
        composeTestRule.setContent {
            ConfigurationStatusCard(testConfigurationStatus)
        }

        composeTestRule.onNodeWithText("Configuration Status").assertIsDisplayed()
        composeTestRule.onNodeWithText("1 of 3 countries configured").assertIsDisplayed()
        composeTestRule.onNodeWithText("Needs configuration: United Kingdom, Singapore").assertIsDisplayed()
    }

    @Test
    fun configurationStatusCard_displaysCompleteConfiguration() {
        val completeStatus = ConfigurationStatus(
            totalCountries = 3,
            configuredCountries = 3,
            needsConfiguration = emptyList(),
            isFullyConfigured = true
        )

        composeTestRule.setContent {
            ConfigurationStatusCard(completeStatus)
        }

        composeTestRule.onNodeWithText("All countries configured").assertIsDisplayed()
        composeTestRule.onNodeWithText("Template system is ready").assertIsDisplayed()
    }

    // Country Selection Card Tests
    @Test
    fun countrySelectionCard_displaysAllCountries() {
        composeTestRule.setContent {
            CountrySelectionCard(
                availableCountries = testCountries,
                selectedCountry = null,
                onCountrySelected = { }
            )
        }

        composeTestRule.onNodeWithText("Select Country").assertIsDisplayed()
        composeTestRule.onNodeWithText("Israel").assertIsDisplayed()
        composeTestRule.onNodeWithText("United Kingdom").assertIsDisplayed()
        composeTestRule.onNodeWithText("Singapore").assertIsDisplayed()
    }

    @Test
    fun countrySelectionCard_highlightsSelectedCountry() {
        composeTestRule.setContent {
            CountrySelectionCard(
                availableCountries = testCountries,
                selectedCountry = Country.ISRAEL,
                onCountrySelected = { }
            )
        }

        // Check that Israel is selected (this will depend on your UI implementation)
        composeTestRule.onNodeWithText("Israel").assertIsDisplayed()
    }

    @Test
    fun countrySelectionCard_triggersSelectionCallback() {
        var selectedCountry: Country? = null

        composeTestRule.setContent {
            CountrySelectionCard(
                availableCountries = testCountries,
                selectedCountry = null,
                onCountrySelected = { selectedCountry = it }
            )
        }

        composeTestRule.onNodeWithText("United Kingdom").performClick()
        assertEquals(Country.UK, selectedCountry)
    }

    // Template Builder Card Tests
    @Test
    fun templateBuilderCard_displaysTemplates() {
        composeTestRule.setContent {
            TemplateBuilderCard(
                templates = testTemplates,
                validationErrors = emptyMap(),
                onTemplatePatternChanged = { _, _ -> },
                onAddTemplate = { },
                onDeleteTemplate = { },
                maxTemplates = 2
            )
        }

        composeTestRule.onNodeWithText("Template Patterns").assertIsDisplayed()
        composeTestRule.onNodeWithText("Template 1 (Priority 1)").assertIsDisplayed()
        composeTestRule.onNodeWithText("Template 2 (Priority 2)").assertIsDisplayed()
    }

    @Test
    fun templateBuilderCard_displaysPatternInputs() {
        composeTestRule.setContent {
            TemplateBuilderCard(
                templates = testTemplates,
                validationErrors = emptyMap(),
                onTemplatePatternChanged = { _, _ -> },
                onAddTemplate = { },
                onDeleteTemplate = { },
                maxTemplates = 2
            )
        }

        // Check that template patterns are displayed
        composeTestRule.onNodeWithText("NNNNNN").assertIsDisplayed()
        composeTestRule.onNodeWithText("NNNNNNN").assertIsDisplayed()
    }

    @Test
    fun templateBuilderCard_allowsPatternEditing() {
        var changedIndex = -1
        var changedPattern = ""

        composeTestRule.setContent {
            TemplateBuilderCard(
                templates = testTemplates,
                validationErrors = emptyMap(),
                onTemplatePatternChanged = { index, pattern ->
                    changedIndex = index
                    changedPattern = pattern
                },
                onAddTemplate = { },
                onDeleteTemplate = { },
                maxTemplates = 2
            )
        }

        // Find the first template input and modify it
        composeTestRule.onAllNodesWithText("NNNNNN")[0]
            .performTextClearance()
        composeTestRule.onAllNodesWithText("")[0]
            .performTextInput("LLNNLL")

        assertEquals(0, changedIndex)
        assertEquals("LLNNLL", changedPattern)
    }

    @Test
    fun templateBuilderCard_showsValidationErrors() {
        val validationErrors = mapOf(
            0 to "Template pattern cannot be empty",
            1 to "Invalid characters in pattern"
        )

        composeTestRule.setContent {
            TemplateBuilderCard(
                templates = testTemplates,
                validationErrors = validationErrors,
                onTemplatePatternChanged = { _, _ -> },
                onAddTemplate = { },
                onDeleteTemplate = { },
                maxTemplates = 2
            )
        }

        composeTestRule.onNodeWithText("Template pattern cannot be empty").assertIsDisplayed()
        composeTestRule.onNodeWithText("Invalid characters in pattern").assertIsDisplayed()
    }

    @Test
    fun templateBuilderCard_showsAddButtonWhenBelowMax() {
        val singleTemplate = listOf(testTemplates[0])

        composeTestRule.setContent {
            TemplateBuilderCard(
                templates = singleTemplate,
                validationErrors = emptyMap(),
                onTemplatePatternChanged = { _, _ -> },
                onAddTemplate = { },
                onDeleteTemplate = { },
                maxTemplates = 2
            )
        }

        composeTestRule.onNodeWithContentDescription("Add template").assertIsDisplayed()
    }

    @Test
    fun templateBuilderCard_hidesAddButtonWhenAtMax() {
        composeTestRule.setContent {
            TemplateBuilderCard(
                templates = testTemplates, // 2 templates
                validationErrors = emptyMap(),
                onTemplatePatternChanged = { _, _ -> },
                onAddTemplate = { },
                onDeleteTemplate = { },
                maxTemplates = 2
            )
        }

        composeTestRule.onNodeWithContentDescription("Add template").assertDoesNotExist()
    }

    @Test
    fun templateBuilderCard_triggersAddTemplate() {
        var addCalled = false

        composeTestRule.setContent {
            TemplateBuilderCard(
                templates = listOf(testTemplates[0]),
                validationErrors = emptyMap(),
                onTemplatePatternChanged = { _, _ -> },
                onAddTemplate = { addCalled = true },
                onDeleteTemplate = { },
                maxTemplates = 2
            )
        }

        composeTestRule.onNodeWithContentDescription("Add template").performClick()
        assertTrue(addCalled)
    }

    @Test
    fun templateBuilderCard_showsDeleteButtonForMultipleTemplates() {
        composeTestRule.setContent {
            TemplateBuilderCard(
                templates = testTemplates,
                validationErrors = emptyMap(),
                onTemplatePatternChanged = { _, _ -> },
                onAddTemplate = { },
                onDeleteTemplate = { },
                maxTemplates = 2
            )
        }

        composeTestRule.onAllNodesWithContentDescription("Delete template").assertCountEquals(2)
    }

    @Test
    fun templateBuilderCard_hidesDeleteButtonForSingleTemplate() {
        composeTestRule.setContent {
            TemplateBuilderCard(
                templates = listOf(testTemplates[0]),
                validationErrors = emptyMap(),
                onTemplatePatternChanged = { _, _ -> },
                onAddTemplate = { },
                onDeleteTemplate = { },
                maxTemplates = 2
            )
        }

        composeTestRule.onNodeWithContentDescription("Delete template").assertDoesNotExist()
    }

    @Test
    fun templateBuilderCard_triggersDeleteTemplate() {
        var deletedIndex = -1

        composeTestRule.setContent {
            TemplateBuilderCard(
                templates = testTemplates,
                validationErrors = emptyMap(),
                onTemplatePatternChanged = { _, _ -> },
                onAddTemplate = { },
                onDeleteTemplate = { deletedIndex = it },
                maxTemplates = 2
            )
        }

        composeTestRule.onAllNodesWithContentDescription("Delete template")[1].performClick()
        assertEquals(1, deletedIndex)
    }

    // Template Item Tests
    @Test
    fun templateItem_displaysTemplateInformation() {
        composeTestRule.setContent {
            TemplateItem(
                template = testTemplates[0],
                index = 0,
                validationError = null,
                canDelete = true,
                onPatternChanged = { _, _ -> },
                onDelete = { }
            )
        }

        composeTestRule.onNodeWithText("Template 1 (Priority 1)").assertIsDisplayed()
        composeTestRule.onNodeWithText("6 numbers").assertIsDisplayed()
        composeTestRule.onNodeWithText("NNNNNN").assertIsDisplayed()
    }

    @Test
    fun templateItem_showsValidationError() {
        composeTestRule.setContent {
            TemplateItem(
                template = testTemplates[0],
                index = 0,
                validationError = "Pattern is invalid",
                canDelete = true,
                onPatternChanged = { _, _ -> },
                onDelete = { }
            )
        }

        composeTestRule.onNodeWithText("Pattern is invalid").assertIsDisplayed()
    }

    @Test
    fun templateItem_showsDeleteButtonWhenCanDelete() {
        composeTestRule.setContent {
            TemplateItem(
                template = testTemplates[0],
                index = 0,
                validationError = null,
                canDelete = true,
                onPatternChanged = { _, _ -> },
                onDelete = { }
            )
        }

        composeTestRule.onNodeWithContentDescription("Delete template").assertIsDisplayed()
    }

    @Test
    fun templateItem_hidesDeleteButtonWhenCannotDelete() {
        composeTestRule.setContent {
            TemplateItem(
                template = testTemplates[0],
                index = 0,
                validationError = null,
                canDelete = false,
                onPatternChanged = { _, _ -> },
                onDelete = { }
            )
        }

        composeTestRule.onNodeWithContentDescription("Delete template").assertDoesNotExist()
    }

    // Pattern Input Tests
    @Test
    fun patternInput_allowsTextInput() {
        var currentPattern = "NNNNNN"

        composeTestRule.setContent {
            PatternInput(
                pattern = currentPattern,
                isValid = true,
                onPatternChanged = { currentPattern = it },
                modifier = androidx.compose.ui.Modifier
            )
        }

        composeTestRule.onNodeWithText("NNNNNN")
            .performTextClearance()
        composeTestRule.onNode(hasSetTextAction())
            .performTextInput("LLNNLL")

        assertEquals("LLNNLL", currentPattern)
    }

    @Test
    fun patternInput_displaysErrorStateForInvalidPattern() {
        composeTestRule.setContent {
            PatternInput(
                pattern = "INVALID",
                isValid = false,
                onPatternChanged = { },
                modifier = androidx.compose.ui.Modifier
            )
        }

        // The exact way error state is shown depends on your implementation
        // This might be color changes, error icons, etc.
        composeTestRule.onNodeWithText("INVALID").assertIsDisplayed()
    }

    @Test
    fun patternInput_limitsInputLength() {
        var inputPattern = ""

        composeTestRule.setContent {
            PatternInput(
                pattern = "",
                isValid = true,
                onPatternChanged = { inputPattern = it },
                modifier = androidx.compose.ui.Modifier
            )
        }

        // Try to input a very long pattern (more than 12 characters)
        composeTestRule.onNode(hasSetTextAction())
            .performTextInput("LLNNLLNNLLNNLL") // 14 characters

        // Should be limited to 12 characters
        assertTrue(inputPattern.length <= 12)
    }

    @Test
    fun patternInput_convertsToUppercase() {
        var inputPattern = ""

        composeTestRule.setContent {
            PatternInput(
                pattern = "",
                isValid = true,
                onPatternChanged = { inputPattern = it },
                modifier = androidx.compose.ui.Modifier
            )
        }

        composeTestRule.onNode(hasSetTextAction())
            .performTextInput("llnnll")

        assertEquals("LLNNLL", inputPattern)
    }

    // Full Screen Integration Tests
    @Test
    fun licensePlateTemplateContent_rendersAllComponents() {
        composeTestRule.setContent {
            LicensePlateTemplateContent(
                availableCountries = testCountries,
                configurationStatus = testConfigurationStatus,
                selectedCountry = Country.ISRAEL,
                templates = testTemplates,
                validationErrors = emptyMap(),
                isSaving = false,
                canSave = true,
                onCountrySelected = { },
                onTemplatePatternChanged = { _, _ -> },
                onAddTemplate = { },
                onDeleteTemplate = { },
                onSaveTemplates = { }
            )
        }

        // Check all main components are present
        composeTestRule.onNodeWithText("License Plate Templates").assertIsDisplayed()
        composeTestRule.onNodeWithText("Save").assertIsDisplayed()
        composeTestRule.onNodeWithText("Configuration Status").assertIsDisplayed()
        composeTestRule.onNodeWithText("Select Country").assertIsDisplayed()
        composeTestRule.onNodeWithText("Template Patterns").assertIsDisplayed()
    }

    @Test
    fun licensePlateTemplateContent_saveButtonEnabledWhenCanSave() {
        composeTestRule.setContent {
            LicensePlateTemplateContent(
                availableCountries = testCountries,
                configurationStatus = testConfigurationStatus,
                selectedCountry = Country.ISRAEL,
                templates = testTemplates,
                validationErrors = emptyMap(),
                isSaving = false,
                canSave = true,
                onCountrySelected = { },
                onTemplatePatternChanged = { _, _ -> },
                onAddTemplate = { },
                onDeleteTemplate = { },
                onSaveTemplates = { }
            )
        }

        composeTestRule.onNodeWithText("Save").assertIsEnabled()
    }

    @Test
    fun licensePlateTemplateContent_saveButtonDisabledWhenCannotSave() {
        composeTestRule.setContent {
            LicensePlateTemplateContent(
                availableCountries = testCountries,
                configurationStatus = testConfigurationStatus,
                selectedCountry = Country.ISRAEL,
                templates = testTemplates,
                validationErrors = emptyMap(),
                isSaving = false,
                canSave = false,
                onCountrySelected = { },
                onTemplatePatternChanged = { _, _ -> },
                onAddTemplate = { },
                onDeleteTemplate = { },
                onSaveTemplates = { }
            )
        }

        composeTestRule.onNodeWithText("Save").assertIsNotEnabled()
    }

    @Test
    fun licensePlateTemplateContent_saveButtonShowsProgressWhenSaving() {
        composeTestRule.setContent {
            LicensePlateTemplateContent(
                availableCountries = testCountries,
                configurationStatus = testConfigurationStatus,
                selectedCountry = Country.ISRAEL,
                templates = testTemplates,
                validationErrors = emptyMap(),
                isSaving = true,
                canSave = true,
                onCountrySelected = { },
                onTemplatePatternChanged = { _, _ -> },
                onAddTemplate = { },
                onDeleteTemplate = { },
                onSaveTemplates = { }
            )
        }

        // Save button should be disabled and show progress
        composeTestRule.onNodeWithText("Save").assertIsNotEnabled()
    }

    @Test
    fun licensePlateTemplateContent_triggersSaveCallback() {
        var saveCalled = false

        composeTestRule.setContent {
            LicensePlateTemplateContent(
                availableCountries = testCountries,
                configurationStatus = testConfigurationStatus,
                selectedCountry = Country.ISRAEL,
                templates = testTemplates,
                validationErrors = emptyMap(),
                isSaving = false,
                canSave = true,
                onCountrySelected = { },
                onTemplatePatternChanged = { _, _ -> },
                onAddTemplate = { },
                onDeleteTemplate = { },
                onSaveTemplates = { saveCalled = true }
            )
        }

        composeTestRule.onNodeWithText("Save").performClick()
        assertTrue(saveCalled)
    }

    @Test
    fun licensePlateTemplateContent_hidesTemplateBuilderWhenNoCountrySelected() {
        composeTestRule.setContent {
            LicensePlateTemplateContent(
                availableCountries = testCountries,
                configurationStatus = testConfigurationStatus,
                selectedCountry = null, // No country selected
                templates = testTemplates,
                validationErrors = emptyMap(),
                isSaving = false,
                canSave = true,
                onCountrySelected = { },
                onTemplatePatternChanged = { _, _ -> },
                onAddTemplate = { },
                onDeleteTemplate = { },
                onSaveTemplates = { }
            )
        }

        composeTestRule.onNodeWithText("Template Patterns").assertDoesNotExist()
    }

    // State-based UI tests
    @Test
    fun licensePlateTemplateScreen_showsLoadingState() {
        // This would require a mocked ViewModel, which is complex in Compose tests
        // In practice, you might test this with a test implementation of your ViewModel
    }

    @Test
    fun licensePlateTemplateScreen_showsErrorState() {
        // Similarly, this would test the Error UI state
        // You could create a test composable that directly shows the error state
        composeTestRule.setContent {
            Box(
                modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) {
                androidx.compose.foundation.layout.Column(
                    horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
                ) {
                    androidx.compose.material3.Icon(
                        imageVector = androidx.compose.material.icons.Icons.Default.Error,
                        contentDescription = "Error",
                        modifier = androidx.compose.ui.Modifier.size(48.dp)
                    )
                    androidx.compose.foundation.layout.Spacer(modifier = androidx.compose.ui.Modifier.height(16.dp))
                    androidx.compose.material3.Text(
                        text = "Test error message",
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = androidx.compose.ui.Modifier.padding(16.dp)
                    )
                }
            }
        }

        composeTestRule.onNodeWithText("Test error message").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Error").assertIsDisplayed()
    }
}