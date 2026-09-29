"""Offline-only rolling Core5 SIM10 TFLite replay on saved Samsung landmarks.

Operator labels are evaluation intent, never training truth. No Android,
classifier, tensor contract, or production gate is changed.
"""
from __future__ import annotations

import argparse
import hashlib
import json
from collections import Counter
from pathlib import Path

import numpy as np

from core5_contract import LABELS, VERSION, resample_timestamp
from core5_semantic_geometry import trajectory_features
from core5_semantic_source_profile import SourceGeometryProfile, source_records, vector

EXPECTED_SIM10 = "3702ff77c1c44a60f0dc7f06e19e778b6498df7dbf7e205991dc15158b8e888f"
MIN_FRAMES = 8
STEP_MS = 200
STABLE_CONSECUTIVE = 3
FROZEN_CONFIDENCE = .95  # Read-only comparison to the unchanged debug gate.


def event_paths(events_dir):
    for path in sorted(events_dir.glob("*.json")):
        if not path.name.startswith("startup-") and ".candidate-replay." not in path.name:
            yield path


def verified_event(path):
    event = json.loads(path.read_text(encoding="utf-8"))
    if event.get("profile") != "FSL_CORE5_SIM10FPS_V1" or not event.get("frames"):
        return None
    if event["feature_version"] != VERSION or event["model_sha256"] != EXPECTED_SIM10:
        raise ValueError("Wrong event feature/model contract: " + path.name)
    frames = event["frames"]
    times = np.asarray([frame["timestamp_ms"] for frame in frames], dtype=np.int64)
    if np.any(np.diff(times) <= 0):
        raise ValueError("Non-monotonic Samsung timestamps: " + path.name)
    vectors = np.asarray([frame["canonical"] for frame in frames], dtype=np.float32)
    if vectors.shape != (len(frames), 225) or not np.isfinite(vectors).all():
        raise ValueError("Invalid raw canonical frame: " + path.name)
    if event.get("tensor") is not None:
        final = np.asarray(event["tensor"], dtype=np.float32)
        if final.shape != (48, 225) or hashlib.sha256(final.astype("<f4").tobytes()).hexdigest() != event["tensor_sha256"]:
            raise ValueError("Final saved tensor hash mismatch: " + path.name)
        indices = [i for i, frame in enumerate(frames) if frame["included"]]
        if len(indices) >= 2:
            reproduced, _ = resample_timestamp(vectors[indices], times[indices])
            if float(np.max(np.abs(reproduced - final))) > 1e-5:
                raise ValueError("Saved final tensor rebuild mismatch: " + path.name)
    return event, frames, times, vectors


def source_window_durations(records):
    durations = [r["summary"]["duration_ms"] for r in records]
    # Source-derived p10/median/p90, rounded to a 100-ms grid, plus the
    # proposal's two-second rolling-history ceiling.
    values = [int(round(float(np.percentile(durations, q)) / 100) * 100)
              for q in (10, 50, 90)] + [2000]
    return sorted(set(min(2000, max(800, v)) for v in values))


def windows_for_event(frames, times, vectors, durations):
    outputs = []
    for duration in durations:
        last_end = None
        for end_index, end_ms in enumerate(times):
            if end_ms - times[0] < duration:
                continue
            if last_end is not None and end_ms - last_end < STEP_MS:
                continue
            start_ms = end_ms - duration
            start_index = int(np.searchsorted(times, start_ms, side="left"))
            selected = np.arange(start_index, end_index + 1)
            if len(selected) < MIN_FRAMES or times[start_index] - start_ms > 250:
                continue
            pose_ratio = float(np.mean([frames[i]["pose_present"] for i in selected]))
            hand_ratio = float(np.mean([frames[i]["left_present"] or frames[i]["right_present"] for i in selected]))
            motion = float(np.linalg.norm(np.diff(vectors[selected], axis=0), axis=1).mean())
            tensor, _ = resample_timestamp(vectors[selected], times[selected])
            outputs.append({"duration_target_ms": duration, "start_ms": int(times[start_index]),
                            "end_ms": int(end_ms), "end_index": end_index,
                            "frame_count": len(selected), "pose_ratio": pose_ratio,
                            "hand_ratio": hand_ratio, "motion_mean_l2": motion,
                            "basic_quality": pose_ratio >= .65 and hand_ratio >= .65 and motion >= .02,
                            "tensor": tensor, "raw_frames": [frames[i] for i in selected]})
            last_end = int(end_ms)
    return outputs


def stable_peak(rows, expected=None, require_geometry=False, consecutive=STABLE_CONSECUTIVE):
    if consecutive < 2:
        raise ValueError("At least two consecutive windows required")
    candidates = []
    for duration in sorted({row["duration_target_ms"] for row in rows}):
        subset = sorted((row for row in rows if row["duration_target_ms"] == duration),
                        key=lambda row: row["end_ms"])
        for index in range(consecutive - 1, len(subset)):
            batch = subset[index - consecutive + 1:index + 1]
            label = batch[0]["top1"]
            if expected is not None and label != expected:
                continue
            if any(row["top1"] != label or row["confidence"] < FROZEN_CONFIDENCE or
                   not row["basic_quality"] or (require_geometry and not row["geometry_source_supported_exploratory"])
                   for row in batch):
                continue
            if any(b["end_ms"] - a["end_ms"] > 500 for a, b in zip(batch, batch[1:])):
                continue
            candidates.append({"end_ms": batch[-1]["end_ms"], "duration_target_ms": duration,
                               "label": label, "confidence": batch[-1]["confidence"]})
            break
    return min(candidates, key=lambda candidate: candidate["end_ms"]) if candidates else None


def replay_event(event, frames, times, vectors, durations, interpreter, inp, out, source_profile):
    windows = windows_for_event(frames, times, vectors, durations)
    rows = []
    for window in windows:
        interpreter.set_tensor(inp["index"], window["tensor"][None])
        interpreter.invoke()
        probabilities = interpreter.get_tensor(out["index"])[0].astype(np.float32)
        if probabilities.shape != (5,) or not np.isfinite(probabilities).all():
            raise ValueError("Invalid TFLite output")
        top = int(np.argmax(probabilities))
        label = LABELS[top]
        semantic, _ = trajectory_features(window["raw_frames"])
        geometry = source_profile.evaluate(vector(semantic), label)
        rows.append({key: value for key, value in window.items() if key not in ("tensor", "raw_frames")}
                    | {"top1": label, "confidence": float(probabilities[top]),
                       "probabilities": probabilities.tolist(),
                       "geometry_distance": geometry["class_distance"],
                       "geometry_source_cutoff": geometry["class_cutoff_source_loo_p95"],
                       "geometry_source_supported_exploratory": geometry["source_supported_exploratory"]})
    qualified = [row for row in rows if row["basic_quality"]]
    peak = max(qualified, key=lambda row: row["confidence"]) if qualified else None
    expected = event["expected_test_label"]
    positive = expected in LABELS
    first_correct = stable_peak(rows, expected) if positive else None
    first_any = stable_peak(rows)
    first_geo = stable_peak(rows, expected, True) if positive else None
    false_any = stable_peak(rows) if not positive else None
    false_geo = stable_peak(rows, require_geometry=True) if not positive else None
    start = event["event_start_ms"]
    end = event["event_end_ms"]
    return {"event_id": event["event_id"], "expected": expected,
            "legacy_end_reason": event["termination"], "legacy_raw_top1": event["raw_top1"],
            "legacy_accepted": event["accepted"], "source_boundary_mode": event["boundary_mode"],
            "event_start_ms": start, "event_end_ms": end, "window_count": len(rows),
            "first_stable_correct_ms": first_correct["end_ms"] - start if first_correct else None,
            "first_stable_correct_lead_before_legacy_end_ms": end - first_correct["end_ms"] if first_correct else None,
            "first_stable_correct_window_duration_ms": first_correct["duration_target_ms"] if first_correct else None,
            "first_stable_any": first_any, "first_stable_correct_with_source_geometry_ms": first_geo["end_ms"] - start if first_geo else None,
            "negative_false_stable_peak": false_any, "negative_false_source_geometry_peak": false_geo,
            "peak_class": peak["top1"] if peak else None,
            "peak_probability": peak["confidence"] if peak else None,
            "peak_window_duration_ms": peak["duration_target_ms"] if peak else None,
            "peak_geometry_plausibility": "EXPLORATORY_SOURCE_SUPPORTED" if peak and peak["geometry_source_supported_exploratory"] else "EXPLORATORY_OUTSIDE_SOURCE" if peak else "NO_QUALITY_WINDOW",
            "windows": rows}


def run(features_dir: Path, events_dir: Path, model: Path, output: Path):
    if output.exists():
        raise FileExistsError("Preserve previous streaming replay evidence")
    if hashlib.sha256(model.read_bytes()).hexdigest() != EXPECTED_SIM10:
        raise ValueError("Wrong SIM10 model hash")
    import tensorflow as tf
    runner = tf.lite.Interpreter(model_path=str(model), num_threads=2)
    runner.allocate_tensors()
    inp, out = runner.get_input_details()[0], runner.get_output_details()[0]
    if inp["shape"].tolist() != [1, 48, 225] or out["shape"].tolist() != [1, 5]:
        raise ValueError("Wrong TFLite tensor contract")
    if inp["dtype"] != np.float32 or out["dtype"] != np.float32:
        raise ValueError("Wrong TFLite dtype")
    profile = SourceGeometryProfile(source_records(features_dir))
    durations = source_window_durations(profile.records)
    results = []
    for path in event_paths(events_dir):
        verified = verified_event(path)
        if verified is None:
            continue
        results.append(replay_event(*verified, durations, runner, inp, out, profile))
        print(f"REPLAY {len(results)} {results[-1]['expected']} {results[-1]['window_count']} windows", flush=True)
    positives = [r for r in results if r["expected"] in LABELS]
    timeouts = [r for r in positives if r["legacy_end_reason"] == "EVENT_TIMEOUT"]
    negatives = [r for r in results if r["expected"].startswith("NON_SIGN:")]
    summary = {"events": len(results), "positive_events": len(positives),
               "timeout_positive_events": len(timeouts), "negative_events": len(negatives),
               "durations_source_derived_ms": durations,
               "positive_first_stable_correct": sum(r["first_stable_correct_ms"] is not None for r in positives),
               "timeout_first_stable_correct": sum(r["first_stable_correct_ms"] is not None for r in timeouts),
               "timeout_first_stable_correct_with_source_geometry": sum(r["first_stable_correct_with_source_geometry_ms"] is not None for r in timeouts),
               "negative_false_stable_peak_events": sum(r["negative_false_stable_peak"] is not None for r in negatives),
               "negative_false_source_geometry_peak_events": sum(r["negative_false_source_geometry_peak"] is not None for r in negatives)}
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps({"status": "OFFLINE_EXPLORATORY_NOT_ANDROID_AUTHORIZED",
                                  "model_sha256": EXPECTED_SIM10, "feature_version": VERSION,
                                  "source_geometry_dev_count": 20,
                                  "source_geometry_dev_supported": sum(profile.evaluate(r["vector"], r["label"])["source_supported_exploratory"] for r in profile.records if r["partition"] == "development_validation"),
                                  "stability_definition": "three consecutive same-duration quality windows, same raw top1, each p>=.95, <=500ms evaluation gaps; no production gate change",
                                  "summary": summary, "events": results}, indent=2, allow_nan=False) + "\n", encoding="utf-8")
    return {**summary, "output": str(output)}


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--features", type=Path, required=True)
    parser.add_argument("--events", type=Path, required=True)
    parser.add_argument("--model", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    print(json.dumps(run(args.features, args.events, args.model, args.output), indent=2))
