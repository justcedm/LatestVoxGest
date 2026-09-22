from __future__ import annotations

import sys
import unittest
from pathlib import Path

import numpy as np


ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts_ml"))

from voxgest_feature_builder import (  # noqa: E402
    EXPECTED_SEQUENCE_SHAPE,
    FEATURE_SIZE,
    HAND_FEATURE_SIZE,
    POSE_FEATURE_SIZE,
    Z_DAMPING,
    build_onehand162_from_arrays,
    sequence_presence,
    validate_sequence,
)


class VoxGestFeatureBuilderTests(unittest.TestCase):
    def setUp(self) -> None:
        self.pose = np.arange(33 * 3, dtype=np.float32).reshape(33, 3) / 100.0
        self.hand = np.arange(21 * 3, dtype=np.float32).reshape(21, 3) / 50.0

    def test_exact_layout_and_dtype(self) -> None:
        result = build_onehand162_from_arrays(self.pose, self.hand)
        self.assertEqual(result.vector.shape, (FEATURE_SIZE,))
        self.assertEqual(result.vector.dtype, np.float32)
        self.assertTrue(np.allclose(result.vector[:3], 0.0))
        self.assertEqual(POSE_FEATURE_SIZE, 99)
        self.assertEqual(HAND_FEATURE_SIZE, 63)

    def test_matches_original_fsl105_extractor_math(self) -> None:
        nose = self.pose[0].copy()
        pose_values = self.pose - nose[np.newaxis, :]
        pose_values.reshape(-1)[0:3] = 0.0
        hand_values = self.hand - nose[np.newaxis, :]
        scale = float(np.linalg.norm(hand_values[0] - hand_values[9]))
        if scale > 0.001:
            hand_values = hand_values / scale
        expected = np.concatenate(
            [pose_values.reshape(-1), hand_values.reshape(-1)]
        ).astype(np.float32)
        expected[2::3] *= Z_DAMPING

        actual = build_onehand162_from_arrays(self.pose, self.hand).vector
        np.testing.assert_array_equal(actual, expected)

    def test_missing_hand_zero_fills_fixed_slot(self) -> None:
        result = build_onehand162_from_arrays(self.pose, None)
        self.assertFalse(result.hand_present)
        self.assertTrue(result.pose_present)
        np.testing.assert_array_equal(
            result.vector[POSE_FEATURE_SIZE:], np.zeros(HAND_FEATURE_SIZE, dtype=np.float32)
        )

    def test_missing_pose_zero_fills_complete_frame(self) -> None:
        result = build_onehand162_from_arrays(None, self.hand)
        self.assertFalse(result.pose_present)
        np.testing.assert_array_equal(result.vector, np.zeros(FEATURE_SIZE, dtype=np.float32))

    def test_sequence_validation_and_presence(self) -> None:
        frame = build_onehand162_from_arrays(self.pose, self.hand).vector
        sequence = np.repeat(frame[np.newaxis, :], EXPECTED_SEQUENCE_SHAPE[0], axis=0)
        self.assertEqual(validate_sequence(sequence), [])
        self.assertEqual(sequence_presence(sequence), (1.0, 1.0))


if __name__ == "__main__":
    unittest.main()
