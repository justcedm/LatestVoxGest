"""Merge old + new FullSign225 manual5 team recorder outputs into v2.

The incoming teammate zips can have several nesting styles. This script searches
recursively for folders named fullsign225_manual5_team_features, copies only
accepted .npy samples from the five approved labels, validates shape first, and
leaves all source folders untouched.
"""

import csv
import hashlib
import json
import re
import shutil
from collections import defaultdict
from datetime import datetime
from pathlib import Path

import numpy as np


ROOT = Path(__file__).resolve().parents[1]
INCOMING_ROOT = ROOT / "external_datasets" / "team_incoming_recorded_features"
OUTPUT_ROOT = ROOT / "external_datasets" / "fullsign225_manual5_team_features_v2"
REPORT_DIR = ROOT / "reports"

FEATURE_FOLDER = "fullsign225_manual5_team_features"
EXPECTED_LABELS = ["EAT", "WATER", "HELLO", "THANKYOU", "NOTHING"]
EXPECTED_SHAPE = (30, 225)
SKIP_PARTS = {"_rejected", "recorder_env", "__pycache__", ".git"}
SKIP_FILE_NAMES = {"readme", "requirements", "metadata"}


def clean_token(value):
    value = str(value or "").strip().upper()
    value = re.sub(r"[^A-Z0-9]+", "_", value)
    return value.strip("_") or "UNKNOWN"


def compact_batch(value):
    token = clean_token(value)
    match = re.match(r"BATCH_?0*([0-9]+)", token)
    if match:
        suffix = re.sub(r"^BATCH_?0*[0-9]+_?", "", token).strip("_")
        return f"BATCH{int(match.group(1)):02d}" + (f"_{suffix}" if suffix else "")
    return token


def should_skip_path(path):
    lower_parts = {part.lower() for part in path.parts}
    if lower_parts & SKIP_PARTS:
        return True
    stem = path.stem.lower()
    return any(stem.startswith(name) for name in SKIP_FILE_NAMES)


def find_feature_roots():
    if not INCOMING_ROOT.exists():
        return []
    roots = [
        path
        for path in INCOMING_ROOT.rglob(FEATURE_FOLDER)
        if path.is_dir() and not should_skip_path(path)
    ]
    return sorted(roots, key=lambda item: (len(item.parts), str(item).lower()))


def infer_signer_and_batch(path):
    try:
        rel_parts = path.relative_to(INCOMING_ROOT).parts
    except ValueError:
        return "UNKNOWN", "UNKNOWN"
    signer = clean_token(rel_parts[0]) if rel_parts else "UNKNOWN"
    batch = "DIRECT"
    for part in rel_parts[1:]:
        lower = part.lower()
        upper = part.upper()
        if lower in {"recorded_features", FEATURE_FOLDER.lower()}:
            break
        if upper in EXPECTED_LABELS or lower in {"_rejected", "recorder_env"}:
            break
        batch = compact_batch(part)
        break
    return signer, batch


def infer_label(path):
    label = path.parent.name.upper()
    return label if label in EXPECTED_LABELS else ""


def load_shape(path):
    try:
        arr = np.load(path, allow_pickle=False)
        return tuple(arr.shape), ""
    except Exception as exc:
        return None, str(exc)


def make_dest_name(signer, batch, label, index, source_path):
    digest = hashlib.sha1(str(source_path).encode("utf-8")).hexdigest()[:8].upper()
    return f"{signer}_{batch}_{label}_{index:03d}_{digest}.npy"


def write_reports(rows, source_folders, duplicate_sources, metadata_path):
    REPORT_DIR.mkdir(exist_ok=True)
    csv_path = REPORT_DIR / "fullsign225_manual5_team_v2_merge.csv"
    json_path = REPORT_DIR / "fullsign225_manual5_team_v2_merge.json"
    md_path = REPORT_DIR / "fullsign225_manual5_team_v2_merge.md"

    fieldnames = [
        "signer",
        "batch",
        "label",
        "source_folder",
        "source_path",
        "dest_path",
        "status",
        "shape",
        "error",
    ]
    with open(csv_path, "w", newline="", encoding="utf-8") as f:
        writer = csv.DictWriter(f, fieldnames=fieldnames)
        writer.writeheader()
        writer.writerows(rows)

    copied = [row for row in rows if row["status"] == "copied"]
    skipped = [row for row in rows if row["status"].startswith("skipped")]
    wrong_shape = [row for row in rows if row["status"] == "wrong_shape"]
    unreadable = [row for row in rows if row["status"] == "unreadable"]

    per_label = defaultdict(int)
    per_signer = defaultdict(int)
    per_signer_label = defaultdict(lambda: defaultdict(int))
    per_batch = defaultdict(int)
    for row in copied:
        per_label[row["label"]] += 1
        per_signer[row["signer"]] += 1
        per_signer_label[row["signer"]][row["label"]] += 1
        per_batch[f'{row["signer"]}/{row["batch"]}'] += 1

    summary = {
        "incoming_root": str(INCOMING_ROOT),
        "output_root": str(OUTPUT_ROOT),
        "feature_folder_name": FEATURE_FOLDER,
        "expected_labels": EXPECTED_LABELS,
        "expected_shape": list(EXPECTED_SHAPE),
        "metadata_path": str(metadata_path),
        "source_folders_found": [str(path) for path in source_folders],
        "total_copied_files": len(copied),
        "skipped_files": len(skipped),
        "wrong_shape_files": len(wrong_shape),
        "unreadable_files": len(unreadable),
        "duplicate_source_paths_seen": duplicate_sources,
        "counts_per_label": dict(per_label),
        "counts_per_signer": dict(per_signer),
        "counts_per_batch": dict(per_batch),
        "counts_per_signer_per_label": {
            signer: dict(labels) for signer, labels in per_signer_label.items()
        },
        "rows": rows,
    }
    json_path.write_text(json.dumps(summary, indent=2), encoding="utf-8")

    signers = sorted(per_signer_label)
    lines = [
        "# FullSign225 Manual5 Team v2 Merge",
        "",
        f"- Incoming root: `{INCOMING_ROOT}`",
        f"- Output root: `{OUTPUT_ROOT}`",
        f"- Metadata: `{metadata_path}`",
        f"- Source folders found: `{len(source_folders)}`",
        f"- Copied valid files: `{len(copied)}`",
        f"- Skipped files: `{len(skipped)}`",
        f"- Wrong-shape files: `{len(wrong_shape)}`",
        f"- Unreadable files: `{len(unreadable)}`",
        f"- Duplicate source paths ignored: `{duplicate_sources}`",
        "",
        "## Counts Per Label",
        "",
        "| Label | Count |",
        "| --- | ---: |",
    ]
    for label in EXPECTED_LABELS:
        lines.append(f"| {label} | {per_label[label]} |")

    lines.extend(
        [
            "",
            "## Counts Per Signer Per Label",
            "",
            "| Signer | EAT | WATER | HELLO | THANKYOU | NOTHING | Total |",
            "| --- | ---: | ---: | ---: | ---: | ---: | ---: |",
        ]
    )
    for signer in signers:
        counts = per_signer_label[signer]
        total = sum(counts.values())
        lines.append(
            "| {signer} | {eat} | {water} | {hello} | {thankyou} | {nothing} | {total} |".format(
                signer=signer,
                eat=counts.get("EAT", 0),
                water=counts.get("WATER", 0),
                hello=counts.get("HELLO", 0),
                thankyou=counts.get("THANKYOU", 0),
                nothing=counts.get("NOTHING", 0),
                total=total,
            )
        )

    lines.extend(["", "## Source Folders", ""])
    for folder in source_folders:
        lines.append(f"- `{folder}`")
    md_path.write_text("\n".join(lines) + "\n", encoding="utf-8")

    return csv_path, json_path, md_path, summary


def merge():
    if OUTPUT_ROOT.exists():
        shutil.rmtree(OUTPUT_ROOT)
    OUTPUT_ROOT.mkdir(parents=True, exist_ok=True)
    for label in EXPECTED_LABELS:
        (OUTPUT_ROOT / label).mkdir(parents=True, exist_ok=True)

    source_folders = find_feature_roots()
    rows = []
    metadata = {"samples": {}}
    label_indices = defaultdict(int)
    seen_sources = set()
    duplicate_sources = 0

    for feature_root in source_folders:
        for file_path in sorted(feature_root.rglob("*.npy")):
            if should_skip_path(file_path):
                continue
            resolved = str(file_path.resolve()).lower()
            if resolved in seen_sources:
                duplicate_sources += 1
                continue
            seen_sources.add(resolved)

            signer, batch = infer_signer_and_batch(file_path)
            label = infer_label(file_path)
            base_row = {
                "signer": signer,
                "batch": batch,
                "label": label,
                "source_folder": str(feature_root),
                "source_path": str(file_path),
                "dest_path": "",
                "status": "",
                "shape": "",
                "error": "",
            }

            if not label:
                base_row["status"] = "skipped_unknown_label"
                rows.append(base_row)
                continue

            shape, error = load_shape(file_path)
            if error:
                base_row["status"] = "unreadable"
                base_row["error"] = error
                rows.append(base_row)
                continue
            base_row["shape"] = str(shape)
            if shape != EXPECTED_SHAPE:
                base_row["status"] = "wrong_shape"
                rows.append(base_row)
                continue

            label_indices[label] += 1
            dest_name = make_dest_name(signer, batch, label, label_indices[label], file_path)
            dest_path = OUTPUT_ROOT / label / dest_name
            shutil.copy2(file_path, dest_path)
            base_row["dest_path"] = str(dest_path)
            base_row["status"] = "copied"
            rows.append(base_row)

            metadata["samples"][f"{label}/{dest_name}"] = {
                "source_id": f"{label}/{signer}/{batch}",
                "signer_id": signer,
                "batch_id": batch,
                "source_video": "team_recorder_fullsign225_v2",
                "feature_profile": "fullsign225",
                "single_hand_pose": False,
                "dominant_hand": "auto",
                "mirrored_input": True,
                "original_path": str(file_path),
                "merged_at": datetime.now().isoformat(timespec="seconds"),
            }

    metadata_path = OUTPUT_ROOT / "metadata_lstm_v2.json"
    metadata_path.write_text(json.dumps(metadata, indent=2), encoding="utf-8")
    csv_path, json_path, md_path, summary = write_reports(
        rows, source_folders, duplicate_sources, metadata_path
    )

    print("=" * 72)
    print("FullSign225 manual5 team v2 merge")
    print("=" * 72)
    print(f"Source folders: {len(source_folders)}")
    print(f"Copied files  : {summary['total_copied_files']}")
    print(f"Wrong shape   : {summary['wrong_shape_files']}")
    print(f"Unreadable    : {summary['unreadable_files']}")
    for label in EXPECTED_LABELS:
        print(f"{label:<10}: {summary['counts_per_label'].get(label, 0)}")
    print(f"Wrote: {csv_path}")
    print(f"Wrote: {json_path}")
    print(f"Wrote: {md_path}")


if __name__ == "__main__":
    merge()
