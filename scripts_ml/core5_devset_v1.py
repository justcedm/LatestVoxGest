"""CORE5_DEVSET_V1 offline planning, sealing, ingest, replay, and validation.

No training, threshold search, Android mutation, or historical-event import.
Collection is restricted to the separate recognition-lab package by the host
capture companion; this importer also rejects matching historical IDs/hashes.
"""
from __future__ import annotations

import argparse
import datetime as dt
import hashlib
import json
import os
import re
import tempfile
import zipfile
from collections import Counter
from pathlib import Path

import numpy as np

from core5_contract import LABELS, VERSION, canonical, resample_timestamp

SCHEMA = "CORE5_DEVSET_V1"
LAB_PACKAGE = "com.voxgest.dryrun.recognitionlab"
ROLES = ("DEV_TUNE", "DEV_HOLDOUT", "SEALED_FINAL")
TARGETS = (("HELLO", 10), ("THANK YOU", 10), ("YES", 15), ("NO", 15),
           ("UNDERSTAND", 10), ("RANDOM_NON_FSL", 10),
           ("PARTIAL_ABORTED", 10), ("NEUTRAL", 10))
NEGATIVE_TO_APP = {"RANDOM_NON_FSL": "NON_SIGN:wave",
                   "PARTIAL_ABORTED": "NON_SIGN:partial", "NEUTRAL": "NON_SIGN:neutral"}
MODEL_HASHES = {
    "baseline_output": "3518ddeb68e69afa37428b8c5fc08b9d3b93e396fa1549224493da293f0ea484",
    "native48_output": "3cff57f526fd0aab4d531855f838ee8c100a97958be39c7cbbdcbd6d30797def",
    "sim10_output": "3702ff77c1c44a60f0dc7f06e19e778b6498df7dbf7e205991dc15158b8e888f",
}
RATES = (60, 30, 20, 15, 12, 10, 8)
ALIAS = re.compile(r"[A-Za-z][A-Za-z0-9_-]{0,31}\Z")
TRIAL = re.compile(r"core5dev_v1__[A-Za-z][A-Za-z0-9_-]*__[A-Za-z][A-Za-z0-9_-]*__"
                   r"(?:HELLO|THANK_YOU|YES|NO|UNDERSTAND|RANDOM_NON_FSL|PARTIAL_ABORTED|NEUTRAL)__trial\d{3}\Z")


def digest(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def json_bytes(value) -> bytes:
    return (json.dumps(value, indent=2, sort_keys=True, allow_nan=False) + "\n").encode("utf-8")


def write_new(path: Path, data: bytes):
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("xb") as stream:
        stream.write(data)


def atomic_manifest(root: Path, manifest: dict):
    path = root / "manifest.json"
    content = json_bytes(manifest)
    if path.exists():
        snapshots = root / "manifest_snapshots"
        snapshots.mkdir(exist_ok=True)
        original = path.read_bytes()
        snapshot = snapshots / (digest(original) + ".json")
        if snapshot.exists():
            if snapshot.read_bytes() != original:
                raise ValueError("Manifest snapshot hash collision")
        else:
            write_new(snapshot, original)
    with tempfile.NamedTemporaryFile(mode="wb", prefix=".manifest-", suffix=".tmp",
                                     dir=root, delete=False) as stream:
        stream.write(content)
        temporary = Path(stream.name)
    os.replace(temporary, path)


def load(root: Path) -> dict:
    manifest = json.loads((root / "manifest.json").read_text(encoding="utf-8"))
    if manifest.get("schema_version") != SCHEMA or manifest.get("historical_role") != "SEALED_HISTORICAL_DIAGNOSTIC":
        raise ValueError("Wrong devset manifest or missing historical seal")
    return manifest


def separated(a: Path, b: Path):
    left, right = a.resolve(), b.resolve()
    if left == right or left in right.parents or right in left.parents:
        raise ValueError("Devset and sealed historical roots must be disjoint")


def historical_registry(historical: Path):
    records = []
    for path in sorted(historical.glob("*.json")):
        if path.name.startswith("startup-") or ".candidate-replay." in path.name or ".android-replay-" in path.name:
            continue
        raw = path.read_bytes()
        event = json.loads(raw)
        if event.get("schema") != "core5_event_v1":
            continue
        records.append({"event_id": event["event_id"], "sha256": digest(raw)})
    if len(records) < 69 or len({r["event_id"] for r in records}) != len(records):
        raise ValueError("Historical event files missing or duplicated")
    return records


def historical_matrix(historical: Path):
    matrix = historical.parent / "core5_samsung_event_matrix_20260927.json"
    raw = matrix.read_bytes()
    rows = json.loads(raw)["events"]
    ids = [row["event_id"] for row in rows]
    if len(ids) != 69 or len(set(ids)) != 69:
        raise ValueError("Authoritative historical matrix is not 69 unique events")
    return ids, digest(raw)


def init(root: Path, historical: Path):
    separated(root, historical)
    if root.exists():
        raise FileExistsError("Never merge a new devset into an existing directory")
    records = historical_registry(historical)
    matrix_ids, matrix_hash = historical_matrix(historical)
    if not set(matrix_ids).issubset({row["event_id"] for row in records}):
        raise ValueError("Historical matrix event missing from raw evidence folder")
    root.mkdir(parents=True)
    seal = {"role": "SEALED_HISTORICAL_DIAGNOSTIC", "event_count": 69,
            "historical_root": str(historical.resolve()), "records": records,
            "matrix_event_ids": matrix_ids, "matrix_sha256": matrix_hash,
            "training_allowed": False, "threshold_fitting_allowed": False}
    write_new(root / "sealed_historical.json", json_bytes(seal))
    manifest = {"schema_version": SCHEMA, "created_utc": dt.datetime.now(dt.timezone.utc).isoformat(),
                "historical_role": "SEALED_HISTORICAL_DIAGNOSTIC",
                "historical_seal_sha256": digest((root / "sealed_historical.json").read_bytes()),
                "source_policy": "NEW_RECOGNITION_LAB_ONLY; NO_HISTORICAL_IMPORT",
                "training_started": False, "threshold_tuning_started": False,
                "lanes": {}, "slots": {}}
    atomic_manifest(root, manifest)
    return {"root": str(root), "sealed_historical_events": 69,
            "excluded_historical_raw_files": len(records), "planned_trials": 0}


def trial_id(signer: str, device: str, label: str, index: int) -> str:
    if not ALIAS.fullmatch(signer) or not ALIAS.fullmatch(device):
        raise ValueError("Signer/device aliases must be safe, explicit identifiers")
    token = label.replace(" ", "_")
    if token not in {name.replace(" ", "_") for name, _ in TARGETS} or not 1 <= index <= 999:
        raise ValueError("Invalid target or trial index")
    result = f"core5dev_v1__{signer}__{device}__{token}__trial{index:03d}"
    assert TRIAL.fullmatch(result)
    return result


def role_for(index: int, policy: str) -> str:
    if policy == "WITHIN_LANE":
        return ("DEV_TUNE", "DEV_TUNE", "DEV_HOLDOUT", "DEV_TUNE", "SEALED_FINAL")[(index - 1) % 5]
    fixed = {"TUNE_LANE": "DEV_TUNE", "HOLDOUT_LANE": "DEV_HOLDOUT", "SEALED_LANE": "SEALED_FINAL"}
    if policy not in fixed:
        raise ValueError("Unknown immutable lane-role policy")
    return fixed[policy]


def add_lane(root: Path, signer: str, device: str, policy: str):
    manifest = load(root)
    if not ALIAS.fullmatch(signer) or not ALIAS.fullmatch(device):
        raise ValueError("Invalid explicit signer/device alias")
    lane = f"{signer}__{device}"
    if lane in manifest["lanes"]:
        raise FileExistsError("Lane already planned; roles cannot be reassigned")
    manifest["lanes"][lane] = {"signer_alias": signer, "device_alias": device,
                                "role_policy": policy, "target_count": 90,
                                "device_serial_sha256": None}
    for label, count in TARGETS:
        for index in range(1, count + 1):
            name = trial_id(signer, device, label, index)
            manifest["slots"][name] = {"lane": lane, "target": label,
                                         "role": role_for(index, policy), "status": "PLANNED",
                                         "source_event_id": None, "raw_capture_sha256": None,
                                         "tensor_sha256": None}
    atomic_manifest(root, manifest)
    return {"lane": lane, "planned": 90,
            "roles": dict(Counter(row["role"] for row in manifest["slots"].values() if row["lane"] == lane))}


def bind_device(root: Path, lane: str, serial: str):
    """Pin a lane to one physical serial without storing that serial in the manifest."""
    manifest = load(root)
    if lane not in manifest["lanes"] or not serial:
        raise ValueError("Unknown lane or empty device serial")
    serial_hash = digest(serial.encode("utf-8"))
    prior = manifest["lanes"][lane]["device_serial_sha256"]
    if prior is not None and prior != serial_hash:
        raise ValueError("Physical device differs from immutable lane binding")
    if prior is None:
        manifest["lanes"][lane]["device_serial_sha256"] = serial_hash
        atomic_manifest(root, manifest)
    return serial_hash


def seal(root: Path):
    manifest = load(root)
    raw = (root / "sealed_historical.json").read_bytes()
    if digest(raw) != manifest["historical_seal_sha256"]:
        raise ValueError("Historical seal changed")
    record = json.loads(raw)
    separated(root, Path(record["historical_root"]))
    if record["role"] != "SEALED_HISTORICAL_DIAGNOSTIC" or record["training_allowed"] or record["threshold_fitting_allowed"]:
        raise ValueError("Invalid historical role")
    if historical_registry(Path(record["historical_root"])) != record["records"]:
        raise ValueError("Historical event IDs or bytes changed")
    matrix_ids, matrix_hash = historical_matrix(Path(record["historical_root"]))
    if matrix_ids != record["matrix_event_ids"] or matrix_hash != record["matrix_sha256"]:
        raise ValueError("Historical 69-event matrix changed")
    return record


def validate_markers(markers: dict, event_start: int, event_end: int):
    names = ("SIGN_START", "SIGN_END", "RETURN_NEUTRAL", "SAFE_REARM")
    values = []
    for name in names:
        value = markers.get(name)
        if type(value) is not int:
            raise ValueError("Missing integer human marker " + name)
        values.append(value)
    if not event_start <= values[0] < values[1] <= values[2] <= values[3] <= event_end:
        raise ValueError("Human marker sequence outside capture interval")
    hold_start, hold_end = markers.get("HOLD_START"), markers.get("HOLD_END")
    if (hold_start is None) != (hold_end is None):
        raise ValueError("HOLD markers must be paired")
    if hold_start is not None and not (values[0] <= hold_start < hold_end <= values[2]):
        raise ValueError("Invalid hold interval")
    uncertainty = markers.get("clock_uncertainty_ms")
    if not isinstance(uncertainty, (int, float)) or not 0 <= uncertainty <= 100:
        raise ValueError("Clock synchronization uncertainty exceeds 100 ms")


def verify_event(raw: bytes, expected: str):
    event = json.loads(raw)
    if event.get("schema") != "core5_event_v1" or event.get("feature_version") != VERSION:
        raise ValueError("Wrong event/feature contract")
    if event.get("expected_test_label") != NEGATIVE_TO_APP.get(expected, expected):
        raise ValueError("App-selected label differs from planned trial")
    if event.get("labels") != LABELS or event.get("capture_origin") != "ANDROID_CAMERA":
        raise ValueError("Wrong labels or non-camera origin")
    if event.get("profile") != "FSL_CORE5_SIM10FPS_V1" or event.get("model_sha256") != MODEL_HASHES["sim10_output"]:
        raise ValueError("Event was not saved by the frozen SIM10 lab candidate")
    if event.get("analysis_mirrored") is not False or event.get("camera") != "FRONT":
        raise ValueError("Unsupported camera/anatomy evidence")
    if event.get("boundary_mode") != "MANUAL":
        raise ValueError("Devset requires manual capture boundaries")
    frames = event.get("frames")
    if not isinstance(frames, list) or len(frames) < 2:
        raise ValueError("No usable raw frame timeline")
    if len({frame["rotation_degrees"] for frame in frames}) != 1:
        raise ValueError("Camera orientation changed during trial")
    times = np.asarray([f["timestamp_ms"] for f in frames], dtype=np.int64)
    if np.any(np.diff(times) <= 0):
        raise ValueError("Non-monotonic event timestamps")
    raw_landmarks = []
    presence = []
    canonicals = []
    for frame in frames:
        pose = np.asarray(frame["pose"], dtype=np.float32)
        left = np.asarray(frame["left"], dtype=np.float32)
        right = np.asarray(frame["right"], dtype=np.float32)
        if pose.shape != (33, 3) or left.shape != (21, 3) or right.shape != (21, 3):
            raise ValueError("Wrong raw landmark layout")
        flags = [bool(frame["pose_present"]), bool(frame["left_present"]), bool(frame["right_present"])]
        rebuilt = canonical(pose if flags[0] else None, left if flags[1] else None, right if flags[2] else None)
        device_vector = np.asarray(frame["canonical"], dtype=np.float32)
        if device_vector.shape != (225,) or float(np.max(np.abs(rebuilt - device_vector))) > 1e-5:
            raise ValueError("Raw-to-canonical landmark parity failed")
        raw_landmarks.append(np.concatenate([pose.ravel(), left.ravel(), right.ravel()]))
        presence.append(flags)
        canonicals.append(device_vector)
    tensor = None
    if event.get("tensor") is not None:
        tensor = np.asarray(event["tensor"], dtype=np.float32)
        if tensor.shape != (48, 225) or not np.isfinite(tensor).all():
            raise ValueError("Wrong or nonfinite final tensor")
        if digest(tensor.astype("<f4").tobytes()) != event.get("tensor_sha256"):
            raise ValueError("Final tensor SHA256 mismatch")
        indices = [i for i, frame in enumerate(frames) if frame["included"]]
        if len(indices) < 2:
            raise ValueError("Tensor exists without included observations")
        rebuilt, _ = resample_timestamp(np.asarray(canonicals)[indices], times[indices])
        if float(np.max(np.abs(rebuilt - tensor))) > 1e-5:
            raise ValueError("Pre-resample event reconstruction failed")
    elif event.get("tensor_sha256") is not None:
        raise ValueError("Hash without tensor")
    return event, np.asarray(raw_landmarks, dtype=np.float32), times, np.asarray(presence, dtype=bool), np.asarray(canonicals), tensor


def model_outputs(tensor, baseline: Path, native_zip: Path, sim: Path):
    if tensor is None:
        return {key: None for key in MODEL_HASHES}
    import tensorflow as tf
    with zipfile.ZipFile(native_zip) as archive:
        native_bytes = archive.read("core5_native48_rdtcn_v1.tflite")
    if sim.suffix.lower() == ".zip":
        with zipfile.ZipFile(sim) as archive:
            sim_bytes = archive.read("core5_sim10fps48_rdtcn_v1.tflite")
    else:
        sim_bytes = sim.read_bytes()
    assets = {"baseline_output": baseline.read_bytes(),
              "native48_output": native_bytes, "sim10_output": sim_bytes}
    output = {}
    for name, content in assets.items():
        if digest(content) != MODEL_HASHES[name]:
            raise ValueError("Frozen model hash mismatch: " + name)
        interpreter = tf.lite.Interpreter(model_content=content, num_threads=2)
        interpreter.allocate_tensors()
        inp, out = interpreter.get_input_details()[0], interpreter.get_output_details()[0]
        if inp["shape"].tolist() != [1, 48, 225] or out["shape"].tolist() != [1, 5]:
            raise ValueError("Wrong frozen model shape")
        interpreter.set_tensor(inp["index"], tensor[None])
        interpreter.invoke()
        probabilities = interpreter.get_tensor(out["index"])[0].astype(np.float32)
        if probabilities.shape != (5,) or not np.isfinite(probabilities).all():
            raise ValueError("Invalid frozen model output")
        output[name] = {"model_sha256": MODEL_HASHES[name], "top1": LABELS[int(np.argmax(probabilities))],
                        "probabilities": probabilities.tolist()}
    return output


def presence_summary(flags: np.ndarray, column: int):
    count = int(flags[:, column].sum())
    total = len(flags)
    return {"count": count, "total": total, "ratio": count / total if total else None}


def validate_metadata(metadata: dict):
    schema_path = Path(__file__).resolve().parents[1] / "reports/fsl_core5_rebase_v1/CORE5_DEVSET_V1_TRIAL_SCHEMA.json"
    schema = json.loads(schema_path.read_text(encoding="utf-8"))
    required = set(schema["required"])
    permitted = required | {"hold_start_ms", "hold_end_ms"}
    if set(metadata) != permitted or metadata["schema_version"] != SCHEMA:
        raise ValueError("Trial metadata does not match versioned schema fields")
    if not TRIAL.fullmatch(metadata["trial_id"]) or metadata["dataset_role"] not in ROLES:
        raise ValueError("Invalid trial ID or role")
    positive = metadata["intended_label"] in LABELS
    negative = metadata["negative_type"] in NEGATIVE_TO_APP
    if positive == negative or metadata["capture_package"] != LAB_PACKAGE:
        raise ValueError("Wrong trial class or capture package")
    if metadata["raw_landmark_path"] != "raw_landmarks.npz":
        raise ValueError("Raw landmark path mismatch")
    if metadata["negative_type"] is not None and metadata["intended_label"] is not None:
        raise ValueError("Positive and negative labels cannot coexist")
    if not ALIAS.fullmatch(metadata["signer_alias"]) or not ALIAS.fullmatch(metadata["device_alias"]):
        raise ValueError("Unsafe signer/device alias")
    if metadata["trial_id"] != trial_id(metadata["signer_alias"], metadata["device_alias"],
                                         metadata["intended_label"] or metadata["negative_type"],
                                         int(metadata["trial_id"].rsplit("trial", 1)[1])):
        raise ValueError("Trial ID and metadata identity disagree")
    if not re.fullmatch(r"FRONT_ROTATION_(?:0|90|180|270)", metadata["camera_orientation"]):
        raise ValueError("Unsupported camera orientation")
    for key in ("human_sign_start_ms", "human_sign_end_ms", "return_neutral_ms", "safe_rearm_ms"):
        if type(metadata[key]) is not int:
            raise ValueError("Human marker must be integer milliseconds")
    if not (metadata["human_sign_start_ms"] < metadata["human_sign_end_ms"] <=
            metadata["return_neutral_ms"] <= metadata["safe_rearm_ms"]):
        raise ValueError("Human marker order changed")
    if not isinstance(metadata["notes"], str) or not isinstance(metadata["source_event_id"], str):
        raise ValueError("Malformed notes/source ID")
    for key in ("raw_capture_sha256", "tensor_sha256"):
        value = metadata[key]
        if value is not None and not re.fullmatch(r"[0-9a-f]{64}", value):
            raise ValueError("Malformed evidence SHA256")
    if not isinstance(metadata["marker_clock_uncertainty_ms"], (int, float)) or not 0 <= metadata["marker_clock_uncertainty_ms"] <= 100:
        raise ValueError("Marker clock uncertainty invalid")
    if metadata["effective_result_rate"] is not None and metadata["effective_result_rate"] <= 0:
        raise ValueError("Invalid effective result rate")
    if not isinstance(metadata["files"], dict) or not metadata["files"]:
        raise ValueError("Missing artifact hashes")
    if metadata["linguistic_ground_truth_validated"] is not False or metadata["supervised_training_allowed"] is not False:
        raise ValueError("Unreviewed captures cannot be supervised training data")
    if type(metadata["capture_valid"]) is not bool or metadata["capture_valid"] != (metadata["invalid_reason"] is None):
        raise ValueError("Capture-valid/invalid-reason mismatch")
    captured = dt.datetime.fromisoformat(metadata["capture_timestamp"])
    if captured.tzinfo is None or captured.utcoffset() != dt.timedelta(0):
        raise ValueError("Capture timestamp must be UTC")
    for key in ("baseline_output", "native48_output", "sim10_output"):
        value = metadata[key]
        if metadata["tensor_sha256"] is None:
            if value is not None:
                raise ValueError("Model output without tensor")
            continue
        probabilities = value["probabilities"]
        if value["model_sha256"] != MODEL_HASHES[key] or value["top1"] not in LABELS or len(probabilities) != 5:
            raise ValueError("Wrong frozen replay output")
        if not np.isfinite(probabilities).all() or abs(sum(probabilities) - 1) > 1e-3:
            raise ValueError("Invalid model probability vector")
        if value["top1"] != LABELS[int(np.argmax(probabilities))]:
            raise ValueError("Model top-1 disagrees with probability vector")
    for key in ("pose_presence", "left_hand_presence", "right_hand_presence"):
        presence = metadata[key]
        expected_ratio = presence["count"] / presence["total"] if presence["total"] else None
        if not 0 <= presence["count"] <= presence["total"] or presence["ratio"] != expected_ratio:
            raise ValueError("Invalid landmark presence summary")
    for name, value in metadata["files"].items():
        if Path(name).name != name or not re.fullmatch(r"[0-9a-f]{64}", value):
            raise ValueError("Malformed artifact manifest")
    return True


def ingest(root: Path, name: str, source: Path, annotations_path: Path,
           baseline: Path, native_zip: Path, sim: Path, notes: str = ""):
    manifest = load(root)
    historical = seal(root)
    if not TRIAL.fullmatch(name) or name not in manifest["slots"]:
        raise ValueError("Trial was not preplanned")
    slot = manifest["slots"][name]
    if slot["status"] != "PLANNED":
        raise FileExistsError("Trial already consumed; never overwrite")
    source_resolved = source.resolve(strict=True)
    historical_root = Path(historical["historical_root"]).resolve()
    if source_resolved == historical_root or historical_root in source_resolved.parents:
        raise ValueError("Historical raw event cannot enter new devset")
    raw = source.read_bytes()
    event, landmarks, times, flags, canonical_frames, tensor = verify_event(raw, slot["target"])
    source_hash = digest(raw)
    historical_ids = {r["event_id"] for r in historical["records"]}
    historical_hashes = {r["sha256"] for r in historical["records"]}
    if event["event_id"] in historical_ids or source_hash in historical_hashes:
        raise ValueError("Sealed historical event duplicate")
    for other in manifest["slots"].values():
        if other["source_event_id"] == event["event_id"] or other["raw_capture_sha256"] == source_hash:
            raise ValueError("Cross-role or cross-lane raw event duplicate")
        if tensor is not None and other["tensor_sha256"] == event["tensor_sha256"]:
            raise ValueError("Exact tensor reused across trial roles/lanes")
    annotations = json.loads(annotations_path.read_text(encoding="utf-8"))
    if annotations.get("trial_id") != name or annotations.get("source_event_id") != event["event_id"]:
        raise ValueError("Annotation does not identify this planned raw event")
    if annotations.get("capture_package") != LAB_PACKAGE:
        raise ValueError("Annotations lack recognition-lab package attestation")
    validate_markers(annotations, int(times[0]), int(times[-1]))
    outputs = model_outputs(tensor, baseline, native_zip, sim)
    invalid = None
    if event.get("termination") != "MANUAL_END":
        invalid = "END_" + str(event.get("termination"))
    if event.get("inference_error"):
        invalid = "INFERENCE_ERROR"
    if slot["target"] in LABELS and tensor is None:
        invalid = "POSITIVE_NO_TENSOR"
    if slot["target"] in ("RANDOM_NON_FSL", "PARTIAL_ABORTED") and tensor is None:
        invalid = "MOTION_NEGATIVE_NO_TENSOR"
    lane = slot["lane"]
    signer, device = lane.split("__", 1)
    parent = root / "trials" / lane
    destination = parent / name
    if destination.exists():
        raise FileExistsError("Trial directory already exists")
    parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix=".staging-", dir=parent) as staging_name:
        staging = Path(staging_name)
        write_new(staging / "raw_capture.json", raw)
        np.savez_compressed(staging / "raw_landmarks.npz", raw_landmarks=landmarks,
                            canonical_frames=canonical_frames, timestamps_ms=times, presence=flags,
                            included=np.asarray([f["included"] for f in event["frames"]], dtype=bool))
        if tensor is not None:
            write_new(staging / "tensor.bin", tensor.astype("<f4").tobytes())
        write_new(staging / "annotations.json", json_bytes(annotations))
        write_new(staging / "model_outputs.json", json_bytes(outputs))
        files = {p.name: digest(p.read_bytes()) for p in sorted(staging.iterdir()) if p.is_file()}
        metadata = {"schema_version": SCHEMA, "trial_id": name, "dataset_role": slot["role"],
                    "intended_label": slot["target"] if slot["target"] in LABELS else None,
                    "negative_type": slot["target"] if slot["target"] not in LABELS else None,
                    "signer_alias": signer, "device_alias": device,
                    "capture_timestamp": annotations["capture_timestamp"],
                    "camera_orientation": f"{event['camera']}_ROTATION_{event['frames'][0]['rotation_degrees']}",
                    "effective_result_rate": event.get("processed_fps"),
                    "human_sign_start_ms": annotations["SIGN_START"],
                    "human_sign_end_ms": annotations["SIGN_END"],
                    "return_neutral_ms": annotations["RETURN_NEUTRAL"],
                    "safe_rearm_ms": annotations["SAFE_REARM"],
                    "hold_start_ms": annotations.get("HOLD_START"),
                    "hold_end_ms": annotations.get("HOLD_END"),
                    "pose_presence": presence_summary(flags, 0),
                    "left_hand_presence": presence_summary(flags, 1),
                    "right_hand_presence": presence_summary(flags, 2),
                    "raw_landmark_path": "raw_landmarks.npz",
                    "tensor_sha256": event.get("tensor_sha256"),
                    "raw_capture_sha256": source_hash,
                    **outputs, "capture_valid": invalid is None, "invalid_reason": invalid,
                    "notes": notes, "capture_package": LAB_PACKAGE,
                    "source_event_id": event["event_id"],
                    "linguistic_ground_truth_validated": False,
                    "supervised_training_allowed": False,
                    "marker_clock_uncertainty_ms": annotations["clock_uncertainty_ms"],
                    "files": files}
        validate_metadata(metadata)
        write_new(staging / "metadata.json", json_bytes(metadata))
        os.rename(staging, destination)
    slot.update(status="CAPTURED_VALID" if invalid is None else "CAPTURED_INVALID",
                source_event_id=event["event_id"], raw_capture_sha256=source_hash,
                tensor_sha256=event.get("tensor_sha256"),
                trial_path=str(destination.relative_to(root)).replace("\\", "/"))
    atomic_manifest(root, manifest)
    return {"trial_id": name, "role": slot["role"], "capture_valid": invalid is None,
            "invalid_reason": invalid, "directory": str(destination)}


def cadence_variants(times, vectors, effective_rate_hz, rates=RATES):
    """Select observed frames only; never synthesize higher-than-observed motion."""
    times = np.asarray(times, dtype=np.int64)
    vectors = np.asarray(vectors, dtype=np.float32)
    if len(times) < 2 or vectors.shape != (len(times), 225) or np.any(np.diff(times) <= 0):
        raise ValueError("Invalid pre-resample trajectory")
    if effective_rate_hz <= 0:
        raise ValueError("Invalid observed rate")
    results = {}
    for rate in rates:
        if rate > effective_rate_hz * 1.05:
            results[str(rate)] = {"status": "UNAVAILABLE_OBSERVED_RATE_TOO_LOW",
                                  "observed_rate_hz": effective_rate_hz}
            continue
        targets = np.arange(times[0], times[-1] + 1, 1000. / rate)
        indices = np.unique([0, *(int(np.argmin(np.abs(times - t))) for t in targets), len(times) - 1])
        tensor, _ = resample_timestamp(vectors[indices], times[indices])
        results[str(rate)] = {"status": "READY_OFFLINE_ONLY", "selected_observations": len(indices),
                              "tensor_sha256": digest(tensor.astype("<f4").tobytes()), "tensor": tensor}
    return results


def cadence_export(trial_dir: Path, output_dir: Path):
    if output_dir.exists():
        raise FileExistsError("Preserve earlier cadence export")
    if trial_dir.resolve() == output_dir.resolve() or trial_dir.resolve() in output_dir.resolve().parents:
        raise ValueError("Cadence export must be outside the immutable trial directory")
    metadata = json.loads((trial_dir / "metadata.json").read_text(encoding="utf-8"))
    validate_metadata(metadata)
    with np.load(trial_dir / "raw_landmarks.npz", allow_pickle=False) as saved:
        included = saved["included"]
        times = saved["timestamps_ms"][included]
        vectors = saved["canonical_frames"][included]
    if len(times) < 2:
        raise ValueError("No observed sign envelope for cadence export")
    effective = (len(times) - 1) * 1000 / (times[-1] - times[0])
    candidates = cadence_variants(times, vectors, effective)
    output_dir.mkdir(parents=True)
    summary = {"schema_version": SCHEMA, "trial_id": metadata["trial_id"],
               "source_raw_capture_sha256": metadata["raw_capture_sha256"],
               "training_performed": False, "source_observed_rate_hz": effective, "rates": {}}
    for rate, candidate in candidates.items():
        row = {k: v for k, v in candidate.items() if k != "tensor"}
        if candidate["status"] == "READY_OFFLINE_ONLY":
            write_new(output_dir / f"cadence_{rate}fps_tensor.bin", candidate["tensor"].astype("<f4").tobytes())
        summary["rates"][rate] = row
    write_new(output_dir / "cadence_manifest.json", json_bytes(summary))
    return {"trial_id": metadata["trial_id"], "rates": {key: value["status"] for key, value in summary["rates"].items()},
            "output": str(output_dir)}


def validate_dataset(root: Path):
    manifest = load(root)
    historical = seal(root)
    seen_ids, seen_raw, seen_tensor = set(), set(), set()
    for name, slot in manifest["slots"].items():
        if not TRIAL.fullmatch(name) or slot["role"] not in ROLES:
            raise ValueError("Invalid planned trial or role")
        if slot["status"] == "PLANNED":
            if slot["source_event_id"] is not None or slot["raw_capture_sha256"] is not None:
                raise ValueError("Planned slot contains evidence")
            continue
        if slot["status"] not in ("CAPTURED_VALID", "CAPTURED_INVALID"):
            raise ValueError("Unknown trial state")
        path = root / slot["trial_path"]
        if path.resolve().parent.parent != (root / "trials").resolve() or path.name != name:
            raise ValueError("Trial path escapes devset")
        metadata = json.loads((path / "metadata.json").read_text(encoding="utf-8"))
        validate_metadata(metadata)
        if metadata["trial_id"] != name or metadata["dataset_role"] != slot["role"]:
            raise ValueError("Cross-role metadata mismatch")
        if metadata["source_event_id"] != slot["source_event_id"] or metadata["raw_capture_sha256"] != slot["raw_capture_sha256"] or metadata["tensor_sha256"] != slot["tensor_sha256"]:
            raise ValueError("Manifest evidence hashes/IDs disagree with trial")
        if metadata["capture_valid"] != (slot["status"] == "CAPTURED_VALID"):
            raise ValueError("Manifest capture-valid state mismatch")
        for file, expected_hash in metadata["files"].items():
            if Path(file).name != file or digest((path / file).read_bytes()) != expected_hash:
                raise ValueError("Trial artifact hash mismatch")
        if digest((path / "raw_capture.json").read_bytes()) != metadata["raw_capture_sha256"]:
            raise ValueError("Raw capture SHA mismatch")
        if metadata["tensor_sha256"] is not None and digest((path / "tensor.bin").read_bytes()) != metadata["tensor_sha256"]:
            raise ValueError("Tensor SHA mismatch")
        for item, value, seen in (("event", slot["source_event_id"], seen_ids),
                                  ("raw", slot["raw_capture_sha256"], seen_raw),
                                  ("tensor", slot["tensor_sha256"], seen_tensor)):
            if value is not None and value in seen:
                raise ValueError("Cross-role duplicate " + item)
            if value is not None:
                seen.add(value)
        if slot["source_event_id"] in {r["event_id"] for r in historical["records"]}:
            raise ValueError("Historical ID imported")
    for lane, settings in manifest["lanes"].items():
        signer, device = settings["signer_alias"], settings["device_alias"]
        if lane != f"{signer}__{device}" or not ALIAS.fullmatch(signer) or not ALIAS.fullmatch(device):
            raise ValueError("Invalid signer/device lane identity")
        expected = {trial_id(signer, device, label, index): (label, role_for(index, settings["role_policy"]))
                    for label, count in TARGETS for index in range(1, count + 1)}
        actual = {name: row for name, row in manifest["slots"].items() if row["lane"] == lane}
        if len(actual) != 90 or settings["target_count"] != 90 or set(actual) != set(expected):
            raise ValueError("Lane does not have exactly 90 planned slots")
        for name, (label, role) in expected.items():
            if actual[name]["target"] != label or actual[name]["role"] != role:
                raise ValueError("Preassigned trial label/role was changed")
    return {"lanes": len(manifest["lanes"]), "planned": sum(r["status"] == "PLANNED" for r in manifest["slots"].values()),
            "captured": sum(r["status"] != "PLANNED" for r in manifest["slots"].values()),
            "sealed_historical": historical["event_count"],
            "excluded_historical_raw_files": len(historical["records"])}


def cli():
    parser = argparse.ArgumentParser()
    sub = parser.add_subparsers(dest="command", required=True)
    p = sub.add_parser("init")
    p.add_argument("--root", type=Path, required=True)
    p.add_argument("--historical-dir", type=Path, required=True)
    p = sub.add_parser("add-lane")
    p.add_argument("--root", type=Path, required=True)
    p.add_argument("--signer", required=True)
    p.add_argument("--device", required=True)
    p.add_argument("--policy", choices=("WITHIN_LANE", "TUNE_LANE", "HOLDOUT_LANE", "SEALED_LANE"), default="WITHIN_LANE")
    p = sub.add_parser("validate")
    p.add_argument("--root", type=Path, required=True)
    p = sub.add_parser("cadence-export")
    p.add_argument("--trial-dir", type=Path, required=True)
    p.add_argument("--output-dir", type=Path, required=True)
    p = sub.add_parser("ingest")
    p.add_argument("--root", type=Path, required=True)
    p.add_argument("--trial-id", required=True)
    p.add_argument("--event-json", type=Path, required=True)
    p.add_argument("--annotations-json", type=Path, required=True)
    p.add_argument("--baseline", type=Path, required=True)
    p.add_argument("--native-zip", type=Path, required=True)
    p.add_argument("--sim", type=Path, required=True)
    p.add_argument("--notes", default="")
    args = parser.parse_args()
    if args.command == "init":
        result = init(args.root, args.historical_dir)
    elif args.command == "add-lane":
        result = add_lane(args.root, args.signer, args.device, args.policy)
    elif args.command == "validate":
        result = validate_dataset(args.root)
    elif args.command == "cadence-export":
        result = cadence_export(args.trial_dir, args.output_dir)
    else:
        result = ingest(args.root, args.trial_id, args.event_json, args.annotations_json,
                        args.baseline, args.native_zip, args.sim, args.notes)
    print(json.dumps(result, indent=2))


if __name__ == "__main__":
    cli()
