package com.example.vehiclerecognition.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for Country entities
 */
@Dao
interface CountryDao {
    
    @Query("SELECT * FROM countries WHERE is_enabled = 1 ORDER BY display_name ASC")
    fun getAllEnabledCountries(): Flow<List<CountryEntity>>
    
    @Query("SELECT * FROM countries ORDER BY display_name ASC")
    fun getAllCountries(): Flow<List<CountryEntity>>
    
    @Query("SELECT * FROM countries ORDER BY display_name ASC")
    suspend fun getAllCountriesSync(): List<CountryEntity>
    
    @Query("SELECT * FROM countries WHERE id = :countryId")
    suspend fun getCountryById(countryId: String): CountryEntity?
    
    @Query("SELECT EXISTS(SELECT 1 FROM countries WHERE id = :countryId AND is_enabled = 1)")
    suspend fun isCountryEnabled(countryId: String): Boolean
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCountry(country: CountryEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCountries(countries: List<CountryEntity>)
    
    @Update
    suspend fun updateCountry(country: CountryEntity)
    
    @Query("UPDATE countries SET is_enabled = :enabled WHERE id = :countryId")
    suspend fun setCountryEnabled(countryId: String, enabled: Boolean)
    
    @Query("DELETE FROM countries WHERE id = :countryId")
    suspend fun deleteCountry(countryId: String)
}