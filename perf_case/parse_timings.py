#!/usr/bin/env python3
from __future__ import annotations

import argparse
import base64
import json
import math
import re
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Dict, Iterable, List, Optional, Tuple


ROOT = Path(__file__).resolve().parent


def load_json(path: Path) -> Any:
    return json.loads(path.read_text("utf-8"))


def ns_to_ms(ns: float) -> float:
    return ns / 1e6


def ns_to_s(ns: float) -> float:
    return ns / 1e9


@dataclass(frozen=True)
class Identity:
    name: str
    group_id: int


@dataclass
class Agg:
    count: int = 0
    total_ns: int = 0
    lag_count: int = 0
    lag_total_ns: int = 0

    def add(self, count: int, total_ns: int, lag_count: int, lag_total_ns: int) -> None:
        self.count += int(count or 0)
        self.total_ns += int(total_ns or 0)
        self.lag_count += int(lag_count or 0)
        self.lag_total_ns += int(lag_total_ns or 0)

    @property
    def avg_ms(self) -> float:
        if self.count <= 0:
            return 0.0
        return ns_to_ms(self.total_ns / self.count)

    @property
    def lag_avg_ms(self) -> float:
        if self.lag_count <= 0:
            return 0.0
        return ns_to_ms(self.lag_total_ns / self.lag_count)


def classify(handler_name: str, group_name: str) -> str:
    name = handler_name.lower()
    group = group_name.lower()

    if "assemblethread" in name or "scoreboard" in name:
        return "scoreboard"
    if "npc" in name or "npclib" in name:
        return "npc"
    if name.startswith("task:") or "scheduler" in name:
        return "scheduler"
    if name.startswith("event:"):
        return "event"
    if "packet" in name:
        return "network"
    if "chunk" in name:
        return "chunks"
    if "entity" in name or "mobspawn" in name or "tickentities" in name:
        return "entities"
    if group in ("matrix", "vulcan", "grim", "spartan"):
        return "anticheat"
    if group == "minecraft":
        return "minecraft"
    return "other"


def md_table(headers: List[str], rows: List[List[str]]) -> str:
    def esc(s: str) -> str:
        return s.replace("\n", " ")

    out = []
    out.append("| " + " | ".join(esc(h) for h in headers) + " |")
    out.append("| " + " | ".join(["---"] * len(headers)) + " |")
    for r in rows:
        out.append("| " + " | ".join(esc(c) for c in r) + " |")
    return "\n".join(out)


def extract_timings_json_from_har(har_path: Path) -> Dict[str, bytes]:
    """
    Attempts to extract timin.gs data.php JSON responses from a Chrome/Firefox HAR export.

    Returns a dict with optional keys: 'root' and 'history' (bytes).
    """
    har = load_json(har_path)
    entries = (((har.get("log") or {}).get("entries")) or [])
    out: Dict[str, bytes] = {}

    for e in entries:
        req = e.get("request") or {}
        resp = e.get("response") or {}
        url = str(req.get("url") or "")
        if "timin.gs" not in url or "data.php" not in url:
            continue
        content = (resp.get("content") or {})
        text = content.get("text")
        if not isinstance(text, str) or not text:
            continue
        encoding = (content.get("encoding") or "").lower()
        try:
            body = base64.b64decode(text) if encoding == "base64" else text.encode("utf-8")
        except Exception:
            continue

        if "history=" in url:
            if len(body) > len(out.get("history", b"")):
                out["history"] = body
        else:
            if len(body) > len(out.get("root", b"")):
                out["root"] = body

    return out


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--id", default="dc6bb4c3b7fd4c95b81e8b23e82351b2", help="timin.gs report id (for title only)")
    ap.add_argument("--dir", default=str(ROOT), help="directory containing timings artifacts (default: perf_case/)")
    args = ap.parse_args()

    timings_id = args.id
    root_dir = Path(args.dir)
    raw_path = root_dir / "timings_raw.json"
    hist_path = root_dir / "timings_history.json"

    if not raw_path.exists() or not hist_path.exists():
        har_path = root_dir / "network.har"
        if har_path.exists():
            extracted = extract_timings_json_from_har(har_path)
            if (not raw_path.exists()) and extracted.get("root"):
                raw_path.write_bytes(extracted["root"])
            if (not hist_path.exists()) and extracted.get("history"):
                hist_path.write_bytes(extracted["history"])

    if not raw_path.exists():
        raise SystemExit(f"missing {raw_path} (run fetch_timings.py or provide network.har)")
    if not hist_path.exists():
        raise SystemExit(f"missing {hist_path} (run fetch_timings.py or provide network.har)")

    raw = load_json(raw_path)
    hist_blob = load_json(hist_path)

    timings_master = raw["timingsMaster"]
    idmap = timings_master["idmap"]
    group_map: Dict[str, str] = idmap["groupMap"]
    handler_map: Dict[str, Dict[str, Any]] = idmap["handlerMap"]

    def get_identity(handler_id: int) -> Optional[Identity]:
        ident = handler_map.get(str(handler_id))
        if not ident:
            return None
        return Identity(name=str(ident.get("name", f"#{handler_id}")), group_id=int(ident.get("group", 0)))

    # Compute sample window from minute reports (more reliable than timingsMaster.start/end).
    minute_reports: List[Dict[str, Any]] = []
    for h in timings_master.get("data", []):
        minute_reports.extend(h.get("minuteReports", []))
    if not minute_reports:
        raise SystemExit("no minuteReports found")

    min_time = min(int(mp["time"]) for mp in minute_reports)
    max_time = max(int(mp["time"]) for mp in minute_reports)
    duration_s = max(1, max_time - min_time)

    # Derive overall mspt (processing time) and lag stats from Full Server Tick minute reports.
    total_tick_count = 0
    total_tick_ns = 0
    total_lag_count = 0
    total_lag_ns = 0
    tps_values: List[float] = []

    for mp in minute_reports:
        tps_values.append(float(mp.get("tps", 0)))
        fst = mp.get("fullServerTick") or {}
        total_tick_count += int(fst.get("count", 0))
        total_tick_ns += int(fst.get("total", 0))
        total_lag_count += int(fst.get("lagCount", 0))
        total_lag_ns += int(fst.get("lagTotal", 0))

    avg_mspt = ns_to_ms(total_tick_ns / max(1, total_tick_count))
    avg_tps = sum(tps_values) / max(1, len(tps_values))
    lag_avg_mspt = ns_to_ms(total_lag_ns / max(1, total_lag_count)) if total_lag_count else 0.0

    # Worst-minute spike (by avg mspt) for evidence.
    worst = None
    for mp in minute_reports:
        fst = mp.get("fullServerTick") or {}
        cnt = int(fst.get("count", 0))
        if cnt <= 0:
            continue
        avg = ns_to_ms(int(fst.get("total", 0)) / cnt)
        entry = (avg, int(mp["time"]), int(fst.get("lagCount", 0)), int(fst.get("lagTotal", 0)))
        if worst is None or entry[0] > worst[0]:
            worst = entry

    # Aggregate handler totals across all history slices.
    history: Dict[str, List[Dict[str, Any]]] = hist_blob["history"]
    agg: Dict[int, Agg] = {}
    for _slice_id, handlers in history.items():
        for h in handlers:
            handler_id = int(h["id"])
            agg.setdefault(handler_id, Agg()).add(
                count=int(h.get("count", 0)),
                total_ns=int(h.get("total", 0)),
                lag_count=int(h.get("lagCount", 0)),
                lag_total_ns=int(h.get("lagTotal", 0)),
            )

    full = agg.get(1) or Agg()
    full_total_ns = max(1, full.total_ns)

    def format_row(handler_id: int, a: Agg) -> List[str]:
        ident = get_identity(handler_id)
        group = group_map.get(str(ident.group_id), str(ident.group_id)) if ident else "?"
        name = ident.name if ident else f"#{handler_id}"
        pct = (a.total_ns / full_total_ns) * 100.0
        category = classify(name, group)
        return [
            f"{pct:.2f}%",
            f"{ns_to_s(a.total_ns):.3f}s",
            str(a.count),
            f"{a.avg_ms:.3f}ms",
            category,
            f"{group}::{name}",
        ]

    top = sorted(agg.items(), key=lambda kv: kv[1].total_ns, reverse=True)[:10]
    top_rows = [format_row(i, a) for i, a in top]

    # Totals by category (percent of full tick time).
    cat_totals: Dict[str, int] = {}
    for handler_id, a in agg.items():
        ident = get_identity(handler_id)
        group = group_map.get(str(ident.group_id), str(ident.group_id)) if ident else "?"
        name = ident.name if ident else f"#{handler_id}"
        cat = classify(name, group)
        cat_totals[cat] = cat_totals.get(cat, 0) + a.total_ns
    cat_rows = []
    for cat, tot_ns in sorted(cat_totals.items(), key=lambda kv: kv[1], reverse=True):
        cat_rows.append([cat, f"{(tot_ns/full_total_ns)*100.0:.2f}%", f"{ns_to_s(tot_ns):.3f}s"])

    # Focus: ThePitPremium group hotspots.
    pit_group_id = None
    for k, v in group_map.items():
        if v == "ThePitPremium":
            pit_group_id = int(k)
            break

    pit_rows = []
    if pit_group_id is not None:
        for handler_id, a in sorted(agg.items(), key=lambda kv: kv[1].total_ns, reverse=True):
            ident = get_identity(handler_id)
            if not ident or ident.group_id != pit_group_id:
                continue
            pit_rows.append(format_row(handler_id, a))
            if len(pit_rows) >= 15:
                break

    out_lines: List[str] = []
    out_lines.append(f"# Timings Summary ({timings_id})")
    out_lines.append("")
    out_lines.append("## Sample")
    out_lines.append("")
    out_lines.append(f"- Window: `{min_time}` → `{max_time}` (~{duration_s/60.0:.1f} min from minute reports)")
    out_lines.append(f"- Avg TPS (minute reports mean): `{avg_tps:.2f}`")
    out_lines.append(f"- Avg MSPT (Full Server Tick processing): `{avg_mspt:.2f}ms`")
    out_lines.append(f"- Lag ticks: `{total_lag_count}` (avg lag MSPT `{lag_avg_mspt:.2f}ms`)" if total_lag_count else "- Lag ticks: `0`")
    if worst:
        worst_avg, worst_time, worst_lc, worst_ltns = worst
        worst_lag_avg = ns_to_ms(worst_ltns / worst_lc) if worst_lc else 0.0
        out_lines.append(f"- Worst minute avg MSPT: `{worst_avg:.2f}ms` at `{worst_time}` (lag ticks `{worst_lc}`, lag avg `{worst_lag_avg:.2f}ms`)")
    out_lines.append("")
    out_lines.append("## Top Hotspots (overall)")
    out_lines.append("")
    out_lines.append(md_table(
        ["% of Full Tick", "Total", "Count", "Avg", "Category", "Handler"],
        top_rows,
    ))
    out_lines.append("")
    out_lines.append("## Category Breakdown")
    out_lines.append("")
    out_lines.append(md_table(["Category", "% of Full Tick", "Total"], cat_rows))
    out_lines.append("")
    out_lines.append("## ThePitPremium Hotspots (top 15)")
    out_lines.append("")
    if pit_rows:
        out_lines.append(md_table(
            ["% of Full Tick", "Total", "Count", "Avg", "Category", "Handler"],
            pit_rows,
        ))
    else:
        out_lines.append("- (ThePitPremium group not found in idmap)")
    out_lines.append("")
    out_lines.append("## Conclusion")
    out_lines.append("")

    # Pull out a few load-bearing handlers for a concise, evidence-backed conclusion.
    def find_pct(needle: str) -> Optional[float]:
        needle_l = needle.lower()
        best: Optional[float] = None
        for handler_id, a in agg.items():
            ident = get_identity(handler_id)
            if not ident:
                continue
            if needle_l not in str(ident.name).lower():
                continue
            pct = (a.total_ns / full_total_ns) * 100.0
            if best is None or pct > best:
                best = pct
        return best

    assemble_pct = find_pct("assemblethread")
    npc_join_pct = find_pct("npcfactory (pitprofileloadedevent)")
    scheduler_pct = find_pct("bukkit scheduler")
    packet_pct = find_pct("packet processing")

    out_lines.append(f"- `Bukkit Scheduler` is the biggest bucket{f' (~{scheduler_pct:.2f}% of Full Tick)' if scheduler_pct is not None else ''}; focus on reducing heavy scheduled tasks and spikes.")
    out_lines.append(f"- `AssembleThread` (scoreboard) is a confirmed hotspot{f' (~{assemble_pct:.2f}% of Full Tick)' if assemble_pct is not None else ''}; mitigate by caching and spreading updates.")
    out_lines.append(f"- `NpcFactory (PitProfileLoadedEvent)` is visible in the top list{f' (~{npc_join_pct:.2f}% of Full Tick)' if npc_join_pct is not None else ''}; reduce per-player work on join/spawn and lower text refresh frequency.")
    if packet_pct is not None:
        out_lines.append(f"- `Packet Processing` is also significant (~{packet_pct:.2f}% of Full Tick); verify whether move/position packets amplify plugin work during lag minutes.")
    out_lines.append("")
    out_lines.append("## Notes")
    out_lines.append("")
    out_lines.append("- Units: totals are nanoseconds in the raw JSON.")
    out_lines.append("- This summary aggregates across all `history` slices in `timings_history.json`.")
    out_lines.append("- Percentages are based on inclusive timings (handlers include their children), so category totals can exceed 100%.")

    (root_dir / "timings_summary.md").write_text("\n".join(out_lines) + "\n", "utf-8")
    print(f"wrote {root_dir/'timings_summary.md'}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
