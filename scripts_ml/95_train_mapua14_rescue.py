"""Four-candidate Mapua-14 CV, sealed-test evaluation, and float32 export.

All checkpoints and temporary artifacts remain in the isolated C: experiment
workspace. The sealed clip-level test is evaluated only after development-only
selection and full-development retraining.
"""

from __future__ import annotations

import csv
import hashlib
import json
import math
import os
import random
import shutil
import time
from collections import Counter
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

os.environ.setdefault("TF_DETERMINISTIC_OPS", "1")
os.environ.setdefault("TF_CPP_MIN_LOG_LEVEL", "2")

import numpy as np
import tensorflow as tf
from sklearn.metrics import (
    accuracy_score,
    balanced_accuracy_score,
    confusion_matrix,
    precision_recall_fscore_support,
)

REPO_ROOT = Path(__file__).resolve().parents[1]
CONFIG_PATH = REPO_ROOT / "training_configs" / "mapua14_rescue_v1.json"
SPLIT_PATH = REPO_ROOT / "reports" / "mapua14_rescue_v1" / "split_manifest.csv"
REPO_REPORT_PATH = REPO_ROOT / "reports" / "mapua14_rescue_v1" / "training_results.json"
EXPERIMENT_ROOT = Path(r"C:\VOXGEST_TRAINING\MAPUA14_RESCUE_V1")
FEATURE_MANIFEST = EXPERIMENT_ROOT / "feature_manifest.csv"
CANDIDATE_ROOT = EXPERIMENT_ROOT / "candidates"
FINAL_ROOT = EXPERIMENT_ROOT / "final"
SELECTION_PATH = EXPERIMENT_ROOT / "development_selection.json"
SEALED_ONCE_PATH = EXPERIMENT_ROOT / "sealed_test_evaluation_once.npz"
EXTERNAL_REPORT_PATH = EXPERIMENT_ROOT / "reports" / "training_results.json"


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def atomic_json(path: Path, payload: Any) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_suffix(path.suffix + ".tmp")
    temporary.write_text(json.dumps(payload, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    temporary.replace(path)


def set_seeds(seed: int) -> None:
    os.environ["PYTHONHASHSEED"] = str(seed)
    random.seed(seed)
    np.random.seed(seed)
    tf.random.set_seed(seed)


def load_rows(path: Path) -> list[dict[str, str]]:
    with path.open("r", encoding="utf-8-sig", newline="") as stream:
        return list(csv.DictReader(stream))


def load_config() -> dict[str, Any]:
    return json.loads(CONFIG_PATH.read_text(encoding="utf-8"))


def load_features(rows: list[dict[str, str]], length: int) -> tuple[np.ndarray, np.ndarray]:
    matrix = np.empty((len(rows), length, 225), dtype=np.float32)
    labels = np.empty(len(rows), dtype=np.int64)
    for index, row in enumerate(rows):
        with np.load(row["feature_path"], allow_pickle=False) as archive:
            value = archive[f"sequence_{length}"]
        if value.shape != (length, 225) or value.dtype != np.float32 or not np.isfinite(value).all():
            raise ValueError(f"invalid feature tensor: {row['feature_path']} {value.shape} {value.dtype}")
        matrix[index] = value
        labels[index] = int(row["class_index"])
    return matrix, labels


def temporal_warp(sequence: np.ndarray, gamma: float) -> np.ndarray:
    positions = np.linspace(0.0, 1.0, len(sequence), dtype=np.float32) ** np.float32(gamma)
    positions *= np.float32(len(sequence) - 1)
    low = np.floor(positions).astype(np.int64)
    high = np.minimum(low + 1, len(sequence) - 1)
    alpha = (positions - low).reshape(-1, 1)
    return ((1.0 - alpha) * sequence[low] + alpha * sequence[high]).astype(np.float32)


def augment_training(x: np.ndarray, y: np.ndarray, config: dict[str, Any], seed: int) -> tuple[np.ndarray, np.ndarray]:
    policy = config["augmentation"]
    copies = int(policy["copies_per_source"])
    rng = np.random.default_rng(seed)
    output_x = [x]
    output_y = [y]
    for _ in range(copies):
        augmented = np.empty_like(x)
        for index, source in enumerate(x):
            speed = float(rng.uniform(*policy["temporal_scale_range"]))
            gamma = 1.0 / speed
            value = temporal_warp(source, gamma)
            coordinate_scale = np.float32(rng.uniform(*policy["coordinate_scale_range"]))
            nonzero = np.any(value.reshape(len(value), 75, 3) != 0.0, axis=2)
            value *= coordinate_scale
            jitter = rng.normal(0.0, policy["jitter_standard_deviation"], size=value.shape).astype(np.float32)
            expanded = np.repeat(nonzero, 3, axis=1)
            value += jitter * expanded
            value[:, 0:3] = 0.0
            maximum_dropout = int(policy["maximum_hand_dropout_frames"])
            dropout_count = int(rng.integers(0, maximum_dropout + 1))
            if dropout_count:
                frames = rng.choice(len(value), size=dropout_count, replace=False)
                if rng.random() < 0.5:
                    value[frames, 99:162] = 0.0
                else:
                    value[frames, 162:225] = 0.0
            augmented[index] = value
        output_x.append(augmented)
        output_y.append(y.copy())
    combined_x = np.concatenate(output_x, axis=0)
    combined_y = np.concatenate(output_y, axis=0)
    order = rng.permutation(len(combined_y))
    return combined_x[order], combined_y[order]


def build_model(architecture: str, length: int, class_count: int, learning_rate: float) -> tf.keras.Model:
    layers = tf.keras.layers
    inputs = layers.Input(shape=(length, 225), name="fullsign225_sequence")
    x = inputs
    if architecture == "RD-TCN":
        for block, dilation in enumerate((1, 2, 4)):
            residual = x
            y = layers.Conv1D(64, 3, padding="causal", dilation_rate=dilation, name=f"rdtcn_b{block}_conv1")(x)
            y = layers.LayerNormalization(name=f"rdtcn_b{block}_norm1")(y)
            y = layers.Activation("relu", name=f"rdtcn_b{block}_relu1")(y)
            y = layers.SpatialDropout1D(0.2, name=f"rdtcn_b{block}_dropout")(y)
            y = layers.Conv1D(64, 3, padding="causal", dilation_rate=dilation, name=f"rdtcn_b{block}_conv2")(y)
            y = layers.LayerNormalization(name=f"rdtcn_b{block}_norm2")(y)
            if residual.shape[-1] != 64:
                residual = layers.Conv1D(64, 1, padding="same", name=f"rdtcn_b{block}_projection")(residual)
            x = layers.Activation("relu", name=f"rdtcn_b{block}_out")(layers.Add()([residual, y]))
        x = layers.GlobalAveragePooling1D(name="temporal_pool")(x)
    elif architecture == "GRU":
        x = layers.LayerNormalization(name="input_normalization")(x)
        x = layers.GRU(96, dropout=0.1, unroll=True, name="gru")(x)
    else:
        raise ValueError(f"unsupported architecture {architecture}")
    x = layers.Dense(64, activation="relu", name="dense64")(x)
    x = layers.Dropout(0.3, name="classifier_dropout")(x)
    outputs = layers.Dense(class_count, activation="softmax", name="class_probabilities")(x)
    model = tf.keras.Model(inputs, outputs, name=f"mapua14_{architecture.lower().replace('-', '')}_{length}f_v1")
    model.compile(
        optimizer=tf.keras.optimizers.Adam(learning_rate=learning_rate),
        loss="sparse_categorical_crossentropy",
        metrics=["accuracy"],
    )
    return model


def calibration(y_true: np.ndarray, probabilities: np.ndarray, bins: int = 10) -> dict[str, float]:
    predicted = np.argmax(probabilities, axis=1)
    confidence = np.max(probabilities, axis=1)
    correct = (predicted == y_true).astype(np.float64)
    ece = 0.0
    for lower in np.linspace(0.0, 1.0, bins, endpoint=False):
        upper = lower + 1.0 / bins
        mask = (confidence >= lower) & (confidence < upper if upper < 1.0 else confidence <= upper)
        if mask.any():
            ece += float(mask.mean()) * abs(float(correct[mask].mean()) - float(confidence[mask].mean()))
    clipped = np.clip(probabilities[np.arange(len(y_true)), y_true], 1e-7, 1.0)
    return {"expected_calibration_error_10bin": ece, "negative_log_likelihood": float(-np.log(clipped).mean())}


def metric_report(y_true: np.ndarray, probabilities: np.ndarray, labels: list[str]) -> dict[str, Any]:
    predicted = np.argmax(probabilities, axis=1)
    precision, recall, f1, support = precision_recall_fscore_support(y_true, predicted, labels=np.arange(len(labels)), zero_division=0)
    matrix = confusion_matrix(y_true, predicted, labels=np.arange(len(labels)))
    confusions = []
    for truth in range(len(labels)):
        for guess in range(len(labels)):
            if truth != guess and matrix[truth, guess]:
                confusions.append({"true": labels[truth], "predicted": labels[guess], "count": int(matrix[truth, guess])})
    confusions.sort(key=lambda item: (-item["count"], item["true"], item["predicted"]))
    return {
        "accuracy": float(accuracy_score(y_true, predicted)),
        "balanced_accuracy": float(balanced_accuracy_score(y_true, predicted)),
        "macro_precision": float(np.mean(precision)), "macro_recall": float(np.mean(recall)), "macro_f1": float(np.mean(f1)),
        "weakest_class_f1": float(np.min(f1)),
        "per_class": {label: {"precision": float(precision[i]), "recall": float(recall[i]), "f1": float(f1[i]), "support": int(support[i])} for i, label in enumerate(labels)},
        "confusion_matrix": matrix.tolist(), "top_confusions": confusions[:20], **calibration(y_true, probabilities),
    }


def train_fold(candidate: dict[str, Any], fold: int, rows: list[dict[str, str]], config: dict[str, Any], contract: dict[str, str]) -> dict[str, Any]:
    output = CANDIDATE_ROOT / candidate["id"] / f"fold_{fold}"
    metrics_path, predictions_path, model_path = output / "metrics.json", output / "validation_predictions.npz", output / "best.keras"
    if metrics_path.is_file() and predictions_path.is_file() and model_path.is_file():
        cached = json.loads(metrics_path.read_text(encoding="utf-8"))
        if cached.get("contract") == contract:
            print(f"candidate {candidate['id']} fold {fold}: cached", flush=True)
            return cached
    output.mkdir(parents=True, exist_ok=True)
    development = [row for row in rows if row["partition"] == "development"]
    train_rows = [row for row in development if int(row["development_fold"]) != fold]
    validation_rows = [row for row in development if int(row["development_fold"]) == fold]
    length = int(candidate["temporal_length"])
    x_train, y_train = load_features(train_rows, length)
    x_validation, y_validation = load_features(validation_rows, length)
    seed = int(config["random_seed"]) + ord(candidate["id"]) * 101 + fold
    set_seeds(seed)
    x_train, y_train = augment_training(x_train, y_train, config, seed)
    tf.keras.backend.clear_session()
    model = build_model(candidate["architecture"], length, len(config["labels"]), config["training"]["learning_rate"])
    if model.count_params() >= int(config["training"]["parameter_budget"]):
        raise RuntimeError(f"parameter budget exceeded: {model.count_params()}")
    callbacks = [
        tf.keras.callbacks.EarlyStopping(monitor="val_loss", patience=config["training"]["early_stopping_patience"], restore_best_weights=True, verbose=1),
        tf.keras.callbacks.ReduceLROnPlateau(monitor="val_loss", patience=4, factor=0.5, min_lr=1e-6, verbose=1),
    ]
    started = time.perf_counter()
    history = model.fit(
        x_train, y_train, validation_data=(x_validation, y_validation),
        epochs=config["training"]["maximum_epochs"], batch_size=config["training"]["batch_size"],
        callbacks=callbacks, verbose=2, shuffle=True,
    )
    seconds = time.perf_counter() - started
    probabilities = model.predict(x_validation, batch_size=32, verbose=0).astype(np.float32)
    report = metric_report(y_validation, probabilities, config["labels"])
    best_epoch = int(np.argmin(history.history["val_loss"]) + 1)
    report.update({
        "candidate": candidate, "fold": fold, "contract": contract, "seed": seed,
        "parameter_count": int(model.count_params()), "training_seconds": seconds,
        "epochs_run": len(history.history["loss"]), "best_epoch": best_epoch,
        "training_accuracy_at_best_epoch": float(history.history["accuracy"][best_epoch - 1]),
        "validation_accuracy_at_best_epoch": float(history.history["val_accuracy"][best_epoch - 1]),
        "overfit_gap": float(history.history["accuracy"][best_epoch - 1] - history.history["val_accuracy"][best_epoch - 1]),
        "training_source_count_before_augmentation": len(train_rows), "training_tensor_count_after_augmentation": len(y_train),
        "validation_source_count": len(validation_rows),
    })
    model.save(model_path)
    with predictions_path.open("wb") as stream:
        np.savez_compressed(stream, y_true=y_validation, probabilities=probabilities)
    atomic_json(metrics_path, report)
    return report


def candidate_cv(candidate: dict[str, Any], rows: list[dict[str, str]], config: dict[str, Any], contract: dict[str, str]) -> dict[str, Any]:
    folds = []
    failures = []
    all_truth, all_probabilities = [], []
    for fold in range(config["dataset"]["development_folds"]):
        try:
            result = train_fold(candidate, fold, rows, config, contract)
            folds.append(result)
            archive = np.load(CANDIDATE_ROOT / candidate["id"] / f"fold_{fold}" / "validation_predictions.npz", allow_pickle=False)
            all_truth.append(archive["y_true"])
            all_probabilities.append(archive["probabilities"])
        except Exception as error:
            failures.append({"fold": fold, "error": f"{type(error).__name__}: {error}"})
            print(f"candidate {candidate['id']} fold {fold}: FAILED {error}", flush=True)
    if failures:
        return {"candidate": candidate, "status": "FAIL", "failures": failures, "successful_folds": len(folds), "folds": folds}
    mean = lambda key: float(np.mean([fold[key] for fold in folds]))
    combined = metric_report(np.concatenate(all_truth), np.concatenate(all_probabilities), config["labels"])
    return {
        "candidate": candidate, "status": "PASS", "folds": folds,
        "mean_development_macro_f1": mean("macro_f1"), "mean_development_accuracy": mean("accuracy"),
        "mean_development_macro_precision": mean("macro_precision"), "mean_development_macro_recall": mean("macro_recall"),
        "mean_development_balanced_accuracy": mean("balanced_accuracy"),
        "mean_development_weakest_class_f1": mean("weakest_class_f1"),
        "mean_development_ece": mean("expected_calibration_error_10bin"),
        "mean_overfit_gap": mean("overfit_gap"), "parameter_count": int(folds[0]["parameter_count"]),
        "total_training_seconds": float(sum(fold["training_seconds"] for fold in folds)),
        "combined_out_of_fold_metrics": combined,
    }


def select_winner(results: list[dict[str, Any]]) -> dict[str, Any]:
    passing = [item for item in results if item["status"] == "PASS"]
    if not passing:
        raise RuntimeError("all four candidates failed")
    return max(passing, key=lambda item: (
        item["mean_development_macro_f1"], item["mean_development_weakest_class_f1"],
        -item["mean_development_ece"], -item["parameter_count"],
    ))


def export_and_parity(model: tf.keras.Model, x_test: np.ndarray, tf_probabilities: np.ndarray, bundle: Path) -> dict[str, Any]:
    converter = tf.lite.TFLiteConverter.from_keras_model(model)
    content = converter.convert()
    model_path = bundle / "mapua14_fullsign225_float32.tflite"
    model_path.write_bytes(content)
    interpreter = tf.lite.Interpreter(model_path=str(model_path))
    interpreter.allocate_tensors()
    input_detail, output_detail = interpreter.get_input_details()[0], interpreter.get_output_details()[0]
    tflite = np.empty_like(tf_probabilities)
    for index, value in enumerate(x_test):
        interpreter.set_tensor(input_detail["index"], value[np.newaxis].astype(np.float32))
        interpreter.invoke()
        tflite[index] = interpreter.get_tensor(output_detail["index"])[0]
    agreement = float(np.mean(np.argmax(tf_probabilities, axis=1) == np.argmax(tflite, axis=1)))
    maximum = float(np.max(np.abs(tf_probabilities - tflite)))
    passed = agreement == 1.0 and maximum <= 1e-5
    return {
        "passed": passed, "tf_tflite_top1_agreement": agreement, "maximum_probability_difference": maximum,
        "required_top1_agreement": 1.0, "maximum_allowed_probability_difference": 1e-5,
        "input_shape": input_detail["shape"].astype(int).tolist(), "output_shape": output_detail["shape"].astype(int).tolist(),
        "input_dtype": str(input_detail["dtype"]), "output_dtype": str(output_detail["dtype"]),
        "model_path": str(model_path), "model_sha256": sha256_file(model_path), "model_size_bytes": model_path.stat().st_size,
    }


def main() -> int:
    config = load_config()
    split_rows = load_rows(SPLIT_PATH)
    features = load_rows(FEATURE_MANIFEST)
    if len(split_rows) != 367 or len(features) != 367 or any(row["status"] not in {"PASS", "CACHED"} for row in features):
        raise RuntimeError("feature extraction is incomplete or contains failures")
    feature_by_id = {row["record_id"]: row for row in features}
    if set(feature_by_id) != {row["record_id"] for row in split_rows}:
        raise RuntimeError("split/feature manifests disagree")
    rows = [{**row, "feature_path": feature_by_id[row["record_id"]]["feature_path"]} for row in split_rows]
    split_sha, config_sha = sha256_file(SPLIT_PATH), sha256_file(CONFIG_PATH)
    contract = {"split_manifest_sha256": split_sha, "training_config_sha256": config_sha, "tensorflow_version": tf.__version__, "numpy_version": np.__version__}
    if REPO_REPORT_PATH.is_file() and EXTERNAL_REPORT_PATH.is_file():
        existing = json.loads(EXTERNAL_REPORT_PATH.read_text(encoding="utf-8"))
        if existing.get("contract") == contract:
            print(json.dumps(existing, indent=2))
            return 0
    set_seeds(config["random_seed"])
    candidate_results = []
    for candidate in config["training"]["candidates"]:
        print(f"=== Candidate {candidate['id']} {candidate['architecture']} {candidate['temporal_length']}f ===", flush=True)
        candidate_results.append(candidate_cv(candidate, rows, config, contract))
    winner_result = select_winner(candidate_results)
    selection = {
        "contract": contract, "selected_utc": datetime.now(timezone.utc).isoformat(),
        "primary_metric": "mean_development_macro_f1", "tie_breakers": ["mean_development_weakest_class_f1", "mean_development_ece", "parameter_count"],
        "candidate_results": candidate_results, "winner": winner_result["candidate"],
        "sealed_test_accessed_during_selection": False,
    }
    atomic_json(SELECTION_PATH, selection)
    winner = winner_result["candidate"]
    final_epochs = max(1, int(round(np.median([fold["best_epoch"] for fold in winner_result["folds"]]))))
    length = int(winner["temporal_length"])
    development_rows = [row for row in rows if row["partition"] == "development"]
    test_rows = [row for row in rows if row["partition"] == "sealed_test"]
    x_development, y_development = load_features(development_rows, length)
    seed = int(config["random_seed"]) + 9001
    set_seeds(seed)
    x_augmented, y_augmented = augment_training(x_development, y_development, config, seed)
    tf.keras.backend.clear_session()
    final_model = build_model(winner["architecture"], length, len(config["labels"]), config["training"]["learning_rate"])
    FINAL_ROOT.mkdir(parents=True, exist_ok=True)
    final_model_path = FINAL_ROOT / "winner.keras"
    started = time.perf_counter()
    final_history = final_model.fit(x_augmented, y_augmented, epochs=final_epochs, batch_size=config["training"]["batch_size"], verbose=2, shuffle=True)
    final_training_seconds = time.perf_counter() - started
    final_model.save(final_model_path)
    if SEALED_ONCE_PATH.is_file():
        sealed = np.load(SEALED_ONCE_PATH, allow_pickle=False)
        if str(sealed["contract_digest"].item()) != hashlib.sha256(json.dumps(contract, sort_keys=True).encode()).hexdigest():
            raise RuntimeError("existing sealed evaluation belongs to another contract; manual audit required")
        y_test, tf_probabilities = sealed["y_true"], sealed["tf_probabilities"]
    else:
        x_test, y_test = load_features(test_rows, length)
        tf_probabilities = final_model.predict(x_test, batch_size=32, verbose=0).astype(np.float32)
        with SEALED_ONCE_PATH.open("wb") as stream:
            np.savez_compressed(stream, y_true=y_test, tf_probabilities=tf_probabilities, contract_digest=hashlib.sha256(json.dumps(contract, sort_keys=True).encode()).hexdigest())
    if "x_test" not in locals():
        x_test, loaded_y = load_features(test_rows, length)
        if not np.array_equal(loaded_y, y_test):
            raise RuntimeError("sealed labels changed")
    test_metrics = metric_report(y_test, tf_probabilities, config["labels"])
    bundle = FINAL_ROOT / f"mapua14_fullsign225_{length}f_v1"
    bundle.mkdir(parents=True, exist_ok=True)
    labels_payload = {
        "profile_id": "MAPUA14_RESCUE_V1", "class_count": 14, "feature_version": config["feature_contract"]["version"],
        "temporal_length": length, "classes": [{"index": i, "id": label, **config["presentation"][label]} for i, label in enumerate(config["labels"])],
    }
    labels_path = bundle / "class_labels_mapua14_v1.json"
    atomic_json(labels_path, labels_payload)
    parity = export_and_parity(final_model, x_test, tf_probabilities, bundle)
    parity["label_sha256"] = sha256_file(labels_path)
    if not parity["passed"]:
        raise RuntimeError(f"TFLite parity failed: {parity}")
    predicted = np.argmax(tf_probabilities, axis=1)
    correct = np.flatnonzero(predicted == y_test)
    golden_index = int(correct[np.argmax(np.max(tf_probabilities[correct], axis=1))]) if len(correct) else 0
    golden_path = bundle / "golden_fullsign225_window_f32.bin"
    golden_path.write_bytes(x_test[golden_index].astype("<f4").tobytes())
    golden_metadata = {
        "shape": [1, length, 225], "dtype": "float32_little_endian", "expected_index": int(predicted[golden_index]),
        "expected_label": config["labels"][int(predicted[golden_index])], "expected_probabilities": tf_probabilities[golden_index].astype(float).tolist(),
        "raw_window_sha256": sha256_file(golden_path),
    }
    atomic_json(bundle / "golden_fullsign225_window_expected.json", golden_metadata)
    runtime_manifest = {
        "profile_id": "MAPUA14_RESCUE_V1", "status": "experimental_developer_diagnostic_only", "android_default_changed": False,
        "feature_version": config["feature_contract"]["version"], "input_shape": [1, length, 225], "output_shape": [1, 14],
        "coordinate_orientation": "canonical_unmirrored", "anatomical_slot_swap": False,
        "model_file": Path(parity["model_path"]).name, "model_sha256": parity["model_sha256"],
        "labels_file": labels_path.name, "labels_sha256": parity["label_sha256"], "tflite_parity": parity,
    }
    atomic_json(bundle / "runtime_manifest.json", runtime_manifest)
    report = {
        "experiment_id": config["experiment_id"], "completed_utc": datetime.now(timezone.utc).isoformat(), "contract": contract,
        "metric_scope": config["metric_scope"], "signer_independent_claim": False,
        "pass_only_video_count": len(rows), "class_counts": dict(Counter(row["source_label"] for row in rows)),
        "development_count": len(development_rows), "sealed_test_count": len(test_rows),
        "candidate_results": candidate_results, "winner": winner, "winner_parameter_count": int(final_model.count_params()),
        "final_retrain_epochs": final_epochs, "final_training_seconds": final_training_seconds,
        "exploratory_clip_level_test_metrics": test_metrics, "sealed_test_evaluation_count": 1,
        "tflite_parity": parity, "external_bundle": str(bundle), "external_keras_model": str(final_model_path),
        "android_profile_status": "NOT_YET_ADDED", "samsung_test_status": "NOT_YET_TESTED",
    }
    atomic_json(EXTERNAL_REPORT_PATH, report)
    atomic_json(REPO_REPORT_PATH, report)
    print(json.dumps(report, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
