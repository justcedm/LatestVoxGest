"""Hybrid-Lite frame/landmark quality gate for VoxGest live recognition.

The gate is intentionally lightweight: it uses the MediaPipe landmarks already
computed by the live scripts and rejects noisy 30-frame windows before TCN/LSTM
word inference. It does not replace the current landmark recognizer.
"""

from dataclasses import dataclass, field
import os

import numpy as np

from lstm_features import (
    FEAT_SIZE,
    FULLSIGN_HAND_SIZE,
    HAND_SIZE,
    POSE_SIZE,
    SEQ_LEN,
    configured_feature_profile,
    configured_mirror_input,
    select_hand_source,
    sequence_hand_presence_ratio,
    sequence_motion_energy,
    sequence_wrist_path,
)


GOOD = "GOOD"
WARNING_HANDS_CLOSE = "WARNING_HANDS_CLOSE"
NO_HAND = "NO_HAND"
LOW_HAND_PRESENCE = "LOW_HAND_PRESENCE"
UNSTABLE_LANDMARKS = "UNSTABLE_LANDMARKS"
HANDS_OVERLAPPING = "HANDS_OVERLAPPING"
LOW_MOTION = "LOW_MOTION"
LOW_WRIST_PATH = "LOW_WRIST_PATH"
BAD_SEQUENCE = "BAD_SEQUENCE"

TRUTHY = {"1", "true", "yes", "on"}
UPPER_BODY_IDS = (0, 11, 12, 23, 24)


def _float_env(name, default):
    try:
        return float(os.environ.get(name, str(default)))
    except ValueError:
        return float(default)


def _bool_env(name, default):
    raw = os.environ.get(name)
    if raw is None:
        return bool(default)
    return raw.strip().lower() in TRUTHY


QUALITY_ENABLED = os.environ.get("VOXGEST_QUALITY_GATE", "1").strip().lower() in TRUTHY
MIN_HAND_PRESENCE = _float_env("VOXGEST_QUALITY_MIN_HAND_PRESENCE", 0.45)
NO_HAND_MAX_PRESENCE = _float_env("VOXGEST_QUALITY_NO_HAND_MAX_PRESENCE", 0.05)
MIN_MOTION = _float_env("VOXGEST_QUALITY_MIN_MOTION", 0.0035)
MIN_WRIST_PATH = _float_env("VOXGEST_QUALITY_MIN_WRIST_PATH", 0.025)
MAX_SUDDEN_JUMP = _float_env("VOXGEST_QUALITY_MAX_SUDDEN_JUMP", 0.22)
UNSTABLE_JUMP = _float_env("VOXGEST_QUALITY_UNSTABLE_JUMP", 0.11)
UNSTABLE_JUMP_RATIO = _float_env("VOXGEST_QUALITY_UNSTABLE_JUMP_RATIO", 8.0)
HAND_OVERLAP_DISTANCE = _float_env("VOXGEST_QUALITY_HAND_OVERLAP_DISTANCE", 0.035)
HAND_OVERLAP_RATIO = _float_env("VOXGEST_QUALITY_HAND_OVERLAP_RATIO", 0.20)
MIN_UPPER_BODY_RATIO = _float_env("VOXGEST_QUALITY_MIN_UPPER_BODY_RATIO", 0.60)
FULLSIGN_ALLOW_HAND_OVERLAP = _bool_env(
    "VOXGEST_FULLSIGN_ALLOW_HAND_OVERLAP",
    configured_feature_profile() == "fullsign225",
)
FULLSIGN_UNSTABLE_LARGE_JUMP_COUNT = int(
    _float_env("VOXGEST_FULLSIGN_UNSTABLE_LARGE_JUMP_COUNT", 4)
)
FULLSIGN_UNSTABLE_JUMP_RATIO = _float_env("VOXGEST_FULLSIGN_UNSTABLE_JUMP_RATIO", 10.0)
EPS = 1e-4


def quality_allows_inference(status):
    return status in {GOOD, WARNING_HANDS_CLOSE}


@dataclass
class FrameQualityResult:
    status: str = GOOD
    reason: str = "ok"
    metrics: dict = field(default_factory=dict)

    @property
    def good(self):
        return quality_allows_inference(self.status)

    @property
    def inference_allowed(self):
        return quality_allows_inference(self.status)

    def failure_reason(self):
        return "ok" if self.good else f"quality:{self.status}"


def _landmarks_to_array(landmarks):
    if landmarks is None:
        return None
    return np.array([[lm.x, lm.y, lm.z] for lm in landmarks.landmark], dtype=np.float32)


def _upper_body_visible(pose_landmarks):
    if pose_landmarks is None:
        return False
    landmarks = pose_landmarks.landmark
    visible = 0
    for idx in UPPER_BODY_IDS:
        lm = landmarks[idx]
        if getattr(lm, "visibility", 1.0) >= 0.30:
            visible += 1
    return visible >= 3


def _min_hand_distance(left, right):
    if left is None or right is None:
        return None
    distances = np.linalg.norm(left[:, np.newaxis, :] - right[np.newaxis, :, :], axis=2)
    return float(np.min(distances))


def capture_frame_quality(results, hand_preference=None, mirrored_input=None):
    """Capture per-frame landmark quality facts from MediaPipe results."""
    mirrored = configured_mirror_input(mirrored_input)
    left = _landmarks_to_array(results.left_hand_landmarks)
    right = _landmarks_to_array(results.right_hand_landmarks)
    _, selected = select_hand_source(results, hand_preference, mirrored)

    if configured_feature_profile() == "fullsign225":
        selected_present = left is not None or right is not None
    else:
        selected_present = selected is not None

    return {
        "pose_present": results.pose_landmarks is not None,
        "upper_body_present": _upper_body_visible(results.pose_landmarks),
        "selected_hand_present": selected_present,
        "left_hand_present": left is not None,
        "right_hand_present": right is not None,
        "both_hands_present": left is not None and right is not None,
        "min_hand_distance": _min_hand_distance(left, right),
        "mirrored_input": mirrored,
    }


def _sequence_hands(seq):
    arr = np.asarray(seq, dtype=np.float32)
    if arr.ndim != 2 or arr.shape[0] != SEQ_LEN:
        return None
    hand_width = arr.shape[1] - POSE_SIZE
    if hand_width == HAND_SIZE:
        return arr[:, POSE_SIZE:].reshape(SEQ_LEN, 1, 21, 3)
    if hand_width == FULLSIGN_HAND_SIZE:
        return arr[:, POSE_SIZE:].reshape(SEQ_LEN, 2, 21, 3)
    return None


def _present_hand_points(frame_hands):
    points = []
    for hand in frame_hands:
        if np.linalg.norm(hand.reshape(-1)) > EPS:
            points.append(hand)
    if not points:
        return None
    return np.concatenate(points, axis=0)


def _jump_metrics(seq):
    hands = _sequence_hands(seq)
    if hands is None:
        return {
            "max_centroid_jump": 0.0,
            "median_centroid_jump": 0.0,
            "jump_ratio": 0.0,
            "large_jump_count": 0,
        }

    centroids = []
    valid = []
    for frame_hands in hands:
        points = _present_hand_points(frame_hands)
        if points is None:
            centroids.append(np.zeros(3, dtype=np.float32))
            valid.append(False)
        else:
            centroids.append(points.mean(axis=0))
            valid.append(True)

    jumps = []
    for idx in range(1, len(centroids)):
        if valid[idx] and valid[idx - 1]:
            jumps.append(float(np.linalg.norm(centroids[idx] - centroids[idx - 1])))

    if not jumps:
        return {
            "max_centroid_jump": 0.0,
            "median_centroid_jump": 0.0,
            "jump_ratio": 0.0,
            "large_jump_count": 0,
        }

    jumps = np.asarray(jumps, dtype=np.float32)
    median_jump = float(np.median(jumps))
    max_jump = float(np.max(jumps))
    return {
        "max_centroid_jump": max_jump,
        "median_centroid_jump": median_jump,
        "jump_ratio": float(max_jump / max(median_jump, EPS)),
        "large_jump_count": int(np.sum(jumps > UNSTABLE_JUMP)),
    }


def _frame_ratios(frame_records):
    if not frame_records:
        return {
            "upper_body_ratio": 1.0,
            "record_hand_presence_ratio": None,
            "both_hands_ratio": 0.0,
            "hand_overlap_ratio": 0.0,
        }

    n = float(len(frame_records))
    upper = sum(1 for item in frame_records if item.get("upper_body_present"))
    hand = sum(1 for item in frame_records if item.get("selected_hand_present"))
    both = sum(1 for item in frame_records if item.get("both_hands_present"))
    overlap = sum(
        1
        for item in frame_records
        if item.get("min_hand_distance") is not None
        and item["min_hand_distance"] < HAND_OVERLAP_DISTANCE
    )
    return {
        "upper_body_ratio": upper / n,
        "record_hand_presence_ratio": hand / n,
        "both_hands_ratio": both / n,
        "hand_overlap_ratio": overlap / n,
    }


def evaluate_sequence_quality(seq, frame_records=None, require_upper_body=True):
    """Return a FrameQualityResult for a 30-frame dynamic sequence."""
    if not QUALITY_ENABLED:
        return FrameQualityResult(metrics={"quality_gate_enabled": False})

    arr = np.asarray(seq, dtype=np.float32)
    expected_shape = (SEQ_LEN, FEAT_SIZE)
    if arr.shape != expected_shape:
        return FrameQualityResult(
            BAD_SEQUENCE,
            f"shape={arr.shape}, expected={expected_shape}",
            {"shape": list(arr.shape), "expected_shape": list(expected_shape)},
        )

    feature_profile = configured_feature_profile()
    motion = sequence_motion_energy(arr)
    wrist_path = sequence_wrist_path(arr)
    seq_presence = sequence_hand_presence_ratio(arr)
    ratios = _frame_ratios(frame_records)
    jump = _jump_metrics(arr)
    presence = ratios["record_hand_presence_ratio"]
    if presence is None:
        presence = seq_presence

    metrics = {
        "feature_profile": feature_profile,
        "hand_presence_ratio": round(float(presence), 6),
        "sequence_hand_presence_ratio": round(float(seq_presence), 6),
        "upper_body_ratio": round(float(ratios["upper_body_ratio"]), 6),
        "both_hands_ratio": round(float(ratios["both_hands_ratio"]), 6),
        "hand_overlap_ratio": round(float(ratios["hand_overlap_ratio"]), 6),
        "motion": round(float(motion), 6),
        "wrist_path": round(float(wrist_path), 6),
        **{key: round(value, 6) if isinstance(value, float) else value for key, value in jump.items()},
    }

    if require_upper_body and ratios["upper_body_ratio"] < MIN_UPPER_BODY_RATIO:
        return FrameQualityResult(BAD_SEQUENCE, "missing upper-body pose reference", metrics)
    if presence <= NO_HAND_MAX_PRESENCE:
        if feature_profile == "fullsign225":
            return FrameQualityResult(LOW_HAND_PRESENCE, "no hand visible enough for fullsign sequence", metrics)
        return FrameQualityResult(NO_HAND, "no configured hand visible", metrics)
    if presence < MIN_HAND_PRESENCE:
        return FrameQualityResult(LOW_HAND_PRESENCE, "too few hand frames in sequence", metrics)
    if jump["max_centroid_jump"] >= MAX_SUDDEN_JUMP:
        return FrameQualityResult(BAD_SEQUENCE, "excessive sudden landmark jump", metrics)

    if feature_profile == "fullsign225":
        if (
            jump["large_jump_count"] >= FULLSIGN_UNSTABLE_LARGE_JUMP_COUNT
            or (
                jump["max_centroid_jump"] >= UNSTABLE_JUMP
                and jump["jump_ratio"] >= FULLSIGN_UNSTABLE_JUMP_RATIO
            )
        ):
            return FrameQualityResult(UNSTABLE_LANDMARKS, "landmark trajectory is severely unstable", metrics)
        if (
            ratios["hand_overlap_ratio"] >= HAND_OVERLAP_RATIO
            and ratios["both_hands_ratio"] >= HAND_OVERLAP_RATIO
        ):
            if FULLSIGN_ALLOW_HAND_OVERLAP:
                return FrameQualityResult(
                    WARNING_HANDS_CLOSE,
                    "hands close/touching; allowed for fullsign225",
                    metrics,
                )
            return FrameQualityResult(HANDS_OVERLAPPING, "two detected hands are too close", metrics)
        return FrameQualityResult(GOOD, "ok", metrics)

    if ratios["hand_overlap_ratio"] >= HAND_OVERLAP_RATIO:
        return FrameQualityResult(HANDS_OVERLAPPING, "two detected hands are too close", metrics)
    if (
        jump["large_jump_count"] >= 2
        or (
            jump["max_centroid_jump"] >= UNSTABLE_JUMP
            and jump["jump_ratio"] >= UNSTABLE_JUMP_RATIO
        )
    ):
        return FrameQualityResult(UNSTABLE_LANDMARKS, "landmark trajectory is unstable", metrics)
    if motion < MIN_MOTION:
        return FrameQualityResult(LOW_MOTION, "sequence motion is too low", metrics)
    if wrist_path < MIN_WRIST_PATH:
        return FrameQualityResult(LOW_WRIST_PATH, "wrist path is too short", metrics)

    return FrameQualityResult(GOOD, "ok", metrics)
