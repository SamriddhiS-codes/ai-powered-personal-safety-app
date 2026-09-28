
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
from app.model_arch import DistressCNN
 
app = FastAPI(title="Safety App ML Service", version="0.1.0")
 
PLACEHOLDER = False
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
        _distress_model = DistressCNN()
        _distress_model.load_state_dict(torch.load(DISTRESS_MODEL_PATH, map_location="cpu"))
        _distress_model.eval()
        print(f"Loaded trained distress model from {DISTRESS_MODEL_PATH}")
    if os.path.exists(TONE_MODEL_PATH):
        _tone_model = DistressCNN()
        _tone_model.load_state_dict(torch.load(TONE_MODEL_PATH, map_location="cpu"))
        _tone_model.eval()
        print(f"Loaded trained tone model from {TONE_MODEL_PATH}")
 
 
@app.on_event("startup")
def startup():
    _load_models_if_available()
 
 
class DistressRequest(BaseModel):
    audio_features: List[float] = Field(..., description="Flat placeholder features (legacy)")
    mel_spectrogram: List[List[float]] | None = Field(
        None, description="128 x 173 log-mel spectrogram, required for the real trained model"
    )
 
 
class DistressResponse(BaseModel):
    is_distress: bool
    confidence: float
 
 
class ToneRequest(BaseModel):
    audio_features: List[float] = Field(default_factory=list, description="Flat placeholder features (legacy)")
    mel_spectrogram: List[List[float]] | None = Field(
        None, description="128 x 173 log-mel spectrogram, required for the real trained model"
    )
 
 
class ToneResponse(BaseModel):
    tone: str  # "calm" | "frightened"
    confidence: float
 
 
@app.get("/health")
def health():
    return {"status": "ok", "placeholder_mode": PLACEHOLDER}
 
 
@app.post("/predict/distress", response_model=DistressResponse)
def predict_distress(req: DistressRequest):
    # Real model path: needs a properly shaped (128, 173) mel spectrogram,
    # matching exactly what the model was trained on (see
    # scripts/train_distress_model.py / app/features.py).
    if not PLACEHOLDER and _distress_model is not None and req.mel_spectrogram is not None:
        import torch
        spec = np.array(req.mel_spectrogram, dtype=np.float32)
        if spec.shape == (128, 173):
            with torch.no_grad():
                tensor = torch.tensor(spec).unsqueeze(0).unsqueeze(0)  # (1, 1, 128, 173)
                logit = _distress_model(tensor)
                confidence = float(torch.sigmoid(logit).item())
                return DistressResponse(is_distress=confidence > 0.6, confidence=confidence)
        # Wrong shape sent — fall through to the placeholder rather than crash,
        # since this usually means the caller hasn't wired real feature
        # extraction yet (see docs/API_CONTRACT.md).
        print(f"WARNING: expected mel_spectrogram shape (128, 173), got {spec.shape}. "
              f"Falling back to placeholder heuristic.")
 
    if not req.audio_features:
        return DistressResponse(is_distress=False, confidence=0.0)
 
    # Placeholder heuristic — used until real spectrogram features are wired
    # in end-to-end from the frontend/backend audio pipeline.
    score = float(np.clip(np.mean(np.abs(req.audio_features)), 0.0, 1.0))
    return DistressResponse(is_distress=score > 0.6, confidence=score)
 
 
@app.post("/predict/tone", response_model=ToneResponse)
def predict_tone(req: ToneRequest):
    # Real model path: same input contract as /predict/distress — a (128, 173)
    # log-mel spectrogram, built with the SAME preprocessing used in
    # scripts/train_tone_model.py. The model outputs P(frightened).
    if not PLACEHOLDER and _tone_model is not None and req.mel_spectrogram is not None:
        import torch
        spec = np.array(req.mel_spectrogram, dtype=np.float32)
        if spec.shape == (128, 173):
            with torch.no_grad():
                tensor = torch.tensor(spec).unsqueeze(0).unsqueeze(0)  # (1, 1, 128, 173)
                p_frightened = float(torch.sigmoid(_tone_model(tensor)).item())
            if p_frightened > 0.5:
                return ToneResponse(tone="frightened", confidence=p_frightened)
            return ToneResponse(tone="calm", confidence=1.0 - p_frightened)
        print(f"WARNING: expected mel_spectrogram shape (128, 173), got {spec.shape}. "
              f"Falling back to placeholder heuristic.")
 
    if not req.audio_features:
        return ToneResponse(tone="calm", confidence=0.0)
 
    # Placeholder heuristic — used until real spectrograms are sent end-to-end.
    score = float(np.clip(np.mean(np.abs(req.audio_features)), 0.0, 1.0))
    tone = "frightened" if score > 0.6 else "calm"
    return ToneResponse(tone=tone, confidence=score)