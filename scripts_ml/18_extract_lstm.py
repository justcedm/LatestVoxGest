"""
VoxGest LSTM extractor.

Builds 30-frame, 162-feature word-sign sequences:
  99 pose floats + 63 dominant-hand floats, all nose-centric.

This script intentionally writes to dataset_words_lstm by default so the new
holistic LSTM data cannot be mixed with older 63-feature hand-only data.
"""

import json
import os
import sys
from pathlib import Path

import cv2
import mediapipe as mp
import numpy as np

from lstm_features import (
    FEAT_SIZE,
    SEQ_LEN,
    configured_hand_preference,
    configured_mirror_input,
    enforce_nose_anchor,
    extract_frame_features,
    single_hand_pose_enabled,
)
from word_config import TARGET_WORDS, WORD_PROFILE

os.environ["PROTOCOL_BUFFERS_PYTHON_IMPLEMENTATION"] = "python"
os.environ["TF_CPP_MIN_LOG_LEVEL"] = "3"

ROOT = Path(__file__).resolve().parents[1]
SAVE_DIR = Path(os.environ.get("VOXGEST_LSTM_DATASET", ROOT / "dataset_words_lstm"))
LOG_PATH = ROOT / "model" / "extraction_log_lstm.txt"
METADATA_PATH = SAVE_DIR / "metadata_lstm_v2.json"
PRESERVE_MANUAL = os.environ.get("VOXGEST_PRESERVE_MANUAL", "1").strip() != "0"
MIRROR_INPUT = False

VIDEO_DIRS = [
    ROOT / "wlasl_videos",
    Path(r"C:\wlasl_videos"),
]

MIN_FRAMES = 5
MIN_SOURCE_VIDEOS = 8
TARGET_SEQS = int(os.environ.get("VOXGEST_TARGET_SEQS_PER_WORD", "400"))
N_AUGMENTS = int(os.environ.get("VOXGEST_N_AUGMENTS_PER_SOURCE", "18"))
MIN_READY_SEQS = int(os.environ.get("VOXGEST_MIN_SEQS_PER_CLASS", "80"))
MIN_READY_GROUPS = int(os.environ.get("VOXGEST_MIN_GROUPS_PER_CLASS", "4"))
SKIP_READY_WORDS = os.environ.get("VOXGEST_SKIP_READY_WORDS", "0").strip().lower() in {
    "1",
    "true",
    "yes",
    "on",
}
DETECTION_CONF = 0.35
TRACKING_CONF = 0.30

VIDEO_EXTS = {".mp4", ".avi", ".mov", ".webm", ".mkv"}
mp_holistic = mp.solutions.holistic
ACTIVE_TARGET_WORDS = [arg.strip().upper() for arg in sys.argv[1:] if arg.strip()] or list(TARGET_WORDS)


def clean_word_folder(word_dir):
    if not word_dir.exists():
        return
    for file_path in word_dir.glob("*.npy"):
        if PRESERVE_MANUAL and file_path.name.startswith("manual_"):
            continue
        file_path.unlink()


def drop_replaced_metadata_for_word(metadata, word):
    prefix = f"{word}/"
    kept = {}
    for key, value in metadata.get("samples", {}).items():
        if not key.startswith(prefix):
            kept[key] = value
            continue
        if PRESERVE_MANUAL and Path(key).name.startswith("manual_"):
            kept[key] = value
    metadata["samples"] = kept


def infer_group_id(word, file_name, metadata):
    key = f"{word}/{file_name}"
    if key in metadata:
        return metadata[key].get("source_id", key)

    stem = Path(file_name).stem
    if "_aug" in stem:
        return f"{word}/{stem.rsplit('_aug', 1)[0]}"
    if "_seq" in stem and stem.startswith("manual_"):
        return f"{word}/{stem.rsplit('_seq', 1)[0]}"
    return f"{word}/{stem}"


def existing_ready_stats(word, metadata):
    word_dir = SAVE_DIR / word
    if not word_dir.exists():
        return 0, 0
    valid = 0
    groups = set()
    for file_path in sorted(word_dir.glob("*.npy")):
        try:
            shape = tuple(np.load(file_path, mmap_mode="r", allow_pickle=False).shape)
        except Exception:
            continue
        if shape != (SEQ_LEN, FEAT_SIZE):
            continue
        valid += 1
        groups.add(infer_group_id(word, file_path.name, metadata))
    return valid, len(groups)


def video_sources_for_word(word):
    sources = []
    seen = set()
    for root_idx, video_root in enumerate(VIDEO_DIRS):
        folder = video_root / word
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


def sanitize_stem(text):
    keep = []
    for ch in text:
        keep.append(ch if ch.isalnum() or ch in ("-", "_") else "_")
    return "".join(keep)[:80]


def aug_mirror(seq):
    out = seq.copy()
    out[:, 0::3] *= -1.0
    return enforce_nose_anchor(out)


def aug_jitter(seq, sigma=0.010):
    noise = np.random.normal(0.0, sigma, (FEAT_SIZE,)).astype(np.float32)
    noise[:3] = 0.0
    return enforce_nose_anchor(seq + noise[np.newaxis, :])


def aug_scale(seq, low=0.90, high=1.10):
    factor = float(np.random.uniform(low, high))
    return enforce_nose_anchor(seq * factor)


def aug_time_warp(seq):
    t_orig = np.linspace(0, SEQ_LEN - 1, SEQ_LEN)
    warp = np.cumsum(np.random.uniform(0.6, 1.4, SEQ_LEN))
    warp = (warp - warp[0]) / (warp[-1] - warp[0]) * (SEQ_LEN - 1)
    warped = np.zeros_like(seq)
    for i in range(FEAT_SIZE):
        warped[:, i] = np.interp(warp, t_orig, seq[:, i])
    return enforce_nose_anchor(warped)


def aug_y_rotation(seq, max_deg=7.0):
    angle = float(np.random.uniform(-max_deg, max_deg) * np.pi / 180.0)
    cos_a = float(np.cos(angle))
    sin_a = float(np.sin(angle))
    out = seq.copy().reshape(SEQ_LEN, -1, 3)
    x_new = out[:, :, 0] * cos_a - out[:, :, 2] * sin_a
    z_new = out[:, :, 0] * sin_a + out[:, :, 2] * cos_a
    out[:, :, 0] = x_new
    out[:, :, 2] = z_new
    return enforce_nose_anchor(out.reshape(SEQ_LEN, FEAT_SIZE))


def aug_x_rotation(seq, max_deg=10.0):
    angle = float(np.random.uniform(-max_deg, max_deg) * np.pi / 180.0)
    cos_a = float(np.cos(angle))
    sin_a = float(np.sin(angle))
    out = seq.copy().reshape(SEQ_LEN, -1, 3)
    y_new = out[:, :, 1] * cos_a - out[:, :, 2] * sin_a
    z_new = out[:, :, 1] * sin_a + out[:, :, 2] * cos_a
    out[:, :, 1] = y_new
    out[:, :, 2] = z_new
    return enforce_nose_anchor(out.reshape(SEQ_LEN, FEAT_SIZE))


def aug_z_rotation(seq, max_deg=8.0):
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
    augmentors = [
        lambda s: aug_jitter(s, 0.006),
        lambda s: aug_jitter(s, 0.012),
        aug_mirror,
        aug_scale,
        aug_time_warp,
        aug_y_rotation,
        aug_x_rotation,
        aug_z_rotation,
        lambda s: aug_jitter(aug_mirror(s), 0.008),
        lambda s: aug_time_warp(aug_jitter(s, 0.006)),
        lambda s: aug_y_rotation(aug_jitter(s, 0.006)),
        lambda s: aug_x_rotation(aug_jitter(s, 0.006)),
        lambda s: aug_z_rotation(aug_jitter(s, 0.006)),
        lambda s: aug_scale(aug_time_warp(s)),
        lambda s: aug_time_warp(aug_mirror(s)),
        lambda s: aug_y_rotation(aug_scale(s)),
        lambda s: aug_x_rotation(aug_scale(s)),
        lambda s: aug_z_rotation(aug_scale(s)),
        lambda s: aug_jitter(aug_scale(s), 0.008),
        lambda s: aug_time_warp(aug_y_rotation(s)),
        lambda s: aug_time_warp(aug_x_rotation(s)),
        lambda s: aug_time_warp(aug_z_rotation(s)),
        lambda s: aug_scale(aug_mirror(s)),
        lambda s: aug_jitter(aug_time_warp(aug_mirror(s)), 0.006),
        lambda s: aug_y_rotation(aug_mirror(s)),
        lambda s: aug_x_rotation(aug_mirror(s)),
        lambda s: aug_z_rotation(aug_mirror(s)),
        lambda s: aug_scale(aug_jitter(aug_y_rotation(s), 0.006)),
        lambda s: aug_scale(aug_jitter(aug_x_rotation(s), 0.006)),
        lambda s: aug_scale(aug_jitter(aug_z_rotation(s), 0.006)),
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
            if aug.shape == (SEQ_LEN, FEAT_SIZE):
                results.append(enforce_nose_anchor(aug))
        except Exception:
            pass
    return results


def extract_sequence_from_video(video_path, holistic):
    if not video_path.exists() or video_path.stat().st_size < 1024:
        return None

    cap = cv2.VideoCapture(str(video_path))
    total = int(cap.get(cv2.CAP_PROP_FRAME_COUNT))
    if total < 3:
        cap.release()
        return None

    step = max(1, total // (SEQ_LEN * 2))
    raw_frames = []

    for frame_idx in range(0, total, step):
        cap.set(cv2.CAP_PROP_POS_FRAMES, frame_idx)
        ok, frame = cap.read()
        if not ok:
            break
        try:
            rgb = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
            results = holistic.process(rgb)
            vec = extract_frame_features(results, mirrored_input=MIRROR_INPUT)
            if vec is not None:
                raw_frames.append(vec)
        except Exception:
            continue

    cap.release()

    if len(raw_frames) < MIN_FRAMES:
        return None

    n_frames = len(raw_frames)
    if n_frames >= SEQ_LEN:
        indices = np.linspace(0, n_frames - 1, SEQ_LEN, dtype=int)
        seq = np.array([raw_frames[i] for i in indices], dtype=np.float32)
    else:
        seq = np.array(raw_frames, dtype=np.float32)
        padding = np.tile(seq[-1], (SEQ_LEN - n_frames, 1))
        seq = np.vstack([seq, padding]).astype(np.float32)

    if seq.shape != (SEQ_LEN, FEAT_SIZE):
        return None
    return enforce_nose_anchor(seq)


def load_metadata():
    if not METADATA_PATH.exists():
        return {
            "version": 2,
            "seq_len": SEQ_LEN,
            "feature_size": FEAT_SIZE,
            "samples": {},
        }
    with open(METADATA_PATH, "r", encoding="utf-8") as f:
        return json.load(f)


def save_metadata(metadata):
    SAVE_DIR.mkdir(parents=True, exist_ok=True)
    with open(METADATA_PATH, "w", encoding="utf-8") as f:
        json.dump(metadata, f, indent=2)


def extract_word(word, holistic, log_file, metadata):
    word_dir = SAVE_DIR / word
    word_dir.mkdir(parents=True, exist_ok=True)
    if SKIP_READY_WORDS:
        existing_sequences, existing_groups = existing_ready_stats(word, metadata)
        if existing_sequences >= MIN_READY_SEQS and existing_groups >= MIN_READY_GROUPS:
            log_file.write(
                f"KEEP  {word:<15} existing_sequences={existing_sequences} "
                f"existing_groups={existing_groups}\n"
            )
            log_file.flush()
            return existing_sequences, existing_groups, 0

    drop_replaced_metadata_for_word(metadata, word)
    clean_word_folder(word_dir)

    sources = video_sources_for_word(word)
    if not sources:
        log_file.write(f"MISS  {word:<15} no videos found\n")
        log_file.flush()
        return 0, 0, 0

    saved = 0
    good = 0
    bad = 0

    for root_idx, video_path in sources:
        if saved >= TARGET_SEQS:
            break

        seq = extract_sequence_from_video(video_path, holistic)
        if seq is None:
            bad += 1
            log_file.write(f"SKIP  {word}/{video_path.name}\n")
            log_file.flush()
            continue

        good += 1
        source_stem = f"src{root_idx}_{sanitize_stem(video_path.stem)}"
        source_id = f"{word}/{source_stem}"

        for aug_idx, aug_seq in enumerate(augment_sequence(seq)):
            if saved >= TARGET_SEQS:
                break
            file_name = f"{source_stem}_aug{aug_idx:02d}.npy"
            out_path = word_dir / file_name
            np.save(out_path, aug_seq.astype(np.float32))
            metadata["samples"][f"{word}/{file_name}"] = {
                "word": word,
                "source_id": source_id,
                "source_video": str(video_path),
                "augment_index": aug_idx,
                "shape": [SEQ_LEN, FEAT_SIZE],
                "dominant_hand": configured_hand_preference(),
                "single_hand_pose": single_hand_pose_enabled(),
                "mirrored_input": MIRROR_INPUT,
            }
            saved += 1

    log_file.write(f"DONE  {word:<15} good={good} bad={bad} saved={saved}\n")
    log_file.flush()
    return saved, good, bad


def main():
    print("=" * 68)
    print("  VoxGest LSTM Extractor | 30 frames x 162 holistic features")
    print("=" * 68)
    print(f"  Dataset      : {SAVE_DIR}")
    print(f"  Video roots  : {[str(p) for p in VIDEO_DIRS if p.exists()]}")
    print(f"  Word profile : {WORD_PROFILE}")
    print(f"  Target words : {len(ACTIVE_TARGET_WORDS)}")
    print(f"  Target seqs  : {TARGET_SEQS}")
    print(f"  Augments/src : {N_AUGMENTS}")
    if SKIP_READY_WORDS:
        print("  Ready labels : skipped when they already pass trainer minimums")
    print(f"  Hand policy  : {configured_hand_preference()}")
    print(f"  Pose mask    : {'single-hand' if single_hand_pose_enabled() else 'full-pose'}")
    print(f"  Mirror input : {configured_mirror_input(MIRROR_INPUT)}")
    if PRESERVE_MANUAL:
        print("  Manual data  : preserved")
    print()

    existing_roots = [p for p in VIDEO_DIRS if p.exists()]
    if not existing_roots:
        print("ERROR: no video directory found. Run scripts_ml/13_download_target_words.py.")
        return

    (ROOT / "model").mkdir(exist_ok=True)
    SAVE_DIR.mkdir(parents=True, exist_ok=True)
    metadata = load_metadata()
    if not PRESERVE_MANUAL:
        metadata["samples"] = {}

    totals = {}
    with open(LOG_PATH, "w", encoding="utf-8") as log_file:
        log_file.write("VoxGest LSTM extraction\n")
        log_file.write(f"seq_len={SEQ_LEN} feature_size={FEAT_SIZE}\n")
        log_file.write("=" * 50 + "\n")

        with mp_holistic.Holistic(
            static_image_mode=False,
            model_complexity=1,
            min_detection_confidence=DETECTION_CONF,
            min_tracking_confidence=TRACKING_CONF,
        ) as holistic:
            for word in ACTIVE_TARGET_WORDS:
                saved, good, bad = extract_word(word, holistic, log_file, metadata)
                totals[word] = (saved, good, bad)
                status = "OK" if good >= MIN_SOURCE_VIDEOS else "LOW"
                print(
                    f"  {status:<3} {word:<12} sequences={saved:>4} "
                    f"source_ok={good:>2} skipped={bad:>2}",
                    flush=True,
                )

    save_metadata(metadata)

    ready = [w for w, (_, good, _) in totals.items() if good >= MIN_SOURCE_VIDEOS]
    low = [w for w, (_, good, _) in totals.items() if 0 < good < MIN_SOURCE_VIDEOS]
    failed = [w for w, (_, good, _) in totals.items() if good == 0]

    print("\n" + "=" * 68)
    print("  Extraction Summary")
    print("=" * 68)
    print(f"  Ready words       : {len(ready)} / {len(ACTIVE_TARGET_WORDS)}")
    print(f"  Total sequences   : {sum(v[0] for v in totals.values())}")
    print(f"  Metadata          : {METADATA_PATH}")
    print(f"  Log               : {LOG_PATH}")
    if low:
        print(f"  Low-source words  : {', '.join(low)}")
    if failed:
        print(f"  Failed words      : {', '.join(failed)}")
    print("\nNext:")
    print("  python scripts_ml/19_train_lstm.py")


if __name__ == "__main__":
    main()
