package com.example.vehiclerecognition

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class VehicleRecognitionApp : Application() {
    override fun onCreate() {
        super.onCreate()
        println("VehicleRecognitionApp: Hilt Application initialized.")
        // Any other application-wide initialization can go here.
    }
} 