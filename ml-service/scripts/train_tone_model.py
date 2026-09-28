"""
Trains the voice-tone model used in the "Are you okay?" check-in:
calm vs. frightened, from a short spoken reply.
 
Dataset: RAVDESS (speech only). Filenames encode the label, e.g.
    03-01-06-01-02-01-12.wav
     ^^ ^^ ^^ ^^ ^^ ^^ ^^
     |  |  |  |  |  |  actor (01-24)
     |  |  |  |  |  repetition
     |  |  |  |  statement
     |  |  |  intensity
     |  |  emotion   <-- the 3rd number is what we use
     |  vocal channel
     modality
 
Emotion codes: 01 neutral, 02 calm, 03 happy, 04 sad, 05 angry,
               06 fearful, 07 disgust, 08 surprised
 
We collapse these to the two classes the check-in actually needs:
    frightened = fearful, angry     (both read as "not okay" in a check-in)
    calm       = neutral, calm, happy
Sad / disgust / surprised are skipped — they don't map cleanly to either.
 
IMPORTANT — the train/validation split is done BY ACTOR, not randomly.
RAVDESS has only 24 speakers. A random split would put the same person's
voice in both train and validation, and the model could score well just by
recognising voices it has already heard. Holding out whole actors tests what
we actually care about: does it work on a voice it has never heard?
"""
 
import glob
import os
import sys
 
import numpy as np
import torch
import torch.nn as nn
from torch.utils.data import Dataset, DataLoader
from sklearn.metrics import classification_report
 
sys.path.append(os.path.join(os.path.dirname(__file__), ".."))
from app.features import extract_mel_spectrogram, pad_or_truncate_spectrogram
from app.model_arch import DistressCNN
 
DATA_DIR = os.path.join(os.path.dirname(__file__), "..", "data", "tone")
MODEL_OUT = os.path.join(os.path.dirname(__file__), "..", "models", "tone_model.pt")
FIXED_FRAMES = 173
 
LABEL_MAP = {
    5: 1,  # angry    -> frightened
    6: 1,  # fearful  -> frightened
    1: 0,  # neutral  -> calm
    2: 0,  # calm     -> calm
    3: 0,  # happy    -> calm
}
VAL_ACTORS = {21, 22, 23, 24}  # 2 male, 2 female — never seen during training
 
 
def collect_clips():
    """Finds every RAVDESS clip, dedupes by filename (the Kaggle download
    contains each file twice), and returns (path, label, actor) tuples."""
    seen = {}
    for path in glob.glob(os.path.join(DATA_DIR, "**", "*.wav"), recursive=True):
        name = os.path.basename(path)
        if name in seen:
            continue
        parts = name.replace(".wav", "").split("-")
        if len(parts) != 7:
            continue
        emotion, actor = int(parts[2]), int(parts[6])
        if emotion not in LABEL_MAP:
            continue
        seen[name] = (path, LABEL_MAP[emotion], actor)
    return list(seen.values())
 
 
class ToneDataset(Dataset):
    def __init__(self, clips):
        self.clips = clips
 
    def __len__(self):
        return len(self.clips)
 
    def __getitem__(self, idx):
        path, label, _ = self.clips[idx]
        spec = extract_mel_spectrogram(path)
        spec = pad_or_truncate_spectrogram(spec, FIXED_FRAMES)
        return torch.tensor(spec, dtype=torch.float32).unsqueeze(0), torch.tensor(
            label, dtype=torch.float32
        )
 
 
def train(epochs: int = 30, seed: int = 42):
    torch.manual_seed(seed)
 
    clips = collect_clips()
    if not clips:
        raise RuntimeError(
            "No RAVDESS clips found under data/tone. Run download_datasets.py first."
        )
 
    train_clips = [c for c in clips if c[2] not in VAL_ACTORS]
    val_clips = [c for c in clips if c[2] in VAL_ACTORS]
 
    n_fr = sum(c[1] for c in train_clips)
    print(f"Total unique clips: {len(clips)}  (train: {len(train_clips)}, val: {len(val_clips)})")
    print(f"Train balance — frightened: {n_fr}, calm: {len(train_clips) - n_fr}")
    print(f"Validation actors (held out entirely): {sorted(VAL_ACTORS)}")
 
    train_loader = DataLoader(ToneDataset(train_clips), batch_size=16, shuffle=True)
    val_loader = DataLoader(ToneDataset(val_clips), batch_size=16)
 
    model = DistressCNN()  # same small CNN; output = logit of "frightened"
    optimizer = torch.optim.Adam(model.parameters(), lr=1e-3, weight_decay=1e-4)
    # Classes are close to balanced here (unlike the scream data), so a mild
    # weight is enough — no need for the aggressive weighting we used before.
    pos_weight = torch.tensor([(len(train_clips) - n_fr) / max(1, n_fr)])
    criterion = nn.BCEWithLogitsLoss(pos_weight=pos_weight)
 
    for epoch in range(epochs):
        model.train()
        total = 0.0
        for feats, y in train_loader:
            optimizer.zero_grad()
            loss = criterion(model(feats), y)
            loss.backward()
            optimizer.step()
            total += loss.item()
        print(f"Epoch {epoch + 1}/{epochs} — train loss: {total / len(train_loader):.4f}")
 
    model.eval()
    preds, truth = [], []
    with torch.no_grad():
        for feats, y in val_loader:
            p = (torch.sigmoid(model(feats)) > 0.5).float()
            preds.extend(p.tolist())
            truth.extend(y.tolist())
 
    print("\nValidation report (on 4 speakers the model has NEVER heard):")
    print(classification_report(truth, preds, target_names=["calm", "frightened"]))
 
    os.makedirs(os.path.dirname(MODEL_OUT), exist_ok=True)
    torch.save(model.state_dict(), MODEL_OUT)
    print(f"\nModel saved to {MODEL_OUT}")
 
 
if __name__ == "__main__":
    train()