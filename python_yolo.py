import argparse
import os
import cv2
import re
import torch
import threading
import time
import queue
import subprocess # Added for ffprobe
import json       # Added for ffprobe output
import numpy as np # Added for perspective correction
from ultralytics import YOLO
from transformers import TrOCRProcessor, VisionEncoderDecoderModel
from PIL import Image
import easyocr


class SimpleOCRWorker:
    """Simple background OCR worker for latest license plate"""
    def __init__(self, ocr_engine_type='trocr', processor=None, model=None, easyocr_reader=None):
        self.ocr_engine_type = ocr_engine_type
        self.processor = processor  # For TrOCR
        self.model = model          # For TrOCR
        self.easyocr_reader = easyocr_reader # For EasyOCR
        self.current_task = None
        self.latest_result = "No plate detected"
        self.processing = False
        self.stop_event = threading.Event()
        
    def process_latest(self, license_plate_crop):
        """Process the latest license plate (non-blocking)"""
        if not self.processing and not self.stop_event.is_set():
            self.current_task = license_plate_crop.copy()
            thread = threading.Thread(target=self._process_ocr, daemon=True)
            thread.start()
    
    def get_latest_result(self):
        """Get the latest OCR result"""
        return self.latest_result
        
    def _process_ocr(self):
        """Process OCR in background"""
        if self.current_task is None or self.stop_event.is_set():
            self.processing = False
            return
            
        self.processing = True
        try:
            if self.ocr_engine_type == 'trocr':
                license_plate_text, confidence = self._read_license_plate_trocr(self.current_task)
            elif self.ocr_engine_type == 'easyocr':
                license_plate_text, confidence = self._read_license_plate_easyocr(self.current_task)
            else:
                raise ValueError(f"Unsupported OCR engine: {self.ocr_engine_type}")

            if self.stop_event.is_set():
                return

            if license_plate_text:
                self.latest_result = license_plate_text
                # print(f"OCR Result: {license_plate_text} (confidence: {confidence:.2f})")
        except Exception as e:
            if not self.stop_event.is_set():
            print(f"OCR error: {e}")
        finally:
            self.processing = False
            
    def _order_points(self, pts):
        """Order the four points: tl, tr, br, bl"""
        rect = np.zeros((4, 2), dtype="float32")
        s = pts.sum(axis=1)
        rect[0] = pts[np.argmin(s)] # Top-left
        rect[2] = pts[np.argmax(s)] # Bottom-right

        diff = np.diff(pts, axis=1)
        rect[1] = pts[np.argmin(diff)] # Top-right
        rect[3] = pts[np.argmax(diff)] # Bottom-left
        return rect

    def _correct_plate_perspective(self, plate_crop_bgr):
        """Detects edges and corrects perspective of the license plate."""
        if plate_crop_bgr is None or plate_crop_bgr.size == 0:
            return plate_crop_bgr # Return original if empty
        
        original_crop = plate_crop_bgr.copy()
        gray = cv2.cvtColor(plate_crop_bgr, cv2.COLOR_BGR2GRAY)
        blurred = cv2.GaussianBlur(gray, (5, 5), 0)
        # Canny parameters might need tuning based on plate image quality
        edged = cv2.Canny(blurred, 50, 150) 

        contours, _ = cv2.findContours(edged.copy(), cv2.RETR_LIST, cv2.CHAIN_APPROX_SIMPLE)
        contours = sorted(contours, key=cv2.contourArea, reverse=True)[:10] # Get 10 largest contours

        screen_cnt = None
        for c in contours:
            peri = cv2.arcLength(c, True)
            approx = cv2.approxPolyDP(c, 0.02 * peri, True) # 0.018 to 0.04 are typical epsilon values
            if len(approx) == 4:
                # Check if the contour area is a significant portion of the image to avoid small quads
                # Also, ensure it's reasonably rectangular (e.g. aspect ratio limits if needed)
                if cv2.contourArea(approx) > (gray.shape[0] * gray.shape[1] * 0.1): # At least 10% of crop area
                    screen_cnt = approx
                    break
        
        if screen_cnt is None:
            # print("DEBUG: No 4-point contour found for perspective correction.")
            return original_crop # Return original if no suitable contour is found

        # Order the points
        ordered_points = self._order_points(screen_cnt.reshape(4, 2))
        (tl, tr, br, bl) = ordered_points

        # Compute the width of the new image
        width_a = np.sqrt(((br[0] - bl[0]) ** 2) + ((br[1] - bl[1]) ** 2))
        width_b = np.sqrt(((tr[0] - tl[0]) ** 2) + ((tr[1] - tl[1]) ** 2))
        max_width = max(int(width_a), int(width_b))
        if max_width == 0: return original_crop # Avoid division by zero if width is 0

        # Compute the height of the new image
        height_a = np.sqrt(((tr[0] - br[0]) ** 2) + ((tr[1] - br[1]) ** 2))
        height_b = np.sqrt(((tl[0] - bl[0]) ** 2) + ((tl[1] - bl[1]) ** 2))
        max_height = max(int(height_a), int(height_b))
        if max_height == 0: return original_crop # Avoid division by zero if height is 0

        # Define the destination points for a "birds-eye view"
        # Ensure target dimensions are reasonable, e.g., for typical LP aspect ratio 3:1 or 4:1
        # Let's aim for a fixed aspect ratio for the output, e.g., width = 3 * height
        # or use max_width and max_height and then resize if needed later.
        # Using calculated max_width and max_height for now. Target aspect can be enforced by resizing later.
        dst_pts = np.array([
            [0, 0],
            [max_width - 1, 0],
            [max_width - 1, max_height - 1],
            [0, max_height - 1]], dtype="float32")

        # Compute the perspective transform matrix and warp
        matrix = cv2.getPerspectiveTransform(ordered_points, dst_pts)
        warped = cv2.warpPerspective(original_crop, matrix, (max_width, max_height))
        
        # Optional: Add a check for warped image size/quality. If too small/distorted, return original.
        if warped.shape[0] < 20 or warped.shape[1] < 50: # Example: min height 20, min width 50
            # print("DEBUG: Warped image too small, returning original.")
            return original_crop

        # print("DEBUG: Perspective correction applied.")
        return warped

    def _preprocess_plate_crop(self, license_plate_crop_bgr):
        """Apply pre-processing steps to the license plate crop."""
        # Correct perspective first
        corrected_perspective_crop = self._correct_plate_perspective(license_plate_crop_bgr)
        if corrected_perspective_crop is None or corrected_perspective_crop.size == 0:
             # Fallback to original if perspective correction somehow failed badly
            corrected_perspective_crop = license_plate_crop_bgr 

        # 1. Convert to Grayscale (from the perspective-corrected crop)
        gray = cv2.cvtColor(corrected_perspective_crop, cv2.COLOR_BGR2GRAY)
        
        # 2. Apply Gaussian Blur to reduce noise before thresholding
        blurred = cv2.GaussianBlur(gray, (3, 3), 0)
        
        # 3. Adaptive Thresholding
        thresh = cv2.adaptiveThreshold(
            blurred, 
            255, 
            cv2.ADAPTIVE_THRESH_GAUSSIAN_C, 
            cv2.THRESH_BINARY, 
            11, 
            2   
        )
        inverted_thresh = cv2.bitwise_not(thresh)
        return inverted_thresh

    def _validate_and_format_plate_by_rules(self, raw_ocr_text: str, numeric_plate_text: str) -> str | None:
        """
        Validates the numeric plate text based on allowed lengths and formats it
        according to predefined rules. Uses raw_ocr_text for hints on 7-digit formats.
        Returns formatted plate string or None if invalid.
        If 7 digits and raw format is ambiguous, returns both possible formats.
        """
        len_numeric = len(numeric_plate_text)

        nn_nnn_nn_raw_re = r"^[a-zA-Z0-9]{2}[-\\s]?[a-zA-Z0-9]{3}[-\\s]?[a-zA-Z0-9]{2}$"
        n_nnnn_nn_raw_re = r"^[a-zA-Z0-9]{1}[-\\s]?[a-zA-Z0-9]{4}[-\\s]?[a-zA-Z0-9]{2}$"

        if len_numeric == 7:
            format1 = f"{numeric_plate_text[:2]}-{numeric_plate_text[2:5]}-{numeric_plate_text[5:]}" # NN-NNN-NN
            format2 = f"{numeric_plate_text[:1]}-{numeric_plate_text[1:5]}-{numeric_plate_text[5:]}" # N-NNNN-NN
            
            # Check raw text for hints
            match_format1_hint = re.fullmatch(nn_nnn_nn_raw_re, raw_ocr_text)
            match_format2_hint = re.fullmatch(n_nnnn_nn_raw_re, raw_ocr_text)

            if match_format1_hint and not match_format2_hint:
                return format1
            elif match_format2_hint and not match_format1_hint:
                return format2
            elif match_format1_hint and match_format2_hint:
                # Ambiguous if raw text could match both (e.g. "1234567" with no separators)
                # In this specific case, it means the regex matched purely on character counts
                # without strong separator hints. Display both.
                return f"{format1} or {format2}"
            else: # No clear hint from raw text structure
                return f"{format1} or {format2}"
        elif len_numeric == 8:
            return f"{numeric_plate_text[:3]}-{numeric_plate_text[3:5]}-{numeric_plate_text[5:]}" # NNN-NN-NNN
        else:
            return None

    def _read_license_plate_trocr(self, license_plate_crop_bgr):
        """Read license plate text using TrOCR"""
        try:
            # Perspective correction is now handled within _preprocess_plate_crop
            processed_plate_img = self._preprocess_plate_crop(license_plate_crop_bgr)
            pil_image = Image.fromarray(processed_plate_img)
            pixel_values = self.processor(images=pil_image, return_tensors="pt").pixel_values.to(self.model.device)
            
            if pixel_values.dtype != self.model.dtype:
                pixel_values = pixel_values.to(self.model.dtype)
            
            generated_ids = self.model.generate(
                pixel_values, 
                max_length=16, 
                num_beams=1, 
                early_stopping=True, 
                do_sample=False
            )
            
            raw_text_from_ocr = self.processor.batch_decode(generated_ids, skip_special_tokens=True)[0]
            
            # Apply visual character corrections
            char_map = {
                'O': '0', 'I': '1', 'L': '1', 'S': '5', 'B': '8',
                'Z': '2', 'G': '6', 'Q': '0', 'A': '4', 'E': '3'
            }
            corrected_raw_text = "".join([char_map.get(char.upper(), char.upper()) for char in raw_text_from_ocr])
            
            numeric_text = re.sub(r'[^0-9]', '', corrected_raw_text)
            
            formatted_plate = self._validate_and_format_plate_by_rules(corrected_raw_text, numeric_text)

            if formatted_plate:
                return formatted_plate, 1.0 
            else:
                return None, 0
                
        except Exception as e:
            print(f"TrOCR processing error: {e}")
            return None, 0

    def _read_license_plate_easyocr(self, license_plate_crop_bgr):
        """Read license plate text using EasyOCR"""
        try:
            # Perspective correction is now handled within _preprocess_plate_crop
            processed_plate_img = self._preprocess_plate_crop(license_plate_crop_bgr)
            results = self.easyocr_reader.readtext(processed_plate_img, detail=1)

            if results:
                raw_text_from_ocr = "".join([res[1] for res in results])
                
                # Apply visual character corrections
                char_map = {
                    'O': '0', 'I': '1', 'L': '1', 'S': '5', 'B': '8',
                    'Z': '2', 'G': '6', 'Q': '0', 'A': '4', 'E': '3'
                }
                corrected_raw_text = "".join([char_map.get(char.upper(), char.upper()) for char in raw_text_from_ocr])
                
                numeric_text = re.sub(r'[^0-9]', '', corrected_raw_text)
                
                formatted_plate = self._validate_and_format_plate_by_rules(corrected_raw_text, numeric_text)
                
                if formatted_plate:
                    confidence = sum([res[2] for res in results]) / len(results) if results else 0
                    return formatted_plate, confidence
            
            return None, 0
        except Exception as e:
            print(f"EasyOCR processing error: {e}")
            return None, 0

    def process_synchronous_ocr(self, license_plate_crop_bgr):
        """Processes OCR synchronously for a given crop and returns the text."""
        if license_plate_crop_bgr is None or license_plate_crop_bgr.size == 0:
            return None
        text_result = None
        try:
            # Perspective correction is now handled within _preprocess_plate_crop
            preprocessed_for_sync_ocr = self._preprocess_plate_crop(license_plate_crop_bgr)

            if self.ocr_engine_type == 'trocr':
                raw_text_from_ocr = self.processor.batch_decode(self.model.generate(self.processor(images=Image.fromarray(preprocessed_for_sync_ocr), return_tensors="pt").pixel_values.to(self.model.device), max_length=16, num_beams=1, early_stopping=True, do_sample=False), skip_special_tokens=True)[0]
                char_map = {
                    'O': '0', 'I': '1', 'L': '1', 'S': '5', 'B': '8',
                    'Z': '2', 'G': '6', 'Q': '0', 'A': '4', 'E': '3'
                }
                corrected_raw_text = "".join([char_map.get(char.upper(), char.upper()) for char in raw_text_from_ocr])
                numeric_text = re.sub(r'[^0-9]', '', corrected_raw_text)
                text_result = self._validate_and_format_plate_by_rules(corrected_raw_text, numeric_text)

            elif self.ocr_engine_type == 'easyocr':
                results = self.easyocr_reader.readtext(preprocessed_for_sync_ocr, detail=1)
                if results:
                    raw_text_from_ocr = "".join([res[1] for res in results])
                    char_map = {
                        'O': '0', 'I': '1', 'L': '1', 'S': '5', 'B': '8',
                        'Z': '2', 'G': '6', 'Q': '0', 'A': '4', 'E': '3'
                    }
                    corrected_raw_text = "".join([char_map.get(char.upper(), char.upper()) for char in raw_text_from_ocr])
                    numeric_text = re.sub(r'[^0-9]', '', corrected_raw_text)
                    text_result = self._validate_and_format_plate_by_rules(corrected_raw_text, numeric_text)
            else:
                print(f"Unsupported OCR engine for synchronous processing: {self.ocr_engine_type}")
                return None
            return text_result
        except Exception as e:
            print(f"Synchronous OCR error: {e}")
            return None


class DetectionWorker(threading.Thread):
    def __init__(self, vehicle_detector, license_plate_detector, ocr_worker, input_queue, results_dict, stop_event, lock, show_vehicles, show_plates, output_lp_dir, output_lp_dir_corrected, ocr_interval):
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
        self.output_lp_dir_corrected = output_lp_dir_corrected
        self.saved_lp_count = 0
        self.saved_lp_corrected_count = 0
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
                
                original_frame_for_ocr, _ = frame_data # display_frame_copy is not used in worker
                self.frame_nmr_processed +=1

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
                    license_plate_crop_orig = original_frame_for_ocr[int(y1_orig):int(y2_orig), int(x1_orig):int(x2_orig), :]
                    
                    if license_plate_crop_orig.size > 0:
                        # 1. Attempt perspective correction FOR SAVING CORRECTED IMAGE
                        # We make a copy to ensure the original crop is not modified if correction fails
                        # or if we want to pass the original to further processing.
                        corrected_lp_for_saving = self.ocr_worker._correct_plate_perspective(license_plate_crop_orig.copy())
                        
                        if corrected_lp_for_saving is not None and corrected_lp_for_saving.size > 0:
                            # Check if correction actually changed the image substantially, to avoid saving identicals.
                            # This is a simple check; more robust would be np.array_equal or diff.
                            # For now, if shape is different or if it's not the exact same memory object (which copy ensures)
                            # it implies a transformation likely occurred or was attempted.
                            # Or, if the _correct_plate_perspective returns the original if it can't correct,
                            # we might end up saving the original again. A more robust check could be added.
                            # For simplicity now, we save if corrected_lp_for_saving is valid.
                            try:
                                lp_corrected_filename = os.path.join(self.output_lp_dir_corrected, f"lp_corr_{self.saved_lp_corrected_count:04d}_frame{self.frame_nmr_processed}.png")
                                cv2.imwrite(lp_corrected_filename, corrected_lp_for_saving)
                                self.saved_lp_corrected_count += 1
                            except Exception as e:
                                print(f"Error saving perspective-corrected license plate image: {e}")

                        # 2. Perform synchronous OCR for annotation and save the ANNOTATED (also perspective corrected internally) crop
                        # The license_plate_crop_orig is used here, as process_synchronous_ocr does its own perspective correction.
                        annot_text = self.ocr_worker.process_synchronous_ocr(license_plate_crop_orig)
                        # print(f"DEBUG (DetectionWorker): Sync OCR for saving frame {self.frame_nmr_processed}. Text: '{annot_text}'")
                        
                        annotated_lp_to_save = license_plate_crop_orig.copy() # Start with original for annotation drawing
                        # If perspective correction was successful for the *annotated* version (done inside process_synchronous_ocr -> _preprocess_plate_crop)
                        # we should ideally draw on that. For now, drawing on license_plate_crop_orig and then saving that after annotation.
                        # This means the annotated LP saved in LPs/ might not be the same perspective as LPs_corrected/ if correction is involved.
                        # To ensure consistency: the image passed to process_synchronous_ocr for text gen
                        # should be the same one that then gets annotated and saved.
                        # So, let's get the text, then get the *drawable* base image from the OCR worker (which would be perspective corrected)
                        # This is getting complex. Simpler: the current `process_synchronous_ocr` already uses the corrected one for OCR text generation.
                        # The image that's annotated should be the one that has undergone perspective correction if possible.
                        
                        # Let's use corrected_lp_for_saving as the base for annotation if it's valid, otherwise fallback to original crop
                        base_for_annotation = corrected_lp_for_saving if (corrected_lp_for_saving is not None and corrected_lp_for_saving.size > 0) else license_plate_crop_orig
                        annotated_lp_to_save = base_for_annotation.copy()

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
                            self.saved_lp_count += 1
                        except Exception as e:
                            print(f"Error saving annotated license plate image: {e}")
                        
                        if self.frame_nmr_processed % self.ocr_processing_interval == 0:
                            self.ocr_worker.process_latest(license_plate_crop_orig.copy()) 

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
                # Normalize to common positive values
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


def main(video_path, model_path, show_vehicles=True, show_plates=True, ocr_engine_type='trocr', manual_rotation=0, ocr_interval=5):
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
    
    # Initialize OCR engine and worker
    ocr_processor = None
    trocr_model_instance = None
    easyocr_reader_instance = None
    
    if ocr_engine_type == 'trocr':
    print("Initializing TrOCR...")
    device = torch.device('cuda' if torch.cuda.is_available() else 'cpu')
        ocr_processor = TrOCRProcessor.from_pretrained('microsoft/trocr-small-printed', use_fast=False)
        trocr_model_instance = VisionEncoderDecoderModel.from_pretrained('microsoft/trocr-small-printed').to(device)
        trocr_model_instance.eval()
        if device.type == 'cuda':
            trocr_model_instance = trocr_model_instance.half()
        print(f"TrOCR initialized on {device}!")
        ocr_worker = SimpleOCRWorker(ocr_engine_type='trocr', processor=ocr_processor, model=trocr_model_instance)
    elif ocr_engine_type == 'easyocr':
        print("Initializing EasyOCR...")
        easyocr_reader_instance = easyocr.Reader(['en'])
        print("EasyOCR initialized!")
        ocr_worker = SimpleOCRWorker(ocr_engine_type='easyocr', easyocr_reader=easyocr_reader_instance)
    else:
        print(f"Error: Unknown OCR engine '{ocr_engine_type}'. Exiting.")
        return

    # Get video rotation
    detected_rotation_angle = get_video_rotation(video_path)
    
    # Determine final rotation angle (manual override takes precedence)
    final_rotation_angle = manual_rotation if manual_rotation != 0 else detected_rotation_angle
    if manual_rotation != 0 and detected_rotation_angle != 0 and manual_rotation != detected_rotation_angle:
        print(f"Manual rotation --rotate {manual_rotation} overrides detected rotation {detected_rotation_angle}.")
    elif final_rotation_angle != 0:
        print(f"Applying rotation: {final_rotation_angle} degrees.")

    # Create output directory for LPs
    output_dir_lps = "script_output/LPs"
    os.makedirs(output_dir_lps, exist_ok=True)
    print(f"Saving detected license plates to: {os.path.abspath(output_dir_lps)}")

    # Create output directory for perspective-corrected LPs
    output_dir_lps_corrected = "script_output/LPs_corrected"
    os.makedirs(output_dir_lps_corrected, exist_ok=True)
    print(f"Saving perspective-corrected license plates to: {os.path.abspath(output_dir_lps_corrected)}")
    
    # Load video
    cap = cv2.VideoCapture(video_path)
    if not cap.isOpened():
        print(f"Error: Could not open video {video_path}")
        return
        
    # Initial frame dimensions (can change if rotated)
    original_frame_width = int(cap.get(cv2.CAP_PROP_FRAME_WIDTH))
    original_frame_height = int(cap.get(cv2.CAP_PROP_FRAME_HEIGHT))
    
    # Threading and Queues
    detection_input_queue = queue.Queue(maxsize=5) # Max 5 frames buffered for detection
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
        output_dir_lps_corrected,
        ocr_interval
    )
    detection_worker.start()

    frame_nmr_display = -1
    is_paused = False # Added pause state variable
    
    try:
        while not stop_event.is_set():
            key = cv2.waitKey(1) # Check for key press first
            if key & 0xFF == ord('q'):
                print("User requested quit.")
                stop_event.set()
                break
            if key & 0xFF == ord(' '): # Spacebar for pause/resume
                is_paused = not is_paused
                if is_paused:
                    print("Paused")
                else:
                    print("Resumed")
            
            if is_paused:
                if 'display_frame' in locals() and display_frame is not None:
                    # Use dynamic frame dimensions for paused text
                    current_frame_height, current_frame_width = display_frame.shape[:2]
                    (text_w, text_h), _ = cv2.getTextSize("Paused", cv2.FONT_HERSHEY_SIMPLEX, 1, 2)
                    cv2.putText(display_frame, "Paused", 
                                (int(current_frame_width / 2 - text_w / 2), int(current_frame_height / 2 + text_h / 2)),
                                cv2.FONT_HERSHEY_SIMPLEX, 1, (0, 0, 255), 2, cv2.LINE_AA)
                    cv2.imshow('License Plate Recognition', display_frame)
                continue 

            ret, frame = cap.read()
            if not ret:
                print("End of video or error reading frame.")
                stop_event.set() # Signal other threads to stop
                break 
            
            # Apply rotation if needed
            if final_rotation_angle == 90:
                frame = cv2.rotate(frame, cv2.ROTATE_90_CLOCKWISE)
            elif final_rotation_angle == 180:
                frame = cv2.rotate(frame, cv2.ROTATE_180)
            elif final_rotation_angle == 270:
                frame = cv2.rotate(frame, cv2.ROTATE_90_COUNTERCLOCKWISE)
            
            # Update frame dimensions after rotation for text placement
            frame_height, frame_width = frame.shape[:2]

            frame_nmr_display += 1
            
            # Prepare frame for detection worker (send original quality)
            # Display a copy to avoid modification races
            frame_for_detection = frame.copy()
                display_frame = frame.copy()

            try:
                # Non-blocking put to queue
                detection_input_queue.put_nowait((frame_for_detection, None)) # Second element could be display_frame copy if needed by worker for drawing
            except queue.Full:
                # print("Detection queue full, skipping frame for detection.") # Optional: for debugging
                pass # If queue is full, main loop continues, prioritizing display

            # Get latest detection results (non-blocking)
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
                    # cv2.putText(display_frame, f'Vehicle {score:.2f}', (int(x1), int(y1) - 25), 
                    #            cv2.FONT_HERSHEY_SIMPLEX, 0.5, (255, 0, 0), 1)
            
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
                
                cv2.rectangle(display_frame, 
                            (text_x - 10, text_y - text_height - 10), 
                            (frame_width - 5, text_y + baseline + 5), 
                            (0, 0, 0), -1)
                cv2.rectangle(display_frame, 
                            (text_x - 10, text_y - text_height - 10), 
                            (frame_width - 5, text_y + baseline + 5), 
                        (0, 255, 0), 1) # Thinner border
                cv2.putText(display_frame, last_plate_text, (text_x, text_y), 
                           cv2.FONT_HERSHEY_SIMPLEX, 0.8, (255, 255, 255), 2)
                
            # Show OCR processing indicator
                if ocr_worker.processing:
                cv2.putText(display_frame, "OCR...", (10, frame_height - 30), 
                               cv2.FONT_HERSHEY_SIMPLEX, 0.6, (0, 255, 255), 2)
            
            # Show Detection processing indicator (based on queue or worker activity)
            if not detection_input_queue.empty() or detection_worker.is_alive() and detection_input_queue.maxsize == detection_input_queue.qsize():
                cv2.putText(display_frame, "Detecting...", (10, frame_height - 60), 
                          cv2.FONT_HERSHEY_SIMPLEX, 0.6, (255, 255, 0), 2)
                
                cv2.imshow('License Plate Recognition', display_frame)
                    
    except KeyboardInterrupt:
        print("\nKeyboardInterrupt: Stopping...")
    finally:
        print("Cleaning up...")
        stop_event.set()
        
        # Signal OCR worker to stop its current task if any
        ocr_worker.stop_event.set() 

        # Attempt to clear the queue for the detection worker
        while not detection_input_queue.empty():
            try:
                detection_input_queue.get_nowait()
            except queue.Empty:
                break
            detection_input_queue.task_done() # For each get
        
        # Send sentinel to detection worker if it's alive
        if detection_worker.is_alive():
            try:
                detection_input_queue.put_nowait(None) # Sentinel to stop worker
            except queue.Full:
                pass # Worker might already be stopping or queue full

        if detection_worker.is_alive():
            print("Waiting for DetectionWorker to finish...")
            detection_worker.join(timeout=5.0) # Wait for worker to finish
            if detection_worker.is_alive():
                print("DetectionWorker did not finish in time.")
        
        # OCR worker uses daemon threads, should exit when main thread exits after stop_event is set.
        # No explicit join needed unless its threads were non-daemon.

        if cap.isOpened():
        cap.release()
        cv2.destroyAllWindows()
            print(f"Total frames displayed: {frame_nmr_display + 1}")
            # print(f"Total frames processed by detection_worker: {detection_worker.frame_nmr_processed if 'detection_worker' in locals() and hasattr(detection_worker, 'frame_nmr_processed') else 'N/A'}")


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description='License Plate Detection and Recognition')
    parser.add_argument('video', help='Path to input video file (.mp4)')
    parser.add_argument('--model', default='./license_plate_detector.pt', 
                       help='Path to license plate detection model (default: ./license_plate_detector.pt)')
    parser.add_argument('--ocr', type=str, default='trocr', choices=['trocr', 'easyocr'],
                       help='OCR engine to use: "trocr" or "easyocr" (default: trocr)')
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
    
    main(args.video, args.model, show_vehicles, show_plates, args.ocr, args.rotate, args.ocr_interval) 