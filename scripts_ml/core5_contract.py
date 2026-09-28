"""Isolated FSL_CORE5_REBASE_V1 contract; no dataset or profile side effects."""
from __future__ import annotations
import hashlib
import numpy as np
from fullsign225_feature_builder import build_fullsign225_from_arrays

PROFILE = "FSL_CORE5_REBASE_V1"
LABELS = ["HELLO", "THANK YOU", "YES", "NO", "UNDERSTAND"]
IDS = [3, 7, 15, 14, 10]
LENGTH = 48
VERSION = "core5_tasks_fullsign225_timestamp48_v1"

def sha256(path):
    h = hashlib.sha256()
    with open(path, "rb") as stream:
        for block in iter(lambda: stream.read(1048576), b""):
            h.update(block)
    return h.hexdigest()

def assign_hands(points, categories):
    """Reported semantic sides only; duplicate/unknown sides fail closed."""
    slots = {"left": [], "right": []}
    unknown = 0
    for hand, category in zip(points, categories):
        side = category.strip().lower()
        if side in slots:
            slots[side].append(hand)
        else:
            unknown += 1
    return {side: values[0] if len(values) == 1 else None for side, values in slots.items()}, {
        "unknown": unknown, "duplicate_left": len(slots["left"]) > 1,
        "duplicate_right": len(slots["right"]) > 1}

def canonical(pose, left, right):
    # Reject malformed arrays explicitly, matching Kotlin's valid/null contract.
    for array, shape in ((pose, (33, 3)), (left, (21, 3)), (right, (21, 3))):
        if array is not None:
            value = np.asarray(array)
            if value.shape != shape or not np.isfinite(value).all():
                raise ValueError("Malformed or non-finite landmarks")
    return build_fullsign225_from_arrays(pose, left, right).vector

def resample_timestamp(frames, timestamps, length=LENGTH):
    x = np.asarray(frames, dtype=np.float32)
    times = np.asarray(timestamps, dtype=np.float64)
    if x.ndim != 2 or x.shape[1] != 225 or len(x) != len(times) or len(x) < 2:
        raise ValueError("At least two [225] observations required")
    if not np.isfinite(x).all() or not np.isfinite(times).all() or np.any(np.diff(times) <= 0):
        raise ValueError("Finite, strictly increasing observations required")
    targets = np.linspace(times[0], times[-1], length)
    upper = np.searchsorted(times, targets, side="left").clip(0, len(times)-1)
    lower = np.maximum(upper-1, 0)
    span = times[upper]-times[lower]
    alpha = np.divide(targets-times[lower], span, out=np.zeros(length), where=span > 0).astype(np.float32)
    output = x[lower] + (x[upper]-x[lower])*alpha[:, None]
    return output.astype(np.float32), {"lower": lower.tolist(), "upper": upper.tolist(),
                                      "alpha": alpha.tolist(), "target_ms": targets.tolist()}

def quality(frames, timestamps, presence):
    x, ts, p = np.asarray(frames), np.asarray(timestamps), np.asarray(presence)
    gaps = np.diff(ts)
    return {"frames": len(x), "duration_ms": float(ts[-1]-ts[0]),
            "processed_fps": float((len(x)-1)*1000/(ts[-1]-ts[0])),
            "max_gap_ms": float(gaps.max()), "p95_gap_ms": float(np.percentile(gaps,95)),
            "pose_ratio": float(p[:,0].mean()), "left_ratio": float(p[:,1].mean()),
            "right_ratio": float(p[:,2].mean()), "any_hand_ratio": float(p[:,1:].any(axis=1).mean()),
            "motion_mean_l2": float(np.linalg.norm(np.diff(x,axis=0),axis=1).mean())}

def observed_envelope(timestamps, presence):
    """Trim outer no-hand padding only; keep every interior observation and final hold."""
    ts = np.asarray(timestamps, dtype=np.float64)
    p = np.asarray(presence, dtype=bool)
    active = np.flatnonzero(p[:,0] & p[:,1:].any(axis=1))
    if len(active) < 2:
        raise ValueError("Insufficient observed sign trajectory")
    lo = int(np.searchsorted(ts, ts[active[0]]-100, side="left"))
    hi = int(np.searchsorted(ts, ts[active[-1]]+100, side="right"))
    return lo, hi
