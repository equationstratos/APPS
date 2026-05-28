"""Presence/motion/breathing detector working on a stream of CsiFrame."""
from collections import deque
from dataclasses import dataclass
from typing import Deque, Iterable, Iterator, Optional
import numpy as np

from .csi_frame import CsiFrame
from .filters import bandpass, hampel


@dataclass
class Detection:
    timestamp: float
    motion: bool
    motion_score: float
    breathing: bool
    breathing_rate_hz: Optional[float]  # None if unstable / no breathing
    confidence: float

    def to_dict(self) -> dict:
        return {
            "ts": self.timestamp,
            "motion": self.motion,
            "motion_score": float(self.motion_score),
            "breathing": self.breathing,
            "rate_hz": None if self.breathing_rate_hz is None else float(self.breathing_rate_hz),
            "rate_cpm": None if self.breathing_rate_hz is None else float(self.breathing_rate_hz * 60),
            "confidence": float(self.confidence),
        }


class Detector:
    """Sliding-window detector.

    - motion_score = mean variance of amplitude across subcarriers over a short window
    - breathing: pick the strongest amplitude time-series across subcarriers,
      bandpass into respiration band, FFT, peak-to-mean ratio > threshold ⇒ breathing.
    """

    def __init__(
        self,
        fs: float = 20.0,
        motion_window_s: float = 1.0,
        breathing_window_s: float = 20.0,
        motion_k: float = 3.0,
        breathing_pmr: float = 4.0,
    ):
        self.fs = fs
        self.motion_n = max(2, int(motion_window_s * fs))
        self.breathing_n = max(int(2 * fs), int(breathing_window_s * fs))
        self.motion_k = motion_k
        self.breathing_pmr = breathing_pmr
        self._buf: Deque[CsiFrame] = deque(maxlen=self.breathing_n)
        self._motion_history: Deque[float] = deque(maxlen=200)

    def push(self, frame: CsiFrame) -> Optional[Detection]:
        self._buf.append(frame)
        if len(self._buf) < self.motion_n:
            return None
        return self._evaluate()

    def process(self, frames: Iterable[CsiFrame]) -> Iterator[Detection]:
        for f in frames:
            det = self.push(f)
            if det is not None:
                yield det

    def _evaluate(self) -> Detection:
        recent = list(self._buf)[-self.motion_n:]
        amp = np.stack([f.amplitudes for f in recent], axis=0)  # (T, K)
        var_per_sc = np.var(amp, axis=0)
        motion_score = float(np.mean(var_per_sc))
        self._motion_history.append(motion_score)
        med = float(np.median(self._motion_history)) or 1e-9
        motion = motion_score > self.motion_k * med

        breathing = False
        rate_hz: Optional[float] = None
        confidence = 0.0
        if len(self._buf) >= self.breathing_n:
            rate_hz, confidence = self._estimate_breathing()
            breathing = rate_hz is not None

        ts = recent[-1].timestamp
        return Detection(ts, motion, motion_score, breathing, rate_hz, confidence)

    def _estimate_breathing(self) -> tuple[Optional[float], float]:
        amp = np.stack([f.amplitudes for f in self._buf], axis=0)  # (T, K)
        # Pick subcarrier with the highest variance (most informative).
        var_sc = np.var(amp, axis=0)
        sc = int(np.argmax(var_sc))
        sig = amp[:, sc]
        sig = hampel(sig, window=7, n_sigma=3.0)
        sig = sig - np.mean(sig)
        try:
            sig = bandpass(sig, self.fs, low=0.1, high=0.5, order=4)
        except ValueError:
            return None, 0.0
        # FFT.
        spec = np.abs(np.fft.rfft(sig * np.hanning(len(sig))))
        freqs = np.fft.rfftfreq(len(sig), d=1.0 / self.fs)
        band = (freqs >= 0.1) & (freqs <= 0.5)
        if not np.any(band):
            return None, 0.0
        spec_b = spec[band]
        freqs_b = freqs[band]
        peak_idx = int(np.argmax(spec_b))
        peak = spec_b[peak_idx]
        mean = float(np.mean(spec_b)) or 1e-9
        pmr = peak / mean  # peak-to-mean ratio
        if pmr < self.breathing_pmr:
            return None, float(pmr / self.breathing_pmr)
        return float(freqs_b[peak_idx]), float(min(1.0, pmr / (self.breathing_pmr * 2)))
