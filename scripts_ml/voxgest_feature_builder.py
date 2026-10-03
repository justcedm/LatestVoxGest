"""Canonical VoxGest OneHand162 feature contract.

Feature order is fixed and versioned:

    [0:99]   MediaPipe pose landmarks 0..32, XYZ, nose-relative
    [99:162] selected MediaPipe hand landmarks 0..20, XYZ, nose-relative

The hand block is divided by the wrist(0)-to-middle-MCP(9) distance when that
distance is safe. Every Z coordinate is multiplied by 0.3. Missing landmarks
are zero-filled without changing slot positions.
"""

from __future__ import annotations

from dataclasses import dataclass
from typing import Any, Iterable

import numpy as np


FEATURE_VERSION = "onehand162_20f_nose_mcp_z03_v2"
FEATURE_PROFILE = "onehand162"
SEQUENCE_LENGTH = 20
POSE_LANDMARK_COUNT = 33
HAND_LANDMARK_COUNT = 21
POSE_FEATURE_SIZE = POSE_LANDMARK_COUNT * 3
HAND_FEATURE_SIZE = HAND_LANDMARK_COUNT * 3
FEATURE_SIZE = POSE_FEATURE_SIZE + HAND_FEATURE_SIZE
EXPECTED_FRAME_SHAPE = (FEATURE_SIZE,)
EXPECTED_SEQUENCE_SHAPE = (SEQUENCE_LENGTH, FEATURE_SIZE)
NOSE_INDEX = 0
WRIST_INDEX = 0
MIDDLE_FINGER_MCP_INDEX = 9
MIN_WRIST_MCP_SCALE = 0.001
Z_DAMPING = 0.3
FEATURE_LAYOUT = {
    "pose": [0, POSE_FEATURE_SIZE],
    "selected_hand": [POSE_FEATURE_SIZE, FEATURE_SIZE],
}
NORMALIZATION_POLICY = {
    "coordinate_reference": "pose_nose_landmark_0",
    "pose_order": "mediapipe_pose_0_through_32_xyz",
    "hand_order": "mediapipe_selected_hand_0_through_20_xyz",
    "hand_scale": "euclidean_wrist_0_to_middle_finger_mcp_9",
    "minimum_hand_scale": MIN_WRIST_MCP_SCALE,
    "z_damping": Z_DAMPING,
    "missing_landmarks": "deterministic_zero_fill_no_slot_shift",
}


@dataclass(frozen=True)
class FrameFeatureResult:
    vector: np.ndarray
    hand_present: bool
    pose_present: bool
    selected_hand: str


def _normalized_hand_name(value: str) -> str:
    side = str(value).strip().lower()
    if side not in {"left", "right"}:
        raise ValueError(f"selected_hand must be 'left' or 'right', got {value!r}")
    return side


def landmarks_to_array(landmarks: Any, expected_count: int) -> np.ndarray | None:
    """Convert a MediaPipe landmark list to an exact float32 XYZ array."""
    if landmarks is None:
        return None
    points: Iterable[Any] = getattr(landmarks, "landmark", landmarks)
    points = list(points)
    if len(points) != expected_count:
        return None
    try:
        array = np.asarray(
            [[point.x, point.y, point.z] for point in points],
            dtype=np.float32,
        )
    except AttributeError:
        array = np.asarray(points, dtype=np.float32)
    if array.shape != (expected_count, 3) or not np.isfinite(array).all():
        return None
    return array


def normalize_pose(pose: np.ndarray) -> tuple[np.ndarray, np.ndarray]:
    """Return (nose-relative pose, original nose reference)."""
    pose = np.asarray(pose, dtype=np.float32)
    if pose.shape != (POSE_LANDMARK_COUNT, 3):
        raise ValueError(f"pose shape must be {(POSE_LANDMARK_COUNT, 3)}, got {pose.shape}")
    if not np.isfinite(pose).all():
        raise ValueError("pose contains NaN or Inf")
    nose = pose[NOSE_INDEX].copy()
    normalized = pose - nose[np.newaxis, :]
    normalized[NOSE_INDEX] = 0.0
    return normalized.astype(np.float32), nose


def normalize_hand(hand: np.ndarray | None, nose: np.ndarray) -> tuple[np.ndarray, bool, float]:
    """Return (nose-relative, scale-normalized hand, presence, raw scale)."""
    if hand is None:
        return np.zeros((HAND_LANDMARK_COUNT, 3), dtype=np.float32), False, 0.0
    hand = np.asarray(hand, dtype=np.float32)
    if hand.shape != (HAND_LANDMARK_COUNT, 3):
        raise ValueError(f"hand shape must be {(HAND_LANDMARK_COUNT, 3)}, got {hand.shape}")
    if not np.isfinite(hand).all():
        raise ValueError("hand contains NaN or Inf")
    normalized = hand - np.asarray(nose, dtype=np.float32)[np.newaxis, :]
    scale = float(
        np.linalg.norm(normalized[WRIST_INDEX] - normalized[MIDDLE_FINGER_MCP_INDEX])
    )
    if scale > MIN_WRIST_MCP_SCALE:
        normalized = normalized / scale
    return normalized.astype(np.float32), True, scale


def build_onehand162_from_arrays(
    pose: np.ndarray | None,
    selected_hand_landmarks: np.ndarray | None,
    *,
    selected_hand: str = "right",
) -> FrameFeatureResult:
    """Build a canonical frame from exact pose and selected-hand arrays."""
    side = _normalized_hand_name(selected_hand)
    if pose is None:
        return FrameFeatureResult(
            vector=np.zeros(FEATURE_SIZE, dtype=np.float32),
            hand_present=selected_hand_landmarks is not None,
            pose_present=False,
            selected_hand=side,
        )

    pose_values, nose = normalize_pose(pose)
    hand_values, hand_present, _scale = normalize_hand(selected_hand_landmarks, nose)
    output = np.concatenate(
        [pose_values.reshape(-1), hand_values.reshape(-1)]
    ).astype(np.float32)
    output[2::3] *= np.float32(Z_DAMPING)
    if output.shape != EXPECTED_FRAME_SHAPE:
        raise RuntimeError(f"unexpected canonical frame shape: {output.shape}")
    return FrameFeatureResult(output, hand_present, True, side)


def select_holistic_hand(results: Any, selected_hand: str = "right") -> Any:
    """Select a fixed anatomical MediaPipe Holistic hand slot."""
    side = _normalized_hand_name(selected_hand)
    return getattr(results, f"{side}_hand_landmarks", None)


def build_onehand162_from_holistic(
    results: Any,
    *,
    selected_hand: str = "right",
) -> FrameFeatureResult:
    """Build a canonical frame from MediaPipe Holistic results."""
    pose = landmarks_to_array(getattr(results, "pose_landmarks", None), POSE_LANDMARK_COUNT)
    hand = landmarks_to_array(select_holistic_hand(results, selected_hand), HAND_LANDMARK_COUNT)
    return build_onehand162_from_arrays(pose, hand, selected_hand=selected_hand)


def extract_frame_features(
    results: Any,
    *,
    selected_hand: str = "right",
) -> tuple[np.ndarray, bool, bool]:
    """Compatibility entry point used by active Holistic extractors/recorders."""
    frame = build_onehand162_from_holistic(results, selected_hand=selected_hand)
    return frame.vector, frame.hand_present, frame.pose_present


def select_hands_solution_hand(hand_results: Any, selected_hand: str = "right") -> Any:
    """Select a hand from MediaPipe Hands using its handedness classification.

    MediaPipe Hands handedness assumes a mirrored/selfie input. Callers must set
    their capture policy accordingly and record that policy in metadata.
    """
    side = _normalized_hand_name(selected_hand)
    hands = list(getattr(hand_results, "multi_hand_landmarks", None) or [])
    classifications = list(getattr(hand_results, "multi_handedness", None) or [])
    for index, handedness in enumerate(classifications):
        if index >= len(hands):
            break
        items = list(getattr(handedness, "classification", None) or [])
        label = str(getattr(items[0], "label", "")).strip().lower() if items else ""
        if label == side:
            return hands[index]
    return None


def build_onehand162_from_hands_pose(
    hand_results: Any,
    pose_results: Any,
    *,
    selected_hand: str = "right",
) -> FrameFeatureResult:
    """Build a canonical frame from separate MediaPipe Hands and Pose results."""
    pose = landmarks_to_array(
        getattr(pose_results, "pose_landmarks", None), POSE_LANDMARK_COUNT
    )
    selected = select_hands_solution_hand(hand_results, selected_hand)
    hand = landmarks_to_array(selected, HAND_LANDMARK_COUNT)
    return build_onehand162_from_arrays(pose, hand, selected_hand=selected_hand)


def validate_frame(array: np.ndarray, *, require_float32: bool = True) -> list[str]:
    array = np.asarray(array)
    issues: list[str] = []
    if array.shape != EXPECTED_FRAME_SHAPE:
        issues.append(f"shape:{list(array.shape)}")
    if require_float32 and array.dtype != np.float32:
        issues.append(f"dtype:{array.dtype}")
    if not np.isfinite(array).all():
        issues.append("non_finite")
    return issues


def validate_sequence(array: np.ndarray, *, require_float32: bool = True) -> list[str]:
    array = np.asarray(array)
    issues: list[str] = []
    if array.shape != EXPECTED_SEQUENCE_SHAPE:
        issues.append(f"shape:{list(array.shape)}")
    if require_float32 and array.dtype != np.float32:
        issues.append(f"dtype:{array.dtype}")
    if not np.isfinite(array).all():
        issues.append("non_finite")
    return issues


def sequence_presence(array: np.ndarray, *, epsilon: float = 1e-6) -> tuple[float, float]:
    """Return deterministic (pose_presence, hand_presence) frame ratios."""
    array = np.asarray(array)
    if array.shape != EXPECTED_SEQUENCE_SHAPE:
        raise ValueError(f"sequence shape must be {EXPECTED_SEQUENCE_SHAPE}, got {array.shape}")
    pose = array[:, :POSE_FEATURE_SIZE]
    hand = array[:, POSE_FEATURE_SIZE:]
    pose_present = np.linalg.norm(pose, axis=1) > epsilon
    hand_present = np.linalg.norm(hand, axis=1) > epsilon
    return float(np.mean(pose_present)), float(np.mean(hand_present))
