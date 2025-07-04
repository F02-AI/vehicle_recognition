# YOLO11 Segmentation Model Conversion to TensorFlow Lite

This guide explains how to convert YOLO11 segmentation models (PyTorch .pt) to TensorFlow Lite (.tflite) format for mobile deployment.

## 📋 Overview

The conversion process takes YOLO11 segmentation models and converts them to TensorFlow Lite format, making them suitable for mobile and edge device deployment. This is useful for integrating vehicle segmentation into Android/iOS apps or other mobile applications.

## 🛠️ Prerequisites

- **Python 3.8+** with pip
- **Ultralytics** package (`pip install ultralytics`)
- **Internet connection** (for downloading models)
- **Sufficient disk space** (models can be 10-130MB each)

## 📁 Files

- `convert_yolo11_seg_to_tflite.sh` - Bash script wrapper
- `convert_yolo11_seg_to_tflite.py` - Python conversion script
- `converted_models/` - Output directory for converted models

## 🚀 Quick Start

### Method 1: Using the Bash Script (Recommended)

```bash
# Make sure you're in the yolo11_vehicle_segmentation directory
./convert_yolo11_seg_to_tflite.sh
```

### Method 2: Direct Python Execution

```bash
python3 convert_yolo11_seg_to_tflite.py
```

## 🎯 Model Selection

When you run the script, you'll be prompted to select which model size to convert:

```
📋 Available YOLO11 Segmentation Model Sizes:
============================================================
  N: Nano         | 2.7M     | Fastest
  S: Small        | 10.1M    | Fast
  M: Medium       | 22.6M    | Medium
  L: Large        | 27.8M    | Slow
  X: Extra Large  | 62.1M    | Slowest
============================================================

🎯 Select model size (n/s/m/l/x) or 'all' for all sizes:
```

**Options:**
- `n` - Convert only Nano model (fastest, smallest)
- `s` - Convert only Small model (default, balanced)
- `m` - Convert only Medium model
- `l` - Convert only Large model
- `x` - Convert only Extra Large model (most accurate, largest)
- `all` - Convert all model sizes

## 💡 Model Size Recommendations

| Size | Best For | Use Case |
|------|----------|----------|
| **Nano (n)** | Real-time mobile apps | Live camera processing, resource-constrained devices |
| **Small (s)** | Balanced performance | General mobile applications, good speed/accuracy balance |
| **Medium (m)** | Better accuracy | Complex scenes, when accuracy is more important than speed |
| **Large (l)** | High accuracy apps | Desktop applications, powerful mobile devices |
| **Extra Large (x)** | Maximum accuracy | Offline processing, when speed is not critical |

## 📝 Example Usage

### Convert Single Model
```bash
./convert_yolo11_seg_to_tflite.sh
# Select 'n' for Nano model
```

### Convert All Models
```bash
./convert_yolo11_seg_to_tflite.sh
# Select 'all' to convert all sizes
```

## 📊 Example Output

```
🚀 YOLO11 Segmentation to TensorFlow Lite Conversion Script
============================================================
✅ Ultralytics version: 8.3.0

📋 Available YOLO11 Segmentation Model Sizes:
============================================================
  N: Nano         | 2.7M     | Fastest
  S: Small        | 10.1M    | Fast
  M: Medium       | 22.6M    | Medium
  L: Large        | 27.8M    | Slow
  X: Extra Large  | 62.1M    | Slowest
============================================================

🎯 Select model size (n/s/m/l/x) or 'all' for all sizes: s

🎯 Converting 1 model(s): S

🔄 Converting YOLO11S-seg model...
   Model: yolo11s-seg.pt
   Info: Small (10.1M params)
📥 Loading YOLO11 model: yolo11s-seg.pt
✅ YOLO11 model loaded successfully
🔄 Exporting to TensorFlow Lite format...
   This may take several minutes...
✅ TFLite export successful: yolo11s-seg.tflite
   Exported model size: 21.45 MB
✅ Model saved to: converted_models/yolo11s-seg.tflite
   Final model size: 21.45 MB

============================================================
📊 CONVERSION SUMMARY
============================================================
✅ Successful conversions (1):
   • YOLO11S-seg → yolo11s-seg.tflite (21.45 MB)

🎉 Conversion completed successfully!
📁 Converted models location: /path/to/converted_models/
⚡ Features: FP32 precision, segmentation-optimized, mobile-ready
🚗 Compatible with: Car, Motorcycle, Truck segmentation
```

## 📂 Output Structure

After conversion, you'll find the models in:

```
yolo11_vehicle_segmentation/
├── converted_models/
│   ├── yolo11n-seg.tflite    # Nano model
│   ├── yolo11s-seg.tflite    # Small model
│   ├── yolo11m-seg.tflite    # Medium model
│   ├── yolo11l-seg.tflite    # Large model
│   └── yolo11x-seg.tflite    # Extra Large model
└── ...
```

## 🔧 Technical Details

### Conversion Settings
- **Format**: TensorFlow Lite (.tflite)
- **Precision**: FP32 (full precision, no quantization)
- **Input Size**: 640x640 pixels
- **Optimizations**: Enabled for mobile deployment
- **Segmentation**: Supports pixel-level vehicle segmentation

### Model Capabilities
- **Classes**: Car, Motorcycle, Truck (from COCO dataset)
- **Output**: Bounding boxes + segmentation masks
- **Inference**: Compatible with TensorFlow Lite runtime
- **Platforms**: Android, iOS, Linux, Windows

## 🚨 Troubleshooting

### Common Issues

1. **"Ultralytics not found"**
   ```bash
   pip install ultralytics
   ```

2. **"Model download failed"**
   - Check internet connection
   - Ensure sufficient disk space
   - Try again (downloads can be interrupted)

3. **"Conversion failed"**
   - Verify Python version (3.8+)
   - Check available disk space
   - Try converting one model at a time

4. **"Permission denied"**
   ```bash
   chmod +x convert_yolo11_seg_to_tflite.sh
   ```

### File Size Issues
- Models range from ~5MB (Nano) to ~130MB (Extra Large)
- Ensure you have enough disk space
- Each model downloads temporarily then converts

## 🔄 Integration

### Android Integration
```java
// Load the TensorFlow Lite model
Interpreter tflite = new Interpreter(loadModelFile("yolo11s-seg.tflite"));

// Run inference
tflite.run(inputBuffer, outputBuffer);
```

### Python Integration
```python
import tflite_runtime.interpreter as tflite

# Load the model
interpreter = tflite.Interpreter(model_path="yolo11s-seg.tflite")
interpreter.allocate_tensors()

# Run inference
interpreter.set_tensor(input_details[0]['index'], input_data)
interpreter.invoke()
output_data = interpreter.get_tensor(output_details[0]['index'])
```

## 💻 Performance Expectations

| Model Size | Mobile CPU | Mobile GPU | Inference Time* |
|------------|------------|------------|----------------|
| Nano (n)   | ~50-100ms  | ~20-40ms   | Real-time     |
| Small (s)  | ~100-200ms | ~40-80ms   | Near real-time |
| Medium (m) | ~200-400ms | ~80-160ms  | Batch processing |
| Large (l)  | ~400-800ms | ~160-320ms | Offline use    |
| Extra Large (x) | ~800ms+ | ~320ms+   | High accuracy  |

*Approximate times for 640x640 input on mid-range mobile devices

## 📚 Additional Resources

- [Ultralytics YOLO11 Documentation](https://docs.ultralytics.com/models/yolo11/)
- [TensorFlow Lite Documentation](https://www.tensorflow.org/lite)
- [Mobile Deployment Guide](https://docs.ultralytics.com/guides/mobile-deployment/)

## 🤝 Support

If you encounter issues:
1. Check the troubleshooting section above
2. Verify your dependencies are up to date
3. Ensure you have sufficient system resources
4. Try converting models individually rather than all at once 