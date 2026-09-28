"""
Shared audio feature extraction, used identically at training time and at
inference time so the model always sees features in the same shape/scale.
"""
 
import io
 
import numpy as np
import librosa
 
SAMPLE_RATE = 22050
CLIP_SECONDS = 4.0
FIXED_FRAMES = 173
MIN_SECONDS = 0.5
 
 
def extract_mfcc_features(audio_path: str, n_mfcc: int = 40, duration: float = 4.0) -> np.ndarray:
    """
    Loads an audio clip and returns a fixed-length MFCC feature vector
    (mean + std per coefficient, so length stays constant regardless of clip length).
    """
    y, sr = librosa.load(audio_path, duration=duration, sr=22050)
    mfcc = librosa.feature.mfcc(y=y, sr=sr, n_mfcc=n_mfcc)
    mean = np.mean(mfcc, axis=1)
    std = np.std(mfcc, axis=1)
    return np.concatenate([mean, std])  # shape: (2 * n_mfcc,)
 
 
def mel_from_waveform(y: np.ndarray, n_mels: int = 128) -> np.ndarray:
    """Waveform (22050 Hz mono) -> log-mel spectrogram. The single place this maths lives."""
    mel = librosa.feature.melspectrogram(y=y, sr=SAMPLE_RATE, n_mels=n_mels)
    return librosa.power_to_db(mel, ref=np.max)
 
 
def extract_mel_spectrogram(audio_path: str, duration: float = 4.0, n_mels: int = 128) -> np.ndarray:
    """Returns a log-mel spectrogram from a file path (used at training time)."""
    y, _ = librosa.load(audio_path, duration=duration, sr=SAMPLE_RATE)
    return mel_from_waveform(y, n_mels)  # shape: (n_mels, time_frames)
 
 
def spectrogram_from_bytes(data: bytes) -> np.ndarray:
    """
    Uploaded audio bytes (WAV/FLAC/OGG) -> (128, 173) spectrogram, ready for the CNN.
    Same steps as training: resample to 22050 Hz mono, first 4 s, log-mel, pad/truncate.
    Raises ValueError if the audio can't be decoded or is too short to be meaningful.
    """
    try:
        y, _ = librosa.load(io.BytesIO(data), sr=SAMPLE_RATE, duration=CLIP_SECONDS)
    except Exception as exc:
        raise ValueError(f"Could not decode audio (send 16-bit PCM WAV): {exc}") from exc
    if y.size < int(MIN_SECONDS * SAMPLE_RATE):
        raise ValueError(f"Audio too short (need at least {MIN_SECONDS}s)")
    return pad_or_truncate_spectrogram(mel_from_waveform(y), FIXED_FRAMES)
 
 
def pad_or_truncate_spectrogram(spec: np.ndarray, fixed_frames: int = 173) -> np.ndarray:
    """
    Pads or truncates a spectrogram's time axis to a fixed length, so every
    sample fed to the CNN has the same shape regardless of clip length.
    Used identically at training time (train_distress_model.py) and at
    inference time (app/main.py) — the two MUST match or the trained model's
    weights won't line up with what it's being asked to classify.
    """
    if spec.shape[1] < fixed_frames:
        pad_width = fixed_frames - spec.shape[1]
        spec = np.pad(spec, ((0, 0), (0, pad_width)), mode="constant")
    else:
        spec = spec[:, :fixed_frames]
    return spec