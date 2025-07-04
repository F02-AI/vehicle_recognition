#!/usr/bin/env python3
"""
Test script to verify YOLO11 Vehicle Segmentation setup
This script checks if all dependencies are installed and the model can be loaded
"""

import sys
import importlib
import traceback

def test_imports():
    """Test if all required packages can be imported"""
    print("Testing imports...")
    
    required_packages = [
        'ultralytics',
        'cv2',
        'numpy',
        'PIL',
        'torch',
        'torchvision'
    ]
    
    failed_imports = []
    
    for package in required_packages:
        try:
            importlib.import_module(package)
            print(f"  ✓ {package}")
        except ImportError as e:
            print(f"  ✗ {package} - {e}")
            failed_imports.append(package)
    
    if failed_imports:
        print(f"\nFailed to import: {', '.join(failed_imports)}")
        print("Please install missing packages with: pip install -r requirements.txt")
        return False
    else:
        print("All imports successful!")
        return True

def test_model_loading():
    """Test if YOLO11 model can be loaded"""
    print("\nTesting YOLO11 model loading...")
    
    try:
        from ultralytics import YOLO
        
        # Try to load the smallest model first
        print("  Loading yolo11n-seg.pt (nano model)...")
        model = YOLO('yolo11n-seg.pt')
        
        print("  ✓ Model loaded successfully!")
        print(f"  Model type: {type(model)}")
        
        # Test model info
        print("  Testing model info...")
        print(f"  Model task: {model.task}")
        print(f"  Model device: {model.device}")
        
        return True
        
    except Exception as e:
        print(f"  ✗ Model loading failed: {e}")
        print("  This might be due to:")
        print("    - Internet connection issues (model needs to download)")
        print("    - Missing PyTorch/CUDA dependencies")
        print("    - Incompatible versions")
        return False

def test_basic_functionality():
    """Test basic functionality with a dummy image"""
    print("\nTesting basic functionality...")
    
    try:
        import numpy as np
        import cv2
        from vehicle_segmentation import VehicleSegmentation
        
        # Create a dummy image
        dummy_image = np.zeros((480, 640, 3), dtype=np.uint8)
        dummy_image[:, :] = [100, 150, 200]  # Light blue background
        
        # Save dummy image
        test_image_path = "test_image.jpg"
        cv2.imwrite(test_image_path, dummy_image)
        print(f"  Created test image: {test_image_path}")
        
        # Initialize vehicle segmentation
        print("  Initializing VehicleSegmentation...")
        vehicle_seg = VehicleSegmentation(model_size='n')  # Use nano for speed
        
        # Process the dummy image
        print("  Processing test image...")
        result_img, detections = vehicle_seg.process_image(
            test_image_path, 
            output_dir="test_output", 
            conf_threshold=0.5
        )
        
        print(f"  ✓ Processing completed! Found {len(detections)} detections")
        print("  (Note: No vehicles expected in dummy image)")
        
        # Clean up
        import os
        if os.path.exists(test_image_path):
            os.remove(test_image_path)
        
        return True
        
    except Exception as e:
        print(f"  ✗ Basic functionality test failed: {e}")
        traceback.print_exc()
        return False

def test_gpu_availability():
    """Test if GPU acceleration is available"""
    print("\nTesting GPU availability...")
    
    try:
        import torch
        
        if torch.cuda.is_available():
            print(f"  ✓ CUDA available: {torch.cuda.get_device_name(0)}")
            print(f"  CUDA version: {torch.version.cuda}")
            print(f"  Available GPUs: {torch.cuda.device_count()}")
        else:
            print("  ⚠ CUDA not available - will use CPU")
            print("  This is fine but processing will be slower")
        
        return True
        
    except Exception as e:
        print(f"  ✗ GPU test failed: {e}")
        return False

def main():
    """Run all tests"""
    print("YOLO11 Vehicle Segmentation - Setup Test")
    print("=" * 50)
    
    test_results = []
    
    # Run tests
    test_results.append(("Imports", test_imports()))
    test_results.append(("GPU Availability", test_gpu_availability()))
    test_results.append(("Model Loading", test_model_loading()))
    test_results.append(("Basic Functionality", test_basic_functionality()))
    
    # Summary
    print("\n" + "=" * 50)
    print("TEST SUMMARY")
    print("=" * 50)
    
    all_passed = True
    for test_name, passed in test_results:
        status = "PASS" if passed else "FAIL"
        print(f"{test_name:20} {status}")
        if not passed:
            all_passed = False
    
    print("=" * 50)
    
    if all_passed:
        print("🎉 ALL TESTS PASSED!")
        print("Your YOLO11 Vehicle Segmentation setup is ready to use!")
        print("\nNext steps:")
        print("1. Run: python vehicle_segmentation.py -i your_image.jpg")
        print("2. Or check example_usage.py for programmatic usage")
    else:
        print("❌ SOME TESTS FAILED!")
        print("Please check the error messages above and:")
        print("1. Install missing dependencies: pip install -r requirements.txt")
        print("2. Check internet connection for model download")
        print("3. Verify PyTorch installation")
        
        return 1
    
    return 0

if __name__ == "__main__":
    sys.exit(main()) 