# **Phase 2 Implementation Plan: License Plate Recognition**

## **1. Overview**

This document outlines the implementation plan for Phase 2 of the Vehicle Recognition Android PoC, focusing on license plate recognition functionality. The implementation will integrate the same detection model used in `python_yolo_fast_plate.py` with multiple OCR options and real-time detection.

### **Key Features to Implement:**
- License plate detection using YOLO model from Python script
- Multiple OCR model options with settings dropdown
- Real-time frame overlay with detection boxes
- Numeric-only plate recognition with Israeli format validation
- Integration with existing watchlist and alert system

## **2. Technical Architecture**

### **2.1 Model Integration Strategy**

**License Plate Detection Model:**
- Copy `license_plate_detector.pt` from Python project to `androidApp/src/main/assets/models/`
- Integrate YOLO model using TensorFlow Lite or PyTorch Mobile
- Use the same vehicle detection flow: Vehicle → License Plate → OCR

**OCR Model Options:**
1. **FastPlateOCR (Default)** - Port from Python implementation
2. **Tesseract OCR** - Robust open-source solution
3. **ML Kit Text Recognition** - Google's mobile-optimized OCR
4. **PaddleOCR Mobile** - State-of-the-art lightweight OCR

### **2.2 Component Architecture**

```
androidApp/src/main/java/com/example/vehiclerecognition/
├── ml/
│   ├── detection/
│   │   ├── LicensePlateDetector.kt
│   │   ├── YoloModelManager.kt
│   │   └── DetectionResult.kt
│   ├── ocr/
│   │   ├── OcrEngine.kt (interface)
│   │   ├── FastPlateOcrEngine.kt
│   │   ├── TesseractOcrEngine.kt
│   │   ├── MLKitOcrEngine.kt
│   │   └── PaddleOcrEngine.kt
│   └── processors/
│       ├── LicensePlateProcessor.kt
│       └── NumericPlateValidator.kt
├── data/
│   ├── models/
│   │   ├── LicensePlateSettings.kt
│   │   └── OcrModelType.kt
│   └── repositories/
│       └── LicensePlateRepository.kt
├── ui/
│   ├── camera/
│   │   ├── LicensePlateOverlay.kt
│   │   └── CameraViewUpdates.kt
│   └── settings/
│       └── OcrModelSelector.kt
└── platform/
    └── ModelLoader.kt
```

## **3. Implementation Steps**

### **Phase 2.1: Model Integration (Week 1-2)**

#### **Step 1: License Plate Detection Setup**
- [ ] Copy `license_plate_detector.pt` to assets folder
- [ ] Add TensorFlow Lite dependencies to `build.gradle.kts`:
  ```kotlin
  implementation("org.tensorflow:tensorflow-lite:2.14.0")
  implementation("org.tensorflow:tensorflow-lite-gpu:2.14.0")
  implementation("org.tensorflow:tensorflow-lite-support:0.4.4")
  ```
- [ ] Create `LicensePlateDetector.kt` class
- [ ] Implement YOLO model loading and inference
- [ ] Add model conversion script (PyTorch to TensorFlow Lite)

#### **Step 2: OCR Engines Implementation**
- [ ] **FastPlateOCR Engine**: Port numeric-only logic from Python
- [ ] **Tesseract Engine**: Add Tesseract dependency and wrapper
- [ ] **ML Kit Engine**: Integrate Google ML Kit Text Recognition
- [ ] **PaddleOCR Engine**: Add PaddleOCR mobile implementation

**Dependencies to add:**
```kotlin
// Tesseract
implementation("cz.adaptech.tesseract4android:tesseract4android:4.6.0")

// ML Kit
implementation("com.google.mlkit:text-recognition:16.0.0")

// PaddleOCR (if available as Android library)
implementation("com.baidu.paddle:paddleocr-android:x.x.x")
```

#### **Step 3: Numeric Plate Validation**
- [ ] Port validation logic from `python_yolo_fast_plate.py`
- [ ] Implement Israeli license plate format rules:
  - 7 digits: `NN-NNN-NN` format
  - 8 digits: `NNN-NN-NNN` format  
  - 9 digits: Show both 8-digit options
- [ ] Create `NumericPlateValidator.kt` with same logic as Python script

### **Phase 2.2: UI Integration (Week 2-3)**

#### **Step 4: Camera Overlay Enhancement**
- [ ] Modify existing camera screen to add license plate overlay
- [ ] Create `LicensePlateOverlay.kt` composable:
  ```kotlin
  @Composable
  fun LicensePlateOverlay(
      detectedPlates: List<PlateDetection>,
      recognizedText: String?,
      overlayBounds: Rect
  )
  ```
- [ ] Add real-time detection box rendering
- [ ] Display numeric text in upper-right corner with validation status

#### **Step 5: Settings Screen Updates**
- [ ] Add OCR model dropdown to existing settings
- [ ] Create `OcrModelSelector.kt`:
  ```kotlin
  enum class OcrModelType {
      FAST_PLATE_OCR, // Default
      TESSERACT,
      ML_KIT,
      PADDLE_OCR
  }
  ```
- [ ] Implement model switching logic
- [ ] Add performance metrics display (processing time, confidence)

#### **Step 6: Detection Mode Integration**
- [ ] Update existing detection modes to include LP-only option
- [ ] Integrate plate recognition with watchlist matching
- [ ] Ensure alerts trigger for LP matches according to workflow document

### **Phase 2.3: Processing Pipeline (Week 3-4)**

#### **Step 7: Real-time Processing Architecture**
- [ ] Create `LicensePlateProcessor.kt` for coordinating detection and OCR
- [ ] Implement frame processing queue (similar to Python script's threading)
- [ ] Add processing interval controls (every Nth frame)
- [ ] Optimize for mobile performance (target 30fps camera feed)

#### **Step 8: Performance Optimizations**
- [ ] Implement GPU acceleration for model inference
- [ ] Add frame skipping for OCR processing
- [ ] Cache model loading results
- [ ] Optimize memory usage for continuous camera feed

#### **Step 9: Error Handling and Fallbacks**
- [ ] Handle model loading failures gracefully
- [ ] Implement OCR engine fallback chain
- [ ] Add user feedback for processing status
- [ ] Handle camera permission edge cases

### **Phase 2.4: Integration and Testing (Week 4)**

#### **Step 10: End-to-End Integration**
- [ ] Connect license plate detection with existing watchlist system
- [ ] Ensure alert system works with LP matches
- [ ] Test all detection modes (LP, LP+Color, LP+Type, etc.)
- [ ] Verify numeric-only constraint works across all OCR engines

#### **Step 11: Performance Testing**
- [ ] Test on high-end devices (Samsung Galaxy S24)
- [ ] Measure frame processing rates
- [ ] Test accuracy with various lighting conditions
- [ ] Validate Israeli license plate format recognition

## **4. File Structure Changes**

### **4.1 New Files to Create**
```
androidApp/src/main/
├── assets/
│   └── models/
│       ├── license_plate_detector.tflite
│       ├── fastplate_model/
│       ├── tesseract_traineddata/
│       └── paddleocr_models/
├── java/com/example/vehiclerecognition/
│   ├── ml/
│   │   ├── detection/
│   │   │   ├── LicensePlateDetector.kt
│   │   │   ├── YoloModelManager.kt
│   │   │   ├── DetectionResult.kt
│   │   │   └── BoundingBox.kt
│   │   ├── ocr/
│   │   │   ├── OcrEngine.kt
│   │   │   ├── FastPlateOcrEngine.kt
│   │   │   ├── TesseractOcrEngine.kt
│   │   │   ├── MLKitOcrEngine.kt
│   │   │   ├── PaddleOcrEngine.kt
│   │   │   └── OcrResult.kt
│   │   └── processors/
│   │       ├── LicensePlateProcessor.kt
│   │       ├── NumericPlateValidator.kt
│   │       └── PlateFormatter.kt
│   ├── data/
│   │   ├── models/
│   │   │   ├── LicensePlateSettings.kt
│   │   │   ├── OcrModelType.kt
│   │   │   └── PlateDetection.kt
│   │   └── repositories/
│   │       └── LicensePlateRepository.kt
│   └── ui/
│       ├── camera/
│       │   ├── LicensePlateOverlay.kt
│       │   └── components/
│       │       ├── DetectionBox.kt
│       │       └── PlateTextDisplay.kt
│       └── settings/
│           └── OcrModelSelector.kt
```

### **4.2 Files to Modify**
- `build.gradle.kts` - Add new dependencies
- `CameraScreen.kt` - Add license plate overlay
- `SettingsScreen.kt` - Add OCR model selector
- `VehicleRecognitionRepository.kt` - Integrate LP processing
- `DetectionWorker.kt` - Add LP detection flow

## **5. Dependencies and Libraries**

### **5.1 Required Dependencies**
```kotlin
dependencies {
    // Existing dependencies...
    
    // ML/AI Libraries
    implementation("org.tensorflow:tensorflow-lite:2.14.0")
    implementation("org.tensorflow:tensorflow-lite-gpu:2.14.0")
    implementation("org.tensorflow:tensorflow-lite-support:0.4.4")
    
    // OCR Libraries
    implementation("cz.adaptech.tesseract4android:tesseract4android:4.6.0")
    implementation("com.google.mlkit:text-recognition:16.0.0")
    
    // Image Processing
    implementation("org.opencv:opencv-android:4.8.0")
    
    // Coroutines for background processing
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
}
```

### **5.2 Model Requirements**
- `license_plate_detector.pt` → Convert to TensorFlow Lite format
- FastPlateOCR models (download from HuggingFace)
- Tesseract trained data files for relevant languages
- ML Kit models (downloaded automatically)

## **6. Performance Considerations**

### **6.1 Mobile Optimization Strategy**
- **Model Quantization**: Convert models to INT8 for faster inference
- **Frame Sampling**: Process every 3rd-5th frame for OCR (configurable)
- **GPU Acceleration**: Use TensorFlow Lite GPU delegate when available
- **Memory Management**: Implement proper bitmap recycling and model caching

### **6.2 Real-time Processing Targets**
- **Camera Preview**: 30 FPS
- **License Plate Detection**: 10-15 FPS
- **OCR Processing**: 3-5 FPS (every 5th frame)
- **UI Updates**: 60 FPS overlay rendering

## **7. Testing Strategy**

### **7.1 Unit Testing**
- [ ] Test numeric plate validation logic
- [ ] Test each OCR engine independently  
- [ ] Test model loading and inference
- [ ] Test Israeli license plate format parsing

### **7.2 Integration Testing**
- [ ] Test camera → detection → OCR pipeline
- [ ] Test watchlist matching with LP data
- [ ] Test alert triggering for LP matches
- [ ] Test settings changes affecting detection behavior

### **7.3 Performance Testing**
- [ ] Memory usage profiling during continuous camera feed
- [ ] Frame rate measurements on target devices
- [ ] Battery consumption analysis
- [ ] Accuracy testing with real Israeli license plates

## **8. Success Criteria**

### **8.1 Functional Requirements**
- ✅ License plate detection using same model as Python script
- ✅ Multiple OCR engine options in settings dropdown
- ✅ Numeric-only plate recognition with Israeli formatting
- ✅ Real-time camera overlay with detection boxes
- ✅ Upper-right corner text display of recognized digits
- ✅ Integration with existing watchlist and alert system

### **8.2 Performance Requirements**
- ✅ Smooth camera preview (≥25 FPS)
- ✅ License plate detection within 200ms
- ✅ OCR processing within 500ms
- ✅ Memory usage under 150MB during operation
- ✅ Accurate recognition of 7-9 digit Israeli plates

### **8.3 User Experience Requirements**
- ✅ Intuitive OCR model selection in settings
- ✅ Clear visual feedback for detected plates
- ✅ Responsive UI during processing
- ✅ Graceful handling of processing failures

## **9. Risk Mitigation**

### **9.1 Technical Risks**
- **Model Conversion Issues**: Have fallback to alternative model formats
- **Performance Constraints**: Implement adaptive quality settings
- **OCR Accuracy**: Provide multiple engine options for redundancy
- **Memory Limitations**: Implement aggressive resource management

### **9.2 Timeline Risks**
- **Model Integration Complexity**: Allocate extra time for TensorFlow Lite conversion
- **OCR Engine Integration**: Start with simpler engines (ML Kit) first
- **Performance Optimization**: Plan iterative optimization cycles

## **10. Timeline Summary**

| Week | Phase | Key Deliverables |
|------|-------|-----------------|
| 1 | Model Integration | YOLO detection working, basic OCR engines |
| 2 | UI Integration | Camera overlay, settings dropdown |
| 3 | Processing Pipeline | Real-time detection, performance optimization |
| 4 | Integration & Testing | End-to-end testing, Polish |

**Total Estimated Time: 4 weeks**

## **11. Future Enhancements (Phase 3+)**

- Multi-language license plate support
- Cloud-based OCR fallback
- Machine learning model fine-tuning
- Advanced preprocessing (perspective correction, enhancement)
- Offline model updates
- Performance analytics and monitoring 