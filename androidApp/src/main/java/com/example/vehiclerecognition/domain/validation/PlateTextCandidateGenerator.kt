package com.example.vehiclerecognition.domain.validation

import com.example.vehiclerecognition.data.models.Country

/**
 * Generates robust candidate license plate strings from raw OCR text by correcting
 * visually ambiguous characters according to provided format patterns.
 *
 * The generator is format-aware: it only proposes substitutions that satisfy the
 * character type required at each position (letters for 'L', digits for 'N').
 *
 * Examples of supported ambiguities:
 *  - 0 ↔ O/D/Q
 *  - 1 ↔ I/L/T
 *  - 2 ↔ Z
 *  - 3 ↔ E
 *  - 4 ↔ A
 *  - 5 ↔ S
 *  - 6 ↔ G
 *  - 7 ↔ T
 *  - 8 ↔ B
 *
 * The output candidates are formatted according to the pattern, including any dashes.
 */
object PlateTextCandidateGenerator {

    /** Map digits to visually similar letters. */
    private val digitToLetters: Map<Char, List<Char>> = mapOf(
        '0' to listOf('O', 'D', 'Q'),
        '1' to listOf('I', 'L', 'T'),
        '2' to listOf('Z'),
        '3' to listOf('E'),
        '4' to listOf('A'),
        '5' to listOf('S'),
        '6' to listOf('G'),
        '7' to listOf('T'),
        '8' to listOf('B')
        // '9' is intentionally omitted to avoid excessive false positives
    )

    /** Map letters to visually similar digits. */
    private val letterToDigits: Map<Char, List<Char>> = mapOf(
        'O' to listOf('0'),
        'D' to listOf('0'),
        'Q' to listOf('0'),
        'I' to listOf('1'),
        'L' to listOf('1'),
        'T' to listOf('1', '7'),
        'Z' to listOf('2'),
        'E' to listOf('3'),
        'A' to listOf('4'),
        'S' to listOf('5'),
        'G' to listOf('6'),
        'B' to listOf('8'),
        // Conservative mapping for 'P' to '9' is omitted to reduce false positives
    )

    /**
     * Returns canonical patterns for a given country.
     * Patterns use 'L' for letters, 'N' for digits, and '-' for a fixed dash.
     */
    fun getPatternsForCountry(country: Country): List<String> = when (country) {
        Country.ISRAEL -> listOf(
            "NN-NNN-NN",
            "NNN-NN-NNN",
            "N-NNNN-NN",
            // Dashless variants to support inputs without separators
            "NNNNNNN",
            "NNNNNNNN"
        )
        Country.UK -> listOf(
            "LLNN-LLL",
            // Some inputs/lists omit the dash
            "LLNNLLL"
        )
        else -> listOf(
            // Default to Israeli patterns for all other countries
            "NN-NNN-NN",
            "NNN-NN-NNN",
            "N-NNNN-NN",
            // Dashless variants to support inputs without separators
            "NNNNNNN",
            "NNNNNNNN"
        )
    }

    /**
     * Generates all candidate strings for the given OCR text that satisfy any of the provided patterns.
     *
     * - Only cross-type substitutions are performed (digits→letters for 'L' positions, letters→digits for 'N' positions).
     * - Non alphanumeric characters in the OCR text are ignored during alignment.
     * - Candidates include dashes as dictated by the pattern.
     * - Generation is capped to avoid combinatorial explosion.
     */
    fun generateCandidates(
        ocrText: String,
        patterns: List<String>,
        maxCandidatesPerPattern: Int = 2048
    ): Set<String> {
        if (ocrText.isBlank() || patterns.isEmpty()) return emptySet()

        val alnum = ocrText.uppercase().replace(Regex("[^A-Z0-9]"), "")
        if (alnum.isEmpty()) return emptySet()

        val candidates = LinkedHashSet<String>()

        for (pattern in patterns) {
            // Build the type mask by removing fixed characters
            val typeMask = pattern.filter { it == 'L' || it == 'N' }
            if (typeMask.length != alnum.length) continue

            // Build per-position option lists
            val perPositionOptions: MutableList<List<Char>> = ArrayList(typeMask.length)
            var hasInvalidPosition = false

            for (i in typeMask.indices) {
                val typeChar = typeMask[i]
                val observed = alnum[i]

                val options: List<Char> = when (typeChar) {
                    'L' -> {
                        if (observed in 'A'..'Z') listOf(observed)
                        else if (observed in '0'..'9') digitToLetters[observed] ?: emptyList()
                        else emptyList()
                    }
                    'N' -> {
                        if (observed in '0'..'9') listOf(observed)
                        else if (observed in 'A'..'Z') letterToDigits[observed] ?: emptyList()
                        else emptyList()
                    }
                    else -> emptyList()
                }

                if (options.isEmpty()) {
                    hasInvalidPosition = true
                    break
                }
                perPositionOptions.add(options)
            }

            if (hasInvalidPosition) continue

            // Generate Cartesian product with cap
            val rawVariants = mutableListOf<StringBuilder>()
            rawVariants.add(StringBuilder())

            for (posOptions in perPositionOptions) {
                val nextVariants = mutableListOf<StringBuilder>()
                for (variant in rawVariants) {
                    for (opt in posOptions) {
                        if (nextVariants.size >= maxCandidatesPerPattern) break
                        val sb = StringBuilder(variant)
                        sb.append(opt)
                        nextVariants.add(sb)
                    }
                    if (nextVariants.size >= maxCandidatesPerPattern) break
                }
                rawVariants.clear()
                rawVariants.addAll(nextVariants)
                if (rawVariants.size >= maxCandidatesPerPattern) break
            }

            // Insert fixed characters per pattern (e.g., '-')
            for (variant in rawVariants) {
                val withFormat = StringBuilder()
                var idx = 0
                for (pc in pattern) {
                    when (pc) {
                        'L', 'N' -> {
                            withFormat.append(variant[idx])
                            idx += 1
                        }
                        else -> withFormat.append(pc)
                    }
                }
                candidates.add(withFormat.toString())
                if (candidates.size >= maxCandidatesPerPattern) break
            }
            if (candidates.size >= maxCandidatesPerPattern) break
        }

        return candidates
    }
}


