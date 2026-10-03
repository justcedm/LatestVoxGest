"""Replay the frozen sealed split from raw Mapua videos through final TFLite.

This is an end-to-end extraction check, not another training pass. It verifies
raw hashes, reruns MediaPipe, reconstructs the frozen motion envelope and
FullSign225 trajectory, resamples to 48, compares the cached tensor, and invokes
the exact Android-bound float32 TFLite model.
"""

from __future__ import annotations

import csv
import hashlib
import json
import os
import time
from collections import Counter
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

import cv2
import mediapipe as mp
import numpy as np
import tensorflow as tf
from sklearn.metrics import classification_report, confusion_matrix

from fullsign225_feature_builder import FEATURE_SIZE, build_fullsign225_from_holistic

REPO_ROOT = Path(__file__).resolve().parents[1]
CONFIG_PATH = REPO_ROOT / "training_configs" / "fsl_practical15_mapua_v1.json"
SPLIT_PATH = REPO_ROOT / "reports" / "fsl_dual_dataset_reset_v1" / "SPLIT_MANIFEST.csv"
RESULT_PATH = REPO_ROOT / "reports" / "fsl_dual_dataset_reset_v1" / "RAW_VIDEO_REPLAY_RESULTS.json"
MODEL_PATH = (
    REPO_ROOT / "android_dry_run" / "app" / "src" / "main" / "assets" / "model"
    / "fsl_practical15_fullsign225_48f_v1" / "fsl_practical15_fullsign225_float32.tflite"
)


def required_root(name: str) -> Path:
    value = os.environ.get(name, "").strip()
    if not value:
        raise RuntimeError(f"{name} must point to the approved external safe-C root")
    root = Path(value).resolve()
    if not root.is_dir():
        raise RuntimeError(f"{name} does not exist")
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


def array_or_none(value: Any, count: int) -> np.ndarray | None:
    if value is None:
        return None
    array = np.asarray([[p.x, p.y, p.z] for p in value.landmark], dtype=np.float32)
    return array if array.shape == (count, 3) and np.isfinite(array).all() else None


def interpolate_short_gaps(
    values: list[np.ndarray | None], max_gap: int
) -> tuple[list[np.ndarray | None], int]:
    result = [None if item is None else item.copy() for item in values]
    filled = 0
    index = 0
    while index < len(result):
        if result[index] is not None:
            index += 1
            continue
        start = index
        while index < len(result) and result[index] is None:
            index += 1
        gap = index - start
        if start == 0 or index == len(result) or gap > max_gap:
            continue
        before, after = result[start - 1], result[index]
        if before is None or after is None:
            continue
        for offset in range(gap):
            weight = np.float32((offset + 1) / (gap + 1))
            result[start + offset] = ((1.0 - weight) * before + weight * after).astype(np.float32)
            filled += 1
    return result, filled


def detect_motion_envelope(centers: list[np.ndarray | None], boundary: int) -> tuple[int, int]:
    valid = [(index, value) for index, value in enumerate(centers) if value is not None]
    if not valid:
        return 0, max(0, len(centers) - 1)
    motion = []
    for (previous_index, previous), (current_index, current) in zip(valid, valid[1:]):
        gap = max(1, current_index - previous_index)
        motion.append((current_index, float(np.linalg.norm(current - previous) / gap)))
    if not motion:
        start = valid[0][0]
        return max(0, start - boundary), min(len(centers) - 1, start + boundary)
    speeds = np.asarray([item[1] for item in motion], dtype=np.float32)
    baseline = float(np.median(speeds))
    mad = float(np.median(np.abs(speeds - baseline)))
    threshold = max(0.004, baseline + 1.5 * mad, float(np.percentile(speeds, 55)) * 0.75)
    active = [frame for frame, speed in motion if speed >= threshold]
    if not active:
        active = [motion[int(np.argmax(speeds))][0]]
    return max(0, min(active) - boundary), min(len(centers) - 1, max(active) + boundary)


def resample_complete(sequence: np.ndarray, length: int = 48) -> np.ndarray:
    if len(sequence) == 1:
        return np.repeat(sequence, length, axis=0).astype(np.float32)
    target = np.linspace(0, len(sequence) - 1, length, dtype=np.float32)
    low = np.floor(target).astype(np.int64)
    high = np.minimum(low + 1, len(sequence) - 1)
    alpha = (target - low).reshape(-1, 1)
    return ((1.0 - alpha) * sequence[low] + alpha * sequence[high]).astype(np.float32)


def extract_raw(video_path: Path, boundary: int, max_gap: int) -> dict[str, Any]:
    started = time.perf_counter()
    capture = cv2.VideoCapture(str(video_path))
    fps = float(capture.get(cv2.CAP_PROP_FPS) or 0.0)
    poses: list[np.ndarray | None] = []
    lefts: list[np.ndarray | None] = []
    rights: list[np.ndarray | None] = []
    with mp.solutions.holistic.Holistic(
        static_image_mode=False,
        model_complexity=1,
        smooth_landmarks=True,
        refine_face_landmarks=False,
        min_detection_confidence=0.5,
        min_tracking_confidence=0.5,
    ) as holistic:
        while True:
            ok, bgr = capture.read()
            if not ok:
                break
            result = holistic.process(cv2.cvtColor(bgr, cv2.COLOR_BGR2RGB))
            poses.append(array_or_none(result.pose_landmarks, 33))
            lefts.append(array_or_none(result.left_hand_landmarks, 21))
            rights.append(array_or_none(result.right_hand_landmarks, 21))
    capture.release()
    if not poses:
        raise RuntimeError("zero decoded frames")
    left_fixed, left_filled = interpolate_short_gaps(lefts, max_gap)
    right_fixed, right_filled = interpolate_short_gaps(rights, max_gap)
    centers = []
    for left, right in zip(left_fixed, right_fixed):
        hands = [hand.mean(axis=0) for hand in (left, right) if hand is not None]
        centers.append(np.mean(hands, axis=0).astype(np.float32) if hands else None)
    start, end = detect_motion_envelope(centers, boundary)
    vectors = []
    for pose, left, right in zip(
        poses[start : end + 1],
        left_fixed[start : end + 1],
        right_fixed[start : end + 1],
    ):
        synthetic = type(
            "Results",
            (),
            {"pose_landmarks": pose, "left_hand_landmarks": left, "right_hand_landmarks": right},
        )()
        vectors.append(build_fullsign225_from_holistic(synthetic).vector)
    complete = np.asarray(vectors, dtype=np.float32)
    if complete.ndim != 2 or complete.shape[1] != FEATURE_SIZE or not np.isfinite(complete).all():
        raise RuntimeError(f"invalid complete trajectory {complete.shape}")
    return {
        "sequence": resample_complete(complete),
        "decoded_frames": len(poses),
        "fps": fps,
        "motion_start": start,
        "motion_end": end,
        "complete_frames": len(complete),
        "pose_present_frames": sum(item is not None for item in poses),
        "left_hand_present_frames": sum(item is not None for item in lefts),
        "right_hand_present_frames": sum(item is not None for item in rights),
        "interpolated_left_frames": left_filled,
        "interpolated_right_frames": right_filled,
        "extraction_ms": (time.perf_counter() - started) * 1000.0,
    }


def infer(interpreter: tf.lite.Interpreter, sequence: np.ndarray) -> tuple[np.ndarray, float]:
    input_detail = interpreter.get_input_details()[0]
    output_detail = interpreter.get_output_details()[0]
    started = time.perf_counter()
    interpreter.set_tensor(input_detail["index"], sequence[np.newaxis, ...].astype(np.float32))
    interpreter.invoke()
    latency_ms = (time.perf_counter() - started) * 1000.0
    return interpreter.get_tensor(output_detail["index"])[0].astype(np.float32), latency_ms


def main() -> int:
    config = json.loads(CONFIG_PATH.read_text(encoding="utf-8"))
    labels = config["labels"]
    dataset_root = required_root("VOXGEST_DATASETS_ROOT")
    training_root = required_root("VOXGEST_TRAINING_ROOT")
    raw_root = dataset_root / config["dataset"]["raw_video_root"]
    feature_root = training_root / "MAPUA26_CALIBRATION_V1" / "features"
    sealed = [row for row in load_csv(SPLIT_PATH) if row["partition"] == "sealed_test"]
    if len(sealed) != 60:
        raise RuntimeError(f"expected 60 sealed records, got {len(sealed)}")
    interpreter = tf.lite.Interpreter(model_path=str(MODEL_PATH), num_threads=2)
    interpreter.allocate_tensors()
    if interpreter.get_input_details()[0]["shape"].tolist() != [1, 48, 225]:
        raise RuntimeError("unexpected TFLite input")
    if interpreter.get_output_details()[0]["shape"].tolist() != [1, 15]:
        raise RuntimeError("unexpected TFLite output")

    records = []
    true_indices = []
    predicted_indices = []
    failures = []
    for position, row in enumerate(sealed, 1):
        try:
            video_path = raw_root / Path(row["relative_path"])
            if sha256_file(video_path) != row["raw_video_sha256"]:
                raise RuntimeError("raw video SHA-256 mismatch")
            extracted = extract_raw(
                video_path,
                int(config["feature_contract"]["motion_boundary_frames"]),
                int(config["feature_contract"]["maximum_internal_interpolation_gap_frames"]),
            )
            cached_path = feature_root / row["source_label"] / f"{row['record_id']}.npz"
            if sha256_file(cached_path) != row["feature_sha256"]:
                raise RuntimeError("cached feature SHA-256 mismatch")
            with np.load(cached_path, allow_pickle=False) as archive:
                cached = archive["sequence_48"].astype(np.float32)
                cached_metadata = json.loads(str(archive["metadata"].item()))
            difference = np.abs(extracted["sequence"] - cached)
            probabilities, latency_ms = infer(interpreter, extracted["sequence"])
            ranking = np.argsort(-probabilities)
            expected = labels.index(row["source_label"])
            predicted = int(ranking[0])
            true_indices.append(expected)
            predicted_indices.append(predicted)
            records.append({
                "record_id": row["record_id"],
                "label": row["source_label"],
                "raw_video_sha256": row["raw_video_sha256"],
                "decoded_frames": extracted["decoded_frames"],
                "motion_start": extracted["motion_start"],
                "motion_end": extracted["motion_end"],
                "cached_motion_start": int(cached_metadata["motion_start"]),
                "cached_motion_end": int(cached_metadata["motion_end"]),
                "complete_trajectory_frames": extracted["complete_frames"],
                "pose_present_frames": extracted["pose_present_frames"],
                "left_hand_present_frames": extracted["left_hand_present_frames"],
                "right_hand_present_frames": extracted["right_hand_present_frames"],
                "interpolated_left_frames": extracted["interpolated_left_frames"],
                "interpolated_right_frames": extracted["interpolated_right_frames"],
                "cache_max_absolute_difference": float(difference.max()),
                "cache_mean_absolute_difference": float(difference.mean()),
                "cache_numeric_match_1e_6": bool(difference.max() <= 1e-6),
                "raw_top1": labels[predicted],
                "raw_top1_probability": float(probabilities[predicted]),
                "top1_top2_margin": float(probabilities[ranking[0]] - probabilities[ranking[1]]),
                "raw_correct": predicted == expected,
                "top5": [
                    {"label": labels[int(index)], "probability": float(probabilities[index])}
                    for index in ranking[:5]
                ],
                "extraction_ms": extracted["extraction_ms"],
                "tflite_ms": latency_ms,
            })
            print(
                f"[{position:02d}/60] {row['source_label']} -> {labels[predicted]} "
                f"correct={predicted == expected} cache_max={difference.max():.3g}",
                flush=True,
            )
        except Exception as error:
            failures.append({
                "record_id": row["record_id"],
                "label": row["source_label"],
                "error": f"{type(error).__name__}: {error}",
            })
            print(f"[{position:02d}/60] FAILED {row['record_id']}: {error}", flush=True)

    if failures:
        raise RuntimeError(f"raw replay had {len(failures)} extraction failures: {failures}")
    metrics = classification_report(
        true_indices,
        predicted_indices,
        labels=list(range(len(labels))),
        target_names=labels,
        output_dict=True,
        zero_division=0,
    )
    confusions = []
    matrix = confusion_matrix(true_indices, predicted_indices, labels=list(range(len(labels))))
    for actual in range(len(labels)):
        for predicted in range(len(labels)):
            if actual != predicted and matrix[actual, predicted]:
                confusions.append({
                    "true": labels[actual],
                    "predicted": labels[predicted],
                    "count": int(matrix[actual, predicted]),
                })
    confusions.sort(key=lambda item: (-item["count"], item["true"], item["predicted"]))
    class_counts = Counter(row["label"] for row in records)
    report = {
        "completed_utc": datetime.now(timezone.utc).isoformat(),
        "scope": "SEALED_RAW_VIDEO_END_TO_END_REPLAY_SECOND_SEALED_EVALUATION",
        "dataset_profile": "MAPUA_PUBLISHED_FSL_ONLY_INTERIM",
        "source_count": len(records),
        "class_counts": dict(sorted(class_counts.items())),
        "raw_hash_verification": "PASS",
        "extraction_failure_count": 0,
        "input_shape": [1, 48, 225],
        "output_shape": [1, 15],
        "accuracy": float(metrics["accuracy"]),
        "macro_f1": float(metrics["macro avg"]["f1-score"]),
        "weakest_class_f1": float(min(metrics[label]["f1-score"] for label in labels)),
        "per_class": {
            label: {
                "precision": float(metrics[label]["precision"]),
                "recall": float(metrics[label]["recall"]),
                "f1": float(metrics[label]["f1-score"]),
                "support": int(metrics[label]["support"]),
            }
            for label in labels
        },
        "top_confusions": confusions[:10],
        "cache_numeric_match_count": sum(row["cache_numeric_match_1e_6"] for row in records),
        "cache_max_absolute_difference": max(row["cache_max_absolute_difference"] for row in records),
        "motion_envelope_exact_match_count": sum(
            row["motion_start"] == row["cached_motion_start"]
            and row["motion_end"] == row["cached_motion_end"]
            for row in records
        ),
        "median_extraction_ms": float(np.median([row["extraction_ms"] for row in records])),
        "median_tflite_ms": float(np.median([row["tflite_ms"] for row in records])),
        "p95_tflite_ms": float(np.percentile([row["tflite_ms"] for row in records], 95)),
        "mediapipe_version": mp.__version__,
        "tensorflow_version": tf.__version__,
        "model_sha256": sha256_file(MODEL_PATH),
        "split_sha256": sha256_file(SPLIT_PATH),
        "signer_independent_claim": False,
        "limitations": [
            "published source does not expose signer identity",
            "sealed support per class is small",
            "replay uses pre-recorded source clips rather than live Android camera events",
        ],
        "records": records,
    }
    RESULT_PATH.write_text(json.dumps(report, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(json.dumps({key: report[key] for key in (
        "source_count", "accuracy", "macro_f1", "weakest_class_f1",
        "top_confusions", "cache_numeric_match_count",
        "cache_max_absolute_difference", "motion_envelope_exact_match_count",
        "median_tflite_ms", "p95_tflite_ms",
    )}, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
