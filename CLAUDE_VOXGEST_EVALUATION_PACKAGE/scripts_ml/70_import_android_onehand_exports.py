"""Import Samsung/Android onehand162 calibration JSON exports.

Input:
    external_datasets/android_phone_exports/VoxGestCalibration/onehand162_phrase_v1/

Output:
    external_datasets/android_onehand162_phrase_v1_features/<LABEL>/*.npy
    external_datasets/android_onehand162_phrase_v1_features/<LABEL>/*.meta.json
"""

from __future__ import annotations

import argparse
import json
import re
from collections import Counter
from datetime import datetime
from pathlib import Path

import numpy as np


ROOT = Path(__file__).resolve().parents[1]
SOURCE_ROOT = ROOT / "external_datasets" / "android_phone_exports" / "VoxGestCalibration" / "onehand162_phrase_v1"
OUTPUT_ROOT = ROOT / "external_datasets" / "android_onehand162_phrase_v1_features"
REPORTS_DIR = ROOT / "reports"
LABELS = ("WHAT", "YOUR", "NAME", "MY", "NOTHING")
EXPECTED_SHAPE = (30, 162)


def safe_name(value: object) -> str:
    text = re.sub(r"[^A-Za-z0-9_-]+", "_", str(value).strip())
    return text.strip("_") or "sample"


def unique_path(path: Path) -> Path:
    if not path.exists():
        return path
    for idx in range(2, 100_000):
        candidate = path.with_name(f"{path.stem}_{idx}{path.suffix}")
        if not candidate.exists():
            return candidate
    raise RuntimeError(f"Could not create unique path for {path}")


def iter_exports(source_root: Path):
    for path in sorted(source_root.rglob("*.json")):
        if path.name.endswith(".meta.json"):
            continue
        yield path


def load_export(path: Path):
    with path.open("r", encoding="utf-8") as f:
        data = json.load(f)
    label = str(data.get("label", "")).strip().upper()
    arr = np.asarray(data.get("feature_array"), dtype=np.float32)
    return data, label, arr


def import_exports(source_root: Path, output_root: Path):
    output_root.mkdir(parents=True, exist_ok=True)
    REPORTS_DIR.mkdir(parents=True, exist_ok=True)

    imported = []
    skipped = []
    counts = Counter()

    for path in iter_exports(source_root):
        try:
            data, label, arr = load_export(path)
        except Exception as exc:
            skipped.append({"path": str(path), "reason": f"unreadable: {exc}"})
            continue

        if label not in LABELS:
            skipped.append({"path": str(path), "reason": f"unsupported_label:{label}"})
            continue
        if tuple(arr.shape) != EXPECTED_SHAPE:
            skipped.append({"path": str(path), "reason": f"shape:{list(arr.shape)}"})
            continue

        label_dir = output_root / label
        label_dir.mkdir(parents=True, exist_ok=True)
        stem = safe_name(path.stem)
        npy_path = unique_path(label_dir / f"{stem}.npy")
        meta_path = npy_path.with_suffix(".meta.json")

        np.save(npy_path, arr.astype(np.float32), allow_pickle=False)
        meta = {
            "source_json": str(path.relative_to(ROOT)) if path.is_relative_to(ROOT) else str(path),
            "label": label,
            "timestamp": data.get("timestamp"),
            "device_model": data.get("device_model"),
            "active_profile": data.get("active_profile"),
            "feature_profile": data.get("feature_profile"),
            "input_shape": data.get("input_shape"),
            "dominant_hand": data.get("dominant_hand"),
            "mirrored_input": data.get("mirrored_input"),
            "selected_hand_slot": data.get("selected_hand_slot"),
            "hand_presence_ratio": data.get("hand_presence_ratio"),
            "missing_pose_count": data.get("missing_pose_count"),
            "missing_hand_count": data.get("missing_hand_count"),
            "motion_score": data.get("motion_score"),
            "wrist_path": data.get("wrist_path"),
            "imported_at": datetime.now().isoformat(timespec="seconds"),
        }
        meta_path.write_text(json.dumps(meta, indent=2), encoding="utf-8")
        counts[label] += 1
        imported.append({"source": str(path), "npy": str(npy_path), "meta": str(meta_path), "label": label})

    report = {
        "source_root": str(source_root),
        "output_root": str(output_root),
        "expected_shape": list(EXPECTED_SHAPE),
        "labels": list(LABELS),
        "imported_count": len(imported),
        "counts": dict(counts),
        "skipped_count": len(skipped),
        "skipped": skipped,
    }
    report_path = REPORTS_DIR / "android_onehand162_import_report.json"
    report_path.write_text(json.dumps(report, indent=2), encoding="utf-8")
    return report, report_path


def main():
    parser = argparse.ArgumentParser(description="Import Android onehand162 calibration JSON exports.")
    parser.add_argument("--source", type=Path, default=SOURCE_ROOT)
    parser.add_argument("--output", type=Path, default=OUTPUT_ROOT)
    args = parser.parse_args()

    report, report_path = import_exports(args.source, args.output)
    print("Android onehand162 import complete")
    print(f"Source : {args.source}")
    print(f"Output : {args.output}")
    print(f"Imported: {report['imported_count']}")
    print(f"Skipped : {report['skipped_count']}")
    print(f"Counts  : {report['counts']}")
    print(f"Report  : {report_path}")


if __name__ == "__main__":
    main()
