package com.example.vehiclerecognition.model

import com.example.vehiclerecognition.data.models.Country

/**
 * Data class representing a watchlist entry for vehicle monitoring
 */
data class WatchlistEntry(
    val licensePlate: String? = null, // Optional for color/type-only modes
    val vehicleType: VehicleType,
    val vehicleColor: VehicleColor,
    val country: Country = Country.ISRAEL // Country for which this entry is valid
)