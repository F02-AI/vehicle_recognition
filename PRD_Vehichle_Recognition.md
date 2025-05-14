## **Product Requirements Document: Vehicle Recognition Android PoC \- Milestone 1 (Refined)**

### **1\. Introduction**

This document outlines the refined requirements for Milestone 1 of the Vehicle Recognition Android Proof of Concept (PoC) application. This phase establishes the core architecture, integrates the device camera with basic controls (including zoom), implements watchlist management using SQLite with specific license plate format support, and provides configuration for detection modes and audible alerts. The application is intended to run offline on an Android device, leveraging integrated computer vision models (assumed available but not developed in this milestone).  
The development will utilize Kotlin Multiplatform to share logic where appropriate and follow Android development best practices.

### **2\. Goals & Objectives for Milestone 1**

The primary objectives for Milestone 1 are:

* Successfully set up the project structure using Kotlin Multiplatform.  
* Integrate the device camera, display a live feed, and implement pinch-to-zoom functionality.  
* Handle necessary camera permissions.  
* Implement a local watchlist database using SQLite, supporting specific Israeli license plate formats.  
* Develop the core logic to match detected vehicle attributes against the watchlist based on configurable criteria, supporting *only* the specified LP formats in the matching process.  
* Implement an audible alert mechanism for watchlist matches.  
* Create a basic settings screen to allow the user to select detection modes.  
* Ensure the selected detection mode persists across app sessions.  
* Establish basic navigation between the main app screens (Camera, Watchlist, Settings).

### **3\. Scope (Milestone 1 Features)**

Milestone 1 will include the following features:

* Project Setup: Establish a Kotlin Multiplatform project structure with Android as the target platform.  
* Camera Integration:  
  * Initialize and display the live camera feed on the main screen.  
  * Request camera permissions upon first launch if not granted.  
  * Handle the case where camera permission is denied (basic message).  
  * Implement pinch-to-zoom functionality on the camera preview to adjust the zoom level. The zoom setting should persist while the camera is active.  
* Real-time Processing Foundation: Set up the framework to receive and process frames from the camera feed. (Note: Integration of the actual LP, Color, Type models is assumed to be possible within this framework, but the models themselves are not developed as part of this milestone).  
* Watchlist Database:  
  * Implement a local database using SQLite (e.g., via Room or SQLDelight) to store vehicle entries.  
  * Each entry must store License Plate (String), Vehicle Type (Car, Motorcycle, Truck), and Vehicle Color (Red, Blue, Green, White, Black, Gray, Yellow).  
  * Support adding new entries to the database (via minimal UI).  
  * Support deleting entries from the database (via minimal UI).  
  * Crucially, the database and associated input logic MUST support and potentially validate license plates using *only* the following formats: `NN-NNN-NN`, `NNN-NN-NNN`, `N-NNNN-NN` (where N is a digit).  
* Detection Modes & Settings:  
  * Create a dedicated "Settings" screen accessible from the main UI.  
  * Display options for detection modes: LP, LP+Color, LP+Type, LP+Color+Type, Color+Type, Color.  
  * Allow the user to select one of these modes (e.g., via radio buttons or toggles).  
  * Persist the selected mode locally so it is remembered on subsequent app launches.  
* Matching Logic:  
  * Implement the logic that compares the attributes of a detected vehicle (LP, Color, Type \- assumed received from processing) against the entries in the watchlist database.  
  * The comparison must strictly adhere to the criteria defined by the currently selected detection mode.  
  * The matching logic MUST only consider detected License Plates that conform to one of the specified Israeli formats (`NN-NNN-NN`, `NNN-NN-NNN`, `N-NNNN-NN`) for LP-based matching modes.  
* Alerting Mechanism:  
  * Trigger an audible alert sound when the Matching Logic identifies a match based on the current settings.  
  * The alert sound must play for a duration of exactly 2 seconds.  
  * The alert sound will not repeat immediately for the same detection event or simultaneous matches.  
  * The user cannot dismiss the alert manually.  
* Basic Navigation: Implement simple navigation flows between the Camera view, Watchlist list view, and Settings screen. A minimal UI for adding/deleting watchlist entries will be included to test the database functionality and LP format handling.

### **4\. Out of Scope (for Milestone 1\)**

The following features and aspects, while potentially part of the overall PoC, are explicitly excluded from Milestone 1:

* Sophisticated UI/UX design beyond basic functional layouts for screens and navigation.  
* Full implementation of the Watchlist Management UI workflow (Section 4.2) including detailed forms or picture association (basic add/delete UI will exist to test the DB and LP format handling).  
* Advanced error handling beyond basic camera permission denial and generic detection failure (as described in Section 5, Error/Exception Handling in the workflow doc).  
* Specific requirements for the alert sound itself (Section 8, Clarifications in the workflow doc).  
* Visual indication on the camera screen for detected vehicles, whether they match or not (Section 8, Clarifications in the workflow doc).  
* Onboarding or tutorial screens (Section 8, Clarifications in the workflow doc).  
* Integration or development of the actual computer vision models (LP, Color, Type classifiers) \- these are assumed to be available components used *by* the app logic developed in this milestone.

### **5\. User Stories (Milestone 1 Focus)**

As an Operator:

* I want to open the app and immediately see the live camera feed so I can begin monitoring.  
* I want the app to ask for camera permission if needed so I can grant access.  
* I want to pinch the camera view to zoom in or out on distant vehicles.  
* I want the app to process the camera feed to identify vehicle attributes (LP, Color, Type).  
* I want to add vehicles with specific Israeli license plate formats (`NN-NNN-NN`, `NNN-NN-NNN`, `N-NNNN-NN`) to a local watchlist database using SQLite.  
* I want to remove vehicles from the local watchlist database.  
* I want the app to compare detected vehicles against my watchlist based on specific criteria, *only* considering detected license plates that match the required Israeli formats.  
* I want to receive a distinct audible alert when a vehicle matching my watchlist and current criteria is detected.  
* I want to be able to choose which vehicle attributes are used for matching (e.g., LP only, LP+Color, etc.) via a Settings screen.  
* I want the app to remember my chosen detection mode setting between uses.  
* I want to navigate easily between the camera view, watchlist list, and settings screens.

### **6\. Functional Requirements**

* FR 1.1 \- Camera Display: The app MUST display the live feed from the device's primary camera on the main screen upon launch.  
* FR 1.2 \- Camera Permission: The app MUST request `android.permission.CAMERA` if it has not been granted. If denied, the app SHOULD display a message instructing the user to grant permission via device settings.  
* FR 1.3 \- Frame Processing Hook: The app MUST provide a mechanism to continuously receive frames from the camera feed, ready for processing by integrated detection models.  
* FR 1.4 \- Pinch-to-Zoom: The app MUST allow the user to adjust the camera's zoom level using pinch gestures on the camera preview.  
* FR 1.5 \- Watchlist Database (SQLite): The app MUST implement a local persistent data store using SQLite (e.g., via Room or SQLDelight) to store watchlist entries. Each entry MUST include fields for License Plate (String), Vehicle Type (Enum: Car, Motorcycle, Truck), and Vehicle Color (Enum: Red, Blue, Green, White, Black, Gray, Yellow).  
* FR 1.6 \- Add Watchlist Entry (Data & Format): The database MUST support adding new vehicle entries. Input for the License Plate MUST be validated to ensure it conforms to one of the required Israeli formats: `NN-NNN-NN`, `NNN-NN-NNN`, or `N-NNNN-NN`. Entries with invalid formats SHOULD NOT be added.  
* FR 1.7 \- Delete Watchlist Entry (Data): The database MUST support deleting existing vehicle entries.  
* FR 1.8 \- Settings Screen: The app MUST include a dedicated UI screen for settings.  
* FR 1.9 \- Detection Mode Selection: The Settings screen MUST present the user with options for the following detection modes: `LP`, `LP+Color`, `LP+Type`, `LP+Color+Type`, `Color+Type`, `Color`. The user MUST be able to select one mode.  
* FR 1.10 \- Persist Detection Mode: The app MUST save the user's selected detection mode locally and load it upon app launch.  
* FR 1.11 \- Matching Logic (Format Specific): The app MUST implement logic that takes detected vehicle attributes (LP, Color, Type) and the current detection mode, and queries the watchlist database to find a match. A match occurs if a watchlist entry's attributes satisfy the criteria of the selected mode when compared to the detected attributes. For modes that include 'LP', the detected License Plate MUST first be checked to ensure it conforms to one of the required Israeli formats (`NN-NNN-NN`, `NNN-NN-NNN`, or `N-NNNN-NN`) before attempting to match against watchlist LPs.  
* FR 1.12 \- Audible Alert Trigger: If the Matching Logic (FR 1.11) finds a match, the app MUST trigger an audible alert sound.  
* FR 1.13 \- Alert Duration: The audible alert MUST play for a duration of exactly 2 seconds.  
* FR 1.14 \- Alert Non-Repeat: The audible alert MUST NOT repeat immediately for the same detection event (e.g., multiple frames of the same matched vehicle or simultaneous matches in one frame should result in only one alert sound event).  
* FR 1.15 \- Alert Non-Dismissible: The user MUST NOT be able to manually dismiss the audible alert.  
* FR 1.16 \- Basic Navigation: The app MUST provide UI elements (e.g., buttons, navigation tabs) to transition between the Camera view, a Watchlist list view, and the Settings screen. A minimal UI for adding/deleting entries from the Watchlist list view should be implemented, including input for LP that can be validated against the required formats.

### **7\. Technical Considerations**

* Architecture: The project will adopt a clean architecture approach, separating concerns into presentation, domain (shared logic), and data layers.  
* Kotlin Multiplatform: Kotlin Multiplatform will be used for implementing shared business logic, including:  
  * Watchlist database operations using a KMP-compatible library built on SQLite (e.g., SQLDelight).  
  * Persistence of settings (e.g., using KMP-settings).  
  * The core matching logic (FR 1.11).  
  * License Plate format validation logic (FR 1.6, FR 1.11) should reside in the shared module.  
* Android Implementation: The Android-specific module will handle:  
  * User Interface (UI) using Jetpack Compose or Views.  
  * Camera integration using CameraX or similar Android APIs, including implementing pinch-to-zoom (FR 1.4).  
  * Triggering platform-specific features like playing sound alerts (FR 1.12).  
  * Handling Android permissions (FR 1.2).  
* Local Storage: SQLite will be the underlying technology for the watchlist database, accessed via a KMP-compatible library. KMP-settings will be used for simple key-value persistence like the selected mode.  
* Concurrency: Kotlin Coroutines will be used for managing asynchronous operations, particularly for database interactions and potentially frame processing.  
* Dependency Injection: A dependency injection framework (e.g., Koin or Hilt) will be used to manage dependencies and improve testability.  
* Testing: Unit tests should be written for the shared KMP logic (database operations, matching logic, settings persistence, LP format validation). Basic integration tests for Android components (like camera zoom, navigation) are desirable but may be scoped based on time constraints for the PoC.

