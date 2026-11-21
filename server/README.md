# Probe server (laptop)

The phones send their probes to this server through a **fixed ngrok URL**
(proposal §2: Network Probe → Ngrok Tunnel → Laptop Server). It uses only the Python standard library.

| Endpoint | Response | Used for |
|---|---|---|
| `GET /ping` | `pong` (4 bytes) | latency (3 requests per probe) |
| `GET /file` | exactly **256 000 bytes** of fixed random data | download speed (1 per probe) |
| `GET /health` | JSON status | manual check before a walk |

The server ignores query strings, so the app can add `?n=<nonce>` to stop caching. Every response sends
`Cache-Control: no-store`. The `/file` content is random and seeded, so the tunnel can't compress it,
and it is the same every run.

## 1. Start the server

```bash
cd server
python probe_server.py --port 8000
```

Every request is appended to `server/logs/server_log_<date>.csv` with server time, path, status,
bytes sent and duration. Use this log as **ground truth** when checking the download timings and byte
counts from the phones. Copy each walk's log into `data/field-notes/`.

## 2. Start ngrok with a static domain

The proposal requires the **same public URL for every probe**. A free ngrok URL changes on every
restart, so claim your free static domain once in the ngrok dashboard
(*Domains → Create domain*), then run:

```bash
ngrok config add-authtoken <your-token>      # once
ngrok http --url=<your-static-domain>.ngrok-free.app 8000
```

Enter `https://<your-static-domain>.ngrok-free.app` as the server URL in the app on **both** phones.

**Browser warning page:** the free ngrok plan can return an HTML warning page instead of your response.
The app (and `self_test.py`) send the header `ngrok-skip-browser-warning: 1` to skip it. If `/file`
ever comes back with the wrong size, check this header first.

## 3. Self-test

```bash
python self_test.py                                        # local
python self_test.py https://<your-static-domain>.ngrok-free.app   # through the tunnel
```

## Before each walk

- Laptop plugged in, sleep and hibernate turned off, on a stable network.
- Server and ngrok both running; `self_test.py` passes through the ngrok URL.
- Note the laptop's network (campus LAN or Wi-Fi) in the walk log.

**Limitation for the report:** every probe goes phone → ngrok edge server → laptop, so the measured
latency includes the round trip to the ngrok edge. The detour is the same for Wi-Fi and cellular, so
comparing the two is still fair, but the absolute values are higher than a direct connection.
