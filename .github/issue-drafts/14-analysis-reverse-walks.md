# [analysis] Support reverse walks (P5 → P1) and show where a network failed

Off-peak walk 2 was done P5 → P1 (on purpose, to balance timing within the slot). The analysis
assumed every walk goes P1 → P5:

- Segment labels were built from the fixed point order, so the reverse walk showed "P4→P5" for
  the stretch actually walked from P4 to P3, and "P5→end" at the start.
- Time was measured from the P1 tap, so the reverse walk had negative times and did not line up
  with the other walks in the time-series figure.

Also, the per-point mean ± SD uses successful probes only. Where Wi-Fi failed (P3, P4), the
mean rests on 1 walk and looks normal, which hides the main finding.

## Tasks
- [ ] Label segments by the next point actually tapped in that walk; add a `direction` column
      (forward / reverse) from the first tap
- [ ] Measure time from the walk's first tap (`t_since_start_s`) instead of from P1
- [ ] Time-series figure: plot against position along the route (P1…P5; between points
      interpolated from the tap times), so walks in both directions line up by place
- [ ] New table `availability.md/.csv`: probes OK / attempted at each point, per network and slot
      (skipped probes excluded), shown in `summary.md` above the mean ± SD tables
- [ ] Route figures: label a mean with `n=` when it rests on fewer walks than the others

## Done when
- The off-peak data (walks 1 and 3 forward, walk 2 reverse) gives correct segment labels, no
  negative times, and the success table shows the Wi-Fi failures at P3 and P4.
- The synthetic test data still runs.
