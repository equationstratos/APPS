"""Signal filters used by the detector."""
import numpy as np
from scipy.signal import butter, sosfiltfilt


def hampel(x: np.ndarray, window: int = 7, n_sigma: float = 3.0) -> np.ndarray:
    """Remove impulsive outliers using a Hampel filter."""
    x = np.asarray(x, dtype=float).copy()
    k = window // 2
    for i in range(len(x)):
        lo, hi = max(0, i - k), min(len(x), i + k + 1)
        med = np.median(x[lo:hi])
        mad = np.median(np.abs(x[lo:hi] - med)) * 1.4826
        if mad > 0 and abs(x[i] - med) > n_sigma * mad:
            x[i] = med
    return x


def bandpass(x: np.ndarray, fs: float, low: float = 0.1, high: float = 0.5,
             order: int = 4) -> np.ndarray:
    """Zero-phase Butterworth band-pass. Defaults target adult breathing (6–30 cpm)."""
    nyq = fs / 2
    sos = butter(order, [low / nyq, high / nyq], btype="band", output="sos")
    return sosfiltfilt(sos, x)
