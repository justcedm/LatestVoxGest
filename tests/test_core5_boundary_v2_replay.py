import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts_ml"))
from core5_boundary_v2_replay import Config, replay


def synthetic(levels):
    rows = []
    for index, (hand, level) in enumerate(levels):
        rows.append({"timestamp_ms": index * 100, "pose": True,
                     "left": False, "right": hand,
                     "translation_xy_per_s": level if hand else None,
                     "articulation_xy_per_s": level if hand else None,
                     "arm_xy_per_s": level if hand else None})
    return rows


CONFIG = Config(start=2, end=.6, dwell_ms=300, min_event_ms=300,
                post_ms=100, no_hand_ms=300)


class BoundaryV2ReplayTests(unittest.TestCase):
    def test_visible_hand_settle_can_end(self):
        rows = synthetic([(False, 0)] * 3 + [(True, 4)] * 6 + [(True, .1)] * 18)
        result = replay(rows, CONFIG)
        self.assertEqual(len(result["events"]), 1)
        self.assertEqual(result["events"][0]["reason"], "VISIBLE_MOTION_SETTLE")

    def test_resumed_motion_cancels_pending_end(self):
        rows = synthetic([(False, 0)] * 3 + [(True, 4)] * 6 +
                         [(True, .1)] * 3 + [(True, 4)] * 5 + [(True, .1)] * 16)
        result = replay(rows, CONFIG)
        self.assertEqual(len(result["events"]), 1)
        self.assertGreater(result["events"][0]["end_index"], 17)

    def test_neutral_never_starts(self):
        result = replay(synthetic([(False, 0)] * 40), CONFIG)
        self.assertEqual(result["events"], [])

    def test_rearm_prevents_hand_still_present_ghost(self):
        rows = synthetic([(False, 0)] * 3 + [(True, 4)] * 6 +
                         [(True, .1)] * 14 + [(True, 4)] * 8)
        result = replay(rows, CONFIG)
        self.assertEqual(len(result["events"]), 1)
        self.assertGreater(result["high_motion_frames_after_first_cut"], 0)


if __name__ == "__main__":
    unittest.main()
