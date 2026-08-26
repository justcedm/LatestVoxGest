"""Shared training/evaluation/export pipeline for experimental FSL models."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import random
from collections import Counter, defaultdict
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

import numpy as np

os.environ.setdefault("TF_DETERMINISTIC_OPS", "1")

from fsl_dataset import (
    AUDIT_JSON_PATH,
    DATASET_VERSION,
    ROOT,
    SPLIT_MANIFEST_PATH,
    assignment_digest,
    load_split_manifest,
    project_relative,
    resolve_project_path,
    write_json_atomic,
)
from voxgest_feature_builder import (
    EXPECTED_SEQUENCE_SHAPE,
    FEATURE_LAYOUT,
    FEATURE_VERSION,
    NORMALIZATION_POLICY,
    validate_sequence,
)


MODEL_ROOT = ROOT / "model" / "experimental"
REPORT_ROOT = ROOT / "reports"
CACHE_ROOT = ROOT / "external_datasets" / "fsl105_onehand162_20f"
RANDOM_SEED = 42


def load_json(path: Path) -> dict[str, Any]:
    payload = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(payload, dict):
        raise ValueError(f"expected JSON object: {path}")
    return payload


def require_ready_audit() -> dict[str, Any]:
    if not AUDIT_JSON_PATH.is_file() or not SPLIT_MANIFEST_PATH.is_file():
        raise SystemExit("Run scripts_ml/75_audit_fsl_dataset.py before training.")
    audit = load_json(AUDIT_JSON_PATH)
    if audit.get("readiness", {}).get("ready_for_training") != "YES":
        raise SystemExit("FSL-105 audit is not READY_FOR_TRAINING=YES.")
    if audit.get("feature_contract", {}).get("feature_version") != FEATURE_VERSION:
        raise SystemExit("Audit feature version does not match the active builder.")
    if not audit.get("split", {}).get("leakage", {}).get("passed"):
        raise SystemExit("Audit leakage check did not pass.")
    return audit


def set_reproducible_seeds(tf) -> None:
    random.seed(RANDOM_SEED)
    np.random.seed(RANDOM_SEED)
    tf.random.set_seed(RANDOM_SEED)


def select_records(records, smoke: bool):
    if not smoke:
        return records
    limits = {"train": 8, "val": 4, "test": 4}
    buckets = defaultdict(list)
    for record in records:
        buckets[(record.split, record.label)].append(record)
    selected = []
    for key in sorted(buckets):
        selected.extend(sorted(buckets[key], key=lambda row: row.path)[: limits[key[0]]])
    return selected


def manifest_digest() -> str:
    return hashlib.sha256(SPLIT_MANIFEST_PATH.read_bytes()).hexdigest()


def prepare_feature_matrix(records, *, smoke: bool) -> np.ndarray:
    if smoke:
        matrix = np.empty((len(records), *EXPECTED_SEQUENCE_SHAPE), dtype=np.float32)
        for index, record in enumerate(records):
            array = np.load(resolve_project_path(record.path), allow_pickle=False)
            issues = validate_sequence(array)
            if issues:
                raise ValueError(f"invalid smoke sample {record.path}: {issues}")
            matrix[index] = array
        return matrix

    digest = manifest_digest()
    cache_dir = CACHE_ROOT / f"cache_{digest[:12]}"
    feature_path = cache_dir / "features.npy"
    metadata_path = cache_dir / "cache_manifest.json"
    expected_meta = {
        "manifest_sha256": digest,
        "feature_version": FEATURE_VERSION,
        "sequence_count": len(records),
        "shape": [len(records), *EXPECTED_SEQUENCE_SHAPE],
        "dtype": "float32",
        "cache_only_not_source_data": True,
    }
    cache_valid = False
    if feature_path.is_file() and metadata_path.is_file():
        try:
            cache_valid = load_json(metadata_path) == expected_meta
            if cache_valid:
                cached = np.load(feature_path, mmap_mode="r", allow_pickle=False)
                cache_valid = cached.shape == tuple(expected_meta["shape"]) and cached.dtype == np.float32
        except (OSError, ValueError, json.JSONDecodeError):
            cache_valid = False
    if not cache_valid:
        cache_dir.mkdir(parents=True, exist_ok=True)
        temporary = feature_path.with_suffix(".npy.tmp")
        matrix = np.lib.format.open_memmap(
            temporary,
            mode="w+",
            dtype=np.float32,
            shape=(len(records), *EXPECTED_SEQUENCE_SHAPE),
        )
        for index, record in enumerate(records):
            array = np.load(resolve_project_path(record.path), allow_pickle=False)
            issues = validate_sequence(array)
            if issues:
                raise ValueError(f"invalid audited sample {record.path}: {issues}")
            matrix[index] = array
            if (index + 1) % 1000 == 0:
                print(f"Canonical cache: {index + 1}/{len(records)}", flush=True)
        matrix.flush()
        del matrix
        temporary.replace(feature_path)
        write_json_atomic(metadata_path, expected_meta)
    return np.load(feature_path, mmap_mode="r", allow_pickle=False)


def build_model(tf, architecture: str, num_classes: int):
    layers = tf.keras.layers

    def residual_block(x, dilation: int):
        residual = x
        y = layers.Conv1D(64, 3, padding="causal", dilation_rate=dilation)(x)
        y = layers.LayerNormalization()(y)
        y = layers.Activation("relu")(y)
        y = layers.SpatialDropout1D(0.2)(y)
        y = layers.Conv1D(64, 3, padding="causal", dilation_rate=dilation)(y)
        y = layers.LayerNormalization()(y)
        if residual.shape[-1] != 64:
            residual = layers.Conv1D(64, 1, padding="same")(residual)
        return layers.Activation("relu")(layers.Add()([residual, y]))

    inputs = layers.Input(shape=EXPECTED_SEQUENCE_SHAPE, name="onehand162_sequence")
    x = inputs
    if architecture == "tcn":
        for dilation in (1, 2, 4):
            x = layers.Conv1D(
                64,
                3,
                padding="causal",
                dilation_rate=dilation,
                name=f"tcn_d{dilation}",
            )(x)
            x = layers.LayerNormalization()(x)
            x = layers.Activation("relu")(x)
            x = layers.SpatialDropout1D(0.2)(x)
    elif architecture == "rdtcn":
        for dilation in (1, 2, 4):
            x = residual_block(x, dilation)
    else:
        raise ValueError(f"unsupported architecture: {architecture}")
    x = layers.GlobalAveragePooling1D()(x)
    x = layers.Dense(64, activation="relu")(x)
    x = layers.Dropout(0.3)(x)
    outputs = layers.Dense(num_classes, activation="softmax", name="class_probabilities")(x)
    model = tf.keras.Model(inputs, outputs, name=f"voxgest_fsl_{architecture}_v2")
    model.compile(
        optimizer=tf.keras.optimizers.Adam(learning_rate=3e-4),
        loss="sparse_categorical_crossentropy",
        metrics=["accuracy"],
    )
    return model


def metrics_report(y_true: np.ndarray, probabilities: np.ndarray, labels: list[str]) -> dict[str, Any]:
    predictions = np.argmax(probabilities, axis=1).astype(np.int64)
    matrix = np.zeros((len(labels), len(labels)), dtype=np.int64)
    for truth, prediction in zip(y_true, predictions):
        matrix[int(truth), int(prediction)] += 1
    per_class: dict[str, Any] = {}
    precision_values = []
    recall_values = []
    f1_values = []
    for index, label in enumerate(labels):
        true_positive = int(matrix[index, index])
        false_positive = int(matrix[:, index].sum() - true_positive)
        false_negative = int(matrix[index, :].sum() - true_positive)
        support = int(matrix[index, :].sum())
        precision = true_positive / (true_positive + false_positive) if true_positive + false_positive else 0.0
        recall = true_positive / (true_positive + false_negative) if true_positive + false_negative else 0.0
        f1 = 2 * precision * recall / (precision + recall) if precision + recall else 0.0
        f1_values.append(f1)
        precision_values.append(precision)
        recall_values.append(recall)
        per_class[label] = {
            "precision": precision,
            "recall": recall,
            "f1": f1,
            "support": support,
        }
    confused = []
    for truth in range(len(labels)):
        for predicted in range(len(labels)):
            if truth != predicted and matrix[truth, predicted]:
                confused.append(
                    {
                        "true": labels[truth],
                        "predicted": labels[predicted],
                        "count": int(matrix[truth, predicted]),
                    }
                )
    confused.sort(key=lambda row: (-row["count"], row["true"], row["predicted"]))
    return {
        "accuracy": float(np.mean(predictions == y_true)) if len(y_true) else 0.0,
        "macro_precision": float(np.mean(precision_values)) if precision_values else 0.0,
        "macro_recall": float(np.mean(recall_values)) if recall_values else 0.0,
        "macro_f1": float(np.mean(f1_values)) if f1_values else 0.0,
        "per_class": per_class,
        "confusion_matrix": matrix.tolist(),
        "top_confused_pairs": confused[:20],
    }


def export_tflite(tf, model, output_path: Path, *, float16: bool) -> dict[str, Any]:
    converter = tf.lite.TFLiteConverter.from_keras_model(model)
    if float16:
        converter.optimizations = [tf.lite.Optimize.DEFAULT]
        converter.target_spec.supported_types = [tf.float16]
    content = converter.convert()
    temporary = output_path.with_suffix(output_path.suffix + ".tmp")
    temporary.write_bytes(content)
    temporary.replace(output_path)
    return {
        "path": project_relative(output_path),
        "size_bytes": len(content),
        "weight_quantization": "float16" if float16 else "float32",
        "input_dtype": "float32",
        "output_dtype": "float32",
    }


def tflite_probabilities(tf, model_path: Path, x: np.ndarray) -> np.ndarray:
    interpreter = tf.lite.Interpreter(model_path=str(model_path))
    interpreter.allocate_tensors()
    input_detail = interpreter.get_input_details()[0]
    output_detail = interpreter.get_output_details()[0]
    predictions = []
    for sample in x:
        interpreter.set_tensor(input_detail["index"], sample[np.newaxis].astype(np.float32))
        interpreter.invoke()
        predictions.append(interpreter.get_tensor(output_detail["index"])[0])
    return np.asarray(predictions, dtype=np.float32)


def class_weights(y: np.ndarray, class_count: int) -> dict[int, float]:
    counts = np.bincount(y, minlength=class_count)
    total = len(y)
    return {
        index: float(total / (class_count * count))
        for index, count in enumerate(counts)
        if count
    }


def train(architecture: str, args: argparse.Namespace) -> dict[str, Any]:
    import tensorflow as tf

    audit = require_ready_audit()
    set_reproducible_seeds(tf)
    all_records = load_split_manifest()
    current_assignment_sha256 = assignment_digest(all_records)
    if current_assignment_sha256 != audit.get("split", {}).get("assignment_sha256"):
        raise SystemExit("Split manifest assignment does not match the audited assignment hash.")
    records = select_records(all_records, args.smoke)
    labels = list(audit["labels"])
    label_to_index = {label: index for index, label in enumerate(labels)}
    features = prepare_feature_matrix(records, smoke=args.smoke)
    indices = {
        split: np.asarray([index for index, record in enumerate(records) if record.split == split])
        for split in ("train", "val", "test")
    }
    x = {split: np.asarray(features[indexes], dtype=np.float32) for split, indexes in indices.items()}
    y = {
        split: np.asarray([label_to_index[records[index].label] for index in indexes], dtype=np.int64)
        for split, indexes in indices.items()
    }
    for split in x:
        if not len(x[split]) or len(set(y[split].tolist())) != len(labels):
            raise SystemExit(f"{split} split is empty or lacks class coverage")

    epochs = min(args.epochs, 2) if args.smoke else args.epochs
    model = build_model(tf, architecture, len(labels))
    callbacks = [
        tf.keras.callbacks.EarlyStopping(
            monitor="val_loss", patience=8, restore_best_weights=True
        ),
        tf.keras.callbacks.ReduceLROnPlateau(
            monitor="val_loss", patience=4, factor=0.5, min_lr=1e-6, verbose=1
        ),
        tf.keras.callbacks.TerminateOnNaN(),
    ]
    history = model.fit(
        x["train"],
        y["train"],
        validation_data=(x["val"], y["val"]),
        epochs=epochs,
        batch_size=args.batch_size,
        class_weight=class_weights(y["train"], len(labels)),
        callbacks=callbacks,
        verbose=2,
        shuffle=True,
    )

    artifact_dir = MODEL_ROOT / ("smoke" if args.smoke else "")
    report_dir = REPORT_ROOT / ("smoke" if args.smoke else "")
    artifact_dir.mkdir(parents=True, exist_ok=True)
    report_dir.mkdir(parents=True, exist_ok=True)
    stem = f"voxgest_fsl_{architecture}_v2" + ("_smoke" if args.smoke else "")
    keras_path = artifact_dir / f"{stem}.keras"
    float32_path = artifact_dir / f"{stem}_float32.tflite"
    float16_path = artifact_dir / f"{stem}_float16.tflite"
    labels_path = artifact_dir / f"{stem}_labels.json"
    manifest_path = artifact_dir / f"runtime_manifest_{stem}.json"
    report_path = report_dir / f"fsl_{architecture}_evaluation.json"

    temporary_keras = keras_path.with_name(f"{keras_path.stem}.tmp.keras")
    model.save(temporary_keras)
    temporary_keras.replace(keras_path)
    write_json_atomic(labels_path, {"labels": labels, "feature_version": FEATURE_VERSION})
    tflite_float32 = export_tflite(tf, model, float32_path, float16=False)
    tflite_float16 = export_tflite(tf, model, float16_path, float16=True)
    val_probs = model.predict(x["val"], batch_size=args.batch_size, verbose=0)
    test_probs = model.predict(x["test"], batch_size=args.batch_size, verbose=0)
    parity_count = min(64, len(x["test"]))
    keras_parity = test_probs[:parity_count]
    float32_parity = tflite_probabilities(tf, float32_path, x["test"][:parity_count])
    float16_parity = tflite_probabilities(tf, float16_path, x["test"][:parity_count])

    manifest = {
        "status": "experimental_not_android_default",
        "model_version": stem,
        "architecture": architecture,
        "model_type": architecture,
        "dataset_version": DATASET_VERSION,
        "feature_version": FEATURE_VERSION,
        "feature_layout": FEATURE_LAYOUT,
        "normalization": NORMALIZATION_POLICY,
        "sequence_length": EXPECTED_SEQUENCE_SHAPE[0],
        "feature_size": EXPECTED_SEQUENCE_SHAPE[1],
        "input_shape": [1, *EXPECTED_SEQUENCE_SHAPE],
        "input_dtype": "float32",
        "output_shape": [1, len(labels)],
        "output_dtype": "float32",
        "class_count": len(labels),
        "class_order": labels,
        "selected_hand_policy": "fixed_anatomical_right_holistic_slot",
        "mirrored_input": False,
        "z_damping": 0.3,
        "rejection_policy": {
            "status": "unchanged_not_tuned_by_this_training",
            "negative_class_available": False,
            "deployment_eligible": False,
        },
        "artifacts": {
            "keras": project_relative(keras_path),
            "tflite_float32": tflite_float32["path"],
            "tflite_float16": tflite_float16["path"],
            "labels": project_relative(labels_path),
        },
        "model_filename_float32": float32_path.name,
        "model_filename_float16": float16_path.name,
    }
    write_json_atomic(manifest_path, manifest)
    report = {
        "generated_at_utc": datetime.now(timezone.utc).isoformat(),
        "run_kind": "smoke" if args.smoke else "full",
        "architecture": architecture,
        "dataset_version": DATASET_VERSION,
        "feature_version": FEATURE_VERSION,
        "labels": labels,
        "class_count": len(labels),
        "split_counts": {split: len(values) for split, values in x.items()},
        "split_method": audit["split"]["method"],
        "split_assignment_sha256": current_assignment_sha256,
        "leakage_audit": audit["split"]["leakage"],
        "training": {
            "epochs_requested": epochs,
            "epochs_completed": len(history.history.get("loss", [])),
            "batch_size": args.batch_size,
            "seed": RANDOM_SEED,
            "class_weights": class_weights(y["train"], len(labels)),
            "history": {
                key: [float(value) for value in values]
                for key, values in history.history.items()
            },
            "parameter_count": int(model.count_params()),
        },
        "validation": metrics_report(y["val"], val_probs, labels),
        "test": metrics_report(y["test"], test_probs, labels),
        "tflite": {
            "float32": tflite_float32,
            "float16": tflite_float16,
            "parity_sample_count": parity_count,
            "float32_max_abs_probability_error": float(np.max(np.abs(keras_parity - float32_parity))),
            "float32_prediction_agreement": float(
                np.mean(np.argmax(keras_parity, axis=1) == np.argmax(float32_parity, axis=1))
            ),
            "float16_max_abs_probability_error": float(np.max(np.abs(keras_parity - float16_parity))),
            "float16_prediction_agreement": float(
                np.mean(np.argmax(keras_parity, axis=1) == np.argmax(float16_parity, axis=1))
            ),
        },
        "deployment": {
            "android_default_changed": False,
            "eligible": False,
            "blockers": [
                "No NOTHING/background class in FSL-105.",
                "Cross-device Samsung and webcam validation not complete.",
                "Rejection thresholds have not been calibrated for this model.",
            ],
        },
        "artifacts": {
            **manifest["artifacts"],
            "runtime_manifest": project_relative(manifest_path),
            "evaluation_report": project_relative(report_path),
        },
    }
    write_json_atomic(report_path, report)
    return report


def parse_args(description: str) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=description)
    parser.add_argument("--epochs", type=int, default=100)
    parser.add_argument("--batch-size", type=int, default=32)
    parser.add_argument("--smoke", action="store_true")
    args = parser.parse_args()
    if args.epochs < 1 or args.batch_size < 1:
        parser.error("epochs and batch size must be positive")
    return args


def print_result(report: dict[str, Any]) -> None:
    print(f"{report['architecture'].upper()} {report['run_kind']} run complete")
    print(f"Validation accuracy: {report['validation']['accuracy']:.4f}")
    print(f"Validation macro-F1: {report['validation']['macro_f1']:.4f}")
    print(f"Test accuracy      : {report['test']['accuracy']:.4f}")
    print(f"Test macro-F1      : {report['test']['macro_f1']:.4f}")
    print(f"Float32 agreement  : {report['tflite']['float32_prediction_agreement']:.4f}")
    print(f"Float16 agreement  : {report['tflite']['float16_prediction_agreement']:.4f}")
    print(f"Report             : {report['artifacts']['evaluation_report']}")
