# [analysis] Analysis script: mean ± SD per point, line plots, scatter + correlation, drops

Analysis script that rebuilds every table/figure from `data/raw/` (assignment: reproducible from the data package).

## Tasks
- [ ] Load all CSVs; tag rows to a point (tap time → +40 s window) or a segment between points
- [ ] Deliverable 3: per walk per point mean, then **mean ± SD across the 3 walks** per network × time slot × point (latency, download speed, signal)
- [ ] Line plots of signal / latency / speed along the route (all runs thin + mean bold)
- [ ] Deliverable 4: scatter signal vs latency and vs speed per network with Pearson + Spearman r
- [ ] Deliverable 5: drops per walk with nearest point
- [ ] Peak vs off-peak comparison; handset check (swapped walks); probe success/skip rates
- [ ] Synthetic **test-only** data generator (clearly marked, never mixed into `data/raw/`) to test the pipeline before real walks

## Done when
- `python analysis/analyse.py` writes tables (CSV/Markdown) and PNG figures to `analysis/output/`.
