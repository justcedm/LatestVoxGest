import os

import numpy as np

SEQ_LEN = 30
POSE_SIZE = 99
HAND_SIZE = 63
FEAT_SIZE = POSE_SIZE + HAND_SIZE

VALID_HAND_PREFERENCES = {"auto", "right", "left"}
TRUTHY = {"1", "true", "yes", "on"}

HEAD_LANDMARKS = set(range(0, 11))
TORSO_LANDMARKS = {11, 12, 23, 24}
LEFT_ARM_LANDMARKS = {11, 13, 15, 17, 19, 21}
RIGHT_ARM_LANDMARKS = {12, 14, 16, 18, 20, 22}


def configured_hand_preference(hand_preference=None):
    """Return the configured dynamic-hand selection policy."""
    raw = hand_preference or os.environ.get("VOXGEST_DOMINANT_HAND", "auto")
    pref = str(raw).strip().lower()
    return pref if pref in VALID_HAND_PREFERENCES else "auto"


def configured_mirror_input(mirrored_input=None):
    """Return whether hand labels should be mapped from mirrored camera input."""
    if mirrored_input is not None:
        return bool(mirrored_input)
    return os.environ.get("VOXGEST_MIRROR_INPUT", "0").strip().lower() in TRUTHY


def single_hand_pose_enabled():
    """Return whether non-dominant pose landmarks are masked from LSTM input."""
    return os.environ.get("VOXGEST_SINGLE_HAND_POSE", "1").strip().lower() in TRUTHY


def _pose_keep_indices(hand_side):
    keep = set(HEAD_LANDMARKS) | set(TORSO_LANDMARKS)
    if hand_side == "left":
        keep |= LEFT_ARM_LANDMARKS
    elif hand_side == "right":
        keep |= RIGHT_ARM_LANDMARKS
    else:
        keep |= LEFT_ARM_LANDMARKS | RIGHT_ARM_LANDMARKS
    return keep


def mask_pose_to_single_hand(pose, hand_side):
    """Keep head/torso and the selected arm; zero the non-dominant arm."""
    if not single_hand_pose_enabled():
        return pose

    out = np.asarray(pose, dtype=np.float32).copy()
    keep = _pose_keep_indices(hand_side)
    for idx in range(out.shape[0]):
        if idx not in keep:
            out[idx, :] = 0.0
    return out


def mediapipe_side_for_preference(hand_preference=None, mirrored_input=None):
    """Map a physical hand preference to the MediaPipe label in the input."""
    pref = configured_hand_preference(hand_preference)
    if pref == "auto":
        return pref
    if configured_mirror_input(mirrored_input):
        return "left" if pref == "right" else "right"
    return pref


def hand_mapping_text(hand_preference=None, mirrored_input=None):
    """Human-readable physical-hand mapping for logs and live tools."""
    pref = configured_hand_preference(hand_preference)
    mirrored = configured_mirror_input(mirrored_input)
    if pref == "auto":
        return "auto physical hand selection"
    media_side = mediapipe_side_for_preference(pref, mirrored)
    mirror_note = "mirrored input" if mirrored else "unmirrored input"
    return f"physical {pref} hand -> MediaPipe {media_side} hand ({mirror_note})"


def apply_sequence_feature_policy(seq, hand_preference=None, mirrored_input=None):
    """Apply current single-hand pose policy to an existing Nx162 sequence."""
    arr = np.asarray(seq, dtype=np.float32).copy()
    if arr.ndim != 2 or arr.shape[1] != FEAT_SIZE:
        return arr

    media_side = mediapipe_side_for_preference(hand_preference, mirrored_input)
    if media_side not in {"left", "right"}:
        return enforce_nose_anchor(arr)

    seq_len = arr.shape[0]
    pose = arr[:, :POSE_SIZE].reshape(seq_len, 33, 3)
    for frame_idx in range(seq_len):
        pose[frame_idx] = mask_pose_to_single_hand(pose[frame_idx], media_side)
    arr[:, :POSE_SIZE] = pose.reshape(seq_len, POSE_SIZE)
    return enforce_nose_anchor(arr)


def select_hand_source(results, hand_preference=None, mirrored_input=None):
    """Return (side, landmarks) for the configured dynamic hand."""
    pref = mediapipe_side_for_preference(hand_preference, mirrored_input)
    if pref == "right":
        return "right", results.right_hand_landmarks
    if pref == "left":
        return "left", results.left_hand_landmarks
    if configured_mirror_input(mirrored_input):
        if results.left_hand_landmarks is not None:
            return "left", results.left_hand_landmarks
        if results.right_hand_landmarks is not None:
            return "right", results.right_hand_landmarks
        return pref, None
    if results.right_hand_landmarks is not None:
        return "right", results.right_hand_landmarks
    if results.left_hand_landmarks is not None:
        return "left", results.left_hand_landmarks
    return pref, None


def select_hand_landmarks(results, hand_preference=None, mirrored_input=None):
    """Select the hand used by the single-hand dynamic feature vector."""
    _, landmarks = select_hand_source(results, hand_preference, mirrored_input)
    return landmarks


def hand_is_present(results, hand_preference=None, mirrored_input=None):
    """Return whether the configured dynamic hand is currently visible."""
    return select_hand_landmarks(results, hand_preference, mirrored_input) is not None


def extract_frame_features(results, hand_preference=None, mirrored_input=None):
    """Return one 162-float holistic frame, or None when pose is missing."""
    if results.pose_landmarks is None:
        return None

    pose = np.array(
        [[lm.x, lm.y, lm.z] for lm in results.pose_landmarks.landmark],
        dtype=np.float32,
    )
    nose = pose[0].copy()
    pose -= nose[np.newaxis, :]

    hand_side, hand_source = select_hand_source(results, hand_preference, mirrored_input)
    pose = mask_pose_to_single_hand(pose, hand_side)
    if hand_source is not None:
        hand = np.array(
            [[lm.x, lm.y, lm.z] for lm in hand_source.landmark],
            dtype=np.float32,
        )
        hand -= nose[np.newaxis, :]
    else:
        hand = np.zeros((21, 3), dtype=np.float32)

    vector = np.concatenate([pose.reshape(-1), hand.reshape(-1)]).astype(np.float32)
    vector[:3] = 0.0
    return vector


def normalize_static_hand(landmarks):
    """Return the 63-float wrist-normalized hand vector used by the letter model."""
    lm = np.array([[lm.x, lm.y, lm.z] for lm in landmarks], dtype=np.float32)
    lm -= lm[0]
    scale = np.linalg.norm(lm[9])
    if scale > 0:
        lm /= scale
    return lm.reshape(-1).astype(np.float32)


def enforce_nose_anchor(seq):
    """Keep the nose origin exact after augmentation/noise."""
    out = np.asarray(seq, dtype=np.float32).copy()
    out[:, :3] = 0.0
    return out


def sequence_motion_energy(seq):
    """Small scalar describing average hand movement between frames."""
    arr = np.asarray(seq, dtype=np.float32)
    if arr.shape != (SEQ_LEN, FEAT_SIZE):
        return 0.0
    hand = arr[:, POSE_SIZE:].reshape(SEQ_LEN, 21, 3)
    diffs = np.diff(hand, axis=0)
    return float(np.mean(np.linalg.norm(diffs, axis=2)))


def sequence_hand_presence_ratio(seq, eps=1e-4):
    """Return fraction of frames with a detected, nonzero hand vector."""
    arr = np.asarray(seq, dtype=np.float32)
    if arr.shape != (SEQ_LEN, FEAT_SIZE):
        return 0.0
    hand = arr[:, POSE_SIZE:]
    present = np.linalg.norm(hand, axis=1) > eps
    return float(np.mean(present))


def sequence_wrist_path(seq, eps=1e-4):
    """Return wrist travel across consecutive frames where the hand exists."""
    arr = np.asarray(seq, dtype=np.float32)
    if arr.shape != (SEQ_LEN, FEAT_SIZE):
        return 0.0
    hand = arr[:, POSE_SIZE:].reshape(SEQ_LEN, 21, 3)
    present = np.linalg.norm(hand.reshape(SEQ_LEN, -1), axis=1) > eps
    valid = present[1:] & present[:-1]
    if not np.any(valid):
        return 0.0
    wrist_diffs = np.linalg.norm(np.diff(hand[:, 0, :], axis=0), axis=1)
    return float(np.sum(wrist_diffs[valid]))


def top_prediction(probs):
    """Return (top_index, top_confidence, margin_to_second_place)."""
    probs = np.asarray(probs, dtype=np.float32)
    order = np.argsort(probs)
    top_idx = int(order[-1])
    top_conf = float(probs[top_idx])
    second = float(probs[order[-2]]) if len(order) > 1 else 0.0
    return top_idx, top_conf, top_conf - second
