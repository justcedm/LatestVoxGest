"""Convert original published Mapua NPY landmarks into canonical FullSign225.

The source NPY files are immutable. Generated tensors are written only beneath
``VOXGEST_TRAINING_ROOT/MAPUA_NPY_CANONICAL_V1``.  Spatial normalization is
delegated to the same builder used by the MP4/Holistic pipeline; temporal
boundaries are derived independently from each original NPY.
"""

from __future__ import annotations

import csv
import hashlib
import io
import json
import os
import time
from collections import defaultdict
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

import numpy as np

from fullsign225_feature_builder import FEATURE_SIZE, build_fullsign225_from_arrays

REPO_ROOT = Path(__file__).resolve().parents[1]
CONFIG_PATH = REPO_ROOT / "training_configs" / "mapua_npy_canonical_v1.json"
BASE_CONFIG_PATH = REPO_ROOT / "training_configs" / "fsl_practical15_mapua_v1.json"
REPORT_ROOT = REPO_ROOT / "reports" / "fsl_dual_dataset_reset_v1"
SPLIT_PATH = REPORT_ROOT / "SPLIT_MANIFEST.csv"
CLIP_METRICS_PATH = REPORT_ROOT / "MAPUA_NPY_CANONICAL_CLIP_METRICS.csv"
CLASS_METRICS_PATH = REPORT_ROOT / "MAPUA_NPY_CANONICAL_CLASS_METRICS.csv"
AUDIT_PATH = REPORT_ROOT / "MAPUA_NPY_CANONICAL_AUDIT.md"

MANIFEST_FIELDS = [
    "record_id", "relative_path", "source_npy_relative_path", "source_label",
    "class_index", "partition", "development_fold", "group_id",
    "source_mp4_sha256", "source_npy_sha256", "source_frame_count",
    "source_feature_dimension", "source_dtype", "motion_start", "motion_end",
    "complete_trajectory_frames", "pose_present_frames",
    "left_hand_present_frames", "right_hand_present_frames",
    "interpolated_left_frames", "interpolated_right_frames",
    "trajectory_motion_mean_l2", "trajectory_motion_p95_l2",
    "trajectory_motion_max_l2", "output_relative_path", "output_tensor_sha256",
    "output_archive_sha256", "converter_version", "status", "error",
]

CLIP_FIELDS = [
    "record_id", "source_label", "relative_path", "partition", "development_fold",
    "mae", "rmse", "pearson", "cosine", "mp4_motion_start", "npy_motion_start",
    "motion_start_difference", "mp4_motion_end", "npy_motion_end",
    "motion_end_difference", "mp4_pose_present_frames", "npy_pose_present_frames",
    "pose_presence_difference", "mp4_left_hand_present_frames",
    "npy_left_hand_present_frames", "left_hand_presence_difference",
    "mp4_right_hand_present_frames", "npy_right_hand_present_frames",
    "right_hand_presence_difference", "mp4_trajectory_motion_mean_l2",
    "npy_trajectory_motion_mean_l2", "mp4_trajectory_motion_p95_l2",
    "npy_trajectory_motion_p95_l2", "mp4_trajectory_motion_max_l2",
    "npy_trajectory_motion_max_l2",
]

CLASS_FIELDS = [
    "source_label", "source_count", "mean_mae", "median_mae", "p95_mae",
    "mean_rmse", "mean_pearson", "mean_cosine",
    "mean_abs_motion_start_difference", "mean_abs_motion_end_difference",
    "mean_abs_pose_presence_difference", "mean_abs_left_hand_presence_difference",
    "mean_abs_right_hand_presence_difference", "mp4_mean_trajectory_motion_l2",
    "npy_mean_trajectory_motion_l2", "mp4_mean_trajectory_motion_p95_l2",
    "npy_mean_trajectory_motion_p95_l2",
]


def required_root(name: str) -> Path:
    value = os.environ.get(name, "").strip()
    if not value:
        raise RuntimeError(f"{name} must point to an approved external safe-C root")
    root = Path(value).resolve()
    if not root.is_dir():
        raise RuntimeError(f"{name} does not exist: {root}")
    return root


def load_csv(path: Path) -> list[dict[str, str]]:
    with path.open("r", encoding="utf-8-sig", newline="") as stream:
        return list(csv.DictReader(stream))


def csv_text(rows: list[dict[str, Any]], fields: list[str]) -> str:
    output = io.StringIO(newline="")
    writer = csv.DictWriter(output, fieldnames=fields, lineterminator="\n")
    writer.writeheader()
    writer.writerows(rows)
    return output.getvalue()


def atomic_text(path: Path, content: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_suffix(path.suffix + ".tmp")
    temporary.write_text(content, encoding="utf-8", newline="")
    temporary.replace(path)


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def sha256_tensor(value: np.ndarray) -> str:
    contiguous = np.ascontiguousarray(value, dtype=np.float32)
    return hashlib.sha256(contiguous.tobytes(order="C")).hexdigest()


def block_or_none(block: np.ndarray, expected_shape: tuple[int, int]) -> np.ndarray | None:
    if block.shape != expected_shape:
        raise ValueError(f"unexpected landmark block shape: {block.shape}")
    if np.all(block == 0.0):
        return None
    return block.astype(np.float32)


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
            result[start + offset] = (
                (np.float32(1.0) - weight) * before + weight * after
            ).astype(np.float32)
            filled += 1
    return result, filled


def detect_motion_envelope(
    centers: list[np.ndarray | None], boundary: int
) -> tuple[int, int]:
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
    threshold = max(
        0.004,
        baseline + 1.5 * mad,
        float(np.percentile(speeds, 55)) * 0.75,
    )
    active = [frame for frame, speed in motion if speed >= threshold]
    if not active:
        active = [motion[int(np.argmax(speeds))][0]]
    return max(0, min(active) - boundary), min(len(centers) - 1, max(active) + boundary)


def resample_complete(sequence: np.ndarray, length: int = 48) -> np.ndarray:
    if sequence.ndim != 2 or sequence.shape[1] != FEATURE_SIZE or len(sequence) == 0:
        raise ValueError(f"invalid complete trajectory: {sequence.shape}")
    if len(sequence) == 1:
        return np.repeat(sequence, length, axis=0).astype(np.float32)
    target = np.linspace(0, len(sequence) - 1, length, dtype=np.float32)
    low = np.floor(target).astype(np.int64)
    high = np.minimum(low + 1, len(sequence) - 1)
    alpha = (target - low).reshape(-1, 1)
    return ((1.0 - alpha) * sequence[low] + alpha * sequence[high]).astype(np.float32)


def trajectory_motion(sequence: np.ndarray) -> dict[str, float]:
    if len(sequence) < 2:
        return {"mean": 0.0, "p95": 0.0, "max": 0.0}
    values = np.linalg.norm(np.diff(sequence.astype(np.float64), axis=0), axis=1)
    return {
        "mean": float(np.mean(values)),
        "p95": float(np.percentile(values, 95)),
        "max": float(np.max(values)),
    }


def convert_source(
    source: np.ndarray, boundary: int, maximum_gap: int
) -> tuple[np.ndarray, np.ndarray, dict[str, Any]]:
    if source.shape != (75, 225):
        raise ValueError(f"expected source shape (75, 225), got {source.shape}")
    if source.dtype != np.float64:
        raise ValueError(f"expected source dtype float64, got {source.dtype}")
    if not np.isfinite(source).all():
        raise ValueError("source contains NaN or Inf")

    poses: list[np.ndarray | None] = []
    lefts: list[np.ndarray | None] = []
    rights: list[np.ndarray | None] = []
    for frame in source:
        poses.append(block_or_none(frame[0:99].reshape(33, 3), (33, 3)))
        lefts.append(block_or_none(frame[99:162].reshape(21, 3), (21, 3)))
        rights.append(block_or_none(frame[162:225].reshape(21, 3), (21, 3)))

    left_fixed, left_filled = interpolate_short_gaps(lefts, maximum_gap)
    right_fixed, right_filled = interpolate_short_gaps(rights, maximum_gap)
    centers: list[np.ndarray | None] = []
    for left, right in zip(left_fixed, right_fixed):
        hands = [hand.mean(axis=0) for hand in (left, right) if hand is not None]
        centers.append(np.mean(hands, axis=0).astype(np.float32) if hands else None)
    start, end = detect_motion_envelope(centers, boundary)

    vectors = [
        build_fullsign225_from_arrays(pose, left, right, input_mirrored=False).vector
        for pose, left, right in zip(
            poses[start : end + 1],
            left_fixed[start : end + 1],
            right_fixed[start : end + 1],
        )
    ]
    complete = np.asarray(vectors, dtype=np.float32)
    sequence = resample_complete(complete, 48)
    if sequence.shape != (48, 225) or sequence.dtype != np.float32:
        raise ValueError(f"invalid canonical output: {sequence.shape} {sequence.dtype}")
    if not np.isfinite(complete).all() or not np.isfinite(sequence).all():
        raise ValueError("canonical output contains NaN or Inf")
    motion = trajectory_motion(sequence)
    metadata = {
        "motion_start": start,
        "motion_end": end,
        "complete_trajectory_frames": len(complete),
        "pose_present_frames": sum(item is not None for item in poses),
        "left_hand_present_frames": sum(item is not None for item in lefts),
        "right_hand_present_frames": sum(item is not None for item in rights),
        "interpolated_left_frames": left_filled,
        "interpolated_right_frames": right_filled,
        "trajectory_motion_mean_l2": motion["mean"],
        "trajectory_motion_p95_l2": motion["p95"],
        "trajectory_motion_max_l2": motion["max"],
    }
    return complete, sequence, metadata


def comparison_metrics(a: np.ndarray, b: np.ndarray) -> dict[str, float]:
    if a.shape != (48, 225) or b.shape != (48, 225):
        raise ValueError(f"comparison shape failure: {a.shape} vs {b.shape}")
    a64, b64 = a.astype(np.float64), b.astype(np.float64)
    difference = a64 - b64
    flat_a, flat_b = a64.reshape(-1), b64.reshape(-1)
    union = (np.abs(flat_a) + np.abs(flat_b)) > 0.0
    pearson = float(np.corrcoef(flat_a[union], flat_b[union])[0, 1]) if union.sum() > 1 else 1.0
    denominator = float(np.linalg.norm(flat_a) * np.linalg.norm(flat_b))
    cosine = float(np.dot(flat_a, flat_b) / denominator) if denominator else 1.0
    return {
        "mae": float(np.mean(np.abs(difference))),
        "rmse": float(np.sqrt(np.mean(np.square(difference)))),
        "pearson": pearson,
        "cosine": cosine,
    }


def f(value: float) -> str:
    return f"{value:.9f}"


def summarize_classes(rows: list[dict[str, Any]], labels: list[str]) -> list[dict[str, Any]]:
    grouped: dict[str, list[dict[str, Any]]] = defaultdict(list)
    for row in rows:
        grouped[row["source_label"]].append(row)
    result = []
    for label in labels:
        items = grouped[label]
        values = lambda key: np.asarray([float(item[key]) for item in items], dtype=np.float64)
        mae = values("mae")
        result.append({
            "source_label": label,
            "source_count": len(items),
            "mean_mae": f(float(mae.mean())),
            "median_mae": f(float(np.median(mae))),
            "p95_mae": f(float(np.percentile(mae, 95))),
            "mean_rmse": f(float(values("rmse").mean())),
            "mean_pearson": f(float(values("pearson").mean())),
            "mean_cosine": f(float(values("cosine").mean())),
            "mean_abs_motion_start_difference": f(float(np.abs(values("motion_start_difference")).mean())),
            "mean_abs_motion_end_difference": f(float(np.abs(values("motion_end_difference")).mean())),
            "mean_abs_pose_presence_difference": f(float(np.abs(values("pose_presence_difference")).mean())),
            "mean_abs_left_hand_presence_difference": f(float(np.abs(values("left_hand_presence_difference")).mean())),
            "mean_abs_right_hand_presence_difference": f(float(np.abs(values("right_hand_presence_difference")).mean())),
            "mp4_mean_trajectory_motion_l2": f(float(values("mp4_trajectory_motion_mean_l2").mean())),
            "npy_mean_trajectory_motion_l2": f(float(values("npy_trajectory_motion_mean_l2").mean())),
            "mp4_mean_trajectory_motion_p95_l2": f(float(values("mp4_trajectory_motion_p95_l2").mean())),
            "npy_mean_trajectory_motion_p95_l2": f(float(values("npy_trajectory_motion_p95_l2").mean())),
        })
    return result


def write_audit(
    config: dict[str, Any], manifest_rows: list[dict[str, Any]],
    clip_rows: list[dict[str, Any]], class_rows: list[dict[str, Any]],
    original_count: int, failures: list[dict[str, Any]],
) -> None:
    mean = lambda key: float(np.mean([float(row[key]) for row in clip_rows]))
    attention = {"HOW_MANY", "HOW_MUCH", "CASH", "CARD", "COIN", "NO", "YES", "HELLO"}
    lines = [
        "# Mapua original-NPY canonicalization audit", "",
        f"Generated UTC: {datetime.now(timezone.utc).isoformat()}", "",
        "## Result", "",
        f"- Original published NPY inventory: {original_count} files.",
        f"- Frozen Practical15 selected clips converted: {sum(row['status'] == 'PASS' for row in manifest_rows)}.",
        f"- Conversion failures: {len(failures)}.",
        "- Source contract: `float64 [75,225]`, raw MediaPipe `pose99|left63|right63`.",
        "- Output contract: `float32 [48,225]`, canonical unmirrored FullSign225.",
        "- Motion boundaries are derived from each original NPY; paired MP4 boundaries are never borrowed for conversion.",
        "- Same-source partition, group, and development-fold assignments are copied unchanged from the frozen split.",
        "- Sealed sources were deterministically converted for catalog parity only; no sealed classifier inference, metric, or model selection was performed.",
        "", "## Aggregate A vs B tensor parity", "",
        f"- Mean per-clip MAE: {mean('mae'):.9f}.",
        f"- Mean per-clip RMSE: {mean('rmse'):.9f}.",
        f"- Mean per-clip Pearson correlation: {mean('pearson'):.9f}.",
        f"- Mean per-clip cosine similarity: {mean('cosine'):.9f}.",
        "", "A is MP4 -> Python Holistic -> canonical48. B is original NPY -> canonical48.",
        "High correlation does not establish Android-domain superiority; Samsung Tasks evidence is still required.",
        "", "## Per-class parity", "",
        "| Class | N | MAE | RMSE | Pearson | Cosine | |start delta| | |end delta| |",
        "|---|---:|---:|---:|---:|---:|---:|---:|",
    ]
    for row in class_rows:
        label = f"**{row['source_label']}**" if row["source_label"] in attention else row["source_label"]
        lines.append(
            f"| {label} | {row['source_count']} | {row['mean_mae']} | {row['mean_rmse']} | "
            f"{row['mean_pearson']} | {row['mean_cosine']} | "
            f"{row['mean_abs_motion_start_difference']} | {row['mean_abs_motion_end_difference']} |"
        )
    lines += [
        "", "## Interpretation boundary", "",
        "The supplied NPYs retain the raw pose and anatomical hand coordinates needed to construct",
        "VoxGest FullSign225, but they do not carry extractor version/settings, landmark confidence,",
        "pose visibility, timestamps, or pixels. Different hand detections and NPY-derived motion",
        "envelopes therefore produce a related but non-identical training representation.",
        "Development-only matched RD-TCN48 evidence, not this distance audit alone, decides which",
        "offline representation is stronger. Neither representation is claimed to match Android",
        "MediaPipe Tasks until the three-domain Samsung capture is performed.",
        "", "## Artifacts", "",
        "- `MAPUA_NPY_CANONICAL_CLIP_METRICS.csv`: one row per frozen selected source.",
        "- `MAPUA_NPY_CANONICAL_CLASS_METRICS.csv`: class aggregates.",
        "- Bulk tensors and the conversion manifest remain outside Git under the configured safe-C experiment root.",
    ]
    atomic_text(AUDIT_PATH, "\n".join(lines) + "\n")


def main() -> int:
    started = time.perf_counter()
    config = json.loads(CONFIG_PATH.read_text(encoding="utf-8"))
    base_config = json.loads(BASE_CONFIG_PATH.read_text(encoding="utf-8"))
    dataset_root = required_root("VOXGEST_DATASETS_ROOT")
    training_root = required_root("VOXGEST_TRAINING_ROOT")
    raw_root = dataset_root / config["selection"]["raw_video_root"]
    external_root = training_root / config["output"]["external_experiment_root"]
    feature_root = external_root / config["output"]["feature_directory"]
    external_manifest = external_root / config["output"]["feature_manifest"]
    feature_root.mkdir(parents=True, exist_ok=True)

    original_count = sum(1 for _ in raw_root.rglob("*.npy"))
    expected_original = int(config["selection"]["expected_original_npy_count"])
    if original_count != expected_original:
        raise RuntimeError(f"original NPY count changed: expected {expected_original}, got {original_count}")

    frozen = load_csv(SPLIT_PATH)
    expected_selected = int(config["selection"]["expected_selected_clip_count"])
    if len(frozen) != expected_selected:
        raise RuntimeError(f"frozen selected count changed: expected {expected_selected}, got {len(frozen)}")
    if len({row["record_id"] for row in frozen}) != len(frozen):
        raise RuntimeError("duplicate frozen record IDs")
    current_manifest_path = training_root / config["selection"]["current_feature_manifest"]
    current = {row["record_id"]: row for row in load_csv(current_manifest_path)}

    manifest_rows: list[dict[str, Any]] = []
    clip_rows: list[dict[str, Any]] = []
    failures: list[dict[str, Any]] = []
    boundary = int(config["feature_contract"]["motion_boundary_frames"])
    maximum_gap = int(config["feature_contract"]["maximum_internal_interpolation_gap_frames"])

    for position, row in enumerate(frozen, start=1):
        base = {
            "record_id": row["record_id"], "relative_path": row["relative_path"],
            "source_label": row["source_label"], "class_index": row["class_index"],
            "partition": row["partition"], "development_fold": row["development_fold"],
            "group_id": row["group_id"], "converter_version": config["converter_version"],
        }
        try:
            a_row = current.get(row["record_id"])
            if a_row is None:
                raise RuntimeError("current MP4 feature manifest row missing")
            if a_row["source_label"] != row["source_label"]:
                raise RuntimeError("current feature label mismatch")
            mp4_path = raw_root / Path(row["relative_path"])
            npy_path = mp4_path.with_suffix(".npy")
            if mp4_path.suffix.lower() != ".mp4" or not mp4_path.is_file() or not npy_path.is_file():
                raise RuntimeError("paired MP4/NPY source missing")
            mp4_hash = sha256_file(mp4_path)
            if mp4_hash.lower() != row["raw_video_sha256"].lower():
                raise RuntimeError("source MP4 hash mismatch")
            source = np.load(npy_path, allow_pickle=False)
            complete, sequence, metadata = convert_source(source, boundary, maximum_gap)
            npy_relative = npy_path.relative_to(raw_root).as_posix()
            output_relative = Path(config["output"]["feature_directory"]) / row["source_label"] / f"{row['record_id']}.npz"
            output_path = external_root / output_relative
            output_path.parent.mkdir(parents=True, exist_ok=True)
            npy_hash = sha256_file(npy_path)
            archive_metadata = {
                **metadata,
                "record_id": row["record_id"],
                "source_label": row["source_label"],
                "source_mp4_relative_path": row["relative_path"],
                "source_npy_relative_path": npy_relative,
                "source_mp4_sha256": mp4_hash,
                "source_npy_sha256": npy_hash,
                "source_frame_count": 75,
                "source_feature_dimension": 225,
                "source_dtype": "float64",
                "converter_version": config["converter_version"],
                "feature_contract_version": config["feature_contract"]["version"],
                "coordinate_orientation": "canonical_unmirrored",
                "slot_swapping": False,
            }
            temporary = output_path.with_suffix(".npz.tmp")
            with temporary.open("wb") as stream:
                np.savez_compressed(
                    stream,
                    complete_trajectory=complete,
                    sequence_48=sequence,
                    metadata=json.dumps(archive_metadata, sort_keys=True),
                )
            temporary.replace(output_path)
            manifest_row = {
                **base,
                "source_npy_relative_path": npy_relative,
                "source_mp4_sha256": mp4_hash,
                "source_npy_sha256": npy_hash,
                "source_frame_count": 75,
                "source_feature_dimension": 225,
                "source_dtype": "float64",
                **metadata,
                "output_relative_path": output_relative.as_posix(),
                "output_tensor_sha256": sha256_tensor(sequence),
                "output_archive_sha256": sha256_file(output_path),
                "status": "PASS", "error": "",
            }
            manifest_rows.append(manifest_row)

            a_path = Path(a_row["feature_path"])
            if sha256_file(a_path).lower() != row["feature_sha256"].lower():
                raise RuntimeError("current MP4 feature archive hash mismatch")
            with np.load(a_path, allow_pickle=False) as archive:
                a_sequence = archive["sequence_48"].astype(np.float32)
                a_metadata = json.loads(str(archive["metadata"].item()))
            parity = comparison_metrics(a_sequence, sequence)
            a_motion, b_motion = trajectory_motion(a_sequence), trajectory_motion(sequence)
            clip_rows.append({
                "record_id": row["record_id"], "source_label": row["source_label"],
                "relative_path": row["relative_path"], "partition": row["partition"],
                "development_fold": row["development_fold"],
                **{key: f(value) for key, value in parity.items()},
                "mp4_motion_start": int(a_metadata["motion_start"]), "npy_motion_start": metadata["motion_start"],
                "motion_start_difference": metadata["motion_start"] - int(a_metadata["motion_start"]),
                "mp4_motion_end": int(a_metadata["motion_end"]), "npy_motion_end": metadata["motion_end"],
                "motion_end_difference": metadata["motion_end"] - int(a_metadata["motion_end"]),
                "mp4_pose_present_frames": int(a_metadata["pose_present_frames"]),
                "npy_pose_present_frames": metadata["pose_present_frames"],
                "pose_presence_difference": metadata["pose_present_frames"] - int(a_metadata["pose_present_frames"]),
                "mp4_left_hand_present_frames": int(a_metadata["left_hand_present_frames"]),
                "npy_left_hand_present_frames": metadata["left_hand_present_frames"],
                "left_hand_presence_difference": metadata["left_hand_present_frames"] - int(a_metadata["left_hand_present_frames"]),
                "mp4_right_hand_present_frames": int(a_metadata["right_hand_present_frames"]),
                "npy_right_hand_present_frames": metadata["right_hand_present_frames"],
                "right_hand_presence_difference": metadata["right_hand_present_frames"] - int(a_metadata["right_hand_present_frames"]),
                "mp4_trajectory_motion_mean_l2": f(a_motion["mean"]),
                "npy_trajectory_motion_mean_l2": f(b_motion["mean"]),
                "mp4_trajectory_motion_p95_l2": f(a_motion["p95"]),
                "npy_trajectory_motion_p95_l2": f(b_motion["p95"]),
                "mp4_trajectory_motion_max_l2": f(a_motion["max"]),
                "npy_trajectory_motion_max_l2": f(b_motion["max"]),
            })
            print(f"[{position:03d}/{len(frozen)}] {row['source_label']} {row['record_id']} PASS", flush=True)
        except Exception as error:
            failed = {
                **base,
                **{field: "" for field in MANIFEST_FIELDS if field not in base},
                "status": "FAIL", "error": f"{type(error).__name__}: {error}",
            }
            manifest_rows.append(failed)
            failures.append(failed)
            print(f"[{position:03d}/{len(frozen)}] {row['source_label']} {row['record_id']} FAIL {error}", flush=True)

    manifest_rows.sort(key=lambda item: (int(item["class_index"]), item["relative_path"]))
    clip_rows.sort(key=lambda item: (base_config["labels"].index(item["source_label"]), item["relative_path"]))
    atomic_text(external_manifest, csv_text(manifest_rows, MANIFEST_FIELDS))
    atomic_text(CLIP_METRICS_PATH, csv_text(clip_rows, CLIP_FIELDS))
    class_rows = summarize_classes(clip_rows, base_config["labels"]) if clip_rows else []
    atomic_text(CLASS_METRICS_PATH, csv_text(class_rows, CLASS_FIELDS))
    write_audit(config, manifest_rows, clip_rows, class_rows, original_count, failures)
    summary = {
        "completed_utc": datetime.now(timezone.utc).isoformat(),
        "converter_version": config["converter_version"],
        "original_npy_count": original_count,
        "selected_clip_count": len(frozen),
        "converted": sum(row["status"] == "PASS" for row in manifest_rows),
        "failures": len(failures),
        "source_contract": "float64[75,225]",
        "output_contract": "float32[48,225]",
        "mean_mae": float(np.mean([float(row["mae"]) for row in clip_rows])) if clip_rows else None,
        "mean_pearson": float(np.mean([float(row["pearson"]) for row in clip_rows])) if clip_rows else None,
        "mean_cosine": float(np.mean([float(row["cosine"]) for row in clip_rows])) if clip_rows else None,
        "sealed_classifier_evaluation": False,
        "elapsed_seconds": time.perf_counter() - started,
    }
    atomic_text(external_root / "conversion_summary.json", json.dumps(summary, indent=2, sort_keys=True) + "\n")
    print(json.dumps(summary, indent=2, sort_keys=True))
    return 2 if failures else 0


if __name__ == "__main__":
    raise SystemExit(main())
