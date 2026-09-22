"""Four-fold development-only evidence for one practical-FSL RD-TCN48."""

from __future__ import annotations

import csv
import hashlib
import importlib.util
import json
import os
import random
import sys
import time
from collections import Counter
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

os.environ.setdefault("TF_DETERMINISTIC_OPS", "1")
os.environ.setdefault("TF_CPP_MIN_LOG_LEVEL", "2")

import numpy as np
import tensorflow as tf

REPO_ROOT = Path(__file__).resolve().parents[1]
CONFIG_PATH = REPO_ROOT / "training_configs" / "fsl_practical15_mapua_v1.json"
SPLIT_PATH = REPO_ROOT / "reports" / "fsl_dual_dataset_reset_v1" / "SPLIT_MANIFEST.csv"
REPORT_PATH = REPO_ROOT / "reports" / "fsl_dual_dataset_reset_v1" / "PILOT_CV_RESULTS.json"
REJECTION_PATH = REPO_ROOT / "reports" / "fsl_dual_dataset_reset_v1" / "REJECTION_PREPARATION.json"


def load_helpers() -> Any:
    path = REPO_ROOT / "scripts_ml" / "95_train_mapua14_rescue.py"
    spec = importlib.util.spec_from_file_location("validated_rdtcn_helpers", path)
    if spec is None or spec.loader is None:
        raise RuntimeError("cannot load validated RD-TCN helpers")
    module = importlib.util.module_from_spec(spec)
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)
    return module


helpers = load_helpers()


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


def load_csv(path: Path) -> list[dict[str, str]]:
    with path.open("r", encoding="utf-8-sig", newline="") as stream:
        return list(csv.DictReader(stream))


def required_root(name: str) -> Path:
    value = os.environ.get(name, "").strip()
    if not value:
        raise RuntimeError(f"{name} must point to the approved external safe-C root")
    root = Path(value).resolve()
    if not root.is_dir():
        raise RuntimeError(f"{name} does not exist: {root}")
    return root


def join_rows(config: dict[str, Any], training_root: Path) -> list[dict[str, str]]:
    frozen = load_csv(SPLIT_PATH)
    manifest_path = training_root / config["dataset"]["source_feature_manifest"]
    source = {row["record_id"]: row for row in load_csv(manifest_path)}
    rows = []
    for row in frozen:
        cached = source.get(row["record_id"])
        if cached is None:
            raise RuntimeError(f"feature missing: {row['record_id']}")
        if cached["source_label"] != row["source_label"]:
            raise RuntimeError(f"label mismatch: {row['record_id']}")
        if cached["feature_sha256"].lower() != row["feature_sha256"].lower():
            raise RuntimeError(f"manifest hash mismatch: {row['record_id']}")
        path = Path(cached["feature_path"])
        if sha256_file(path).lower() != row["feature_sha256"].lower():
            raise RuntimeError(f"feature hash mismatch: {row['record_id']}")
        rows.append({**row, "feature_path": str(path)})
    if len(rows) != int(config["dataset"]["expected_clip_count"]):
        raise RuntimeError(f"unexpected selected count: {len(rows)}")
    return rows


def class_weights(y: np.ndarray, class_count: int) -> dict[int, float]:
    counts = Counter(int(value) for value in y)
    if set(counts) != set(range(class_count)):
        raise RuntimeError(f"training fold lacks a class: {counts}")
    total = float(len(y))
    return {index: total / (class_count * counts[index]) for index in range(class_count)}


def rejection_analysis(y_true: np.ndarray, probabilities: np.ndarray) -> dict[str, Any]:
    order = np.argsort(-probabilities, axis=1)
    predicted = order[:, 0]
    confidence = probabilities[np.arange(len(probabilities)), predicted]
    margin = confidence - probabilities[np.arange(len(probabilities)), order[:, 1]]
    correct = predicted == y_true
    candidates = []
    for minimum_confidence in np.arange(0.50, 0.951, 0.05):
        for minimum_margin in np.arange(0.05, 0.501, 0.05):
            accepted = (confidence >= minimum_confidence) & (margin >= minimum_margin)
            accepted_count = int(accepted.sum())
            wrong = int((accepted & ~correct).sum())
            right = int((accepted & correct).sum())
            candidates.append({
                "minimum_confidence": round(float(minimum_confidence), 2),
                "minimum_margin": round(float(minimum_margin), 2),
                "accepted_count": accepted_count,
                "accepted_correct": right,
                "wrong_accepted": wrong,
                "coverage": float(accepted.mean()),
                "accepted_precision": float(right / accepted_count) if accepted_count else 0.0,
            })
    zero_wrong = [item for item in candidates if item["wrong_accepted"] == 0]
    if zero_wrong:
        selected = max(
            zero_wrong,
            key=lambda item: (
                item["accepted_correct"],
                item["coverage"],
                -item["minimum_confidence"],
                -item["minimum_margin"],
            ),
        )
        selection_status = "ZERO_WRONG_POINT_FOUND"
    else:
        selected = min(
            candidates,
            key=lambda item: (
                item["wrong_accepted"],
                -item["accepted_correct"],
                -item["coverage"],
                item["minimum_confidence"],
                item["minimum_margin"],
            ),
        )
        selection_status = "NO_ZERO_WRONG_POINT_IN_GRID_MINIMUM_ERROR_REPORTED"
    return {
        "scope": "development_out_of_fold_only",
        "thresholds_are_prepared_not_live_approved": True,
        "raw_correct_rate": float(correct.mean()),
        "correct_confidence_p05": float(np.percentile(confidence[correct], 5)),
        "correct_margin_p05": float(np.percentile(margin[correct], 5)),
        "wrong_confidence_max": float(confidence[~correct].max()) if (~correct).any() else None,
        "wrong_margin_max": float(margin[~correct].max()) if (~correct).any() else None,
        "selection_status": selection_status,
        "selected_development_point": selected,
        "grid": candidates,
        "additional_required_live_signals": [
            "complete_event",
            "tracking_quality",
            "duration_bounds",
            "duplicate_release",
        ],
    }


def main() -> int:
    training_root = required_root("VOXGEST_TRAINING_ROOT")
    config = json.loads(CONFIG_PATH.read_text(encoding="utf-8"))
    if config["training"]["architecture"] != "RD-TCN" or int(config["training"]["temporal_length"]) != 48:
        raise RuntimeError("only the authorized RD-TCN48 pilot is permitted")
    rows = join_rows(config, training_root)
    development = [row for row in rows if row["partition"] == "development"]
    sealed = [row for row in rows if row["partition"] == "sealed_test"]
    if len(development) != 334 or len(sealed) != 60:
        raise RuntimeError("frozen partition counts changed")
    experiment_root = training_root / config["output"]["experiment_root"]
    fold_root = experiment_root / "pilot_cv"
    contract = {
        "config_sha256": sha256_file(CONFIG_PATH),
        "split_sha256": sha256_file(SPLIT_PATH),
        "tensorflow_version": tf.__version__,
        "numpy_version": np.__version__,
        "architecture": "RD-TCN48",
        "candidate_count": 1,
        "sealed_rows_loaded": False,
    }
    fold_reports = []
    all_truth = []
    all_probabilities = []
    for fold in range(int(config["dataset"]["development_folds"])):
        output = fold_root / f"fold_{fold}"
        metrics_path = output / "metrics.json"
        predictions_path = output / "validation_predictions.npz"
        model_path = output / "best.keras"
        if metrics_path.is_file() and predictions_path.is_file() and model_path.is_file():
            cached = json.loads(metrics_path.read_text(encoding="utf-8"))
            if cached.get("contract") == contract:
                with np.load(predictions_path, allow_pickle=False) as archive:
                    all_truth.append(archive["y_true"])
                    all_probabilities.append(archive["probabilities"])
                fold_reports.append(cached)
                print(f"fold {fold}: cached", flush=True)
                continue
        train_rows = [row for row in development if int(row["development_fold"]) != fold]
        validation_rows = [row for row in development if int(row["development_fold"]) == fold]
        x_train, y_train = helpers.load_features(train_rows, 48)
        x_validation, y_validation = helpers.load_features(validation_rows, 48)
        weights = class_weights(y_train, len(config["labels"]))
        seed = int(config["random_seed"]) + fold * 1009
        os.environ["PYTHONHASHSEED"] = str(seed)
        random.seed(seed)
        np.random.seed(seed)
        tf.random.set_seed(seed)
        x_train, y_train = helpers.augment_training(x_train, y_train, config, seed)
        tf.keras.backend.clear_session()
        model = helpers.build_model("RD-TCN", 48, len(config["labels"]), config["training"]["learning_rate"])
        if model.count_params() >= int(config["training"]["parameter_budget"]):
            raise RuntimeError(f"parameter budget exceeded: {model.count_params()}")
        callbacks = [
            tf.keras.callbacks.EarlyStopping(
                monitor="val_loss",
                patience=config["training"]["early_stopping_patience"],
                restore_best_weights=True,
                verbose=1,
            ),
            tf.keras.callbacks.ReduceLROnPlateau(
                monitor="val_loss",
                patience=4,
                factor=0.5,
                min_lr=1e-6,
                verbose=1,
            ),
        ]
        started = time.perf_counter()
        history = model.fit(
            x_train,
            y_train,
            validation_data=(x_validation, y_validation),
            epochs=config["training"]["maximum_epochs"],
            batch_size=config["training"]["batch_size"],
            callbacks=callbacks,
            class_weight=weights,
            verbose=2,
            shuffle=True,
        )
        probabilities = model.predict(x_validation, batch_size=32, verbose=0).astype(np.float32)
        report = helpers.metric_report(y_validation, probabilities, config["labels"])
        best_epoch = int(np.argmin(history.history["val_loss"]) + 1)
        report.update({
            "contract": contract,
            "fold": fold,
            "seed": seed,
            "best_epoch": best_epoch,
            "epochs_run": len(history.history["loss"]),
            "training_seconds": time.perf_counter() - started,
            "parameter_count": int(model.count_params()),
            "training_source_count_before_augmentation": len(train_rows),
            "training_tensor_count_after_augmentation": len(y_train),
            "validation_source_count": len(validation_rows),
            "class_weight": {str(key): value for key, value in weights.items()},
        })
        output.mkdir(parents=True, exist_ok=True)
        model.save(model_path)
        with predictions_path.open("wb") as stream:
            np.savez_compressed(stream, y_true=y_validation, probabilities=probabilities)
        atomic_json(metrics_path, report)
        fold_reports.append(report)
        all_truth.append(y_validation)
        all_probabilities.append(probabilities)

    y_true = np.concatenate(all_truth)
    probabilities = np.concatenate(all_probabilities)
    combined = helpers.metric_report(y_true, probabilities, config["labels"])
    rejection = rejection_analysis(y_true, probabilities)
    report = {
        "experiment_id": config["experiment_id"],
        "completed_utc": datetime.now(timezone.utc).isoformat(),
        "contract": contract,
        "candidate_count": 1,
        "architecture": "RD-TCN48",
        "metric_scope": config["metric_scope"],
        "signer_independent_claim": False,
        "development_clip_count": len(development),
        "sealed_clip_count_untouched": len(sealed),
        "folds": fold_reports,
        "mean_fold_macro_f1": float(np.mean([fold["macro_f1"] for fold in fold_reports])),
        "mean_fold_accuracy": float(np.mean([fold["accuracy"] for fold in fold_reports])),
        "mean_fold_weakest_class_f1": float(np.mean([fold["weakest_class_f1"] for fold in fold_reports])),
        "combined_out_of_fold_metrics": combined,
        "rejection_preparation": rejection,
        "sealed_test_accessed": False,
    }
    atomic_json(REPORT_PATH, report)
    atomic_json(REJECTION_PATH, rejection)
    print(json.dumps({
        "mean_fold_macro_f1": report["mean_fold_macro_f1"],
        "mean_fold_accuracy": report["mean_fold_accuracy"],
        "combined_macro_f1": combined["macro_f1"],
        "combined_accuracy": combined["accuracy"],
        "weakest_class_f1": combined["weakest_class_f1"],
        "top_confusions": combined["top_confusions"][:10],
        "rejection_selection_status": rejection["selection_status"],
        "selected_rejection_point": rejection["selected_development_point"],
        "sealed_test_accessed": False,
    }, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
