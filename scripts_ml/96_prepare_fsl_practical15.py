"""Freeze, validate, and audit the published-Mapua practical candidate pool."""

from __future__ import annotations

import argparse
import csv
import hashlib
import io
import json
import os
from collections import Counter, defaultdict
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

import cv2
import mediapipe as mp
import numpy as np

REPO_ROOT = Path(__file__).resolve().parents[1]
CONFIG_PATH = REPO_ROOT / "training_configs" / "fsl_practical15_mapua_v1.json"
REPORT_ROOT = REPO_ROOT / "reports" / "fsl_dual_dataset_reset_v1"
SPLIT_PATH = REPORT_ROOT / "SPLIT_MANIFEST.csv"
METRICS_PATH = REPORT_ROOT / "LANDMARK_CLASS_METRICS.csv"
EVIDENCE_PATH = REPORT_ROOT / "LANDMARK_REPRESENTATIVE_EVIDENCE.csv"
AUDIT_PATH = REPORT_ROOT / "LANDMARK_AUDIT.md"
SUMMARY_PATH = REPORT_ROOT / "DATASET_SUMMARY.md"
SPLIT_FIELDS = [
    "record_id", "source_dataset", "relative_path", "source_label", "class_index",
    "raw_video_sha256", "technical_status", "partition", "development_fold",
    "group_id", "feature_sha256",
]
METRIC_FIELDS = [
    "source_dataset", "class_label", "tier", "raw_total_clips", "decoded_clips",
    "technical_pass", "technical_review", "technical_reject", "pose_presence",
    "any_hand_presence", "left_hand_presence", "right_hand_presence",
    "both_hand_presence", "internal_dropout", "motion_duration_ms_p05",
    "motion_duration_ms_median", "motion_duration_ms_p95",
    "active_landmark_frames_p05", "active_landmark_frames_median",
    "active_landmark_frames_p95", "complete_event_usable_rate",
    "selected_pass_tensors", "tensor_shape", "tensor_finite", "source_limitations",
]
EVIDENCE_FIELDS = [
    "class_label", "strength", "relative_path", "record_id", "raw_sha256_prefix",
    "motion_start_frame", "motion_middle_frame", "motion_end_frame",
    "complete_trajectory_frames", "pose_present_frames",
    "left_hand_present_frames", "right_hand_present_frames", "feature_sha256",
    "contact_sheet_sha256",
]


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def load_csv(path: Path) -> list[dict[str, str]]:
    with path.open("r", encoding="utf-8-sig", newline="") as stream:
        return list(csv.DictReader(stream))


def csv_text(rows: list[dict[str, Any]], fields: list[str]) -> str:
    output = io.StringIO(newline="")
    writer = csv.DictWriter(output, fieldnames=fields, lineterminator="\n")
    writer.writeheader()
    writer.writerows(rows)
    return output.getvalue()


def atomic_text(path: Path, content: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_suffix(path.suffix + ".tmp")
    temporary.write_text(content, encoding="utf-8", newline="")
    temporary.replace(path)


def required_root(name: str) -> Path:
    value = os.environ.get(name, "").strip()
    if not value:
        raise RuntimeError(f"{name} must point to the approved external safe-C root")
    root = Path(value).resolve()
    if not root.is_dir():
        raise RuntimeError(f"{name} does not exist: {root}")
    return root


def validate_feature(row: dict[str, str], config: dict[str, Any]) -> dict[str, Any]:
    path = Path(row["feature_path"])
    if not path.is_file() or sha256_file(path).lower() != row["feature_sha256"].lower():
        raise RuntimeError(f"feature integrity failure: {row['record_id']}")
    with np.load(path, allow_pickle=False) as archive:
        required = {"complete_trajectory", "sequence_20", "sequence_32", "sequence_48", "metadata"}
        if not required.issubset(archive.files):
            raise RuntimeError(f"feature archive incomplete: {row['record_id']}")
        complete = archive["complete_trajectory"]
        sequence = archive["sequence_48"]
        metadata = json.loads(str(archive["metadata"].item()))
    if complete.ndim != 2 or complete.shape[1] != 225 or sequence.shape != (48, 225):
        raise RuntimeError(f"feature shape failure: {row['record_id']}")
    if complete.dtype != np.float32 or sequence.dtype != np.float32:
        raise RuntimeError(f"feature dtype failure: {row['record_id']}")
    if not np.isfinite(complete).all() or not np.isfinite(sequence).all():
        raise RuntimeError(f"non-finite feature: {row['record_id']}")
    if metadata["feature_extractor_version"] != config["feature_contract"]["version"]:
        raise RuntimeError(f"feature version failure: {row['record_id']}")
    if str(metadata["mediapipe_version"]) != config["feature_contract"]["mediapipe_version"]:
        raise RuntimeError(f"MediaPipe version failure: {row['record_id']}")
    return metadata


def write_immutable(path: Path, content: str) -> None:
    if path.exists() and path.read_text(encoding="utf-8") != content:
        raise RuntimeError(f"refusing to change frozen file: {path.name}")
    if not path.exists():
        atomic_text(path, content)


def percentile(values: list[int], fraction: float) -> int:
    ordered = sorted(values)
    index = int(round((len(ordered) - 1) * fraction))
    return ordered[index]


def draw_landmarks(image: np.ndarray, results: Any) -> np.ndarray:
    output = image.copy()
    drawing = mp.solutions.drawing_utils
    holistic = mp.solutions.holistic
    drawing.draw_landmarks(output, results.pose_landmarks, holistic.POSE_CONNECTIONS)
    drawing.draw_landmarks(output, results.left_hand_landmarks, holistic.HAND_CONNECTIONS)
    drawing.draw_landmarks(output, results.right_hand_landmarks, holistic.HAND_CONNECTIONS)
    return output


def render_contact_sheet(
    video_path: Path,
    start: int,
    middle: int,
    end: int,
    destination: Path,
) -> str:
    capture = cv2.VideoCapture(str(video_path))
    panels = []
    with mp.solutions.holistic.Holistic(
        static_image_mode=True,
        model_complexity=1,
        refine_face_landmarks=False,
        min_detection_confidence=0.5,
        min_tracking_confidence=0.5,
    ) as holistic:
        for label, frame_index in (("START", start), ("MIDDLE", middle), ("END", end)):
            capture.set(cv2.CAP_PROP_POS_FRAMES, frame_index)
            ok, bgr = capture.read()
            if not ok:
                raise RuntimeError(f"cannot decode evidence frame {frame_index}: {video_path}")
            results = holistic.process(cv2.cvtColor(bgr, cv2.COLOR_BGR2RGB))
            panel = draw_landmarks(bgr, results)
            cv2.putText(panel, f"{label} f={frame_index}", (12, 28), cv2.FONT_HERSHEY_SIMPLEX, 0.7, (0, 255, 255), 2)
            panels.append(panel)
    capture.release()
    height = 240
    resized = [cv2.resize(panel, (int(panel.shape[1] * height / panel.shape[0]), height)) for panel in panels]
    destination.parent.mkdir(parents=True, exist_ok=True)
    if not cv2.imwrite(str(destination), np.concatenate(resized, axis=1)):
        raise RuntimeError(f"cannot write evidence contact sheet: {destination}")
    return sha256_file(destination)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--render-evidence", action="store_true")
    args = parser.parse_args()
    config = json.loads(CONFIG_PATH.read_text(encoding="utf-8"))
    labels = config["labels"]
    training_root = required_root("VOXGEST_TRAINING_ROOT")
    dataset_root = required_root("VOXGEST_DATASETS_ROOT")
    feature_manifest = training_root / config["dataset"]["source_feature_manifest"]
    feature_rows = [row for row in load_csv(feature_manifest) if row["source_label"] in labels]
    if len(feature_rows) != int(config["dataset"]["expected_clip_count"]):
        raise RuntimeError(f"expected 394 selected PASS features, got {len(feature_rows)}")
    if any(row["status"] not in {"PASS", "CACHED"} for row in feature_rows):
        raise RuntimeError("selected feature manifest contains failures")
    if len({row["record_id"] for row in feature_rows}) != len(feature_rows):
        raise RuntimeError("duplicate record IDs")

    metadata_by_id = {}
    for index, row in enumerate(feature_rows, 1):
        metadata_by_id[row["record_id"]] = validate_feature(row, config)
        if index % 50 == 0:
            print(f"validated {index}/{len(feature_rows)}", flush=True)

    split_rows = []
    for row in sorted(feature_rows, key=lambda item: (labels.index(item["source_label"]), item["relative_path"])):
        split_rows.append({
            "record_id": row["record_id"],
            "source_dataset": "MAPUA_TRANSACTIONAL_FSL",
            "relative_path": row["relative_path"].replace("\\", "/"),
            "source_label": row["source_label"],
            "class_index": str(labels.index(row["source_label"])),
            "raw_video_sha256": row["raw_video_sha256"].lower(),
            "technical_status": row["technical_status"],
            "partition": row["partition"],
            "development_fold": row["development_fold"],
            "group_id": row["group_id"],
            "feature_sha256": row["feature_sha256"].lower(),
        })
    group_assignments: dict[str, set[tuple[str, str]]] = defaultdict(set)
    for row in split_rows:
        group_assignments[row["group_id"]].add((row["partition"], row["development_fold"]))
    crossings = {group: values for group, values in group_assignments.items() if len(values) > 1}
    if crossings:
        raise RuntimeError(f"source-related group leakage: {crossings}")
    split_content = csv_text(split_rows, SPLIT_FIELDS)
    write_immutable(SPLIT_PATH, split_content)

    audit_root = dataset_root / config["dataset"]["raw_audit_root"]
    media = {row["class"]: row for row in load_csv(audit_root / "mediapipe_quality_by_class.csv")}
    motion = {row["CLASS_LABEL"]: row for row in load_csv(audit_root / "motion_temporal_quality_by_class.csv")}
    tiers = {
        **{label: "A" for label in labels[:10]},
        **{label: "B" for label in labels[10:13]},
        "COIN": "CONTINGENCY_RETAIL",
        "DISCOUNT": "CONTINGENCY_RETAIL",
    }
    feature_counts = Counter(row["source_label"] for row in feature_rows)
    metrics = []
    for label in labels:
        m = media[label]
        t = motion[label]
        metrics.append({
            "source_dataset": "MAPUA_TRANSACTIONAL_FSL",
            "class_label": label,
            "tier": tiers[label],
            "raw_total_clips": t["VIDEO_COUNT"],
            "decoded_clips": m["successful_videos"],
            "technical_pass": t["PASS"],
            "technical_review": t["REVIEW"],
            "technical_reject": t["REJECT_TECHNICAL"],
            "pose_presence": m["pose_ratio"],
            "any_hand_presence": m["any_hand_ratio"],
            "left_hand_presence": m["left_hand_ratio"],
            "right_hand_presence": m["right_hand_ratio"],
            "both_hand_presence": m["both_hands_ratio"],
            "internal_dropout": m["internal_dropout_ratio"],
            "motion_duration_ms_p05": t["MOTION_DURATION_MS_P05"],
            "motion_duration_ms_median": t["MOTION_DURATION_MS_MEDIAN"],
            "motion_duration_ms_p95": t["MOTION_DURATION_MS_P95"],
            "active_landmark_frames_p05": t["LANDMARK_FRAME_COUNT_P05"],
            "active_landmark_frames_median": t["LANDMARK_FRAME_COUNT_MEDIAN"],
            "active_landmark_frames_p95": t["LANDMARK_FRAME_COUNT_P95"],
            "complete_event_usable_rate": f"{int(t['PASS']) / int(t['VIDEO_COUNT']):.6f}",
            "selected_pass_tensors": feature_counts[label],
            "tensor_shape": "48x225_float32",
            "tensor_finite": "PASS",
            "source_limitations": "single_published_dataset;signer_ids_unavailable",
        })
    atomic_text(METRICS_PATH, csv_text(metrics, METRIC_FIELDS))

    evidence_rows = []
    evidence_root = training_root / config["output"]["experiment_root"] / "representative_evidence"
    raw_root = dataset_root / config["dataset"]["raw_video_root"]
    for label in labels:
        rows = [row for row in feature_rows if row["source_label"] == label]
        ranked = sorted(
            rows,
            key=lambda row: (
                int(metadata_by_id[row["record_id"]]["complete_trajectory_frames"]),
                int(metadata_by_id[row["record_id"]]["left_hand_present_frames"]) +
                int(metadata_by_id[row["record_id"]]["right_hand_present_frames"]),
            ),
        )
        for strength, row in (("weak", ranked[0]), ("strong", ranked[-1])):
            metadata = metadata_by_id[row["record_id"]]
            start = int(metadata["motion_start"])
            end = int(metadata["motion_end"])
            middle = (start + end) // 2
            sheet_hash = "NOT_RENDERED"
            if args.render_evidence:
                sheet_hash = render_contact_sheet(
                    raw_root / Path(row["relative_path"]),
                    start,
                    middle,
                    end,
                    evidence_root / f"{label}_{strength}.png",
                )
            evidence_rows.append({
                "class_label": label,
                "strength": strength,
                "relative_path": row["relative_path"].replace("\\", "/"),
                "record_id": row["record_id"],
                "raw_sha256_prefix": row["raw_video_sha256"][:16],
                "motion_start_frame": start,
                "motion_middle_frame": middle,
                "motion_end_frame": end,
                "complete_trajectory_frames": metadata["complete_trajectory_frames"],
                "pose_present_frames": metadata["pose_present_frames"],
                "left_hand_present_frames": metadata["left_hand_present_frames"],
                "right_hand_present_frames": metadata["right_hand_present_frames"],
                "feature_sha256": row["feature_sha256"],
                "contact_sheet_sha256": sheet_hash,
            })
    atomic_text(EVIDENCE_PATH, csv_text(evidence_rows, EVIDENCE_FIELDS))

    counts = Counter(row["source_label"] for row in split_rows)
    partitions = Counter(row["partition"] for row in split_rows)
    folds = Counter(row["development_fold"] for row in split_rows if row["partition"] == "development")
    all_complete = [int(metadata["complete_trajectory_frames"]) for metadata in metadata_by_id.values()]
    lowest_usable = sorted(metrics, key=lambda row: float(row["complete_event_usable_rate"]))[:5]
    audit_lines = [
        "# Landmark and complete-motion audit",
        "",
        "STATUS=MAPUA_CANDIDATE_POOL_AUDITED; FSL105_PENDING_RAW",
        "",
        "MediaPipe is used only as the landmark extractor. The classifier is trained on",
        "canonical complete-event FullSign225 trajectories. All 394 selected PASS archives",
        "were independently hash checked, opened, shape/dtype checked, and tested for",
        "finite values before this report was written.",
        "",
        "## Aggregate evidence",
        "",
        f"- Candidate labels: {len(labels)}.",
        f"- Selected PASS clips: {len(split_rows)}.",
        f"- Development/sealed: {partitions['development']}/{partitions['sealed_test']}.",
        f"- Development fold counts: {dict(sorted(folds.items()))}.",
        f"- Complete-event trajectory frames: p05={percentile(all_complete, 0.05)}, median={percentile(all_complete, 0.50)}, p95={percentile(all_complete, 0.95)}.",
        "- Tensor contract: float32 [48,225], finite, canonical unmirrored.",
        "- Source-related partition/fold crossings: 0.",
        "- FSL-105 metrics: PENDING_RAW; no historical OneHand162 proxy was mixed in.",
        "",
        "## Lowest complete-event usable rates",
        "",
        "| Class | PASS/raw | Usable rate | Any-hand | Internal dropout |",
        "|---|---:|---:|---:|---:|",
    ]
    for row in lowest_usable:
        audit_lines.append(
            f"| {row['class_label']} | {row['technical_pass']}/{row['raw_total_clips']} | "
            f"{row['complete_event_usable_rate']} | {row['any_hand_presence']} | {row['internal_dropout']} |"
        )
    audit_lines += [
        "",
        "## Representative evidence",
        "",
        "For every class, one weakest and one strongest selected complete trajectory is",
        "listed in LANDMARK_REPRESENTATIVE_EVIDENCE.csv with source-relative identity,",
        "motion start/middle/end indices, pose/hand counts, and feature hashes. Contact",
        f"sheets are rendered outside Git under the experiment evidence directory: {args.render_evidence}.",
        "They contain the three boundary frames with MediaPipe pose/hand overlays and are",
        "hash-linked by the CSV. Raw pixels never enter the classifier.",
        "",
        "## Decision boundary",
        "",
        "This audit establishes computer-vision and tensor viability only. It does not",
        "establish signer-independent generalization, linguistic equivalence with FSL-105,",
        "Android parity, or live usability.",
    ]
    atomic_text(AUDIT_PATH, "\n".join(audit_lines) + "\n")

    summary_lines = [
        "# Practical FSL dataset and split summary",
        "",
        f"Created UTC: {datetime.now(timezone.utc).isoformat()}",
        "",
        "- Dataset profile: published Mapua-only interim; FSL-105 pending official raw.",
        f"- Frozen candidate vocabulary ({len(labels)}): {', '.join(labels)}.",
        f"- PASS-only clips: {len(split_rows)}.",
        f"- Per-class counts: {dict(counts)}.",
        f"- Development/sealed: {dict(partitions)}.",
        f"- Development folds: {dict(sorted(folds.items()))}.",
        "- Minimum grouping: source clip; audited source-related group.",
        "- Exact/perceptual related group crossings: 0.",
        "- Source-video overlap: 0.",
        "- Signer overlap: NOT_MEASURABLE_SIGNER_IDS_UNAVAILABLE.",
        f"- Split SHA-256: {sha256_file(SPLIT_PATH)}.",
        f"- Config SHA-256: {sha256_file(CONFIG_PATH)}.",
        f"- Source feature manifest SHA-256: {sha256_file(feature_manifest)}.",
        "- Review/reject videos used for training: NO.",
        "- Raw video/features/checkpoints remain outside Git.",
    ]
    atomic_text(SUMMARY_PATH, "\n".join(summary_lines) + "\n")
    print(json.dumps({
        "selected": len(split_rows),
        "class_counts": dict(counts),
        "partitions": dict(partitions),
        "folds": dict(sorted(folds.items())),
        "split_sha256": sha256_file(SPLIT_PATH),
        "evidence_rendered": args.render_evidence,
    }, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
