"""Train-only class-conditioned verifier comparison; Samsung is evaluation only.

The existing frozen SIM10 TFLite proposes a class. All verifier fitting uses
FSL-105 official-train FIT; cutoffs use CALIBRATION only. HOLDOUT and saved
Samsung are opened only after the fit/cutoffs are fixed. No official test data.
"""
from __future__ import annotations

import argparse
import hashlib
import json
from collections import Counter
from pathlib import Path

import numpy as np

from core5_contract import LABELS
from core5_ood_train_eval import SIM10_SHA
from core5_streaming_window_replay import event_paths, verified_event

BASELINE_SHA = "3518ddeb68e69afa37428b8c5fc08b9d3b93e396fa1549224493da293f0ea484"
METHODS = ("global_ood", "global_product", "class_confidence", "class_margin",
           "class_ovr", "class_prototype", "class_mahalanobis")


def load_source(manifest_path: Path, features_dir: Path):
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    if manifest["official_test_opened"]:
        raise ValueError("Official test is sealed")
    rows = []
    for row in manifest["records"]:
        if row["partition"] == "quarantined_cross_label_duplicate":
            continue
        with np.load(features_dir / (row["record_id"] + ".npz"), allow_pickle=False) as saved:
            meta = json.loads(str(saved["metadata"].item()))
            if meta["record_id"] != row["record_id"] or meta["source_sha256"] != row["source_sha256"]:
                raise ValueError("Source feature manifest mismatch")
            tensor = saved["tensor"].astype(np.float32)
        rows.append({"event_id": row["record_id"], "partition": row["partition"],
                     "expected": row["source_label"] if row["binary_label"] == "CORE5_LIKE" else "OTHER_FSL",
                     "source_label": row["source_label"],
                     "tensor": tensor if tensor.shape == (48, 225) else None})
    counts = Counter((r["partition"], r["expected"] in LABELS) for r in rows)
    if counts[("fit", True)] != 61 or counts[("calibration", True)] != 10 or counts[("holdout", True)] != 10:
        raise ValueError("Wrong source split")
    return rows


def load_samsung(events_dir: Path):
    rows = []
    for path in event_paths(events_dir):
        checked = verified_event(path)
        if checked is None:
            continue
        event = checked[0]
        tensor = np.asarray(event["tensor"], dtype=np.float32) if event.get("tensor") is not None else None
        rows.append({"event_id": event["event_id"], "partition": "sealed_samsung",
                     "expected": event["expected_test_label"], "source_label": None,
                     "tensor": tensor, "legacy_end_reason": event["termination"]})
    if len(rows) != 69:
        raise ValueError("Unexpected saved Samsung count")
    return rows


def tflite_sim(path: Path):
    if hashlib.sha256(path.read_bytes()).hexdigest() != SIM10_SHA:
        raise ValueError("Wrong fixed SIM10 classifier")
    import tensorflow as tf
    interpreter = tf.lite.Interpreter(model_path=str(path), num_threads=2)
    interpreter.allocate_tensors()
    inp, out = interpreter.get_input_details()[0], interpreter.get_output_details()[0]
    if inp["shape"].tolist() != [1, 48, 225] or out["shape"].tolist() != [1, 5]:
        raise ValueError("Wrong classifier contract")
    return interpreter, inp, out


def infer_classifier(bundle, tensors):
    model, inp, out = bundle
    scores = []
    for tensor in tensors:
        model.set_tensor(inp["index"], tensor[None])
        model.invoke()
        scores.append(model.get_tensor(out["index"])[0].astype(np.float32))
    result = np.asarray(scores)
    if result.shape != (len(tensors), 5) or not np.isfinite(result).all():
        raise ValueError("Invalid five-class output")
    return result


def embed_and_score(records, sim_path, baseline_keras, baseline_tflite,
                    ood_keras, ood_tflite, ood_sha):
    import tensorflow as tf
    valid = [r for r in records if r["tensor"] is not None]
    x = np.stack([r["tensor"] for r in valid])
    frozen = tflite_sim(sim_path)
    probabilities = infer_classifier(frozen, x)
    baseline = tf.keras.models.load_model(baseline_keras, compile=False)
    baseline_embeddings = tf.keras.Model(baseline.inputs, baseline.layers[-2].output)
    baseline_probabilities = baseline.predict(x, batch_size=64, verbose=0)
    if hashlib.sha256(baseline_tflite.read_bytes()).hexdigest() != BASELINE_SHA:
        raise ValueError("Wrong frozen baseline TFLite")
    reference = tf.lite.Interpreter(model_path=str(baseline_tflite), num_threads=2)
    reference.allocate_tensors()
    ref_in, ref_out = reference.get_input_details()[0], reference.get_output_details()[0]
    parity_inputs = x[np.unique([0, 1, 2, len(x)-3, len(x)-2, len(x)-1])]
    baseline_reference = infer_classifier((reference, ref_in, ref_out), parity_inputs)
    parity = float(np.max(np.abs(baseline.predict(parity_inputs, verbose=0) - baseline_reference)))
    if parity > 1e-5:
        raise ValueError("Baseline Keras embedding source is not TFLite-parity compatible")
    ood = tf.keras.models.load_model(ood_keras, compile=False)
    embeddings = baseline_embeddings.predict(x, batch_size=64, verbose=0)
    ood_keras_scores = ood.predict(x, batch_size=64, verbose=0).reshape(-1)
    if hashlib.sha256(ood_tflite.read_bytes()).hexdigest() != ood_sha:
        raise ValueError("Wrong source-calibrated OOD TFLite")
    binary = tf.lite.Interpreter(model_path=str(ood_tflite), num_threads=2)
    binary.allocate_tensors()
    binary_in, binary_out = binary.get_input_details()[0], binary.get_output_details()[0]
    if binary_in["shape"].tolist() != [1, 48, 225] or binary_out["shape"].tolist() != [1, 1]:
        raise ValueError("Wrong binary OOD contract")
    ood_scores = []
    for tensor in x:
        binary.set_tensor(binary_in["index"], tensor[None])
        binary.invoke()
        ood_scores.append(float(binary.get_tensor(binary_out["index"])[0, 0]))
    ood_scores = np.asarray(ood_scores)
    ood_parity = float(np.max(np.abs(ood_scores - ood_keras_scores)))
    if ood_parity > 1e-5:
        raise ValueError("OOD Keras/TFLite parity failed")
    for row, p, e, o in zip(valid, probabilities, embeddings, ood_scores):
        row["probabilities"] = p
        row["embedding"] = e.astype(np.float64)
        row["ood_score"] = float(o)
        row["proposed"] = LABELS[int(np.argmax(p))]
    return {"source_and_samsung_tensors": len(valid),
            "baseline_embedding_width": int(embeddings.shape[1]),
            "baseline_keras_output_shape": list(baseline_probabilities.shape),
            "baseline_keras_tflite_parity_max": parity,
            "ood_keras_tflite_parity_max": ood_parity,
            "keras_probability_finite": bool(np.isfinite(baseline_probabilities).all())}


def fit_methods(records):
    from sklearn.covariance import LedoitWolf
    from sklearn.decomposition import PCA
    from sklearn.linear_model import LogisticRegression
    from sklearn.preprocessing import StandardScaler

    fit = [r for r in records if r["partition"] == "fit" and r["tensor"] is not None]
    if len(fit) != 1279:
        raise ValueError("Wrong usable fit count")
    matrix = np.stack([r["embedding"] for r in fit])
    scaler = StandardScaler().fit(matrix)
    for row in records:
        if row["tensor"] is not None:
            row["scaled"] = scaler.transform(row["embedding"][None])[0]
    x = np.stack([r["scaled"] for r in fit])
    models = {}
    centers = {}
    for label in LABELS:
        y = np.asarray([r["expected"] == label for r in fit], dtype=np.int32)
        models[label] = LogisticRegression(C=.1, class_weight="balanced", max_iter=1000,
                                           random_state=20260929).fit(x, y)
        centers[label] = np.mean(x[y == 1], axis=0)
    core5_fit = np.stack([r["scaled"] for r in fit if r["expected"] in LABELS])
    projection = PCA(n_components=8, random_state=20260929).fit(core5_fit)
    gaussian = {}
    shrinkage = {}
    condition = {}
    for label in LABELS:
        group = projection.transform(np.stack([r["scaled"] for r in fit if r["expected"] == label]))
        estimate = LedoitWolf().fit(group)
        gaussian[label] = estimate
        shrinkage[label] = float(estimate.shrinkage_)
        condition[label] = float(np.linalg.cond(estimate.covariance_))
    if max(condition.values()) > 1e8:
        raise ValueError("Mahalanobis covariance unstable")
    return {"ovr": models, "centers": centers, "projection": projection,
            "gaussian": gaussian, "shrinkage": shrinkage, "condition": condition}


def class_scores(row, label, fitted):
    index = LABELS.index(label)
    p = row["probabilities"]
    margin = float(p[index] - max(p[j] for j in range(5) if j != index))
    scaled = row["scaled"]
    transformed = fitted["projection"].transform(scaled[None])[0]
    estimate = fitted["gaussian"][label]
    delta = transformed - estimate.location_
    mahal = float(np.sqrt(delta @ estimate.precision_ @ delta / len(delta)))
    return {"class_confidence": float(p[index]), "class_margin": margin,
            "class_ovr": float(fitted["ovr"][label].predict_proba(scaled[None])[0, 1]),
            "class_prototype": -float(np.linalg.norm(scaled - fitted["centers"][label]) / np.sqrt(len(scaled))),
            "class_mahalanobis": -mahal}


def calibrate(records, fitted):
    calibration = [r for r in records if r["partition"] == "calibration" and r["expected"] in LABELS]
    cutoffs = {}
    for label in LABELS:
        positive = [r for r in calibration if r["expected"] == label]
        if len(positive) != 2:
            raise ValueError("Require exactly two calibration positives per class")
        scores = [class_scores(r, label, fitted) for r in positive]
        cutoffs[label] = {name: min(row[name] for row in scores) for name in scores[0]}
    return cutoffs


def score_rows(records, fitted, cutoffs, global_cutoffs):
    for row in records:
        if row["tensor"] is None:
            row["decisions"] = {name: False for name in METHODS}
            row["proposed"] = None
            row["proposed_scores"] = None
            continue
        label = row["proposed"]
        scores = class_scores(row, label, fitted)
        row["proposed_scores"] = scores
        row["decisions"] = {
            "global_ood": row["ood_score"] >= global_cutoffs["binary_ood"],
            "global_product": row["ood_score"] * float(np.max(row["probabilities"])) >= global_cutoffs["combined_product"],
            **{name: scores[name] >= cutoffs[label][name] for name in scores},
        }


def metrics(rows):
    positives = [r for r in rows if r["expected"] in LABELS]
    negatives = [r for r in rows if r["expected"] not in LABELS]
    result = {}
    for name in METHODS:
        result[name] = {
            "positives": len(positives),
            "correct_accepted": sum(r["decisions"][name] and r["proposed"] == r["expected"] for r in positives),
            "wrong_accepted": sum(r["decisions"][name] and r["proposed"] != r["expected"] for r in positives),
            "positive_rejected": sum(not r["decisions"][name] for r in positives),
            "negatives": len(negatives),
            "negative_false_accepted": sum(r["decisions"][name] for r in negatives),
            "per_class": {label: {"count": sum(r["expected"] == label for r in positives),
                                  "raw_correct": sum(r["expected"] == label and r["proposed"] == label for r in positives),
                                  "correct_accepted": sum(r["expected"] == label and r["proposed"] == label and r["decisions"][name] for r in positives)}
                          for label in LABELS},
            "negative_by_type": {label: {"count": sum(r["expected"] == label for r in negatives),
                                         "false_accepted": sum(r["expected"] == label and r["decisions"][name] for r in negatives)}
                                 for label in sorted(set(r["expected"] for r in negatives))},
        }
    return result


def sanitized(rows):
    return [{"event_id": r["event_id"], "expected": r["expected"], "partition": r["partition"],
             "source_label": r["source_label"], "proposed": r["proposed"],
             "classifier_probabilities": r["probabilities"].tolist() if r["tensor"] is not None else None,
             "ood_score": r.get("ood_score"), "proposed_scores": r["proposed_scores"],
             "decisions": r["decisions"], "legacy_end_reason": r.get("legacy_end_reason")}
            for r in rows]


def run(manifest, features, samsung, sim, baseline_keras, baseline_tflite,
        ood_keras, ood_tflite, ood_evaluation, output):
    if output.exists():
        raise FileExistsError("Preserve earlier verifier evidence")
    source = load_source(manifest, features)
    device = load_samsung(samsung)
    prior = json.loads(ood_evaluation.read_text(encoding="utf-8"))
    if prior["official_test_opened"] or prior["samsung_training_used"]:
        raise ValueError("Global OOD thresholds lack source-only provenance")
    global_cutoffs = prior["source_calibrated_cutoffs"]
    rows = source + device
    embedding_info = embed_and_score(rows, sim, baseline_keras, baseline_tflite,
                                     ood_keras, ood_tflite, prior["model_sha256"])
    fitted = fit_methods(rows)
    cutoffs = calibrate(rows, fitted)
    score_rows(rows, fitted, cutoffs, global_cutoffs)
    source_cal = metrics([r for r in source if r["partition"] == "calibration"])
    source_hold = metrics([r for r in source if r["partition"] == "holdout"])
    samsung_result = metrics(device)
    report = {"status": "OFFLINE_CLASS_CONDITIONED_NOT_ANDROID_AUTHORIZED",
              "official_test_opened": False, "samsung_fit_or_calibration_used": False,
              "threshold_policy": "fit on official-train fit only; per-class minimum of two labelled calibration positives, including a classifier-wrong HELLO; no Samsung tuning",
              "energy_status": "UNIDENTIFIABLE: only normalized softmax probabilities are available; log-sum-exp of log probabilities is constant, not model-logit energy",
              "embedding": embedding_info,
              "mahalanobis": {"dimensions": 8, "fit_positive_count_per_class": {label: sum(r["expected"] == label and r["partition"] == "fit" for r in source) for label in LABELS},
                              "ledoit_wolf_shrinkage": fitted["shrinkage"],
                              "covariance_condition_number": fitted["condition"]},
              "global_cutoffs": {"ood": global_cutoffs["binary_ood"],
                                 "product": global_cutoffs["combined_product"]},
              "class_calibration_cutoffs": cutoffs,
              "source_calibration": source_cal, "source_holdout": source_hold,
              "sealed_samsung": samsung_result,
              "source_events": sanitized([r for r in source if r["partition"] in ("calibration", "holdout")]),
              "samsung_events": sanitized(device)}
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(report, indent=2, allow_nan=False) + "\n", encoding="utf-8")
    return {"embedding": embedding_info, "source_holdout": source_hold,
            "sealed_samsung": samsung_result, "output": str(output)}


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    for name in ("manifest", "features", "samsung", "sim", "baseline_keras",
                 "baseline_tflite", "ood_keras", "ood_tflite", "ood_evaluation", "output"):
        parser.add_argument("--" + name.replace("_", "-"), type=Path, required=True)
    args = parser.parse_args()
    print(json.dumps(run(args.manifest, args.features, args.samsung, args.sim,
                         args.baseline_keras, args.baseline_tflite, args.ood_keras,
                         args.ood_tflite, args.ood_evaluation, args.output), indent=2))
