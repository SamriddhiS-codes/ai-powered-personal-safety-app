"""
Two checks on the tone model, run from the ml-service folder:
 
    python scripts\\check_pipeline.py
 
1. PIPELINE CHECK - does the new upload path (spectrogram_from_bytes) produce
   the same spectrogram as the training path (extract_mel_spectrogram)?
   The max difference should be ~0.
 
2. ACCURACY CHECK - re-scores the held-out actors (21-24) through the upload
   path and reports accuracy overall and by intensity (01 = normal, 02 = strong).
   Overall accuracy should be close to the 0.75 from training.
"""
import glob
import os
import sys
 
import numpy as np
import torch
 
sys.path.append(os.path.join(os.path.dirname(__file__), ".."))
from app.features import (
    FIXED_FRAMES,
    extract_mel_spectrogram,
    pad_or_truncate_spectrogram,
    spectrogram_from_bytes,
)
from app.model_arch import DistressCNN
 
ROOT = os.path.join(os.path.dirname(__file__), "..")
DATA_DIR = os.path.join(ROOT, "data", "tone")
MODEL_PATH = os.path.join(ROOT, "models", "tone_model.pt")
VAL_ACTORS = {21, 22, 23, 24}
LABEL_MAP = {5: 1, 6: 1, 1: 0, 2: 0, 3: 0}  # same as train_tone_model.py
 
model = DistressCNN()
model.load_state_dict(torch.load(MODEL_PATH, map_location="cpu", weights_only=True))
model.eval()
 
seen = {}
for path in glob.glob(os.path.join(DATA_DIR, "**", "*.wav"), recursive=True):
    name = os.path.basename(path)
    parts = name.replace(".wav", "").split("-")
    if name in seen or len(parts) != 7:
        continue
    emotion, intensity, actor = int(parts[2]), int(parts[3]), int(parts[6])
    if emotion in LABEL_MAP and actor in VAL_ACTORS:
        seen[name] = (path, LABEL_MAP[emotion], intensity)
 
max_diff = 0.0
rows = []  # (label, p_frightened, intensity)
for path, label, intensity in seen.values():
    train_spec = pad_or_truncate_spectrogram(extract_mel_spectrogram(path), FIXED_FRAMES)
    with open(path, "rb") as f:
        upload_spec = spectrogram_from_bytes(f.read())
    max_diff = max(max_diff, float(np.abs(train_spec - upload_spec).max()))
    with torch.no_grad():
        x = torch.tensor(upload_spec, dtype=torch.float32).unsqueeze(0).unsqueeze(0)
        p = float(torch.sigmoid(model(x)).item())
    rows.append((label, p, intensity))
 
print(f"Clips checked: {len(rows)} (actors {sorted(VAL_ACTORS)})")
print(f"\n1. Pipeline check - max spectrogram difference, training path vs upload path: {max_diff:.6f}")
 
labels = np.array([r[0] for r in rows])
probs = np.array([r[1] for r in rows])
intens = np.array([r[2] for r in rows])
preds = (probs > 0.5).astype(int)
 
print(f"\n2. Accuracy check - overall: {(preds == labels).mean():.2f}")
for i in (1, 2):
    m = intens == i
    if m.any():
        print(f"   intensity {i:02d}: accuracy {(preds[m] == labels[m]).mean():.2f} ({m.sum()} clips)")
print(f"   mean P(frightened) on truly frightened clips: {probs[labels == 1].mean():.2f}")
print(f"   mean P(frightened) on truly calm clips:       {probs[labels == 0].mean():.2f}")
print(f"   share of all clips scoring between 0.4 and 0.6: {((probs > 0.4) & (probs < 0.6)).mean():.2f}")