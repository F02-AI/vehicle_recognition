# Vehicle Recognition App - Client Overview

## What It Does

The Vehicle Recognition App is a mobile application that uses your Android phone's camera to automatically detect and identify vehicles in real-time. It can recognize license plates (Israeli format), vehicle colors, and vehicle types (Car, Motorcycle, Truck), then alert you when vehicles from your watchlist appear.

## Key Features

### 🎯 **Real-Time Detection**
- Point your camera at vehicles and get instant recognition
- Detects license plates, colors, and vehicle types simultaneously
- Works in real-time with live camera feed

### 📋 **Watchlist Management**
- Add vehicles you want to monitor (license plate, color, type)
- Get instant audio alerts when watchlisted vehicles are detected
- Flexible matching: license plate only, color, type, or any combination

### 🔒 **Complete Privacy**
- All processing happens on your device - no internet required
- No data sent to external servers
- Works completely offline

### ⚙️ **Flexible Configuration**
- Multiple detection modes (license plate only, color, type combinations)
- Adjustable sensitivity settings
- Camera zoom with persistent settings

## How It Works

1. **Open the app** and point your camera at vehicles
2. **Add vehicles to your watchlist** using the Watchlist tab
3. **Configure detection settings** in the Settings tab
4. **Receive alerts** when watchlisted vehicles are detected

## System Requirements

- **Android Device**: Android 7.0 (API 24) or higher
- **Camera**: Rear-facing camera with autofocus
- **Performance**: High-end device recommended (tested on Samsung Galaxy S24)
- **Storage**: ~200MB free space
- **Permissions**: Camera access required

## Detection Capabilities

### License Plates
- Israeli format support
- Automatic format validation
- Works at distances up to 20-40 meters with zoom

### Vehicle Types
- **Car**: Standard passenger vehicles
- **Motorcycle**: Two-wheeled vehicles
- **Truck**: Commercial and large vehicles

### Vehicle Colors
- Red, Blue, Green, White, Black, Gray, Yellow
- Primary and secondary color detection

## Current Status

This is a **Proof of Concept (PoC)** application demonstrating real-time vehicle recognition capabilities on mobile devices. The app showcases the technical feasibility of on-device AI processing for vehicle monitoring applications.

## Usage Instructions

### Getting Started
1. Install the app on your Android device
2. Grant camera permissions when prompted
3. The app opens to the Camera screen by default

### Adding Vehicles to Watch
1. Tap **"Watchlist"** at the bottom
2. Tap **"Add Vehicle"** button
3. Enter vehicle details:
   - License plate (optional)
   - Vehicle type (required)
   - Vehicle color (required)
4. Tap **"Add"** to save

### Configuring Detection
1. Tap **"Settings"** at the bottom
2. Choose your **Detection Mode**:
   - **License Plate Only**: Match by license plate number
   - **License Plate + Color**: Match both license plate and color
   - **Color + Type**: Match vehicles by color and type only
   - **Color Only**: Match vehicles by color only
3. Adjust other settings as needed

### Using the Camera
1. Return to **"Camera"** tab
2. Point camera at vehicles
3. Green boxes show detected vehicles
4. Red alert indicate watchlist matches
5. Audio alert plays when matches are found
6. Pinch to zoom for distant vehicles

---

**Note**: This application is designed for demonstration and evaluation purposes. Performance may vary based on device capabilities, lighting conditions, and camera quality. 