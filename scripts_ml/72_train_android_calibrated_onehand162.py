"""Train the Android-calibrated onehand162 TCN model.

This trainer only consumes phone-native feature arrays imported by
70_import_android_onehand_exports.py. It never overwrites the original
onehand162_phrase_v1 artifacts.
"""

from __future__ import annotations

import argparse
import json
import os
import time
from pathlib import Path

os.environ["PROTOCOL_BUFFERS_PYTHON_IMPLEMENTATION"] = "python"
os.environ["TF_CPP_MIN_LOG_LEVEL"] = "3"

import numpy as np
import tensorflow as tf
from tensorflow.keras import Input
from tensorflow.keras.callbacks import EarlyStopping, ModelCheckpoint, ReduceLROnPlateau
from tensorflow.keras.layers import (
    Activation,
    Add,
    BatchNormalization,
    Concatenate,
    Conv1D,
    Dense,
    Dropout,
    GlobalAveragePooling1D,
    GlobalMaxPooling1D,
    SpatialDropout1D,
)
from tensorflow.keras.models import Model
from tensorflow.keras.utils import to_categorical


ROOT = Path(__file__).resolve().parents[1]
DATASET_ROOT = ROOT / "external_datasets" / "android_onehand162_phrase_v1_features"
MODEL_DIR = ROOT / "model"
PROFILE = "onehand162_android_calibrated_v1"
LABELS = ("WHAT", "YOUR", "NAME", "MY", "NOTHING")
EXPECTED_SHAPE = (30, 162)
MINIMUMS = {"MY": 50, "WHAT": 50, "YOUR": 50, "NAME": 50, "NOTHING": 100}
PREFERRED = {"MY": 80, "WHAT": 80, "YOUR": 80, "NAME": 80, "NOTHING": 150}
EPOCHS = int(os.environ.get("VOXGEST_ANDROID_ONEHAND_EPOCHS", "90"))
BATCH_SIZE = int(os.environ.get("VOXGEST_ANDROID_ONEHAND_BATCH_SIZE", "32"))
VAL_SPLIT = float(os.environ.get("VOXGEST_ANDROID_ONEHAND_VAL_SPLIT", "0.20"))
RANDOM_SEED = int(os.environ.get("VOXGEST_RANDOM_SEED", "42"))

H5_PATH = MODEL_DIR / "voxgest_tcn_onehand162_android_calibrated_v1.h5"
TFLITE_PATH = MODEL_DIR / "voxgest_tcn_onehand162_android_calibrated_v1.tflite"
LABELS_PATH = MODEL_DIR / "class_labels_tcn_onehand162_android_calibrated_v1.json"
MANIFEST_PATH = MODEL_DIR / "runtime_manifest_onehand162_android_calibrated_v1.json"
REPORT_PATH = MODEL_DIR / "tcn_training_report_onehand162_android_calibrated_v1.json"


def load_dataset(dataset_root: Path):
    sequences = []
    y_int = []
    paths = []
    class_stats = {}
    unreadable = []
    wrong_shape = []

    for class_idx, label in enumerate(LABELS):
        label_dir = dataset_root / label
        count = 0
        if label_dir.exists():
            for npy_path in sorted(label_dir.glob("*.npy")):
                try:
                    arr = np.load(npy_path, allow_pickle=False).astype(np.float32)
                except Exception as exc:
                    unreadable.append({"path": str(npy_path), "label": label, "error": str(exc)})
                    continue
                if tuple(arr.shape) != EXPECTED_SHAPE:
                    wrong_shape.append({"path": str(npy_path), "label": label, "shape": list(arr.shape)})
                    continue
                sequences.append(arr)
                y_int.append(class_idx)
                paths.append(npy_path)
                count += 1
        class_stats[label] = {
            "sequences": count,
            "minimum": MINIMUMS[label],
            "preferred": PREFERRED[label],
            "minimum_ready": count >= MINIMUMS[label],
            "preferred_ready": count >= PREFERRED[label],
        }

    missing = {label: max(0, MINIMUMS[label] - class_stats[label]["sequences"]) for label in LABELS}
    missing = {label: value for label, value in missing.items() if value > 0}
    if missing:
        raise RuntimeError(f"Android calibrated onehand162 dataset is not minimum-ready: {missing}")
    if unreadable or wrong_shape:
        raise RuntimeError(f"Dataset has unreadable={len(unreadable)} wrong_shape={len(wrong_shape)} files. Run audit first.")

    return (
        np.asarray(sequences, dtype=np.float32),
        np.asarray(y_int, dtype=np.int32),
        np.asarray(paths, dtype=object),
        class_stats,
    )


def split_indices(y_int: np.ndarray):
    rng = np.random.default_rng(RANDOM_SEED)
    train = []
    val = []
    split_notes = {}
    for class_idx in sorted(set(y_int.tolist())):
        indices = np.where(y_int == class_idx)[0]
        shuffled = indices.copy()
        rng.shuffle(shuffled)
        n_val = max(1, int(round(len(shuffled) * VAL_SPLIT)))
        n_val = min(n_val, len(shuffled) - 1)
        val.extend(int(idx) for idx in shuffled[:n_val])
        train.extend(int(idx) for idx in shuffled[n_val:])
        split_notes[LABELS[class_idx]] = {"mode": "stratified_random", "train": len(shuffled) - n_val, "val": n_val}
    rng.shuffle(train)
    rng.shuffle(val)
    return np.asarray(train, dtype=np.int32), np.asarray(val, dtype=np.int32), split_notes


def residual_tcn_block(x, filters: int, kernel_size: int, dilation: int, dropout: float):
    shortcut = x
    x = Conv1D(filters, kernel_size, padding="same", dilation_rate=dilation, use_bias=False)(x)
    x = BatchNormalization()(x)
    x = Activation("relu")(x)
    x = SpatialDropout1D(dropout)(x)
    x = Conv1D(filters, kernel_size, padding="same", dilation_rate=dilation, use_bias=False)(x)
    x = BatchNormalization()(x)
    if shortcut.shape[-1] != filters:
        shortcut = Conv1D(filters, 1, padding="same", use_bias=False)(shortcut)
    x = Add()([shortcut, x])
    return Activation("relu")(x)


def build_model():
    filters = int(os.environ.get("VOXGEST_ANDROID_ONEHAND_FILTERS", "96"))
    kernel = int(os.environ.get("VOXGEST_ANDROID_ONEHAND_KERNEL", "3"))
    dropout = float(os.environ.get("VOXGEST_ANDROID_ONEHAND_DROPOUT", "0.20"))
    inp = Input(shape=EXPECTED_SHAPE, name="onehand162_android_sequence_input")
    x = BatchNormalization(name="input_norm")(inp)
    x = Conv1D(filters, 1, padding="same", activation="relu")(x)
    for dilation in (1, 2, 4, 8):
        x = residual_tcn_block(x, filters, kernel, dilation, dropout)
    x = Concatenate()([GlobalAveragePooling1D()(x), GlobalMaxPooling1D()(x)])
    x = Dense(128, activation="relu")(x)
    x = Dropout(0.35)(x)
    x = Dense(64, activation="relu")(x)
    x = Dropout(0.20)(x)
    out = Dense(len(LABELS), activation="softmax", name="output")(x)
    return Model(inp, out, name="VoxGest_TCN_onehand162_android_calibrated_v1")


def class_weights(y_train_int: np.ndarray):
    classes, counts = np.unique(y_train_int, return_counts=True)
    total = float(len(y_train_int))
    n_classes = float(len(classes))
    return {int(cls): float(total / (n_classes * count)) for cls, count in zip(classes, counts)}


def export_tflite(model: Model):
    converter = tf.lite.TFLiteConverter.from_keras_model(model)
    converter.optimizations = [tf.lite.Optimize.DEFAULT]
    tflite = converter.convert()
    TFLITE_PATH.write_bytes(tflite)

    try:
        from ai_edge_litert.interpreter import Interpreter
    except ImportError:
        Interpreter = tf.lite.Interpreter

    interpreter = Interpreter(model_path=str(TFLITE_PATH))
    interpreter.allocate_tensors()
    input_shape = interpreter.get_input_details()[0]["shape"].tolist()
    output_shape = interpreter.get_output_details()[0]["shape"].tolist()
    return input_shape, output_shape


def write_manifest(report, input_shape, output_shape):
    manifest = {
        "version": 1,
        "profile": PROFILE,
        "status": "android_calibrated_experimental_until_live_tested",
        "default_model": False,
        "replaces_onehand162": False,
        "feature_profile": "onehand162",
        "dominant_hand": "right",
        "mirrored_input": True,
        "single_hand_pose": True,
        "model_kind": "basic_tcn",
        "models": {
            "tcn": {
                "file": "model/voxgest_tcn_onehand162_android_calibrated_v1.tflite",
                "keras_file": "model/voxgest_tcn_onehand162_android_calibrated_v1.h5",
                "labels_file": "model/class_labels_tcn_onehand162_android_calibrated_v1.json",
                "training_report": "model/tcn_training_report_onehand162_android_calibrated_v1.json",
                "input_shape": input_shape,
                "output_shape": output_shape,
                "sequence_length": EXPECTED_SHAPE[0],
                "feature_size": EXPECTED_SHAPE[1],
                "labels": list(LABELS),
                "negative_label": "NOTHING",
                "nothing_policy": "ignore_no_output",
            }
        },
        "dataset": {
            "features": "external_datasets/android_onehand162_phrase_v1_features",
            "class_stats": report["class_stats"],
            "minimums": MINIMUMS,
            "preferred": PREFERRED,
        },
        "runtime_policy": {
            "input_policy": "android_camera_feature_arrays_recorded_before_inference",
            "nothing_policy": "NOTHING remains no-output",
            "android_flag": "USE_ANDROID_CALIBRATED_ONEHAND_MODEL",
        },
    }
    MANIFEST_PATH.write_text(json.dumps(manifest, indent=2), encoding="utf-8")


def train(dataset_root: Path):
    MODEL_DIR.mkdir(exist_ok=True)
    X, y_int, paths, class_stats = load_dataset(dataset_root)
    train_idx, val_idx, split_notes = split_indices(y_int)
    y = to_categorical(y_int, len(LABELS))
    X_train, X_val = X[train_idx], X[val_idx]
    y_train, y_val = y[train_idx], y[val_idx]
    y_train_int, y_val_int = y_int[train_idx], y_int[val_idx]

    LABELS_PATH.write_text(json.dumps({label: idx for idx, label in enumerate(LABELS)}, indent=2), encoding="utf-8")
    model = build_model()
    model.compile(
        optimizer=tf.keras.optimizers.Adam(learning_rate=0.001),
        loss="categorical_crossentropy",
        metrics=["accuracy"],
    )

    started = time.time()
    history = model.fit(
        X_train,
        y_train,
        validation_data=(X_val, y_val),
        epochs=EPOCHS,
        batch_size=BATCH_SIZE,
        class_weight=class_weights(y_train_int),
        callbacks=[
            EarlyStopping(monitor="val_accuracy", patience=14, restore_best_weights=True, verbose=1),
            ModelCheckpoint(str(H5_PATH), monitor="val_accuracy", save_best_only=True, verbose=1),
            ReduceLROnPlateau(monitor="val_loss", factor=0.5, patience=5, min_lr=1e-6, verbose=1),
        ],
        verbose=1,
    )
    model.save(H5_PATH)

    pred_probs = model.predict(X_val, verbose=0)
    preds = np.argmax(pred_probs, axis=1)
    best_val = float(max(history.history.get("val_accuracy", [0.0])) * 100.0)
    per_class = {}
    for idx, label in enumerate(LABELS):
        mask = y_val_int == idx
        total = int(mask.sum())
        correct = int((preds[mask] == idx).sum()) if total else 0
        per_class[label] = {
            "correct": correct,
            "total": total,
            "accuracy": float(correct / total * 100.0) if total else 0.0,
        }

    input_shape, output_shape = export_tflite(model)
    report = {
        "profile": PROFILE,
        "dataset": str(dataset_root),
        "model": str(H5_PATH),
        "tflite": str(TFLITE_PATH),
        "labels": str(LABELS_PATH),
        "runtime_manifest": str(MANIFEST_PATH),
        "input_shape": input_shape,
        "output_shape": output_shape,
        "classes": list(LABELS),
        "class_stats": class_stats,
        "minimums": MINIMUMS,
        "preferred": PREFERRED,
        "train_sequences": int(len(X_train)),
        "val_sequences": int(len(X_val)),
        "split_notes": split_notes,
        "best_val_accuracy": best_val,
        "per_class": per_class,
        "training_seconds": float(time.time() - started),
    }
    REPORT_PATH.write_text(json.dumps(report, indent=2), encoding="utf-8")
    write_manifest(report, input_shape, output_shape)
    return report


def main():
    parser = argparse.ArgumentParser(description="Train Android-calibrated onehand162 TCN.")
    parser.add_argument("--dataset", type=Path, default=DATASET_ROOT)
    args = parser.parse_args()

    print("=" * 72)
    print("VoxGest Android-calibrated OneHand162 trainer")
    print("=" * 72)
    print(f"Dataset: {args.dataset}")
    print(f"Labels : {list(LABELS)}")
    print(f"Shape  : {EXPECTED_SHAPE}")
    report = train(args.dataset)
    print("=" * 72)
    print(f"Best val : {report['best_val_accuracy']:.2f}%")
    print(f"H5       : {H5_PATH}")
    print(f"TFLite   : {TFLITE_PATH}")
    print(f"Labels   : {LABELS_PATH}")
    print(f"Manifest : {MANIFEST_PATH}")
    print(f"Report   : {REPORT_PATH}")
    print("=" * 72)


if __name__ == "__main__":
    main()
