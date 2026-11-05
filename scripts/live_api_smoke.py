#!/usr/bin/env python3
"""Live smoke checks for public job APIs and the pinned Hugging Face model endpoint.

This intentionally checks metadata/JSON only. It never downloads the 696 MB model.
"""
from __future__ import annotations

import json
import sys
import urllib.error
import urllib.request

USER_AGENT = "HamariMehnatAndroidCISmoke/1.0"
MODEL_URL = (
    "https://huggingface.co/LiquidAI/LFM2.5-1.2B-Instruct-GGUF/"
    "resolve/430bea1/LFM2.5-1.2B-Instruct-QAD-Q4_0.gguf"
)


def request(url: str, method: str = "GET"):
    req = urllib.request.Request(url, method=method, headers={"User-Agent": USER_AGENT})
    return urllib.request.urlopen(req, timeout=30)


def check_json_feed(name: str, url: str, collection: str) -> None:
    with request(url) as response:
        if response.status != 200:
            raise RuntimeError(f"{name}: HTTP {response.status}")
        payload = json.loads(response.read().decode("utf-8"))
    values = payload.get(collection)
    if not isinstance(values, list):
        raise RuntimeError(f"{name}: JSON field '{collection}' is not an array")
    print(f"PASS {name}: HTTP 200, {len(values)} items")


def check_model_metadata() -> None:
    with request(MODEL_URL, "HEAD") as response:
        if response.status != 200:
            raise RuntimeError(f"Hugging Face model endpoint: HTTP {response.status}")
        size = response.headers.get("Content-Length")
        if size and int(size) != 695_755_488:
            raise RuntimeError(f"Model endpoint size mismatch: expected 695755488, received {size}")
        print(f"PASS Hugging Face model endpoint: HTTP 200, Content-Length={size or 'not supplied'}")


def main() -> int:
    checks = [
        ("Arbeitnow", "https://www.arbeitnow.com/api/job-board-api?page=1", "data"),
        ("Remotive", "https://remotive.com/api/remote-jobs?search=mechanic&limit=5", "jobs"),
        ("Himalayas", "https://himalayas.app/jobs/api/search?q=mechanic&page=1", "jobs"),
        ("Jobicy", "https://jobicy.com/api/v2/remote-jobs?count=5&tag=mechanic", "jobs"),
    ]
    failed = []
    for name, url, collection in checks:
        try:
            check_json_feed(name, url, collection)
        except Exception as exc:
            failed.append(f"{name}: {type(exc).__name__}: {exc}")
            print(f"FAIL {failed[-1]}", file=sys.stderr)
    try:
        check_model_metadata()
    except Exception as exc:
        failed.append(f"Hugging Face model endpoint: {type(exc).__name__}: {exc}")
        print(f"FAIL {failed[-1]}", file=sys.stderr)
    if failed:
        print(f"{len(failed)} live smoke check(s) failed.", file=sys.stderr)
        return 1
    print("All live public endpoint smoke checks passed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
