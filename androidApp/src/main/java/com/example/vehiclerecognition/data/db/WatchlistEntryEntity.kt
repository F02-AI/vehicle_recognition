package com.example.vehiclerecognition.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.vehiclerecognition.model.VehicleColor
import com.example.vehiclerecognition.model.VehicleType
import com.example.vehiclerecognition.model.WatchlistEntry
import com.example.vehiclerecognition.data.models.Country
import java.util.UUID

@Entity(tableName = "watchlist_entries")
data class WatchlistEntryEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val licensePlate: String?,
    val vehicleType: String, // Stored as String, converted from/to Enum
    val vehicleColor: String, // Stored as String, converted from/to Enum
    val country: String = Country.ISRAEL.isoCode // Default to Israel for backward compatibility
) {
    fun toDomainModel(): WatchlistEntry {
        return WatchlistEntry(
            licensePlate = licensePlate,
            vehicleType = VehicleType.valueOf(vehicleType),
            vehicleColor = VehicleColor.valueOf(vehicleColor),
            country = Country.fromIsoCode(country) ?: Country.ISRAEL
        )
    }
}

fun WatchlistEntry.toEntity(): WatchlistEntryEntity {
    return WatchlistEntryEntity(
        licensePlate = licensePlate,
        vehicleType = vehicleType.name,
        vehicleColor = vehicleColor.name,
        country = country.isoCode
    )
} 