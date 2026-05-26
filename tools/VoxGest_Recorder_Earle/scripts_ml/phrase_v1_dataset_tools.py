"""Merge and audit VoxGest phrase-v1 teammate feature datasets.

The scripts that import this module deliberately copy accepted `.npy` feature
samples into profile-specific external dataset folders. They never delete or
modify the incoming teammate folders.
"""

from __future__ import annotations

import csv
import hashlib
import json
import re
import shutil
from collections import Counter, defaultdict
from dataclasses import dataclass
from datetime import datetime
from pathlib import Path

import numpy as np


ROOT = Path(__file__).resolve().parents[1]
INCOMING_ROOT = ROOT / "external_datasets" / "team_incoming_recorded_features"
REPORTS_DIR = ROOT / "reports"

SKIP_PARTS = {
    "_rejected",
    "recorder_env",
    "__pycache__",
    ".git",
    "scripts_ml",
    "android_dry_run",
}
IGNORED_LABELS = {"IS", "ARE", "A", "DO", "ASK_NAME"}


@dataclass(frozen=True)
class PhraseDatasetConfig:
    profile: str
    feature_folder: str
    output_folder: str
    labels: tuple[str, ...]
    expected_shape: tuple[int, int]
    minimums: dict[str, int]
    preferred: dict[str, int] | None = None
    source_folders: tuple[str, ...] | None = None


ONEHAND162_PHRASE_V1 = PhraseDatasetConfig(
    profile="onehand162_phrase_v1",
    feature_folder="onehand162_phrase_v1_features",
    output_folder="onehand162_phrase_v1_features",
    labels=(
        "WHAT",
        "YOUR",
        "NAME",
        "MY",
        "YOU",
        "OKAY",
        "STUDENT",
        "WHERE",
        "LIVE",
        "NOTHING",
    ),
    expected_shape=(30, 162),
    minimums={
        "WHAT": 80,
        "YOUR": 80,
        "NAME": 80,
        "NOTHING": 120,
    },
    preferred={
        "WHAT": 120,
        "YOUR": 120,
        "NAME": 120,
        "NOTHING": 180,
    },
    source_folders=("onehand162_phrase_v1_features", "dataset_words_lstm"),
)


FULLSIGN225_PHRASE_V1 = PhraseDatasetConfig(
    profile="fullsign225_phrase_v1",
    feature_folder="fullsign225_phrase_v1_features",
    output_folder="fullsign225_phrase_v1_features",
    labels=(
        "WHAT",
        "YOUR",
        "NAME",
        "MY",
        "YOU",
        "OKAY",
        "STUDENT",
        "WHERE",
        "LIVE",
        "NOTHING",
    ),
    expected_shape=(30, 225),
    minimums={
        "WHAT": 80,
        "YOUR": 80,
        "NAME": 80,
        "MY": 80,
        "YOU": 80,
        "OKAY": 80,
        "STUDENT": 80,
        "WHERE": 80,
        "LIVE": 80,
        "NOTHING": 180,
    },
    source_folders=("fullsign225_phrase_v1_features", "fullsign225_manual16_features"),
)


def _now():
    return datetime.now().replace(microsecond=0).isoformat()


def _safe_name(text):
    text = re.sub(r"[^A-Za-z0-9_-]+", "_", str(text).strip().upper())
    return text.strip("_") or "UNKNOWN"


def _has_skip_part(path: Path):
    return any(part.lower() in SKIP_PARTS for part in path.parts)


def _find_feature_roots(config: PhraseDatasetConfig):
    if not INCOMING_ROOT.exists():
        return []
    roots = []
    for folder_name in config.source_folders or (config.feature_folder,):
        for path in INCOMING_ROOT.rglob(folder_name):
            if path.is_dir() and not _has_skip_part(path):
                roots.append(path)
    return sorted(set(roots))


def _infer_signer_and_batch(feature_root: Path):
    try:
        rel_parts = feature_root.relative_to(INCOMING_ROOT).parts
    except ValueError:
        rel_parts = feature_root.parts

    signer = _safe_name(rel_parts[0]) if rel_parts else "UNKNOWN"
    batch_parts = []
    for part in rel_parts[1:]:
        lower = part.lower()
        if lower in {"recorded_features", feature_root.name.lower()}:
            continue
        if lower in SKIP_PARTS:
            continue
        batch_parts.append(part)
    batch = _safe_name("_".join(batch_parts) or "DIRECT")
    return signer, batch


def _label_from_path(path: Path, labels: set[str]):
    for part in reversed(path.parts):
        upper = part.upper()
        if upper in labels or upper in IGNORED_LABELS:
            return upper
    return ""


def _digest(path: Path):
    return hashlib.sha1(str(path).encode("utf-8")).hexdigest()[:8]


def _load_shape(path: Path):
    arr = np.load(path, allow_pickle=False)
    return tuple(arr.shape)


def merge_dataset(config: PhraseDatasetConfig):
    out_root = ROOT / "external_datasets" / config.output_folder
    out_root.mkdir(parents=True, exist_ok=True)
    REPORTS_DIR.mkdir(parents=True, exist_ok=True)

    labels = set(config.labels)
    source_roots = _find_feature_roots(config)
    records = []
    copied_counts = Counter()
    signer_counts = Counter()
    signer_label_counts = Counter()
    ignored_labels = Counter()
    wrong_shape = []
    unreadable = []
    skipped = []
    dest_seen = set()

    for feature_root in source_roots:
        signer, batch = _infer_signer_and_batch(feature_root)
        label_indexes = Counter()
        for file_path in sorted(feature_root.rglob("*.npy")):
            if _has_skip_part(file_path):
                skipped.append({"path": str(file_path), "reason": "skip_folder"})
                continue

            label = _label_from_path(file_path, labels)
            if not label:
                skipped.append({"path": str(file_path), "reason": "unknown_label"})
                continue
            if label in IGNORED_LABELS:
                ignored_labels[label] += 1
                skipped.append({"path": str(file_path), "label": label, "reason": "ignored_label"})
                continue

            try:
                shape = _load_shape(file_path)
            except Exception as exc:
                unreadable.append({"path": str(file_path), "label": label, "error": str(exc)})
                continue

            if shape != config.expected_shape:
                wrong_shape.append({
                    "path": str(file_path),
                    "label": label,
                    "shape": list(shape),
                    "expected_shape": list(config.expected_shape),
                })
                continue

            label_indexes[label] += 1
            dest_name = (
                f"{signer}_{batch}_{label}_{label_indexes[label]:03d}_{_digest(file_path)}.npy"
            )
            dest_name = _safe_name(Path(dest_name).stem) + ".npy"
            dest_dir = out_root / label
            dest_dir.mkdir(parents=True, exist_ok=True)
            dest_path = dest_dir / dest_name
            while dest_path.name in dest_seen or dest_path.exists():
                label_indexes[label] += 1
                dest_path = dest_dir / (
                    f"{signer}_{batch}_{label}_{label_indexes[label]:03d}_{_digest(file_path)}.npy"
                )
            shutil.copy2(file_path, dest_path)
            dest_seen.add(dest_path.name)

            copied_counts[label] += 1
            signer_counts[signer] += 1
            signer_label_counts[(signer, label)] += 1
            records.append({
                "source_path": str(file_path),
                "source_root": str(feature_root),
                "signer": signer,
                "batch": batch,
                "label": label,
                "shape": list(shape),
                "dest_path": str(dest_path),
                "status": "copied",
            })

    summary = {
        "generated_at": _now(),
        "profile": config.profile,
        "incoming_root": str(INCOMING_ROOT),
        "output_root": str(out_root),
        "source_roots": [str(path) for path in source_roots],
        "expected_shape": list(config.expected_shape),
        "labels": list(config.labels),
        "copied_total": len(records),
        "copied_per_label": dict(copied_counts),
        "copied_per_signer": dict(signer_counts),
        "copied_per_signer_label": {
            f"{signer}/{label}": count
            for (signer, label), count in sorted(signer_label_counts.items())
        },
        "ignored_labels": dict(ignored_labels),
        "wrong_shape_count": len(wrong_shape),
        "wrong_shape_files": wrong_shape,
        "unreadable_count": len(unreadable),
        "unreadable_files": unreadable,
        "skipped_count": len(skipped),
        "skipped_files": skipped[:500],
    }
    _write_merge_reports(config, summary, records)
    print_merge_summary(summary)
    return summary


def audit_dataset(config: PhraseDatasetConfig):
    data_root = ROOT / "external_datasets" / config.output_folder
    REPORTS_DIR.mkdir(parents=True, exist_ok=True)
    labels = set(config.labels)

    per_label = Counter()
    per_signer = Counter()
    per_batch = Counter()
    per_signer_label = Counter()
    wrong_shape = []
    unreadable = []
    ignored_labels = Counter()
    files = []

    if data_root.exists():
        for file_path in sorted(data_root.rglob("*.npy")):
            if _has_skip_part(file_path):
                continue
            label = _label_from_path(file_path, labels)
            if label in IGNORED_LABELS:
                ignored_labels[label] += 1
                continue
            if label not in labels:
                continue
            signer = _safe_name(file_path.name.split("_", 1)[0])
            batch = _safe_name(file_path.name.split("_", 2)[1] if "_" in file_path.name else "UNKNOWN")
            try:
                shape = _load_shape(file_path)
            except Exception as exc:
                unreadable.append({"path": str(file_path), "label": label, "error": str(exc)})
                continue
            if shape != config.expected_shape:
                wrong_shape.append({
                    "path": str(file_path),
                    "label": label,
                    "shape": list(shape),
                    "expected_shape": list(config.expected_shape),
                })
                continue
            per_label[label] += 1
            per_signer[signer] += 1
            per_batch[batch] += 1
            per_signer_label[(signer, label)] += 1
            files.append({"path": str(file_path), "signer": signer, "batch": batch, "label": label})

    minimum_status = {
        label: {
            "count": int(per_label.get(label, 0)),
            "minimum": int(required),
            "ready": int(per_label.get(label, 0)) >= int(required),
        }
        for label, required in config.minimums.items()
    }
    preferred_status = {
        label: {
            "count": int(per_label.get(label, 0)),
            "preferred": int(required),
            "ready": int(per_label.get(label, 0)) >= int(required),
        }
        for label, required in (config.preferred or {}).items()
    }
    ready_minimum = (
        all(item["ready"] for item in minimum_status.values())
        and not wrong_shape
        and not unreadable
    )

    summary = {
        "generated_at": _now(),
        "profile": config.profile,
        "dataset_root": str(data_root),
        "expected_shape": list(config.expected_shape),
        "labels": list(config.labels),
        "per_label": {label: int(per_label.get(label, 0)) for label in config.labels},
        "per_signer": dict(per_signer),
        "per_batch": dict(per_batch),
        "per_signer_label": {
            f"{signer}/{label}": count
            for (signer, label), count in sorted(per_signer_label.items())
        },
        "ignored_labels": dict(ignored_labels),
        "wrong_shape_count": len(wrong_shape),
        "wrong_shape_files": wrong_shape,
        "unreadable_count": len(unreadable),
        "unreadable_files": unreadable,
        "minimum_status": minimum_status,
        "preferred_status": preferred_status,
        "ready_minimum": ready_minimum,
        "files_checked": len(files),
    }
    _write_audit_reports(config, summary)
    print_audit_summary(summary)
    return summary


def _write_json(path: Path, payload):
    path.write_text(json.dumps(payload, indent=2), encoding="utf-8")


def _write_merge_reports(config: PhraseDatasetConfig, summary, records):
    prefix = REPORTS_DIR / f"{config.profile}_merge"
    _write_json(prefix.with_suffix(".json"), summary)
    with prefix.with_suffix(".csv").open("w", encoding="utf-8", newline="") as f:
        fieldnames = ["status", "signer", "batch", "label", "shape", "source_path", "dest_path", "source_root"]
        writer = csv.DictWriter(f, fieldnames=fieldnames)
        writer.writeheader()
        for row in records:
            writer.writerow({key: row.get(key, "") for key in fieldnames})
    md = [
        f"# {config.profile} Merge Report",
        "",
        f"- Generated: `{summary['generated_at']}`",
        f"- Incoming root: `{summary['incoming_root']}`",
        f"- Output root: `{summary['output_root']}`",
        f"- Source roots found: `{len(summary['source_roots'])}`",
        f"- Copied files: `{summary['copied_total']}`",
        f"- Wrong-shape files: `{summary['wrong_shape_count']}`",
        f"- Unreadable files: `{summary['unreadable_count']}`",
        "",
        "## Copied Per Label",
        "",
        "| Label | Count |",
        "| --- | ---: |",
    ]
    for label in config.labels:
        md.append(f"| {label} | {summary['copied_per_label'].get(label, 0)} |")
    md += [
        "",
        "## Ignored Labels",
        "",
        ", ".join(f"{label}: {count}" for label, count in summary["ignored_labels"].items()) or "None",
        "",
        "## Source Roots",
        "",
    ]
    md += [f"- `{path}`" for path in summary["source_roots"]] or ["- None"]
    prefix.with_suffix(".md").write_text("\n".join(md) + "\n", encoding="utf-8")


def _write_audit_reports(config: PhraseDatasetConfig, summary):
    prefix = REPORTS_DIR / f"{config.profile}_audit"
    _write_json(prefix.with_suffix(".json"), summary)
    with prefix.with_suffix(".csv").open("w", encoding="utf-8", newline="") as f:
        fieldnames = ["label", "count", "minimum", "minimum_ready", "preferred", "preferred_ready"]
        writer = csv.DictWriter(f, fieldnames=fieldnames)
        writer.writeheader()
        for label in config.labels:
            minimum = summary["minimum_status"].get(label, {})
            preferred = summary["preferred_status"].get(label, {})
            writer.writerow({
                "label": label,
                "count": summary["per_label"].get(label, 0),
                "minimum": minimum.get("minimum", ""),
                "minimum_ready": minimum.get("ready", ""),
                "preferred": preferred.get("preferred", ""),
                "preferred_ready": preferred.get("ready", ""),
            })
    md = [
        f"# {config.profile} Audit Report",
        "",
        f"- Generated: `{summary['generated_at']}`",
        f"- Dataset: `{summary['dataset_root']}`",
        f"- Expected shape: `{tuple(summary['expected_shape'])}`",
        f"- Files checked: `{summary['files_checked']}`",
        f"- Ready minimum: `{summary['ready_minimum']}`",
        f"- Wrong-shape files: `{summary['wrong_shape_count']}`",
        f"- Unreadable files: `{summary['unreadable_count']}`",
        "",
        "## Per Label Counts",
        "",
        "| Label | Count | Minimum | Minimum Ready | Preferred | Preferred Ready |",
        "| --- | ---: | ---: | --- | ---: | --- |",
    ]
    for label in config.labels:
        min_info = summary["minimum_status"].get(label, {})
        pref_info = summary["preferred_status"].get(label, {})
        md.append(
            f"| {label} | {summary['per_label'].get(label, 0)} | "
            f"{min_info.get('minimum', '')} | {min_info.get('ready', '')} | "
            f"{pref_info.get('preferred', '')} | {pref_info.get('ready', '')} |"
        )
    md += [
        "",
        "## Per Signer Counts",
        "",
        "| Signer | Count |",
        "| --- | ---: |",
    ]
    for signer, count in sorted(summary["per_signer"].items()):
        md.append(f"| {signer} | {count} |")
    md += [
        "",
        "## Per Signer Per Label",
        "",
        "| Signer/Label | Count |",
        "| --- | ---: |",
    ]
    for key, count in sorted(summary["per_signer_label"].items()):
        md.append(f"| {key} | {count} |")
    md += [
        "",
        "## Ignored Labels",
        "",
        ", ".join(f"{label}: {count}" for label, count in summary["ignored_labels"].items()) or "None",
    ]
    prefix.with_suffix(".md").write_text("\n".join(md) + "\n", encoding="utf-8")


def print_merge_summary(summary):
    print("=" * 72)
    print(f"{summary['profile']} merge")
    print("=" * 72)
    print(f"Source roots : {len(summary['source_roots'])}")
    print(f"Copied       : {summary['copied_total']}")
    print(f"Wrong shape  : {summary['wrong_shape_count']}")
    print(f"Unreadable   : {summary['unreadable_count']}")
    print(f"Ignored      : {summary['ignored_labels'] or {}}")
    print(f"Output       : {summary['output_root']}")


def print_audit_summary(summary):
    print("=" * 72)
    print(f"{summary['profile']} audit")
    print("=" * 72)
    print(f"Dataset      : {summary['dataset_root']}")
    print(f"Files checked: {summary['files_checked']}")
    print(f"Ready minimum: {summary['ready_minimum']}")
    print(f"Wrong shape  : {summary['wrong_shape_count']}")
    print(f"Unreadable   : {summary['unreadable_count']}")
    for label, count in summary["per_label"].items():
        min_info = summary["minimum_status"].get(label)
        if min_info:
            print(f"  {label:<10} {count:>4} / {min_info['minimum']:<4} ready={min_info['ready']}")
        else:
            print(f"  {label:<10} {count:>4}")
