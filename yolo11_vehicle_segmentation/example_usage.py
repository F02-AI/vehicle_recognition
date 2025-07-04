#!/usr/bin/env python3
"""
Example usage of the VehicleSegmentation class
This script demonstrates how to use the vehicle segmentation functionality programmatically
"""

from vehicle_segmentation import VehicleSegmentation
import cv2
import numpy as np

def example_image_processing():
    """Example of processing a single image"""
    print("=== Image Processing Example ===")
    
    # Initialize the vehicle segmentation model
    vehicle_seg = VehicleSegmentation(model_size='s')  # Use small model for faster processing
    
    # Process an image (replace with your image path)
    image_path = "path/to/your/image.jpg"
    
    try:
        result_img, detections = vehicle_seg.process_image(
            image_path, 
            output_dir="example_output", 
            conf_threshold=0.5
        )
        
        print(f"Processing complete! Found {len(detections)} vehicles")
        
        # Print details about each detection
        for i, detection in enumerate(detections):
            class_name = vehicle_seg.CLASS_NAMES[detection['class_id']]
            confidence = detection['confidence']
            print(f"  Vehicle {i+1}: {class_name} (confidence: {confidence:.2f})")
            
    except Exception as e:
        print(f"Error processing image: {e}")
        print("Please make sure the image path is correct and the file exists")

def example_video_processing():
    """Example of processing a video"""
    print("\n=== Video Processing Example ===")
    
    # Initialize the vehicle segmentation model
    vehicle_seg = VehicleSegmentation(model_size='n')  # Use nano model for faster video processing
    
    # Process a video (replace with your video path)
    video_path = "path/to/your/video.mp4"
    
    try:
        output_path = vehicle_seg.process_video(
            video_path,
            output_dir="example_output",
            conf_threshold=0.6
        )
        
        print(f"Video processing complete! Output saved to: {output_path}")
        
    except Exception as e:
        print(f"Error processing video: {e}")
        print("Please make sure the video path is correct and the file exists")

def example_realtime_video_processing():
    """Example of real-time video processing with live display"""
    print("\n=== Real-time Video Processing Example ===")
    
    # Initialize the vehicle segmentation model
    vehicle_seg = VehicleSegmentation(model_size='n')  # Use nano model for best real-time performance
    
    # Process a video in real-time (replace with your video path)
    video_path = "path/to/your/video.mp4"
    
    try:
        print("Starting real-time video processing...")
        print("Controls: 'q' to quit, 'p' to pause/resume")
        
        output_path = vehicle_seg.process_video(
            video_path,
            output_dir="example_output",
            conf_threshold=0.6,
            realtime=True,           # Enable real-time display
            save_output=True         # Still save output video
        )
        
        print(f"Real-time processing complete! Output saved to: {output_path}")
        
    except Exception as e:
        print(f"Error processing video: {e}")
        print("Please make sure the video path is correct and the file exists")

def example_directory_processing():
    """Example of processing all images in a directory"""
    print("\n=== Directory Processing Example ===")
    
    # Initialize the vehicle segmentation model
    vehicle_seg = VehicleSegmentation(model_size='m')  # Use medium model for better accuracy
    
    # Process all images in a directory (replace with your directory path)
    directory_path = "path/to/your/images/"
    
    try:
        vehicle_seg.detect_in_directory(
            directory_path,
            output_dir="example_output",
            conf_threshold=0.4
        )
        
        print("Directory processing complete!")
        
    except Exception as e:
        print(f"Error processing directory: {e}")
        print("Please make sure the directory path is correct and contains images")

def example_custom_processing():
    """Example of custom processing with manual result handling"""
    print("\n=== Custom Processing Example ===")
    
    # Initialize the vehicle segmentation model
    vehicle_seg = VehicleSegmentation(model_size='s')
    
    # Create a sample image for demonstration (blue rectangle)
    sample_img = np.zeros((480, 640, 3), dtype=np.uint8)
    sample_img[:, :, 0] = 100  # Add some blue color
    
    # Save the sample image
    sample_path = "example_output/sample_image.jpg"
    cv2.imwrite(sample_path, sample_img)
    
    print(f"Created sample image: {sample_path}")
    print("Note: This sample image likely won't contain any vehicles,")
    print("but demonstrates the processing pipeline")

def main():
    """Main function to run all examples"""
    print("YOLO11 Vehicle Segmentation - Example Usage")
    print("=" * 50)
    
    # Create output directory
    import os
    os.makedirs("example_output", exist_ok=True)
    
    # Run examples
    example_image_processing()
    example_video_processing()
    example_realtime_video_processing()
    example_directory_processing()
    example_custom_processing()
    
    print("\n" + "=" * 50)
    print("Examples complete!")
    print("To run with your own files, update the file paths in this script.")
    print("You can also use the command line interface:")
    print("  python vehicle_segmentation.py -i your_image.jpg")
    print("  python vehicle_segmentation.py -i your_video.mp4 -r  # Real-time mode")

if __name__ == "__main__":
    main() 