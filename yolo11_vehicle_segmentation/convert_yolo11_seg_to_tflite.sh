#!/bin/bash

# YOLO11 Segmentation to TensorFlow Lite Conversion Script
# Automated script for converting YOLO11-seg models to TFLite for mobile deployment

set -e  # Exit on error

echo "🚀 Starting YOLO11 Segmentation to TensorFlow Lite conversion..."
echo "=============================================================="

# Check if Python is available
if ! command -v python3 &> /dev/null; then
    echo "❌ Python3 not found!"
    echo "Please install Python3 first."
    exit 1
fi

# Check if we're in the right directory
if [ ! -f "vehicle_segmentation.py" ]; then
    echo "❌ vehicle_segmentation.py not found!"
    echo "Please run this script from the yolo11_vehicle_segmentation directory."
    exit 1
fi

# Check if virtual environment exists (optional)
if [ -d "../.venvo" ]; then
    echo "📦 Activating virtual environment..."
    source ../.venvo/bin/activate
elif [ -d ".venv" ]; then
    echo "📦 Activating virtual environment..."
    source .venv/bin/activate
else
    echo "⚠️  No virtual environment found. Using system Python."
    echo "Consider creating a virtual environment for better dependency management."
fi

# Run the conversion script
echo "🔄 Running YOLO11 segmentation conversion script..."
python3 convert_yolo11_seg_to_tflite.py

# Check if conversion was successful
if [ $? -eq 0 ]; then
    echo ""
    echo "✅ SUCCESS! YOLO11 segmentation model conversion completed"
    echo "📱 Your optimized TensorFlow Lite model is ready for mobile deployment"
    echo ""
    echo "🏎️  Model features:"
    echo "   • YOLO11 segmentation architecture"
    echo "   • Mobile-optimized for deployment"
    echo "   • Compatible with TensorFlow Lite API"
    echo "   • Supports Car, Motorcycle, and Truck segmentation"
    echo ""
    echo "💡 Next steps:"
    echo "   1. Test the TFLite model with your mobile app"
    echo "   2. Integrate with your segmentation pipeline"
    echo "   3. Consider further optimizations if needed"
    echo "   4. Compare performance with original PyTorch model"
else
    echo "❌ Conversion failed! Check the error messages above."
    exit 1
fi 