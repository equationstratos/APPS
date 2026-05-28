"""Detector tests using deterministic synthetic CSI sequences.

Note: these are *unit tests* exercising the algorithmic pipeline with known
inputs (sine wave at 0.25 Hz mimicking breathing). They are not a substitute
for validation against real-world captures.
"""
import numpy as np
import pytest

from ruview.core.csi_frame import CsiFrame
from ruview.core.detector import Detector


def _make_stream(fs: float, duration_s: float, breathing_hz: float | None,
                 motion_burst: tuple[float, float] | None = None):
    n = int(fs * duration_s)
    t = np.arange(n) / fs
    K = 32
    base = 50.0 + np.random.default_rng(0).standard_normal((n, K)) * 0.05
    if breathing_hz is not None:
        base[:, K // 2] += 0.5 * np.sin(2 * np.pi * breathing_hz * t)
    if motion_burst is not None:
        a, b = motion_burst
        ia, ib = int(a * fs), int(b * fs)
        base[ia:ib] += np.random.default_rng(1).standard_normal((ib - ia, K)) * 5.0
    for i in range(n):
        yield CsiFrame(timestamp=t[i], amplitudes=base[i].copy(),
                       phases=np.zeros(K))


def test_breathing_detected_at_0_25hz():
    det = Detector(fs=20.0, breathing_window_s=20.0, breathing_pmr=3.0)
    last = None
    for d in det.process(_make_stream(20.0, 30.0, breathing_hz=0.25)):
        last = d
    assert last is not None
    assert last.breathing is True
    assert last.breathing_rate_hz is not None
    assert 0.20 <= last.breathing_rate_hz <= 0.30


def test_no_breathing_when_signal_is_flat():
    det = Detector(fs=20.0, breathing_window_s=20.0)
    last = None
    for d in det.process(_make_stream(20.0, 30.0, breathing_hz=None)):
        last = d
    assert last is not None
    assert last.breathing is False


def test_motion_flagged_during_burst():
    det = Detector(fs=20.0, motion_window_s=1.0, motion_k=2.0)
    motion_seen = False
    for d in det.process(_make_stream(20.0, 10.0, breathing_hz=None,
                                      motion_burst=(4.0, 6.0))):
        if 4.5 <= d.timestamp <= 6.5 and d.motion:
            motion_seen = True
    assert motion_seen


if __name__ == "__main__":
    pytest.main([__file__, "-v"])
