package com.example.vehiclerecognition.data.repository

import com.example.vehiclerecognition.data.db.WatchlistDao
import com.example.vehiclerecognition.data.db.WatchlistEntryEntity
import com.example.vehiclerecognition.data.db.toEntity
import com.example.vehiclerecognition.domain.repository.WatchlistRepository
import com.example.vehiclerecognition.domain.validation.LicensePlateValidator // FR 1.6
import com.example.vehiclerecognition.model.WatchlistEntry
import com.example.vehiclerecognition.model.VehicleColor
import com.example.vehiclerecognition.model.VehicleType

class AndroidWatchlistRepository(
    private val watchlistDao: WatchlistDao,
    private val licensePlateValidator: LicensePlateValidator // For FR 1.6
) : WatchlistRepository {

    override suspend fun addEntry(entry: WatchlistEntry): Boolean {
        // FR 1.6: Input for the License Plate MUST be validated to ensure it conforms to one of the required Israeli formats
        // if it's provided. If not provided, it's valid for Color+Type detection modes.
        val licensePlate = entry.licensePlate
        if (licensePlate != null && !licensePlateValidator.isValid(licensePlate)) {
            println("AndroidWatchlistRepository: Invalid license plate format for $licensePlate. Entry not added.")
            return false
        }
        try {
            val entityToInsert = entry.toEntity()
            watchlistDao.insertEntry(entityToInsert)
            println("AndroidWatchlistRepository: Added entry with license plate ${entry.licensePlate ?: "none"}")
            return true
        } catch (e: Exception) {
            println("AndroidWatchlistRepository: Error adding entry ${entry.licensePlate} - ${e.message}")
            return false
        }
    }

    override suspend fun deleteEntry(licensePlate: String): Boolean {
        try {
            val rowsAffected = watchlistDao.deleteEntryByLicensePlate(licensePlate)
            println("AndroidWatchlistRepository: Deleted $rowsAffected entries for LP $licensePlate")
            return rowsAffected > 0
        } catch (e: Exception) {
            println("AndroidWatchlistRepository: Error deleting entry $licensePlate - ${e.message}")
            return false
        }
    }

    override suspend fun getAllEntries(): List<WatchlistEntry> {
        return try {
            val entries = watchlistDao.getAllEntries().map { it.toDomainModel() }
            println("AndroidWatchlistRepository: Retrieved ${entries.size} entries")
            entries
        } catch (e: Exception) {
            println("AndroidWatchlistRepository: Error getting all entries - ${e.message}")
            emptyList()
        }
    }

    override suspend fun findEntryByLicensePlate(licensePlate: String): WatchlistEntry? {
        return try {
            val entry = watchlistDao.findEntryByLicensePlate(licensePlate)?.toDomainModel()
            println("AndroidWatchlistRepository: Found entry for LP $licensePlate: $entry")
            entry
        } catch (e: Exception) {
            println("AndroidWatchlistRepository: Error finding entry for LP $licensePlate - ${e.message}")
            null
        }
    }

    override suspend fun deleteEntryByColorAndType(color: VehicleColor, type: VehicleType): Boolean {
        try {
            // First, get all entries matching the criteria
            val allEntries = watchlistDao.getAllEntries()
            val matchingEntries = allEntries
                .filter { it.vehicleColor == color.name && it.vehicleType == type.name && it.licensePlate == null }
            
            if (matchingEntries.isEmpty()) {
                println("AndroidWatchlistRepository: No entries found for Color: $color, Type: $type")
                return false
            }
            
            // For simplicity, we'll just delete the first matching entry
            val entryToDelete = matchingEntries.first()
            val rowsAffected = watchlistDao.deleteEntryById(entryToDelete.id)
            
            println("AndroidWatchlistRepository: Deleted $rowsAffected entries for Color: $color, Type: $type")
            return rowsAffected > 0
        } catch (e: Exception) {
            println("AndroidWatchlistRepository: Error deleting entry by Color: $color, Type: $type - ${e.message}")
            return false
        }
    }
} 