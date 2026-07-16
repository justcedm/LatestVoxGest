"""Audit confirmed one-handed FSL features before Samsung recording."""

from __future__ import annotations

import argparse
import json
from pathlib import Path
from typing import Any

import numpy as np


ROOT = Path(__file__).resolve().parents[1]
DATASET_ROOT = ROOT / "external_datasets" / "fsl_features"
CONFIRMED_LABELS_PATH = ROOT / "reports" / "fsl" / "confirmed_onehanded_labels.json"
REPORT_PATH = ROOT / "reports" / "fsl" / "audit_report.json"
EXPECTED_SHAPE = (20, 162)
FSL105_DEVICE = "FSL105_VIDEO"


def write_json_atomic(path: Path, payload: Any) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_suffix(path.suffix + ".tmp")
    temporary.write_text(
        json.dumps(payload, indent=2, ensure_ascii=False) + "\n",
        encoding="utf-8",
    )
    temporary.replace(path)


def load_confirmed_labels() -> list[str]:
    try:
        payload = json.loads(CONFIRMED_LABELS_PATH.read_text(encoding="utf-8"))
    except FileNotFoundError as exc:
        raise SystemExit(f"Confirmed-label file not found: {CONFIRMED_LABELS_PATH}") from exc
    except json.JSONDecodeError as exc:
        raise SystemExit(f"Invalid confirmed-label JSON: {exc}") from exc
    if not isinstance(payload, list) or not all(isinstance(item, str) for item in payload):
        raise SystemExit("confirmed_onehanded_labels.json must be a flat string array")
    if len(payload) != len(set(payload)):
        raise SystemExit("confirmed_onehanded_labels.json contains duplicate labels")
    return payload


def load_meta(npy_path: Path) -> tuple[dict[str, Any] | None, str | None]:
    meta_path = npy_path.with_suffix(".meta.json")
    if not meta_path.is_file():
        return None, "missing_meta"
    try:
        payload = json.loads(meta_path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        return None, f"invalid_meta:{exc}"
    if not isinstance(payload, dict):
        return None, "invalid_meta:not_an_object"
    return payload, None


def validate_array(npy_path: Path) -> str | None:
    try:
        array = np.load(npy_path, mmap_mode="r", allow_pickle=False)
    except (OSError, ValueError) as exc:
        return f"unreadable:{exc}"
    if tuple(array.shape) != EXPECTED_SHAPE:
        return f"shape:{list(array.shape)}"
    if not np.isfinite(array).all():
        return "non_finite"
    return None


def minimum_for(label: str) -> int:
    return 150 if label == "NOTHING" else 100


def audit_dataset(dataset_root: Path) -> dict[str, Any]:
    labels = load_confirmed_labels()
    per_label: dict[str, dict[str, Any]] = {}
    invalid_files: list[dict[str, str]] = []
    warnings: list[dict[str, str]] = []

    for label in labels:
        fsl105_sequences = 0
        samsung_sequences = 0
        device_counts: dict[str, int] = {}
        label_dir = dataset_root / label
        if label_dir.is_dir():
            for npy_path in sorted(label_dir.glob("*.npy")):
                reason = validate_array(npy_path)
                if reason is not None:
                    invalid_files.append(
                        {"path": str(npy_path), "label": label, "reason": reason}
                    )
                    continue
                meta, meta_error = load_meta(npy_path)
                if meta_error is not None or meta is None:
                    invalid_files.append(
                        {
                            "path": str(npy_path),
                            "label": label,
                            "reason": meta_error or "invalid_meta",
                        }
                    )
                    continue
                device_model = str(meta.get("device_model", "")).strip()
                if not device_model:
                    invalid_files.append(
                        {
                            "path": str(npy_path),
                            "label": label,
                            "reason": "missing_device_model",
                        }
                    )
                    continue
                meta_label = str(meta.get("label", "")).strip()
                if meta_label and meta_label != label:
                    invalid_files.append(
                        {
                            "path": str(npy_path),
                            "label": label,
                            "reason": f"meta_label_mismatch:{meta_label}",
                        }
                    )
                    continue

                device_counts[device_model] = device_counts.get(device_model, 0) + 1
                if device_model == FSL105_DEVICE:
                    fsl105_sequences += 1
                    if str(meta.get("source", "")).strip().lower() != "fsl105":
                        warnings.append(
                            {
                                "path": str(npy_path),
                                "label": label,
                                "warning": "FSL105_VIDEO metadata source is not fsl105",
                            }
                        )
                else:
                    # Per the recording audit contract, every non-FSL105 device
                    # is counted in the Samsung/recorded partition.
                    samsung_sequences += 1

        combined_total = fsl105_sequences + samsung_sequences
        minimum_required = minimum_for(label)
        per_label[label] = {
            "fsl105_sequences": fsl105_sequences,
            "samsung_sequences": samsung_sequences,
            "combined_total": combined_total,
            "minimum_required": minimum_required,
            "minimum_met": combined_total >= minimum_required,
            "sequences_needed_to_minimum": max(0, minimum_required - combined_total),
            "per_device": dict(sorted(device_counts.items())),
        }

    priority_labels = sorted(labels, key=lambda label: (per_label[label]["samsung_sequences"], label))
    recording_priority = [
        {
            "rank": index,
            "label": label,
            **per_label[label],
        }
        for index, label in enumerate(priority_labels, start=1)
    ]
    labels_below_minimum = [
        label for label in labels if not per_label[label]["minimum_met"]
    ]
    existing_label_dirs = sorted(
        path.name for path in dataset_root.iterdir() if path.is_dir()
    ) if dataset_root.is_dir() else []
    report = {
        "dataset_root": str(dataset_root.resolve()),
        "expected_shape": list(EXPECTED_SHAPE),
        "labels": labels,
        "partition_policy": {
            "fsl105": f"device_model == {FSL105_DEVICE}",
            "samsung": f"device_model != {FSL105_DEVICE}",
        },
        "minimum_policy": {"default": 100, "NOTHING": 150},
        "per_label": per_label,
        "recording_priority": recording_priority,
        "labels_below_minimum": labels_below_minimum,
        "invalid_files": invalid_files,
        "warnings": warnings,
        "ignored_label_directories": sorted(set(existing_label_dirs) - set(labels)),
        "ready_minimum": not labels_below_minimum and not invalid_files,
    }
    write_json_atomic(REPORT_PATH, report)
    return report


def yes_no(value: bool) -> str:
    return "YES" if value else "NO"


def print_report(report: dict[str, Any]) -> None:
    print("FSL one-handed dataset audit")
    print(f"Dataset: {report['dataset_root']}")
    print("")
    print(
        f"{'LABEL':<24} {'FSL105':>7} {'SAMSUNG':>8} {'COMBINED':>9} "
        f"{'MIN':>5} {'MET':>4}"
    )
    for label in report["labels"]:
        row = report["per_label"][label]
        print(
            f"{label:<24} {row['fsl105_sequences']:>7} "
            f"{row['samsung_sequences']:>8} {row['combined_total']:>9} "
            f"{row['minimum_required']:>5} {yes_no(row['minimum_met']):>4}"
        )

    print("")
    print("RECORDING PRIORITY (Samsung count ascending)")
    print(
        f"{'RANK':>4} {'LABEL':<24} {'SAMSUNG':>8} {'FSL105':>7} "
        f"{'COMBINED':>9} {'NEEDED':>7}"
    )
    for row in report["recording_priority"]:
        print(
            f"{row['rank']:>4} {row['label']:<24} {row['samsung_sequences']:>8} "
            f"{row['fsl105_sequences']:>7} {row['combined_total']:>9} "
            f"{row['sequences_needed_to_minimum']:>7}"
        )
    print("")
    print(f"Labels below minimum: {len(report['labels_below_minimum'])}")
    print(f"Invalid files: {len(report['invalid_files'])}")
    print(f"Warnings: {len(report['warnings'])}")
    print(f"Ready minimum: {report['ready_minimum']}")
    print(f"Report: {REPORT_PATH}")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--dataset", type=Path, default=DATASET_ROOT)
    args = parser.parse_args()
    report = audit_dataset(args.dataset)
    print_report(report)


if __name__ == "__main__":
    main()
