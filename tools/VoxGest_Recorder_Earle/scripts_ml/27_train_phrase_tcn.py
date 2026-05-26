"""
VoxGest phrase-intent TCN trainer.

Trains a separate endpoint-based phrase model from 60x162 complete gesture
segments. This model is intentionally separate from the fast 30-frame word TCN
so phrase outputs wait for segmentation before classification.
"""

import json
import os
import re
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

from lstm_features import (
    FEAT_SIZE,
    apply_sequence_feature_policy,
    configured_hand_preference,
    configured_mirror_input,
    single_hand_pose_enabled,
)
from phrase_config import (
    PHRASE_REQUIRED_LABELS,
    PHRASE_SEQ_LEN,
    PHRASE_TRAINING_LABELS,
)

ROOT = Path(__file__).resolve().parents[1]
DATA_DIR = Path(os.environ.get("VOXGEST_PHRASE_DATASET", ROOT / "dataset_phrase_intents"))
METADATA_PATH = DATA_DIR / "metadata_phrase_v1.json"

MODEL_PATH = ROOT / "model" / "voxgest_phrase_tcn_v1.h5"
LABELS_PATH = ROOT / "model" / "class_labels_phrase_tcn_v1.json"
TFLITE_PATH = ROOT / "model" / "voxgest_phrase_tcn_v1.tflite"
REPORT_PATH = ROOT / "model" / "phrase_tcn_training_report.json"

EPOCHS = int(os.environ.get("VOXGEST_PHRASE_TCN_EPOCHS", "120"))
BATCH_SIZE = int(os.environ.get("VOXGEST_PHRASE_TCN_BATCH_SIZE", "32"))
VAL_SPLIT = float(os.environ.get("VOXGEST_PHRASE_VAL_SPLIT", "0.25"))
MIN_SEQS = int(os.environ.get("VOXGEST_PHRASE_MIN_SEQS_PER_CLASS", "80"))
MIN_GROUPS = int(os.environ.get("VOXGEST_PHRASE_MIN_GROUPS_PER_CLASS", "3"))
RANDOM_SEED = int(os.environ.get("VOXGEST_RANDOM_SEED", "42"))
INCLUDE_EXTRA_LABELS = os.environ.get("VOXGEST_PHRASE_INCLUDE_EXTRA_LABELS", "").strip() == "1"
REQUIRE_HAND_METADATA = (
    os.environ.get(
        "VOXGEST_REQUIRE_HAND_METADATA",
        "1" if single_hand_pose_enabled() else "0",
    ).strip()
    != "0"
)
REQUIRE_REQUIRED_LABELS = os.environ.get("VOXGEST_REQUIRE_PHRASE_LABELS", "1").strip() != "0"


def load_metadata():
    if METADATA_PATH.exists():
        with open(METADATA_PATH, "r", encoding="utf-8") as f:
            return json.load(f).get("samples", {})
    return {}


def iter_label_dirs():
    if not DATA_DIR.exists():
        return
    label_dirs = {path.name.upper(): path for path in DATA_DIR.iterdir() if path.is_dir()}
    seen = set()
    for label in PHRASE_TRAINING_LABELS:
        if label in label_dirs:
            seen.add(label)
            yield label, label_dirs[label]

    if INCLUDE_EXTRA_LABELS:
        for label in sorted(set(label_dirs) - seen):
            yield label, label_dirs[label]


def infer_group_id(label, file_name, metadata):
    key = f"{label}/{file_name}"
    if key in metadata:
        return metadata[key].get("source_id", key)

    stem = Path(file_name).stem
    manual_match = re.match(r"(manual_.+?)_seq\d+$", stem)
    if manual_match:
        return f"{label}/{manual_match.group(1)}"
    return f"{label}/{stem}"


def has_hand_policy_metadata(label, file_name, metadata):
    sample_meta = metadata.get(f"{label}/{file_name}", {})
    return all(
        field in sample_meta
        for field in ("dominant_hand", "mirrored_input", "single_hand_pose")
    )


def infer_mirrored_input(label, file_name, metadata):
    sample_meta = metadata.get(f"{label}/{file_name}", {})
    if "mirrored_input" in sample_meta:
        return bool(sample_meta["mirrored_input"])
    return file_name.startswith("manual_")


def infer_hand_preference(label, file_name, metadata):
    sample_meta = metadata.get(f"{label}/{file_name}", {})
    return sample_meta.get("dominant_hand") or configured_hand_preference()


def load_sequences():
    if not DATA_DIR.exists():
        raise FileNotFoundError(
            "Phrase dataset not found. Record phrase data first:\n"
            "  python scripts_ml/26_record_phrase_intents.py ASK_NAME"
        )

    metadata = load_metadata()
    sequences = []
    labels = []
    groups = []
    classes = []
    class_stats = {}
    skipped_wrong_shape = 0
    skipped_missing_metadata = 0

    for label, label_dir in iter_label_dirs():
        records = []
        label_skipped_meta = 0

        for file_path in sorted(label_dir.glob("*.npy")):
            try:
                arr = np.load(file_path, allow_pickle=False)
            except Exception:
                continue

            if arr.shape != (PHRASE_SEQ_LEN, FEAT_SIZE):
                skipped_wrong_shape += 1
                continue

            if REQUIRE_HAND_METADATA and not has_hand_policy_metadata(label, file_path.name, metadata):
                skipped_missing_metadata += 1
                label_skipped_meta += 1
                continue

            arr = apply_sequence_feature_policy(
                arr,
                hand_preference=infer_hand_preference(label, file_path.name, metadata),
                mirrored_input=infer_mirrored_input(label, file_path.name, metadata),
            )
            records.append((arr.astype(np.float32), infer_group_id(label, file_path.name, metadata)))

        group_count = len({group_id for _, group_id in records})
        used = len(records) >= MIN_SEQS and group_count >= MIN_GROUPS
        need_sequences = max(0, MIN_SEQS - len(records))
        need_groups = max(0, MIN_GROUPS - group_count)
        class_stats[label] = {
            "sequences": len(records),
            "groups": group_count,
            "used": used,
            "skipped_missing_hand_metadata": label_skipped_meta,
            "need_sequences": need_sequences,
            "need_groups": need_groups,
        }
        status = "OK  " if used else "SKIP"
        print(
            f"  {status} {label:<18} sequences={len(records):>4} "
            f"groups={group_count:>3} skipped_meta={label_skipped_meta:>4} "
            f"need_seq={need_sequences:>3} need_groups={need_groups:>2}"
        )

        if used:
            class_idx = len(classes)
            classes.append(label)
            sequences.extend(arr for arr, _ in records)
            labels.extend([class_idx] * len(records))
            groups.extend(group_id for _, group_id in records)

    return (
        np.array(sequences, dtype=np.float32),
        np.array(labels, dtype=np.int32),
        np.array(groups, dtype=object),
        classes,
        class_stats,
        skipped_wrong_shape,
        skipped_missing_metadata,
    )


def grouped_class_split(labels, groups):
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


def balanced_class_weights(labels):
    classes, counts = np.unique(labels, return_counts=True)
    total = float(len(labels))
    n_classes = float(len(classes))
    return {int(cls): float(total / (n_classes * count)) for cls, count in zip(classes, counts)}


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


def build_model(num_classes):
    filters = int(os.environ.get("VOXGEST_PHRASE_TCN_FILTERS", "96"))
    kernel_size = int(os.environ.get("VOXGEST_PHRASE_TCN_KERNEL", "3"))
    dropout = float(os.environ.get("VOXGEST_PHRASE_TCN_DROPOUT", "0.25"))

    inp = Input(shape=(PHRASE_SEQ_LEN, FEAT_SIZE), name="phrase_sequence_input")
    x = BatchNormalization(name="input_norm")(inp)
    x = Conv1D(filters, 1, padding="same", activation="relu")(x)
    for dilation in (1, 2, 4, 8, 16):
        x = residual_tcn_block(x, filters, kernel_size, dilation, dropout)

    avg_pool = GlobalAveragePooling1D()(x)
    max_pool = GlobalMaxPooling1D()(x)
    x = Concatenate()([avg_pool, max_pool])
    x = Dense(128, activation="relu")(x)
    x = Dropout(0.40)(x)
    x = Dense(64, activation="relu")(x)
    x = Dropout(0.25)(x)
    out = Dense(num_classes, activation="softmax", name="output")(x)
    return Model(inp, out, name="VoxGest_Phrase_TCN_v1")


def export_tflite(model):
    converter = tf.lite.TFLiteConverter.from_keras_model(model)
    converter.optimizations = [tf.lite.Optimize.DEFAULT]
    tflite = converter.convert()
    with open(TFLITE_PATH, "wb") as f:
        f.write(tflite)

    try:
        from ai_edge_litert.interpreter import Interpreter as Interpreter
    except ImportError:
        Interpreter = tf.lite.Interpreter

    interpreter = Interpreter(model_path=str(TFLITE_PATH))
    interpreter.allocate_tensors()
    input_details = interpreter.get_input_details()[0]
    output_details = interpreter.get_output_details()[0]
    test = np.zeros((1, PHRASE_SEQ_LEN, FEAT_SIZE), dtype=np.float32)
    interpreter.set_tensor(input_details["index"], test)
    interpreter.invoke()
    out = interpreter.get_tensor(output_details["index"])
    return input_details["shape"].tolist(), output_details["shape"].tolist(), int(out.argmax())


def main():
    print("=" * 76)
    print("  VoxGest Phrase TCN Trainer | endpoint-based sentence intents")
    print("=" * 76)
    print(f"  Dataset      : {DATA_DIR}")
    print(f"  Shape        : {PHRASE_SEQ_LEN} x {FEAT_SIZE}")
    print(f"  Train labels : {PHRASE_TRAINING_LABELS}")
    print(f"  Required     : {PHRASE_REQUIRED_LABELS}")
    print(f"  Hand policy  : {configured_hand_preference()}")
    print(f"  Pose mask    : {'single-hand' if single_hand_pose_enabled() else 'full-pose'}")
    print(f"  Mirror env   : {configured_mirror_input()}")
    print(f"  Require meta : {REQUIRE_HAND_METADATA}")
    print(f"  Epochs/batch : {EPOCHS}/{BATCH_SIZE}")
    print()

    (ROOT / "model").mkdir(exist_ok=True)

    print("[1/6] Loading phrase sequences...")
    (
        X,
        y_int,
        groups,
        classes,
        class_stats,
        skipped_wrong_shape,
        skipped_missing_metadata,
    ) = load_sequences()

    if len(classes) < 2:
        print("\nNeed at least two valid phrase classes, usually ASK_NAME and NOTHING.")
        print(
            "\nMeaning of the readiness columns:\n"
            f"  sequences: saved phrase samples; minimum is {MIN_SEQS} per class.\n"
            f"  groups   : separate recording sessions; minimum is {MIN_GROUPS} per class.\n"
            "  60 x 162 is the shape of one saved sample, not 60 separate sessions.\n"
        )
        print(
            "Fatigue-friendly option:\n"
            "  $env:VOXGEST_PHRASE_SEQUENCES_PER_LABEL='30'\n"
            "  Record two more short sessions each for ASK_NAME and NOTHING,\n"
            "  then train again. Existing 60 + 30 + 30 = 120 samples in 3 groups.\n"
        )
        return

    missing_required = [label for label in PHRASE_REQUIRED_LABELS if label not in classes]
    if missing_required and REQUIRE_REQUIRED_LABELS:
        print("\nMissing required phrase classes:")
        for label in missing_required:
            stats = class_stats.get(label, {})
            print(
                f"  {label}: sequences={stats.get('sequences', 0)} "
                f"groups={stats.get('groups', 0)} "
                f"skipped_meta={stats.get('skipped_missing_hand_metadata', 0)}"
            )
        print("\nTraining stopped to avoid exporting an incomplete phrase contract.")
        return

    labels_map = {name: idx for idx, name in enumerate(classes)}
    with open(LABELS_PATH, "w", encoding="utf-8") as f:
        json.dump(labels_map, f, indent=2)

    y = to_categorical(y_int, len(classes))
    print(f"\n  Classes   : {len(classes)}")
    print(f"  Sequences : {len(X)}")
    print(f"  Labels    : {LABELS_PATH}")

    print("\n[2/6] Splitting by phrase recording group...")
    train_idx, val_idx = grouped_class_split(y_int, groups)
    if len(train_idx) == 0 or len(val_idx) == 0:
        print("Need both train and validation groups. Record more separate sessions.")
        return

    X_train, X_val = X[train_idx], X[val_idx]
    y_train, y_val = y[train_idx], y[val_idx]
    y_train_int = y_int[train_idx]
    y_val_int = y_int[val_idx]
    print(f"  Train sequences : {len(X_train)}")
    print(f"  Val sequences   : {len(X_val)}")
    print(f"  Train groups    : {len(set(groups[train_idx].tolist()))}")
    print(f"  Val groups      : {len(set(groups[val_idx].tolist()))}")

    print("\n[3/6] Building phrase TCN...")
    model = build_model(len(classes))
    model.compile(
        optimizer=tf.keras.optimizers.Adam(learning_rate=0.001),
        loss="categorical_crossentropy",
        metrics=["accuracy"],
    )
    model.summary()

    print("\n[4/6] Training...")
    started = time.time()
    history = model.fit(
        X_train,
        y_train,
        validation_data=(X_val, y_val),
        epochs=EPOCHS,
        batch_size=BATCH_SIZE,
        class_weight=balanced_class_weights(y_train_int),
        callbacks=[
            EarlyStopping(
                monitor="val_accuracy",
                patience=18,
                restore_best_weights=True,
                verbose=1,
            ),
            ModelCheckpoint(str(MODEL_PATH), monitor="val_accuracy", save_best_only=True, verbose=1),
            ReduceLROnPlateau(
                monitor="val_loss",
                factor=0.5,
                patience=6,
                min_lr=1e-6,
                verbose=1,
            ),
        ],
        verbose=1,
    )
    best_val = float(max(history.history.get("val_accuracy", [0.0])) * 100.0)
    print(f"\n  Best grouped val accuracy : {best_val:.2f}%")
    print(f"  Training time             : {(time.time() - started) / 60:.1f} min")

    print("\n[5/6] Per-class validation accuracy...")
    pred_probs = model.predict(X_val, verbose=0)
    preds = np.argmax(pred_probs, axis=1)
    per_class = {}
    confusion = {}
    print(f"\n  {'Class':<18} {'Correct':>8} {'Total':>7} {'Acc':>8}  Top miss")
    print("  " + "-" * 62)
    for idx, name in enumerate(classes):
        mask = y_val_int == idx
        total = int(mask.sum())
        if total == 0:
            continue
        correct = int((preds[mask] == idx).sum())
        acc = correct / total * 100.0
        wrong = preds[mask][preds[mask] != idx]
        top_miss = "-"
        if len(wrong):
            values, counts = np.unique(wrong, return_counts=True)
            top_miss = classes[int(values[int(np.argmax(counts))])]
        per_class[name] = {"correct": correct, "total": total, "accuracy": acc}
        confusion[name] = top_miss
        flag = " CHECK" if acc < 85 else ""
        print(f"  {name:<18} {correct:>8} {total:>7} {acc:>7.1f}%  {top_miss}{flag}")

    print("\n[6/6] Exporting phrase TFLite...")
    try:
        in_shape, out_shape, test_class = export_tflite(model)
        kb = TFLITE_PATH.stat().st_size / 1024.0
        print(f"  Saved : {TFLITE_PATH} ({kb:.0f} KB)")
        print(f"  Input : {in_shape}")
        print(f"  Output: {out_shape}")
        print(f"  Smoke : class {test_class}")
    except Exception as exc:
        print(f"  TFLite export failed: {exc}")

    report = {
        "dataset": str(DATA_DIR),
        "model": str(MODEL_PATH),
        "labels": str(LABELS_PATH),
        "tflite": str(TFLITE_PATH),
        "seq_len": PHRASE_SEQ_LEN,
        "feature_size": FEAT_SIZE,
        "training_labels": PHRASE_TRAINING_LABELS,
        "required_labels": PHRASE_REQUIRED_LABELS,
        "classes": classes,
        "missing_required_labels": missing_required,
        "ignored_wrong_shape_files": int(skipped_wrong_shape),
        "ignored_missing_hand_metadata_files": int(skipped_missing_metadata),
        "class_stats": class_stats,
        "train_sequences": int(len(X_train)),
        "val_sequences": int(len(X_val)),
        "train_groups": int(len(set(groups[train_idx].tolist()))),
        "val_groups": int(len(set(groups[val_idx].tolist()))),
        "best_grouped_val_accuracy": best_val,
        "per_class": per_class,
        "top_miss": confusion,
    }
    with open(REPORT_PATH, "w", encoding="utf-8") as f:
        json.dump(report, f, indent=2)

    print("\n" + "=" * 76)
    print("  COMPLETE")
    print(f"  Model  : {MODEL_PATH}")
    print(f"  Labels : {LABELS_PATH}")
    print(f"  Report : {REPORT_PATH}")
    print(f"  Classes: {classes}")
    print("=" * 76)


if __name__ == "__main__":
    main()
