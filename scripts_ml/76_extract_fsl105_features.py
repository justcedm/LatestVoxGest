"""Extract train-only FSL-105 windows for confirmed one-handed labels.

The extractor is deterministic, resumable, and ownership-aware: it may clean
artifacts produced from FSL-105, but it never removes Samsung or PC captures.
"""

from __future__ import annotations

import argparse
import csv
import hashlib
import json
import multiprocessing
import os
import re
from concurrent.futures import ProcessPoolExecutor, as_completed
from pathlib import Path
from typing import Any

import numpy as np

from fsl_config import FSL_FEATURE_SIZE, FSL_SEQUENCE_LENGTH
from voxgest_feature_builder import (
    FEATURE_LAYOUT,
    FEATURE_VERSION,
    MIN_WRIST_MCP_SCALE,
    NORMALIZATION_POLICY,
    Z_DAMPING,
    extract_frame_features,
)


ROOT = Path(__file__).resolve().parents[1]
RAW_ROOT = ROOT / "external_datasets" / "fsl105_raw"
CLIPS_ROOT = RAW_ROOT / "clips"
TRAIN_CSV = RAW_ROOT / "train.csv"
CONFIRMED_LABELS_PATH = ROOT / "reports" / "fsl" / "confirmed_onehanded_labels.json"
OUTPUT_ROOT = ROOT / "external_datasets" / "fsl_features"
SUMMARY_PATH = ROOT / "reports" / "fsl" / "extraction_summary.json"
CHECKPOINT_PATH = ROOT / "reports" / "fsl" / "extraction_progress.json"

WINDOW_STRIDE = 5
MIN_RIGHT_HAND_FRAMES = 13
MODEL_COMPLEXITY = 1
MIN_DETECTION_CONFIDENCE = 0.5
MIN_TRACKING_CONFIDENCE = 0.5
VIDEO_EXTENSIONS = {".mp4", ".mov", ".avi", ".mkv", ".webm", ".m4v"}

_CV2 = None
_HOLISTIC = None


def write_json_atomic(path: Path, payload: Any) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_suffix(path.suffix + ".tmp")
    temporary.write_text(
        json.dumps(payload, indent=2, ensure_ascii=False) + "\n",
        encoding="utf-8",
    )
    temporary.replace(path)


def save_npy_atomic(path: Path, array: np.ndarray) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_suffix(path.suffix + ".tmp")
    with temporary.open("wb") as handle:
        np.save(handle, array, allow_pickle=False)
    temporary.replace(path)


def file_sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def normalize_label(value: Any) -> str:
    return re.sub(r"\s+", " ", str(value).strip().upper())


def safe_stem(value: Any) -> str:
    text = re.sub(r"[^A-Za-z0-9_-]+", "_", str(value).strip())
    return text.strip("_") or "video"


def load_confirmed_labels() -> list[str]:
    try:
        payload = json.loads(CONFIRMED_LABELS_PATH.read_text(encoding="utf-8"))
    except FileNotFoundError as exc:
        raise SystemExit(f"Confirmed-label file not found: {CONFIRMED_LABELS_PATH}") from exc
    except json.JSONDecodeError as exc:
        raise SystemExit(f"Invalid confirmed-label JSON: {exc}") from exc
    if not isinstance(payload, list) or not all(isinstance(item, str) for item in payload):
        raise SystemExit("confirmed_onehanded_labels.json must be a flat string array")
    labels = [normalize_label(item) for item in payload]
    if len(labels) != len(set(labels)):
        raise SystemExit("confirmed_onehanded_labels.json contains duplicate labels")
    if len(labels) < 10:
        raise SystemExit(f"Only {len(labels)} confirmed labels; at least 10 are required")
    return labels


def read_train_tasks(labels: list[str]) -> list[dict[str, str]]:
    label_set = set(labels)
    tasks: list[dict[str, str]] = []
    output_identities: set[tuple[str, str]] = set()
    with TRAIN_CSV.open("r", encoding="utf-8-sig", newline="") as handle:
        rows = csv.DictReader(handle)
        for row in rows:
            label = normalize_label(row.get("label", ""))
            if label not in label_set:
                continue
            relative_video = str(row.get("vid_path", "")).strip().replace("\\", "/")
            if not relative_video:
                raise SystemExit(f"Missing vid_path in train.csv row: {row}")
            video_path = CLIPS_ROOT / Path(relative_video)
            if not video_path.is_file():
                fallback = RAW_ROOT / Path(relative_video)
                if fallback.is_file():
                    video_path = fallback
                else:
                    raise SystemExit(f"Train video not found: {relative_video}")
            if video_path.suffix.lower() not in VIDEO_EXTENSIONS:
                raise SystemExit(f"Unsupported train video extension: {video_path}")
            video_stem = safe_stem(video_path.stem)
            identity = (label, video_stem)
            if identity in output_identities:
                raise SystemExit(
                    "Requested filename would collide for duplicate label/video stem: "
                    f"label={label} stem={video_stem}"
                )
            output_identities.add(identity)
            key = f"{label}|{relative_video.lower()}"
            tasks.append(
                {
                    "key": key,
                    "label": label,
                    "relative_video": relative_video,
                    "video_path": str(video_path),
                    "video_stem": video_stem,
                }
            )

    tasks.sort(key=lambda item: (item["label"], item["relative_video"].lower()))
    present_labels = {task["label"] for task in tasks}
    missing = sorted(set(labels) - present_labels)
    if missing:
        raise SystemExit(f"Confirmed labels missing from train.csv: {missing}")
    return tasks


def build_config(labels: list[str], output_root: Path) -> dict[str, Any]:
    return {
        "confirmed_labels": labels,
        "confirmed_labels_sha256": file_sha256(CONFIRMED_LABELS_PATH),
        "train_csv_sha256": file_sha256(TRAIN_CSV),
        "output_root": str(output_root.resolve()),
        "sequence_length": FSL_SEQUENCE_LENGTH,
        "feature_size": FSL_FEATURE_SIZE,
        "stride": WINDOW_STRIDE,
        "minimum_right_hand_frames": MIN_RIGHT_HAND_FRAMES,
        "minimum_hand_presence_ratio": MIN_RIGHT_HAND_FRAMES / FSL_SEQUENCE_LENGTH,
        "feature_version": FEATURE_VERSION,
        "feature_layout": FEATURE_LAYOUT,
        "normalization": NORMALIZATION_POLICY,
        "wrist_mcp_scale_threshold": MIN_WRIST_MCP_SCALE,
        "z_damping": Z_DAMPING,
        "selected_hand": "right",
        "handedness_policy": "fixed_anatomical_right_holistic_slot",
        "mirrored_input": False,
        "model_complexity": MODEL_COMPLEXITY,
        "min_detection_confidence": MIN_DETECTION_CONFIDENCE,
        "min_tracking_confidence": MIN_TRACKING_CONFIDENCE,
        "split": "train.csv_only",
    }


def assert_safe_output_root(output_root: Path) -> None:
    resolved = output_root.resolve()
    allowed = (ROOT / "external_datasets").resolve()
    if not resolved.is_relative_to(allowed) or resolved == allowed:
        raise SystemExit(f"Unsafe output directory: {resolved}")


def fsl_owned(meta: dict[str, Any]) -> bool:
    return (
        str(meta.get("source", "")).strip().lower() == "fsl105"
        or str(meta.get("device_model", "")).strip().upper() == "FSL105_VIDEO"
    )


def explicitly_non_fsl_owned(meta_path: Path) -> bool:
    if not meta_path.is_file():
        return False
    try:
        meta = json.loads(meta_path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError):
        return False
    return isinstance(meta, dict) and not fsl_owned(meta)


def clean_existing_fsl105(output_root: Path) -> dict[str, int]:
    assert_safe_output_root(output_root)
    removed_npy: set[Path] = set()
    removed_meta: set[Path] = set()
    malformed_meta = 0

    if not output_root.exists():
        return {"removed_npy": 0, "removed_meta": 0, "malformed_meta": 0}

    for meta_path in sorted(output_root.rglob("*.meta.json")):
        try:
            meta = json.loads(meta_path.read_text(encoding="utf-8"))
        except (OSError, json.JSONDecodeError):
            malformed_meta += 1
            continue
        if not fsl_owned(meta):
            continue
        npy_path = meta_path.with_name(meta_path.name.removesuffix(".meta.json") + ".npy")
        if npy_path.is_file():
            npy_path.unlink()
            removed_npy.add(npy_path)
        meta_path.unlink()
        removed_meta.add(meta_path)

    # Canonical filenames are unambiguously generated by this extractor. This
    # also clears a partial file whose metadata was not yet atomically written.
    for npy_path in sorted(output_root.rglob("fsl105_*_w*.npy")):
        meta_path = npy_path.with_suffix(".meta.json")
        if explicitly_non_fsl_owned(meta_path):
            continue
        if npy_path.is_file():
            npy_path.unlink()
            removed_npy.add(npy_path)
        if meta_path.is_file():
            meta_path.unlink()
            removed_meta.add(meta_path)

    return {
        "removed_npy": len(removed_npy),
        "removed_meta": len(removed_meta),
        "malformed_meta": malformed_meta,
    }


def load_checkpoint(config: dict[str, Any]) -> dict[str, dict[str, Any]]:
    if not CHECKPOINT_PATH.is_file():
        return {}
    try:
        payload = json.loads(CHECKPOINT_PATH.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError):
        return {}
    checkpoint_config = payload.get("config")
    compatible = isinstance(checkpoint_config, dict) and all(
        config.get(key) == value for key, value in checkpoint_config.items()
    )
    if not compatible:
        raise SystemExit(
            "Existing extraction checkpoint has a different configuration. "
            "Run once with --clean-existing-fsl105 to start a fresh extraction."
        )
    completed = payload.get("completed", {})
    return completed if isinstance(completed, dict) else {}


def checkpoint_row_is_valid(row: dict[str, Any], output_root: Path) -> bool:
    output_files = row.get("output_files")
    if not isinstance(output_files, list):
        return False
    for relative in output_files:
        npy_path = output_root / Path(str(relative))
        meta_path = npy_path.with_suffix(".meta.json")
        if not npy_path.is_file() or not meta_path.is_file():
            return False
        try:
            array = np.load(npy_path, mmap_mode="r", allow_pickle=False)
            if array.shape != (FSL_SEQUENCE_LENGTH, FSL_FEATURE_SIZE):
                return False
            meta = json.loads(meta_path.read_text(encoding="utf-8"))
        except (OSError, ValueError, json.JSONDecodeError):
            return False
        if not fsl_owned(meta) or meta.get("sequence_length_at_export") != FSL_SEQUENCE_LENGTH:
            return False
    return True


def initialize_worker() -> None:
    global _CV2, _HOLISTIC
    os.environ.setdefault("TF_CPP_MIN_LOG_LEVEL", "2")
    import cv2
    import mediapipe as mp

    cv2.setNumThreads(1)
    _CV2 = cv2
    _HOLISTIC = mp.solutions.holistic.Holistic(
        static_image_mode=False,
        model_complexity=MODEL_COMPLEXITY,
        enable_segmentation=False,
        refine_face_landmarks=False,
        min_detection_confidence=MIN_DETECTION_CONFIDENCE,
        min_tracking_confidence=MIN_TRACKING_CONFIDENCE,
    )


def build_frame_features(results: Any) -> tuple[np.ndarray, bool, bool]:
    """Canonical right-hand OneHand162 wrapper retained for worker callers."""
    return extract_frame_features(results, selected_hand="right")


def remove_canonical_video_outputs(label_dir: Path, video_stem: str) -> None:
    for npy_path in label_dir.glob(f"fsl105_{video_stem}_w*.npy"):
        meta_path = npy_path.with_suffix(".meta.json")
        if explicitly_non_fsl_owned(meta_path):
            raise RuntimeError(f"Refusing to overwrite non-FSL recording: {npy_path}")
        npy_path.unlink(missing_ok=True)
        meta_path.unlink(missing_ok=True)


def process_video(task: dict[str, str], output_root_text: str) -> dict[str, Any]:
    output_root = Path(output_root_text)
    label = task["label"]
    video_path = Path(task["video_path"])
    video_stem = task["video_stem"]
    label_dir = output_root / label
    label_dir.mkdir(parents=True, exist_ok=True)
    remove_canonical_video_outputs(label_dir, video_stem)

    if hasattr(_HOLISTIC, "reset"):
        _HOLISTIC.reset()
    capture = _CV2.VideoCapture(str(video_path))
    if not capture.isOpened():
        raise RuntimeError(f"OpenCV could not open {video_path}")

    vectors: list[np.ndarray] = []
    hand_presence: list[bool] = []
    pose_presence: list[bool] = []
    try:
        while True:
            ok, frame = capture.read()
            if not ok:
                break
            rgb = _CV2.cvtColor(frame, _CV2.COLOR_BGR2RGB)
            results = _HOLISTIC.process(rgb)
            vector, hand_present, pose_present = build_frame_features(results)
            vectors.append(vector)
            hand_presence.append(hand_present)
            pose_presence.append(pose_present)
    finally:
        capture.release()

    if not vectors:
        raise RuntimeError(f"No decoded frames: {video_path}")

    output_files: list[str] = []
    skipped_low_hand = 0
    candidate_windows = 0
    for window_idx, start in enumerate(
        range(0, max(0, len(vectors) - FSL_SEQUENCE_LENGTH + 1), WINDOW_STRIDE)
    ):
        candidate_windows += 1
        end = start + FSL_SEQUENCE_LENGTH
        right_hand_frames = sum(hand_presence[start:end])
        if right_hand_frames < MIN_RIGHT_HAND_FRAMES:
            skipped_low_hand += 1
            continue

        sequence = np.asarray(vectors[start:end], dtype=np.float32)
        if sequence.shape != (FSL_SEQUENCE_LENGTH, FSL_FEATURE_SIZE):
            raise RuntimeError(f"Unexpected sequence shape {sequence.shape} for {video_path}")
        filename = f"fsl105_{video_stem}_w{window_idx}.npy"
        npy_path = label_dir / filename
        meta_path = npy_path.with_suffix(".meta.json")
        meta = {
            "label": label,
            "source": "fsl105",
            "signer_id": f"fsl105_{video_stem}",
            "device_model": "FSL105_VIDEO",
            "sequence_length_at_export": FSL_SEQUENCE_LENGTH,
            "feature_version": FEATURE_VERSION,
            "feature_profile": "onehand162",
            "feature_layout": FEATURE_LAYOUT,
            "normalization": NORMALIZATION_POLICY,
            "dtype": "float32",
            "selected_hand": "right",
            "handedness_policy": "fixed_anatomical_right_holistic_slot",
            "mirrored_input": False,
            "fsl_mode": True,
            "window_start_frame": start,
            "hand_presence_ratio": right_hand_frames / FSL_SEQUENCE_LENGTH,
            "pose_presence_ratio": sum(pose_presence[start:end]) / FSL_SEQUENCE_LENGTH,
            "source_video": task["relative_video"],
        }
        save_npy_atomic(npy_path, sequence)
        write_json_atomic(meta_path, meta)
        output_files.append(str(npy_path.relative_to(output_root)))

    return {
        "key": task["key"],
        "label": label,
        "relative_video": task["relative_video"],
        "decoded_frames": len(vectors),
        "pose_present_frames": sum(pose_presence),
        "right_hand_present_frames": sum(hand_presence),
        "candidate_windows": candidate_windows,
        "skipped_low_hand_windows": skipped_low_hand,
        "saved_windows": len(output_files),
        "output_files": output_files,
    }


def summarize_outputs(
    labels: list[str],
    tasks: list[dict[str, str]],
    completed: dict[str, dict[str, Any]],
    config: dict[str, Any],
    cleanup: dict[str, int],
    output_root: Path,
) -> dict[str, Any]:
    per_label: dict[str, dict[str, Any]] = {}
    shape_mismatches: list[str] = []
    metadata_errors: list[str] = []
    expected_output_files = {
        str(Path(relative))
        for row in completed.values()
        for relative in row.get("output_files", [])
    }
    actual_output_files = {
        str(path.relative_to(output_root))
        for path in output_root.rglob("fsl105_*_w*.npy")
        if path.is_file()
    }
    missing_output_files = sorted(expected_output_files - actual_output_files)
    unexpected_output_files = sorted(actual_output_files - expected_output_files)
    orphan_metadata = sorted(
        str(path.relative_to(output_root))
        for path in output_root.rglob("fsl105_*_w*.meta.json")
        if path.is_file()
        and not path.with_name(path.name.removesuffix(".meta.json") + ".npy").is_file()
    )

    for label in labels:
        videos = [row for row in completed.values() if row.get("label") == label]
        sequence_count = 0
        label_dir = output_root / label
        if label_dir.is_dir():
            for npy_path in sorted(label_dir.glob("fsl105_*_w*.npy")):
                meta_path = npy_path.with_suffix(".meta.json")
                try:
                    array = np.load(npy_path, mmap_mode="r", allow_pickle=False)
                    if array.shape != (FSL_SEQUENCE_LENGTH, FSL_FEATURE_SIZE):
                        shape_mismatches.append(str(npy_path))
                        continue
                    meta = json.loads(meta_path.read_text(encoding="utf-8"))
                except (OSError, ValueError, json.JSONDecodeError):
                    metadata_errors.append(str(npy_path))
                    continue
                if (
                    meta.get("label") != label
                    or meta.get("source") != "fsl105"
                    or meta.get("device_model") != "FSL105_VIDEO"
                    or meta.get("sequence_length_at_export") != FSL_SEQUENCE_LENGTH
                    or meta.get("fsl_mode") is not True
                ):
                    metadata_errors.append(str(npy_path))
                    continue
                sequence_count += 1
        per_label[label] = {
            "train_videos": len(videos),
            "sequence_count": sequence_count,
            "needs_supplemental": sequence_count < 80,
            "candidate_windows": sum(int(row.get("candidate_windows", 0)) for row in videos),
            "skipped_low_hand_windows": sum(
                int(row.get("skipped_low_hand_windows", 0)) for row in videos
            ),
        }

    summary = {
        "source": "fsl105",
        "split": "train.csv_only",
        "confirmed_label_count": len(labels),
        "confirmed_labels": labels,
        "train_video_count": len(tasks),
        "completed_video_count": len(completed),
        "sequence_length": FSL_SEQUENCE_LENGTH,
        "feature_size": FSL_FEATURE_SIZE,
        "expected_shape": [FSL_SEQUENCE_LENGTH, FSL_FEATURE_SIZE],
        "stride": WINDOW_STRIDE,
        "minimum_right_hand_frames": MIN_RIGHT_HAND_FRAMES,
        "cleanup": cleanup,
        "total_sequences": sum(item["sequence_count"] for item in per_label.values()),
        "shape_mismatches": shape_mismatches,
        "metadata_errors": metadata_errors,
        "output_integrity": {
            "expected_files": len(expected_output_files),
            "actual_files": len(actual_output_files),
            "missing_output_files": missing_output_files,
            "unexpected_output_files": unexpected_output_files,
            "orphan_metadata": orphan_metadata,
        },
        "per_label": per_label,
        "config": config,
    }
    return summary


def print_summary(summary: dict[str, Any]) -> None:
    print("FSL-105 confirmed one-handed extraction complete")
    print(f"Train videos: {summary['completed_video_count']}/{summary['train_video_count']}")
    print(f"Total sequences: {summary['total_sequences']}")
    print("")
    print(f"{'LABEL':<24} {'VIDEOS':>6} {'SEQUENCES':>10} {'STATUS':>20}")
    for label in summary["confirmed_labels"]:
        row = summary["per_label"][label]
        status = "NEEDS_SUPPLEMENTAL" if row["needs_supplemental"] else "OK"
        print(f"{label:<24} {row['train_videos']:>6} {row['sequence_count']:>10} {status:>20}")
    print(f"Shape mismatches: {len(summary['shape_mismatches'])}")
    print(f"Metadata errors: {len(summary['metadata_errors'])}")
    print(
        "Output integrity: "
        f"expected={summary['output_integrity']['expected_files']} "
        f"actual={summary['output_integrity']['actual_files']} "
        f"missing={len(summary['output_integrity']['missing_output_files'])} "
        f"unexpected={len(summary['output_integrity']['unexpected_output_files'])} "
        f"orphan_meta={len(summary['output_integrity']['orphan_metadata'])}"
    )
    print(f"Summary: {SUMMARY_PATH}")


def run(args: argparse.Namespace) -> int:
    if FSL_SEQUENCE_LENGTH != 20 or FSL_FEATURE_SIZE != 162:
        raise SystemExit(
            f"Runtime contract mismatch: expected (20, 162), got "
            f"({FSL_SEQUENCE_LENGTH}, {FSL_FEATURE_SIZE})"
        )
    output_root = args.output.resolve()
    assert_safe_output_root(output_root)
    labels = load_confirmed_labels()
    tasks = read_train_tasks(labels)
    config = build_config(labels, output_root)
    output_root.mkdir(parents=True, exist_ok=True)

    cleanup = {"removed_npy": 0, "removed_meta": 0, "malformed_meta": 0}
    if args.clean_existing_fsl105:
        cleanup = clean_existing_fsl105(output_root)
        CHECKPOINT_PATH.unlink(missing_ok=True)
        SUMMARY_PATH.unlink(missing_ok=True)
        completed: dict[str, dict[str, Any]] = {}
    else:
        completed = load_checkpoint(config)

    task_by_key = {task["key"]: task for task in tasks}
    valid_completed: dict[str, dict[str, Any]] = {}
    for key, row in completed.items():
        if key in task_by_key and checkpoint_row_is_valid(row, output_root):
            valid_completed[key] = row
    completed = valid_completed
    pending = [task for task in tasks if task["key"] not in completed]

    write_json_atomic(
        CHECKPOINT_PATH,
        {"config": config, "completed": completed, "errors": []},
    )
    print("FSL-105 train-only extraction", flush=True)
    print(f"Confirmed labels: {len(labels)}", flush=True)
    print(f"Train videos selected: {len(tasks)}", flush=True)
    print(f"Resume hits: {len(completed)}", flush=True)
    print(f"Videos requiring MediaPipe: {len(pending)}", flush=True)
    print(f"Cleanup: {cleanup}", flush=True)

    errors: list[dict[str, str]] = []
    if pending:
        with ProcessPoolExecutor(
            max_workers=args.workers,
            initializer=initialize_worker,
        ) as executor:
            futures = {
                executor.submit(process_video, task, str(output_root)): task
                for task in pending
            }
            processed = 0
            for future in as_completed(futures):
                task = futures[future]
                try:
                    row = future.result()
                    completed[task["key"]] = row
                except Exception as exc:
                    errors.append(
                        {
                            "key": task["key"],
                            "video": task["video_path"],
                            "error": f"{type(exc).__name__}: {exc}",
                        }
                    )
                processed += 1
                if processed % 5 == 0 or processed == len(pending):
                    write_json_atomic(
                        CHECKPOINT_PATH,
                        {"config": config, "completed": completed, "errors": errors},
                    )
                    print(
                        f"Extraction progress: {processed}/{len(pending)} "
                        f"completed_total={len(completed)}/{len(tasks)} errors={len(errors)}",
                        flush=True,
                    )

    write_json_atomic(
        CHECKPOINT_PATH,
        {"config": config, "completed": completed, "errors": errors},
    )
    if errors:
        print(json.dumps(errors, indent=2, ensure_ascii=False))
        raise SystemExit(f"ERROR: {len(errors)} train videos failed extraction")
    if len(completed) != len(tasks):
        raise SystemExit(
            f"ERROR: extraction incomplete: {len(completed)}/{len(tasks)} train videos"
        )

    summary = summarize_outputs(labels, tasks, completed, config, cleanup, output_root)
    write_json_atomic(SUMMARY_PATH, summary)
    print_summary(summary)
    integrity = summary["output_integrity"]
    if (
        summary["shape_mismatches"]
        or summary["metadata_errors"]
        or integrity["missing_output_files"]
        or integrity["unexpected_output_files"]
        or integrity["orphan_metadata"]
    ):
        return 2
    return 0


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, default=OUTPUT_ROOT)
    parser.add_argument("--workers", type=int, default=min(4, os.cpu_count() or 1))
    parser.add_argument(
        "--clean-existing-fsl105",
        action="store_true",
        help="Remove only existing FSL105-owned artifacts and reset the extraction checkpoint.",
    )
    args = parser.parse_args()
    if args.workers < 1:
        parser.error("--workers must be at least 1")
    return args


def main() -> int:
    multiprocessing.freeze_support()
    return run(parse_args())


if __name__ == "__main__":
    raise SystemExit(main())
