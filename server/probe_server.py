"""Probe server for the Wi-Fi vs cellular measurement study (CS4473, Group 11).

Runs on the laptop and is exposed to the phones through a fixed ngrok URL.

Endpoints
  GET /ping   -> 4-byte body "pong"            (latency probe)
  GET /file   -> exactly FILE_SIZE bytes       (download-speed probe, 250 KB)
  GET /health -> short JSON status             (manual check before a walk)

Query strings (e.g. ?n=<nonce> used by the app for cache-busting) are ignored.
Every request is appended to logs/server_log_<date>.csv so the phone's download
timing can be checked against the server side (ground truth, proposal §3).

Standard library only:  python probe_server.py [--port 8000]
"""

import argparse
import csv
import json
import os
import random
import threading
import time
from datetime import datetime, timezone
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlsplit

FILE_SIZE = 256_000  # bytes (250 KB) - must match ProbeConfig.FILE_SIZE in the app
FILE_SEED = 4473     # fixed seed -> identical file content every run
CHUNK = 16 * 1024

# Random (incompressible) bytes so no proxy/tunnel can shrink the transfer.
_rng = random.Random(FILE_SEED)
PAYLOAD = bytes(_rng.getrandbits(8) for _ in range(FILE_SIZE))

LOG_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "logs")
LOG_FIELDS = ["server_time_iso", "epoch_ms", "client", "path", "query",
              "status", "bytes_sent", "duration_ms", "user_agent"]
_log_lock = threading.Lock()


def log_request(row):
    os.makedirs(LOG_DIR, exist_ok=True)
    path = os.path.join(LOG_DIR, f"server_log_{datetime.now():%Y%m%d}.csv")
    with _log_lock:
        new = not os.path.exists(path)
        with open(path, "a", newline="") as f:
            w = csv.DictWriter(f, fieldnames=LOG_FIELDS)
            if new:
                w.writeheader()
            w.writerow(row)


class ProbeHandler(BaseHTTPRequestHandler):
    server_version = "ProbeServer/1.0"
    protocol_version = "HTTP/1.1"  # keep-alive allowed; Content-Length always set

    def _send(self, status, body, content_type):
        self.send_response(status)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-store, no-cache, must-revalidate")
        self.send_header("Pragma", "no-cache")
        self.end_headers()
        sent = 0
        for i in range(0, len(body), CHUNK):
            self.wfile.write(body[i:i + CHUNK])
            sent += len(body[i:i + CHUNK])
        return sent

    def do_GET(self):
        t0 = time.perf_counter()
        parts = urlsplit(self.path)
        route = parts.path.rstrip("/") or "/"
        status, sent = 200, 0
        try:
            if route == "/ping":
                sent = self._send(200, b"pong", "text/plain")
            elif route == "/file":
                sent = self._send(200, PAYLOAD, "application/octet-stream")
            elif route == "/health":
                body = json.dumps({"ok": True, "file_size": FILE_SIZE,
                                   "server_time": datetime.now(timezone.utc).isoformat()}).encode()
                sent = self._send(200, body, "application/json")
            else:
                status = 404
                sent = self._send(404, b"not found", "text/plain")
        except (BrokenPipeError, ConnectionResetError):
            status = 499  # client closed the connection mid-transfer
        finally:
            log_request({
                "server_time_iso": datetime.now(timezone.utc).isoformat(timespec="milliseconds"),
                "epoch_ms": int(time.time() * 1000),
                # ngrok forwards the phone's public IP; keep only that (no device IDs)
                "client": self.headers.get("X-Forwarded-For", self.client_address[0]),
                "path": route,
                "query": parts.query,
                "status": status,
                "bytes_sent": sent,
                "duration_ms": round((time.perf_counter() - t0) * 1000, 2),
                "user_agent": self.headers.get("User-Agent", ""),
            })

    def log_message(self, fmt, *args):  # one short console line per request
        print(f"{datetime.now():%H:%M:%S} {self.command} {self.path} -> {args[1] if len(args) > 1 else ''}")


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--host", default="0.0.0.0")
    ap.add_argument("--port", type=int, default=8000)
    args = ap.parse_args()
    httpd = ThreadingHTTPServer((args.host, args.port), ProbeHandler)
    print(f"Probe server on http://{args.host}:{args.port}  (/ping, /file = {FILE_SIZE} B, /health)")
    print(f"Request log: {LOG_DIR}")
    try:
        httpd.serve_forever()
    except KeyboardInterrupt:
        print("\nstopped")


if __name__ == "__main__":
    main()
