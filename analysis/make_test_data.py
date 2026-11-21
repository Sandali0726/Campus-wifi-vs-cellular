"""
!!! SYNTHETIC TEST DATA - NOT MEASUREMENTS !!!

Generates fake CSVs in the app's exact format so analyse.py can be tested before the real walks.
Output goes to analysis/test_data/ (git-ignored) and every file name starts with SYNTHETIC_.
Never copy these files into data/raw/: fabricated data is academic misconduct (assignment §4).

    python analysis/make_test_data.py
    python analysis/analyse.py --data-dir analysis/test_data --out-dir analysis/output_test
"""
import csv
import random
from datetime import datetime, timedelta, timezone
from pathlib import Path

OUT = Path(__file__).resolve().parent / "test_data"
TZ = timezone(timedelta(hours=5, minutes=30))
HEADER = ("timestamp_iso,epoch_ms,elapsed_ms,phone_id,net_role,time_slot,walk_id,row_type,trigger,waypoint,"
          "status,error,event,active_transport,net_type,operator,wifi_rssi,wifi_link_mbps,wifi_freq_mhz,"
          "wifi_bssid_hash,cell_dbm,cell_level,cell_rsrp,cell_rsrq,cell_sinr,cell_nr_rsrp,ping1_ms,ping2_ms,"
          "ping3_ms,latency_median_ms,latency_mean_ms,ttfb_ms,dl_bytes,dl_total_ms,dl_mbps,lat,lon,"
          "loc_accuracy_m,loc_age_s").split(",")

# made-up route profile: (wifi rssi, cell dbm) at P1..P5
PROFILE = {"P1": (-48, -108), "P2": (-60, -104), "P3": (-78, -92), "P4": (-85, -90), "P5": (-55, -100)}
POINTS = list(PROFILE)
PAUSE_S, WALK_S = 40, 70


def signal_at(t_rel, schedule):
    """Interpolate the profile along the walk timeline."""
    for i, (p, t0) in enumerate(schedule):
        if t_rel < t0:
            if i == 0:
                return PROFILE[p], ""
            prev, tp = schedule[i - 1]
            f = min(1, max(0, (t_rel - tp - PAUSE_S) / WALK_S))
            a, b = PROFILE[prev], PROFILE[p]
            return (a[0] + f * (b[0] - a[0]), a[1] + f * (b[1] - a[1])), prev
    return PROFILE[schedule[-1][0]], schedule[-1][0]


def make_run(phone, role, slot, walk, start, rng):
    schedule = [(p, i * (PAUSE_S + WALK_S)) for i, p in enumerate(POINTS)]
    end = schedule[-1][1] + PAUSE_S + 5
    busy = 1.6 if slot == "PEAK" else 1.0
    rows, wp = [], ""

    def row(t, **kw):
        ts = start + timedelta(seconds=t)
        r = dict.fromkeys(HEADER, "")
        r.update(timestamp_iso=ts.isoformat(timespec="milliseconds"), epoch_ms=int(ts.timestamp() * 1000),
                 elapsed_ms=int(t * 1000), phone_id=phone, net_role=role, time_slot=slot, walk_id=walk,
                 waypoint=wp, active_transport="WIFI" if role == "WIFI" else "CELL",
                 net_type="LTE", operator="SYNTHETIC")
        r.update(kw)
        return r

    rows.append(row(0, row_type="EVENT", event="RUN_START"))
    taps = {t0: p for p, t0 in schedule}
    events = sorted(set([t for t in range(0, end, 10)] + list(taps)))
    for t in events:
        if t in taps:
            wp = taps[t]
            rows.append(row(t, row_type="WAYPOINT", trigger="TAP"))
        (w, c), _ = signal_at(t, schedule)
        w += rng.gauss(0, 3)
        c += rng.gauss(0, 2)
        sig = dict(wifi_rssi=round(w) if role == "WIFI" else "", cell_dbm=round(c), cell_level=max(0, min(4, int((c + 120) / 8))))
        if role == "WIFI":
            q = max(0.05, (w + 90) / 45)
            lat = (12 + 30 / q) * busy * rng.uniform(0.8, 1.4)
            mbps = 25 * q / busy * rng.uniform(0.7, 1.2)
            fail = w < -84 and rng.random() < 0.5
        else:
            q = max(0.1, (c + 115) / 30)
            lat = (45 + 25 / q) * busy * rng.uniform(0.85, 1.25)
            mbps = 12 * q / busy * rng.uniform(0.75, 1.15)
            fail = False
        trig = "TAP" if t in taps else "TIMER"
        if fail:
            rows.append(row(t + 0.2, row_type="EVENT", event="NET_LOST", error="WIFI", **sig))
            rows.append(row(t + 0.5, row_type="PROBE", trigger=trig, status="FAILED",
                            error="ping1:UnknownHostException", **sig))
            rows.append(row(t + 6, row_type="EVENT", event="NET_AVAILABLE", error="WIFI", **sig))
            continue
        pings = sorted([lat * rng.uniform(0.9, 1.1) for _ in range(3)])
        tot = 256000 * 8 / (mbps * 1000)
        rows.append(row(t + 0.5, row_type="PROBE", trigger=trig, status="OK",
                        ping1_ms=f"{pings[2] * 1.8:.2f}", ping2_ms=f"{pings[0]:.2f}", ping3_ms=f"{pings[1]:.2f}",
                        latency_median_ms=f"{pings[1]:.2f}", latency_mean_ms=f"{(pings[2] * 1.8 + pings[0] + pings[1]) / 3:.2f}",
                        ttfb_ms=f"{lat:.2f}", dl_bytes=256000, dl_total_ms=f"{tot:.2f}", dl_mbps=f"{mbps:.3f}", **sig))
    rows.append(row(end, row_type="EVENT", event="RUN_STOP"))
    return rows


def main():
    rng = random.Random(1)
    OUT.mkdir(exist_ok=True)
    for slot, day_hour in (("OFFPEAK", 8), ("PEAK", 12)):
        for walk in (1, 2, 3):
            start = datetime(2026, 11, 23 + walk, day_hour, 15, tzinfo=TZ)
            wifi_phone = "B" if walk == 2 else "A"  # role swap on walk 2
            for phone in "AB":
                role = "WIFI" if phone == wifi_phone else "CELL"
                rows = make_run(phone, role, slot, walk, start, rng)
                name = f"SYNTHETIC_{phone}_{role}_{slot}_walk{walk}_{start:%Y%m%d-%H%M%S}.csv"
                with open(OUT / name, "w", newline="") as f:
                    w = csv.DictWriter(f, fieldnames=HEADER)
                    w.writeheader()
                    w.writerows(rows)
    print(f"wrote 12 SYNTHETIC files to {OUT}")


if __name__ == "__main__":
    main()
