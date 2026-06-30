"""Classify FSL-105 signs as one-handed, two-handed, or ambiguous.

This inspection script reads the public FSL-105 metadata, runs MediaPipe
Holistic over extracted video files, and writes CSV/Markdown review reports.
It does not train models, export features, modify Android assets, or extract
archives.
"""

from __future__ import annotations

import argparse
import csv
import math
import re
import sys
import zipfile
from collections import Counter, defaultdict
from dataclasses import dataclass
from datetime import datetime
from pathlib import Path
from typing import Iterable

import numpy as np


ROOT = Path(__file__).resolve().parents[1]
DEFAULT_FSL_ROOT = ROOT / "FSL-105 A dataset for recognizing 105 Filipino sign language videos"
DEFAULT_OUTPUT_DIR = ROOT / "reports"
DEFAULT_DOC_PATH = ROOT / "docs" / "FSL105_HANDEDNESS_CLASSIFICATION.md"

VIDEO_EXTENSIONS = {".mp4", ".mov", ".avi", ".mkv", ".webm", ".m4v"}
METADATA_FILES = ("labels.csv", "train.csv", "test.csv")

MIN_ACTIVE_HAND_PRESENCE = 0.45
MAX_PASSIVE_HAND_PRESENCE_FOR_ONE_HAND = 0.25
MIN_BOTH_HAND_PRESENCE_FOR_TWO_HAND = 0.35
MIN_NON_DOMINANT_MOTION_RATIO_FOR_TWO_HAND = 0.25
MIN_NON_DOMINANT_PRESENCE_RATIO_FOR_TWO_HAND = 0.35

HAND_PALM_LANDMARKS = (0, 5, 9, 13, 17)
EPSILON = 1.0e-9

VIDEO_REPORT_COLUMNS = [
    "video_path",
    "label",
    "total_frames",
    "left_present_frames",
    "right_present_frames",
    "both_present_frames",
    "left_presence_ratio",
    "right_presence_ratio",
    "both_presence_ratio",
    "left_motion_total",
    "right_motion_total",
    "left_motion_mean",
    "right_motion_mean",
    "dominant_hand",
    "non_dominant_presence_ratio",
    "non_dominant_motion_ratio",
    "classification",
]

CLASS_REPORT_COLUMNS = [
    "label",
    "video_count",
    "one_handed_votes",
    "two_handed_votes",
    "ambiguous_votes",
    "avg_left_presence",
    "avg_right_presence",
    "avg_both_presence",
    "avg_left_motion",
    "avg_right_motion",
    "final_classification",
    "confidence_note",
]

MANUAL_REVIEW_COLUMNS = [
    "label",
    "video_count",
    "final_classification",
    "confidence_note",
    "one_handed_votes",
    "two_handed_votes",
    "ambiguous_votes",
    "avg_left_presence",
    "avg_right_presence",
    "avg_both_presence",
    "avg_left_motion",
    "avg_right_motion",
]

FAILED_VIDEO_COLUMNS = ["video_path", "label", "reason"]

cv2 = None
mp = None


@dataclass(frozen=True)
class Metadata:
    labels: list[str]
    categories: dict[str, str]
    path_to_label: dict[str, str]
    metadata_files: list[Path]
    referenced_paths: list[Path]


def require_video_dependencies() -> None:
    """Load video dependencies only when actual video processing begins."""
    global cv2, mp
    if cv2 is not None and mp is not None:
        return
    try:
        import cv2 as cv2_module
        import mediapipe as mp_module
    except ModuleNotFoundError as exc:
        raise SystemExit(
            "Missing video dependency. Install/use the VoxGest Python environment "
            "with mediapipe 0.10.x, opencv-python, pandas, and numpy before running "
            "this script."
        ) from exc
    cv2 = cv2_module
    mp = mp_module


def normalize_key(value: object) -> str:
    text = str(value).replace("\\", "/").strip().lower()
    text = re.sub(r"/+", "/", text)
    return text


def path_keys(path: Path, root: Path | None = None) -> set[str]:
    keys = {
        normalize_key(path),
        normalize_key(path.name),
        normalize_key(path.stem),
    }
    try:
        keys.add(normalize_key(path.resolve()))
    except OSError:
        pass
    if root is not None:
        try:
            keys.add(normalize_key(path.relative_to(root)))
        except ValueError:
            pass
    return keys


def normalize_label(value: object) -> str:
    text = str(value).strip().upper()
    text = re.sub(r"\s+", " ", text)
    return text


def safe_markdown_text(value: object) -> str:
    return str(value).replace("|", "\\|")


def atomic_write_text(path: Path, text: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    tmp_path = path.with_suffix(path.suffix + ".tmp")
    tmp_path.write_text(text, encoding="utf-8")
    tmp_path.replace(path)


def atomic_write_csv(path: Path, rows: list[dict], columns: list[str]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    tmp_path = path.with_suffix(path.suffix + ".tmp")
    with tmp_path.open("w", encoding="utf-8", newline="") as file:
        writer = csv.DictWriter(file, fieldnames=columns, extrasaction="ignore")
        writer.writeheader()
        for row in rows:
            writer.writerow(row)
    tmp_path.replace(path)


def read_csv_rows(path: Path) -> list[dict]:
    with path.open("r", encoding="utf-8-sig", newline="") as file:
        return list(csv.DictReader(file))


def find_metadata_files(root: Path) -> list[Path]:
    found: list[Path] = []
    for name in METADATA_FILES:
        found.extend(sorted(path for path in root.rglob(name) if path.is_file()))
    return sorted(set(found))


def choose_column(fieldnames: Iterable[str], candidates: Iterable[str]) -> str | None:
    lookup = {name.strip().lower(): name for name in fieldnames if name}
    for candidate in candidates:
        found = lookup.get(candidate.lower())
        if found:
            return found
    return None


def load_metadata(root: Path) -> Metadata:
    labels: list[str] = []
    categories: dict[str, str] = {}
    label_by_id: dict[str, str] = {}
    path_to_label: dict[str, str] = {}
    referenced_paths: list[Path] = []
    metadata_files = find_metadata_files(root)

    for csv_path in metadata_files:
        rows = read_csv_rows(csv_path)
        if not rows:
            continue
        fields = rows[0].keys()
        label_col = choose_column(fields, ("label", "sign", "class", "class_name", "gloss", "word"))
        id_col = choose_column(fields, ("id", "id_label", "label_id", "class_id", "index"))
        category_col = choose_column(fields, ("category", "type", "group"))

        if csv_path.name.lower() == "labels.csv":
            for row in rows:
                label = normalize_label(row.get(label_col, "")) if label_col else ""
                if not label:
                    continue
                if label not in labels:
                    labels.append(label)
                if id_col and str(row.get(id_col, "")).strip():
                    label_by_id[str(row.get(id_col, "")).strip()] = label
                if category_col:
                    categories[label] = str(row.get(category_col, "")).strip()

    for csv_path in metadata_files:
        if csv_path.name.lower() not in {"train.csv", "test.csv"}:
            continue
        rows = read_csv_rows(csv_path)
        if not rows:
            continue
        fields = rows[0].keys()
        video_col = choose_column(
            fields,
            (
                "vid_path",
                "video_path",
                "path",
                "video",
                "file",
                "filename",
                "file_name",
                "clip",
            ),
        )
        label_col = choose_column(fields, ("label", "sign", "class", "class_name", "gloss", "word"))
        id_col = choose_column(fields, ("id_label", "label_id", "class_id", "id", "index"))
        category_col = choose_column(fields, ("category", "type", "group"))
        if video_col is None:
            continue
        for row in rows:
            rel_video = str(row.get(video_col, "")).strip()
            if not rel_video:
                continue
            label = normalize_label(row.get(label_col, "")) if label_col else ""
            if not label and id_col:
                label = label_by_id.get(str(row.get(id_col, "")).strip(), "")
            if not label:
                continue
            if label not in labels:
                labels.append(label)
            if category_col:
                categories[label] = str(row.get(category_col, "")).strip()
            video_path = csv_path.parent / Path(rel_video.replace("\\", "/"))
            referenced_paths.append(video_path)
            for key in path_keys(video_path, root):
                path_to_label[key] = label
            path_to_label[normalize_key(rel_video)] = label

    return Metadata(
        labels=sorted(labels),
        categories=categories,
        path_to_label=path_to_label,
        metadata_files=metadata_files,
        referenced_paths=referenced_paths,
    )


def iter_video_files(root: Path) -> list[Path]:
    return sorted(
        path
        for path in root.rglob("*")
        if path.is_file() and path.suffix.lower() in VIDEO_EXTENSIONS
    )


def inspect_zip_video_entries(root: Path) -> dict[str, int]:
    archive_counts: dict[str, int] = {}
    for zip_path in sorted(root.rglob("*.zip")):
        count = 0
        try:
            with zipfile.ZipFile(zip_path) as archive:
                for item in archive.infolist():
                    if Path(item.filename).suffix.lower() in VIDEO_EXTENSIONS:
                        count += 1
        except zipfile.BadZipFile:
            count = -1
        archive_counts[str(zip_path)] = count
    return archive_counts


def discover_videos(root: Path) -> list[Path]:
    if not root.exists():
        raise SystemExit(f"ERROR: FSL-105 root folder not found: {root}")
    if not root.is_dir():
        raise SystemExit(f"ERROR: FSL-105 root is not a directory: {root}")

    videos = iter_video_files(root)
    if videos:
        return videos

    archive_counts = inspect_zip_video_entries(root)
    if archive_counts:
        archive_lines = []
        for archive, count in archive_counts.items():
            if count < 0:
                archive_lines.append(f"- {archive}: unreadable zip archive")
            else:
                archive_lines.append(f"- {archive}: {count} video entries")
        raise SystemExit(
            "ERROR: No extracted FSL-105 video files were found.\n"
            f"Searched under: {root}\n"
            f"Expected extensions: {', '.join(sorted(VIDEO_EXTENSIONS))}\n"
            "Found archive(s) instead:\n"
            + "\n".join(archive_lines)
            + "\nExtract clips.zip first so metadata paths like clips\\17\\6.MOV exist. "
            "This script will not unzip or modify the dataset."
        )

    raise SystemExit(
        "ERROR: No FSL-105 video files were found.\n"
        f"Searched under: {root}\n"
        f"Expected extensions: {', '.join(sorted(VIDEO_EXTENSIONS))}"
    )


def infer_label(video_path: Path, root: Path, metadata: Metadata) -> str:
    for key in path_keys(video_path, root):
        label = metadata.path_to_label.get(key)
        if label:
            return label
    try:
        relative_parts = video_path.relative_to(root).parts
    except ValueError:
        relative_parts = video_path.parts
    for part in reversed(relative_parts[:-1]):
        candidate = normalize_label(part)
        if candidate in metadata.labels:
            return candidate
    stem_tokens = [token for token in re.split(r"[^A-Za-z0-9]+", video_path.stem) if token]
    for token in stem_tokens:
        candidate = normalize_label(token)
        if candidate in metadata.labels:
            return candidate
    return "UNKNOWN"


def landmark_array(landmarks) -> np.ndarray | None:
    if landmarks is None or len(landmarks.landmark) == 0:
        return None
    return np.asarray([[lm.x, lm.y, lm.z] for lm in landmarks.landmark], dtype=np.float32)


def palm_center(landmarks) -> np.ndarray | None:
    arr = landmark_array(landmarks)
    if arr is None or arr.shape[0] <= max(HAND_PALM_LANDMARKS):
        return None
    return arr[list(HAND_PALM_LANDMARKS)].mean(axis=0)


def update_motion(center: np.ndarray | None, previous: np.ndarray | None) -> tuple[float, int]:
    if center is None or previous is None:
        return 0.0, 0
    distance = float(np.linalg.norm(center - previous))
    if not math.isfinite(distance):
        return 0.0, 0
    return distance, 1


def classify_video_stats(stats: dict) -> dict:
    left_presence = float(stats["left_presence_ratio"])
    right_presence = float(stats["right_presence_ratio"])
    both_presence = float(stats["both_presence_ratio"])
    left_motion = float(stats["left_motion_total"])
    right_motion = float(stats["right_motion_total"])

    if left_motion > right_motion + EPSILON:
        dominant_hand = "LEFT"
    elif right_motion > left_motion + EPSILON:
        dominant_hand = "RIGHT"
    elif left_presence > right_presence + EPSILON:
        dominant_hand = "LEFT"
    elif right_presence > left_presence + EPSILON:
        dominant_hand = "RIGHT"
    else:
        dominant_hand = "UNKNOWN"

    if dominant_hand == "LEFT":
        dominant_motion = left_motion
        non_dominant_motion = right_motion
        non_dominant_presence = right_presence
    elif dominant_hand == "RIGHT":
        dominant_motion = right_motion
        non_dominant_motion = left_motion
        non_dominant_presence = left_presence
    else:
        dominant_motion = max(left_motion, right_motion)
        non_dominant_motion = min(left_motion, right_motion)
        non_dominant_presence = min(left_presence, right_presence)

    active_presence = max(left_presence, right_presence)
    passive_presence = min(left_presence, right_presence)
    non_dominant_motion_ratio = (
        float(non_dominant_motion / dominant_motion) if dominant_motion > EPSILON else 0.0
    )

    support_or_motion = (
        non_dominant_presence >= MIN_NON_DOMINANT_PRESENCE_RATIO_FOR_TWO_HAND
        or non_dominant_motion_ratio >= MIN_NON_DOMINANT_MOTION_RATIO_FOR_TWO_HAND
    )
    if both_presence >= MIN_BOTH_HAND_PRESENCE_FOR_TWO_HAND and support_or_motion:
        classification = "TWO_HANDED"
    elif (
        active_presence >= MIN_ACTIVE_HAND_PRESENCE
        and passive_presence <= MAX_PASSIVE_HAND_PRESENCE_FOR_ONE_HAND
        and non_dominant_motion_ratio < MIN_NON_DOMINANT_MOTION_RATIO_FOR_TWO_HAND
    ):
        classification = "ONE_HANDED"
    else:
        classification = "AMBIGUOUS"

    stats["dominant_hand"] = dominant_hand
    stats["non_dominant_presence_ratio"] = round(non_dominant_presence, 6)
    stats["non_dominant_motion_ratio"] = round(non_dominant_motion_ratio, 6)
    stats["classification"] = classification
    return stats


def process_video(video_path: Path, label: str, holistic) -> dict:
    cap = cv2.VideoCapture(str(video_path))
    if not cap.isOpened():
        raise RuntimeError("opencv_could_not_open")

    total_frames = 0
    left_present_frames = 0
    right_present_frames = 0
    both_present_frames = 0
    left_motion_total = 0.0
    right_motion_total = 0.0
    left_motion_steps = 0
    right_motion_steps = 0
    previous_left: np.ndarray | None = None
    previous_right: np.ndarray | None = None

    while True:
        ok, frame = cap.read()
        if not ok:
            break
        total_frames += 1
        rgb = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
        results = holistic.process(rgb)

        left_center = palm_center(results.left_hand_landmarks)
        right_center = palm_center(results.right_hand_landmarks)
        left_present = left_center is not None
        right_present = right_center is not None

        if left_present:
            left_present_frames += 1
        if right_present:
            right_present_frames += 1
        if left_present and right_present:
            both_present_frames += 1

        left_delta, left_step = update_motion(left_center, previous_left)
        right_delta, right_step = update_motion(right_center, previous_right)
        left_motion_total += left_delta
        right_motion_total += right_delta
        left_motion_steps += left_step
        right_motion_steps += right_step

        previous_left = left_center if left_present else None
        previous_right = right_center if right_present else None

    cap.release()
    if total_frames == 0:
        raise RuntimeError("no_decoded_frames")

    stats = {
        "video_path": str(video_path),
        "label": label,
        "total_frames": total_frames,
        "left_present_frames": left_present_frames,
        "right_present_frames": right_present_frames,
        "both_present_frames": both_present_frames,
        "left_presence_ratio": round(left_present_frames / total_frames, 6),
        "right_presence_ratio": round(right_present_frames / total_frames, 6),
        "both_presence_ratio": round(both_present_frames / total_frames, 6),
        "left_motion_total": round(left_motion_total, 6),
        "right_motion_total": round(right_motion_total, 6),
        "left_motion_mean": round(left_motion_total / left_motion_steps, 6) if left_motion_steps else 0.0,
        "right_motion_mean": round(right_motion_total / right_motion_steps, 6) if right_motion_steps else 0.0,
        "dominant_hand": "UNKNOWN",
        "non_dominant_presence_ratio": 0.0,
        "non_dominant_motion_ratio": 0.0,
        "classification": "AMBIGUOUS",
    }
    return classify_video_stats(stats)


def aggregate_classes(video_rows: list[dict], labels: list[str]) -> list[dict]:
    grouped: dict[str, list[dict]] = defaultdict(list)
    for row in video_rows:
        grouped[str(row["label"])].append(row)

    output_labels = sorted(set(labels) | set(grouped.keys()))
    class_rows: list[dict] = []
    for label in output_labels:
        rows = grouped.get(label, [])
        vote_counts = Counter(row.get("classification", "AMBIGUOUS") for row in rows)
        video_count = len(rows)

        if video_count == 0:
            final_classification = "AMBIGUOUS"
            confidence_note = "manual_review:no_processed_videos_for_label"
            avg_left_presence = avg_right_presence = avg_both_presence = 0.0
            avg_left_motion = avg_right_motion = 0.0
        else:
            avg_left_presence = float(np.mean([float(row["left_presence_ratio"]) for row in rows]))
            avg_right_presence = float(np.mean([float(row["right_presence_ratio"]) for row in rows]))
            avg_both_presence = float(np.mean([float(row["both_presence_ratio"]) for row in rows]))
            avg_left_motion = float(np.mean([float(row["left_motion_mean"]) for row in rows]))
            avg_right_motion = float(np.mean([float(row["right_motion_mean"]) for row in rows]))

            ranked = vote_counts.most_common()
            top_class, top_count = ranked[0]
            top_share = top_count / video_count
            if top_class == "AMBIGUOUS":
                final_classification = "AMBIGUOUS"
                confidence_note = f"manual_review:ambiguous_majority:{top_count}/{video_count}"
            elif top_share >= 0.70:
                final_classification = top_class
                confidence_note = f"strong_majority:{top_count}/{video_count}"
            elif top_share >= 0.50 and len(ranked) > 1 and top_count > ranked[1][1]:
                final_classification = top_class
                confidence_note = f"weak_majority:{top_count}/{video_count}"
            else:
                final_classification = "AMBIGUOUS"
                confidence_note = f"manual_review:mixed_votes:{dict(vote_counts)}"

            if video_count < 2 and final_classification != "AMBIGUOUS":
                confidence_note += ";low_sample_count"

        class_rows.append(
            {
                "label": label,
                "video_count": video_count,
                "one_handed_votes": int(vote_counts.get("ONE_HANDED", 0)),
                "two_handed_votes": int(vote_counts.get("TWO_HANDED", 0)),
                "ambiguous_votes": int(vote_counts.get("AMBIGUOUS", 0)),
                "avg_left_presence": round(avg_left_presence, 6),
                "avg_right_presence": round(avg_right_presence, 6),
                "avg_both_presence": round(avg_both_presence, 6),
                "avg_left_motion": round(avg_left_motion, 6),
                "avg_right_motion": round(avg_right_motion, 6),
                "final_classification": final_classification,
                "confidence_note": confidence_note,
            }
        )
    return class_rows


def build_manual_review_rows(class_rows: list[dict]) -> list[dict]:
    rows = []
    for row in class_rows:
        note = str(row.get("confidence_note", ""))
        final_classification = str(row.get("final_classification", ""))
        if final_classification == "AMBIGUOUS" or "weak_majority" in note or "low_sample_count" in note:
            rows.append(row)
    return rows


def markdown_table(rows: list[dict], columns: list[str], limit: int | None = None) -> list[str]:
    selected = rows if limit is None else rows[:limit]
    if not selected:
        return ["None recorded in this run."]
    lines = [
        "| " + " | ".join(columns) + " |",
        "| " + " | ".join("---" for _ in columns) + " |",
    ]
    for row in selected:
        lines.append("| " + " | ".join(safe_markdown_text(row.get(column, "")) for column in columns) + " |")
    if limit is not None and len(rows) > limit:
        lines.append(f"\nShowing {limit} of {len(rows)} rows. See the CSV report for the full list.")
    return lines


def write_markdown_report(
    doc_path: Path,
    fsl_root: Path,
    output_dir: Path,
    metadata: Metadata,
    video_rows: list[dict],
    class_rows: list[dict],
    failed_rows: list[dict],
    max_videos: int | None,
    discovery_note: str | None = None,
) -> None:
    one_handed = [row for row in class_rows if row["final_classification"] == "ONE_HANDED"]
    two_handed = [row for row in class_rows if row["final_classification"] == "TWO_HANDED"]
    manual = build_manual_review_rows(class_rows)
    generated_at = datetime.now().isoformat(timespec="seconds")
    max_note = "full discovered set" if max_videos is None else f"limited run, max_videos={max_videos}"

    lines = [
        "# FSL-105 Handedness Classification",
        "",
        f"Generated: {generated_at}",
        "",
        "## Scope",
        "",
        "This report is produced by `scripts_ml/93_classify_fsl105_handedness.py`. "
        "It inspects FSL-105 videos with MediaPipe Holistic and classifies each sign "
        "as `ONE_HANDED`, `TWO_HANDED`, or `AMBIGUOUS` for VoxGest training decisions.",
        "",
        f"- FSL root: `{fsl_root}`",
        f"- Output directory: `{output_dir}`",
        f"- Metadata files read: {len(metadata.metadata_files)}",
        f"- Metadata labels found: {len(metadata.labels)}",
        f"- Videos processed in this run: {len(video_rows)} ({max_note})",
        f"- Failed videos: {len(failed_rows)}",
        "",
    ]
    if discovery_note:
        lines.extend(
            [
                "## Current Dataset Status",
                "",
                "The script did not run MediaPipe over videos because extracted video files were not available.",
                "",
                "```text",
                discovery_note,
                "```",
                "",
            ]
        )
    lines.extend(
        [
        "## Statistics Used",
        "",
        "- `left_presence_ratio` and `right_presence_ratio`: fraction of decoded frames where MediaPipe detected each hand.",
        "- `both_presence_ratio`: fraction of frames where both hands were detected together.",
        "- `left_motion_total` and `right_motion_total`: summed palm-center movement between consecutive detected frames.",
        "- `left_motion_mean` and `right_motion_mean`: average palm-center movement per valid motion step.",
        "- `dominant_hand`: hand with greater total motion, falling back to presence if motion is tied.",
        "- `non_dominant_presence_ratio`: presence ratio for the hand that is not dominant.",
        "- `non_dominant_motion_ratio`: non-dominant total motion divided by dominant total motion.",
        "",
        "## Heuristic",
        "",
        f"- `MIN_ACTIVE_HAND_PRESENCE = {MIN_ACTIVE_HAND_PRESENCE}`",
        f"- `MAX_PASSIVE_HAND_PRESENCE_FOR_ONE_HAND = {MAX_PASSIVE_HAND_PRESENCE_FOR_ONE_HAND}`",
        f"- `MIN_BOTH_HAND_PRESENCE_FOR_TWO_HAND = {MIN_BOTH_HAND_PRESENCE_FOR_TWO_HAND}`",
        f"- `MIN_NON_DOMINANT_MOTION_RATIO_FOR_TWO_HAND = {MIN_NON_DOMINANT_MOTION_RATIO_FOR_TWO_HAND}`",
        f"- `MIN_NON_DOMINANT_PRESENCE_RATIO_FOR_TWO_HAND = {MIN_NON_DOMINANT_PRESENCE_RATIO_FOR_TWO_HAND}`",
        "",
        "`ONE_HANDED` means one hand is active enough while the passive hand remains below the one-hand presence ceiling. "
        "`TWO_HANDED` means both hands are present together often enough and the non-dominant hand shows support or motion. "
        "`AMBIGUOUS` means the video/class does not satisfy either rule strongly enough and should be manually reviewed.",
        "",
        "## Likely One-Handed Classes",
        "",
        *markdown_table(
            one_handed,
            ["label", "video_count", "one_handed_votes", "avg_left_presence", "avg_right_presence", "confidence_note"],
        ),
        "",
        "## Likely Two-Handed Classes",
        "",
        *markdown_table(
            two_handed,
            ["label", "video_count", "two_handed_votes", "avg_both_presence", "avg_left_motion", "avg_right_motion", "confidence_note"],
        ),
        "",
        "## Manual Review Classes",
        "",
        *markdown_table(
            manual,
            ["label", "video_count", "final_classification", "confidence_note", "one_handed_votes", "two_handed_votes", "ambiguous_votes"],
            limit=80,
        ),
        "",
        "## Output Files",
        "",
        "- `reports/fsl105_handedness_by_video.csv`: one row per processed video.",
        "- `reports/fsl105_handedness_by_class.csv`: aggregated class-level handedness decision.",
        "- `reports/fsl105_handedness_manual_review.csv`: ambiguous, weak, or low-sample labels.",
        "- `reports/fsl105_handedness_failed_videos.csv`: videos that failed to open/process.",
        "",
        "## VoxGest Impact",
        "",
        "- `ONE_HANDED`: compatible with the current 162-feature one-hand temporal pipeline.",
        "- `TWO_HANDED`: may require the 225-feature full-sign pipeline so the second hand is represented.",
        "- `AMBIGUOUS`: should be manually reviewed before inclusion in training.",
        "",
        "## Limitations",
        "",
        "- MediaPipe detection can miss hands because of blur, occlusion, cropping, lighting, or signer speed.",
        "- A static support hand can produce low motion, so this script treats non-dominant presence as support evidence.",
        "- `--max_videos` runs are inspection/smoke runs and should not be treated as final class labels.",
        "- The script reads extracted video files only. It does not unzip `clips.zip` or modify datasets.",
        ]
    )
    atomic_write_text(doc_path, "\n".join(lines) + "\n")


def run(args: argparse.Namespace) -> int:
    fsl_root = args.fsl_root.resolve()
    output_dir = args.output_dir.resolve()
    doc_path = args.doc_path.resolve()
    output_dir.mkdir(parents=True, exist_ok=True)

    by_video_path = output_dir / "fsl105_handedness_by_video.csv"
    by_class_path = output_dir / "fsl105_handedness_by_class.csv"
    manual_review_path = output_dir / "fsl105_handedness_manual_review.csv"
    failed_path = output_dir / "fsl105_handedness_failed_videos.csv"

    metadata = load_metadata(fsl_root) if fsl_root.exists() else Metadata([], {}, {}, [], [])
    if args.max_videos is not None and args.max_videos < 0:
        raise SystemExit("ERROR: --max_videos must be zero or greater.")

    try:
        videos = discover_videos(fsl_root)
    except SystemExit as exc:
        discovery_note = str(exc)
        video_rows: list[dict] = []
        failed_rows: list[dict] = []
        class_rows = aggregate_classes(video_rows, metadata.labels)
        manual_rows = build_manual_review_rows(class_rows)
        atomic_write_csv(by_video_path, video_rows, VIDEO_REPORT_COLUMNS)
        atomic_write_csv(by_class_path, class_rows, CLASS_REPORT_COLUMNS)
        atomic_write_csv(manual_review_path, manual_rows, MANUAL_REVIEW_COLUMNS)
        atomic_write_csv(failed_path, failed_rows, FAILED_VIDEO_COLUMNS)
        write_markdown_report(
            doc_path=doc_path,
            fsl_root=fsl_root,
            output_dir=output_dir,
            metadata=metadata,
            video_rows=video_rows,
            class_rows=class_rows,
            failed_rows=failed_rows,
            max_videos=args.max_videos,
            discovery_note=discovery_note,
        )
        print(discovery_note)
        print("")
        print("Blocked-state reports written:")
        print(f"- {by_video_path}")
        print(f"- {by_class_path}")
        print(f"- {manual_review_path}")
        print(f"- {failed_path}")
        print(f"- {doc_path}")
        return 1

    if args.max_videos is not None:
        videos = videos[: args.max_videos]

    require_video_dependencies()

    video_rows: list[dict] = []
    failed_rows: list[dict] = []

    print(f"FSL-105 handedness classification")
    print(f"FSL root       : {fsl_root}")
    print(f"Videos found   : {len(videos)}")
    print(f"Metadata labels: {len(metadata.labels)}")
    print(f"Output dir     : {output_dir}")
    print("")

    if not videos:
        print("No videos selected for processing. Writing empty aggregate reports.")

    with mp.solutions.holistic.Holistic(
        static_image_mode=False,
        model_complexity=args.model_complexity,
        enable_segmentation=False,
        refine_face_landmarks=False,
        min_detection_confidence=args.min_detection_confidence,
        min_tracking_confidence=args.min_tracking_confidence,
    ) as holistic:
        for index, video_path in enumerate(videos, start=1):
            label = infer_label(video_path, fsl_root, metadata)
            print(f"[{index}/{len(videos)}] {label}: {video_path}")
            try:
                row = process_video(video_path, label, holistic)
            except Exception as exc:
                reason = f"{type(exc).__name__}:{exc}"
                print(f"  FAILED: {reason}")
                failed_rows.append({"video_path": str(video_path), "label": label, "reason": reason})
                atomic_write_csv(failed_path, failed_rows, FAILED_VIDEO_COLUMNS)
                continue

            print(
                "  "
                f"classification={row['classification']} "
                f"left={row['left_presence_ratio']} right={row['right_presence_ratio']} "
                f"both={row['both_presence_ratio']} dominant={row['dominant_hand']}"
            )
            video_rows.append(row)
            atomic_write_csv(by_video_path, video_rows, VIDEO_REPORT_COLUMNS)
            atomic_write_csv(failed_path, failed_rows, FAILED_VIDEO_COLUMNS)

    class_rows = aggregate_classes(video_rows, metadata.labels)
    manual_rows = build_manual_review_rows(class_rows)
    atomic_write_csv(by_video_path, video_rows, VIDEO_REPORT_COLUMNS)
    atomic_write_csv(by_class_path, class_rows, CLASS_REPORT_COLUMNS)
    atomic_write_csv(manual_review_path, manual_rows, MANUAL_REVIEW_COLUMNS)
    atomic_write_csv(failed_path, failed_rows, FAILED_VIDEO_COLUMNS)
    write_markdown_report(
        doc_path=doc_path,
        fsl_root=fsl_root,
        output_dir=output_dir,
        metadata=metadata,
        video_rows=video_rows,
        class_rows=class_rows,
        failed_rows=failed_rows,
        max_videos=args.max_videos,
    )

    print("")
    print("Reports written:")
    print(f"- {by_video_path}")
    print(f"- {by_class_path}")
    print(f"- {manual_review_path}")
    print(f"- {failed_path}")
    print(f"- {doc_path}")
    return 0


def parse_args(argv: list[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Classify FSL-105 sign classes as one-handed, two-handed, or ambiguous."
    )
    parser.add_argument(
        "--fsl_root",
        "--fsl105_dir",
        type=Path,
        default=DEFAULT_FSL_ROOT,
        help="Path to the extracted FSL-105 folder containing labels.csv/train.csv/test.csv and clips/",
    )
    parser.add_argument("--output_dir", type=Path, default=DEFAULT_OUTPUT_DIR)
    parser.add_argument("--doc_path", type=Path, default=DEFAULT_DOC_PATH)
    parser.add_argument("--max_videos", type=int, default=None, help="Optional cap for smoke tests.")
    parser.add_argument("--model_complexity", type=int, default=1, choices=(0, 1, 2))
    parser.add_argument("--min_detection_confidence", type=float, default=0.5)
    parser.add_argument("--min_tracking_confidence", type=float, default=0.5)
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    args = parse_args(sys.argv[1:] if argv is None else argv)
    return run(args)


if __name__ == "__main__":
    raise SystemExit(main())
