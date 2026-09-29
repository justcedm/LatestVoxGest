"""Official FSL-105 train-only Core5-vs-Other sparse Tasks extraction.

All binary classes use the same fresh-per-clip MediaPipe Tasks extraction,
~10-Hz timestamps, FullSign225 builder, observed-hand envelope +100ms context,
and timestamp-linear 48-step resampling. Official test.csv/clips are unopened.
"""
from __future__ import annotations

import argparse
import csv
import hashlib
import json
from concurrent.futures import ProcessPoolExecutor, as_completed
from collections import Counter
from pathlib import Path

import numpy as np

from core5_contract import IDS, LABELS, assign_hands, canonical, observed_envelope, quality, resample_timestamp, sha256

VERSION = "core5_ood_tasks_fullsign225_sparse10fps_timestamp48_v1"
EXPECTED_TASKS = {"hand_landmarker.task": "fbc2a30080c3c557093b5ddfc334698132eb341044ccee322ccf8bcf3607cde1",
                  "pose_landmarker_lite.task": "59929e1d1ee95287735ddd833b19cf4ac46d29bc7afddbbf6753c459690d574a"}


def build_manifest(dataset: Path, output: Path):
    if output.exists():
        raise FileExistsError("Preserve prior manifest")
    with (dataset / "labels.csv").open(encoding="utf-8-sig", newline="") as stream:
        labels = {int(row["id"]): row["label"] for row in csv.DictReader(stream)}
    if len(labels) != 105 or [labels[i] for i in IDS] != LABELS:
        raise ValueError("Authoritative FSL-105 label map mismatch")
    rows = []
    with (dataset / "train.csv").open(encoding="utf-8-sig", newline="") as stream:
        for source in csv.DictReader(stream):
            number = int(source["id_label"])
            if source["label"] != labels[number]:
                raise ValueError("CSV label disagrees with labels.csv")
            relative = Path("clips") / source["vid_path"].replace("\\", "/")
            file = (dataset / relative).resolve(strict=True)
            if dataset.resolve() not in file.parents or not file.is_file():
                raise ValueError("Unsafe or missing source clip")
            rows.append({"record_id": f"train-{number}-{file.stem}",
                         "source_relative_path": relative.as_posix(),
                         "source_label": labels[number], "source_id": number,
                         "binary_label": "CORE5_LIKE" if number in IDS else "OTHER_FSL",
                         "source_sha256": sha256(file), "source_bytes": file.stat().st_size,
                         "official_split": "train"})
    if len(rows) != 1704 or len({r["record_id"] for r in rows}) != len(rows):
        raise ValueError("Unexpected official training rows or duplicate IDs")
    hash_groups = {}
    for row in rows:
        hash_groups.setdefault(row["source_sha256"], []).append(row)
    duplicate_groups = [group for group in hash_groups.values() if len(group) > 1]
    if any(len({r["binary_label"] for r in group}) > 1 for group in duplicate_groups):
        raise ValueError("Cross-binary duplicate source video; stop for forensic review")
    # Four published rows occur as byte-identical videos under conflicting
    # non-Core5 labels. Exclude both sides of each conflict from all partitions.
    for group in duplicate_groups:
        for row in group:
            row["partition"] = "quarantined_cross_label_duplicate"
    grouped = {}
    for row in rows:
        if "partition" not in row:
            grouped.setdefault(row["source_id"], []).append(row)
    for group in grouped.values():
        ordered = sorted(group, key=lambda row: row["source_sha256"])
        if len(ordered) < 6:
            raise ValueError("Insufficient class samples for split")
        for index, row in enumerate(ordered):
            row["partition"] = "calibration" if index < 2 else "holdout" if index < 4 else "fit"
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps({"dataset": "DLSU_FSL105_V2", "official_test_opened": False,
                                  "feature_version": VERSION, "sampling_period_ms": 100,
                                  "task_assets_sha256": EXPECTED_TASKS,
                                  "split_policy": "Quarantine both rows of every cross-label byte-duplicate group; SHA-ordered per remaining authoritative class: first2 calibration, next2 holdout, remaining fit; official test unopened; signer IDs unavailable",
                                  "quarantined_duplicate_groups": [{"sha256": group[0]["source_sha256"],
                                                                    "records": [{"record_id": r["record_id"], "label": r["source_label"]} for r in group]}
                                                                   for group in duplicate_groups],
                                  "records": sorted(rows, key=lambda row: row["record_id"])},
                                 indent=2, allow_nan=False) + "\n", encoding="utf-8")
    return {"records": len(rows), "quarantined_duplicate_groups": len(duplicate_groups),
            "partitions": dict(Counter(r["binary_label"] + ":" + r["partition"] for r in rows)),
            "output": str(output)}


def extract_one(row, dataset: Path, assets: Path, output_dir: Path):
    import cv2
    import mediapipe as mp
    from mediapipe.tasks.python import vision

    if mp.__version__ != "0.10.35":
        raise ValueError("Wrong MediaPipe version")
    source = dataset / row["source_relative_path"]
    if sha256(source) != row["source_sha256"]:
        raise ValueError("Source hash mismatch: " + row["record_id"])
    target = output_dir / (row["record_id"] + ".npz")
    if target.exists():
        with np.load(target, allow_pickle=False) as saved:
            meta = json.loads(str(saved["metadata"].item()))
            if meta["source_sha256"] != row["source_sha256"] or meta["feature_version"] != VERSION:
                raise ValueError("Incompatible cached extraction")
        return meta
    base = mp.tasks.BaseOptions
    hands = vision.HandLandmarker.create_from_options(vision.HandLandmarkerOptions(
        base_options=base(model_asset_path=str(assets / "hand_landmarker.task")),
        running_mode=vision.RunningMode.VIDEO, num_hands=2,
        min_hand_detection_confidence=.45, min_hand_presence_confidence=.45,
        min_tracking_confidence=.45))
    pose = vision.PoseLandmarker.create_from_options(vision.PoseLandmarkerOptions(
        base_options=base(model_asset_path=str(assets / "pose_landmarker_lite.task")),
        running_mode=vision.RunningMode.VIDEO, min_pose_detection_confidence=.45,
        min_pose_presence_confidence=.45, min_tracking_confidence=.45))
    cap = cv2.VideoCapture(str(source))
    if not cap.isOpened():
        hands.close(); pose.close()
        raise ValueError("Cannot decode " + row["record_id"])
    fps = float(cap.get(cv2.CAP_PROP_FPS))
    if not np.isfinite(fps) or fps <= 0:
        hands.close(); pose.close(); cap.release()
        raise ValueError("Invalid source fps")
    raw, vectors, timestamps, presence = [], [], [], []
    decoded = 0
    previous_decoded_ms = -1
    try:
        while True:
            ok, bgr = cap.read()
            if not ok:
                break
            t = float(cap.get(cv2.CAP_PROP_POS_MSEC))
            if not np.isfinite(t) or t <= previous_decoded_ms:
                t = decoded * 1000 / fps
            timestamp = int(round(t))
            if timestamp <= previous_decoded_ms:
                timestamp = previous_decoded_ms + 1
            previous_decoded_ms = timestamp
            decoded += 1
            if timestamps and timestamp - timestamps[-1] < 100:
                continue
            rgb = cv2.cvtColor(bgr, cv2.COLOR_BGR2RGB)
            image = mp.Image(image_format=mp.ImageFormat.SRGB, data=rgb)
            hand_result = hands.detect_for_video(image, timestamp)
            pose_result = pose.detect_for_video(image, timestamp)
            def xyz(points):
                return np.asarray([[p.x, p.y, p.z] for p in points], dtype=np.float32)
            pv = xyz(pose_result.pose_landmarks[0]) if pose_result.pose_landmarks else None
            hand_points = [xyz(h) for h in hand_result.hand_landmarks]
            categories = [h[0].category_name if h else "" for h in hand_result.handedness]
            slots, _ = assign_hands(hand_points, categories)
            lv, rv = slots["left"], slots["right"]
            vectors.append(canonical(pv, lv, rv))
            timestamps.append(timestamp)
            presence.append([pv is not None, lv is not None, rv is not None])
            raw.append(np.concatenate([(pv if pv is not None else np.zeros((33, 3))).ravel(),
                                       (lv if lv is not None else np.zeros((21, 3))).ravel(),
                                       (rv if rv is not None else np.zeros((21, 3))).ravel()]).astype(np.float32))
    finally:
        cap.release(); hands.close(); pose.close()
    vectors = np.asarray(vectors, dtype=np.float32)
    raw = np.asarray(raw, dtype=np.float32)
    timestamps = np.asarray(timestamps, dtype=np.int64)
    presence = np.asarray(presence, dtype=bool)
    if len(timestamps) < 2 or np.any(np.diff(timestamps) <= 0):
        raise ValueError("Insufficient/nonmonotonic extracted frames: " + row["record_id"])
    try:
        lo, hi = observed_envelope(timestamps, presence)
        tensor, _ = resample_timestamp(vectors[lo:hi], timestamps[lo:hi])
        status = "PASS" if presence[lo:hi, 0].mean() >= .65 and presence[lo:hi, 1:].any(axis=1).mean() >= .65 else "REVIEW"
    except ValueError:
        lo, hi = 0, 0
        tensor = np.empty((0, 225), dtype=np.float32)
        status = "NO_VALID_ENVELOPE"
    meta = {**row, "feature_version": VERSION, "sampling_period_ms": 100,
            "tasks_version": mp.__version__, "source_fps": fps, "decoded_frames": decoded,
            "processed_frames": len(timestamps), "envelope_start": lo, "envelope_end": hi,
            "status": status, "quality": quality(vectors, timestamps, presence)}
    output_dir.mkdir(parents=True, exist_ok=True)
    np.savez_compressed(target, raw_landmarks=raw, canonical_frames=vectors,
                        timestamps_ms=timestamps, presence=presence, tensor=tensor,
                        metadata=np.array(json.dumps(meta, allow_nan=False)))
    return meta


def extract(manifest_path: Path, dataset: Path, assets: Path, output_dir: Path,
            limit: int | None, workers: int):
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    if manifest["feature_version"] != VERSION or manifest["official_test_opened"]:
        raise ValueError("Wrong manifest")
    for name, expected in EXPECTED_TASKS.items():
        if sha256(assets / name) != expected:
            raise ValueError("MediaPipe task asset mismatch: " + name)
    rows = [row for row in manifest["records"] if row["partition"] != "quarantined_cross_label_duplicate"]
    rows = rows[:limit] if limit is not None else rows
    results = []
    if workers < 1 or workers > 4:
        raise ValueError("Use one to four workers")
    if workers == 1:
        for index, row in enumerate(rows, 1):
            results.append(extract_one(row, dataset, assets, output_dir))
            if index % 20 == 0 or index == len(rows):
                print(json.dumps({"processed": index, "of": len(rows),
                                  "status_counts": dict(Counter(r["status"] for r in results))}), flush=True)
    else:
        # Each clip creates fresh Tasks instances, avoiding cross-video tracker
        # state even when worker processes are reused.
        with ProcessPoolExecutor(max_workers=workers) as pool:
            futures = {pool.submit(extract_one, row, dataset, assets, output_dir): row["record_id"]
                       for row in rows}
            for future in as_completed(futures):
                try:
                    results.append(future.result())
                except Exception as error:
                    raise RuntimeError("Extraction failed for " + futures[future]) from error
                index = len(results)
                if index % 20 == 0 or index == len(rows):
                    print(json.dumps({"processed": index, "of": len(rows),
                                      "status_counts": dict(Counter(r["status"] for r in results))}), flush=True)
    return {"processed": len(results), "status_counts": dict(Counter(r["status"] for r in results)),
            "output_dir": str(output_dir)}


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    sub = parser.add_subparsers(dest="command", required=True)
    m = sub.add_parser("manifest")
    m.add_argument("--dataset", type=Path, required=True)
    m.add_argument("--output", type=Path, required=True)
    e = sub.add_parser("extract")
    e.add_argument("--manifest", type=Path, required=True)
    e.add_argument("--dataset", type=Path, required=True)
    e.add_argument("--assets", type=Path, required=True)
    e.add_argument("--output-dir", type=Path, required=True)
    e.add_argument("--limit", type=int)
    e.add_argument("--workers", type=int, default=1)
    args = parser.parse_args()
    result = build_manifest(args.dataset, args.output) if args.command == "manifest" else extract(
        args.manifest, args.dataset, args.assets, args.output_dir, args.limit, args.workers)
    print(json.dumps(result, indent=2))
