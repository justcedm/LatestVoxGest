"""
VoxGest data-sprint status helper.

Shows which target words are ready for retraining, which words need videos or
manual recording, and how many 60-sequence recording sessions are still needed.
"""

import json
import math
import os
import re
from pathlib import Path

import numpy as np

from lstm_features import FEAT_SIZE, SEQ_LEN, single_hand_pose_enabled
from word_config import TARGET_WORDS, TRAINING_WORDS, WORD_PROFILE

ROOT = Path(__file__).resolve().parents[1]
DATA_DIR = Path(os.environ.get("VOXGEST_LSTM_DATASET", ROOT / "dataset_words_lstm"))
VIDEO_DIR = ROOT / "wlasl_videos"
METADATA_PATH = DATA_DIR / "metadata_lstm_v2.json"
VIDEO_EXTS = {".mp4", ".avi", ".mov", ".webm", ".mkv"}

MIN_SEQS = int(os.environ.get("VOXGEST_MIN_SEQS_PER_CLASS", "80"))
MIN_GROUPS = int(os.environ.get("VOXGEST_MIN_GROUPS_PER_CLASS", "4"))
SESSION_SIZE = int(os.environ.get("VOXGEST_MANUAL_SEQUENCES_PER_WORD", "60"))
WORD_TARGET = int(os.environ.get("VOXGEST_SPRINT_TARGET_SEQS_PER_WORD", "240"))
MANUAL_WORD_TARGET = int(os.environ.get("VOXGEST_SPRINT_MANUAL_SEQS_PER_WORD", "240"))
NEGATIVE_TARGET = int(os.environ.get("VOXGEST_SPRINT_NEGATIVE_SEQS", "480"))
MIN_VIDEOS = int(os.environ.get("VOXGEST_SPRINT_MIN_VIDEOS", "15"))


def load_metadata():
    if not METADATA_PATH.exists():
        return {}
    with open(METADATA_PATH, "r", encoding="utf-8") as f:
        return json.load(f).get("samples", {})


def has_hand_policy_metadata(word, file_path, metadata):
    sample = metadata.get(f"{word}/{file_path.name}", {})
    return all(
        field in sample
        for field in ("dominant_hand", "mirrored_input", "single_hand_pose")
    )


def is_manual_sample(word, file_path, metadata):
    sample = metadata.get(f"{word}/{file_path.name}", {})
    return (
        file_path.name.startswith("manual_")
        or str(sample.get("source_video", "")) == "manual_webcam"
    )


def infer_group_id(word, file_path, metadata):
    key = f"{word}/{file_path.name}"
    if key in metadata:
        return metadata[key].get("source_id", key)

    stem = file_path.stem
    aug_match = re.match(r"(.+)_aug\d+$", stem)
    if aug_match:
        return f"{word}/{aug_match.group(1)}"

    manual_match = re.match(r"(manual_.+?)_seq\d+$", stem)
    if manual_match:
        return f"{word}/{manual_match.group(1)}"

    return f"{word}/{stem}"


def video_count(word):
    word_dir = VIDEO_DIR / word
    if not word_dir.exists():
        return 0
    return sum(1 for path in word_dir.iterdir() if path.suffix.lower() in VIDEO_EXTS)


def word_stats(word, metadata):
    word_dir = DATA_DIR / word
    stats = {
        "files": 0,
        "usable": 0,
        "groups": set(),
        "manual": 0,
        "manual_groups": set(),
        "skipped_meta": 0,
        "wrong_shape": 0,
        "videos": video_count(word),
    }
    if not word_dir.exists():
        return stats

    for file_path in sorted(word_dir.glob("*.npy")):
        stats["files"] += 1
        try:
            arr = np.load(file_path, allow_pickle=False)
        except Exception:
            continue

        if arr.shape != (SEQ_LEN, FEAT_SIZE):
            stats["wrong_shape"] += 1
            continue

        manual = is_manual_sample(word, file_path, metadata)
        if (
            single_hand_pose_enabled()
            and manual
            and not has_hand_policy_metadata(word, file_path, metadata)
        ):
            stats["skipped_meta"] += 1
            continue

        group_id = infer_group_id(word, file_path, metadata)
        stats["usable"] += 1
        stats["groups"].add(group_id)
        if manual:
            stats["manual"] += 1
            stats["manual_groups"].add(group_id)

    return stats


def sessions_needed(word, stats):
    target = NEGATIVE_TARGET if word == "NOTHING" else WORD_TARGET
    seq_need = max(0, target - stats["usable"])
    group_need = max(0, MIN_GROUPS - len(stats["groups"]))
    by_sequence = math.ceil(seq_need / max(1, SESSION_SIZE))
    return max(by_sequence, group_need)


def manual_sessions_needed(word, stats):
    target = NEGATIVE_TARGET if word == "NOTHING" else MANUAL_WORD_TARGET
    seq_need = max(0, target - stats["manual"])
    group_need = max(0, MIN_GROUPS - len(stats["manual_groups"]))
    by_sequence = math.ceil(seq_need / max(1, SESSION_SIZE))
    return max(by_sequence, group_need)


def readiness(stats):
    return stats["usable"] >= MIN_SEQS and len(stats["groups"]) >= MIN_GROUPS


def main():
    metadata = load_metadata()
    rows = []
    for word in TRAINING_WORDS:
        stats = word_stats(word, metadata)
        groups = len(stats["groups"])
        manual_groups = len(stats["manual_groups"])
        sessions = sessions_needed(word, stats)
        manual_sessions = manual_sessions_needed(word, stats)
        rows.append((word, stats, groups, manual_groups, sessions, manual_sessions))

    ready = sum(1 for _, stats, _, _, _, _ in rows if readiness(stats))

    print("=" * 88)
    print("  VoxGest Motion Data Sprint Status")
    print("=" * 88)
    print(f"  Profile       : {WORD_PROFILE}")
    print(f"  Target words  : {len(TARGET_WORDS)} + NOTHING")
    print(f"  Dataset       : {DATA_DIR}")
    print(f"  Word target   : {WORD_TARGET} usable sequences")
    print(f"  Manual target : {MANUAL_WORD_TARGET} webcam sequences per word")
    print(f"  NOTHING target: {NEGATIVE_TARGET} usable sequences")
    print()
    print(
        f"  {'Word':<14} {'Use':>5} {'Groups':>6} {'Manual':>6} "
        f"{'MGrp':>5} {'Videos':>6} {'MNeed':>5}  Status"
    )
    print("  " + "-" * 78)

    for word, stats, groups, manual_groups, _, manual_sessions in rows:
        status = "TRAIN_OK" if readiness(stats) else "DATA"
        if manual_sessions > 0:
            status += "/LIVE_REC"
        if word != "NOTHING" and stats["videos"] < MIN_VIDEOS:
            status += "/DL"
        if stats["skipped_meta"]:
            status += f" skip_meta={stats['skipped_meta']}"
        print(
            f"  {word:<14} {stats['usable']:>5} {groups:>6} {stats['manual']:>6} "
            f"{manual_groups:>5} {stats['videos']:>6} {manual_sessions:>5}  {status}"
        )

    print(f"\n  Ready for trainer minimum: {ready}/{len(rows)} classes")

    record_first = [
        (word, manual_sessions)
        for word, _, _, _, _, manual_sessions in rows
        if manual_sessions > 0
    ]
    if record_first:
        print("\n  Record next for live reliability:")
        for word, sessions in sorted(record_first, key=lambda item: (-item[1], item[0]))[:10]:
            print(f"  - {word}: {sessions} x {SESSION_SIZE}-sequence sessions")

    low_video = [
        word
        for word, stats, _, _, _, _ in rows
        if word != "NOTHING" and stats["videos"] < MIN_VIDEOS
    ]
    if low_video:
        print("\n  Need more source videos before extraction:")
        print("  " + " ".join(low_video))


if __name__ == "__main__":
    main()
