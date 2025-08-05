package com.example.vehiclerecognition.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface WatchlistDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntry(entry: WatchlistEntryEntity)

    // Using a custom query for delete by ID
    @Query("DELETE FROM watchlist_entries WHERE id = :id")
    suspend fun deleteEntryById(id: String): Int // Returns number of rows affected

    // Keep this for backward compatibility
    @Query("DELETE FROM watchlist_entries WHERE licensePlate = :licensePlate")
    suspend fun deleteEntryByLicensePlate(licensePlate: String): Int

    @Query("SELECT * FROM watchlist_entries")
    suspend fun getAllEntries(): List<WatchlistEntryEntity>
    
    @Query("SELECT * FROM watchlist_entries WHERE country = :country")
    suspend fun getEntriesByCountry(country: String): List<WatchlistEntryEntity>

    @Query("SELECT * FROM watchlist_entries WHERE licensePlate = :licensePlate LIMIT 1")
    suspend fun findEntryByLicensePlate(licensePlate: String): WatchlistEntryEntity?
    
    @Query("SELECT * FROM watchlist_entries WHERE licensePlate = :licensePlate AND country = :country LIMIT 1")
    suspend fun findEntryByLicensePlateAndCountry(licensePlate: String, country: String): WatchlistEntryEntity?
    
    @Query("DELETE FROM watchlist_entries")
    suspend fun clearAll(): Int
    
    @Query("DELETE FROM watchlist_entries WHERE country = :country")
    suspend fun clearByCountry(country: String): Int
} 