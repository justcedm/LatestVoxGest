"""Readability and MediaPipe extraction audit for FullSign225 team16 videos.

This audit does not write training arrays and does not train a model. It checks
whether normalized videos can be opened, sampled, tracked by MediaPipe Holistic,
and represented as 30 x 225 fullsign sequences in memory.
"""

import csv
import json
import os
from collections import Counter, defaultdict
from datetime import datetime
from pathlib import Path

os.environ.setdefault("VOXGEST_FEATURE_PROFILE", "fullsign225")
os.environ.setdefault("VOXGEST_SINGLE_HAND_POSE", "0")
os.environ.setdefault("PROTOCOL_BUFFERS_PYTHON_IMPLEMENTATION", "python")
os.environ.setdefault("TF_CPP_MIN_LOG_LEVEL", "3")

import cv2
import mediapipe as mp
import numpy as np

from lstm_features import (
    FEAT_SIZE,
    SEQ_LEN,
    configured_feature_profile,
    enforce_nose_anchor,
    extract_frame_features,
    sequence_hand_presence_ratio,
    sequence_motion_energy,
    sequence_wrist_path,
)


ROOT = Path(__file__).resolve().parents[1]
INPUT_ROOT = Path(
    os.environ.get(
        "VOXGEST_FULLSIGN225_TEAM16_VIDEO_ROOT",
        ROOT / "external_datasets" / "fullsign225_team_dataset_normalized",
    )
)
REPORT_DIR = ROOT / "reports"
CSV_OUT = REPORT_DIR / "fullsign225_team16_extraction_audit.csv"
JSON_OUT = REPORT_DIR / "fullsign225_team16_extraction_audit.json"
MD_OUT = REPORT_DIR / "fullsign225_team16_extraction_audit.md"

TEAM16_LABELS = [
    "DOCTOR",
    "EAT",
    "HELLO",
    "HELP",
    "NAME",
    "NO",
    "NOTHING",
    "PAIN",
    "PLEASE",
    "SORRY",
    "STOP",
    "THANKYOU",
    "TIME",
    "WANT",
    "WATER",
    "YES",
]

VIDEO_EXTENSIONS = {".mp4"}
MAX_SAMPLED_FRAMES = int(os.environ.get("VOXGEST_AUDIT_MAX_SAMPLED_FRAMES", "48"))
MIN_VIDEO_FRAMES = int(os.environ.get("VOXGEST_AUDIT_MIN_VIDEO_FRAMES", "10"))
MIN_FEATURE_FRAMES = int(os.environ.get("VOXGEST_AUDIT_MIN_FEATURE_FRAMES", "8"))
MIN_POSE_RATIO = float(os.environ.get("VOXGEST_AUDIT_MIN_POSE_RATIO", "0.45"))
MIN_HAND_RATIO = float(os.environ.get("VOXGEST_AUDIT_MIN_HAND_RATIO", "0.20"))
READY_MIN_VIDEOS = int(os.environ.get("VOXGEST_AUDIT_READY_MIN_VIDEOS", "10"))
TARGET_MIN_VIDEOS = int(os.environ.get("VOXGEST_AUDIT_TARGET_MIN_VIDEOS", "30"))
DETECTION_CONF = float(os.environ.get("VOXGEST_AUDIT_DETECTION_CONF", "0.45"))
TRACKING_CONF = float(os.environ.get("VOXGEST_AUDIT_TRACKING_CONF", "0.40"))
EXPECTED_SHAPE = (SEQ_LEN, FEAT_SIZE)


def label_key(value):
    return "".join(ch for ch in str(value).upper() if ch.isalnum())


LABEL_BY_KEY = {label_key(label): label for label in TEAM16_LABELS}


def discover_videos():
    videos = []
    manual_review = []
    if not INPUT_ROOT.exists():
        raise FileNotFoundError(f"Missing normalized dataset root: {INPUT_ROOT}")

    for path in sorted(INPUT_ROOT.rglob("*"), key=lambda item: str(item).casefold()):
        if not path.is_file():
            continue
        if path.suffix.lower() not in VIDEO_EXTENSIONS:
            continue
        label = LABEL_BY_KEY.get(label_key(path.parent.name))
        if label is None:
            manual_review.append(
                {
                    "video_path": str(path),
                    "label": "",
                    "file_name": path.name,
                    "opencv_open": False,
                    "frame_count": 0,
                    "frames_sampled": 0,
                    "frames_read": 0,
                    "pose_frames": 0,
                    "left_hand_frames": 0,
                    "right_hand_frames": 0,
                    "either_hand_frames": 0,
                    "both_hands_frames": 0,
                    "feature_frames": 0,
                    "pose_ratio": 0.0,
                    "left_hand_ratio": 0.0,
                    "right_hand_ratio": 0.0,
                    "either_hand_ratio": 0.0,
                    "both_hands_ratio": 0.0,
                    "sequence_shape_ok": False,
                    "extraction_success": False,
                    "poor_tracking": True,
                    "motion": 0.0,
                    "wrist_path": 0.0,
                    "hand_presence_ratio": 0.0,
                    "failure_reason": "outside_known_label_folder",
                }
            )
            continue
        videos.append((label, path))

    videos.sort(key=lambda item: (TEAM16_LABELS.index(item[0]), str(item[1]).casefold()))
    return videos, manual_review


def sample_indices(frame_count):
    if frame_count <= 0:
        return []
    n = min(frame_count, MAX_SAMPLED_FRAMES)
    if n <= 1:
        return [0]
    return sorted(set(int(round(x)) for x in np.linspace(0, frame_count - 1, n)))


def build_sequence(feature_frames):
    if len(feature_frames) < MIN_FEATURE_FRAMES:
        return None
    if len(feature_frames) >= SEQ_LEN:
        indices = np.linspace(0, len(feature_frames) - 1, SEQ_LEN, dtype=int)
        seq = np.array([feature_frames[i] for i in indices], dtype=np.float32)
    else:
        seq = np.array(feature_frames, dtype=np.float32)
        pad = np.tile(seq[-1], (SEQ_LEN - len(feature_frames), 1))
        seq = np.vstack([seq, pad]).astype(np.float32)
    if seq.shape != EXPECTED_SHAPE:
        return None
    return enforce_nose_anchor(seq)


def failure_reasons(row):
    reasons = []
    if not row["opencv_open"]:
        reasons.append("opencv_open_failed")
    if row["frame_count"] < MIN_VIDEO_FRAMES:
        reasons.append(f"low_frame_count<{MIN_VIDEO_FRAMES}")
    if row["frames_read"] == 0:
        reasons.append("no_frames_read")
    if row["pose_ratio"] < MIN_POSE_RATIO:
        reasons.append(f"low_pose_ratio<{MIN_POSE_RATIO:.2f}")
    if row["either_hand_ratio"] < MIN_HAND_RATIO:
        reasons.append(f"low_hand_ratio<{MIN_HAND_RATIO:.2f}")
    if row["feature_frames"] < MIN_FEATURE_FRAMES:
        reasons.append(f"low_feature_frames<{MIN_FEATURE_FRAMES}")
    if not row["sequence_shape_ok"]:
        reasons.append("sequence_shape_not_30x225")
    return reasons


def audit_video(label, video_path, holistic):
    row = {
        "video_path": str(video_path),
        "label": label,
        "file_name": video_path.name,
        "opencv_open": False,
        "frame_count": 0,
        "frames_sampled": 0,
        "frames_read": 0,
        "pose_frames": 0,
        "left_hand_frames": 0,
        "right_hand_frames": 0,
        "either_hand_frames": 0,
        "both_hands_frames": 0,
        "feature_frames": 0,
        "pose_ratio": 0.0,
        "left_hand_ratio": 0.0,
        "right_hand_ratio": 0.0,
        "either_hand_ratio": 0.0,
        "both_hands_ratio": 0.0,
        "sequence_shape_ok": False,
        "extraction_success": False,
        "poor_tracking": True,
        "motion": 0.0,
        "wrist_path": 0.0,
        "hand_presence_ratio": 0.0,
        "failure_reason": "",
    }
    cap = cv2.VideoCapture(str(video_path))
    row["opencv_open"] = bool(cap.isOpened())
    if not row["opencv_open"]:
        row["failure_reason"] = "opencv_open_failed"
        cap.release()
        return row

    frame_count = int(cap.get(cv2.CAP_PROP_FRAME_COUNT) or 0)
    row["frame_count"] = frame_count
    indices = sample_indices(frame_count)
    row["frames_sampled"] = len(indices)
    feature_frames = []

    for idx in indices:
        cap.set(cv2.CAP_PROP_POS_FRAMES, idx)
        ok, frame = cap.read()
        if not ok or frame is None:
            continue
        row["frames_read"] += 1
        try:
            rgb = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
            results = holistic.process(rgb)
        except Exception:
            continue

        pose = results.pose_landmarks is not None
        left = results.left_hand_landmarks is not None
        right = results.right_hand_landmarks is not None
        if pose:
            row["pose_frames"] += 1
        if left:
            row["left_hand_frames"] += 1
        if right:
            row["right_hand_frames"] += 1
        if left or right:
            row["either_hand_frames"] += 1
        if left and right:
            row["both_hands_frames"] += 1

        vec = extract_frame_features(results, mirrored_input=False)
        if vec is not None and vec.shape == (FEAT_SIZE,):
            feature_frames.append(vec)

    cap.release()
    row["feature_frames"] = len(feature_frames)

    denom = max(1, row["frames_read"])
    row["pose_ratio"] = round(row["pose_frames"] / denom, 6)
    row["left_hand_ratio"] = round(row["left_hand_frames"] / denom, 6)
    row["right_hand_ratio"] = round(row["right_hand_frames"] / denom, 6)
    row["either_hand_ratio"] = round(row["either_hand_frames"] / denom, 6)
    row["both_hands_ratio"] = round(row["both_hands_frames"] / denom, 6)

    seq = build_sequence(feature_frames)
    if seq is not None:
        row["sequence_shape_ok"] = True
        row["motion"] = round(float(sequence_motion_energy(seq)), 6)
        row["wrist_path"] = round(float(sequence_wrist_path(seq)), 6)
        row["hand_presence_ratio"] = round(float(sequence_hand_presence_ratio(seq)), 6)

    reasons = failure_reasons(row)
    row["failure_reason"] = ";".join(reasons) if reasons else "ok"
    row["poor_tracking"] = any(
        reason.startswith("low_pose_ratio")
        or reason.startswith("low_hand_ratio")
        or reason.startswith("low_feature_frames")
        for reason in reasons
    )
    row["extraction_success"] = not reasons
    return row


def summarize(rows):
    per_label = {}
    for label in TEAM16_LABELS:
        label_rows = [row for row in rows if row["label"] == label]
        total = len(label_rows)
        success = sum(1 for row in label_rows if row["extraction_success"])
        failed = total - success
        poor = sum(1 for row in label_rows if row["poor_tracking"])
        per_label[label] = {
            "total_videos": total,
            "extraction_success": success,
            "failed_videos": failed,
            "poor_tracking_videos": poor,
            "pose_success_avg": round(
                float(np.mean([row["pose_ratio"] for row in label_rows])) if label_rows else 0.0,
                6,
            ),
            "either_hand_avg": round(
                float(np.mean([row["either_hand_ratio"] for row in label_rows])) if label_rows else 0.0,
                6,
            ),
            "left_hand_avg": round(
                float(np.mean([row["left_hand_ratio"] for row in label_rows])) if label_rows else 0.0,
                6,
            ),
            "right_hand_avg": round(
                float(np.mean([row["right_hand_ratio"] for row in label_rows])) if label_rows else 0.0,
                6,
            ),
            "ready_for_training": success >= READY_MIN_VIDEOS,
            "needs_more_recordings": success < TARGET_MIN_VIDEOS,
        }
    labels_ready = [label for label, stats in per_label.items() if stats["ready_for_training"]]
    labels_needing_more = [label for label, stats in per_label.items() if stats["needs_more_recordings"]]
    labels_under_10 = [label for label, stats in per_label.items() if stats["extraction_success"] < READY_MIN_VIDEOS]
    labels_under_30 = [label for label, stats in per_label.items() if stats["extraction_success"] < TARGET_MIN_VIDEOS]
    failure_counts = Counter()
    for row in rows:
        if row["failure_reason"] == "ok":
            continue
        for reason in row["failure_reason"].split(";"):
            failure_counts[reason] += 1
    return per_label, labels_ready, labels_needing_more, labels_under_10, labels_under_30, dict(failure_counts)


def write_csv(rows):
    fields = [
        "label",
        "file_name",
        "video_path",
        "opencv_open",
        "frame_count",
        "frames_sampled",
        "frames_read",
        "pose_frames",
        "left_hand_frames",
        "right_hand_frames",
        "either_hand_frames",
        "both_hands_frames",
        "feature_frames",
        "pose_ratio",
        "left_hand_ratio",
        "right_hand_ratio",
        "either_hand_ratio",
        "both_hands_ratio",
        "sequence_shape_ok",
        "extraction_success",
        "poor_tracking",
        "motion",
        "wrist_path",
        "hand_presence_ratio",
        "failure_reason",
    ]
    with open(CSV_OUT, "w", encoding="utf-8", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=fields)
        writer.writeheader()
        writer.writerows(rows)


def write_markdown(payload):
    lines = [
        "# FullSign225 Team16 Extraction Audit",
        "",
        f"Generated: {payload['generated_at']}",
        f"Input: `{payload['input_root']}`",
        f"Feature profile: `{payload['feature_profile']}`",
        f"Input target: `{payload['input_shape']}`",
        f"Videos audited: {payload['totals']['videos']}",
        f"Extraction success: {payload['totals']['extraction_success']}",
        f"Failed videos: {payload['totals']['failed_videos']}",
        f"Poor tracking videos: {payload['totals']['poor_tracking_videos']}",
        "",
        "## Per Label",
        "",
        "| Label | Total | Success | Failed | Poor Tracking | Pose Avg | Hand Avg | Ready |",
        "| --- | ---: | ---: | ---: | ---: | ---: | ---: | --- |",
    ]
    for label in TEAM16_LABELS:
        stats = payload["per_label"][label]
        ready = "yes" if stats["ready_for_training"] else "no"
        lines.append(
            f"| {label} | {stats['total_videos']} | {stats['extraction_success']} | "
            f"{stats['failed_videos']} | {stats['poor_tracking_videos']} | "
            f"{stats['pose_success_avg']:.2f} | {stats['either_hand_avg']:.2f} | {ready} |"
        )
    lines.extend(
        [
            "",
            "## Labels Ready For Training",
            "",
            ", ".join(payload["labels_ready_for_training"]) if payload["labels_ready_for_training"] else "None",
            "",
            "## Labels Needing More Recordings",
            "",
            ", ".join(payload["labels_needing_more_recordings"])
            if payload["labels_needing_more_recordings"]
            else "None",
            "",
            "## Labels Under 10 Successful Extractions",
            "",
            ", ".join(payload["labels_under_10"]) if payload["labels_under_10"] else "None",
            "",
            "## Labels Under 30 Successful Extractions",
            "",
            ", ".join(payload["labels_under_30"]) if payload["labels_under_30"] else "None",
            "",
            "## Failure Reasons",
            "",
        ]
    )
    if payload["failure_reasons"]:
        for reason, count in sorted(payload["failure_reasons"].items(), key=lambda item: (-item[1], item[0])):
            lines.append(f"- {reason}: {count}")
    else:
        lines.append("None")
    lines.extend(
        [
            "",
            "## NOTHING Warning",
            "",
            "NOTHING currently has 19 normalized videos. It is usable for an initial audit, but should be expanded later with idle hands, hand entering/leaving frame, partial signs, aborted signs, and transition movements.",
            "",
            "## Next Step",
            "",
            "Do not train yet unless the team accepts the audit result. If accepted, prepare a separate `fullsign225_team16` profile and extraction/training commands without changing demo10 or onehand162 defaults.",
        ]
    )
    with open(MD_OUT, "w", encoding="utf-8") as f:
        f.write("\n".join(lines) + "\n")


def main():
    if configured_feature_profile() != "fullsign225" or FEAT_SIZE != 225:
        raise RuntimeError(f"Expected fullsign225/225 features, got {configured_feature_profile()}/{FEAT_SIZE}")
    REPORT_DIR.mkdir(parents=True, exist_ok=True)
    videos, manual_review = discover_videos()
    rows = []
    print("=" * 78, flush=True)
    print("VoxGest FullSign225 team16 extraction audit", flush=True)
    print("=" * 78, flush=True)
    print(f"Input root : {INPUT_ROOT}", flush=True)
    print(f"Videos     : {len(videos)}", flush=True)
    print(f"Feature    : {configured_feature_profile()} ({FEAT_SIZE})", flush=True)
    print(f"Reports    : {CSV_OUT.name}, {JSON_OUT.name}, {MD_OUT.name}", flush=True)
    print("", flush=True)

    with mp.solutions.holistic.Holistic(
        static_image_mode=False,
        model_complexity=1,
        min_detection_confidence=DETECTION_CONF,
        min_tracking_confidence=TRACKING_CONF,
    ) as holistic:
        for idx, (label, video_path) in enumerate(videos, start=1):
            row = audit_video(label, video_path, holistic)
            rows.append(row)
            status = "OK" if row["extraction_success"] else "FAIL"
            print(
                f"{idx:>3}/{len(videos)} {status:<4} {label:<9} "
                f"pose={row['pose_ratio']:.0%} hand={row['either_hand_ratio']:.0%} "
                f"frames={row['feature_frames']:<3} {video_path.name}",
                flush=True,
            )

    rows.extend(manual_review)
    per_label, labels_ready, labels_need_more, labels_under_10, labels_under_30, failure_counts = summarize(rows)
    payload = {
        "generated_at": datetime.now().isoformat(timespec="seconds"),
        "input_root": str(INPUT_ROOT),
        "feature_profile": configured_feature_profile(),
        "input_shape": [1, SEQ_LEN, FEAT_SIZE],
        "labels": TEAM16_LABELS,
        "thresholds": {
            "min_video_frames": MIN_VIDEO_FRAMES,
            "min_feature_frames": MIN_FEATURE_FRAMES,
            "min_pose_ratio": MIN_POSE_RATIO,
            "min_hand_ratio": MIN_HAND_RATIO,
            "ready_min_videos": READY_MIN_VIDEOS,
            "target_min_videos": TARGET_MIN_VIDEOS,
            "max_sampled_frames": MAX_SAMPLED_FRAMES,
        },
        "totals": {
            "videos": len(rows),
            "manual_review": len(manual_review),
            "extraction_success": sum(1 for row in rows if row["extraction_success"]),
            "failed_videos": sum(1 for row in rows if not row["extraction_success"]),
            "poor_tracking_videos": sum(1 for row in rows if row["poor_tracking"]),
        },
        "per_label": per_label,
        "labels_ready_for_training": labels_ready,
        "labels_needing_more_recordings": labels_need_more,
        "labels_under_10": labels_under_10,
        "labels_under_30": labels_under_30,
        "failure_reasons": failure_counts,
        "nothing_warning": "NOTHING only has 19 videos and should be expanded later.",
        "records": rows,
    }
    write_csv(rows)
    with open(JSON_OUT, "w", encoding="utf-8") as f:
        json.dump(payload, f, indent=2)
    write_markdown(payload)

    print("", flush=True)
    print(f"Wrote: {CSV_OUT}", flush=True)
    print(f"Wrote: {JSON_OUT}", flush=True)
    print(f"Wrote: {MD_OUT}", flush=True)
    print(f"Ready labels: {', '.join(labels_ready) if labels_ready else 'none'}", flush=True)
    print(f"Need more recordings: {', '.join(labels_need_more) if labels_need_more else 'none'}", flush=True)
    print("NOTE: NOTHING only has 19 videos and should be expanded later.", flush=True)


if __name__ == "__main__":
    main()
