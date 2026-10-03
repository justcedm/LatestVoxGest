"""Freeze the PASS-only Mapua-14 split and re-extract canonical FullSign225.

Generated feature tensors stay in C:/VOXGEST_TRAINING/MAPUA14_RESCUE_V1.
Only the immutable source-video split and concise reproducibility reports enter Git.
"""

from __future__ import annotations

import argparse
import csv
import hashlib
import json
import random
import sys
import time
from collections import Counter, defaultdict
from concurrent.futures import ProcessPoolExecutor, as_completed
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

import cv2
import mediapipe as mp
import numpy as np

from fullsign225_feature_builder import (
    FEATURE_LAYOUT,
    FEATURE_SIZE,
    FRAME_FEATURE_VERSION,
    NORMALIZATION_POLICY,
    build_fullsign225_from_holistic,
)

REPO_ROOT = Path(__file__).resolve().parents[1]
CONFIG_PATH = REPO_ROOT / "training_configs" / "mapua14_rescue_v1.json"
REPORT_ROOT = REPO_ROOT / "reports" / "mapua14_rescue_v1"
SPLIT_PATH = REPORT_ROOT / "split_manifest.csv"
SUMMARY_PATH = REPORT_ROOT / "dataset_and_split_summary.json"
EXPERIMENT_ROOT = Path(r"C:\VOXGEST_TRAINING\MAPUA14_RESCUE_V1")
FEATURE_ROOT = EXPERIMENT_ROOT / "features"
EXTERNAL_MANIFEST = EXPERIMENT_ROOT / "feature_manifest.csv"
EXPECTED_SOURCE_COMMIT = "7929cd28027feb34b83eb3ad0649f39f5fb00abe"
CSV_FIELDS = [
    "record_id", "relative_path", "source_label", "class_index", "raw_video_sha256",
    "technical_status", "partition", "development_fold", "group_id",
]
FEATURE_FIELDS = CSV_FIELDS + [
    "feature_path", "feature_extractor_version", "mediapipe_version", "decoded_frames",
    "fps", "motion_start", "motion_end", "motion_boundary_frames", "pose_present_frames",
    "left_hand_present_frames", "right_hand_present_frames", "interpolated_left_frames",
    "interpolated_right_frames", "temporal_lengths", "missing_landmark_policy",
    "feature_sha256", "processing_seconds", "status", "error",
]


def load_config() -> dict[str, Any]:
    return json.loads(CONFIG_PATH.read_text(encoding="utf-8"))


def sha256_bytes(content: bytes) -> str:
    return hashlib.sha256(content).hexdigest()


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def atomic_text(path: Path, content: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_suffix(path.suffix + ".tmp")
    temporary.write_text(content, encoding="utf-8", newline="")
    temporary.replace(path)


def csv_content(rows: list[dict[str, Any]], fields: list[str]) -> str:
    import io
    output = io.StringIO(newline="")
    writer = csv.DictWriter(output, fieldnames=fields, lineterminator="\n")
    writer.writeheader()
    writer.writerows(rows)
    return output.getvalue()


def freeze_split(config: dict[str, Any]) -> list[dict[str, str]]:
    dataset_root = Path(config["dataset"]["root"])
    audit_path = dataset_root / config["dataset"]["audit_csv"]
    labels = config["labels"]
    allowed = set(config["dataset"]["status_allowlist"])
    with audit_path.open("r", encoding="utf-8-sig", newline="") as stream:
        source = [
            row for row in csv.DictReader(stream)
            if row["class"] in labels and row["technical_status"] in allowed
        ]
    if len(source) != 367:
        raise RuntimeError(f"PASS-only source count changed: expected 367, got {len(source)}")
    if len({row["relative_path"] for row in source}) != len(source):
        raise RuntimeError("duplicate source path in selected records")
    sha_counts = Counter(row["sha256"].upper() for row in source)
    sha_to_group: dict[str, str] = {}
    for index, digest in enumerate(sorted(sha_counts)):
        sha_to_group[digest] = f"sha-{index:04d}-{digest[:12].lower()}"
    by_label: dict[str, list[dict[str, str]]] = defaultdict(list)
    for row in source:
        by_label[row["class"]].append(row)
    result: list[dict[str, str]] = []
    seed = int(config["random_seed"])
    for class_index, label in enumerate(labels):
        rows = sorted(by_label[label], key=lambda row: row["relative_path"])
        rng = random.Random(seed + class_index * 1009)
        rng.shuffle(rows)
        test_count = max(1, int(round(len(rows) * config["dataset"]["sealed_test_fraction"])))
        for position, row in enumerate(rows):
            sealed = position < test_count
            result.append({
                "record_id": sha256_bytes(row["relative_path"].encode("utf-8"))[:16],
                "relative_path": row["relative_path"].replace("\\", "/"),
                "source_label": label,
                "class_index": str(class_index),
                "raw_video_sha256": row["sha256"].lower(),
                "technical_status": row["technical_status"],
                "partition": "sealed_test" if sealed else "development",
                "development_fold": "" if sealed else str((position - test_count) % 4),
                "group_id": sha_to_group[row["sha256"].upper()],
            })
    result.sort(key=lambda row: (row["source_label"], row["relative_path"]))
    proposed = csv_content(result, CSV_FIELDS)
    if SPLIT_PATH.exists():
        current = SPLIT_PATH.read_text(encoding="utf-8")
        if current != proposed:
            raise RuntimeError("refusing to change frozen split_manifest.csv")
    else:
        atomic_text(SPLIT_PATH, proposed)
    return result


def validate_duplicate_isolation(config: dict[str, Any], records: list[dict[str, str]]) -> dict[str, int]:
    assignments = {
        row["relative_path"]: (row["partition"], row["development_fold"])
        for row in records
    }
    by_sha: dict[str, set[tuple[str, str]]] = defaultdict(set)
    for row in records:
        by_sha[row["raw_video_sha256"]].add((row["partition"], row["development_fold"]))
    exact_groups = sum(count > 1 for count in Counter(row["raw_video_sha256"] for row in records).values())
    exact_crossings = sum(len(group) > 1 for group in by_sha.values())
    near_selected = 0
    near_crossings = 0
    near_path = Path(config["dataset"]["root"]) / "audit" / "near_duplicate_video_pairs.csv"
    with near_path.open("r", encoding="utf-8-sig", newline="") as stream:
        for pair in csv.DictReader(stream):
            left = pair["LEFT_RELATIVE_PATH"].replace("\\", "/")
            right = pair["RIGHT_RELATIVE_PATH"].replace("\\", "/")
            if left in assignments and right in assignments:
                near_selected += 1
                if assignments[left] != assignments[right]:
                    near_crossings += 1
    if exact_crossings or near_crossings:
        raise RuntimeError(
            f"duplicate isolation failed: exact_crossings={exact_crossings} near_crossings={near_crossings}"
        )
    return {
        "selected_exact_duplicate_groups": exact_groups,
        "selected_near_duplicate_pairs": near_selected,
        "exact_duplicate_partition_crossings": exact_crossings,
        "near_duplicate_partition_crossings": near_crossings,
    }


def interpolate_short_gaps(values: list[np.ndarray | None], max_gap: int) -> tuple[list[np.ndarray | None], int]:
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


def resample_complete(sequence: np.ndarray, length: int) -> np.ndarray:
    if len(sequence) == 1:
        return np.repeat(sequence, length, axis=0).astype(np.float32)
    new = np.linspace(0, len(sequence) - 1, length, dtype=np.float32)
    low = np.floor(new).astype(np.int64)
    high = np.minimum(low + 1, len(sequence) - 1)
    alpha = (new - low).reshape(-1, 1)
    return ((1.0 - alpha) * sequence[low] + alpha * sequence[high]).astype(np.float32)


def detect_motion_envelope(centers: list[np.ndarray | None], boundary: int) -> tuple[int, int]:
    valid = [(i, value) for i, value in enumerate(centers) if value is not None]
    if not valid:
        return 0, max(0, len(centers) - 1)
    motion: list[tuple[int, float]] = []
    for (previous_i, previous), (current_i, current) in zip(valid, valid[1:]):
        gap = max(1, current_i - previous_i)
        motion.append((current_i, float(np.linalg.norm(current - previous) / gap)))
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


def extract_one(arguments: tuple[dict[str, str], dict[str, Any]]) -> dict[str, str]:
    record, config = arguments
    started = time.perf_counter()
    destination = FEATURE_ROOT / record["source_label"] / f"{record['record_id']}.npz"
    base = dict(record)
    try:
        if destination.is_file():
            with np.load(destination, allow_pickle=False) as existing:
                if existing["sequence_32"].shape == (32, FEATURE_SIZE) and existing["sequence_48"].shape == (48, FEATURE_SIZE):
                    metadata = json.loads(str(existing["metadata"].item()))
                    return {**base, **metadata, "feature_path": str(destination), "feature_sha256": sha256_file(destination), "processing_seconds": "0", "status": "CACHED", "error": ""}
        source = Path(config["dataset"]["raw_mp4_root"]) / Path(record["relative_path"])
        actual_sha = sha256_file(source)
        if actual_sha != record["raw_video_sha256"]:
            raise RuntimeError(f"raw SHA mismatch: {actual_sha}")
        capture = cv2.VideoCapture(str(source))
        fps = float(capture.get(cv2.CAP_PROP_FPS) or 0.0)
        pose_values: list[np.ndarray | None] = []
        left_values: list[np.ndarray | None] = []
        right_values: list[np.ndarray | None] = []
        with mp.solutions.holistic.Holistic(
            static_image_mode=False, model_complexity=1, smooth_landmarks=True,
            refine_face_landmarks=False,
            min_detection_confidence=config["feature_contract"]["mediapipe_min_detection_confidence"],
            min_tracking_confidence=config["feature_contract"]["mediapipe_min_tracking_confidence"],
        ) as holistic:
            while True:
                ok, bgr = capture.read()
                if not ok:
                    break
                results = holistic.process(cv2.cvtColor(bgr, cv2.COLOR_BGR2RGB))
                def array_or_none(value: Any, count: int) -> np.ndarray | None:
                    if value is None:
                        return None
                    array = np.asarray([[p.x, p.y, p.z] for p in value.landmark], dtype=np.float32)
                    return array if array.shape == (count, 3) and np.isfinite(array).all() else None
                pose_values.append(array_or_none(results.pose_landmarks, 33))
                left_values.append(array_or_none(results.left_hand_landmarks, 21))
                right_values.append(array_or_none(results.right_hand_landmarks, 21))
        capture.release()
        if not pose_values:
            raise RuntimeError("zero decoded video frames")
        max_gap = int(config["feature_contract"]["maximum_internal_interpolation_gap_frames"])
        left_fixed, left_filled = interpolate_short_gaps(left_values, max_gap)
        right_fixed, right_filled = interpolate_short_gaps(right_values, max_gap)
        centers: list[np.ndarray | None] = []
        for left, right in zip(left_fixed, right_fixed):
            hands = [hand.mean(axis=0) for hand in (left, right) if hand is not None]
            centers.append(np.mean(hands, axis=0).astype(np.float32) if hands else None)
        start, end = detect_motion_envelope(centers, int(config["feature_contract"]["motion_boundary_frames"]))
        vectors = []
        for pose, left, right in zip(pose_values[start:end + 1], left_fixed[start:end + 1], right_fixed[start:end + 1]):
            synthetic = type("Results", (), {"pose_landmarks": pose, "left_hand_landmarks": left, "right_hand_landmarks": right})()
            vectors.append(build_fullsign225_from_holistic(synthetic).vector)
        complete = np.asarray(vectors, dtype=np.float32)
        if complete.ndim != 2 or complete.shape[1] != FEATURE_SIZE or not np.isfinite(complete).all():
            raise RuntimeError(f"invalid complete trajectory shape {complete.shape}")
        seq32, seq48 = resample_complete(complete, 32), resample_complete(complete, 48)
        metadata = {
            "feature_extractor_version": config["feature_contract"]["version"], "mediapipe_version": mp.__version__,
            "decoded_frames": str(len(pose_values)), "fps": f"{fps:.6f}", "motion_start": str(start), "motion_end": str(end),
            "motion_boundary_frames": str(config["feature_contract"]["motion_boundary_frames"]),
            "pose_present_frames": str(sum(item is not None for item in pose_values)),
            "left_hand_present_frames": str(sum(item is not None for item in left_values)),
            "right_hand_present_frames": str(sum(item is not None for item in right_values)),
            "interpolated_left_frames": str(left_filled), "interpolated_right_frames": str(right_filled),
            "temporal_lengths": "32;48", "missing_landmark_policy": "pose_missing_zero_frame;hand_missing_fixed_zero_slot;internal_hand_gap_max3_linear",
        }
        destination.parent.mkdir(parents=True, exist_ok=True)
        temporary = destination.with_suffix(".npz.tmp")
        with temporary.open("wb") as stream:
            np.savez_compressed(stream, sequence_32=seq32, sequence_48=seq48, metadata=json.dumps(metadata, sort_keys=True))
        temporary.replace(destination)
        return {**base, **metadata, "feature_path": str(destination), "feature_sha256": sha256_file(destination), "processing_seconds": f"{time.perf_counter() - started:.6f}", "status": "PASS", "error": ""}
    except Exception as error:
        return {**base, **{field: "" for field in FEATURE_FIELDS if field not in base}, "feature_path": str(destination), "processing_seconds": f"{time.perf_counter() - started:.6f}", "status": "FAIL", "error": f"{type(error).__name__}: {error}"}


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--freeze-only", action="store_true")
    parser.add_argument("--workers", type=int, default=3)
    args = parser.parse_args()
    config = load_config()
    if config["source_commit"] != EXPECTED_SOURCE_COMMIT:
        raise RuntimeError("unexpected source-commit contract")
    records = freeze_split(config)
    duplicate_evidence = validate_duplicate_isolation(config, records)
    class_counts = Counter(row["source_label"] for row in records)
    partition_counts = Counter(row["partition"] for row in records)
    fold_counts = Counter(row["development_fold"] for row in records if row["partition"] == "development")
    summary = {
        "experiment_id": config["experiment_id"], "created_utc": datetime.now(timezone.utc).isoformat(),
        "metric_scope": config["metric_scope"], "pass_only_video_count": len(records),
        "class_counts": dict(sorted(class_counts.items())), "partition_counts": dict(sorted(partition_counts.items())),
        "development_fold_counts": dict(sorted(fold_counts.items())), "split_manifest_sha256": sha256_file(SPLIT_PATH),
        "duplicate_policy": "exact SHA and audited perceptual-neighbor groups cannot cross a partition or development fold",
        "duplicate_evidence": duplicate_evidence,
        "signer_overlap_claim": "NOT_MEASURABLE_SIGNER_IDS_UNAVAILABLE", "source_video_overlap_count": 0,
        "feature_contract": {"frame_version": FRAME_FEATURE_VERSION, "layout": FEATURE_LAYOUT, "normalization": NORMALIZATION_POLICY, **config["feature_contract"]},
        "raw_features_checkpoints_outside_git": str(EXPERIMENT_ROOT),
    }
    atomic_text(SUMMARY_PATH, json.dumps(summary, indent=2, sort_keys=True) + "\n")
    print(json.dumps({"split": summary, "freeze_only": args.freeze_only}, indent=2))
    if args.freeze_only:
        return 0
    FEATURE_ROOT.mkdir(parents=True, exist_ok=True)
    results: list[dict[str, str]] = []
    with ProcessPoolExecutor(max_workers=max(1, args.workers)) as pool:
        futures = {pool.submit(extract_one, (record, config)): record for record in records}
        for completed, future in enumerate(as_completed(futures), start=1):
            row = future.result()
            results.append(row)
            print(f"[{completed}/{len(records)}] {row['source_label']} {row['record_id']} {row['status']}", flush=True)
            if completed % 10 == 0:
                atomic_text(EXTERNAL_MANIFEST, csv_content(sorted(results, key=lambda item: (item["source_label"], item["relative_path"])), FEATURE_FIELDS))
    results.sort(key=lambda item: (item["source_label"], item["relative_path"]))
    atomic_text(EXTERNAL_MANIFEST, csv_content(results, FEATURE_FIELDS))
    failures = [row for row in results if row["status"] == "FAIL"]
    if failures:
        print(json.dumps(failures, indent=2), file=sys.stderr)
        return 2
    summary["extraction"] = {"completed": len(results), "failed": 0, "mediapipe_version": mp.__version__, "external_feature_manifest": str(EXTERNAL_MANIFEST), "external_feature_manifest_sha256": sha256_file(EXTERNAL_MANIFEST)}
    atomic_text(SUMMARY_PATH, json.dumps(summary, indent=2, sort_keys=True) + "\n")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
