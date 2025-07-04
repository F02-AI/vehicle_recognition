#!/usr/bin/env python3
"""
TensorFlow Lite License Plate Detection Test Script
Tests the converted YOLO TFLite model on video files with real-time visualization
"""

import cv2
import numpy as np
import tensorflow as tf
import time
import argparse
from pathlib import Path
import sys

class TFLiteDetector:
    """TensorFlow Lite YOLO detector wrapper"""
    
    def __init__(self, model_path, confidence_threshold=0.25, nms_threshold=0.45):
        self.model_path = model_path
        self.confidence_threshold = confidence_threshold
        self.nms_threshold = nms_threshold
        
        # Load TFLite model
        self.interpreter = tf.lite.Interpreter(model_path=model_path)
        self.interpreter.allocate_tensors()
        
        # Get input and output details
        self.input_details = self.interpreter.get_input_details()
        self.output_details = self.interpreter.get_output_details()
        
        # Get input shape
        self.input_shape = self.input_details[0]['shape']
        self.input_height = self.input_shape[1]
        self.input_width = self.input_shape[2]
        
        print(f"✅ TFLite model loaded: {model_path}")
        print(f"   Input shape: {self.input_shape}")
        print(f"   Input type: {self.input_details[0]['dtype']}")
        print(f"   Output shape: {self.output_details[0]['shape']}")
        
        # Performance tracking
        self.inference_times = []
        self.preprocessing_times = []
        self.postprocessing_times = []
    
    def preprocess_image(self, image):
        """Preprocess image for YOLO input"""
        start_time = time.time()
        
        # Resize image to model input size
        input_image = cv2.resize(image, (self.input_width, self.input_height))
        
        # Convert BGR to RGB
        input_image = cv2.cvtColor(input_image, cv2.COLOR_BGR2RGB)
        
        # Normalize to [0, 1] or [0, 255] depending on model
        if self.input_details[0]['dtype'] == np.uint8:
            input_image = input_image.astype(np.uint8)
        else:
            input_image = input_image.astype(np.float32) / 255.0
        
        # Add batch dimension
        input_image = np.expand_dims(input_image, axis=0)
        
        self.preprocessing_times.append(time.time() - start_time)
        return input_image
    
    def postprocess_outputs(self, outputs, original_image_shape):
        """Postprocess YOLO outputs to get bounding boxes"""
        start_time = time.time()
        
        # Get output tensor
        output = outputs[0]
        
        # Handle different output formats
        if len(output.shape) == 3:
            # Shape: (1, num_detections, 85) -> (num_detections, 85)
            output = output[0]
            
        # CRITICAL FIX: The TFLite model has a transposed output format
        # Standard YOLO output is (num_detections, 5+classes), e.g., (8400, 5)
        # This TFLite model outputs (5+classes, num_detections), e.g., (5, 8400)
        # We need to transpose it back to the standard format.
        if output.shape[0] < output.shape[1]:
            print("   ⚠️ Detected transposed output. Transposing to fix.")
            output = output.T # Transpose from (5, 8400) to (8400, 5)

        # Extract boxes, scores, and class predictions
        # YOLO output format: [x_center, y_center, width, height, confidence, class_scores...]
        if output.shape[1] >= 5:
            boxes = output[:, :4]  # x, y, w, h
            confidence = output[:, 4]  # objectness confidence
            
            # If there are class scores, use them; otherwise use confidence
            if output.shape[1] > 5:
                class_scores = output[:, 5:]
                class_confidence = np.max(class_scores, axis=1)
                final_confidence = confidence * class_confidence
            else:
                final_confidence = confidence
        else:
            # Fallback for unexpected output format
            boxes = output[:, :4]
            final_confidence = np.ones(len(boxes)) * 0.5
        
        # Filter by confidence threshold
        valid_detections = final_confidence > self.confidence_threshold
        boxes = boxes[valid_detections]
        final_confidence = final_confidence[valid_detections]
        
        # Convert center coordinates to corner coordinates
        detections = []
        orig_h, orig_w = original_image_shape[:2]
        
        for i, (box, conf) in enumerate(zip(boxes, final_confidence)):
            # YOLO format: center_x, center_y, width, height (normalized)
            center_x, center_y, width, height = box
            
            # Convert to pixel coordinates
            center_x *= orig_w
            center_y *= orig_h
            width *= orig_w
            height *= orig_h
            
            # Convert to corner coordinates
            x1 = int(center_x - width / 2)
            y1 = int(center_y - height / 2)
            x2 = int(center_x + width / 2)
            y2 = int(center_y + height / 2)
            
            # Ensure coordinates are within image bounds
            x1 = max(0, min(x1, orig_w))
            y1 = max(0, min(y1, orig_h))
            x2 = max(0, min(x2, orig_w))
            y2 = max(0, min(y2, orig_h))
            
            detections.append({
                'bbox': [x1, y1, x2, y2],
                'confidence': float(conf),
                'class': 'license_plate'
            })
        
        # Apply Non-Maximum Suppression
        if len(detections) > 1:
            boxes_nms = np.array([det['bbox'] for det in detections])
            scores_nms = np.array([det['confidence'] for det in detections])
            
            # Convert to [x, y, w, h] format for NMS
            boxes_nms_format = boxes_nms.copy()
            boxes_nms_format[:, 2] = boxes_nms[:, 2] - boxes_nms[:, 0]  # width
            boxes_nms_format[:, 3] = boxes_nms[:, 3] - boxes_nms[:, 1]  # height
            
            indices = cv2.dnn.NMSBoxes(
                boxes_nms_format.tolist(),
                scores_nms.tolist(),
                self.confidence_threshold,
                self.nms_threshold
            )
            
            if len(indices) > 0:
                indices = indices.flatten()
                detections = [detections[i] for i in indices]
        
        self.postprocessing_times.append(time.time() - start_time)
        return detections
    
    def detect(self, image):
        """Run detection on a single image"""
        # Preprocess
        input_image = self.preprocess_image(image)
        
        # Run inference
        start_time = time.time()
        self.interpreter.set_tensor(self.input_details[0]['index'], input_image)
        self.interpreter.invoke()
        outputs = [self.interpreter.get_tensor(output['index']) for output in self.output_details]
        self.inference_times.append(time.time() - start_time)
        
        # Postprocess
        detections = self.postprocess_outputs(outputs, image.shape)
        
        return detections
    
    def get_performance_stats(self):
        """Get performance statistics"""
        if not self.inference_times:
            return {}
        
        return {
            'avg_inference_time': np.mean(self.inference_times) * 1000,  # ms
            'avg_preprocessing_time': np.mean(self.preprocessing_times) * 1000,  # ms
            'avg_postprocessing_time': np.mean(self.postprocessing_times) * 1000,  # ms
            'avg_total_time': (np.mean(self.inference_times) + 
                             np.mean(self.preprocessing_times) + 
                             np.mean(self.postprocessing_times)) * 1000,
            'fps': 1.0 / (np.mean(self.inference_times) + 
                         np.mean(self.preprocessing_times) + 
                         np.mean(self.postprocessing_times)),
            'total_detections': len(self.inference_times)
        }

def draw_detections(image, detections):
    """Draw detection boxes and labels on image"""
    for detection in detections:
        x1, y1, x2, y2 = detection['bbox']
        confidence = detection['confidence']
        
        # Draw bounding box
        cv2.rectangle(image, (x1, y1), (x2, y2), (0, 255, 0), 2)
        
        # Draw confidence score
        label = f"License Plate: {confidence:.2f}"
        label_size = cv2.getTextSize(label, cv2.FONT_HERSHEY_SIMPLEX, 0.5, 2)[0]
        
        # Draw label background
        cv2.rectangle(image, (x1, y1 - label_size[1] - 10), 
                     (x1 + label_size[0], y1), (0, 255, 0), -1)
        
        # Draw label text
        cv2.putText(image, label, (x1, y1 - 5), 
                   cv2.FONT_HERSHEY_SIMPLEX, 0.5, (0, 0, 0), 2)
    
    return image

def test_video(model_path, video_path, output_path=None, show_video=True):
    """Test TFLite model on video file"""
    print(f"🎬 Testing TFLite model on video: {video_path}")
    
    # Initialize detector
    detector = TFLiteDetector(model_path)
    
    # Open video
    cap = cv2.VideoCapture(video_path)
    if not cap.isOpened():
        print(f"❌ Error: Could not open video {video_path}")
        return False
    
    # Get video properties
    fps = cap.get(cv2.CAP_PROP_FPS)
    width = int(cap.get(cv2.CAP_PROP_FRAME_WIDTH))
    height = int(cap.get(cv2.CAP_PROP_FRAME_HEIGHT))
    total_frames = int(cap.get(cv2.CAP_PROP_FRAME_COUNT))
    
    print(f"📹 Video info: {width}x{height} @ {fps:.1f} FPS, {total_frames} frames")
    
    # Setup video writer if output path specified
    out_writer = None
    if output_path:
        fourcc = cv2.VideoWriter_fourcc(*'mp4v')
        out_writer = cv2.VideoWriter(output_path, fourcc, fps, (width, height))
        print(f"💾 Output video will be saved to: {output_path}")
    
    frame_count = 0
    detection_count = 0
    start_time = time.time()
    
    print("\n🚀 Starting detection...")
    print("Press 'q' to quit, 'space' to pause/resume")
    
    paused = False
    
    try:
        while True:
            if not paused:
                ret, frame = cap.read()
                if not ret:
                    break
                
                frame_count += 1
                
                # Run detection
                detections = detector.detect(frame)
                detection_count += len(detections)
                
                # Draw detections
                frame_with_detections = draw_detections(frame.copy(), detections)
                
                # Add performance info
                if frame_count > 1:  # Skip first frame for accurate timing
                    stats = detector.get_performance_stats()
                    info_text = [
                        f"Frame: {frame_count}/{total_frames}",
                        f"Detections: {len(detections)}",
                        f"Processing: {stats.get('avg_total_time', 0):.1f}ms",
                        f"FPS: {stats.get('fps', 0):.1f}",
                        f"Total detected: {detection_count}"
                    ]
                    
                    for i, text in enumerate(info_text):
                        cv2.putText(frame_with_detections, text, (10, 30 + i * 25),
                                   cv2.FONT_HERSHEY_SIMPLEX, 0.7, (255, 255, 255), 2)
                        cv2.putText(frame_with_detections, text, (10, 30 + i * 25),
                                   cv2.FONT_HERSHEY_SIMPLEX, 0.7, (0, 0, 0), 1)
                
                # Save frame if output writer is available
                if out_writer:
                    out_writer.write(frame_with_detections)
                
                # Show frame
                if show_video:
                    cv2.imshow('License Plate Detection Test', frame_with_detections)
            
            # Handle keyboard input
            if show_video:
                key = cv2.waitKey(1) & 0xFF
                if key == ord('q'):
                    break
                elif key == ord(' '):
                    paused = not paused
                    print(f"{'⏸️ Paused' if paused else '▶️ Resumed'}")
            
            # Progress update
            if frame_count % 30 == 0:
                progress = (frame_count / total_frames) * 100
                print(f"📊 Progress: {progress:.1f}% ({frame_count}/{total_frames})")
    
    except KeyboardInterrupt:
        print("\n⚠️ Interrupted by user")
    
    finally:
        # Cleanup
        cap.release()
        if out_writer:
            out_writer.release()
        if show_video:
            cv2.destroyAllWindows()
    
    # Final statistics
    total_time = time.time() - start_time
    stats = detector.get_performance_stats()
    
    print("\n📊 Final Performance Report:")
    print("=" * 50)
    print(f"🎬 Video processed: {frame_count} frames in {total_time:.1f}s")
    print(f"🎯 Total detections: {detection_count}")
    print(f"⚡ Average processing time: {stats.get('avg_total_time', 0):.1f}ms per frame")
    print(f"   - Preprocessing: {stats.get('avg_preprocessing_time', 0):.1f}ms")
    print(f"   - Inference: {stats.get('avg_inference_time', 0):.1f}ms")
    print(f"   - Postprocessing: {stats.get('avg_postprocessing_time', 0):.1f}ms")
    print(f"🚀 Average FPS: {stats.get('fps', 0):.1f}")
    print(f"📈 Detection rate: {detection_count/frame_count:.2f} detections/frame")
    
    if output_path:
        print(f"💾 Output saved to: {output_path}")
    
    return True

def main():
    """Main function"""
    parser = argparse.ArgumentParser(description='Test TensorFlow Lite license plate detection model')
    parser.add_argument('--model', default='androidApp/src/main/assets/models/license_plate_detector.tflite',
                       help='Path to TFLite model file')
    parser.add_argument('--video', default='script_videos/luxury.mp4',
                       help='Path to input video file')
    parser.add_argument('--output', default=None,
                       help='Path to output video file (optional)')
    parser.add_argument('--no-display', action='store_true',
                       help='Disable video display (for headless operation)')
    parser.add_argument('--confidence', type=float, default=0.25,
                       help='Confidence threshold for detection')
    parser.add_argument('--nms', type=float, default=0.45,
                       help='NMS threshold for detection')
    
    args = parser.parse_args()
    
    # Check if model file exists
    if not Path(args.model).exists():
        print(f"❌ Model file not found: {args.model}")
        print("Please run the conversion script first!")
        return False
    
    # Check if video file exists
    if not Path(args.video).exists():
        print(f"❌ Video file not found: {args.video}")
        return False
    
    # Set default output path if not specified
    if args.output is None:
        video_stem = Path(args.video).stem
        args.output = f"script_output/{video_stem}_detection_results.mp4"
        
        # Create output directory if it doesn't exist
        Path("script_output").mkdir(exist_ok=True)
    
    print("🚀 TensorFlow Lite License Plate Detection Test")
    print("=" * 50)
    print(f"📱 Model: {args.model}")
    print(f"🎬 Video: {args.video}")
    print(f"💾 Output: {args.output}")
    print(f"🎯 Confidence threshold: {args.confidence}")
    print(f"🔄 NMS threshold: {args.nms}")
    print()
    
    # Run test
    success = test_video(
        model_path=args.model,
        video_path=args.video,
        output_path=args.output,
        show_video=not args.no_display
    )
    
    if success:
        print("\n✅ Test completed successfully!")
    else:
        print("\n❌ Test failed!")
    
    return success

if __name__ == "__main__":
    success = main()
    sys.exit(0 if success else 1) 