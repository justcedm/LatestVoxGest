"""Train-only FSL-105 Core5-vs-Other rejector and sealed-development comparison.

The official test split is never listed or opened. Calibration thresholds use
only official-train calibration positives; holdout and Samsung are evaluation.
"""
from __future__ import annotations

import argparse
import hashlib
import json
from collections import Counter
from pathlib import Path

import numpy as np

from core5_contract import LABELS
from core5_ood_dataset import VERSION
from core5_semantic_geometry import trajectory_features
from core5_semantic_source_profile import SourceGeometryProfile, frames_from_arrays, vector

SIM10_SHA = "3702ff77c1c44a60f0dc7f06e19e778b6498df7dbf7e205991dc15158b8e888f"
SEED = 20260929


def load_records(manifest_path: Path, features_dir: Path):
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    if manifest["official_test_opened"] or manifest["feature_version"] != VERSION:
        raise ValueError("Wrong OOD manifest")
    records = []
    for row in manifest["records"]:
        if row["partition"] == "quarantined_cross_label_duplicate":
            continue
        path = features_dir / (row["record_id"] + ".npz")
        if not path.is_file():
            raise FileNotFoundError("Extraction incomplete: " + row["record_id"])
        with np.load(path, allow_pickle=False) as saved:
            meta = json.loads(str(saved["metadata"].item()))
            for key in ("record_id", "binary_label", "partition", "source_sha256"):
                if meta[key] != row[key]:
                    raise ValueError("Feature/manifest mismatch: " + row["record_id"])
            if meta["feature_version"] != VERSION:
                raise ValueError("Wrong feature version")
            tensor = saved["tensor"].astype(np.float32)
            raw = saved["raw_landmarks"]
            times = saved["timestamps_ms"]
            presence = saved["presence"]
        usable = tensor.shape == (48, 225) and np.isfinite(tensor).all()
        if not usable and meta["status"] != "NO_VALID_ENVELOPE":
            raise ValueError("Non-usable feature tensor without explicit status: " + row["record_id"])
        lo, hi = meta["envelope_start"], meta["envelope_end"]
        if usable and not 0 <= lo < hi <= len(times):
            raise ValueError("Invalid source envelope")
        semantic = None
        if usable:
            semantic, _ = trajectory_features(frames_from_arrays(raw[lo:hi], times[lo:hi], presence[lo:hi]))
        records.append({"record_id": row["record_id"], "source_label": row["source_label"],
                        "binary_label": row["binary_label"], "partition": row["partition"],
                        "quality_status": meta["status"], "tensor": tensor if usable else None,
                        "semantic": semantic, "geometry_vector": vector(semantic) if usable else None})
    return records


def make_model(tf):
    inputs = tf.keras.Input(shape=(48, 225), dtype=tf.float32, name="fullsign225_48")
    x = tf.keras.layers.Conv1D(24, 5, padding="same", activation="relu")(inputs)
    x = tf.keras.layers.Conv1D(24, 3, padding="same", dilation_rate=2, activation="relu")(x)
    x = tf.keras.layers.Conv1D(16, 3, padding="same", dilation_rate=4, activation="relu")(x)
    average = tf.keras.layers.GlobalAveragePooling1D()(x)
    maximum = tf.keras.layers.GlobalMaxPooling1D()(x)
    x = tf.keras.layers.Concatenate()([average, maximum])
    x = tf.keras.layers.Dense(24, activation="relu")(x)
    outputs = tf.keras.layers.Dense(1, activation="sigmoid", name="p_core5_like")(x)
    return tf.keras.Model(inputs, outputs, name="core5_ood_small_tcn")


def balanced_weights(labels):
    positive = int(np.sum(labels == 1))
    negative = int(np.sum(labels == 0))
    if positive == 0 or negative == 0:
        raise ValueError("Missing binary class")
    return np.where(labels == 1, len(labels) / (2 * positive), len(labels) / (2 * negative)).astype(np.float32)


def cutoff_for_recall(scores, positive, retain_fraction=.9):
    values = sorted(float(score) for score, truth in zip(scores, positive) if truth)
    if not values:
        raise ValueError("No positive calibration records")
    index = max(0, len(values) - int(np.ceil(retain_fraction * len(values))))
    return values[index]


def method_metrics(records, decisions):
    if len(records) != len(decisions):
        raise ValueError("Prediction length mismatch")
    positives = [i for i, row in enumerate(records) if row["binary_label"] == "CORE5_LIKE"]
    negatives = [i for i, row in enumerate(records) if row["binary_label"] == "OTHER_FSL"]
    accepted_positive = sum(bool(decisions[i]) for i in positives)
    rejected_negative = sum(not decisions[i] for i in negatives)
    return {"core5_recall": accepted_positive / len(positives),
            "non_core5_rejection": rejected_negative / len(negatives),
            "false_reject_rate": 1 - accepted_positive / len(positives),
            "false_accept_rate": 1 - rejected_negative / len(negatives),
            "core5_accepted": accepted_positive, "core5_total": len(positives),
            "other_rejected": rejected_negative, "other_total": len(negatives)}


def infer_tflite(runner, tensors):
    inp, out = runner.get_input_details()[0], runner.get_output_details()[0]
    if inp["shape"].tolist() != [1, 48, 225] or inp["dtype"] != np.float32:
        raise ValueError("Wrong input contract")
    results = []
    for tensor in tensors:
        runner.set_tensor(inp["index"], tensor[None])
        runner.invoke()
        result = runner.get_tensor(out["index"])[0]
        if not np.isfinite(result).all():
            raise ValueError("Nonfinite TFLite output")
        results.append(result.astype(np.float32))
    return np.asarray(results)


def run(manifest_path: Path, features_dir: Path, classifier: Path, output_dir: Path):
    if output_dir.exists():
        raise FileExistsError("Preserve prior OOD training checkpoint")
    if hashlib.sha256(classifier.read_bytes()).hexdigest() != SIM10_SHA:
        raise ValueError("Wrong fixed Core5 classifier")
    import tensorflow as tf
    tf.keras.utils.set_random_seed(SEED)
    records = load_records(manifest_path, features_dir)
    partitions = {name: [r for r in records if r["partition"] == name] for name in ("fit", "calibration", "holdout")}
    for name, subset in partitions.items():
        counts = Counter(r["binary_label"] for r in subset)
        if counts != ({"CORE5_LIKE": 61, "OTHER_FSL": 1219} if name == "fit" else
                      {"CORE5_LIKE": 10, "OTHER_FSL": 200}):
            raise ValueError("Unexpected partition counts: " + name + " " + repr(counts))
    valid = {name: [r for r in subset if r["tensor"] is not None]
             for name, subset in partitions.items()}
    arrays = {name: (np.stack([r["tensor"] for r in subset]),
                     np.asarray([r["binary_label"] == "CORE5_LIKE" for r in subset], dtype=np.float32))
              for name, subset in valid.items()}
    model = make_model(tf)
    model.compile(optimizer=tf.keras.optimizers.Adam(learning_rate=.001),
                  loss="binary_crossentropy", metrics=[tf.keras.metrics.AUC(name="auc")])
    xfit, yfit = arrays["fit"]
    xcal, ycal = arrays["calibration"]
    history = model.fit(xfit, yfit, sample_weight=balanced_weights(yfit),
                        validation_data=(xcal, ycal, balanced_weights(ycal)),
                        epochs=35, batch_size=32, verbose=2,
                        callbacks=[tf.keras.callbacks.EarlyStopping(monitor="val_loss", patience=6,
                                                                    restore_best_weights=True)])
    converter = tf.lite.TFLiteConverter.from_keras_model(model)
    tflite = converter.convert()
    binary = tf.lite.Interpreter(model_content=tflite, num_threads=2)
    binary.allocate_tensors()
    classifier_runner = tf.lite.Interpreter(model_path=str(classifier), num_threads=2)
    classifier_runner.allocate_tensors()
    predictions = {}
    geometry_records = [{"record_id": r["record_id"], "label": r["source_label"],
                         "partition": "train" if r["partition"] == "fit" else "development_validation",
                         "vector": r["geometry_vector"]}
                        for r in records if r["binary_label"] == "CORE5_LIKE" and r["tensor"] is not None]
    geometry_profile = SourceGeometryProfile(geometry_records)
    for partition in ("calibration", "holdout"):
        subset = valid[partition]
        tensors = arrays[partition][0]
        b = infer_tflite(binary, tensors)[:, 0]
        c = infer_tflite(classifier_runner, tensors)
        if c.shape != (len(subset), 5):
            raise ValueError("Wrong classifier output shape")
        ranked = np.sort(c, axis=1)
        entries = []
        for row, binary_score, probabilities, top1, margin in zip(subset, b, c, ranked[:, -1], ranked[:, -1] - ranked[:, -2]):
            class_name = LABELS[int(np.argmax(probabilities))]
            geometry = geometry_profile.evaluate(row["geometry_vector"], class_name)
            entries.append({"record_id": row["record_id"], "expected_binary": row["binary_label"],
                            "expected_class": row["source_label"], "classifier_top1": class_name,
                            "classifier_confidence": float(top1), "classifier_margin": float(margin),
                            "classifier_probabilities": probabilities.tolist(),
                            "ood_score": float(binary_score), "combined_score": float(binary_score * top1),
                            "geometry_score": geometry["class_distance"],
                            "geometry_source_cutoff": geometry["class_cutoff_source_loo_p95"],
                            "geometry_plausible": geometry["source_supported_exploratory"],
                            "quality_status": row["quality_status"], "no_inference": False})
        for row in partitions[partition]:
            if row["tensor"] is None:
                entries.append({"record_id": row["record_id"], "expected_binary": row["binary_label"],
                                "expected_class": row["source_label"], "classifier_top1": None,
                                "classifier_confidence": -1.0, "classifier_margin": -1.0,
                                "classifier_probabilities": None, "ood_score": -1.0,
                                "combined_score": -1.0, "geometry_score": None,
                                "geometry_source_cutoff": None, "geometry_plausible": False,
                                "quality_status": row["quality_status"], "no_inference": True})
        entries.sort(key=lambda entry: entry["record_id"])
        predictions[partition] = entries
    calibration = predictions["calibration"]
    positive = [r["expected_binary"] == "CORE5_LIKE" and not r["no_inference"] for r in calibration]
    scores = {"confidence_only": "classifier_confidence", "margin_only": "classifier_margin",
              "binary_ood": "ood_score", "combined_product": "combined_score"}
    cutoffs = {name: cutoff_for_recall([r[key] for r in calibration], positive)
               for name, key in scores.items()}
    evaluations = {}
    for partition in ("calibration", "holdout"):
        entries = predictions[partition]
        subset = partitions[partition]
        methods = {name: [not r["no_inference"] and r[key] >= cutoffs[name] for r in entries] for name, key in scores.items()}
        methods["binary_and_geometry"] = [not r["no_inference"] and r["ood_score"] >= cutoffs["binary_ood"] and r["geometry_plausible"] for r in entries]
        methods["binary_geometry_conf95"] = [not r["no_inference"] and r["ood_score"] >= cutoffs["binary_ood"] and r["geometry_plausible"] and r["classifier_confidence"] >= .95 for r in entries]
        evaluations[partition] = {}
        for name, choices in methods.items():
            metrics = method_metrics(subset, choices)
            metrics["core5_correct_accepted"] = sum(bool(choice) and entry["expected_binary"] == "CORE5_LIKE"
                                                       and entry["classifier_top1"] == entry["expected_class"]
                                                       for entry, choice in zip(entries, choices))
            metrics["core5_wrong_accepted"] = sum(bool(choice) and entry["expected_binary"] == "CORE5_LIKE"
                                                     and entry["classifier_top1"] != entry["expected_class"]
                                                     for entry, choice in zip(entries, choices))
            evaluations[partition][name] = metrics
        for index, entry in enumerate(entries):
            entry["decisions"] = {name: bool(choices[index]) for name, choices in methods.items()}
    output_dir.mkdir(parents=True)
    model_path = output_dir / "core5_ood_small_tcn_float32.tflite"
    model_path.write_bytes(tflite)
    model.save(output_dir / "core5_ood_small_tcn.keras")
    check = infer_tflite(binary, arrays["holdout"][0])[:, 0]
    reference = model.predict(arrays["holdout"][0], verbose=0).reshape(-1)
    parity = float(np.max(np.abs(check - reference)))
    report = {"status": "OFFLINE_EXPERIMENTAL_NOT_ANDROID_AUTHORIZED",
              "official_test_opened": False, "samsung_training_used": False,
              "feature_version": VERSION, "model_parameter_count": model.count_params(),
              "model_sha256": hashlib.sha256(tflite).hexdigest(), "model_bytes": len(tflite),
              "classifier_sha256": SIM10_SHA,
              "train_counts": {name: dict(Counter(r["binary_label"] for r in subset)) for name, subset in partitions.items()},
              "usable_counts": {name: dict(Counter(r["binary_label"] for r in subset)) for name, subset in valid.items()},
              "no_inference_counts": {name: len(partitions[name]) - len(valid[name]) for name in partitions},
              "source_calibrated_cutoffs": cutoffs, "evaluations": evaluations,
              "geometry_source_profile": geometry_profile.report(),
              "tflite_reference_max_probability_difference": parity,
              "epochs_completed": len(history.history["loss"]),
              "training_history": {key: [float(v) for v in values] for key, values in history.history.items()},
              "records": predictions}
    (output_dir / "evaluation.json").write_text(json.dumps(report, indent=2, allow_nan=False) + "\n", encoding="utf-8")
    return {"model_sha256": report["model_sha256"], "model_parameter_count": model.count_params(),
            "epochs_completed": report["epochs_completed"], "parity_max": parity,
            "holdout": evaluations["holdout"], "output_dir": str(output_dir)}


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--manifest", type=Path, required=True)
    parser.add_argument("--features", type=Path, required=True)
    parser.add_argument("--classifier", type=Path, required=True)
    parser.add_argument("--output-dir", type=Path, required=True)
    args = parser.parse_args()
    print(json.dumps(run(args.manifest, args.features, args.classifier, args.output_dir), indent=2))
