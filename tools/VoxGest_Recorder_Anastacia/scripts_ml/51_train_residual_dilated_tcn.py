"""
VoxGest Residual Dilated TCN trainer.

This experimental trainer keeps the existing landmark feature contract and
dataset layout, but writes separate rd_tcn artifacts so the basic TCN pipeline
and demo10 baseline remain untouched.
"""

import json
import os
import re
import sys
import time
from pathlib import Path

os.environ["PROTOCOL_BUFFERS_PYTHON_IMPLEMENTATION"] = "python"
os.environ["TF_CPP_MIN_LOG_LEVEL"] = "3"

_raw_profile = os.environ.get("VOXGEST_WORD_PROFILE", "demo10").strip().lower()
if _raw_profile.startswith("fullsign225_") and not os.environ.get("VOXGEST_FEATURE_PROFILE"):
    os.environ["VOXGEST_FEATURE_PROFILE"] = "fullsign225"

import numpy as np
import tensorflow as tf
from tensorflow.keras import Input
from tensorflow.keras.callbacks import EarlyStopping, ModelCheckpoint, ReduceLROnPlateau
from tensorflow.keras.layers import (
    Activation,
    Add,
    BatchNormalization,
    Conv1D,
    Dense,
    Dropout,
    GlobalAveragePooling1D,
)
from tensorflow.keras.models import Model
from tensorflow.keras.utils import to_categorical

from lstm_features import (
    FEAT_SIZE,
    SEQ_LEN,
    apply_sequence_feature_policy,
    configured_feature_profile,
    configured_hand_preference,
    configured_mirror_input,
    default_dataset_dir_name,
    hand_mapping_text,
    single_hand_pose_enabled,
)
from word_config import TARGET_WORDS, TRAINING_WORDS, WORD_PROFILE


ROOT = Path(__file__).resolve().parents[1]
FEATURE_PROFILE = configured_feature_profile()
MODEL_KIND = os.environ.get("VOXGEST_DYNAMIC_MODEL", "rd_tcn").strip().lower()
SUPPORTED_MODEL_KINDS = {"rd_tcn", "residual_dilated_tcn"}


def default_dataset_dir():
    profile_defaults = {
        "fullsign225_manual5_team": ROOT / "external_datasets" / "fullsign225_manual5_team_features",
    }
    return profile_defaults.get(WORD_PROFILE, ROOT / default_dataset_dir_name())


DATA_DIR = Path(os.environ.get("VOXGEST_LSTM_DATASET", default_dataset_dir()))
INCLUDE_EXTRA_WORDS = os.environ.get("VOXGEST_INCLUDE_EXTRA_WORDS", "").strip() == "1"
MANUAL_TO_TRAIN = os.environ.get("VOXGEST_MANUAL_TO_TRAIN", "1").strip() != "0"


def artifact_suffix():
    safe_word = "".join(ch if ch.isalnum() or ch in {"_", "-"} else "_" for ch in WORD_PROFILE)
    if FEATURE_PROFILE != "onehand162":
        if safe_word.startswith("fullsign225_") or safe_word.endswith(f"_{FEATURE_PROFILE}"):
            return safe_word
        return f"{safe_word}_{FEATURE_PROFILE}"
    if WORD_PROFILE == "demo10":
        return "v1"
    return safe_word


ARTIFACT_SUFFIX = artifact_suffix()
MODEL_PATH = ROOT / "model" / f"voxgest_rd_tcn_{ARTIFACT_SUFFIX}.h5"
LABELS_PATH = ROOT / "model" / f"class_labels_rd_tcn_{ARTIFACT_SUFFIX}.json"
TFLITE_PATH = ROOT / "model" / f"voxgest_rd_tcn_{ARTIFACT_SUFFIX}.tflite"
REPORT_PATH = ROOT / "model" / f"rd_tcn_training_report_{ARTIFACT_SUFFIX}.json"
RUNTIME_MANIFEST_PATH = ROOT / "model" / f"runtime_manifest_rd_tcn_{ARTIFACT_SUFFIX}.json"
COMPARISON_DOC = ROOT / "docs" / "FULLSIGN225_MANUAL5_TEAM_TCN_VS_RDTCN.md"

EPOCHS = int(os.environ.get("VOXGEST_RD_TCN_EPOCHS", os.environ.get("VOXGEST_TCN_EPOCHS", "140")))
BATCH_SIZE = int(os.environ.get("VOXGEST_RD_TCN_BATCH_SIZE", os.environ.get("VOXGEST_TCN_BATCH_SIZE", "32")))
VAL_SPLIT = float(os.environ.get("VOXGEST_VAL_SPLIT", "0.20"))
RANDOM_SEED = int(os.environ.get("VOXGEST_RANDOM_SEED", "42"))
TEAM_SEED_PROFILES = {"fullsign225_manual5_team"}
MIN_SEQS_DEFAULT = "20" if WORD_PROFILE in TEAM_SEED_PROFILES else "80"
MIN_GROUPS_DEFAULT = "1" if WORD_PROFILE in TEAM_SEED_PROFILES else "4"
MIN_SEQS = int(os.environ.get("VOXGEST_MIN_SEQS_PER_CLASS", MIN_SEQS_DEFAULT))
MIN_GROUPS = int(os.environ.get("VOXGEST_MIN_GROUPS_PER_CLASS", MIN_GROUPS_DEFAULT))
RANDOM_VAL_FALLBACK = (
    os.environ.get(
        "VOXGEST_RANDOM_VAL_FALLBACK",
        "1" if WORD_PROFILE in TEAM_SEED_PROFILES else "0",
    ).strip()
    != "0"
)
LEGACY_AUGS_PER_SOURCE = 19
REQUIRE_HAND_METADATA = (
    os.environ.get(
        "VOXGEST_REQUIRE_HAND_METADATA",
        "1" if single_hand_pose_enabled() else "0",
    ).strip()
    != "0"
)
REQUIRE_ALL_TRAINING_WORDS = os.environ.get("VOXGEST_REQUIRE_ALL_TRAINING_WORDS", "1").strip() != "0"


def iter_word_dirs(data_dir):
    word_dirs = {path.name.upper(): path for path in data_dir.iterdir() if path.is_dir()}
    seen = set()
    for word in TRAINING_WORDS:
        if word in word_dirs:
            seen.add(word)
            yield word, word_dirs[word]
    if INCLUDE_EXTRA_WORDS:
        for word in sorted(set(word_dirs) - seen):
            yield word, word_dirs[word]


def load_metadata(data_dir):
    for name in ("metadata_lstm_v2.json", "metadata_lstm_v1.json", "metadata_fullsign225.json"):
        path = data_dir / name
        if path.exists():
            with open(path, "r", encoding="utf-8") as f:
                raw = json.load(f)
            return raw.get("samples", raw if isinstance(raw, dict) else {})
    return {}


def infer_group_id(word, file_name, metadata):
    key = f"{word}/{file_name}"
    if key in metadata:
        sample_meta = metadata[key]
        return sample_meta.get("source_id") or sample_meta.get("signer_id") or key

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

    signer_match = re.match(r"([A-Za-z0-9_-]+)_" + re.escape(word) + r"_\d+$", stem, flags=re.IGNORECASE)
    if signer_match:
        return f"{word}/{signer_match.group(1).upper()}"

    return f"{word}/{stem}"


def infer_mirrored_input(word, file_name, metadata):
    sample_meta = metadata.get(f"{word}/{file_name}", {})
    if "mirrored_input" in sample_meta:
        return bool(sample_meta["mirrored_input"])
    source_video = str(sample_meta.get("source_video", ""))
    return source_video in {"manual_webcam", "teammate_webcam_fullsign225"} or file_name.startswith("manual_")


def is_manual_sample(word, file_name, metadata):
    sample_meta = metadata.get(f"{word}/{file_name}", {})
    return file_name.startswith("manual_") or str(sample_meta.get("source_video", "")).startswith("manual")


def has_hand_policy_metadata(word, file_name, metadata):
    sample_meta = metadata.get(f"{word}/{file_name}", {})
    return all(field in sample_meta for field in ("dominant_hand", "mirrored_input", "single_hand_pose"))


def infer_hand_preference(word, file_name, metadata):
    sample_meta = metadata.get(f"{word}/{file_name}", {})
    return sample_meta.get("dominant_hand") or configured_hand_preference()


def load_sequences():
    if not DATA_DIR.exists():
        raise FileNotFoundError(
            f"Dataset not found: {DATA_DIR}\n"
            "Create/merge the team recorder output there before training."
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
        word_wrong_shape = 0

        for file_path in sorted(word_dir.glob("*.npy")):
            try:
                arr = np.load(file_path, allow_pickle=False)
            except Exception:
                continue

            if arr.shape != (SEQ_LEN, FEAT_SIZE):
                skipped_wrong_shape += 1
                word_wrong_shape += 1
                continue

            if (
                REQUIRE_HAND_METADATA
                and is_manual_sample(word, file_path.name, metadata)
                and not has_hand_policy_metadata(word, file_path.name, metadata)
            ):
                skipped_missing_hand_metadata += 1
                word_skipped_missing_hand_metadata += 1
                continue

            arr = apply_sequence_feature_policy(
                arr,
                hand_preference=infer_hand_preference(word, file_path.name, metadata),
                mirrored_input=infer_mirrored_input(word, file_path.name, metadata),
            )
            records.append((arr.astype(np.float32), infer_group_id(word, file_path.name, metadata)))

        group_count = len({group_id for _, group_id in records})
        used = len(records) >= MIN_SEQS and group_count >= MIN_GROUPS
        class_stats[word] = {
            "sequences": len(records),
            "groups": group_count,
            "used": used,
            "wrong_shape": word_wrong_shape,
            "skipped_missing_hand_metadata": word_skipped_missing_hand_metadata,
        }
        status = "OK  " if used else "SKIP"
        print(
            f"  {status} {word:<15} sequences={len(records):>4} "
            f"groups={group_count:>3} wrong_shape={word_wrong_shape:>3} "
            f"skipped_meta={word_skipped_missing_hand_metadata:>3}"
        )

        if used:
            class_idx = len(valid_classes)
            valid_classes.append(word)
            sequences.extend(arr for arr, _ in records)
            labels.extend([class_idx] * len(records))
            groups.extend(group_id for _, group_id in records)

    return (
        np.array(sequences, dtype=np.float32),
        np.array(labels, dtype=np.int32),
        np.array(groups, dtype=object),
        valid_classes,
        class_stats,
        skipped_wrong_shape,
        skipped_missing_hand_metadata,
    )


def random_class_split_for_indices(indices, rng):
    indices = np.array(indices, dtype=np.int32)
    rng.shuffle(indices)
    n_val = max(1, int(round(len(indices) * VAL_SPLIT)))
    if len(indices) > 1:
        n_val = min(n_val, len(indices) - 1)
    return indices[n_val:].tolist(), indices[:n_val].tolist()


def grouped_class_split(labels, groups):
    rng = np.random.default_rng(RANDOM_SEED)
    train_idx = []
    val_idx = []
    random_fallback_classes = []

    for class_idx in sorted(set(labels.tolist())):
        class_indices = np.where(labels == class_idx)[0]
        class_groups = sorted(set(groups[class_indices].tolist()))
        rng.shuffle(class_groups)

        if RANDOM_VAL_FALLBACK and len(class_groups) < 2:
            train_part, val_part = random_class_split_for_indices(class_indices, rng)
            train_idx.extend(train_part)
            val_idx.extend(val_part)
            random_fallback_classes.append(int(class_idx))
            continue

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
            if not val_groups and len(class_groups) > 1:
                val_groups = {class_groups[0]}

        for idx in class_indices:
            if groups[idx] in val_groups:
                val_idx.append(int(idx))
            else:
                train_idx.append(int(idx))

    rng.shuffle(train_idx)
    rng.shuffle(val_idx)
    if random_fallback_classes:
        print(f"  Random validation fallback used for class indices: {random_fallback_classes}")
    return np.array(train_idx, dtype=np.int32), np.array(val_idx, dtype=np.int32)


def balanced_class_weights(labels):
    classes, counts = np.unique(labels, return_counts=True)
    total = float(len(labels))
    n_classes = float(len(classes))
    return {int(cls): float(total / (n_classes * count)) for cls, count in zip(classes, counts)}


def residual_dilated_tcn_block(x, filters, kernel_size, dilation_rate, dropout):
    shortcut = x

    x = Conv1D(
        filters,
        kernel_size,
        padding="same",
        dilation_rate=dilation_rate,
        use_bias=False,
    )(x)
    x = BatchNormalization()(x)
    x = Activation("relu")(x)
    x = Dropout(dropout)(x)

    x = Conv1D(
        filters,
        kernel_size,
        padding="same",
        dilation_rate=dilation_rate,
        use_bias=False,
    )(x)
    x = BatchNormalization()(x)

    if shortcut.shape[-1] != filters:
        shortcut = Conv1D(filters, 1, padding="same", use_bias=False)(shortcut)
        shortcut = BatchNormalization()(shortcut)

    x = Add()([shortcut, x])
    return Activation("relu")(x)


def build_model(num_classes):
    filters = int(os.environ.get("VOXGEST_RD_TCN_FILTERS", "64"))
    kernel_size = int(os.environ.get("VOXGEST_RD_TCN_KERNEL", "3"))
    dropout = float(os.environ.get("VOXGEST_RD_TCN_DROPOUT", "0.20"))
    dense_units = int(os.environ.get("VOXGEST_RD_TCN_DENSE", "96"))

    inp = Input(shape=(SEQ_LEN, FEAT_SIZE), name="sequence_input")
    x = BatchNormalization(name="input_norm")(inp)

    for dilation_rate in (1, 2, 4, 8):
        x = residual_dilated_tcn_block(x, filters, kernel_size, dilation_rate, dropout)

    x = GlobalAveragePooling1D(name="temporal_average")(x)
    x = Dense(dense_units, activation="relu", name="dense_context")(x)
    x = Dropout(0.30, name="dense_dropout")(x)
    out = Dense(num_classes, activation="softmax", name="output")(x)
    return Model(inp, out, name="VoxGest_Residual_Dilated_TCN")


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
    test = np.zeros((1, SEQ_LEN, FEAT_SIZE), dtype=np.float32)
    interpreter.set_tensor(input_details["index"], test)
    interpreter.invoke()
    out = interpreter.get_tensor(output_details["index"])
    return input_details["shape"].tolist(), output_details["shape"].tolist(), int(out.argmax())


def write_runtime_manifest(classes, report):
    manifest = {
        "version": 1,
        "profile": WORD_PROFILE,
        "status": "experimental_until_live_tested",
        "default_model": False,
        "replaces_demo10": False,
        "replaces_onehand162": False,
        "phrase_enabled_default": False,
        "feature_profile": FEATURE_PROFILE,
        "model_kind": "rd_tcn",
        "models": {
            "rd_tcn": {
                "file": str(TFLITE_PATH.relative_to(ROOT)).replace("\\", "/"),
                "keras_file": str(MODEL_PATH.relative_to(ROOT)).replace("\\", "/"),
                "labels_file": str(LABELS_PATH.relative_to(ROOT)).replace("\\", "/"),
                "training_report": str(REPORT_PATH.relative_to(ROOT)).replace("\\", "/"),
                "input_shape": [1, SEQ_LEN, FEAT_SIZE],
                "output_shape": [1, len(classes)],
                "sequence_length": SEQ_LEN,
                "feature_size": FEAT_SIZE,
                "labels": classes,
                "negative_label": "NOTHING",
                "nothing_policy": "ignore_no_output",
            }
        },
        "dataset": {
            "features": str(DATA_DIR),
            "class_stats": report.get("class_stats", {}),
        },
        "validation": {
            "best_grouped_val_accuracy": report.get("best_grouped_val_accuracy"),
            "per_class": report.get("per_class", {}),
            "top_confusions": report.get("top_confusions", {}),
        },
        "runtime_policy": {
            "input_policy": "accepted_predictions_only",
            "raw_prediction_policy": "never_update_sentence_output_directly",
            "nothing_policy": "NOTHING remains no-output",
            "word_profile_env": f"VOXGEST_WORD_PROFILE={WORD_PROFILE}",
            "feature_profile_env": f"VOXGEST_FEATURE_PROFILE={FEATURE_PROFILE}",
            "dynamic_model_env": "VOXGEST_DYNAMIC_MODEL=rd_tcn",
        },
    }
    with open(RUNTIME_MANIFEST_PATH, "w", encoding="utf-8") as f:
        json.dump(manifest, f, indent=2)
    return RUNTIME_MANIFEST_PATH


def read_basic_tcn_report():
    basic_path = ROOT / "model" / f"tcn_training_report_{ARTIFACT_SUFFIX}.json"
    if not basic_path.exists():
        return None, basic_path
    try:
        with open(basic_path, "r", encoding="utf-8") as f:
            return json.load(f), basic_path
    except Exception:
        return None, basic_path


def write_comparison_doc(rd_report):
    COMPARISON_DOC.parent.mkdir(exist_ok=True)
    basic_report, basic_path = read_basic_tcn_report()
    rd_acc = rd_report.get("best_grouped_val_accuracy")
    basic_acc = basic_report.get("best_grouped_val_accuracy") if basic_report else None

    if basic_acc is None:
        comparison = "Basic TCN report is not available yet for this profile."
    elif rd_acc is None:
        comparison = "Residual Dilated TCN training did not produce a validation score."
    elif rd_acc > basic_acc:
        comparison = "Residual Dilated TCN is better on grouped validation for this run."
    elif rd_acc < basic_acc:
        comparison = "Basic TCN is better on grouped validation for this run."
    else:
        comparison = "Both models tied on grouped validation for this run."

    lines = [
        "# FullSign225 Manual5 Team: TCN vs Residual Dilated TCN",
        "",
        "This comparison is profile-safe and experimental. Live webcam testing still decides final usability.",
        "",
        "## Profile",
        "",
        f"- Word profile: `{WORD_PROFILE}`",
        f"- Feature profile: `{FEATURE_PROFILE}`",
        f"- Input shape: `[1, {SEQ_LEN}, {FEAT_SIZE}]`",
        f"- Labels: {', '.join(rd_report.get('classes', []))}",
        "",
        "## Basic TCN",
        "",
    ]
    if basic_report:
        lines.extend(
            [
                f"- Report: `{basic_path.relative_to(ROOT).as_posix()}`",
                f"- Grouped validation accuracy: {basic_acc:.2f}%",
            ]
        )
    else:
        lines.append(f"- Report not found: `{basic_path.relative_to(ROOT).as_posix()}`")

    lines.extend(
        [
            "",
            "## Residual Dilated TCN",
            "",
            f"- Report: `{REPORT_PATH.relative_to(ROOT).as_posix()}`",
            f"- Grouped validation accuracy: {rd_acc:.2f}%" if rd_acc is not None else "- Grouped validation accuracy: unavailable",
            f"- TFLite: `{TFLITE_PATH.relative_to(ROOT).as_posix()}`",
            "",
            "## Recommendation",
            "",
            comparison,
            "",
            "Do not promote either model from validation alone. Run the live webcam protocol and keep demo10 as the safe baseline until live trials pass.",
            "",
        ]
    )
    COMPARISON_DOC.write_text("\n".join(lines), encoding="utf-8")
    return COMPARISON_DOC


def main():
    if MODEL_KIND not in SUPPORTED_MODEL_KINDS:
        print(
            "Warning: VOXGEST_DYNAMIC_MODEL is not rd_tcn/residual_dilated_tcn; "
            "training still writes rd_tcn artifacts."
        )

    print("=" * 72)
    print("  VoxGest Residual Dilated TCN Trainer")
    print("=" * 72)
    print(f"  Dataset      : {DATA_DIR}")
    print(f"  Word profile : {WORD_PROFILE}")
    print(f"  Feature prof : {FEATURE_PROFILE} ({FEAT_SIZE} floats/frame)")
    print(f"  Model kind   : residual_dilated_tcn")
    print(f"  Target words : {len(TARGET_WORDS)}")
    print(f"  Train words  : {len(TRAINING_WORDS)} including negatives")
    print(f"  Hand policy  : {configured_hand_preference()}")
    print(f"  Hand mapping : {hand_mapping_text(mirrored_input=configured_mirror_input())}")
    print(f"  Pose mask    : {'single-hand' if single_hand_pose_enabled() else 'full-pose'}")
    print(f"  Min seq/group: {MIN_SEQS}/{MIN_GROUPS}")
    print(f"  Random val   : {RANDOM_VAL_FALLBACK}")
    print(f"  Epochs/batch : {EPOCHS}/{BATCH_SIZE}")
    print()

    (ROOT / "model").mkdir(exist_ok=True)

    print("[1/6] Loading sequences...")
    try:
        (
            X,
            y_int,
            groups,
            classes,
            class_stats,
            skipped_wrong_shape,
            skipped_missing_hand_metadata,
        ) = load_sequences()
    except FileNotFoundError as exc:
        print(str(exc))
        return 2

    if len(classes) < 2:
        print("\nNeed at least two valid classes.")
        return 2

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
        return 2

    labels_map = {name: idx for idx, name in enumerate(classes)}
    with open(LABELS_PATH, "w", encoding="utf-8") as f:
        json.dump(labels_map, f, indent=2)

    y = to_categorical(y_int, len(classes))
    print(f"\n  Classes   : {len(classes)}")
    print(f"  Sequences : {len(X)}")
    print(f"  Labels    : {LABELS_PATH}")

    print("\n[2/6] Splitting by source group...")
    train_idx, val_idx = grouped_class_split(y_int, groups)
    if len(val_idx) == 0 or len(train_idx) == 0:
        print("Training stopped: split produced an empty train or validation set.")
        return 2

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
        class_weight=class_weights,
        callbacks=[
            EarlyStopping(
                monitor="val_accuracy",
                patience=20,
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
    top_confusions = {}
    confusion_counts = {}
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
        miss_count = 0
        if len(wrong):
            values, counts = np.unique(wrong, return_counts=True)
            max_idx = int(np.argmax(counts))
            top_miss = classes[int(values[max_idx])]
            miss_count = int(counts[max_idx])
        per_class[name] = {"correct": correct, "total": total, "accuracy": acc}
        top_confusions[name] = top_miss
        confusion_counts[name] = {"top_miss": top_miss, "count": miss_count}
        flag = " CHECK" if acc < 80 else ""
        print(f"  {name:<15} {correct:>8} {total:>7} {acc:>7.1f}%  {top_miss}{flag}")

    print("\n[6/6] Exporting TFLite...")
    tflite_status = {"ok": False, "error": None, "input_shape": None, "output_shape": None, "smoke_class": None}
    try:
        in_shape, out_shape, test_class = export_tflite(model)
        kb = TFLITE_PATH.stat().st_size / 1024.0
        tflite_status.update(
            {
                "ok": True,
                "input_shape": in_shape,
                "output_shape": out_shape,
                "smoke_class": int(test_class),
                "size_kb": kb,
            }
        )
        print(f"  Saved : {TFLITE_PATH} ({kb:.0f} KB)")
        print(f"  Input : {in_shape}")
        print(f"  Output: {out_shape}")
        print(f"  Smoke : class {test_class}")
    except Exception as exc:
        tflite_status["error"] = str(exc)
        print(f"  TFLite export failed: {exc}")

    report = {
        "profile": WORD_PROFILE,
        "word_profile": WORD_PROFILE,
        "feature_profile": FEATURE_PROFILE,
        "model_type": "Residual Dilated TCN",
        "model_kind": "rd_tcn",
        "dataset": str(DATA_DIR),
        "model": str(MODEL_PATH),
        "labels": str(LABELS_PATH),
        "tflite": str(TFLITE_PATH),
        "runtime_manifest": str(RUNTIME_MANIFEST_PATH),
        "seq_len": SEQ_LEN,
        "feature_size": FEAT_SIZE,
        "input_shape": [1, SEQ_LEN, FEAT_SIZE],
        "output_shape": [1, len(classes)],
        "target_words": TARGET_WORDS,
        "training_words": TRAINING_WORDS,
        "classes": classes,
        "sample_counts": {word: stats.get("sequences", 0) for word, stats in class_stats.items()},
        "class_stats": class_stats,
        "train_validation_split_method": "grouped source split with optional random fallback for one-group classes",
        "manual_to_train": MANUAL_TO_TRAIN,
        "random_val_fallback": RANDOM_VAL_FALLBACK,
        "min_sequences_per_class": MIN_SEQS,
        "min_groups_per_class": MIN_GROUPS,
        "ignored_wrong_shape_files": int(skipped_wrong_shape),
        "ignored_missing_hand_metadata_files": int(skipped_missing_hand_metadata),
        "train_sequences": int(len(X_train)),
        "val_sequences": int(len(X_val)),
        "train_groups": int(len(set(groups[train_idx].tolist()))),
        "val_groups": int(len(set(groups[val_idx].tolist()))),
        "best_grouped_val_accuracy": best_val,
        "per_class": per_class,
        "top_confusions": top_confusions,
        "confusion_counts": confusion_counts,
        "tflite_export_status": tflite_status,
        "artifacts": {
            "keras": str(MODEL_PATH.relative_to(ROOT)).replace("\\", "/"),
            "tflite": str(TFLITE_PATH.relative_to(ROOT)).replace("\\", "/"),
            "labels": str(LABELS_PATH.relative_to(ROOT)).replace("\\", "/"),
            "training_report": str(REPORT_PATH.relative_to(ROOT)).replace("\\", "/"),
            "runtime_manifest": str(RUNTIME_MANIFEST_PATH.relative_to(ROOT)).replace("\\", "/"),
        },
    }
    with open(REPORT_PATH, "w", encoding="utf-8") as f:
        json.dump(report, f, indent=2)
    manifest_path = write_runtime_manifest(classes, report)
    comparison_path = write_comparison_doc(report)

    print("\n" + "=" * 72)
    print("  COMPLETE")
    print(f"  Model     : {MODEL_PATH}")
    print(f"  Labels    : {LABELS_PATH}")
    print(f"  TFLite    : {TFLITE_PATH}")
    print(f"  Report    : {REPORT_PATH}")
    print(f"  Manifest  : {manifest_path}")
    print(f"  Comparison: {comparison_path}")
    print(f"  Classes   : {classes}")
    print("=" * 72)
    return 0


if __name__ == "__main__":
    sys.exit(main())
