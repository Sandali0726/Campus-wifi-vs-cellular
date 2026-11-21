# [app] CSV logging and export

CSV storage and export (proposal block diagram: Local CSV Storage → Offline Analysis).

## Tasks
- [ ] One file per run: `<phone>_<role>_<slot>_walk<n>_<yyyyMMdd-HHmmss>.csv` in app-specific external storage
- [ ] Fixed header (see `docs/DATA_DICTIONARY.md`), append + flush after every row, thread-safe
- [ ] Columns documented in `docs/DATA_DICTIONARY.md` (run info, row type/status/event, transport, Wi-Fi RSSI/link/freq/BSSID hash, cell dBm/level/RSRP/RSRQ/SINR/NR-RSRP, 3 pings + median/mean, TTFB, bytes, download time, Mbps, lat/lon/accuracy/age)
- [ ] Share/export button (and `adb pull` path documented)

## Done when
- A run produces a well-formed CSV that loads in pandas.
