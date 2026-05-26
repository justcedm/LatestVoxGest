"""Extract FullSign225 Team16 video features into a separate dataset folder.

This script consumes only the normalized Team16 videos that passed the prior
readability/MediaPipe audit. It writes 30 x 225 arrays under
external_datasets/fullsign225_team16_features and never touches demo10,
onehand162, dataset_words_lstm, or raw videos.
"""

import csv
import json
import os
import shutil
from collections import defaultdict
from datetime import datetime
from pathlib import Path

os.environ.setdefault("VOXGEST_WORD_PROFILE", "fullsign225_team16")
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
OUTPUT_ROOT = Path(
    os.environ.get(
        "VOXGEST_FULLSIGN225_TEAM16_FEATURES",
        ROOT / "external_datasets" / "fullsign225_team16_features",
    )
)
AUDIT_JSON = Path(
    os.environ.get(
        "VOXGEST_FULLSIGN225_TEAM16_AUDIT_JSON",
        ROOT / "reports" / "fullsign225_team16_extraction_audit.json",
    )
)
REPORT_DIR = ROOT / "reports"
CSV_OUT = REPORT_DIR / "fullsign225_team16_feature_extraction.csv"
JSON_OUT = REPORT_DIR / "fullsign225_team16_feature_extraction.json"
MD_OUT = REPORT_DIR / "fullsign225_team16_feature_extraction_summary.md"
METADATA_OUT = OUTPUT_ROOT / "metadata_lstm_v2.json"

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

SKIP_FILE_NAMES = {
    "NOTHING_fullsign225_011.mp4",
    "NOTHING_fullsign225_018.mp4",
}
VIDEO_EXTENSIONS = {".mp4"}
MAX_SAMPLED_FRAMES = int(os.environ.get("VOXGEST_FULLSIGN225_EXTRACT_MAX_FRAMES", "60"))
MIN_FEATURE_FRAMES = int(os.environ.get("VOXGEST_FULLSIGN225_EXTRACT_MIN_FEATURE_FRAMES", "8"))
N_AUGMENTS = int(os.environ.get("VOXGEST_FULLSIGN225_AUGMENTS_PER_VIDEO", "8"))
DETECTION_CONF = float(os.environ.get("VOXGEST_FULLSIGN225_DETECTION_CONF", "0.45"))
TRACKING_CONF = float(os.environ.get("VOXGEST_FULLSIGN225_TRACKING_CONF", "0.40"))
EXPECTED_SHAPE = (SEQ_LEN, FEAT_SIZE)


def label_key(value):
    return "".join(ch for ch in str(value).upper() if ch.isalnum())


LABEL_BY_KEY = {label_key(label): label for label in TEAM16_LABELS}


def safe_clear_output():
    if not OUTPUT_ROOT.exists():
        return
    resolved = OUTPUT_ROOT.resolve()
    allowed = (ROOT / "external_datasets").resolve()
    if allowed not in resolved.parents:
        raise RuntimeError(f"Refusing to clear unexpected output folder: {resolved}")
    shutil.rmtree(resolved)


def load_passing_audit_records():
    if not AUDIT_JSON.exists():
        return None
    with open(AUDIT_JSON, "r", encoding="utf-8") as f:
        payload = json.load(f)
    records = []
    for row in payload.get("records", []):
        if not row.get("extraction_success"):
            continue
        file_name = str(row.get("file_name", ""))
        if file_name in SKIP_FILE_NAMES:
            continue
        label = str(row.get("label", "")).upper()
        path = Path(row.get("video_path", ""))
        if label not in TEAM16_LABELS or not path.exists():
            continue
        records.append((label, path, row))
    return records


def discover_videos():
    audited = load_passing_audit_records()
    if audited is not None:
        return sorted(audited, key=lambda item: (TEAM16_LABELS.index(item[0]), str(item[1]).casefold()))

    videos = []
    for path in sorted(INPUT_ROOT.rglob("*"), key=lambda item: str(item).casefold()):
        if not path.is_file() or path.suffix.lower() not in VIDEO_EXTENSIONS:
            continue
        if path.name in SKIP_FILE_NAMES:
            continue
        label = LABEL_BY_KEY.get(label_key(path.parent.name))
        if label is None:
            continue
        videos.append((label, path, {}))
    return sorted(videos, key=lambda item: (TEAM16_LABELS.index(item[0]), str(item[1]).casefold()))


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


def aug_jitter(seq, sigma=0.008):
    noise = np.random.normal(0.0, sigma, (FEAT_SIZE,)).astype(np.float32)
    noise[:3] = 0.0
    return enforce_nose_anchor(seq + noise[np.newaxis, :])


def aug_scale(seq, low=0.94, high=1.06):
    factor = float(np.random.uniform(low, high))
    return enforce_nose_anchor(seq * factor)


def aug_time_warp(seq):
    t_orig = np.linspace(0, SEQ_LEN - 1, SEQ_LEN)
    warp = np.cumsum(np.random.uniform(0.75, 1.25, SEQ_LEN))
    warp = (warp - warp[0]) / (warp[-1] - warp[0]) * (SEQ_LEN - 1)
    warped = np.zeros_like(seq)
    for i in range(FEAT_SIZE):
        warped[:, i] = np.interp(warp, t_orig, seq[:, i])
    return enforce_nose_anchor(warped)


def aug_y_rotation(seq, max_deg=5.0):
    angle = float(np.random.uniform(-max_deg, max_deg) * np.pi / 180.0)
    cos_a = float(np.cos(angle))
    sin_a = float(np.sin(angle))
    out = seq.copy().reshape(SEQ_LEN, -1, 3)
    x_new = out[:, :, 0] * cos_a - out[:, :, 2] * sin_a
    z_new = out[:, :, 0] * sin_a + out[:, :, 2] * cos_a
    out[:, :, 0] = x_new
    out[:, :, 2] = z_new
    return enforce_nose_anchor(out.reshape(SEQ_LEN, FEAT_SIZE))


def aug_x_rotation(seq, max_deg=5.0):
    angle = float(np.random.uniform(-max_deg, max_deg) * np.pi / 180.0)
    cos_a = float(np.cos(angle))
    sin_a = float(np.sin(angle))
    out = seq.copy().reshape(SEQ_LEN, -1, 3)
    y_new = out[:, :, 1] * cos_a - out[:, :, 2] * sin_a
    z_new = out[:, :, 1] * sin_a + out[:, :, 2] * cos_a
    out[:, :, 1] = y_new
    out[:, :, 2] = z_new
    return enforce_nose_anchor(out.reshape(SEQ_LEN, FEAT_SIZE))


def aug_z_rotation(seq, max_deg=4.0):
    angle = float(np.random.uniform(-max_deg, max_deg) * np.pi / 180.0)
    cos_a = float(np.cos(angle))
    sin_a = float(np.sin(angle))
    out = seq.copy().reshape(SEQ_LEN, -1, 3)
    x_new = out[:, :, 0] * cos_a - out[:, :, 1] * sin_a
    y_new = out[:, :, 0] * sin_a + out[:, :, 1] * cos_a
    out[:, :, 0] = x_new
    out[:, :, 1] = y_new
    return enforce_nose_anchor(out.reshape(SEQ_LEN, FEAT_SIZE))


def augment_sequence(seq):
    # No mirror augmentation here: FullSign225 uses fixed left/right hand slots.
    augmentors = [
        lambda s: aug_jitter(s, 0.005),
        lambda s: aug_jitter(s, 0.010),
        aug_scale,
        aug_time_warp,
        aug_y_rotation,
        aug_x_rotation,
        aug_z_rotation,
        lambda s: aug_time_warp(aug_jitter(s, 0.006)),
        lambda s: aug_scale(aug_time_warp(s)),
        lambda s: aug_y_rotation(aug_jitter(s, 0.006)),
        lambda s: aug_x_rotation(aug_jitter(s, 0.006)),
        lambda s: aug_z_rotation(aug_jitter(s, 0.006)),
    ]
    results = [enforce_nose_anchor(seq)]
    chosen = np.random.choice(
        len(augmentors),
        size=min(N_AUGMENTS, len(augmentors)),
        replace=False,
    )
    for idx in chosen:
        try:
            aug = augmentors[int(idx)](seq)
            if aug.shape == EXPECTED_SHAPE:
                results.append(enforce_nose_anchor(aug))
        except Exception:
            continue
    return results


def extract_sequence_from_video(video_path, holistic):
    cap = cv2.VideoCapture(str(video_path))
    if not cap.isOpened():
        cap.release()
        return None, {"failure_reason": "opencv_open_failed", "feature_frames": 0}

    frame_count = int(cap.get(cv2.CAP_PROP_FRAME_COUNT) or 0)
    feature_frames = []
    for idx in sample_indices(frame_count):
        cap.set(cv2.CAP_PROP_POS_FRAMES, idx)
        ok, frame = cap.read()
        if not ok or frame is None:
            continue
        try:
            rgb = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
            results = holistic.process(rgb)
            vec = extract_frame_features(results, mirrored_input=False)
        except Exception:
            continue
        if vec is not None and vec.shape == (FEAT_SIZE,):
            feature_frames.append(vec)
    cap.release()

    seq = build_sequence(feature_frames)
    if seq is None:
        return None, {
            "failure_reason": f"feature_extraction_failed_frames={len(feature_frames)}",
            "feature_frames": len(feature_frames),
        }
    return seq, {
        "failure_reason": "ok",
        "feature_frames": len(feature_frames),
        "motion": round(sequence_motion_energy(seq), 6),
        "wrist_path": round(sequence_wrist_path(seq), 6),
        "hand_presence_ratio": round(sequence_hand_presence_ratio(seq), 6),
    }


def empty_metadata():
    return {
        "version": 2,
        "profile": "fullsign225_team16",
        "seq_len": SEQ_LEN,
        "feature_size": FEAT_SIZE,
        "feature_profile": configured_feature_profile(),
        "source_dataset": str(INPUT_ROOT),
        "generated_at": datetime.now().isoformat(timespec="seconds"),
        "samples": {},
    }


def write_reports(rows, per_label, skipped_files):
    REPORT_DIR.mkdir(parents=True, exist_ok=True)
    fields = [
        "label",
        "source_video",
        "saved_sequences",
        "source_group",
        "feature_frames",
        "motion",
        "wrist_path",
        "hand_presence_ratio",
        "status",
        "failure_reason",
    ]
    with open(CSV_OUT, "w", encoding="utf-8", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=fields)
        writer.writeheader()
        writer.writerows(rows)

    payload = {
        "generated_at": datetime.now().isoformat(timespec="seconds"),
        "profile": "fullsign225_team16",
        "feature_profile": configured_feature_profile(),
        "input_shape": [1, SEQ_LEN, FEAT_SIZE],
        "input_root": str(INPUT_ROOT),
        "output_root": str(OUTPUT_ROOT),
        "audit_json": str(AUDIT_JSON),
        "augmentations_per_video": N_AUGMENTS,
        "videos_attempted": len(rows),
        "videos_extracted": sum(1 for row in rows if row["status"] == "ok"),
        "total_sequences": sum(int(row["saved_sequences"]) for row in rows),
        "skipped_files": skipped_files,
        "per_label": per_label,
        "records": rows,
    }
    with open(JSON_OUT, "w", encoding="utf-8") as f:
        json.dump(payload, f, indent=2)

    lines = [
        "# FullSign225 Team16 Feature Extraction",
        "",
        f"Generated: {payload['generated_at']}",
        f"Input: `{INPUT_ROOT}`",
        f"Output: `{OUTPUT_ROOT}`",
        f"Feature profile: `{configured_feature_profile()}`",
        f"Input shape target: `{payload['input_shape']}`",
        f"Videos extracted: {payload['videos_extracted']} / {payload['videos_attempted']}",
        f"Total sequences: {payload['total_sequences']}",
        f"Augmentations per passing video: {N_AUGMENTS}",
        "",
        "## Per Label",
        "",
        "| Label | Videos | Sequences | Groups | Status |",
        "| --- | ---: | ---: | ---: | --- |",
    ]
    for label in TEAM16_LABELS:
        stats = per_label[label]
        status = "ready" if stats["videos"] >= 10 and stats["sequences"] >= 80 else "weak"
        lines.append(
            f"| {label} | {stats['videos']} | {stats['sequences']} | "
            f"{stats['groups']} | {status} |"
        )
    lines.extend(
        [
            "",
            "## Skipped Videos",
            "",
        ]
    )
    for item in skipped_files:
        lines.append(f"- {item}")
    lines.extend(
        [
            "",
            "## Notes",
            "",
            "The two failed NOTHING videos from the audit are skipped. This dataset is experimental and does not replace demo10 or onehand162.",
        ]
    )
    with open(MD_OUT, "w", encoding="utf-8") as f:
        f.write("\n".join(lines) + "\n")


def main():
    if configured_feature_profile() != "fullsign225" or FEAT_SIZE != 225:
        raise RuntimeError(f"Expected fullsign225/225 features, got {configured_feature_profile()}/{FEAT_SIZE}")
    if not INPUT_ROOT.exists():
        raise FileNotFoundError(f"Missing normalized dataset root: {INPUT_ROOT}")
    if os.environ.get("VOXGEST_CLEAR_FULLSIGN225_TEAM16_FEATURES", "0").strip() == "1":
        safe_clear_output()

    OUTPUT_ROOT.mkdir(parents=True, exist_ok=True)
    for label in TEAM16_LABELS:
        (OUTPUT_ROOT / label).mkdir(parents=True, exist_ok=True)

    videos = discover_videos()
    if not videos:
        raise RuntimeError("No passing Team16 videos found. Run the extraction audit first.")

    metadata = empty_metadata()
    rows = []
    per_label = {
        label: {"videos": 0, "sequences": 0, "groups": 0, "failed": 0}
        for label in TEAM16_LABELS
    }
    label_groups = defaultdict(set)
    skipped_files = sorted(SKIP_FILE_NAMES)

    print("=" * 78, flush=True)
    print("VoxGest FullSign225 Team16 feature extraction", flush=True)
    print("=" * 78, flush=True)
    print(f"Input root : {INPUT_ROOT}", flush=True)
    print(f"Output root: {OUTPUT_ROOT}", flush=True)
    print(f"Videos     : {len(videos)}", flush=True)
    print(f"Feature    : {configured_feature_profile()} ({FEAT_SIZE})", flush=True)
    print(f"Augments   : {N_AUGMENTS} per video, no mirror augmentation", flush=True)
    print("", flush=True)

    with mp.solutions.holistic.Holistic(
        static_image_mode=False,
        model_complexity=1,
        min_detection_confidence=DETECTION_CONF,
        min_tracking_confidence=TRACKING_CONF,
    ) as holistic:
        for idx, (label, video_path, audit_row) in enumerate(videos, start=1):
            seq, extract_info = extract_sequence_from_video(video_path, holistic)
            source_stem = video_path.stem
            source_id = f"{label}/{source_stem}"
            row = {
                "label": label,
                "source_video": str(video_path),
                "saved_sequences": 0,
                "source_group": source_id,
                "feature_frames": extract_info.get("feature_frames", 0),
                "motion": extract_info.get("motion", 0.0),
                "wrist_path": extract_info.get("wrist_path", 0.0),
                "hand_presence_ratio": extract_info.get("hand_presence_ratio", 0.0),
                "status": "failed",
                "failure_reason": extract_info.get("failure_reason", "unknown"),
            }
            if seq is None:
                per_label[label]["failed"] += 1
                rows.append(row)
                print(f"{idx:>3}/{len(videos)} FAIL {label:<9} {video_path.name}", flush=True)
                continue

            augmented = augment_sequence(seq)
            for aug_idx, aug_seq in enumerate(augmented):
                file_name = f"{source_stem}_aug{aug_idx:02d}.npy"
                out_path = OUTPUT_ROOT / label / file_name
                np.save(out_path, aug_seq.astype(np.float32))
                metadata["samples"][f"{label}/{file_name}"] = {
                    "word": label,
                    "source_id": source_id,
                    "source_video": str(video_path),
                    "source_dataset": "fullsign225_team_dataset_normalized",
                    "audit_passed": True,
                    "audit_failure_reason": audit_row.get("failure_reason", "ok"),
                    "augment_index": aug_idx,
                    "shape": [SEQ_LEN, FEAT_SIZE],
                    "feature_profile": configured_feature_profile(),
                    "dominant_hand": "both_fixed_slots",
                    "single_hand_pose": False,
                    "mirrored_input": False,
                }

            row["saved_sequences"] = len(augmented)
            row["status"] = "ok"
            row["failure_reason"] = "ok"
            rows.append(row)
            per_label[label]["videos"] += 1
            per_label[label]["sequences"] += len(augmented)
            label_groups[label].add(source_id)
            print(
                f"{idx:>3}/{len(videos)} OK   {label:<9} seqs={len(augmented):>2} "
                f"frames={row['feature_frames']:<3} {video_path.name}",
                flush=True,
            )

    for label in TEAM16_LABELS:
        per_label[label]["groups"] = len(label_groups[label])

    with open(METADATA_OUT, "w", encoding="utf-8") as f:
        json.dump(metadata, f, indent=2)
    write_reports(rows, per_label, skipped_files)

    print("", flush=True)
    print(f"Wrote metadata: {METADATA_OUT}", flush=True)
    print(f"Wrote report  : {JSON_OUT}", flush=True)
    print(f"Wrote report  : {CSV_OUT}", flush=True)
    print(f"Wrote report  : {MD_OUT}", flush=True)
    print(f"Total sequences: {sum(stats['sequences'] for stats in per_label.values())}", flush=True)


if __name__ == "__main__":
    main()
