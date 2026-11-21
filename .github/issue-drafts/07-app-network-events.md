# [app] Log connection drops/switches with NetworkCallback

Log connection drops and switches (assignment: "point out where connectivity dropped or switched"; deliverable 5).

## Tasks
- [ ] `registerDefaultNetworkCallback`: on `onAvailable`, `onLost`, `onCapabilitiesChanged` (transport change) write an EVENT row immediately with the current waypoint
- [ ] Count failed probes alongside events

## Done when
- Turning Wi-Fi off/on during a run produces EVENT rows (LOST / AVAILABLE).
