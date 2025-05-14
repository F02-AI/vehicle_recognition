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

    // Using a custom query for delete by license plate as Room's @Delete needs an entity instance.
    @Query("DELETE FROM watchlist_entries WHERE licensePlate = :licensePlate")
    suspend fun deleteEntry(licensePlate: String): Int // Returns number of rows affected

    @Query("SELECT * FROM watchlist_entries")
    suspend fun getAllEntries(): List<WatchlistEntryEntity>

    @Query("SELECT * FROM watchlist_entries WHERE licensePlate = :licensePlate LIMIT 1")
    suspend fun findEntryByLicensePlate(licensePlate: String): WatchlistEntryEntity?
} 