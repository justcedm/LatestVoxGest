"""Final retrain, one-time sealed evaluation, and float32 TFLite export."""

from __future__ import annotations

import hashlib
import importlib.util
import json
import os
import shutil
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
VOCABULARY_PATH = REPO_ROOT / "reports" / "fsl_dual_dataset_reset_v1" / "FINAL_VOCABULARY.json"
PILOT_PATH = REPO_ROOT / "reports" / "fsl_dual_dataset_reset_v1" / "PILOT_CV_RESULTS.json"
REJECTION_PATH = REPO_ROOT / "reports" / "fsl_dual_dataset_reset_v1" / "REJECTION_PREPARATION.json"
REPORT_PATH = REPO_ROOT / "reports" / "fsl_dual_dataset_reset_v1" / "FINAL_MODEL_RESULTS.json"
ANDROID_BUNDLE = (
    REPO_ROOT / "android_dry_run" / "app" / "src" / "main" / "assets" /
    "model" / "fsl_practical15_fullsign225_48f_v1"
)
PROTECTED = {
    "android_dry_run/app/src/main/assets/model/fsl_fullsign225_20f_105_v1/"
    "voxgest_fsl_fullsign225_105_float32.tflite":
        "42d040ec2269d437546d327decaaca32839abdd6bb63b2d400063c90630e5d13",
    "android_dry_run/app/src/main/assets/model/fsl_fullsign225_20f_105_v1/"
    "class_labels_fsl105_fullsign225_v1.json":
        "bfa76d96ed10bf97f43ca80bcfcc5badd3e96df7ebe4c0654fc078552da55fb6",
    "android_dry_run/app/src/main/assets/model/mapua14_rescue_v1/"
    "mapua14_fullsign225_float32.tflite":
        "f850c5d414c5c253ef9131bae5a85bb3ed5ad5412abdf9936df510c6ec043dcc",
    "android_dry_run/app/src/main/assets/model/mapua14_rescue_v1/"
    "class_labels_mapua14_v1.json":
        "af398236fd62da6c5bafbe0b60d21bc8a155c48aeb45987090c1b14a20cb9ef0",
    "android_dry_run/app/src/main/assets/model/"
    "voxgest_tcn_fullsign225_manual5_team_v2.tflite":
        "97ee230505e5d6ca82caa4c5ffc604dc431c7e0b2bc95c54cec4bf59545c7633",
    "android_dry_run/app/src/main/assets/model/"
    "class_labels_tcn_fullsign225_manual5_team_v2.json":
        "5fd8265d8f4e4915d6ac8846b0c9ea99bae6e079197c4d4a158bf8d3aa85fd49",
}


def load_pilot() -> Any:
    path = REPO_ROOT / "scripts_ml" / "97_pilot_cv_fsl_practical15.py"
    spec = importlib.util.spec_from_file_location("practical15_pilot_helpers", path)
    if spec is None or spec.loader is None:
        raise RuntimeError("cannot load pilot helpers")
    module = importlib.util.module_from_spec(spec)
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)
    return module


pilot = load_pilot()


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


def verify_protected() -> dict[str, str]:
    evidence = {}
    for relative, expected in PROTECTED.items():
        actual = sha256_file(REPO_ROOT / relative)
        if actual != expected:
            raise RuntimeError(f"protected artifact changed: {relative} {actual}")
        evidence[relative] = actual
    return evidence


def export_tflite(
    model: tf.keras.Model,
    x_test: np.ndarray,
    tf_probabilities: np.ndarray,
    bundle: Path,
) -> dict[str, Any]:
    converter = tf.lite.TFLiteConverter.from_keras_model(model)
    content = converter.convert()
    model_path = bundle / "fsl_practical15_fullsign225_float32.tflite"
    model_path.write_bytes(content)
    interpreter = tf.lite.Interpreter(model_path=str(model_path))
    interpreter.allocate_tensors()
    input_detail = interpreter.get_input_details()[0]
    output_detail = interpreter.get_output_details()[0]
    tflite_probabilities = np.empty_like(tf_probabilities)
    latencies_ms = []
    for index, value in enumerate(x_test):
        interpreter.set_tensor(input_detail["index"], value[np.newaxis].astype(np.float32))
        started = time.perf_counter()
        interpreter.invoke()
        latencies_ms.append((time.perf_counter() - started) * 1000.0)
        tflite_probabilities[index] = interpreter.get_tensor(output_detail["index"])[0]
    agreement = float(np.mean(
        np.argmax(tf_probabilities, axis=1) == np.argmax(tflite_probabilities, axis=1)
    ))
    maximum = float(np.max(np.abs(tf_probabilities - tflite_probabilities)))
    return {
        "passed": agreement == 1.0 and maximum <= 1e-5,
        "tf_tflite_top1_agreement": agreement,
        "maximum_probability_difference": maximum,
        "required_top1_agreement": 1.0,
        "maximum_allowed_probability_difference": 1e-5,
        "input_shape": input_detail["shape"].astype(int).tolist(),
        "output_shape": output_detail["shape"].astype(int).tolist(),
        "input_dtype": str(input_detail["dtype"]),
        "output_dtype": str(output_detail["dtype"]),
        "model_file": model_path.name,
        "model_sha256": sha256_file(model_path),
        "model_size_bytes": model_path.stat().st_size,
        "desktop_tflite_latency_ms_median": float(np.median(latencies_ms)),
        "desktop_tflite_latency_ms_p95": float(np.percentile(latencies_ms, 95)),
    }


def copy_bundle(bundle: Path) -> None:
    expected = {
        "fsl_practical15_fullsign225_float32.tflite",
        "class_labels_fsl_practical15_v1.json",
        "golden_fullsign225_window_f32.bin",
        "golden_fullsign225_window_expected.json",
        "runtime_manifest.json",
    }
    ANDROID_BUNDLE.mkdir(parents=True, exist_ok=True)
    unexpected = {path.name for path in ANDROID_BUNDLE.iterdir()} - expected
    if unexpected:
        raise RuntimeError(f"unexpected existing Android bundle files: {unexpected}")
    for name in expected:
        shutil.copy2(bundle / name, ANDROID_BUNDLE / name)


def main() -> int:
    protected_before = verify_protected()
    training_root = pilot.required_root("VOXGEST_TRAINING_ROOT")
    config = json.loads(CONFIG_PATH.read_text(encoding="utf-8"))
    vocabulary = json.loads(VOCABULARY_PATH.read_text(encoding="utf-8"))
    pilot_report = json.loads(PILOT_PATH.read_text(encoding="utf-8"))
    rejection = json.loads(REJECTION_PATH.read_text(encoding="utf-8"))
    if not vocabulary.get("frozen") or vocabulary["labels"] != config["labels"]:
        raise RuntimeError("final vocabulary is not frozen or does not match config")
    if pilot_report.get("sealed_test_accessed") is not False:
        raise RuntimeError("pilot selection improperly accessed the sealed test")
    rows = pilot.join_rows(config, training_root)
    development = [row for row in rows if row["partition"] == "development"]
    sealed_rows = [row for row in rows if row["partition"] == "sealed_test"]
    final_epochs = max(
        1,
        int(round(np.median([fold["best_epoch"] for fold in pilot_report["folds"]]))),
    )
    experiment_root = training_root / config["output"]["experiment_root"]
    final_root = experiment_root / "final"
    final_root.mkdir(parents=True, exist_ok=True)
    contract = {
        "config_sha256": sha256_file(CONFIG_PATH),
        "split_sha256": sha256_file(SPLIT_PATH),
        "vocabulary_sha256": sha256_file(VOCABULARY_PATH),
        "pilot_report_sha256": sha256_file(PILOT_PATH),
        "architecture": "RD-TCN48",
        "tensorflow_version": tf.__version__,
        "numpy_version": np.__version__,
    }
    if REPORT_PATH.is_file():
        existing = json.loads(REPORT_PATH.read_text(encoding="utf-8"))
        if existing.get("contract") == contract:
            print(json.dumps(existing, indent=2))
            return 0

    x_development, y_development = pilot.helpers.load_features(development, 48)
    weights = pilot.class_weights(y_development, len(config["labels"]))
    seed = int(config["random_seed"]) + 9001
    pilot.helpers.set_seeds(seed)
    x_augmented, y_augmented = pilot.helpers.augment_training(
        x_development, y_development, config, seed
    )
    tf.keras.backend.clear_session()
    model = pilot.helpers.build_model(
        "RD-TCN", 48, len(config["labels"]), config["training"]["learning_rate"]
    )
    if model.count_params() >= int(config["training"]["parameter_budget"]):
        raise RuntimeError(f"parameter budget exceeded: {model.count_params()}")
    started = time.perf_counter()
    history = model.fit(
        x_augmented,
        y_augmented,
        epochs=final_epochs,
        batch_size=config["training"]["batch_size"],
        class_weight=weights,
        verbose=2,
        shuffle=True,
    )
    final_training_seconds = time.perf_counter() - started
    keras_path = final_root / "fsl_practical15_rdtcn48.keras"
    model.save(keras_path)

    x_test, y_test = pilot.helpers.load_features(sealed_rows, 48)
    contract_digest = hashlib.sha256(json.dumps(contract, sort_keys=True).encode()).hexdigest()
    sealed_once = final_root / "sealed_test_evaluation_once.npz"
    if sealed_once.is_file():
        with np.load(sealed_once, allow_pickle=False) as archive:
            if str(archive["contract_digest"].item()) != contract_digest:
                raise RuntimeError("sealed evaluation belongs to another contract")
            stored_y = archive["y_true"]
            tf_probabilities = archive["tf_probabilities"]
        if not np.array_equal(stored_y, y_test):
            raise RuntimeError("sealed labels changed")
    else:
        tf_probabilities = model.predict(x_test, batch_size=32, verbose=0).astype(np.float32)
        with sealed_once.open("wb") as stream:
            np.savez_compressed(
                stream,
                y_true=y_test,
                tf_probabilities=tf_probabilities,
                contract_digest=contract_digest,
            )
    test_metrics = pilot.helpers.metric_report(y_test, tf_probabilities, config["labels"])

    bundle = final_root / config["output"]["bundle_id"]
    bundle.mkdir(parents=True, exist_ok=True)
    labels_path = bundle / "class_labels_fsl_practical15_v1.json"
    atomic_json(labels_path, {
        "profile_id": config["output"]["profile_id"],
        "class_count": len(config["labels"]),
        "feature_version": config["feature_contract"]["version"],
        "temporal_length": 48,
        "classes": [
            {"index": index, "id": label, **config["presentation"][label]}
            for index, label in enumerate(config["labels"])
        ],
    })
    parity = export_tflite(model, x_test, tf_probabilities, bundle)
    parity["labels_sha256"] = sha256_file(labels_path)
    parity["config_sha256"] = sha256_file(CONFIG_PATH)
    if not parity["passed"]:
        raise RuntimeError(f"TFLite parity failed: {parity}")

    predicted = np.argmax(tf_probabilities, axis=1)
    correct = np.flatnonzero(predicted == y_test)
    golden_index = (
        int(correct[np.argmax(np.max(tf_probabilities[correct], axis=1))])
        if len(correct) else 0
    )
    golden_path = bundle / "golden_fullsign225_window_f32.bin"
    golden_path.write_bytes(x_test[golden_index].astype("<f4").tobytes())
    atomic_json(bundle / "golden_fullsign225_window_expected.json", {
        "shape": [1, 48, 225],
        "dtype": "float32_little_endian",
        "expected_index": int(predicted[golden_index]),
        "expected_label": config["labels"][int(predicted[golden_index])],
        "expected_probabilities": tf_probabilities[golden_index].astype(float).tolist(),
        "raw_window_sha256": sha256_file(golden_path),
    })
    selected_gate = rejection["selected_development_point"]
    atomic_json(bundle / "runtime_manifest.json", {
        "profile_id": config["output"]["profile_id"],
        "bundle_id": config["output"]["bundle_id"],
        "dataset_profile": vocabulary["dataset_profile"],
        "status": "experimental_non_default_requires_samsung_qualification",
        "android_default_changed": False,
        "feature_version": config["feature_contract"]["version"],
        "input_shape": [1, 48, 225],
        "output_shape": [1, len(config["labels"])],
        "coordinate_orientation": "canonical_unmirrored",
        "anatomical_slot_swap": False,
        "temporal_contract": "complete_event_resample48",
        "model_file": parity["model_file"],
        "model_sha256": parity["model_sha256"],
        "labels_file": labels_path.name,
        "labels_sha256": parity["labels_sha256"],
        "config_sha256": parity["config_sha256"],
        "tflite_parity": parity,
        "development_gate_preparation": {
            "minimum_confidence": selected_gate["minimum_confidence"],
            "minimum_margin": selected_gate["minimum_margin"],
            "minimum_trajectory_motion_mean_l2": 0.02,
            "wrong_accepted_in_development": selected_gate["wrong_accepted"],
            "live_approved": False,
        },
    })
    copy_bundle(bundle)
    protected_after = verify_protected()
    if protected_before != protected_after:
        raise RuntimeError("a protected rollback artifact changed")

    report = {
        "experiment_id": config["experiment_id"],
        "completed_utc": datetime.now(timezone.utc).isoformat(),
        "contract": contract,
        "metric_scope": config["metric_scope"],
        "signer_independent_claim": False,
        "dataset_profile": vocabulary["dataset_profile"],
        "class_count": len(config["labels"]),
        "labels": config["labels"],
        "pass_only_clip_count": len(rows),
        "class_counts": dict(Counter(row["source_label"] for row in rows)),
        "development_count": len(development),
        "sealed_test_count": len(sealed_rows),
        "architecture_candidates_trained": ["RD-TCN48"],
        "candidate_count": 1,
        "final_retrain_epochs": final_epochs,
        "final_parameter_count": int(model.count_params()),
        "final_training_seconds": final_training_seconds,
        "training_accuracy_last_epoch": float(history.history["accuracy"][-1]),
        "exploratory_clip_level_test_metrics": test_metrics,
        "source_wise_metrics": {"MAPUA_TRANSACTIONAL_FSL": test_metrics},
        "sealed_test_evaluation_count": 1,
        "tflite_parity": parity,
        "development_rejection_preparation": selected_gate,
        "android_profile_status": "BUNDLE_ADDED_NOT_DEFAULT",
        "samsung_test_status": "NOT_YET_TESTED",
        "protected_standard_fsl105_preserved": True,
        "protected_mapua14_preserved": True,
        "protected_demo_lane_preserved": True,
    }
    atomic_json(REPORT_PATH, report)
    print(json.dumps(report, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
