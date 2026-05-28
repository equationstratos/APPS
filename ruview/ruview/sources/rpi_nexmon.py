"""Read CSI from a Raspberry Pi 3B+/4 patched with Nexmon_CSI.

Nexmon_CSI emits UDP packets (default destination port 5500) carrying raw
CSI as int16 I/Q pairs preceded by a small header. We parse the minimum
needed to extract the I/Q array.

Reference: https://github.com/seemoo-lab/nexmon_csi
"""
import socket
import struct
import time
from typing import Iterator

import numpy as np

from .base import CsiSource
from ..core.csi_frame import CsiFrame


class RpiNexmonSource(CsiSource):
    def __init__(self, port: int = 5500, n_subcarriers: int = 256):
        self._sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        self._sock.bind(("0.0.0.0", port))
        self.n_sc = n_subcarriers

    def iter_frames(self) -> Iterator[CsiFrame]:
        while True:
            data, _ = self._sock.recvfrom(8192)
            frame = self._parse(data)
            if frame is not None:
                yield frame

    def _parse(self, data: bytes) -> CsiFrame | None:
        # Nexmon CSI UDP packet: 18-byte header then int16 I/Q pairs.
        if len(data) < 18 + 4 * self.n_sc:
            return None
        try:
            rssi = struct.unpack_from("<b", data, 2)[0]
            payload = np.frombuffer(data, dtype=np.int16, offset=18,
                                    count=2 * self.n_sc)
            iq = payload.reshape(-1, 2).astype(np.float32)
            csi = iq[:, 0] + 1j * iq[:, 1]
            return CsiFrame(time.time(), np.abs(csi), np.angle(csi), float(rssi))
        except Exception:
            return None

    def close(self) -> None:
        self._sock.close()
