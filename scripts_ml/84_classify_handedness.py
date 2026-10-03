"""Classify all FSL-105 labels for the right-hand 162-feature pipeline.

The classifier samples at most five training videos per label and counts
MediaPipe Holistic hand detections over every decoded frame.  Test-split
videos are deliberately excluded.
"""

from __future__ import annotations

import argparse
import csv
import json
import multiprocessing
import os
import random
import re
from collections import defaultdict
from concurrent.futures import ProcessPoolExecutor, as_completed
from pathlib import Path
from typing import Any


ROOT = Path(__file__).resolve().parents[1]
RAW_ROOT = ROOT / "external_datasets" / "fsl105_raw"
LABELS_CSV = RAW_ROOT / "labels.csv"
TRAIN_CSV = RAW_ROOT / "train.csv"
CLIPS_ROOT = RAW_ROOT / "clips"
REPORT_DIR = ROOT / "reports" / "fsl"
REPORT_PATH = REPORT_DIR / "handedness_report.json"
CONFIRMED_PATH = REPORT_DIR / "confirmed_onehanded_labels.json"
CACHE_PATH = REPORT_DIR / "handedness_frame_counts_cache.json"
LEGACY_STATS_PATH = ROOT / "reports" / "fsl105_handedness_by_video.csv"

SAMPLE_VIDEOS_PER_LABEL = 5
RIGHT_HAND_THRESHOLD = 0.70
BOTH_HANDS_THRESHOLD = 0.40
RANDOM_SEED = 20260716
VIDEO_EXTENSIONS = {".mp4", ".mov", ".avi", ".mkv", ".webm", ".m4v"}

_HOLISTIC = None
_CV2 = None


def normalize_label(value: Any) -> str:
    return re.sub(r"\s+", " ", str(value).strip().upper())


def normalize_video_key(value: Any) -> str:
    text = str(value).strip().replace("\\", "/")
    text = re.sub(r"/+", "/", text)
    match = re.search(r"(?:^|/)clips/(\d+/[^/]+)$", text, flags=re.IGNORECASE)
    if match:
        return f"clips/{match.group(1)}".lower()
    return text.lstrip("./").lower()


def read_csv(path: Path) -> list[dict[str, str]]:
    with path.open("r", encoding="utf-8-sig", newline="") as handle:
        return list(csv.DictReader(handle))


def write_json(path: Path, payload: Any) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_suffix(path.suffix + ".tmp")
    temporary.write_text(
        json.dumps(payload, indent=2, ensure_ascii=False) + "\n",
        encoding="utf-8",
    )
    temporary.replace(path)


def load_labels() -> tuple[list[str], dict[str, str]]:
    rows = read_csv(LABELS_CSV)
    labels: list[str] = []
    label_by_id: dict[str, str] = {}
    for row in rows:
        label = normalize_label(row.get("label", ""))
        label_id = str(row.get("id", "")).strip()
        if not label or not label_id:
            raise SystemExit(f"ERROR: invalid labels.csv row: {row}")
        labels.append(label)
        label_by_id[label_id] = label
    if len(labels) != 105 or len(set(labels)) != 105:
        raise SystemExit(
            f"ERROR: expected 105 unique labels in {LABELS_CSV}, found "
            f"{len(labels)} rows and {len(set(labels))} unique labels"
        )
    return labels, label_by_id


def resolve_train_video(relative_path: str) -> Path:
    normalized = relative_path.strip().replace("\\", "/")
    candidates = [
        CLIPS_ROOT / Path(normalized),
        RAW_ROOT / Path(normalized),
    ]
    for candidate in candidates:
        if candidate.is_file():
            return candidate
    raise FileNotFoundError(
        f"train video not found for {relative_path!r}; tried: "
        + ", ".join(str(path) for path in candidates)
    )


def sample_train_videos(
    labels: list[str], label_by_id: dict[str, str], sample_count: int, seed: int
) -> dict[str, list[tuple[str, Path]]]:
    grouped: dict[str, list[tuple[str, Path]]] = defaultdict(list)
    for row in read_csv(TRAIN_CSV):
        label_id = str(row.get("id_label", "")).strip()
        csv_label = normalize_label(row.get("label", ""))
        label = csv_label or label_by_id.get(label_id, "")
        if label_id in label_by_id and label != label_by_id[label_id]:
            raise SystemExit(
                f"ERROR: train.csv label mismatch for id {label_id}: "
                f"{label!r} != {label_by_id[label_id]!r}"
            )
        relative_path = str(row.get("vid_path", "")).strip()
        if not label or not relative_path:
            raise SystemExit(f"ERROR: invalid train.csv row: {row}")
        video_path = resolve_train_video(relative_path)
        if video_path.suffix.lower() not in VIDEO_EXTENSIONS:
            continue
        grouped[label].append((normalize_video_key(relative_path), video_path))

    selected: dict[str, list[tuple[str, Path]]] = {}
    for label_index, label in enumerate(labels):
        candidates = sorted(grouped.get(label, []), key=lambda item: item[0])
        if not candidates:
            raise SystemExit(f"ERROR: no train videos found for label={label}")
        count = min(sample_count, len(candidates))
        rng = random.Random(seed + label_index)
        chosen = rng.sample(candidates, count) if len(candidates) > count else candidates
        selected[label] = sorted(chosen, key=lambda item: item[0])
    return selected


def cache_config(args: argparse.Namespace) -> dict[str, Any]:
    return {
        "model_complexity": args.model_complexity,
        "min_detection_confidence": args.min_detection_confidence,
        "min_tracking_confidence": args.min_tracking_confidence,
    }


def load_resume_cache(args: argparse.Namespace) -> dict[str, dict[str, int]]:
    if not CACHE_PATH.is_file():
        return {}
    try:
        payload = json.loads(CACHE_PATH.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError):
        return {}
    if payload.get("config") != cache_config(args):
        print("Ignoring handedness cache because its MediaPipe configuration differs.")
        return {}
    videos = payload.get("videos", {})
    return {
        normalize_video_key(key): value
        for key, value in videos.items()
        if isinstance(value, dict)
    }


def load_legacy_stats(args: argparse.Namespace) -> dict[str, dict[str, int]]:
    if not args.reuse_legacy_stats or not LEGACY_STATS_PATH.is_file():
        return {}
    if cache_config(args) != {
        "model_complexity": 1,
        "min_detection_confidence": 0.5,
        "min_tracking_confidence": 0.5,
    }:
        print("Legacy stats require the verified MediaPipe configuration; ignoring them.")
        return {}

    output: dict[str, dict[str, int]] = {}
    for row in read_csv(LEGACY_STATS_PATH):
        try:
            total_frames = int(row["total_frames"])
            right_frames = int(row["right_present_frames"])
            both_frames = int(row["both_present_frames"])
            left_frames = int(row["left_present_frames"])
        except (KeyError, TypeError, ValueError):
            continue
        key = normalize_video_key(row.get("video_path", ""))
        if not key or total_frames <= 0:
            continue
        output[key] = {
            "total_frames": total_frames,
            "right_detected_frames": right_frames,
            "left_only_frames": max(0, left_frames - both_frames),
            "both_frames": both_frames,
        }
    return output


def initialize_worker(
    model_complexity: int,
    min_detection_confidence: float,
    min_tracking_confidence: float,
) -> None:
    global _HOLISTIC, _CV2
    os.environ.setdefault("TF_CPP_MIN_LOG_LEVEL", "2")
    import cv2
    import mediapipe as mp

    _CV2 = cv2
    _HOLISTIC = mp.solutions.holistic.Holistic(
        static_image_mode=False,
        model_complexity=model_complexity,
        enable_segmentation=False,
        refine_face_landmarks=False,
        min_detection_confidence=min_detection_confidence,
        min_tracking_confidence=min_tracking_confidence,
    )


def process_video(task: tuple[str, str]) -> tuple[str, dict[str, int]]:
    key, path_text = task
    capture = _CV2.VideoCapture(path_text)
    if not capture.isOpened():
        raise RuntimeError(f"OpenCV could not open {path_text}")

    total_frames = 0
    right_detected_frames = 0
    left_only_frames = 0
    both_frames = 0
    try:
        while True:
            ok, frame = capture.read()
            if not ok:
                break
            total_frames += 1
            rgb = _CV2.cvtColor(frame, _CV2.COLOR_BGR2RGB)
            results = _HOLISTIC.process(rgb)
            right_detected = results.right_hand_landmarks is not None
            left_detected = results.left_hand_landmarks is not None
            right_detected_frames += int(right_detected)
            left_only_frames += int(left_detected and not right_detected)
            both_frames += int(left_detected and right_detected)
    finally:
        capture.release()

    if total_frames == 0:
        raise RuntimeError(f"No frames decoded from {path_text}")
    return key, {
        "total_frames": total_frames,
        "right_detected_frames": right_detected_frames,
        "left_only_frames": left_only_frames,
        "both_frames": both_frames,
    }


def run(args: argparse.Namespace) -> int:
    labels, label_by_id = load_labels()
    selected = sample_train_videos(labels, label_by_id, args.samples_per_label, args.seed)
    selected_by_key = {
        key: (label, path)
        for label, videos in selected.items()
        for key, path in videos
    }

    counts = load_resume_cache(args)
    legacy_counts = load_legacy_stats(args)
    reused_legacy = 0
    for key in selected_by_key:
        if key not in counts and key in legacy_counts:
            counts[key] = legacy_counts[key]
            reused_legacy += 1

    pending = [
        (key, str(path))
        for key, (_label, path) in selected_by_key.items()
        if key not in counts
    ]
    print("FSL-105 handedness classification")
    print(f"Labels loaded: {len(labels)}")
    print(f"Train videos selected: {len(selected_by_key)} (up to {args.samples_per_label}/label)")
    print(f"Resume-cache hits: {len(selected_by_key) - len(pending) - reused_legacy}")
    print(f"Verified legacy MediaPipe stats reused: {reused_legacy}")
    print(f"Videos requiring MediaPipe: {len(pending)}")

    if pending:
        with ProcessPoolExecutor(
            max_workers=args.workers,
            initializer=initialize_worker,
            initargs=(
                args.model_complexity,
                args.min_detection_confidence,
                args.min_tracking_confidence,
            ),
        ) as executor:
            futures = {executor.submit(process_video, task): task for task in pending}
            completed = 0
            for future in as_completed(futures):
                key, result = future.result()
                counts[key] = result
                completed += 1
                if completed % 10 == 0 or completed == len(pending):
                    write_json(
                        CACHE_PATH,
                        {"config": cache_config(args), "videos": counts},
                    )
                    print(
                        f"MediaPipe progress: {completed}/{len(pending)}; "
                        f"last={key}",
                        flush=True,
                    )

    missing = sorted(set(selected_by_key) - set(counts))
    if missing:
        raise SystemExit(f"ERROR: missing handedness counts for {len(missing)} videos: {missing}")
    write_json(CACHE_PATH, {"config": cache_config(args), "videos": counts})

    one_handed: list[str] = []
    two_handed: list[str] = []
    ambiguous: list[str] = []
    for label in labels:
        video_counts = [counts[key] for key, _path in selected[label]]
        total_frames = sum(item["total_frames"] for item in video_counts)
        right_frames = sum(item["right_detected_frames"] for item in video_counts)
        left_only_frames = sum(item["left_only_frames"] for item in video_counts)
        both_frames = sum(item["both_frames"] for item in video_counts)
        # right_detected_frames includes both-hand frames, while left_only_frames
        # is exclusive. Their sum is therefore the number of frames where at
        # least one hand was detected, with no double counting.
        hand_active_frames = right_frames + left_only_frames
        right_ratio = right_frames / hand_active_frames if hand_active_frames else 0.0
        both_ratio = both_frames / hand_active_frames if hand_active_frames else 0.0

        # BOTH takes precedence because right-hand detection includes BOTH frames.
        if hand_active_frames == 0:
            classification = "AMBIGUOUS"
            ambiguous.append(label)
        elif both_ratio > BOTH_HANDS_THRESHOLD:
            classification = "TWO_HANDED"
            two_handed.append(label)
        elif right_ratio >= RIGHT_HAND_THRESHOLD:
            classification = "ONE_HANDED"
            one_handed.append(label)
        else:
            classification = "AMBIGUOUS"
            ambiguous.append(label)
        print(
            f"{label}: videos={len(video_counts)} decoded_frames={total_frames} "
            f"hand_active_frames={hand_active_frames} "
            f"right={right_frames} ({right_ratio:.3f}) "
            f"left_only={left_only_frames} both={both_frames} ({both_ratio:.3f}) "
            f"=> {classification}"
        )

    report = {
        "one_handed": sorted(one_handed),
        "two_handed": sorted(two_handed),
        "ambiguous": sorted(ambiguous),
        "total_classified": len(labels),
    }
    classified_sets = [set(report[name]) for name in ("one_handed", "two_handed", "ambiguous")]
    if any(classified_sets[i] & classified_sets[j] for i in range(3) for j in range(i + 1, 3)):
        raise AssertionError("Handedness classifications are not disjoint")
    if set().union(*classified_sets) != set(labels):
        raise AssertionError("Handedness classifications do not cover all 105 labels")
    write_json(REPORT_PATH, report)
    write_json(CONFIRMED_PATH, report["one_handed"])

    print("\nONE_HANDED " + json.dumps(report["one_handed"], ensure_ascii=False))
    print("TWO_HANDED " + json.dumps(report["two_handed"], ensure_ascii=False))
    print("AMBIGUOUS " + json.dumps(report["ambiguous"], ensure_ascii=False))
    print(
        f"Counts: one_handed={len(one_handed)} two_handed={len(two_handed)} "
        f"ambiguous={len(ambiguous)} total={len(labels)}"
    )
    print(f"Saved: {REPORT_PATH}")
    print(f"Saved: {CONFIRMED_PATH}")

    if len(one_handed) < 10:
        print(
            f"STOP: only {len(one_handed)} one-handed signs were confirmed; "
            "at least 10 are required."
        )
        return 2
    return 0


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--samples-per-label", type=int, default=SAMPLE_VIDEOS_PER_LABEL)
    parser.add_argument("--seed", type=int, default=RANDOM_SEED)
    parser.add_argument("--workers", type=int, default=min(4, os.cpu_count() or 1))
    parser.add_argument("--model-complexity", type=int, choices=(0, 1, 2), default=1)
    parser.add_argument("--min-detection-confidence", type=float, default=0.5)
    parser.add_argument("--min-tracking-confidence", type=float, default=0.5)
    parser.add_argument(
        "--reuse-legacy-stats",
        action="store_true",
        help=(
            "Reuse verified per-video MediaPipe counts from the existing legacy CSV. "
            "The CSV labels are ignored; only path-keyed frame counts are reused."
        ),
    )
    args = parser.parse_args()
    if args.samples_per_label < 1 or args.samples_per_label > SAMPLE_VIDEOS_PER_LABEL:
        parser.error(f"--samples-per-label must be between 1 and {SAMPLE_VIDEOS_PER_LABEL}")
    if args.workers < 1:
        parser.error("--workers must be at least 1")
    return args


def main() -> int:
    multiprocessing.freeze_support()
    return run(parse_args())


if __name__ == "__main__":
    raise SystemExit(main())
