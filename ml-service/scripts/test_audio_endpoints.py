"""
Smoke test for the real audio pipeline. With the ML service running:
 
    python scripts/test_audio_endpoints.py path/to/clip.wav
 
Sends the same clip to both endpoints and prints the predictions.
"""
import sys
 
import requests
 
BASE = "http://127.0.0.1:8000"
 
if len(sys.argv) != 2:
    sys.exit("usage: python scripts/test_audio_endpoints.py clip.wav")
 
for endpoint in ("/predict/distress/audio", "/predict/tone/audio"):
    with open(sys.argv[1], "rb") as f:
        r = requests.post(BASE + endpoint, files={"file": ("clip.wav", f, "audio/wav")})
    print(endpoint, r.status_code, r.json())