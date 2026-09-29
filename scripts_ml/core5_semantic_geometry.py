"""Offline Core5 semantic geometry from authentic pose/hand landmarks.

Only image-normalized XY is used for hand-to-body relations. MediaPipe pose
and hand Z have different origins, so cross-component depth is not inferred.
This module never changes the 225 classifier contract or Android runtime.
"""
from __future__ import annotations

import math
from collections import Counter

import numpy as np

POSE = {"nose": 0, "left_eye": 2, "right_eye": 5, "left_mouth": 9,
        "right_mouth": 10, "left_shoulder": 11, "right_shoulder": 12,
        "left_elbow": 13, "right_elbow": 14, "left_wrist": 15, "right_wrist": 16}
TIPS = (4, 8, 12, 16, 20)
MCPS = (5, 9, 13, 17)
FINGERS = ((5, 6, 7, 8), (9, 10, 11, 12), (13, 14, 15, 16), (17, 18, 19, 20))


def unit(vector):
    size = float(np.linalg.norm(vector))
    return vector / size if size > 1e-8 else np.zeros_like(vector)


def joint_angle(a, b, c):
    v1, v2 = unit(a - b), unit(c - b)
    if not np.any(v1) or not np.any(v2):
        return None
    return float(math.degrees(math.acos(np.clip(np.dot(v1, v2), -1, 1))))


def frame_features(frame):
    if not frame["pose_present"]:
        return {"timestamp_ms": frame["timestamp_ms"], "pose": False, "left": None,
                "right": None, "both": None}
    pose = np.asarray(frame["pose"], dtype=np.float64)
    if pose.shape != (33, 3) or not np.isfinite(pose).all():
        raise ValueError("Invalid pose landmarks")
    p = pose[:, :2]
    shoulders = (p[11] + p[12]) / 2
    shoulder_width = float(np.linalg.norm(p[11] - p[12]))
    if shoulder_width < .04:
        return {"timestamp_ms": frame["timestamp_ms"], "pose": False,
                "left": None, "right": None, "both": None}
    anchors = {"nose": p[0], "eye": (p[2] + p[5]) / 2,
               "mouth": (p[9] + p[10]) / 2, "shoulder": shoulders}
    result = {"timestamp_ms": frame["timestamp_ms"], "pose": True,
              "shoulder_width": shoulder_width, "left": None, "right": None, "both": None}
    for side in ("left", "right"):
        if not frame[side + "_present"]:
            continue
        raw = np.asarray(frame[side], dtype=np.float64)
        if raw.shape != (21, 3) or not np.isfinite(raw).all():
            raise ValueError("Invalid hand landmarks")
        hand = raw[:, :2]
        wrist = hand[0]
        palm = np.median(hand[[0, *MCPS]], axis=0)
        hand_scale = float(np.median(np.linalg.norm(hand[list(MCPS)] - wrist, axis=1)))
        if hand_scale < .008:
            continue
        values = {"hand_scale_to_shoulder": hand_scale / shoulder_width,
                  "wrist_xy": wrist.tolist(), "palm_xy": palm.tolist(),
                  "palm_torso_xy": ((palm - shoulders) / shoulder_width).tolist(),
                  "palm_to_shoulder_xy": ((palm - shoulders) / shoulder_width).tolist(),
                  "hand_orientation_xy": unit(hand[9] - wrist).tolist(),
                  "palm_axis_xy": unit(hand[17] - hand[5]).tolist(),
                  "palm_signed_area_proxy": float(np.cross(hand[9] - wrist, hand[17] - hand[5]) / (hand_scale ** 2)),
                  "hand_opening": float(np.median(np.linalg.norm(hand[list(TIPS)] - wrist, axis=1)) / hand_scale)}
        bends = [joint_angle(hand[mcp], hand[pip], hand[dip]) for mcp, pip, dip, _ in FINGERS]
        values["finger_extension_angle"] = float(np.median([v for v in bends if v is not None])) if any(v is not None for v in bends) else None
        for anchor_name, anchor in anchors.items():
            for point_name, point in (("wrist", wrist), ("palm", palm)):
                v = (point - anchor) / shoulder_width
                values[f"{point_name}_to_{anchor_name}_xy"] = v.tolist()
                values[f"{point_name}_to_{anchor_name}_distance"] = float(np.linalg.norm(v))
        values["tip_to_nose_min"] = float(np.min(np.linalg.norm(hand[list(TIPS)] - anchors["nose"], axis=1)) / shoulder_width)
        values["tip_to_mouth_min"] = float(np.min(np.linalg.norm(hand[list(TIPS)] - anchors["mouth"], axis=1)) / shoulder_width)
        shoulder, elbow, pose_wrist = ((11, 13, 15) if side == "left" else (12, 14, 16))
        values["elbow_angle"] = joint_angle(p[shoulder], p[elbow], p[pose_wrist])
        values["forearm_direction_xy"] = unit(p[pose_wrist] - p[elbow]).tolist()
        result[side] = values
    if result["left"] and result["right"]:
        l, r = result["left"], result["right"]
        diff = np.asarray(r["palm_xy"]) - np.asarray(l["palm_xy"])
        result["both"] = {"inter_palm_xy": (diff / shoulder_width).tolist(),
                          "inter_palm_distance": float(np.linalg.norm(diff) / shoulder_width),
                          "orientation_alignment": float(np.dot(l["hand_orientation_xy"], r["hand_orientation_xy"]))}
    return result


def median(values):
    valid = [v for v in values if v is not None and math.isfinite(v)]
    return float(np.median(valid)) if valid else None


def trajectory_features(frames):
    features = [frame_features(f) for f in frames]
    counts = Counter(side for side in ("left", "right") for f in features if f[side])
    dominant = max(("left", "right"), key=lambda s: counts[s])
    observed = [f for f in features if f[dominant]]
    summary = {"frames": len(frames), "valid_geometry_frames": sum(f["pose"] for f in features),
               "dominant_side": dominant if observed else None,
               "dominant_presence_ratio": counts[dominant] / len(features) if features else 0.0,
               "two_hand_ratio": sum(f["both"] is not None for f in features) / len(features) if features else 0.0,
               "duration_ms": frames[-1]["timestamp_ms"] - frames[0]["timestamp_ms"] if len(frames) > 1 else 0}
    if not observed:
        return summary, features
    keys = ("palm_to_nose_distance", "palm_to_eye_distance", "palm_to_mouth_distance",
            "palm_to_shoulder_distance", "tip_to_nose_min", "tip_to_mouth_min",
            "elbow_angle", "palm_signed_area_proxy", "hand_opening",
            "finger_extension_angle", "hand_scale_to_shoulder")
    for key in keys:
        summary[key + "_median"] = median(f[dominant][key] for f in observed)
        summary[key + "_p10"] = float(np.percentile([f[dominant][key] for f in observed if f[dominant][key] is not None], 10)) if any(f[dominant][key] is not None for f in observed) else None
    vector_keys = ("palm_to_nose_xy", "palm_to_eye_xy", "palm_to_mouth_xy",
                   "palm_to_shoulder_xy", "hand_orientation_xy", "palm_axis_xy", "forearm_direction_xy")
    for key in vector_keys:
        for axis, name in enumerate("xy"):
            summary[key + "_" + name + "_median"] = median(f[dominant][key][axis] for f in observed)
    summary["inter_palm_distance_median"] = median(f["both"]["inter_palm_distance"] if f["both"] else None for f in features)
    summary["inter_orientation_alignment_median"] = median(f["both"]["orientation_alignment"] if f["both"] else None for f in features)
    summary["start_palm_torso_xy"] = observed[0][dominant]["palm_torso_xy"]
    summary["end_palm_torso_xy"] = observed[-1][dominant]["palm_torso_xy"]
    palms = [(f["timestamp_ms"], np.asarray(f[dominant]["palm_torso_xy"])) for f in observed]
    pairs = [(b_t - a_t, b_p - a_p) for (a_t, a_p), (b_t, b_p) in zip(palms, palms[1:])]
    valid = [(dt, delta) for dt, delta in pairs if dt > 0]
    summary["trajectory_length"] = float(sum(np.linalg.norm(delta) for _, delta in valid))
    summary["trajectory_displacement_xy"] = (palms[-1][1] - palms[0][1]).tolist()
    summary["translation_velocity_p90"] = float(np.percentile([np.linalg.norm(delta) * 1000 / dt for dt, delta in valid], 90)) if valid else None
    return summary, features
