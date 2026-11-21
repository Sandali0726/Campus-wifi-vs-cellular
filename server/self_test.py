"""Quick check of the probe server, locally or through the ngrok URL.

  python self_test.py                          # http://localhost:8000
  python self_test.py https://<static-domain>  # through ngrok, like the phones
"""
import statistics
import sys
import time
import urllib.request
import uuid

BASE = (sys.argv[1] if len(sys.argv) > 1 else "http://localhost:8000").rstrip("/")
EXPECTED = 256_000


def get(path):
    req = urllib.request.Request(f"{BASE}{path}?n={uuid.uuid4().hex}",
                                 headers={"ngrok-skip-browser-warning": "1",
                                          "Cache-Control": "no-cache"})
    t0 = time.perf_counter()
    with urllib.request.urlopen(req, timeout=15) as r:
        body = r.read()
    return body, (time.perf_counter() - t0) * 1000


def main():
    pings = []
    for _ in range(3):
        body, ms = get("/ping")
        assert body == b"pong", f"/ping returned {body[:50]!r}"
        pings.append(ms)
    print(f"/ping  ok   latencies ms = {[round(p, 1) for p in pings]}  median = {statistics.median(pings):.1f}")

    body, ms = get("/file")
    assert len(body) == EXPECTED, f"/file returned {len(body)} bytes, expected {EXPECTED}"
    print(f"/file  ok   {len(body)} B in {ms:.1f} ms  ->  {len(body) * 8 / ms / 1000:.2f} Mbps")

    body2, _ = get("/file")
    assert body == body2, "/file content changed between requests"
    print("/file  ok   content identical across requests")
    print("ALL TESTS PASSED")


if __name__ == "__main__":
    main()
