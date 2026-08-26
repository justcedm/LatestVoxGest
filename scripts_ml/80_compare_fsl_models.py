"""Compare full FSL TCN/RD-TCN runs and select an experimental winner."""

from __future__ import annotations

import csv
import json
from datetime import datetime, timezone
from pathlib import Path

from fsl_dataset import ROOT, write_json_atomic
from voxgest_feature_builder import (
    EXPECTED_SEQUENCE_SHAPE,
    FEATURE_LAYOUT,
    FEATURE_VERSION,
    NORMALIZATION_POLICY,
)


REPORTS = {
    "tcn": ROOT / "reports" / "fsl_tcn_evaluation.json",
    "rdtcn": ROOT / "reports" / "fsl_rdtcn_evaluation.json",
}
CSV_PATH = ROOT / "reports" / "fsl_model_comparison.csv"
DOC_PATH = ROOT / "docs" / "FSL_MODEL_SELECTION.md"
MANIFEST_PATH = ROOT / "model" / "experimental" / "runtime_manifest_fsl_selected_v2.json"


def load_report(path: Path) -> dict:
    payload = json.loads(path.read_text(encoding="utf-8"))
    if payload.get("run_kind") != "full":
        raise SystemExit(f"Full training report required: {path}")
    if payload.get("feature_version") != FEATURE_VERSION:
        raise SystemExit(f"Feature-version mismatch: {path}")
    if not payload.get("leakage_audit", {}).get("passed"):
        raise SystemExit(f"Leakage audit failed: {path}")
    changed = False
    for split in ("validation", "test"):
        metrics = payload[split]
        per_class = list(metrics["per_class"].values())
        if "macro_precision" not in metrics:
            metrics["macro_precision"] = sum(row["precision"] for row in per_class) / len(per_class)
            changed = True
        if "macro_recall" not in metrics:
            metrics["macro_recall"] = sum(row["recall"] for row in per_class) / len(per_class)
            changed = True
    if changed:
        write_json_atomic(path, payload)
    return payload


def main() -> int:
    reports = {name: load_report(path) for name, path in REPORTS.items()}
    rows = []
    for name, report in reports.items():
        rows.append(
            {
                "architecture": name,
                "validation_accuracy": report["validation"]["accuracy"],
                "validation_macro_precision": report["validation"]["macro_precision"],
                "validation_macro_recall": report["validation"]["macro_recall"],
                "validation_macro_f1": report["validation"]["macro_f1"],
                "test_accuracy": report["test"]["accuracy"],
                "test_macro_precision": report["test"]["macro_precision"],
                "test_macro_recall": report["test"]["macro_recall"],
                "test_macro_f1": report["test"]["macro_f1"],
                "parameters": report["training"]["parameter_count"],
                "float32_tflite_bytes": report["tflite"]["float32"]["size_bytes"],
                "float16_tflite_bytes": report["tflite"]["float16"]["size_bytes"],
                "float32_prediction_agreement": report["tflite"]["float32_prediction_agreement"],
                "float16_prediction_agreement": report["tflite"]["float16_prediction_agreement"],
            }
        )
    winner = max(
        rows,
        key=lambda row: (
            row["validation_macro_f1"],
            row["validation_accuracy"],
            -row["parameters"],
        ),
    )
    CSV_PATH.parent.mkdir(parents=True, exist_ok=True)
    with CSV_PATH.open("w", encoding="utf-8-sig", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=list(rows[0]))
        writer.writeheader()
        writer.writerows(rows)

    winner_report = reports[winner["architecture"]]
    manifest = {
        "generated_at_utc": datetime.now(timezone.utc).isoformat(),
        "status": "experimental_selected_not_android_default",
        "selection_rule": "validation_macro_f1_then_validation_accuracy_then_smaller_model",
        "selected_architecture": winner["architecture"],
        "model_type": winner["architecture"],
        "model_version": f"voxgest_fsl_{winner['architecture']}_v2",
        "feature_version": FEATURE_VERSION,
        "feature_layout": FEATURE_LAYOUT,
        "normalization": NORMALIZATION_POLICY,
        "dataset_version": winner_report["dataset_version"],
        "input_shape": [1, *EXPECTED_SEQUENCE_SHAPE],
        "input_dtype": "float32",
        "output_shape": [1, len(winner_report["labels"])],
        "output_dtype": "float32",
        "sequence_length": EXPECTED_SEQUENCE_SHAPE[0],
        "feature_size": EXPECTED_SEQUENCE_SHAPE[1],
        "class_count": len(winner_report["labels"]),
        "class_order": winner_report["labels"],
        "handedness_policy": "fixed_anatomical_right_holistic_slot",
        "mirrored_input": False,
        "z_damping": 0.3,
        "model_filename_float32": Path(winner_report["artifacts"]["tflite_float32"]).name,
        "model_filename_float16": Path(winner_report["artifacts"]["tflite_float16"]).name,
        "model_artifacts": winner_report["artifacts"],
        "metrics": winner,
        "android_default_changed": False,
        "deployment_eligible": False,
        "rejection_policy": "stable_runtime_policy_unchanged_not_calibrated_for_this_model",
        "deployment_blockers": winner_report["deployment"]["blockers"],
    }
    write_json_atomic(MANIFEST_PATH, manifest)

    table_rows = [
        f"| {row['architecture']} | {row['validation_accuracy']:.4f} | {row['validation_macro_f1']:.4f} | {row['test_accuracy']:.4f} | {row['test_macro_f1']:.4f} | {row['parameters']:,} | {row['float16_tflite_bytes'] / 1024:.1f} KB |"
        for row in rows
    ]
    doc = [
        "# FSL Model Selection",
        "",
        "| Model | Val accuracy | Val macro-F1 | Test accuracy | Test macro-F1 | Parameters | Float16 TFLite |",
        "|---|---:|---:|---:|---:|---:|---:|",
        *table_rows,
        "",
        f"Selected experimental model: **{winner['architecture'].upper()}**.",
        "",
        "Selection is deterministic and validation-only: validation macro-F1, "
        "then validation accuracy, then smaller parameter count. The held-out "
        "test set is used only for final reporting, not architecture selection.",
        "",
        "This selection does not promote a model to Android. The stable model, "
        "class mapping, and rejection thresholds remain unchanged because the "
        "FSL-105 subset has no NOTHING/background class and cross-device "
        "validation is incomplete.",
        "",
    ]
    DOC_PATH.parent.mkdir(parents=True, exist_ok=True)
    DOC_PATH.write_text("\n".join(doc), encoding="utf-8")
    print(f"Selected experimental architecture: {winner['architecture']}")
    print(f"Comparison: {CSV_PATH}")
    print(f"Manifest: {MANIFEST_PATH}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
