"""Canonical FullSign225 frame builder shared by offline experiments.

The numeric contract mirrors StandardFullSign225FeatureBuilder.kt:
pose99 | anatomical-left63 | anatomical-right63, nose-relative, independent
wrist-to-middle-MCP hand scaling, global Z damping, deterministic zero blocks,
and no mirroring or anatomical slot swapping.
"""

from __future__ import annotations

from dataclasses import dataclass
from typing import Any, Iterable

import numpy as np

FRAME_FEATURE_VERSION = "fullsign225_frame_v1"
POSE_LANDMARK_COUNT = 33
HAND_LANDMARK_COUNT = 21
POSE_SIZE = POSE_LANDMARK_COUNT * 3
HAND_SIZE = HAND_LANDMARK_COUNT * 3
FEATURE_SIZE = POSE_SIZE + HAND_SIZE * 2
LEFT_START = POSE_SIZE
RIGHT_START = POSE_SIZE + HAND_SIZE
NOSE_INDEX = 0
WRIST_INDEX = 0
MIDDLE_FINGER_MCP_INDEX = 9
MIN_WRIST_MCP_SCALE = np.float32(0.001)
Z_DAMPING = np.float32(0.3)
FEATURE_LAYOUT = {
    "pose": [0, POSE_SIZE],
    "anatomical_left_hand": [LEFT_START, RIGHT_START],
    "anatomical_right_hand": [RIGHT_START, FEATURE_SIZE],
}
NORMALIZATION_POLICY = {
    "coordinate_reference": "pose_nose_landmark_0",
    "pose_order": "mediapipe_pose_0_through_32_xyz",
    "hand_order": "mediapipe_hand_0_through_20_xyz",
    "hand_scale": "independent_euclidean_wrist_0_to_middle_finger_mcp_9",
    "minimum_hand_scale": float(MIN_WRIST_MCP_SCALE),
    "z_damping": float(Z_DAMPING),
    "missing_pose": "zero_entire_225_frame",
    "missing_hand": "zero_fixed_anatomical_63_slot",
    "mirrored_input": False,
    "slot_swapping": False,
}


@dataclass(frozen=True)
class FullSign225Frame:
    vector: np.ndarray
    pose_present: bool
    left_hand_present: bool
    right_hand_present: bool
    left_hand_scale: float
    right_hand_scale: float


def landmarks_to_array(landmarks: Any, expected_count: int) -> np.ndarray | None:
    if landmarks is None:
        return None
    points: Iterable[Any] = getattr(landmarks, "landmark", landmarks)
    points = list(points)
    if len(points) != expected_count:
        return None
    try:
        result = np.asarray(
            [[point.x, point.y, point.z] for point in points], dtype=np.float32
        )
    except AttributeError:
        result = np.asarray(points, dtype=np.float32)
    if result.shape != (expected_count, 3) or not np.isfinite(result).all():
        return None
    return result


def _normalize_hand(
    hand: np.ndarray | None, nose: np.ndarray
) -> tuple[np.ndarray, bool, float]:
    if hand is None:
        return np.zeros((HAND_LANDMARK_COUNT, 3), dtype=np.float32), False, 0.0
    values = np.asarray(hand, dtype=np.float32) - nose[np.newaxis, :]
    scale = np.float32(
        np.linalg.norm(values[WRIST_INDEX] - values[MIDDLE_FINGER_MCP_INDEX])
    )
    if scale > MIN_WRIST_MCP_SCALE:
        values = values / scale
    return values.astype(np.float32), True, float(scale)


def build_fullsign225_from_arrays(
    pose: np.ndarray | None,
    anatomical_left_hand: np.ndarray | None,
    anatomical_right_hand: np.ndarray | None,
    *,
    input_mirrored: bool = False,
) -> FullSign225Frame:
    if input_mirrored:
        raise ValueError("FullSign225 requires canonical unmirrored model input")
    pose_values = landmarks_to_array(pose, POSE_LANDMARK_COUNT)
    left_values = landmarks_to_array(anatomical_left_hand, HAND_LANDMARK_COUNT)
    right_values = landmarks_to_array(anatomical_right_hand, HAND_LANDMARK_COUNT)
    if pose_values is None:
        return FullSign225Frame(
            np.zeros(FEATURE_SIZE, dtype=np.float32),
            False,
            left_values is not None,
            right_values is not None,
            0.0,
            0.0,
        )
    nose = pose_values[NOSE_INDEX].copy()
    normalized_pose = (pose_values - nose[np.newaxis, :]).astype(np.float32)
    normalized_pose[NOSE_INDEX] = np.float32(0.0)
    normalized_left, left_present, left_scale = _normalize_hand(left_values, nose)
    normalized_right, right_present, right_scale = _normalize_hand(right_values, nose)
    output = np.concatenate(
        (
            normalized_pose.reshape(-1),
            normalized_left.reshape(-1),
            normalized_right.reshape(-1),
        )
    ).astype(np.float32)
    output[2::3] *= Z_DAMPING
    if output.shape != (FEATURE_SIZE,) or not np.isfinite(output).all():
        raise ValueError("invalid canonical FullSign225 output")
    return FullSign225Frame(
        output, True, left_present, right_present, left_scale, right_scale
    )


def build_fullsign225_from_holistic(
    results: Any, *, input_mirrored: bool = False
) -> FullSign225Frame:
    return build_fullsign225_from_arrays(
        landmarks_to_array(getattr(results, "pose_landmarks", None), POSE_LANDMARK_COUNT),
        landmarks_to_array(getattr(results, "left_hand_landmarks", None), HAND_LANDMARK_COUNT),
        landmarks_to_array(getattr(results, "right_hand_landmarks", None), HAND_LANDMARK_COUNT),
        input_mirrored=input_mirrored,
    )
