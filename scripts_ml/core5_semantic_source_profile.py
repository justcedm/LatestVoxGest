"""Official FSL-105 train-only semantic geometry profile, no model mutation."""
from __future__ import annotations

import argparse
import json
from collections import Counter
from pathlib import Path

import numpy as np

from core5_contract import LABELS, VERSION, observed_envelope
from core5_semantic_geometry import trajectory_features


FEATURES = (
    "dominant_presence_ratio", "two_hand_ratio", "palm_to_nose_distance_median",
    "palm_to_eye_distance_median", "palm_to_mouth_distance_median",
    "palm_to_shoulder_distance_median", "tip_to_nose_min_p10",
    "tip_to_mouth_min_p10", "elbow_angle_median", "palm_signed_area_proxy_median",
    "hand_opening_median", "finger_extension_angle_median",
    "palm_to_nose_xy_x_median", "palm_to_nose_xy_y_median",
    "hand_orientation_xy_x_median", "hand_orientation_xy_y_median",
    "forearm_direction_xy_x_median", "forearm_direction_xy_y_median",
    "inter_palm_distance_median", "trajectory_length",
    "translation_velocity_p90", "trajectory_displacement_x",
    "trajectory_displacement_y",
)


def frames_from_arrays(raw, times, presence):
    if raw.ndim != 2 or raw.shape[1] != 225 or len(raw) != len(times) or presence.shape != (len(raw), 3):
        raise ValueError("Invalid raw landmark shapes")
    return [{"timestamp_ms": int(t), "pose_present": bool(p[0]),
             "left_present": bool(p[1]), "right_present": bool(p[2]),
             "pose": x[:99].reshape(33, 3).tolist(),
             "left": x[99:162].reshape(21, 3).tolist(),
             "right": x[162:225].reshape(21, 3).tolist()}
            for x, t, p in zip(raw, times, presence)]


def vector(summary):
    displacement = summary.get("trajectory_displacement_xy") or [None, None]
    result = []
    for key in FEATURES:
        value = displacement[0 if key.endswith("_x") else 1] if key.startswith("trajectory_displacement_") else summary.get(key)
        result.append(float(value) if value is not None else None)
    return result


def source_records(features_dir: Path):
    records = []
    for path in sorted(features_dir.glob("*.npz")):
        if path.name.startswith("test-"):
            continue  # Do not open official-test feature files.
        with np.load(path, allow_pickle=False) as saved:
            meta = json.loads(str(saved["metadata"].item()))
            if meta["source_dataset"] != "DLSU_FSL105_V2" or meta["feature_version"] != VERSION:
                raise ValueError("Wrong source/contract: " + path.name)
            if meta["official_split"] != "train":
                continue  # Official test remains sealed.
            if meta["source_label"] not in LABELS:
                raise ValueError("Unexpected label")
            raw, times, presence = saved["raw_landmarks"], saved["timestamps_ms"], saved["presence"]
        lo, hi = observed_envelope(times, presence)
        summary, _ = trajectory_features(frames_from_arrays(raw[lo:hi], times[lo:hi], presence[lo:hi]))
        records.append({"record_id": meta["record_id"], "label": meta["source_label"],
                        "partition": meta["partition"], "summary": summary, "vector": vector(summary)})
    if Counter(r["partition"] for r in records) != {"train": 61, "development_validation": 20}:
        raise ValueError("Unexpected source train/development count")
    return records


class SourceGeometryProfile:
    def __init__(self, records):
        self.records = records
        train = [r for r in records if r["partition"] == "train"]
        matrix = np.asarray([[np.nan if v is None else v for v in r["vector"]] for r in train])
        self.fill = np.nanmedian(matrix, axis=0)
        if not np.isfinite(self.fill).all():
            raise ValueError("No source support for a semantic feature")
        filled = np.where(np.isnan(matrix), self.fill, matrix)
        self.center = np.median(filled, axis=0)
        self.scale = np.percentile(filled, 75, axis=0) - np.percentile(filled, 25, axis=0)
        self.scale = np.maximum(self.scale, .05)
        standardized = (filled - self.center) / self.scale
        self.centroids = {label: np.mean(standardized[[r["label"] == label for r in train]], axis=0)
                          for label in LABELS}
        self.source_train = train
        self.source_standardized = standardized
        # Source-only leave-one-out true-class distance distribution. A p95
        # exploratory cutoff is checked on held-out source development clips.
        self.cutoffs = {}
        for label in LABELS:
            subset = standardized[[r["label"] == label for r in train]]
            distances = [self._distance(x, np.mean(np.delete(subset, index, axis=0), axis=0))
                         for index, x in enumerate(subset)]
            self.cutoffs[label] = float(np.percentile(distances, 95))

    def _distance(self, vector, centroid):
        return float(np.linalg.norm(vector - centroid) / np.sqrt(len(FEATURES)))

    def standardized(self, values):
        raw = np.asarray([np.nan if v is None else v for v in values], dtype=np.float64)
        return (np.where(np.isnan(raw), self.fill, raw) - self.center) / self.scale

    def evaluate(self, values, label):
        scaled = self.standardized(values)
        distances = {name: self._distance(scaled, center) for name, center in self.centroids.items()}
        return {"nearest_geometry_class": min(distances, key=distances.get),
                "class_distance": distances[label], "class_cutoff_source_loo_p95": self.cutoffs[label],
                "source_supported_exploratory": bool(distances[label] <= self.cutoffs[label]),
                "all_class_distances": distances}

    def report(self):
        dev = [r for r in self.records if r["partition"] == "development_validation"]
        evaluated = [{"record_id": r["record_id"], "expected": r["label"],
                      **self.evaluate(r["vector"], r["label"])} for r in dev]
        distributions = {}
        for label in LABELS:
            group = [r for r in self.records if r["label"] == label]
            distributions[label] = {key: {"n": len(valid), "median": float(np.median(valid)) if valid else None,
                                          "p10": float(np.percentile(valid, 10)) if valid else None,
                                          "p90": float(np.percentile(valid, 90)) if valid else None}
                                    for key in FEATURES
                                    for valid in [[r["vector"][FEATURES.index(key)] for r in group
                                                   if r["vector"][FEATURES.index(key)] is not None]]}
        # Between-class separation divided by typical within-class variation.
        separability = {}
        for index, name in enumerate(FEATURES):
            medians, spreads = [], []
            for label in LABELS:
                valid = [r["vector"][index] for r in self.source_train
                         if r["label"] == label and r["vector"][index] is not None]
                if valid:
                    mid = float(np.median(valid))
                    medians.append(mid)
                    spreads.append(float(np.median(np.abs(np.asarray(valid) - mid))))
            separability[name] = float(np.std(medians) / max(float(np.median(spreads)), .01)) if len(medians) > 1 else None
        return {"status": "SOURCE_ONLY_EXPLORATORY_NOT_VALIDATED",
                "source_counts": dict(Counter(r["partition"] for r in self.records)),
                "features": FEATURES, "source_distributions": distributions,
                "feature_separability_ratio": separability,
                "development": evaluated,
                "development_nearest_centroid_correct": sum(r["nearest_geometry_class"] == r["expected"] for r in evaluated),
                "development_geometry_plausible": sum(r["source_supported_exploratory"] for r in evaluated),
                "development_count": len(evaluated),
                "source_train_loo_p95_cutoffs": self.cutoffs,
                "train_records": [{"record_id": r["record_id"], "label": r["label"], "summary": r["summary"]}
                                  for r in self.source_train]}


def run(features_dir: Path, output: Path):
    if output.exists():
        raise FileExistsError("Preserve previous profile evidence")
    profile = SourceGeometryProfile(source_records(features_dir))
    report = profile.report()
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(report, indent=2, allow_nan=False) + "\n", encoding="utf-8")
    return {"source_counts": report["source_counts"],
            "development_nearest_centroid_correct": report["development_nearest_centroid_correct"],
            "development_geometry_plausible": report["development_geometry_plausible"],
            "development_count": report["development_count"], "output": str(output)}


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--features", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    print(json.dumps(run(args.features, args.output), indent=2))
