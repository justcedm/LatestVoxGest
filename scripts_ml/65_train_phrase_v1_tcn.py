"""Train Basic TCN models for phrase-v1 feature profiles.

This trainer is profile-explicit so onehand162 phrase-v1 stays a five-label
contract while fullsign225 phrase-v1 stays a ten-label contract.
"""

from __future__ import annotations

import argparse
import json
import os
import time
from collections import Counter, defaultdict
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

from phrase_v1_dataset_tools import EXTERNAL_ROOT, PROFILES, ROOT, audit_dataset, file_sha1, safe_name


MODEL_DIR = ROOT / "model"
EPOCHS = int(os.environ.get("VOXGEST_PHRASE_V1_TCN_EPOCHS", "80"))
BATCH_SIZE = int(os.environ.get("VOXGEST_PHRASE_V1_TCN_BATCH_SIZE", "64"))
VAL_SPLIT = float(os.environ.get("VOXGEST_PHRASE_V1_VAL_SPLIT", "0.20"))
RANDOM_SEED = int(os.environ.get("VOXGEST_RANDOM_SEED", "42"))


def artifact_paths(profile: str):
    return {
        "model": MODEL_DIR / f"voxgest_tcn_{profile}.h5",
        "tflite": MODEL_DIR / f"voxgest_tcn_{profile}.tflite",
        "labels": MODEL_DIR / f"class_labels_tcn_{profile}.json",
        "report": MODEL_DIR / f"tcn_training_report_{profile}.json",
        "manifest": MODEL_DIR / f"runtime_manifest_{profile}.json",
    }


def parse_group_id(file_path: Path, label: str) -> str:
    parts = [safe_name(part) for part in file_path.stem.split("_")]
    signer = parts[0] if parts else "UNKNOWN"
    try:
        label_idx = parts.index(label)
    except ValueError:
        label_idx = -1
    source = "_".join(parts[1:label_idx]) if label_idx > 1 else "UNKNOWN"
    digest = file_sha1(file_path)[:8]
    return f"{label}/{signer}/{source}/{digest}"


def load_sequences(config):
    data_root = EXTERNAL_ROOT / config.output_folder
    sequences = []
    y_int = []
    groups = []
    class_stats = {}
    skipped_wrong_shape = 0
    unreadable = []

    for class_idx, label in enumerate(config.labels):
        label_dir = data_root / label
        records = []
        if label_dir.exists():
            for file_path in sorted(label_dir.glob("*.npy")):
                try:
                    arr = np.load(file_path, allow_pickle=False)
                except Exception as exc:
                    unreadable.append({"path": str(file_path), "label": label, "error": str(exc)})
                    continue
                if tuple(arr.shape) != tuple(config.expected_shape):
                    skipped_wrong_shape += 1
                    continue
                records.append((arr.astype(np.float32), parse_group_id(file_path, label)))

        class_stats[label] = {
            "sequences": len(records),
            "groups": len({group for _, group in records}),
            "minimum": config.minimums[label],
            "preferred": config.preferred[label],
            "used": len(records) >= config.minimums[label],
        }
        for arr, group in records:
            sequences.append(arr)
            y_int.append(class_idx)
            groups.append(group)

    missing = {
        label: max(0, config.minimums[label] - class_stats[label]["sequences"])
        for label in config.labels
        if class_stats[label]["sequences"] < config.minimums[label]
    }
    if missing:
        raise RuntimeError(f"{config.profile} is not minimum-ready: {missing}")

    return (
        np.asarray(sequences, dtype=np.float32),
        np.asarray(y_int, dtype=np.int32),
        np.asarray(groups, dtype=object),
        class_stats,
        skipped_wrong_shape,
        unreadable,
    )


def split_indices(y_int: np.ndarray, groups: np.ndarray):
    rng = np.random.default_rng(RANDOM_SEED)
    train = []
    val = []
    split_notes = {}

    for class_idx in sorted(set(y_int.tolist())):
        class_indices = np.where(y_int == class_idx)[0]
        class_groups = sorted(set(groups[class_indices].tolist()))
        rng.shuffle(class_groups)
        if len(class_groups) >= 2:
            n_val = max(1, int(round(len(class_groups) * VAL_SPLIT)))
            n_val = min(n_val, len(class_groups) - 1)
            val_groups = set(class_groups[:n_val])
            for idx in class_indices:
                (val if groups[idx] in val_groups else train).append(int(idx))
            split_notes[int(class_idx)] = {"mode": "grouped", "val_groups": len(val_groups), "groups": len(class_groups)}
        else:
            shuffled = class_indices.copy()
            rng.shuffle(shuffled)
            n_val = max(1, int(round(len(shuffled) * VAL_SPLIT)))
            n_val = min(n_val, len(shuffled) - 1)
            val.extend(int(idx) for idx in shuffled[:n_val])
            train.extend(int(idx) for idx in shuffled[n_val:])
            split_notes[int(class_idx)] = {"mode": "random_fallback", "groups": len(class_groups)}

    rng.shuffle(train)
    rng.shuffle(val)
    return np.asarray(train, dtype=np.int32), np.asarray(val, dtype=np.int32), split_notes


def residual_tcn_block(x, filters, kernel_size, dilation, dropout):
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


def build_model(config):
    filters = int(os.environ.get("VOXGEST_PHRASE_V1_TCN_FILTERS", "96"))
    kernel = int(os.environ.get("VOXGEST_PHRASE_V1_TCN_KERNEL", "3"))
    dropout = float(os.environ.get("VOXGEST_PHRASE_V1_TCN_DROPOUT", "0.20"))
    seq_len, feat_size = config.expected_shape

    inp = Input(shape=(seq_len, feat_size), name=f"{config.profile}_sequence_input")
    x = BatchNormalization(name="input_norm")(inp)
    x = Conv1D(filters, 1, padding="same", activation="relu")(x)
    for dilation in (1, 2, 4, 8):
        x = residual_tcn_block(x, filters, kernel, dilation, dropout)
    avg_pool = GlobalAveragePooling1D()(x)
    max_pool = GlobalMaxPooling1D()(x)
    x = Concatenate()([avg_pool, max_pool])
    x = Dense(128, activation="relu")(x)
    x = Dropout(0.35)(x)
    x = Dense(64, activation="relu")(x)
    x = Dropout(0.20)(x)
    out = Dense(len(config.labels), activation="softmax", name="output")(x)
    return Model(inp, out, name=f"VoxGest_TCN_{config.profile}")


def class_weights(labels):
    classes, counts = np.unique(labels, return_counts=True)
    total = float(len(labels))
    n_classes = float(len(classes))
    return {int(cls): float(total / (n_classes * count)) for cls, count in zip(classes, counts)}


def export_tflite(model, config, tflite_path: Path):
    converter = tf.lite.TFLiteConverter.from_keras_model(model)
    converter.optimizations = [tf.lite.Optimize.DEFAULT]
    tflite = converter.convert()
    tflite_path.write_bytes(tflite)

    try:
        from ai_edge_litert.interpreter import Interpreter
    except ImportError:
        Interpreter = tf.lite.Interpreter

    interpreter = Interpreter(model_path=str(tflite_path))
    interpreter.allocate_tensors()
    input_details = interpreter.get_input_details()[0]
    output_details = interpreter.get_output_details()[0]
    test = np.zeros((1, config.expected_shape[0], config.expected_shape[1]), dtype=np.float32)
    interpreter.set_tensor(input_details["index"], test)
    interpreter.invoke()
    out = interpreter.get_tensor(output_details["index"])
    return input_details["shape"].tolist(), output_details["shape"].tolist(), int(out.argmax())


def write_manifest(config, paths, report, input_shape, output_shape):
    feature_profile = "fullsign225" if config.expected_shape[1] == 225 else "onehand162"
    manifest = {
        "version": 1,
        "profile": config.profile,
        "status": "experimental_until_live_tested",
        "default_model": False,
        "replaces_demo10": False,
        "phrase_enabled_default": False,
        "feature_profile": feature_profile,
        "model_kind": "basic_tcn",
        "models": {
            "tcn": {
                "file": str(paths["tflite"].relative_to(ROOT)).replace("\\", "/"),
                "keras_file": str(paths["model"].relative_to(ROOT)).replace("\\", "/"),
                "labels_file": str(paths["labels"].relative_to(ROOT)).replace("\\", "/"),
                "training_report": str(paths["report"].relative_to(ROOT)).replace("\\", "/"),
                "input_shape": input_shape,
                "output_shape": output_shape,
                "sequence_length": config.expected_shape[0],
                "feature_size": config.expected_shape[1],
                "labels": list(config.labels),
                "negative_label": "NOTHING",
                "nothing_policy": "ignore_no_output",
            }
        },
        "dataset": {
            "features": str((EXTERNAL_ROOT / config.output_folder).relative_to(ROOT)).replace("\\", "/"),
            "class_stats": report["class_stats"],
        },
        "validation": {
            "best_val_accuracy": report["best_val_accuracy"],
            "per_class": report["per_class"],
            "split_notes": report["split_notes"],
        },
        "runtime_policy": {
            "input_policy": "accepted_predictions_only",
            "raw_prediction_policy": "never_update_sentence_output_directly",
            "nothing_policy": "NOTHING remains no-output",
        },
    }
    paths["manifest"].write_text(json.dumps(manifest, indent=2), encoding="utf-8")


def train_profile(profile: str):
    if profile not in PROFILES:
        raise SystemExit(f"Unknown profile: {profile}")
    config = PROFILES[profile]
    paths = artifact_paths(profile)
    MODEL_DIR.mkdir(exist_ok=True)

    print("=" * 72)
    print(f"VoxGest Basic TCN training: {profile}")
    print("=" * 72)
    print(f"Dataset : {EXTERNAL_ROOT / config.output_folder}")
    print(f"Shape   : {config.expected_shape}")
    print(f"Labels  : {list(config.labels)}")
    print(f"Epochs  : {EPOCHS}")

    audit = audit_dataset(config)
    if not audit["ready_min"]:
        raise RuntimeError(f"{profile} is not ready for minimum training: {audit['missing_minimum']}")

    X, y_int, groups, class_stats, skipped_wrong_shape, unreadable = load_sequences(config)
    train_idx, val_idx, split_notes = split_indices(y_int, groups)
    y = to_categorical(y_int, len(config.labels))
    X_train, X_val = X[train_idx], X[val_idx]
    y_train, y_val = y[train_idx], y[val_idx]
    y_train_int = y_int[train_idx]
    y_val_int = y_int[val_idx]

    labels_map = {label: idx for idx, label in enumerate(config.labels)}
    paths["labels"].write_text(json.dumps(labels_map, indent=2), encoding="utf-8")

    print(f"Sequences: {len(X)}")
    print(f"Train    : {len(X_train)}")
    print(f"Val      : {len(X_val)}")
    print(f"Labels   : {paths['labels']}")

    model = build_model(config)
    model.compile(
        optimizer=tf.keras.optimizers.Adam(learning_rate=0.001),
        loss="categorical_crossentropy",
        metrics=["accuracy"],
    )
    model.summary()

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
            ModelCheckpoint(str(paths["model"]), monitor="val_accuracy", save_best_only=True, verbose=1),
            ReduceLROnPlateau(monitor="val_loss", factor=0.5, patience=5, min_lr=1e-6, verbose=1),
        ],
        verbose=1,
    )
    model.save(paths["model"])

    pred_probs = model.predict(X_val, verbose=0)
    preds = np.argmax(pred_probs, axis=1)
    best_val = float(max(history.history.get("val_accuracy", [0.0])) * 100.0)
    per_class = {}
    top_miss = {}
    for idx, label in enumerate(config.labels):
        mask = y_val_int == idx
        total = int(mask.sum())
        correct = int((preds[mask] == idx).sum()) if total else 0
        acc = float(correct / total * 100.0) if total else 0.0
        wrong = preds[mask][preds[mask] != idx] if total else []
        miss = "-"
        if len(wrong):
            values, counts = np.unique(wrong, return_counts=True)
            miss = config.labels[int(values[int(np.argmax(counts))])]
        per_class[label] = {"correct": correct, "total": total, "accuracy": acc}
        top_miss[label] = miss

    input_shape, output_shape, smoke_class = export_tflite(model, config, paths["tflite"])
    report = {
        "profile": profile,
        "dataset": str(EXTERNAL_ROOT / config.output_folder),
        "model": str(paths["model"]),
        "tflite": str(paths["tflite"]),
        "labels": str(paths["labels"]),
        "seq_len": config.expected_shape[0],
        "feature_size": config.expected_shape[1],
        "input_shape": input_shape,
        "output_shape": output_shape,
        "classes": list(config.labels),
        "class_stats": class_stats,
        "minimums": config.minimums,
        "preferred": config.preferred,
        "train_sequences": int(len(X_train)),
        "val_sequences": int(len(X_val)),
        "split_notes": split_notes,
        "best_val_accuracy": best_val,
        "per_class": per_class,
        "top_miss": top_miss,
        "skipped_wrong_shape": int(skipped_wrong_shape),
        "unreadable_files": unreadable,
        "training_seconds": float(time.time() - started),
        "tflite_smoke_class": int(smoke_class),
    }
    paths["report"].write_text(json.dumps(report, indent=2), encoding="utf-8")
    write_manifest(config, paths, report, input_shape, output_shape)

    print("=" * 72)
    print(f"COMPLETE: {profile}")
    print(f"Best val: {best_val:.2f}%")
    print(f"Model   : {paths['model']}")
    print(f"TFLite  : {paths['tflite']}")
    print(f"Report  : {paths['report']}")
    print(f"Manifest: {paths['manifest']}")
    print("=" * 72)
    return report


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("profile", choices=sorted(PROFILES))
    args = parser.parse_args()
    train_profile(args.profile)


if __name__ == "__main__":
    main()
