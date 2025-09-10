package com.example.vehiclerecognition.ml.processors

import com.example.vehiclerecognition.data.models.Country
import com.example.vehiclerecognition.data.models.LicensePlateTemplate
import com.example.vehiclerecognition.domain.service.LicensePlateTemplateService
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Enhanced OCR processor that uses license plate templates to improve recognition accuracy
 * Handles common character confusions (1/I, 0/O, etc.) and applies template-based corrections
 */
@Singleton
class TemplateAwareOcrEnhancer @Inject constructor(
    private val templateService: LicensePlateTemplateService
) {
    
    companion object {
        // Common OCR character confusions (actual -> should be)
        private val COMMON_CONFUSIONS = mapOf(
            // Numbers that look like letters
            '0' to listOf('O', 'Q'),
            '1' to listOf('I', 'l', 'L'),
            '2' to listOf('Z', 'S'),
            '5' to listOf('S'),
            '6' to listOf('G', 'b'),
            '8' to listOf('B'),
            
            // Letters that look like numbers
            'O' to listOf('0', 'Q'),
            'I' to listOf('1', 'l', 'L'),
            'L' to listOf('1', 'I'),
            'S' to listOf('5', '2'),
            'Z' to listOf('2'),
            'G' to listOf('6'),
            'B' to listOf('8'),
            'Q' to listOf('0', 'O'),
            
            // Common letter confusions
            'D' to listOf('O', '0'),
            'P' to listOf('R', 'B'),
            'R' to listOf('P'),
            'U' to listOf('V'),
            'V' to listOf('U'),
            'W' to listOf('V'),
            'M' to listOf('N', 'W'),
            'N' to listOf('M')
        )
        
        // Characters that should be prioritized for specific positions based on templates
        private val LETTER_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
        private val NUMBER_CHARS = "0123456789"
    }
    
    /**
     * Processes raw OCR text and attempts to match it against license plate templates
     * Returns the best matching formatted license plate or null if no valid match found
     */
    suspend fun enhanceOcrResult(rawText: String, country: Country, debugLogger: ((String) -> Unit)? = null): OcrEnhancementResult {
        android.util.Log.d("TemplateAwareOcrEnhancer", "Enhancing OCR: rawText='$rawText', country=${country.displayName}")
        debugLogger?.invoke("Template enhance: '$rawText' (${country.displayName})")
        
        if (rawText.isEmpty()) {
            return OcrEnhancementResult(null, false, emptyList())
        }
        
        val templates = templateService.getTemplatesForCountry(country.isoCode).first()
        android.util.Log.d("TemplateAwareOcrEnhancer", "Found ${templates.size} templates for ${country.displayName}: ${templates.map { it.templatePattern }}")
        debugLogger?.invoke("Found ${templates.size} templates: ${templates.map { it.templatePattern }}")
        
        if (templates.isEmpty()) {
            return OcrEnhancementResult(
                formattedPlate = rawText,
                isValidFormat = false,
                possibleMatches = emptyList(),
                message = "No templates configured for ${country.displayName}"
            )
        }
        
        val cleanedText = cleanOcrText(rawText)
        android.util.Log.d("TemplateAwareOcrEnhancer", "Cleaned text: '$cleanedText'")
        
        val allPossibleTexts = generatePossibleTexts(cleanedText)
        android.util.Log.d("TemplateAwareOcrEnhancer", "Generated ${allPossibleTexts.size} possible texts: ${allPossibleTexts.take(5)}")
        
        // Try to match against each template
        val matches = mutableListOf<TemplateMatch>()
        
        for (template in templates.sortedBy { it.priority }) {
            android.util.Log.d("TemplateAwareOcrEnhancer", "=== TRYING TEMPLATE: ${template.displayName} (${template.templatePattern}) ===")
            for (possibleText in allPossibleTexts) {
                android.util.Log.d("TemplateAwareOcrEnhancer", "Trying text: '$possibleText' against template: ${template.templatePattern}")
                val match = attemptTemplateMatch(possibleText, template)
                if (match != null) {
                    android.util.Log.d("TemplateAwareOcrEnhancer", "✓ Match found: '$possibleText' -> '${match.formattedText}' (confidence: ${match.confidence}) [${template.displayName}]")
                    matches.add(match)
                } else {
                    android.util.Log.d("TemplateAwareOcrEnhancer", "✗ No match for: '$possibleText' against ${template.templatePattern}")
                }
            }
            android.util.Log.d("TemplateAwareOcrEnhancer", "Template ${template.displayName} found ${matches.count { it.template == template }} matches")
        }
        
        // Return the best match (prioritize longer matches, then confidence, then template priority)
        val bestMatch = matches.maxWithOrNull(
            compareBy<TemplateMatch> { it.formattedText.length }.thenBy { it.confidence }.thenBy { -it.template.priority }
        )
        
        android.util.Log.d("TemplateAwareOcrEnhancer", "=== ALL TEMPLATE MATCHES ===")
        android.util.Log.d("TemplateAwareOcrEnhancer", "Found ${matches.size} total matches:")
        matches.forEach { match ->
            android.util.Log.d("TemplateAwareOcrEnhancer", "  ${match.template.displayName} (${match.template.templatePattern}): '${match.formattedText}' (len=${match.formattedText.length}, conf=${match.confidence})")
        }
        android.util.Log.d("TemplateAwareOcrEnhancer", "Best match: ${bestMatch?.let { "${it.formattedText} (confidence: ${it.confidence}, template: ${it.template.displayName})" } ?: "none"}")
        
        return if (bestMatch != null && bestMatch.confidence > 0.7f) {
            android.util.Log.d("TemplateAwareOcrEnhancer", "Using best match: ${bestMatch.formattedText}")
            
            val possibleMatchesList = matches.take(3).map { "${it.formattedText} (${it.confidence})" }
            android.util.Log.d("TemplateAwareOcrEnhancer", "=== CREATING OCR ENHANCEMENT RESULT ===")
            android.util.Log.d("TemplateAwareOcrEnhancer", "Best match: ${bestMatch.formattedText} from template ${bestMatch.template.displayName}")
            android.util.Log.d("TemplateAwareOcrEnhancer", "All matches used for possibleMatches:")
            matches.take(3).forEach { match ->
                android.util.Log.d("TemplateAwareOcrEnhancer", "  - ${match.formattedText} (${match.confidence}) from ${match.template.displayName}")
            }
            android.util.Log.d("TemplateAwareOcrEnhancer", "Final possibleMatches list: $possibleMatchesList")
            
            OcrEnhancementResult(
                formattedPlate = bestMatch.formattedText,
                isValidFormat = true,
                possibleMatches = possibleMatchesList,
                message = "Matched template: ${bestMatch.template.displayName}"
            )
        } else {
            android.util.Log.d("TemplateAwareOcrEnhancer", "No good match found, using cleaned text: '$cleanedText'")
            // No good template match, return cleaned text
            OcrEnhancementResult(
                formattedPlate = cleanedText,
                isValidFormat = false,
                possibleMatches = matches.take(3).map { "${it.formattedText} (${it.confidence})" },
                message = if (matches.isEmpty()) {
                    "No template matches found"
                } else {
                    "Low confidence matches only"
                }
            )
        }
    }
    
    /**
     * Cleans raw OCR text by removing spaces, dashes, and normalizing characters
     */
    private fun cleanOcrText(rawText: String): String {
        return rawText.replace(Regex("[^A-Z0-9]"), "").uppercase()
    }
    
    /**
     * Generates all possible variations of the input text by applying character confusion rules
     */
    private fun generatePossibleTexts(cleanedText: String): List<String> {
        if (cleanedText.length > 10) {
            // Too long, only generate a few variations to avoid exponential explosion
            return listOf(cleanedText, applyCommonCorrections(cleanedText))
        }
        
        val variations = mutableSetOf<String>()
        variations.add(cleanedText)
        
        // Generate variations by substituting confused characters
        generateVariationsRecursive(cleanedText.toCharArray(), 0, variations)
        
        // Limit to top 50 variations to avoid performance issues
        return variations.take(50)
    }
    
    /**
     * Recursively generates character variations using common confusion mappings
     */
    private fun generateVariationsRecursive(
        chars: CharArray, 
        index: Int, 
        variations: MutableSet<String>
    ) {
        if (index >= chars.size) {
            variations.add(String(chars))
            return
        }
        
        val currentChar = chars[index]
        
        // Add variation with current character unchanged
        generateVariationsRecursive(chars, index + 1, variations)
        
        // Add variations with confused characters
        COMMON_CONFUSIONS[currentChar]?.forEach { confusedChar ->
            if (variations.size < 100) { // Limit variations
                chars[index] = confusedChar
                generateVariationsRecursive(chars, index + 1, variations)
                chars[index] = currentChar // restore
            }
        }
    }
    
    /**
     * Applies common OCR corrections without template matching
     */
    private fun applyCommonCorrections(text: String): String {
        var corrected = text
        
        // Apply some common heuristics
        corrected = corrected.replace("l", "1") // lowercase l often mistaken for 1
        corrected = corrected.replace("o", "0") // lowercase o often mistaken for 0
        
        return corrected
    }
    
    /**
     * Attempts to match text against a specific template pattern
     */
    private fun attemptTemplateMatch(text: String, template: LicensePlateTemplate): TemplateMatch? {
        val pattern = template.templatePattern
        
        android.util.Log.d("TemplateAwareOcrEnhancer", "=== TEMPLATE MATCH ATTEMPT ===")
        android.util.Log.d("TemplateAwareOcrEnhancer", "Attempting match: text='$text' (${text.length}) vs pattern='$pattern' (${pattern.length}) template='${template.displayName}'")
        
        if (text.length != pattern.length) {
            // Try substring matching for longer text
            if (text.length > pattern.length) {
                android.util.Log.d("TemplateAwareOcrEnhancer", "Text longer than pattern, trying substring matching")
                return findBestSubstringMatch(text, template)
            }
            android.util.Log.d("TemplateAwareOcrEnhancer", "Text length mismatch, skipping (text: ${text.length}, pattern: ${pattern.length})")
            return null
        }
        
        var confidence = 0f
        val formattedChars = mutableListOf<Char>()
        
        android.util.Log.d("TemplateAwareOcrEnhancer", "Character-by-character matching: text='$text' vs pattern='$pattern'")
        
        for (i in pattern.indices) {
            val expectedType = pattern[i]
            val actualChar = text[i]
            
            android.util.Log.d("TemplateAwareOcrEnhancer", "  Position $i: expecting '$expectedType', got '$actualChar'")
            
            when (expectedType) {
                'L' -> {
                    if (actualChar in LETTER_CHARS) {
                        confidence += 1f
                        formattedChars.add(actualChar)
                    } else if (actualChar in NUMBER_CHARS) {
                        // Try to convert number to letter
                        val convertedChar = convertNumberToLetter(actualChar)
                        if (convertedChar != null) {
                            confidence += 0.8f
                            formattedChars.add(convertedChar)
                        } else {
                            confidence += 0.3f
                            formattedChars.add(actualChar)
                        }
                    } else {
                        formattedChars.add(actualChar)
                    }
                }
                'N' -> {
                    if (actualChar in NUMBER_CHARS) {
                        confidence += 1f
                        formattedChars.add(actualChar)
                    } else if (actualChar in LETTER_CHARS) {
                        // Try to convert letter to number
                        val convertedChar = convertLetterToNumber(actualChar)
                        if (convertedChar != null) {
                            confidence += 0.8f
                            formattedChars.add(convertedChar)
                        } else {
                            confidence += 0.3f
                            formattedChars.add(actualChar)
                        }
                    } else {
                        formattedChars.add(actualChar)
                    }
                }
                else -> {
                    // Unknown pattern character, accept as-is
                    formattedChars.add(actualChar)
                }
            }
        }
        
        val normalizedConfidence = confidence / pattern.length
        val formattedResult = String(formattedChars.toCharArray())
        
        android.util.Log.d("TemplateAwareOcrEnhancer", "Match result: confidence=$confidence/$pattern.length=$normalizedConfidence, formatted='$formattedResult'")
        
        return if (normalizedConfidence > 0.5f) {
            android.util.Log.d("TemplateAwareOcrEnhancer", "✓ Match accepted (confidence > 0.5)")
            TemplateMatch(
                template = template,
                formattedText = formattedResult,
                confidence = normalizedConfidence
            )
        } else {
            android.util.Log.d("TemplateAwareOcrEnhancer", "✗ Match rejected (confidence $normalizedConfidence <= 0.5)")
            null
        }
    }
    
    /**
     * Finds the best substring match for text longer than template
     */
    private fun findBestSubstringMatch(text: String, template: LicensePlateTemplate): TemplateMatch? {
        val pattern = template.templatePattern
        var bestMatch: TemplateMatch? = null
        
        for (i in 0..(text.length - pattern.length)) {
            val substring = text.substring(i, i + pattern.length)
            val match = attemptTemplateMatch(substring, template)
            
            if (match != null && (bestMatch == null || match.confidence > bestMatch.confidence)) {
                bestMatch = match
            }
        }
        
        return bestMatch
    }
    
    /**
     * Converts a number character to its most likely letter equivalent
     */
    private fun convertNumberToLetter(number: Char): Char? {
        return when (number) {
            '0' -> 'O'
            '1' -> 'I'
            '5' -> 'S'
            '6' -> 'G'
            '8' -> 'B'
            else -> null
        }
    }
    
    /**
     * Converts a letter character to its most likely number equivalent
     */
    private fun convertLetterToNumber(letter: Char): Char? {
        return when (letter) {
            'O', 'Q' -> '0'
            'I', 'L' -> '1'
            'S' -> '5'
            'G' -> '6'
            'B' -> '8'
            'Z' -> '2'
            else -> null
        }
    }
    
    /**
     * Checks if the country has configured templates
     */
    suspend fun hasConfiguredTemplates(country: Country): Boolean {
        return templateService.getTemplatesForCountry(country.isoCode).first().isNotEmpty()
    }
    
    /**
     * Gets template information for display
     */
    suspend fun getTemplateInfo(country: Country): List<String> {
        val templates = templateService.getTemplatesForCountry(country.isoCode).first()
        return templates.sortedBy { it.priority }.map { 
            "${it.displayName}: ${it.templatePattern} (${it.description})"
        }
    }
    
    /**
     * Validates user input directly against templates without OCR corrections
     * This is for manual entry validation, not OCR processing
     */
    suspend fun validateUserInput(userInput: String, country: Country): UserInputValidationResult {
        if (userInput.isEmpty()) {
            return UserInputValidationResult(false, null, "Input cannot be empty")
        }
        
        val templates = templateService.getTemplatesForCountry(country.isoCode).first()
        if (templates.isEmpty()) {
            return UserInputValidationResult(
                isValid = false,
                matchedTemplate = null,
                message = "No templates configured for ${country.displayName}"
            )
        }
        
        val cleanedInput = userInput // Assuming input is already cleaned and uppercase
        
        // Try exact template matching without OCR corrections
        for (template in templates.sortedBy { it.priority }) {
            val match = validateAgainstTemplate(cleanedInput, template)
            if (match != null) {
                return UserInputValidationResult(
                    isValid = true,
                    matchedTemplate = template,
                    message = "Valid format for ${template.displayName}"
                )
            }
        }
        
        // No template matched
        val templateInfo = templates.sortedBy { it.priority }.map { 
            "${it.displayName}: ${it.templatePattern}"
        }.joinToString(", ")
        
        return UserInputValidationResult(
            isValid = false,
            matchedTemplate = null,
            message = "Invalid format. Expected: $templateInfo"
        )
    }
    
    /**
     * Validates input against a template without OCR character corrections
     * Only checks exact letter/number matching
     */
    private fun validateAgainstTemplate(input: String, template: LicensePlateTemplate): LicensePlateTemplate? {
        val pattern = template.templatePattern
        
        if (input.length != pattern.length) {
            return null
        }
        
        for (i in pattern.indices) {
            val expectedType = pattern[i]
            val actualChar = input[i]
            
            when (expectedType) {
                'L' -> {
                    if (actualChar !in LETTER_CHARS) {
                        return null // Must be exact letter, no conversions
                    }
                }
                'N' -> {
                    if (actualChar !in NUMBER_CHARS) {
                        return null // Must be exact number, no conversions
                    }
                }
                else -> {
                    // Unknown pattern character, skip validation
                }
            }
        }
        
        return template // All characters matched exactly
    }
}

/**
 * Result of OCR enhancement processing
 */
data class OcrEnhancementResult(
    val formattedPlate: String?,
    val isValidFormat: Boolean,
    val possibleMatches: List<String>,
    val message: String = ""
)

/**
 * Represents a potential template match
 */
data class TemplateMatch(
    val template: LicensePlateTemplate,
    val formattedText: String,
    val confidence: Float
)

/**
 * Result of user input validation (without OCR corrections)
 */
data class UserInputValidationResult(
    val isValid: Boolean,
    val matchedTemplate: LicensePlateTemplate?,
    val message: String
)