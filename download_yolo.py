import argparse
import os
import requests
from tqdm import tqdm

def download_file(url, filename):
    """
    Helper function to download a file with a progress bar.
    """
    try:
        response = requests.get(url, stream=True)
        response.raise_for_status()  # Raise an exception for bad status codes
        total_size = int(response.headers.get('content-length', 0))
        
        with open(filename, 'wb') as f, tqdm(
            desc=filename,
            total=total_size,
            unit='iB',
            unit_scale=True,
            unit_divisor=1024,
        ) as bar:
            for data in response.iter_content(chunk_size=1024):
                size = f.write(data)
                bar.update(size)
        print(f"Successfully downloaded {filename}")
        return True
    except requests.exceptions.RequestException as e:
        print(f"Error downloading {filename}: {e}")
        if os.path.exists(filename): # Clean up partially downloaded file
            os.remove(filename)
        return False
    except IOError as e:
        print(f"Error writing file {filename}: {e}")
        if os.path.exists(filename): # Clean up partially downloaded file
            os.remove(filename)
        return False

def main():
    parser = argparse.ArgumentParser(description="Download YOLO models from Ultralytics.")
    parser.add_argument(
        '--model_name', 
        type=str, 
        default='yolov8n.pt', 
        help="Name of the YOLO model to download (e.g., yolov8n.pt, yolov10n.pt)."
    )
    args = parser.parse_args()

    model_name = args.model_name.lower()

    if os.path.exists(model_name):
        print(f"Model '{model_name}' already exists in the current directory. Skipping download.")
        return

    # Ultralytics assets are typically hosted on GitHub releases
    # The v0.0.0 tag is a generic one they use for many assets.
    # For newer or differently versioned models, the URL might need adjustment
    # or the user might need to find the specific release URL.
    base_url = "https://github.com/ultralytics/assets/releases/download/v0.0.0/"
    # It's also common for models to be version-specific, e.g., v8.0, v8.1 releases
    # model_url = f"https://github.com/ultralytics/ultralytics/releases/download/v8.1.0/{model_name}" # Example for specific release
    model_url = f"{base_url}{model_name}"

    print(f"Attempting to download {model_name} from {model_url}...")
    
    if not download_file(model_url, model_name):
        print(f"Failed to download from {model_url}.")
        print(f"Please check the model name and its availability at Ultralytics GitHub releases.")
        print(f"The model might be under a different release tag or may require a different URL structure.")
        print(f"Refer to Ultralytics documentation for specific model download links: https://docs.ultralytics.com/models/")

if __name__ == '__main__':
    main() 