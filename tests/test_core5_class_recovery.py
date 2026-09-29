import json
import sys
import tempfile
import unittest
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts_ml"))
from core5_class_verifiers import load_source, metrics
from core5_yes_no_forensics import time_decimate


class ClassRecoveryTests(unittest.TestCase):
    def test_cadence_selection_preserves_endpoints_and_monotonic_time(self):
        times = np.arange(0, 1000, 16, dtype=np.int64)
        frames = np.broadcast_to(np.arange(len(times), dtype=np.float32)[:, None],
                                 (len(times), 225)).copy()
        for phase in (0, 33, 66):
            chosen, selected_times, count = time_decimate(frames, times, phase)
            self.assertEqual(chosen.shape, (count, 225))
            self.assertEqual(selected_times[0], times[0])
            self.assertEqual(selected_times[-1], times[-1])
            self.assertTrue(np.all(np.diff(selected_times) > 0))
            self.assertLess(count, len(times) / 2)

    def test_sealed_official_test_manifest_is_refused(self):
        with tempfile.TemporaryDirectory() as folder:
            manifest = Path(folder) / "manifest.json"
            manifest.write_text(json.dumps({"official_test_opened": True}), encoding="utf-8")
            with self.assertRaises(ValueError):
                load_source(manifest, Path(folder))

    def test_metrics_separate_raw_wrong_accept_from_rejection(self):
        rows = [
            {"expected": "YES", "proposed": "YES", "decisions": {"global_ood": True}},
            {"expected": "NO", "proposed": "HELLO", "decisions": {"global_ood": True}},
            {"expected": "NON_SIGN:wave", "proposed": "YES", "decisions": {"global_ood": False}},
        ]
        # Exercise the complete metric schema with identical test decisions.
        for row in rows:
            row["decisions"] = {name: row["decisions"]["global_ood"] for name in
                                ("global_ood", "global_product", "class_confidence", "class_margin",
                                 "class_ovr", "class_prototype", "class_mahalanobis")}
        result = metrics(rows)["global_ood"]
        self.assertEqual(result["correct_accepted"], 1)
        self.assertEqual(result["wrong_accepted"], 1)
        self.assertEqual(result["negative_false_accepted"], 0)


if __name__ == "__main__":
    unittest.main()
