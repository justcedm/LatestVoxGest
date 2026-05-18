"""
VoxGest LSTM trainer.

Trains the target-word motion model from 30x162 holistic sequences.
Important fix: validation is split by source video/group, not by individual
augmented .npy files. This prevents augmented copies of the same sign video
from appearing in both train and validation.
"""

import json
import os
import re
import time
from pathlib import Path

import numpy as np
import tensorflow as tf
from tensorflow.keras import Input
from tensorflow.keras.callbacks import EarlyStopping, ModelCheckpoint, ReduceLROnPlateau
from tensorflow.keras.layers import LSTM, Dense, Dropout
from tensorflow.keras.models import Model
from tensorflow.keras.utils import to_categorical

from lstm_features import (
    FEAT_SIZE,
    SEQ_LEN,
    apply_sequence_feature_policy,
    configured_hand_preference,
    configured_mirror_input,
    hand_mapping_text,
    single_hand_pose_enabled,
)
from word_config import TARGET_WORDS, TRAINING_WORDS, WORD_PROFILE

os.environ["PROTOCOL_BUFFERS_PYTHON_IMPLEMENTATION"] = "python"
os.environ["TF_CPP_MIN_LOG_LEVEL"] = "3"

ROOT = Path(__file__).resolve().parents[1]
PREFERRED_DATA_DIR = Path(os.environ.get("VOXGEST_LSTM_DATASET", ROOT / "dataset_words_lstm"))
LEGACY_DATA_DIR = ROOT / "dataset_words"
ALLOW_LEGACY_FALLBACK = os.environ.get("VOXGEST_ALLOW_LEGACY_LSTM", "").strip() == "1"
INCLUDE_EXTRA_WORDS = os.environ.get("VOXGEST_INCLUDE_EXTRA_WORDS", "").strip() == "1"
MANUAL_TO_TRAIN = os.environ.get("VOXGEST_MANUAL_TO_TRAIN", "1").strip() != "0"
DATA_DIR = (
    PREFERRED_DATA_DIR
    if PREFERRED_DATA_DIR.exists() or not ALLOW_LEGACY_FALLBACK
    else LEGACY_DATA_DIR
)


def artifact_suffix():
    if WORD_PROFILE == "demo10":
        return "v1"
    return "".join(ch if ch.isalnum() or ch in {"_", "-"} else "_" for ch in WORD_PROFILE)


ARTIFACT_SUFFIX = artifact_suffix()
MODEL_PATH = ROOT / "model" / f"voxgest_lstm_{ARTIFACT_SUFFIX}.h5"
LABELS_PATH = ROOT / "model" / f"class_labels_lstm_{ARTIFACT_SUFFIX}.json"
TFLITE_PATH = ROOT / "model" / f"voxgest_lstm_{ARTIFACT_SUFFIX}.tflite"
REPORT_PATH = (
    ROOT / "model" / "lstm_training_report.json"
    if WORD_PROFILE == "demo10"
    else ROOT / "model" / f"lstm_training_report_{ARTIFACT_SUFFIX}.json"
)

EPOCHS = 150
BATCH_SIZE = 32
VAL_SPLIT = 0.20
MIN_SEQS = 80
MIN_GROUPS = 4
RANDOM_SEED = 42
LEGACY_AUGS_PER_SOURCE = 19
REQUIRE_HAND_METADATA = (
    os.environ.get(
        "VOXGEST_REQUIRE_HAND_METADATA",
        "1" if single_hand_pose_enabled() else "0",
    ).strip()
    != "0"
)
REQUIRE_ALL_TRAINING_WORDS = os.environ.get(
    "VOXGEST_REQUIRE_ALL_TRAINING_WORDS",
    "1",
).strip() != "0"


def iter_word_dirs(data_dir):
    word_dirs = {
        path.name.upper(): path
        for path in data_dir.iterdir()
        if path.is_dir()
    }

    seen = set()
    for word in TRAINING_WORDS:
        if word in word_dirs:
            seen.add(word)
            yield word, word_dirs[word]

    if INCLUDE_EXTRA_WORDS:
        for word in sorted(set(word_dirs) - seen):
            yield word, word_dirs[word]


def load_metadata(data_dir):
    for name in ("metadata_lstm_v2.json", "metadata_lstm_v1.json"):
        path = data_dir / name
        if path.exists():
            with open(path, "r", encoding="utf-8") as f:
                return json.load(f).get("samples", {})
    return {}


def infer_group_id(word, file_name, metadata):
    key = f"{word}/{file_name}"
    if key in metadata:
        return metadata[key].get("source_id", key)

    stem = Path(file_name).stem

    aug_match = re.match(r"(.+)_aug\d+$", stem)
    if aug_match:
        return f"{word}/{aug_match.group(1)}"

    legacy_match = re.match(r"seq_(\d+)$", stem)
    if legacy_match:
        seq_idx = int(legacy_match.group(1))
        return f"{word}/legacy_source_{seq_idx // LEGACY_AUGS_PER_SOURCE:04d}"

    manual_match = re.match(r"(manual_.+?)_seq\d+$", stem)
    if manual_match:
        return f"{word}/{manual_match.group(1)}"

    return f"{word}/{stem}"


def infer_mirrored_input(word, file_name, metadata):
    key = f"{word}/{file_name}"
    sample_meta = metadata.get(key, {})
    if "mirrored_input" in sample_meta:
        return bool(sample_meta["mirrored_input"])
    source_video = str(sample_meta.get("source_video", ""))
    return source_video == "manual_webcam" or file_name.startswith("manual_")


def is_manual_sample(word, file_name, metadata):
    key = f"{word}/{file_name}"
    sample_meta = metadata.get(key, {})
    return (
        file_name.startswith("manual_")
        or str(sample_meta.get("source_video", "")) == "manual_webcam"
    )


def has_hand_policy_metadata(word, file_name, metadata):
    key = f"{word}/{file_name}"
    sample_meta = metadata.get(key, {})
    return all(
        field in sample_meta
        for field in ("dominant_hand", "mirrored_input", "single_hand_pose")
    )


def infer_hand_preference(word, file_name, metadata):
    key = f"{word}/{file_name}"
    sample_meta = metadata.get(key, {})
    return sample_meta.get("dominant_hand") or configured_hand_preference()


def load_sequences():
    if not DATA_DIR.exists():
        raise FileNotFoundError(
            "LSTM dataset not found: "
            f"{DATA_DIR}\n"
            "Run this first so words are trained from the current 30x162 pipeline:\n"
            "  python scripts_ml/18_extract_lstm.py\n"
            "If you intentionally want the old fallback dataset, set:\n"
            "  $env:VOXGEST_ALLOW_LEGACY_LSTM='1'"
        )

    metadata = load_metadata(DATA_DIR)
    sequences = []
    labels = []
    groups = []
    valid_classes = []
    class_stats = {}
    skipped_wrong_shape = 0
    skipped_missing_hand_metadata = 0

    for word, word_dir in iter_word_dirs(DATA_DIR):
        records = []
        word_skipped_missing_hand_metadata = 0

        for file_path in sorted(word_dir.glob("*.npy")):
            try:
                arr = np.load(file_path, allow_pickle=False)
            except Exception:
                continue

            if arr.shape != (SEQ_LEN, FEAT_SIZE):
                skipped_wrong_shape += 1
                continue

            if (
                REQUIRE_HAND_METADATA
                and is_manual_sample(word, file_path.name, metadata)
                and not has_hand_policy_metadata(word, file_path.name, metadata)
            ):
                skipped_missing_hand_metadata += 1
                word_skipped_missing_hand_metadata += 1
                continue

            group_id = infer_group_id(word, file_path.name, metadata)
            mirrored_input = infer_mirrored_input(word, file_path.name, metadata)
            hand_preference = infer_hand_preference(word, file_path.name, metadata)
            arr = apply_sequence_feature_policy(
                arr,
                hand_preference=hand_preference,
                mirrored_input=mirrored_input,
            )
            records.append((arr.astype(np.float32), group_id))

        group_count = len({g for _, g in records})
        if len(records) >= MIN_SEQS and group_count >= MIN_GROUPS:
            class_idx = len(valid_classes)
            valid_classes.append(word)
            sequences.extend(arr for arr, _ in records)
            labels.extend([class_idx] * len(records))
            groups.extend(group_id for _, group_id in records)
            class_stats[word] = {
                "sequences": len(records),
                "groups": group_count,
                "used": True,
                "skipped_missing_hand_metadata": word_skipped_missing_hand_metadata,
            }
            print(
                f"  OK   {word:<15} sequences={len(records):>4} "
                f"groups={group_count:>3} skipped_meta={word_skipped_missing_hand_metadata:>4}"
            )
        else:
            class_stats[word] = {
                "sequences": len(records),
                "groups": group_count,
                "used": False,
                "skipped_missing_hand_metadata": word_skipped_missing_hand_metadata,
            }
            print(
                f"  SKIP {word:<15} sequences={len(records):>4} "
                f"groups={group_count:>3} skipped_meta={word_skipped_missing_hand_metadata:>4}"
            )

    if skipped_wrong_shape:
        print(f"\n  Ignored wrong-shape legacy files: {skipped_wrong_shape}")
    if skipped_missing_hand_metadata:
        print(f"  Ignored manual samples missing hand metadata: {skipped_missing_hand_metadata}")

    return (
        np.array(sequences, dtype=np.float32),
        np.array(labels, dtype=np.int32),
        np.array(groups, dtype=object),
        valid_classes,
        class_stats,
        skipped_wrong_shape,
        skipped_missing_hand_metadata,
    )


def grouped_class_split(labels, groups):
    rng = np.random.default_rng(RANDOM_SEED)
    train_idx = []
    val_idx = []

    for class_idx in sorted(set(labels.tolist())):
        class_indices = np.where(labels == class_idx)[0]
        class_groups = sorted(set(groups[class_indices].tolist()))
        rng.shuffle(class_groups)
        non_manual_groups = [
            group
            for group in class_groups
            if "/manual_" not in str(group).replace("\\", "/")
        ]

        n_val_groups = max(1, int(round(len(class_groups) * VAL_SPLIT)))
        if len(class_groups) > 1:
            n_val_groups = min(n_val_groups, len(class_groups) - 1)
        val_groups = set(class_groups[:n_val_groups])
        if MANUAL_TO_TRAIN:
            val_groups = {
                group
                for group in val_groups
                if "/manual_" not in str(group).replace("\\", "/")
            }
            if not val_groups and len(non_manual_groups) > 1:
                val_groups = {non_manual_groups[0]}

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
    return {
        int(cls): float(total / (n_classes * count))
        for cls, count in zip(classes, counts)
    }


def build_model(num_classes, recurrent_dropout=0.10, unroll=False):
    inp = Input(shape=(SEQ_LEN, FEAT_SIZE), name="sequence_input")
    x = LSTM(
        128,
        return_sequences=True,
        activation="tanh",
        recurrent_dropout=recurrent_dropout,
        unroll=unroll,
    )(inp)
    x = Dropout(0.35)(x)
    x = LSTM(
        64,
        return_sequences=False,
        activation="tanh",
        recurrent_dropout=recurrent_dropout,
        unroll=unroll,
    )(x)
    x = Dropout(0.35)(x)
    x = Dense(96, activation="relu")(x)
    x = Dropout(0.25)(x)
    out = Dense(num_classes, activation="softmax", name="output")(x)
    return Model(inp, out, name="VoxGest_LSTM_v1")


def copy_weights(src, dst):
    for src_layer, dst_layer in zip(src.layers, dst.layers):
        weights = src_layer.get_weights()
        if weights:
            dst_layer.set_weights(weights)


def export_tflite(model, num_classes):
    lite_model = build_model(num_classes, recurrent_dropout=0.0, unroll=True)
    copy_weights(model, lite_model)

    converter = tf.lite.TFLiteConverter.from_keras_model(lite_model)
    converter.optimizations = [tf.lite.Optimize.DEFAULT]
    converter.target_spec.supported_ops = [
        tf.lite.OpsSet.TFLITE_BUILTINS,
        tf.lite.OpsSet.SELECT_TF_OPS,
    ]
    converter._experimental_lower_tensor_list_ops = False

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
    test = np.zeros((1, SEQ_LEN, FEAT_SIZE), dtype=np.float32)
    interpreter.set_tensor(input_details["index"], test)
    interpreter.invoke()
    out = interpreter.get_tensor(output_details["index"])
    return input_details["shape"].tolist(), output_details["shape"].tolist(), int(out.argmax())


def main():
    print("=" * 72)
    print("  VoxGest LSTM Trainer | focused word profile | group-aware validation")
    print("=" * 72)
    print(f"  Dataset      : {DATA_DIR}")
    print(f"  Word profile : {WORD_PROFILE}")
    print(f"  Target words : {len(TARGET_WORDS)}")
    print(f"  Train words  : {len(TRAINING_WORDS)} including negatives")
    print(f"  Hand policy  : {configured_hand_preference()}")
    print(f"  Hand mapping : {hand_mapping_text(mirrored_input=configured_mirror_input())}")
    print(f"  Pose mask    : {'single-hand' if single_hand_pose_enabled() else 'full-pose'}")
    print(f"  Mirror env   : {configured_mirror_input()}")
    print(f"  Require meta : {REQUIRE_HAND_METADATA}")
    print(f"  Require all  : {REQUIRE_ALL_TRAINING_WORDS}")
    if INCLUDE_EXTRA_WORDS:
        print("  Extra words  : enabled by VOXGEST_INCLUDE_EXTRA_WORDS=1")
    if MANUAL_TO_TRAIN:
        print("  Manual data  : forced into training split")
    if DATA_DIR == LEGACY_DATA_DIR:
        print("  Mode         : LEGACY FALLBACK - not recommended for live word recognition")
    print()

    (ROOT / "model").mkdir(exist_ok=True)

    print("[1/6] Loading sequences...")
    (
        X,
        y_int,
        groups,
        classes,
        class_stats,
        skipped_wrong_shape,
        skipped_missing_hand_metadata,
    ) = load_sequences()
    num_classes = len(classes)

    if num_classes < 2:
        print("\nNeed at least two valid word classes.")
        return

    missing_required_classes = [word for word in TRAINING_WORDS if word not in classes]
    if missing_required_classes and REQUIRE_ALL_TRAINING_WORDS:
        print("\nMissing required training classes:")
        for word in missing_required_classes:
            stats = class_stats.get(word, {})
            print(
                f"  {word}: sequences={stats.get('sequences', 0)} "
                f"groups={stats.get('groups', 0)} "
                f"skipped_meta={stats.get('skipped_missing_hand_metadata', 0)}"
            )
        print("\nTraining stopped to avoid exporting a changed class contract.")
        print("Record clean samples or set VOXGEST_REQUIRE_ALL_TRAINING_WORDS='0' intentionally.")
        return

    labels_map = {name: idx for idx, name in enumerate(classes)}
    with open(LABELS_PATH, "w", encoding="utf-8") as f:
        json.dump(labels_map, f, indent=2)

    y = to_categorical(y_int, num_classes)
    print(f"\n  Classes   : {num_classes}")
    print(f"  Sequences : {len(X)}")
    print(f"  Labels    : {LABELS_PATH}")

    print("\n[2/6] Splitting by source group...")
    train_idx, val_idx = grouped_class_split(y_int, groups)
    X_train, X_val = X[train_idx], X[val_idx]
    y_train, y_val = y[train_idx], y[val_idx]
    y_train_int = y_int[train_idx]
    y_val_int = y_int[val_idx]
    print(f"  Train sequences : {len(X_train)}")
    print(f"  Val sequences   : {len(X_val)}")
    print(f"  Train groups    : {len(set(groups[train_idx].tolist()))}")
    print(f"  Val groups      : {len(set(groups[val_idx].tolist()))}")

    class_weights = balanced_class_weights(y_train_int)

    print("\n[3/6] Building model...")
    model = build_model(num_classes)
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
        class_weight=class_weights,
        callbacks=[
            EarlyStopping(
                monitor="val_accuracy",
                patience=22,
                restore_best_weights=True,
                verbose=1,
            ),
            ModelCheckpoint(
                str(MODEL_PATH),
                monitor="val_accuracy",
                save_best_only=True,
                verbose=1,
            ),
            ReduceLROnPlateau(
                monitor="val_loss",
                factor=0.5,
                patience=7,
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
    print(f"\n  {'Class':<15} {'Correct':>8} {'Total':>7} {'Acc':>8}  Top miss")
    print("  " + "-" * 58)
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
        flag = " CHECK" if acc < 80 else ""
        print(f"  {name:<15} {correct:>8} {total:>7} {acc:>7.1f}%  {top_miss}{flag}")

    print("\n[6/6] Exporting TFLite...")
    try:
        in_shape, out_shape, test_class = export_tflite(model, num_classes)
        kb = TFLITE_PATH.stat().st_size / 1024.0
        print(f"  Saved : {TFLITE_PATH} ({kb:.0f} KB)")
        print(f"  Input : {in_shape}")
        print(f"  Output: {out_shape}")
        print(f"  Smoke : class {test_class}")
    except Exception as exc:
        print(f"  TFLite export failed: {exc}")
        print("  You can retry with: python scripts_ml/export_tflite_fixed.py")

    report = {
        "dataset": str(DATA_DIR),
        "model": str(MODEL_PATH),
        "labels": str(LABELS_PATH),
        "tflite": str(TFLITE_PATH),
        "seq_len": SEQ_LEN,
        "feature_size": FEAT_SIZE,
        "word_profile": WORD_PROFILE,
        "target_words": TARGET_WORDS,
        "training_words": TRAINING_WORDS,
        "include_extra_words": INCLUDE_EXTRA_WORDS,
        "manual_to_train": MANUAL_TO_TRAIN,
        "classes": classes,
        "missing_target_words": [
            word for word in TARGET_WORDS if word not in class_stats
        ],
        "skipped_target_words": [
            word
            for word in TARGET_WORDS
            if word in class_stats and not class_stats[word]["used"]
        ],
        "ignored_wrong_shape_files": int(skipped_wrong_shape),
        "ignored_missing_hand_metadata_files": int(skipped_missing_hand_metadata),
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

    print("\n" + "=" * 72)
    print("  COMPLETE")
    print(f"  Model  : {MODEL_PATH}")
    print(f"  Labels : {LABELS_PATH}")
    print(f"  Report : {REPORT_PATH}")
    print(f"  Classes: {classes}")
    print("=" * 72)


if __name__ == "__main__":
    main()
