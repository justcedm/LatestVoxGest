"""
Phrase-intent video extractor.

Uses selected videos under phrase_videos/<LABEL>/ to build 60x162 phrase
sequences. This reduces manual recording fatigue, but curated videos should
still be followed by a smaller webcam calibration pass.

Expected folders:
  phrase_videos/ASK_NAME/*.mp4
  phrase_videos/NOTHING/*.mp4
  phrase_videos/PARTIAL_ASK_NAME/*.mp4

Examples:
  python scripts_ml/28_extract_phrase_videos.py ASK_NAME
  python scripts_ml/28_extract_phrase_videos.py ASK_NAME PARTIAL_ASK_NAME NOTHING
"""

import json
import os
import sys
from pathlib import Path

import cv2
import mediapipe as mp
import numpy as np

from gesture_segmenter import GestureSegmenter, resample_sequence
from lstm_features import (
    FEAT_SIZE,
    configured_hand_preference,
    configured_mirror_input,
    enforce_nose_anchor,
    extract_frame_features,
    single_hand_pose_enabled,
)
from phrase_config import (
    PHRASE_RECORDABLE_LABELS,
    PHRASE_SEQ_LEN,
    PHRASE_TRAINING_LABELS,
    normalize_phrase_label,
    phrase_output_text,
)

os.environ["PROTOCOL_BUFFERS_PYTHON_IMPLEMENTATION"] = "python"
os.environ["TF_CPP_MIN_LOG_LEVEL"] = "3"

ROOT = Path(__file__).resolve().parents[1]
SAVE_DIR = Path(os.environ.get("VOXGEST_PHRASE_DATASET", ROOT / "dataset_phrase_intents"))
METADATA_PATH = SAVE_DIR / "metadata_phrase_v1.json"
LOG_PATH = ROOT / "model" / "phrase_video_extraction_log.txt"

VIDEO_DIRS = [
    Path(os.environ.get("VOXGEST_PHRASE_VIDEO_DIR", ROOT / "phrase_videos")),
    Path(r"C:\phrase_videos"),
]
VIDEO_EXTS = {".mp4", ".avi", ".mov", ".webm", ".mkv"}

MIRROR_INPUT = os.environ.get("VOXGEST_PHRASE_VIDEO_MIRROR_INPUT", "0").strip() == "1"
PRESERVE_MANUAL = os.environ.get("VOXGEST_PRESERVE_MANUAL", "1").strip() != "0"
N_AUGMENTS = int(os.environ.get("VOXGEST_PHRASE_VIDEO_AUGMENTS", "6"))
TARGET_SEQS = int(os.environ.get("VOXGEST_PHRASE_VIDEO_TARGET_SEQS", "240"))
MIN_FRAMES = int(os.environ.get("VOXGEST_PHRASE_VIDEO_MIN_FRAMES", "18"))
TIMED_STRIDE = int(os.environ.get("VOXGEST_PHRASE_VIDEO_TIMED_STRIDE", "30"))
FALLBACK_WHOLE = os.environ.get("VOXGEST_PHRASE_VIDEO_FALLBACK_WHOLE", "1").strip() != "0"

mp_holistic = mp.solutions.holistic


def sanitize_stem(text):
    keep = []
    for ch in text:
        keep.append(ch if ch.isalnum() or ch in ("-", "_") else "_")
    return "".join(keep)[:80]


def load_metadata():
    if METADATA_PATH.exists():
        with open(METADATA_PATH, "r", encoding="utf-8") as f:
            return json.load(f)
    return {
        "version": 1,
        "seq_len": PHRASE_SEQ_LEN,
        "feature_size": FEAT_SIZE,
        "samples": {},
    }


def save_metadata(metadata):
    SAVE_DIR.mkdir(parents=True, exist_ok=True)
    with open(METADATA_PATH, "w", encoding="utf-8") as f:
        json.dump(metadata, f, indent=2)


def clean_label_folder(label_dir):
    if not label_dir.exists():
        return
    for file_path in label_dir.glob("*.npy"):
        if PRESERVE_MANUAL and file_path.name.startswith("manual_"):
            continue
        file_path.unlink()


def video_sources_for_label(label):
    sources = []
    seen = set()
    for root_idx, video_root in enumerate(VIDEO_DIRS):
        folder = video_root / label
        if not folder.exists():
            continue
        for file_path in sorted(folder.iterdir()):
            if file_path.suffix.lower() not in VIDEO_EXTS:
                continue
            key = str(file_path.resolve()).lower()
            if key in seen:
                continue
            seen.add(key)
            sources.append((root_idx, file_path))
    return sources


def read_feature_frames(video_path, holistic):
    if not video_path.exists() or video_path.stat().st_size < 1024:
        return []

    cap = cv2.VideoCapture(str(video_path))
    frames = []
    while True:
        ok, frame = cap.read()
        if not ok:
            break
        try:
            rgb = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
            results = holistic.process(rgb)
            vec = extract_frame_features(results, mirrored_input=MIRROR_INPUT)
            if vec is not None:
                frames.append(vec)
        except Exception:
            continue
    cap.release()
    return frames


def extract_endpoint_segments(frames):
    segmenter = GestureSegmenter()
    segments = []
    for vec in frames:
        segment = segmenter.update(vec)
        if segment is not None:
            segments.append(segment)
    if not segments and FALLBACK_WHOLE and len(frames) >= MIN_FRAMES:
        segments.append(resample_sequence(np.array(frames, dtype=np.float32), PHRASE_SEQ_LEN))
    return segments


def extract_timed_segments(frames):
    if len(frames) < MIN_FRAMES:
        return []
    if len(frames) < PHRASE_SEQ_LEN:
        return [resample_sequence(np.array(frames, dtype=np.float32), PHRASE_SEQ_LEN)]

    segments = []
    for start in range(0, len(frames) - PHRASE_SEQ_LEN + 1, max(1, TIMED_STRIDE)):
        window = np.array(frames[start:start + PHRASE_SEQ_LEN], dtype=np.float32)
        segments.append(enforce_nose_anchor(window))
    return segments


def segments_from_video(label, video_path, holistic):
    frames = read_feature_frames(video_path, holistic)
    if len(frames) < MIN_FRAMES:
        return []
    if label == "NOTHING":
        return extract_timed_segments(frames)
    return extract_endpoint_segments(frames)


def aug_jitter(seq, sigma=0.008):
    noise = np.random.normal(0.0, sigma, (FEAT_SIZE,)).astype(np.float32)
    noise[:3] = 0.0
    return enforce_nose_anchor(seq + noise[np.newaxis, :])


def aug_scale(seq, low=0.92, high=1.08):
    return enforce_nose_anchor(seq * float(np.random.uniform(low, high)))


def aug_time_warp(seq):
    t_orig = np.linspace(0, PHRASE_SEQ_LEN - 1, PHRASE_SEQ_LEN)
    warp = np.cumsum(np.random.uniform(0.75, 1.25, PHRASE_SEQ_LEN))
    warp = (warp - warp[0]) / (warp[-1] - warp[0]) * (PHRASE_SEQ_LEN - 1)
    warped = np.zeros_like(seq)
    for i in range(FEAT_SIZE):
        warped[:, i] = np.interp(warp, t_orig, seq[:, i])
    return enforce_nose_anchor(warped)


def aug_y_rotation(seq, max_deg=5.0):
    angle = float(np.random.uniform(-max_deg, max_deg) * np.pi / 180.0)
    cos_a = float(np.cos(angle))
    sin_a = float(np.sin(angle))
    out = seq.copy().reshape(PHRASE_SEQ_LEN, -1, 3)
    x_new = out[:, :, 0] * cos_a - out[:, :, 2] * sin_a
    z_new = out[:, :, 0] * sin_a + out[:, :, 2] * cos_a
    out[:, :, 0] = x_new
    out[:, :, 2] = z_new
    return enforce_nose_anchor(out.reshape(PHRASE_SEQ_LEN, FEAT_SIZE))


def augment_sequence(seq):
    augmentors = [
        lambda s: aug_jitter(s, 0.005),
        lambda s: aug_jitter(s, 0.010),
        aug_scale,
        aug_time_warp,
        aug_y_rotation,
        lambda s: aug_time_warp(aug_jitter(s, 0.006)),
        lambda s: aug_y_rotation(aug_scale(s)),
        lambda s: aug_scale(aug_time_warp(s)),
    ]
    results = [enforce_nose_anchor(seq)]
    if N_AUGMENTS <= 0:
        return results
    chosen = np.random.choice(
        len(augmentors),
        size=min(N_AUGMENTS, len(augmentors)),
        replace=False,
    )
    for idx in chosen:
        try:
            aug = augmentors[int(idx)](seq)
            if aug.shape == (PHRASE_SEQ_LEN, FEAT_SIZE):
                results.append(enforce_nose_anchor(aug))
        except Exception:
            pass
    return results


def save_sequence(label, source_id, source_video, source_stem, aug_idx, seq, metadata, saved):
    label_dir = SAVE_DIR / label
    label_dir.mkdir(parents=True, exist_ok=True)
    file_name = f"{source_stem}_aug{aug_idx:02d}_{saved:04d}.npy"
    out_path = label_dir / file_name
    np.save(out_path, seq.astype(np.float32))
    metadata["samples"][f"{label}/{file_name}"] = {
        "label": label,
        "phrase_text": phrase_output_text(label),
        "source_id": source_id,
        "source_video": str(source_video),
        "capture_mode": "video_timed" if label == "NOTHING" else "video_endpoint",
        "augment_index": aug_idx,
        "shape": [PHRASE_SEQ_LEN, FEAT_SIZE],
        "dominant_hand": configured_hand_preference(),
        "single_hand_pose": single_hand_pose_enabled(),
        "mirrored_input": MIRROR_INPUT,
    }


def extract_label(label, holistic, log_file, metadata):
    label_dir = SAVE_DIR / label
    label_dir.mkdir(parents=True, exist_ok=True)
    clean_label_folder(label_dir)

    sources = video_sources_for_label(label)
    if not sources:
        log_file.write(f"MISS  {label:<18} no videos found\n")
        return 0, 0, 0

    saved = 0
    good = 0
    bad = 0
    for root_idx, video_path in sources:
        if saved >= TARGET_SEQS:
            break
        segments = segments_from_video(label, video_path, holistic)
        if not segments:
            bad += 1
            log_file.write(f"SKIP  {label}/{video_path.name}\n")
            continue

        good += 1
        for seg_idx, segment in enumerate(segments):
            if saved >= TARGET_SEQS:
                break
            source_stem = f"src{root_idx}_{sanitize_stem(video_path.stem)}_seg{seg_idx:02d}"
            source_id = f"{label}/{source_stem}"
            for aug_idx, aug_seq in enumerate(augment_sequence(segment)):
                if saved >= TARGET_SEQS:
                    break
                save_sequence(
                    label,
                    source_id,
                    video_path,
                    source_stem,
                    aug_idx,
                    aug_seq,
                    metadata,
                    saved,
                )
                saved += 1

    log_file.write(f"DONE  {label:<18} good={good} bad={bad} saved={saved}\n")
    return saved, good, bad


def main():
    labels = [normalize_phrase_label(item) for item in sys.argv[1:]] or PHRASE_TRAINING_LABELS
    invalid = [label for label in labels if label not in PHRASE_RECORDABLE_LABELS]
    if invalid:
        print(f"Unknown phrase labels: {invalid}")
        print(f"Allowed: {PHRASE_RECORDABLE_LABELS}")
        return

    print("=" * 78)
    print("  VoxGest Phrase Video Extractor | 60 frames x 162 features")
    print("=" * 78)
    print(f"  Dataset      : {SAVE_DIR}")
    print(f"  Video roots  : {[str(path) for path in VIDEO_DIRS if path.exists()]}")
    print(f"  Labels       : {labels}")
    print(f"  Shape        : {PHRASE_SEQ_LEN} x {FEAT_SIZE}")
    print(f"  Target/label : {TARGET_SEQS}")
    print(f"  Augments     : {N_AUGMENTS}")
    print(f"  Hand policy  : {configured_hand_preference()}")
    print(f"  Pose mask    : {'single-hand' if single_hand_pose_enabled() else 'full-pose'}")
    print(f"  Mirror input : {configured_mirror_input(MIRROR_INPUT)}")
    if PRESERVE_MANUAL:
        print("  Manual data  : preserved")
    print()

    existing_roots = [path for path in VIDEO_DIRS if path.exists()]
    if not existing_roots:
        print("No phrase video directory found.")
        print("Create phrase_videos/<LABEL>/ and place selected .mp4 files there.")
        return

    (ROOT / "model").mkdir(exist_ok=True)
    SAVE_DIR.mkdir(parents=True, exist_ok=True)
    metadata = load_metadata()
    if PRESERVE_MANUAL:
        metadata["samples"] = {
            key: value
            for key, value in metadata.get("samples", {}).items()
            if Path(key).name.startswith("manual_")
        }
    else:
        metadata["samples"] = {}

    totals = {}
    with open(LOG_PATH, "w", encoding="utf-8") as log_file:
        log_file.write("VoxGest phrase video extraction\n")
        log_file.write(f"seq_len={PHRASE_SEQ_LEN} feature_size={FEAT_SIZE}\n")
        log_file.write("=" * 56 + "\n")

        with mp_holistic.Holistic(
            static_image_mode=False,
            model_complexity=1,
            min_detection_confidence=0.35,
            min_tracking_confidence=0.30,
        ) as holistic:
            for label in labels:
                saved, good, bad = extract_label(label, holistic, log_file, metadata)
                totals[label] = (saved, good, bad)
                status = "OK" if saved else "MISS"
                print(
                    f"  {status:<4} {label:<18} "
                    f"sequences={saved:>4} source_ok={good:>2} skipped={bad:>2}"
                )

    save_metadata(metadata)

    print("\n" + "=" * 78)
    print("  Phrase Video Extraction Summary")
    print("=" * 78)
    print(f"  Total sequences : {sum(item[0] for item in totals.values())}")
    print(f"  Metadata        : {METADATA_PATH}")
    print(f"  Log             : {LOG_PATH}")
    print("\nNext:")
    print("  python scripts_ml/26_record_phrase_intents.py ASK_NAME")
    print("  python scripts_ml/27_train_phrase_tcn.py")


if __name__ == "__main__":
    main()
