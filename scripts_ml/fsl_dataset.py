"""Shared audited FSL-105 dataset inventory and leakage-safe split logic."""

from __future__ import annotations

import csv
import hashlib
import json
from collections import Counter, defaultdict
from dataclasses import asdict, dataclass
from pathlib import Path
from typing import Any, Iterable

import numpy as np

from voxgest_feature_builder import (
    EXPECTED_SEQUENCE_SHAPE,
    FEATURE_LAYOUT,
    FEATURE_VERSION,
    NORMALIZATION_POLICY,
    sequence_presence,
    validate_sequence,
)


ROOT = Path(__file__).resolve().parents[1]
DEFAULT_DATASET_ROOT = ROOT / "external_datasets" / "fsl_features"
CONFIRMED_LABELS_PATH = ROOT / "reports" / "fsl" / "confirmed_onehanded_labels.json"
EXTRACTION_SUMMARY_PATH = ROOT / "reports" / "fsl" / "extraction_summary.json"
AUDIT_JSON_PATH = ROOT / "reports" / "fsl105_dataset_audit.json"
AUDIT_CSV_PATH = ROOT / "reports" / "fsl105_dataset_audit.csv"
SPLIT_MANIFEST_PATH = ROOT / "reports" / "fsl105_split_manifest.csv"
READINESS_DOC_PATH = ROOT / "docs" / "FSL105_DATASET_READINESS.md"
DATASET_VERSION = "fsl105_train64_onehand162_v2_audit1"
RANDOM_SEED = 42
MINIMUM_SEQUENCES_PER_LABEL = 100
FSL105_DEVICE = "FSL105_VIDEO"


@dataclass(frozen=True)
class SampleRecord:
    path: str
    meta_path: str
    label: str
    signer_id: str
    recording_session: str
    source_video: str
    device_model: str
    source: str
    feature_version: str
    feature_version_source: str
    content_sha256: str
    pose_presence_ratio: float
    hand_presence_ratio: float
    split: str = ""


def read_json(path: Path) -> Any:
    return json.loads(path.read_text(encoding="utf-8"))


def write_json_atomic(path: Path, payload: Any) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_suffix(path.suffix + ".tmp")
    temporary.write_text(
        json.dumps(payload, indent=2, ensure_ascii=False) + "\n",
        encoding="utf-8",
    )
    temporary.replace(path)


def project_relative(path: Path) -> str:
    absolute = path.absolute()
    return str(absolute.relative_to(ROOT)) if absolute.is_relative_to(ROOT) else str(absolute)


def resolve_project_path(value: str) -> Path:
    path = Path(value)
    return path if path.is_absolute() else ROOT / path


def load_confirmed_labels() -> list[str]:
    payload = read_json(CONFIRMED_LABELS_PATH)
    if not isinstance(payload, list) or not all(isinstance(value, str) for value in payload):
        raise ValueError("confirmed labels must be a JSON string array")
    if len(payload) != len(set(payload)):
        raise ValueError("confirmed labels contain duplicates")
    return payload


def verify_legacy_fsl105_contract() -> dict[str, Any]:
    """Verify that unversioned FSL-105 sidecars came from equivalent math."""
    summary = read_json(EXTRACTION_SUMMARY_PATH)
    config = summary.get("config", {})
    checks = {
        "source_is_fsl105": summary.get("source") == "fsl105",
        "sequence_length_is_20": summary.get("sequence_length") == 20,
        "feature_size_is_162": summary.get("feature_size") == 162,
        "shape_is_20x162": summary.get("expected_shape") == [20, 162],
        "config_feature_width_is_162": config.get("feature_size") == 162,
        "source_partition_is_train_csv_only": summary.get("split") == "train.csv_only",
        "right_hand_minimum_is_13": summary.get("minimum_right_hand_frames") == 13,
        "wrist_mcp_scale_is_0001": config.get("wrist_mcp_scale_threshold") == 0.001,
        "z_damping_is_03": config.get("z_damping") == 0.3,
        "shape_mismatches_zero": not summary.get("shape_mismatches"),
        "metadata_errors_zero": not summary.get("metadata_errors"),
        "output_integrity_clean": all(
            not summary.get("output_integrity", {}).get(key)
            for key in ("missing_output_files", "unexpected_output_files", "orphan_metadata")
        ),
    }
    return {
        "verified": all(checks.values()),
        "checks": checks,
        "summary_total_sequences": int(summary.get("total_sequences", 0)),
        "summary_train_video_count": int(summary.get("train_video_count", 0)),
        "summary_completed_video_count": int(summary.get("completed_video_count", 0)),
        "inference": (
            "Unversioned FSL-105 sidecars are accepted only because the committed "
            "extraction summary and feature-builder parity test prove equivalent "
            "pose[0:33]+right-hand[0:21] normalization math."
        ),
    }


def inventory_dataset(dataset_root: Path) -> tuple[list[SampleRecord], list[dict[str, str]], dict[str, Any]]:
    labels = load_confirmed_labels()
    contract_proof = verify_legacy_fsl105_contract()
    records: list[SampleRecord] = []
    invalid: list[dict[str, str]] = []
    missing_version_count = 0

    for label in labels:
        label_dir = dataset_root / label
        if not label_dir.is_dir():
            invalid.append({"path": project_relative(label_dir), "label": label, "reason": "missing_label_directory"})
            continue
        for npy_path in sorted(label_dir.glob("*.npy")):
            relative = project_relative(npy_path)
            meta_path = npy_path.with_suffix(".meta.json")
            try:
                array = np.load(npy_path, mmap_mode="r", allow_pickle=False)
            except (OSError, ValueError) as exc:
                invalid.append({"path": relative, "label": label, "reason": f"unreadable:{exc}"})
                continue
            issues = validate_sequence(array)
            if issues:
                invalid.append({"path": relative, "label": label, "reason": ";".join(issues)})
                continue
            try:
                meta = read_json(meta_path)
            except (OSError, json.JSONDecodeError) as exc:
                invalid.append({"path": relative, "label": label, "reason": f"invalid_meta:{exc}"})
                continue
            if not isinstance(meta, dict):
                invalid.append({"path": relative, "label": label, "reason": "invalid_meta:not_object"})
                continue

            meta_label = str(meta.get("label", "")).strip()
            if meta_label != label:
                invalid.append({"path": relative, "label": label, "reason": f"meta_label:{meta_label}"})
                continue
            source = str(meta.get("source", "")).strip().lower()
            device = str(meta.get("device_model", "")).strip()
            signer = str(meta.get("signer_id", "")).strip()
            source_video = str(meta.get("source_video", "")).strip().replace("\\", "/")
            if source != "fsl105" or device != FSL105_DEVICE:
                invalid.append({"path": relative, "label": label, "reason": "not_fsl105_owned"})
                continue
            if not signer or not source_video:
                invalid.append({"path": relative, "label": label, "reason": "missing_signer_or_source_video"})
                continue

            version = str(meta.get("feature_version", "")).strip()
            if version:
                if version != FEATURE_VERSION:
                    invalid.append({"path": relative, "label": label, "reason": f"feature_version:{version}"})
                    continue
                version_source = "sidecar"
            elif contract_proof["verified"]:
                version = FEATURE_VERSION
                version_source = "inferred_verified_extractor_provenance"
                missing_version_count += 1
            else:
                invalid.append({"path": relative, "label": label, "reason": "missing_unverifiable_feature_version"})
                continue

            pose_ratio, hand_ratio = sequence_presence(array)
            content_sha256 = hashlib.sha256(
                np.ascontiguousarray(array).tobytes()
            ).hexdigest()
            records.append(
                SampleRecord(
                    path=relative,
                    meta_path=project_relative(meta_path),
                    label=label,
                    signer_id=signer,
                    recording_session=str(meta.get("recording_session") or source_video),
                    source_video=source_video,
                    device_model=device,
                    source=source,
                    feature_version=version,
                    feature_version_source=version_source,
                    content_sha256=content_sha256,
                    pose_presence_ratio=pose_ratio,
                    hand_presence_ratio=hand_ratio,
                )
            )

    provenance = {
        "contract_proof": contract_proof,
        "explicit_feature_version_count": len(records) - missing_version_count,
        "inferred_feature_version_count": missing_version_count,
        "inventory_matches_extraction_summary": (
            len(records) == contract_proof["summary_total_sequences"]
        ),
    }
    return records, invalid, provenance


def _stable_tiebreak(value: str) -> str:
    return hashlib.sha256(f"{RANDOM_SEED}:{value}".encode("utf-8")).hexdigest()


def _choose_cover_groups(
    candidates: set[str],
    group_labels: dict[str, set[str]],
    group_sizes: dict[str, int],
    required_labels: set[str],
    target_samples: int,
) -> set[str]:
    selected: set[str] = set()
    covered: set[str] = set()
    sample_count = 0
    while covered != required_labels:
        choices = [group for group in candidates - selected if group_labels[group] - covered]
        if not choices:
            break
        best = min(
            choices,
            key=lambda group: (
                -len(group_labels[group] - covered),
                abs((sample_count + group_sizes[group]) - target_samples),
                _stable_tiebreak(group),
            ),
        )
        selected.add(best)
        covered.update(group_labels[best])
        sample_count += group_sizes[best]
    while sample_count < target_samples and candidates - selected:
        best = min(
            candidates - selected,
            key=lambda group: (
                abs((sample_count + group_sizes[group]) - target_samples),
                _stable_tiebreak(group),
            ),
        )
        if abs((sample_count + group_sizes[best]) - target_samples) > abs(sample_count - target_samples):
            break
        selected.add(best)
        sample_count += group_sizes[best]
    return selected


def assign_grouped_splits(records: list[SampleRecord], labels: list[str]) -> tuple[list[SampleRecord], dict[str, Any]]:
    by_signer: dict[str, list[SampleRecord]] = defaultdict(list)
    for record in records:
        by_signer[record.signer_id].append(record)
    all_groups = set(by_signer)
    required_labels = set(labels)
    group_labels = {group: {record.label for record in rows} for group, rows in by_signer.items()}
    group_sizes = {group: len(rows) for group, rows in by_signer.items()}
    total = len(records)

    test_groups = _choose_cover_groups(
        all_groups, group_labels, group_sizes, required_labels, round(total * 0.15)
    )
    val_groups = _choose_cover_groups(
        all_groups - test_groups,
        group_labels,
        group_sizes,
        required_labels,
        round(total * 0.15),
    )
    train_groups = all_groups - test_groups - val_groups
    split_groups = {"train": train_groups, "val": val_groups, "test": test_groups}

    assigned: list[SampleRecord] = []
    for record in records:
        split = next(name for name, groups in split_groups.items() if record.signer_id in groups)
        assigned.append(SampleRecord(**{**asdict(record), "split": split}))

    coverage = {
        split: sorted({record.label for record in assigned if record.split == split})
        for split in split_groups
    }
    split_counts = Counter(record.split for record in assigned)
    split_label_counts = {
        split: dict(sorted(Counter(record.label for record in assigned if record.split == split).items()))
        for split in split_groups
    }
    signer_overlap = {
        "train_val": sorted(train_groups & val_groups),
        "train_test": sorted(train_groups & test_groups),
        "val_test": sorted(val_groups & test_groups),
    }
    videos = {
        split: {record.source_video for record in assigned if record.split == split}
        for split in split_groups
    }
    source_video_overlap = {
        "train_val": sorted(videos["train"] & videos["val"]),
        "train_test": sorted(videos["train"] & videos["test"]),
        "val_test": sorted(videos["val"] & videos["test"]),
    }
    leakage = {
        "signer_overlap": signer_overlap,
        "source_video_overlap": source_video_overlap,
        "signer_overlap_count": sum(len(values) for values in signer_overlap.values()),
        "source_video_overlap_count": sum(len(values) for values in source_video_overlap.values()),
        "passed": not any(signer_overlap.values()) and not any(source_video_overlap.values()),
    }
    split_info = {
        "method": "global_signer_grouped_70_15_15_greedy_label_coverage",
        "seed": RANDOM_SEED,
        "group_hierarchy": ["signer_id", "recording_session", "source_video"],
        "actual_group_key": "signer_id",
        "groups": {split: sorted(groups) for split, groups in split_groups.items()},
        "sequence_counts": dict(split_counts),
        "label_counts": split_label_counts,
        "label_coverage": coverage,
        "all_labels_covered": all(set(values) == required_labels for values in coverage.values()),
        "leakage": leakage,
    }
    return assigned, split_info


def duplicate_report(records: Iterable[SampleRecord]) -> dict[str, Any]:
    """Find duplicate paths, source windows, and exact feature-array content."""
    paths = Counter(record.path for record in records)
    source_windows = Counter(
        (
            record.label,
            record.source_video,
            Path(record.path).stem.rsplit("_w", 1)[-1],
        )
        for record in records
    )
    content_hashes: dict[str, list[str]] = defaultdict(list)
    for record in records:
        content_hashes[record.content_sha256].append(record.path)
    duplicate_paths = sorted(path for path, count in paths.items() if count > 1)
    duplicate_source_windows = [
        {"label": key[0], "source_video": key[1], "window": key[2], "count": count}
        for key, count in source_windows.items()
        if count > 1
    ]
    duplicate_content_all = [
        {"sha256": digest, "count": len(paths_for_hash), "paths": sorted(paths_for_hash)}
        for digest, paths_for_hash in content_hashes.items()
        if len(paths_for_hash) > 1
    ]
    duplicate_content_all.sort(key=lambda row: (-row["count"], row["sha256"]))
    excluded_paths = sorted(
        path
        for row in duplicate_content_all
        for path in row["paths"]
    )
    duplicate_content_report = [
        {**row, "paths": row["paths"][:100]}
        for row in duplicate_content_all[:100]
    ]
    return {
        "policy": "duplicate_path_source_window_and_exact_float32_content_sha256",
        "duplicate_path_count": len(duplicate_paths),
        "duplicate_source_window_count": len(duplicate_source_windows),
        "duplicate_content_hash_count": len(duplicate_content_all),
        "duplicate_paths": duplicate_paths[:100],
        "duplicate_source_windows": duplicate_source_windows[:100],
        "duplicate_content": duplicate_content_report,
        "excluded_paths": excluded_paths,
        "source_is_duplicate_free": not duplicate_paths and not duplicate_source_windows and not duplicate_content_all,
        "safe_after_exclusion": not duplicate_paths and not duplicate_source_windows,
    }


def assignment_digest(records: Iterable[SampleRecord]) -> str:
    """Hash the ordered sample/label/group/split assignment used by trainers."""
    digest = hashlib.sha256()
    for record in records:
        digest.update(
            json.dumps(
                [record.path, record.label, record.signer_id, record.source_video, record.split],
                ensure_ascii=False,
                separators=(",", ":"),
            ).encode("utf-8")
        )
        digest.update(b"\n")
    return digest.hexdigest()


def load_split_manifest(path: Path = SPLIT_MANIFEST_PATH) -> list[SampleRecord]:
    records: list[SampleRecord] = []
    with path.open("r", encoding="utf-8-sig", newline="") as handle:
        for row in csv.DictReader(handle):
            records.append(
                SampleRecord(
                    path=row["path"],
                    meta_path=row["meta_path"],
                    label=row["label"],
                    signer_id=row["signer_id"],
                    recording_session=row["recording_session"],
                    source_video=row["source_video"],
                    device_model=row["device_model"],
                    source=row["source"],
                    feature_version=row["feature_version"],
                    feature_version_source=row["feature_version_source"],
                    content_sha256=row["content_sha256"],
                    pose_presence_ratio=float(row["pose_presence_ratio"]),
                    hand_presence_ratio=float(row["hand_presence_ratio"]),
                    split=row["split"],
                )
            )
    return records
