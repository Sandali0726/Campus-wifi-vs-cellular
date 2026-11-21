# [app] Signal strength, network type and location readers

Read signal strength and network type (proposal: WifiManager RSSI, TelephonyManager signal + type).

## Tasks
- [ ] Wi-Fi: RSSI via `NetworkCallback` with `FLAG_INCLUDE_LOCATION_INFO` → `WifiInfo` from `NetworkCapabilities.transportInfo` (Android 12+), fallback to `WifiManager`; also link speed + frequency
- [ ] Cellular: `TelephonyManager.signalStrength.cellSignalStrengths` → dBm, level 0–4, RSRP/RSRQ/SINR for LTE/NR
- [ ] Network type: `dataNetworkType` + `TelephonyDisplayInfo` override (to detect 5G NSA) 
- [ ] Active transport (WIFI / CELLULAR / NONE) from `ConnectivityManager`
- [ ] Location: `LocationManager` updates from GPS + NETWORK providers; store last fix (lat, lon, accuracy), rounded to 4 decimals (coarse)

## Done when
- Each CSV row has signal, network type, and location columns filled on the right phone.
