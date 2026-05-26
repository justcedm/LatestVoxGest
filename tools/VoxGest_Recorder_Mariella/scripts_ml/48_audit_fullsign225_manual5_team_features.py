"""Audit merged FullSign225 manual5 team feature arrays."""

import csv
import json
import re
from collections import defaultdict
from pathlib import Path

import numpy as np


ROOT = Path(__file__).resolve().parents[1]
DATASET_ROOT = ROOT / "external_datasets" / "fullsign225_manual5_team_features"
INCOMING_ROOT = ROOT / "external_datasets" / "team_incoming_recorded_features"
REPORT_DIR = ROOT / "reports"

EXPECTED_SIGNERS = ["CED", "ANASTACIA", "MARIELLA", "EARLE"]
EXPECTED_LABELS = ["EAT", "WATER", "HELLO", "THANKYOU", "NOTHING"]
EXPECTED_SHAPE = (30, 225)
MIN_TARGET = {"EAT": 80, "WATER": 80, "HELLO": 80, "THANKYOU": 80, "NOTHING": 120}
PREFERRED_TARGET = {"EAT": 120, "WATER": 120, "HELLO": 120, "THANKYOU": 120, "NOTHING": 180}


def infer_signer(file_name):
    stem = Path(file_name).stem
    match = re.match(r"([A-Za-z0-9_-]+)_", stem)
    return match.group(1).upper() if match else "UNKNOWN"


def count_rejected():
    rejected = defaultdict(lambda: defaultdict(int))
    if not INCOMING_ROOT.exists():
        return rejected
    for file_path in INCOMING_ROOT.rglob("*.npy"):
        parts = {part.lower() for part in file_path.parts}
        if "_rejected" not in parts:
            continue
        label = file_path.parent.name.upper()
        if label not in EXPECTED_LABELS:
            continue
        rel = file_path.relative_to(INCOMING_ROOT).parts
        signer = rel[0].upper() if rel else "UNKNOWN"
        rejected[signer][label] += 1
    return rejected


def audit():
    REPORT_DIR.mkdir(exist_ok=True)

    rows = []
    per_label = defaultdict(int)
    per_signer_label = defaultdict(lambda: defaultdict(int))
    unreadable = []
    wrong_shape = []

    for label in EXPECTED_LABELS:
        label_dir = DATASET_ROOT / label
        if not label_dir.exists():
            rows.append(
                {
                    "label": label,
                    "signer": "",
                    "file": "",
                    "status": "missing_label_folder",
                    "shape": "",
                    "error": "",
                }
            )
            continue

        for file_path in sorted(label_dir.glob("*.npy")):
            signer = infer_signer(file_path.name)
            try:
                arr = np.load(file_path, allow_pickle=False)
            except Exception as exc:
                unreadable.append(str(file_path))
                rows.append(
                    {
                        "label": label,
                        "signer": signer,
                        "file": str(file_path),
                        "status": "unreadable",
                        "shape": "",
                        "error": str(exc),
                    }
                )
                continue

            shape = tuple(arr.shape)
            if shape != EXPECTED_SHAPE:
                wrong_shape.append(str(file_path))
                rows.append(
                    {
                        "label": label,
                        "signer": signer,
                        "file": str(file_path),
                        "status": "wrong_shape",
                        "shape": str(shape),
                        "error": "",
                    }
                )
                continue

            per_label[label] += 1
            per_signer_label[signer][label] += 1
            rows.append(
                {
                    "label": label,
                    "signer": signer,
                    "file": str(file_path),
                    "status": "ok",
                    "shape": str(shape),
                    "error": "",
                }
            )

    rejected = count_rejected()
    labels_under_min = [label for label in EXPECTED_LABELS if per_label[label] < MIN_TARGET[label]]
    labels_under_preferred = [
        label for label in EXPECTED_LABELS if per_label[label] < PREFERRED_TARGET[label]
    ]
    ready_minimum = not labels_under_min and not wrong_shape and not unreadable
    ready_preferred = not labels_under_preferred and not wrong_shape and not unreadable

    csv_path = REPORT_DIR / "fullsign225_manual5_team_audit.csv"
    json_path = REPORT_DIR / "fullsign225_manual5_team_audit.json"
    md_path = REPORT_DIR / "fullsign225_manual5_team_audit.md"

    fieldnames = ["label", "signer", "file", "status", "shape", "error"]
    with open(csv_path, "w", newline="", encoding="utf-8") as f:
        writer = csv.DictWriter(f, fieldnames=fieldnames)
        writer.writeheader()
        writer.writerows(rows)

    summary = {
        "dataset_root": str(DATASET_ROOT),
        "expected_shape": list(EXPECTED_SHAPE),
        "per_label": dict(per_label),
        "per_signer_label": {signer: dict(labels) for signer, labels in per_signer_label.items()},
        "rejected_per_signer_label": {signer: dict(labels) for signer, labels in rejected.items()},
        "wrong_shape_files": wrong_shape,
        "unreadable_files": unreadable,
        "minimum_target": MIN_TARGET,
        "preferred_target": PREFERRED_TARGET,
        "labels_under_minimum": labels_under_min,
        "labels_under_preferred": labels_under_preferred,
        "ready_minimum": ready_minimum,
        "ready_preferred": ready_preferred,
    }
    json_path.write_text(json.dumps(summary, indent=2), encoding="utf-8")

    lines = [
        "# FullSign225 Manual5 Team Audit",
        "",
        f"- Dataset: `{DATASET_ROOT}`",
        f"- Expected shape: `{EXPECTED_SHAPE}`",
        f"- Ready for minimum target: `{ready_minimum}`",
        f"- Ready for preferred target: `{ready_preferred}`",
        "",
        "## Per Label Counts",
        "",
        "| Label | Count | Minimum | Preferred |",
        "| --- | ---: | ---: | ---: |",
    ]
    for label in EXPECTED_LABELS:
        lines.append(
            f"| {label} | {per_label[label]} | {MIN_TARGET[label]} | {PREFERRED_TARGET[label]} |"
        )

    lines.extend(
        [
            "",
            "## Per Signer Counts",
            "",
            "| Signer | EAT | WATER | HELLO | THANKYOU | NOTHING |",
            "| --- | ---: | ---: | ---: | ---: | ---: |",
        ]
    )
    for signer in EXPECTED_SIGNERS + sorted(set(per_signer_label) - set(EXPECTED_SIGNERS)):
        counts = per_signer_label.get(signer, {})
        lines.append(
            "| {signer} | {eat} | {water} | {hello} | {thankyou} | {nothing} |".format(
                signer=signer,
                eat=counts.get("EAT", 0),
                water=counts.get("WATER", 0),
                hello=counts.get("HELLO", 0),
                thankyou=counts.get("THANKYOU", 0),
                nothing=counts.get("NOTHING", 0),
            )
        )

    lines.extend(["", "## Issues", ""])
    lines.append(f"- Wrong-shape files: {len(wrong_shape)}")
    lines.append(f"- Unreadable files: {len(unreadable)}")
    lines.append(
        "- Labels under minimum target: "
        + (", ".join(labels_under_min) if labels_under_min else "none")
    )
    lines.append(
        "- Labels under preferred target: "
        + (", ".join(labels_under_preferred) if labels_under_preferred else "none")
    )

    lines.extend(["", "## Rejected Samples Seen", ""])
    for signer in EXPECTED_SIGNERS:
        total = sum(rejected.get(signer, {}).values())
        lines.append(f"- {signer}: {total}")
    md_path.write_text("\n".join(lines) + "\n", encoding="utf-8")

    print("=" * 72)
    print("FullSign225 manual5 team audit")
    print("=" * 72)
    for label in EXPECTED_LABELS:
        print(f"{label:<10}: {per_label[label]}")
    print(f"Wrong shape : {len(wrong_shape)}")
    print(f"Unreadable  : {len(unreadable)}")
    print(f"Ready min   : {ready_minimum}")
    print(f"Wrote: {csv_path}")
    print(f"Wrote: {json_path}")
    print(f"Wrote: {md_path}")
    return 0 if ready_minimum else 1


if __name__ == "__main__":
    raise SystemExit(audit())
