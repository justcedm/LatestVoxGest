import importlib.util
import sys
import unittest
from pathlib import Path

import numpy as np


ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts_ml"))

SPEC = importlib.util.spec_from_file_location(
    "mapua_npy_converter",
    ROOT / "scripts_ml" / "102_convert_mapua_original_npy_fullsign225.py",
)
assert SPEC is not None and SPEC.loader is not None
CONVERTER = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = CONVERTER
SPEC.loader.exec_module(CONVERTER)

from fullsign225_feature_builder import build_fullsign225_from_arrays


class MapuaNpyConverterTest(unittest.TestCase):
    def make_source(self) -> np.ndarray:
        source = np.zeros((75, 225), dtype=np.float64)
        pose = np.zeros((33, 3), dtype=np.float64)
        pose[:, 0] = np.linspace(0.40, 0.70, 33)
        pose[:, 1] = np.linspace(0.30, 0.90, 33)
        pose[:, 2] = np.linspace(-0.20, 0.20, 33)
        left = np.zeros((21, 3), dtype=np.float64)
        left[:, 0] = np.linspace(0.45, 0.65, 21)
        left[:, 1] = np.linspace(0.40, 0.70, 21)
        left[:, 2] = np.linspace(-0.05, 0.05, 21)
        for frame in range(75):
            source[frame, 0:99] = pose.reshape(-1)
            moved = left.copy()
            if frame >= 30:
                moved[:, 0] += 0.25
            source[frame, 99:162] = moved.reshape(-1)
        return source

    def test_conversion_is_float32_fullsign225_and_uses_npy_motion(self) -> None:
        source = self.make_source()
        complete, sequence, metadata = CONVERTER.convert_source(source, 3, 3)
        self.assertEqual((27, 33), (metadata["motion_start"], metadata["motion_end"]))
        self.assertEqual((7, 225), complete.shape)
        self.assertEqual((48, 225), sequence.shape)
        self.assertEqual(np.float32, sequence.dtype)
        self.assertTrue(np.isfinite(sequence).all())
        self.assertEqual(75, metadata["pose_present_frames"])
        self.assertEqual(75, metadata["left_hand_present_frames"])
        self.assertEqual(0, metadata["right_hand_present_frames"])
        self.assertTrue(np.all(sequence[:, 162:225] == 0.0))
        self.assertTrue(np.all(sequence[:, 0:3] == 0.0))

    def test_spatial_normalization_delegates_to_shared_builder(self) -> None:
        source = self.make_source()
        complete, _, metadata = CONVERTER.convert_source(source, 3, 3)
        frame = metadata["motion_start"]
        expected = build_fullsign225_from_arrays(
            source[frame, 0:99].reshape(33, 3),
            source[frame, 99:162].reshape(21, 3),
            None,
            input_mirrored=False,
        ).vector
        np.testing.assert_array_equal(expected, complete[0])

    def test_short_internal_hand_gap_is_bounded(self) -> None:
        values = [np.ones((21, 3), dtype=np.float32) for _ in range(8)]
        values[3] = None
        values[4] = None
        filled, count = CONVERTER.interpolate_short_gaps(values, max_gap=2)
        self.assertEqual(2, count)
        self.assertIsNotNone(filled[3])
        self.assertIsNotNone(filled[4])
        values[2:6] = [None, None, None, None]
        unfilled, count = CONVERTER.interpolate_short_gaps(values, max_gap=2)
        self.assertEqual(0, count)
        self.assertTrue(all(item is None for item in unfilled[2:6]))

    def test_rejects_wrong_source_contract(self) -> None:
        with self.assertRaisesRegex(ValueError, "source shape"):
            CONVERTER.convert_source(np.zeros((48, 225), dtype=np.float64), 3, 3)
        with self.assertRaisesRegex(ValueError, "source dtype"):
            CONVERTER.convert_source(np.zeros((75, 225), dtype=np.float32), 3, 3)


if __name__ == "__main__":
    unittest.main()
