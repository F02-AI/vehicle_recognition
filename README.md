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

## 1. Prerequisites

Before you begin, ensure you have the following installed on your system:

*   **Android Studio:** The official Integrated Development Environment (IDE) for Android app development. Download it from [developer.android.com/studio](https://developer.android.com/studio). During installation, make sure to install the Android SDK, Android SDK Platform-Tools, and Android SDK Build-Tools.
*   **Java Development Kit (JDK):** Android Studio often bundles its own JDK, but it's good practice to have a compatible version installed. JDK 11 or higher is recommended. You can download it from [Oracle](https://www.oracle.com/java/technologies/javase-jdk11-downloads.html) or use an open-source alternative like OpenJDK.
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
2.  If you see the Welcome screen, click on "**Open**".
    *   If you have an existing project open, go to **File > Open...**.
3.  Navigate to the directory where you cloned the repository and select the root project folder.
4.  Click "**OK**".
5.  Android Studio will import the project and Gradle will start syncing. This process might take a few minutes, especially for the first time, as it downloads dependencies. You can monitor the progress in the bottom status bar.

## 3. Building the Project

Once Gradle sync is complete, you can build the project:

1.  In Android Studio, go to **Build > Make Project** (or click the hammer icon in the toolbar).
2.  Alternatively, you can use **Build > Rebuild Project** to clean and then build the project from scratch.
3.  Monitor the build progress in the "**Build**" tool window (usually at the bottom of Android Studio). Successful builds will show a "BUILD SUCCESSFUL" message.

## 4. Running the Application

You can run the application on an Android Emulator or a physical Android device.

### Using an Android Emulator

1.  **Set up an Emulator (if you haven't already):**
    *   In Android Studio, go to **Tools > AVD Manager** (Android Virtual Device Manager).
    *   Click on "**Create Virtual Device...**".
    *   Select a device definition (e.g., Pixel 6) and click "**Next**".
    *   Select a system image (choose a recent API level). If the image is not downloaded, click the "Download" link next to it.
    *   Click "**Next**", give your AVD a name if desired, and click "**Finish**".
2.  **Select the Emulator:**
    *   In the Android Studio toolbar, you'll see a dropdown menu next to the "Run" button (green play icon). Select your newly created emulator from this list.
3.  **Run the App:**
    *   Click the "**Run 'app'**" button (the green play icon).
    *   Android Studio will build the app (if necessary), install it on the emulator, and launch it.

### Using a Physical Device

1.  **Enable Developer Options and USB Debugging on your Android device:**
    *   Go to **Settings > About phone**.
    *   Tap on "**Build number**" seven times until you see a message saying "You are now a developer!".
    *   Go back to **Settings > System > Developer options** (the location might vary slightly depending on the device manufacturer and Android version).
    *   Enable "**USB debugging**".
2.  **Connect your device to your computer via USB.**
3.  **Authorize your computer:**
    *   On your device, a dialog should appear asking "Allow USB debugging?". Check "**Always allow from this computer**" and tap "**Allow**".
4.  **Select the Device:**
    *   In the Android Studio toolbar, your connected device should now appear in the device selection dropdown menu. Select it.
5.  **Run the App:**
    *   Click the "**Run 'app'**" button (the green play icon).
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
    *   Ensure Android Studio and Gradle plugin versions are compatible.
    *   Look at the "Build" tool window for specific error messages.
    *   Try **File > Invalidate Caches / Restart... > Invalidate and Restart**.
    *   Ensure you have the correct SDKs downloaded via **Tools > SDK Manager**.
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

This comprehensive guide should help you get the Vehicle Recognition Android PoC up and running. If you encounter further issues, consult the official Android developer documentation or seek help from online communities, providing specific error messages from Logcat or the build output. 