"""Pull one operator-confirmed CONTROLLED_WINDOW lab event; seal and replay it.

Private raw/tensor/probability evidence stays outside Git and outside CORE5_DEVSET_V1.
The two checked-in CSVs contain scalar diagnostic results, never raw landmarks.
"""
from __future__ import annotations

import argparse
import csv
import hashlib
import json
import os
import re
import subprocess
from pathlib import Path

import numpy as np

from core5_contract import LABELS, VERSION, canonical, resample_timestamp
from core5_devset_v1 import MODEL_HASHES, model_outputs, write_new
from core5_same_tensor_ab import gate_on_saved_event

LAB_PACKAGE = "com.voxgest.dryrun.recognitionlab"
EVENT_ID = re.compile(r"[0-9a-f]{8}-(?:[0-9a-f]{4}-){3}[0-9a-f]{12}\Z")
ROOT = Path(__file__).resolve().parents[1]
REPORT = ROOT / "reports/core5_deadline_recovery_20261003"
MATRIX_FIELDS = ("trial_id", "event_id", "dataset_role", "intended", "android_model",
                 "raw_top1", "top1_confidence", "top2", "margin", "capture_duration_ms",
                 "raw_mediapipe_result_count", "envelope_frame_count", "nonzero_hand_presence",
                 "pose_presence", "termination", "gate_reason", "result", "tensor_sha256",
                 "android_desktop_max_probability_difference")
COMPARISON_FIELDS = ("trial_id", "event_id", "intended", "tensor_sha256", "model",
                     "model_sha256", "top1", "top1_confidence", "top2", "margin",
                     "p_hello", "p_thank_you", "p_yes", "p_no", "p_understand",
                     "gate_reason", "accepted", "android_desktop_max_probability_difference")


def validated_private_root(root: Path) -> Path:
    target = root.resolve()
    protected = (ROOT.resolve(), Path("D:/VoxGest/evidence/core5_devset_v1").resolve(),
                 Path("D:/VoxGest/evidence/fsl_core5_rebase_v1").resolve())
    if any(target == p or p in target.parents or target in p.parents for p in protected):
        raise ValueError("Deadline evidence must not overlap repo, devset, or sealed historical evidence")
    return target


def pull(adb: Path, serial: str, event_id: str) -> bytes:
    if not EVENT_ID.fullmatch(event_id):
        raise ValueError("Expected a single UUID event ID")
    result = subprocess.run([str(adb), "-s", serial, "exec-out", "run-as", LAB_PACKAGE,
                             "cat", f"files/core5_diagnostics/{event_id}.json"],
                            capture_output=True, check=False)
    if result.returncode or not result.stdout:
        raise RuntimeError("Cannot pull isolated lab event: " + result.stderr.decode("utf-8", "replace")[:250])
    return result.stdout


def validate_event(event: dict, event_id: str, intended: str) -> np.ndarray | None:
    if event.get("event_id") != event_id or event.get("expected_test_label") != intended:
        raise ValueError("Event ID or selected test label mismatch; require operator confirmation")
    if event.get("schema") != "core5_event_v1" or event.get("boundary_mode") != "CONTROLLED_WINDOW":
        raise ValueError("Not a new controlled-window lab event")
    if event.get("profile") != "FSL_CORE5_SIM10FPS_V1" or event.get("model_sha256") != MODEL_HASHES["sim10_output"]:
        raise ValueError("Wrong frozen SIM10 profile/model hash")
    if event.get("feature_version") != VERSION or event.get("analysis_mirrored") is not False:
        raise ValueError("Wrong feature contract or mirrored analysis")
    if event.get("labels") != LABELS or event.get("camera") != "FRONT":
        raise ValueError("Wrong labels/camera")
    if event.get("termination") != "CONTROLLED_WINDOW_END":
        raise ValueError("Controlled event did not complete its fixed window")
    frames = event.get("frames")
    if not isinstance(frames, list) or not frames:
        raise ValueError("Missing raw frame timeline")
    times = np.asarray([f["timestamp_ms"] for f in frames], dtype=np.int64)
    if len(times) > 1 and np.any(np.diff(times) <= 0):
        raise ValueError("Non-monotonic observations")
    vectors = []
    for frame in frames:
        def landmarks(name: str, present: str):
            value = np.asarray(frame[name], dtype=np.float32)
            if value.shape != ((33, 3) if name == "pose" else (21, 3)) or not np.isfinite(value).all():
                raise ValueError("Malformed raw landmarks")
            return value if frame[present] else None
        rebuilt = canonical(landmarks("pose", "pose_present"),
                            landmarks("left", "left_present"), landmarks("right", "right_present"))
        saved = np.asarray(frame["canonical"], dtype=np.float32)
        if saved.shape != (225,) or np.max(np.abs(rebuilt - saved)) > 1e-5:
            raise ValueError("Raw-to-canonical parity failed")
        vectors.append(saved)
    raw_tensor = event.get("tensor")
    if raw_tensor is None:
        if event.get("tensor_sha256") is not None:
            raise ValueError("Hash without tensor")
        return None
    tensor = np.asarray(raw_tensor, dtype=np.float32)
    if tensor.shape != (48, 225) or not np.isfinite(tensor).all():
        raise ValueError("Wrong final tensor shape/nonfinite")
    if hashlib.sha256(tensor.astype("<f4").tobytes()).hexdigest() != event.get("tensor_sha256"):
        raise ValueError("Final tensor SHA256 mismatch")
    included = [i for i, frame in enumerate(frames) if frame["included"]]
    if len(included) < 2:
        raise ValueError("Tensor lacks observed envelope")
    rebuilt, _ = resample_timestamp(np.asarray(vectors)[included], times[included])
    if np.max(np.abs(rebuilt - tensor)) > 1e-5:
        raise ValueError("Timestamp resample parity failed")
    return tensor


def export_reports(private: Path) -> dict:
    matrix, comparison = [], []
    for path in sorted((private / "analysis").glob("*.json")):
        trial = json.loads(path.read_text(encoding="utf-8"))
        raw = json.loads((private / "raw" / f"{trial['event_id']}.json").read_text(encoding="utf-8"))
        event = trial["event"]
        android_p = event["android_probabilities"]
        order = sorted(range(5), key=lambda i: android_p[i], reverse=True) if android_p else []
        raw_correct = event["raw_top1"] == trial["intended"]
        result = ("NO_INFERENCE" if not order else
                  ("CORRECT_ACCEPTED" if raw_correct else "WRONG_ACCEPTED") if event["accepted"] else
                  ("RAW_CORRECT_REJECTED" if raw_correct else "RAW_WRONG_REJECTED"))
        duration = (event["event_end_ms"] - event["event_start_ms"]
                    if event["event_end_ms"] is not None and event["event_start_ms"] is not None else None)
        matrix.append({"trial_id": trial["trial_id"], "event_id": trial["event_id"],
                       "dataset_role": trial["dataset_role"], "intended": trial["intended"],
                       "android_model": "SIM10", "raw_top1": event["raw_top1"],
                       "top1_confidence": event["confidence"],
                       "top2": LABELS[order[1]] if len(order) > 1 else None,
                       "margin": event["margin"], "capture_duration_ms": duration,
                       "raw_mediapipe_result_count": event["raw_frame_count"],
                       "envelope_frame_count": event["envelope_frame_count"],
                       "nonzero_hand_presence": trial["hand_presence"],
                       "pose_presence": trial["pose_presence"],
                       "termination": event["termination"], "gate_reason": event["gate_reason"],
                       "result": result, "tensor_sha256": trial["tensor_sha256"],
                       "android_desktop_max_probability_difference":
                           trial["android_desktop_max_probability_difference"]})
        for name, key in (("BASELINE", "baseline_output"), ("NATIVE48", "native48_output"),
                          ("SIM10", "sim10_output")):
            output = trial["outputs"][key] if trial["outputs"] else None
            if output is None:
                continue
            probabilities = output["probabilities"]
            ranked = sorted(range(5), key=lambda i: probabilities[i], reverse=True)
            gate = gate_on_saved_event(raw, probabilities)
            comparison.append({"trial_id": trial["trial_id"], "event_id": trial["event_id"],
                               "intended": trial["intended"], "tensor_sha256": trial["tensor_sha256"],
                               "model": name, "model_sha256": output["model_sha256"],
                               "top1": output["top1"], "top1_confidence": probabilities[ranked[0]],
                               "top2": LABELS[ranked[1]], "margin": probabilities[ranked[0]]-probabilities[ranked[1]],
                               **dict(zip(("p_hello", "p_thank_you", "p_yes", "p_no", "p_understand"), probabilities)),
                               "gate_reason": gate, "accepted": gate == "ACCEPTED",
                               "android_desktop_max_probability_difference":
                                   trial["android_desktop_max_probability_difference"] if name == "SIM10" else None})
    for name, fields, rows in (("SAMSUNG_CORE5_MATRIX.csv", MATRIX_FIELDS, matrix),
                               ("THREE_MODEL_COMPARISON.csv", COMPARISON_FIELDS, comparison)):
        target = REPORT / name
        temporary = target.with_suffix(".tmp")
        with temporary.open("w", newline="", encoding="utf-8") as stream:
            writer = csv.DictWriter(stream, fieldnames=fields)
            writer.writeheader()
            writer.writerows(rows)
        os.replace(temporary, target)
    return {"controlled_trials": len(matrix), "model_replays": len(comparison)}


def capture_and_replay(args):
    if args.intended not in LABELS or not args.operator_confirmed:
        raise ValueError("A Core5 intended sign and explicit operator confirmation are required")
    if not re.fullmatch(r"deadline_core5_(?:hello|thank_you|yes|no|understand)_[0-9]{3}", args.trial_id):
        raise ValueError("Unsafe or non-deterministic deadline trial ID")
    private = validated_private_root(args.private_root)
    raw = pull(args.adb, args.serial, args.event_id)
    event = json.loads(raw)
    tensor = validate_event(event, args.event_id, args.intended)
    outputs = model_outputs(tensor, args.baseline, args.bundle, args.bundle) if tensor is not None else None
    max_delta = None
    if outputs is not None:
        android = np.asarray(event["android_probabilities"], dtype=np.float32)
        sim = np.asarray(outputs["sim10_output"]["probabilities"], dtype=np.float32)
        if android.shape != (5,):
            raise ValueError("Wrong Android probability vector")
        max_delta = float(np.max(np.abs(android - sim)))
        if max_delta > 1e-5:
            raise ValueError("Android/Desktop SIM10 replay parity failed")
        if gate_on_saved_event(event, sim) != event["gate_reason"]:
            raise ValueError("Saved Android gate disagrees with offline gate")
    trial = {"trial_id": args.trial_id, "event_id": args.event_id,
             "intended": args.intended, "dataset_role": "DEADLINE_DIAGNOSTIC",
             "raw_capture_sha256": hashlib.sha256(raw).hexdigest(),
             "tensor_sha256": event.get("tensor_sha256"), "event": {
                 key: event.get(key) for key in ("raw_top1", "confidence", "margin", "gate_reason",
                     "accepted", "termination", "raw_frame_count", "envelope_frame_count",
                     "model_sha256", "android_probabilities", "event_start_ms", "event_end_ms")},
             "pose_presence": sum(bool(f["pose_present"]) for f in event["frames"] if f["included"]),
             "hand_presence": sum(bool(f["left_present"] or f["right_present"])
                                  for f in event["frames"] if f["included"]),
             "outputs": outputs, "android_desktop_max_probability_difference": max_delta}
    private.mkdir(parents=True, exist_ok=True)
    raw_path = private / "raw" / f"{args.event_id}.json"
    analysis_path = private / "analysis" / f"{args.trial_id}.json"
    if raw_path.exists() or analysis_path.exists():
        raise FileExistsError("Evidence already exists; never replace a trial")
    write_new(raw_path, raw)
    write_new(analysis_path, (json.dumps(trial, indent=2, allow_nan=False) + "\n").encode())
    trial["report_rows"] = export_reports(private)
    return trial


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--adb", type=Path, required=True)
    parser.add_argument("--serial", required=True)
    parser.add_argument("--event-id", required=True)
    parser.add_argument("--intended", required=True, choices=LABELS)
    parser.add_argument("--trial-id", required=True)
    parser.add_argument("--operator-confirmed", action="store_true")
    parser.add_argument("--private-root", type=Path, required=True)
    parser.add_argument("--baseline", type=Path, required=True)
    parser.add_argument("--bundle", type=Path, required=True)
    print(json.dumps(capture_and_replay(parser.parse_args()), indent=2, allow_nan=False))
