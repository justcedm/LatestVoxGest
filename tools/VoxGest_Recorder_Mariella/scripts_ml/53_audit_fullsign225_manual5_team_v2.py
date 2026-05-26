"""Audit the merged FullSign225 manual5 team v2 feature dataset."""

import csv
import json
import re
from collections import defaultdict
from pathlib import Path

import numpy as np


ROOT = Path(__file__).resolve().parents[1]
DATASET_ROOT = ROOT / "external_datasets" / "fullsign225_manual5_team_features_v2"
REPORT_DIR = ROOT / "reports"

EXPECTED_LABELS = ["EAT", "WATER", "HELLO", "THANKYOU", "NOTHING"]
EXPECTED_SHAPE = (30, 225)
MIN_TARGET = {"EAT": 80, "WATER": 80, "HELLO": 80, "THANKYOU": 80, "NOTHING": 120}
PREFERRED_TARGET = {"EAT": 120, "WATER": 120, "HELLO": 120, "THANKYOU": 120, "NOTHING": 180}


def parse_name(file_name):
    stem = Path(file_name).stem
    match = re.match(
        r"(?P<signer>[A-Z0-9]+)_(?P<batch>.+?)_(?P<label>EAT|WATER|HELLO|THANKYOU|NOTHING)_\d+_",
        stem,
    )
    if not match:
        parts = stem.split("_")
        return (parts[0].upper() if parts else "UNKNOWN", "UNKNOWN")
    return match.group("signer").upper(), match.group("batch").upper()


def audit():
    REPORT_DIR.mkdir(exist_ok=True)
    rows = []
    per_label = defaultdict(int)
    per_signer = defaultdict(int)
    per_batch = defaultdict(int)
    per_signer_label = defaultdict(lambda: defaultdict(int))
    wrong_shape = []
    unreadable = []
    missing_label_dirs = []

    for label in EXPECTED_LABELS:
        label_dir = DATASET_ROOT / label
        if not label_dir.exists():
            missing_label_dirs.append(label)
            rows.append(
                {
                    "label": label,
                    "signer": "",
                    "batch": "",
                    "file": str(label_dir),
                    "status": "missing_label_folder",
                    "shape": "",
                    "error": "",
                }
            )
            continue

        for file_path in sorted(label_dir.glob("*.npy")):
            signer, batch = parse_name(file_path.name)
            row = {
                "label": label,
                "signer": signer,
                "batch": batch,
                "file": str(file_path),
                "status": "",
                "shape": "",
                "error": "",
            }
            try:
                arr = np.load(file_path, allow_pickle=False)
            except Exception as exc:
                row["status"] = "unreadable"
                row["error"] = str(exc)
                unreadable.append(str(file_path))
                rows.append(row)
                continue

            shape = tuple(arr.shape)
            row["shape"] = str(shape)
            if shape != EXPECTED_SHAPE:
                row["status"] = "wrong_shape"
                wrong_shape.append(str(file_path))
                rows.append(row)
                continue

            row["status"] = "ok"
            rows.append(row)
            per_label[label] += 1
            per_signer[signer] += 1
            per_batch[f"{signer}/{batch}"] += 1
            per_signer_label[signer][label] += 1

    labels_under_minimum = [
        label for label in EXPECTED_LABELS if per_label[label] < MIN_TARGET[label]
    ]
    labels_under_preferred = [
        label for label in EXPECTED_LABELS if per_label[label] < PREFERRED_TARGET[label]
    ]
    ready_minimum = (
        not labels_under_minimum
        and not wrong_shape
        and not unreadable
        and not missing_label_dirs
    )
    ready_preferred = (
        not labels_under_preferred
        and not wrong_shape
        and not unreadable
        and not missing_label_dirs
    )

    csv_path = REPORT_DIR / "fullsign225_manual5_team_v2_audit.csv"
    json_path = REPORT_DIR / "fullsign225_manual5_team_v2_audit.json"
    md_path = REPORT_DIR / "fullsign225_manual5_team_v2_audit.md"

    fieldnames = ["label", "signer", "batch", "file", "status", "shape", "error"]
    with open(csv_path, "w", newline="", encoding="utf-8") as f:
        writer = csv.DictWriter(f, fieldnames=fieldnames)
        writer.writeheader()
        writer.writerows(rows)

    summary = {
        "dataset_root": str(DATASET_ROOT),
        "expected_labels": EXPECTED_LABELS,
        "expected_shape": list(EXPECTED_SHAPE),
        "minimum_target": MIN_TARGET,
        "preferred_target": PREFERRED_TARGET,
        "counts_per_label": dict(per_label),
        "counts_per_signer": dict(per_signer),
        "counts_per_batch": dict(per_batch),
        "counts_per_signer_per_label": {
            signer: dict(labels) for signer, labels in per_signer_label.items()
        },
        "missing_label_dirs": missing_label_dirs,
        "wrong_shape_files": wrong_shape,
        "unreadable_files": unreadable,
        "labels_under_minimum": labels_under_minimum,
        "labels_under_preferred": labels_under_preferred,
        "ready_minimum": ready_minimum,
        "ready_preferred": ready_preferred,
        "rows": rows,
    }
    json_path.write_text(json.dumps(summary, indent=2), encoding="utf-8")

    lines = [
        "# FullSign225 Manual5 Team v2 Audit",
        "",
        f"- Dataset: `{DATASET_ROOT}`",
        f"- Expected shape: `{EXPECTED_SHAPE}`",
        f"- Ready for one-day minimum target: `{ready_minimum}`",
        f"- Ready for preferred target: `{ready_preferred}`",
        "",
        "## Per Label Counts",
        "",
        "| Label | Count | Minimum | Preferred | Status |",
        "| --- | ---: | ---: | ---: | --- |",
    ]
    for label in EXPECTED_LABELS:
        count = per_label[label]
        if count >= PREFERRED_TARGET[label]:
            status = "preferred"
        elif count >= MIN_TARGET[label]:
            status = "minimum"
        else:
            status = "under minimum"
        lines.append(
            f"| {label} | {count} | {MIN_TARGET[label]} | {PREFERRED_TARGET[label]} | {status} |"
        )

    lines.extend(
        [
            "",
            "## Per Signer Per Label",
            "",
            "| Signer | EAT | WATER | HELLO | THANKYOU | NOTHING | Total |",
            "| --- | ---: | ---: | ---: | ---: | ---: | ---: |",
        ]
    )
    for signer in sorted(per_signer_label):
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

    lines.extend(["", "## Per Batch Counts", ""])
    for batch, count in sorted(per_batch.items()):
        lines.append(f"- `{batch}`: {count}")

    lines.extend(["", "## Issues", ""])
    lines.append(f"- Missing label folders: {', '.join(missing_label_dirs) if missing_label_dirs else 'none'}")
    lines.append(f"- Wrong-shape files: {len(wrong_shape)}")
    lines.append(f"- Unreadable files: {len(unreadable)}")
    lines.append(
        "- Labels under minimum target: "
        + (", ".join(labels_under_minimum) if labels_under_minimum else "none")
    )
    lines.append(
        "- Labels under preferred target: "
        + (", ".join(labels_under_preferred) if labels_under_preferred else "none")
    )
    md_path.write_text("\n".join(lines) + "\n", encoding="utf-8")

    print("=" * 72)
    print("FullSign225 manual5 team v2 audit")
    print("=" * 72)
    for label in EXPECTED_LABELS:
        print(f"{label:<10}: {per_label[label]}")
    print(f"Wrong shape : {len(wrong_shape)}")
    print(f"Unreadable  : {len(unreadable)}")
    print(f"Ready min   : {ready_minimum}")
    print(f"Ready pref  : {ready_preferred}")
    print(f"Wrote: {csv_path}")
    print(f"Wrote: {json_path}")
    print(f"Wrote: {md_path}")
    return 0 if ready_minimum else 1


if __name__ == "__main__":
    raise SystemExit(audit())
