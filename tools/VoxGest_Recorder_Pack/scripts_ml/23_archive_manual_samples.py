"""
Archive manual LSTM samples for selected words without deleting them.

Use this when a word was recorded with the wrong signing style, hand policy, or
camera setup. The files are moved under dataset_words_lstm/_archived_manual and
their metadata entries are removed so the next training run will ignore them.
"""

import argparse
import json
import os
import shutil
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DATA_DIR = Path(os.environ.get("VOXGEST_LSTM_DATASET", ROOT / "dataset_words_lstm"))
METADATA_PATH = DATA_DIR / "metadata_lstm_v2.json"
ARCHIVE_ROOT = DATA_DIR / "_archived_manual"


def load_metadata():
    if not METADATA_PATH.exists():
        return {"version": 2, "samples": {}}
    with open(METADATA_PATH, "r", encoding="utf-8") as f:
        return json.load(f)


def save_metadata(metadata):
    with open(METADATA_PATH, "w", encoding="utf-8") as f:
        json.dump(metadata, f, indent=2)


def has_hand_metadata(word, file_path, metadata):
    sample = metadata.get("samples", {}).get(f"{word}/{file_path.name}", {})
    return (
        "dominant_hand" in sample
        and "mirrored_input" in sample
        and "single_hand_pose" in sample
    )


def manual_files_for(word, metadata, latest_groups=None, missing_hand_metadata=False):
    word_dir = DATA_DIR / word
    if not word_dir.exists():
        return []
    files = sorted(word_dir.glob("manual_*.npy"))
    if missing_hand_metadata:
        files = [
            file_path
            for file_path in files
            if not has_hand_metadata(word, file_path, metadata)
        ]
    if not latest_groups:
        return files

    grouped = {}
    for file_path in files:
        key = f"{word}/{file_path.name}"
        source_id = metadata.get("samples", {}).get(key, {}).get("source_id")
        if not source_id:
            source_id = file_path.stem.rsplit("_seq", 1)[0]
        grouped.setdefault(str(source_id), []).append(file_path)

    latest = sorted(grouped)[-latest_groups:]
    selected = []
    for source_id in latest:
        selected.extend(grouped[source_id])
    return sorted(selected)


def archive_word(
    word,
    archive_dir,
    metadata,
    apply,
    latest_groups=None,
    missing_hand_metadata=False,
):
    files = manual_files_for(
        word,
        metadata,
        latest_groups,
        missing_hand_metadata=missing_hand_metadata,
    )
    if not files:
        print(f"  {word:<12} no manual samples found")
        return 0

    suffix_parts = []
    if latest_groups:
        suffix_parts.append(f"latest_groups={latest_groups}")
    if missing_hand_metadata:
        suffix_parts.append("missing_hand_metadata")
    suffix = f" {' '.join(suffix_parts)}" if suffix_parts else ""
    print(f"  {word:<12} manual samples: {len(files)}{suffix}")
    if not apply:
        return len(files)

    out_dir = archive_dir / word
    out_dir.mkdir(parents=True, exist_ok=True)

    for file_path in files:
        shutil.move(str(file_path), str(out_dir / file_path.name))
        metadata.get("samples", {}).pop(f"{word}/{file_path.name}", None)

    return len(files)


def main():
    parser = argparse.ArgumentParser(description="Archive manual LSTM samples.")
    parser.add_argument("words", nargs="+", help="Words to archive, e.g. DOCTOR WATER")
    parser.add_argument(
        "--apply",
        action="store_true",
        help="Actually move files. Without this flag the script only previews.",
    )
    parser.add_argument(
        "--latest-groups",
        type=int,
        default=0,
        help="Archive only the N newest manual recording groups for each word.",
    )
    parser.add_argument(
        "--missing-hand-metadata",
        action="store_true",
        help="Archive only manual samples missing dominant-hand/mirror metadata.",
    )
    args = parser.parse_args()

    words = [word.upper() for word in args.words]
    stamp = time.strftime("%Y%m%d_%H%M%S")
    archive_dir = ARCHIVE_ROOT / stamp
    metadata = load_metadata()

    print("=" * 68)
    print("  VoxGest Manual Sample Archiver")
    print("=" * 68)
    print(f"  Dataset : {DATA_DIR}")
    print(f"  Archive : {archive_dir}")
    print(f"  Mode    : {'APPLY' if args.apply else 'DRY RUN'}")
    print()

    total = 0
    for word in words:
        total += archive_word(
            word,
            archive_dir,
            metadata,
            args.apply,
            latest_groups=args.latest_groups,
            missing_hand_metadata=args.missing_hand_metadata,
        )

    if args.apply:
        save_metadata(metadata)
        print(f"\nArchived {total} manual samples.")
        print("Retrain after recording clean replacement samples.")
    else:
        print(f"\nDry run found {total} manual samples.")
        print("Run again with --apply to archive them.")


if __name__ == "__main__":
    main()
