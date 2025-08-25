package com.example.vehiclerecognition.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        WatchlistEntryEntity::class, 
        CountryEntity::class, 
        LicensePlateTemplateEntity::class
    ], 
    version = 4, 
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun watchlistDao(): WatchlistDao
    abstract fun countryDao(): CountryDao
    abstract fun licensePlateTemplateDao(): LicensePlateTemplateDao

    companion object {
        /**
         * Migration from version 2 to 3: Add country column to watchlist_entries table
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE watchlist_entries ADD COLUMN country TEXT NOT NULL DEFAULT 'ISRAEL'")
            }
        }
        
        /**
         * Migration from version 3 to 4: Add countries and license_plate_templates tables
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Create countries table
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `countries` (
                        `id` TEXT NOT NULL,
                        `display_name` TEXT NOT NULL,
                        `flag_resource_id` TEXT NOT NULL,
                        `is_enabled` INTEGER NOT NULL DEFAULT 1,
                        `created_at` INTEGER NOT NULL DEFAULT 0,
                        `updated_at` INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY(`id`)
                    )
                """)
                
                // Create license_plate_templates table
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `license_plate_templates` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `country_id` TEXT NOT NULL,
                        `template_pattern` TEXT NOT NULL,
                        `display_name` TEXT NOT NULL,
                        `priority` INTEGER NOT NULL,
                        `is_active` INTEGER NOT NULL DEFAULT 1,
                        `description` TEXT NOT NULL,
                        `regex_pattern` TEXT NOT NULL,
                        `created_at` INTEGER NOT NULL DEFAULT 0,
                        `updated_at` INTEGER NOT NULL DEFAULT 0,
                        FOREIGN KEY(`country_id`) REFERENCES `countries`(`id`) ON DELETE CASCADE
                    )
                """)
                
                // Create indexes
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_license_plate_templates_country_id` ON `license_plate_templates` (`country_id`)")
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_license_plate_templates_country_id_priority` ON `license_plate_templates` (`country_id`, `priority`)")
                
                // Insert default countries
                val currentTime = System.currentTimeMillis()
                database.execSQL("INSERT OR IGNORE INTO `countries` VALUES ('ISRAEL', 'Israel', 'flag_israel', 1, $currentTime, $currentTime)")
                database.execSQL("INSERT OR IGNORE INTO `countries` VALUES ('UK', 'United Kingdom', 'flag_uk', 1, $currentTime, $currentTime)")
                database.execSQL("INSERT OR IGNORE INTO `countries` VALUES ('SINGAPORE', 'Singapore', 'flag_singapore', 1, $currentTime, $currentTime)")
            }
        }
    }
} 