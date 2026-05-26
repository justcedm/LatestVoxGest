"""
Motion-model live diagnostic for the 30x162 word model.

Use this before the full sentence builder when you only want to see the raw
word model output, top confidence, margin, and motion score.
"""

import json
import os
from collections import deque

import cv2
import mediapipe as mp
import numpy as np
import tensorflow as tf

from lstm_features import (
    SEQ_LEN,
    configured_hand_preference,
    configured_mirror_input,
    extract_frame_features,
    sequence_hand_presence_ratio,
    sequence_motion_energy,
    sequence_wrist_path,
    single_hand_pose_enabled,
    top_prediction,
)

os.environ["PROTOCOL_BUFFERS_PYTHON_IMPLEMENTATION"] = "python"
os.environ["TF_CPP_MIN_LOG_LEVEL"] = "3"

LSTM_MODEL = "model/voxgest_lstm_v1.h5"
LSTM_LABELS = "model/class_labels_lstm_v1.json"
LSTM_REPORT = "model/lstm_training_report.json"
TCN_MODEL = "model/voxgest_tcn_v1.h5"
TCN_LABELS = "model/class_labels_tcn_v1.json"
TCN_REPORT = "model/tcn_training_report.json"
DYNAMIC_MODEL_KIND = os.environ.get("VOXGEST_DYNAMIC_MODEL", "auto").strip().lower()

MIN_CONF = float(os.environ.get("VOXGEST_DIAG_MIN_CONF", "0.65"))
MIN_MARGIN = float(os.environ.get("VOXGEST_DIAG_MIN_MARGIN", "0.12"))
MIN_MOTION = float(os.environ.get("VOXGEST_DIAG_MIN_MOTION", "0.03"))
MIN_WRIST_PATH = float(os.environ.get("VOXGEST_DIAG_MIN_WRIST_PATH", "0.35"))
MIN_HAND_PRESENCE = float(os.environ.get("VOXGEST_DIAG_MIN_HAND_PRESENCE", "0.25"))

WORD_RULES = {
    "YES": {"conf": 0.56, "margin": 0.07, "motion": 0.008, "path": 0.05},
    "NO": {"conf": 0.55, "margin": 0.06, "motion": 0.008, "path": 0.04},
    "WATER": {"conf": 0.56, "margin": 0.06, "motion": 0.006, "path": 0.04},
    "PLEASE": {"conf": 0.68, "margin": 0.18, "motion": 0.025, "path": 0.25},
    "HELLO": {"conf": 0.70, "margin": 0.18, "motion": 0.030, "path": 0.30},
    "HELP": {"conf": 0.60, "margin": 0.10, "motion": 0.025, "path": 0.20},
    "STOP": {"conf": 0.62, "margin": 0.10, "motion": 0.025, "path": 0.20},
    "DOCTOR": {"conf": 0.70, "margin": 0.15, "motion": 0.020, "path": 0.20},
    "NAME": {"conf": 0.55, "margin": 0.05, "motion": 0.020, "path": 0.20},
    "THANKYOU": {"conf": 0.52, "margin": 0.03, "motion": 0.010, "path": 0.08},
    "NOTHING": {"conf": 0.55, "margin": 0.05, "motion": 0.000, "path": 0.00, "presence": 0.00},
}
MIRROR_INPUT = True


def load_labels(path):
    with open(path, "r", encoding="utf-8") as f:
        raw = json.load(f)
    if all(str(k).isdigit() for k in raw.keys()):
        return {int(k): v for k, v in raw.items()}
    return {int(v): k for k, v in raw.items()}


def dynamic_model_candidates():
    def report_score(path):
        try:
            with open(path, "r", encoding="utf-8") as f:
                return float(json.load(f).get("best_grouped_val_accuracy", -1.0))
        except Exception:
            return -1.0

    options = {
        "tcn": [("TCN", TCN_MODEL, TCN_LABELS, TCN_REPORT)],
        "lstm": [("LSTM", LSTM_MODEL, LSTM_LABELS, LSTM_REPORT)],
    }
    if DYNAMIC_MODEL_KIND in options:
        candidates = options[DYNAMIC_MODEL_KIND]
    else:
        candidates = sorted(
            options["tcn"] + options["lstm"],
            key=lambda item: report_score(item[3]),
            reverse=True,
        )
    return [(name, model, labels) for name, model, labels, _ in candidates]


def thresholds_for(label):
    rule = WORD_RULES.get(label, {})
    return (
        rule.get("conf", MIN_CONF),
        rule.get("margin", MIN_MARGIN),
        rule.get("motion", MIN_MOTION),
        rule.get("path", MIN_WRIST_PATH),
        rule.get("presence", MIN_HAND_PRESENCE),
    )


print("Loading motion model...")
model_name = ""
model_path = ""
last_error = None
for candidate_name, candidate_model, candidate_labels in dynamic_model_candidates():
    if not os.path.exists(candidate_model) or not os.path.exists(candidate_labels):
        last_error = f"missing {candidate_model} or {candidate_labels}"
        continue
    try:
        lstm_model = tf.keras.models.load_model(candidate_model)
        labels = load_labels(candidate_labels)
        model_name = candidate_name
        model_path = candidate_model
        break
    except Exception as exc:
        last_error = exc

if not model_name:
    raise RuntimeError(f"Could not load motion model: {last_error}")

print(f"Loaded {len(labels)} word classes.")
print(f"Motion model: {model_name} ({model_path})")
print(f"Dominant hand policy: {configured_hand_preference()}")
print(f"Pose policy: {'single-hand' if single_hand_pose_enabled() else 'full-pose'}")
print(f"Mirror input: {configured_mirror_input(MIRROR_INPUT)}")

mp_holistic = mp.solutions.holistic
mp_draw = mp.solutions.drawing_utils
frame_window = deque(maxlen=SEQ_LEN)

cap = cv2.VideoCapture(0)
cap.set(cv2.CAP_PROP_FRAME_WIDTH, 640)
cap.set(cv2.CAP_PROP_FRAME_HEIGHT, 480)

with mp_holistic.Holistic(
    min_detection_confidence=0.45,
    min_tracking_confidence=0.40,
    model_complexity=1,
) as holistic:
    while cap.isOpened():
        ok, frame = cap.read()
        if not ok:
            break

        frame = cv2.flip(frame, 1)
        rgb = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
        results = holistic.process(rgb)

        if results.pose_landmarks:
            mp_draw.draw_landmarks(frame, results.pose_landmarks, mp_holistic.POSE_CONNECTIONS)
        for hand_landmarks in (results.right_hand_landmarks, results.left_hand_landmarks):
            if hand_landmarks:
                mp_draw.draw_landmarks(frame, hand_landmarks, mp_holistic.HAND_CONNECTIONS)

        vec = extract_frame_features(results, mirrored_input=MIRROR_INPUT)
        if vec is None:
            frame_window.clear()
            cv2.putText(
                frame,
                "Need pose in frame",
                (20, 48),
                cv2.FONT_HERSHEY_SIMPLEX,
                1.0,
                (0, 0, 255),
                2,
            )
        else:
            frame_window.append(vec)
            if len(frame_window) < SEQ_LEN:
                cv2.putText(
                    frame,
                    f"Buffering {len(frame_window)}/{SEQ_LEN}",
                    (20, 48),
                    cv2.FONT_HERSHEY_SIMPLEX,
                    1.0,
                    (255, 160, 80),
                    2,
                )
            else:
                seq = np.array(list(frame_window), dtype=np.float32)
                probs = lstm_model.predict(seq[np.newaxis, ...], verbose=0)[0]
                top_idx, conf, margin = top_prediction(probs)
                motion = sequence_motion_energy(seq)
                wrist_path = sequence_wrist_path(seq)
                hand_presence = sequence_hand_presence_ratio(seq)
                order = np.argsort(probs)[-3:][::-1]
                label = labels.get(top_idx, "?")
                min_conf, min_margin, min_motion, min_path, min_presence = thresholds_for(label)
                accepted = (
                    conf >= min_conf
                    and margin >= min_margin
                    and motion >= min_motion
                    and wrist_path >= min_path
                    and hand_presence >= min_presence
                )
                color = (0, 255, 255) if accepted else (0, 0, 255)

                cv2.putText(
                    frame,
                    f"{label} {conf:.0%} margin {margin:.0%}",
                    (20, 48),
                    cv2.FONT_HERSHEY_SIMPLEX,
                    0.9,
                    color,
                    2,
                )
                cv2.putText(
                    frame,
                    f"motion {motion:.3f} path {wrist_path:.2f} hand {hand_presence:.0%}",
                    (20, 80),
                    cv2.FONT_HERSHEY_SIMPLEX,
                    0.6,
                    (220, 220, 220),
                    1,
                )
                cv2.putText(
                    frame,
                    (
                        f"{'ACCEPT' if accepted else 'REJECT'} "
                        f"min {min_conf:.0%}/{min_margin:.0%} "
                        f"mot {min_motion:.2f} path {min_path:.2f}"
                    ),
                    (20, 104),
                    cv2.FONT_HERSHEY_SIMPLEX,
                    0.48,
                    color,
                    1,
                )
                y = 132
                for idx in order:
                    cv2.putText(
                        frame,
                        f"{labels.get(int(idx), '?')}: {float(probs[idx]):.0%}",
                        (20, y),
                        cv2.FONT_HERSHEY_SIMPLEX,
                        0.55,
                        (180, 180, 180),
                        1,
                    )
                    y += 24

        cv2.imshow(f"VoxGest {model_name} Diagnostic", frame)
        if cv2.waitKey(1) & 0xFF == ord("q"):
            break

cap.release()
cv2.destroyAllWindows()
