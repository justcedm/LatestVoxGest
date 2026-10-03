"""Audit imported Android onehand162 calibration feature samples."""

from __future__ import annotations

import argparse
import json
from collections import defaultdict
from pathlib import Path

import numpy as np


ROOT = Path(__file__).resolve().parents[1]
DATASET_ROOT = ROOT / "external_datasets" / "android_onehand162_phrase_v1_features"
REPORTS_DIR = ROOT / "reports"
LABELS = ("WHAT", "YOUR", "NAME", "MY", "NOTHING")
EXPECTED_SHAPE = (30, 162)
MINIMUMS = {"MY": 50, "WHAT": 50, "YOUR": 50, "NAME": 50, "NOTHING": 100}
PREFERRED = {"MY": 80, "WHAT": 80, "YOUR": 80, "NAME": 80, "NOTHING": 150}


def mean(values):
    clean = [float(v) for v in values if v is not None]
    return float(sum(clean) / len(clean)) if clean else 0.0


def load_meta(npy_path: Path):
    meta_path = npy_path.with_suffix(".meta.json")
    if not meta_path.exists():
        return {}
    try:
        with meta_path.open("r", encoding="utf-8") as f:
            return json.load(f)
    except Exception:
        return {}


def audit_dataset(dataset_root: Path):
    REPORTS_DIR.mkdir(parents=True, exist_ok=True)
    per_label = {}
    unreadable = []
    wrong_shape = []

    for label in LABELS:
        label_dir = dataset_root / label
        stats = defaultdict(list)
        count = 0
        if label_dir.exists():
            for npy_path in sorted(label_dir.glob("*.npy")):
                try:
                    arr = np.load(npy_path, allow_pickle=False)
                except Exception as exc:
                    unreadable.append({"path": str(npy_path), "label": label, "error": str(exc)})
                    continue
                if tuple(arr.shape) != EXPECTED_SHAPE:
                    wrong_shape.append({"path": str(npy_path), "label": label, "shape": list(arr.shape)})
                    continue
                count += 1
                meta = load_meta(npy_path)
                for key in ("missing_pose_count", "missing_hand_count", "hand_presence_ratio", "motion_score", "wrist_path"):
                    stats[key].append(meta.get(key))

        per_label[label] = {
            "count": count,
            "minimum": MINIMUMS[label],
            "preferred": PREFERRED[label],
            "missing_minimum": max(0, MINIMUMS[label] - count),
            "missing_preferred": max(0, PREFERRED[label] - count),
            "missing_pose_average": mean(stats["missing_pose_count"]),
            "missing_hand_average": mean(stats["missing_hand_count"]),
            "hand_presence_average": mean(stats["hand_presence_ratio"]),
            "motion_average": mean(stats["motion_score"]),
            "wrist_path_average": mean(stats["wrist_path"]),
        }

    ready_minimum = all(per_label[label]["count"] >= MINIMUMS[label] for label in LABELS)
    ready_preferred = all(per_label[label]["count"] >= PREFERRED[label] for label in LABELS)
    report = {
        "dataset_root": str(dataset_root),
        "expected_shape": list(EXPECTED_SHAPE),
        "labels": list(LABELS),
        "per_label": per_label,
        "ready_minimum": ready_minimum,
        "ready_preferred": ready_preferred,
        "unreadable_files": unreadable,
        "wrong_shape_files": wrong_shape,
    }
    report_path = REPORTS_DIR / "android_onehand162_audit_report.json"
    report_path.write_text(json.dumps(report, indent=2), encoding="utf-8")
    return report, report_path


def main():
    parser = argparse.ArgumentParser(description="Audit Android onehand162 imported feature samples.")
    parser.add_argument("--dataset", type=Path, default=DATASET_ROOT)
    args = parser.parse_args()

    report, report_path = audit_dataset(args.dataset)
    print("Android onehand162 audit")
    print(f"Dataset: {args.dataset}")
    for label in LABELS:
        item = report["per_label"][label]
        print(
            f"{label:7s} count={item['count']:4d} min={item['minimum']:3d} "
            f"hand={item['hand_presence_average']:.3f} motion={item['motion_average']:.6f} "
            f"missing_pose={item['missing_pose_average']:.2f} missing_hand={item['missing_hand_average']:.2f}"
        )
    print(f"Ready minimum  : {report['ready_minimum']}")
    print(f"Ready preferred: {report['ready_preferred']}")
    print(f"Unreadable     : {len(report['unreadable_files'])}")
    print(f"Wrong shape    : {len(report['wrong_shape_files'])}")
    print(f"Report         : {report_path}")


if __name__ == "__main__":
    main()
