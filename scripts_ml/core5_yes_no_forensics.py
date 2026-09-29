"""Read-only YES cadence and NO global-OOD forensic replay.

Only official FSL-105 train-derived features and saved Samsung diagnostics are
opened. The official test manifest/clips and deployed Android assets are not
modified. Samsung operator labels are evaluation intent, never training truth.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import zipfile
from pathlib import Path

import numpy as np

from core5_contract import LABELS, observed_envelope, resample_timestamp
from core5_ood_train_eval import SIM10_SHA
from core5_ood_fusion_replay import source_geometry_fit
from core5_semantic_geometry import trajectory_features
from core5_semantic_source_profile import frames_from_arrays, vector
from core5_streaming_window_replay import event_paths, verified_event, windows_for_event

EXPECTED = {
    "BASELINE": "3518ddeb68e69afa37428b8c5fc08b9d3b93e396fa1549224493da293f0ea484",
    "NATIVE48": "3cff57f526fd0aab4d531855f838ee8c100a97958be39c7cbbdcbd6d30797def",
    "SIM10": SIM10_SHA,
}


def runner(tf, data: bytes, expected_hash: str):
    if hashlib.sha256(data).hexdigest() != expected_hash:
        raise ValueError("Frozen model hash mismatch")
    model = tf.lite.Interpreter(model_content=data, num_threads=2)
    model.allocate_tensors()
    inp, out = model.get_input_details()[0], model.get_output_details()[0]
    if inp["shape"].tolist() != [1, 48, 225] or out["shape"].tolist() != [1, 5]:
        raise ValueError("Wrong Core5 TFLite shape")
    return model, inp, out


def predict(bundle, tensor):
    model, inp, out = bundle
    model.set_tensor(inp["index"], tensor.astype(np.float32)[None])
    model.invoke()
    scores = model.get_tensor(out["index"])[0].astype(np.float32)
    if scores.shape != (5,) or not np.isfinite(scores).all():
        raise ValueError("Invalid five-class probabilities")
    return {"top1": LABELS[int(np.argmax(scores))], "probabilities": scores.tolist()}


def time_decimate(vectors, times, phase_ms):
    grid = np.arange(float(times[0]) + phase_ms, float(times[-1]) + 1, 100.)
    indices = [int(np.argmin(np.abs(times - target))) for target in grid]
    indices = np.unique([0, *indices, len(times) - 1])
    return vectors[indices], times[indices], len(indices)


def block_stats(tensor):
    result = {}
    for name, lo, hi in (("pose", 0, 99), ("left", 99, 162), ("right", 162, 225)):
        block = tensor[:, lo:hi]
        result[name] = {"mean_abs": float(np.abs(block).mean()),
                        "std": float(block.std()),
                        "zero_fraction": float(np.mean(block == 0)),
                        "mean_temporal_delta_l2": float(np.linalg.norm(np.diff(block, axis=0), axis=1).mean())}
    return result


def source_yes(manifest, native_dir, sparse_dir, models):
    output = []
    for row in manifest["records"]:
        if row["source_label"] != "YES" or row["partition"] == "quarantined_cross_label_duplicate":
            continue
        with np.load(native_dir / (row["record_id"] + ".npz"), allow_pickle=False) as saved:
            meta = json.loads(str(saved["metadata"].item()))
            if meta["official_split"] != "train" or meta["source_sha256"] != row["source_sha256"]:
                raise ValueError("Wrong native YES source provenance")
            vectors, times, presence = saved["canonical_frames"], saved["timestamps_ms"], saved["presence"]
        with np.load(sparse_dir / (row["record_id"] + ".npz"), allow_pickle=False) as saved:
            meta = json.loads(str(saved["metadata"].item()))
            if meta["source_sha256"] != row["source_sha256"]:
                raise ValueError("Wrong sparse YES source provenance")
            sparse_tensor = saved["tensor"]
            sparse_times = saved["timestamps_ms"]
        lo, hi = observed_envelope(times, presence)
        x, t = vectors[lo:hi], times[lo:hi]
        if sparse_tensor.shape != (48, 225):
            raise ValueError("Missing sparse YES tensor")
        variants = {}
        full, _ = resample_timestamp(x, t)
        variants["full_observed"] = {"tensor": full, "observations": len(t)}
        for phase in (0, 33, 66):
            selected, selected_times, count = time_decimate(x, t, phase)
            sparse, _ = resample_timestamp(selected, selected_times)
            variants[f"decimated_phase{phase}"] = {"tensor": sparse, "observations": count}
        variants["separate_sparse_tasks"] = {"tensor": sparse_tensor, "observations": len(sparse_times)}
        runs = {}
        for name, data in variants.items():
            runs[name] = {"observations": data["observations"],
                          "stats": block_stats(data["tensor"]),
                          "model_outputs": {model: predict(bundle, data["tensor"])
                                            for model, bundle in models.items()}}
        output.append({"record_id": row["record_id"], "partition": row["partition"],
                       "native_observations": len(times), "native_rate_hz": float((len(times)-1)*1000/(times[-1]-times[0])),
                       "sparse_observations": len(sparse_times),
                       "sparse_rate_hz": float((len(sparse_times)-1)*1000/(sparse_times[-1]-sparse_times[0])),
                       "variants": runs})
    return output


def device_yes_no(events_dir, models, durations, ood_model, ood_embedding, geometry_profile):
    yes, no = [], []
    for path in event_paths(events_dir):
        checked = verified_event(path)
        if checked is None:
            continue
        event, frames, times, vectors = checked
        if event["expected_test_label"] not in ("YES", "NO"):
            continue
        if event.get("tensor") is None:
            continue
        tensor = np.asarray(event["tensor"], dtype=np.float32)
        final = {name: predict(bundle, tensor) for name, bundle in models.items()}
        event_stats = {"event_id": event["event_id"], "expected": event["expected_test_label"],
                       "termination": event["termination"], "raw_frame_count": event["raw_frame_count"],
                       "duration_ms": int(times[-1]-times[0]),
                       "effective_rate_hz": float((len(times)-1)*1000/(times[-1]-times[0])),
                       "pose_presence": int(sum(frame["pose_present"] for frame in frames)),
                       "left_presence": int(sum(frame["left_present"] for frame in frames)),
                       "right_presence": int(sum(frame["right_present"] for frame in frames)),
                       "feature_stats": block_stats(tensor), "final_models": final}
        if event["expected_test_label"] == "YES":
            windows = []
            for window in windows_for_event(frames, times, vectors, durations):
                outputs = {name: predict(bundle, window["tensor"]) for name, bundle in models.items()}
                windows.append({"start_ms": window["start_ms"], "end_ms": window["end_ms"],
                                "end_relative_ms": int(window["end_ms"]-times[0]),
                                "duration_target_ms": window["duration_target_ms"],
                                "frame_count": window["frame_count"],
                                "pose_ratio": window["pose_ratio"], "hand_ratio": window["hand_ratio"],
                                "motion_mean_l2": window["motion_mean_l2"],
                                "basic_quality": window["basic_quality"],
                                "model_outputs": outputs})
            event_stats["windows"] = windows
            yes.append(event_stats)
        else:
            score = float(ood_model.predict(tensor[None], verbose=0)[0, 0])
            embedding = ood_embedding.predict(tensor[None], verbose=0)[0].astype(np.float32)
            included = [frame for frame in frames if frame["included"]]
            semantic, _ = trajectory_features(included)
            geometry = geometry_profile.evaluate(vector(semantic), "NO")
            event_stats.update({"ood_score": score, "ood_embedding": embedding.tolist(),
                                "geometry_no_distance": geometry["class_distance"],
                                "geometry_no_cutoff": geometry["class_cutoff_source_loo_p95"]})
            no.append(event_stats)
    return yes, no


def no_source_embeddings(manifest, features_dir, ood_model, ood_embedding):
    records = []
    for row in manifest["records"]:
        if row["partition"] == "quarantined_cross_label_duplicate":
            continue
        with np.load(features_dir / (row["record_id"] + ".npz"), allow_pickle=False) as saved:
            tensor = saved["tensor"]
        if tensor.shape != (48, 225):
            continue
        if row["source_label"] != "NO" and row["binary_label"] != "OTHER_FSL":
            continue
        records.append({"record_id": row["record_id"], "source_label": row["source_label"],
                        "partition": row["partition"], "tensor": tensor.astype(np.float32)})
    x = np.stack([r["tensor"] for r in records])
    scores = ood_model.predict(x, batch_size=64, verbose=0).reshape(-1)
    embeddings = ood_embedding.predict(x, batch_size=64, verbose=0)
    return [{"record_id": r["record_id"], "source_label": r["source_label"],
             "partition": r["partition"], "ood_score": float(score),
             "ood_embedding": emb.astype(np.float32).tolist()}
            for r, score, emb in zip(records, scores, embeddings)]


def run(manifest_path, native_dir, sparse_dir, events_dir, baseline_path, zip_path,
        sim_path, ood_dir, output):
    if output.exists():
        raise FileExistsError("Preserve prior forensic evidence")
    import tensorflow as tf
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    if manifest["official_test_opened"]:
        raise ValueError("Official test not permitted")
    with zipfile.ZipFile(zip_path) as archive:
        native_bytes = archive.read("core5_native48_rdtcn_v1.tflite")
    models = {"BASELINE": runner(tf, baseline_path.read_bytes(), EXPECTED["BASELINE"]),
              "NATIVE48": runner(tf, native_bytes, EXPECTED["NATIVE48"]),
              "SIM10": runner(tf, sim_path.read_bytes(), EXPECTED["SIM10"])}
    ood_model = tf.keras.models.load_model(ood_dir / "core5_ood_small_tcn.keras", compile=False)
    ood_embedding = tf.keras.Model(ood_model.inputs, ood_model.layers[-2].output)
    source = source_yes(manifest, native_dir, sparse_dir, models)
    geometry_profile, durations = source_geometry_fit(manifest, sparse_dir)
    device_yes, device_no = device_yes_no(events_dir, models, durations, ood_model, ood_embedding, geometry_profile)
    source_no = no_source_embeddings(manifest, sparse_dir, ood_model, ood_embedding)
    if len(source) != 16 or len(device_yes) != 11 or len(device_no) != 9:
        raise ValueError("Unexpected source/Samsung YES or NO count")
    summary = {"source_yes": len(source), "device_yes": len(device_yes), "device_no": len(device_no),
               "same_tensor_yes_correct": {name: sum(e["final_models"][name]["top1"] == "YES" for e in device_yes)
                                           for name in models},
               "same_tensor_no_correct": {name: sum(e["final_models"][name]["top1"] == "NO" for e in device_no)
                                          for name in models},
               "source_yes_variant_correct": {variant: {name: sum(e["variants"][variant]["model_outputs"][name]["top1"] == "YES" for e in source)
                                                       for name in models}
                                              for variant in source[0]["variants"]},
               "yes_window_flips": sum(w["model_outputs"]["BASELINE"]["top1"] == "YES" and
                                       w["model_outputs"]["SIM10"]["top1"] != "YES"
                                       for e in device_yes for w in e["windows"]),
               "yes_total_windows": sum(len(e["windows"]) for e in device_yes)}
    report = {"status": "OFFLINE_DIAGNOSTIC_NO_ANDROID_CHANGE", "official_test_opened": False,
              "samsung_training_used": False, "model_sha256": EXPECTED,
              "summary": summary, "source_yes": source, "device_yes": device_yes,
              "device_no": device_no, "source_no_and_other_embeddings": source_no}
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(report, indent=2, allow_nan=False) + "\n", encoding="utf-8")
    return {**summary, "output": str(output)}


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    for name in ("manifest", "native_dir", "sparse_dir", "events_dir", "baseline", "zip", "sim", "ood_dir", "output"):
        parser.add_argument("--" + name.replace("_", "-"), type=Path, required=True)
    args = parser.parse_args()
    print(json.dumps(run(args.manifest, args.native_dir, args.sparse_dir, args.events_dir,
                         args.baseline, args.zip, args.sim, args.ood_dir, args.output), indent=2))
