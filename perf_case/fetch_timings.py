#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import os
import re
import sys
import time
from pathlib import Path
from typing import Any, Dict, Optional, Tuple

import requests


def write_text(path: Path, text: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, "utf-8")


def write_bytes(path: Path, data: bytes) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(data)


def log_fetch(log_path: Path, title: str, url: str, resp: Optional[requests.Response], err: Optional[BaseException]) -> None:
    lines = []
    lines.append(f"## {title}")
    lines.append(f"time={time.strftime('%Y-%m-%dT%H:%M:%S%z')}")
    lines.append(f"url={url}")
    if err is not None:
        lines.append(f"error={type(err).__name__}: {err}")
    if resp is not None:
        lines.append(f"status={resp.status_code}")
        lines.append(f"content_type={resp.headers.get('content-type', '')}")
        lines.append(f"content_length={resp.headers.get('content-length', '')}")
        lines.append("headers:")
        for k, v in resp.headers.items():
            lines.append(f"  {k}: {v}")
    lines.append("")
    log_path.parent.mkdir(parents=True, exist_ok=True)
    with log_path.open("a", encoding="utf-8") as f:
        f.write("\n".join(lines) + "\n")


def get(session: requests.Session, url: str, *, timeout: int = 30) -> requests.Response:
    resp = session.get(url, timeout=timeout, allow_redirects=True)
    return resp


def extract_data_php_from_sourcemap(sourcemap_json: Dict[str, Any]) -> str:
    contents = sourcemap_json.get("sourcesContent") or []
    # Look for the exact frontend call: xhr('data.php?' + qs.stringify(...))
    needle = "data.php?"
    for c in contents:
        if not isinstance(c, str):
            continue
        idx = c.find(needle)
        if idx != -1:
            return "data.php"
    # fallback: regex search
    joined = "\n".join(c for c in contents if isinstance(c, str) and "data.php" in c)
    m = re.search(r"['\"](data\\.php)['\"]", joined)
    if m:
        return m.group(1)
    raise RuntimeError("Could not locate data.php endpoint in timings.js.map")


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--id", required=True, help="timin.gs report id")
    ap.add_argument("--out", default="perf_case", help="output directory (default: perf_case)")
    ap.add_argument("--base", default="https://timin.gs", help="base URL (default: https://timin.gs)")
    ap.add_argument("--no-proxy", action="store_true", help="ignore HTTP(S)_PROXY env vars")
    ap.add_argument("--keep-sourcemap", action="store_true", help="keep timings.js.map on disk (large file)")
    args = ap.parse_args()

    out_dir = Path(args.out)
    out_dir.mkdir(parents=True, exist_ok=True)

    fetch_log = out_dir / "fetch_log.txt"
    header = f"# fetch_timings.py started\nid={args.id}\nbase={args.base}\nno_proxy={args.no_proxy}\n\n"
    fetch_log.parent.mkdir(parents=True, exist_ok=True)
    with fetch_log.open("a", encoding="utf-8") as f:
        f.write(header)

    session = requests.Session()
    session.headers.update(
        {
            "User-Agent": "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121 Safari/537.36",
            "Accept-Language": "en-US,en;q=0.9",
        }
    )
    if args.no_proxy:
        session.trust_env = False

    page_url = f"{args.base}/?id={args.id}"
    try:
        resp = get(session, page_url)
        log_fetch(fetch_log, "GET timings page", page_url, resp, None)
        resp.raise_for_status()
        write_bytes(out_dir / "timings_page.html", resp.content)
    except Exception as e:
        log_fetch(fetch_log, "GET timings page", page_url, getattr(e, "response", None), e)
        raise

    # Fetch source map (used to discover the JSON API endpoint reliably).
    map_url = f"{args.base}/dist/timings.js.map"
    try:
        resp = get(session, map_url)
        log_fetch(fetch_log, "GET timings.js.map", map_url, resp, None)
        resp.raise_for_status()
        sourcemap_bytes = resp.content
        if args.keep_sourcemap:
            write_bytes(out_dir / "timings.js.map", sourcemap_bytes)
    except Exception as e:
        log_fetch(fetch_log, "GET timings.js.map", map_url, getattr(e, "response", None), e)
        raise

    sourcemap = json.loads(sourcemap_bytes.decode("utf-8"))
    data_php_path = extract_data_php_from_sourcemap(sourcemap)

    api_root_url = f"{args.base}/{data_php_path}?id={args.id}"
    try:
        resp = get(session, api_root_url)
        log_fetch(fetch_log, "GET data.php root", api_root_url, resp, None)
        resp.raise_for_status()
        write_bytes(out_dir / "timings_raw.json", resp.content)
    except Exception as e:
        log_fetch(fetch_log, "GET data.php root", api_root_url, getattr(e, "response", None), e)
        raise

    raw = json.loads((out_dir / "timings_raw.json").read_text("utf-8"))
    histories = raw["timingsMaster"]["data"]
    ids = [str(h["id"]) for h in histories]
    write_text(out_dir / "history_ids.txt", ",".join(ids) + "\n")

    api_hist_url = f"{args.base}/{data_php_path}?id={args.id}&history={','.join(ids)}"
    try:
        resp = get(session, api_hist_url, timeout=60)
        log_fetch(fetch_log, "GET data.php history", api_hist_url, resp, None)
        resp.raise_for_status()
        write_bytes(out_dir / "timings_history.json", resp.content)
    except Exception as e:
        log_fetch(fetch_log, "GET data.php history", api_hist_url, getattr(e, "response", None), e)
        raise

    write_text(out_dir / "network_dump.json", json.dumps({"urls": [page_url, map_url, api_root_url, api_hist_url]}, indent=2))
    print(f"OK: wrote {out_dir/'timings_raw.json'} and {out_dir/'timings_history.json'}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
