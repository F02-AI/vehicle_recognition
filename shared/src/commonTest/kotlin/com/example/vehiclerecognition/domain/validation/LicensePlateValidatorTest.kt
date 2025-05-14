package com.example.vehiclerecognition.domain.validation

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LicensePlateValidatorTest {

    @Test
    fun `isValid returns true for valid NN-NNN-NN format`() {
        assertTrue(LicensePlateValidator.isValid("12-345-67"))
    }

    @Test
    fun `isValid returns true for valid NNN-NN-NNN format`() {
        assertTrue(LicensePlateValidator.isValid("123-45-678"))
    }

    @Test
    fun `isValid returns true for valid N-NNNN-NN format`() {
        assertTrue(LicensePlateValidator.isValid("1-2345-67"))
    }

    @Test
    fun `isValid returns false for invalid format - too few digits`() {
        assertFalse(LicensePlateValidator.isValid("1-234-56"))
    }

    @Test
    fun `isValid returns false for invalid format - too many digits`() {
        assertFalse(LicensePlateValidator.isValid("12-3456-78"))
    }

    @Test
    fun `isValid returns false for invalid format - incorrect hyphen placement`() {
        assertFalse(LicensePlateValidator.isValid("12345-678"))
        assertFalse(LicensePlateValidator.isValid("12-34-567"))
    }

    @Test
    fun `isValid returns false for empty string`() {
        assertFalse(LicensePlateValidator.isValid(""))
    }

    @Test
    fun `isValid returns false for blank string`() {
        assertFalse(LicensePlateValidator.isValid("   "))
    }

    @Test
    fun `isValid returns false for string with letters`() {
        assertFalse(LicensePlateValidator.isValid("AB-CDE-FG"))
        assertFalse(LicensePlateValidator.isValid("12-34F-56"))
    }

    @Test
    fun `isValid returns false for partial match`() {
        assertFalse(LicensePlateValidator.isValid("12-345-678")) // Extra digit at end for NN-NNN-NN
        assertFalse(LicensePlateValidator.isValid("123-45-67"))  // Missing digit at end for NNN-NN-NNN
    }
} 