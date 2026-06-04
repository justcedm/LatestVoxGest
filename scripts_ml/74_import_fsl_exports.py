"""Import FSL Android calibration JSON exports into 20x162 NPY features."""

from __future__ import annotations

import argparse
import json
import re
from collections import Counter, defaultdict
from datetime import datetime
from pathlib import Path

import numpy as np

from fsl_config import FSL_EXPECTED_SHAPE, FSL_LABELS, FSL_SEQUENCE_LENGTH


ROOT = Path(__file__).resolve().parents[1]
SOURCE_ROOT = ROOT / "external_datasets" / "fsl_phone_exports" / "VoxGestCalibration" / "fsl_phrase_v1"
OUTPUT_ROOT = ROOT / "external_datasets" / "fsl_features"
REPORTS_DIR = ROOT / "reports" / "fsl"
DEVICE_REGISTRY_PATH = REPORTS_DIR / "device_registry.json"


def safe_name(value: object) -> str:
    text = re.sub(r"[^A-Za-z0-9_-]+", "_", str(value).strip())
    return text.strip("_") or "sample"


def relative_text(path: Path) -> str:
    return str(path.relative_to(ROOT)) if path.is_relative_to(ROOT) else str(path)


def unique_path(path: Path) -> Path:
    if not path.exists():
        return path
    for index in range(2, 100_000):
        candidate = path.with_name(f"{path.stem}_{index}{path.suffix}")
        if not candidate.exists():
            return candidate
    raise RuntimeError(f"Could not create unique path for {path}")


def iter_exports(source_root: Path):
    if not source_root.exists():
        return
    for path in sorted(source_root.rglob("*.json")):
        if path.name.endswith(".meta.json"):
            continue
        yield path


def load_export(path: Path):
    with path.open("r", encoding="utf-8") as file:
        data = json.load(file)
    label = str(data.get("label") or path.parent.name).strip().upper()
    arr = np.asarray(data.get("feature_array"), dtype=np.float32)
    return data, label, arr


def load_device_registry() -> dict[str, list[str]]:
    if not DEVICE_REGISTRY_PATH.exists():
        return {}
    try:
        with DEVICE_REGISTRY_PATH.open("r", encoding="utf-8") as file:
            raw = json.load(file)
    except Exception:
        return {}
    registry: dict[str, list[str]] = {}
    for device, stems in raw.items():
        if isinstance(stems, list):
            registry[str(device)] = [str(stem) for stem in stems]
    return registry


def save_device_registry(registry: dict[str, list[str]]) -> None:
    REPORTS_DIR.mkdir(parents=True, exist_ok=True)
    ordered = {device: sorted(set(stems)) for device, stems in sorted(registry.items())}
    DEVICE_REGISTRY_PATH.write_text(json.dumps(ordered, indent=2), encoding="utf-8")


def signer_id_for(path: Path, data: dict) -> str:
    signer_id = str(data.get("signer_id", "")).strip()
    if signer_id:
        return safe_name(signer_id)
    return safe_name(path.stem.split("_", 1)[0])


def skip(skipped: list[dict], path: Path, reason: str) -> None:
    print(f"skipped: {reason} path={path}")
    skipped.append({"path": str(path), "reason": reason})


def import_exports(source_root: Path, output_root: Path):
    output_root.mkdir(parents=True, exist_ok=True)
    REPORTS_DIR.mkdir(parents=True, exist_ok=True)

    imported: list[dict] = []
    skipped: list[dict] = []
    counts = Counter()
    per_device = Counter()
    skipped_reasons = Counter()
    registry = load_device_registry()

    for path in iter_exports(source_root) or []:
        try:
            data, label, arr = load_export(path)
        except Exception as exc:
            reason = f"unreadable:{exc}"
            skip(skipped, path, reason)
            skipped_reasons[reason] += 1
            continue

        if label not in FSL_LABELS:
            reason = f"unsupported_label:{label}"
            skip(skipped, path, reason)
            skipped_reasons[reason] += 1
            continue

        if tuple(arr.shape) == (30, FSL_EXPECTED_SHAPE[1]):
            reason = "legacy_30frame"
            skip(skipped, path, reason)
            skipped_reasons[reason] += 1
            continue

        sequence_length_at_export = data.get("sequence_length_at_export")
        if sequence_length_at_export is not None:
            try:
                found_sequence_length = int(sequence_length_at_export)
            except (TypeError, ValueError):
                found_sequence_length = sequence_length_at_export
            if found_sequence_length != FSL_SEQUENCE_LENGTH:
                reason = f"sequence_length_mismatch expected={FSL_SEQUENCE_LENGTH} found={found_sequence_length}"
                skip(skipped, path, reason)
                skipped_reasons[reason] += 1
                continue

        if tuple(arr.shape) != FSL_EXPECTED_SHAPE:
            reason = f"shape:{list(arr.shape)}"
            skip(skipped, path, reason)
            skipped_reasons[reason] += 1
            continue

        label_dir = output_root / label
        label_dir.mkdir(parents=True, exist_ok=True)
        stem = safe_name(path.stem)
        npy_path = unique_path(label_dir / f"{stem}.npy")
        meta_path = npy_path.with_suffix(".meta.json")
        device_model = safe_name(data.get("device_model") or "UNKNOWN_DEVICE")
        signer_id = signer_id_for(path, data)

        np.save(npy_path, arr.astype(np.float32), allow_pickle=False)
        meta = {
            "source_json": relative_text(path),
            "label": label,
            "timestamp": data.get("timestamp"),
            "device_model": device_model,
            "device_session_tag": data.get("device_session_tag"),
            "signer_id": signer_id,
            "fsl_mode": bool(data.get("fsl_mode", True)),
            "active_profile": data.get("active_profile"),
            "feature_profile": data.get("feature_profile"),
            "input_shape": data.get("input_shape"),
            "sequence_length_at_export": data.get("sequence_length_at_export"),
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
        per_device[device_model] += 1
        registry.setdefault(device_model, [])
        if npy_path.stem not in registry[device_model]:
            registry[device_model].append(npy_path.stem)
        imported.append(
            {
                "source": str(path),
                "npy": str(npy_path),
                "meta": str(meta_path),
                "label": label,
                "device_model": device_model,
                "signer_id": signer_id,
            }
        )

    save_device_registry(registry)
    report = {
        "source_root": str(source_root),
        "output_root": str(output_root),
        "expected_shape": list(FSL_EXPECTED_SHAPE),
        "labels": list(FSL_LABELS),
        "imported_count": len(imported),
        "counts": {label: int(counts.get(label, 0)) for label in FSL_LABELS},
        "per_device": dict(sorted(per_device.items())),
        "skipped_count": len(skipped),
        "skipped_reasons": dict(sorted(skipped_reasons.items())),
        "skipped": skipped,
        "device_registry": str(DEVICE_REGISTRY_PATH),
    }
    report_path = REPORTS_DIR / "fsl_import_report.json"
    report_path.write_text(json.dumps(report, indent=2), encoding="utf-8")
    return report, report_path


def print_summary(report: dict, report_path: Path) -> None:
    print("FSL import complete")
    print(f"Imported: {report['imported_count']}")
    print(f"Skipped : {report['skipped_count']}")
    print("")
    print("Per-label count")
    for label in FSL_LABELS:
        print(f"{label:<12} {report['counts'].get(label, 0)}")
    print("")
    print("Per-device count")
    if report["per_device"]:
        for device, count in report["per_device"].items():
            print(f"{device:<24} {count}")
    else:
        print("NONE                     0")
    print("")
    print("Skipped reasons")
    if report["skipped_reasons"]:
        for reason, count in report["skipped_reasons"].items():
            print(f"{reason:<48} {count}")
    else:
        print("NONE                                             0")
    print(f"Report  : {report_path}")
    print(f"Registry: {DEVICE_REGISTRY_PATH}")


def main() -> None:
    parser = argparse.ArgumentParser(description="Import FSL Android calibration JSON exports.")
    parser.add_argument("--source", type=Path, default=SOURCE_ROOT)
    parser.add_argument("--output", type=Path, default=OUTPUT_ROOT)
    args = parser.parse_args()

    report, report_path = import_exports(args.source, args.output)
    print_summary(report, report_path)


if __name__ == "__main__":
    main()
