import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts_ml"))
from core5_ood_fusion_replay import first_stable, select_variant


class OodFusionTests(unittest.TestCase):
    def test_first_stable_uses_only_approved_variant_decisions(self):
        rows = [{"duration_target_ms": 1300, "end_ms": t,
                 "classifier_top1": "HELLO", "classifier_confidence": .99,
                 "ood_score": .9, "geometry_score": .2,
                 "decisions": {"binary_ood": accepted}}
                for t, accepted in ((1000, False), (1200, True), (1400, True),
                                    (1600, True))]
        self.assertEqual(first_stable(rows, "binary_ood")["end_ms"], 1600)

    def test_calibration_selection_requires_recall(self):
        metrics = {"binary_ood": {"core5_recall": .8, "non_core5_rejection": .99},
                   "combined_product": {"core5_recall": .9, "non_core5_rejection": .9},
                   "confidence_only": {"core5_recall": .9, "non_core5_rejection": .6},
                   "margin_only": {"core5_recall": .9, "non_core5_rejection": .5},
                   "binary_and_geometry": {"core5_recall": .7, "non_core5_rejection": 1.0},
                   "binary_geometry_conf95": {"core5_recall": .6, "non_core5_rejection": 1.0}}
        self.assertEqual(select_variant(metrics), "combined_product")


if __name__ == "__main__":
    unittest.main()
