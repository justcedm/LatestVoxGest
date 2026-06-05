"""Audit imported FSL 20x162 feature samples."""

from __future__ import annotations

import argparse
import json
from collections import Counter, defaultdict
from pathlib import Path

import numpy as np

from fsl_config import FSL_EXPECTED_SHAPE, FSL_LABELS, FSL_MINIMUMS, FSL_PREFERRED


ROOT = Path(__file__).resolve().parents[1]
DATASET_ROOT = ROOT / "external_datasets" / "fsl_features"
REPORTS_DIR = ROOT / "reports" / "fsl"
REPORT_PATH = REPORTS_DIR / "audit_report.json"
DIVERSITY_REPORT_PATH = REPORTS_DIR / "device_diversity_report.json"


def load_meta(npy_path: Path) -> dict:
    meta_path = npy_path.with_suffix(".meta.json")
    if not meta_path.exists():
        return {}
    try:
        with meta_path.open("r", encoding="utf-8") as file:
            return json.load(file)
    except Exception:
        return {}


def has_all_zero_frame(arr: np.ndarray) -> bool:
    return bool(np.any(np.all(np.isclose(arr, 0.0), axis=1)))


def audit_dataset(dataset_root: Path):
    REPORTS_DIR.mkdir(parents=True, exist_ok=True)
    per_label = {}
    per_device_label: dict[str, dict[str, int]] = defaultdict(lambda: defaultdict(int))
    invalid_files = []
    warnings = []

    for label in FSL_LABELS:
        label_dir = dataset_root / label
        count = 0
        signers = set()
        devices = set()
        device_counts = Counter()

        if label_dir.exists():
            for npy_path in sorted(label_dir.glob("*.npy")):
                try:
                    arr = np.load(npy_path, allow_pickle=False)
                except Exception as exc:
                    invalid_files.append({"path": str(npy_path), "label": label, "reason": f"unreadable:{exc}"})
                    continue

                reason = None
                if tuple(arr.shape) != FSL_EXPECTED_SHAPE:
                    reason = f"shape:{list(arr.shape)}"
                elif np.isnan(arr).any():
                    reason = "nan"
                elif has_all_zero_frame(arr):
                    reason = "all_zero_frame"

                if reason is not None:
                    invalid_files.append({"path": str(npy_path), "label": label, "reason": reason})
                    continue

                meta = load_meta(npy_path)
                device = str(meta.get("device_model") or "UNKNOWN_DEVICE")
                signer = str(meta.get("signer_id") or "UNKNOWN_SIGNER")
                count += 1
                devices.add(device)
                signers.add(signer)
                device_counts[device] += 1
                per_device_label[device][label] += 1

        if count > 0:
            device, device_count = device_counts.most_common(1)[0]
            share = float(device_count) / float(count)
            if share > 0.70:
                message = f"WARNING: label={label} device={device} dominates at {share * 100.0:.1f}% - diversity risk"
                print(message)
                warnings.append({"label": label, "device": device, "share": share, "type": "device_dominance"})

        if len(signers) < 3:
            message = f"WARNING: label={label} only {len(signers)} signers - generalization risk"
            print(message)
            warnings.append({"label": label, "signers": len(signers), "type": "low_signer_diversity"})

        minimum = FSL_MINIMUMS[label]
        preferred = FSL_PREFERRED[label]
        per_label[label] = {
            "sequence_count": count,
            "minimum": minimum,
            "preferred": preferred,
            "minimum_met": count >= minimum,
            "preferred_met": count >= preferred,
            "sequences_needed_to_minimum": max(0, minimum - count),
            "sequences_needed_to_preferred": max(0, preferred - count),
            "unique_signer_count": len(signers),
            "unique_signers": sorted(signers),
            "unique_device_count": len(devices),
            "unique_devices": sorted(devices),
            "per_device": dict(sorted(device_counts.items())),
        }

    device_diversity_report = build_device_diversity_report(per_label)
    report = {
        "dataset_root": str(dataset_root),
        "expected_shape": list(FSL_EXPECTED_SHAPE),
        "labels": list(FSL_LABELS),
        "per_label": per_label,
        "device_diversity_report": device_diversity_report,
        "per_device": {
            device: {label: int(labels.get(label, 0)) for label in FSL_LABELS}
            for device, labels in sorted(per_device_label.items())
        },
        "invalid_files": invalid_files,
        "warnings": warnings,
        "ready_minimum": all(per_label[label]["minimum_met"] for label in FSL_LABELS),
        "ready_preferred": all(per_label[label]["preferred_met"] for label in FSL_LABELS),
    }
    REPORT_PATH.write_text(json.dumps(report, indent=2), encoding="utf-8")
    DIVERSITY_REPORT_PATH.write_text(json.dumps(device_diversity_report, indent=2), encoding="utf-8")
    return report, REPORT_PATH


def build_device_diversity_report(per_label: dict) -> dict:
    labels = {}
    low_diversity = []
    for label in FSL_LABELS:
        item = per_label[label]
        total = int(item["sequence_count"])
        signer_count = int(item["unique_signer_count"])
        device_count = int(item["unique_device_count"])
        diversity_score = float((signer_count * device_count) / total) if total > 0 else 0.0
        status = "LOW_DIVERSITY" if diversity_score < 0.02 else "OK"
        if status == "LOW_DIVERSITY":
            low_diversity.append(label)
        labels[label] = {
            "sequence_count": total,
            "unique_device_models": item["unique_devices"],
            "unique_signer_ids": item["unique_signers"],
            "sequence_count_per_device": item["per_device"],
            "diversity_score": diversity_score,
            "status": status,
        }
    return {
        "labels": labels,
        "low_diversity_labels": low_diversity,
        "formula": "(unique_signers * unique_devices) / total_sequences",
    }


def yes_no(value: bool) -> str:
    return "YES" if value else "NO"


def print_ready_table(report: dict, report_path: Path) -> None:
    print("FSL dataset audit")
    print(f"Dataset: {report['dataset_root']}")
    print("")
    print(f"{'LABEL':<12} {'SEQUENCES':>9} {'MIN_MET':>8} {'PREF_MET':>8} {'SIGNERS':>8} {'DEVICES':>8}")
    for label in FSL_LABELS:
        item = report["per_label"][label]
        print(
            f"{label:<12} {item['sequence_count']:>9} {yes_no(item['minimum_met']):>8} "
            f"{yes_no(item['preferred_met']):>8} {item['unique_signer_count']:>8} {item['unique_device_count']:>8}"
        )
    print("")
    print(f"Invalid files   : {len(report['invalid_files'])}")
    print(f"Warnings        : {len(report['warnings'])}")
    print(f"Ready minimum   : {report['ready_minimum']}")
    print(f"Ready preferred : {report['ready_preferred']}")
    print(f"Report          : {report_path}")
    print("")
    print("Device diversity")
    for label in FSL_LABELS:
        item = report["device_diversity_report"]["labels"][label]
        signer_word = "signer" if len(item["unique_signer_ids"]) == 1 else "signers"
        device_word = "device" if len(item["unique_device_models"]) == 1 else "devices"
        print(
            f"{label}: {item['sequence_count']} seqs | {len(item['unique_signer_ids'])} {signer_word} | "
            f"{len(item['unique_device_models'])} {device_word} | diversity={item['diversity_score']:.3f} {item['status']}"
        )
    print(f"Diversity report: {DIVERSITY_REPORT_PATH}")


def main() -> None:
    parser = argparse.ArgumentParser(description="Audit FSL 20x162 feature samples.")
    parser.add_argument("--dataset", type=Path, default=DATASET_ROOT)
    args = parser.parse_args()

    report, report_path = audit_dataset(args.dataset)
    print_ready_table(report, report_path)


if __name__ == "__main__":
    main()
