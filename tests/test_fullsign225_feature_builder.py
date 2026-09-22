import sys
import unittest
from pathlib import Path

import numpy as np

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts_ml"))

from fullsign225_feature_builder import (  # noqa: E402
    FEATURE_SIZE,
    build_fullsign225_from_arrays,
)


class FullSign225FeatureBuilderTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        fixture = (
            ROOT
            / "android_dry_run/app/src/main/assets/model"
            / "fsl_fullsign225_20f_105_v1/golden_fullsign225_feature_fixture_f32.bin"
        )
        cls.values = np.fromfile(fixture, dtype="<f4")
        if cls.values.size != 1350:
            raise AssertionError(f"unexpected fixture size: {cls.values.size}")
        raw = cls.values[:FEATURE_SIZE]
        cls.pose = raw[:99].reshape(33, 3)
        cls.left = raw[99:162].reshape(21, 3)
        cls.right = raw[162:225].reshape(21, 3)

    def expected(self, case_index):
        start = FEATURE_SIZE * (case_index + 1)
        return self.values[start : start + FEATURE_SIZE]

    def test_five_case_android_golden_parity(self):
        cases = (
            (self.pose, self.left, self.right),
            (self.pose, self.left, None),
            (self.pose, None, self.right),
            (self.pose, None, None),
            (None, self.left, self.right),
        )
        for index, case in enumerate(cases):
            with self.subTest(index=index):
                actual = build_fullsign225_from_arrays(*case).vector
                np.testing.assert_allclose(actual, self.expected(index), rtol=0.0, atol=1e-6)

    def test_mirroring_fails_closed(self):
        with self.assertRaises(ValueError):
            build_fullsign225_from_arrays(
                self.pose, self.left, self.right, input_mirrored=True
            )

    def test_anatomical_slots_never_swap(self):
        left_only = build_fullsign225_from_arrays(self.pose, self.left, None).vector
        right_only = build_fullsign225_from_arrays(self.pose, None, self.right).vector
        self.assertGreater(np.linalg.norm(left_only[99:162]), 0.0)
        self.assertEqual(float(np.linalg.norm(left_only[162:225])), 0.0)
        self.assertEqual(float(np.linalg.norm(right_only[99:162])), 0.0)
        self.assertGreater(np.linalg.norm(right_only[162:225]), 0.0)


if __name__ == "__main__":
    unittest.main()
