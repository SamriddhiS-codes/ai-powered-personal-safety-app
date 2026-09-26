"""
FastAPI ML service — internal only, called by the Spring Boot backend.

Two models live here:
  1. Distress-sound detector  (CNN over spectrograms — Kaggle scream dataset)
  2. Voice-tone confirmation  (calm vs. frightened — RAVDESS / CREMA-D)

Both endpoints below currently return a placeholder heuristic so the rest of
the system (backend, frontend, tests) can be built and demoed end-to-end
before the trained models are ready. Swap `PLACEHOLDER = True` to False once
model artifacts exist at the paths below (see scripts/train_distress_model.py).
"""

from fastapi import FastAPI
from pydantic import BaseModel, Field
from typing import List
import numpy as np

app = FastAPI(title="Safety App ML Service", version="0.1.0")

PLACEHOLDER = True
DISTRESS_MODEL_PATH = "models/distress_model.pt"
TONE_MODEL_PATH = "models/tone_model.pt"

_distress_model = None
_tone_model = None


def _load_models_if_available():
    """Lazily loads trained models if PLACEHOLDER is False and files exist."""
    global _distress_model, _tone_model
    if PLACEHOLDER:
        return
    import torch
    import os
    if os.path.exists(DISTRESS_MODEL_PATH):
        _distress_model = torch.load(DISTRESS_MODEL_PATH)
        _distress_model.eval()
    if os.path.exists(TONE_MODEL_PATH):
        _tone_model = torch.load(TONE_MODEL_PATH)
        _tone_model.eval()


@app.on_event("startup")
def startup():
    _load_models_if_available()


class DistressRequest(BaseModel):
    audio_features: List[float] = Field(..., description="Precomputed MFCC/spectrogram feature vector")


class DistressResponse(BaseModel):
    is_distress: bool
    confidence: float


class ToneRequest(BaseModel):
    audio_features: List[float]


class ToneResponse(BaseModel):
    tone: str  # "calm" | "frightened"
    confidence: float


@app.get("/health")
def health():
    return {"status": "ok", "placeholder_mode": PLACEHOLDER}


@app.post("/predict/distress", response_model=DistressResponse)
def predict_distress(req: DistressRequest):
    if not req.audio_features:
        return DistressResponse(is_distress=False, confidence=0.0)

    if PLACEHOLDER or _distress_model is None:
        # Naive placeholder: treat higher average energy as more distress-like.
        # This exists ONLY so the pipeline is runnable before training finishes —
        # replace with a real forward pass once the CNN is trained (see
        # scripts/train_distress_model.py).
        score = float(np.clip(np.mean(np.abs(req.audio_features)), 0.0, 1.0))
        return DistressResponse(is_distress=score > 0.6, confidence=score)

    import torch
    with torch.no_grad():
        tensor = torch.tensor(req.audio_features, dtype=torch.float32).unsqueeze(0)
        output = _distress_model(tensor)
        confidence = float(torch.sigmoid(output).item())
        return DistressResponse(is_distress=confidence > 0.6, confidence=confidence)


@app.post("/predict/tone", response_model=ToneResponse)
def predict_tone(req: ToneRequest):
    if not req.audio_features:
        return ToneResponse(tone="calm", confidence=0.0)

    if PLACEHOLDER or _tone_model is None:
        score = float(np.clip(np.mean(np.abs(req.audio_features)), 0.0, 1.0))
        tone = "frightened" if score > 0.6 else "calm"
        return ToneResponse(tone=tone, confidence=score)

    import torch
    with torch.no_grad():
        tensor = torch.tensor(req.audio_features, dtype=torch.float32).unsqueeze(0)
        output = _tone_model(tensor)
        confidence = float(torch.sigmoid(output).item())
        tone = "frightened" if confidence > 0.6 else "calm"
        return ToneResponse(tone=tone, confidence=confidence)
