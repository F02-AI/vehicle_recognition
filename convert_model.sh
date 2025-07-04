#!/bin/bash

# YOLO to TensorFlow Lite Conversion Script
# Automated script for converting PyTorch YOLO models to TFLite for Android

set -e  # Exit on error

echo "🚀 Starting YOLO to TensorFlow Lite conversion..."
echo "=============================================="

# Check if virtual environment exists
if [ ! -d ".venvo" ]; then
    echo "❌ Virtual environment '.venvo' not found!"
    echo "Please create and activate your virtual environment first."
    exit 1
fi

# Activate virtual environment and run conversion
echo "📦 Activating virtual environment..."
source .venvo/bin/activate

echo "🔄 Running YOLO conversion script..."
python convert_yolo_to_tflite.py

# Check if conversion was successful
if [ $? -eq 0 ]; then
    echo ""
    echo "✅ SUCCESS! Model conversion completed"
    echo "📱 Your optimized TensorFlow Lite model is ready for Android"
    echo "📍 Location: androidApp/src/main/assets/models/license_plate_detector.tflite"
    echo ""
    echo "🏎️  Model features:"
    echo "   • INT8 quantization for maximum speed"
    echo "   • Mobile-optimized for Android deployment"
    echo "   • Compatible with TensorFlow Lite Android API"
    echo ""
    echo "💡 Next steps:"
    echo "   1. Build your Android app"
    echo "   2. Test license plate detection performance"
    echo "   3. Consider further optimizations if needed"
else
    echo "❌ Conversion failed! Check the error messages above."
    exit 1
fi 