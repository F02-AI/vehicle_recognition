import argparse
import os
import cv2
import re
import threading
import time
import queue
import subprocess
import json
import numpy as np
from ultralytics import YOLO
from fast_plate_ocr import ONNXPlateRecognizer
from PIL import Image


class NumericOnlyONNXPlateRecognizer(ONNXPlateRecognizer):
    """
    Custom FastPlateOCR that forces numeric-only output by constraining the model's character decoding
    """
    
    def _postprocess_output_numeric_only(self, model_output, max_plate_slots, model_alphabet):
        """
        Custom post-processing that only considers numeric characters (0-9) from the model alphabet
        """
        predictions = model_output.reshape((-1, max_plate_slots, len(model_alphabet)))
        
        # Create a mask for numeric characters only (0-9)
        numeric_mask = np.array([char.isdigit() for char in model_alphabet])
        numeric_indices = np.where(numeric_mask)[0]
        
        # Only consider predictions for numeric characters
        # Set all non-numeric character probabilities to very low values
        masked_predictions = predictions.copy()
        for i, char in enumerate(model_alphabet):
            if not char.isdigit():
                masked_predictions[:, :, i] = -np.inf
        
        # Get the indices of the highest probability characters (now only numeric)
        prediction_indices = np.argmax(masked_predictions, axis=-1)
        
        # Convert indices to characters using the original alphabet
        alphabet_array = np.array(list(model_alphabet))
        plate_chars = alphabet_array[prediction_indices]
        
        # Join characters and return as list
        plates = np.apply_along_axis("".join, 1, plate_chars).tolist()
        return plates
    
    def run(self, source, return_confidence=False):
        """
        Override the run method to use our custom numeric-only post-processing
        """
        # Use parent's preprocessing and model inference
        from fast_plate_ocr.inference.onnx_inference import _load_image_from_source
        from fast_plate_ocr.inference.process import preprocess_image
        
        x = _load_image_from_source(source)
        # Preprocess
        x = preprocess_image(x, self.config["img_height"], self.config["img_width"])
        # Run model
        y = self.model.run(None, {"input": x})
        
        # Use our custom numeric-only post-processing
        plates = self._postprocess_output_numeric_only(
            y[0],
            self.config["max_plate_slots"],
            self.config["alphabet"]
        )
        
        if return_confidence:
            # For confidence, we'd need to modify this too, but for simplicity, return empty array
            confidence = np.array([])
            return plates, confidence
        
        return plates


class FastPlateOCRWorker:
    """Fast Plate OCR worker for latest license plate"""
    def __init__(self, model_name='global-plates-mobile-vit-v2-model'):
        self.model_name = model_name
        self.ocr_recognizer = None
        self.current_task = None
        self.latest_result = "No plate detected"
        self.processing = False
        self.stop_event = threading.Event()
        self.debug_counter = 0  # Add debug counter
        self._initialize_model()
        
    def _initialize_model(self):
        """Initialize the fast_plate_ocr model"""
        try:
            print(f"Initializing Numeric-Only FastPlateOCR with model: {self.model_name}")
            self.ocr_recognizer = NumericOnlyONNXPlateRecognizer(self.model_name)
            print("Numeric-Only FastPlateOCR initialized successfully!")
        except Exception as e:
            print(f"Error initializing FastPlateOCR: {e}")
            self.ocr_recognizer = None
        
    def process_latest(self, license_plate_crop):
        """Process the latest license plate (non-blocking)"""
        if not self.processing and not self.stop_event.is_set() and self.ocr_recognizer is not None:
            self.current_task = license_plate_crop.copy()
            thread = threading.Thread(target=self._process_ocr, daemon=True)
            thread.start()
    
    def get_latest_result(self):
        """Get the latest OCR result"""
        return self.latest_result
        
    def _process_ocr(self):
        """Process OCR in background"""
        if self.current_task is None or self.stop_event.is_set() or self.ocr_recognizer is None:
            self.processing = False
            return
            
        self.processing = True
        self.debug_counter += 1
        debug_mode = self.debug_counter <= 3  # Only debug first 3 attempts
        
        try:
            if debug_mode:
                print(f"DEBUG: Processing image shape: {self.current_task.shape}")
            
            # fast_plate_ocr expects grayscale images only
            plate_gray = cv2.cvtColor(self.current_task, cv2.COLOR_BGR2GRAY)
            result = self.ocr_recognizer.run(plate_gray)
            
            if debug_mode:
                print(f"DEBUG: FastPlateOCR result: {result}, type: {type(result)}")
            
            if self.stop_event.is_set():
                return

            # fast_plate_ocr returns a list of strings, not an object with .text
            if result and isinstance(result, list) and len(result) > 0:
                # Take the first result (most confident)
                raw_text = result[0] if result[0] else ""
                if raw_text:
                    # Apply validation and formatting (no character mapping needed)
                    formatted_text = self._validate_and_format_plate(raw_text)
                    if formatted_text:
                        self.latest_result = formatted_text
                        print(f"FastPlateOCR Success (formatted): {self.latest_result}")
                    else:
                        # If formatting fails, show the raw numeric text for debugging
                        numeric_only = re.sub(r'[^0-9]', '', raw_text.replace('_', '').strip())
                        self.latest_result = f"Raw: {numeric_only} (len:{len(numeric_only)})"
                        print(f"FastPlateOCR (validation failed): {self.latest_result}")
                else:
                    self.latest_result = "No plate detected"
                    if debug_mode:
                        print(f"DEBUG: Empty result string")
            else:
                self.latest_result = "No plate detected"
                if debug_mode:
                    print(f"DEBUG: No valid result from OCR")
                
        except Exception as e:
            if not self.stop_event.is_set():
                print(f"FastPlateOCR error: {e}")
        finally:
            self.processing = False
            
    def _validate_and_format_plate(self, raw_text: str) -> str:
        """
        Validation and formatting for NUMERIC-ONLY license plates
        Uses the same logic as python_yolo.py for consistent formatting
        """
        if not raw_text:
            return None
        
        # Remove any padding characters (underscores) that might come from the model
        clean_text = raw_text.replace('_', '').strip()
        
        # Extract just numbers (should already be numeric, but just in case)
        numeric_only = re.sub(r'[^0-9]', '', clean_text)
        
        # CONSTRAINT: Must have exactly 7, 8, or 9 digits (discard less than 7, max 9)
        if len(numeric_only) < 7 or len(numeric_only) > 9:
            return None
             
        # Apply numeric format rules based on length - same logic as python_yolo.py
        return self._validate_and_format_plate_by_rules(clean_text, numeric_only)
    
    def _validate_and_format_plate_by_rules(self, raw_ocr_text: str, numeric_plate_text: str) -> str:
        """
        Validates the numeric plate text based on allowed lengths and formats it
        according to predefined rules. Since FastPlateOCR only outputs numbers,
        we format based on length without checking for format hints in raw text.
        """
        len_numeric = len(numeric_plate_text)

        if len_numeric == 7:
            # For 7 digits, default to NN-NNN-NN format (most common)
            # Since we only have pure numbers, we can't detect the intended format
            return f"{numeric_plate_text[:2]}-{numeric_plate_text[2:5]}-{numeric_plate_text[5:]}"  # NN-NNN-NN
        elif len_numeric == 8:
            return f"{numeric_plate_text[:3]}-{numeric_plate_text[3:5]}-{numeric_plate_text[5:]}"  # NNN-NN-NNN
        elif len_numeric == 9:
            # For 9 digits, show both 8-digit options (without first and without last digit)
            option1 = numeric_plate_text[1:]  # Remove first digit
            option2 = numeric_plate_text[:-1]  # Remove last digit
            formatted_option1 = f"{option1[:3]}-{option1[3:5]}-{option1[5:]}"  # NNN-NN-NNN
            formatted_option2 = f"{option2[:3]}-{option2[3:5]}-{option2[5:]}"  # NNN-NN-NNN
            return f"{formatted_option1} or {formatted_option2}"
        else:
            return None

    def _apply_numeric_format_rules(self, numeric_text: str) -> str:
        """Apply format rules for numeric-only license plates (7-8 digits only)"""
        len_numeric = len(numeric_text)
        
        if len_numeric == 7:
            # NN-NNN-NN format (most common 7-digit format)
            return f"{numeric_text[:2]}-{numeric_text[2:5]}-{numeric_text[5:]}"
        elif len_numeric == 8:
            # NNN-NN-NNN format
            return f"{numeric_text[:3]}-{numeric_text[3:5]}-{numeric_text[5:]}"
        
        # Fallback (shouldn't reach here due to validation)
        return numeric_text

    def process_synchronous_ocr(self, license_plate_crop_bgr):
        """Processes OCR synchronously for a given crop and returns the text."""
        if license_plate_crop_bgr is None or license_plate_crop_bgr.size == 0 or self.ocr_recognizer is None:
            return None
        try:
            # fast_plate_ocr expects grayscale images only
            plate_gray = cv2.cvtColor(license_plate_crop_bgr, cv2.COLOR_BGR2GRAY)
            result = self.ocr_recognizer.run(plate_gray)
            
            # fast_plate_ocr returns a list of strings, not an object with .text
            if result and isinstance(result, list) and len(result) > 0:
                # Take the first result (most confident)
                raw_text = result[0] if result[0] else ""
                if raw_text:
                    # Apply validation and formatting (no character mapping needed)
                    formatted_text = self._validate_and_format_plate(raw_text)
                    if formatted_text:
                        return formatted_text
                    else:
                        # For sync OCR, return None if validation fails (so it doesn't get annotated)
                        print(f"Sync OCR validation failed for: {raw_text}")
                        return None
            
            return None
            
        except Exception as e:
            print(f"Synchronous FastPlateOCR error: {e}")
            return None

    def _auto_correct_plate_perspective_and_enhance(self, plate_crop_bgr, correct_perspective=False, enhance=False, debug_save_path=None):
        """
        Improved: Optionally corrects the perspective and/or enhances the license plate crop for OCR.
        If debug_save_path is provided and corners are found, saves a debug image with red dots on corners.
        Returns the processed plate image.
        """
        if plate_crop_bgr is None or plate_crop_bgr.size == 0:
            return plate_crop_bgr
        import cv2
        import numpy as np
        import os
        import time

        result = plate_crop_bgr.copy()
        debug_corners = None

        if correct_perspective:
            orig = result
            gray = cv2.cvtColor(orig, cv2.COLOR_BGR2GRAY)
            h_img, w_img = gray.shape
            
            # Strategy 1: Try to find rectangular plate border
            thresh = cv2.adaptiveThreshold(gray, 255, cv2.ADAPTIVE_THRESH_GAUSSIAN_C,
                                           cv2.THRESH_BINARY_INV, 19, 9)
            kernel = cv2.getStructuringElement(cv2.MORPH_RECT, (5, 5))
            closed = cv2.morphologyEx(thresh, cv2.MORPH_CLOSE, kernel)
            contours, _ = cv2.findContours(closed, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
            
            best_rect = None
            best_score = 0
            
            # Look for rectangular border first
            for c in contours:
                peri = cv2.arcLength(c, True)
                approx = cv2.approxPolyDP(c, 0.02 * peri, True)
                if len(approx) == 4:
                    x, y, w, h = cv2.boundingRect(approx)
                    aspect = w / float(h)
                    area = cv2.contourArea(approx)
                    rect_area = w * h
                    fill_ratio = area / float(rect_area + 1e-5)
                    if 2.0 < aspect < 6.0 and fill_ratio > 0.6 and w > 0.5 * w_img and h > 0.2 * h_img:
                        score = fill_ratio * area
                        if score > best_score:
                            best_score = score
                            best_rect = approx
            
            # Strategy 2: If no border found, detect text region and estimate corners
            if best_rect is None:
                # Use MSER (Maximally Stable Extremal Regions) for robust text detection
                mser = cv2.MSER_create()
                regions, _ = mser.detectRegions(gray)
                
                # Collect all text-like regions
                text_boxes = []
                for region in regions:
                    x, y, w, h = cv2.boundingRect(region.reshape(-1, 1, 2))
                    aspect = w / float(h) if h > 0 else 0
                    area = w * h
                    
                    # Filter for character-like regions (typical license plate characters)
                    if (0.3 < aspect < 3.0 and 100 < area < 5000 and 
                        10 < w < w_img * 0.3 and 15 < h < h_img * 0.8):
                        text_boxes.append((x, y, x+w, y+h))
                
                # If MSER doesn't find enough, try morphological approach
                if len(text_boxes) < 3:
                    # Use different thresholding for text detection
                    blur = cv2.GaussianBlur(gray, (3, 3), 0)
                    _, thresh1 = cv2.threshold(blur, 0, 255, cv2.THRESH_BINARY + cv2.THRESH_OTSU)
                    thresh2 = cv2.adaptiveThreshold(blur, 255, cv2.ADAPTIVE_THRESH_MEAN_C, cv2.THRESH_BINARY, 15, 8)
                    
                    # Combine thresholds
                    combined = cv2.bitwise_or(thresh1, thresh2)
                    
                    # Find text-like contours with horizontal dilation to connect characters
                    kernel_text = cv2.getStructuringElement(cv2.MORPH_RECT, (5, 1))
                    dilated = cv2.dilate(combined, kernel_text, iterations=1)
                    
                    text_contours, _ = cv2.findContours(dilated, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
                    
                    # Find text-like regions
                    for c in text_contours:
                        x, y, w, h = cv2.boundingRect(c)
                        area = cv2.contourArea(c)
                        aspect = w / float(h) if h > 0 else 0
                        # Look for word-like or line-like regions (license plate text)
                        if (1.5 < aspect < 8.0 and area > 200 and 
                            w > w_img * 0.3 and h > h_img * 0.15 and h < h_img * 0.7):
                            text_boxes.append((x, y, x+w, y+h))
                
                # Find the license plate region from text boxes
                if text_boxes:
                    # Find overall bounding box of text regions
                    min_x = min(box[0] for box in text_boxes)
                    min_y = min(box[1] for box in text_boxes)
                    max_x = max(box[2] for box in text_boxes)
                    max_y = max(box[3] for box in text_boxes)
                    
                    # Calculate text region dimensions
                    text_width = max_x - min_x
                    text_height = max_y - min_y
                    
                    # Add padding appropriate for license plate (more horizontal, less vertical)
                    padding_x = max(int(text_width * 0.2), 10)  # 20% horizontal padding
                    padding_y = max(int(text_height * 0.4), 8)   # 40% vertical padding
                    
                    # Ensure we stay within image bounds
                    min_x = max(0, min_x - padding_x)
                    min_y = max(0, min_y - padding_y)
                    max_x = min(w_img, max_x + padding_x)
                    max_y = min(h_img, max_y + padding_y)
                    
                    # Validate the detected region makes sense for a license plate
                    detected_width = max_x - min_x
                    detected_height = max_y - min_y
                    detected_aspect = detected_width / float(detected_height) if detected_height > 0 else 0
                    
                    # Only use detected region if it looks like a license plate
                    if (1.8 < detected_aspect < 6.0 and 
                        detected_width > w_img * 0.4 and 
                        detected_height > h_img * 0.2):
                        
                        # Create estimated corners for the license plate region
                        estimated_corners = np.array([
                            [min_x, min_y],      # top-left
                            [max_x, min_y],      # top-right
                            [max_x, max_y],      # bottom-right
                            [min_x, max_y]       # bottom-left
                        ], dtype=np.float32)
                        
                        # Convert to the same format as contour approximation
                        best_rect = estimated_corners.reshape(-1, 1, 2).astype(np.int32)
            
            # Strategy 3: Final fallback - use central portion of image (likely license plate area)
            if best_rect is None:
                # Instead of using entire image, use central region where license plate likely is
                margin_x = int(w_img * 0.1)  # 10% margin from sides
                margin_y = int(h_img * 0.2)  # 20% margin from top/bottom
                
                estimated_corners = np.array([
                    [margin_x, margin_y],
                    [w_img - margin_x, margin_y],
                    [w_img - margin_x, h_img - margin_y],
                    [margin_x, h_img - margin_y]
                ], dtype=np.float32)
                best_rect = estimated_corners.reshape(-1, 1, 2).astype(np.int32)
            
            # Save debug image if requested
            if debug_save_path is not None:
                debug_corners = orig.copy()
                if best_rect is not None:
                    pts = best_rect.reshape(4, 2)
                    for i, (x, y) in enumerate(pts):
                        cv2.circle(debug_corners, (int(x), int(y)), 8, (0, 0, 255), -1)
                        # Label corners for debugging
                        cv2.putText(debug_corners, str(i), (int(x)+10, int(y)), cv2.FONT_HERSHEY_SIMPLEX, 0.5, (255, 255, 0), 1)
                    cv2.putText(debug_corners, "CORNERS FOUND", (10, 30), cv2.FONT_HERSHEY_SIMPLEX, 0.7, (0, 255, 0), 2)
                else:
                    cv2.putText(debug_corners, "NO CORNERS FOUND", (10, 30), cv2.FONT_HERSHEY_SIMPLEX, 0.7, (0, 0, 255), 2)
                os.makedirs(os.path.dirname(debug_save_path), exist_ok=True)
                cv2.imwrite(debug_save_path, debug_corners)
                print(f"DEBUG: Saved debug image to {debug_save_path}")
            
            if best_rect is not None:
                pts = best_rect.reshape(4, 2)
                # Order points: top-left, top-right, bottom-right, bottom-left
                rect = np.zeros((4, 2), dtype="float32")
                s = pts.sum(axis=1)
                rect[0] = pts[np.argmin(s)]
                rect[2] = pts[np.argmax(s)]
                diff = np.diff(pts, axis=1)
                rect[1] = pts[np.argmin(diff)]
                rect[3] = pts[np.argmax(diff)]
                (tl, tr, br, bl) = rect
                widthA = np.linalg.norm(br - bl)
                widthB = np.linalg.norm(tr - tl)
                maxWidth = max(int(widthA), int(widthB))
                heightA = np.linalg.norm(tr - br)
                heightB = np.linalg.norm(tl - bl)
                maxHeight = max(int(heightA), int(heightB))
                if maxWidth >= 30 and maxHeight >= 10:
                    dst = np.array([
                        [0, 0],
                        [maxWidth - 1, 0],
                        [maxWidth - 1, maxHeight - 1],
                        [0, maxHeight - 1]
                    ], dtype="float32")
                    M = cv2.getPerspectiveTransform(rect, dst)
                    result = cv2.warpPerspective(orig, M, (maxWidth, maxHeight))

        if enhance:
            # Enhancement: grayscale, equalize, denoise, sharpen
            gray = cv2.cvtColor(result, cv2.COLOR_BGR2GRAY)
            eq = cv2.equalizeHist(gray)
            denoised = cv2.fastNlMeansDenoising(eq, h=10)
            kernel = np.array([[0, -1, 0], [-1, 5, -1], [0, -1, 0]])
            sharpened = cv2.filter2D(denoised, -1, kernel)
            result = cv2.cvtColor(sharpened, cv2.COLOR_GRAY2BGR)
        return result


class DetectionWorker(threading.Thread):
    def __init__(self, vehicle_detector, license_plate_detector, ocr_worker, input_queue, results_dict, stop_event, lock, show_vehicles, show_plates, output_lp_dir, ocr_interval):
        super().__init__(daemon=True)
        self.vehicle_detector = vehicle_detector
        self.license_plate_detector = license_plate_detector
        self.ocr_worker = ocr_worker
        self.input_queue = input_queue
        self.results_dict = results_dict
        self.stop_event = stop_event
        self.lock = lock
        self.frame_nmr_processed = 0
        self.vehicle_classes = [2, 3, 5, 7]
        self.show_vehicles = show_vehicles
        self.show_plates = show_plates
        self.output_lp_dir = output_lp_dir
        self.saved_lp_count = 0
        self.ocr_processing_interval = ocr_interval
        if self.ocr_processing_interval <= 0:
            print("Warning: OCR interval must be > 0. Defaulting to 1.")
            self.ocr_processing_interval = 1

    def run(self):
        print("DetectionWorker started.")
        while not self.stop_event.is_set():
            try:
                frame_data = self.input_queue.get(timeout=0.1)
                if frame_data is None:
                    break
                
                original_frame_for_ocr, _ = frame_data
                self.frame_nmr_processed += 1

                # Detect vehicles
                detected_vehicles = []
                if self.show_vehicles or self.show_plates:
                    vehicle_detections_raw = self.vehicle_detector(original_frame_for_ocr, verbose=False)[0]
                    for detection in vehicle_detections_raw.boxes.data.tolist():
                        x1, y1, x2, y2, score, class_id = detection
                        if int(class_id) in self.vehicle_classes and score > 0.5:
                            detected_vehicles.append([x1, y1, x2, y2, score, class_id])
                
                # Detect license plates
                detected_license_plates = []
                best_license_plate_coords = None
                best_score = 0
                
                if self.show_plates:
                    license_plates_raw = self.license_plate_detector(original_frame_for_ocr, verbose=False)[0]
                    for lp_detection in license_plates_raw.boxes.data.tolist():
                        x1_lp, y1_lp, x2_lp, y2_lp, score_lp, class_id_lp = lp_detection
                        associated_vehicle = None
                        for vehicle in detected_vehicles:
                            vx1, vy1, vx2, vy2, _, _ = vehicle
                            if (x1_lp >= vx1 - 20 and y1_lp >= vy1 - 20 and 
                                x2_lp <= vx2 + 20 and y2_lp <= vy2 + 20):
                                associated_vehicle = vehicle
                                break
                        if associated_vehicle is not None:
                            detected_license_plates.append([x1_lp, y1_lp, x2_lp, y2_lp, score_lp])
                            if score_lp > best_score:
                                best_score = score_lp
                                best_license_plate_coords = (x1_lp, y1_lp, x2_lp, y2_lp)
                
                # Process and save LP if detected
                if best_license_plate_coords is not None:
                    x1_orig, y1_orig, x2_orig, y2_orig = best_license_plate_coords
                    # Expand the crop by 30% in both width and height around the center
                    cx = (x1_orig + x2_orig) / 2.0
                    cy = (y1_orig + y2_orig) / 2.0
                    w = (x2_orig - x1_orig)
                    h = (y2_orig - y1_orig)
                    new_w = w * 1.1
                    new_h = h * 1.1
                    new_x1 = int(max(cx - new_w / 2, 0))
                    new_x2 = int(min(cx + new_w / 2, original_frame_for_ocr.shape[1]))
                    new_y1 = int(max(cy - new_h / 2, 0))
                    new_y2 = int(min(cy + new_h / 2, original_frame_for_ocr.shape[0]))
                    license_plate_crop_orig = original_frame_for_ocr[new_y1:new_y2, new_x1:new_x2, :]
                    if license_plate_crop_orig.size > 0:
                        # Auto correct and save debug corners image
                        debug_corners_dir = os.path.join('script_output', 'debug_corners')
                        os.makedirs(debug_corners_dir, exist_ok=True)  # Ensure directory exists
                        debug_corners_path = os.path.join(debug_corners_dir, f'lp_debug_{self.frame_nmr_processed}.png')
                        print(f"DEBUG: Saving debug corners to {debug_corners_path}")
                        
                        # Apply perspective correction and enhancement
                        license_plate_corrected = self.ocr_worker._auto_correct_plate_perspective_and_enhance(
                            license_plate_crop_orig, correct_perspective=True, enhance=False, debug_save_path=debug_corners_path)
                        
                        # Perform synchronous OCR on the corrected image
                        annot_text = self.ocr_worker.process_synchronous_ocr(license_plate_corrected)
                        print(f"DEBUG (DetectionWorker): FastPlateOCR for saving frame {self.frame_nmr_processed}. Text: '{annot_text}' (perspective-corrected: {license_plate_corrected.shape != license_plate_crop_orig.shape})")
                        
                        # Save the perspective-corrected image with OCR annotation
                        annotated_lp_to_save = license_plate_corrected.copy()
                        if annot_text:
                            text_to_draw = annot_text
                            font_scale = 0.6; font_thickness = 1; font = cv2.FONT_HERSHEY_SIMPLEX
                            text_color = (0,0,255); bg_color = (255,255,255)
                            (text_w, text_h), baseline = cv2.getTextSize(text_to_draw, font, font_scale, font_thickness)
                            margin = 3
                            text_x_lp = annotated_lp_to_save.shape[1] - text_w - margin
                            text_y_lp = text_h + margin
                            if text_x_lp < 0: text_x_lp = margin
                            if text_y_lp > annotated_lp_to_save.shape[0] - margin : text_y_lp = annotated_lp_to_save.shape[0] - margin
                            cv2.rectangle(annotated_lp_to_save, (text_x_lp - margin, text_y_lp - text_h - margin + baseline),
                                          (text_x_lp + text_w + margin, text_y_lp + margin + baseline), bg_color, -1)
                            cv2.putText(annotated_lp_to_save, text_to_draw, (text_x_lp, text_y_lp + baseline // 2),
                                        font, font_scale, text_color, font_thickness, cv2.LINE_AA)
                        try:
                            lp_filename = os.path.join(self.output_lp_dir, f"lp_{self.saved_lp_count:04d}_frame{self.frame_nmr_processed}.png")
                            cv2.imwrite(lp_filename, annotated_lp_to_save)
                            print(f"DEBUG: Saved perspective-corrected LP to {lp_filename}")
                            self.saved_lp_count += 1
                        except Exception as e:
                            print(f"Error saving annotated license plate image: {e}")
                            
                        # Use the corrected image for OCR processing as well
                        if self.frame_nmr_processed % self.ocr_processing_interval == 0:
                            self.ocr_worker.process_latest(license_plate_corrected.copy())

                with self.lock:
                    self.results_dict['vehicles'] = detected_vehicles if self.show_vehicles else []
                    self.results_dict['plates'] = detected_license_plates if self.show_plates else []

                self.input_queue.task_done()

            except queue.Empty:
                continue
            except Exception as e:
                if not self.stop_event.is_set():
                    print(f"DetectionWorker error: {e}")
                self.input_queue.task_done()

        print("DetectionWorker finished.")


def get_video_rotation(video_path):
    """ 
    Uses ffprobe to get the rotation angle of the video.
    Returns the rotation angle (90, 180, 270) or 0 if no rotation or error.
    """
    try:
        cmd = [
            'ffprobe', '-loglevel', 'error', '-select_streams', 'v:0',
            '-show_entries', 'stream_tags=rotate', '-of', 'json', video_path
        ]
        result = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE, check=True, text=True)
        data = json.loads(result.stdout)
        
        if 'streams' in data and data['streams']:
            stream_info = data['streams'][0]
            if 'tags' in stream_info and 'rotate' in stream_info['tags']:
                rotation = int(stream_info['tags']['rotate'])
                if rotation < 0:
                    rotation += 360 
                if rotation in [90, 180, 270]:
                    print(f"Video rotation detected: {rotation} degrees")
                    return rotation
        return 0
    except FileNotFoundError:
        print("ffprobe not found. Please ensure FFmpeg is installed and in PATH for rotation detection.")
        return 0
    except subprocess.CalledProcessError as e:
        print(f"ffprobe error: {e.stderr}")
        return 0
    except Exception as e:
        print(f"Error getting video rotation: {e}")
        return 0


def main(video_path, model_path, show_vehicles=True, show_plates=True, fast_plate_model='global-plates-mobile-vit-v2-model', manual_rotation=0, ocr_interval=5):
    # Load models
    if not os.path.isfile(model_path):
        print(f"Error: License plate detector model not found at {model_path}")
        return
    
    # Load vehicle detection model (COCO)
    if not os.path.isfile('yolov8n.pt'):
        print("Downloading yolov8n.pt...")
        os.system('curl -L https://github.com/ultralytics/assets/releases/download/v0.0.0/yolov8n.pt -o yolov8n.pt')
    
    vehicle_detector = YOLO('yolov8n.pt')
    license_plate_detector = YOLO(model_path)
    
    # Initialize FastPlateOCR worker
    ocr_worker = FastPlateOCRWorker(model_name=fast_plate_model)

    # Get video rotation
    detected_rotation_angle = get_video_rotation(video_path)
    
    # Determine final rotation angle (manual override takes precedence)
    final_rotation_angle = manual_rotation if manual_rotation != 0 else detected_rotation_angle
    if manual_rotation != 0 and detected_rotation_angle != 0 and manual_rotation != detected_rotation_angle:
        print(f"Manual rotation --rotate {manual_rotation} overrides detected rotation {detected_rotation_angle}.")
    elif final_rotation_angle != 0:
        print(f"Applying rotation: {final_rotation_angle} degrees.")

    # Create output directory for LPs
    output_dir_lps = "script_output/LPs_fastplate"
    os.makedirs(output_dir_lps, exist_ok=True)
    print(f"Saving detected license plates to: {os.path.abspath(output_dir_lps)}")
    
    # Load video
    cap = cv2.VideoCapture(video_path)
    if not cap.isOpened():
        print(f"Error: Could not open video {video_path}")
        return
    
    # Get video properties for proper playback speed
    video_fps = cap.get(cv2.CAP_PROP_FPS)
    if video_fps <= 0:
        video_fps = 30  # Default fallback FPS
    frame_delay = int(1000 / video_fps)  # Delay in milliseconds
    print(f"Video FPS: {video_fps:.2f}, Frame delay: {frame_delay}ms")
        
    # Threading and Queues
    detection_input_queue = queue.Queue(maxsize=5)
    detection_results = {'vehicles': [], 'plates': []}
    stop_event = threading.Event()
    results_lock = threading.Lock()

    # Start Detection Worker
    detection_worker = DetectionWorker(
        vehicle_detector, 
        license_plate_detector, 
        ocr_worker,
        detection_input_queue, 
        detection_results, 
        stop_event, 
        results_lock,
        show_vehicles,
        show_plates,
        output_dir_lps,
        ocr_interval
    )
    detection_worker.start()

    frame_nmr_display = -1
    is_paused = False
    
    try:
        while not stop_event.is_set():
            key = cv2.waitKey(frame_delay)  # Use proper frame delay for real-time playback
            if key & 0xFF == ord('q'):
                print("User requested quit.")
                stop_event.set()
                break
            if key & 0xFF == ord(' '):
                is_paused = not is_paused
                if is_paused:
                    print("Paused")
                else:
                    print("Resumed")
            
            if is_paused:
                if 'display_frame' in locals() and display_frame is not None:
                    current_frame_height, current_frame_width = display_frame.shape[:2]
                    (text_w, text_h), _ = cv2.getTextSize("Paused", cv2.FONT_HERSHEY_SIMPLEX, 1, 2)
                    cv2.putText(display_frame, "Paused", 
                                (int(current_frame_width / 2 - text_w / 2), int(current_frame_height / 2 + text_h / 2)),
                                cv2.FONT_HERSHEY_SIMPLEX, 1, (0, 0, 255), 2, cv2.LINE_AA)
                    cv2.imshow('FastPlate License Plate Recognition', display_frame)
                continue

            ret, frame = cap.read()
            if not ret:
                print("End of video or error reading frame.")
                stop_event.set()
                break 
            
            # Apply rotation if needed
            if final_rotation_angle == 90:
                frame = cv2.rotate(frame, cv2.ROTATE_90_CLOCKWISE)
            elif final_rotation_angle == 180:
                frame = cv2.rotate(frame, cv2.ROTATE_180)
            elif final_rotation_angle == 270:
                frame = cv2.rotate(frame, cv2.ROTATE_90_COUNTERCLOCKWISE)
            
            frame_height, frame_width = frame.shape[:2]
            frame_nmr_display += 1
            
            frame_for_detection = frame.copy()
            display_frame = frame.copy()

            try:
                detection_input_queue.put_nowait((frame_for_detection, None))
            except queue.Full:
                pass

            # Get latest detection results
            current_vehicles = []
            current_plates = []
            with results_lock:
                current_vehicles = detection_results.get('vehicles', [])
                current_plates = detection_results.get('plates', [])
                
                # Draw vehicle boxes (optional)
                if show_vehicles:
                    for vehicle in current_vehicles:
                        x1, y1, x2, y2, score, class_id = vehicle
                        cv2.rectangle(display_frame, (int(x1), int(y1)), (int(x2), int(y2)), (255, 0, 0), 1)
            
            # Draw license plate boxes
            if show_plates:
                for plate in current_plates:
                    x1, y1, x2, y2, score = plate
                    cv2.rectangle(display_frame, (int(x1), int(y1)), (int(x2), int(y2)), (0, 255, 0), 2)
                        
            # Display latest OCR result
            last_plate_text = f"License: {ocr_worker.get_latest_result()}"
            (text_width, text_height), baseline = cv2.getTextSize(last_plate_text, cv2.FONT_HERSHEY_SIMPLEX, 0.8, 2)
            text_x = frame_width - text_width - 20 if frame_width > text_width + 20 else 10
            text_y = 40
                
            # Draw background rectangle for better visibility
            cv2.rectangle(display_frame, 
                        (text_x - 10, text_y - text_height - 10), 
                        (frame_width - 5, text_y + baseline + 5), 
                        (0, 0, 0), -1)  # Black background
            cv2.rectangle(display_frame, 
                        (text_x - 10, text_y - text_height - 10), 
                        (frame_width - 5, text_y + baseline + 5), 
                        (0, 255, 0), 2)  # Green border, thicker
            cv2.putText(display_frame, last_plate_text, (text_x, text_y), 
                       cv2.FONT_HERSHEY_SIMPLEX, 0.8, (255, 255, 255), 2)  # White text
            
            # Display FPS information (moved to left side, below other indicators)
            fps_text = f"FPS: {video_fps:.1f}"
            cv2.putText(display_frame, fps_text, (10, frame_height - 90), 
                       cv2.FONT_HERSHEY_SIMPLEX, 0.7, (255, 255, 255), 2)
            cv2.putText(display_frame, fps_text, (10, frame_height - 90), 
                       cv2.FONT_HERSHEY_SIMPLEX, 0.7, (0, 0, 0), 1)
            
            # Show OCR processing indicator
            if ocr_worker.processing:
                cv2.putText(display_frame, "FastPlate OCR...", (10, frame_height - 30), 
                           cv2.FONT_HERSHEY_SIMPLEX, 0.6, (0, 255, 255), 2)
            
            # Show Detection processing indicator
            if not detection_input_queue.empty() or (detection_worker.is_alive() and detection_input_queue.qsize() == detection_input_queue.maxsize):
                cv2.putText(display_frame, "Detecting...", (10, frame_height - 60), 
                           cv2.FONT_HERSHEY_SIMPLEX, 0.6, (255, 255, 0), 2)
                
            cv2.imshow('FastPlate License Plate Recognition', display_frame)
                    
    except KeyboardInterrupt:
        print("\nKeyboardInterrupt: Stopping...")
    finally:
        print("Cleaning up...")
        stop_event.set()
        
        # Signal OCR worker to stop
        ocr_worker.stop_event.set() 

        # Clear the queue
        while not detection_input_queue.empty():
            try:
                detection_input_queue.get_nowait()
                detection_input_queue.task_done()
            except queue.Empty:
                break
        
        # Send sentinel to detection worker
        if detection_worker.is_alive():
            try:
                detection_input_queue.put_nowait(None)
            except queue.Full:
                pass

        if detection_worker.is_alive():
            print("Waiting for DetectionWorker to finish...")
            detection_worker.join(timeout=5.0)
            if detection_worker.is_alive():
                print("DetectionWorker did not finish in time.")

        if cap.isOpened():
            cap.release()
        cv2.destroyAllWindows()
        print(f"Total frames displayed: {frame_nmr_display + 1}")


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description='License Plate Detection and Recognition with FastPlateOCR')
    parser.add_argument('video', help='Path to input video file (.mp4)')
    parser.add_argument('--model', default='./license_plate_detector.pt', 
                       help='Path to license plate detection model (default: ./license_plate_detector.pt)')
    parser.add_argument('--fast-plate-model', type=str, default='global-plates-mobile-vit-v2-model',
                       choices=['global-plates-mobile-vit-v2-model', 'european-plates-mobile-vit-v2-model', 
                               'argentinian-plates-cnn-model', 'argentinian-plates-cnn-synth-model'],
                       help='FastPlateOCR model to use (default: global-plates-mobile-vit-v2-model)')
    parser.add_argument('--rotate', type=int, default=0, choices=[0, 90, 180, 270],
                        help='Manually rotate video: 0 (none), 90, 180, 270 degrees clockwise. Overrides auto-detection.')
    parser.add_argument('--ocr-interval', type=int, default=5,
                        help='Process OCR every Nth frame processed by the detection worker (default: 5)')
    parser.add_argument('--show-vehicles-only', action='store_true',
                       help='Show only vehicle detection boxes (faster)')
    parser.add_argument('--show-plates-only', action='store_true', 
                       help='Show only license plate detection and OCR')
    args = parser.parse_args()
    
    # Determine what to show based on arguments
    if args.show_vehicles_only:
        show_vehicles, show_plates = True, False
    elif args.show_plates_only:
        show_vehicles, show_plates = False, True
    else:
        show_vehicles, show_plates = True, True  # Show both by default
    
    main(args.video, args.model, show_vehicles, show_plates, args.fast_plate_model, args.rotate, args.ocr_interval) 