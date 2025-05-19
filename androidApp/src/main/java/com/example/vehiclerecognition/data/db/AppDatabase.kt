package com.example.vehiclerecognition.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [WatchlistEntryEntity::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun watchlistDao(): WatchlistDao

    // Companion object for singleton pattern (typical for Room)
    // companion object {
    //     @Volatile
    //     private var INSTANCE: AppDatabase? = null
    //
    //     fun getDatabase(context: android.content.Context): AppDatabase {
    //         return INSTANCE ?: synchronized(this) {
    //             val instance = androidx.room.Room.databaseBuilder(
    //                 context.applicationContext,
    //                 AppDatabase::class.java,
    //                 "vehicle_recognition_database"
    //             ).build()
    //             INSTANCE = instance
    //             instance
    //         }
    //     }
    // }
} 