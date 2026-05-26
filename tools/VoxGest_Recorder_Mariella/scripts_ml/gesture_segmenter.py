"""Endpoint-based gesture segmentation for long phrase intents.

Short word signs can be recognized with sliding windows. Phrase intents need a
different commit policy: collect the full motion, wait for a final hold, then
classify the completed segment.
"""

import os
from collections import deque

import numpy as np

from lstm_features import FEAT_SIZE, POSE_SIZE
from phrase_config import PHRASE_SEQ_LEN


def _env_float(name, default):
    return float(os.environ.get(name, str(default)))


def _env_int(name, default):
    return int(os.environ.get(name, str(default)))


def hand_present(vec, eps=1e-4):
    arr = np.asarray(vec, dtype=np.float32)
    if arr.shape != (FEAT_SIZE,):
        return False
    return bool(np.linalg.norm(arr[POSE_SIZE:]) > eps)


def frame_hand_motion(prev_vec, vec):
    if prev_vec is None:
        return 0.0
    prev = np.asarray(prev_vec, dtype=np.float32)
    curr = np.asarray(vec, dtype=np.float32)
    if prev.shape != (FEAT_SIZE,) or curr.shape != (FEAT_SIZE,):
        return 0.0
    prev_hand = prev[POSE_SIZE:].reshape(21, 3)
    curr_hand = curr[POSE_SIZE:].reshape(21, 3)
    return float(np.mean(np.linalg.norm(curr_hand - prev_hand, axis=1)))


def resample_sequence(seq, target_len=PHRASE_SEQ_LEN):
    arr = np.asarray(seq, dtype=np.float32)
    if arr.ndim != 2 or arr.shape[1] != FEAT_SIZE:
        return np.zeros((target_len, FEAT_SIZE), dtype=np.float32)
    if len(arr) == target_len:
        return arr.astype(np.float32)
    if len(arr) == 0:
        return np.zeros((target_len, FEAT_SIZE), dtype=np.float32)
    if len(arr) == 1:
        return np.repeat(arr, target_len, axis=0).astype(np.float32)

    old_x = np.linspace(0.0, 1.0, len(arr), dtype=np.float32)
    new_x = np.linspace(0.0, 1.0, target_len, dtype=np.float32)
    out = np.empty((target_len, FEAT_SIZE), dtype=np.float32)
    for feat_idx in range(FEAT_SIZE):
        out[:, feat_idx] = np.interp(new_x, old_x, arr[:, feat_idx])
    out[:, :3] = 0.0
    return out


class GestureSegmenter:
    """State machine for complete phrase capture."""

    def __init__(
        self,
        target_len=PHRASE_SEQ_LEN,
        start_motion=None,
        end_motion=None,
        start_frames=None,
        end_hold_frames=None,
        min_frames=None,
        max_frames=None,
        preroll_frames=None,
    ):
        self.target_len = target_len
        self.start_motion = start_motion if start_motion is not None else _env_float(
            "VOXGEST_PHRASE_START_MOTION",
            0.018,
        )
        self.end_motion = end_motion if end_motion is not None else _env_float(
            "VOXGEST_PHRASE_END_MOTION",
            0.010,
        )
        self.start_frames = start_frames if start_frames is not None else _env_int(
            "VOXGEST_PHRASE_START_FRAMES",
            3,
        )
        self.end_hold_frames = (
            end_hold_frames
            if end_hold_frames is not None
            else _env_int("VOXGEST_PHRASE_END_HOLD_FRAMES", 10)
        )
        self.min_frames = min_frames if min_frames is not None else _env_int(
            "VOXGEST_PHRASE_MIN_FRAMES",
            36,
        )
        self.max_frames = max_frames if max_frames is not None else _env_int(
            "VOXGEST_PHRASE_MAX_FRAMES",
            120,
        )
        self.preroll_frames = preroll_frames if preroll_frames is not None else _env_int(
            "VOXGEST_PHRASE_PREROLL_FRAMES",
            8,
        )
        self.reset()

    def reset(self):
        self.state = "IDLE"
        self.preroll = deque(maxlen=self.preroll_frames)
        self.frames = []
        self.prev_vec = None
        self.start_count = 0
        self.end_count = 0
        self.last_motion = 0.0
        self.last_segment_frames = 0

    def update(self, vec):
        arr = np.asarray(vec, dtype=np.float32)
        if arr.shape != (FEAT_SIZE,):
            return None

        motion = frame_hand_motion(self.prev_vec, arr)
        present = hand_present(arr)
        self.last_motion = motion
        self.prev_vec = arr

        if self.state == "IDLE":
            self.preroll.append(arr)
            if present and motion >= self.start_motion:
                self.start_count += 1
            else:
                self.start_count = 0

            if self.start_count >= self.start_frames:
                self.state = "ACTIVE"
                self.frames = list(self.preroll)
                self.end_count = 0
            return None

        self.frames.append(arr)
        self.last_segment_frames = len(self.frames)

        if motion <= self.end_motion:
            self.end_count += 1
        else:
            self.end_count = 0

        complete_by_hold = (
            self.end_count >= self.end_hold_frames
            and len(self.frames) >= self.min_frames
        )
        complete_by_timeout = len(self.frames) >= self.max_frames

        if complete_by_hold or complete_by_timeout:
            raw = np.array(self.frames, dtype=np.float32)
            segment = resample_sequence(raw, self.target_len)
            self.state = "IDLE"
            self.preroll.clear()
            self.frames = []
            self.start_count = 0
            self.end_count = 0
            self.last_segment_frames = len(raw)
            return segment

        return None

    def debug_text(self):
        return (
            f"{self.state} mot {self.last_motion:.3f} "
            f"hold {self.end_count}/{self.end_hold_frames} "
            f"frames {self.last_segment_frames}"
        )
