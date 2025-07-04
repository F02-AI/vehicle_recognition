package com.example.vehiclerecognition.domain.logic

import com.example.vehiclerecognition.domain.repository.WatchlistRepository
import com.example.vehiclerecognition.domain.validation.LicensePlateValidator
import com.example.vehiclerecognition.model.DetectionMode
import com.example.vehiclerecognition.model.VehicleColor
import com.example.vehiclerecognition.model.VehicleType
import com.example.vehiclerecognition.model.WatchlistEntry
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// Mock implementation for testing
class MockWatchlistRepository(private val entries: MutableList<WatchlistEntry> = mutableListOf()) : WatchlistRepository {
    override suspend fun addEntry(entry: WatchlistEntry): Boolean {
        if (entries.any { it.licensePlate == entry.licensePlate }) return false
        return entries.add(entry)
    }
    override suspend fun deleteEntry(licensePlate: String): Boolean = entries.removeIf { it.licensePlate == licensePlate }
    override suspend fun deleteEntryByColorAndType(color: VehicleColor, type: VehicleType): Boolean = 
        entries.removeIf { it.vehicleColor == color && it.vehicleType == type && it.licensePlate == null }
    override suspend fun getAllEntries(): List<WatchlistEntry> = entries.toList()
    override suspend fun findEntryByLicensePlate(licensePlate: String): WatchlistEntry? = entries.find { it.licensePlate == licensePlate }
}

class VehicleMatcherTest {

    private val validator = LicensePlateValidator // Real validator

    @Test
    fun `findMatch returns true for LP match with valid LP format`() = runBlocking {
        val repo = MockWatchlistRepository()
        repo.addEntry(WatchlistEntry("12-345-67", VehicleType.CAR, VehicleColor.BLUE))
        val matcher = VehicleMatcher(repo, validator)
        val detected = VehicleMatcher.DetectedVehicle(licensePlate = "12-345-67")
        assertTrue(matcher.findMatch(detected, DetectionMode.LP))
    }

    @Test
    fun `findMatch returns false for LP match with invalid detected LP format`() = runBlocking {
        val repo = MockWatchlistRepository()
        repo.addEntry(WatchlistEntry("12-345-67", VehicleType.CAR, VehicleColor.BLUE))
        val matcher = VehicleMatcher(repo, validator)
        val detected = VehicleMatcher.DetectedVehicle(licensePlate = "12-345") // Invalid - only 5 digits
        assertFalse(matcher.findMatch(detected, DetectionMode.LP))
    }

    @Test
    fun `findMatch returns false when watchlist is empty`() = runBlocking {
        val repo = MockWatchlistRepository() // Empty
        val matcher = VehicleMatcher(repo, validator)
        val detected = VehicleMatcher.DetectedVehicle(licensePlate = "12-345-67")
        assertFalse(matcher.findMatch(detected, DetectionMode.LP))
    }

    @Test
    fun `findMatch returns true for LP_COLOR match`() = runBlocking {
        val repo = MockWatchlistRepository()
        repo.addEntry(WatchlistEntry("11-111-11", VehicleType.MOTORCYCLE, VehicleColor.RED))
        val matcher = VehicleMatcher(repo, validator)
        val detected = VehicleMatcher.DetectedVehicle(licensePlate = "11-111-11", color = VehicleColor.RED)
        assertTrue(matcher.findMatch(detected, DetectionMode.LP_COLOR))
    }

    @Test
    fun `findMatch returns false for LP_COLOR mismatch on color`() = runBlocking {
        val repo = MockWatchlistRepository()
        repo.addEntry(WatchlistEntry("11-111-11", VehicleType.MOTORCYCLE, VehicleColor.RED))
        val matcher = VehicleMatcher(repo, validator)
        val detected = VehicleMatcher.DetectedVehicle(licensePlate = "11-111-11", color = VehicleColor.BLUE)
        assertFalse(matcher.findMatch(detected, DetectionMode.LP_COLOR))
    }

    @Test
    fun `findMatch returns true for LP_COLOR_TYPE match`() = runBlocking {
        val repo = MockWatchlistRepository()
        repo.addEntry(WatchlistEntry("22-222-22", VehicleType.TRUCK, VehicleColor.BLACK))
        val matcher = VehicleMatcher(repo, validator)
        val detected = VehicleMatcher.DetectedVehicle(
            licensePlate = "22-222-22", 
            color = VehicleColor.BLACK,
            type = VehicleType.TRUCK
        )
        assertTrue(matcher.findMatch(detected, DetectionMode.LP_COLOR_TYPE))
    }

    @Test
    fun `findMatch returns false if detected LP is null for LP-dependent mode`() = runBlocking {
        val repo = MockWatchlistRepository()
        repo.addEntry(WatchlistEntry("12-345-67", VehicleType.CAR, VehicleColor.BLUE))
        val matcher = VehicleMatcher(repo, validator)
        val detected = VehicleMatcher.DetectedVehicle(licensePlate = null, color = VehicleColor.BLUE)
        assertFalse(matcher.findMatch(detected, DetectionMode.LP_COLOR))
    }

    @Test
    fun `findMatch returns true for COLOR_TYPE match, ignoring LP`() = runBlocking {
        val repo = MockWatchlistRepository()
        repo.addEntry(WatchlistEntry("99-999-99", VehicleType.CAR, VehicleColor.WHITE))
        val matcher = VehicleMatcher(repo, validator)
        // Detected LP is invalid, but mode doesn't use it
        val detected = VehicleMatcher.DetectedVehicle(licensePlate = "INVALID-LP", color = VehicleColor.WHITE, type = VehicleType.CAR)
        assertTrue(matcher.findMatch(detected, DetectionMode.COLOR_TYPE))
    }

    @Test
    fun `findMatch returns true for COLOR match, ignoring LP and Type`() = runBlocking {
        val repo = MockWatchlistRepository()
        repo.addEntry(WatchlistEntry("88-888-88", VehicleType.TRUCK, VehicleColor.GREEN))
        val matcher = VehicleMatcher(repo, validator)
        val detected = VehicleMatcher.DetectedVehicle(color = VehicleColor.GREEN)
        assertTrue(matcher.findMatch(detected, DetectionMode.COLOR))
    }

    // New tests for numeric-only matching (excluding dashes)
    @Test
    fun `findMatch returns true for LP match with different formatting but same digits`() = runBlocking {
        val repo = MockWatchlistRepository()
        repo.addEntry(WatchlistEntry("12-345-67", VehicleType.CAR, VehicleColor.BLUE))
        val matcher = VehicleMatcher(repo, validator)
        // Detected plate has different format but same digits (1234567)
        val detected = VehicleMatcher.DetectedVehicle(licensePlate = "123-4567")
        assertTrue(matcher.findMatch(detected, DetectionMode.LP))
    }

    @Test
    fun `findMatch returns true for LP match ignoring dashes and spaces`() = runBlocking {
        val repo = MockWatchlistRepository()
        repo.addEntry(WatchlistEntry("12-345-67", VehicleType.CAR, VehicleColor.BLUE))
        val matcher = VehicleMatcher(repo, validator)
        // Detected plate with spaces instead of dashes
        val detected = VehicleMatcher.DetectedVehicle(licensePlate = "12 345 67")
        assertTrue(matcher.findMatch(detected, DetectionMode.LP))
    }

    @Test
    fun `findMatch returns true for LP match with numeric-only detected plate`() = runBlocking {
        val repo = MockWatchlistRepository()
        repo.addEntry(WatchlistEntry("12-345-67", VehicleType.CAR, VehicleColor.BLUE))
        val matcher = VehicleMatcher(repo, validator)
        // Detected plate with no formatting, just digits
        val detected = VehicleMatcher.DetectedVehicle(licensePlate = "1234567")
        assertTrue(matcher.findMatch(detected, DetectionMode.LP))
    }

    @Test
    fun `findMatch returns false for LP match with different digits despite similar formatting`() = runBlocking {
        val repo = MockWatchlistRepository()
        repo.addEntry(WatchlistEntry("12-345-67", VehicleType.CAR, VehicleColor.BLUE))
        val matcher = VehicleMatcher(repo, validator)
        // Different digits, same format
        val detected = VehicleMatcher.DetectedVehicle(licensePlate = "12-345-68")
        assertFalse(matcher.findMatch(detected, DetectionMode.LP))
    }

    @Test
    fun `findMatch returns true for LP_COLOR match with different formatting but same digits`() = runBlocking {
        val repo = MockWatchlistRepository()
        repo.addEntry(WatchlistEntry("11-111-11", VehicleType.MOTORCYCLE, VehicleColor.RED))
        val matcher = VehicleMatcher(repo, validator)
        // Different format, same digits and color
        val detected = VehicleMatcher.DetectedVehicle(licensePlate = "1111111", color = VehicleColor.RED)
        assertTrue(matcher.findMatch(detected, DetectionMode.LP_COLOR))
    }

    @Test
    fun `findMatch returns false for empty digits in license plate`() = runBlocking {
        val repo = MockWatchlistRepository()
        repo.addEntry(WatchlistEntry("12-345-67", VehicleType.CAR, VehicleColor.BLUE))
        val matcher = VehicleMatcher(repo, validator)
        // Plate with no digits (only dashes)
        val detected = VehicleMatcher.DetectedVehicle(licensePlate = "---")
        assertFalse(matcher.findMatch(detected, DetectionMode.LP))
    }
} 