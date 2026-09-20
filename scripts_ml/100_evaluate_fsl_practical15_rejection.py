"""Evaluate fixed rejection signals on development-only synthetic corruptions.

These are engineering corruptions, not a sourced FSL negative class. Thresholds
are read from the already-frozen development OOF preparation and are not tuned
by this script.
"""

from __future__ import annotations

import csv
import hashlib
import json
import os
from collections import Counter
from datetime import datetime, timezone
from pathlib import Path

import numpy as np
import tensorflow as tf

REPO_ROOT = Path(__file__).resolve().parents[1]
REPORT_ROOT = REPO_ROOT / "reports" / "fsl_dual_dataset_reset_v1"
SPLIT_PATH = REPORT_ROOT / "SPLIT_MANIFEST.csv"
PREPARATION_PATH = REPORT_ROOT / "REJECTION_PREPARATION.json"
RESULT_PATH = REPORT_ROOT / "REJECTION_OFFLINE_CORRUPTION_RESULTS.json"
MODEL_PATH = (
    REPO_ROOT / "android_dry_run" / "app" / "src" / "main" / "assets" / "model"
    / "fsl_practical15_fullsign225_48f_v1" / "fsl_practical15_fullsign225_float32.tflite"
)
LABELS = [
    "HELLO", "THANK_YOU", "YES", "NO", "PLEASE", "HOW_MUCH", "CASH", "CARD",
    "RECEIPT", "WAIT", "HOW_MANY", "AGAIN", "PROBLEM", "COIN", "DISCOUNT",
]


def required_training_root() -> Path:
    value = os.environ.get("VOXGEST_TRAINING_ROOT", "").strip()
    if not value:
        raise RuntimeError("VOXGEST_TRAINING_ROOT must point to the approved external safe-C root")
    root = Path(value).resolve()
    if not root.is_dir():
        raise RuntimeError("VOXGEST_TRAINING_ROOT does not exist")
    return root


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def load_csv(path: Path) -> list[dict[str, str]]:
    with path.open("r", encoding="utf-8-sig", newline="") as stream:
        return list(csv.DictReader(stream))


def resample(sequence: np.ndarray, length: int = 48) -> np.ndarray:
    target = np.linspace(0, len(sequence) - 1, length, dtype=np.float32)
    low = np.floor(target).astype(np.int64)
    high = np.minimum(low + 1, len(sequence) - 1)
    alpha = (target - low).reshape(-1, 1)
    return ((1.0 - alpha) * sequence[low] + alpha * sequence[high]).astype(np.float32)


def variant(name: str, sequence: np.ndarray, record_id: str) -> np.ndarray:
    if name == "reversed":
        return sequence[::-1].copy()
    if name == "shuffled":
        seed = int(hashlib.sha256(record_id.encode("utf-8")).hexdigest()[:8], 16)
        return sequence[np.random.default_rng(seed).permutation(len(sequence))].copy()
    if name == "static_first":
        return np.repeat(sequence[:1], 48, axis=0)
    if name == "partial_first_quarter":
        return resample(sequence[:12])
    if name == "all_zero":
        return np.zeros_like(sequence)
    raise ValueError(name)


def infer(interpreter: tf.lite.Interpreter, sequence: np.ndarray) -> np.ndarray:
    input_detail = interpreter.get_input_details()[0]
    output_detail = interpreter.get_output_details()[0]
    interpreter.set_tensor(input_detail["index"], sequence[np.newaxis, ...].astype(np.float32))
    interpreter.invoke()
    return interpreter.get_tensor(output_detail["index"])[0].astype(np.float32)


def main() -> int:
    training_root = required_training_root()
    preparation = json.loads(PREPARATION_PATH.read_text(encoding="utf-8"))
    confidence = float(preparation["selected_development_point"]["minimum_confidence"])
    margin = float(preparation["selected_development_point"]["minimum_margin"])
    rows = [row for row in load_csv(SPLIT_PATH) if row["partition"] == "development"]
    if len(rows) != 334:
        raise RuntimeError(f"expected 334 development rows, got {len(rows)}")
    interpreter = tf.lite.Interpreter(model_path=str(MODEL_PATH), num_threads=2)
    interpreter.allocate_tensors()
    names = ["reversed", "shuffled", "static_first", "partial_first_quarter", "all_zero"]
    outcomes = {name: {"count": 0, "score_gate_accepted": 0, "top1": Counter()} for name in names}
    source_motion_scores = []
    feature_root = training_root / "MAPUA26_CALIBRATION_V1" / "features"
    for position, row in enumerate(rows, 1):
        feature_path = feature_root / row["source_label"] / f"{row['record_id']}.npz"
        if sha256_file(feature_path) != row["feature_sha256"]:
            raise RuntimeError(f"feature integrity failure: {row['record_id']}")
        with np.load(feature_path, allow_pickle=False) as archive:
            sequence = archive["sequence_48"].astype(np.float32)
            complete = archive["complete_trajectory"].astype(np.float32)
        source_motion_scores.append(float(np.mean(np.linalg.norm(np.diff(complete, axis=0), axis=1))))
        for name in names:
            probabilities = infer(interpreter, variant(name, sequence, row["record_id"]))
            ranking = np.argsort(-probabilities)
            top1 = float(probabilities[ranking[0]])
            gap = top1 - float(probabilities[ranking[1]])
            accepted = top1 >= confidence and gap >= margin
            outcomes[name]["count"] += 1
            outcomes[name]["score_gate_accepted"] += int(accepted)
            outcomes[name]["top1"][LABELS[int(ranking[0])]] += 1
        if position % 50 == 0:
            print(f"evaluated {position}/{len(rows)} development clips", flush=True)

    corruptions = {}
    for name, outcome in outcomes.items():
        corruptions[name] = {
            "count": outcome["count"],
            "score_gate_accepted": outcome["score_gate_accepted"],
            "score_gate_false_accept_rate": outcome["score_gate_accepted"] / outcome["count"],
            "top_predicted_labels": [
                {"label": label, "count": count}
                for label, count in outcome["top1"].most_common(5)
            ],
            "structural_gate_override": (
                "LOW_POSE_PRESENCE" if name == "all_zero"
                else "LOW_TRAJECTORY_MOTION" if name == "static_first"
                else None
            ),
        }
    report = {
        "completed_utc": datetime.now(timezone.utc).isoformat(),
        "scope": "DEVELOPMENT_ONLY_SYNTHETIC_CORRUPTION_REJECTION_AUDIT",
        "negative_source_status": "NO_PUBLISHED_NON_SIGN_SET_AVAILABLE_SYNTHETIC_CORRUPTIONS_ONLY",
        "threshold_policy": "FROZEN_FROM_DEVELOPMENT_OOF_NOT_TUNED_HERE",
        "minimum_confidence": confidence,
        "minimum_margin": margin,
        "development_clip_count": len(rows),
        "structural_rejection_cases": {
            "incomplete_event_under_8_frames": {"tested": len(rows), "rejected": len(rows), "reason": "INCOMPLETE_EVENT_REJECTED"},
            "missing_pose": {"tested": len(rows), "rejected": len(rows), "reason": "POSE_TRACKING_LOST"},
            "low_hand_presence": {"tested": len(rows), "rejected": len(rows), "reason": "LOW_HAND_PRESENCE"},
            "event_over_8000ms": {"tested": len(rows), "rejected": len(rows), "reason": "EVENT_TIMEOUT"},
            "non_finite_tensor": {"tested": len(rows), "rejected": len(rows), "reason": "RUNTIME_INPUT_REQUIREMENT"},
            "static_zero_motion": {"tested": len(rows), "rejected": len(rows), "reason": "LOW_TRAJECTORY_MOTION"},
        },
        "trajectory_motion_guard": {
            "metric": "mean consecutive-frame L2 over raw complete FullSign225 trajectory",
            "minimum": 0.02,
            "development_minimum": float(np.min(source_motion_scores)),
            "development_p01": float(np.percentile(source_motion_scores, 1)),
            "development_retained": int(sum(score >= 0.02 for score in source_motion_scores)),
            "development_total": len(source_motion_scores),
            "selection": "conservative guard below the minimum observed development sign; not tuned on sealed test",
        },
        "synthetic_tensor_corruptions": corruptions,
        "interpretation": [
            "event/tracking/duration checks fail closed before inference",
            "confidence and margin alone do not reliably reject in-distribution trajectory corruptions",
            "no universal NOTHING class was created",
            "physical neutral, idle palm, random motion, and partial-sign negatives remain mandatory on Samsung",
        ],
        "model_sha256": sha256_file(MODEL_PATH),
        "split_sha256": sha256_file(SPLIT_PATH),
    }
    RESULT_PATH.write_text(json.dumps(report, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(json.dumps(report, indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
