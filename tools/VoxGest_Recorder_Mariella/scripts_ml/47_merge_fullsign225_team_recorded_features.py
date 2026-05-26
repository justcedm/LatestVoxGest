"""
Merge teammate FullSign225 manual5 recorder outputs into one training folder.

The recorder zips often contain extra nesting, so this script finds valid .npy
files by label folder name instead of assuming one exact directory depth.
Original teammate folders are never deleted or modified.
"""

import csv
import json
import re
import shutil
from collections import defaultdict
from datetime import datetime
from pathlib import Path

import numpy as np


ROOT = Path(__file__).resolve().parents[1]
INCOMING_ROOT = ROOT / "external_datasets" / "team_incoming_recorded_features"
OUTPUT_ROOT = ROOT / "external_datasets" / "fullsign225_manual5_team_features"
REPORT_DIR = ROOT / "reports"

EXPECTED_SIGNERS = ["CED", "ANASTACIA", "MARIELLA", "EARLE"]
EXPECTED_LABELS = ["EAT", "WATER", "HELLO", "THANKYOU", "NOTHING"]
EXPECTED_SHAPE = (30, 225)
SKIP_PARTS = {"_rejected", "recorder_env", "__pycache__"}
FEATURE_FOLDER = "fullsign225_manual5_team_features"


def clean_name(value):
    value = value.strip().upper()
    return re.sub(r"[^A-Z0-9_-]+", "_", value)


def should_skip(path):
    parts = {part.lower() for part in path.parts}
    if any(part.lower() in parts for part in SKIP_PARTS):
        return True
    return path.suffix.lower() != ".npy"


def is_recorder_feature_file(path):
    parts = [part.lower() for part in path.parts]
    return "recorded_features" in parts and FEATURE_FOLDER.lower() in parts


def find_label(path):
    parent = path.parent.name.upper()
    if parent in EXPECTED_LABELS:
        return parent
    return None


def load_shape(path):
    try:
        arr = np.load(path, allow_pickle=False)
        return tuple(arr.shape), None
    except Exception as exc:
        return None, str(exc)


def signer_dirs():
    if not INCOMING_ROOT.exists():
        return []
    found = {path.name.upper(): path for path in INCOMING_ROOT.iterdir() if path.is_dir()}
    ordered = []
    for signer in EXPECTED_SIGNERS:
        if signer in found:
            ordered.append((signer, found.pop(signer)))
        else:
            ordered.append((signer, INCOMING_ROOT / signer))
    for signer, path in sorted(found.items()):
        ordered.append((signer, path))
    return ordered


def merge():
    REPORT_DIR.mkdir(exist_ok=True)
    OUTPUT_ROOT.mkdir(parents=True, exist_ok=True)
    for label in EXPECTED_LABELS:
        (OUTPUT_ROOT / label).mkdir(parents=True, exist_ok=True)

    rows = []
    metadata = {"samples": {}}
    counts = defaultdict(lambda: defaultdict(int))
    rejected_counts = defaultdict(lambda: defaultdict(int))

    for signer, signer_root in signer_dirs():
        signer = clean_name(signer)
        if not signer_root.exists():
            rows.append(
                {
                    "signer": signer,
                    "label": "",
                    "source_path": str(signer_root),
                    "dest_path": "",
                    "status": "missing_signer_folder",
                    "shape": "",
                    "error": "",
                }
            )
            continue

        for file_path in signer_root.rglob("*.npy"):
            lower_parts = {part.lower() for part in file_path.parts}
            label = find_label(file_path)

            if "_rejected" in lower_parts:
                if label:
                    rejected_counts[signer][label] += 1
                continue
            if should_skip(file_path) or not is_recorder_feature_file(file_path):
                continue
            if not label:
                rows.append(
                    {
                        "signer": signer,
                        "label": "",
                        "source_path": str(file_path),
                        "dest_path": "",
                        "status": "skipped_unknown_label",
                        "shape": "",
                        "error": "",
                    }
                )
                continue

            shape, error = load_shape(file_path)
            if error:
                rows.append(
                    {
                        "signer": signer,
                        "label": label,
                        "source_path": str(file_path),
                        "dest_path": "",
                        "status": "unreadable",
                        "shape": "",
                        "error": error,
                    }
                )
                continue
            if shape != EXPECTED_SHAPE:
                rows.append(
                    {
                        "signer": signer,
                        "label": label,
                        "source_path": str(file_path),
                        "dest_path": "",
                        "status": "wrong_shape",
                        "shape": str(shape),
                        "error": "",
                    }
                )
                continue

            dest_name = f"{signer}_{file_path.name}"
            dest_path = OUTPUT_ROOT / label / dest_name
            shutil.copy2(file_path, dest_path)
            counts[signer][label] += 1
            metadata["samples"][f"{label}/{dest_name}"] = {
                "source_id": f"{label}/{signer}",
                "signer_id": signer,
                "source_video": "team_recorder_fullsign225",
                "feature_profile": "fullsign225",
                "single_hand_pose": False,
                "dominant_hand": "auto",
                "mirrored_input": True,
                "original_path": str(file_path),
                "merged_at": datetime.now().isoformat(timespec="seconds"),
            }
            rows.append(
                {
                    "signer": signer,
                    "label": label,
                    "source_path": str(file_path),
                    "dest_path": str(dest_path),
                    "status": "copied",
                    "shape": str(shape),
                    "error": "",
                }
            )

    metadata_path = OUTPUT_ROOT / "metadata_lstm_v2.json"
    metadata_path.write_text(json.dumps(metadata, indent=2), encoding="utf-8")

    csv_path = REPORT_DIR / "fullsign225_manual5_team_merge.csv"
    json_path = REPORT_DIR / "fullsign225_manual5_team_merge.json"
    md_path = REPORT_DIR / "fullsign225_manual5_team_merge.md"

    fieldnames = ["signer", "label", "source_path", "dest_path", "status", "shape", "error"]
    with open(csv_path, "w", newline="", encoding="utf-8") as f:
        writer = csv.DictWriter(f, fieldnames=fieldnames)
        writer.writeheader()
        writer.writerows(rows)

    summary = {
        "incoming_root": str(INCOMING_ROOT),
        "output_root": str(OUTPUT_ROOT),
        "expected_labels": EXPECTED_LABELS,
        "expected_shape": list(EXPECTED_SHAPE),
        "copied_per_signer_label": {signer: dict(labels) for signer, labels in counts.items()},
        "rejected_per_signer_label": {signer: dict(labels) for signer, labels in rejected_counts.items()},
        "rows": rows,
    }
    json_path.write_text(json.dumps(summary, indent=2), encoding="utf-8")

    lines = [
        "# FullSign225 Manual5 Team Merge",
        "",
        f"- Incoming root: `{INCOMING_ROOT}`",
        f"- Output root: `{OUTPUT_ROOT}`",
        f"- Metadata: `{metadata_path}`",
        "",
        "## Accepted Samples",
        "",
        "| Signer | EAT | WATER | HELLO | THANKYOU | NOTHING |",
        "| --- | ---: | ---: | ---: | ---: | ---: |",
    ]
    for signer in EXPECTED_SIGNERS:
        label_counts = counts.get(signer, {})
        lines.append(
            "| {signer} | {eat} | {water} | {hello} | {thankyou} | {nothing} |".format(
                signer=signer,
                eat=label_counts.get("EAT", 0),
                water=label_counts.get("WATER", 0),
                hello=label_counts.get("HELLO", 0),
                thankyou=label_counts.get("THANKYOU", 0),
                nothing=label_counts.get("NOTHING", 0),
            )
        )
    lines.extend(["", "## Rejected Samples Seen", ""])
    for signer in EXPECTED_SIGNERS:
        label_counts = rejected_counts.get(signer, {})
        total = sum(label_counts.values())
        lines.append(f"- {signer}: {total}")
    md_path.write_text("\n".join(lines) + "\n", encoding="utf-8")

    print("=" * 72)
    print("FullSign225 manual5 team merge")
    print("=" * 72)
    for label in EXPECTED_LABELS:
        total = sum(counts[signer].get(label, 0) for signer in counts)
        print(f"{label:<10}: {total}")
    print(f"Wrote: {csv_path}")
    print(f"Wrote: {json_path}")
    print(f"Wrote: {md_path}")


if __name__ == "__main__":
    merge()
