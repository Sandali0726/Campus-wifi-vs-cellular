# CSV data dictionary

The app writes one file per phone per walk: `<phone>_<role>_<slot>_walk<n>_<yyyyMMdd-HHmmss>.csv`
(for example `A_WIFI_OFFPEAK_walk1_20261124-081502.csv`). Every file has the same header. An empty
cell means the value was not available.

## Row types

| `row_type` | Written when | Notes |
|---|---|---|
| `PROBE` | every 10 s (`trigger=TIMER`) and on each waypoint tap (`trigger=TAP`) | `status` = `OK`, `FAILED` or `SKIPPED` |
| `WAYPOINT` | a P1–P5 button is tapped | ground truth for position; `waypoint` = the point tapped |
| `EVENT` | a network or run event happens | the event name is in `event`; details in `error` |

Values of `event`: `RUN_START`, `RUN_STOP`, `NET_AVAILABLE`, `NET_LOST`, `TRANSPORT_CHANGE`
(e.g. `WIFI->CELL`), `VALIDATED` / `NOT_VALIDATED` (the network is up but has no working internet),
`WIFI_AP_CHANGE` (hand-over between access points, shown as hashes), `CELL_TYPE_CHANGE` (e.g. `LTE->NR_NSA`).

## Columns

| Column | Unit | Description |
|---|---|---|
| `timestamp_iso` | – | phone local time with offset, ms precision |
| `epoch_ms` | ms | Unix time (both phones use automatic network time) |
| `elapsed_ms` | ms | time since Start (monotonic clock) |
| `phone_id` | – | `A` or `B` (physical handset) |
| `net_role` | – | `WIFI` or `CELL`: the network this phone measures on this walk |
| `time_slot` | – | `OFFPEAK` (08:00–09:00) or `PEAK` (12:30–13:30) |
| `walk_id` | – | 1–3 within a time slot |
| `row_type`, `trigger` | – | see above |
| `waypoint` | – | last waypoint tapped (`P1`–`P5`; empty before the first tap) |
| `status` | – | probe result: `OK`, `FAILED` or `SKIPPED` |
| `error` | – | first failure (e.g. `ping2:SocketTimeoutException`, `download:size_120000`) or event detail |
| `event` | – | event name (EVENT rows only) |
| `active_transport` | – | the phone's current default network: `WIFI`, `CELL`, `NONE` |
| `net_type` | – | cellular data type: `LTE`, `LTE_A`, `NR_NSA`, `NR_SA`, `3G`, `2G`, `NONE` |
| `operator` | – | cellular operator name (e.g. Mobitel) |
| `wifi_rssi` | dBm | Wi-Fi received signal strength (empty when Wi-Fi is off) |
| `wifi_link_mbps` | Mbps | Wi-Fi PHY link rate |
| `wifi_freq_mhz` | MHz | channel frequency (≈2400 = 2.4 GHz, ≈5000 = 5 GHz) |
| `wifi_bssid_hash` | – | first 8 hex characters of the SHA-256 of the AP's BSSID |
| `cell_dbm` | dBm | primary cell signal strength (LTE RSRP when on LTE) |
| `cell_level` | 0–4 | Android's signal bars |
| `cell_rsrp`, `cell_rsrq`, `cell_sinr` | dBm, dB, dB | LTE (or NR) reference signal power / quality / SNR |
| `cell_nr_rsrp` | dBm | 5G NR SS-RSRP when a 5G cell is present |
| `ping1_ms`…`ping3_ms` | ms | the three `/ping` round-trip times (ping 1 may include connection set-up) |
| `latency_median_ms` | ms | median of the successful pings (**main latency metric**) |
| `latency_mean_ms` | ms | mean of the successful pings |
| `ttfb_ms` | ms | `/file` request start → first body byte |
| `dl_bytes` | B | bytes received (256 000 when the download is complete) |
| `dl_total_ms` | ms | `/file` request start → last byte |
| `dl_mbps` | Mbps | `dl_bytes × 8 / dl_total_ms / 1000` (**main speed metric**) |
| `lat`, `lon` | ° | last location fix, rounded to 4 decimals (~11 m) |
| `loc_accuracy_m` | m | accuracy reported by Android |
| `loc_age_s` | s | age of the location fix |

**Signal used in the analysis:** `wifi_rssi` for `net_role=WIFI`, `cell_dbm` for `net_role=CELL`.
Both phones log both signals, so a phone in the cellular role still records `cell_*`, and a phone in
the Wi-Fi role records `cell_*` while its mobile data is off.
