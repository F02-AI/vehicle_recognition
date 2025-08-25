# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Development Commands

Use the provided `run.sh` helper script for common operations:

```bash
# Build the project
./run.sh build

# Clean the build
./run.sh clean

# Install the app on connected device/emulator
./run.sh install

# Build and install the app
./run.sh run

# Build debug APK
./run.sh apk

# Build without deprecation warnings
./run.sh quiet-build

# Show deprecation fix recommendations
./run.sh fix-deprecations
```

**Testing**: Run tests with `./gradlew test` for shared module tests. Android tests require a device/emulator: `./gradlew connectedAndroidTest`

**Linting**: No explicit lint command configured - inspect build output for warnings.

## Architecture Overview

This is a Kotlin Multiplatform vehicle recognition Android app using:

- **MVVM Architecture**: Compose UI + ViewModels + Repositories
- **Dependency Injection**: Hilt for all major components
- **ML/AI Stack**: TensorFlow Lite models for license plate detection and OCR
- **Database**: Room for local watchlist storage
- **Settings**: DataStore for preferences persistence

### Key Modules

- **androidApp/**: Main Android application module with UI, ViewModels, dependency injection
- **shared/**: Kotlin Multiplatform module with domain logic, models, validation
- **ML Processing**: Custom TensorFlow Lite models for vehicle and license plate detection

### Core Processing Pipeline

```
Camera → License Plate Detection → OCR Processing → Vehicle Matching → Alert System
```

### Package Structure

```
com.example.vehiclerecognition/
├── data/ (repositories, database, models)
├── di/ (Hilt dependency injection modules)
├── domain/ (business logic, validation - shared module)
├── ml/ (detection models, OCR engines, processors)
├── ui/ (Compose screens, ViewModels, navigation)
└── platform/ (Android-specific implementations)
```

## Development Guidelines

**ML Models**: TensorFlow Lite models located in `androidApp/src/main/assets/models/`. Models are loaded asynchronously on background threads.

**State Management**: All ViewModels use StateFlow for reactive UI. Camera processing runs on separate executor threads.

**Detection Modes**: Six different matching modes (LP_ONLY, LP_COLOR, LP_TYPE, etc.) configured via settings.

**Country Support**: Currently supports Israeli and UK license plate formats with dedicated validation.

**Resource Management**: ML models and camera resources require proper cleanup - check existing ViewModels for patterns.

**Testing**: Shared module contains domain logic tests. Android-specific UI testing requires connected device setup.

The project follows clean architecture principles with clear separation between UI, domain, and data layers. Most business logic resides in the shared module for potential multi-platform expansion.