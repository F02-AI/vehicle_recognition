package com.example.vehiclerecognition.domain.repository

import com.example.vehiclerecognition.model.WatchlistEntry
import com.example.vehiclerecognition.data.models.Country
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for managing watchlist entries
 */
interface WatchlistRepository {
    /**
     * Adds a new entry to the watchlist
     * @param entry The watchlist entry to add
     * @return true if successful, false otherwise
     */
    suspend fun addEntry(entry: WatchlistEntry): Boolean
    
    /**
     * Deletes an entry from the watchlist by license plate
     * @param licensePlate The license plate of the entry to delete
     * @return true if successful, false otherwise
     */
    suspend fun deleteEntry(licensePlate: String): Boolean
    
    /**
     * Gets all watchlist entries as a flow
     * @return Flow of list of watchlist entries
     */
    fun getAllEntries(): Flow<List<WatchlistEntry>>
    
    /**
     * Gets watchlist entries for a specific country as a flow
     * @param country The country to filter by
     * @return Flow of list of watchlist entries for the specified country
     */
    fun getEntriesByCountry(country: Country): Flow<List<WatchlistEntry>>
    
    /**
     * Clears all entries from the watchlist
     * @return true if successful, false otherwise
     */
    suspend fun clearAll(): Boolean
    
    /**
     * Clears all entries for a specific country from the watchlist
     * @param country The country to clear entries for
     * @return true if successful, false otherwise
     */
    suspend fun clearByCountry(country: Country): Boolean
}