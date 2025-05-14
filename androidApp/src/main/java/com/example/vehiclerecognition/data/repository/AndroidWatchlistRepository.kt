package com.example.vehiclerecognition.data.repository

import com.example.vehiclerecognition.data.db.WatchlistDao
import com.example.vehiclerecognition.data.db.toDomainModel
import com.example.vehiclerecognition.data.db.toEntity
import com.example.vehiclerecognition.domain.repository.WatchlistRepository
import com.example.vehiclerecognition.domain.validation.LicensePlateValidator // FR 1.6
import com.example.vehiclerecognition.model.WatchlistEntry

class AndroidWatchlistRepository(
    private val watchlistDao: WatchlistDao,
    private val licensePlateValidator: LicensePlateValidator // For FR 1.6
) : WatchlistRepository {

    override suspend fun addEntry(entry: WatchlistEntry): Boolean {
        // FR 1.6: Input for the License Plate MUST be validated to ensure it conforms to one of the required Israeli formats.
        // Entries with invalid formats SHOULD NOT be added.
        if (!licensePlateValidator.isValid(entry.licensePlate)) {
            println("AndroidWatchlistRepository: Invalid license plate format for ${'$'}{entry.licensePlate}. Entry not added.")
            return false
        }
        try {
            watchlistDao.insertEntry(entry.toEntity())
            println("AndroidWatchlistRepository: Added entry ${'$'}{entry.licensePlate}")
            return true
        } catch (e: Exception) {
            println("AndroidWatchlistRepository: Error adding entry ${'$'}{entry.licensePlate} - ${'$'}{e.message}")
            return false
        }
    }

    override suspend fun deleteEntry(licensePlate: String): Boolean {
        try {
            val rowsAffected = watchlistDao.deleteEntry(licensePlate)
            val success = rowsAffected > 0
            if (success) {
                println("AndroidWatchlistRepository: Deleted entry ${'$'}{licensePlate}")
            } else {
                println("AndroidWatchlistRepository: Entry ${'$'}{licensePlate} not found for deletion.")
            }
            return success
        } catch (e: Exception) {
            println("AndroidWatchlistRepository: Error deleting entry ${'$'}{licensePlate} - ${'$'}{e.message}")
            return false
        }
    }

    override suspend fun getAllEntries(): List<WatchlistEntry> {
        return try {
            val entities = watchlistDao.getAllEntries()
            println("AndroidWatchlistRepository: Retrieved ${'$'}{entities.size} entries")
            entities.map { it.toDomainModel() }
        } catch (e: Exception) {
            println("AndroidWatchlistRepository: Error retrieving all entries - ${'$'}{e.message}")
            emptyList()
        }
    }

    override suspend fun findEntryByLicensePlate(licensePlate: String): WatchlistEntry? {
        return try {
            val entity = watchlistDao.findEntryByLicensePlate(licensePlate)
            if (entity != null) {
                println("AndroidWatchlistRepository: Found entry ${'$'}{licensePlate}")
            } else {
                println("AndroidWatchlistRepository: Entry ${'$'}{licensePlate} not found.")
            }
            entity?.toDomainModel()
        } catch (e: Exception) {
            println("AndroidWatchlistRepository: Error finding entry ${'$'}{licensePlate} - ${'$'}{e.message}")
            null
        }
    }
} 