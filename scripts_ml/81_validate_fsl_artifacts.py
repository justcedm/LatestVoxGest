"""Validate final experimental FSL artifacts and measure local CPU latency."""

from __future__ import annotations

import hashlib
import json
import time
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

import numpy as np

from fsl_dataset import AUDIT_JSON_PATH, ROOT, resolve_project_path, write_json_atomic
from voxgest_feature_builder import (
    EXPECTED_SEQUENCE_SHAPE,
    FEATURE_LAYOUT,
    FEATURE_VERSION,
    NORMALIZATION_POLICY,
)


MODELS = {
    "tcn": ROOT / "reports" / "fsl_tcn_evaluation.json",
    "rdtcn": ROOT / "reports" / "fsl_rdtcn_evaluation.json",
}
OUTPUT_PATH = ROOT / "reports" / "fsl_artifact_validation.json"


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def build_benchmark_batch(limit: int = 64) -> np.ndarray:
    """Use deterministic synthetic input; held-out parity was measured at export."""
    rng = np.random.default_rng(42)
    return rng.normal(0.0, 0.25, (limit, *EXPECTED_SEQUENCE_SHAPE)).astype(np.float32)


def benchmark_keras(model, batch: np.ndarray) -> float:
    model.predict(batch[:1], verbose=0)
    repeats = 5
    started = time.perf_counter()
    for _ in range(repeats):
        model.predict(batch, batch_size=len(batch), verbose=0)
    elapsed = time.perf_counter() - started
    return elapsed * 1000.0 / (repeats * len(batch))


def validate_tflite(tf, path: Path, batch: np.ndarray) -> dict[str, Any]:
    interpreter = tf.lite.Interpreter(model_path=str(path))
    interpreter.allocate_tensors()
    input_detail = interpreter.get_input_details()[0]
    output_detail = interpreter.get_output_details()[0]
    interpreter.set_tensor(input_detail["index"], batch[:1])
    interpreter.invoke()
    started = time.perf_counter()
    for sample in batch:
        interpreter.set_tensor(input_detail["index"], sample[np.newaxis])
        interpreter.invoke()
        interpreter.get_tensor(output_detail["index"])
    elapsed = time.perf_counter() - started
    return {
        "path": str(path.relative_to(ROOT)),
        "sha256": sha256(path),
        "size_bytes": path.stat().st_size,
        "input_shape": input_detail["shape"].astype(int).tolist(),
        "input_shape_signature": input_detail["shape_signature"].astype(int).tolist(),
        "input_dtype": np.dtype(input_detail["dtype"]).name,
        "output_shape": output_detail["shape"].astype(int).tolist(),
        "output_dtype": np.dtype(output_detail["dtype"]).name,
        "mean_single_sequence_latency_ms": elapsed * 1000.0 / len(batch),
        "contract_passed": (
            input_detail["shape"].astype(int).tolist() == [1, *EXPECTED_SEQUENCE_SHAPE]
            and np.dtype(input_detail["dtype"]) == np.float32
            and output_detail["shape"].astype(int).tolist() == [1, 64]
            and np.dtype(output_detail["dtype"]) == np.float32
        ),
    }


def main() -> int:
    import tensorflow as tf

    audit = json.loads(AUDIT_JSON_PATH.read_text(encoding="utf-8"))
    if audit["readiness"]["ready_for_training"] != "YES":
        raise SystemExit("Dataset audit is not ready.")
    batch = build_benchmark_batch()
    results = {}
    all_passed = True
    for architecture, report_path in MODELS.items():
        report = json.loads(report_path.read_text(encoding="utf-8"))
        if report["feature_version"] != FEATURE_VERSION:
            raise SystemExit(f"Feature mismatch in {report_path}")
        if report.get("split_assignment_sha256") != audit["split"]["assignment_sha256"]:
            raise SystemExit(f"Split-assignment mismatch in {report_path}")
        keras_path = resolve_project_path(report["artifacts"]["keras"])
        float32_path = resolve_project_path(report["artifacts"]["tflite_float32"])
        float16_path = resolve_project_path(report["artifacts"]["tflite_float16"])
        runtime_manifest_path = resolve_project_path(report["artifacts"]["runtime_manifest"])
        runtime_manifest = json.loads(runtime_manifest_path.read_text(encoding="utf-8"))
        runtime_manifest.update(
            {
                "model_type": architecture,
                "class_count": len(report["labels"]),
                "class_order": report["labels"],
                "input_shape": [1, *EXPECTED_SEQUENCE_SHAPE],
                "input_dtype": "float32",
                "output_shape": [1, len(report["labels"])],
                "output_dtype": "float32",
                "feature_version": FEATURE_VERSION,
                "feature_layout": FEATURE_LAYOUT,
                "normalization": NORMALIZATION_POLICY,
                "dataset_version": report["dataset_version"],
                "model_filename_float32": float32_path.name,
                "model_filename_float16": float16_path.name,
            }
        )
        write_json_atomic(runtime_manifest_path, runtime_manifest)
        model = tf.keras.models.load_model(keras_path)
        keras_contract = (
            list(model.input_shape) == [None, *EXPECTED_SEQUENCE_SHAPE]
            and list(model.output_shape) == [None, 64]
        )
        row = {
            "keras": {
                "path": str(keras_path.relative_to(ROOT)),
                "sha256": sha256(keras_path),
                "size_bytes": keras_path.stat().st_size,
                "input_shape": list(model.input_shape),
                "output_shape": list(model.output_shape),
                "batched_cpu_latency_ms_per_sequence": benchmark_keras(model, batch),
                "contract_passed": keras_contract,
            },
            "float32_tflite": validate_tflite(tf, float32_path, batch),
            "float16_tflite": validate_tflite(tf, float16_path, batch),
        }
        row["passed"] = bool(
            row["keras"]["contract_passed"]
            and row["float32_tflite"]["contract_passed"]
            and row["float16_tflite"]["contract_passed"]
            and report["tflite"]["float32_prediction_agreement"] == 1.0
            and report["tflite"]["float16_prediction_agreement"] == 1.0
        )
        all_passed = all_passed and row["passed"]
        results[architecture] = row
        report["latency"] = {
            "environment": "local Windows CPU; diagnostic only, not Android latency",
            "keras_batched_ms_per_sequence": row["keras"]["batched_cpu_latency_ms_per_sequence"],
            "tflite_float32_single_sequence_ms": row["float32_tflite"]["mean_single_sequence_latency_ms"],
            "tflite_float16_single_sequence_ms": row["float16_tflite"]["mean_single_sequence_latency_ms"],
        }
        report["artifact_sha256"] = {
            "keras": row["keras"]["sha256"],
            "tflite_float32": row["float32_tflite"]["sha256"],
            "tflite_float16": row["float16_tflite"]["sha256"],
        }
        write_json_atomic(report_path, report)

    output = {
        "generated_at_utc": datetime.now(timezone.utc).isoformat(),
        "feature_version": FEATURE_VERSION,
        "benchmark_sequences": len(batch),
        "latency_input": "deterministic_synthetic_float32_contract_benchmark",
        "models": results,
        "all_passed": all_passed,
        "android_benchmark_performed": False,
    }
    write_json_atomic(OUTPUT_PATH, output)
    print(f"FSL artifact validation passed: {all_passed}")
    print(f"Report: {OUTPUT_PATH}")
    return 0 if all_passed else 2


if __name__ == "__main__":
    raise SystemExit(main())
