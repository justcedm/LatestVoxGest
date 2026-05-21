"""Prepare Anastacia-only FullSign225 manual5 seed features.

This copies valid teammate recorder .npy files into a clean seed feature folder
without deleting or modifying the incoming source folder.
"""

import csv
import json
import os
import shutil
from datetime import datetime
from pathlib import Path

os.environ.setdefault("VOXGEST_WORD_PROFILE", "fullsign225_manual5_anas_seed")
os.environ.setdefault("VOXGEST_FEATURE_PROFILE", "fullsign225")
os.environ.setdefault("VOXGEST_SINGLE_HAND_POSE", "0")
os.environ.setdefault("TF_CPP_MIN_LOG_LEVEL", "3")

import numpy as np

from lstm_features import FEAT_SIZE, SEQ_LEN, configured_feature_profile
from word_config import TRAINING_WORDS, WORD_PROFILE


ROOT = Path(__file__).resolve().parents[1]
SOURCE_DIR = ROOT / "external_datasets" / "team_incoming_recorded_features" / "ANASTACIA" / "recorded_features" / "fullsign225_manual5_team_features"
DEST_DIR = ROOT / "external_datasets" / "fullsign225_manual5_anas_seed_features"
SOURCE_METADATA = SOURCE_DIR / "metadata_lstm_v2.json"
DEST_METADATA = DEST_DIR / "metadata_lstm_v2.json"
REPORT_DIR = ROOT / "reports"
CSV_OUT = REPORT_DIR / "fullsign225_manual5_anas_seed_prepare.csv"
JSON_OUT = REPORT_DIR / "fullsign225_manual5_anas_seed_prepare.json"
MD_OUT = REPORT_DIR / "fullsign225_manual5_anas_seed_prepare.md"
EXPECTED_SHAPE = (SEQ_LEN, FEAT_SIZE)
SIGNER_ID = "ANASTACIA"


def load_source_metadata():
    if not SOURCE_METADATA.exists():
        return {}
    with open(SOURCE_METADATA, "r", encoding="utf-8") as f:
        return json.load(f).get("samples", {})


def safe_copy_name(label, index):
    return f"{SIGNER_ID}_{label}_{index:03d}.npy"


def inspect_shape(path):
    try:
        shape = tuple(np.load(path, mmap_mode="r", allow_pickle=False).shape)
    except Exception as exc:
        return None, str(exc)
    return shape, ""


def prepare_label(label, source_metadata):
    source_label_dir = SOURCE_DIR / label
    dest_label_dir = DEST_DIR / label
    dest_label_dir.mkdir(parents=True, exist_ok=True)
    rows = []
    valid_index = 0

    if not source_label_dir.exists():
        rows.append(
            {
                "label": label,
                "original_path": str(source_label_dir),
                "original_filename": "",
                "copied_path": "",
                "copied_filename": "",
                "shape": "",
                "status": "missing_label_folder",
                "reason": "source label folder does not exist",
            }
        )
        return rows

    for source_path in sorted(source_label_dir.glob("*.npy")):
        shape, error = inspect_shape(source_path)
        if shape != EXPECTED_SHAPE:
            rows.append(
                {
                    "label": label,
                    "original_path": str(source_path),
                    "original_filename": source_path.name,
                    "copied_path": "",
                    "copied_filename": "",
                    "shape": list(shape) if shape else "",
                    "status": "skipped",
                    "reason": error or f"shape={shape}, expected={EXPECTED_SHAPE}",
                }
            )
            continue

        valid_index += 1
        out_name = safe_copy_name(label, valid_index)
        out_path = dest_label_dir / out_name
        shutil.copy2(source_path, out_path)
        source_key = f"{label}/{source_path.name}"
        source_record = source_metadata.get(source_key, {})
        rows.append(
            {
                "label": label,
                "original_path": str(source_path),
                "original_filename": source_path.name,
                "copied_path": str(out_path),
                "copied_filename": out_name,
                "shape": list(shape),
                "status": "copied",
                "reason": "ok",
                "source_record": source_record,
            }
        )

    return rows


def build_metadata(rows):
    metadata = {
        "version": 2,
        "profile": WORD_PROFILE,
        "seq_len": SEQ_LEN,
        "feature_size": FEAT_SIZE,
        "feature_profile": configured_feature_profile(),
        "source_dataset": "anastacia_teammate_recorder_seed",
        "source_root": str(SOURCE_DIR),
        "signer_id": SIGNER_ID,
        "samples": {},
    }
    for row in rows:
        if row["status"] != "copied":
            continue
        key = f"{row['label']}/{row['copied_filename']}"
        source_record = row.get("source_record", {})
        metadata["samples"][key] = {
            "word": row["label"],
            "signer_id": SIGNER_ID,
            "source_id": f"{row['label']}/{SIGNER_ID}_seed",
            "source_video": "teammate_webcam_fullsign225",
            "source_path": row["original_path"],
            "source_filename": row["original_filename"],
            "shape": [SEQ_LEN, FEAT_SIZE],
            "feature_profile": configured_feature_profile(),
            "dominant_hand": source_record.get("dominant_hand", "both_fixed_slots"),
            "single_hand_pose": False,
            "mirrored_input": source_record.get("mirrored_input", True),
            "quality_status": source_record.get("quality_status", "UNKNOWN"),
            "quality_reason": source_record.get("quality_reason", "copied from teammate recorder"),
        }
    return metadata


def write_csv(rows):
    REPORT_DIR.mkdir(parents=True, exist_ok=True)
    fields = [
        "label",
        "original_path",
        "original_filename",
        "copied_path",
        "copied_filename",
        "shape",
        "status",
        "reason",
    ]
    with open(CSV_OUT, "w", encoding="utf-8", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=fields)
        writer.writeheader()
        for row in rows:
            writer.writerow({field: row.get(field, "") for field in fields})


def write_markdown(payload):
    lines = [
        "# FullSign225 Anastacia Seed Prepare Report",
        "",
        f"Generated: {payload['generated_at']}",
        f"Profile: `{payload['profile']}`",
        f"Source: `{payload['source']}`",
        f"Destination: `{payload['destination']}`",
        f"Expected shape: `{payload['expected_shape']}`",
        "",
        "## Summary",
        "",
        f"- Copied valid samples: {payload['totals']['copied']}",
        f"- Skipped files: {payload['totals']['skipped']}",
        f"- Missing label folders: {', '.join(payload['missing_label_folders']) if payload['missing_label_folders'] else 'none'}",
        "",
        "## Per Label",
        "",
        "| Label | Copied | Skipped |",
        "| --- | ---: | ---: |",
    ]
    for label in TRAINING_WORDS:
        stats = payload["per_label"].get(label, {"copied": 0, "skipped": 0})
        lines.append(f"| {label} | {stats['copied']} | {stats['skipped']} |")
    lines.extend(
        [
            "",
            "This is a seed/spoiler dataset prepared from Anastacia's accepted recorder output only. It is not the final team dataset.",
        ]
    )
    with open(MD_OUT, "w", encoding="utf-8") as f:
        f.write("\n".join(lines) + "\n")


def main():
    if configured_feature_profile() != "fullsign225" or FEAT_SIZE != 225:
        raise RuntimeError(f"Expected fullsign225/225 features, got {configured_feature_profile()}/{FEAT_SIZE}")
    if not SOURCE_DIR.exists():
        raise FileNotFoundError(f"Source dataset not found: {SOURCE_DIR}")

    source_metadata = load_source_metadata()
    all_rows = []
    for label in TRAINING_WORDS:
        all_rows.extend(prepare_label(label, source_metadata))

    metadata = build_metadata(all_rows)
    DEST_DIR.mkdir(parents=True, exist_ok=True)
    with open(DEST_METADATA, "w", encoding="utf-8") as f:
        json.dump(metadata, f, indent=2)

    per_label = {}
    missing_label_folders = []
    for label in TRAINING_WORDS:
        rows = [row for row in all_rows if row["label"] == label]
        per_label[label] = {
            "copied": sum(1 for row in rows if row["status"] == "copied"),
            "skipped": sum(1 for row in rows if row["status"] == "skipped"),
        }
        if any(row["status"] == "missing_label_folder" for row in rows):
            missing_label_folders.append(label)

    payload = {
        "generated_at": datetime.now().isoformat(timespec="seconds"),
        "profile": WORD_PROFILE,
        "source": str(SOURCE_DIR),
        "destination": str(DEST_DIR),
        "metadata": str(DEST_METADATA),
        "expected_shape": [SEQ_LEN, FEAT_SIZE],
        "training_words": TRAINING_WORDS,
        "per_label": per_label,
        "missing_label_folders": missing_label_folders,
        "totals": {
            "copied": sum(1 for row in all_rows if row["status"] == "copied"),
            "skipped": sum(1 for row in all_rows if row["status"] == "skipped"),
        },
        "records": [
            {key: value for key, value in row.items() if key != "source_record"}
            for row in all_rows
        ],
    }

    write_csv(all_rows)
    REPORT_DIR.mkdir(parents=True, exist_ok=True)
    with open(JSON_OUT, "w", encoding="utf-8") as f:
        json.dump(payload, f, indent=2)
    write_markdown(payload)

    print("=" * 72)
    print("FullSign225 Anastacia seed prepare")
    print("=" * 72)
    print(f"Source      : {SOURCE_DIR}")
    print(f"Destination : {DEST_DIR}")
    print(f"Copied      : {payload['totals']['copied']}")
    print(f"Skipped     : {payload['totals']['skipped']}")
    print(f"Wrote: {CSV_OUT}")
    print(f"Wrote: {JSON_OUT}")
    print(f"Wrote: {MD_OUT}")


if __name__ == "__main__":
    main()
