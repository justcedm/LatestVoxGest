"""Train exactly one Scenario-15 Mapua9 RD-TCN48 candidate.

Four frozen development folds estimate the single candidate. The final model
is retrained once on all development clips, evaluated once on the sealed split,
exported as float32 TFLite, parity checked, and copied into an isolated Android
asset directory. No model-family search is performed.
"""

from __future__ import annotations

import csv
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
CONFIG_PATH = REPO_ROOT / "training_configs" / "scenario15_mapua9_v1.json"
SPLIT_PATH = REPO_ROOT / "reports" / "scenario15_counter_v1" / "MAPUA9_SPLIT_MANIFEST.csv"
REPO_REPORT_PATH = REPO_ROOT / "reports" / "scenario15_counter_v1" / "MAPUA9_TRAINING_RESULTS.json"
EXPERIMENT_ROOT = Path(r"C:\VOXGEST_TRAINING\SCENARIO15_MAPUA9_V1")
CANDIDATE_ROOT = EXPERIMENT_ROOT / "candidate_rdtcn48"
FINAL_ROOT = EXPERIMENT_ROOT / "final"
EXTERNAL_REPORT_PATH = EXPERIMENT_ROOT / "reports" / "training_results.json"
SEALED_ONCE_PATH = EXPERIMENT_ROOT / "sealed_test_evaluation_once.npz"
ANDROID_BUNDLE = (
    REPO_ROOT
    / "android_dry_run"
    / "app"
    / "src"
    / "main"
    / "assets"
    / "model"
    / "scenario15_mapua9_v1"
)

PROTECTED_ASSETS = {
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
}


def load_legacy_training_helpers() -> Any:
    path = REPO_ROOT / "scripts_ml" / "95_train_mapua14_rescue.py"
    spec = importlib.util.spec_from_file_location("mapua14_training_helpers", path)
    if spec is None or spec.loader is None:
        raise RuntimeError("cannot load validated RD-TCN training helpers")
    module = importlib.util.module_from_spec(spec)
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)
    return module


legacy = load_legacy_training_helpers()


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def atomic_json(path: Path, payload: Any) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_suffix(path.suffix + ".tmp")
    temporary.write_text(
        json.dumps(payload, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )
    temporary.replace(path)


def load_csv(path: Path) -> list[dict[str, str]]:
    with path.open("r", encoding="utf-8-sig", newline="") as stream:
        return list(csv.DictReader(stream))


def verify_protected_assets() -> dict[str, str]:
    actual = {}
    for relative, expected in PROTECTED_ASSETS.items():
        digest = sha256_file(REPO_ROOT / relative)
        if digest != expected:
            raise RuntimeError(f"protected asset changed: {relative} {digest}")
        actual[relative] = digest
    return actual


def join_and_validate_rows(
    config: dict[str, Any],
) -> tuple[list[dict[str, str]], str]:
    split = load_csv(SPLIT_PATH)
    source_path = Path(config["dataset"]["source_feature_manifest"])
    source = {row["record_id"]: row for row in load_csv(source_path)}
    if len(split) != int(config["dataset"]["expected_clip_count"]):
        raise RuntimeError(f"unexpected frozen split count: {len(split)}")
    rows = []
    for frozen in split:
        cached = source.get(frozen["record_id"])
        if cached is None:
            raise RuntimeError(f"cached feature missing: {frozen['record_id']}")
        if cached["source_label"] != frozen["source_label"]:
            raise RuntimeError(f"label drift: {frozen['record_id']}")
        if cached["feature_sha256"].lower() != frozen["feature_sha256"].lower():
            raise RuntimeError(f"feature manifest drift: {frozen['record_id']}")
        feature_path = Path(cached["feature_path"])
        if sha256_file(feature_path).lower() != frozen["feature_sha256"].lower():
            raise RuntimeError(f"feature file drift: {frozen['record_id']}")
        rows.append({**frozen, "feature_path": str(feature_path)})
    return rows, sha256_file(source_path)


def export_and_parity(
    model: tf.keras.Model,
    x_test: np.ndarray,
    tf_probabilities: np.ndarray,
    bundle: Path,
) -> dict[str, Any]:
    converter = tf.lite.TFLiteConverter.from_keras_model(model)
    content = converter.convert()
    model_path = bundle / "scenario15_mapua9_fullsign225_float32.tflite"
    model_path.write_bytes(content)
    interpreter = tf.lite.Interpreter(model_path=str(model_path))
    interpreter.allocate_tensors()
    input_detail = interpreter.get_input_details()[0]
    output_detail = interpreter.get_output_details()[0]
    tflite = np.empty_like(tf_probabilities)
    for index, value in enumerate(x_test):
        interpreter.set_tensor(input_detail["index"], value[np.newaxis].astype(np.float32))
        interpreter.invoke()
        tflite[index] = interpreter.get_tensor(output_detail["index"])[0]
    agreement = float(
        np.mean(np.argmax(tf_probabilities, axis=1) == np.argmax(tflite, axis=1))
    )
    maximum = float(np.max(np.abs(tf_probabilities - tflite)))
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
    }


def copy_bundle_to_android(bundle: Path) -> None:
    ANDROID_BUNDLE.mkdir(parents=True, exist_ok=True)
    expected = {
        "scenario15_mapua9_fullsign225_float32.tflite",
        "class_labels_scenario15_mapua9_v1.json",
        "golden_fullsign225_window_f32.bin",
        "golden_fullsign225_window_expected.json",
        "runtime_manifest.json",
    }
    for name in expected:
        shutil.copy2(bundle / name, ANDROID_BUNDLE / name)
    unexpected = {path.name for path in ANDROID_BUNDLE.iterdir()} - expected
    if unexpected:
        raise RuntimeError(f"unexpected files in Android Mapua9 bundle: {unexpected}")


def main() -> int:
    protected_before = verify_protected_assets()
    config = json.loads(CONFIG_PATH.read_text(encoding="utf-8"))
    if config["training"]["architecture"] != "RD-TCN":
        raise RuntimeError("only the authorized RD-TCN architecture may be trained")
    if int(config["training"]["temporal_length"]) != 48:
        raise RuntimeError("only the authorized 48-frame model may be trained")
    rows, source_manifest_sha = join_and_validate_rows(config)
    contract = {
        "split_manifest_sha256": sha256_file(SPLIT_PATH),
        "training_config_sha256": sha256_file(CONFIG_PATH),
        "source_feature_manifest_sha256": source_manifest_sha,
        "tensorflow_version": tf.__version__,
        "numpy_version": np.__version__,
        "single_candidate": "RD-TCN48",
    }
    if REPO_REPORT_PATH.is_file() and EXTERNAL_REPORT_PATH.is_file():
        existing = json.loads(EXTERNAL_REPORT_PATH.read_text(encoding="utf-8"))
        if existing.get("contract") == contract:
            print(json.dumps(existing, indent=2))
            return 0

    candidate = {"id": "S", "architecture": "RD-TCN", "temporal_length": 48}
    legacy.CANDIDATE_ROOT = CANDIDATE_ROOT
    legacy.set_seeds(int(config["random_seed"]))
    cv_result = legacy.candidate_cv(candidate, rows, config, contract)
    if cv_result["status"] != "PASS":
        raise RuntimeError(f"single authorized candidate failed: {cv_result}")

    final_epochs = max(
        1,
        int(round(np.median([fold["best_epoch"] for fold in cv_result["folds"]]))),
    )
    development_rows = [row for row in rows if row["partition"] == "development"]
    test_rows = [row for row in rows if row["partition"] == "sealed_test"]
    x_development, y_development = legacy.load_features(development_rows, 48)
    seed = int(config["random_seed"]) + 9001
    legacy.set_seeds(seed)
    x_augmented, y_augmented = legacy.augment_training(
        x_development,
        y_development,
        config,
        seed,
    )
    tf.keras.backend.clear_session()
    final_model = legacy.build_model(
        "RD-TCN",
        48,
        len(config["labels"]),
        config["training"]["learning_rate"],
    )
    if final_model.count_params() >= int(config["training"]["parameter_budget"]):
        raise RuntimeError(f"parameter budget exceeded: {final_model.count_params()}")
    FINAL_ROOT.mkdir(parents=True, exist_ok=True)
    final_model_path = FINAL_ROOT / "scenario15_mapua9_rdtcn48.keras"
    started = time.perf_counter()
    final_model.fit(
        x_augmented,
        y_augmented,
        epochs=final_epochs,
        batch_size=config["training"]["batch_size"],
        verbose=2,
        shuffle=True,
    )
    final_training_seconds = time.perf_counter() - started
    final_model.save(final_model_path)

    digest = hashlib.sha256(json.dumps(contract, sort_keys=True).encode()).hexdigest()
    x_test, y_test = legacy.load_features(test_rows, 48)
    if SEALED_ONCE_PATH.is_file():
        with np.load(SEALED_ONCE_PATH, allow_pickle=False) as sealed:
            if str(sealed["contract_digest"].item()) != digest:
                raise RuntimeError("sealed evaluation belongs to another contract")
            sealed_y = sealed["y_true"]
            tf_probabilities = sealed["tf_probabilities"]
        if not np.array_equal(sealed_y, y_test):
            raise RuntimeError("sealed labels changed")
    else:
        tf_probabilities = final_model.predict(
            x_test,
            batch_size=32,
            verbose=0,
        ).astype(np.float32)
        SEALED_ONCE_PATH.parent.mkdir(parents=True, exist_ok=True)
        with SEALED_ONCE_PATH.open("wb") as stream:
            np.savez_compressed(
                stream,
                y_true=y_test,
                tf_probabilities=tf_probabilities,
                contract_digest=digest,
            )
    test_metrics = legacy.metric_report(y_test, tf_probabilities, config["labels"])

    bundle = FINAL_ROOT / "scenario15_mapua9_fullsign225_48f_v1"
    bundle.mkdir(parents=True, exist_ok=True)
    labels_payload = {
        "profile_id": "SCENARIO15_MAPUA9_V1",
        "class_count": 9,
        "feature_version": config["feature_contract"]["version"],
        "temporal_length": 48,
        "classes": [
            {
                "index": index,
                "id": label,
                **config["presentation"][label],
            }
            for index, label in enumerate(config["labels"])
        ],
    }
    labels_path = bundle / "class_labels_scenario15_mapua9_v1.json"
    atomic_json(labels_path, labels_payload)
    parity = export_and_parity(final_model, x_test, tf_probabilities, bundle)
    parity["labels_sha256"] = sha256_file(labels_path)
    if not parity["passed"]:
        raise RuntimeError(f"TFLite parity failed: {parity}")

    predicted = np.argmax(tf_probabilities, axis=1)
    correct = np.flatnonzero(predicted == y_test)
    golden_index = (
        int(correct[np.argmax(np.max(tf_probabilities[correct], axis=1))])
        if len(correct)
        else 0
    )
    golden_path = bundle / "golden_fullsign225_window_f32.bin"
    golden_path.write_bytes(x_test[golden_index].astype("<f4").tobytes())
    atomic_json(
        bundle / "golden_fullsign225_window_expected.json",
        {
            "shape": [1, 48, 225],
            "dtype": "float32_little_endian",
            "expected_index": int(predicted[golden_index]),
            "expected_label": config["labels"][int(predicted[golden_index])],
            "expected_probabilities": tf_probabilities[golden_index].astype(float).tolist(),
            "raw_window_sha256": sha256_file(golden_path),
        },
    )
    runtime_manifest = {
        "profile_id": "SCENARIO15_MAPUA9_V1",
        "status": "scenario15_isolated_not_production_default",
        "android_default_changed": False,
        "feature_version": config["feature_contract"]["version"],
        "input_shape": [1, 48, 225],
        "output_shape": [1, 9],
        "coordinate_orientation": "canonical_unmirrored",
        "anatomical_slot_swap": False,
        "model_file": parity["model_file"],
        "model_sha256": parity["model_sha256"],
        "labels_file": labels_path.name,
        "labels_sha256": parity["labels_sha256"],
        "tflite_parity": parity,
    }
    atomic_json(bundle / "runtime_manifest.json", runtime_manifest)
    copy_bundle_to_android(bundle)
    protected_after = verify_protected_assets()
    if protected_before != protected_after:
        raise RuntimeError("protected rollback artifacts changed during training")

    report = {
        "experiment_id": config["experiment_id"],
        "completed_utc": datetime.now(timezone.utc).isoformat(),
        "contract": contract,
        "metric_scope": config["metric_scope"],
        "signer_independent_claim": False,
        "pass_only_video_count": len(rows),
        "class_counts": dict(Counter(row["source_label"] for row in rows)),
        "development_count": len(development_rows),
        "sealed_test_count": len(test_rows),
        "architecture_candidates_trained": ["RD-TCN48"],
        "candidate_count": 1,
        "development_cv": cv_result,
        "final_retrain_epochs": final_epochs,
        "final_parameter_count": int(final_model.count_params()),
        "final_training_seconds": final_training_seconds,
        "exploratory_clip_level_test_metrics": test_metrics,
        "sealed_test_evaluation_count": 1,
        "tflite_parity": parity,
        "android_profile_status": "BUNDLE_ADDED_NOT_ROUTED",
        "samsung_test_status": "NOT_YET_TESTED",
        "protected_standard_fsl105_preserved": True,
        "protected_mapua14_rollback_preserved": True,
    }
    atomic_json(EXTERNAL_REPORT_PATH, report)
    atomic_json(REPO_REPORT_PATH, report)
    print(json.dumps(report, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
