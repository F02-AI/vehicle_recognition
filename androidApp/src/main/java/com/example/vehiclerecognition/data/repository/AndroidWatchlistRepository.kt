package com.example.vehiclerecognition.data.repository

import com.example.vehiclerecognition.data.db.WatchlistDao
import com.example.vehiclerecognition.data.db.WatchlistEntryEntity
import com.example.vehiclerecognition.data.db.toEntity
import com.example.vehiclerecognition.domain.repository.WatchlistRepository
import com.example.vehiclerecognition.domain.validation.LicensePlateValidator // FR 1.6
import com.example.vehiclerecognition.domain.validation.CountryAwareLicensePlateValidator
import com.example.vehiclerecognition.model.WatchlistEntry
import com.example.vehiclerecognition.model.VehicleColor
import com.example.vehiclerecognition.model.VehicleType
import com.example.vehiclerecognition.data.models.Country
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.catch

class AndroidWatchlistRepository(
    private val watchlistDao: WatchlistDao,
    private val licensePlateValidator: LicensePlateValidator // For FR 1.6
) : WatchlistRepository {

    override suspend fun addEntry(entry: WatchlistEntry): Boolean {
        // Note: License plate validation is now handled in the ViewModel using template-based validation
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

    override fun getAllEntries(): Flow<List<WatchlistEntry>> = flow {
        val entries = watchlistDao.getAllEntries().map { it.toDomainModel() }
        println("AndroidWatchlistRepository: Retrieved ${entries.size} entries")
        emit(entries)
    }.catch { e ->
        println("AndroidWatchlistRepository: Error getting all entries - ${e.message}")
        emit(emptyList())
    }
    
    override fun getEntriesByCountry(country: Country): Flow<List<WatchlistEntry>> = flow {
        val entries = watchlistDao.getEntriesByCountry(country.isoCode).map { it.toDomainModel() }
        println("AndroidWatchlistRepository: Retrieved ${entries.size} entries for country ${country.displayName}")
        emit(entries)
    }.catch { e ->
        println("AndroidWatchlistRepository: Error getting entries for country ${country.displayName} - ${e.message}")
        emit(emptyList())
    }

    override suspend fun clearAll(): Boolean {
        return try {
            val rowsAffected = watchlistDao.clearAll()
            println("AndroidWatchlistRepository: Cleared all entries, $rowsAffected rows affected")
            rowsAffected > 0
        } catch (e: Exception) {
            println("AndroidWatchlistRepository: Error clearing all entries - ${e.message}")
            false
        }
    }
    
    override suspend fun clearByCountry(country: Country): Boolean {
        return try {
            val rowsAffected = watchlistDao.clearByCountry(country.isoCode)
            println("AndroidWatchlistRepository: Cleared ${rowsAffected} entries for country ${country.displayName}")
            rowsAffected > 0
        } catch (e: Exception) {
            println("AndroidWatchlistRepository: Error clearing entries for country ${country.displayName} - ${e.message}")
            false
        }
    }
    
    // Legacy methods for backward compatibility
    suspend fun findEntryByLicensePlate(licensePlate: String): WatchlistEntry? {
        return try {
            val entry = watchlistDao.findEntryByLicensePlate(licensePlate)?.toDomainModel()
            println("AndroidWatchlistRepository: Found entry for LP $licensePlate: $entry")
            entry
        } catch (e: Exception) {
            println("AndroidWatchlistRepository: Error finding entry for LP $licensePlate - ${e.message}")
            null
        }
    }

    suspend fun deleteEntryByColorAndType(color: VehicleColor, type: VehicleType): Boolean {
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