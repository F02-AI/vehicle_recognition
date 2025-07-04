# Vehicle Recognition App: Project Structure

This document provides a comprehensive overview of the Vehicle Recognition application's architecture and file organization. The project follows a Kotlin Multiplatform approach with a focus on Android.

## 1. Top-Level Structure

```
vehicle_recognition/
├── androidApp/            # Android-specific implementation
├── shared/                # Cross-platform shared code
├── gradle/                # Gradle wrapper files and properties
├── .gradle/              # Gradle build cache (not versioned)
├── build/                # Build outputs (not versioned)
├── App_Workflow_Doc.md   # Documentation of application workflow
├── PRD_Vehichle_Recognition.md  # Product Requirements Document
├── build.gradle.kts      # Root build configuration
├── gradle.properties     # Gradle global properties
├── gradlew               # Gradle wrapper script (Unix)
├── gradlew.bat           # Gradle wrapper script (Windows)
├── run.sh                # Custom build/run script
├── README.md             # Project overview and documentation
└── settings.gradle.kts   # Gradle settings (module definitions)
```

## 2. `androidApp` Module

This module contains Android-specific implementation details.

```
androidApp/
├── build.gradle.kts      # Android module build config
├── src/
    └── main/
        ├── AndroidManifest.xml  # Android app manifest
        ├── java/               # Kotlin/Java source code
        │   └── com/example/vehiclerecognition/
        │       ├── VehicleRecognitionApp.kt  # Application class
        │       ├── MainActivity.kt           # Main activity
        │       ├── data/                     # Data layer (DB, preferences, etc.)
        │       ├── di/                       # Dependency injection
        │       ├── platform/                 # Android-specific implementations
        │       └── ui/                       # UI components
        │           ├── camera/               # Camera screen
        │           ├── navigation/           # Navigation components
        │           ├── watchlist/            # Watchlist screen
        │           └── settings/             # Settings screen
        └── res/                  # Android resources (layouts, strings, etc.)
```

### Key Components in `androidApp`:

- **VehicleRecognitionApp.kt**: Application class that sets up Hilt for dependency injection
- **MainActivity.kt**: Main entry point, hosts the navigation controller
- **ui/**: Contains feature-specific UI components organized by screen
  - **camera/**: Camera functionality for vehicle detection
  - **watchlist/**: Management of vehicle watchlist entries
  - **settings/**: App settings and detection mode configuration
  - **navigation/**: Navigation system connecting all screens
- **data/**: Android-specific implementations of repositories and data sources
- **di/**: Dependency injection modules and components (using Hilt)
- **platform/**: Platform-specific implementations of interfaces defined in shared module

## 3. `shared` Module

This module contains cross-platform code shared between different targets (currently only Android is implemented, but could be extended to iOS).

```
shared/
├── build.gradle.kts      # Shared module build config
├── src/
    ├── commonMain/       # Common code for all platforms
    │   └── kotlin/com/example/vehiclerecognition/
    │       ├── domain/   # Business logic
    │       │   ├── logic/       # Core business logic
    │       │   ├── platform/    # Platform abstractions
    │       │   ├── repository/  # Repository interfaces
    │       │   └── validation/  # Validation logic
    │       └── model/    # Data models/entities
    └── commonTest/       # Tests for common code
```

### Key Components in `shared`:

- **domain/**: Contains the core business logic
  - **logic/**: Implements business rules (e.g., VehicleMatcher)
  - **repository/**: Defines interfaces for data access
  - **validation/**: Contains validation rules (e.g., LicensePlateValidator)
  - **platform/**: Defines interfaces for platform-specific features
- **model/**: Contains data models and entities
  - **WatchlistEntry.kt**: Represents a vehicle in the watchlist
  - **DetectionMode.kt**: Defines detection/matching modes
  - **VehicleColor.kt**: Enum of supported vehicle colors
  - **VehicleType.kt**: Enum of supported vehicle types

## 4. Architecture Overview

The application follows a clean architecture approach with clear separation of concerns:

### Layers:

1. **Presentation Layer** (in `androidApp/ui`)
   - Jetpack Compose UI components
   - ViewModels that manage UI state and interact with domain layer

2. **Domain Layer** (in `shared/domain`)
   - Business logic
   - Repository interfaces
   - Pure Kotlin code with no external dependencies

3. **Data Layer** (in `androidApp/data`)
   - Android-specific implementations of repositories
   - Database access
   - External API clients

### Data Flow:

1. UI events are captured by Composables
2. ViewModels process events and call domain layer
3. Domain layer executes business logic
4. Repository interfaces access data through their implementations
5. Data changes flow back through the same layers via Flow/State

## 5. Dependency Injection

The application uses Hilt for dependency injection:

- `VehicleRecognitionApp.kt` is annotated with `@HiltAndroidApp`
- ViewModels are annotated with `@HiltViewModel`
- Modules in the `di` package define how dependencies are provided

## 6. Key Technologies

- **Kotlin Multiplatform**: For sharing code between platforms
- **Jetpack Compose**: For declarative UI
- **Hilt**: For dependency injection
- **Room**: For local database storage
- **Kotlin Coroutines/Flow**: For asynchronous operations and reactive programming
- **CameraX**: For camera functionality
- **Material 3**: For UI components and styling

## 7. Build System

The project uses Gradle with Kotlin DSL:

- Root `build.gradle.kts`: Defines common configuration
- Module-specific build files: Define module-specific dependencies
- `settings.gradle.kts`: Defines included modules

## 8. Testing

- `commonTest`: Contains tests for shared platform-independent code
- Android-specific tests would be in `androidApp/src/test` and `androidApp/src/androidTest`

## 9. Documentation

- **App_Workflow_Doc.md**: Documents the application workflow
- **PRD_Vehichle_Recognition.md**: Product Requirements Document
- **README.md**: Project overview and basic documentation 