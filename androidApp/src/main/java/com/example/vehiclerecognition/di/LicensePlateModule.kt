package com.example.vehiclerecognition.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Hilt module for license plate recognition dependencies
 */
@Module
@InstallIn(SingletonComponent::class)
object LicensePlateModule {
    
    // All dependencies are already provided through constructor injection
    // using @Inject and @Singleton annotations, so no explicit provides methods needed
} 