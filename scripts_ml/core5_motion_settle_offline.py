"""Read-only replay of the existing debug AUTO_MOTION boundary over saved Core5 frames.

This assesses event termination only. It cannot establish linguistic sign end or
post-truncation classifier/OOD safety without a separately validated ground truth.
"""
from __future__ import annotations

import argparse
import json
from pathlib import Path

import numpy as np


def replay_existing_debug_rule(frames, threshold=.08):
    """Match Core5Recorder's hold/floor, with an offline-only delta threshold."""
    motion_started = None
    still_since = None
    first_hand = None
    last_delta = None
    for index, frame in enumerate(frames):
        if not (frame["pose_present"] and (frame["left_present"] or frame["right_present"])):
            continue
        stamp = frame["timestamp_ms"]
        if motion_started is None:
            motion_started = stamp
            first_hand = index
        if index == 0:
            continue
        previous = frames[index - 1]
        if previous["pose_present"] and (previous["left_present"] or previous["right_present"]):
            before = np.asarray(previous["canonical"], dtype=np.float32)
            after = np.asarray(frame["canonical"], dtype=np.float32)
            last_delta = float(np.linalg.norm(after - before))
            if last_delta < threshold:
                if still_since is None:
                    still_since = stamp
                if stamp - motion_started >= 1200 and stamp - still_since >= 900:
                    return {"trigger_index": index, "trigger_ms": stamp,
                            "first_hand_index": first_hand, "first_hand_ms": motion_started,
                            "final_delta_l2": last_delta,
                            "dwell_ms": stamp - still_since}
            else:
                still_since = None
        else:
            still_since = None
    return {"trigger_index": None, "trigger_ms": None,
            "first_hand_index": first_hand, "first_hand_ms": motion_started,
            "final_delta_l2": last_delta, "dwell_ms": None}


def delta_statistics(frames):
    pairs = []
    for before, after in zip(frames, frames[1:]):
        if not (before["pose_present"] and after["pose_present"] and
                (before["left_present"] or before["right_present"]) and
                (after["left_present"] or after["right_present"])):
            continue
        dt = after["timestamp_ms"] - before["timestamp_ms"]
        if dt <= 0:
            continue
        old = np.asarray(before["canonical"], dtype=np.float32)
        new = np.asarray(after["canonical"], dtype=np.float32)
        delta = float(np.linalg.norm(new - old))
        pairs.append((delta, delta * 1000 / dt))
    def summarize(values):
        return {"n": len(values), "median": float(np.median(values)) if values else None,
                "p90": float(np.percentile(values, 90)) if values else None,
                "max": max(values) if values else None}
    return {"all_delta_l2": summarize([d for d, _ in pairs]),
            "tail_10_delta_l2": summarize([d for d, _ in pairs[-10:]]),
            "tail_10_delta_l2_per_second": summarize([v for _, v in pairs[-10:]])}


def run(events_dir: Path, output: Path):
    if output.exists():
        raise FileExistsError("Preserve prior offline boundary evidence")
    rows = []
    for path in sorted(events_dir.glob("*.json")):
        if path.name.startswith("startup-") or ".candidate-replay." in path.name:
            continue
        event = json.loads(path.read_text(encoding="utf-8"))
        if event.get("profile") != "FSL_CORE5_SIM10FPS_V1":
            continue
        if event.get("boundary_mode") not in {"AUTO_LEGACY", "MANUAL"}:
            continue
        label = event.get("expected_test_label", "")
        if event["boundary_mode"] == "MANUAL" and not label.startswith("NON_SIGN:"):
            continue
        frames = event["frames"]
        simulated = replay_existing_debug_rule(frames)
        sensitivity = {str(threshold): replay_existing_debug_rule(frames, threshold)["trigger_ms"]
                       for threshold in (.08, .2, .4, .8, 1.2, 2.0)}
        end = frames[-1]["timestamp_ms"] if frames else None
        rows.append({"event_id": event["event_id"], "intended": label,
                     "source_boundary": event["boundary_mode"],
                     "source_end_reason": event["termination"],
                     "source_raw_top1": event.get("raw_top1"),
                     "source_gate_reason": event["gate_reason"],
                     "source_accepted": event["accepted"],
                     "source_frames": len(frames),
                     "source_end_ms": end,
                     "debug_motion_trigger_before_source_end":
                         simulated["trigger_ms"] is not None,
                     "trigger_to_source_end_ms": end - simulated["trigger_ms"]
                         if end is not None and simulated["trigger_ms"] is not None else None,
                     "sensitivity_trigger_ms": sensitivity,
                     "observed_feature_delta": delta_statistics(frames),
                     **simulated})
    rows.sort(key=lambda row: (row["source_boundary"], row["intended"], row["event_id"]))
    counts = {}
    for row in rows:
        group = row["source_boundary"] + ":" + row["intended"]
        item = counts.setdefault(group, {"events": 0, "would_trigger": 0})
        item["events"] += 1
        item["would_trigger"] += bool(row["debug_motion_trigger_before_source_end"])
    sensitivity_counts = {str(threshold): {group: sum(
        row["source_boundary"] + ":" + row["intended"] == group and
        row["sensitivity_trigger_ms"][str(threshold)] is not None for row in rows)
        for group in counts} for threshold in (.08, .2, .4, .8, 1.2, 2.0)}
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps({"rule": "EXISTING_DEBUG_AUTO_MOTION_0.08_L2_900MS_1200MS",
                                  "contract": "saved timestamped 225-vector observations",
                                  "limitations": ["No human-verified sign-end timestamp",
                                                  "No classifier or gate rerun on truncated event",
                                                  "MANUAL negatives do not reproduce automatic arming"],
                                  "counts": counts, "sensitivity_counts": sensitivity_counts,
                                  "rows": rows}, indent=2) + "\n", encoding="utf-8")
    return {"events": len(rows), "counts": counts,
            "sensitivity_counts": sensitivity_counts, "output": str(output)}


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--events-dir", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    print(json.dumps(run(args.events_dir, args.output), indent=2))
