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
    version = 5, 
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
        
        /**
         * Migration from version 4 to 5: Convert country field from enum names to ISO codes
         */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Update watchlist entries to use ISO codes instead of enum names
                // Only convert the most common ones that users might have
                database.execSQL("UPDATE watchlist_entries SET country = 'IL' WHERE country = 'ISRAEL'")
                database.execSQL("UPDATE watchlist_entries SET country = 'GB' WHERE country = 'UK'")
                database.execSQL("UPDATE watchlist_entries SET country = 'AU' WHERE country = 'AUSTRALIA'")
                database.execSQL("UPDATE watchlist_entries SET country = 'US' WHERE country = 'UNITED_STATES'")
                database.execSQL("UPDATE watchlist_entries SET country = 'CA' WHERE country = 'CANADA'")
                database.execSQL("UPDATE watchlist_entries SET country = 'DE' WHERE country = 'GERMANY'")
                database.execSQL("UPDATE watchlist_entries SET country = 'FR' WHERE country = 'FRANCE'")
                database.execSQL("UPDATE watchlist_entries SET country = 'SG' WHERE country = 'SINGAPORE'")
                
                // For any remaining enum names that weren't converted, default to IL
                database.execSQL("UPDATE watchlist_entries SET country = 'IL' WHERE length(country) > 3")
            }
        }
    }
} 