"""
Downloads the two public datasets this project's ML pipeline is built on.

Run once, locally (not inside this container — Kaggle isn't reachable from
here). Requires a free Kaggle account and API token:
  1. Create an account at kaggle.com
  2. Go to Account -> Create New API Token -> downloads kaggle.json
  3. Place it at ~/.kaggle/kaggle.json (chmod 600)
  4. pip install -r ../requirements.txt
  5. python download_datasets.py

Datasets:
  - Human Screaming Detection Dataset (Kaggle) -> data/scream/
      https://www.kaggle.com/datasets/whats2000/human-screaming-detection-dataset
      Used to train the distress-sound CNN (Sprint 2).

  - RAVDESS Emotional Speech Audio (Kaggle mirror) -> data/tone/
      https://www.kaggle.com/datasets/uwrfkaggler/ravdess-emotional-speech-audio
      Used to train the voice-tone (calm vs. frightened) confirmation model (Sprint 3).
      Only the "fearful" and "calm"/"neutral" emotion classes are needed —
      see NOTES below on collapsing RAVDESS's 8 emotion labels down to 2.
"""

import os
import zipfile

DATA_DIR = os.path.join(os.path.dirname(__file__), "..", "data")


def download_and_unzip(dataset_slug: str, target_subdir: str):
    from kaggle.api.kaggle_api_extended import KaggleApi

    api = KaggleApi()
    api.authenticate()

    target_path = os.path.join(DATA_DIR, target_subdir)
    os.makedirs(target_path, exist_ok=True)

    print(f"Downloading {dataset_slug} -> {target_path}")
    api.dataset_download_files(dataset_slug, path=target_path, unzip=True)
    print(f"Done: {target_path}")


if __name__ == "__main__":
    download_and_unzip("whats2000/human-screaming-detection-dataset", "scream")
    download_and_unzip("uwrfkaggler/ravdess-emotional-speech-audio", "tone")

    print(
        "\nNOTES:\n"
        "- RAVDESS filenames encode emotion in the 3rd number, e.g. 03-01-06-...\n"
        "  where 06 = fearful. For this project, collapse to 2 classes:\n"
        "    frightened = {fearful, angry}  (both read as 'not calm' in a check-in)\n"
        "    calm       = {neutral, calm, happy}\n"
        "  Adjust the mapping in train_tone_model.py's LABEL_MAP as your team sees fit.\n"
    )
