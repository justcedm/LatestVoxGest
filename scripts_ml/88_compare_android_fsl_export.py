"""Verify an Android FSL feature-window export against the Python contract.

The Android debug exporter stores both raw MediaPipe pose/right-hand landmarks
and the 20x162 vectors produced on-device. This script rebuilds every frame
with the canonical Python implementation and reports a measurable
ANDROID_FEATURE_PARITY result.
"""

from __future__ import annotations

import argparse
import json
from pathlib import Path
from typing import Any

import numpy as np

from voxgest_feature_builder import (
    EXPECTED_SEQUENCE_SHAPE,
    FEATURE_VERSION,
    build_onehand162_from_arrays,
    validate_sequence,
)


PROFILE_ID = "fsl_onehand162_20f_rdtcn_v2"
MAX_ABS_ERROR = 1.0e-6


def landmarks_array(value: Any, expected_count: int) -> np.ndarray | None:
    if value is None:
        return None
    if not isinstance(value, list) or len(value) != expected_count:
        raise ValueError(f"expected {expected_count} landmarks, got {type(value).__name__}")
    result = np.asarray(
        [[point["x"], point["y"], point["z"]] for point in value],
        dtype=np.float32,
    )
    if result.shape != (expected_count, 3) or not np.isfinite(result).all():
        raise ValueError(f"invalid landmark array shape/data: {result.shape}")
    return result


def compare_export(path: Path) -> dict[str, Any]:
    document = json.loads(path.read_text(encoding="utf-8"))
    if document.get("schema_version") != "voxgest_fsl_probe_window_v1":
        raise ValueError("unsupported Android FSL export schema")
    metadata = document.get("metadata") or {}
    if metadata.get("feature_version") != FEATURE_VERSION:
        raise ValueError("feature_version mismatch")
    if metadata.get("profile_id") != PROFILE_ID:
        raise ValueError("profile_id mismatch")
    if metadata.get("shape") != [1, *EXPECTED_SEQUENCE_SHAPE]:
        raise ValueError("shape metadata must be [1,20,162]")
    if metadata.get("dtype") != "float32":
        raise ValueError("dtype metadata must be float32")
    for field in ("expected", "signer_id", "session_id", "device_id"):
        if not str(metadata.get(field, "")).strip():
            raise ValueError(f"missing required {field} metadata")

    window = document.get("window") or {}
    raw_frames = window.get("raw_frames")
    android_features = np.asarray(window.get("canonical_features"), dtype=np.float32)
    sequence_issues = validate_sequence(android_features)
    if sequence_issues:
        raise ValueError(f"invalid Android feature sequence: {sequence_issues}")
    if not isinstance(raw_frames, list) or len(raw_frames) != EXPECTED_SEQUENCE_SHAPE[0]:
        raise ValueError("raw_frames must contain exactly 20 frames")

    python_frames: list[np.ndarray] = []
    pose_flags: list[bool] = []
    hand_flags: list[bool] = []
    timestamps: list[int] = []
    for frame in raw_frames:
        timestamps.append(int(frame["timestamp_ms"]))
        pose = landmarks_array(frame.get("pose_landmarks"), 33)
        hand = landmarks_array(frame.get("right_hand_landmarks"), 21)
        rebuilt = build_onehand162_from_arrays(pose, hand, selected_hand="right")
        python_frames.append(rebuilt.vector)
        pose_flags.append(rebuilt.pose_present)
        hand_flags.append(rebuilt.hand_present)
    if any(current <= previous for previous, current in zip(timestamps, timestamps[1:])):
        raise ValueError("raw frame timestamps are not strictly increasing")

    python_features = np.asarray(python_frames, dtype=np.float32)
    differences = np.abs(android_features - python_features)
    max_error = float(np.max(differences))
    mean_error = float(np.mean(differences))
    passed = max_error <= MAX_ABS_ERROR
    return {
        "marker": "ANDROID_FEATURE_PARITY",
        "status": "PASS" if passed else "FAIL",
        "passed": passed,
        "source_export": str(path.resolve()),
        "profile_id": PROFILE_ID,
        "feature_version": FEATURE_VERSION,
        "shape": list(android_features.shape),
        "dtype": str(android_features.dtype),
        "selected_hand_policy": "fixed_anatomical_right",
        "maximum_absolute_error": max_error,
        "mean_absolute_error": mean_error,
        "threshold": MAX_ABS_ERROR,
        "pose_present_frames": int(sum(pose_flags)),
        "right_hand_present_frames": int(sum(hand_flags)),
        "timestamps_strictly_increasing": True,
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("export", type=Path, help="Detailed Android probe window JSON")
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    report = compare_export(args.export)
    rendered = json.dumps(report, indent=2)
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(rendered + "\n", encoding="utf-8")
    print(
        f"ANDROID_FEATURE_PARITY {report['status']} "
        f"max_abs_error={report['maximum_absolute_error']:.9g} "
        f"mean_abs_error={report['mean_absolute_error']:.9g}"
    )
    if not report["passed"]:
        raise SystemExit(1)


if __name__ == "__main__":
    main()
