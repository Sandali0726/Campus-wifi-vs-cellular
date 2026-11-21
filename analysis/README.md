# Analysis

`analyse.py` rebuilds every table and figure in the report from the raw CSVs in `data/raw/`.

```bash
pip install -r analysis/requirements.txt
python analysis/analyse.py            # → analysis/output/
```

| Output | Proposal deliverable |
|---|---|
| `point_stats.md/.csv` | **D3**: mean ± SD of latency, speed and signal at P1–P5, for each network × time slot |
| `fig_route_*.png` | the same values plotted along the route (thin lines show each walk) |
| `fig_scatter.png`, `correlations.md` | **D4**: signal vs latency and vs speed, Pearson r and Spearman ρ per network |
| `drops.md/.csv` | **D5**: every drop / switch / failed probe, with the point or segment where it happened |
| `fig_timeseries.png` | every run against minutes since P1 ("plot all runs") |
| `run_summary.md` | per run: probes OK / failed / skipped, drops, data used, probe interval |
| `handset_check.md` | the same network on phone A vs phone B (role-swap check) |
| `summary.md` | everything above in one file |

## Method

- **Position (ground truth):** a probe counts as *at Pk* if it falls within 40 s after the Pk tap
  (`--window`). Other probes are labelled with the segment they were on, such as `P3→P4`.
- **Signal:** `wifi_rssi` for Wi-Fi-role runs, `cell_dbm` for cellular-role runs.
- **Latency:** the median of the 3 pings (`latency_median_ms`). **Speed:** `dl_mbps`.
- **Mean ± SD:** first the mean of each walk at each point, then the mean and SD (ddof = 1) across
  the 3 walks of that condition. So n = number of walks, not number of probes.
- **Correlation:** computed over all successful probes of each network. Spearman is reported as well,
  because the relationship is not expected to be linear.
- **Drops:** `NET_LOST` and `NOT_VALIDATED` events, plus `FAILED` probes. **Switches:** `TRANSPORT_CHANGE`,
  `WIFI_AP_CHANGE` and `CELL_TYPE_CHANGE` events.

## Testing without real data

`make_test_data.py` writes **synthetic** files to `analysis/test_data/`, which is git-ignored. Every
file name starts with `SYNTHETIC_`, and the script prints a warning on every run. These files only
exist to test the pipeline. They must never go into `data/raw/` or the report.

```bash
python analysis/make_test_data.py
python analysis/analyse.py --data-dir analysis/test_data --out-dir analysis/output_test
```
