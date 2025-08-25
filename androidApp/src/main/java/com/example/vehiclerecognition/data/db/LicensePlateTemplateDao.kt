package com.example.vehiclerecognition.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for LicensePlateTemplate entities
 */
@Dao
interface LicensePlateTemplateDao {
    
    @Query("""
        SELECT * FROM license_plate_templates 
        WHERE country_id = :countryId AND is_active = 1 
        ORDER BY priority ASC
    """)
    fun getTemplatesByCountry(countryId: String): Flow<List<LicensePlateTemplateEntity>>
    
    @Query("""
        SELECT * FROM license_plate_templates 
        WHERE country_id = :countryId AND is_active = 1 
        ORDER BY priority ASC
    """)
    suspend fun getTemplatesByCountrySync(countryId: String): List<LicensePlateTemplateEntity>
    
    @Query("""
        SELECT * FROM license_plate_templates 
        WHERE country_id = :countryId AND priority = :priority AND is_active = 1
    """)
    suspend fun getTemplateByCountryAndPriority(countryId: String, priority: Int): LicensePlateTemplateEntity?
    
    @Query("SELECT * FROM license_plate_templates WHERE id = :templateId")
    suspend fun getTemplateById(templateId: Int): LicensePlateTemplateEntity?
    
    @Query("""
        SELECT COUNT(*) FROM license_plate_templates 
        WHERE country_id = :countryId AND is_active = 1
    """)
    suspend fun getActiveTemplateCountForCountry(countryId: String): Int
    
    @Query("""
        SELECT EXISTS(
            SELECT 1 FROM license_plate_templates 
            WHERE country_id = :countryId AND is_active = 1
        )
    """)
    suspend fun hasActiveTemplatesForCountry(countryId: String): Boolean
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTemplate(template: LicensePlateTemplateEntity): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTemplates(templates: List<LicensePlateTemplateEntity>)
    
    @Update
    suspend fun updateTemplate(template: LicensePlateTemplateEntity)
    
    @Query("UPDATE license_plate_templates SET is_active = :active WHERE id = :templateId")
    suspend fun setTemplateActive(templateId: Int, active: Boolean)
    
    @Query("DELETE FROM license_plate_templates WHERE id = :templateId")
    suspend fun deleteTemplate(templateId: Int)
    
    @Query("DELETE FROM license_plate_templates WHERE country_id = :countryId")
    suspend fun deleteTemplatesByCountry(countryId: String)
    
    /**
     * Replaces all templates for a country atomically
     */
    @Transaction
    suspend fun replaceTemplatesForCountry(countryId: String, templates: List<LicensePlateTemplateEntity>) {
        deleteTemplatesByCountry(countryId)
        insertTemplates(templates)
    }
    
    /**
     * Gets all countries that have no active templates (for warning display)
     */
    @Query("""
        SELECT c.* FROM countries c
        LEFT JOIN license_plate_templates lpt ON c.id = lpt.country_id AND lpt.is_active = 1
        WHERE c.is_enabled = 1 AND lpt.id IS NULL
        ORDER BY c.display_name ASC
    """)
    suspend fun getCountriesWithoutTemplates(): List<CountryEntity>
}