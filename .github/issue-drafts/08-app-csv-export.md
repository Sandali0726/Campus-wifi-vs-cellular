# [app] CSV logging and export

CSV storage and export (proposal block diagram: Local CSV Storage → Offline Analysis).

## Tasks
- [ ] One file per run: `<phone>_<slot>_<walk>_<yyyyMMdd-HHmmss>.csv` in app-specific external storage
- [ ] Fixed header (see README data dictionary), append + flush after every row, thread-safe
- [ ] Columns: timestamp_iso, epoch_ms, elapsed_ms, phone_id, walk_id, time_slot, row_type, trigger, waypoint, status, error, active_transport, net_type, wifi_rssi, wifi_link_mbps, wifi_freq_mhz, cell_dbm, cell_level, cell_rsrp, cell_rsrq, ping1_ms, ping2_ms, ping3_ms, latency_median_ms, latency_mean_ms, ttfb_ms, dl_bytes, dl_ms, dl_mbps, lat, lon, loc_accuracy_m, event
- [ ] Share/export button (and `adb pull` path documented)

## Done when
- A run produces a well-formed CSV that loads in pandas.
