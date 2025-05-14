## **App Workflow Document: Vehicle Recognition Android PoC**

### **1\. Title**

Vehicle Recognition Android PoC \- App Workflow Document

### **2\. Overview of the App Workflow**

This document outlines the user workflows for a Proof of Concept (PoC) Android application designed for real-time vehicle recognition using the device camera. The app's core purpose is to detect and identify vehicles based on their license plate (Israeli formats), color, and type (Car/Motorcycle/Truck) and match them against a user-defined watchlist. When a match occurs, the app will alert the user via sound. The application is intended to function offline, directly on the mobile device.  
The overall user journey typically involves:

1. Opening the app and accessing the live camera feed.  
2. Zooming in and out to adjust the zoom level of the camera  
3. Optionally, managing a watchlist of vehicles to monitor.  
4. Optionally, configuring the detection criteria (e.g., detect by LP only, or LP+Color+Type).  
5. Monitoring vehicles via the camera feed.  
6. Receiving an audible alert if a vehicle matching the watchlist and current detection settings is identified.  
7. Exiting the app.

### **3\. User Roles and Scenarios**

User Role: Operator  
Description: An individual using the Android application on a high-end device (like a Samsung Galaxy S24) to monitor vehicles in real-time via the camera feed. The Operator needs to quickly identify specific vehicles from a watchlist and be alerted when they are detected.  
Typical User Scenarios:

* Scenario 1: Initial Setup & Monitoring: The Operator opens the app for the first time, understands how to activate the camera feed, and starts monitoring a scene for vehicles.  
* Scenario 2: Watchlist Management: The Operator needs to add a new vehicle to the watchlist (specifying LP, Color, and Type). They may also need to delete an existing entry.  
* Scenario 3: Adjusting Detection Criteria: The Operator needs to change which vehicle attributes are used for matching (e.g., switch from matching only by LP to matching by LP \+ Color \+ Type).  
* Scenario 4: Receiving and Acknowledging Alerts: The Operator detects a vehicle on the watchlist and receives an audible alert.  
* Scenario 5: Using Zoom for Distance: The Operator uses the camera's pinch-to-zoom feature to focus on vehicles at a distance (up to \~40-50m) for better detection.

### **4\. Feature-Based Workflows**

This section details the workflows for the primary features of the PoC app.  
4.1. Camera Feed & Real-time Detection

* User Entry Point: Launching the app; typically defaults to the camera view or is accessible via a main screen button/tab.  
* Step-by-Step Workflow:  
  1. Operator launches the app.  
  2. The app requests camera permissions (if not already granted).  
  3. Upon permission grant (or if already granted), the app initializes the camera feed.  
  4. The live video stream appears on the screen.  
  5. The app automatically begins processing the video stream in real-time using the integrated computer vision models (LP, Color, Type) based on the currently selected detection mode.  
  6. As vehicles are detected, the app processes the results (extracting LP, Color, Type).  
  7. The Operator can use pinch-to-zoom gestures on the camera preview to adjust the camera's focus/zoom level for distant vehicles. This zoom setting persists while monitoring.  
* Outcome: The app continuously processes the camera feed, identifies vehicles and their attributes, and prepares data for watchlist matching.

4.2. Watchlist Management (Add/Delete)

* User Entry Point: A dedicated "Watchlist" section or button, likely accessible from the main camera view or a navigation menu.  
* Step-by-Step Workflow (Add Vehicle):  
  1. Operator navigates to the "Watchlist" screen.  
  2. Operator taps an "Add New Vehicle" button (e.g., a '+' icon).  
  3. A dedicated input screen/form appears.  
  4. Operator enters the License Plate number (using the defined Israeli formats placeholders or free text input as agreed for PoC).  
  5. Operator selects the Vehicle Type (Car, Motorcycle, Truck) from a predefined list (e.g., dropdown or radio buttons).  
  6. Operator selects the Vehicle Color (Red, Blue, Green, White, Black, Gray, Yellow) from a predefined list.  
  7. (Optional) Operator may have an option to associate a picture. This is not required for the entry to be valid.  
  8. Operator taps a "Save" or "Add" button.  
  9. The app validates the input (e.g., ensuring LP format if applicable, although free text is an option for PoC).  
  10. The vehicle entry is added to the local watchlist database.  
  11. The Operator is returned to the Watchlist list view, showing the newly added vehicle.  
* Step-by-Step Workflow (Delete Vehicle):  
  1. Operator navigates to the "Watchlist" screen.  
  2. Operator views the list of vehicles currently on the watchlist.  
  3. Operator taps a "Delete" icon associated with the specific vehicle entry they wish to remove.  
  4. A confirmation dialog appears (e.g., "Are you sure you want to delete this vehicle?").  
  5. Operator confirms the deletion by tapping a "Yes" or "Delete" button in the dialog.  
  6. The selected vehicle entry is removed from the local watchlist database.  
  7. The Operator is returned to the Watchlist list view, which is updated.  
* Outcome: The watchlist database is updated with added or deleted vehicle entries, which are then used for matching against detected vehicles.

4.3. Settings Configuration (Detection Modes)

* User Entry Point: A "Settings" or "Modes" button/icon, likely accessible from the main camera view or a navigation menu.  
* Step-by-Step Workflow:  
  1. Operator navigates to the "Settings" screen.  
  2. The screen displays options for detection modes using toggles or radio buttons.  
  3. The available modes are: LP, LP+Color, LP+Type, LP+Color+Type, Color+Type, Color.  
  4. Operator selects one of the available modes.  
  5. The selection is automatically saved and persists across app sessions.  
  6. Operator navigates back to the camera view (e.g., using a back button).  
* Outcome: The app's real-time processing logic is updated to use the selected criteria for matching vehicles against the watchlist.

4.4. Alerting on Watchlist Match

* User Entry Point: This workflow is triggered automatically when a vehicle is detected via the camera feed and matches an entry in the watchlist based on the current settings.  
* Step-by-Step Workflow:  
  1. The app's real-time processing detects a vehicle and extracts its attributes (LP, Color, Type).  
  2. The app compares the detected attributes against the entries in the watchlist database, using the criteria defined in the current detection mode (e.g., if mode is "LP+Color", it checks if both the detected LP and Color match an entry).  
  3. If a match is found (and no alert is currently playing from a recent match):  
     * The app triggers an audible alert sound.  
     * The alert sound plays for a duration of 2 seconds.  
     * The alert does not repeat immediately for the same detection event.  
     * The user cannot dismiss the alert manually.  
     * If multiple watchlist vehicles are detected in the same frame or sequence, the alert sounds only once for that detection event.  
  4. If no match is found, or if an alert is already playing, no new alert is triggered by this specific detection.  
* Outcome: The Operator is notified via sound that a vehicle matching the watchlist criteria has been detected.

### **5\. End-to-End User Journey**

This section outlines a typical complete flow through the application.

1. App Launch & Camera Access:  
   * Operator taps the app icon.  
   * App requests/uses camera permission.  
   * Live camera feed is displayed.  
   * Real-time detection begins automatically using the default/last-used mode (e.g., LP). No basic onboarding/tutorial screens are shown.  
2. Initial Watchlist Check (Implicit): The app is ready to match detections against the current watchlist (initially empty or pre-populated).  
3. Manage Watchlist (Optional):  
   * Operator navigates to the Watchlist screen. This is a dedicated screen.  
   * Operator taps "Add New Vehicle".  
   * Operator enters LP, selects Type and Color. Adding a picture is optional.  
   * Operator taps "Save".  
   * Operator returns to the Watchlist list view.  
   * To delete, Operator taps the delete icon next to an entry, confirms in the dialog.  
   * Operator taps back to return to the Camera view.  
4. Configure Settings (Optional):  
   * Operator navigates to the Settings screen.  
   * Operator selects a different detection mode (e.g., LP+Color+Type).  
   * Operator taps back to return to the Camera view.  
5. Monitoring & Detection:  
   * Operator points the camera at vehicles.  
   * Operator uses pinch-to-zoom if needed.  
   * The app processes frames, detects vehicles, extracts attributes. No visual indication is shown for detected vehicles that do not match the watchlist.  
6. Watchlist Match & Alert:  
   * A detected vehicle's attributes (based on current mode) match a watchlist entry.  
   * The app plays the audible alert sound for 2 seconds.  
   * The alert does not repeat or allow user dismissal.  
   * If multiple matches occur simultaneously, the alert sounds only once.  
7. Continue Monitoring or Stop:  
   * Operator continues monitoring, receiving alerts for subsequent distinct matches.  
   * Operator navigates away from the camera screen or closes the app.  
8. App Exit:  
   * Operator closes the app.  
   * Settings and Watchlist data are persisted locally.

Error/Exception Handling Workflows (Basic PoC Level):

* Camera Permission Denied: App displays a message informing the user that camera access is required and guides them to grant permission in device settings.  
* Detection Failure: If the models fail to detect a vehicle or extract attributes from a frame (e.g., poor lighting, blur, distance), no match will occur, and no alert will be triggered. The app continues processing subsequent frames. No specific user notification for individual detection failures is required for PoC.  
* Watchlist Save Error: If adding/deleting a watchlist entry fails (e.g., database error \- unlikely for simple local DB), the app should ideally notify the user (e.g., "Failed to save entry").

### **6\. Feature Interdependencies**

* Camera Integration is fundamental; it provides the raw video stream that all other detection features rely on.  
* The LP Model, Vehicle Type Model, and Color Classifier process the camera feed to extract attributes. These models are independent in their processing but their *results* are combined.  
* The Settings Screen & Modes feature directly controls *how* the results from the detection models are used in the Matching Logic. Changing a setting immediately affects the criteria for triggering an alert.  
* The Watchlist Database stores the target vehicles. The Matching Logic queries this database using the detected attributes and the current settings.  
* The Alerts are triggered *only* when the Matching Logic finds a match between detected attributes and a watchlist entry.  
* Watchlist Management (Add/Delete UI) provides the interface for the user to modify the Watchlist Database.

### **7\. Workflow Diagram (Optional)**

*(Placeholder for Diagram)*  
A visual diagram could represent the main flow:  
\[App Launch\] \--\> \[Request Camera Permission\] \--\> \[Display Camera Feed\]  
|  
v  
\[Real-time Vehicle Detection (LP, Color, Type)\]  
|  
v  
\[Apply Settings Filter (e.g., LP+Color)\]  
|  
v  
\[Match against Watchlist Database\]  
|  
v (Match Found)  
\[Trigger Audible Alert (2 seconds, no repeat/dismiss)\]  
|  
v (No Match)  
\[Continue Monitoring\]  
Separate flows could show:  
\[Camera Feed\] \<--\> \[Pinch-to-Zoom Interaction\]  
\[Main Screen/Menu\] \--\> \[Watchlist Screen (Dedicated)\] \--\> \[Add Vehicle Workflow\] \--\> \[Watchlist Database\]  
\--\> \[Delete\ Vehicle\ Workflow\ (Icon\ +\ Confirmation)\]\--\> \[Watchlist Database\]  
\[Main Screen/Menu\] \--\> \[Settings Screen\] \--\> \[Select Detection Mode Workflow\] \--\> \[Settings Persistence\]

### **8\. Assumptions and Open Questions**

Assumptions Made:

* The app will have a basic, functional Android UI suitable for a PoC, allowing navigation between the camera view, watchlist, and settings.  
* Basic Android UI conventions (e.g., back button navigation) will be used.  
* Local storage (e.g., SQLite) will be used for the watchlist database.  
* The free-text input for future LP types in the watchlist assumes the user knows the LP format for non-Israeli plates; the app won't validate these non-Israeli formats at the PoC stage if free text is used.

Clarifications Received:

* UI Layout Requirements: No specific requirements beyond basic input fields/toggles on the dedicated screens.  
* Watchlist UI: Adding and deleting watchlist entries will use a dedicated screen.  
* Watchlist Deletion: Deletion will be handled via a delete icon associated with the entry, followed by a confirmation dialog.  
* Watchlist Picture: Adding a picture to a watchlist entry is not required.  
* Alert Behavior: The audible alert will play for exactly 2 seconds, will not repeat, and cannot be dismissed by the user.  
* Visual Feedback: There will be no visual indication on the camera screen for detected vehicles that do not match the watchlist.  
* Alert Sound: There are no specific requirements for the alert sound itself.  
* Multiple Matches: If multiple watchlist vehicles are detected simultaneously or in rapid succession, the alert will sound only once for that detection event.  
* Onboarding: No basic onboarding steps are required beyond initial camera permissions.

