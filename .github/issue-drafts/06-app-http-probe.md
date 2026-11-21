# [app] HTTP network probe: latency (3 pings) and 250 KB download speed

The network probe (proposal: latency = 3 HTTP requests; download of a fixed 250 KB file).

## Tasks
- [ ] `HttpURLConnection` with connect/read timeouts; header `ngrok-skip-browser-warning: 1`; random `?n=` nonce; no caching
- [ ] 3 × `GET /ping`, each timed with `SystemClock.elapsedRealtimeNanos()`; log all 3 raw values + **median and mean** (proposal mentions both)
- [ ] 1 × `GET /file`: record bytes, time-to-first-byte, total time; Mbps = bytes×8 / transfer time
- [ ] Verify downloaded bytes == 256 000, otherwise status FAILED
- [ ] On exception/timeout → status FAILED with error class

## Done when
- Probe returns a result object; failures don't crash the service.
