"""Train the future VoxGest FSL residual dilated TCN model.

Do not run this until the FSL audit shows at least five labels at their
minimum sample counts. This script is intentionally separate from the active
ASL/onehand162 Android runtime.
"""

from __future__ import annotations

import argparse
import json
import random
from collections import Counter, defaultdict
from pathlib import Path

import numpy as np

from fsl_config import FSL_EXPECTED_SHAPE, FSL_LABELS, FSL_MINIMUMS


ROOT = Path(__file__).resolve().parents[1]
DATASET_ROOT = ROOT / "external_datasets" / "fsl_features"
MODEL_DIR = ROOT / "model"
H5_PATH = MODEL_DIR / "voxgest_rdtcn_fsl_v1.h5"
TFLITE_PATH = MODEL_DIR / "voxgest_rdtcn_fsl_v1.tflite"
REPORT_PATH = MODEL_DIR / "fsl_rdtcn_training_report_v1.json"
MANIFEST_PATH = MODEL_DIR / "runtime_manifest_fsl_rdtcn_v1.json"
RANDOM_SEED = 42


def load_json(path: Path) -> dict:
    if not path.exists():
        return {}
    try:
        with path.open("r", encoding="utf-8") as file:
            return json.load(file)
    except Exception:
        return {}


def load_samples(dataset_root: Path) -> tuple[list[dict], list[dict]]:
    samples = []
    skipped = []
    for label in FSL_LABELS:
        label_dir = dataset_root / label
        if not label_dir.exists():
            continue
        for npy_path in sorted(label_dir.glob("*.npy")):
            try:
                arr = np.load(npy_path, allow_pickle=False)
            except Exception as exc:
                skipped.append({"path": str(npy_path), "label": label, "reason": f"unreadable:{exc}"})
                continue
            if tuple(arr.shape) != FSL_EXPECTED_SHAPE:
                skipped.append({"path": str(npy_path), "label": label, "reason": f"shape:{list(arr.shape)}"})
                continue
            meta = load_json(npy_path.with_suffix(".meta.json"))
            samples.append(
                {
                    "path": npy_path,
                    "label": label,
                    "signer_id": str(meta.get("signer_id") or "UNKNOWN_SIGNER"),
                }
            )
    return samples, skipped


def ready_labels(samples: list[dict]) -> list[str]:
    counts = Counter(sample["label"] for sample in samples)
    return [label for label in FSL_LABELS if counts[label] >= FSL_MINIMUMS[label]]


def split_samples(samples: list[dict], labels: list[str]) -> tuple[list[dict], list[dict], dict]:
    rng = random.Random(RANDOM_SEED)
    train = []
    val = []
    split_methods = {}

    for label in labels:
        label_samples = [sample for sample in samples if sample["label"] == label]
        by_signer = defaultdict(list)
        for sample in label_samples:
            by_signer[sample["signer_id"]].append(sample)

        if len(by_signer) >= 2:
            holdout_signer = sorted(by_signer, key=lambda signer: (len(by_signer[signer]), signer))[0]
            val.extend(by_signer[holdout_signer])
            for signer, signer_samples in by_signer.items():
                if signer != holdout_signer:
                    train.extend(signer_samples)
            split_methods[label] = {
                "method": "holdout_signer",
                "validation_signer": holdout_signer,
            }
        else:
            print(f"WARNING: single_signer_fallback label={label}")
            rng.shuffle(label_samples)
            cut = max(1, int(len(label_samples) * 0.8))
            train.extend(label_samples[:cut])
            val.extend(label_samples[cut:] or label_samples[-1:])
            split_methods[label] = {
                "method": "single_signer_fallback_random_80_20",
                "validation_signer": label_samples[-1]["signer_id"] if label_samples else "UNKNOWN_SIGNER",
            }

    return train, val, split_methods


def load_arrays(samples: list[dict], label_to_index: dict[str, int]) -> tuple[np.ndarray, np.ndarray]:
    x = np.asarray([np.load(sample["path"], allow_pickle=False).astype(np.float32) for sample in samples], dtype=np.float32)
    y = np.asarray([label_to_index[sample["label"]] for sample in samples], dtype=np.int64)
    return x, y


def augment_batch(batch: np.ndarray) -> np.ndarray:
    out = batch.copy()
    for index in range(out.shape[0]):
        jitter = random.choice([-1, 0, 1])
        if jitter == 1:
            out[index] = np.vstack([out[index][1:], out[index][-1:]])
        elif jitter == -1:
            out[index] = np.vstack([out[index][:1], out[index][:-1]])

        scale = np.random.uniform(0.95, 1.05)
        out[index] *= scale

        noise = np.random.normal(0.0, 0.005, out[index].shape).astype(np.float32)
        noise[:, 2::3] = 0.0
        out[index] += noise
        out[index, 0:3] = 0.0
    return out


class FslBatchSequence:
    def __init__(self, x: np.ndarray, y: np.ndarray, batch_size: int, augment: bool):
        import tensorflow as tf

        class _Sequence(tf.keras.utils.Sequence):
            def __init__(self, outer):
                self.outer = outer

            def __len__(self):
                return int(np.ceil(len(self.outer.x) / self.outer.batch_size))

            def __getitem__(self, index):
                start = index * self.outer.batch_size
                end = start + self.outer.batch_size
                batch_x = self.outer.x[self.outer.indices[start:end]]
                batch_y = self.outer.y[self.outer.indices[start:end]]
                if self.outer.augment:
                    batch_x = augment_batch(batch_x)
                return batch_x, batch_y

            def on_epoch_end(self):
                np.random.shuffle(self.outer.indices)

        self.x = x
        self.y = y
        self.batch_size = batch_size
        self.augment = augment
        self.indices = np.arange(len(x))
        self.sequence = _Sequence(self)


def rdtcn_block(x, filters, dilation_rate, dropout=0.2):
    from tensorflow.keras.layers import Activation, Add, Conv1D, LayerNormalization, SpatialDropout1D

    residual = x
    x = Conv1D(filters, kernel_size=3, padding="causal", dilation_rate=dilation_rate)(x)
    x = LayerNormalization()(x)
    x = Activation("relu")(x)
    x = SpatialDropout1D(dropout)(x)
    x = Conv1D(filters, kernel_size=3, padding="causal", dilation_rate=dilation_rate)(x)
    x = LayerNormalization()(x)
    if residual.shape[-1] != filters:
        residual = Conv1D(filters, kernel_size=1)(residual)
    x = Add()([x, residual])
    return Activation("relu")(x)


def build_model(num_classes: int):
    from tensorflow.keras import Model
    from tensorflow.keras.layers import Dense, Dropout, GlobalAveragePooling1D, Input
    from tensorflow.keras.optimizers import Adam

    inputs = Input(shape=FSL_EXPECTED_SHAPE)
    x = rdtcn_block(inputs, 64, dilation_rate=1)
    x = rdtcn_block(x, 64, dilation_rate=2)
    x = rdtcn_block(x, 64, dilation_rate=4)
    x = GlobalAveragePooling1D()(x)
    x = Dense(64, activation="relu")(x)
    x = Dropout(0.3)(x)
    outputs = Dense(num_classes, activation="softmax")(x)
    model = Model(inputs, outputs)
    model.compile(optimizer=Adam(3e-4), loss="sparse_categorical_crossentropy", metrics=["accuracy"])
    return model


def confusion_and_accuracy(y_true: np.ndarray, y_pred: np.ndarray, labels: list[str]) -> tuple[list[list[int]], dict[str, float]]:
    matrix = np.zeros((len(labels), len(labels)), dtype=int)
    for true, pred in zip(y_true, y_pred):
        matrix[int(true), int(pred)] += 1
    per_class = {}
    for index, label in enumerate(labels):
        total = int(matrix[index].sum())
        per_class[label] = float(matrix[index, index] / total) if total else 0.0
    return matrix.tolist(), per_class


def export_tflite(model, output_path: Path) -> tuple[str, int]:
    import tensorflow as tf

    converter = tf.lite.TFLiteConverter.from_keras_model(model)
    converter.optimizations = [tf.lite.Optimize.DEFAULT]
    converter.target_spec.supported_types = [tf.float16]
    try:
        tflite_model = converter.convert()
        strategy = "float16"
    except Exception:
        converter = tf.lite.TFLiteConverter.from_keras_model(model)
        converter.optimizations = [tf.lite.Optimize.DEFAULT]
        tflite_model = converter.convert()
        strategy = "dynamic_range"
    output_path.write_bytes(tflite_model)
    return strategy, output_path.stat().st_size


def update_manifest(labels: list[str], ready_for_android: bool) -> None:
    manifest = load_json(MANIFEST_PATH)
    manifest.update(
        {
            "version": 1,
            "profile": "fsl_rdtcn_v1",
            "status": "ready_for_android" if ready_for_android else "trained_needs_review",
            "model_kind": "rd_tcn",
            "sequence_length": FSL_EXPECTED_SHAPE[0],
            "feature_size": FSL_EXPECTED_SHAPE[1],
            "input_shape": [1, FSL_EXPECTED_SHAPE[0], FSL_EXPECTED_SHAPE[1]],
            "labels": labels,
        }
    )
    MANIFEST_PATH.write_text(json.dumps(manifest, indent=2), encoding="utf-8")


def train(args) -> dict:
    import tensorflow as tf
    from tensorflow.keras.callbacks import EarlyStopping, ReduceLROnPlateau

    random.seed(RANDOM_SEED)
    np.random.seed(RANDOM_SEED)
    tf.random.set_seed(RANDOM_SEED)

    samples, skipped = load_samples(args.dataset)
    labels = ready_labels(samples)
    if len(labels) < 5:
        raise SystemExit(f"Need at least 5 ready labels; found {len(labels)}: {labels}")

    train_samples, val_samples, split_methods = split_samples(samples, labels)
    label_to_index = {label: index for index, label in enumerate(labels)}
    x_train, y_train = load_arrays(train_samples, label_to_index)
    x_val, y_val = load_arrays(val_samples, label_to_index)

    model = build_model(len(labels))
    train_seq = FslBatchSequence(x_train, y_train, args.batch_size, augment=True).sequence
    val_seq = FslBatchSequence(x_val, y_val, args.batch_size, augment=False).sequence
    history = model.fit(
        train_seq,
        validation_data=val_seq,
        epochs=args.epochs,
        callbacks=[
            EarlyStopping(monitor="val_accuracy", patience=12, restore_best_weights=True),
            ReduceLROnPlateau(monitor="val_accuracy", patience=6, factor=0.5, verbose=1),
        ],
        verbose=1,
    )

    MODEL_DIR.mkdir(parents=True, exist_ok=True)
    model.save(H5_PATH)
    tflite_strategy, tflite_size = export_tflite(model, TFLITE_PATH)

    val_probs = model.predict(x_val, batch_size=args.batch_size, verbose=0)
    val_pred = np.argmax(val_probs, axis=1)
    confusion, per_class_accuracy = confusion_and_accuracy(y_val, val_pred, labels)
    val_accuracy = float(np.mean(val_pred == y_val)) if len(y_val) else 0.0
    ready_for_android = val_accuracy >= 0.80 and (tflite_size / 1024.0) <= 500.0
    update_manifest(labels, ready_for_android)

    report = {
        "labels": labels,
        "input_shape": list(FSL_EXPECTED_SHAPE),
        "train_sequences": int(len(x_train)),
        "val_sequences": int(len(x_val)),
        "skipped_files": skipped,
        "split_method_used": split_methods,
        "history": {key: [float(v) for v in values] for key, values in history.history.items()},
        "val_accuracy": val_accuracy,
        "per_class_accuracy": per_class_accuracy,
        "confusion_matrix": confusion,
        "total_params": int(model.count_params()),
        "tflite_conversion": tflite_strategy,
        "tflite_size_kb": float(tflite_size / 1024.0),
        "ready_for_android": ready_for_android,
        "artifacts": {
            "h5": str(H5_PATH),
            "tflite": str(TFLITE_PATH),
            "manifest": str(MANIFEST_PATH),
        },
    }
    REPORT_PATH.write_text(json.dumps(report, indent=2), encoding="utf-8")
    return report


def main() -> None:
    parser = argparse.ArgumentParser(description="Train the FSL RD-TCN model after FSL audit readiness.")
    parser.add_argument("--dataset", type=Path, default=DATASET_ROOT)
    parser.add_argument("--epochs", type=int, default=100)
    parser.add_argument("--batch_size", type=int, default=32)
    args = parser.parse_args()

    report = train(args)
    print("FSL RD-TCN training complete")
    print(f"Val accuracy      : {report['val_accuracy']:.4f}")
    print(f"TFLite size       : {report['tflite_size_kb']:.1f} KB")
    print(f"Ready for Android : {report['ready_for_android']}")
    print("Per-class accuracy")
    for label, acc in report["per_class_accuracy"].items():
        print(f"{label:<12} {acc:.4f}")
    print(f"Report            : {REPORT_PATH}")


if __name__ == "__main__":
    main()
