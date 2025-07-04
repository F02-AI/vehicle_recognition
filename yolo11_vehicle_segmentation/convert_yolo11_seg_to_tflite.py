#!/usr/bin/env python3
"""
YOLO11 Segmentation to TensorFlow Lite Conversion Script
Uses ultralytics YOLO's built-in export functionality for proper conversion
"""

import os
import sys
import shutil
from pathlib import Path
from ultralytics import YOLO

# Configuration
MODELS_DIR = "converted_models"
TFLITE_MODEL_NAME_TEMPLATE = "yolo11{size}-seg.tflite"

# Available model sizes
AVAILABLE_SIZES = {
    'n': {'name': 'Nano', 'params': '2.7M', 'speed': 'Fastest'},
    's': {'name': 'Small', 'params': '10.1M', 'speed': 'Fast'},
    'm': {'name': 'Medium', 'params': '22.6M', 'speed': 'Medium'},
    'l': {'name': 'Large', 'params': '27.8M', 'speed': 'Slow'},
    'x': {'name': 'Extra Large', 'params': '62.1M', 'speed': 'Slowest'}
}

def select_model_size():
    """Interactive model size selection"""
    print("\n📋 Available YOLO11 Segmentation Model Sizes:")
    print("=" * 60)
    
    for size, info in AVAILABLE_SIZES.items():
        print(f"  {size.upper()}: {info['name']:<12} | {info['params']:<8} | {info['speed']}")
    
    print("=" * 60)
    
    while True:
        try:
            choice = input("\n🎯 Select model size (n/s/m/l/x) or 'all' for all sizes: ").lower().strip()
            
            if choice == 'all':
                return list(AVAILABLE_SIZES.keys())
            elif choice in AVAILABLE_SIZES:
                return [choice]
            else:
                print("❌ Invalid choice. Please select n, s, m, l, x, or 'all'")
        except KeyboardInterrupt:
            print("\n\n👋 Conversion cancelled by user")
            sys.exit(0)

def convert_model(model_size):
    """Convert a single YOLO11 segmentation model to TensorFlow Lite"""
    model_name = f"yolo11{model_size}-seg.pt"
    
    print(f"\n🔄 Converting YOLO11{model_size.upper()}-seg model...")
    print(f"   Model: {model_name}")
    print(f"   Info: {AVAILABLE_SIZES[model_size]['name']} ({AVAILABLE_SIZES[model_size]['params']} params)")
    
    try:
        # Load YOLO model (will download if not present)
        print(f"📥 Loading YOLO11 model: {model_name}")
        model = YOLO(model_name)
        print(f"✅ YOLO11 model loaded successfully")
        
        # Export to TensorFlow Lite with optimizations
        print(f"🔄 Exporting to TensorFlow Lite format...")
        print("   This may take several minutes...")
        
        # Try TensorFlow Lite export first
        try:
            export_path = model.export(
                format='tflite',           # Export to TensorFlow Lite
                imgsz=640,                 # Input image size (standard for segmentation)
                int8=False,                # Use full precision (no INT8 quantization)
                data=None,                 # No validation data needed
                optimize=True,             # Enable optimizations
                half=False,                # Use FP32 precision
                device='cpu',              # Use CPU for export
                simplify=True,             # Simplify model for better compatibility
                workspace=4                # Workspace size in GB
            )
            
        except Exception as tf_error:
            print(f"⚠️  TensorFlow Lite export failed: {tf_error}")
            print("🔄 Falling back to ONNX export...")
            
            # Fallback to ONNX export
            export_path = model.export(
                format='onnx',             # Export to ONNX (more compatible)
                imgsz=640,                 # Input image size (standard for segmentation)
                optimize=True,             # Enable optimizations
                half=False,                # Use FP32 precision
                device='cpu',              # Use CPU for export
                simplify=True,             # Simplify model for better compatibility
                opset=11                   # ONNX opset version
            )
            
            # Update file extension for ONNX
            global TFLITE_MODEL_NAME_TEMPLATE
            TFLITE_MODEL_NAME_TEMPLATE = "yolo11{size}-seg.onnx"
        
        print(f"✅ Export successful: {export_path}")
        
        # Get the exported file path
        if isinstance(export_path, (str, Path)):
            exported_path = str(export_path)
        else:
            # Find the exported file in current directory
            exported_files = list(Path('.').glob(f'*{model_size}*seg*'))
            if exported_files:
                exported_path = str(exported_files[0])
            else:
                print("❌ Could not find exported file")
                return False
        
        # Show file size
        size_mb = os.path.getsize(exported_path) / (1024 * 1024)
        print(f"   Exported model size: {size_mb:.2f} MB")
        
        # Create models directory if it doesn't exist
        Path(MODELS_DIR).mkdir(parents=True, exist_ok=True)
        
        # Copy to models directory with proper naming
        model_name_final = TFLITE_MODEL_NAME_TEMPLATE.format(size=model_size)
        destination = os.path.join(MODELS_DIR, model_name_final)
        
        shutil.copy2(exported_path, destination)
        print(f"✅ Model saved to: {destination}")
        
        # Show final file size
        final_size_mb = os.path.getsize(destination) / (1024 * 1024)
        print(f"   Final model size: {final_size_mb:.2f} MB")
        
        # Clean up temporary file if different from destination
        if os.path.abspath(exported_path) != os.path.abspath(destination):
            try:
                os.remove(exported_path)
                print(f"🧹 Cleaned up temporary file: {exported_path}")
            except:
                pass
        
        return True
        
    except Exception as e:
        print(f"❌ Conversion failed for {model_name}: {e}")
        return False

def main():
    """Main conversion pipeline"""
    print("🚀 YOLO11 Segmentation to TensorFlow Lite Conversion Script")
    print("=" * 60)
    
    # Check if we have the required dependencies
    try:
        import ultralytics
        print(f"✅ Ultralytics version: {ultralytics.__version__}")
    except ImportError:
        print("❌ Ultralytics not found. Please install it with: pip install ultralytics")
        return False
    
    # Check for TensorFlow availability
    try:
        import tensorflow
        print(f"✅ TensorFlow version: {tensorflow.__version__}")
        tflite_available = True
    except ImportError:
        print("⚠️  TensorFlow not available. Will export to ONNX format instead.")
        tflite_available = False
    
    # Select model size(s)
    selected_sizes = select_model_size()
    
    print(f"\n🎯 Converting {len(selected_sizes)} model(s): {', '.join([s.upper() for s in selected_sizes])}")
    
    # Convert each selected model
    successful_conversions = []
    failed_conversions = []
    
    for model_size in selected_sizes:
        if convert_model(model_size):
            successful_conversions.append(model_size)
        else:
            failed_conversions.append(model_size)
    
    # Summary
    print("\n" + "=" * 60)
    print("📊 CONVERSION SUMMARY")
    print("=" * 60)
    
    if successful_conversions:
        print(f"✅ Successful conversions ({len(successful_conversions)}):")
        for size in successful_conversions:
            model_file = TFLITE_MODEL_NAME_TEMPLATE.format(size=size)
            file_path = os.path.join(MODELS_DIR, model_file)
            if os.path.exists(file_path):
                size_mb = os.path.getsize(file_path) / (1024 * 1024)
                print(f"   • YOLO11{size.upper()}-seg → {model_file} ({size_mb:.2f} MB)")
    
    if failed_conversions:
        print(f"\n❌ Failed conversions ({len(failed_conversions)}):")
        for size in failed_conversions:
            print(f"   • YOLO11{size.upper()}-seg")
    
    if successful_conversions:
        format_type = "TensorFlow Lite" if tflite_available else "ONNX"
        print(f"\n🎉 Conversion completed successfully!")
        print(f"📁 Converted models location: {os.path.abspath(MODELS_DIR)}/")
        print(f"⚡ Format: {format_type} with FP32 precision")
        print(f"🚗 Compatible with: Car, Motorcycle, Truck segmentation")
        
        print(f"\n💡 Usage recommendations:")
        print(f"   • Nano (n): Best for real-time mobile apps")
        print(f"   • Small (s): Good balance of speed and accuracy")
        print(f"   • Medium (m): Better accuracy for complex scenes")
        print(f"   • Large (l): High accuracy applications")
        print(f"   • Extra Large (x): Maximum accuracy when speed is not critical")
        
        return True
    else:
        print(f"\n❌ All conversions failed!")
        print(f"\nTroubleshooting tips:")
        print(f"1. Ensure you have a stable internet connection (models download automatically)")
        print(f"2. Check that you have enough disk space")
        print(f"3. Verify ultralytics is properly installed: pip install ultralytics")
        print(f"4. Try converting one model at a time")
        return False

if __name__ == "__main__":
    success = main()
    sys.exit(0 if success else 1)