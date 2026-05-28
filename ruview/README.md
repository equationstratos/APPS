# ruview — Wi-Fi presence & breathing sensing for safe-and-rescue

Detect a person's presence (and breathing rate) through walls using Wi-Fi
Channel State Information (CSI). Intended for **search & rescue** scenarios —
locating someone trapped behind debris or in a smoke-filled room — not for
surveillance.

## Variants

| Variant | Hardware | Detects |
|---|---|---|
| **ESP32** (serial or UDP) | 2× ESP32 boards (TX + RX) with [ESP32-CSI-Tool](https://github.com/StevenMHernandez/ESP32-CSI-Tool) firmware | Presence + breathing |
| **Raspberry Pi** | RPi 3B+/4 with [Nexmon_CSI](https://github.com/seemoo-lab/nexmon_csi) | Presence + breathing |
| **Laptop (RSSI)** | Any Wi-Fi card in monitor mode | Motion only (degraded — see notes) |
| **Android — viewer** | Phone on same LAN as ESP32/RPi sensor | Displays sensor output |
| **Android — RTT** | Phone (Android 9+) + FTM-capable AP | Motion (range variance) |
| **Android — UWB** | Phone (Android 14+, UWB hw) + peer UWB device | Presence + motion |

## Install

```
pip install -r requirements.txt
```

## Run

```
# ESP32 receiver tethered via USB:
python -m ruview --source esp32_serial --port /dev/ttyUSB0 --publish

# ESP32 receiver streaming UDP (recommended for Android viewer):
python -m ruview --source esp32_udp --publish

# Raspberry Pi (after running scripts/install_nexmon_rpi.sh on the Pi):
python -m ruview --source rpi --udp-port 5500 --publish

# Laptop RSSI fallback (motion only):
sudo iw dev wlan0 set type monitor
python -m ruview --source laptop --iface wlan0 --mode motion
```

`--publish` broadcasts JSON detections on UDP 5005 — that's the feed
`ruview-viewer.apk` listens to.

## Algorithm

- **Motion**: sliding variance of CSI amplitude across subcarriers; adaptive
  threshold = median × k (default k=3).
- **Breathing**: Hampel outlier removal → 0.1–0.5 Hz Butterworth band-pass →
  FFT on a 20 s window → dominant peak frequency; declared "breathing" when
  peak-to-mean ratio in the band exceeds 4.

Tuned in `ruview/core/detector.py`.

## JSON output

```json
{"ts": 1722000000.1, "motion": false, "motion_score": 0.004,
 "breathing": true, "rate_hz": 0.27, "rate_cpm": 16.2,
 "confidence": 0.84, "alert": "BREATHING_DETECTED"}
```

## Tests

```
python -m pytest tests/
```

## Honesty notes

- Most laptop Wi-Fi cards do **not** expose CSI. The `laptop` variant uses
  RSSI as a 1-subcarrier proxy — it can flag motion, not breathing. For real
  CSI on a laptop, use an Intel 5300 (mPCIe) or an Atheros ath9k USB dongle
  with the matching CSI tool.
- Breathing detection through thick walls/concrete is unreliable. The
  realistic rescue scenario is drywall / plasterboard / single-leaf
  partition, sensor 2–4 m from the person.
- This is research-grade code. Validate against your real environment
  before depending on it for life-safety decisions.

## Layout

```
ruview/         Python package (core algos + sources + CLI)
firmware/esp32/ ESP32 firmware notes (uses ESP32-CSI-Tool upstream)
scripts/        Nexmon_CSI installer for Raspberry Pi
tests/          Unit tests for the detector
android/        3 separate Android Studio projects, one APK each
```
