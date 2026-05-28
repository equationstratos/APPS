# ESP32 firmware — CSI sensor pair

`ruview` does not ship its own ESP32 firmware. Use the well-tested
[ESP32-CSI-Tool](https://github.com/StevenMHernandez/ESP32-CSI-Tool) with two
boards:

- **Active AP** sketch on board A → emits ping packets at 100 Hz.
- **Active STA** sketch on board B → receives, dumps CSI lines over USB at
  921600 baud, or forwards them as UDP datagrams (`active_sta_udp` variant).

## Wiring for the rescue setup

```
[ESP32 TX] - - - wall - - - [ESP32 RX] --USB or Wi-Fi--> host running ruview
```

Place TX and RX 2–4 m apart on opposite sides of the obstacle. The Fresnel
zone between them is where presence/breathing perturbations show up.

## Running with ruview

USB:
```
python -m ruview --source esp32_serial --port /dev/ttyUSB0 --publish
```

UDP (recommended when feeding ruview-viewer Android):
```
python -m ruview --source esp32_udp --udp-port 5006 --publish
```

## Recommended ESP32-CSI-Tool patch

In `active_sta/main/main.c`, raise the ping rate to ~100 Hz (default 10 Hz is
too slow for breathing detection — needs ≥4 Hz Nyquist over 0.5 Hz).
