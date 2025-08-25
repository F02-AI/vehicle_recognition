package com.example.vehiclerecognition.domain.validation

import com.example.vehiclerecognition.data.models.LicensePlateTemplate

/**
 * Comprehensive validation rules for license plate templates
 */
object TemplateValidationRules {
    
    // Constants for validation
    const val MIN_TEMPLATE_LENGTH = 3
    const val MAX_TEMPLATE_LENGTH = 12
    const val MAX_TEMPLATES_PER_COUNTRY = 2
    
    /**
     * Validates template pattern format and business rules
     */
    fun validateTemplatePattern(pattern: String): ValidationResult {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        
        // Basic format validation
        if (pattern.isEmpty()) {
            errors.add("Template pattern cannot be empty")
        } else {
            if (pattern.length < MIN_TEMPLATE_LENGTH) {
                errors.add("Template pattern must be at least $MIN_TEMPLATE_LENGTH characters")
            }
            
            if (pattern.length > MAX_TEMPLATE_LENGTH) {
                errors.add("Template pattern cannot exceed $MAX_TEMPLATE_LENGTH characters")
            }
            
            // Character validation
            val invalidChars = pattern.filter { it != 'L' && it != 'N' }
            if (invalidChars.isNotEmpty()) {
                errors.add("Template pattern contains invalid characters: ${invalidChars.toSet().joinToString(", ")}")
            }
            
            // Must contain both letters and numbers
            if (!pattern.contains('L')) {
                errors.add("Template pattern must contain at least one letter (L)")
            }
            
            if (!pattern.contains('N')) {
                errors.add("Template pattern must contain at least one number (N)")
            }
            
            // Business rule warnings
            if (pattern.length < 5) {
                warnings.add("Template pattern is quite short (${pattern.length} characters)")
            }
            
            if (pattern.length > 10) {
                warnings.add("Template pattern is quite long (${pattern.length} characters)")
            }
            
            // Pattern complexity warnings
            val letterCount = pattern.count { it == 'L' }
            val numberCount = pattern.count { it == 'N' }
            
            if (letterCount > numberCount * 2) {
                warnings.add("Pattern has unusually high letter-to-number ratio ($letterCount:$numberCount)")
            }
            
            if (numberCount > letterCount * 2) {
                warnings.add("Pattern has unusually high number-to-letter ratio ($numberCount:$letterCount)")
            }
        }
        
        return ValidationResult(errors.isEmpty(), errors, warnings)
    }
    
    /**
     * Validates a set of templates for a country
     */
    fun validateTemplatesForCountry(templates: List<LicensePlateTemplate>): ValidationResult {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        
        // Count validation
        if (templates.isEmpty()) {
            errors.add("At least one template is required per country")
            return ValidationResult(false, errors, warnings)
        }
        
        if (templates.size > MAX_TEMPLATES_PER_COUNTRY) {
            errors.add("Maximum $MAX_TEMPLATES_PER_COUNTRY templates allowed per country")
        }
        
        // Priority validation
        val priorities = templates.map { it.priority }.sorted()
        val expectedPriorities = (1..templates.size).toList()
        
        if (priorities != expectedPriorities) {
            errors.add("Template priorities must be consecutive starting from 1")
        }
        
        // Duplicate validation
        val patterns = templates.map { it.templatePattern }
        val duplicatePatterns = patterns.groupBy { it }.filter { it.value.size > 1 }.keys
        if (duplicatePatterns.isNotEmpty()) {
            errors.add("Duplicate template patterns found: ${duplicatePatterns.joinToString(", ")}")
        }
        
        val displayNames = templates.map { it.displayName }
        val duplicateNames = displayNames.groupBy { it }.filter { it.value.size > 1 }.keys
        if (duplicateNames.isNotEmpty()) {
            errors.add("Duplicate display names found: ${duplicateNames.joinToString(", ")}")
        }
        
        // Individual template validation
        templates.forEachIndexed { index, template ->
            val templateValidation = validateTemplatePattern(template.templatePattern)
            if (!templateValidation.isValid) {
                errors.add("Template ${index + 1}: ${templateValidation.errors.joinToString("; ")}")
            }
            warnings.addAll(templateValidation.warnings.map { "Template ${index + 1}: $it" })
        }
        
        // Business logic warnings
        if (templates.size == 2) {
            val template1 = templates.find { it.priority == 1 }
            val template2 = templates.find { it.priority == 2 }
            
            if (template1 != null && template2 != null) {
                if (template1.templatePattern.length == template2.templatePattern.length) {
                    warnings.add("Both templates have the same length - consider different lengths for better recognition")
                }
                
                if (arePatternsVerySimilar(template1.templatePattern, template2.templatePattern)) {
                    warnings.add("Templates are very similar - this might cause recognition ambiguity")
                }
            }
        }
        
        return ValidationResult(errors.isEmpty(), errors, warnings)
    }
    
    /**
     * Validates plate text against template requirements
     */
    fun validatePlateText(plateText: String, template: LicensePlateTemplate): ValidationResult {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        
        val cleanText = plateText.replace(Regex("[^A-Z0-9]"), "").uppercase()
        
        if (cleanText.isEmpty()) {
            errors.add("Plate text cannot be empty")
            return ValidationResult(false, errors, warnings)
        }
        
        if (cleanText.length != template.templatePattern.length) {
            errors.add("Plate text length (${cleanText.length}) does not match template length (${template.templatePattern.length})")
        } else {
            // Character type validation
            for (i in cleanText.indices) {
                val char = cleanText[i]
                val expectedType = template.templatePattern[i]
                
                when (expectedType) {
                    'L' -> if (!char.isLetter()) {
                        errors.add("Position ${i + 1} expects a letter but got '$char'")
                    }
                    'N' -> if (!char.isDigit()) {
                        errors.add("Position ${i + 1} expects a number but got '$char'")
                    }
                }
            }
        }
        
        return ValidationResult(errors.isEmpty(), errors, warnings)
    }
    
    /**
     * Checks if two patterns are very similar (might cause confusion)
     */
    private fun arePatternsVerySimilar(pattern1: String, pattern2: String): Boolean {
        if (pattern1.length != pattern2.length) return false
        
        var differences = 0
        for (i in pattern1.indices) {
            if (pattern1[i] != pattern2[i]) {
                differences++
            }
        }
        
        // Consider patterns similar if they differ by only 1 character
        return differences <= 1
    }
    
    /**
     * Generates validation suggestions for common issues
     */
    fun generateSuggestions(pattern: String): List<String> {
        val suggestions = mutableListOf<String>()
        
        if (pattern.isEmpty()) {
            suggestions.add("Try patterns like 'LLNNLLL' for UK plates or 'NNNNNN' for Israeli plates")
            return suggestions
        }
        
        if (pattern.all { it == 'L' }) {
            suggestions.add("Add some numbers (N) to the pattern")
        }
        
        if (pattern.all { it == 'N' }) {
            suggestions.add("Add some letters (L) to the pattern")
        }
        
        if (pattern.length < MIN_TEMPLATE_LENGTH) {
            suggestions.add("Consider making the pattern longer for better uniqueness")
        }
        
        if (pattern.length > MAX_TEMPLATE_LENGTH) {
            suggestions.add("Consider making the pattern shorter for better performance")
        }
        
        return suggestions
    }
}

/**
 * Result of validation with errors and warnings
 */
data class ValidationResult(
    val isValid: Boolean,
    val errors: List<String> = emptyList(),
    val warnings: List<String> = emptyList()
)