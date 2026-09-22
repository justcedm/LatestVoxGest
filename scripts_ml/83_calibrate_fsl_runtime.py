"""Calibrate experimental RD-TCN acceptance gates on validation data only."""

from __future__ import annotations

import hashlib
import json
import re
from collections import defaultdict
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

import numpy as np

from fsl_dataset import (
    ROOT,
    assignment_digest,
    load_split_manifest,
    resolve_project_path,
    write_json_atomic,
)
from fsl_train_common import prepare_feature_matrix, require_ready_audit
from voxgest_feature_builder import (
    EXPECTED_SEQUENCE_SHAPE,
    FEATURE_LAYOUT,
    FEATURE_VERSION,
    NORMALIZATION_POLICY,
)


MODEL_PATH = ROOT / "model" / "experimental" / "voxgest_fsl_rdtcn_v2.keras"
MODEL_FLOAT16_PATH = (
    ROOT / "model" / "experimental" / "voxgest_fsl_rdtcn_v2_float16.tflite"
)
ACTIVITY_MODEL_PATH = ROOT / "model" / "voxgest_activity_detector_v1.tflite"
ACTIVITY_REPORT_PATH = ROOT / "reports" / "activity_detector_v1_evaluation.json"
MODEL_REPORT_PATH = ROOT / "reports" / "fsl_rdtcn_evaluation.json"
CALIBRATION_REPORT_PATH = ROOT / "reports" / "fsl_rejection_calibration.json"
RUNTIME_MANIFEST_PATH = ROOT / "model" / "runtime_manifest.json"
CONFIDENCE_CANDIDATES = [round(value, 2) for value in np.arange(0.50, 1.00, 0.05)]
MARGIN_CANDIDATES = [round(value, 2) for value in np.arange(0.00, 1.00, 0.05)]
COOLDOWN_CANDIDATES = [6, 8, 10, 12]


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def acceptance_metrics(
    probabilities: np.ndarray,
    targets: np.ndarray,
    confidence_threshold: float,
    margin_threshold: float,
) -> dict[str, Any]:
    predictions = np.argmax(probabilities, axis=1)
    sorted_probabilities = np.sort(probabilities, axis=1)
    confidence = sorted_probabilities[:, -1]
    margin = sorted_probabilities[:, -1] - sorted_probabilities[:, -2]
    correct = predictions == targets
    accepted = (confidence >= confidence_threshold) & (margin >= margin_threshold)
    true_accept = int(np.sum(correct & accepted))
    false_accept = int(np.sum(~correct & accepted))
    false_reject = int(np.sum(correct & ~accepted))
    true_reject = int(np.sum(~correct & ~accepted))
    precision = (
        true_accept / (true_accept + false_accept)
        if true_accept + false_accept
        else 0.0
    )
    recall = (
        true_accept / (true_accept + false_reject)
        if true_accept + false_reject
        else 0.0
    )
    f1 = 2.0 * precision * recall / (precision + recall) if precision + recall else 0.0
    return {
        "confidence_threshold": confidence_threshold,
        "margin_threshold": margin_threshold,
        "true_accept": true_accept,
        "false_accept": false_accept,
        "false_reject": false_reject,
        "true_reject": true_reject,
        "accepted_count": int(np.sum(accepted)),
        "rejected_count": int(np.sum(~accepted)),
        "acceptance_precision": precision,
        "acceptance_recall": recall,
        "acceptance_f1": f1,
    }


def select_best(rows: list[dict[str, Any]], threshold_key: str) -> dict[str, Any]:
    """Maximize validation acceptance F1 with deterministic safe tie-breaks."""
    return max(
        rows,
        key=lambda row: (
            row["acceptance_f1"],
            row["acceptance_precision"],
            row["acceptance_recall"],
            -row[threshold_key],
        ),
    )


def read_window_start(record: Any) -> int:
    try:
        payload = json.loads(resolve_project_path(record.meta_path).read_text(encoding="utf-8"))
        if "window_start_frame" in payload:
            return int(payload["window_start_frame"])
    except (OSError, ValueError, TypeError, json.JSONDecodeError):
        pass
    match = re.search(r"_w(\d+)$", Path(record.path).stem)
    return int(match.group(1)) * 5 if match else 0


def cooldown_metrics(
    records: list[Any],
    probabilities: np.ndarray,
    confidence_threshold: float,
    margin_threshold: float,
    cooldown_frames: int,
) -> dict[str, Any]:
    predictions = np.argmax(probabilities, axis=1)
    sorted_probabilities = np.sort(probabilities, axis=1)
    confidence = sorted_probabilities[:, -1]
    margin = sorted_probabilities[:, -1] - sorted_probabilities[:, -2]
    accepted = (confidence >= confidence_threshold) & (margin >= margin_threshold)
    streams: dict[str, list[tuple[int, int]]] = defaultdict(list)
    for record, prediction, is_accepted in zip(records, predictions, accepted):
        if is_accepted:
            streams[record.source_video].append(
                (read_window_start(record), int(prediction))
            )

    candidate_count = 0
    emitted_count = 0
    suppressed_count = 0
    duplicate_count = 0
    for events in streams.values():
        emitted_by_label: dict[int, int] = {}
        for frame_number, prediction in sorted(events):
            candidate_count += 1
            previous_frame = emitted_by_label.get(prediction)
            if (
                previous_frame is not None
                and frame_number - previous_frame <= cooldown_frames
            ):
                suppressed_count += 1
                continue
            if previous_frame is not None:
                duplicate_count += 1
            emitted_by_label[prediction] = frame_number
            emitted_count += 1
    duplicate_rate = duplicate_count / emitted_count if emitted_count else 0.0
    return {
        "cooldown_frames": cooldown_frames,
        "accepted_candidates": candidate_count,
        "emitted_count": emitted_count,
        "suppressed_count": suppressed_count,
        "duplicate_output_count": duplicate_count,
        "duplicate_output_rate": duplicate_rate,
        "stream_count": len(streams),
        "simulation": (
            "validation windows grouped by source video and ordered by source frame; "
            "same-token emissions within cooldown are suppressed"
        ),
    }


def main() -> int:
    import tensorflow as tf

    audit = require_ready_audit()
    records = load_split_manifest()
    assignment_sha256 = assignment_digest(records)
    if assignment_sha256 != audit["split"]["assignment_sha256"]:
        raise SystemExit("split manifest does not match audited assignment")
    model_report = json.loads(MODEL_REPORT_PATH.read_text(encoding="utf-8"))
    labels = list(model_report["labels"])
    if model_report["split_assignment_sha256"] != assignment_sha256:
        raise SystemExit("RD-TCN report split hash does not match audit")
    if model_report["feature_version"] != FEATURE_VERSION:
        raise SystemExit("RD-TCN report feature version does not match builder")

    features = prepare_feature_matrix(records, smoke=False)
    label_to_index = {label: index for index, label in enumerate(labels)}
    validation_indices = np.asarray(
        [index for index, record in enumerate(records) if record.split == "val"],
        dtype=np.int64,
    )
    validation_records = [records[index] for index in validation_indices]
    x_validation = np.asarray(features[validation_indices], dtype=np.float32)
    y_validation = np.asarray(
        [label_to_index[record.label] for record in validation_records],
        dtype=np.int64,
    )

    model = tf.keras.models.load_model(MODEL_PATH)
    probabilities = model.predict(x_validation, batch_size=64, verbose=1).astype(np.float32)
    if probabilities.shape != (len(validation_records), len(labels)):
        raise RuntimeError(f"unexpected RD-TCN output shape: {probabilities.shape}")

    confidence_rows = [
        acceptance_metrics(probabilities, y_validation, threshold, 0.0)
        for threshold in CONFIDENCE_CANDIDATES
    ]
    selected_confidence = select_best(confidence_rows, "confidence_threshold")
    confidence_threshold = float(selected_confidence["confidence_threshold"])

    margin_rows = [
        acceptance_metrics(probabilities, y_validation, confidence_threshold, threshold)
        for threshold in MARGIN_CANDIDATES
    ]
    selected_margin = select_best(margin_rows, "margin_threshold")
    margin_threshold = float(selected_margin["margin_threshold"])

    cooldown_rows = [
        cooldown_metrics(
            validation_records,
            probabilities,
            confidence_threshold,
            margin_threshold,
            cooldown,
        )
        for cooldown in COOLDOWN_CANDIDATES
    ]
    selected_cooldown = min(
        cooldown_rows,
        key=lambda row: (row["duplicate_output_rate"], row["cooldown_frames"]),
    )
    cooldown_frames = int(selected_cooldown["cooldown_frames"])

    activity_report = json.loads(ACTIVITY_REPORT_PATH.read_text(encoding="utf-8"))
    generated_at = datetime.now(timezone.utc).isoformat()
    report = {
        "generated_at_utc": generated_at,
        "status": "experimental_validation_only_calibration",
        "model_version": "rdtcn_v2",
        "feature_version": FEATURE_VERSION,
        "validation_sequence_count": len(validation_records),
        "split_assignment_sha256": assignment_sha256,
        "selection_policy": {
            "confidence": "maximize acceptance F1 over 0.50..0.95 in 0.05 steps",
            "margin": (
                "with selected confidence fixed, maximize acceptance F1 over "
                "0.00..0.95 in 0.05 steps"
            ),
            "cooldown": (
                "minimize duplicate-output rate over 6, 8, 10, 12 frames; "
                "choose the shortest cooldown on ties"
            ),
            "test_set_used_for_calibration": False,
        },
        "confidence_sweep": confidence_rows,
        "selected_confidence": selected_confidence,
        "margin_sweep": margin_rows,
        "selected_margin": selected_margin,
        "cooldown_sweep": cooldown_rows,
        "selected_cooldown": selected_cooldown,
        "limitations": [
            "Cooldown is simulated from stored windows at five-frame extraction stride, not a continuous live camera session.",
            "Acceptance calibration has no real no-sign validation recordings.",
            "The activity detector did not meet its >95% synthetic validation target.",
        ],
    }
    write_json_atomic(CALIBRATION_REPORT_PATH, report)

    manifest = {
        "generated_at_utc": generated_at,
        "status": "experimental_not_android_default",
        "model_version": "rdtcn_v2",
        "feature_version": FEATURE_VERSION,
        "sequence_length": EXPECTED_SEQUENCE_SHAPE[0],
        "feature_size": EXPECTED_SEQUENCE_SHAPE[1],
        "num_classes": len(labels),
        "confidence_threshold": confidence_threshold,
        "margin_threshold": margin_threshold,
        "nsac_class_index": None,
        "cooldown_frames": cooldown_frames,
        "selected_hand": "right",
        "activity_detector_threshold": 0.5,
        "class_order": labels,
        "feature_layout": FEATURE_LAYOUT,
        "normalization": NORMALIZATION_POLICY,
        "input_shape": [1, *EXPECTED_SEQUENCE_SHAPE],
        "input_dtype": "float32",
        "output_shape": [1, len(labels)],
        "output_dtype": "float32",
        "rdtcn_model_filename": MODEL_FLOAT16_PATH.name,
        "activity_detector_filename": ACTIVITY_MODEL_PATH.name,
        "split_assignment_sha256": assignment_sha256,
        "calibration": {
            "source": "validation_only",
            "sequence_count": len(validation_records),
            "acceptance_metrics": selected_margin,
            "cooldown_metrics": selected_cooldown,
            "report": str(CALIBRATION_REPORT_PATH.relative_to(ROOT)),
        },
        "activity_detector_validation": {
            "accuracy": activity_report["validation"]["accuracy"],
            "target_met": activity_report["target"]["met"],
            "report": str(ACTIVITY_REPORT_PATH.relative_to(ROOT)),
        },
        "artifact_sha256": {
            "rdtcn_float16": sha256(MODEL_FLOAT16_PATH),
            "activity_detector": sha256(ACTIVITY_MODEL_PATH),
        },
        "android_default_changed": False,
        "deployment_eligible": False,
        "deployment_blockers": [
            "No real no-sign/activity recordings are available for detector validation.",
            "Activity detector held-out synthetic accuracy is below the >95% target.",
            "Cooldown calibration has not been validated on a continuous live stream.",
            "Multi-device validation is incomplete.",
        ],
    }
    write_json_atomic(RUNTIME_MANIFEST_PATH, manifest)
    print(f"Confidence threshold : {confidence_threshold:.2f}")
    print(f"Margin threshold     : {margin_threshold:.2f}")
    print(f"Cooldown frames      : {cooldown_frames}")
    print(f"Acceptance F1        : {selected_margin['acceptance_f1']:.6f}")
    print(f"Manifest             : {RUNTIME_MANIFEST_PATH}")
    print(f"Report               : {CALIBRATION_REPORT_PATH}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
