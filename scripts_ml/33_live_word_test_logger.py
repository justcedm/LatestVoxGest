"""Live dynamic-word test logger for VoxGest.

Manual mode keeps the original SPACE-to-log flow. Demo gate mode defaults to
a one-word capture flow: press SPACE/C, wait for stable hands, then record one
30-frame gesture window.
"""

import csv
import json
import os
import sys
import time
from collections import deque
from datetime import datetime
from pathlib import Path

import cv2
import mediapipe as mp
import numpy as np
import tensorflow as tf

from lstm_features import (
    FEAT_SIZE,
    POSE_SIZE,
    SEQ_LEN,
    configured_feature_profile,
    configured_hand_preference,
    configured_mirror_input,
    extract_frame_features,
    hand_mapping_text,
    sequence_hand_presence_ratio,
    sequence_motion_energy,
    sequence_wrist_path,
    single_hand_pose_enabled,
    top_prediction,
)
from frame_quality_gate import (
    BAD_SEQUENCE,
    GOOD,
    HANDS_OVERLAPPING,
    LOW_HAND_PRESENCE,
    LOW_MOTION,
    LOW_WRIST_PATH,
    NO_HAND,
    UNSTABLE_LANDMARKS,
    WARNING_HANDS_CLOSE,
    capture_frame_quality,
    evaluate_sequence_quality,
    quality_allows_inference,
)
from word_config import NEGATIVE_WORDS, TARGET_WORDS, TRAINING_WORDS, WORD_PROFILE


os.environ["PROTOCOL_BUFFERS_PYTHON_IMPLEMENTATION"] = "python"
os.environ["TF_CPP_MIN_LOG_LEVEL"] = "3"

ROOT = Path(__file__).resolve().parents[1]
REPORT_DIR = ROOT / "reports"
FEATURE_PROFILE = configured_feature_profile()
TRUTHY = {"1", "true", "yes", "on"}


def _int_env(name, default):
    try:
        return int(os.environ.get(name, str(default)))
    except ValueError:
        return int(default)


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


def _gate_profile():
    profile = os.environ.get("VOXGEST_GATE_PROFILE", "strict").strip().lower()
    return profile if profile in {"strict", "demo"} else "strict"


GATE_PROFILE = _gate_profile()


def _labels_from_env():
    raw = os.environ.get("VOXGEST_LIVE_TEST_LABELS", "").strip()
    if not raw:
        return []
    return [item.strip().upper() for item in raw.replace(";", ",").split(",") if item.strip()]


def _unique_labels(labels):
    seen = set()
    out = []
    for label in labels:
        if label not in seen:
            out.append(label)
            seen.add(label)
    return out


def _capture_mode():
    if GATE_PROFILE == "demo":
        default = "manual_capture"
    else:
        default = "hand_trigger_auto" if FEATURE_PROFILE == "fullsign225" else "manual"
    mode = os.environ.get("VOXGEST_LIVE_TEST_CAPTURE_MODE", default).strip().lower()
    return mode if mode in {"manual", "hand_trigger_auto", "manual_capture"} else default


def artifact_suffix():
    safe_word = "".join(ch if ch.isalnum() or ch in {"_", "-"} else "_" for ch in WORD_PROFILE)
    if FEATURE_PROFILE != "onehand162":
        if safe_word.startswith("fullsign225_") or safe_word.endswith(f"_{FEATURE_PROFILE}"):
            return safe_word
        return f"{safe_word}_{FEATURE_PROFILE}"
    if WORD_PROFILE == "demo10":
        return "v1"
    return safe_word


ARTIFACT_SUFFIX = artifact_suffix()
LSTM_MODEL = ROOT / "model" / f"voxgest_lstm_{ARTIFACT_SUFFIX}.h5"
LSTM_LABELS = ROOT / "model" / f"class_labels_lstm_{ARTIFACT_SUFFIX}.json"
LSTM_REPORT = (
    ROOT / "model" / "lstm_training_report.json"
    if WORD_PROFILE == "demo10"
    else ROOT / "model" / f"lstm_training_report_{ARTIFACT_SUFFIX}.json"
)
TCN_MODEL = ROOT / "model" / f"voxgest_tcn_{ARTIFACT_SUFFIX}.h5"
TCN_LABELS = ROOT / "model" / f"class_labels_tcn_{ARTIFACT_SUFFIX}.json"
TCN_REPORT = (
    ROOT / "model" / "tcn_training_report.json"
    if WORD_PROFILE == "demo10"
    else ROOT / "model" / f"tcn_training_report_{ARTIFACT_SUFFIX}.json"
)
RD_TCN_MODEL = ROOT / "model" / f"voxgest_rd_tcn_{ARTIFACT_SUFFIX}.h5"
RD_TCN_LABELS = ROOT / "model" / f"class_labels_rd_tcn_{ARTIFACT_SUFFIX}.json"
RD_TCN_REPORT = ROOT / "model" / f"rd_tcn_training_report_{ARTIFACT_SUFFIX}.json"
DYNAMIC_MODEL_KIND = os.environ.get("VOXGEST_DYNAMIC_MODEL", "auto").strip().lower()
ALLOW_EXTRA_WORD_LABELS = _bool_env("VOXGEST_ALLOW_EXTRA_WORD_LABELS", False)

ACTIVE_WORD_LABELS = set(TARGET_WORDS)
ACTIVE_DYNAMIC_LABELS = set(TRAINING_WORDS)
MIRROR_INPUT = True
ENV_LABELS = _labels_from_env()
EXPECTED_LABELS = _unique_labels(
    ENV_LABELS or [item.upper() for item in sys.argv[1:]] or list(TARGET_WORDS) + list(NEGATIVE_WORDS)
)
DYNAMIC_EVERY_N_FRAMES = _int_env("VOXGEST_DYNAMIC_EVERY_N_FRAMES", 3)
NO_POSE_RESET_FRAMES = 12
CAPTURE_MODE = _capture_mode()
TRIALS_PER_LABEL = max(1, _int_env("VOXGEST_LIVE_TEST_TRIALS_PER_LABEL", 1))
HAND_READY_FRAMES = max(1, _int_env("VOXGEST_HAND_READY_FRAMES", 10))
CAPTURE_FRAMES = max(1, _int_env("VOXGEST_CAPTURE_FRAMES", SEQ_LEN))
MOTION_START_THRESHOLD = _float_env("VOXGEST_MOTION_START_THRESHOLD", 0.025)
REST_SECONDS = max(0.0, _float_env("VOXGEST_LIVE_TEST_REST_SECONDS", 2.0))
RESULT_SECONDS = max(0.4, _float_env("VOXGEST_LIVE_TEST_RESULT_SECONDS", 1.2))
AUTO_READY_HAND_PRESENCE = _float_env("VOXGEST_LIVE_TEST_READY_HAND_PRESENCE", 0.20)
FULLSIGN_ALLOW_HAND_OVERLAP = _bool_env(
    "VOXGEST_FULLSIGN_ALLOW_HAND_OVERLAP",
    FEATURE_PROFILE == "fullsign225",
)

WAITING = "WAITING"
STABILIZING = "STABILIZING"
RECORDING = "RECORDING"
CLASSIFYING = "CLASSIFYING"
RESULT = "RESULT"
REST = "REST"
MANUAL = "MANUAL"

THRESHOLDS = {
    "YES": {"conf": 0.56, "margin": 0.07, "motion": 0.008, "path": 0.05, "presence": 0.25},
    "NO": {"conf": 0.55, "margin": 0.06, "motion": 0.008, "path": 0.04, "presence": 0.25},
    "WATER": {"conf": 0.56, "margin": 0.06, "motion": 0.006, "path": 0.04, "presence": 0.25},
    "PLEASE": {"conf": 0.68, "margin": 0.18, "motion": 0.025, "path": 0.25, "presence": 0.25},
    "HELLO": {"conf": 0.70, "margin": 0.18, "motion": 0.030, "path": 0.30, "presence": 0.25},
    "HELP": {"conf": 0.60, "margin": 0.10, "motion": 0.025, "path": 0.20, "presence": 0.25},
    "STOP": {"conf": 0.62, "margin": 0.10, "motion": 0.025, "path": 0.20, "presence": 0.25},
    "DOCTOR": {"conf": 0.70, "margin": 0.15, "motion": 0.020, "path": 0.20, "presence": 0.25},
    "NAME": {"conf": 0.55, "margin": 0.05, "motion": 0.020, "path": 0.20, "presence": 0.25},
    "THANKYOU": {"conf": 0.52, "margin": 0.03, "motion": 0.010, "path": 0.08, "presence": 0.25},
    "NOTHING": {"conf": 0.55, "margin": 0.05, "motion": 0.0, "path": 0.0, "presence": 0.0},
}
DEFAULT_THRESHOLD = {"conf": 0.65, "margin": 0.12, "motion": 0.03, "path": 0.35, "presence": 0.25}
DEMO_LOW_MOTION_LABELS = {"MY", "OKAY", "YOU", "YOUR"}
DEMO_ALLOWED_QUALITY = {
    GOOD,
    WARNING_HANDS_CLOSE,
    UNSTABLE_LANDMARKS,
    LOW_MOTION,
    LOW_WRIST_PATH,
}
DEMO_SEVERE_QUALITY_BLOCKS = {BAD_SEQUENCE, LOW_HAND_PRESENCE, NO_HAND}
DEMO_THRESHOLD_OVERRIDES = {
    "MY": {"conf": 0.50, "margin": 0.07, "motion": 0.006, "path": 0.05, "presence": 0.25},
    "OKAY": {"conf": 0.50, "margin": 0.07, "motion": 0.006, "path": 0.05, "presence": 0.25},
    "YOU": {"conf": 0.50, "margin": 0.07, "motion": 0.006, "path": 0.05, "presence": 0.25},
    "YOUR": {"conf": 0.50, "margin": 0.07, "motion": 0.006, "path": 0.05, "presence": 0.25},
}
PROFILE_THRESHOLDS = {
    "fullsign225_manual5": {
        "EAT": {"conf": 0.60, "margin": 0.08, "motion": 0.015, "path": 0.20, "presence": 0.25},
    },
    "fullsign225_manual5_team": {
        "EAT": {"conf": 0.60, "margin": 0.08, "motion": 0.015, "path": 0.20, "presence": 0.25},
    },
    "fullsign225_manual5_team_v2": {
        "EAT": {"conf": 0.60, "margin": 0.08, "motion": 0.015, "path": 0.20, "presence": 0.25},
    },
}


def threshold_for(label):
    rule = dict(THRESHOLDS.get(label, DEFAULT_THRESHOLD))
    profile_rule = PROFILE_THRESHOLDS.get(WORD_PROFILE, {}).get(label)
    if profile_rule:
        rule.update(profile_rule)
    if GATE_PROFILE == "demo":
        demo_rule = DEMO_THRESHOLD_OVERRIDES.get(label)
        if demo_rule:
            rule.update(demo_rule)
    return rule


def live_quality_allows_inference(status):
    if GATE_PROFILE == "demo":
        if status in DEMO_SEVERE_QUALITY_BLOCKS:
            return False
        return status in DEMO_ALLOWED_QUALITY
    return quality_allows_inference(status)


def load_label_maps(path):
    with open(path, "r", encoding="utf-8") as f:
        raw = json.load(f)
    if all(str(k).lstrip("-").isdigit() for k in raw):
        label_to_idx = {v: int(k) for k, v in raw.items()}
        idx_to_label = {int(k): v for k, v in raw.items()}
    else:
        label_to_idx = {k: int(v) for k, v in raw.items()}
        idx_to_label = {int(v): k for k, v in raw.items()}
    return label_to_idx, idx_to_label


def report_score(path):
    try:
        with open(path, "r", encoding="utf-8") as f:
            return float(json.load(f).get("best_grouped_val_accuracy", -1.0))
    except Exception:
        return -1.0


def model_input_shape(model):
    shape = getattr(model, "input_shape", None)
    if isinstance(shape, (list, tuple)) and shape and isinstance(shape[0], (list, tuple)):
        shape = shape[0]
    if isinstance(shape, (list, tuple)):
        return [1 if value is None else int(value) for value in shape]
    return [1, SEQ_LEN, FEAT_SIZE]


def dynamic_candidates():
    options = {
        "tcn": [("TCN", TCN_MODEL, TCN_LABELS, TCN_REPORT)],
        "rd_tcn": [("RD-TCN", RD_TCN_MODEL, RD_TCN_LABELS, RD_TCN_REPORT)],
        "residual_dilated_tcn": [("RD-TCN", RD_TCN_MODEL, RD_TCN_LABELS, RD_TCN_REPORT)],
        "lstm": [("LSTM", LSTM_MODEL, LSTM_LABELS, LSTM_REPORT)],
    }
    if DYNAMIC_MODEL_KIND in options:
        return options[DYNAMIC_MODEL_KIND]
    return sorted(options["tcn"] + options["lstm"], key=lambda item: report_score(item[3]), reverse=True)


def load_dynamic_model():
    errors = []
    for name, model_path, labels_path, _ in dynamic_candidates():
        if not model_path.exists() or not labels_path.exists():
            errors.append(f"{name}: missing {model_path.name} or {labels_path.name}")
            continue
        label_to_idx, idx_to_label = load_label_maps(labels_path)
        labels = {label.upper() for label in label_to_idx}
        missing = sorted(ACTIVE_DYNAMIC_LABELS - labels)
        extra = sorted(labels - ACTIVE_DYNAMIC_LABELS)
        if missing:
            errors.append(f"{name}: missing active labels {missing}")
            continue
        if extra and not ALLOW_EXTRA_WORD_LABELS:
            errors.append(f"{name}: skipped extra labels {extra}")
            continue
        model = tf.keras.models.load_model(model_path)
        input_shape = model_input_shape(model)
        if len(input_shape) >= 3 and input_shape[-1] != FEAT_SIZE:
            errors.append(
                f"{name}: input feature mismatch, model expects {input_shape[-1]} "
                f"but {FEATURE_PROFILE} provides {FEAT_SIZE}"
            )
            continue
        return name, str(model_path), model, idx_to_label
    raise RuntimeError("No usable dynamic model. " + " | ".join(errors))


def normalize_quality_for_live(quality):
    if (
        FEATURE_PROFILE == "fullsign225"
        and FULLSIGN_ALLOW_HAND_OVERLAP
        and quality.status == HANDS_OVERLAPPING
    ):
        quality.status = WARNING_HANDS_CLOSE
        quality.reason = "hands close/touching; allowed for fullsign225"
    return quality


def gate_failure(label, conf, margin, motion, wrist_path, hand_presence, quality_status=GOOD):
    if not live_quality_allows_inference(quality_status):
        return [f"quality:{quality_status}"]
    if label in NEGATIVE_WORDS:
        return ["noop_label"]

    rule = threshold_for(label)
    failures = []
    if conf < rule["conf"]:
        failures.append(f"low_conf<{rule['conf']:.2f}")
    if margin < rule["margin"]:
        failures.append(f"low_margin<{rule['margin']:.2f}")
    if motion < rule["motion"]:
        failures.append(f"low_motion<{rule['motion']:.3f}")
    if wrist_path < rule["path"]:
        failures.append(f"low_path<{rule['path']:.2f}")
    if hand_presence < rule["presence"]:
        failures.append(f"low_hand<{rule['presence']:.2f}")
    if label not in ACTIVE_DYNAMIC_LABELS:
        failures.append("inactive_label")
    return failures


def default_prediction(reason="waiting"):
    return {
        "label": "",
        "top3_predictions": [],
        "confidence": 0.0,
        "margin": 0.0,
        "motion": 0.0,
        "wrist_path": 0.0,
        "hand_presence": 0.0,
        "quality_status": "NO_HAND",
        "quality_reason": reason,
        "inference_allowed": False,
    }


def sequence_from_frames(frames):
    if not frames:
        return None
    arr = np.array(frames, dtype=np.float32)
    if arr.ndim != 2 or arr.shape[1] != FEAT_SIZE:
        return None
    if len(arr) >= SEQ_LEN:
        indices = np.linspace(0, len(arr) - 1, SEQ_LEN, dtype=int)
        return arr[indices].astype(np.float32)
    pad = np.tile(arr[-1], (SEQ_LEN - len(arr), 1))
    return np.vstack([arr, pad]).astype(np.float32)


def frame_motion_delta(prev_vec, vec):
    if prev_vec is None or vec is None:
        return 0.0
    prev_hand = prev_vec[POSE_SIZE:]
    hand = vec[POSE_SIZE:]
    if prev_hand.shape != hand.shape:
        return 0.0
    return float(np.mean(np.abs(hand - prev_hand)))


def ready_from_frame(vec, record):
    if vec is None or not record:
        return False
    if not record.get("pose_present") or not record.get("selected_hand_present"):
        return False
    if FEATURE_PROFILE == "fullsign225":
        return True
    return sequence_hand_presence_ratio(sequence_from_frames([vec] * SEQ_LEN)) >= AUTO_READY_HAND_PRESENCE


def classify_sequence(frames, quality_records, model, idx_to_label):
    seq = sequence_from_frames(frames)
    if seq is None:
        return {
            **default_prediction("empty or corrupt captured sequence"),
            "quality_status": "BAD_SEQUENCE",
            "quality_reason": "empty or corrupt captured sequence",
        }

    quality = normalize_quality_for_live(evaluate_sequence_quality(seq, list(quality_records)))
    motion = sequence_motion_energy(seq)
    wrist_path = sequence_wrist_path(seq)
    hand_presence = sequence_hand_presence_ratio(seq)
    inference_allowed = live_quality_allows_inference(quality.status)
    if inference_allowed:
        probs = model.predict(seq[np.newaxis, ...], verbose=0)[0]
        pred_idx, conf, margin = top_prediction(probs)
        label = idx_to_label.get(pred_idx, "?")
        order = np.argsort(probs)[::-1][:3]
        top3 = [
            {
                "label": idx_to_label.get(int(idx), "?"),
                "confidence": round(float(probs[int(idx)]), 6),
            }
            for idx in order
        ]
    else:
        label = ""
        conf = 0.0
        margin = 0.0
        top3 = []

    return {
        "label": label,
        "top3_predictions": top3,
        "confidence": float(conf),
        "margin": float(margin),
        "motion": float(motion),
        "wrist_path": float(wrist_path),
        "hand_presence": float(hand_presence),
        "quality_status": quality.status,
        "quality_reason": quality.reason,
        "inference_allowed": bool(inference_allowed),
    }


def build_trial_row(model_name, model_path, expected, prediction, state):
    pred = prediction.get("label", "")
    failures = gate_failure(
        pred,
        prediction.get("confidence", 0.0),
        prediction.get("margin", 0.0),
        prediction.get("motion", 0.0),
        prediction.get("wrist_path", 0.0),
        prediction.get("hand_presence", 0.0),
        prediction.get("quality_status", GOOD),
    )
    accepted = not failures
    negative_expected = expected in NEGATIVE_WORDS
    matched = pred == expected or (negative_expected and not accepted)
    if not matched:
        failures.append(f"wrong_label:{pred}")

    accepted = bool(accepted and pred not in NEGATIVE_WORDS)
    failure_reason = "ok" if not failures else ";".join(failures)
    if negative_expected and pred in NEGATIVE_WORDS and not accepted:
        failure_reason = "ok_no_output"
    return {
        "timestamp": datetime.now().isoformat(timespec="seconds"),
        "model_name": model_name,
        "model_path": model_path,
        "gate_profile": GATE_PROFILE,
        "capture_mode": CAPTURE_MODE,
        "state": state,
        "input_shape": [1, SEQ_LEN, FEAT_SIZE],
        "expected_label": expected,
        "predicted_label": pred,
        "top3_predictions": prediction.get("top3_predictions", []),
        "confidence": round(float(prediction.get("confidence", 0.0)), 6),
        "margin": round(float(prediction.get("margin", 0.0)), 6),
        "motion": round(float(prediction.get("motion", 0.0)), 6),
        "wrist_path": round(float(prediction.get("wrist_path", 0.0)), 6),
        "hand_presence": round(float(prediction.get("hand_presence", 0.0)), 6),
        "quality_status": prediction.get("quality_status", GOOD),
        "quality_reason": prediction.get("quality_reason", "ok"),
        "inference_allowed": bool(prediction.get("inference_allowed", False)),
        "accepted_by_gate": accepted,
        "gate_decision": "accept" if accepted else "reject",
        "matched_expected": matched,
        "failure_reason": failure_reason,
        "dominant_hand": configured_hand_preference(),
        "feature_profile": FEATURE_PROFILE,
        "single_hand_pose": single_hand_pose_enabled(),
        "mirrored_input": configured_mirror_input(MIRROR_INPUT),
    }


def print_trial_result(label, trial_no, state, row):
    verdict = "ACCEPTED" if row["accepted_by_gate"] else "REJECTED"
    top3 = row.get("top3_predictions") or []
    top3_text = ", ".join(
        f"{item.get('label', '?')}:{float(item.get('confidence', 0.0)):.0%}"
        for item in top3
    ) or "-"
    print(
        "\n".join(
            [
                f"{label} trial {trial_no} | {state} | {verdict}",
                f"  model={row['model_path']}",
                f"  feature={row['feature_profile']} input_shape={row['input_shape']} gate_profile={row['gate_profile']}",
                f"  expected={row['expected_label']} predicted={row['predicted_label'] or '-'} top3={top3_text}",
                (
                    f"  conf={float(row['confidence']):.0%} margin={float(row['margin']):.0%} "
                    f"motion={float(row['motion']):.3f} path={float(row['wrist_path']):.2f} "
                    f"hand={float(row['hand_presence']):.0%}"
                ),
                (
                    f"  quality={row['quality_status']} gate={row['gate_decision']} "
                    f"matched={row['matched_expected']} reason={row['failure_reason']}"
                ),
            ]
        ),
        flush=True,
    )


def write_header(path):
    path.parent.mkdir(parents=True, exist_ok=True)
    fields = [
        "timestamp",
        "model_name",
        "model_path",
        "gate_profile",
        "capture_mode",
        "state",
        "input_shape",
        "expected_label",
        "predicted_label",
        "top3_predictions",
        "confidence",
        "margin",
        "motion",
        "wrist_path",
        "hand_presence",
        "quality_status",
        "quality_reason",
        "inference_allowed",
        "accepted_by_gate",
        "gate_decision",
        "matched_expected",
        "failure_reason",
        "dominant_hand",
        "feature_profile",
        "single_hand_pose",
        "mirrored_input",
    ]
    with open(path, "w", encoding="utf-8", newline="") as f:
        csv.DictWriter(f, fieldnames=fields).writeheader()
    return fields


def append_row(path, fields, row):
    with open(path, "a", encoding="utf-8", newline="") as f:
        csv.DictWriter(f, fieldnames=fields).writerow(row)


def build_demo_label_ranking(rows):
    buckets = {}
    for row in rows:
        label = row["expected_label"]
        bucket = buckets.setdefault(
            label,
            {
                "trials": 0,
                "matched": 0,
                "accepted_correct": 0,
                "false_accepts": 0,
                "gate_blocked": 0,
                "capture_blocked": 0,
            },
        )
        bucket["trials"] += 1
        if row.get("matched_expected"):
            bucket["matched"] += 1
        if (
            row.get("accepted_by_gate")
            and row.get("predicted_label") == label
            and label not in NEGATIVE_WORDS
        ):
            bucket["accepted_correct"] += 1
        if row.get("accepted_by_gate") and not row.get("matched_expected"):
            bucket["false_accepts"] += 1
        reason = str(row.get("failure_reason", ""))
        predicted_expected = row.get("predicted_label") == label and label not in NEGATIVE_WORDS
        if predicted_expected and not row.get("accepted_by_gate"):
            if any(part in reason for part in ("low_conf", "low_margin", "low_motion", "low_path", "low_hand")):
                bucket["gate_blocked"] += 1
        if not row.get("inference_allowed", True) or any(
            part in reason
            for part in (
                "quality:BAD_SEQUENCE",
                "quality:LOW_HAND_PRESENCE",
                "quality:UNSTABLE_LANDMARKS",
                "quality:NO_HAND",
            )
        ):
            bucket["capture_blocked"] += 1

    ranking = {
        "stable_labels": [],
        "partially_stable_labels": [],
        "unstable_labels": [],
        "gate_blocked_labels": [],
        "capture_blocked_labels": [],
        "per_label": {},
    }
    for label, bucket in buckets.items():
        trials = max(1, bucket["trials"])
        match_rate = bucket["matched"] / trials
        accepted_correct_rate = bucket["accepted_correct"] / trials
        false_accept_rate = bucket["false_accepts"] / trials
        gate_block_rate = bucket["gate_blocked"] / trials
        capture_block_rate = bucket["capture_blocked"] / trials
        row = {
            **bucket,
            "match_rate": round(match_rate, 4),
            "accepted_correct_rate": round(accepted_correct_rate, 4),
            "false_accept_rate": round(false_accept_rate, 4),
            "gate_block_rate": round(gate_block_rate, 4),
            "capture_block_rate": round(capture_block_rate, 4),
        }
        ranking["per_label"][label] = row

        if label in NEGATIVE_WORDS:
            if bucket["false_accepts"] == 0 and bucket["accepted_correct"] == 0:
                ranking["stable_labels"].append(label)
            else:
                ranking["unstable_labels"].append(label)
        elif accepted_correct_rate >= 0.70 and false_accept_rate == 0:
            ranking["stable_labels"].append(label)
        elif match_rate > 0.0 or bucket["accepted_correct"] > 0 or bucket["gate_blocked"] > 0:
            ranking["partially_stable_labels"].append(label)
        else:
            ranking["unstable_labels"].append(label)

        if bucket["gate_blocked"]:
            ranking["gate_blocked_labels"].append(label)
        if bucket["capture_blocked"]:
            ranking["capture_blocked_labels"].append(label)

    for key in (
        "stable_labels",
        "partially_stable_labels",
        "unstable_labels",
        "gate_blocked_labels",
        "capture_blocked_labels",
    ):
        ranking[key] = sorted(ranking[key])
    return ranking


def write_json_summary(path, rows, model_name, model_path):
    totals = {}
    for row in rows:
        expected = row["expected_label"]
        bucket = totals.setdefault(
            expected,
            {
                "trials": 0,
                "matched_expected": 0,
                "accepted_by_gate": 0,
                "false_accepts": 0,
                "rejected": 0,
                "confusions": {},
                "failure_reasons": {},
                "quality_rejections": {},
                "inference_blocked": 0,
            },
        )
        bucket["trials"] += 1
        if row["matched_expected"]:
            bucket["matched_expected"] += 1
        if row["accepted_by_gate"]:
            bucket["accepted_by_gate"] += 1
        else:
            bucket["rejected"] += 1
        if not row.get("inference_allowed", True):
            bucket["inference_blocked"] += 1
        if row["accepted_by_gate"] and not row["matched_expected"]:
            bucket["false_accepts"] += 1
        predicted = row["predicted_label"]
        if predicted != expected:
            bucket["confusions"][predicted] = bucket["confusions"].get(predicted, 0) + 1
        reason = row["failure_reason"]
        if reason != "ok":
            for part in str(reason).split(";"):
                bucket["failure_reasons"][part] = bucket["failure_reasons"].get(part, 0) + 1
                if part.startswith("quality:"):
                    status = part.split(":", 1)[1]
                    bucket["quality_rejections"][status] = bucket["quality_rejections"].get(status, 0) + 1

    payload = {
        "generated_at": datetime.now().isoformat(timespec="seconds"),
        "model_name": model_name,
        "model_path": model_path,
        "gate_profile": GATE_PROFILE,
        "capture_mode": CAPTURE_MODE,
        "feature_profile": FEATURE_PROFILE,
        "input_shape": [1, SEQ_LEN, FEAT_SIZE],
        "capture_warmup_frames": HAND_READY_FRAMES,
        "dominant_hand": configured_hand_preference(),
        "single_hand_pose": single_hand_pose_enabled(),
        "mirrored_input": configured_mirror_input(MIRROR_INPUT),
        "expected_labels": EXPECTED_LABELS,
        "trials_per_label": TRIALS_PER_LABEL if CAPTURE_MODE == "hand_trigger_auto" else None,
        "demo_label_ranking": build_demo_label_ranking(rows),
        "summary": totals,
        "records": rows,
    }
    with open(path, "w", encoding="utf-8") as f:
        json.dump(payload, f, indent=2)
    return payload


def write_ranking_reports(json_path, payload):
    ranking = payload.get("demo_label_ranking", {})
    ranking_json_path = json_path.with_name(f"{json_path.stem}_ranking.json")
    ranking_md_path = json_path.with_name(f"{json_path.stem}_ranking.md")

    with open(ranking_json_path, "w", encoding="utf-8") as f:
        json.dump(
            {
                "generated_at": payload.get("generated_at"),
                "model_name": payload.get("model_name"),
                "model_path": payload.get("model_path"),
                "gate_profile": payload.get("gate_profile"),
                "capture_mode": payload.get("capture_mode"),
                "feature_profile": payload.get("feature_profile"),
                "input_shape": payload.get("input_shape"),
                "capture_warmup_frames": payload.get("capture_warmup_frames"),
                "demo_label_ranking": ranking,
            },
            f,
            indent=2,
        )

    def list_text(items):
        return ", ".join(items) if items else "-"

    lines = [
        "# VoxGest Demo Live Label Ranking",
        "",
        f"- Generated: {payload.get('generated_at')}",
        f"- Model: {payload.get('model_name')} ({payload.get('model_path')})",
        f"- Feature profile: {payload.get('feature_profile')}",
        f"- Input shape: {payload.get('input_shape')}",
        f"- Gate profile: {payload.get('gate_profile')}",
        f"- Capture mode: {payload.get('capture_mode')}",
        f"- Warm-up frames: {payload.get('capture_warmup_frames')}",
        "",
        "## Buckets",
        "",
        f"- Stable labels: {list_text(ranking.get('stable_labels', []))}",
        f"- Partially stable labels: {list_text(ranking.get('partially_stable_labels', []))}",
        f"- Unstable labels: {list_text(ranking.get('unstable_labels', []))}",
        f"- Gate-blocked labels: {list_text(ranking.get('gate_blocked_labels', []))}",
        f"- Capture-blocked labels: {list_text(ranking.get('capture_blocked_labels', []))}",
        "",
        "## Per Label",
        "",
        "| Label | Trials | Matched | Accepted Correct | False Accepts | Gate Blocked | Capture Blocked | Match Rate |",
        "|---|---:|---:|---:|---:|---:|---:|---:|",
    ]
    for label, row in sorted((ranking.get("per_label") or {}).items()):
        lines.append(
            "| {label} | {trials} | {matched} | {accepted_correct} | {false_accepts} | "
            "{gate_blocked} | {capture_blocked} | {match_rate:.0%} |".format(
                label=label,
                trials=int(row.get("trials", 0)),
                matched=int(row.get("matched", 0)),
                accepted_correct=int(row.get("accepted_correct", 0)),
                false_accepts=int(row.get("false_accepts", 0)),
                gate_blocked=int(row.get("gate_blocked", 0)),
                capture_blocked=int(row.get("capture_blocked", 0)),
                match_rate=float(row.get("match_rate", 0.0)),
            )
        )
    lines.extend(
        [
            "",
            "## Demo Runtime Notes",
            "",
            "- `NOTHING` remains a no-output class and is never accepted as a sentence token.",
            "- `VOXGEST_GATE_PROFILE=demo` lowers thresholds only for demo/runtime testing.",
            "- Strict gating remains the default when `VOXGEST_GATE_PROFILE` is not set.",
            "- Demo capture mode waits for stable hands before recording the 30-frame window.",
        ]
    )
    ranking_md_path.write_text("\n".join(lines) + "\n", encoding="utf-8")
    return ranking_json_path, ranking_md_path


def draw(frame, model_name, expected, state, trial_text, last_row, last_prediction, paused=False):
    h, w = frame.shape[:2]
    overlay = frame.copy()
    cv2.rectangle(overlay, (0, 0), (w, 220), (10, 10, 10), -1)
    cv2.addWeighted(overlay, 0.72, frame, 0.28, 0, frame)

    shown_state = "PAUSED" if paused else state
    pred = last_prediction or default_prediction()
    cv2.putText(frame, f"EXPECTED: {expected}", (15, 36), cv2.FONT_HERSHEY_SIMPLEX, 0.90, (0, 255, 150), 2)
    cv2.putText(
        frame,
        f"state {shown_state}   trial {trial_text}   model {model_name}",
        (15, 70),
        cv2.FONT_HERSHEY_SIMPLEX,
        0.58,
        (230, 230, 230),
        1,
    )
    cv2.putText(
        frame,
        f"hand {float(pred.get('hand_presence', 0.0)):.0%}   motion {float(pred.get('motion', 0.0)):.3f}",
        (15, 102),
        cv2.FONT_HERSHEY_SIMPLEX,
        0.52,
        (210, 220, 220),
        1,
    )
    cv2.putText(
        frame,
        f"prediction {pred.get('label') or '-'}   confidence {float(pred.get('confidence', 0.0)):.0%}",
        (15, 132),
        cv2.FONT_HERSHEY_SIMPLEX,
        0.52,
        (205, 225, 225),
        1,
    )
    cv2.putText(
        frame,
        f"quality {pred.get('quality_status', '-')}   allowed={bool(pred.get('inference_allowed', False))}",
        (15, 162),
        cv2.FONT_HERSHEY_SIMPLEX,
        0.52,
        (160, 220, 160) if pred.get("inference_allowed") else (120, 200, 255),
        1,
    )
    if last_row:
        verdict = "accepted" if last_row["accepted_by_gate"] else "rejected"
        cv2.putText(
            frame,
            f"last {last_row['expected_label']} -> {last_row['predicted_label'] or '-'} "
            f"{float(last_row['confidence']):.0%} {verdict}",
            (15, 192),
            cv2.FONT_HERSHEY_SIMPLEX,
            0.48,
            (190, 190, 190),
            1,
        )

    controls = "Q/ESC quit  R retry  N skip  P pause"
    if CAPTURE_MODE == "manual":
        controls = "SPACE log  N next  B previous  Q/ESC quit"
    elif CAPTURE_MODE == "manual_capture":
        controls = "SPACE/C start capture  N next  B previous  R retry  Q/ESC quit"
    cv2.putText(frame, controls, (15, h - 18), cv2.FONT_HERSHEY_SIMPLEX, 0.48, (210, 210, 210), 1)


def draw_landmarks(frame, results, mp_holistic, mp_draw):
    if results.pose_landmarks:
        mp_draw.draw_landmarks(frame, results.pose_landmarks, mp_holistic.POSE_CONNECTIONS)
    for hand in (results.right_hand_landmarks, results.left_hand_landmarks):
        if hand:
            mp_draw.draw_landmarks(frame, hand, mp_holistic.HAND_CONNECTIONS)


def current_auto_label(auto_trial_idx):
    label_idx = auto_trial_idx // TRIALS_PER_LABEL
    if label_idx >= len(EXPECTED_LABELS):
        return None, 0, label_idx
    return EXPECTED_LABELS[label_idx], (auto_trial_idx % TRIALS_PER_LABEL) + 1, label_idx


def main():
    invalid = [label for label in EXPECTED_LABELS if label not in ACTIVE_DYNAMIC_LABELS]
    if invalid:
        print(f"Unknown/inactive expected labels: {invalid}")
        print(f"Allowed: {sorted(ACTIVE_DYNAMIC_LABELS)}")
        return

    model_name, model_path, model, idx_to_label = load_dynamic_model()
    stamp = datetime.now().strftime("%Y%m%d_%H%M%S")
    out_path = REPORT_DIR / f"live_word_test_log_{stamp}.csv"
    json_path = REPORT_DIR / f"live_word_test_log_{stamp}.json"
    fields = write_header(out_path)
    rows = []

    print("=" * 76)
    print("VoxGest live word test logger")
    print("=" * 76)
    print(f"Model   : {model_name} ({model_path})")
    print(f"Profile : {WORD_PROFILE}")
    print(f"Feature : {FEATURE_PROFILE} ({FEAT_SIZE} floats/frame)")
    print(f"Gate    : {GATE_PROFILE}")
    print(f"Capture : {CAPTURE_MODE}")
    print(f"Labels  : {EXPECTED_LABELS}")
    if CAPTURE_MODE == "hand_trigger_auto":
        print(f"Trials  : {TRIALS_PER_LABEL} per label")
    elif CAPTURE_MODE == "manual_capture":
        print("Trials  : manual one-word capture; SPACE/C starts each 30-frame window")
    else:
        print("Trials  : manual SPACE logging")
    print(f"CSV     : {out_path}")
    print(f"JSON    : {json_path}")
    print(f"Hand    : {configured_hand_preference()}")
    print(f"Map     : {hand_mapping_text(mirrored_input=MIRROR_INPUT)}")
    print(f"Pose    : {'single-hand' if single_hand_pose_enabled() else 'full-pose'}")
    print(f"Mirror  : {configured_mirror_input(MIRROR_INPUT)}")
    print(f"Overlap : {'allowed for FullSign225' if FULLSIGN_ALLOW_HAND_OVERLAP else 'strict'}")
    print()

    frame_window = deque(maxlen=SEQ_LEN)
    quality_window = deque(maxlen=SEQ_LEN)
    ready_count = 0
    capture_frames = []
    capture_quality = []
    capture_missing_frames = 0
    previous_vec = None
    current_motion = 0.0
    state = MANUAL if CAPTURE_MODE == "manual" else WAITING
    last_state_print = None
    last_row = None
    last_prediction = default_prediction("waiting for a complete sequence")
    expected_idx = 0
    manual_trial_count = 0
    auto_trial_idx = 0
    result_until = 0.0
    rest_until = 0.0
    paused = False
    stop_requested = False
    frame_count = 0
    no_pose_frames = 0
    total_auto_trials = len(EXPECTED_LABELS) * TRIALS_PER_LABEL

    mp_holistic = mp.solutions.holistic
    mp_draw = mp.solutions.drawing_utils
    cap = cv2.VideoCapture(0)
    cap.set(cv2.CAP_PROP_FRAME_WIDTH, 640)
    cap.set(cv2.CAP_PROP_FRAME_HEIGHT, 480)

    try:
        with mp_holistic.Holistic(
            min_detection_confidence=0.45,
            min_tracking_confidence=0.40,
            model_complexity=1,
        ) as holistic:
            while cap.isOpened() and not stop_requested:
                ok, frame = cap.read()
                if not ok:
                    break

                now = time.time()
                frame_count += 1
                frame = cv2.flip(frame, 1)
                rgb = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
                results = holistic.process(rgb)
                draw_landmarks(frame, results, mp_holistic, mp_draw)

                vec = extract_frame_features(results, mirrored_input=MIRROR_INPUT)
                record = capture_frame_quality(results, mirrored_input=MIRROR_INPUT) if vec is not None else None
                if vec is not None:
                    current_motion = frame_motion_delta(previous_vec, vec)
                    previous_vec = vec
                else:
                    current_motion = 0.0

                if CAPTURE_MODE == "manual":
                    if vec is not None:
                        no_pose_frames = 0
                        frame_window.append(vec)
                        quality_window.append(record)
                        if len(frame_window) == SEQ_LEN and frame_count % DYNAMIC_EVERY_N_FRAMES == 0:
                            last_prediction = classify_sequence(list(frame_window), list(quality_window), model, idx_to_label)
                    else:
                        no_pose_frames += 1
                        if no_pose_frames >= NO_POSE_RESET_FRAMES:
                            frame_window.clear()
                            quality_window.clear()
                            last_prediction = {
                                **default_prediction("pose missing for reset window"),
                                "quality_status": "BAD_SEQUENCE",
                                "quality_reason": "pose missing for reset window",
                            }
                            no_pose_frames = 0

                    expected = EXPECTED_LABELS[expected_idx]
                    trial_text = str(manual_trial_count)
                elif CAPTURE_MODE == "manual_capture":
                    expected = EXPECTED_LABELS[expected_idx]
                    trial_text = str(manual_trial_count)
                    trial_in_label = manual_trial_count + 1

                    if not paused:
                        if state == WAITING:
                            last_prediction = {
                                **last_prediction,
                                "motion": current_motion,
                                "hand_presence": 1.0 if record and record.get("selected_hand_present") else 0.0,
                                "quality_status": "GOOD" if ready_from_frame(vec, record) else "LOW_HAND_PRESENCE",
                                "quality_reason": "press SPACE/C to start capture",
                                "inference_allowed": False,
                            }
                        elif state == STABILIZING:
                            if ready_from_frame(vec, record):
                                ready_count += 1
                            else:
                                ready_count = 0
                            last_prediction = {
                                **last_prediction,
                                "motion": current_motion,
                                "hand_presence": 1.0 if record and record.get("selected_hand_present") else 0.0,
                                "quality_status": "GOOD" if ready_count else "LOW_HAND_PRESENCE",
                                "quality_reason": f"stabilizing {ready_count}/{HAND_READY_FRAMES}",
                                "inference_allowed": False,
                            }
                            if ready_count >= HAND_READY_FRAMES:
                                state = RECORDING
                                capture_frames = []
                                capture_quality = []
                                capture_missing_frames = 0
                                print(f"{expected} trial {trial_in_label} | RECORDING", flush=True)
                        elif state == RECORDING:
                            if vec is None:
                                capture_missing_frames += 1
                                if capture_missing_frames >= NO_POSE_RESET_FRAMES:
                                    state = WAITING
                                    ready_count = 0
                                    capture_frames = []
                                    capture_quality = []
                                    last_prediction = {
                                        **default_prediction("capture cancelled: pose missing"),
                                        "quality_status": "BAD_SEQUENCE",
                                        "quality_reason": "capture cancelled: pose missing",
                                    }
                                    print(f"{expected} trial {trial_in_label} | WAITING | capture cancelled: pose missing", flush=True)
                            else:
                                capture_frames.append(vec)
                                capture_quality.append(record)
                                last_prediction = {
                                    **last_prediction,
                                    "motion": current_motion,
                                    "hand_presence": 1.0 if record and record.get("selected_hand_present") else 0.0,
                                    "quality_status": "GOOD",
                                    "quality_reason": f"recording {len(capture_frames)}/{CAPTURE_FRAMES}",
                                    "inference_allowed": False,
                                }
                                if len(capture_frames) >= CAPTURE_FRAMES:
                                    state = CLASSIFYING
                        elif state == CLASSIFYING:
                            last_prediction = classify_sequence(capture_frames, capture_quality, model, idx_to_label)
                            row = build_trial_row(model_name, model_path, expected, last_prediction, state)
                            append_row(out_path, fields, row)
                            rows.append(row)
                            last_row = row
                            manual_trial_count += 1
                            print_trial_result(expected, manual_trial_count, state, row)
                            state = RESULT
                            result_until = now + RESULT_SECONDS
                        elif state == RESULT:
                            if now >= result_until:
                                state = WAITING
                                ready_count = 0
                                capture_frames = []
                                capture_quality = []
                                capture_missing_frames = 0

                    if state != last_state_print:
                        print(f"{expected} trial {trial_in_label} | {state}", flush=True)
                        last_state_print = state
                else:
                    expected, trial_in_label, label_idx = current_auto_label(auto_trial_idx)
                    if expected is None:
                        print("All automatic trials complete.", flush=True)
                        break

                    if paused:
                        trial_text = f"{auto_trial_idx + 1}/{total_auto_trials}"
                    elif state == WAITING:
                        if ready_from_frame(vec, record):
                            state = STABILIZING
                            ready_count += 1
                        else:
                            ready_count = 0
                        last_prediction = {
                            **last_prediction,
                            "motion": current_motion,
                            "hand_presence": 1.0 if record and record.get("selected_hand_present") else 0.0,
                            "quality_status": "GOOD" if ready_count else "LOW_HAND_PRESENCE",
                            "quality_reason": "hands visible" if ready_count else "waiting for hands",
                            "inference_allowed": False,
                        }
                    elif state == STABILIZING:
                        if not ready_from_frame(vec, record):
                            state = WAITING
                            ready_count = 0
                        else:
                            ready_count += 1
                        last_prediction = {
                            **last_prediction,
                            "motion": current_motion,
                            "hand_presence": 1.0 if record and record.get("selected_hand_present") else 0.0,
                            "quality_status": "GOOD" if ready_count else "LOW_HAND_PRESENCE",
                            "quality_reason": f"stabilizing {ready_count}/{HAND_READY_FRAMES}",
                            "inference_allowed": False,
                        }
                        if ready_count >= HAND_READY_FRAMES and current_motion >= MOTION_START_THRESHOLD:
                            state = RECORDING
                            capture_frames = []
                            capture_quality = []
                            capture_missing_frames = 0
                            print(f"{expected} trial {trial_in_label} | RECORDING", flush=True)
                    elif state == RECORDING:
                        if vec is None:
                            capture_missing_frames += 1
                            if capture_missing_frames >= NO_POSE_RESET_FRAMES:
                                state = WAITING
                                ready_count = 0
                                capture_frames = []
                                capture_quality = []
                                last_prediction = {
                                    **default_prediction("capture cancelled: pose missing"),
                                    "quality_status": "BAD_SEQUENCE",
                                    "quality_reason": "capture cancelled: pose missing",
                                }
                                print(f"{expected} trial {trial_in_label} | WAITING | capture cancelled: pose missing", flush=True)
                        else:
                            capture_frames.append(vec)
                            capture_quality.append(record)
                            last_prediction = {
                                **last_prediction,
                                "motion": current_motion,
                                "hand_presence": 1.0 if record and record.get("selected_hand_present") else 0.0,
                                "quality_status": "GOOD",
                                "quality_reason": f"recording {len(capture_frames)}/{CAPTURE_FRAMES}",
                                "inference_allowed": False,
                            }
                            if len(capture_frames) >= CAPTURE_FRAMES:
                                state = CLASSIFYING
                    elif state == CLASSIFYING:
                        last_prediction = classify_sequence(capture_frames, capture_quality, model, idx_to_label)
                        row = build_trial_row(model_name, model_path, expected, last_prediction, state)
                        append_row(out_path, fields, row)
                        rows.append(row)
                        last_row = row
                        print_trial_result(expected, trial_in_label, state, row)
                        auto_trial_idx += 1
                        state = RESULT
                        result_until = now + RESULT_SECONDS
                    elif state == RESULT:
                        if now >= result_until:
                            state = REST
                            rest_until = now + REST_SECONDS
                    elif state == REST:
                        if now >= rest_until:
                            ready_count = 0
                            capture_frames = []
                            capture_quality = []
                            capture_missing_frames = 0
                            state = WAITING

                    if state != last_state_print:
                        print(f"{expected} trial {trial_in_label} | {state}", flush=True)
                        last_state_print = state
                    display_expected = last_row["expected_label"] if state in {RESULT, REST} and last_row else expected
                    trial_text = f"{auto_trial_idx + 1 if auto_trial_idx < total_auto_trials else total_auto_trials}/{total_auto_trials}"
                    expected = display_expected

                draw(frame, model_name, expected, state, trial_text, last_row, last_prediction, paused)
                cv2.imshow("VoxGest Live Word Logger", frame)
                key = cv2.waitKey(1) & 0xFF
                if key in {27, ord("q"), ord("Q")}:
                    stop_requested = True
                elif key in {ord("p"), ord("P")}:
                    paused = not paused
                    print("Paused." if paused else "Resumed.", flush=True)
                elif CAPTURE_MODE == "manual":
                    if key in {ord("n"), ord("N")}:
                        expected_idx = (expected_idx + 1) % len(EXPECTED_LABELS)
                    elif key in {ord("b"), ord("B")}:
                        expected_idx = (expected_idx - 1) % len(EXPECTED_LABELS)
                    elif key == ord(" "):
                        expected = EXPECTED_LABELS[expected_idx]
                        row = build_trial_row(model_name, model_path, expected, last_prediction, "MANUAL")
                        append_row(out_path, fields, row)
                        rows.append(row)
                        last_row = row
                        manual_trial_count += 1
                        print_trial_result(expected, manual_trial_count, "MANUAL", row)
                elif CAPTURE_MODE == "manual_capture":
                    if key in {ord("n"), ord("N")}:
                        expected_idx = (expected_idx + 1) % len(EXPECTED_LABELS)
                        state = WAITING
                        ready_count = 0
                        capture_frames = []
                        capture_quality = []
                        capture_missing_frames = 0
                    elif key in {ord("b"), ord("B")}:
                        expected_idx = (expected_idx - 1) % len(EXPECTED_LABELS)
                        state = WAITING
                        ready_count = 0
                        capture_frames = []
                        capture_quality = []
                        capture_missing_frames = 0
                    elif key in {ord("r"), ord("R")}:
                        state = WAITING
                        ready_count = 0
                        capture_frames = []
                        capture_quality = []
                        capture_missing_frames = 0
                        print("Retrying current manual-capture trial.", flush=True)
                    elif key in {ord(" "), ord("c"), ord("C")} and state in {WAITING, RESULT}:
                        state = STABILIZING
                        ready_count = 0
                        capture_frames = []
                        capture_quality = []
                        capture_missing_frames = 0
                        expected = EXPECTED_LABELS[expected_idx]
                        print(f"{expected} trial {manual_trial_count + 1} | START CAPTURE", flush=True)
                else:
                    if key in {ord("r"), ord("R")}:
                        ready_count = 0
                        capture_frames = []
                        capture_quality = []
                        capture_missing_frames = 0
                        state = WAITING
                        print("Retrying current trial.", flush=True)
                    elif key in {ord("n"), ord("N")}:
                        print("Skipping current trial.", flush=True)
                        auto_trial_idx += 1
                        ready_count = 0
                        capture_frames = []
                        capture_quality = []
                        capture_missing_frames = 0
                        state = WAITING

    except KeyboardInterrupt:
        print("\nInterrupted by CTRL+C. Writing partial logs.", flush=True)
    finally:
        cap.release()
        cv2.destroyAllWindows()
        payload = write_json_summary(json_path, rows, model_name, model_path)
        ranking_json_path, ranking_md_path = write_ranking_reports(json_path, payload)
        print(f"\nWrote: {out_path}")
        print(f"Wrote: {json_path}")
        print(f"Wrote: {ranking_json_path}")
        print(f"Wrote: {ranking_md_path}")


if __name__ == "__main__":
    main()
