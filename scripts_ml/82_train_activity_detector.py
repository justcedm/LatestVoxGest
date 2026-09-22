"""Train the experimental VoxGest single-frame sign activity detector.

The detector is deliberately isolated from the 64-class RD-TCN.  It consumes
one canonical OneHand162 frame and emits a single signing probability.  All
synthetic examples are generated inside the existing signer-grouped dataset
partitions so validation and test signers never influence training samples.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import random
from collections import Counter
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

import numpy as np

os.environ.setdefault("TF_DETERMINISTIC_OPS", "1")

from fsl_dataset import (
    ROOT,
    assignment_digest,
    load_split_manifest,
    project_relative,
    write_json_atomic,
)
from fsl_train_common import prepare_feature_matrix, require_ready_audit
from voxgest_feature_builder import FEATURE_SIZE, FEATURE_VERSION


RANDOM_SEED = 42
MODEL_VERSION = "voxgest_activity_detector_v1"
KERAS_PATH = ROOT / "model" / "experimental" / f"{MODEL_VERSION}.keras"
TFLITE_PATH = ROOT / "model" / f"{MODEL_VERSION}.tflite"
REPORT_PATH = ROOT / "reports" / "activity_detector_v1_evaluation.json"

# The user-requested negative families are all represented.  Boundary frames
# receive a smaller share because FSL-105 windows contain signs throughout and
# therefore provide only weak, noisy entry/exit supervision.
NEGATIVE_WEIGHTS = {
    "zero_vector": 0.30,
    "gaussian_noise": 0.30,
    "cross_sign_interpolation": 0.30,
    "sequence_boundary": 0.10,
}


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def set_seeds(tf) -> None:
    random.seed(RANDOM_SEED)
    np.random.seed(RANDOM_SEED)
    tf.random.set_seed(RANDOM_SEED)


def allocate_counts(total: int) -> dict[str, int]:
    """Allocate exactly *total* rows using the declared deterministic mix."""
    names = list(NEGATIVE_WEIGHTS)
    counts = {name: int(total * NEGATIVE_WEIGHTS[name]) for name in names}
    for name in names[: total - sum(counts.values())]:
        counts[name] += 1
    if sum(counts.values()) != total or any(value < 1 for value in counts.values()):
        raise ValueError(f"invalid negative allocation for {total}: {counts}")
    return counts


def sample_frames(
    features: np.ndarray,
    sequence_indices: np.ndarray,
    count: int,
    rng: np.random.Generator,
    *,
    boundary: bool = False,
) -> np.ndarray:
    chosen_sequences = rng.choice(
        sequence_indices,
        size=count,
        replace=len(sequence_indices) < count,
    )
    if boundary:
        chosen_frames = rng.choice(np.asarray([0, 19], dtype=np.int64), size=count)
    else:
        # Central frames are the least ambiguous available positive proxy.
        chosen_frames = rng.integers(3, 17, size=count)
    return np.asarray(features[chosen_sequences, chosen_frames], dtype=np.float32)


def sample_frames_from_exact_sequences(
    features: np.ndarray,
    chosen_sequences: np.ndarray,
    rng: np.random.Generator,
) -> np.ndarray:
    """Select one central frame from each already-paired sequence index."""
    chosen_frames = rng.integers(3, 17, size=len(chosen_sequences))
    return np.asarray(features[chosen_sequences, chosen_frames], dtype=np.float32)


def different_label_pairs(
    sequence_indices: np.ndarray,
    record_labels: np.ndarray,
    count: int,
    rng: np.random.Generator,
) -> tuple[np.ndarray, np.ndarray]:
    left = rng.choice(sequence_indices, size=count, replace=True)
    right = rng.choice(sequence_indices, size=count, replace=True)
    same = record_labels[left] == record_labels[right]
    attempts = 0
    while np.any(same):
        right[same] = rng.choice(sequence_indices, size=int(np.sum(same)), replace=True)
        same = record_labels[left] == record_labels[right]
        attempts += 1
        if attempts > 100:
            raise RuntimeError("could not create different-label interpolation pairs")
    return left, right


def build_partition(
    features: np.ndarray,
    records: list[Any],
    split: str,
    count_per_class: int,
    gaussian_scale: np.ndarray,
    seed_offset: int,
) -> tuple[np.ndarray, np.ndarray, np.ndarray, dict[str, int]]:
    """Return balanced (frames, targets, category names, category counts)."""
    rng = np.random.default_rng(RANDOM_SEED + seed_offset)
    split_indices = np.asarray(
        [index for index, record in enumerate(records) if record.split == split],
        dtype=np.int64,
    )
    if len(split_indices) == 0:
        raise ValueError(f"no records for split {split}")
    labels = np.asarray([record.label for record in records], dtype=object)
    positives = sample_frames(features, split_indices, count_per_class, rng)

    negative_counts = allocate_counts(count_per_class)
    negatives: list[np.ndarray] = []
    categories: list[np.ndarray] = []

    zero_count = negative_counts["zero_vector"]
    negatives.append(np.zeros((zero_count, FEATURE_SIZE), dtype=np.float32))
    categories.append(np.full(zero_count, "negative_zero_vector", dtype=object))

    noise_count = negative_counts["gaussian_noise"]
    noise = rng.normal(
        loc=0.0,
        scale=gaussian_scale[np.newaxis, :],
        size=(noise_count, FEATURE_SIZE),
    ).astype(np.float32)
    # The canonical nose landmark is always the origin when pose exists.
    noise[:, 0:3] = 0.0
    negatives.append(noise)
    categories.append(np.full(noise_count, "negative_gaussian_noise", dtype=object))

    interpolation_count = negative_counts["cross_sign_interpolation"]
    left_indices, right_indices = different_label_pairs(
        split_indices, labels, interpolation_count, rng
    )
    left_frames = sample_frames_from_exact_sequences(features, left_indices, rng)
    right_frames = sample_frames_from_exact_sequences(features, right_indices, rng)
    alpha = rng.uniform(0.25, 0.75, size=(interpolation_count, 1)).astype(np.float32)
    interpolated = alpha * left_frames + (1.0 - alpha) * right_frames
    negatives.append(interpolated.astype(np.float32))
    categories.append(
        np.full(interpolation_count, "negative_cross_sign_interpolation", dtype=object)
    )

    boundary_count = negative_counts["sequence_boundary"]
    boundary_frames = sample_frames(
        features,
        split_indices,
        boundary_count,
        rng,
        boundary=True,
    )
    negatives.append(boundary_frames)
    categories.append(np.full(boundary_count, "negative_sequence_boundary", dtype=object))

    negative_matrix = np.concatenate(negatives, axis=0).astype(np.float32)
    negative_categories = np.concatenate(categories)
    x = np.concatenate([positives, negative_matrix], axis=0).astype(np.float32)
    y = np.concatenate(
        [
            np.ones(count_per_class, dtype=np.float32),
            np.zeros(count_per_class, dtype=np.float32),
        ]
    )
    category = np.concatenate(
        [np.full(count_per_class, "positive_sign_center", dtype=object), negative_categories]
    )
    order = rng.permutation(len(x))
    return x[order], y[order], category[order], negative_counts


def build_model(tf):
    inputs = tf.keras.layers.Input(shape=(FEATURE_SIZE,), name="onehand162_frame")
    x = tf.keras.layers.Dense(64, activation="relu", name="dense_64")(inputs)
    x = tf.keras.layers.Dense(32, activation="relu", name="dense_32")(x)
    outputs = tf.keras.layers.Dense(1, activation="sigmoid", name="signing_probability")(x)
    model = tf.keras.Model(inputs, outputs, name=MODEL_VERSION)
    model.compile(
        optimizer=tf.keras.optimizers.Adam(learning_rate=3e-4),
        loss="binary_crossentropy",
        metrics=["accuracy"],
    )
    return model


def binary_report(
    targets: np.ndarray,
    probabilities: np.ndarray,
    categories: np.ndarray,
    threshold: float = 0.5,
) -> dict[str, Any]:
    probabilities = np.asarray(probabilities, dtype=np.float32).reshape(-1)
    targets = np.asarray(targets, dtype=np.int64).reshape(-1)
    predictions = (probabilities >= threshold).astype(np.int64)
    tp = int(np.sum((targets == 1) & (predictions == 1)))
    tn = int(np.sum((targets == 0) & (predictions == 0)))
    fp = int(np.sum((targets == 0) & (predictions == 1)))
    fn = int(np.sum((targets == 1) & (predictions == 0)))
    precision = tp / (tp + fp) if tp + fp else 0.0
    recall = tp / (tp + fn) if tp + fn else 0.0
    f1 = 2.0 * precision * recall / (precision + recall) if precision + recall else 0.0
    by_category = {}
    for name in sorted(set(categories.tolist())):
        mask = categories == name
        by_category[name] = {
            "count": int(np.sum(mask)),
            "accuracy": float(np.mean(predictions[mask] == targets[mask])),
            "mean_signing_probability": float(np.mean(probabilities[mask])),
        }
    return {
        "threshold": threshold,
        "sample_count": len(targets),
        "accuracy": float(np.mean(predictions == targets)),
        "precision": precision,
        "recall": recall,
        "f1": f1,
        "true_positive": tp,
        "true_negative": tn,
        "false_positive": fp,
        "false_negative": fn,
        "by_category": by_category,
    }


def export_tflite(tf, model, path: Path) -> None:
    converter = tf.lite.TFLiteConverter.from_keras_model(model)
    content = converter.convert()
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_suffix(path.suffix + ".tmp")
    temporary.write_bytes(content)
    temporary.replace(path)


def tflite_predict(tf, path: Path, frames: np.ndarray) -> tuple[np.ndarray, dict[str, Any]]:
    interpreter = tf.lite.Interpreter(model_path=str(path))
    interpreter.allocate_tensors()
    input_detail = interpreter.get_input_details()[0]
    output_detail = interpreter.get_output_details()[0]
    values = []
    for frame in frames:
        interpreter.set_tensor(input_detail["index"], frame[np.newaxis].astype(np.float32))
        interpreter.invoke()
        values.append(float(interpreter.get_tensor(output_detail["index"])[0, 0]))
    contract = {
        "input_shape": input_detail["shape"].astype(int).tolist(),
        "input_dtype": np.dtype(input_detail["dtype"]).name,
        "output_shape": output_detail["shape"].astype(int).tolist(),
        "output_dtype": np.dtype(output_detail["dtype"]).name,
    }
    return np.asarray(values, dtype=np.float32), contract


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--train-per-class", type=int, default=12000)
    parser.add_argument("--val-per-class", type=int, default=5000)
    parser.add_argument("--test-per-class", type=int, default=5000)
    parser.add_argument("--epochs", type=int, default=60)
    parser.add_argument("--batch-size", type=int, default=64)
    args = parser.parse_args()
    if args.train_per_class < 5000:
        parser.error("--train-per-class must be at least 5000")
    if min(args.val_per_class, args.test_per_class) < 1000:
        parser.error("validation/test per-class counts must be at least 1000")
    if min(args.epochs, args.batch_size) < 1:
        parser.error("epochs and batch size must be positive")
    return args


def main() -> int:
    import tensorflow as tf

    args = parse_args()
    audit = require_ready_audit()
    set_seeds(tf)
    records = load_split_manifest()
    if assignment_digest(records) != audit["split"]["assignment_sha256"]:
        raise SystemExit("split manifest does not match audited assignment")
    features = prepare_feature_matrix(records, smoke=False)

    train_indices = np.asarray(
        [index for index, record in enumerate(records) if record.split == "train"],
        dtype=np.int64,
    )
    stats_rng = np.random.default_rng(RANDOM_SEED)
    stats_frames = sample_frames(features, train_indices, min(20000, len(train_indices) * 2), stats_rng)
    gaussian_scale = np.std(stats_frames, axis=0).astype(np.float32)
    positive_scales = gaussian_scale[gaussian_scale > 1e-6]
    fallback_scale = float(np.median(positive_scales)) if len(positive_scales) else 0.1
    gaussian_scale = np.where(gaussian_scale > 1e-6, gaussian_scale, fallback_scale)
    gaussian_scale = np.clip(gaussian_scale, 1e-3, 5.0).astype(np.float32)

    requested = {
        "train": args.train_per_class,
        "val": args.val_per_class,
        "test": args.test_per_class,
    }
    datasets = {}
    allocations = {}
    for offset, split in enumerate(("train", "val", "test"), start=1):
        x, y, category, counts = build_partition(
            features,
            records,
            split,
            requested[split],
            gaussian_scale,
            offset * 1000,
        )
        datasets[split] = (x, y, category)
        allocations[split] = counts
        print(
            f"Activity data {split}: {len(x)} balanced frames; negatives={counts}",
            flush=True,
        )

    model = build_model(tf)
    callbacks = [
        tf.keras.callbacks.EarlyStopping(
            monitor="val_loss", patience=10, restore_best_weights=True
        ),
        tf.keras.callbacks.ReduceLROnPlateau(
            monitor="val_loss", factor=0.5, patience=5, min_lr=1e-6
        ),
    ]
    history = model.fit(
        datasets["train"][0],
        datasets["train"][1],
        validation_data=(datasets["val"][0], datasets["val"][1]),
        epochs=args.epochs,
        batch_size=args.batch_size,
        callbacks=callbacks,
        verbose=2,
        shuffle=True,
    )

    KERAS_PATH.parent.mkdir(parents=True, exist_ok=True)
    temporary_keras = KERAS_PATH.with_suffix(".tmp.keras")
    model.save(temporary_keras)
    temporary_keras.replace(KERAS_PATH)
    export_tflite(tf, model, TFLITE_PATH)

    reports = {}
    keras_probabilities = {}
    for split in ("val", "test"):
        probabilities = model.predict(
            datasets[split][0], batch_size=args.batch_size, verbose=0
        ).reshape(-1)
        keras_probabilities[split] = probabilities
        reports[split] = binary_report(
            datasets[split][1], probabilities, datasets[split][2]
        )

    parity_count = min(512, len(datasets["test"][0]))
    tflite_values, tflite_contract = tflite_predict(
        tf, TFLITE_PATH, datasets["test"][0][:parity_count]
    )
    keras_values = keras_probabilities["test"][:parity_count]
    parity = {
        "sample_count": parity_count,
        "maximum_absolute_probability_difference": float(
            np.max(np.abs(keras_values - tflite_values))
        ),
        "binary_prediction_agreement": float(
            np.mean((keras_values >= 0.5) == (tflite_values >= 0.5))
        ),
    }
    target_met = reports["val"]["accuracy"] > 0.95
    report = {
        "generated_at_utc": datetime.now(timezone.utc).isoformat(),
        "model_version": MODEL_VERSION,
        "status": "experimental_not_android_default",
        "feature_version": FEATURE_VERSION,
        "input_contract": {
            "shape": [1, FEATURE_SIZE],
            "dtype": "float32",
            "semantic": "single canonical OneHand162 frame",
        },
        "output_contract": {
            "shape": [1, 1],
            "dtype": "float32",
            "semantic": "sigmoid signing probability; index 0 is no-sign, index 1 is sign",
        },
        "architecture": ["Dense(64, relu)", "Dense(32, relu)", "Dense(1, sigmoid)"],
        "parameter_count": int(model.count_params()),
        "dataset": {
            "source": "FSL-105 canonical onehand162 sequences plus synthetic negatives",
            "split_assignment_sha256": audit["split"]["assignment_sha256"],
            "partition_policy": "existing signer-grouped train/val/test partition preserved before synthesis",
            "balanced_one_to_one": True,
            "counts_per_target": requested,
            "negative_allocations": allocations,
            "negative_weights": NEGATIVE_WEIGHTS,
            "boundary_label_caveat": (
                "FSL-105 windows contain active signs; first/last frames are a noisy "
                "entry/exit proxy rather than verified no-sign observations."
            ),
        },
        "training": {
            "epochs_requested": args.epochs,
            "epochs_completed": len(history.history["loss"]),
            "batch_size": args.batch_size,
            "seed": RANDOM_SEED,
            "history": {key: [float(value) for value in values] for key, values in history.history.items()},
        },
        "validation": reports["val"],
        "test": reports["test"],
        "target": {
            "metric": "held-out signer-grouped validation binary accuracy",
            "required": ">0.95",
            "met": target_met,
        },
        "tflite": {
            "path": project_relative(TFLITE_PATH),
            "size_bytes": TFLITE_PATH.stat().st_size,
            "sha256": sha256(TFLITE_PATH),
            "contract": tflite_contract,
            "keras_parity": parity,
        },
        "artifacts": {
            "keras": project_relative(KERAS_PATH),
            "tflite": project_relative(TFLITE_PATH),
            "report": project_relative(REPORT_PATH),
        },
    }
    write_json_atomic(REPORT_PATH, report)
    print(f"Validation accuracy : {reports['val']['accuracy']:.6f}")
    print(f"Test accuracy       : {reports['test']['accuracy']:.6f}")
    print(f">95% target met     : {target_met}")
    print(f"TFLite              : {TFLITE_PATH}")
    print(f"Report              : {REPORT_PATH}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
