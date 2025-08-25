package com.example.vehiclerecognition.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.*
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class MigrationTest {
    private val TEST_DB = "migration-test"

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java.canonicalName,
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    @Throws(IOException::class)
    fun migrate2To3_addsCountryColumnToWatchlistEntries() {
        // Create the database with version 2 schema
        val db = helper.createDatabase(TEST_DB, 2)

        // Insert some test data in version 2 format
        db.execSQL("""
            INSERT INTO watchlist_entries (id, license_plate, vehicle_type, vehicle_color, date_added, is_active)
            VALUES (1, '12-345-67', 'CAR', 'BLUE', 1000, 1)
        """)

        // Prepare for the next version
        db.close()

        // Run the migration to version 3
        val migratedDb = helper.runMigrationsAndValidate(
            TEST_DB, 3, true, AppDatabase.MIGRATION_2_3
        )

        // Verify the country column was added with default value
        val cursor = migratedDb.query("SELECT * FROM watchlist_entries WHERE id = 1")
        assertTrue(cursor.moveToFirst())

        val countryIndex = cursor.getColumnIndex("country")
        assertTrue("Country column should exist", countryIndex >= 0)
        assertEquals("ISRAEL", cursor.getString(countryIndex))
        
        cursor.close()
        migratedDb.close()
    }

    @Test
    @Throws(IOException::class)
    fun migrate3To4_createsCountriesAndTemplatesTables() {
        // Create the database with version 3 schema
        val db = helper.createDatabase(TEST_DB, 3)

        // Insert some test data in version 3 format
        db.execSQL("""
            INSERT INTO watchlist_entries (id, license_plate, vehicle_type, vehicle_color, date_added, is_active, country)
            VALUES (1, '12-345-67', 'CAR', 'BLUE', 1000, 1, 'ISRAEL')
        """)

        // Prepare for the next version
        db.close()

        // Run the migration to version 4
        val migratedDb = helper.runMigrationsAndValidate(
            TEST_DB, 4, true, AppDatabase.MIGRATION_3_4
        )

        // Verify countries table was created and populated
        val countriesCursor = migratedDb.query("SELECT * FROM countries ORDER BY id")
        
        assertTrue("Should have at least one country", countriesCursor.count >= 3)
        
        // Check first country (should be ISRAEL alphabetically after ordering)
        countriesCursor.moveToFirst()
        val countries = mutableListOf<String>()
        do {
            countries.add(countriesCursor.getString(countriesCursor.getColumnIndex("id")))
        } while (countriesCursor.moveToNext())
        
        assertTrue("Should contain ISRAEL", countries.contains("ISRAEL"))
        assertTrue("Should contain UK", countries.contains("UK"))
        assertTrue("Should contain SINGAPORE", countries.contains("SINGAPORE"))
        
        // Verify default values
        countriesCursor.moveToFirst()
        do {
            val isEnabled = countriesCursor.getInt(countriesCursor.getColumnIndex("is_enabled"))
            assertEquals("Countries should be enabled by default", 1, isEnabled)
            
            val createdAt = countriesCursor.getLong(countriesCursor.getColumnIndex("created_at"))
            assertTrue("Created timestamp should be set", createdAt > 0)
        } while (countriesCursor.moveToNext())
        
        countriesCursor.close()

        // Verify license_plate_templates table was created
        val templatesCursor = migratedDb.query("SELECT name FROM sqlite_master WHERE type='table' AND name='license_plate_templates'")
        assertTrue("license_plate_templates table should exist", templatesCursor.count == 1)
        templatesCursor.close()

        // Verify table structure by inserting a test template
        migratedDb.execSQL("""
            INSERT INTO license_plate_templates 
            (country_id, template_pattern, display_name, priority, is_active, description, regex_pattern, created_at, updated_at)
            VALUES ('ISRAEL', 'NNNNNN', 'Test Format', 1, 1, 'Test Description', '^[0-9]{6}$', 2000, 2000)
        """)

        val templateCursor = migratedDb.query("SELECT * FROM license_plate_templates WHERE country_id = 'ISRAEL'")
        assertTrue("Should be able to insert and retrieve template", templateCursor.moveToFirst())
        assertEquals("NNNNNN", templateCursor.getString(templateCursor.getColumnIndex("template_pattern")))
        templateCursor.close()

        // Verify indexes were created
        val indexCursor = migratedDb.query("""
            SELECT name FROM sqlite_master 
            WHERE type='index' AND tbl_name='license_plate_templates'
        """)
        
        assertTrue("Should have at least 2 indexes", indexCursor.count >= 2)
        
        val indexNames = mutableListOf<String>()
        indexCursor.moveToFirst()
        do {
            indexNames.add(indexCursor.getString(0))
        } while (indexCursor.moveToNext())
        
        assertTrue("Should have country_id index", indexNames.any { it.contains("country_id") })
        assertTrue("Should have unique priority index", indexNames.any { it.contains("priority") })
        
        indexCursor.close()

        // Verify foreign key constraint works
        try {
            migratedDb.execSQL("""
                INSERT INTO license_plate_templates 
                (country_id, template_pattern, display_name, priority, description, regex_pattern)
                VALUES ('NON_EXISTENT', 'LLNNLL', 'Bad Country', 1, 'Test', '^test$')
            """)
            fail("Should not be able to insert template with non-existent country")
        } catch (e: Exception) {
            // Expected - foreign key constraint should prevent this
            assertTrue("Should be foreign key error", 
                       e.message?.contains("FOREIGN KEY constraint failed") ?: false)
        }

        migratedDb.close()
    }

    @Test
    @Throws(IOException::class)
    fun migrate3To4_preservesExistingWatchlistData() {
        // Create the database with version 3 schema
        val db = helper.createDatabase(TEST_DB, 3)

        // Insert test watchlist data
        val testEntries = listOf(
            "INSERT INTO watchlist_entries VALUES (1, '12-345-67', 'CAR', 'BLUE', 1000, 1, 'ISRAEL')",
            "INSERT INTO watchlist_entries VALUES (2, 'AB12CDE', 'MOTORCYCLE', 'RED', 2000, 1, 'UK')",
            "INSERT INTO watchlist_entries VALUES (3, '98-765-43', 'TRUCK', 'WHITE', 3000, 0, 'ISRAEL')"
        )

        testEntries.forEach { db.execSQL(it) }

        // Verify data exists before migration
        val beforeCursor = db.query("SELECT COUNT(*) FROM watchlist_entries")
        beforeCursor.moveToFirst()
        val beforeCount = beforeCursor.getInt(0)
        assertEquals(3, beforeCount)
        beforeCursor.close()

        db.close()

        // Run the migration to version 4
        val migratedDb = helper.runMigrationsAndValidate(
            TEST_DB, 4, true, AppDatabase.MIGRATION_3_4
        )

        // Verify all watchlist data is preserved
        val afterCursor = migratedDb.query("SELECT * FROM watchlist_entries ORDER BY id")
        assertEquals("All watchlist entries should be preserved", 3, afterCursor.count)

        afterCursor.moveToFirst()
        do {
            val id = afterCursor.getInt(afterCursor.getColumnIndex("id"))
            val licensePlate = afterCursor.getString(afterCursor.getColumnIndex("license_plate"))
            val country = afterCursor.getString(afterCursor.getColumnIndex("country"))

            when (id) {
                1 -> {
                    assertEquals("12-345-67", licensePlate)
                    assertEquals("ISRAEL", country)
                }
                2 -> {
                    assertEquals("AB12CDE", licensePlate)
                    assertEquals("UK", country)
                }
                3 -> {
                    assertEquals("98-765-43", licensePlate)
                    assertEquals("ISRAEL", country)
                }
            }
        } while (afterCursor.moveToNext())

        afterCursor.close()
        migratedDb.close()
    }

    @Test
    @Throws(IOException::class)
    fun migrate3To4_handlesUniqueConstraintOnTemplates() {
        // Create the database with version 4 schema
        val db = helper.createDatabase(TEST_DB, 3)
        db.close()

        val migratedDb = helper.runMigrationsAndValidate(
            TEST_DB, 4, true, AppDatabase.MIGRATION_3_4
        )

        // Insert first template
        migratedDb.execSQL("""
            INSERT INTO license_plate_templates 
            (country_id, template_pattern, display_name, priority, description, regex_pattern)
            VALUES ('ISRAEL', 'NNNNNN', 'Primary Format', 1, '6 numbers', '^[0-9]{6}$')
        """)

        // Try to insert another template with same country and priority
        try {
            migratedDb.execSQL("""
                INSERT INTO license_plate_templates 
                (country_id, template_pattern, display_name, priority, description, regex_pattern)
                VALUES ('ISRAEL', 'NNNNNNN', 'Secondary Format', 1, '7 numbers', '^[0-9]{7}$')
            """)
            fail("Should not be able to insert template with duplicate country_id and priority")
        } catch (e: Exception) {
            // Expected - unique constraint should prevent this
            assertTrue("Should be unique constraint error", 
                       e.message?.contains("UNIQUE constraint failed") ?: false)
        }

        // But should be able to insert with different priority
        migratedDb.execSQL("""
            INSERT INTO license_plate_templates 
            (country_id, template_pattern, display_name, priority, description, regex_pattern)
            VALUES ('ISRAEL', 'NNNNNNN', 'Secondary Format', 2, '7 numbers', '^[0-9]{7}$')
        """)

        val cursor = migratedDb.query("SELECT COUNT(*) FROM license_plate_templates WHERE country_id = 'ISRAEL'")
        cursor.moveToFirst()
        assertEquals("Should have 2 templates for ISRAEL", 2, cursor.getInt(0))
        cursor.close()

        migratedDb.close()
    }

    @Test
    @Throws(IOException::class)
    fun migrate2To3To4_fullMigrationPath() {
        // Test complete migration path from version 2 to 4
        
        // Start with version 2
        val db = helper.createDatabase(TEST_DB, 2)

        // Insert version 2 data
        db.execSQL("""
            INSERT INTO watchlist_entries (id, license_plate, vehicle_type, vehicle_color, date_added, is_active)
            VALUES (1, '12-345-67', 'CAR', 'BLUE', 1000, 1)
        """)

        db.close()

        // Run all migrations
        val migratedDb = helper.runMigrationsAndValidate(
            TEST_DB, 4, true, 
            AppDatabase.MIGRATION_2_3,
            AppDatabase.MIGRATION_3_4
        )

        // Verify final state
        // 1. Watchlist entry should have country column with default value
        val watchlistCursor = migratedDb.query("SELECT * FROM watchlist_entries WHERE id = 1")
        assertTrue(watchlistCursor.moveToFirst())
        assertEquals("ISRAEL", watchlistCursor.getString(watchlistCursor.getColumnIndex("country")))
        watchlistCursor.close()

        // 2. Countries table should exist and be populated
        val countriesCursor = migratedDb.query("SELECT COUNT(*) FROM countries")
        countriesCursor.moveToFirst()
        assertEquals(3, countriesCursor.getInt(0))
        countriesCursor.close()

        // 3. Templates table should exist and be functional
        migratedDb.execSQL("""
            INSERT INTO license_plate_templates 
            (country_id, template_pattern, display_name, priority, description, regex_pattern)
            VALUES ('ISRAEL', 'NNNNNN', 'Test Format', 1, 'Test Description', '^[0-9]{6}$')
        """)

        val templateCursor = migratedDb.query("SELECT COUNT(*) FROM license_plate_templates")
        templateCursor.moveToFirst()
        assertEquals(1, templateCursor.getInt(0))
        templateCursor.close()

        migratedDb.close()
    }

    @Test
    @Throws(IOException::class)
    fun migrate3To4_handlesEmptyDatabase() {
        // Test migration with no existing data
        val db = helper.createDatabase(TEST_DB, 3)
        db.close()

        val migratedDb = helper.runMigrationsAndValidate(
            TEST_DB, 4, true, AppDatabase.MIGRATION_3_4
        )

        // Should still create countries and templates tables
        val countriesCursor = migratedDb.query("SELECT COUNT(*) FROM countries")
        countriesCursor.moveToFirst()
        assertEquals("Should have default countries", 3, countriesCursor.getInt(0))
        countriesCursor.close()

        // Templates table should be empty but functional
        val templateCursor = migratedDb.query("SELECT COUNT(*) FROM license_plate_templates")
        templateCursor.moveToFirst()
        assertEquals("Templates table should be empty", 0, templateCursor.getInt(0))
        templateCursor.close()

        // Should be able to insert templates
        migratedDb.execSQL("""
            INSERT INTO license_plate_templates 
            (country_id, template_pattern, display_name, priority, description, regex_pattern)
            VALUES ('ISRAEL', 'NNNNNN', 'Test Format', 1, 'Test Description', '^[0-9]{6}$')
        """)

        migratedDb.close()
    }
}