import cv2
import numpy as np
import argparse
import pytesseract
import re
import os # Added for path operations

class PyImageSearchANPR:
    def __init__(self, min_ar=2.5, max_ar=5.5, debug=False): # Adjusted AR based on common license plates
        self.min_ar = min_ar
        self.max_ar = max_ar
        self.debug = debug
        self.frame_count = 0 # For debug window naming

    def debug_imshow(self, title, image, wait_key=False):
        if self.debug:
            # Use static titles for window reuse
            window_title = f"Debug: {title}" 
            cv2.imshow(window_title, image)
            if wait_key: # True means pause (cv2.waitKey(0))
                cv2.waitKey(0)
            else: # False means continue after 1ms (cv2.waitKey(1))
                cv2.waitKey(1)

    def locate_license_plate_candidates(self, gray, keep=5):
        # Perform a blackhat morphological operation to reveal dark characters on light backgrounds
        rect_kernel = cv2.getStructuringElement(cv2.MORPH_RECT, (13, 5))
        blackhat = cv2.morphologyEx(gray, cv2.MORPH_BLACKHAT, rect_kernel)
        self.debug_imshow("Blackhat", blackhat)

        # Find regions in the image that are light
        square_kernel = cv2.getStructuringElement(cv2.MORPH_RECT, (3, 3))
        light = cv2.morphologyEx(gray, cv2.MORPH_CLOSE, square_kernel)
        light = cv2.threshold(light, 0, 255, cv2.THRESH_BINARY | cv2.THRESH_OTSU)[1]
        self.debug_imshow("Light Regions", light)

        # Compute the Scharr gradient representation of the blackhat image in the x-direction
        # and scale the result back to the range [0, 255]
        grad_x = cv2.Sobel(blackhat, ddepth=cv2.CV_32F, dx=1, dy=0, ksize=-1)
        grad_x = np.absolute(grad_x)
        (min_val, max_val) = (np.min(grad_x), np.max(grad_x))
        # Handle division by zero if max_val == min_val
        if max_val - min_val > 0:
            grad_x = 255 * ((grad_x - min_val) / (max_val - min_val))
        else:
            grad_x = np.zeros_like(grad_x)
        grad_x = grad_x.astype("uint8")
        self.debug_imshow("Scharr", grad_x)

        # Blur the gradient representation, apply a closing operation, and threshold the image using Otsu's method
        grad_x = cv2.GaussianBlur(grad_x, (5, 5), 0)
        grad_x = cv2.morphologyEx(grad_x, cv2.MORPH_CLOSE, rect_kernel)
        thresh = cv2.threshold(grad_x, 0, 255, cv2.THRESH_BINARY | cv2.THRESH_OTSU)[1]
        self.debug_imshow("Grad Thresh", thresh)

        # Perform a series of erosions and dilations
        thresh = cv2.erode(thresh, None, iterations=2)
        thresh = cv2.dilate(thresh, None, iterations=2)
        self.debug_imshow("Grad Erode/Dilate", thresh)

        # Take the bitwise AND between the threshold result and the light regions of the image
        thresh = cv2.bitwise_and(thresh, thresh, mask=light)
        thresh = cv2.dilate(thresh, None, iterations=2)
        thresh = cv2.erode(thresh, None, iterations=1)
        self.debug_imshow("Final Thresh", thresh, wait_key=self.debug and self.frame_count == 0)

        # Find contours in the thresholded image and sort them by size in descending order
        cnts, _ = cv2.findContours(thresh.copy(), cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
        cnts = sorted(cnts, key=cv2.contourArea, reverse=True)[:keep]
        
        return cnts

    def locate_license_plate(self, gray, candidates, clear_border=False):
        lp_cnt = None
        roi = None

        for c in candidates:
            (x, y, w, h) = cv2.boundingRect(c)
            ar = w / float(h) if h > 0 else 0 # Avoid division by zero

            if self.min_ar <= ar <= self.max_ar:
                lp_cnt = c
                # Extract the license plate ROI
                license_plate = gray[y:y + h, x:x + w]
                if license_plate.size == 0: continue # Skip if crop is empty
                roi = cv2.threshold(license_plate, 0, 255, cv2.THRESH_BINARY_INV | cv2.THRESH_OTSU)[1]
                
                # Option to clear border pixels (if plate has a border that interferes with OCR)
                if clear_border:
                    roi = self.clear_plate_border(roi)

                self.debug_imshow("Located License Plate ROI", license_plate)
                self.debug_imshow("Located ROI (Thresholded)", roi, wait_key=self.debug and self.frame_count == 0)
                break
        
        return roi, lp_cnt
        
    def clear_plate_border(self, plate_roi, p=0.03): # Clear 3% border
        h, w = plate_roi.shape[:2]
        if h == 0 or w == 0: return plate_roi # Skip if ROI is empty
        border_h = int(p * h)
        border_w = int(p * w)
        plate_roi[0:border_h, :] = 0
        plate_roi[h - border_h:h, :] = 0
        plate_roi[:, 0:border_w] = 0
        plate_roi[:, w - border_w:w] = 0
        return plate_roi


    def build_tesseract_options(self, psm=7):
        # Tell Tesseract to only OCR alphanumeric characters
        alphanumeric = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        options = f"-c tessedit_char_whitelist={alphanumeric}"
        # Set the PSM mode
        options += f" --psm {psm}"
        return options

    def find_and_ocr(self, frame, psm=7, clear_border=False, frame_number=0):
        self.frame_count = frame_number # Update frame count for debug window titles
        lp_text = None
        original_frame_shape = frame.shape

        target_width = 600.0
        if frame.shape[1] == 0: return frame, None, "INVALID_FRAME_WIDTH"
        r = target_width / frame.shape[1]
        dim = (int(target_width), int(frame.shape[0] * r))
        # Ensure dim has positive values before resizing
        if dim[0] <= 0 or dim[1] <= 0:
            print(f"[Warning] Invalid resize dimensions: {dim}. Skipping resize.")
            processed_frame = frame.copy()
        else:
            processed_frame = cv2.resize(frame, dim, interpolation=cv2.INTER_AREA)
        
        gray = cv2.cvtColor(processed_frame, cv2.COLOR_BGR2GRAY)
        self.debug_imshow("Grayscale", gray)

        # Locate license plate candidates
        candidates = self.locate_license_plate_candidates(gray)

        # Locate the actual license plate from the candidates
        (lp_roi, lp_cnt_resized) = self.locate_license_plate(gray, candidates, clear_border=clear_border)
        lp_cnt_original = None

        if lp_roi is not None and lp_cnt_resized is not None:
            # Scale contour back to original frame size
            lp_cnt_original = lp_cnt_resized.astype("float")
            lp_cnt_original[:, 0, 0] /= r
            lp_cnt_original[:, 0, 1] /= r
            lp_cnt_original = lp_cnt_original.astype("int")

            self.debug_imshow("License Plate ROI for OCR", lp_roi, wait_key=self.debug and frame_number == 0)
            options = self.build_tesseract_options(psm=psm)
            try:
                raw_ocr_output = pytesseract.image_to_string(lp_roi, config=options).strip()
                lp_text = "".join(c for c in raw_ocr_output if c.isalnum())
                print(f"Frame {frame_number} - Raw Tesseract: '{raw_ocr_output}', Cleaned: '{lp_text}'")
            except pytesseract.TesseractNotFoundError:
                print("[ERROR] Tesseract is not installed or not in your PATH.")
                lp_text = "TESSERACT_ERROR"
            except Exception as e:
                print(f"[ERROR] Tesseract OCR failed on frame {frame_number}: {e}")
                lp_text = "OCR_FAILED"
            
            if self.debug and lp_cnt_resized is not None:
                debug_display_frame = processed_frame.copy()
                cv2.drawContours(debug_display_frame, [lp_cnt_resized], -1, (0, 255, 0), 2)
                if lp_text and lp_text not in ["TESSERACT_ERROR", "OCR_FAILED"]:
                    (x,y,w,h) = cv2.boundingRect(lp_cnt_resized)
                    cv2.putText(debug_display_frame, lp_text, (x, y - 15), 
                                cv2.FONT_HERSHEY_SIMPLEX, 0.75, (0, 0, 255), 2)
                # Changed wait_key to False to prevent blocking on every frame for this debug view
                self.debug_imshow("Output ANPR (Debug Frame)", debug_display_frame, wait_key=False)

        return processed_frame, lp_cnt_original, lp_text


if __name__ == '__main__':
    ap = argparse.ArgumentParser()
    ap.add_argument("-i", "--input", required=True, help="Path to input image or video file")
    ap.add_argument("-d", "--debug", action="store_true", help="Whether or not to show debug images")
    ap.add_argument("--min-ar", type=float, default=2.5, help="Minimum aspect ratio for license plate filtering")
    ap.add_argument("--max-ar", type=float, default=5.5, help="Maximum aspect ratio for license plate filtering")
    ap.add_argument("--psm", type=int, default=7, help="Tesseract PSM mode (e.g., 7 for single line, 8 for single word)")
    ap.add_argument("--clear-border", action="store_true", help="Clear border pixels from license plate ROI before OCR")
    args = vars(ap.parse_args())

    anpr = PyImageSearchANPR(min_ar=args["min_ar"], max_ar=args["max_ar"], debug=args["debug"])
    input_path = args["input"]

    # Check if input is an image or video
    is_video = False
    video_extensions = [".mp4", ".avi", ".mov", ".mkv"]
    if any(input_path.lower().endswith(ext) for ext in video_extensions):
        is_video = True

    if not is_video:
        # Process single image
        try:
            image = cv2.imread(input_path)
            if image is None:
                print(f"Error: Could not load image from {input_path}")
                exit()

            print(f"Processing image: {input_path}")
            # Pass frame_number=0 for single image processing
            (processed_image, lp_contour, ocr_text) = anpr.find_and_ocr(
                image, psm=args["psm"], clear_border=args["clear_border"], frame_number=0
            )

            if not args["debug"]:
                output_display = image.copy() # Display on original scale image
                if lp_contour is not None:
                    cv2.drawContours(output_display, [lp_contour], -1, (0, 255, 0), 2)
                    if ocr_text and ocr_text not in ["TESSERACT_ERROR", "OCR_FAILED"]:
                        (x, y, w, h) = cv2.boundingRect(lp_contour)
                        cv2.putText(output_display, ocr_text, (x, y - 15), 
                                    cv2.FONT_HERSHEY_SIMPLEX, 0.75, (0, 0, 255), 2)
                cv2.imshow("ANPR Result", output_display)
                cv2.waitKey(0)
            elif args["debug"] and lp_contour is None:
                print("No license plate found in debug mode for the image.")
                cv2.imshow("Input Image (No Plate Found - Debug)", image)
                cv2.waitKey(0)

        except Exception as e:
            print(f"An error occurred processing image {input_path}: {e}")
        finally:
            cv2.destroyAllWindows()
    else:
        # Process video
        cap = cv2.VideoCapture(input_path)
        if not cap.isOpened():
            print(f"Error: Could not open video file {input_path}")
            exit()

        frame_nmr = 0
        print(f"Processing video: {input_path}...")
        try:
            while True:
                ret, frame = cap.read()
                if not ret:
                    print("End of video or error reading frame.")
                    break
                
                frame_nmr += 1
                
                # Pass current frame_nmr to find_and_ocr
                (processed_frame_resized, lp_contour_original, ocr_text) = anpr.find_and_ocr(
                    frame, psm=args["psm"], clear_border=args["clear_border"], frame_number=frame_nmr
                )
                
                # Create a display frame (original size) to draw on
                display_frame = frame.copy() 

                if lp_contour_original is not None:
                    cv2.drawContours(display_frame, [lp_contour_original], -1, (0, 255, 0), 2)
                    if ocr_text and ocr_text not in ["TESSERACT_ERROR", "OCR_FAILED"]:
                        (x, y, w, h) = cv2.boundingRect(lp_contour_original)
                        cv2.putText(display_frame, ocr_text, (x, y - 15), 
                                    cv2.FONT_HERSHEY_SIMPLEX, 0.6, (0, 0, 255), 2)
                
                # Display processing status
                cv2.putText(display_frame, f"Frame: {frame_nmr}", (10, 30), 
                            cv2.FONT_HERSHEY_SIMPLEX, 0.7, (255,255,0), 2)

                cv2.imshow("OpenCV ANPR Video", display_frame)

                if cv2.waitKey(1) & 0xFF == ord('q'):
                    print("Video processing stopped by user.")
                    break
        except Exception as e:
            print(f"An error occurred during video processing: {e}")
        finally:
            cap.release()
            cv2.destroyAllWindows()
            print(f"Total frames processed from video: {frame_nmr}") 