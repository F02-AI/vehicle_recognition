#!/usr/bin/env python3
"""
YOLO11 Vehicle Segmentation Script
Detects and segments Car, Motorcycle, and Truck classes using YOLO11-seg model
"""

import argparse
import cv2
import numpy as np
from pathlib import Path
from ultralytics import YOLO
import os
import time

# COCO class names that correspond to our target vehicles
TARGET_CLASSES = {
    'car': 2,
    'motorcycle': 3, 
    'truck': 7
}

# Class names for display
CLASS_NAMES = {2: 'Car', 3: 'Motorcycle', 7: 'Truck'}

# Colors for visualization (BGR format)
CLASS_COLORS = {
    2: (0, 255, 0),    # Car - Green
    3: (255, 0, 0),    # Motorcycle - Blue  
    7: (0, 0, 255)     # Truck - Red
}

class VehicleSegmentation:
    def __init__(self, model_size='s'):
        """
        Initialize the vehicle segmentation model
        
        Args:
            model_size (str): YOLO11 model size ('n', 's', 'm', 'l', 'x')
        """
        self.model_name = f"yolo11{model_size}-seg.pt"
        print(f"Loading YOLO11 segmentation model: {self.model_name}")
        
        # Load the model (will download if not present)
        self.model = YOLO(self.model_name)
        
        # Verify model loaded successfully
        print(f"Model loaded successfully: {self.model_name}")
        
    def process_image(self, image_path, output_dir="output", conf_threshold=0.5):
        """
        Process a single image for vehicle segmentation
        
        Args:
            image_path (str): Path to input image
            output_dir (str): Directory to save results
            conf_threshold (float): Confidence threshold for detections
        """
        image_path = Path(image_path)
        output_dir = Path(output_dir)
        output_dir.mkdir(exist_ok=True)
        
        # Run inference
        results = self.model(str(image_path), conf=conf_threshold)
        
        # Process results
        for r in results:
            # Get original image
            img = cv2.imread(str(image_path))
            
            # Filter for vehicle classes only
            vehicle_detections = []
            
            if r.boxes is not None and r.masks is not None:
                for i, (box, mask) in enumerate(zip(r.boxes, r.masks)):
                    class_id = int(box.cls[0])
                    confidence = float(box.conf[0])
                    
                    # Only process vehicle classes
                    if class_id in CLASS_NAMES:
                        vehicle_detections.append({
                            'class_id': class_id,
                            'confidence': confidence,
                            'box': box.xyxy[0].cpu().numpy(),
                            'mask': mask.data[0].cpu().numpy()
                        })
            
            # Draw results
            result_img = self.draw_results(img, vehicle_detections)
            
            # Save result
            output_path = output_dir / f"segmented_{image_path.name}"
            cv2.imwrite(str(output_path), result_img)
            
            print(f"Processed: {image_path.name}")
            print(f"Found {len(vehicle_detections)} vehicle(s)")
            print(f"Result saved to: {output_path}")
            
            return result_img, vehicle_detections
    
    def process_video(self, video_path, output_dir="output", conf_threshold=0.5, realtime=False, save_output=True):
        """
        Process a video for vehicle segmentation
        
        Args:
            video_path (str): Path to input video
            output_dir (str): Directory to save results
            conf_threshold (float): Confidence threshold for detections
            realtime (bool): Whether to display video in real-time
            save_output (bool): Whether to save output video
        """
        video_path = Path(video_path)
        output_dir = Path(output_dir)
        output_dir.mkdir(exist_ok=True)
        
        # Open video
        cap = cv2.VideoCapture(str(video_path))
        
        # Get video properties
        fps = int(cap.get(cv2.CAP_PROP_FPS))
        width = int(cap.get(cv2.CAP_PROP_FRAME_WIDTH))
        height = int(cap.get(cv2.CAP_PROP_FRAME_HEIGHT))
        
        # Setup output video writer if saving
        out = None
        output_path = None
        if save_output:
            output_path = output_dir / f"segmented_{video_path.name}"
            fourcc = cv2.VideoWriter_fourcc(*'mp4v')
            out = cv2.VideoWriter(str(output_path), fourcc, fps, (width, height))
        
        frame_count = 0
        vehicle_count_total = 0
        
        # FPS calculation variables
        fps_counter = 0
        fps_start_time = time.time()
        current_fps = 0
        
        print(f"Processing video: {video_path.name}")
        print(f"Video properties: {width}x{height} @ {fps} FPS")
        
        if realtime:
            print("Real-time mode enabled. Press 'q' to quit, 'p' to pause/resume")
            cv2.namedWindow('Vehicle Segmentation', cv2.WINDOW_NORMAL)
            paused = False
        
        while True:
            if not paused or not realtime:
                ret, frame = cap.read()
                if not ret:
                    break
                    
                frame_count += 1
                
                # Start timing for FPS calculation
                frame_start_time = time.time()
                
                # Run inference on frame
                results = self.model(frame, conf=conf_threshold, verbose=False)
                
                # Process results
                vehicle_detections = []
                for r in results:
                    if r.boxes is not None and r.masks is not None:
                        for i, (box, mask) in enumerate(zip(r.boxes, r.masks)):
                            class_id = int(box.cls[0])
                            confidence = float(box.conf[0])
                            
                            # Only process vehicle classes
                            if class_id in CLASS_NAMES:
                                vehicle_detections.append({
                                    'class_id': class_id,
                                    'confidence': confidence,
                                    'box': box.xyxy[0].cpu().numpy(),
                                    'mask': mask.data[0].cpu().numpy()
                                })
                
                # Draw results on frame
                result_frame = self.draw_results(frame, vehicle_detections)
                
                # Calculate FPS
                fps_counter += 1
                if fps_counter % 10 == 0:  # Update FPS every 10 frames
                    elapsed_time = time.time() - fps_start_time
                    current_fps = fps_counter / elapsed_time
                    fps_counter = 0
                    fps_start_time = time.time()
                
                # Add FPS text overlay (top right)
                fps_text = f"FPS: {current_fps:.1f}"
                font = cv2.FONT_HERSHEY_SIMPLEX
                font_scale = 0.7
                font_thickness = 2
                
                # Get text size for positioning
                (text_width, text_height), baseline = cv2.getTextSize(fps_text, font, font_scale, font_thickness)
                
                # Position at top right with padding
                x = width - text_width - 10
                y = text_height + 20
                
                # Draw background rectangle for better visibility
                cv2.rectangle(result_frame, (x - 5, y - text_height - 5), 
                             (x + text_width + 5, y + 5), (0, 0, 0), -1)
                
                # Draw FPS text
                cv2.putText(result_frame, fps_text, (x, y), font, font_scale, (0, 255, 255), font_thickness)
                
                # Add vehicle count text (top left)
                vehicle_text = f"Vehicles: {len(vehicle_detections)}"
                cv2.rectangle(result_frame, (5, 5), (200, 35), (0, 0, 0), -1)
                cv2.putText(result_frame, vehicle_text, (10, 25), font, font_scale, (0, 255, 255), font_thickness)
                
                # Write frame to output video if saving
                if save_output and out is not None:
                    out.write(result_frame)
                
                # Display in real-time if enabled
                if realtime:
                    cv2.imshow('Vehicle Segmentation', result_frame)
                    
                    # Handle key presses
                    key = cv2.waitKey(1) & 0xFF
                    if key == ord('q'):
                        print("Quit requested by user")
                        break
                    elif key == ord('p'):
                        paused = not paused
                        if paused:
                            print("Paused - press 'p' to resume")
                        else:
                            print("Resumed")
                
                vehicle_count_total += len(vehicle_detections)
                
                # Print progress every 30 frames (only if not in real-time mode)
                if not realtime and frame_count % 30 == 0:
                    print(f"Processed {frame_count} frames...")
            
            else:
                # In paused state, just wait for key press
                key = cv2.waitKey(30) & 0xFF
                if key == ord('q'):
                    print("Quit requested by user")
                    break
                elif key == ord('p'):
                    paused = False
                    print("Resumed")
        
        # Release everything
        cap.release()
        if out is not None:
            out.release()
        
        if realtime:
            cv2.destroyAllWindows()
        
        print(f"Video processing complete!")
        print(f"Total frames processed: {frame_count}")
        print(f"Total vehicle detections: {vehicle_count_total}")
        if save_output:
            print(f"Result saved to: {output_path}")
        
        return output_path
    
    def draw_results(self, img, detections):
        """
        Draw segmentation results on image
        
        Args:
            img (numpy.ndarray): Input image
            detections (list): List of detection dictionaries
            
        Returns:
            numpy.ndarray: Image with results drawn
        """
        result_img = img.copy()
        
        for detection in detections:
            class_id = detection['class_id']
            confidence = detection['confidence']
            box = detection['box']
            mask = detection['mask']
            
            # Get class info
            class_name = CLASS_NAMES[class_id]
            color = CLASS_COLORS[class_id]
            
            # Resize mask to match image dimensions
            mask_resized = cv2.resize(mask, (img.shape[1], img.shape[0]))
            
            # Create colored mask
            colored_mask = np.zeros_like(img)
            colored_mask[mask_resized > 0.5] = color
            
            # Blend mask with original image
            result_img = cv2.addWeighted(result_img, 0.7, colored_mask, 0.3, 0)
            
            # Draw bounding box
            x1, y1, x2, y2 = box.astype(int)
            cv2.rectangle(result_img, (x1, y1), (x2, y2), color, 2)
            
            # Draw label
            label = f"{class_name}: {confidence:.2f}"
            label_size = cv2.getTextSize(label, cv2.FONT_HERSHEY_SIMPLEX, 0.7, 2)[0]
            cv2.rectangle(result_img, (x1, y1 - label_size[1] - 10), 
                         (x1 + label_size[0], y1), color, -1)
            cv2.putText(result_img, label, (x1, y1 - 5), 
                       cv2.FONT_HERSHEY_SIMPLEX, 0.7, (255, 255, 255), 2)
        
        return result_img
    
    def detect_in_directory(self, input_dir, output_dir="output", conf_threshold=0.5):
        """
        Process all images in a directory
        
        Args:
            input_dir (str): Directory containing input images
            output_dir (str): Directory to save results
            conf_threshold (float): Confidence threshold for detections
        """
        input_dir = Path(input_dir)
        output_dir = Path(output_dir)
        output_dir.mkdir(exist_ok=True)
        
        # Supported image formats
        image_extensions = {'.jpg', '.jpeg', '.png', '.bmp', '.tiff', '.webp'}
        
        image_files = []
        for ext in image_extensions:
            image_files.extend(input_dir.glob(f"*{ext}"))
            image_files.extend(input_dir.glob(f"*{ext.upper()}"))
        
        if not image_files:
            print(f"No images found in {input_dir}")
            return
        
        print(f"Found {len(image_files)} images in {input_dir}")
        
        total_vehicles = 0
        for image_file in image_files:
            try:
                _, detections = self.process_image(image_file, output_dir, conf_threshold)
                total_vehicles += len(detections)
            except Exception as e:
                print(f"Error processing {image_file}: {e}")
        
        print(f"Directory processing complete!")
        print(f"Total vehicle detections across all images: {total_vehicles}")

def main():
    parser = argparse.ArgumentParser(description='YOLO11 Vehicle Segmentation')
    parser.add_argument('--input', '-i', required=True, help='Input image, video, or directory path')
    parser.add_argument('--output', '-o', default='output', help='Output directory')
    parser.add_argument('--model-size', '-m', default='s', choices=['n', 's', 'm', 'l', 'x'], 
                       help='YOLO11 model size (n=nano, s=small, m=medium, l=large, x=extra large)')
    parser.add_argument('--conf', '-c', type=float, default=0.5, 
                       help='Confidence threshold (0.0-1.0)')
    parser.add_argument('--type', '-t', choices=['image', 'video', 'directory'], 
                       help='Input type (auto-detected if not specified)')
    parser.add_argument('--realtime', '-r', action='store_true', 
                       help='Enable real-time video playback with live segmentation')
    parser.add_argument('--no-save', action='store_true', 
                       help='Disable saving output video (only for real-time mode)')
    
    args = parser.parse_args()
    
    # Initialize segmentation model
    vehicle_seg = VehicleSegmentation(model_size=args.model_size)
    
    # Determine input type
    input_path = Path(args.input)
    if args.type:
        input_type = args.type
    elif input_path.is_file():
        if input_path.suffix.lower() in ['.mp4', '.avi', '.mov', '.mkv', '.wmv']:
            input_type = 'video'
        else:
            input_type = 'image'
    elif input_path.is_dir():
        input_type = 'directory'
    else:
        print(f"Error: Input path {args.input} does not exist")
        return
    
    # Process based on input type
    if input_type == 'image':
        vehicle_seg.process_image(args.input, args.output, args.conf)
    elif input_type == 'video':
        save_output = not args.no_save
        vehicle_seg.process_video(args.input, args.output, args.conf, 
                                 realtime=args.realtime, save_output=save_output)
    elif input_type == 'directory':
        if args.realtime:
            print("Warning: Real-time mode is only available for video processing")
        vehicle_seg.detect_in_directory(args.input, args.output, args.conf)

if __name__ == "__main__":
    main() 