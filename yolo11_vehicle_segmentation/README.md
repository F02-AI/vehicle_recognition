# YOLO11 Vehicle Segmentation

A Python script for detecting and segmenting vehicles (Car, Motorcycle, Truck) using YOLO11 segmentation models from Ultralytics.

## Features

- **Instance Segmentation**: Provides pixel-level segmentation masks for detected vehicles
- **Multi-class Detection**: Detects Cars, Motorcycles, and Trucks
- **Multiple Input Types**: Supports single images, videos, and batch processing of directories
- **Configurable Models**: Choose from different YOLO11 model sizes (nano to extra-large)
- **Visual Results**: Colored segmentation masks with bounding boxes and confidence scores
- **Real-time Processing**: Live video playback with segmentation overlay and FPS counter
- **Interactive Controls**: Pause/resume and quit functionality during real-time processing

## Installation

1. Clone or download this folder to your local machine
2. Install the required dependencies:

```bash
pip install -r requirements.txt
```

## Usage

### Basic Usage

```bash
# Process a single image
python vehicle_segmentation.py -i path/to/image.jpg

# Process a video (saves output file)
python vehicle_segmentation.py -i path/to/video.mp4

# Process a video in real-time (live display + saves output)
python vehicle_segmentation.py -i path/to/video.mp4 -r

# Process all images in a directory
python vehicle_segmentation.py -i path/to/image_directory/
```

### Advanced Usage

```bash
# Use different model sizes
python vehicle_segmentation.py -i image.jpg -m n    # Nano (fastest)
python vehicle_segmentation.py -i image.jpg -m s    # Small (default)
python vehicle_segmentation.py -i image.jpg -m m    # Medium
python vehicle_segmentation.py -i image.jpg -m l    # Large
python vehicle_segmentation.py -i image.jpg -m x    # Extra Large (most accurate)

# Set confidence threshold
python vehicle_segmentation.py -i image.jpg -c 0.7   # 70% confidence

# Specify output directory
python vehicle_segmentation.py -i image.jpg -o results/

# Real-time video processing with different options
python vehicle_segmentation.py -i video.mp4 -r              # Real-time + save output
python vehicle_segmentation.py -i video.mp4 -r --no-save    # Real-time only (no file output)
python vehicle_segmentation.py -i video.mp4 -r -m n         # Real-time with nano model (fastest)
python vehicle_segmentation.py -i video.mp4 -r -c 0.8       # Real-time with high confidence

# Force input type (if auto-detection fails)
python vehicle_segmentation.py -i input.jpg -t image
```

### Command Line Arguments

| Argument | Short | Description | Default |
|----------|-------|-------------|---------|
| `--input` | `-i` | Input image, video, or directory path | Required |
| `--output` | `-o` | Output directory | `output` |
| `--model-size` | `-m` | YOLO11 model size (n/s/m/l/x) | `s` |
| `--conf` | `-c` | Confidence threshold (0.0-1.0) | `0.5` |
| `--type` | `-t` | Input type (image/video/directory) | Auto-detected |
| `--realtime` | `-r` | Enable real-time video playback | `False` |
| `--no-save` | | Disable saving output video (real-time only) | `False` |

## Model Sizes

| Size | Model | Speed | Accuracy | Parameters |
|------|-------|-------|----------|------------|
| n | yolo11n-seg.pt | Fastest | Good | 2.7M |
| s | yolo11s-seg.pt | Fast | Better | 10.1M |
| m | yolo11m-seg.pt | Medium | Good | 22.6M |
| l | yolo11l-seg.pt | Slow | Better | 27.8M |
| x | yolo11x-seg.pt | Slowest | Best | 62.1M |

## Vehicle Classes

The script detects and segments these vehicle types:

- **Car** (Class ID: 2) - Green color
- **Motorcycle** (Class ID: 3) - Blue color  
- **Truck** (Class ID: 7) - Red color

## Output

The script generates:

1. **Segmented Images/Videos**: Original input with colored segmentation masks overlaid
2. **Bounding Boxes**: Rectangle around each detected vehicle
3. **Labels**: Class name and confidence score for each detection
4. **Console Output**: Summary of detections and processing statistics
5. **Real-time Display** (video only): Live video window with segmentation overlay
6. **Performance Stats**: FPS counter (top-right) and vehicle count (top-left) in real-time mode

## Real-time Mode Controls

When using real-time mode (`-r` flag), the following keyboard controls are available:

- **'q'**: Quit the application
- **'p'**: Pause/resume video playback
- **ESC**: Close the video window

The real-time display shows:
- **FPS Counter**: Current processing speed (top-right corner)
- **Vehicle Count**: Number of detected vehicles in current frame (top-left corner)
- **Segmentation Masks**: Colored overlays for detected vehicles
- **Bounding Boxes**: Rectangles around detected vehicles with confidence scores

## Examples

### Single Image Processing
```bash
python vehicle_segmentation.py -i sample_image.jpg -o results/
```

Output:
```
Loading YOLO11 segmentation model: yolo11s-seg.pt
Model loaded successfully: yolo11s-seg.pt
Processed: sample_image.jpg
Found 3 vehicle(s)
Result saved to: results/segmented_sample_image.jpg
```

### Video Processing
```bash
python vehicle_segmentation.py -i traffic_video.mp4 -c 0.6 -o video_results/
```

Output:
```
Loading YOLO11 segmentation model: yolo11s-seg.pt
Model loaded successfully: yolo11s-seg.pt
Processing video: traffic_video.mp4
Video properties: 1920x1080 @ 30 FPS
Processed 30 frames...
Processed 60 frames...
...
Video processing complete!
Total frames processed: 300
Total vehicle detections: 450
Result saved to: video_results/segmented_traffic_video.mp4
```

### Real-time Video Processing
```bash
python vehicle_segmentation.py -i traffic_video.mp4 -r -m n -c 0.6
```

Output:
```
Loading YOLO11 segmentation model: yolo11n-seg.pt
Model loaded successfully: yolo11n-seg.pt
Processing video: traffic_video.mp4
Video properties: 1920x1080 @ 30 FPS
Real-time mode enabled. Press 'q' to quit, 'p' to pause/resume
[Opens video window with live segmentation]
Video processing complete!
Total frames processed: 300
Total vehicle detections: 450
Result saved to: output/segmented_traffic_video.mp4
```

### Directory Processing
```bash
python vehicle_segmentation.py -i image_folder/ -m m -o batch_results/
```

Output:
```
Loading YOLO11 segmentation model: yolo11m-seg.pt
Model loaded successfully: yolo11m-seg.pt
Found 15 images in image_folder
Processed: image1.jpg
Found 2 vehicle(s)
Result saved to: batch_results/segmented_image1.jpg
...
Directory processing complete!
Total vehicle detections across all images: 28
```

## Technical Details

- **Model Architecture**: YOLO11 segmentation models from Ultralytics
- **Framework**: PyTorch backend with OpenCV for image processing
- **Input Formats**: JPG, PNG, BMP, TIFF, WebP images; MP4, AVI, MOV, MKV, WMV videos
- **Output Format**: Same as input format for images; MP4 for videos

## Performance Tips

1. **Model Size**: Use smaller models (n, s) for real-time applications, larger models (l, x) for accuracy
2. **Confidence Threshold**: Lower values detect more objects but may include false positives
3. **GPU Acceleration**: Script automatically uses GPU if available and CUDA is installed
4. **Batch Processing**: For many images, directory processing is more efficient than individual calls
5. **Real-time Performance**: 
   - Use nano model (`-m n`) for best real-time performance
   - Consider using `--no-save` flag to skip file output and improve speed
   - Lower confidence thresholds may slow down processing due to more detections
   - Close other applications to free up system resources

## Troubleshooting

### Common Issues

1. **"No module named 'ultralytics'"**: Install requirements with `pip install -r requirements.txt`
2. **"Model download failed"**: Check internet connection; models download automatically on first use
3. **"CUDA out of memory"**: Use smaller model size or reduce input resolution
4. **"No vehicles detected"**: Lower confidence threshold or check input image quality

### Requirements

- Python 3.8+
- 4GB+ RAM (8GB+ recommended for larger models)
- GPU with CUDA support (optional but recommended)
- Internet connection for initial model download

## License

This script uses the Ultralytics YOLO11 models which are licensed under AGPL-3.0 for open source use. 