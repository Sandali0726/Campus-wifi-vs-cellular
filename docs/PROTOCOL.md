# Field protocol

Follow this on **every** walk so that the conditions stay constant (proposal §3).

## Route and points

Route: **GTN Lab → Library**, with at least one indoor stretch and one outdoor stretch.
Mark each point physically (tape on the floor or a fixed landmark) and photograph it once for the report.

| Point | Description (fill in once) | Indoor / outdoor |
|---|---|---|
| P1 | GTN Lab, … | indoor |
| P2 | … | |
| P3 | … | |
| P4 | … | |
| P5 | Library entrance, … | |

## Schedule (2 factors × 2 levels, 3 repetitions)

| Slot | Time | Walks |
|---|---|---|
| Off-peak | 08:00–09:00 | 3 (on 3 different days if possible) |
| Peak | 12:30–13:30 | 3 |

**Role swap** (cancels handset differences; the twist in the proposal):

| Walk | Phone A | Phone B |
|---|---|---|
| 1 | Wi-Fi | Cellular |
| 2 | **Cellular** | **Wi-Fi** |
| 3 | Wi-Fi | Cellular |

Use the same pattern in both time slots. Over 6 walks, each phone measures each network at least twice.

## Before the walk (checklist, both phones)

- [ ] Laptop: `probe_server.py` and ngrok running; `python server/self_test.py <ngrok URL>` passes
- [ ] Date & time set to **automatic**, on both phones (clock sync)
- [ ] Wi-Fi-role phone: **Wi-Fi ON, mobile data OFF**, connected to the campus network (write down the SSID)
- [ ] Cellular-role phone: **Wi-Fi OFF, mobile data ON** (Mobitel)
- [ ] Battery saver OFF, battery above 50 %, all other apps closed, screen timeout long (the app keeps the screen on)
- [ ] Location ON; Bluetooth state the same as on the other walks
- [ ] In the app: phone, role, time slot, walk number and URL are correct
- [ ] Walk log started (`data/field-notes/walk_log_template.md`)

## During the walk

1. Press **Start** on both phones at P1. Hold both phones at chest height, side by side, screens on.
2. At each point: tap **Pk on both phones at the same moment**, stand still for **40 s**
   (the app shows "✔ walk on"), then walk on at normal pace.
3. Write down any place where a phone showed no signal, a "No internet" icon, or switched network.
4. At P5, after 40 s, press **Stop** on both phones.

## After the walk

- [ ] Copy the CSVs from both phones (Share button, or `adb pull`) into `data/raw/` **without editing them**
- [ ] Copy the day's `server/logs/server_log_<date>.csv` to `data/field-notes/`
- [ ] Finish the walk log; commit both

## Validation (once, before the first real walk)

Fill in `data/field-notes/validation_template.md`:
1. At 3 spots, compare the RSSI on the app screen with the RSSI shown by Android
   (Settings → Network → Wi-Fi → network details, or a Wi-Fi analyser app).
2. For 5 downloads, compare `dl_bytes` and `dl_total_ms` in the CSV with the server log
   (bytes_sent must be 256000; the timestamps must line up).
3. Turn Wi-Fi off and on during a test run, and check that `NET_LOST` / `NET_AVAILABLE` rows appear.
