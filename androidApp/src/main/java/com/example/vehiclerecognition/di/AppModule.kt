package com.example.vehiclerecognition.di

import android.content.Context
import androidx.room.Room
import com.example.vehiclerecognition.R // Assuming R class is available for sound resource
import com.example.vehiclerecognition.data.db.AppDatabase
import com.example.vehiclerecognition.data.db.CountryDao
import com.example.vehiclerecognition.data.db.LicensePlateTemplateDao
import com.example.vehiclerecognition.data.db.WatchlistDao
import com.example.vehiclerecognition.data.repositories.AndroidLicensePlateTemplateRepository
import com.example.vehiclerecognition.data.repository.AndroidSettingsRepository
import com.example.vehiclerecognition.data.repository.AndroidWatchlistRepository
import com.example.vehiclerecognition.domain.repository.LicensePlateTemplateRepository
import com.example.vehiclerecognition.domain.service.LicensePlateTemplateService
import com.example.vehiclerecognition.domain.logic.VehicleMatcher
import com.example.vehiclerecognition.domain.platform.SoundAlertPlayer
import com.example.vehiclerecognition.domain.repository.SettingsRepository
import com.example.vehiclerecognition.domain.repository.WatchlistRepository
import com.example.vehiclerecognition.domain.validation.LicensePlateValidator
import com.example.vehiclerecognition.domain.validation.DefaultLicensePlateValidator
import com.example.vehiclerecognition.platform.AndroidSoundAlertPlayer
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext appContext: Context): AppDatabase {
        return Room.databaseBuilder(
            appContext,
            AppDatabase::class.java,
            "vehicle_recognition_database"
        ).addMigrations(AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4, AppDatabase.MIGRATION_4_5)
         .fallbackToDestructiveMigrationOnDowngrade()
         .build()
    }

    @Provides
    @Singleton
    fun provideWatchlistDao(appDatabase: AppDatabase): WatchlistDao {
        return appDatabase.watchlistDao()
    }

    @Provides
    @Singleton
    fun provideCountryDao(appDatabase: AppDatabase): CountryDao {
        return appDatabase.countryDao()
    }

    @Provides
    @Singleton
    fun provideLicensePlateTemplateDao(appDatabase: AppDatabase): LicensePlateTemplateDao {
        return appDatabase.licensePlateTemplateDao()
    }

    @Provides
    @Singleton
    fun provideLicensePlateValidator(): LicensePlateValidator {
        return DefaultLicensePlateValidator // It's an object
    }

    @Provides
    @Singleton
    fun provideWatchlistRepository(
        watchlistDao: WatchlistDao,
        licensePlateValidator: LicensePlateValidator
    ): WatchlistRepository {
        return AndroidWatchlistRepository(watchlistDao, licensePlateValidator)
    }

    @Provides
    @Singleton
    fun provideSettingsRepository(@ApplicationContext appContext: Context): SettingsRepository {
        return AndroidSettingsRepository(appContext)
    }

    @Provides
    @Singleton
    fun provideSoundAlertPlayer(
        @ApplicationContext appContext: Context
        // TODO: You need to ensure R.raw.alert_sound exists or provide a valid ID.
        // For now, if R.raw.alert_sound doesn't resolve, this will be a compile error.
        // Consider providing a default/fallback sound ID or handling this more robustly.
    ): SoundAlertPlayer {
        // If R.raw.alert_sound is not found, replace with a placeholder or default.
        // For the sake of this example, assuming it resolves.
        // If not, one might need to use context.getResources().getIdentifier("alert_sound", "raw", context.getPackageName())
        // or define a default sound resource ID that is known to exist.
        val soundResId = try {
             R.raw.alert_sound // This line might cause an error if the resource doesn't exist
        } catch (e: NoClassDefFoundError) {
            // Fallback or error logging if R class or resource is not found at compile/runtime
            // This is a common issue if the DI module is in a different module than the resources
            // without proper setup. For a single-module app, it should be fine if res/raw/alert_sound.mp3 (or .wav etc) exists.
            android.util.Log.e("AppModule", "R.raw.alert_sound not found. Using placeholder 0.", e)
            0 // Placeholder, will likely fail to play. Ensure actual resource exists.
        }
         if (soundResId == 0) {
            android.util.Log.w("AppModule", "Sound resource ID is 0. Sound alert player might not work.")
        }
        return AndroidSoundAlertPlayer(appContext, soundResId)
    }

    @Provides
    @Singleton
    fun provideVehicleMatcher(
        watchlistRepository: WatchlistRepository,
        licensePlateValidator: LicensePlateValidator,
        templateService: LicensePlateTemplateService,
        templateAwareEnhancer: com.example.vehiclerecognition.ml.processors.TemplateAwareOcrEnhancer
    ): VehicleMatcher {
        return VehicleMatcher(watchlistRepository, licensePlateValidator, templateService, templateAwareEnhancer)
    }

    @Provides
    @Singleton
    fun provideLicensePlateTemplateRepository(
        countryDao: CountryDao,
        templateDao: LicensePlateTemplateDao
    ): LicensePlateTemplateRepository {
        return AndroidLicensePlateTemplateRepository(countryDao, templateDao)
    }

    @Provides
    @Singleton
    fun provideLicensePlateTemplateService(
        templateRepository: LicensePlateTemplateRepository
    ): LicensePlateTemplateService {
        return LicensePlateTemplateService(templateRepository)
    }
} 