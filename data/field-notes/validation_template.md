# Validation sheet (ground truth checks)

Date: ____  Phones: A = Pixel 7 Pro (Android 16), B = Pixel 7 Pro (Android 16)

## 1. Wi-Fi RSSI: app vs Android settings

| Spot | Time | App RSSI (dBm) | Settings RSSI (dBm) | Difference |
|---|---|---|---|---|
| 1 | | | | |
| 2 | | | | |
| 3 | | | | |

## 2. Download: phone CSV vs server log

| Probe time (CSV) | dl_bytes (CSV) | dl_total_ms (CSV) | bytes_sent (server) | server time | Match? |
|---|---|---|---|---|---|
| | | | | | |

## 3. Network events

| Action | Expected row | Seen in CSV? |
|---|---|---|
| Wi-Fi off | EVENT NET_LOST | |
| Wi-Fi on | EVENT NET_AVAILABLE | |
| Wrong role (data on while in Wi-Fi role) | ⚠ warning on screen | |
