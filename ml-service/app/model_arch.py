"""
Shared model architecture — imported by both the training script
(scripts/train_distress_model.py) and the live inference app (app/main.py).
Keeping the class definition in one place avoids a common PyTorch pitfall:
a model saved with torch.save(model, path) can fail to load from a different
script/module than the one that trained it, because the pickle format needs
to resolve the exact class definition it came from.
"""
 
import torch.nn as nn
 
 
class DistressCNN(nn.Module):
    """Small CNN over log-mel spectrograms. A baseline to iterate on, not final."""
 
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