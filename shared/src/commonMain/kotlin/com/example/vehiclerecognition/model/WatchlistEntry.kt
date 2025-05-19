package com.example.vehiclerecognition.model

/**
 * Represents a vehicle entry in the watchlist.
 * As per FR 1.5.
 *
 * @property licensePlate The license plate number (String), optional for color+type detection.
 * @property vehicleType The type of the vehicle (Enum: Car, Motorcycle, Truck).
 * @property vehicleColor The color of the vehicle (Enum: Red, Blue, Green, White, Black, Gray, Yellow).
 */
data class WatchlistEntry(
    val licensePlate: String?,
    val vehicleType: VehicleType,
    val vehicleColor: VehicleColor
) 