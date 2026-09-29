"""Offline-only CORE5_DEVSET_V1 safeguards; no device or model training."""
from __future__ import annotations

import json
import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts_ml"))
import core5_devset_v1 as dev
import core5_devset_capture as cap
from core5_contract import LABELS, VERSION, canonical, resample_timestamp


def event_bytes(event_id="new-event", label="HELLO"):
    frames, vectors = [], []
    times = np.asarray([1000, 1100, 1200, 1300], dtype=np.int64)
    for index, timestamp in enumerate(times):
        pose = np.zeros((33, 3), np.float32)
        left = np.zeros((21, 3), np.float32)
        right = np.zeros((21, 3), np.float32)
        pose[:, 0] = .2 + index * .01
        right[:, 0] = .3 + index * .03
        vector = canonical(pose, None, right)
        vectors.append(vector)
        frames.append({"timestamp_ms": int(timestamp), "rotation_degrees": 0,
                       "included": True, "pose_present": True,
                       "left_present": False, "right_present": True,
                       "pose": pose.tolist(), "left": left.tolist(),
                       "right": right.tolist(), "canonical": vector.tolist()})
    tensor, _ = resample_timestamp(vectors, times)
    event = {"schema": "core5_event_v1", "feature_version": VERSION,
             "event_id": event_id, "expected_test_label": label,
             "boundary_mode": "MANUAL", "labels": LABELS,
             "capture_origin": "ANDROID_CAMERA", "analysis_mirrored": False,
             "camera": "FRONT", "profile": "FSL_CORE5_SIM10FPS_V1",
             "model_sha256": dev.MODEL_HASHES["sim10_output"],
             "termination": "MANUAL_END", "inference_error": None,
             "processed_fps": 10.0, "frames": frames,
             "tensor": tensor.tolist(), "tensor_sha256": dev.digest(tensor.astype("<f4").tobytes())}
    return dev.json_bytes(event)


class DevsetTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.base = Path(self.temp.name)
        self.history = self.base / "history" / "device_files"
        self.history.mkdir(parents=True)
        ids = [f"historical-{i:03d}" for i in range(69)]
        for event_id in ids:
            (self.history / f"{event_id}.json").write_bytes(
                dev.json_bytes({"schema": "core5_event_v1", "event_id": event_id}))
        (self.history.parent / "core5_samsung_event_matrix_20260927.json").write_bytes(
            dev.json_bytes({"events": [{"event_id": item} for item in ids]}))
        self.root = self.base / "new_devset"
        dev.init(self.root, self.history)
        dev.add_lane(self.root, "signerA", "samsungA56", "WITHIN_LANE")
        self.name = dev.trial_id("signerA", "samsungA56", "HELLO", 1)

    def test_plan_roles_and_historical_extra_exclusion(self):
        manifest = dev.load(self.root)
        self.assertEqual(len(manifest["slots"]), 90)
        self.assertEqual({r: sum(s["role"] == r for s in manifest["slots"].values())
                          for r in dev.ROLES}, {"DEV_TUNE": 54, "DEV_HOLDOUT": 18, "SEALED_FINAL": 18})
        self.assertEqual(dev.validate_dataset(self.root)["sealed_historical"], 69)
        (self.history / "extra.json").write_bytes(dev.json_bytes(
            {"schema": "core5_event_v1", "event_id": "extra"}))
        with self.assertRaises(ValueError):
            dev.seal(self.root)

    def test_cannot_change_roles_or_bind_different_device(self):
        with self.assertRaises(FileExistsError):
            dev.add_lane(self.root, "signerA", "samsungA56", "SEALED_LANE")
        dev.bind_device(self.root, "signerA__samsungA56", "SERIAL_A")
        with self.assertRaises(ValueError):
            dev.bind_device(self.root, "signerA__samsungA56", "SERIAL_B")
        self.assertNotIn("SERIAL_A", (self.root / "manifest.json").read_text())

    def test_markers_and_import_duplicate_protection(self):
        source = self.base / "pending.json"
        source.write_bytes(event_bytes())
        annotation = self.base / "annotations.json"
        annotation.write_bytes(dev.json_bytes({"trial_id": self.name,
            "source_event_id": "new-event", "capture_package": dev.LAB_PACKAGE,
            "capture_timestamp": "2026-09-29T00:00:00+00:00",
            "clock_uncertainty_ms": 20, "SIGN_START": 1020,
            "SIGN_END": 1150, "RETURN_NEUTRAL": 1200, "SAFE_REARM": 1250}))
        replay = {key: {"model_sha256": value, "top1": "HELLO",
                 "probabilities": [1., 0., 0., 0., 0.]}
                  for key, value in dev.MODEL_HASHES.items()}
        with patch.object(dev, "model_outputs", return_value=replay):
            result = dev.ingest(self.root, self.name, source, annotation,
                                self.base / "baseline", self.base / "native", self.base / "sim")
            self.assertTrue(result["capture_valid"])
            self.assertEqual(dev.validate_dataset(self.root)["captured"], 1)
            with self.assertRaises(FileExistsError):
                dev.ingest(self.root, self.name, source, annotation,
                           self.base / "baseline", self.base / "native", self.base / "sim")
            second = dev.trial_id("signerA", "samsungA56", "HELLO", 2)
            with self.assertRaises(ValueError):
                dev.ingest(self.root, second, source, annotation,
                           self.base / "baseline", self.base / "native", self.base / "sim")
        metadata = json.loads((Path(result["directory"]) / "metadata.json").read_text())
        self.assertFalse(metadata["supervised_training_allowed"])
        self.assertEqual(len(metadata["baseline_output"]["probabilities"]), 5)

    def test_historical_id_and_path_cannot_import(self):
        historical = self.history / "historical-000.json"
        with self.assertRaises(ValueError):
            dev.ingest(self.root, self.name, historical, self.base / "missing.json",
                       self.base / "baseline", self.base / "native", self.base / "sim")
        copied = self.base / "copied.json"
        copied.write_bytes(historical.read_bytes())
        with self.assertRaises(ValueError):
            dev.ingest(self.root, self.name, copied, self.base / "missing.json",
                       self.base / "baseline", self.base / "native", self.base / "sim")

    def test_marker_and_cadence_validation(self):
        marks = {"SIGN_START": 10, "SIGN_END": 20,
                 "RETURN_NEUTRAL": 30, "SAFE_REARM": 40,
                 "clock_uncertainty_ms": 10}
        dev.validate_markers(marks, 0, 50)
        with self.assertRaises(ValueError):
            dev.validate_markers({**marks, "SAFE_REARM": 25}, 0, 50)
        with self.assertRaises(ValueError):
            dev.validate_markers({**marks, "clock_uncertainty_ms": 120}, 0, 50)
        variants = dev.cadence_variants([0, 100, 200, 300],
                                        np.zeros((4, 225), np.float32), 10)
        self.assertEqual(variants["60"]["status"], "UNAVAILABLE_OBSERVED_RATE_TOO_LOW")
        self.assertEqual(variants["10"]["tensor"].shape, (48, 225))

    def test_capture_targets_only_lab_package(self):
        with patch.object(cap, "adb", return_value="List of devices attached\nABC device model:SM-A566B"):
            self.assertEqual(cap.single_authorized_serial(Path("adb")), "ABC")
        with patch.object(cap, "adb", return_value="List of devices attached\nABC unauthorized"):
            with self.assertRaises(RuntimeError):
                cap.single_authorized_serial(Path("adb"))
        with patch.object(cap, "adb", return_value="ok") as fake:
            cap.shell_am(Path("adb"), "ABC", "--es", "expected", "THANK YOU")
            command = fake.call_args.args[-1]
            self.assertIn(dev.LAB_PACKAGE, command)
            self.assertIn("'THANK YOU'", command)
            self.assertNotIn("com.voxgest.dryrun/", command)


if __name__ == "__main__":
    unittest.main()
