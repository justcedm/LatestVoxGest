"""
Sanity-check the trained LSTM word model against available 30x162 sequences.

This is not a replacement for live testing or the grouped validation report
created by 19_train_lstm.py. It is useful for quickly finding which saved
classes the current dynamic .h5 model cannot recognize at all.
"""

import json
import os
from pathlib import Path

import numpy as np
import tensorflow as tf

from lstm_features import (
    FEAT_SIZE,
    SEQ_LEN,
    apply_sequence_feature_policy,
    single_hand_pose_enabled,
)

os.environ["PROTOCOL_BUFFERS_PYTHON_IMPLEMENTATION"] = "python"
os.environ["TF_CPP_MIN_LOG_LEVEL"] = "3"

ROOT = Path(__file__).resolve().parents[1]
DATA_DIR = Path(os.environ.get("VOXGEST_LSTM_DATASET", ROOT / "dataset_words_lstm"))
if not DATA_DIR.exists():
    DATA_DIR = ROOT / "dataset_words"

LSTM_MODEL_PATH = ROOT / "model" / "voxgest_lstm_v1.h5"
LSTM_LABELS_PATH = ROOT / "model" / "class_labels_lstm_v1.json"
LSTM_REPORT_PATH = ROOT / "model" / "lstm_training_report.json"
TCN_MODEL_PATH = ROOT / "model" / "voxgest_tcn_v1.h5"
TCN_LABELS_PATH = ROOT / "model" / "class_labels_tcn_v1.json"
TCN_REPORT_PATH = ROOT / "model" / "tcn_training_report.json"
METADATA_PATH = DATA_DIR / "metadata_lstm_v2.json"
DYNAMIC_MODEL_KIND = os.environ.get("VOXGEST_DYNAMIC_MODEL", "auto").strip().lower()
REQUIRE_HAND_METADATA = (
    os.environ.get(
        "VOXGEST_REQUIRE_HAND_METADATA",
        "1" if single_hand_pose_enabled() else "0",
    ).strip()
    != "0"
)


def load_metadata():
    if not METADATA_PATH.exists():
        return {}
    with open(METADATA_PATH, "r", encoding="utf-8") as f:
        return json.load(f).get("samples", {})


def infer_mirrored_input(word, file_name, metadata):
    sample_meta = metadata.get(f"{word}/{file_name}", {})
    if "mirrored_input" in sample_meta:
        return bool(sample_meta["mirrored_input"])
    return sample_meta.get("source_video") == "manual_webcam" or file_name.startswith("manual_")


def infer_hand_preference(word, file_name, metadata):
    sample_meta = metadata.get(f"{word}/{file_name}", {})
    return sample_meta.get("dominant_hand")


def is_manual_sample(word, file_name, metadata):
    sample_meta = metadata.get(f"{word}/{file_name}", {})
    return (
        file_name.startswith("manual_")
        or sample_meta.get("source_video") == "manual_webcam"
    )


def has_hand_policy_metadata(word, file_name, metadata):
    sample_meta = metadata.get(f"{word}/{file_name}", {})
    return all(
        field in sample_meta
        for field in ("dominant_hand", "mirrored_input", "single_hand_pose")
    )


def load_labels(path):
    with open(path, "r", encoding="utf-8") as f:
        raw = json.load(f)
    if all(str(k).isdigit() for k in raw.keys()):
        idx_to_label = {int(k): v for k, v in raw.items()}
        label_to_idx = {v: int(k) for k, v in raw.items()}
    else:
        label_to_idx = {k: int(v) for k, v in raw.items()}
        idx_to_label = {int(v): k for k, v in raw.items()}
    return label_to_idx, idx_to_label


def dynamic_model_candidates():
    def report_score(path):
        try:
            with open(path, "r", encoding="utf-8") as f:
                return float(json.load(f).get("best_grouped_val_accuracy", -1.0))
        except Exception:
            return -1.0

    options = {
        "tcn": [("TCN", TCN_MODEL_PATH, TCN_LABELS_PATH, TCN_REPORT_PATH)],
        "lstm": [("LSTM", LSTM_MODEL_PATH, LSTM_LABELS_PATH, LSTM_REPORT_PATH)],
    }
    if DYNAMIC_MODEL_KIND in options:
        candidates = options[DYNAMIC_MODEL_KIND]
    else:
        candidates = sorted(
            options["tcn"] + options["lstm"],
            key=lambda item: report_score(item[3]),
            reverse=True,
        )
    return [(name, model, labels) for name, model, labels, _ in candidates]


print("Loading motion model...")
model = None
model_name = ""
model_path = None
last_error = None
for candidate_name, candidate_model, candidate_labels in dynamic_model_candidates():
    if not candidate_model.exists() or not candidate_labels.exists():
        last_error = f"missing {candidate_model} or {candidate_labels}"
        continue
    try:
        model = tf.keras.models.load_model(candidate_model)
        label_to_idx, idx_to_label = load_labels(candidate_labels)
        model_name = candidate_name
        model_path = candidate_model
        break
    except Exception as exc:
        last_error = exc

if model is None:
    raise RuntimeError(f"Could not load motion model: {last_error}")

metadata = load_metadata()

print(f"Dataset: {DATA_DIR}")
print(f"Model: {model_name} ({model_path})")
print(f"Classes: {len(label_to_idx)}")
print()

results = {}
empty_words = {}
wrong_shape = 0
skipped_missing_hand_metadata = 0

for word in sorted(p.name for p in DATA_DIR.iterdir() if p.is_dir()):
    if word not in label_to_idx:
        continue

    samples = []
    word_skipped_missing_hand_metadata = 0
    for file_path in sorted((DATA_DIR / word).glob("*.npy")):
        try:
            arr = np.load(file_path, allow_pickle=False)
        except Exception:
            continue
        if arr.shape == (SEQ_LEN, FEAT_SIZE):
            if (
                REQUIRE_HAND_METADATA
                and is_manual_sample(word, file_path.name, metadata)
                and not has_hand_policy_metadata(word, file_path.name, metadata)
            ):
                skipped_missing_hand_metadata += 1
                word_skipped_missing_hand_metadata += 1
                continue
            mirrored_input = infer_mirrored_input(word, file_path.name, metadata)
            hand_preference = infer_hand_preference(word, file_path.name, metadata)
            samples.append(
                apply_sequence_feature_policy(
                    arr,
                    hand_preference=hand_preference,
                    mirrored_input=mirrored_input,
                ).astype(np.float32)
            )
        else:
            wrong_shape += 1

    if not samples:
        reason = "no usable 30x162 samples"
        if word_skipped_missing_hand_metadata:
            reason += (
                f"; skipped {word_skipped_missing_hand_metadata} manual samples "
                "missing hand metadata"
            )
        print(f"  {word:<15} {reason}")
        empty_words[word] = {
            "skipped_missing_hand_metadata": word_skipped_missing_hand_metadata,
        }
        continue

    X = np.array(samples, dtype=np.float32)
    probs = model.predict(X, verbose=0)
    pred_classes = np.argmax(probs, axis=1)
    true_idx = label_to_idx[word]
    correct = int(np.sum(pred_classes == true_idx))
    acc = correct / len(samples) * 100.0

    wrong = pred_classes[pred_classes != true_idx]
    top_miss = "-"
    if len(wrong):
        values, counts = np.unique(wrong, return_counts=True)
        top_miss = idx_to_label.get(int(values[int(np.argmax(counts))]), "?")

    avg_conf = float(np.mean(np.max(probs, axis=1)) * 100.0)
    results[word] = (acc, len(samples), avg_conf, top_miss)

print("=" * 70)
print("  MOTION WORD SANITY REPORT")
print("=" * 70)
print("  Note: this tests saved data, not a fresh live signer.")
if wrong_shape:
    print(f"  Ignored wrong-shape legacy files: {wrong_shape}")
if skipped_missing_hand_metadata:
    print(f"  Ignored manual samples missing hand metadata: {skipped_missing_hand_metadata}")
print()
print(f"  {'Word':<15} {'Acc':>8} {'Samples':>8} {'AvgConf':>9}  Top miss")
print("  " + "-" * 58)
for word, (acc, total, avg_conf, top_miss) in sorted(results.items(), key=lambda item: item[1][0]):
    flag = " CHECK" if acc < 80 else ""
    print(f"  {word:<15} {acc:>7.1f}% {total:>8} {avg_conf:>8.1f}%  {top_miss}{flag}")

if empty_words:
    print("\n  Model labels without usable samples:")
    for word in sorted(empty_words):
        skipped = empty_words[word]["skipped_missing_hand_metadata"]
        suffix = (
            f" ({skipped} skipped for missing hand metadata)"
            if skipped
            else ""
        )
        print(f"  - {word}{suffix}")

ready = sum(1 for acc, _, _, _ in results.values() if acc >= 80)
print(f"\n  Ready on saved-data sanity check: {ready}/{len(label_to_idx)}")
print("  For real quality, use model/lstm_training_report.json and live tests.")
