"""Unified CLI: pick a source, run the detector, optionally publish to viewer."""
import argparse
import json
import sys

from .core.alert import RescueAlert, UdpPublisher
from .core.detector import Detector


def _make_source(name: str, args: argparse.Namespace):
    if name == "esp32_serial":
        from .sources.esp32_serial import Esp32SerialSource
        return Esp32SerialSource(port=args.port, baud=args.baud)
    if name == "esp32_udp":
        from .sources.esp32_udp import Esp32UdpSource
        return Esp32UdpSource(port=args.udp_port)
    if name == "rpi":
        from .sources.rpi_nexmon import RpiNexmonSource
        return RpiNexmonSource(port=args.udp_port, n_subcarriers=args.subcarriers)
    if name == "laptop":
        from .sources.laptop_rssi import LaptopRssiSource
        return LaptopRssiSource(iface=args.iface, target_bssid=args.bssid)
    raise SystemExit(f"unknown source: {name}")


def main(argv: list[str] | None = None) -> int:
    p = argparse.ArgumentParser(prog="ruview")
    p.add_argument("--source", required=True,
                   choices=["esp32_serial", "esp32_udp", "rpi", "laptop"])
    p.add_argument("--mode", choices=["motion", "breathing", "both"], default="both")
    p.add_argument("--fs", type=float, default=20.0, help="sample rate (Hz)")
    p.add_argument("--port", default="/dev/ttyUSB0", help="ESP32 serial port")
    p.add_argument("--baud", type=int, default=921600)
    p.add_argument("--udp-port", type=int, default=5006)
    p.add_argument("--subcarriers", type=int, default=256, help="Nexmon: # subcarriers")
    p.add_argument("--iface", default="wlan0mon", help="laptop monitor iface")
    p.add_argument("--bssid", default=None, help="laptop: filter on a BSSID")
    p.add_argument("--publish", action="store_true",
                   help="broadcast detections to UDP 5005 (ruview-viewer)")
    p.add_argument("--viewer-host", default="255.255.255.255")
    args = p.parse_args(argv)

    src = _make_source(args.source, args)
    det = Detector(fs=args.fs)
    alert = RescueAlert()
    pub = UdpPublisher(host=args.viewer_host) if args.publish else None

    try:
        for d in det.process(src.iter_frames()):
            payload = alert.update(d)
            if args.mode == "motion" and not d.motion and not payload.get("alert", "").startswith("BREATH"):
                continue
            if args.mode == "breathing" and not d.breathing and payload["alert"] != "BREATHING_DETECTED":
                continue
            print(json.dumps(payload))
            sys.stdout.flush()
            if pub:
                pub.send(payload)
    except KeyboardInterrupt:
        pass
    finally:
        src.close()
        if pub:
            pub.close()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
