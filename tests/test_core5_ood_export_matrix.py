import csv
import json
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts_ml"))
from core5_ood_export_matrix import export


class OodMatrixTests(unittest.TestCase):
    def test_export_safe_scores_and_reject_duplicate_output(self):
        with tempfile.TemporaryDirectory() as directory:
            source = Path(directory) / "private.json"
            output = Path(directory) / "safe.csv"
            source.write_text(json.dumps({
                "official_test_opened": False,
                "samsung_tuning_used": False,
                "source_calibration_selected_variant": "combined_product",
                "samsung_events": [{
                    "event_id": "sample-event", "expected": "NON_SIGN:wave",
                    "final_tensor": {"classifier_top1": "HELLO",
                                     "classifier_confidence": .9, "ood_score": .1,
                                     "geometry_score": 2.0, "geometry_source_cutoff": 1.0},
                    "first_stable": {name: None for name in
                                     ("confidence_only", "binary_ood", "combined_product",
                                      "binary_and_geometry")},
                    "final_accept_reject": "EXPLORATORY_REJECT",
                    "legacy_end_reason": "EVENT_TIMEOUT", "legacy_raw_top1": "HELLO",
                    "legacy_accepted": False, "windows_evaluated": 3,
                }],
            }), encoding="utf-8")
            self.assertEqual(export(source, output)["events"], 1)
            with output.open(newline="", encoding="utf-8") as stream:
                row = next(csv.DictReader(stream))
            self.assertEqual(row["intended_event"], "NON_SIGN:wave")
            self.assertEqual(row["final_accept_reject"], "EXPLORATORY_REJECT")
            self.assertNotIn("path", output.read_text(encoding="utf-8").lower())
            with self.assertRaises(FileExistsError):
                export(source, output)

    def test_sealed_test_use_is_refused(self):
        with tempfile.TemporaryDirectory() as directory:
            source = Path(directory) / "invalid.json"
            source.write_text(json.dumps({"official_test_opened": True,
                                          "samsung_tuning_used": False}), encoding="utf-8")
            with self.assertRaises(ValueError):
                export(source, Path(directory) / "should_not_exist.csv")


if __name__ == "__main__":
    unittest.main()
