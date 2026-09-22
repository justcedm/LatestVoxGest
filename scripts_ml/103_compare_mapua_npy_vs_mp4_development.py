"""Matched development-only RD-TCN48 comparison for Mapua MP4 vs original NPY.

Both pipelines use the same frozen development rows/folds, seeds, model helper,
augmentation, class weighting, optimizer, batch size, and stopping policy.
No sealed feature archive is opened and no sealed classifier inference occurs.
"""

from __future__ import annotations

import csv
import hashlib
import importlib.util
import io
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
BASE_CONFIG_PATH = REPO_ROOT / "training_configs" / "fsl_practical15_mapua_v1.json"
NPY_CONFIG_PATH = REPO_ROOT / "training_configs" / "mapua_npy_canonical_v1.json"
REPORT_ROOT = REPO_ROOT / "reports" / "fsl_dual_dataset_reset_v1"
SPLIT_PATH = REPORT_ROOT / "SPLIT_MANIFEST.csv"
REPORT_PATH = REPORT_ROOT / "MAPUA_NPY_VS_MP4_DEVELOPMENT_COMPARISON.md"
CONFUSION_PATH = REPORT_ROOT / "MAPUA_NPY_VS_MP4_CONFUSION_MATRIX.csv"


def load_helpers() -> Any:
    path = REPO_ROOT / "scripts_ml" / "95_train_mapua14_rescue.py"
    spec = importlib.util.spec_from_file_location("matched_rdtcn_helpers", path)
    if spec is None or spec.loader is None:
        raise RuntimeError("cannot load validated RD-TCN helpers")
    module = importlib.util.module_from_spec(spec)
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)
    return module


helpers = load_helpers()


def required_root(name: str) -> Path:
    value = os.environ.get(name, "").strip()
    if not value:
        raise RuntimeError(f"{name} must point to an approved external safe-C root")
    root = Path(value).resolve()
    if not root.is_dir():
        raise RuntimeError(f"{name} does not exist: {root}")
    return root


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def load_csv(path: Path) -> list[dict[str, str]]:
    with path.open("r", encoding="utf-8-sig", newline="") as stream:
        return list(csv.DictReader(stream))


def atomic_text(path: Path, content: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_suffix(path.suffix + ".tmp")
    temporary.write_text(content, encoding="utf-8", newline="")
    temporary.replace(path)


def atomic_json(path: Path, payload: Any) -> None:
    atomic_text(path, json.dumps(payload, indent=2, sort_keys=True) + "\n")


def class_weights(y: np.ndarray, class_count: int) -> dict[int, float]:
    counts = Counter(int(value) for value in y)
    if set(counts) != set(range(class_count)):
        raise RuntimeError(f"training fold lacks a class: {counts}")
    total = float(len(y))
    return {index: total / (class_count * counts[index]) for index in range(class_count)}


def build_development_rows(
    pipeline: str, frozen_development: list[dict[str, str]], training_root: Path,
    base_config: dict[str, Any], npy_config: dict[str, Any],
) -> tuple[list[dict[str, str]], str]:
    if pipeline == "mp4":
        manifest_path = training_root / base_config["dataset"]["source_feature_manifest"]
        manifest = {row["record_id"]: row for row in load_csv(manifest_path)}
        rows = []
        for frozen in frozen_development:
            source = manifest.get(frozen["record_id"])
            if source is None or source["source_label"] != frozen["source_label"]:
                raise RuntimeError(f"MP4 feature join failure: {frozen['record_id']}")
            if source["feature_sha256"].lower() != frozen["feature_sha256"].lower():
                raise RuntimeError(f"MP4 manifest hash mismatch: {frozen['record_id']}")
            feature_path = Path(source["feature_path"])
            if sha256_file(feature_path).lower() != frozen["feature_sha256"].lower():
                raise RuntimeError(f"MP4 archive hash mismatch: {frozen['record_id']}")
            rows.append({**frozen, "feature_path": str(feature_path)})
        return rows, sha256_file(manifest_path)

    external_root = training_root / npy_config["output"]["external_experiment_root"]
    manifest_path = external_root / npy_config["output"]["feature_manifest"]
    manifest = {row["record_id"]: row for row in load_csv(manifest_path)}
    rows = []
    for frozen in frozen_development:
        source = manifest.get(frozen["record_id"])
        if source is None or source["status"] != "PASS":
            raise RuntimeError(f"NPY feature join failure: {frozen['record_id']}")
        for field in ("source_label", "partition", "development_fold", "group_id"):
            if source[field] != frozen[field]:
                raise RuntimeError(f"NPY frozen assignment mismatch {field}: {frozen['record_id']}")
        feature_path = external_root / Path(source["output_relative_path"])
        if sha256_file(feature_path).lower() != source["output_archive_sha256"].lower():
            raise RuntimeError(f"NPY archive hash mismatch: {frozen['record_id']}")
        with np.load(feature_path, allow_pickle=False) as archive:
            sequence = archive["sequence_48"]
        digest = hashlib.sha256(np.ascontiguousarray(sequence, dtype=np.float32).tobytes()).hexdigest()
        if digest.lower() != source["output_tensor_sha256"].lower():
            raise RuntimeError(f"NPY tensor hash mismatch: {frozen['record_id']}")
        rows.append({**frozen, "feature_path": str(feature_path)})
    return rows, sha256_file(manifest_path)


def train_pipeline(
    pipeline: str, rows: list[dict[str, str]], manifest_sha256: str,
    config: dict[str, Any], experiment_root: Path,
) -> dict[str, Any]:
    fold_root = experiment_root / "development_cv" / pipeline
    contract = {
        "pipeline": pipeline,
        "feature_manifest_sha256": manifest_sha256,
        "config_sha256": sha256_file(BASE_CONFIG_PATH),
        "split_sha256": sha256_file(SPLIT_PATH),
        "tensorflow_version": tf.__version__,
        "numpy_version": np.__version__,
        "architecture": "RD-TCN48",
        "sealed_rows_loaded": False,
        "matched_comparison_version": "mapua_npy_vs_mp4_development_v1",
    }
    fold_reports = []
    all_truth = []
    all_probabilities = []
    folds = int(config["dataset"]["development_folds"])
    for fold in range(folds):
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
                print(f"{pipeline} fold {fold}: cached", flush=True)
                continue

        train_rows = [row for row in rows if int(row["development_fold"]) != fold]
        validation_rows = [row for row in rows if int(row["development_fold"]) == fold]
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
        model = helpers.build_model(
            "RD-TCN", 48, len(config["labels"]), config["training"]["learning_rate"]
        )
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
                monitor="val_loss", patience=4, factor=0.5, min_lr=1e-6, verbose=1
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
        report.update({
            "contract": contract,
            "pipeline": pipeline,
            "fold": fold,
            "seed": seed,
            "best_epoch": int(np.argmin(history.history["val_loss"]) + 1),
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
        print(
            f"{pipeline} fold {fold}: macro_f1={report['macro_f1']:.6f} "
            f"accuracy={report['accuracy']:.6f}", flush=True
        )

    y_true = np.concatenate(all_truth)
    probabilities = np.concatenate(all_probabilities)
    combined = helpers.metric_report(y_true, probabilities, config["labels"])
    return {
        "pipeline": pipeline,
        "contract": contract,
        "folds": fold_reports,
        "development_clip_count": len(rows),
        "mean_fold_macro_f1": float(np.mean([fold["macro_f1"] for fold in fold_reports])),
        "mean_fold_accuracy": float(np.mean([fold["accuracy"] for fold in fold_reports])),
        "combined_out_of_fold_metrics": combined,
        "sealed_test_accessed": False,
    }


def choose_best(mp4: dict[str, Any], npy: dict[str, Any]) -> str:
    def key(report: dict[str, Any]) -> tuple[float, float, float, float]:
        metric = report["combined_out_of_fold_metrics"]
        return (
            float(metric["macro_f1"]),
            float(metric["weakest_class_f1"]),
            -float(metric["expected_calibration_error_10bin"]),
            float(metric["accuracy"]),
        )
    return "MP4_HOLISTIC" if key(mp4) >= key(npy) else "ORIGINAL_NPY"


def confusion_csv(reports: dict[str, dict[str, Any]], labels: list[str]) -> str:
    fields = ["pipeline", "true_label", *labels]
    rows = []
    for pipeline, report in reports.items():
        matrix = report["combined_out_of_fold_metrics"]["confusion_matrix"]
        for index, label in enumerate(labels):
            rows.append({
                "pipeline": pipeline,
                "true_label": label,
                **{target: matrix[index][target_index] for target_index, target in enumerate(labels)},
            })
    output = io.StringIO(newline="")
    writer = csv.DictWriter(output, fieldnames=fields, lineterminator="\n")
    writer.writeheader()
    writer.writerows(rows)
    return output.getvalue()


def confusion_text(items: list[dict[str, Any]], limit: int = 10) -> str:
    if not items:
        return "NONE"
    return "; ".join(
        f"{item['true']}->{item['predicted']}:{item['count']}" for item in items[:limit]
    )


def write_report(
    reports: dict[str, dict[str, Any]], labels: list[str], best: str,
) -> None:
    mp4 = reports["MP4_HOLISTIC"]["combined_out_of_fold_metrics"]
    npy = reports["ORIGINAL_NPY"]["combined_out_of_fold_metrics"]
    lines = [
        "# Mapua NPY vs MP4 development comparison", "",
        f"Generated UTC: {datetime.now(timezone.utc).isoformat()}", "",
        "## Fixed experimental contract", "",
        "- Architecture: RD-TCN48, 125391 parameters.",
        "- Same 334 frozen development clips and four source-group-preserving folds.",
        "- Same seeds, augmentation, class weights, batch size, optimizer, learning rate, and early stopping.",
        "- Metric is combined out-of-fold development performance; signer independence is not claimed.",
        "- Frozen split metadata was read only to select development rows; sealed feature files, predictions, and metrics were not opened.",
        "- No Android model or profile was changed.", "",
        "## Required decision fields", "",
        f"MP4_PIPELINE_CV_MACRO_F1={mp4['macro_f1']:.9f}",
        f"NPY_PIPELINE_CV_MACRO_F1={npy['macro_f1']:.9f}",
        f"MP4_WEAKEST_CLASS_F1={mp4['weakest_class_f1']:.9f}",
        f"NPY_WEAKEST_CLASS_F1={npy['weakest_class_f1']:.9f}",
        f"MP4_TOP_CONFUSIONS={confusion_text(mp4['top_confusions'])}",
        f"NPY_TOP_CONFUSIONS={confusion_text(npy['top_confusions'])}",
        f"BEST_DEVELOPMENT_PIPELINE={best}", "",
        "## Aggregate development metrics", "",
        "| Pipeline | Accuracy | Macro precision | Macro recall | Macro F1 | Weakest F1 | ECE-10 | NLL |",
        "|---|---:|---:|---:|---:|---:|---:|---:|",
        f"| MP4/Holistic | {mp4['accuracy']:.9f} | {mp4['macro_precision']:.9f} | {mp4['macro_recall']:.9f} | {mp4['macro_f1']:.9f} | {mp4['weakest_class_f1']:.9f} | {mp4['expected_calibration_error_10bin']:.9f} | {mp4['negative_log_likelihood']:.9f} |",
        f"| Original NPY | {npy['accuracy']:.9f} | {npy['macro_precision']:.9f} | {npy['macro_recall']:.9f} | {npy['macro_f1']:.9f} | {npy['weakest_class_f1']:.9f} | {npy['expected_calibration_error_10bin']:.9f} | {npy['negative_log_likelihood']:.9f} |",
        "", "## Fold evidence", "",
        "| Fold | MP4 macro F1 | NPY macro F1 | MP4 accuracy | NPY accuracy |",
        "|---:|---:|---:|---:|---:|",
    ]
    for fold_index in range(len(reports["MP4_HOLISTIC"]["folds"])):
        a_fold = reports["MP4_HOLISTIC"]["folds"][fold_index]
        b_fold = reports["ORIGINAL_NPY"]["folds"][fold_index]
        lines.append(
            f"| {fold_index} | {a_fold['macro_f1']:.9f} | {b_fold['macro_f1']:.9f} | "
            f"{a_fold['accuracy']:.9f} | {b_fold['accuracy']:.9f} |"
        )
    lines += [
        "", "## Per-class F1", "",
        "| Class | MP4/Holistic | Original NPY | Delta NPY-MP4 |",
        "|---|---:|---:|---:|",
    ]
    for label in labels:
        a = float(mp4["per_class"][label]["f1"])
        b = float(npy["per_class"][label]["f1"])
        lines.append(f"| {label} | {a:.9f} | {b:.9f} | {b-a:+.9f} |")
    lines += [
        "", "## Confusion review", "",
        f"- MP4/Holistic: {confusion_text(mp4['top_confusions'], 20)}.",
        f"- Original NPY: {confusion_text(npy['top_confusions'], 20)}.",
        "- Full 15x15 matrices are stored in `MAPUA_NPY_VS_MP4_CONFUSION_MATRIX.csv`.",
        "- Required pair review: CARD/COIN, CASH/PROBLEM, NO/YES, and HOW_MANY/HOW_MUCH is represented explicitly in those matrices even when a cell is zero.",
        "", "## Decision boundary", "",
        f"`{best}` is the stronger offline development representation under the fixed experiment.",
        "This is not evidence that it transfers better to Android MediaPipe Tasks. The next decision",
        "requires Samsung pre-normalization landmark capture and the planned three-domain comparison.",
        "The deployed FSL_PRACTICAL15_V1 model remains unchanged.",
    ]
    atomic_text(REPORT_PATH, "\n".join(lines) + "\n")


def main() -> int:
    config = json.loads(BASE_CONFIG_PATH.read_text(encoding="utf-8"))
    npy_config = json.loads(NPY_CONFIG_PATH.read_text(encoding="utf-8"))
    training_root = required_root("VOXGEST_TRAINING_ROOT")
    frozen = load_csv(SPLIT_PATH)
    development = [row for row in frozen if row["partition"] == "development"]
    if len(development) != int(npy_config["selection"]["development_clip_count"]):
        raise RuntimeError(f"development count changed: {len(development)}")
    if any(not row["development_fold"] for row in development):
        raise RuntimeError("development row lacks frozen fold")

    mp4_rows, mp4_manifest_sha = build_development_rows(
        "mp4", development, training_root, config, npy_config
    )
    npy_rows, npy_manifest_sha = build_development_rows(
        "npy", development, training_root, config, npy_config
    )
    for a, b in zip(mp4_rows, npy_rows):
        for field in ("record_id", "source_label", "class_index", "development_fold", "group_id"):
            if a[field] != b[field]:
                raise RuntimeError(f"pipeline row mismatch {field}: {a['record_id']}")

    experiment_root = training_root / npy_config["output"]["external_experiment_root"]
    reports = {
        "MP4_HOLISTIC": train_pipeline("mp4", mp4_rows, mp4_manifest_sha, config, experiment_root),
        "ORIGINAL_NPY": train_pipeline("npy", npy_rows, npy_manifest_sha, config, experiment_root),
    }
    best = choose_best(reports["MP4_HOLISTIC"], reports["ORIGINAL_NPY"])
    result = {
        "completed_utc": datetime.now(timezone.utc).isoformat(),
        "scope": "MATCHED_FOUR_FOLD_DEVELOPMENT_ONLY",
        "architecture": "RD-TCN48",
        "development_clip_count": len(development),
        "sealed_test_accessed": False,
        "best_development_pipeline": best,
        "pipelines": reports,
    }
    atomic_json(experiment_root / "development_comparison_results.json", result)
    atomic_text(CONFUSION_PATH, confusion_csv(reports, config["labels"]))
    write_report(reports, config["labels"], best)
    print(json.dumps({
        "MP4_PIPELINE_CV_MACRO_F1": reports["MP4_HOLISTIC"]["combined_out_of_fold_metrics"]["macro_f1"],
        "NPY_PIPELINE_CV_MACRO_F1": reports["ORIGINAL_NPY"]["combined_out_of_fold_metrics"]["macro_f1"],
        "MP4_WEAKEST_CLASS_F1": reports["MP4_HOLISTIC"]["combined_out_of_fold_metrics"]["weakest_class_f1"],
        "NPY_WEAKEST_CLASS_F1": reports["ORIGINAL_NPY"]["combined_out_of_fold_metrics"]["weakest_class_f1"],
        "BEST_DEVELOPMENT_PIPELINE": best,
        "SEALED_TEST_TOUCHED": False,
    }, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
