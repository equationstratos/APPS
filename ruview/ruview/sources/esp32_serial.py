"""Read CSI from an ESP32 flashed with ESP32-CSI-Tool.

ESP32-CSI-Tool emits CSV lines starting with 'CSI_DATA,' over USB serial.
Format (relevant fields): CSI_DATA,role,mac,rssi,...,len,first_word,data
where `data` is a bracketed list of int8 I,Q pairs.

Reference: https://github.com/StevenMHernandez/ESP32-CSI-Tool
"""
from typing import Iterator
import time
import numpy as np

try:
    import serial  # type: ignore
except ImportError:
    serial = None

from .base import CsiSource
from ..core.csi_frame import CsiFrame


class Esp32SerialSource(CsiSource):
    def __init__(self, port: str = "/dev/ttyUSB0", baud: int = 921600):
        if serial is None:
            raise RuntimeError("pyserial not installed (pip install pyserial)")
        self._ser = serial.Serial(port, baud, timeout=1.0)

    def iter_frames(self) -> Iterator[CsiFrame]:
        while True:
            raw = self._ser.readline()
            if not raw:
                continue
            try:
                line = raw.decode("utf-8", errors="ignore").strip()
            except Exception:
                continue
            if not line.startswith("CSI_DATA,"):
                continue
            frame = self._parse_line(line)
            if frame is not None:
                yield frame

    @staticmethod
    def _parse_line(line: str) -> CsiFrame | None:
        try:
            head, data = line.rsplit("[", 1)
            data = data.rstrip("]").strip()
            fields = head.split(",")
            rssi = float(fields[3])
            iq = [int(x) for x in data.split() if x]
            if len(iq) < 2:
                return None
            iq = np.array(iq[: (len(iq) // 2) * 2], dtype=np.int8).reshape(-1, 2)
            complex_csi = iq[:, 0].astype(np.float32) + 1j * iq[:, 1].astype(np.float32)
            amp = np.abs(complex_csi)
            ph = np.angle(complex_csi)
            return CsiFrame(time.time(), amp, ph, rssi)
        except Exception:
            return None

    def close(self) -> None:
        self._ser.close()
