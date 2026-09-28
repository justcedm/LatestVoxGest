"""Offline-only timestamped Core5 motion/quality descriptors from saved Samsung events.

This does not train, change Android, or label a linguistic sign-end instant.
"""
from __future__ import annotations

import argparse
import csv
import json
from pathlib import Path

import numpy as np

SHOULDERS = (11, 12)
ELBOWS = {"left": 13, "right": 14}
WRISTS = {"left": 15, "right": 16}
MCP = (5, 9, 13, 17)
PALM = (0, 5, 9, 13, 17)


def med_mad_percentiles(values):
    finite = np.asarray([v for v in values if v is not None and np.isfinite(v)], dtype=np.float64)
    if finite.size == 0:
        return {"n": 0, "median": None, "mad": None, "p10": None, "p90": None, "p95": None}
    median = float(np.median(finite))
    return {"n": int(finite.size), "median": median,
            "mad": float(np.median(np.abs(finite - median))),
            "p10": float(np.percentile(finite, 10)),
            "p90": float(np.percentile(finite, 90)),
            "p95": float(np.percentile(finite, 95))}


def geometry(frame):
    if not frame["pose_present"]:
        return None
    pose = np.asarray(frame["pose"], dtype=np.float64)
    if pose.shape != (33, 3) or not np.isfinite(pose).all():
        raise ValueError("Invalid pose coordinates")
    shoulder_width = float(np.linalg.norm(pose[11, :2] - pose[12, :2]))
    if shoulder_width < .04:
        return None
    anchor = (pose[11, :2] + pose[12, :2]) / 2
    sides = {}
    for side in ("left", "right"):
        if not frame[side + "_present"]:
            continue
        hand = np.asarray(frame[side], dtype=np.float64)
        if hand.shape != (21, 3) or not np.isfinite(hand).all():
            raise ValueError("Invalid hand coordinates")
        scale = float(np.median(np.linalg.norm(hand[list(MCP), :2] - hand[0, :2], axis=1)))
        if scale < .008:
            continue
        palm = np.median(hand[list(PALM), :2], axis=0)
        sides[side] = {"palm_torso_xy": (palm - anchor) / shoulder_width,
                       "fingers_wrist_xy": (hand[1:, :2] - hand[0, :2]) / scale,
                       "fingers_wrist_z": (hand[1:, 2] - hand[0, 2]) / scale,
                       "arm_xy": np.stack(((pose[WRISTS[side], :2] - pose[SHOULDERS[0 if side == "left" else 1], :2]) / shoulder_width,
                                           (pose[ELBOWS[side], :2] - pose[SHOULDERS[0 if side == "left" else 1], :2]) / shoulder_width)),
                       "hand_scale_xy": scale}
    return {"shoulder_width_xy": shoulder_width, "sides": sides}


def timeline(event):
    frames = event["frames"]
    shapes = [geometry(frame) for frame in frames]
    output = []
    for index, frame in enumerate(frames):
        g = shapes[index]
        dt = frame["timestamp_ms"] - frames[index - 1]["timestamp_ms"] if index else None
        if dt is not None and dt <= 0:
            raise ValueError("Non-increasing timestamps: " + event["event_id"])
        row = {"event_id": event["event_id"], "expected": event["expected_test_label"],
               "boundary": event["boundary_mode"], "source_end": event["termination"],
               "index": index, "timestamp_ms": frame["timestamp_ms"], "dt_ms": dt,
               "pose": bool(frame["pose_present"]),
               "left": bool(frame["left_present"]), "right": bool(frame["right_present"]),
               "shoulder_width_xy": g["shoulder_width_xy"] if g else None,
               "hand_scale_left_xy": g["sides"].get("left", {}).get("hand_scale_xy") if g else None,
               "hand_scale_right_xy": g["sides"].get("right", {}).get("hand_scale_xy") if g else None,
               "translation_xy_per_s": None, "articulation_xy_per_s": None,
               "articulation_z_per_s": None, "arm_xy_per_s": None,
               "common_tracked_sides": 0}
        if index and g and shapes[index - 1]:
            prior = shapes[index - 1]
            common = set(g["sides"]) & set(prior["sides"])
            row["common_tracked_sides"] = len(common)
            if common:
                trans, articulation, depth, arm = [], [], [], []
                for side in common:
                    now, before = g["sides"][side], prior["sides"][side]
                    scale = 1000 / dt
                    trans.append(float(np.linalg.norm(now["palm_torso_xy"] - before["palm_torso_xy"]) * scale))
                    articulation.append(float(np.median(np.linalg.norm(
                        now["fingers_wrist_xy"] - before["fingers_wrist_xy"], axis=1)) * scale))
                    depth.append(float(np.median(np.abs(
                        now["fingers_wrist_z"] - before["fingers_wrist_z"])) * scale))
                    arm.append(float(np.median(np.linalg.norm(now["arm_xy"] - before["arm_xy"], axis=1)) * scale))
                # Two-hand event stays active if either anatomical hand is moving.
                row["translation_xy_per_s"] = max(trans)
                row["articulation_xy_per_s"] = max(articulation)
                row["articulation_z_per_s"] = max(depth)
                row["arm_xy_per_s"] = max(arm)
        output.append(row)
    return output


def run(events_dir: Path, output_stem: Path):
    csv_path, json_path = output_stem.with_suffix(".csv"), output_stem.with_suffix(".json")
    if csv_path.exists() or json_path.exists():
        raise FileExistsError("Preserve earlier descriptor evidence")
    rows, summary = [], []
    for path in sorted(events_dir.glob("*.json")):
        if path.name.startswith("startup-") or ".candidate-replay." in path.name:
            continue
        event = json.loads(path.read_text(encoding="utf-8"))
        if event.get("profile") != "FSL_CORE5_SIM10FPS_V1" or event.get("boundary_mode") not in {"MANUAL", "AUTO_LEGACY"}:
            continue
        if not event.get("frames"):
            continue
        event_rows = timeline(event)
        rows.extend(event_rows)
        tail = event_rows[-10:]
        summary.append({"event_id": event["event_id"], "expected": event["expected_test_label"],
                        "boundary": event["boundary_mode"], "source_end": event["termination"],
                        "frames": len(event_rows), "duration_ms": event_rows[-1]["timestamp_ms"] - event_rows[0]["timestamp_ms"],
                        "pose_ratio": sum(r["pose"] for r in event_rows) / len(event_rows),
                        "left_ratio": sum(r["left"] for r in event_rows) / len(event_rows),
                        "right_ratio": sum(r["right"] for r in event_rows) / len(event_rows),
                        "tracking_gap_pairs": sum(r["common_tracked_sides"] == 0 for r in event_rows[1:]),
                        "all": {key: med_mad_percentiles([r[key] for r in event_rows]) for key in
                                ("translation_xy_per_s", "articulation_xy_per_s", "arm_xy_per_s", "articulation_z_per_s")},
                        "tail10": {key: med_mad_percentiles([r[key] for r in tail]) for key in
                                   ("translation_xy_per_s", "articulation_xy_per_s", "arm_xy_per_s", "articulation_z_per_s")}})
    output_stem.parent.mkdir(parents=True, exist_ok=True)
    if not rows:
        raise ValueError("No Core5 Samsung frames")
    with csv_path.open("w", encoding="utf-8", newline="") as stream:
        writer = csv.DictWriter(stream, fieldnames=list(rows[0]))
        writer.writeheader()
        writer.writerows(rows)
    json_path.write_text(json.dumps({"schema": "core5_boundary_descriptors_v1",
                                     "xy_translation": "median palm relative to shoulder midpoint / shoulder width / elapsed seconds",
                                     "xy_articulation": "median finger-relative-to-wrist speed / hand scale / elapsed seconds",
                                     "xy_arm": "median pose wrist/elbow shoulder-relative speed / shoulder width / elapsed seconds",
                                     "z": "reported separately and excluded from XY motion decision",
                                     "limitations": "No independently observed linguistic sign-end or video; tail10 is only a proxy",
                                     "events": summary}, indent=2) + "\n", encoding="utf-8")
    return {"events": len(summary), "timeline_rows": len(rows), "csv": str(csv_path), "json": str(json_path)}


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--events-dir", required=True, type=Path)
    parser.add_argument("--output-stem", required=True, type=Path)
    args = parser.parse_args()
    print(json.dumps(run(args.events_dir, args.output_stem), indent=2))
