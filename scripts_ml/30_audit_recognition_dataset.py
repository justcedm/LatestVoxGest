"""Audit VoxGest Recognition Hardening v1 word data.

Outputs:
  reports/recognition_audit_words.json
  reports/recognition_audit_words.csv
"""

import csv
import json
import os
import re
from datetime import datetime
from pathlib import Path

import numpy as np

from lstm_features import FEAT_SIZE, SEQ_LEN, single_hand_pose_enabled
from word_config import NEGATIVE_WORDS, TARGET_WORDS, TRAINING_WORDS, WORD_PROFILE


ROOT = Path(__file__).resolve().parents[1]
DATA_DIR = Path(os.environ.get("VOXGEST_LSTM_DATASET", ROOT / "dataset_words_lstm"))
REPORT_DIR = ROOT / "reports"
PROFILE_PREFIX = (
    "recognition"
    if WORD_PROFILE in {"demo10", "hardening", "recognition_hardening"}
    else f"{WORD_PROFILE}_recognition"
)
JSON_OUT = REPORT_DIR / f"{PROFILE_PREFIX}_audit_words.json"
CSV_OUT = REPORT_DIR / f"{PROFILE_PREFIX}_audit_words.csv"
MD_OUT = REPORT_DIR / f"{PROFILE_PREFIX}_audit_summary.md"

MIN_SEQS = int(os.environ.get("VOXGEST_MIN_SEQS_PER_CLASS", "80"))
MIN_GROUPS = int(os.environ.get("VOXGEST_MIN_GROUPS_PER_CLASS", "4"))
FOCUS_WORDS = {"WATER", "THANKYOU", "YES", "NO", "DOCTOR", "PLEASE", "HELLO", "NAME"}
REQUIRED_LABELS = list(TRAINING_WORDS)
DEMO10_LABELS = {
    "YES",
    "NO",
    "PLEASE",
    "WATER",
    "HELLO",
    "HELP",
    "STOP",
    "DOCTOR",
    "NAME",
    "THANKYOU",
    "NOTHING",
}


def load_metadata():
    for name in ("metadata_lstm_v2.json", "metadata_lstm_v1.json"):
        path = DATA_DIR / name
        if path.exists():
            with open(path, "r", encoding="utf-8") as f:
                return json.load(f).get("samples", {})
    return {}


def infer_group_id(label, file_name, metadata):
    key = f"{label}/{file_name}"
    if key in metadata:
        return metadata[key].get("source_id", key)

    stem = Path(file_name).stem
    for pattern in (r"(.+)_aug\d+$", r"(manual_.+?)_seq\d+$"):
        match = re.match(pattern, stem)
        if match:
            return f"{label}/{match.group(1)}"
    return f"{label}/{stem}"


def is_manual(label, file_name, metadata):
    sample = metadata.get(f"{label}/{file_name}", {})
    return file_name.startswith("manual_") or sample.get("source_video") == "manual_webcam"


def has_hand_policy_metadata(label, file_name, metadata):
    sample = metadata.get(f"{label}/{file_name}", {})
    return all(field in sample for field in ("dominant_hand", "mirrored_input", "single_hand_pose"))


def audit_label(label, metadata):
    label_dir = DATA_DIR / label
    row = {
        "label": label,
        "priority": "focus" if label in FOCUS_WORDS else ("negative" if label in NEGATIVE_WORDS else "keep"),
        "total_files": 0,
        "valid_shape": 0,
        "wrong_shape": 0,
        "manual_samples": 0,
        "video_samples": 0,
        "groups": 0,
        "manual_groups": 0,
        "missing_hand_metadata": 0,
        "missing_metadata_total": 0,
        "right_hand_meta": 0,
        "left_hand_meta": 0,
        "auto_hand_meta": 0,
        "mirrored_true": 0,
        "mirrored_false": 0,
        "single_hand_true": 0,
        "single_hand_false": 0,
        "ready": False,
        "quality": "RED",
        "status": "not_enough_data",
        "need_sequences": MIN_SEQS,
        "need_groups": MIN_GROUPS,
        "guidance": "",
    }

    groups = set()
    manual_groups = set()
    if not label_dir.exists():
        row["guidance"] = "missing label directory"
        return row

    for file_path in sorted(label_dir.glob("*.npy")):
        row["total_files"] += 1
        try:
            shape = tuple(np.load(file_path, mmap_mode="r", allow_pickle=False).shape)
        except Exception:
            row["wrong_shape"] += 1
            continue

        if shape != (SEQ_LEN, FEAT_SIZE):
            row["wrong_shape"] += 1
            continue

        row["valid_shape"] += 1
        group_id = infer_group_id(label, file_path.name, metadata)
        groups.add(group_id)
        if is_manual(label, file_path.name, metadata):
            row["manual_samples"] += 1
            manual_groups.add(group_id)
        else:
            row["video_samples"] += 1

        sample = metadata.get(f"{label}/{file_path.name}", {})
        missing_meta = not has_hand_policy_metadata(label, file_path.name, metadata)
        if missing_meta:
            row["missing_metadata_total"] += 1
            if is_manual(label, file_path.name, metadata):
                row["missing_hand_metadata"] += 1

        hand = str(sample.get("dominant_hand", "")).lower()
        if hand == "right":
            row["right_hand_meta"] += 1
        elif hand == "left":
            row["left_hand_meta"] += 1
        elif hand == "auto":
            row["auto_hand_meta"] += 1

        if sample.get("mirrored_input") is True:
            row["mirrored_true"] += 1
        elif sample.get("mirrored_input") is False:
            row["mirrored_false"] += 1

        if sample.get("single_hand_pose") is True:
            row["single_hand_true"] += 1
        elif sample.get("single_hand_pose") is False:
            row["single_hand_false"] += 1

    row["groups"] = len(groups)
    row["manual_groups"] = len(manual_groups)
    row["ready"] = row["valid_shape"] >= MIN_SEQS and row["groups"] >= MIN_GROUPS
    if row["ready"] and row["valid_shape"] >= MIN_SEQS * 2 and row["groups"] >= MIN_GROUPS * 2:
        row["quality"] = "GREEN"
        row["status"] = "strong_dataset_support"
    elif row["ready"]:
        row["quality"] = "YELLOW"
        row["status"] = "trainable_needs_manual_hardening"
    else:
        row["quality"] = "RED"
        row["status"] = "exclude_or_record_more"
    row["need_sequences"] = max(0, MIN_SEQS - row["valid_shape"])
    row["need_groups"] = max(0, MIN_GROUPS - row["groups"])

    guidance = []
    if row["need_sequences"]:
        guidance.append(f"record {row['need_sequences']}+ more valid sequences")
    if row["need_groups"]:
        guidance.append(f"add {row['need_groups']}+ separate recording groups")
    if row["missing_hand_metadata"]:
        guidance.append("archive or replace metadata-less manual samples")
    if label == "NOTHING":
        guidance.append("include idle, partial, incomplete, transition, and hand-enter/leave hard negatives")
    elif label in FOCUS_WORDS:
        guidance.append("prioritize clean webcam calibration and live-test this class")
    elif row["quality"] == "YELLOW":
        guidance.append("trainable, but schedule manual hardening before demo")
    row["guidance"] = "; ".join(guidance) if guidance else "ready for retraining/live test"
    return row


def load_report(path):
    try:
        with open(path, "r", encoding="utf-8") as f:
            return json.load(f)
    except Exception:
        return {}


def main():
    REPORT_DIR.mkdir(parents=True, exist_ok=True)
    metadata = load_metadata()
    rows = [audit_label(label, metadata) for label in REQUIRED_LABELS]
    ready = [row["label"] for row in rows if row["ready"]]
    blocked = [row["label"] for row in rows if not row["ready"]]
    wrong_shape_total = sum(row["wrong_shape"] for row in rows)
    missing_demo10 = sorted(DEMO10_LABELS - set(REQUIRED_LABELS))
    quality_counts = {
        quality: sum(1 for row in rows if row["quality"] == quality)
        for quality in ("GREEN", "YELLOW", "RED")
    }
    trainable_label_count = len(ready)

    summary = {
        "generated_at": datetime.now().isoformat(timespec="seconds"),
        "dataset": str(DATA_DIR),
        "word_profile": WORD_PROFILE,
        "seq_len": SEQ_LEN,
        "feature_size": FEAT_SIZE,
        "single_hand_pose_enabled": single_hand_pose_enabled(),
        "required_labels": REQUIRED_LABELS,
        "target_words": TARGET_WORDS,
        "focus_words": sorted(FOCUS_WORDS),
        "minimum_sequences_per_class": MIN_SEQS,
        "minimum_groups_per_class": MIN_GROUPS,
        "ready_labels": ready,
        "blocked_labels": blocked,
        "quality_counts": quality_counts,
        "trainable_label_count": trainable_label_count,
        "target_label_count_including_negative": len(REQUIRED_LABELS),
        "wrong_shape_files": wrong_shape_total,
        "nothing_present": "NOTHING" in ready,
        "demo10_labels_preserved_in_profile": not missing_demo10,
        "missing_demo10_labels_from_profile": missing_demo10,
        "training_readiness_passed": (
            len(blocked) == 0
            and "NOTHING" in ready
            and not missing_demo10
            and wrong_shape_total == 0
        ),
        "hard_negative_guidance": (
            "Record NOTHING from idle, partial/incomplete signs, transitions, "
            "aborted signs, and hands entering/leaving frame."
        ),
        "model_reports": {
            "lstm": load_report(ROOT / "model" / "lstm_training_report.json"),
            "tcn": load_report(ROOT / "model" / "tcn_training_report.json"),
        },
        "rows": rows,
    }

    with open(JSON_OUT, "w", encoding="utf-8") as f:
        json.dump(summary, f, indent=2)

    fieldnames = list(rows[0].keys()) if rows else []
    with open(CSV_OUT, "w", encoding="utf-8", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=fieldnames)
        writer.writeheader()
        writer.writerows(rows)

    write_markdown_summary(summary, rows)

    print("=" * 76)
    print(f"VoxGest dataset audit | profile={WORD_PROFILE}")
    print("=" * 76)
    print(f"Dataset : {DATA_DIR}")
    print(f"JSON    : {JSON_OUT}")
    print(f"CSV     : {CSV_OUT}")
    print(f"MD      : {MD_OUT}")
    print(f"Ready   : {len(ready)} / {len(REQUIRED_LABELS)} labels")
    print(f"G/Y/R   : {quality_counts['GREEN']} / {quality_counts['YELLOW']} / {quality_counts['RED']}")
    print()
    for row in rows:
        status = row["quality"]
        print(
            f"{status:>6} {row['label']:<12} seq={row['valid_shape']:>4} "
            f"groups={row['groups']:>3} manual={row['manual_samples']:>4} "
            f"missing_meta={row['missing_hand_metadata']:>4}  {row['guidance']}"
        )


def write_markdown_summary(summary, rows):
    lines = [
        f"# {WORD_PROFILE} Recognition Dataset Audit",
        "",
        f"Generated: {summary['generated_at']}",
        f"Dataset: `{DATA_DIR}`",
        f"Labels: {summary['trainable_label_count']} trainable / {len(REQUIRED_LABELS)} required",
        f"Training readiness passed: `{summary['training_readiness_passed']}`",
        "",
        "## Quality Buckets",
        "",
        f"- GREEN: {summary['quality_counts']['GREEN']}",
        f"- YELLOW: {summary['quality_counts']['YELLOW']}",
        f"- RED: {summary['quality_counts']['RED']}",
        "",
        "## Per-Class Audit",
        "",
        "| Label | Quality | Trainable | Sequences | Groups | Manual | Wrong shape | Guidance |",
        "| --- | --- | --- | ---: | ---: | ---: | ---: | --- |",
    ]
    for row in rows:
        lines.append(
            "| "
            f"{row['label']} | {row['quality']} | {row['ready']} | "
            f"{row['valid_shape']} | {row['groups']} | {row['manual_samples']} | "
            f"{row['wrong_shape']} | {row['guidance']} |"
        )
    lines.extend(
        [
            "",
            "## Readiness Gates",
            "",
            f"- NOTHING present and trainable: {'NOTHING' in summary['ready_labels']}",
            f"- Demo10 labels preserved in profile: {summary['demo10_labels_preserved_in_profile']}",
            f"- Wrong-shape files in required labels: {summary['wrong_shape_files']}",
            f"- Blocked labels: {', '.join(summary['blocked_labels']) if summary['blocked_labels'] else 'none'}",
        ]
    )
    with open(MD_OUT, "w", encoding="utf-8") as f:
        f.write("\n".join(lines) + "\n")


if __name__ == "__main__":
    main()
