package com.example.vehiclerecognition.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity representing a license plate template in the database
 */
@Entity(
    tableName = "license_plate_templates",
    foreignKeys = [
        ForeignKey(
            entity = CountryEntity::class,
            parentColumns = ["id"],
            childColumns = ["country_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["country_id"]),
        Index(value = ["country_id", "priority"], unique = true) // Unique priority per country
    ]
)
data class LicensePlateTemplateEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Int = 0,
    
    @ColumnInfo(name = "country_id")
    val countryId: String,
    
    @ColumnInfo(name = "template_pattern")
    val templatePattern: String,
    
    @ColumnInfo(name = "display_name")
    val displayName: String,
    
    @ColumnInfo(name = "priority")
    val priority: Int, // 1 = primary, 2 = secondary
    
    @ColumnInfo(name = "is_active")
    val isActive: Boolean = true,
    
    @ColumnInfo(name = "description")
    val description: String,
    
    @ColumnInfo(name = "regex_pattern")
    val regexPattern: String,
    
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
    
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
)