import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts_ml"))
from core5_ood_train_eval import cutoff_for_recall, method_metrics


class OodEvaluationTests(unittest.TestCase):
    def test_positive_only_calibration_cutoff(self):
        scores = [.01, .1, .2, .3, .4, .5, .6, .7, .8, .9, .99]
        positives = [True] * 10 + [False]
        self.assertEqual(cutoff_for_recall(scores, positives, .9), .1)

    def test_binary_rates_are_separate(self):
        rows = [{"binary_label": "CORE5_LIKE"}] * 2 + [{"binary_label": "OTHER_FSL"}] * 3
        result = method_metrics(rows, [True, False, False, True, False])
        self.assertEqual(result["core5_recall"], .5)
        self.assertAlmostEqual(result["non_core5_rejection"], 2 / 3)
        self.assertAlmostEqual(result["false_accept_rate"], 1 / 3)


if __name__ == "__main__":
    unittest.main()
