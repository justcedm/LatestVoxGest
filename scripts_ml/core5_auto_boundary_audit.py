"""Offline, read-only analysis of unchanged Core5 AUTO_LEGACY event evidence."""
from __future__ import annotations

import argparse
import json
import statistics
from pathlib import Path

import numpy as np


def no_hand_runs(frames):
    runs = []
    start = None
    for index, frame in enumerate(frames):
        neutral = frame["pose_present"] and not frame["left_present"] and not frame["right_present"]
        if neutral and start is None:
            start = index
        if start is not None and (not neutral or index == len(frames) - 1):
            end = index if neutral else index - 1
            runs.append({"start_index": start, "end_index": end, "count": end - start + 1,
                         "start_ms": frames[start]["timestamp_ms"],
                         "end_ms": frames[end]["timestamp_ms"]})
            start = None
    return runs


def last_hand_motion(frames, threshold=0.08):
    """Feature-delta proxy, not an independently observed human sign-end time."""
    last = None
    for before, after in zip(frames, frames[1:]):
        common_left = before["left_present"] and after["left_present"]
        common_right = before["right_present"] and after["right_present"]
        if not common_left and not common_right:
            continue
        a, b = np.asarray(before["canonical"], dtype=np.float32), np.asarray(after["canonical"], dtype=np.float32)
        blocks = []
        if common_left:
            blocks.append(b[99:162] - a[99:162])
        if common_right:
            blocks.append(b[162:225] - a[162:225])
        delta = float(np.linalg.norm(np.concatenate(blocks)))
        if delta >= threshold:
            last = {"timestamp_ms": after["timestamp_ms"], "hand_feature_delta_l2": delta}
    return last


def analyze(event, manual):
    frames = event["frames"]
    runs = no_hand_runs(frames)
    end = event.get("event_end_ms")
    last_hand = next((f["timestamp_ms"] for f in reversed(frames)
                      if f["left_present"] or f["right_present"]), None)
    last_motion = last_hand_motion(frames)
    first = frames[0] if frames else None
    return {
        "event_id": event["event_id"],
        "expected_test_label": event["expected_test_label"],
        "collector_start_reason": "SIGN_ENTRY_INFERRED" if first and first["pose_present"] and
            (first["left_present"] or first["right_present"]) else "NOT_DETERMINABLE_FROM_SAVED_FRAMES",
        "first_saved_frame_ms": event.get("event_start_ms"),
        "end_reason": event["termination"],
        "end_ms": end,
        "event_duration_ms": end - event["event_start_ms"] if isinstance(end, int) and isinstance(event.get("event_start_ms"), int) else None,
        "last_hand_present_ms": last_hand,
        "last_hand_to_end_ms": end - last_hand if isinstance(end, int) and isinstance(last_hand, int) else None,
        "last_hand_motion_proxy_ms": last_motion["timestamp_ms"] if last_motion else None,
        "last_motion_proxy_to_end_ms": end - last_motion["timestamp_ms"] if isinstance(end, int) and last_motion else None,
        "motion_proxy_definition": "common anatomical hand feature L2 delta >= 0.08 between consecutive results; not human-validated sign end",
        "no_hand_runs": runs,
        "max_consecutive_pose_no_hand": max((run["count"] for run in runs), default=0),
        "trailing_pose_no_hand": runs[-1]["count"] if runs and runs[-1]["end_index"] == len(frames)-1 else 0,
        "hand_present_last_frame": bool(frames and (frames[-1]["left_present"] or frames[-1]["right_present"])),
        "hand_persistence_after_physical_neutral": "UNKNOWN_WITHOUT_OPERATOR_MARKER_OR_VIDEO",
        "total_observations": event["raw_frame_count"],
        "effective_mediapipe_rate_hz": event.get("processed_fps"),
        "raw_top1": event.get("raw_top1"),
        "confidence": event.get("confidence"),
        "accepted": event["accepted"],
        "gate_reason": event["gate_reason"],
        "manual_reference_median_duration_ms": statistics.median(
            m["event_end_ms"] - m["event_start_ms"] for m in manual
            if isinstance(m.get("event_end_ms"), int) and isinstance(m.get("event_start_ms"), int)) if manual else None,
        "manual_reference_count": len(manual),
    }


def run(events_dir: Path, output: Path):
    events = []
    for path in events_dir.glob("*.json"):
        if path.name.startswith("startup-") or ".candidate-replay." in path.name:
            continue
        event = json.loads(path.read_text(encoding="utf-8"))
        if event.get("profile") == "FSL_CORE5_SIM10FPS_V1":
            events.append(event)
    manual_by_label = {}
    for event in events:
        if event.get("boundary_mode") == "MANUAL" and event.get("termination") == "MANUAL_END":
            manual_by_label.setdefault(event["expected_test_label"], []).append(event)
    auto = [analyze(event, manual_by_label.get(event["expected_test_label"], []))
            for event in events if event.get("boundary_mode") == "AUTO_LEGACY"]
    if output.exists():
        raise FileExistsError("Automatic audit evidence already exists; preserve it")
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps({"events": auto, "collector_release_requirement":
        "three consecutive pose-present observations with no hand",
        "limitations": "Physical return to neutral and true last meaningful human motion are not directly observable from landmarks alone"},
        indent=2) + "\n", encoding="utf-8")
    return {"auto_events": len(auto), "output": str(output)}


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--events-dir", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    print(json.dumps(run(args.events_dir, args.output), indent=2))
