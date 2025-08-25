package com.example.vehiclerecognition.domain.validation

import com.example.vehiclerecognition.data.models.LicensePlateTemplate
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertEquals
import kotlin.test.assertContains

class TemplateValidationRulesTest {

    // Pattern validation tests - Happy paths
    @Test
    fun `validateTemplatePattern returns valid for correct UK pattern`() {
        val result = TemplateValidationRules.validateTemplatePattern("LLNNLLL")
        assertTrue(result.isValid)
        assertTrue(result.errors.isEmpty())
    }

    @Test
    fun `validateTemplatePattern returns valid for Israeli patterns`() {
        val result1 = TemplateValidationRules.validateTemplatePattern("NNNNNN")
        assertTrue(result1.isValid)
        assertTrue(result1.errors.isEmpty())

        val result2 = TemplateValidationRules.validateTemplatePattern("NNNNNNN")
        assertTrue(result2.isValid)
        assertTrue(result2.errors.isEmpty())
    }

    @Test
    fun `validateTemplatePattern returns valid for Singapore patterns`() {
        val result1 = TemplateValidationRules.validateTemplatePattern("LLLNNNNL")
        assertTrue(result1.isValid)
        assertTrue(result1.errors.isEmpty())

        val result2 = TemplateValidationRules.validateTemplatePattern("LLLNNL")
        assertTrue(result2.isValid)
        assertTrue(result2.errors.isEmpty())
    }

    @Test
    fun `validateTemplatePattern returns valid for mixed patterns`() {
        val result1 = TemplateValidationRules.validateTemplatePattern("LNLNLN")
        assertTrue(result1.isValid)
        assertTrue(result1.errors.isEmpty())

        val result2 = TemplateValidationRules.validateTemplatePattern("NNNLLLNNN")
        assertTrue(result2.isValid)
        assertTrue(result2.errors.isEmpty())
    }

    // Pattern validation tests - Error cases
    @Test
    fun `validateTemplatePattern returns invalid for empty pattern`() {
        val result = TemplateValidationRules.validateTemplatePattern("")
        assertFalse(result.isValid)
        assertContains(result.errors, "Template pattern cannot be empty")
    }

    @Test
    fun `validateTemplatePattern returns invalid for pattern too short`() {
        val result = TemplateValidationRules.validateTemplatePattern("LN")
        assertFalse(result.isValid)
        assertContains(result.errors, "Template pattern must be at least ${TemplateValidationRules.MIN_TEMPLATE_LENGTH} characters")
    }

    @Test
    fun `validateTemplatePattern returns invalid for pattern too long`() {
        val longPattern = "L".repeat(TemplateValidationRules.MAX_TEMPLATE_LENGTH + 1)
        val result = TemplateValidationRules.validateTemplatePattern(longPattern)
        assertFalse(result.isValid)
        assertContains(result.errors, "Template pattern cannot exceed ${TemplateValidationRules.MAX_TEMPLATE_LENGTH} characters")
    }

    @Test
    fun `validateTemplatePattern returns invalid for invalid characters`() {
        val result = TemplateValidationRules.validateTemplatePattern("LLNNXYZ")
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.contains("invalid characters") })
    }

    @Test
    fun `validateTemplatePattern returns invalid for no letters`() {
        val result = TemplateValidationRules.validateTemplatePattern("NNNNNNN")
        assertFalse(result.isValid)
        assertContains(result.errors, "Template pattern must contain at least one letter (L)")
    }

    @Test
    fun `validateTemplatePattern returns invalid for no numbers`() {
        val result = TemplateValidationRules.validateTemplatePattern("LLLLLLL")
        assertFalse(result.isValid)
        assertContains(result.errors, "Template pattern must contain at least one number (N)")
    }

    @Test
    fun `validateTemplatePattern returns multiple errors for invalid pattern`() {
        val result = TemplateValidationRules.validateTemplatePattern("XYZ")
        assertFalse(result.isValid)
        assertTrue(result.errors.size > 1)
        assertTrue(result.errors.any { it.contains("invalid characters") })
        assertTrue(result.errors.any { it.contains("at least one letter") })
        assertTrue(result.errors.any { it.contains("at least one number") })
    }

    // Pattern validation tests - Warning cases
    @Test
    fun `validateTemplatePattern returns warning for short pattern`() {
        val result = TemplateValidationRules.validateTemplatePattern("LLNN")
        assertTrue(result.isValid)
        assertContains(result.warnings, "Template pattern is quite short (4 characters)")
    }

    @Test
    fun `validateTemplatePattern returns warning for long pattern`() {
        val result = TemplateValidationRules.validateTemplatePattern("LLLNNNLLLN")
        assertTrue(result.isValid)
        assertContains(result.warnings, "Template pattern is quite long (10 characters)")
    }

    @Test
    fun `validateTemplatePattern returns warning for high letter to number ratio`() {
        val result = TemplateValidationRules.validateTemplatePattern("LLLLLLN")
        assertTrue(result.isValid)
        assertTrue(result.warnings.any { it.contains("unusually high letter-to-number ratio") })
    }

    @Test
    fun `validateTemplatePattern returns warning for high number to letter ratio`() {
        val result = TemplateValidationRules.validateTemplatePattern("NNNNNNNL")
        assertTrue(result.isValid)
        assertTrue(result.warnings.any { it.contains("unusually high number-to-letter ratio") })
    }

    // Template set validation tests - Happy paths
    @Test
    fun `validateTemplatesForCountry returns valid for single template`() {
        val templates = listOf(
            createTemplate("LLNNLLL", "UK Format", 1)
        )
        val result = TemplateValidationRules.validateTemplatesForCountry(templates)
        assertTrue(result.isValid)
        assertTrue(result.errors.isEmpty())
    }

    @Test
    fun `validateTemplatesForCountry returns valid for two templates with correct priorities`() {
        val templates = listOf(
            createTemplate("NNNNNN", "Primary", 1),
            createTemplate("NNNNNNN", "Secondary", 2)
        )
        val result = TemplateValidationRules.validateTemplatesForCountry(templates)
        assertTrue(result.isValid)
        assertTrue(result.errors.isEmpty())
    }

    // Template set validation tests - Error cases
    @Test
    fun `validateTemplatesForCountry returns invalid for empty list`() {
        val result = TemplateValidationRules.validateTemplatesForCountry(emptyList())
        assertFalse(result.isValid)
        assertContains(result.errors, "At least one template is required per country")
    }

    @Test
    fun `validateTemplatesForCountry returns invalid for too many templates`() {
        val templates = listOf(
            createTemplate("LLNNLLL", "Format 1", 1),
            createTemplate("NNNNNN", "Format 2", 2),
            createTemplate("LLLNNL", "Format 3", 3)
        )
        val result = TemplateValidationRules.validateTemplatesForCountry(templates)
        assertFalse(result.isValid)
        assertContains(result.errors, "Maximum ${TemplateValidationRules.MAX_TEMPLATES_PER_COUNTRY} templates allowed per country")
    }

    @Test
    fun `validateTemplatesForCountry returns invalid for incorrect priorities`() {
        val templates = listOf(
            createTemplate("NNNNNN", "Primary", 1),
            createTemplate("NNNNNNN", "Secondary", 3) // Should be 2
        )
        val result = TemplateValidationRules.validateTemplatesForCountry(templates)
        assertFalse(result.isValid)
        assertContains(result.errors, "Template priorities must be consecutive starting from 1")
    }

    @Test
    fun `validateTemplatesForCountry returns invalid for single template with wrong priority`() {
        val templates = listOf(
            createTemplate("NNNNNN", "Primary", 2) // Should be 1
        )
        val result = TemplateValidationRules.validateTemplatesForCountry(templates)
        assertFalse(result.isValid)
        assertContains(result.errors, "Template priorities must be consecutive starting from 1")
    }

    @Test
    fun `validateTemplatesForCountry returns invalid for duplicate patterns`() {
        val templates = listOf(
            createTemplate("NNNNNN", "Primary", 1),
            createTemplate("NNNNNN", "Secondary", 2) // Duplicate pattern
        )
        val result = TemplateValidationRules.validateTemplatesForCountry(templates)
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.contains("Duplicate template patterns found") })
    }

    @Test
    fun `validateTemplatesForCountry returns invalid for duplicate display names`() {
        val templates = listOf(
            createTemplate("NNNNNN", "Standard", 1),
            createTemplate("NNNNNNN", "Standard", 2) // Duplicate name
        )
        val result = TemplateValidationRules.validateTemplatesForCountry(templates)
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.contains("Duplicate display names found") })
    }

    @Test
    fun `validateTemplatesForCountry returns invalid for templates with invalid patterns`() {
        val templates = listOf(
            createTemplate("", "Empty Pattern", 1), // Invalid pattern
            createTemplate("NNNNNNN", "Valid Pattern", 2)
        )
        val result = TemplateValidationRules.validateTemplatesForCountry(templates)
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.contains("Template 1:") })
    }

    // Template set validation tests - Warning cases
    @Test
    fun `validateTemplatesForCountry returns warning for templates with same length`() {
        val templates = listOf(
            createTemplate("LLLLLL", "Letters", 1),
            createTemplate("NNNNNN", "Numbers", 2) // Same length as first template
        )
        val result = TemplateValidationRules.validateTemplatesForCountry(templates)
        assertTrue(result.isValid)
        assertContains(result.warnings, "Both templates have the same length - consider different lengths for better recognition")
    }

    @Test
    fun `validateTemplatesForCountry returns warning for very similar patterns`() {
        val templates = listOf(
            createTemplate("LLNNLL", "Format 1", 1),
            createTemplate("LLNNLN", "Format 2", 2) // Differs by only 1 character
        )
        val result = TemplateValidationRules.validateTemplatesForCountry(templates)
        assertTrue(result.isValid)
        assertContains(result.warnings, "Templates are very similar - this might cause recognition ambiguity")
    }

    // Plate text validation tests - Happy paths
    @Test
    fun `validatePlateText returns valid for matching plate`() {
        val template = createTemplate("LLNNLLL", "UK Format", 1)
        val result = TemplateValidationRules.validatePlateText("AB12CDE", template)
        assertTrue(result.isValid)
        assertTrue(result.errors.isEmpty())
    }

    @Test
    fun `validatePlateText handles lowercase input`() {
        val template = createTemplate("LLNNLLL", "UK Format", 1)
        val result = TemplateValidationRules.validatePlateText("ab12cde", template)
        assertTrue(result.isValid)
        assertTrue(result.errors.isEmpty())
    }

    @Test
    fun `validatePlateText strips special characters`() {
        val template = createTemplate("LLNNLLL", "UK Format", 1)
        val result = TemplateValidationRules.validatePlateText("AB-12-CDE", template)
        assertTrue(result.isValid)
        assertTrue(result.errors.isEmpty())
    }

    // Plate text validation tests - Error cases
    @Test
    fun `validatePlateText returns invalid for empty text`() {
        val template = createTemplate("LLNNLLL", "UK Format", 1)
        val result = TemplateValidationRules.validatePlateText("", template)
        assertFalse(result.isValid)
        assertContains(result.errors, "Plate text cannot be empty")
    }

    @Test
    fun `validatePlateText returns invalid for wrong length`() {
        val template = createTemplate("LLNNLLL", "UK Format", 1)
        val result = TemplateValidationRules.validatePlateText("AB12CD", template) // Too short
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.contains("length") && it.contains("does not match") })
    }

    @Test
    fun `validatePlateText returns invalid for wrong character types`() {
        val template = createTemplate("LLNNLLL", "UK Format", 1)
        val result = TemplateValidationRules.validatePlateText("1234CDE", template) // Numbers where letters expected
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.contains("expects a letter") })
    }

    @Test
    fun `validatePlateText returns invalid for letters where numbers expected`() {
        val template = createTemplate("LLNNLLL", "UK Format", 1)
        val result = TemplateValidationRules.validatePlateText("ABABCDE", template) // Letters where numbers expected
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.contains("expects a number") })
    }

    @Test
    fun `validatePlateText returns multiple position errors`() {
        val template = createTemplate("LLNNLL", "Test Format", 1)
        val result = TemplateValidationRules.validatePlateText("123456", template) // All wrong types
        assertFalse(result.isValid)
        assertTrue(result.errors.size >= 2) // Should have errors for multiple positions
    }

    // Suggestion generation tests
    @Test
    fun `generateSuggestions returns helpful suggestions for empty pattern`() {
        val suggestions = TemplateValidationRules.generateSuggestions("")
        assertTrue(suggestions.isNotEmpty())
        assertTrue(suggestions.any { it.contains("LLNNLLL") || it.contains("NNNNNN") })
    }

    @Test
    fun `generateSuggestions suggests adding numbers for all-letter pattern`() {
        val suggestions = TemplateValidationRules.generateSuggestions("LLLLLL")
        assertContains(suggestions, "Add some numbers (N) to the pattern")
    }

    @Test
    fun `generateSuggestions suggests adding letters for all-number pattern`() {
        val suggestions = TemplateValidationRules.generateSuggestions("NNNNNN")
        assertContains(suggestions, "Add some letters (L) to the pattern")
    }

    @Test
    fun `generateSuggestions suggests lengthening short pattern`() {
        val suggestions = TemplateValidationRules.generateSuggestions("LN")
        assertContains(suggestions, "Consider making the pattern longer for better uniqueness")
    }

    @Test
    fun `generateSuggestions suggests shortening long pattern`() {
        val longPattern = "L".repeat(TemplateValidationRules.MAX_TEMPLATE_LENGTH + 1)
        val suggestions = TemplateValidationRules.generateSuggestions(longPattern)
        assertContains(suggestions, "Consider making the pattern shorter for better performance")
    }

    // Edge cases and boundary tests
    @Test
    fun `validateTemplatePattern handles boundary lengths correctly`() {
        val minPattern = "L".repeat(TemplateValidationRules.MIN_TEMPLATE_LENGTH - 1) + "N"
        val minResult = TemplateValidationRules.validateTemplatePattern(minPattern)
        assertTrue(minResult.isValid)

        val maxPattern = "L".repeat(TemplateValidationRules.MAX_TEMPLATE_LENGTH - 1) + "N"
        val maxResult = TemplateValidationRules.validateTemplatePattern(maxPattern)
        assertTrue(maxResult.isValid)
    }

    @Test
    fun `validateTemplatePattern handles special character combinations`() {
        val result = TemplateValidationRules.validateTemplatePattern("LL123NN")
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.contains("invalid characters") })
    }

    // Helper function to create test templates
    private fun createTemplate(pattern: String, displayName: String, priority: Int): LicensePlateTemplate {
        return LicensePlateTemplate(
            id = 0,
            countryId = "TEST",
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