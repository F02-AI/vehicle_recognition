#!/usr/bin/env python3
"""
Model Comparison Debug Script
Compares PyTorch YOLO model vs TensorFlow Lite model on the same frame
"""

import cv2
import numpy as np
import tensorflow as tf
from ultralytics import YOLO
import time
from pathlib import Path

def test_pytorch_model(model_path, test_frame):
    """Test the original PyTorch YOLO model"""
    print("🔍 Testing PyTorch model...")
    
    try:
        # Load PyTorch model
        model = YOLO(model_path)
        
        # Run detection
        start_time = time.time()
        results = model(test_frame, verbose=False)[0]
        inference_time = time.time() - start_time
        
        # Extract detections
        detections = []
        if hasattr(results, 'boxes') and results.boxes is not None:
            for detection in results.boxes.data.tolist():
                x1, y1, x2, y2, score, class_id = detection
                detections.append({
                    'bbox': [int(x1), int(y1), int(x2), int(y2)],
                    'confidence': float(score),
                    'class_id': int(class_id)
                })
        
        print(f"✅ PyTorch model results:")
        print(f"   Inference time: {inference_time*1000:.1f}ms")
        print(f"   Detections found: {len(detections)}")
        for i, det in enumerate(detections):
            print(f"   Detection {i}: bbox={det['bbox']}, conf={det['confidence']:.3f}, class={det['class_id']}")
        
        return detections, inference_time
        
    except Exception as e:
        print(f"❌ PyTorch model error: {e}")
        return [], 0

def test_tflite_model(model_path, test_frame, confidence_threshold=0.25):
    """Test the TensorFlow Lite model with detailed debugging"""
    print("\n🔍 Testing TensorFlow Lite model...")
    
    try:
        # Load TFLite model
        interpreter = tf.lite.Interpreter(model_path=model_path)
        interpreter.allocate_tensors()
        
        # Get input and output details
        input_details = interpreter.get_input_details()
        output_details = interpreter.get_output_details()
        
        print(f"📊 TFLite model info:")
        print(f"   Input shape: {input_details[0]['shape']}")
        print(f"   Input type: {input_details[0]['dtype']}")
        print(f"   Output shape: {output_details[0]['shape']}")
        print(f"   Output type: {output_details[0]['dtype']}")
        
        # Preprocess image
        input_shape = input_details[0]['shape']
        input_height, input_width = input_shape[1], input_shape[2]
        
        # Resize image
        input_image = cv2.resize(test_frame, (input_width, input_height))
        
        # Convert BGR to RGB
        input_image = cv2.cvtColor(input_image, cv2.COLOR_BGR2RGB)
        
        # Normalize based on input type
        if input_details[0]['dtype'] == np.uint8:
            input_image = input_image.astype(np.uint8)
            print(f"   Using UINT8 input (0-255 range)")
        else:
            input_image = input_image.astype(np.float32) / 255.0
            print(f"   Using FLOAT32 input (0-1 range)")
        
        # Add batch dimension
        input_image = np.expand_dims(input_image, axis=0)
        
        print(f"   Preprocessed shape: {input_image.shape}")
        print(f"   Preprocessed dtype: {input_image.dtype}")
        print(f"   Preprocessed range: [{input_image.min():.3f}, {input_image.max():.3f}]")
        
        # Run inference
        start_time = time.time()
        interpreter.set_tensor(input_details[0]['index'], input_image)
        interpreter.invoke()
        outputs = [interpreter.get_tensor(output['index']) for output in output_details]
        inference_time = time.time() - start_time
        
        print(f"   Inference time: {inference_time*1000:.1f}ms")
        
        # Analyze raw output
        output = outputs[0]
        print(f"   Raw output shape: {output.shape}")
        print(f"   Raw output dtype: {output.dtype}")
        print(f"   Raw output range: [{output.min():.6f}, {output.max():.6f}]")
        
        # Handle different output formats
        if len(output.shape) == 3:
            output = output[0]  # Remove batch dimension
            print(f"   After removing batch dim: {output.shape}")
        
        # Analyze output structure
        if output.shape[1] >= 5:
            boxes = output[:, :4]
            confidence = output[:, 4]
            
            print(f"   Boxes shape: {boxes.shape}")
            print(f"   Confidence shape: {confidence.shape}")
            print(f"   Confidence range: [{confidence.min():.6f}, {confidence.max():.6f}]")
            print(f"   Detections above {confidence_threshold}: {np.sum(confidence > confidence_threshold)}")
            
            # Show top 10 confidence scores
            top_indices = np.argsort(confidence)[-10:][::-1]
            print(f"   Top 10 confidence scores:")
            for i, idx in enumerate(top_indices):
                print(f"     {i+1}: {confidence[idx]:.6f} at index {idx}")
            
            # If there are class scores
            if output.shape[1] > 5:
                class_scores = output[:, 5:]
                print(f"   Class scores shape: {class_scores.shape}")
                print(f"   Class scores range: [{class_scores.min():.6f}, {class_scores.max():.6f}]")
                
                # Calculate final confidence
                class_confidence = np.max(class_scores, axis=1)
                final_confidence = confidence * class_confidence
                print(f"   Final confidence range: [{final_confidence.min():.6f}, {final_confidence.max():.6f}]")
                print(f"   Final detections above {confidence_threshold}: {np.sum(final_confidence > confidence_threshold)}")
            else:
                final_confidence = confidence
        
        # Apply the same postprocessing as in the test script
        detections = postprocess_tflite_output(outputs, test_frame.shape, confidence_threshold)
        
        print(f"✅ TFLite model results:")
        print(f"   Detections found: {len(detections)}")
        for i, det in enumerate(detections):
            print(f"   Detection {i}: bbox={det['bbox']}, conf={det['confidence']:.3f}")
        
        return detections, inference_time, outputs
        
    except Exception as e:
        print(f"❌ TFLite model error: {e}")
        import traceback
        traceback.print_exc()
        return [], 0, None

def postprocess_tflite_output(outputs, original_image_shape, confidence_threshold=0.25):
    """Postprocess TFLite outputs with debugging"""
    output = outputs[0]
    
    # Handle different output formats
    if len(output.shape) == 3:
        output = output[0]
    
    print(f"   Processing output shape: {output.shape}")
    
    # CRITICAL: The TFLite model has shape (5, 8400) which is TRANSPOSED!
    # PyTorch YOLO normally outputs (num_detections, num_classes+5)
    # But TFLite outputs (num_classes+5, num_detections)
    if output.shape[0] == 5 and output.shape[1] > output.shape[0]:
        print("   Detected transposed output format - transposing back")
        output = output.T  # Transpose to get (8400, 5)
        print(f"   After transpose: {output.shape}")
    
    # Extract boxes, scores, and class predictions
    if output.shape[1] >= 5:
        boxes = output[:, :4]
        confidence = output[:, 4]
        
        if output.shape[1] > 5:
            class_scores = output[:, 5:]
            class_confidence = np.max(class_scores, axis=1)
            final_confidence = confidence * class_confidence
        else:
            final_confidence = confidence
    else:
        boxes = output[:, :4]
        final_confidence = np.ones(len(boxes)) * 0.5
    
    print(f"   Final confidence stats: min={final_confidence.min():.6f}, max={final_confidence.max():.6f}")
    
    # Filter by confidence threshold
    valid_detections = final_confidence > confidence_threshold
    boxes = boxes[valid_detections]
    final_confidence = final_confidence[valid_detections]
    
    print(f"   Valid detections after filtering: {len(boxes)}")
    
    # Convert center coordinates to corner coordinates
    detections = []
    orig_h, orig_w = original_image_shape[:2]
    
    for i, (box, conf) in enumerate(zip(boxes, final_confidence)):
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
    
    return detections

def draw_comparison(image, pytorch_detections, tflite_detections):
    """Draw detections from both models for comparison"""
    result = image.copy()
    
    # Draw PyTorch detections in green
    for det in pytorch_detections:
        x1, y1, x2, y2 = det['bbox']
        cv2.rectangle(result, (x1, y1), (x2, y2), (0, 255, 0), 2)
        cv2.putText(result, f"PyTorch: {det['confidence']:.2f}", 
                   (x1, y1-10), cv2.FONT_HERSHEY_SIMPLEX, 0.5, (0, 255, 0), 2)
    
    # Draw TFLite detections in red
    for det in tflite_detections:
        x1, y1, x2, y2 = det['bbox']
        cv2.rectangle(result, (x1, y1), (x2, y2), (0, 0, 255), 2)
        cv2.putText(result, f"TFLite: {det['confidence']:.2f}", 
                   (x1, y2+20), cv2.FONT_HERSHEY_SIMPLEX, 0.5, (0, 0, 255), 2)
    
    return result

def main():
    """Main comparison function"""
    pytorch_model_path = "license_plate_detector.pt"
    tflite_model_path = "androidApp/src/main/assets/models/license_plate_detector.tflite"
    test_video_path = "script_videos/luxury.mp4"
    
    print("🚀 Model Comparison Debug Script")
    print("=" * 50)
    
    # Check if files exist
    if not Path(pytorch_model_path).exists():
        print(f"❌ PyTorch model not found: {pytorch_model_path}")
        return
    
    if not Path(tflite_model_path).exists():
        print(f"❌ TFLite model not found: {tflite_model_path}")
        return
    
    if not Path(test_video_path).exists():
        print(f"❌ Test video not found: {test_video_path}")
        return
    
    # Load test frame
    cap = cv2.VideoCapture(test_video_path)
    if not cap.isOpened():
        print(f"❌ Could not open video: {test_video_path}")
        return
    
    # Read multiple frames and test on different ones
    frame_indices = [100, 500, 1000, 1500, 2000]  # Test on different frames
    
    for frame_idx in frame_indices:
        print(f"\n{'='*50}")
        print(f"🎬 Testing frame {frame_idx}")
        print(f"{'='*50}")
        
        # Seek to specific frame
        cap.set(cv2.CAP_PROP_POS_FRAMES, frame_idx)
        ret, frame = cap.read()
        
        if not ret:
            print(f"❌ Could not read frame {frame_idx}")
            continue
        
        print(f"📸 Frame info: {frame.shape}")
        
        # Test PyTorch model
        pytorch_dets, pytorch_time = test_pytorch_model(pytorch_model_path, frame)
        
        # Test TFLite model
        tflite_dets, tflite_time, raw_output = test_tflite_model(tflite_model_path, frame)
        
        # Compare results
        print(f"\n📊 Comparison for frame {frame_idx}:")
        print(f"   PyTorch: {len(pytorch_dets)} detections in {pytorch_time*1000:.1f}ms")
        print(f"   TFLite:  {len(tflite_dets)} detections in {tflite_time*1000:.1f}ms")
        
        if len(pytorch_dets) > 0 and len(tflite_dets) == 0:
            print("⚠️  PyTorch found detections but TFLite found none!")
            
            # Save debug images
            debug_dir = Path("script_output/debug_comparison")
            debug_dir.mkdir(parents=True, exist_ok=True)
            
            # Save original frame
            cv2.imwrite(str(debug_dir / f"frame_{frame_idx}_original.jpg"), frame)
            
            # Save comparison
            comparison = draw_comparison(frame, pytorch_dets, tflite_dets)
            cv2.imwrite(str(debug_dir / f"frame_{frame_idx}_comparison.jpg"), comparison)
            
            print(f"💾 Debug images saved to {debug_dir}")
            
            # Try different confidence thresholds for TFLite
            print("\n🔧 Testing different confidence thresholds for TFLite:")
            for threshold in [0.01, 0.05, 0.1, 0.15, 0.2, 0.25]:
                test_dets = postprocess_tflite_output(raw_output, frame.shape, threshold)
                print(f"   Threshold {threshold:.2f}: {len(test_dets)} detections")
        
        elif len(pytorch_dets) > 0 and len(tflite_dets) > 0:
            print("✅ Both models found detections!")
        
        # Only test first few frames to avoid spam
        if frame_idx >= 1000:
            break
    
    cap.release()
    
    print("\n" + "="*50)
    print("🏁 Comparison complete!")
    print("If TFLite consistently shows 0 detections while PyTorch works,")
    print("the issue is likely in the model conversion or preprocessing.")

if __name__ == "__main__":
    main() 