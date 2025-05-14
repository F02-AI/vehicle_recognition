package com.example.vehiclerecognition.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.vehiclerecognition.model.VehicleColor
import com.example.vehiclerecognition.model.VehicleType
import com.example.vehiclerecognition.model.WatchlistEntry

@Entity(tableName = "watchlist_entries")
data class WatchlistEntryEntity(
    @PrimaryKey
    val licensePlate: String,
    val vehicleType: String, // Stored as String, converted from/to Enum
    val vehicleColor: String // Stored as String, converted from/to Enum
) {
    fun toDomainModel(): WatchlistEntry {
        return WatchlistEntry(
            licensePlate = licensePlate,
            vehicleType = VehicleType.valueOf(vehicleType),
            vehicleColor = VehicleColor.valueOf(vehicleColor)
        )
    }
}

fun WatchlistEntry.toEntity(): WatchlistEntryEntity {
    return WatchlistEntryEntity(
        licensePlate = licensePlate,
        vehicleType = vehicleType.name,
        vehicleColor = vehicleColor.name
    )
} 