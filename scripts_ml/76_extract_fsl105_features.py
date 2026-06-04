"""Extract 20x162 FSL windows from FSL-105 videos for future retraining."""

from __future__ import annotations

import argparse
import json
import re
from collections import defaultdict
from datetime import datetime
from pathlib import Path

import numpy as np

from fsl_config import FSL_EXPECTED_SHAPE, FSL_FEATURE_SIZE, FSL_LABELS, FSL_SEQUENCE_LENGTH


ROOT = Path(__file__).resolve().parents[1]
OUTPUT_ROOT = ROOT / "external_datasets" / "fsl_features"
REPORTS_DIR = ROOT / "reports" / "fsl"
VIDEO_EXTENSIONS = {".mp4", ".mov", ".avi", ".mkv", ".webm", ".m4v"}
POSE_LANDMARK_COUNT = 33
HAND_LANDMARK_COUNT = 21
POSE_SIZE = 99
HAND_SIZE = 63
WINDOW_STRIDE = 5
MIN_HAND_PRESENCE_RATIO = 0.65
MIN_WRIST_MCP_SCALE = 0.001
Z_DAMPING = 0.3
cv2 = None
mp = None


def require_video_dependencies() -> None:
    global cv2, mp
    if cv2 is not None and mp is not None:
        return
    try:
        import cv2 as cv2_module
        import mediapipe as mp_module
    except ModuleNotFoundError as exc:
        raise SystemExit(
            "Missing video extraction dependency. Run with the VoxGest environment "
            "that has opencv-python and mediapipe installed, for example: "
            "voxgest_env\\Scripts\\python.exe scripts_ml\\76_extract_fsl105_features.py --fsl105_dir <path>"
        ) from exc
    cv2 = cv2_module
    mp = mp_module


def safe_name(value: object) -> str:
    text = re.sub(r"[^A-Za-z0-9_-]+", "_", str(value).strip())
    return text.strip("_") or "sample"


def clean_label(value: object) -> str:
    return safe_name(value).upper()


def unique_path(path: Path) -> Path:
    if not path.exists():
        return path
    for index in range(2, 100_000):
        candidate = path.with_name(f"{path.stem}_{index}{path.suffix}")
        if not candidate.exists():
            return candidate
    raise RuntimeError(f"Could not create unique path for {path}")


def iter_videos(root: Path):
    for path in sorted(root.rglob("*")):
        if path.is_file() and path.suffix.lower() in VIDEO_EXTENSIONS:
            yield path


def infer_label(video_path: Path, root: Path) -> str | None:
    try:
        parts = video_path.relative_to(root).parts
    except ValueError:
        parts = video_path.parts
    for part in reversed(parts[:-1]):
        label = clean_label(part)
        if label in FSL_LABELS:
            return label
    stem_tokens = re.split(r"[^A-Za-z0-9]+", video_path.stem)
    for token in stem_tokens:
        label = clean_label(token)
        if label in FSL_LABELS:
            return label
    return None


def landmarks_to_array(landmarks, count: int) -> np.ndarray | None:
    if landmarks is None or len(landmarks.landmark) != count:
        return None
    return np.asarray([[lm.x, lm.y, lm.z] for lm in landmarks.landmark], dtype=np.float32)


def build_frame_features(results) -> tuple[np.ndarray | None, bool]:
    pose = landmarks_to_array(results.pose_landmarks, POSE_LANDMARK_COUNT)
    if pose is None:
        return None, False

    nose = pose[0].copy()
    pose_values = pose - nose[np.newaxis, :]

    right_hand = landmarks_to_array(results.right_hand_landmarks, HAND_LANDMARK_COUNT)
    hand_present = right_hand is not None
    if right_hand is None:
        hand_values = np.zeros((HAND_LANDMARK_COUNT, 3), dtype=np.float32)
    else:
        hand_values = right_hand - nose[np.newaxis, :]
        scale = float(np.linalg.norm(hand_values[0] - hand_values[9]))
        if scale > MIN_WRIST_MCP_SCALE:
            hand_values = hand_values / scale

    output = np.concatenate([pose_values.reshape(-1), hand_values.reshape(-1)]).astype(np.float32)
    output[0:3] = 0.0
    output[2::3] *= Z_DAMPING
    return output, hand_present


def read_video_features(video_path: Path, holistic) -> tuple[list[tuple[int, np.ndarray, bool]], int]:
    cap = cv2.VideoCapture(str(video_path))
    frames: list[tuple[int, np.ndarray, bool]] = []
    processed = 0
    frame_index = 0
    while True:
        ok, frame = cap.read()
        if not ok:
            break
        processed += 1
        try:
            rgb = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
            results = holistic.process(rgb)
            vector, hand_present = build_frame_features(results)
            if vector is not None and vector.shape == (FSL_FEATURE_SIZE,):
                frames.append((frame_index, vector, hand_present))
        except Exception:
            pass
        frame_index += 1
    cap.release()
    return frames, processed


def save_window(label: str, video_path: Path, window: list[tuple[int, np.ndarray, bool]], output_root: Path) -> Path:
    label_dir = output_root / label
    label_dir.mkdir(parents=True, exist_ok=True)
    start_frame = window[0][0]
    stem = safe_name(f"{label}_{video_path.stem}_{start_frame:06d}")
    npy_path = unique_path(label_dir / f"{stem}.npy")
    meta_path = npy_path.with_suffix(".meta.json")
    seq = np.asarray([item[1] for item in window], dtype=np.float32)
    np.save(npy_path, seq, allow_pickle=False)
    meta = {
        "source_video": str(video_path),
        "label": label,
        "window_start_frame": int(start_frame),
        "device_model": "FSL105_VIDEO",
        "signer_id": f"fsl105_{safe_name(video_path.stem)}",
        "sequence_length_at_export": FSL_SEQUENCE_LENGTH,
        "fsl_mode": True,
        "feature_shape": list(seq.shape),
        "hand_presence_ratio": float(sum(1 for _, _, present in window if present) / len(window)),
        "extracted_at": datetime.now().isoformat(timespec="seconds"),
    }
    meta_path.write_text(json.dumps(meta, indent=2), encoding="utf-8")
    return npy_path


def extract_video(label: str, video_path: Path, holistic, output_root: Path) -> dict:
    frames, processed = read_video_features(video_path, holistic)
    saved = 0
    skipped_low_hand = 0
    hand_present_frames = sum(1 for _, _, present in frames if present)
    no_hand_majority = bool(frames and hand_present_frames <= (len(frames) / 2.0))

    for start in range(0, max(0, len(frames) - FSL_SEQUENCE_LENGTH + 1), WINDOW_STRIDE):
        window = frames[start : start + FSL_SEQUENCE_LENGTH]
        if len(window) != FSL_SEQUENCE_LENGTH:
            continue
        hand_presence_ratio = sum(1 for _, _, present in window if present) / FSL_SEQUENCE_LENGTH
        if hand_presence_ratio < MIN_HAND_PRESENCE_RATIO:
            skipped_low_hand += 1
            continue
        seq = np.asarray([item[1] for item in window], dtype=np.float32)
        if seq.shape != FSL_EXPECTED_SHAPE:
            continue
        save_window(label, video_path, window, output_root)
        saved += 1

    return {
        "label": label,
        "video": str(video_path),
        "processed_frames": processed,
        "feature_frames": len(frames),
        "hand_present_frames": hand_present_frames,
        "saved_windows": saved,
        "skipped_low_hand_windows": skipped_low_hand,
        "no_hand_majority": no_hand_majority,
    }


def extract_dataset(fsl105_dir: Path, output_root: Path) -> tuple[dict, Path]:
    require_video_dependencies()
    output_root.mkdir(parents=True, exist_ok=True)
    REPORTS_DIR.mkdir(parents=True, exist_ok=True)

    videos = []
    skipped_unknown_label = []
    for video_path in iter_videos(fsl105_dir):
        label = infer_label(video_path, fsl105_dir)
        if label is None:
            skipped_unknown_label.append(str(video_path))
            continue
        videos.append((label, video_path))

    per_label = defaultdict(lambda: {"videos": 0, "windows": 0, "low_hand_windows": 0})
    no_hand_majority = []
    rows = []

    with mp.solutions.holistic.Holistic(
        static_image_mode=False,
        model_complexity=1,
        min_detection_confidence=0.5,
        min_tracking_confidence=0.5,
    ) as holistic:
        for label, video_path in videos:
            row = extract_video(label, video_path, holistic, output_root)
            rows.append(row)
            per_label[label]["videos"] += 1
            per_label[label]["windows"] += int(row["saved_windows"])
            per_label[label]["low_hand_windows"] += int(row["skipped_low_hand_windows"])
            if row["no_hand_majority"]:
                no_hand_majority.append(row)

    report = {
        "fsl105_dir": str(fsl105_dir),
        "output_root": str(output_root),
        "expected_shape": list(FSL_EXPECTED_SHAPE),
        "window": FSL_SEQUENCE_LENGTH,
        "stride": WINDOW_STRIDE,
        "videos_seen": len(videos),
        "skipped_unknown_label": skipped_unknown_label,
        "per_label": {label: dict(per_label[label]) for label in FSL_LABELS},
        "no_hand_majority_videos": no_hand_majority,
        "videos": rows,
    }
    report_path = REPORTS_DIR / "fsl105_extraction_report.json"
    report_path.write_text(json.dumps(report, indent=2), encoding="utf-8")
    return report, report_path


def print_summary(report: dict, report_path: Path) -> None:
    print("FSL-105 extraction complete")
    print(f"Input : {report['fsl105_dir']}")
    print(f"Output: {report['output_root']}")
    print("")
    print(f"{'LABEL':<12} {'VIDEOS':>6} {'WINDOWS':>8} {'LOW_HAND':>9}")
    for label in FSL_LABELS:
        item = report["per_label"][label]
        print(f"{label:<12} {item['videos']:>6} {item['windows']:>8} {item['low_hand_windows']:>9}")
    if report["no_hand_majority_videos"]:
        print("")
        for row in report["no_hand_majority_videos"]:
            print(
                "WARNING: no_hand_majority "
                f"label={row['label']} video={row['video']} "
                f"hand_frames={row['hand_present_frames']}/{row['feature_frames']}"
            )
    print(f"Skipped unknown label videos: {len(report['skipped_unknown_label'])}")
    print(f"Report: {report_path}")


def main() -> None:
    parser = argparse.ArgumentParser(description="Extract FSL-105 videos into 20x162 FSL features.")
    parser.add_argument("--fsl105_dir", type=Path, required=True, help="Path to the FSL-105 video folder.")
    parser.add_argument("--output", type=Path, default=OUTPUT_ROOT)
    args = parser.parse_args()

    if not args.fsl105_dir.exists():
        raise SystemExit(f"FSL-105 directory does not exist: {args.fsl105_dir}")

    report, report_path = extract_dataset(args.fsl105_dir, args.output)
    print_summary(report, report_path)


if __name__ == "__main__":
    main()
