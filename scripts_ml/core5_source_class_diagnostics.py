"""Read-only Core5 class motion diagnostics from official FSL-105 train clips.

The official test split stays sealed. Output is a private analysis artifact, not
a gate or a training representation.
"""
from __future__ import annotations

import argparse
import json
from pathlib import Path

import numpy as np

from core5_boundary_descriptors import geometry, med_mad_percentiles, timeline
from core5_contract import LABELS, VERSION, observed_envelope


def summarize(path: Path):
    with np.load(path, allow_pickle=False) as saved:
        meta = json.loads(str(saved["metadata"].item()))
        if meta["source_dataset"] != "DLSU_FSL105_V2" or meta["feature_version"] != VERSION:
            raise ValueError("Incompatible source or feature version: " + str(path))
        if meta["official_split"] != "train":
            return None
        if meta["source_label"] not in LABELS:
            raise ValueError("Unexpected label: " + str(path))
        raw = saved["raw_landmarks"]
        times = saved["timestamps_ms"]
        presence = saved["presence"]
    if raw.ndim != 2 or raw.shape[1] != 225 or len(raw) != len(times) or presence.shape != (len(raw), 3):
        raise ValueError("Invalid saved clip shape: " + str(path))
    source_clip_duration_ms = int(times[-1] - times[0])
    lo, hi = observed_envelope(times, presence)
    raw, times, presence = raw[lo:hi], times[lo:hi], presence[lo:hi]
    frames = [{"timestamp_ms": int(t), "pose_present": bool(p[0]),
               "left_present": bool(p[1]), "right_present": bool(p[2]),
               "pose": x[:99].reshape(33, 3).tolist(),
               "left": x[99:162].reshape(21, 3).tolist(),
               "right": x[162:225].reshape(21, 3).tolist()}
              for x, t, p in zip(raw, times, presence)]
    event = {"event_id": meta["record_id"], "expected_test_label": meta["source_label"],
             "boundary_mode": "OFFICIAL_SOURCE", "termination": "SOURCE_CLIP_END", "frames": frames}
    motion = timeline(event)
    positions = {side: [] for side in ("left", "right")}
    for frame in frames:
        g = geometry(frame)
        if g:
            for side in positions:
                if side in g["sides"]:
                    positions[side].append(g["sides"][side]["palm_torso_xy"])
    used = max(positions, key=lambda side: len(positions[side]))
    selected = positions[used]
    trajectory = sum(float(np.linalg.norm(b - a)) for a, b in zip(selected, selected[1:])) if len(selected) > 1 else None
    return {"record_id": meta["record_id"], "label": meta["source_label"],
            "partition": meta["partition"], "duration_ms": int(times[-1] - times[0]),
            "source_clip_duration_ms": source_clip_duration_ms,
            "frames": len(frames), "pose_ratio": float(presence[:, 0].mean()),
            "left_ratio": float(presence[:, 1].mean()), "right_ratio": float(presence[:, 2].mean()),
            "two_hand_ratio": float(np.logical_and(presence[:, 1], presence[:, 2]).mean()),
            "dominant_tracked_side": used if selected else None,
            "trajectory_length_body_scales": trajectory,
            "start_palm_torso_xy": selected[0].tolist() if selected else None,
            "end_palm_torso_xy": selected[-1].tolist() if selected else None,
            "translation_xy_speed": med_mad_percentiles([r["translation_xy_per_s"] for r in motion]),
            "articulation_xy_speed": med_mad_percentiles([r["articulation_xy_per_s"] for r in motion]),
            "arm_xy_speed": med_mad_percentiles([r["arm_xy_per_s"] for r in motion])}


def run(features: Path, output: Path):
    if output.exists():
        raise FileExistsError("Preserve earlier source diagnostic evidence")
    records = [summarize(p) for p in sorted(features.glob("*.npz"))]
    records = [r for r in records if r is not None]
    groups = {}
    for label in LABELS:
        group = [r for r in records if r["label"] == label]
        groups[label] = {"clips": len(group),
                         "duration_ms": med_mad_percentiles([r["duration_ms"] for r in group]),
                         "pose_ratio": med_mad_percentiles([r["pose_ratio"] for r in group]),
                         "left_ratio": med_mad_percentiles([r["left_ratio"] for r in group]),
                         "right_ratio": med_mad_percentiles([r["right_ratio"] for r in group]),
                         "two_hand_ratio": med_mad_percentiles([r["two_hand_ratio"] for r in group]),
                         "trajectory_length_body_scales": med_mad_percentiles([r["trajectory_length_body_scales"] for r in group]),
                         "translation_xy_speed_p90": med_mad_percentiles([r["translation_xy_speed"]["p90"] for r in group]),
                         "articulation_xy_speed_p90": med_mad_percentiles([r["articulation_xy_speed"]["p90"] for r in group]),
                         "arm_xy_speed_p90": med_mad_percentiles([r["arm_xy_speed"]["p90"] for r in group]),
                         "start_palm_torso_xy": [med_mad_percentiles([r["start_palm_torso_xy"][axis] if r["start_palm_torso_xy"] else None for r in group]) for axis in (0, 1)],
                         "end_palm_torso_xy": [med_mad_percentiles([r["end_palm_torso_xy"][axis] if r["end_palm_torso_xy"] else None for r in group]) for axis in (0, 1)]}
    result = {"status": "DIAGNOSTIC_ONLY_NOT_A_GATE", "source": "official FSL-105 train only; observed sign envelope plus 100ms context",
              "feature_version": VERSION, "official_test_opened": False,
              "records": records, "by_class": groups}
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(result, indent=2, allow_nan=False) + "\n", encoding="utf-8")
    return {"records": len(records), "counts": {label: groups[label]["clips"] for label in LABELS},
            "output": str(output)}


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--features", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    print(json.dumps(run(args.features, args.output), indent=2))
