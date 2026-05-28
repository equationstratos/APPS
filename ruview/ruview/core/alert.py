"""Safe & rescue alert logic and JSON wire format.

The JSON shape produced here is the contract consumed by ruview-viewer (Android).
"""
import json
import socket
from dataclasses import dataclass
from typing import Optional

from .detector import Detection


@dataclass
class RescueAlert:
    """Sticky alert: 'breathing detected behind obstacle'."""
    breathing_consecutive: int = 0
    threshold: int = 5  # need N consecutive breathing detections

    def update(self, det: Detection) -> Optional[dict]:
        if det.breathing:
            self.breathing_consecutive += 1
        else:
            self.breathing_consecutive = 0
        active = self.breathing_consecutive >= self.threshold
        payload = det.to_dict()
        payload["alert"] = "BREATHING_DETECTED" if active else (
            "MOTION" if det.motion else "CLEAR"
        )
        return payload


class UdpPublisher:
    """Broadcast detections as JSON-lines over UDP. Consumed by ruview-viewer."""

    def __init__(self, host: str = "255.255.255.255", port: int = 5005):
        self.addr = (host, port)
        self._sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        self._sock.setsockopt(socket.SOL_SOCKET, socket.SO_BROADCAST, 1)

    def send(self, payload: dict) -> None:
        self._sock.sendto(json.dumps(payload).encode("utf-8"), self.addr)

    def close(self) -> None:
        self._sock.close()
