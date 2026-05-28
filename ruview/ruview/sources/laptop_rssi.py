"""Degraded laptop variant: RSSI-based motion sensing on standard Wi-Fi cards.

No CSI here — we sniff probe/data frames in monitor mode and use RSSI variance
as a 1-subcarrier proxy. Detects motion only. Not breathing. Documented in README.

Requires: card supporting monitor mode, `iw dev <iface> set type monitor` done
beforehand, root (or CAP_NET_RAW).
"""
import time
from collections import deque
from typing import Deque, Iterator

import numpy as np

try:
    from scapy.all import sniff, RadioTap, Dot11  # type: ignore
except ImportError:
    sniff = None

from .base import CsiSource
from ..core.csi_frame import CsiFrame


class LaptopRssiSource(CsiSource):
    def __init__(self, iface: str = "wlan0mon", target_bssid: str | None = None,
                 fs: float = 10.0):
        if sniff is None:
            raise RuntimeError("scapy not installed (pip install scapy)")
        self.iface = iface
        self.target_bssid = target_bssid.lower() if target_bssid else None
        self.fs = fs
        self._queue: Deque[CsiFrame] = deque(maxlen=1000)

    def _on_pkt(self, pkt) -> None:  # noqa: ANN001
        if not pkt.haslayer(RadioTap):
            return
        if self.target_bssid and pkt.haslayer(Dot11):
            addr2 = (pkt[Dot11].addr2 or "").lower()
            if addr2 != self.target_bssid:
                return
        rssi = getattr(pkt[RadioTap], "dBm_AntSignal", None)
        if rssi is None:
            return
        # Single-subcarrier "pseudo-CSI": amplitude = linearized RSSI proxy.
        amp = np.array([10 ** (float(rssi) / 20.0)], dtype=np.float32)
        self._queue.append(CsiFrame(time.time(), amp, np.zeros(1), float(rssi)))

    def iter_frames(self) -> Iterator[CsiFrame]:
        # Sniff in a tight generator: scapy's prn callback feeds the queue.
        stop = {"flag": False}

        def feeder():
            sniff(iface=self.iface, prn=self._on_pkt,
                  store=False, stop_filter=lambda _p: stop["flag"])

        import threading
        t = threading.Thread(target=feeder, daemon=True)
        t.start()
        try:
            while True:
                if self._queue:
                    yield self._queue.popleft()
                else:
                    time.sleep(1.0 / self.fs)
        finally:
            stop["flag"] = True
