"""Read-only Core5 capture-quality telemetry; never an acceptance gate."""
from __future__ import annotations

import argparse
import csv
import json
from pathlib import Path

import numpy as np

from core5_boundary_descriptors import geometry


def longest_run(values):
    longest = current = 0
    for value in values:
        current = current + 1 if value else 0
        longest = max(longest, current)
    return longest


def audit(event):
    frames = event["frames"]
    included = [frame for frame in frames if frame["included"]]
    window = included or frames
    n = len(window)
    pose_ratio = sum(bool(f["pose_present"]) for f in window) / n
    hand_ratio = sum(bool(f["left_present"] or f["right_present"]) for f in window) / n
    useful = sum(bool(f["pose_present"] and (f["left_present"] or f["right_present"])) for f in window)
    gaps = longest_run(not (f["left_present"] or f["right_present"]) for f in window)
    sides = ["L" if f["left_present"] and not f["right_present"] else
             "R" if f["right_present"] and not f["left_present"] else
             "B" if f["left_present"] and f["right_present"] else "0" for f in window]
    direct_slot_switches = sum(a in "LR" and b in "LR" and a != b for a, b in zip(sides, sides[1:]))
    scales = []
    edge = 0
    observed_hands = 0
    ambiguous_reports = 0
    for frame in window:
        g = geometry(frame)
        if g:
            scales.extend(s["hand_scale_xy"] for s in g["sides"].values())
        for side in ("left", "right"):
            if not frame[side + "_present"]:
                continue
            hand = np.asarray(frame[side], dtype=np.float64)
            palm = np.median(hand[[0, 5, 9, 13, 17], :2], axis=0)
            edge += int(np.any(palm < .04) or np.any(palm > .96))
            observed_hands += 1
        ambiguous_reports += sum(h.get("slot") not in ("LEFT", "RIGHT", "left", "right")
                                 for h in frame.get("handedness", []))
    scale_cv = float(np.std(scales) / np.mean(scales)) if len(scales) >= 2 and np.mean(scales) > 0 else None
    continuity = 1 - gaps / n
    scale_stability = 1 / (1 + scale_cv) if scale_cv is not None else 0
    # Descriptive index only; weights were not fitted/validated and do not gate.
    index = 100 * (.25 * pose_ratio + .25 * hand_ratio + .25 * useful / n +
                   .15 * continuity + .10 * scale_stability)
    return {"event_id": event["event_id"], "expected": event["expected_test_label"],
            "boundary": event["boundary_mode"], "source_end": event["termination"],
            "raw_top1": event.get("raw_top1"), "accepted": event["accepted"],
            "window_frames": n, "useful_frames": useful, "pose_ratio": pose_ratio,
            "any_hand_ratio": hand_ratio, "longest_no_hand_gap_frames": gaps,
            "direct_single_slot_switches": direct_slot_switches,
            "ambiguous_hand_reports": ambiguous_reports,
            "hand_scale_cv": scale_cv,
            "palm_edge_fraction": edge / observed_hands if observed_hands else None,
            "diagnostic_quality_index_0_100": index}


def run(events_dir: Path, output_stem: Path):
    json_path, csv_path = output_stem.with_suffix(".json"), output_stem.with_suffix(".csv")
    if json_path.exists() or csv_path.exists():
        raise FileExistsError("Preserve prior quality evidence")
    rows = []
    for path in sorted(events_dir.glob("*.json")):
        if path.name.startswith("startup-") or ".candidate-replay." in path.name:
            continue
        event = json.loads(path.read_text(encoding="utf-8"))
        if event.get("profile") == "FSL_CORE5_SIM10FPS_V1" and event.get("frames"):
            rows.append(audit(event))
    if not rows:
        raise ValueError("No saved Core5 events")
    output_stem.parent.mkdir(parents=True, exist_ok=True)
    json_path.write_text(json.dumps({"status": "DIAGNOSTIC_ONLY_NOT_AN_ACCEPTANCE_GATE",
                                     "quality_index_weights": {"pose": .25, "hand": .25,
                                                               "useful": .25, "continuity": .15,
                                                               "hand_scale_stability": .10},
                                     "occlusion_note": "No pixels: palm-edge fraction is a framing proxy, not an occlusion detector",
                                     "rows": rows}, indent=2) + "\n", encoding="utf-8")
    with csv_path.open("w", newline="", encoding="utf-8") as stream:
        writer = csv.DictWriter(stream, fieldnames=list(rows[0]))
        writer.writeheader()
        writer.writerows(rows)
    return {"events": len(rows), "json": str(json_path), "csv": str(csv_path)}


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--events-dir", required=True, type=Path)
    parser.add_argument("--output-stem", required=True, type=Path)
    args = parser.parse_args()
    print(json.dumps(run(args.events_dir, args.output_stem), indent=2))
