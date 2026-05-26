"""Audit the FullSign225 manual16 webcam feature dataset."""

import csv
import json
import os
from datetime import datetime
from pathlib import Path

os.environ.setdefault("VOXGEST_WORD_PROFILE", "fullsign225_manual16")
os.environ.setdefault("VOXGEST_FEATURE_PROFILE", "fullsign225")
os.environ.setdefault("VOXGEST_SINGLE_HAND_POSE", "0")
os.environ.setdefault("TF_CPP_MIN_LOG_LEVEL", "3")

import numpy as np

from lstm_features import FEAT_SIZE, SEQ_LEN, configured_feature_profile
from word_config import TRAINING_WORDS, WORD_PROFILE


ROOT = Path(__file__).resolve().parents[1]
DATASET_DIR = Path(
    os.environ.get(
        "VOXGEST_FULLSIGN225_MANUAL16_DATASET",
        ROOT / "external_datasets" / "fullsign225_manual16_features",
    )
)
METADATA_PATH = DATASET_DIR / "metadata_lstm_v2.json"
REJECT_LOG = DATASET_DIR / "rejected_samples.jsonl"
REPORT_DIR = ROOT / "reports"
CSV_OUT = REPORT_DIR / "fullsign225_manual16_audit.csv"
JSON_OUT = REPORT_DIR / "fullsign225_manual16_audit.json"
MD_OUT = REPORT_DIR / "fullsign225_manual16_audit.md"
EXPECTED_SHAPE = (SEQ_LEN, FEAT_SIZE)


def load_metadata():
    if not METADATA_PATH.exists():
        return {}
    with open(METADATA_PATH, "r", encoding="utf-8") as f:
        return json.load(f).get("samples", {})


def load_rejected():
    if not REJECT_LOG.exists():
        return []
    records = []
    with open(REJECT_LOG, "r", encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if not line:
                continue
            try:
                records.append(json.loads(line))
            except json.JSONDecodeError:
                records.append({"parse_error": line})
    return records


def group_id(word, path, metadata):
    key = f"{word}/{path.name}"
    sample = metadata.get(key, {})
    if sample.get("source_id"):
        return sample["source_id"]
    stem = path.stem
    if "_seq" in stem:
        return f"{word}/{stem.rsplit('_seq', 1)[0]}"
    return f"{word}/{stem}"


def audit_label(word, metadata):
    word_dir = DATASET_DIR / word
    files = sorted(word_dir.glob("*.npy")) if word_dir.exists() else []
    valid = 0
    wrong_shape = []
    unreadable = []
    groups = set()

    for path in files:
        try:
            shape = tuple(np.load(path, mmap_mode="r", allow_pickle=False).shape)
        except Exception as exc:
            unreadable.append({"file": str(path), "error": str(exc)})
            continue
        if shape != EXPECTED_SHAPE:
            wrong_shape.append({"file": str(path), "shape": list(shape)})
            continue
        valid += 1
        groups.add(group_id(word, path, metadata))

    return {
        "label": word,
        "samples": valid,
        "groups": len(groups),
        "wrong_shape_files": len(wrong_shape),
        "unreadable_files": len(unreadable),
        "missing": valid == 0,
        "under_20": valid < 20,
        "under_30": valid < 30,
        "wrong_shape_examples": wrong_shape[:5],
        "unreadable_examples": unreadable[:5],
    }


def write_csv(rows):
    fields = [
        "label",
        "samples",
        "groups",
        "wrong_shape_files",
        "unreadable_files",
        "missing",
        "under_20",
        "under_30",
    ]
    REPORT_DIR.mkdir(parents=True, exist_ok=True)
    with open(CSV_OUT, "w", encoding="utf-8", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=fields)
        writer.writeheader()
        for row in rows:
            writer.writerow({field: row[field] for field in fields})


def write_markdown(payload):
    lines = [
        "# FullSign225 Manual16 Dataset Audit",
        "",
        f"Generated: {payload['generated_at']}",
        f"Profile: `{payload['profile']}`",
        f"Dataset: `{payload['dataset']}`",
        f"Feature profile: `{payload['feature_profile']}`",
        f"Expected shape: `{payload['expected_shape']}`",
        "",
        "## Summary",
        "",
        f"- Total valid samples: {payload['totals']['valid_samples']}",
        f"- Missing labels: {', '.join(payload['missing_labels']) if payload['missing_labels'] else 'none'}",
        f"- Labels under 20: {', '.join(payload['labels_under_20']) if payload['labels_under_20'] else 'none'}",
        f"- Labels under 30: {', '.join(payload['labels_under_30']) if payload['labels_under_30'] else 'none'}",
        f"- NOTHING samples: {payload['nothing_count']}",
        f"- Rejected/failed samples logged: {payload['rejected_count']}",
        "",
        "## Per Label",
        "",
        "| Label | Samples | Groups | Wrong Shape | Unreadable |",
        "| --- | ---: | ---: | ---: | ---: |",
    ]
    for row in payload["per_label"]:
        lines.append(
            f"| {row['label']} | {row['samples']} | {row['groups']} | "
            f"{row['wrong_shape_files']} | {row['unreadable_files']} |"
        )
    lines.extend(
        [
            "",
            "## Readiness",
            "",
            "Train only after every label has enough clean samples and no wrong-shape files. For a fast first pass, 20 per label can be used cautiously; 30+ per label is preferred.",
        ]
    )
    with open(MD_OUT, "w", encoding="utf-8") as f:
        f.write("\n".join(lines) + "\n")


def main():
    if configured_feature_profile() != "fullsign225" or FEAT_SIZE != 225:
        raise RuntimeError(f"Expected fullsign225/225 features, got {configured_feature_profile()}/{FEAT_SIZE}")

    metadata = load_metadata()
    rejected = load_rejected()
    rows = [audit_label(word, metadata) for word in TRAINING_WORDS]
    missing = [row["label"] for row in rows if row["missing"]]
    under_20 = [row["label"] for row in rows if row["under_20"]]
    under_30 = [row["label"] for row in rows if row["under_30"]]
    nothing_count = next((row["samples"] for row in rows if row["label"] == "NOTHING"), 0)
    payload = {
        "generated_at": datetime.now().isoformat(timespec="seconds"),
        "profile": WORD_PROFILE,
        "dataset": str(DATASET_DIR),
        "metadata": str(METADATA_PATH),
        "feature_profile": configured_feature_profile(),
        "expected_shape": [SEQ_LEN, FEAT_SIZE],
        "training_words": TRAINING_WORDS,
        "per_label": rows,
        "missing_labels": missing,
        "labels_under_20": under_20,
        "labels_under_30": under_30,
        "nothing_count": nothing_count,
        "rejected_count": len(rejected),
        "rejected_examples": rejected[:20],
        "totals": {
            "valid_samples": sum(row["samples"] for row in rows),
            "wrong_shape_files": sum(row["wrong_shape_files"] for row in rows),
            "unreadable_files": sum(row["unreadable_files"] for row in rows),
        },
    }

    write_csv(rows)
    REPORT_DIR.mkdir(parents=True, exist_ok=True)
    with open(JSON_OUT, "w", encoding="utf-8") as f:
        json.dump(payload, f, indent=2)
    write_markdown(payload)

    print("=" * 72)
    print("FullSign225 manual16 audit")
    print("=" * 72)
    print(f"Dataset       : {DATASET_DIR}")
    print(f"Valid samples : {payload['totals']['valid_samples']}")
    print(f"Missing labels: {missing if missing else 'none'}")
    print(f"Under 20      : {under_20 if under_20 else 'none'}")
    print(f"Under 30      : {under_30 if under_30 else 'none'}")
    print(f"NOTHING count : {nothing_count}")
    print(f"Rejected log  : {len(rejected)}")
    print(f"Wrote: {CSV_OUT}")
    print(f"Wrote: {JSON_OUT}")
    print(f"Wrote: {MD_OUT}")


if __name__ == "__main__":
    main()
