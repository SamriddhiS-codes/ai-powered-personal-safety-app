"""
Trains the distress-sound detector (scream vs. non-scream) on the Kaggle
Human Screaming Detection Dataset.

Expected data layout after running download_datasets.py:
    ml-service/data/scream/
        scream/*.wav
        not_scream/*.wav
  (adjust GLOB patterns below to match the actual folder names in the
   dataset you download — Kaggle dataset internal structure can vary)

This is a starting point, not a finished pipeline: run it, look at the
validation accuracy, and iterate (more data augmentation, deeper CNN,
different features) with your team before treating a number as final.
"""

import glob
import os
import numpy as np
import torch
import torch.nn as nn
from torch.utils.data import Dataset, DataLoader, random_split
from sklearn.metrics import classification_report

import sys
sys.path.append(os.path.join(os.path.dirname(__file__), ".."))
from app.features import extract_mel_spectrogram

DATA_DIR = os.path.join(os.path.dirname(__file__), "..", "data", "scream")
MODEL_OUT = os.path.join(os.path.dirname(__file__), "..", "models", "distress_model.pt")
N_MELS = 128
FIXED_FRAMES = 173  # ~4 seconds at default hop length; pad/truncate to this


class ScreamDataset(Dataset):
    def __init__(self, positive_glob: str, negative_glob: str):
        self.paths = []
        self.labels = []
        for p in glob.glob(positive_glob):
            self.paths.append(p)
            self.labels.append(1)
        for p in glob.glob(negative_glob):
            self.paths.append(p)
            self.labels.append(0)

    def __len__(self):
        return len(self.paths)

    def __getitem__(self, idx):
        spec = extract_mel_spectrogram(self.paths[idx])
        spec = self._pad_or_truncate(spec)
        return torch.tensor(spec, dtype=torch.float32).unsqueeze(0), torch.tensor(
            self.labels[idx], dtype=torch.float32
        )

    def _pad_or_truncate(self, spec: np.ndarray) -> np.ndarray:
        if spec.shape[1] < FIXED_FRAMES:
            pad_width = FIXED_FRAMES - spec.shape[1]
            spec = np.pad(spec, ((0, 0), (0, pad_width)), mode="constant")
        else:
            spec = spec[:, :FIXED_FRAMES]
        return spec


class DistressCNN(nn.Module):
    """Small CNN over log-mel spectrograms. Deliberately simple to start —
    this is a baseline your team should iterate on, not a final architecture."""

    def __init__(self):
        super().__init__()
        self.conv = nn.Sequential(
            nn.Conv2d(1, 16, kernel_size=3, padding=1),
            nn.ReLU(),
            nn.MaxPool2d(2),
            nn.Conv2d(16, 32, kernel_size=3, padding=1),
            nn.ReLU(),
            nn.MaxPool2d(2),
        )
        self.classifier = nn.Sequential(
            nn.AdaptiveAvgPool2d((4, 4)),
            nn.Flatten(),
            nn.Linear(32 * 4 * 4, 64),
            nn.ReLU(),
            nn.Linear(64, 1),
        )

    def forward(self, x):
        x = self.conv(x)
        return self.classifier(x).squeeze(1)  # raw logit; apply sigmoid outside


def train():
    dataset = ScreamDataset(
        positive_glob=os.path.join(DATA_DIR, "**", "*scream*", "*.wav"),
        negative_glob=os.path.join(DATA_DIR, "**", "*not_scream*", "*.wav"),
    )
    if len(dataset) == 0:
        raise RuntimeError(
            "No audio files found. Run download_datasets.py first and check "
            "that the glob patterns above match the dataset's actual folder names."
        )

    val_size = max(1, int(0.2 * len(dataset)))
    train_size = len(dataset) - val_size
    train_ds, val_ds = random_split(dataset, [train_size, val_size])

    train_loader = DataLoader(train_ds, batch_size=16, shuffle=True)
    val_loader = DataLoader(val_ds, batch_size=16)

    model = DistressCNN()
    optimizer = torch.optim.Adam(model.parameters(), lr=1e-3)
    criterion = nn.BCEWithLogitsLoss()

    epochs = 15
    for epoch in range(epochs):
        model.train()
        total_loss = 0.0
        for features, labels in train_loader:
            optimizer.zero_grad()
            logits = model(features)
            loss = criterion(logits, labels)
            loss.backward()
            optimizer.step()
            total_loss += loss.item()
        print(f"Epoch {epoch + 1}/{epochs} — train loss: {total_loss / len(train_loader):.4f}")

    # Validation report
    model.eval()
    all_preds, all_labels = [], []
    with torch.no_grad():
        for features, labels in val_loader:
            logits = model(features)
            preds = (torch.sigmoid(logits) > 0.5).float()
            all_preds.extend(preds.tolist())
            all_labels.extend(labels.tolist())

    print("\nValidation report:")
    print(classification_report(all_labels, all_preds, target_names=["not_scream", "scream"]))

    os.makedirs(os.path.dirname(MODEL_OUT), exist_ok=True)
    torch.save(model, MODEL_OUT)
    print(f"\nModel saved to {MODEL_OUT}")
    print("Set PLACEHOLDER = False in app/main.py to use it for inference.")


if __name__ == "__main__":
    train()
