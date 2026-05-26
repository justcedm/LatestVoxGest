"""Train a small TCN for dynamic alphabet letters J/Z."""

import json
import os
import re
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

from lstm_features import (
    FEAT_SIZE,
    SEQ_LEN,
    apply_sequence_feature_policy,
    configured_hand_preference,
    single_hand_pose_enabled,
)
from motion_letter_config import MOTION_LETTER_LABELS


ROOT = Path(__file__).resolve().parents[1]
DATA_DIR = Path(os.environ.get("VOXGEST_MOTION_LETTER_DATASET", ROOT / "dataset_motion_letters"))
METADATA_PATH = DATA_DIR / "metadata_motion_letters_v1.json"

MODEL_PATH = ROOT / "model" / "voxgest_motion_letters_tcn_v1.h5"
LABELS_PATH = ROOT / "model" / "class_labels_motion_letters_tcn_v1.json"
TFLITE_PATH = ROOT / "model" / "voxgest_motion_letters_tcn_v1.tflite"
REPORT_PATH = ROOT / "model" / "motion_letters_tcn_training_report.json"

EPOCHS = int(os.environ.get("VOXGEST_MOTION_LETTER_EPOCHS", "100"))
BATCH_SIZE = int(os.environ.get("VOXGEST_MOTION_LETTER_BATCH_SIZE", "32"))
VAL_SPLIT = float(os.environ.get("VOXGEST_MOTION_LETTER_VAL_SPLIT", "0.25"))
MIN_SEQS = int(os.environ.get("VOXGEST_MOTION_LETTER_MIN_SEQS_PER_CLASS", "60"))
MIN_GROUPS = int(os.environ.get("VOXGEST_MOTION_LETTER_MIN_GROUPS_PER_CLASS", "3"))
RANDOM_SEED = int(os.environ.get("VOXGEST_RANDOM_SEED", "42"))


def load_metadata():
    if METADATA_PATH.exists():
        with open(METADATA_PATH, "r", encoding="utf-8") as f:
            return json.load(f).get("samples", {})
    return {}


def infer_group_id(label, file_name, metadata):
    key = f"{label}/{file_name}"
    if key in metadata:
        return metadata[key].get("source_id", key)
    stem = Path(file_name).stem
    manual_match = re.match(r"(manual_.+?)_seq\d+$", stem)
    if manual_match:
        return f"{label}/{manual_match.group(1)}"
    return f"{label}/{stem}"


def infer_mirrored_input(label, file_name, metadata):
    sample = metadata.get(f"{label}/{file_name}", {})
    if "mirrored_input" in sample:
        return bool(sample["mirrored_input"])
    return file_name.startswith("manual_")


def infer_hand_preference(label, file_name, metadata):
    sample = metadata.get(f"{label}/{file_name}", {})
    return sample.get("dominant_hand") or configured_hand_preference()


def load_sequences():
    if not DATA_DIR.exists():
        raise FileNotFoundError(
            "Motion-letter dataset not found. Record samples first:\n"
            "  python scripts_ml/34_record_motion_letters.py J Z NOTHING"
        )

    metadata = load_metadata()
    sequences = []
    labels = []
    groups = []
    class_stats = {}
    classes = []
    wrong_shape = 0

    for label in MOTION_LETTER_LABELS:
        label_dir = DATA_DIR / label
        records = []
        if label_dir.exists():
            for file_path in sorted(label_dir.glob("*.npy")):
                try:
                    arr = np.load(file_path, allow_pickle=False)
                except Exception:
                    continue
                if arr.shape != (SEQ_LEN, FEAT_SIZE):
                    wrong_shape += 1
                    continue
                arr = apply_sequence_feature_policy(
                    arr,
                    hand_preference=infer_hand_preference(label, file_path.name, metadata),
                    mirrored_input=infer_mirrored_input(label, file_path.name, metadata),
                )
                records.append((arr.astype(np.float32), infer_group_id(label, file_path.name, metadata)))

        group_count = len({group for _, group in records})
        used = len(records) >= MIN_SEQS and group_count >= MIN_GROUPS
        class_stats[label] = {
            "sequences": len(records),
            "groups": group_count,
            "used": used,
            "need_sequences": max(0, MIN_SEQS - len(records)),
            "need_groups": max(0, MIN_GROUPS - group_count),
        }
        status = "OK  " if used else "SKIP"
        print(
            f"  {status} {label:<8} sequences={len(records):>4} "
            f"groups={group_count:>3} need_seq={class_stats[label]['need_sequences']:>3} "
            f"need_groups={class_stats[label]['need_groups']:>2}"
        )

        if used:
            class_idx = len(classes)
            classes.append(label)
            sequences.extend(arr for arr, _ in records)
            labels.extend([class_idx] * len(records))
            groups.extend(group for _, group in records)

    return (
        np.array(sequences, dtype=np.float32),
        np.array(labels, dtype=np.int32),
        np.array(groups, dtype=object),
        classes,
        class_stats,
        wrong_shape,
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


def residual_block(x, filters, kernel_size, dilation, dropout):
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


def build_model(num_classes):
    filters = int(os.environ.get("VOXGEST_MOTION_LETTER_FILTERS", "64"))
    dropout = float(os.environ.get("VOXGEST_MOTION_LETTER_DROPOUT", "0.25"))
    inp = Input(shape=(SEQ_LEN, FEAT_SIZE), name="motion_letter_sequence_input")
    x = BatchNormalization(name="input_norm")(inp)
    x = Conv1D(filters, 1, padding="same", activation="relu")(x)
    for dilation in (1, 2, 4, 8):
        x = residual_block(x, filters, 3, dilation, dropout)
    avg_pool = GlobalAveragePooling1D()(x)
    max_pool = GlobalMaxPooling1D()(x)
    x = Concatenate()([avg_pool, max_pool])
    x = Dense(96, activation="relu")(x)
    x = Dropout(0.35)(x)
    x = Dense(48, activation="relu")(x)
    out = Dense(num_classes, activation="softmax", name="output")(x)
    return Model(inp, out, name="VoxGest_Motion_Letter_TCN_v1")


def export_tflite(model):
    converter = tf.lite.TFLiteConverter.from_keras_model(model)
    converter.optimizations = [tf.lite.Optimize.DEFAULT]
    tflite = converter.convert()
    with open(TFLITE_PATH, "wb") as f:
        f.write(tflite)
    interpreter = tf.lite.Interpreter(model_path=str(TFLITE_PATH))
    interpreter.allocate_tensors()
    return (
        interpreter.get_input_details()[0]["shape"].tolist(),
        interpreter.get_output_details()[0]["shape"].tolist(),
    )


def main():
    print("=" * 76)
    print("  VoxGest Motion Letter TCN Trainer | J/Z")
    print("=" * 76)
    print(f"  Dataset : {DATA_DIR}")
    print(f"  Shape   : {SEQ_LEN} x {FEAT_SIZE}")
    print(f"  Labels  : {MOTION_LETTER_LABELS}")
    print(f"  Hand    : {configured_hand_preference()}")
    print(f"  Pose    : {'single-hand' if single_hand_pose_enabled() else 'full-pose'}")
    print()

    (ROOT / "model").mkdir(exist_ok=True)
    X, y_int, groups, classes, class_stats, wrong_shape = load_sequences()
    missing = [label for label in MOTION_LETTER_LABELS if not class_stats[label]["used"]]
    if missing:
        report = {
            "status": "not_ready",
            "dataset": str(DATA_DIR),
            "required_labels": MOTION_LETTER_LABELS,
            "missing_or_underfilled_labels": missing,
            "class_stats": class_stats,
            "wrong_shape_files": wrong_shape,
        }
        with open(REPORT_PATH, "w", encoding="utf-8") as f:
            json.dump(report, f, indent=2)
        print("\nNot enough motion-letter data yet. Report written:")
        print(f"  {REPORT_PATH}")
        return

    labels_map = {label: idx for idx, label in enumerate(classes)}
    with open(LABELS_PATH, "w", encoding="utf-8") as f:
        json.dump(labels_map, f, indent=2)

    train_idx, val_idx = grouped_split(y_int, groups)
    y = to_categorical(y_int, len(classes))
    X_train, X_val = X[train_idx], X[val_idx]
    y_train, y_val = y[train_idx], y[val_idx]
    y_val_int = y_int[val_idx]

    model = build_model(len(classes))
    model.compile(
        optimizer=tf.keras.optimizers.Adam(learning_rate=0.001),
        loss="categorical_crossentropy",
        metrics=["accuracy"],
    )
    callbacks = [
        ModelCheckpoint(str(MODEL_PATH), monitor="val_accuracy", save_best_only=True, mode="max", verbose=1),
        EarlyStopping(monitor="val_accuracy", patience=15, restore_best_weights=True, mode="max"),
        ReduceLROnPlateau(monitor="val_loss", factor=0.5, patience=7, min_lr=1e-5),
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
    for idx, label in enumerate(classes):
        mask = y_val_int == idx
        total = int(mask.sum())
        correct = int((preds[mask] == idx).sum())
        per_class[label] = {
            "correct": correct,
            "total": total,
            "accuracy": (correct / total * 100.0) if total else 0.0,
        }

    input_shape, output_shape = export_tflite(best_model)
    report = {
        "status": "trained",
        "dataset": str(DATA_DIR),
        "model": str(MODEL_PATH),
        "labels": str(LABELS_PATH),
        "tflite": str(TFLITE_PATH),
        "input_shape": input_shape,
        "output_shape": output_shape,
        "classes": classes,
        "class_stats": class_stats,
        "wrong_shape_files": wrong_shape,
        "train_sequences": int(len(X_train)),
        "val_sequences": int(len(X_val)),
        "best_grouped_val_accuracy": max(history.history.get("val_accuracy", [0.0])) * 100.0,
        "per_class": per_class,
    }
    with open(REPORT_PATH, "w", encoding="utf-8") as f:
        json.dump(report, f, indent=2)

    print("\nSaved:")
    print(f"  Model : {MODEL_PATH}")
    print(f"  TFLite: {TFLITE_PATH}")
    print(f"  Labels: {LABELS_PATH}")
    print(f"  Report: {REPORT_PATH}")


if __name__ == "__main__":
    main()
