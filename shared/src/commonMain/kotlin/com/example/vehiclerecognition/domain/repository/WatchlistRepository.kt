package com.example.vehiclerecognition.domain.repository

import com.example.vehiclerecognition.model.WatchlistEntry

/**
 * Interface for managing the vehicle watchlist data.
 * Defines operations for adding, deleting, and retrieving watchlist entries.
 * As per FR 1.5, FR 1.6, FR 1.7.
 */
interface WatchlistRepository {

    /**
     * Adds a new entry to the watchlist.
     * Implementations should ensure license plate format validation (FR 1.6)
     * before adding, although primary validation might occur before calling this.
     *
     * @param entry The WatchlistEntry to add.
     * @return True if the entry was added successfully, false otherwise (e.g., if validation failed or due to DB error).
     */
    suspend fun addEntry(entry: WatchlistEntry): Boolean

    /**
     * Deletes an entry from the watchlist, typically identified by its license plate.
     *
     * @param licensePlate The license plate of the entry to delete.
     * @return True if the entry was deleted successfully, false otherwise (e.g., entry not found).
     */
    suspend fun deleteEntry(licensePlate: String): Boolean

    /**
     * Retrieves all entries from the watchlist.
     *
     * @return A list of all WatchlistEntry objects.
     */
    suspend fun getAllEntries(): List<WatchlistEntry>

    /**
     * Finds a specific watchlist entry by its license plate.
     *
     * @param licensePlate The license plate to search for.
     * @return The WatchlistEntry if found, null otherwise.
     */
    suspend fun findEntryByLicensePlate(licensePlate: String): WatchlistEntry?
} 