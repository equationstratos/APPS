"""Receive CSI lines over UDP from an ESP32 configured to broadcast.

Use this when the receiver ESP32 isn't tethered to the host. The ESP32
sketch forwards each CSI_DATA line as a UDP datagram.
"""
import socket
import time
from typing import Iterator

import numpy as np

from .base import CsiSource
from ..core.csi_frame import CsiFrame
from .esp32_serial import Esp32SerialSource  # reuse the line parser


class Esp32UdpSource(CsiSource):
    def __init__(self, bind: str = "0.0.0.0", port: int = 5006):
        self._sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        self._sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        self._sock.bind((bind, port))

    def iter_frames(self) -> Iterator[CsiFrame]:
        while True:
            data, _ = self._sock.recvfrom(4096)
            line = data.decode("utf-8", errors="ignore").strip()
            frame = Esp32SerialSource._parse_line(line)
            if frame is not None:
                yield frame

    def close(self) -> None:
        self._sock.close()
