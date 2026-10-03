from __future__ import annotations

import importlib.util
import sys
import unittest
from pathlib import Path
from types import SimpleNamespace

import numpy as np


ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts_ml"))

from live_pipeline_v2 import TwoStageRecognizer  # noqa: E402


def load_calibration_module():
    path = ROOT / "scripts_ml" / "83_calibrate_fsl_runtime.py"
    spec = importlib.util.spec_from_file_location("calibrate_fsl_runtime", path)
    module = importlib.util.module_from_spec(spec)
    assert spec.loader is not None
    spec.loader.exec_module(module)
    return module


class ConstantModel:
    def __init__(self, output: np.ndarray):
        self.output = np.asarray(output, dtype=np.float32)
        self.calls = 0

    def predict(self, _array: np.ndarray) -> np.ndarray:
        self.calls += 1
        return self.output.copy()


def manifest() -> dict:
    return {
        "class_order": [f"LABEL_{index}" for index in range(64)],
        "activity_detector_threshold": 0.5,
        "confidence_threshold": 0.5,
        "margin_threshold": 0.1,
        "cooldown_frames": 10,
        "sequence_length": 20,
    }


class LivePipelineTests(unittest.TestCase):
    def setUp(self) -> None:
        self.frame = np.ones(162, dtype=np.float32)
        probabilities = np.zeros(64, dtype=np.float32)
        probabilities[7] = 0.8
        probabilities[3] = 0.1
        self.activity = ConstantModel(np.asarray([0.9], dtype=np.float32))
        self.classifier = ConstantModel(probabilities)
        self.recognizer = TwoStageRecognizer(
            self.activity, self.classifier, manifest()
        )

    def test_only_active_frames_enter_sequence_and_twentieth_is_classified(self) -> None:
        for _ in range(19):
            event = self.recognizer.process_frame_features(self.frame)
            self.assertEqual(event.state, "buffering")
        event = self.recognizer.process_frame_features(self.frame)
        self.assertTrue(event.emitted)
        self.assertEqual(event.label, "LABEL_7")
        self.assertEqual(self.classifier.calls, 1)

    def test_inactive_frame_clears_partial_buffer(self) -> None:
        for _ in range(5):
            self.recognizer.process_frame_features(self.frame)
        self.activity.output = np.asarray([0.1], dtype=np.float32)
        event = self.recognizer.process_frame_features(self.frame)
        self.assertEqual(event.state, "activity_rejected")
        self.assertEqual(event.buffer_size, 0)
        self.assertEqual(len(self.recognizer.buffer), 0)

    def test_repeated_token_is_suppressed_by_cooldown(self) -> None:
        for _ in range(20):
            first = self.recognizer.process_frame_features(self.frame)
        self.assertTrue(first.emitted)
        second = self.recognizer.process_frame_features(self.frame)
        self.assertEqual(second.state, "cooldown_rejected")
        self.assertFalse(second.emitted)


class CalibrationTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.calibration = load_calibration_module()

    def test_acceptance_accounting(self) -> None:
        probabilities = np.asarray(
            [[0.8, 0.2], [0.6, 0.4], [0.45, 0.55], [0.3, 0.7]],
            dtype=np.float32,
        )
        targets = np.asarray([0, 1, 1, 1], dtype=np.int64)
        row = self.calibration.acceptance_metrics(
            probabilities, targets, confidence_threshold=0.6, margin_threshold=0.1
        )
        self.assertEqual(row["true_accept"], 2)
        self.assertEqual(row["false_accept"], 1)
        self.assertEqual(row["false_reject"], 1)
        self.assertEqual(row["true_reject"], 0)

    def test_cooldown_simulation_suppresses_nearby_same_label(self) -> None:
        records = [
            SimpleNamespace(
                source_video="video.mp4",
                meta_path="missing.json",
                path=f"external_datasets/fsl_features/A/fsl105_0_w{index}.npy",
            )
            for index in range(3)
        ]
        probabilities = np.zeros((3, 2), dtype=np.float32)
        probabilities[:, 0] = 0.9
        probabilities[:, 1] = 0.1
        row = self.calibration.cooldown_metrics(
            records,
            probabilities,
            confidence_threshold=0.5,
            margin_threshold=0.1,
            cooldown_frames=6,
        )
        self.assertEqual(row["accepted_candidates"], 3)
        self.assertEqual(row["suppressed_count"], 1)
        self.assertEqual(row["emitted_count"], 2)


if __name__ == "__main__":
    unittest.main()
