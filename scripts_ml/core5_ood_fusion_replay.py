"""Offline fusion/stability comparison on held-out FSL train and sealed Samsung.

Variant selection uses calibration only; no official test or Samsung tuning.
No Android output is authorized from this diagnostic replay.
"""
from __future__ import annotations

import argparse
import csv
import hashlib
import json
from collections import Counter
from pathlib import Path

import numpy as np

from core5_contract import LABELS
from core5_ood_dataset import VERSION
from core5_ood_train_eval import SIM10_SHA
from core5_semantic_geometry import trajectory_features
from core5_semantic_source_profile import SourceGeometryProfile, frames_from_arrays, vector
from core5_streaming_window_replay import event_paths, source_window_durations, verified_event, windows_for_event

VARIANT_PRIORITY = ("binary_ood", "combined_product", "confidence_only", "margin_only",
                    "binary_and_geometry", "binary_geometry_conf95")


def source_geometry_fit(manifest, features_dir):
    records = []
    for row in manifest["records"]:
        if row["binary_label"] != "CORE5_LIKE":
            continue
        path = features_dir / (row["record_id"] + ".npz")
        with np.load(path, allow_pickle=False) as saved:
            meta = json.loads(str(saved["metadata"].item()))
            if meta["feature_version"] != VERSION or meta["source_sha256"] != row["source_sha256"]:
                raise ValueError("Wrong source feature contract")
            if meta["status"] == "NO_VALID_ENVELOPE":
                continue
            lo, hi = meta["envelope_start"], meta["envelope_end"]
            summary, _ = trajectory_features(frames_from_arrays(saved["raw_landmarks"][lo:hi],
                                                                  saved["timestamps_ms"][lo:hi],
                                                                  saved["presence"][lo:hi]))
        records.append({"record_id": row["record_id"], "label": row["source_label"],
                        "partition": "train" if row["partition"] == "fit" else "development_validation",
                        "vector": vector(summary), "summary": summary})
    return SourceGeometryProfile(records), source_window_durations(
        [record for record in records if record["partition"] == "train"])


def tflite_runner(path, digest):
    if hashlib.sha256(path.read_bytes()).hexdigest() != digest:
        raise ValueError("TFLite model hash mismatch")
    import tensorflow as tf
    runner = tf.lite.Interpreter(model_path=str(path), num_threads=2)
    runner.allocate_tensors()
    inp, out = runner.get_input_details()[0], runner.get_output_details()[0]
    if inp["shape"].tolist() != [1, 48, 225] or inp["dtype"] != np.float32:
        raise ValueError("Wrong TFLite input")
    return runner, inp, out


def infer(runner, inp, out, tensor):
    runner.set_tensor(inp["index"], tensor[None])
    runner.invoke()
    values = runner.get_tensor(out["index"])[0].astype(np.float32)
    if not np.isfinite(values).all():
        raise ValueError("Nonfinite TFLite probabilities")
    return values


def choices(row, cutoffs):
    basic = row["basic_quality"]
    confidence, margin, ood = row["classifier_confidence"], row["classifier_margin"], row["ood_score"]
    geometry = row["geometry_plausible"]
    return {"confidence_only": basic and confidence >= cutoffs["confidence_only"],
            "margin_only": basic and margin >= cutoffs["margin_only"],
            "binary_ood": basic and ood >= cutoffs["binary_ood"],
            "combined_product": basic and ood * confidence >= cutoffs["combined_product"],
            "binary_and_geometry": basic and ood >= cutoffs["binary_ood"] and geometry,
            "binary_geometry_conf95": basic and ood >= cutoffs["binary_ood"] and geometry and confidence >= .95}


def first_stable(rows, variant):
    candidates = []
    for duration in sorted({r["duration_target_ms"] for r in rows}):
        ordered = sorted((r for r in rows if r["duration_target_ms"] == duration), key=lambda r: r["end_ms"])
        for index in range(2, len(ordered)):
            batch = ordered[index - 2:index + 1]
            if any(not r["decisions"][variant] or r["classifier_top1"] != batch[0]["classifier_top1"] for r in batch):
                continue
            if any(b["end_ms"] - a["end_ms"] > 500 for a, b in zip(batch, batch[1:])):
                continue
            candidates.append({"class": batch[0]["classifier_top1"], "end_ms": batch[-1]["end_ms"],
                               "window_duration_ms": duration, "classifier_confidence": batch[-1]["classifier_confidence"],
                               "ood_score": batch[-1]["ood_score"], "geometry_score": batch[-1]["geometry_score"]})
            break
    return min(candidates, key=lambda row: row["end_ms"]) if candidates else None


def replay_windows(frames, times, vectors, durations, classifier, binary, geometry_profile, cutoffs):
    rows = []
    for window in windows_for_event(frames, times, vectors, durations):
        c = infer(*classifier, window["tensor"])
        b = float(infer(*binary, window["tensor"])[0])
        if c.shape != (5,):
            raise ValueError("Wrong five-class output")
        top = int(np.argmax(c))
        label = LABELS[top]
        semantic, _ = trajectory_features(window["raw_frames"])
        g = geometry_profile.evaluate(vector(semantic), label)
        sorted_probs = np.sort(c)
        row = {key: value for key, value in window.items() if key not in ("raw_frames", "tensor")}
        row.update({"classifier_top1": label, "classifier_confidence": float(c[top]),
                    "classifier_margin": float(sorted_probs[-1] - sorted_probs[-2]),
                    "ood_score": b, "geometry_score": g["class_distance"],
                    "geometry_source_cutoff": g["class_cutoff_source_loo_p95"],
                    "geometry_plausible": g["source_supported_exploratory"]})
        row["decisions"] = choices(row, cutoffs)
        rows.append(row)
    return rows


def evaluate_events(events, variants):
    output = {}
    for variant in variants:
        positives = [event for event in events if event["expected"] in LABELS]
        negatives = [event for event in events if event["expected"] not in LABELS]
        first_correct = sum(event["first_stable"][variant] is not None and
                            event["first_stable"][variant]["class"] == event["expected"] for event in positives)
        first_wrong = sum(event["first_stable"][variant] is not None and
                          event["first_stable"][variant]["class"] != event["expected"] for event in positives)
        false_accept = sum(event["first_stable"][variant] is not None for event in negatives)
        output[variant] = {"positive_events": len(positives), "first_correct": first_correct,
                           "first_wrong": first_wrong, "no_stable": len(positives) - first_correct - first_wrong,
                           "negative_events": len(negatives), "negative_false_accepts": false_accept}
    return output


def select_variant(calibration):
    eligible = [(name, calibration[name]["non_core5_rejection"]) for name in VARIANT_PRIORITY
                if calibration[name]["core5_recall"] >= .9]
    return sorted(eligible, key=lambda pair: (-pair[1], VARIANT_PRIORITY.index(pair[0])))[0][0] if eligible else None


def run(manifest_path: Path, features_dir: Path, classifier_path: Path,
        ood_dir: Path, samsung_dir: Path, output: Path):
    if output.exists():
        raise FileExistsError("Preserve prior fusion replay")
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    if manifest["official_test_opened"]:
        raise ValueError("Official test may not be opened")
    trained = json.loads((ood_dir / "evaluation.json").read_text(encoding="utf-8"))
    if trained["official_test_opened"] or trained["samsung_training_used"]:
        raise ValueError("Invalid training provenance")
    geometry_profile, durations = source_geometry_fit(manifest, features_dir)
    cutoffs = trained["source_calibrated_cutoffs"]
    selected = select_variant(trained["evaluations"]["calibration"])
    classifier = tflite_runner(classifier_path, SIM10_SHA)
    binary = tflite_runner(ood_dir / "core5_ood_small_tcn_float32.tflite", trained["model_sha256"])
    source = []
    for row in manifest["records"]:
        if row["partition"] != "holdout":
            continue
        with np.load(features_dir / (row["record_id"] + ".npz"), allow_pickle=False) as saved:
            meta = json.loads(str(saved["metadata"].item()))
            if meta["feature_version"] != VERSION or meta["source_sha256"] != row["source_sha256"]:
                raise ValueError("Wrong holdout feature")
            raw, times, presence = saved["raw_landmarks"], saved["timestamps_ms"], saved["presence"]
            vectors = saved["canonical_frames"]
        frames = frames_from_arrays(raw, times, presence)
        windows = replay_windows(frames, times, vectors, durations, classifier, binary, geometry_profile, cutoffs)
        source.append({"event_id": row["record_id"], "expected": row["source_label"] if row["binary_label"] == "CORE5_LIKE" else "OTHER_FSL",
                       "source_label": row["source_label"], "first_stable": {name: first_stable(windows, name) for name in VARIANT_PRIORITY},
                       "windows_evaluated": len(windows)})
        if len(source) % 25 == 0:
            print("SOURCE", len(source), flush=True)
    samsung = []
    for path in event_paths(samsung_dir):
        verified = verified_event(path)
        if verified is None:
            continue
        event, frames, times, vectors = verified
        windows = replay_windows(frames, times, vectors, durations, classifier, binary, geometry_profile, cutoffs)
        tensor = np.asarray(event["tensor"], dtype=np.float32) if event.get("tensor") is not None else None
        if tensor is not None:
            c = infer(*classifier, tensor)
            b = float(infer(*binary, tensor)[0])
            included = [frame for frame in frames if frame["included"]]
            semantic, _ = trajectory_features(included)
            label = LABELS[int(np.argmax(c))]
            g = geometry_profile.evaluate(vector(semantic), label)
            final = {"classifier_top1": label, "classifier_confidence": float(np.max(c)),
                     "ood_score": b, "geometry_score": g["class_distance"],
                     "geometry_source_cutoff": g["class_cutoff_source_loo_p95"],
                     "geometry_plausible": g["source_supported_exploratory"]}
        else:
            final = {"classifier_top1": None, "classifier_confidence": None,
                     "ood_score": None, "geometry_score": None,
                     "geometry_source_cutoff": None, "geometry_plausible": None}
        first = {name: first_stable(windows, name) for name in VARIANT_PRIORITY}
        samsung.append({"event_id": event["event_id"], "expected": event["expected_test_label"],
                        "legacy_end_reason": event["termination"], "legacy_accepted": event["accepted"],
                        "legacy_raw_top1": event["raw_top1"], "final_tensor": final,
                        "first_stable": first, "windows_evaluated": len(windows),
                        "first_stable_class": first[selected]["class"] if selected and first[selected] else None,
                        "final_accept_reject": "EXPLORATORY_ACCEPT" if selected and first[selected] else "EXPLORATORY_REJECT"})
        print("SAMSUNG", len(samsung), flush=True)
    report = {"status": "OFFLINE_EXPLORATORY_NO_ANDROID_AUTHORIZATION",
              "official_test_opened": False, "samsung_tuning_used": False,
              "source_calibration_selected_variant": selected,
              "source_calibration_cutoffs": cutoffs, "durations_source_derived_ms": durations,
              "heldout_source": evaluate_events(source, VARIANT_PRIORITY),
              "sealed_samsung": evaluate_events(samsung, VARIANT_PRIORITY),
              "source_events": source, "samsung_events": samsung}
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(report, indent=2, allow_nan=False) + "\n", encoding="utf-8")
    return {"selected_variant": selected, "heldout_source": report["heldout_source"],
            "sealed_samsung": report["sealed_samsung"], "output": str(output)}


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--manifest", type=Path, required=True)
    parser.add_argument("--features", type=Path, required=True)
    parser.add_argument("--classifier", type=Path, required=True)
    parser.add_argument("--ood-dir", type=Path, required=True)
    parser.add_argument("--samsung", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    print(json.dumps(run(args.manifest, args.features, args.classifier,
                         args.ood_dir, args.samsung, args.output), indent=2))
