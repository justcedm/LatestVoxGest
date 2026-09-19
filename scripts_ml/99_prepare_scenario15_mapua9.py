"""Freeze and validate the existing canonical Mapua9 feature subset.

This does not decode videos or extract new landmarks. It verifies and reuses
the preserved MAPUA26 FullSign225 cache, then records an immutable,
path-independent split manifest and concise evidence in Git.
"""

from __future__ import annotations

import csv
import hashlib
import io
import json
from collections import Counter, defaultdict
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

import numpy as np

REPO_ROOT = Path(__file__).resolve().parents[1]
CONFIG_PATH = REPO_ROOT / "training_configs" / "scenario15_mapua9_v1.json"
REPORT_ROOT = REPO_ROOT / "reports" / "scenario15_counter_v1"
SPLIT_PATH = REPORT_ROOT / "MAPUA9_SPLIT_MANIFEST.csv"
SUMMARY_PATH = REPORT_ROOT / "MAPUA9_DATASET_SUMMARY.json"
SPLIT_FIELDS = [
    "record_id",
    "relative_path",
    "source_label",
    "class_index",
    "raw_video_sha256",
    "technical_status",
    "partition",
    "development_fold",
    "group_id",
    "feature_sha256",
]


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def atomic_text(path: Path, content: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_suffix(path.suffix + ".tmp")
    temporary.write_text(content, encoding="utf-8", newline="")
    temporary.replace(path)


def csv_content(rows: list[dict[str, str]]) -> str:
    output = io.StringIO(newline="")
    writer = csv.DictWriter(output, fieldnames=SPLIT_FIELDS, lineterminator="\n")
    writer.writeheader()
    writer.writerows(rows)
    return output.getvalue()


def load_csv(path: Path) -> list[dict[str, str]]:
    with path.open("r", encoding="utf-8-sig", newline="") as stream:
        return list(csv.DictReader(stream))


def validate_tensor(row: dict[str, str], config: dict[str, Any]) -> int:
    feature_path = Path(row["feature_path"])
    if not feature_path.is_file():
        raise RuntimeError(f"missing feature file for {row['record_id']}")
    if sha256_file(feature_path).lower() != row["feature_sha256"].lower():
        raise RuntimeError(f"feature SHA mismatch for {row['record_id']}")
    with np.load(feature_path, allow_pickle=False) as archive:
        required = {
            "complete_trajectory",
            "sequence_20",
            "sequence_32",
            "sequence_48",
            "metadata",
        }
        if not required.issubset(archive.files):
            raise RuntimeError(f"incomplete archive for {row['record_id']}: {archive.files}")
        complete = archive["complete_trajectory"]
        sequence = archive["sequence_48"]
        metadata = json.loads(str(archive["metadata"].item()))
    if complete.ndim != 2 or complete.shape[1] != 225:
        raise RuntimeError(f"invalid complete trajectory for {row['record_id']}: {complete.shape}")
    if sequence.shape != (48, 225) or sequence.dtype != np.float32:
        raise RuntimeError(
            f"invalid 48-frame tensor for {row['record_id']}: {sequence.shape} {sequence.dtype}"
        )
    if not np.isfinite(complete).all() or not np.isfinite(sequence).all():
        raise RuntimeError(f"non-finite feature values for {row['record_id']}")
    if metadata.get("feature_extractor_version") != config["feature_contract"]["version"]:
        raise RuntimeError(f"feature contract mismatch for {row['record_id']}")
    if str(metadata.get("mediapipe_version")) != config["feature_contract"]["mediapipe_version"]:
        raise RuntimeError(f"MediaPipe version mismatch for {row['record_id']}")
    if int(metadata.get("motion_boundary_frames", -1)) != config["feature_contract"]["motion_boundary_frames"]:
        raise RuntimeError(f"motion boundary mismatch for {row['record_id']}")
    return int(complete.shape[0])


def main() -> int:
    config = json.loads(CONFIG_PATH.read_text(encoding="utf-8"))
    labels = config["labels"]
    source_path = Path(config["dataset"]["source_feature_manifest"])
    source = [row for row in load_csv(source_path) if row["source_label"] in labels]
    expected = int(config["dataset"]["expected_clip_count"])
    if len(source) != expected:
        raise RuntimeError(f"source count changed: expected {expected}, got {len(source)}")
    if len({row["record_id"] for row in source}) != len(source):
        raise RuntimeError("duplicate record_id in selected source")
    if len({row["relative_path"] for row in source}) != len(source):
        raise RuntimeError("duplicate relative_path in selected source")
    if any(row["status"] not in {"PASS", "CACHED"} for row in source):
        raise RuntimeError("selected source contains failed extraction rows")
    if any(row["technical_status"] != "PASS" for row in source):
        raise RuntimeError("selected source contains non-PASS videos")

    rows: list[dict[str, str]] = []
    complete_lengths: list[int] = []
    ordered = sorted(
        source,
        key=lambda item: (labels.index(item["source_label"]), item["relative_path"]),
    )
    for row in ordered:
        complete_lengths.append(validate_tensor(row, config))
        rows.append(
            {
                "record_id": row["record_id"],
                "relative_path": row["relative_path"].replace("\\", "/"),
                "source_label": row["source_label"],
                "class_index": str(labels.index(row["source_label"])),
                "raw_video_sha256": row["raw_video_sha256"].lower(),
                "technical_status": row["technical_status"],
                "partition": row["partition"],
                "development_fold": row["development_fold"],
                "group_id": row["group_id"],
                "feature_sha256": row["feature_sha256"].lower(),
            }
        )

    assignments: dict[str, set[tuple[str, str]]] = defaultdict(set)
    for row in rows:
        assignments[row["group_id"]].add((row["partition"], row["development_fold"]))
    crossings = {group: values for group, values in assignments.items() if len(values) > 1}
    if crossings:
        raise RuntimeError(f"group leakage across partitions/folds: {crossings}")

    proposed = csv_content(rows)
    if SPLIT_PATH.exists() and SPLIT_PATH.read_text(encoding="utf-8") != proposed:
        raise RuntimeError("refusing to change frozen MAPUA9_SPLIT_MANIFEST.csv")
    if not SPLIT_PATH.exists():
        atomic_text(SPLIT_PATH, proposed)

    class_counts = Counter(row["source_label"] for row in rows)
    partition_counts = Counter(row["partition"] for row in rows)
    fold_counts = Counter(
        row["development_fold"] for row in rows if row["partition"] == "development"
    )
    if set(fold_counts) != {"0", "1", "2", "3"}:
        raise RuntimeError(f"unexpected development folds: {dict(fold_counts)}")
    summary = {
        "experiment_id": config["experiment_id"],
        "created_utc": datetime.now(timezone.utc).isoformat(),
        "source_cache_manifest_sha256": sha256_file(source_path),
        "split_manifest_sha256": sha256_file(SPLIT_PATH),
        "selected_clip_count": len(rows),
        "class_counts": dict(class_counts),
        "partition_counts": dict(partition_counts),
        "development_fold_counts": dict(sorted(fold_counts.items())),
        "technical_status_counts": dict(Counter(row["technical_status"] for row in rows)),
        "feature_status": "ALL_HASHED_FLOAT32_48X225_FINITE_AND_CONTRACT_MATCHED",
        "complete_trajectory_frame_range": [min(complete_lengths), max(complete_lengths)],
        "group_partition_or_fold_crossings": 0,
        "source_video_overlap_count": 0,
        "signer_overlap_claim": "NOT_MEASURABLE_SIGNER_IDS_UNAVAILABLE",
        "metric_scope": config["metric_scope"],
        "feature_contract": config["feature_contract"],
        "source_features_outside_git": True,
    }
    atomic_text(SUMMARY_PATH, json.dumps(summary, indent=2, sort_keys=True) + "\n")
    print(json.dumps(summary, indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
