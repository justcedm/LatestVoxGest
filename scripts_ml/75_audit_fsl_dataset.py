"""Audit FSL-105 OneHand162 data and emit leakage-safe split artifacts."""

from __future__ import annotations

import argparse
import csv
import json
from collections import Counter, defaultdict
from datetime import datetime, timezone
from pathlib import Path
from statistics import mean
from typing import Any

from fsl_dataset import (
    AUDIT_CSV_PATH,
    AUDIT_JSON_PATH,
    DATASET_VERSION,
    DEFAULT_DATASET_ROOT,
    MINIMUM_SEQUENCES_PER_LABEL,
    READINESS_DOC_PATH,
    ROOT,
    SPLIT_MANIFEST_PATH,
    assignment_digest,
    assign_grouped_splits,
    duplicate_report,
    inventory_dataset,
    load_confirmed_labels,
    write_json_atomic,
)
from voxgest_feature_builder import (
    EXPECTED_SEQUENCE_SHAPE,
    FEATURE_LAYOUT,
    FEATURE_VERSION,
    NORMALIZATION_POLICY,
)


HANDEDNESS_REPORT_PATH = ROOT / "reports" / "fsl" / "handedness_report.json"


def distribution(values: list[float]) -> dict[str, float]:
    return {
        "minimum": min(values) if values else 0.0,
        "mean": mean(values) if values else 0.0,
        "maximum": max(values) if values else 0.0,
    }


def _class_imbalance_summary(counts: dict[str, int]) -> dict[str, Any]:
    values = list(counts.values())
    minimum = min(values) if values else 0
    maximum = max(values) if values else 0
    return {
        "minimum_sequences": minimum,
        "maximum_sequences": maximum,
        "maximum_to_minimum_ratio": maximum / minimum if minimum else None,
        "counts": counts,
    }


def audit_unscoped_directories(dataset_root: Path, labels: list[str]) -> dict[str, Any]:
    """Report, but never mix, directories outside the confirmed FSL-105 scope."""
    rows: list[dict[str, Any]] = []
    if dataset_root.is_dir():
        for directory in sorted(path for path in dataset_root.iterdir() if path.is_dir()):
            if directory.name in labels:
                continue
            npy_files = sorted(directory.glob("*.npy"))
            rows.append(
                {
                    "label_directory": directory.name,
                    "sequence_count": len(npy_files),
                    "status": "EMPTY" if not npy_files else "EXCLUDED_UNSCOPED_NOT_MIXED",
                }
            )
    return {
        "directory_count": len(rows),
        "sequence_count": sum(row["sequence_count"] for row in rows),
        "directories": rows,
        "mixed_into_training": False,
    }


def write_split_manifest(records) -> None:
    SPLIT_MANIFEST_PATH.parent.mkdir(parents=True, exist_ok=True)
    fields = list(records[0].__dataclass_fields__) if records else []
    temporary = SPLIT_MANIFEST_PATH.with_suffix(".csv.tmp")
    with temporary.open("w", encoding="utf-8-sig", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=fields)
        writer.writeheader()
        for record in records:
            writer.writerow({field: getattr(record, field) for field in fields})
    temporary.replace(SPLIT_MANIFEST_PATH)


def write_audit_csv(rows: list[dict[str, Any]]) -> None:
    AUDIT_CSV_PATH.parent.mkdir(parents=True, exist_ok=True)
    temporary = AUDIT_CSV_PATH.with_suffix(".csv.tmp")
    fields = [
        "label",
        "total_sequences",
        "signer_count",
        "source_video_count",
        "train_sequences",
        "val_sequences",
        "test_sequences",
        "pose_presence_minimum",
        "pose_presence_mean",
        "hand_presence_minimum",
        "hand_presence_mean",
        "minimum_required",
        "minimum_met",
    ]
    with temporary.open("w", encoding="utf-8-sig", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=fields)
        writer.writeheader()
        writer.writerows(rows)
    temporary.replace(AUDIT_CSV_PATH)


def write_readiness_doc(report: dict[str, Any]) -> None:
    readiness = report["readiness"]
    split = report["split"]
    lines = [
        "# FSL-105 Dataset Readiness",
        "",
        f"READY_FOR_TRAINING = {readiness['ready_for_training']}",
        f"READY_FOR_DEPLOYMENT = {readiness['ready_for_deployment']}",
        "",
        "## Audited contract",
        "",
        f"- Dataset version: `{report['dataset_version']}`",
        f"- Feature version: `{report['feature_contract']['feature_version']}`",
        f"- Shape: `{report['feature_contract']['shape']}` float32",
        f"- Layout: pose `{report['feature_contract']['layout']['pose']}`, selected hand `{report['feature_contract']['layout']['selected_hand']}`",
        f"- Source-valid sequences: {report['source_valid_sequence_count']:,}",
        f"- Training-eligible sequences after duplicate exclusion: {report['training_eligible_sequence_count']:,}",
        f"- Exact duplicate-content sequences excluded: {report['excluded_duplicate_sequence_count']:,}",
        f"- Invalid sequences: {len(report['invalid_files']):,}",
        f"- Confirmed one-handed labels: {report['label_count']}",
        f"- FSL-105 videos processed: {report['total_videos']:,}",
        f"- Videos contributing eligible sequences: {report['videos_with_eligible_sequences']:,}",
        "",
        "## Split and leakage proof",
        "",
        f"- Method: `{split['method']}`",
        f"- Group key: `{split['actual_group_key']}` (no window-level random split)",
        f"- Train/validation/test sequences: {split['sequence_counts'].get('train', 0):,} / {split['sequence_counts'].get('val', 0):,} / {split['sequence_counts'].get('test', 0):,}",
        f"- All labels present in all splits: {split['all_labels_covered']}",
        f"- Signer overlap count: {split['leakage']['signer_overlap_count']}",
        f"- Source-video overlap count: {split['leakage']['source_video_overlap_count']}",
        "",
        "## Readiness decision",
        "",
    ]
    lines.extend(f"- {reason}" for reason in readiness["reasons"])
    lines.extend(
        [
            "",
            "## Deployment warning",
            "",
            "This audit can authorize a closed-set experimental training run only. "
            "FSL-105 contains no canonical `NSAC`/background class, so the "
            "existing rejection policy must remain active and no Android default "
            "model or thresholds may be replaced from these results alone.",
            "",
            "## Provenance note",
            "",
            report["provenance"]["contract_proof"]["inference"],
            "",
        ]
    )
    READINESS_DOC_PATH.parent.mkdir(parents=True, exist_ok=True)
    temporary = READINESS_DOC_PATH.with_suffix(".md.tmp")
    temporary.write_text("\n".join(lines), encoding="utf-8")
    temporary.replace(READINESS_DOC_PATH)


def run_audit(dataset_root: Path) -> dict[str, Any]:
    labels = load_confirmed_labels()
    unscoped = audit_unscoped_directories(dataset_root, labels)
    records, invalid, provenance = inventory_dataset(dataset_root)
    duplicates = duplicate_report(records)
    duplicate_exclusions = set(duplicates["excluded_paths"])
    eligible_records = [record for record in records if record.path not in duplicate_exclusions]
    post_exclusion_duplicates = duplicate_report(eligible_records)
    duplicates["post_exclusion"] = {
        "duplicate_path_count": post_exclusion_duplicates["duplicate_path_count"],
        "duplicate_source_window_count": post_exclusion_duplicates["duplicate_source_window_count"],
        "duplicate_content_hash_count": post_exclusion_duplicates["duplicate_content_hash_count"],
        "passed": post_exclusion_duplicates["source_is_duplicate_free"],
    }
    assigned, split_info = assign_grouped_splits(eligible_records, labels)
    split_info["assignment_sha256"] = assignment_digest(assigned)

    by_label = defaultdict(list)
    for record in assigned:
        by_label[record.label].append(record)
    csv_rows: list[dict[str, Any]] = []
    per_label: dict[str, Any] = {}
    below_minimum: list[str] = []
    for label in labels:
        rows = by_label[label]
        split_counts = Counter(record.split for record in rows)
        pose = distribution([record.pose_presence_ratio for record in rows])
        hand = distribution([record.hand_presence_ratio for record in rows])
        minimum_met = len(rows) >= MINIMUM_SEQUENCES_PER_LABEL
        if not minimum_met:
            below_minimum.append(label)
        per_label[label] = {
            "total_sequences": len(rows),
            "signer_count": len({record.signer_id for record in rows}),
            "source_video_count": len({record.source_video for record in rows}),
            "split_counts": dict(split_counts),
            "pose_presence": pose,
            "hand_presence": hand,
            "minimum_required": MINIMUM_SEQUENCES_PER_LABEL,
            "minimum_met": minimum_met,
        }
        csv_rows.append(
            {
                "label": label,
                "total_sequences": len(rows),
                "signer_count": per_label[label]["signer_count"],
                "source_video_count": per_label[label]["source_video_count"],
                "train_sequences": split_counts.get("train", 0),
                "val_sequences": split_counts.get("val", 0),
                "test_sequences": split_counts.get("test", 0),
                "pose_presence_minimum": pose["minimum"],
                "pose_presence_mean": pose["mean"],
                "hand_presence_minimum": hand["minimum"],
                "hand_presence_mean": hand["mean"],
                "minimum_required": MINIMUM_SEQUENCES_PER_LABEL,
                "minimum_met": minimum_met,
            }
        )

    try:
        handedness = json.loads(HANDEDNESS_REPORT_PATH.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError):
        handedness = {}
    handedness_counts = {
        "one_handed": len(handedness.get("one_handed", [])),
        "two_handed": len(handedness.get("two_handed", [])),
        "ambiguous": len(handedness.get("ambiguous", [])),
    }
    train_ready = (
        bool(assigned)
        and not invalid
        and not below_minimum
        and provenance["contract_proof"]["verified"]
        and provenance["inventory_matches_extraction_summary"]
        and duplicates["post_exclusion"]["passed"]
        and split_info["all_labels_covered"]
        and split_info["leakage"]["passed"]
    )
    reasons = [
        f"Feature provenance verified: {provenance['contract_proof']['verified']}.",
        f"Inventory matches extraction summary: {provenance['inventory_matches_extraction_summary']}.",
        f"All {len(labels)} labels meet {MINIMUM_SEQUENCES_PER_LABEL} sequences: {not below_minimum}.",
        f"All train/validation/test label coverage complete: {split_info['all_labels_covered']}.",
        f"Signer/source-video leakage audit passed: {split_info['leakage']['passed']}.",
        f"Exact duplicate-content records excluded from training: {len(duplicate_exclusions)}.",
        f"Duplicate audit passed after exclusion: {duplicates['post_exclusion']['passed']}.",
        "Deployment remains blocked because no NSAC/background class is present and cross-device validation is pending.",
    ]
    report = {
        "generated_at_utc": datetime.now(timezone.utc).isoformat(),
        "dataset_root": str(dataset_root.absolute()),
        "dataset_version": DATASET_VERSION,
        "feature_contract": {
            "feature_version": FEATURE_VERSION,
            "shape": list(EXPECTED_SEQUENCE_SHAPE),
            "dtype": "float32",
            "layout": FEATURE_LAYOUT,
            "normalization": NORMALIZATION_POLICY,
            "handedness_policy": "fixed anatomical right hand for FSL-105 extraction",
        },
        "label_count": len(labels),
        "labels": labels,
        "missing_classes": [label for label in labels if not by_label[label]],
        "valid_sequence_count": len(assigned),
        "source_valid_sequence_count": len(records),
        "training_eligible_sequence_count": len(assigned),
        "excluded_duplicate_sequence_count": len(duplicate_exclusions),
        "invalid_files": invalid,
        "labels_below_minimum": below_minimum,
        "unscoped_directories": unscoped,
        "source_counts": dict(sorted(Counter(record.source for record in assigned).items())),
        "device_counts": dict(sorted(Counter(record.device_model for record in assigned).items())),
        "signer_count": len({record.signer_id for record in assigned}),
        "source_video_count": len({record.source_video for record in assigned}),
        "total_videos": provenance["contract_proof"]["summary_completed_video_count"],
        "videos_with_eligible_sequences": len({record.source_video for record in assigned}),
        "class_imbalance": _class_imbalance_summary(
            {label: len(by_label[label]) for label in labels}
        ),
        "presence": {
            "pose": distribution([record.pose_presence_ratio for record in assigned]),
            "selected_hand": distribution([record.hand_presence_ratio for record in assigned]),
        },
        "per_label": per_label,
        "handedness_classification": handedness_counts,
        "provenance": provenance,
        "duplicates": duplicates,
        "split": split_info,
        "readiness": {
            "ready_for_training": "YES" if train_ready else "NO",
            "ready_for_deployment": "NO",
            "reasons": reasons,
        },
        "artifacts": {
            "audit_json": str(AUDIT_JSON_PATH),
            "audit_csv": str(AUDIT_CSV_PATH),
            "split_manifest": str(SPLIT_MANIFEST_PATH),
            "readiness_doc": str(READINESS_DOC_PATH),
        },
    }
    write_split_manifest(assigned)
    write_audit_csv(csv_rows)
    write_json_atomic(AUDIT_JSON_PATH, report)
    write_readiness_doc(report)
    return report


def refresh_existing_summary() -> dict[str, Any]:
    """Add derived summary fields without reopening disconnected source arrays."""
    report = json.loads(AUDIT_JSON_PATH.read_text(encoding="utf-8"))
    labels = list(report["labels"])
    counts = {
        label: int(report["per_label"][label]["total_sequences"])
        for label in labels
    }
    report["missing_classes"] = [label for label, count in counts.items() if count == 0]
    report["total_videos"] = int(
        report["provenance"]["contract_proof"]["summary_completed_video_count"]
    )
    report["videos_with_eligible_sequences"] = int(report["source_video_count"])
    report["class_imbalance"] = _class_imbalance_summary(counts)
    report["summary_refreshed_at_utc"] = datetime.now(timezone.utc).isoformat()
    write_json_atomic(AUDIT_JSON_PATH, report)
    write_readiness_doc(report)
    return report


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--dataset", type=Path, default=DEFAULT_DATASET_ROOT)
    parser.add_argument(
        "--summary-only",
        action="store_true",
        help="Refresh fields derived from the last completed audit without reopening arrays.",
    )
    args = parser.parse_args()
    report = refresh_existing_summary() if args.summary_only else run_audit(args.dataset)
    print("FSL-105 dataset audit complete")
    print(f"Valid sequences       : {report['valid_sequence_count']}")
    print(f"Invalid files         : {len(report['invalid_files'])}")
    print(f"Labels                : {report['label_count']}")
    print(f"Signer overlap        : {report['split']['leakage']['signer_overlap_count']}")
    print(f"Source-video overlap  : {report['split']['leakage']['source_video_overlap_count']}")
    print(f"READY_FOR_TRAINING    : {report['readiness']['ready_for_training']}")
    print(f"Report                : {AUDIT_JSON_PATH}")
    return 0 if report["readiness"]["ready_for_training"] == "YES" else 2


if __name__ == "__main__":
    raise SystemExit(main())
