# ruview — Android companion APKs

Three independent Android Studio projects, one method each. Open each
sub-folder as a separate project in Android Studio (Hedgehog or newer), then
**Build → Build APK**.

| Folder | APK | Method | Min SDK | Detects |
|---|---|---|---|---|
| `ruview-viewer/` | ruview-viewer.apk | UDP/JSON receiver from ESP32/RPi sensor | 26 (8.0) | Presence + breathing (computed by sensor) |
| `ruview-rtt/` | ruview-rtt.apk | `WifiRttManager` (802.11mc FTM) | 28 (9.0) | Motion (range variance) |
| `ruview-uwb/` | ruview-uwb.apk | `android.uwb` ranging | 34 (14) | Presence + motion |

## Pairing with the Python sensor

For `ruview-viewer`, run the sensor with `--publish` so JSON detections are
broadcast on UDP 5005:

```
python -m ruview --source esp32_udp --publish
```

Then launch ruview-viewer on a phone on the same LAN. It binds UDP 5005 and
renders the JSON `RescueAlert` payloads in real time.

## JSON contract (consumed by ruview-viewer)

```json
{
  "ts": 1722000000.123,
  "motion": false,
  "motion_score": 0.0042,
  "breathing": true,
  "rate_hz": 0.27,
  "rate_cpm": 16.2,
  "confidence": 0.84,
  "alert": "BREATHING_DETECTED"
}
```

Source of truth: `ruview/core/alert.py`.
