"""Read-only Baseline/Native48/SIM10 replay of saved Samsung Core5 tensors."""
from __future__ import annotations

import argparse
import csv
import hashlib
import json
import zipfile
from pathlib import Path

import numpy as np

from core5_contract import LABELS, VERSION, sha256

ROOT = Path(__file__).resolve().parents[1]
BASELINE = ROOT / "android_dry_run/app/src/debug/assets/model/fsl_core5_rebase_v1/core5_float32.tflite"
SIM10 = ROOT / "android_dry_run/app/src/debug/assets/model/fsl_core5_sparse10fps_candidate_v1/core5_float32.tflite"
EXPECTED_NATIVE = "3cff57f526fd0aab4d531855f838ee8c100a97958be39c7cbbdcbd6d30797def"
EXPECTED_SIM10 = "3702ff77c1c44a60f0dc7f06e19e778b6498df7dbf7e205991dc15158b8e888f"
NEGATIVE_LABELS = ("NON_SIGN:wave", "NON_SIGN:partial")


def gate_on_saved_event(event, probabilities):
    """Mirror Core5Contract.gate using the saved event's immutable quality fields."""
    reason = event["termination"]
    if reason in {"EVENT_TIMEOUT", "POSE_TRACKING_LOST", "INCOMPLETE_EVENT_REJECTED",
                  "LIFECYCLE_STOP", "NO_FRAMES_TIMEOUT", "CAPTURE_ERROR", "OPERATOR_CANCEL"}:
        return reason
    frames = [frame for frame in event["frames"] if frame["included"]]
    if len(frames) < 8:
        return "INCOMPLETE_EVENT"
    if frames[-1]["timestamp_ms"] - frames[0]["timestamp_ms"] > 8000:
        return "EVENT_TIMEOUT"
    if sum(bool(frame["pose_present"]) for frame in frames) / len(frames) < .65:
        return "LOW_POSE_PRESENCE"
    if sum(bool(frame["left_present"] or frame["right_present"]) for frame in frames) / len(frames) < .65:
        return "LOW_HAND_PRESENCE"
    if event["trajectory_motion_mean_l2"] < .02:
        return "LOW_TRAJECTORY_MOTION"
    ranked = sorted(probabilities, reverse=True)
    if ranked[0] < .95:
        return "LOW_CONFIDENCE"
    if ranked[0] - ranked[1] < .05:
        return "LOW_MARGIN"
    return "ACCEPTED"


def infer(interpreter, tensor):
    inp, out = interpreter.get_input_details()[0], interpreter.get_output_details()[0]
    if inp["shape"].tolist() != [1, 48, 225] or out["shape"].tolist() != [1, 5]:
        raise ValueError("TFLite shape mismatch")
    if inp["dtype"] != np.float32 or out["dtype"] != np.float32:
        raise ValueError("TFLite dtype mismatch")
    interpreter.set_tensor(inp["index"], tensor[None])
    interpreter.invoke()
    probabilities = interpreter.get_tensor(out["index"])[0].astype(np.float32)
    if probabilities.shape != (5,) or not np.isfinite(probabilities).all():
        raise ValueError("Invalid TFLite probabilities")
    return probabilities


def run(events_dir: Path, bundle: Path, output_dir: Path,
        expected_label: str = "HELLO", expected_count: int = 9,
        output_stem: str = "core5_hello_same_tensor_ab_20260927"):
    import tensorflow as tf

    if expected_label not in LABELS and expected_label not in NEGATIVE_LABELS:
        raise ValueError("Expected label must be a Core5 class or supported negative")
    if expected_count <= 0:
        raise ValueError("Expected count must be positive")
    if not output_stem.replace("_", "").replace("-", "").isalnum():
        raise ValueError("Unsafe output stem")

    base_manifest = json.loads((BASELINE.parent / "runtime_manifest.json").read_text(encoding="utf-8"))
    if sha256(BASELINE) != base_manifest["model_sha256"]:
        raise ValueError("Baseline model hash mismatch")
    if sha256(SIM10) != EXPECTED_SIM10:
        raise ValueError("Staged SIM10 model hash mismatch")
    with zipfile.ZipFile(bundle) as archive:
        native_bytes = archive.read("core5_native48_rdtcn_v1.tflite")
    if hashlib.sha256(native_bytes).hexdigest() != EXPECTED_NATIVE:
        raise ValueError("Native48 model hash mismatch")

    model_bytes = {
        "BASELINE": BASELINE.read_bytes(),
        "NATIVE48": native_bytes,
        "SIM10": SIM10.read_bytes(),
    }
    model_hashes = {name: hashlib.sha256(data).hexdigest() for name, data in model_bytes.items()}
    runners = {}
    for name, data in model_bytes.items():
        runner = tf.lite.Interpreter(model_content=data, num_threads=2)
        runner.allocate_tensors()
        runners[name] = runner

    records = []
    for path in sorted(events_dir.glob("*.json")):
        if path.name.startswith("startup-") or ".candidate-replay." in path.name:
            continue
        event = json.loads(path.read_text(encoding="utf-8"))
        if event.get("profile") != "FSL_CORE5_SIM10FPS_V1" or event.get("expected_test_label") != expected_label:
            continue
        if event.get("feature_version") != VERSION or event.get("model_sha256") != EXPECTED_SIM10:
            raise ValueError("Incompatible source event: " + path.name)
        if event.get("tensor") is None:
            raise ValueError("Source event has no final tensor: " + path.name)
        tensor = np.asarray(event["tensor"], dtype=np.float32)
        if tensor.shape != (48, 225) or not np.isfinite(tensor).all():
            raise ValueError("Invalid source tensor: " + path.name)
        digest = hashlib.sha256(tensor.astype("<f4").tobytes()).hexdigest()
        if digest != event.get("tensor_sha256"):
            raise ValueError("Source tensor hash mismatch: " + path.name)
        for model, runner in runners.items():
            vector = infer(runner, tensor)
            index = int(np.argmax(vector))
            row = {
                "event_id": event["event_id"],
                "source_file": path.name,
                "source_termination": event["termination"],
                "source_boundary_mode": event["boundary_mode"],
                "source_accepted": event["accepted"],
                "source_raw_top1": event["raw_top1"],
                "tracked_left_frames": sum(bool(f["left_present"]) for f in event["frames"]),
                "tracked_right_frames": sum(bool(f["right_present"]) for f in event["frames"]),
                "tensor_sha256": digest,
                "model": model,
                "model_sha256": model_hashes[model],
                "top1": LABELS[index],
                "confidence": float(vector[index]),
                "probabilities": [float(p) for p in vector],
            }
            row["same_gate_reason"] = gate_on_saved_event(event, vector)
            row["same_gate_accepted"] = row["same_gate_reason"] == "ACCEPTED"
            if model == "SIM10":
                android = np.asarray(event["android_probabilities"], dtype=np.float32)
                row["android_max_probability_difference"] = float(np.max(np.abs(vector - android)))
                if row["android_max_probability_difference"] > 1e-5:
                    raise ValueError("SIM10 Android/Desktop replay mismatch: " + path.name)
                if row["same_gate_reason"] != event["gate_reason"]:
                    raise ValueError("Saved Android gate disagrees with offline gate: " + path.name)
            records.append(row)
    if len(records) != expected_count * 3 or len({r["event_id"] for r in records}) != expected_count:
        raise ValueError(f"Expected exactly {expected_count} Samsung {expected_label} tensors and {expected_count * 3} replays; found {len(records)} replays")

    output_dir.mkdir(parents=True, exist_ok=True)
    json_path = output_dir / (output_stem + ".json")
    csv_path = output_dir / (output_stem + ".csv")
    if json_path.exists() or csv_path.exists():
        raise FileExistsError("A/B evidence already exists; preserve it")
    json_path.write_text(json.dumps({"contract": [1, 48, 225], "labels": LABELS,
                                     "records": records}, indent=2) + "\n", encoding="utf-8")
    with csv_path.open("w", newline="", encoding="utf-8") as stream:
        fields = ["event_id", "source_termination", "source_boundary_mode", "source_accepted",
                  "tracked_left_frames", "tracked_right_frames", "tensor_sha256",
                  "model", "model_sha256", "top1", "confidence", "same_gate_reason",
                  "same_gate_accepted"] + [f"p_{i}" for i in range(5)]
        writer = csv.DictWriter(stream, fieldnames=fields)
        writer.writeheader()
        for record in records:
            writer.writerow({**{key: record[key] for key in fields if key in record},
                             **{f"p_{i}": p for i, p in enumerate(record["probabilities"])}})
    counts = {name: {"raw_expected": sum(r["top1"] == expected_label for r in records if r["model"] == name),
                     "accepted_expected": sum(r["top1"] == expected_label and r["same_gate_accepted"] for r in records if r["model"] == name),
                     "accepted_any": sum(r["same_gate_accepted"] for r in records if r["model"] == name)}
              for name in model_bytes}
    return {"events": expected_count, "replays": len(records), "expected_label": expected_label,
            "model_counts": counts,
            "json": str(json_path), "csv": str(csv_path)}


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--events-dir", required=True, type=Path)
    parser.add_argument("--bundle", required=True, type=Path)
    parser.add_argument("--output-dir", required=True, type=Path)
    parser.add_argument("--expected-label", default="HELLO", choices=LABELS + list(NEGATIVE_LABELS))
    parser.add_argument("--expected-count", type=int, default=9)
    parser.add_argument("--output-stem", default="core5_hello_same_tensor_ab_20260927")
    args = parser.parse_args()
    print(json.dumps(run(args.events_dir, args.bundle, args.output_dir,
                         args.expected_label, args.expected_count, args.output_stem), indent=2))
