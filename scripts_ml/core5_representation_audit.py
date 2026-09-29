"""Read-only mathematical and empirical FullSign225 relation audit."""
from __future__ import annotations

import argparse
import json
from pathlib import Path

import numpy as np

from core5_contract import LABELS, VERSION
from core5_semantic_source_profile import frames_from_arrays


def frame_measure(frame):
    if not frame["pose_present"]:
        return None
    pose = np.asarray(frame["pose"], dtype=np.float64)
    shoulder = float(np.linalg.norm(pose[11, :2] - pose[12, :2]))
    if shoulder < .04:
        return None
    nose, mouth = pose[0, :2], (pose[9, :2] + pose[10, :2]) / 2
    result = []
    canonical = np.asarray(frame["canonical"], dtype=np.float64).reshape(75, 3)
    for side, offset in (("left", 33), ("right", 54)):
        if not frame[side + "_present"]:
            continue
        hand = np.asarray(frame[side], dtype=np.float64)
        scale = float(np.linalg.norm(hand[0] - hand[9]))
        if scale <= .001:
            continue
        wrist = hand[0, :2]
        canonical_wrist = canonical[offset, :2]
        canonical_mouth = (canonical[9, :2] + canonical[10, :2]) / 2
        reconstructed = canonical_wrist * scale
        true_nose = float(np.linalg.norm(wrist - nose) / shoulder)
        true_mouth = float(np.linalg.norm(wrist - mouth) / shoulder)
        # This subtraction is deliberately wrong: it mixes hand-scaled and
        # unscaled pose units. Quantify its failure, never use it as geometry.
        naive_mouth = float(np.linalg.norm(canonical_wrist - canonical_mouth) / shoulder)
        result.append({"side": side, "shoulder_width_xy": shoulder,
                       "hand_scale_xyz": scale, "hand_to_shoulder_scale_ratio": scale / shoulder,
                       "true_wrist_nose_body_scales": true_nose,
                       "true_wrist_mouth_body_scales": true_mouth,
                       "naive_canonical_wrist_mouth_body_scales": naive_mouth,
                       "naive_to_true_mouth_ratio": naive_mouth / max(true_mouth, .01),
                       "wrist_nose_roundtrip_error_xy": float(np.linalg.norm(reconstructed - (wrist - nose)))})
    return result


def stats(values):
    if not values:
        return {"n": 0, "median": None, "p10": None, "p90": None}
    return {"n": len(values), "median": float(np.median(values)),
            "p10": float(np.percentile(values, 10)), "p90": float(np.percentile(values, 90))}


def summarize_events(events):
    by_label = {}
    fields = ("shoulder_width_xy", "hand_scale_xyz", "hand_to_shoulder_scale_ratio",
              "true_wrist_nose_body_scales", "true_wrist_mouth_body_scales",
              "naive_canonical_wrist_mouth_body_scales", "naive_to_true_mouth_ratio",
              "wrist_nose_roundtrip_error_xy")
    for label in sorted(set(label for label, _ in events)):
        selected = [frames for name, frames in events if name == label]
        measures = [[m for frame in frames for m in (frame_measure(frame) or [])] for frames in selected]
        per_event = [{key: float(np.median([m[key] for m in sample])) for key in fields}
                     for sample in measures if sample]
        by_label[label] = {"events": len(selected), "events_with_valid_pose_hand": len(per_event),
                           "per_event_medians": {key: stats([e[key] for e in per_event]) for key in fields}}
    return by_label


def run(source_dir: Path, samsung_dir: Path, output: Path):
    if output.exists():
        raise FileExistsError("Preserve earlier relation audit")
    source = []
    for path in sorted(source_dir.glob("*.npz")):
        if path.name.startswith("test-"):
            continue  # Official test feature files stay sealed.
        with np.load(path, allow_pickle=False) as saved:
            meta = json.loads(str(saved["metadata"].item()))
            if meta["official_split"] != "train":
                continue
            if meta["feature_version"] != VERSION or meta["source_label"] not in LABELS:
                raise ValueError("Wrong source/contract")
            frames = frames_from_arrays(saved["raw_landmarks"], saved["timestamps_ms"], saved["presence"])
            for frame, canonical in zip(frames, saved["canonical_frames"]):
                frame["canonical"] = canonical.tolist()
        source.append((meta["source_label"], frames))
    samsung = []
    for path in sorted(samsung_dir.glob("*.json")):
        if path.name.startswith("startup-") or ".candidate-replay." in path.name:
            continue
        event = json.loads(path.read_text(encoding="utf-8"))
        if event.get("profile") != "FSL_CORE5_SIM10FPS_V1" or not event.get("frames"):
            continue
        if event["feature_version"] != VERSION:
            raise ValueError("Wrong Samsung feature version")
        samsung.append((event["expected_test_label"], event["frames"]))
    report = {"status": "AUDIT_ONLY_NO_NORMALIZATION_CHANGE",
              "mathematical_contract": "P_i'=(P_i-N); H_j'=(H_j-N)/s_hand; all Z multiplied by .3. Hand-to-body XY cannot be recovered from 225 alone without s_hand; source raw and Samsung raw retain it.",
              "official_test_opened": False, "source_train": summarize_events(source),
              "samsung_diagnostic": summarize_events(samsung),
              "source_events": len(source), "samsung_events": len(samsung)}
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(report, indent=2, allow_nan=False) + "\n", encoding="utf-8")
    return {"source_events": len(source), "samsung_events": len(samsung), "output": str(output)}


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--source", type=Path, required=True)
    parser.add_argument("--samsung", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    print(json.dumps(run(args.source, args.samsung, args.output), indent=2))
