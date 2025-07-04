#!/usr/bin/env python3
"""
YOLO to TensorFlow Lite Conversion Script
Uses ultralytics YOLO's built-in export functionality for proper conversion
"""

import os
import shutil
from pathlib import Path
from ultralytics import YOLO

# Configuration
PYTORCH_MODEL_PATH = "license_plate_detector.pt"
ANDROID_MODELS_DIR = "androidApp/src/main/assets/models"
TFLITE_MODEL_NAME = "license_plate_detector.tflite"

def main():
    """Main conversion pipeline using YOLO's export functionality"""
    print("🚀 YOLO to TensorFlow Lite Conversion Script")
    print("=" * 50)
    
    # Check if source model exists
    if not os.path.exists(PYTORCH_MODEL_PATH):
        print(f"❌ PyTorch model not found: {PYTORCH_MODEL_PATH}")
        return False
    
    try:
        # Load YOLO model
        print(f"📥 Loading YOLO model from {PYTORCH_MODEL_PATH}")
        model = YOLO(PYTORCH_MODEL_PATH)
        print(f"✅ YOLO model loaded successfully")
        
        # Export to TensorFlow Lite with optimizations
        print(f"🔄 Exporting to TensorFlow Lite format...")
        print("   This may take several minutes...")
        
        # Export without quantization for full precision
        export_path = model.export(
            format='tflite',           # Export to TensorFlow Lite
            imgsz=640,                 # Input image size
            int8=False,                # Use full precision (no INT8)
            data=None,                 # No validation data needed
            optimize=True,             # Enable optimizations
            half=False,                # Use FP32
            device='cpu'               # Use CPU for export
        )
        
        print(f"✅ TFLite export successful: {export_path}")
        
        # Get the exported file path
        if isinstance(export_path, (str, Path)):
            tflite_path = str(export_path)
        else:
            # Find the .tflite file in current directory
            tflite_files = list(Path('.').glob('*.tflite'))
            if tflite_files:
                tflite_path = str(tflite_files[0])
            else:
                print("❌ Could not find exported TFLite file")
                return False
        
        # Show file size
        size_mb = os.path.getsize(tflite_path) / (1024 * 1024)
        print(f"   Exported model size: {size_mb:.2f} MB")
        
        # Copy to Android assets
        print(f"📁 Copying to Android assets folder...")
        
        # Ensure the Android models directory exists
        Path(ANDROID_MODELS_DIR).mkdir(parents=True, exist_ok=True)
        
        # Copy the file
        destination = os.path.join(ANDROID_MODELS_DIR, TFLITE_MODEL_NAME)
        shutil.copy2(tflite_path, destination)
        
        print(f"✅ Model copied to Android assets: {destination}")
        
        # Show final file size
        final_size_mb = os.path.getsize(destination) / (1024 * 1024)
        print(f"   Final model size: {final_size_mb:.2f} MB")
        
        # Clean up temporary file if it's not the same as destination
        if os.path.abspath(tflite_path) != os.path.abspath(destination):
            try:
                os.remove(tflite_path)
                print(f"🧹 Cleaned up temporary file: {tflite_path}")
            except:
                pass
        
        print("\n🎉 Conversion completed successfully!")
        print(f"   ✅ Full-precision YOLO model ready for Android deployment")
        print(f"   📁 Location: {destination}")
        print(f"   ⚡ Features: FP32 precision, mobile-optimized")
        
        return True
        
    except Exception as e:
        print(f"❌ Conversion failed: {e}")
        print("\nTroubleshooting tips:")
        print("1. Ensure you're running in the virtual environment with ultralytics installed")
        print("2. Make sure the model file is a valid YOLO model")
        print("3. Check that you have enough disk space for the conversion")
        return False

if __name__ == "__main__":
    success = main()
    exit(0 if success else 1) 