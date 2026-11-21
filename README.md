# Measuring Wi-Fi and Cellular Performance Along a University Route

CS4473 Mobile Computing — Group Research Project (Assignment 2), **Option F: Mobile Network Measurement**
Group 11: K.K.S. Kapuruge (220301V), W.S.S. Jayawardhana (220282K)

**Research question:** How do signal strength, latency and download speed change along a campus route
(GTN Lab → Library, points P1–P5), how do Wi-Fi and cellular compare, and does this differ between an
off-peak (08:00–09:00) and a peak (12:30–13:30) time of day?

Two identical Google Pixel 7 Pro phones (Android 16) are walked together: one with only Wi-Fi active,
one with only cellular (Mobitel) active. A Kotlin app on each phone logs one CSV row every 10 s and on
every waypoint tap (signal, network type, latency, 250 KB download speed, coarse location, waypoint).

## Repository layout

| Folder | Contents |
|---|---|
| [`android-app/`](android-app/) | Kotlin Android app (the measurement instrument) |
| [`server/`](server/) | Laptop probe server reached through a fixed ngrok URL |
| [`data/raw/`](data/raw/) | Raw CSVs pulled from the phones — never edited by hand |
| [`data/field-notes/`](data/field-notes/) | Hand-written walk logs and validation sheets |
| [`analysis/`](analysis/) | `analyse.py` — rebuilds every table and figure from `data/raw/` |
| [`docs/`](docs/) | Field protocol and templates |
| [`report/`](report/) | Final 3–4 page two-column report |
| [`AI_USAGE_LOG.md`](AI_USAGE_LOG.md) | AI tools used, for what, and how the output was used |

## How to reproduce

1. **Server** — see [`server/README.md`](server/README.md) (start server, start ngrok on the static domain).
2. **App** — open `android-app/` in Android Studio, build and install on both phones, enter the ngrok URL.
3. **Walks** — follow [`docs/PROTOCOL.md`](docs/PROTOCOL.md); copy CSVs into `data/raw/`.
4. **Analysis** — `pip install -r analysis/requirements.txt && python analysis/analyse.py`
   → tables and figures in `analysis/output/`.

## Project tracking

Work is split into issues; drafts are in [`.github/issue-drafts/`](.github/issue-drafts/).
