package com.example.vehiclerecognition.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [WatchlistEntryEntity::class], version = 3, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun watchlistDao(): WatchlistDao

    companion object {
        /**
         * Migration from version 2 to 3: Add country column to watchlist_entries table
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE watchlist_entries ADD COLUMN country TEXT NOT NULL DEFAULT 'ISRAEL'")
            }
        }
    }
} 