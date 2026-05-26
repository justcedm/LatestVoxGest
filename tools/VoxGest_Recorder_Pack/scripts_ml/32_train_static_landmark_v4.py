"""Train static alphabet landmark model v4.

Input contract stays [1, 63]. Labels stay A-Z, del, space, nothing.
The trainer uses group-aware validation when webcam calibration sessions are
present so one recording session cannot leak into both train and validation.
"""

import json
import os
import re
from datetime import datetime
from pathlib import Path

os.environ["PROTOCOL_BUFFERS_PYTHON_IMPLEMENTATION"] = "python"
os.environ["TF_CPP_MIN_LOG_LEVEL"] = "3"

import numpy as np
import tensorflow as tf
from tensorflow.keras import Input
from tensorflow.keras.callbacks import EarlyStopping, ModelCheckpoint, ReduceLROnPlateau
from tensorflow.keras.layers import BatchNormalization, Dense, Dropout
from tensorflow.keras.models import Model
from tensorflow.keras.utils import to_categorical


ROOT = Path(__file__).resolve().parents[1]
LABELS = list("ABCDEFGHIJKLMNOPQRSTUVWXYZ") + ["del", "space", "nothing"]
DATASET_ENV = os.environ.get("VOXGEST_STATIC_DATASETS", "")
DATA_DIRS = [
    Path(item.strip())
    for item in DATASET_ENV.split(";")
    if item.strip()
] or [
    ROOT / "dataset_static_calibration",
    ROOT / "dataset_static_landmarks",
]

MODEL_PATH = Path(os.environ.get("VOXGEST_STATIC_V4_MODEL", ROOT / "model" / "voxgest_static_v4.h5"))
LABELS_PATH = Path(os.environ.get("VOXGEST_STATIC_V4_LABELS", ROOT / "model" / "class_labels_static_v4.json"))
TFLITE_PATH = Path(os.environ.get("VOXGEST_STATIC_V4_TFLITE", ROOT / "model" / "voxgest_static_v4.tflite"))
REPORT_PATH = Path(os.environ.get("VOXGEST_STATIC_V4_REPORT", ROOT / "model" / "static_v4_training_report.json"))

EPOCHS = int(os.environ.get("VOXGEST_STATIC_V4_EPOCHS", "120"))
BATCH_SIZE = int(os.environ.get("VOXGEST_STATIC_V4_BATCH_SIZE", "64"))
VAL_SPLIT = float(os.environ.get("VOXGEST_STATIC_V4_VAL_SPLIT", "0.20"))
MIN_SEQS = int(os.environ.get("VOXGEST_STATIC_MIN_SEQS_PER_CLASS", "20"))
MIN_GROUPS = int(os.environ.get("VOXGEST_STATIC_MIN_GROUPS_PER_CLASS", "2"))
RANDOM_SEED = int(os.environ.get("VOXGEST_RANDOM_SEED", "42"))


def load_metadata(data_dir):
    for name in ("metadata_static_calibration_v1.json", "metadata_static_v1.json"):
        path = data_dir / name
        if path.exists():
            with open(path, "r", encoding="utf-8") as f:
                return json.load(f).get("samples", {})
    return {}


def infer_group_id(label, file_name, metadata):
    key = f"{label}/{file_name}"
    if key in metadata:
        return metadata[key].get("source_id", key)
    stem = Path(file_name).stem
    manual = re.match(r"(manual_.+?)_seq\d+$", stem)
    if manual:
        return f"{label}/{manual.group(1)}"
    aug = re.match(r"(.+)_aug\d+$", stem)
    if aug:
        return f"{label}/{aug.group(1)}"
    return f"{label}/{stem}"


def iter_records():
    for data_dir in DATA_DIRS:
        if not data_dir.exists():
            continue
        metadata = load_metadata(data_dir)
        for label in LABELS:
            label_dir = data_dir / label
            if not label_dir.exists():
                continue
            for file_path in sorted(label_dir.glob("*.npy")):
                yield data_dir, label, file_path, metadata


def load_sequences():
    records_by_label = {label: [] for label in LABELS}
    wrong_shape = 0
    for data_dir, label, file_path, metadata in iter_records():
        try:
            arr = np.load(file_path, allow_pickle=False)
        except Exception:
            continue
        if arr.shape != (63,):
            wrong_shape += 1
            continue
        group_id = f"{data_dir.name}/{infer_group_id(label, file_path.name, metadata)}"
        records_by_label[label].append((arr.astype(np.float32), group_id))

    class_stats = {}
    missing = []
    for label, records in records_by_label.items():
        groups = {group for _, group in records}
        used = len(records) >= MIN_SEQS and len(groups) >= MIN_GROUPS
        class_stats[label] = {
            "sequences": len(records),
            "groups": len(groups),
            "used": used,
            "need_sequences": max(0, MIN_SEQS - len(records)),
            "need_groups": max(0, MIN_GROUPS - len(groups)),
        }
        if not used:
            missing.append(label)

    if missing:
        return None, None, None, class_stats, wrong_shape, missing

    X = []
    y = []
    groups = []
    for class_idx, label in enumerate(LABELS):
        for arr, group_id in records_by_label[label]:
            X.append(arr)
            y.append(class_idx)
            groups.append(group_id)
    return (
        np.array(X, dtype=np.float32),
        np.array(y, dtype=np.int32),
        np.array(groups, dtype=object),
        class_stats,
        wrong_shape,
        [],
    )


def grouped_split(labels, groups):
    rng = np.random.default_rng(RANDOM_SEED)
    train_idx = []
    val_idx = []
    for class_idx in sorted(set(labels.tolist())):
        class_indices = np.where(labels == class_idx)[0]
        class_groups = sorted(set(groups[class_indices].tolist()))
        rng.shuffle(class_groups)
        n_val_groups = max(1, int(round(len(class_groups) * VAL_SPLIT)))
        if len(class_groups) > 1:
            n_val_groups = min(n_val_groups, len(class_groups) - 1)
        val_groups = set(class_groups[:n_val_groups])
        for idx in class_indices:
            if groups[idx] in val_groups:
                val_idx.append(int(idx))
            else:
                train_idx.append(int(idx))
    rng.shuffle(train_idx)
    rng.shuffle(val_idx)
    return np.array(train_idx, dtype=np.int32), np.array(val_idx, dtype=np.int32)


def build_model():
    inp = Input(shape=(63,), name="static_hand_landmarks")
    x = BatchNormalization(name="input_norm")(inp)
    x = Dense(256, activation="relu")(x)
    x = Dropout(0.35)(x)
    x = Dense(192, activation="relu")(x)
    x = Dropout(0.30)(x)
    x = Dense(128, activation="relu")(x)
    x = Dropout(0.20)(x)
    out = Dense(len(LABELS), activation="softmax", name="output")(x)
    return Model(inp, out, name="VoxGest_Static_Landmark_v4")


def export_tflite(model):
    converter = tf.lite.TFLiteConverter.from_keras_model(model)
    converter.optimizations = [tf.lite.Optimize.DEFAULT]
    tflite = converter.convert()
    with open(TFLITE_PATH, "wb") as f:
        f.write(tflite)
    interpreter = tf.lite.Interpreter(model_path=str(TFLITE_PATH))
    interpreter.allocate_tensors()
    input_shape = interpreter.get_input_details()[0]["shape"].tolist()
    output_shape = interpreter.get_output_details()[0]["shape"].tolist()
    return input_shape, output_shape


def write_readiness_report(class_stats, wrong_shape, missing):
    REPORT_PATH.parent.mkdir(parents=True, exist_ok=True)
    payload = {
        "generated_at": datetime.now().isoformat(timespec="seconds"),
        "status": "not_ready",
        "datasets": [str(path) for path in DATA_DIRS],
        "labels": LABELS,
        "minimum_sequences_per_class": MIN_SEQS,
        "minimum_groups_per_class": MIN_GROUPS,
        "wrong_shape_files": wrong_shape,
        "missing_or_underfilled_labels": missing,
        "class_stats": class_stats,
    }
    with open(REPORT_PATH, "w", encoding="utf-8") as f:
        json.dump(payload, f, indent=2)


def main():
    print("=" * 76)
    print("VoxGest static landmark trainer v4")
    print("=" * 76)
    print(f"Datasets : {[str(path) for path in DATA_DIRS]}")
    print(f"Labels   : {LABELS}")
    print(f"Shape    : 63")
    print()

    MODEL_PATH.parent.mkdir(parents=True, exist_ok=True)
    X, y_int, groups, class_stats, wrong_shape, missing = load_sequences()
    for label in LABELS:
        stats = class_stats[label]
        status = "OK" if stats["used"] else "FIX"
        print(
            f"{status:>3} {label:<8} seq={stats['sequences']:>4} "
            f"groups={stats['groups']:>3} need_seq={stats['need_sequences']:>3} "
            f"need_groups={stats['need_groups']:>2}"
        )

    if missing:
        print("\nStatic v4 training stopped: every label needs enough calibration data.")
        print(f"Report: {REPORT_PATH}")
        write_readiness_report(class_stats, wrong_shape, missing)
        return

    labels_map = {label: idx for idx, label in enumerate(LABELS)}
    with open(LABELS_PATH, "w", encoding="utf-8") as f:
        json.dump(labels_map, f, indent=2)

    train_idx, val_idx = grouped_split(y_int, groups)
    X_train, X_val = X[train_idx], X[val_idx]
    y_train_int, y_val_int = y_int[train_idx], y_int[val_idx]
    y_train = to_categorical(y_train_int, len(LABELS))
    y_val = to_categorical(y_val_int, len(LABELS))

    print(f"\nTrain sequences : {len(X_train)}")
    print(f"Val sequences   : {len(X_val)}")
    print(f"Train groups    : {len(set(groups[train_idx].tolist()))}")
    print(f"Val groups      : {len(set(groups[val_idx].tolist()))}")

    model = build_model()
    model.compile(
        optimizer=tf.keras.optimizers.Adam(learning_rate=0.001),
        loss="categorical_crossentropy",
        metrics=["accuracy"],
    )
    callbacks = [
        ModelCheckpoint(str(MODEL_PATH), monitor="val_accuracy", save_best_only=True, mode="max", verbose=1),
        EarlyStopping(monitor="val_accuracy", patience=18, restore_best_weights=True, mode="max"),
        ReduceLROnPlateau(monitor="val_loss", factor=0.5, patience=8, min_lr=1e-5),
    ]
    history = model.fit(
        X_train,
        y_train,
        validation_data=(X_val, y_val),
        epochs=EPOCHS,
        batch_size=BATCH_SIZE,
        callbacks=callbacks,
        verbose=2,
    )

    best_model = tf.keras.models.load_model(MODEL_PATH)
    probs = best_model.predict(X_val, verbose=0)
    preds = probs.argmax(axis=1)
    per_class = {}
    for idx, label in enumerate(LABELS):
        mask = y_val_int == idx
        total = int(mask.sum())
        correct = int((preds[mask] == idx).sum())
        per_class[label] = {
            "correct": correct,
            "total": total,
            "accuracy": (correct / total * 100.0) if total else 0.0,
        }

    input_shape, output_shape = export_tflite(best_model)
    best_val = max(history.history.get("val_accuracy", [0.0])) * 100.0
    report = {
        "generated_at": datetime.now().isoformat(timespec="seconds"),
        "status": "trained",
        "datasets": [str(path) for path in DATA_DIRS],
        "model": str(MODEL_PATH),
        "labels": str(LABELS_PATH),
        "tflite": str(TFLITE_PATH),
        "input_shape": input_shape,
        "output_shape": output_shape,
        "classes": LABELS,
        "class_stats": class_stats,
        "wrong_shape_files": wrong_shape,
        "train_sequences": int(len(X_train)),
        "val_sequences": int(len(X_val)),
        "train_groups": int(len(set(groups[train_idx].tolist()))),
        "val_groups": int(len(set(groups[val_idx].tolist()))),
        "best_grouped_val_accuracy": best_val,
        "per_class": per_class,
    }
    with open(REPORT_PATH, "w", encoding="utf-8") as f:
        json.dump(report, f, indent=2)

    print("\nSaved:")
    print(f"  Model : {MODEL_PATH}")
    print(f"  TFLite: {TFLITE_PATH}")
    print(f"  Labels: {LABELS_PATH}")
    print(f"  Report: {REPORT_PATH}")
    print(f"  Best grouped val accuracy: {best_val:.2f}%")


if __name__ == "__main__":
    main()
