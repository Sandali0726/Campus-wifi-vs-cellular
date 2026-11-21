# [app] Foreground measurement service with 10 s timer, waypoint taps and skip logic

Foreground service that drives probing (proposal §2: "probe every 10 s and also immediately when a waypoint is tapped… if a probe is still running when the next one is due, the next one is skipped and logged as skipped").

## Tasks
- [ ] `MeasurementService` with `foregroundServiceType="location"` and an ongoing notification
- [ ] Partial wake lock while running
- [ ] Timer: one probe every 10 s (coroutine)
- [ ] Waypoint tap → set current waypoint, write a WAYPOINT row, trigger an immediate probe (trigger = TAP)
- [ ] Busy flag: if a probe is running when the next is due → write a `SKIPPED` row
- [ ] Start/Stop from the activity; clean shutdown closes the CSV

## Done when
- With a mocked probe, CSV shows a row ~every 10 s, TAP rows on waypoint presses, and SKIPPED rows when a probe is slow.
