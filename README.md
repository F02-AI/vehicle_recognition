# Vehicle Recognition Android PoC

This document provides comprehensive instructions for setting up, building, and running the Vehicle Recognition Android Proof of Concept (PoC) application. It is designed to guide developers, especially those new to Android development or Android Studio.

## Table of Contents

1.  [Prerequisites](#prerequisites)
2.  [Project Setup](#project-setup)
    *   [Cloning the Repository](#cloning-the-repository)
    *   [Importing into Android Studio](#importing-into-android-studio)
3.  [Building the Project](#building-the-project)
4.  [Running the Application](#running-the-application)
    *   [Using an Android Emulator](#using-an-android-emulator)
    *   [Using a Physical Device](#using-a-physical-device)
5.  [Project Structure Overview](#project-structure-overview)
6.  [Dependency Management (Hilt)](#dependency-management-hilt)
    *   [Project-level `build.gradle.kts`](#project-level-buildgradlekts-or-buildgradle)
    *   [App-level `build.gradle.kts`](#app-level-buildgradlekts-or-buildgradle)
    *   [Application Class](#application-class)
    *   [Enable Java 8+ Features](#enable-java-8-features-if-not-already)
7.  [Troubleshooting](#troubleshooting)
8.  [Running Gradle Sync and Building the App](#running-gradle-sync-and-building-the-app)

## Helper Script

For convenience, a helper shell script is provided to simplify common tasks:

```bash
# Make the script executable (only needed once)
chmod +x run.sh

# Show available commands
./run.sh help

# Build the project
./run.sh build

# Install the app on a connected device/emulator
./run.sh install

# Build and install the app
./run.sh run

# Build without showing deprecation warnings
./run.sh quiet-build

# Show recommendations for fixing Gradle deprecation warnings
./run.sh fix-deprecations

# Update JDK configuration
./run.sh update-jdk
```

## Android Studio Run Configuration

A pre-configured run configuration (`.idea/runConfigurations/androidApp.xml`) is included in the project. 

If Android Studio shows "Run configuration androidApp is not supported in the current project. Cannot obtain the package":

1. Close and reopen the project after building it with `./run.sh build`
2. Make sure you're using JDK 17 in Android Studio (File > Project Structure > SDK Location)
3. Verify that the app build completes successfully

If issues persist, use the command line approach with `./run.sh run` to build and install the app.

## 1. Prerequisites

Before you begin, ensure you have the following installed on your system:

*   **Android Studio:** The official Integrated Development Environment (IDE) for Android app development. Download it from [developer.android.com/studio](https://developer.android.com/studio). The current stable version is Android Studio Meerkat Feature Drop (2024.3.2).

    **System Requirements:**
    * **Windows:** 64-bit Microsoft Windows 10 or higher; 8 GB RAM (16 GB for emulator); CPU with virtualization support (Intel VT-x or AMD-V); 8 GB free space (16 GB with emulator)
    * **macOS:** macOS 12 (Monterey) or higher; 8 GB RAM (16 GB for emulator); Apple M1 chip or newer (or 6th generation Intel Core); 8 GB free space (16 GB with emulator)
    * **Linux:** Any 64-bit Linux distribution with Gnome, KDE, or Unity DE; GNU C Library (glibc) 2.31 or later; 8 GB RAM (16 GB for emulator); CPU with virtualization support; 8 GB free space (16 GB with emulator)

*   **Java Development Kit (JDK):** Android Studio bundles its own OpenJDK distribution, so a separate installation is typically not necessary.

*   **Git:** For cloning the project repository. Download it from [git-scm.com](https://git-scm.com/downloads).

## 2. Project Setup

### Cloning the Repository

1.  Open your terminal or command prompt.
2.  Navigate to the directory where you want to store the project.
3.  Clone the repository using the following command:
    ```bash
    git clone <repository_url>
    cd <project_directory_name>
    ```
    Replace `<repository_url>` with the actual URL of this Git repository and `<project_directory_name>` with the name of the directory created by the clone.

### Importing into Android Studio

1.  Launch Android Studio.
2.  From the welcome screen, select "Open" or if you have an existing project open, go to **File > Open...**.
3.  Navigate to the directory where you cloned the repository and select the root project folder.
4.  Click "OK".
5.  Android Studio will import the project and Gradle will start syncing. This process might take a few minutes, especially for the first time, as it downloads dependencies. You can monitor the progress in the bottom status bar.

## 3. Building the Project

Once Gradle sync is complete, you can build the project:

1.  In Android Studio, go to **Build > Make Project** (or click the hammer icon in the toolbar).
2.  Alternatively, you can use **Build > Rebuild Project** to clean and then build the project from scratch.
3.  Monitor the build progress in the "Build" tool window (usually at the bottom of Android Studio). Successful builds will show a "BUILD SUCCESSFUL" message.

## 4. Running the Application

You can run the application on an Android Emulator or a physical Android device.

### Using an Android Emulator

1.  **Set up an Emulator:**
    *   In Android Studio, go to **Tools > Device Manager** (previously called AVD Manager).
    *   Click on "Create Device".
    *   Select a device definition (e.g., Pixel 7) and click "Next".
    *   Select a system image (recommend using at least Android 11/API level 30 or higher). If the image is not downloaded, click the "Download" link next to it.
    *   Click "Next", configure optional settings or use defaults, and click "Finish".

2.  **Launch the Emulator:**
    *   You can either launch the emulator separately by clicking the play button next to the device in the Device Manager, or proceed to the next step.

3.  **Run the App:**
    *   In the Android Studio toolbar, select your emulator from the target device dropdown menu next to the "Run" button (green play icon).
    *   Click the "Run 'app'" button.
    *   Android Studio will build the app (if necessary), install it on the emulator, and launch it.

    **Note:** Running the emulator requires significant system resources. For optimal performance, ensure your computer meets the recommended system requirements (16GB RAM, recent CPU with virtualization support).

### Using a Physical Device

1.  **Enable Developer Options and USB Debugging on your Android device:**
    *   Go to **Settings > About phone**.
    *   Tap on "Build number" seven times until you see a message saying "You are now a developer!".
    *   Go back to **Settings > System > Developer options** (the location might vary depending on the device manufacturer and Android version).
    *   Enable "USB debugging".

2.  **Connect your device to your computer via USB.**

3.  **Authorize your computer:**
    *   On your device, a dialog should appear asking "Allow USB debugging?". Check "Always allow from this computer" and tap "Allow".

4.  **Select the Device:**
    *   In the Android Studio toolbar, your connected device should now appear in the device selection dropdown menu. Select it.

5.  **Run the App:**
    *   Click the "Run 'app'" button (the green play icon).
    *   Android Studio will build the app, install it on your device, and launch it.

## 5. Project Structure Overview

A typical Android project in Android Studio has the following key directories:

*   `app/`: This is the main module for your application.
    *   `src/main/java/`: Contains your Kotlin or Java source code, organized by package name.
    *   `src/main/res/`: Contains all non-code resources:
        *   `drawable/`: Images and other drawable resources.
        *   `layout/`: XML files defining user interfaces.
        *   `mipmap/`: Launcher icons.
        *   `values/`: XML files for strings, colors, dimensions, styles, etc.
    *   `src/main/AndroidManifest.xml`: The application manifest file. It describes essential information about your app to the Android build tools, the Android operating system, and Google Play.
    *   `build.gradle.kts` (or `build.gradle`): The build script for the `app` module.
*   `build.gradle.kts` (or `build.gradle`): The top-level build script for the entire project.
*   `gradle/wrapper/`: Contains Gradle wrapper files, which help ensure a consistent Gradle version for building.
*   `shared/`: (If present in this Kotlin Multiplatform project) Contains code shared between Android and potentially other platforms.

## 6. Dependency Management (Hilt)

This project uses Hilt for dependency injection. The following setup is crucial for Hilt to work correctly.

### Project-level `build.gradle.kts` (or `build.gradle`)

Ensure you have the Hilt Gradle plugin classpath defined in your project-level `build.gradle.kts` (e.g., `/build.gradle.kts`) file:

```kotlin
// build.gradle.kts (Project level)
plugins {
    // ... other plugins
    id("com.google.dagger.hilt.android") version "2.50" apply false // Or the latest Hilt version
}
```

For Groovy-based `build.gradle`:
```groovy
// build.gradle (Project level)
buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        // ... other classpath dependencies
        classpath "com.google.dagger:hilt-android-gradle-plugin:2.50" // Or the latest Hilt version
    }
}

plugins {
    // ... other plugins
}
```

### App-level `build.gradle.kts` (or `build.gradle`)

In your app-level `build.gradle.kts` file (e.g., `/androidApp/build.gradle.kts` or `/app/build.gradle.kts`):

**Apply the Hilt plugin and Kotlin Kapt (or KSP):**

```kotlin
// androidApp/build.gradle.kts (App level)
plugins {
    id("com.android.application")
    kotlin("android")
    id("kotlin-kapt") // For Kapt
    // or id("com.google.devtools.ksp") version "<ksp-version>" // For KSP
    id("com.google.dagger.hilt.android")
}

android {
    // ... android configurations (compileSdk, defaultConfig, etc.)
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8 // Or higher
        targetCompatibility = JavaVersion.VERSION_1_8 // Or higher
    }
    kotlinOptions {
        jvmTarget = "1.8" // Or higher
    }
}

dependencies {
    // ... other dependencies

    // Hilt Core
    implementation("com.google.dagger:hilt-android:2.50") // Use the same version as the plugin
    kapt("com.google.dagger:hilt-compiler:2.50") // For Kapt
    // For KSP: replace kapt with ksp
    // ksp("com.google.dagger:hilt-compiler:2.50")

    // Hilt Navigation Compose (if using Jetpack Navigation with Compose)
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0") // Or latest

    // Room (if using, ensure Kapt/KSP is configured for Room as well)
    // implementation("androidx.room:room-runtime:<room_version>")
    // implementation("androidx.room:room-ktx:<room_version>")
    // kapt("androidx.room:room-compiler:<room_version>")
    // For KSP with Room:
    // ksp("androidx.room:room-compiler:<room_version>")
}

// Allow references to generated code (needed for Hilt)
kapt {
    correctErrorTypes = true
}
```

**Note on KSP vs. Kapt:**
KSP (Kotlin Symbol Processing) is generally preferred over Kapt for better build performance. If you choose KSP:
1.  Add the KSP plugin: `id("com.google.devtools.ksp") version "<latest-ksp-version>" apply false` to project-level `build.gradle.kts` and `id("com.google.devtools.ksp")` to app-level.
2.  Replace `kapt` configurations with `ksp` for Hilt and Room compilers.

### Application Class

Ensure your Application class is annotated with `@HiltAndroidApp`:

```kotlin
// VehicleRecognitionApp.kt
package com.example.vehiclerecognition // Ensure this matches your package name

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class VehicleRecognitionApp : Application() {
    // Application-specific logic can go here
}
```

And register it in your `AndroidManifest.xml` (usually in `app/src/main/AndroidManifest.xml`):

```xml
<!-- AndroidManifest.xml -->
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="com.example.vehiclerecognition"> <!-- Ensure this matches your package name -->

    <application
        android:name=".VehicleRecognitionApp"
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:theme="@style/AppTheme"> <!-- Ensure your theme is defined -->
        <activity
            android:name=".MainActivity" /* Replace with your main activity */
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>

</manifest>
```
Replace `com.example.vehiclerecognition` with your actual package name and `.MainActivity` with your actual main launcher activity if different.

### Enable Java 8+ Features (if not already)

Ensure your `compileOptions` and `kotlinOptions` in the app-level `build.gradle.kts` target Java 8 or higher, as shown in the Hilt setup example above. This is often required for libraries like Hilt.

## 7. Troubleshooting

Common issues and how to resolve them:

*   **Gradle Sync Failed:**
    *   Check your internet connection.
    *   Ensure Android Studio and Gradle plugin versions are compatible. Android Studio Meerkat Feature Drop (2024.3.2) is compatible with Android Gradle Plugin (AGP) versions 3.2-8.10.
    *   Look at the "Build" tool window for specific error messages.
    *   Try **File > Invalidate Caches and Restart**.
    *   Ensure you have the correct SDKs downloaded via **Tools > SDK Manager**.
*   **Emulator Performance Issues:**
    *   Enable hardware acceleration in the emulator settings.
    *   Reduce the memory allocated to the emulator if your system has limited resources.
    *   Close unnecessary applications and background processes to free up system resources.
    *   Consider using a physical device if your computer doesn't meet the recommended specifications.
*   **Hilt Errors:**
    *   Verify Hilt version consistency between the plugin and dependencies.
    *   Ensure Kapt/KSP setup is correct for all annotation processors (Hilt, Room, etc.).
    *   Confirm the `@HiltAndroidApp` annotation on your Application class and its registration in the `AndroidManifest.xml`.
    *   Clean project (**Build > Clean Project**) and rebuild (**Build > Rebuild Project**).
*   **App Crashes on Launch:**
    *   Check **Logcat** (View > Tool Windows > Logcat) in Android Studio for error messages and stack traces. This is crucial for diagnosing runtime issues. Filter by your app's package name and "Error" level.
*   **Device Not Detected:**
    *   Ensure USB debugging is enabled and authorized (for physical devices).
    *   Try a different USB cable or port.
    *   Restart Android Studio and/or your computer.
    *   For emulators, ensure the AVD is running correctly and has enough system resources.

This comprehensive guide should help you get the Vehicle Recognition Android PoC up and running. If you encounter further issues, consult the [official Android developer documentation](https://developer.android.com/docs) or seek help from online communities, providing specific error messages from Logcat or the build output.

## Running Gradle Sync and Building the App

Once you have imported the project into Android Studio and ensured the Hilt dependencies and plugins are correctly configured as described above, you need to synchronize your project with the Gradle files. Android Studio usually prompts you to do this automatically if it detects changes in your `build.gradle` files.

### 1. Gradle Sync:

*   **Automatic Sync:** If you see a bar at the top of Android Studio saying "Gradle files have changed since last project sync. A project sync may be necessary for the IDE to work properly," click on **"Sync Now"**.
*   **Manual Sync:** If you don't see the prompt or want to trigger it manually:
    *   Go to **File > Sync Project with Gradle Files** from the Android Studio menu.
    *   Alternatively, you can click the **"Sync Project with Gradle Files"** button (often looks like an elephant with a green arrow) in the toolbar.

This process will download any new dependencies and ensure your project is correctly configured according to your `build.gradle` files.

### 2. Building the App:

Once the Gradle sync is successful, you can build your application:

*   Go to **Build > Make Project** (or `Cmd+F9` on macOS / `Ctrl+F9` on Windows/Linux). This will compile your project and show any build errors in the "Build" window.
*   To create an APK or AAB for release or debugging:
    *   **Build > Build Bundle(s) / APK(s) > Build APK(s)** (for a debug APK)
    *   **Build > Build Bundle(s) / APK(s) > Build Bundle(s)** (for an Android App Bundle, typically used for Play Store releases)
    *   **Build > Generate Signed Bundle / APK...** (for a release-signed APK/AAB)

### 3. Running the App:

To run your app on an emulator or a connected physical device:

*   Select your target device from the **"Running Devices"** dropdown menu in the toolbar (it usually shows "app" next to it).
*   Click the **"Run 'app'"** button (green play icon) in the toolbar, or go to **Run > Run 'app'** (or `Ctrl+R` on macOS / `Shift+F10` on Windows/Linux).

### 4. Cleaning the Project:

Sometimes, you might encounter build issues that can be resolved by cleaning the project:

*   Go to **Build > Clean Project**.
*   After cleaning, you might want to **Build > Rebuild Project**.

These are the basic steps to get your project configured, built, and running in Android Studio. If you encounter any specific errors during these processes, the "Build" window in Android Studio will typically provide detailed messages to help you diagnose the issue.

## Handling Gradle Deprecation Warnings

When building the project, you may see deprecation warnings that make the project incompatible with Gradle 9.0. The project includes a helper task to identify and provide recommendations for fixing these warnings:

```bash
./gradlew identifyDeprecations
```

### Common deprecation issues:

1. **Mutating configurations after they've been resolved**:
   - This is often caused by plugins applying changes late in the build process
   - Most of these warnings can be safely ignored for now, as they're coming from internal implementations of the Android Gradle Plugin and Kotlin Multiplatform Plugin
   - These will be fixed in future versions of the respective plugins

2. **BuildIdentifier.getName() deprecation**:
   - This is an internal implementation detail of the Kotlin Multiplatform plugin
   - Wait for the Kotlin Multiplatform plugin to update

3. **ReportingExtension.getBaseDir() deprecation**:
   - This is used by the Kotlin Multiplatform plugin
   - Will be fixed in a future update

If you need to suppress these warnings for development, you can use:

```bash
./gradlew build --warning-mode none
```

Or update `gradle.properties` to set:
```
org.gradle.warning.mode=none
```

When the Android Gradle Plugin and Kotlin Multiplatform Plugin are updated to newer versions, these deprecation warnings will be resolved. 