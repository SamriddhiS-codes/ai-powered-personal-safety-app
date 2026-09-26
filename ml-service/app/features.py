"""
Shared audio feature extraction, used identically at training time and at
inference time so the model always sees features in the same shape/scale.
"""

import numpy as np
import librosa


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


def extract_mel_spectrogram(audio_path: str, duration: float = 4.0, n_mels: int = 128) -> np.ndarray:
    """Returns a log-mel spectrogram — used for the CNN-based distress detector."""
    y, sr = librosa.load(audio_path, duration=duration, sr=22050)
    mel = librosa.feature.melspectrogram(y=y, sr=sr, n_mels=n_mels)
    log_mel = librosa.power_to_db(mel, ref=np.max)
    return log_mel  # shape: (n_mels, time_frames)
