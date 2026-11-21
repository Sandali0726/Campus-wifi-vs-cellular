# NetMeasure: Android measurement app

A Kotlin app (minSdk 29, targetSdk 36) that logs Wi-Fi/cellular signal and network performance along
the route. It was tested on **Google Pixel 7 Pro, Android 16**.

## Build and install

1. Open the `android-app/` folder in Android Studio and let Gradle sync.
2. Connect the phone with USB debugging on, then **Run**. From the command line:
   ```bash
   ./gradlew installDebug
   ```
3. Unit tests:
   ```bash
   ./gradlew testDebugUnitTest
   ```

## Use on a walk

1. Set **Phone** (A/B), **Network role** (Wi-Fi/Cellular), **Time slot** and **Walk number**, and enter
   the ngrok **server URL**. All of these are remembered between runs.
2. Configure the radios: the Wi-Fi-role phone has **Wi-Fi ON, mobile data OFF**; the cellular-role
   phone has **Wi-Fi OFF, mobile data ON**. If the active network doesn't match the role, the screen shows a ⚠ warning.
3. Press **Start** and grant permissions: location "while using the app", phone, notifications.
4. At each marked point, tap **P1…P5** on **both phones at the same moment** and wait until the
   counter reaches 40 s ("✔ walk on").
5. Press **Stop** at the end. Use **Share last CSV**, or pull all runs:
   ```bash
   adb pull /sdcard/Android/data/lk.ac.mrt.netmeasure/files/runs/ data/raw/
   ```

## How it works

```
MainActivity ──intents──► MeasurementService (foreground, type=location, wake lock)
   ▲                         │ every 10 s (fixed rate) + on waypoint tap
   │ StateFlow               ├─ WifiSignalReader   NetworkCallback(FLAG_INCLUDE_LOCATION_INFO) → WifiInfo
   │ (live status)           ├─ CellSignalReader   TelephonyManager.signalStrength, dataNetworkType,
   │                         │                     TelephonyCallback.DisplayInfoListener (5G NSA)
   │                         ├─ LocationTracker    LocationManager (GPS + network), rounded to 4 dp
   │                         ├─ NetworkProbe       HttpURLConnection: 3× /ping + 1× /file (256 000 B)
   │                         ├─ NetworkMonitor     registerDefaultNetworkCallback → drop/switch events
   └─────────────────────────┴─ CsvLogger          one CSV per run, flushed after every row
```

| File | Responsibility | Issue |
|---|---|---|
| `MainActivity.kt`, `RunConfig.kt`, `res/` | Screen, settings, runtime permissions, share | 3 |
| `MeasurementService.kt`, `MeasurementState.kt` | Foreground service, 10 s timer, waypoint taps, skip logic | 4 |
| `WifiSignalReader.kt`, `CellSignalReader.kt`, `LocationTracker.kt` | Signal, network type, location | 5 |
| `NetworkProbe.kt` | Latency + download probe | 6 |
| `NetworkMonitor.kt` | Network lost/available/switch events | 7 |
| `MeasurementRow.kt`, `CsvLogger.kt` | CSV format, file storage | 8 |

**Skip rule** (from the proposal): if a probe is still running when the next one is due (timer or
tap), the new probe is not started. Instead a `PROBE` row with `status=SKIPPED` is written.

**Platform restrictions handled:**
- Android 14+ requires location permission **before** `startForeground(type=location)`, so the
  activity requests permissions first.
- Android 12+ hides Wi-Fi details in `WifiManager.getConnectionInfo()`, so `WifiInfo` comes from
  `NetworkCapabilities` with `FLAG_INCLUDE_LOCATION_INFO`.
- `READ_PHONE_STATE` is needed for `dataNetworkType`.
- A partial wake lock and the foreground service keep the 10 s timer running under Doze.
- The screen stays on while the activity is open.
- BSSIDs are stored only as an 8-character SHA-256 prefix (privacy rule).

See [`../docs/DATA_DICTIONARY.md`](../docs/DATA_DICTIONARY.md) for every CSV column.
