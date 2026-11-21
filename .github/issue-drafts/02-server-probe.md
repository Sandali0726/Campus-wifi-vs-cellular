# [server] Laptop probe server (/ping, /file 250 KB) + ngrok setup

Laptop server that the phones probe through a fixed Ngrok URL (proposal §2 block diagram: Network Probe → Ngrok Tunnel → Laptop Server).

## Tasks
- [ ] Python server (standard library only) with:
  - `GET /ping` → tiny response (latency probe)
  - `GET /file` → exactly 256 000 bytes, fixed content, `Content-Length`, `Cache-Control: no-store`
- [ ] Ignore query strings (`?n=<nonce>` used by the app for cache-busting)
- [ ] Server-side CSV log per request (time, path, client, bytes, duration) → ground truth for download timing
- [ ] `server/README.md`: run server, run `ngrok http --url=<static-domain> 8000`, note on the `ngrok-skip-browser-warning` header
- [ ] Simple self-test script

## Done when
- `curl localhost:8000/ping` and `/file` work; `/file` is 256 000 bytes; requests appear in the log.
