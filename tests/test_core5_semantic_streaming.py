import sys
import unittest
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts_ml"))
from core5_contract import canonical
from core5_semantic_geometry import frame_features, trajectory_features
from core5_streaming_window_replay import stable_peak


def sample_frame(scale=1.0, offset=0.0):
    pose = np.zeros((33, 3), dtype=np.float32)
    pose[:, :2] = (.5, .5)
    pose[0, :2] = (.5, .2)
    pose[2, :2], pose[5, :2] = (.48, .23), (.52, .23)
    pose[9, :2], pose[10, :2] = (.48, .3), (.52, .3)
    pose[11, :2], pose[12, :2] = (.4, .5), (.6, .5)
    pose[13, :2], pose[14, :2] = (.38, .6), (.62, .6)
    pose[15, :2], pose[16, :2] = (.4, .7), (.6, .7)
    hand = np.zeros((21, 3), dtype=np.float32)
    for i in range(21):
        hand[i, :2] = (.5 + (i % 5) * .012, .33 + (i // 5) * .02)
    pose[:, :2] = pose[:, :2] * scale + offset
    hand[:, :2] = hand[:, :2] * scale + offset
    return {"timestamp_ms": 1000, "pose_present": True, "left_present": False,
            "right_present": True, "pose": pose.tolist(), "left": None, "right": hand.tolist()}, pose, hand


class SemanticGeometryTests(unittest.TestCase):
    def test_body_relative_geometry_invariant_to_translation_and_scale(self):
        original, _, _ = sample_frame()
        shifted, _, _ = sample_frame(.8, .07)
        a = frame_features(original)["right"]
        b = frame_features(shifted)["right"]
        for name in ("palm_to_nose_distance", "palm_to_mouth_distance",
                     "palm_to_shoulder_distance", "hand_opening"):
            self.assertAlmostEqual(a[name], b[name], places=5)

    def test_225_hand_and_pose_units_cannot_be_subtracted_for_face_distance(self):
        frame, pose, hand = sample_frame()
        vector = canonical(pose, None, hand).reshape(75, 3)
        real = np.linalg.norm(hand[0, :2] - (pose[9, :2] + pose[10, :2]) / 2)
        naive = np.linalg.norm(vector[54, :2] - (vector[9, :2] + vector[10, :2]) / 2)
        self.assertGreater(abs(real - naive), .1)
        self.assertAlmostEqual(frame_features(frame)["right"]["wrist_to_mouth_distance"],
                               real / np.linalg.norm(pose[11, :2] - pose[12, :2]), places=5)

    def test_trajectory_reports_motion_and_presence(self):
        frames = []
        for i in range(5):
            frame, _, _ = sample_frame()
            frame["timestamp_ms"] = i * 100
            for point in frame["right"]:
                point[0] += i * .01
            frames.append(frame)
        summary, per_frame = trajectory_features(frames)
        self.assertEqual(summary["dominant_side"], "right")
        self.assertGreater(summary["trajectory_length"], 0)
        self.assertEqual(len(per_frame), 5)

    def test_first_stable_peak_is_earliest_across_durations(self):
        rows = []
        for duration, starts in ((1500, (1500, 1700, 1900)), (2000, (1200, 1400, 1600))):
            for t in starts:
                rows.append({"duration_target_ms": duration, "end_ms": t,
                             "top1": "HELLO", "confidence": .99,
                             "basic_quality": True, "geometry_source_supported_exploratory": True})
        result = stable_peak(rows, "HELLO")
        self.assertEqual(result["end_ms"], 1600)
        self.assertEqual(result["duration_target_ms"], 2000)


if __name__ == "__main__":
    unittest.main()
