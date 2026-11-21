"""
Analysis for "Measuring Wi-Fi and Cellular Performance Along a University Route"
(CS4473 Group 11). Rebuilds every table and figure from the raw CSVs.

    pip install -r analysis/requirements.txt
    python analysis/analyse.py                      # reads data/raw/, writes analysis/output/

Options:
    --data-dir DIR    folder with the app's CSV files (default: data/raw)
    --out-dir DIR     output folder (default: analysis/output)
    --window S        seconds after a waypoint tap that count as "at the point" (default: 40)

Outputs (deliverable numbers refer to the proposal, section 5):
    point_stats.csv / .md      D3  mean ± SD (across walks) of latency, speed, signal per point,
                                   per network and time slot
    walk_point_means.csv           per-walk means per point (the values the SD is computed from)
    correlations.csv / .md     D4  Pearson r and Spearman rho of signal vs latency and vs speed
    drops.csv / .md            D5  every connection drop / switch / failed probe, with the point
    run_summary.csv / .md          per run: probes OK / failed / skipped, drops, data used
    handset_check.md               the same network measured on phone A vs phone B (role swap)
    fig_route_*.png                mean ± SD per point along the route (Wi-Fi vs cellular, both slots)
    fig_timeseries.png             every run along the walk, all runs plotted
    fig_scatter.png            D4  signal vs latency and vs speed with r
    summary.md                     everything above in one file
"""
import argparse
import sys
from pathlib import Path

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt  # noqa: E402
import numpy as np  # noqa: E402
import pandas as pd  # noqa: E402
from scipy import stats  # noqa: E402

ROOT = Path(__file__).resolve().parent.parent
POINTS = ["P1", "P2", "P3", "P4", "P5"]
ROLES = ["WIFI", "CELL"]
SLOTS = ["OFFPEAK", "PEAK"]
ROLE_LABEL = {"WIFI": "Wi-Fi", "CELL": "Cellular"}
SLOT_LABEL = {"OFFPEAK": "Off-peak (08:00–09:00)", "PEAK": "Peak (12:30–13:30)"}

# Colour = network (validated categorical slots 1 & 2); line style = time slot (never colour alone).
COLOR = {"WIFI": "#2a78d6", "CELL": "#eb6834"}
LINESTYLE = {"OFFPEAK": "-", "PEAK": "--"}
MARKER = {"OFFPEAK": "o", "PEAK": "s"}
INK, INK2, GRID = "#0b0b0b", "#52514e", "#e4e3df"

METRICS = {
    "latency_median_ms": "Latency, median of 3 pings (ms)",
    "dl_mbps": "Download speed (Mbps)",
    "signal_dbm": "Signal strength (dBm)",
}


# --------------------------------------------------------------------------- loading


def load(data_dir: Path) -> pd.DataFrame:
    files = sorted(data_dir.glob("*.csv"))
    if not files:
        sys.exit(f"No CSV files in {data_dir}")
    if any(f.name.startswith("SYNTHETIC") for f in files):
        print("!!! WARNING: SYNTHETIC test data - these results are NOT measurements !!!")
    frames = []
    for f in files:
        d = pd.read_csv(f, dtype={"waypoint": str, "error": str, "event": str, "wifi_bssid_hash": str})
        d["run"] = f.stem
        frames.append(d)
    df = pd.concat(frames, ignore_index=True)
    for c in ("waypoint", "error", "event", "status", "trigger"):
        df[c] = df[c].fillna("")
    df["time"] = pd.to_datetime(df["epoch_ms"], unit="ms")
    # Signal of the network being measured: Wi-Fi RSSI on the Wi-Fi phone, cell dBm on the cell phone.
    df["signal_dbm"] = np.where(df["net_role"] == "WIFI", df["wifi_rssi"], df["cell_dbm"])
    return df.sort_values(["run", "epoch_ms"]).reset_index(drop=True)


def assign_locations(df: pd.DataFrame, window_s: float) -> pd.DataFrame:
    """Label every row with the point it was taken at (within `window_s` after the tap) or the
    segment between points. Waypoint taps are the ground truth for position."""
    df = df.copy()
    df["location"] = ""
    df["at_point"] = False
    df["t_since_p1_s"] = np.nan
    for run, g in df.groupby("run"):
        taps = g[g["row_type"] == "WAYPOINT"][["epoch_ms", "waypoint"]].values.tolist()
        p1 = next((t for t, w in taps if w == "P1"), g["epoch_ms"].min())
        df.loc[g.index, "t_since_p1_s"] = (g["epoch_ms"] - p1) / 1000
        for idx, t in zip(g.index, g["epoch_ms"]):
            last = [(tt, w) for tt, w in taps if tt <= t]
            if not last:
                df.at[idx, "location"] = "start"
                continue
            tt, w = last[-1]
            if (t - tt) / 1000 <= window_s:
                df.at[idx, "location"] = w
                df.at[idx, "at_point"] = True
            else:
                nxt = POINTS[POINTS.index(w) + 1] if w in POINTS[:-1] else "end"
                df.at[idx, "location"] = f"{w}→{nxt}"
    return df


# --------------------------------------------------------------------------- tables


def fmt(m, s, d=1):
    if pd.isna(m):
        return "–"
    return f"{m:.{d}f} ± {s:.{d}f}" if not pd.isna(s) else f"{m:.{d}f}"


def point_stats(df):
    ok = df[(df["row_type"] == "PROBE") & (df["status"] == "OK") & df["at_point"]]
    per_walk = (ok.groupby(["net_role", "time_slot", "walk_id", "phone_id", "location"])[list(METRICS)]
                .mean().reset_index())
    agg = (per_walk.groupby(["net_role", "time_slot", "location"])[list(METRICS)]
           .agg(["mean", "std", "count"]))
    agg.columns = [f"{m}_{s}" for m, s in agg.columns]
    agg = agg.reset_index()
    agg["n_walks"] = agg["latency_median_ms_count"]
    return per_walk, agg


def point_stats_md(agg):
    lines = []
    for metric, label, d in (("latency_median_ms", "Latency (ms)", 0), ("dl_mbps", "Download speed (Mbps)", 2),
                             ("signal_dbm", "Signal (dBm)", 0)):
        lines += [f"### {label}: mean ± SD across walks", "",
                  "| Network | Time slot | " + " | ".join(POINTS) + " |",
                  "|---|---|" + "---|" * len(POINTS)]
        for r in ROLES:
            for s in SLOTS:
                cells = []
                for p in POINTS:
                    row = agg[(agg.net_role == r) & (agg.time_slot == s) & (agg.location == p)]
                    if row.empty:
                        cells.append("–")
                    else:
                        x = row.iloc[0]
                        cells.append(f"{fmt(x[f'{metric}_mean'], x[f'{metric}_std'], d)} (n={int(x['n_walks'])})")
                lines.append(f"| {ROLE_LABEL[r]} | {s} | " + " | ".join(cells) + " |")
        lines.append("")
    return "\n".join(lines)


def correlations(df):
    ok = df[(df["row_type"] == "PROBE") & (df["status"] == "OK")]
    out = []
    for r in ROLES:
        d = ok[ok.net_role == r]
        for y in ("latency_median_ms", "dl_mbps"):
            dd = d[["signal_dbm", y]].dropna()
            if len(dd) < 3 or dd["signal_dbm"].nunique() < 2:
                out.append(dict(network=r, y=y, n=len(dd)))
                continue
            pr, pp = stats.pearsonr(dd["signal_dbm"], dd[y])
            sr, sp = stats.spearmanr(dd["signal_dbm"], dd[y])
            out.append(dict(network=r, y=y, n=len(dd), pearson_r=pr, pearson_p=pp, spearman_rho=sr, spearman_p=sp))
    return pd.DataFrame(out)


def drops(df):
    """Connection drops, switches and failed probes, with where they happened."""
    ev = df[(df["row_type"] == "EVENT") &
            df["event"].isin(["NET_LOST", "NOT_VALIDATED", "TRANSPORT_CHANGE", "WIFI_AP_CHANGE", "CELL_TYPE_CHANGE"])]
    fp = df[(df["row_type"] == "PROBE") & (df["status"] == "FAILED")].assign(event="PROBE_FAILED")
    d = pd.concat([ev, fp]).sort_values(["run", "epoch_ms"])
    d["kind"] = d["event"].map({"NET_LOST": "drop", "NOT_VALIDATED": "drop", "PROBE_FAILED": "drop"}).fillna("switch")
    return d[["run", "phone_id", "net_role", "time_slot", "walk_id", "timestamp_iso", "t_since_p1_s",
              "location", "kind", "event", "error", "signal_dbm"]]


def run_summary(df, dr):
    rows = []
    for run, g in df.groupby("run"):
        p = g[g["row_type"] == "PROBE"]
        d = dr[dr.run == run]
        dur = (g["epoch_ms"].max() - g["epoch_ms"].min()) / 1000
        rows.append(dict(
            run=run, phone=g.phone_id.iloc[0], network=g.net_role.iloc[0], slot=g.time_slot.iloc[0],
            walk=g.walk_id.iloc[0], duration_min=round(dur / 60, 1),
            probes=len(p), ok=(p.status == "OK").sum(), failed=(p.status == "FAILED").sum(),
            skipped=(p.status == "SKIPPED").sum(),
            net_lost=(d.event == "NET_LOST").sum(), switches=(d.kind == "switch").sum(),
            data_mb=round(p["dl_bytes"].fillna(0).sum() / 1e6, 1),
            mean_interval_s=round(p[p.trigger == "TIMER"]["epoch_ms"].diff().mean() / 1000, 1),
        ))
    return pd.DataFrame(rows)


def handset_check(df):
    ok = df[(df["row_type"] == "PROBE") & (df["status"] == "OK")]
    t = ok.groupby(["net_role", "phone_id"])[list(METRICS)].agg(["mean", "count"])
    lines = ["| Network | Phone | Latency mean (ms) | Speed mean (Mbps) | Signal mean (dBm) | probes |",
             "|---|---|---|---|---|---|"]
    for (r, ph), x in t.iterrows():
        lines.append(f"| {ROLE_LABEL[r]} | {ph} | {x[('latency_median_ms', 'mean')]:.0f} | "
                     f"{x[('dl_mbps', 'mean')]:.2f} | {x[('signal_dbm', 'mean')]:.0f} | "
                     f"{int(x[('latency_median_ms', 'count')])} |")
    return "\n".join(lines) + ("\n\nThe phones swap roles between walks. If the same network gives similar values on "
                               "phone A and on phone B, the handset is not a major factor.\n")


# --------------------------------------------------------------------------- figures


def style(ax, ylabel, xlabel=None):
    ax.set_ylabel(ylabel, color=INK2, fontsize=9)
    if xlabel:
        ax.set_xlabel(xlabel, color=INK2, fontsize=9)
    ax.grid(axis="y", color=GRID, linewidth=0.8)
    ax.set_axisbelow(True)
    for s in ("top", "right"):
        ax.spines[s].set_visible(False)
    for s in ("left", "bottom"):
        ax.spines[s].set_color(GRID)
    ax.tick_params(colors=INK2, labelsize=8)


def fig_route(agg, per_walk, metric, out: Path):
    fig, ax = plt.subplots(figsize=(6.4, 3.6), dpi=200)
    x = np.arange(len(POINTS))
    for i, r in enumerate(ROLES):
        for j, s in enumerate(SLOTS):
            off = (i * 2 + j - 1.5) * 0.06  # small dodge so error bars don't overlap
            # every walk, thin
            for _, w in per_walk[(per_walk.net_role == r) & (per_walk.time_slot == s)].groupby("walk_id"):
                w = w.set_index("location").reindex(POINTS)
                ax.plot(x + off, w[metric], color=COLOR[r], ls=LINESTYLE[s], lw=0.8, alpha=0.3)
            a = agg[(agg.net_role == r) & (agg.time_slot == s)].set_index("location").reindex(POINTS)
            ax.errorbar(x + off, a[f"{metric}_mean"], yerr=a[f"{metric}_std"], color=COLOR[r], ls=LINESTYLE[s],
                        lw=2, marker=MARKER[s], ms=6, mec="white", mew=1.5, capsize=3,
                        label=f"{ROLE_LABEL[r]}, {SLOT_LABEL[s]}")
    ax.set_xticks(x, POINTS)
    style(ax, METRICS[metric], "Waypoint along the route (GTN Lab → Library)")
    ax.legend(fontsize=7, frameon=False, ncol=2, loc="upper center", bbox_to_anchor=(0.5, -0.2))
    ax.set_title(f"{METRICS[metric].split(' (')[0]} along the route: mean ± SD over walks (thin lines: each walk)",
                 fontsize=9, color=INK, loc="left")
    fig.tight_layout()
    fig.savefig(out)
    plt.close(fig)


def fig_timeseries(df, out: Path):
    ok = df[(df["row_type"] == "PROBE") & (df["status"] == "OK")]
    fig, axes = plt.subplots(3, 2, figsize=(9, 7.5), dpi=200, sharex=True)
    # Latency and speed share a y-scale across networks so they can be compared directly;
    # signal does not: Wi-Fi RSSI and cellular RSRP are different quantities.
    for rr in (0, 1):
        axes[rr, 1].sharey(axes[rr, 0])
    for c, r in enumerate(ROLES):
        for rr, m in enumerate(METRICS):
            ax = axes[rr, c]
            for s in SLOTS:
                for _, g in ok[(ok.net_role == r) & (ok.time_slot == s)].groupby("run"):
                    ax.plot(g["t_since_p1_s"] / 60, g[m], color=COLOR[r], ls=LINESTYLE[s], lw=1, alpha=0.7,
                            marker=".", ms=3)
            # tap times of the first run as reference lines
            taps = df[(df.net_role == r) & (df.row_type == "WAYPOINT")]
            for p, t in taps.groupby("waypoint")["t_since_p1_s"].median().items():
                ax.axvline(t / 60, color=GRID, lw=1, zorder=0)
                if rr == 0:
                    ax.text(t / 60, 1.02, p, transform=ax.get_xaxis_transform(), fontsize=7, color=INK2, ha="center")
            ylabel = METRICS[m] if c == 0 else None
            if m == "signal_dbm":
                ylabel = "Wi-Fi RSSI (dBm)" if r == "WIFI" else "Cellular signal (dBm)"
            style(ax, ylabel, "Minutes since P1" if rr == 2 else None)
            if rr == 0:
                ax.set_title(ROLE_LABEL[r], fontsize=10, color=INK, loc="left", pad=14)
    handles = [plt.Line2D([], [], color=INK2, ls=LINESTYLE[s], label=SLOT_LABEL[s]) for s in SLOTS]
    fig.legend(handles=handles, loc="lower center", ncol=2, frameon=False, fontsize=8)
    fig.tight_layout(rect=(0, 0.04, 1, 1))
    fig.savefig(out)
    plt.close(fig)


def fig_scatter(df, corr, out: Path):
    ok = df[(df["row_type"] == "PROBE") & (df["status"] == "OK")]
    fig, axes = plt.subplots(2, 2, figsize=(8, 6.5), dpi=200)
    for c, r in enumerate(ROLES):
        for rr, y in enumerate(("latency_median_ms", "dl_mbps")):
            ax = axes[rr, c]
            for s in SLOTS:
                d = ok[(ok.net_role == r) & (ok.time_slot == s)]
                ax.scatter(d["signal_dbm"], d[y], s=14, color=COLOR[r], marker=MARKER[s],
                           alpha=0.6 if s == "OFFPEAK" else 0.35, edgecolors="white", linewidths=0.5,
                           label=SLOT_LABEL[s])
            k = corr[(corr.network == r) & (corr.y == y)]
            if not k.empty and "pearson_r" in k and not pd.isna(k.iloc[0].get("pearson_r")):
                k = k.iloc[0]
                # latency falls with signal (top-right empty), speed rises (top-left empty)
                x0, ha = (0.98, "right") if y == "latency_median_ms" else (0.02, "left")
                ax.text(x0, 0.97, f"Pearson r = {k.pearson_r:.2f}\nSpearman ρ = {k.spearman_rho:.2f}\nn = {int(k.n)}",
                        transform=ax.transAxes, va="top", ha=ha, fontsize=8, color=INK, zorder=5,
                        bbox=dict(boxstyle="round,pad=0.3", fc="white", ec=GRID, alpha=0.9))
            style(ax, METRICS[y] if c == 0 else None,
                  ("Wi-Fi RSSI (dBm)" if r == "WIFI" else "Cellular signal (dBm)") if rr == 1 else None)
            if rr == 0:
                ax.set_title(ROLE_LABEL[r], fontsize=10, color=INK, loc="left")
    handles = [plt.Line2D([], [], color=INK2, marker=MARKER[s], ls="", label=SLOT_LABEL[s]) for s in SLOTS]
    fig.legend(handles=handles, loc="lower center", ncol=2, frameon=False, fontsize=8)
    fig.tight_layout(rect=(0, 0.04, 1, 1))
    fig.savefig(out)
    plt.close(fig)


# --------------------------------------------------------------------------- main


def md_table(d: pd.DataFrame, floatfmt=".2f") -> str:
    d = d.copy()
    for c in d.select_dtypes("float").columns:
        d[c] = d[c].map(lambda v: "" if pd.isna(v) else format(v, floatfmt))
    head = "| " + " | ".join(map(str, d.columns)) + " |\n|" + "---|" * len(d.columns) + "\n"
    return head + "\n".join("| " + " | ".join(map(str, r)) + " |" for r in d.values.tolist()) + "\n"


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--data-dir", type=Path, default=ROOT / "data" / "raw")
    ap.add_argument("--out-dir", type=Path, default=ROOT / "analysis" / "output")
    ap.add_argument("--window", type=float, default=40.0)
    a = ap.parse_args()
    a.out_dir.mkdir(parents=True, exist_ok=True)

    df = assign_locations(load(a.data_dir), a.window)
    per_walk, agg = point_stats(df)
    corr = correlations(df)
    dr = drops(df)
    rs = run_summary(df, dr)

    per_walk.to_csv(a.out_dir / "walk_point_means.csv", index=False)
    agg.to_csv(a.out_dir / "point_stats.csv", index=False)
    corr.to_csv(a.out_dir / "correlations.csv", index=False)
    dr.to_csv(a.out_dir / "drops.csv", index=False)
    rs.to_csv(a.out_dir / "run_summary.csv", index=False)

    ps_md = point_stats_md(agg)
    corr_md = md_table(corr.rename(columns={"y": "versus"}), ".3f")
    drops_md = md_table(dr.drop(columns=["run", "timestamp_iso"]), ".0f") if len(dr) else "No drops recorded.\n"
    per_run_drops = (dr[dr.kind == "drop"].groupby(["net_role", "time_slot", "walk_id", "location"]).size()
                     .rename("drops").reset_index())
    rs_md = md_table(rs.drop(columns=["run"]), ".1f")
    hc_md = handset_check(df)
    (a.out_dir / "point_stats.md").write_text(ps_md)
    (a.out_dir / "correlations.md").write_text(corr_md)
    (a.out_dir / "drops.md").write_text(drops_md)
    (a.out_dir / "run_summary.md").write_text(rs_md)
    (a.out_dir / "handset_check.md").write_text(hc_md)

    for m in METRICS:
        fig_route(agg, per_walk, m, a.out_dir / f"fig_route_{m}.png")
    fig_timeseries(df, a.out_dir / "fig_timeseries.png")
    fig_scatter(df, corr, a.out_dir / "fig_scatter.png")

    synthetic = any(r.startswith("SYNTHETIC") for r in df.run.unique())
    summary = "\n".join([
        "# Analysis summary",
        "",
        "> **SYNTHETIC TEST DATA - not measurements.**\n" if synthetic else "",
        f"Data: `{a.data_dir}` - {df.run.nunique()} runs, {(df.row_type == 'PROBE').sum()} probe rows. "
        f"A probe counts as *at a point* if it is within {a.window:.0f} s after that point's waypoint tap.",
        "", "## Runs", "", rs_md,
        "## D3 - Mean ± SD per point (across walks)", "", ps_md,
        "## D4 - Signal vs latency and speed", "", corr_md, "![scatter](fig_scatter.png)", "",
        "## D5 - Drops per walk, by location", "",
        md_table(per_run_drops) if len(per_run_drops) else "No drops recorded.\n",
        "Full list: [drops.md](drops.md)", "",
        "## Handset check (role swap)", "", hc_md,
        "## Figures", "",
        *[f"![{m}](fig_route_{m}.png)" for m in METRICS], "![timeseries](fig_timeseries.png)", "",
    ])
    (a.out_dir / "summary.md").write_text(summary)
    print(f"Done: {df.run.nunique()} runs -> {a.out_dir}")


if __name__ == "__main__":
    main()
