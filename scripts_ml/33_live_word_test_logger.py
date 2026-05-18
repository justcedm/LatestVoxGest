"""Live dynamic-word test logger for Recognition Hardening v1.

Press SPACE after performing the expected sign to log one trial. The CSV row
captures the raw prediction, confidence, motion metrics, gate result, and a
failure reason.
"""

import csv
import json
import os
import sys
from collections import deque
from datetime import datetime
from pathlib import Path

import cv2
import mediapipe as mp
import numpy as np
import tensorflow as tf

from lstm_features import (
    FEAT_SIZE,
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
from word_config import NEGATIVE_WORDS, TARGET_WORDS, TRAINING_WORDS, WORD_PROFILE


os.environ["PROTOCOL_BUFFERS_PYTHON_IMPLEMENTATION"] = "python"
os.environ["TF_CPP_MIN_LOG_LEVEL"] = "3"

ROOT = Path(__file__).resolve().parents[1]
REPORT_DIR = ROOT / "reports"
FEATURE_PROFILE = configured_feature_profile()


def artifact_suffix():
    if FEATURE_PROFILE != "onehand162":
        safe_word = "".join(ch if ch.isalnum() or ch in {"_", "-"} else "_" for ch in WORD_PROFILE)
        return safe_word if safe_word.endswith(f"_{FEATURE_PROFILE}") else f"{safe_word}_{FEATURE_PROFILE}"
    if WORD_PROFILE == "demo10":
        return "v1"
    return "".join(ch if ch.isalnum() or ch in {"_", "-"} else "_" for ch in WORD_PROFILE)


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
DYNAMIC_MODEL_KIND = os.environ.get("VOXGEST_DYNAMIC_MODEL", "auto").strip().lower()
ALLOW_EXTRA_WORD_LABELS = os.environ.get("VOXGEST_ALLOW_EXTRA_WORD_LABELS", "0").strip().lower() in {
    "1",
    "true",
    "yes",
    "on",
}

ACTIVE_WORD_LABELS = set(TARGET_WORDS)
ACTIVE_DYNAMIC_LABELS = set(TRAINING_WORDS)
MIRROR_INPUT = True
EXPECTED_LABELS = [item.upper() for item in sys.argv[1:]] or list(TARGET_WORDS) + list(NEGATIVE_WORDS)
DYNAMIC_EVERY_N_FRAMES = int(os.environ.get("VOXGEST_DYNAMIC_EVERY_N_FRAMES", "3"))
NO_POSE_RESET_FRAMES = 12

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


def dynamic_candidates():
    options = {
        "tcn": [("TCN", TCN_MODEL, TCN_LABELS, TCN_REPORT)],
        "lstm": [("LSTM", LSTM_MODEL, LSTM_LABELS, LSTM_REPORT)],
    }
    if DYNAMIC_MODEL_KIND in options:
        candidates = options[DYNAMIC_MODEL_KIND]
    else:
        candidates = sorted(options["tcn"] + options["lstm"], key=lambda item: report_score(item[3]), reverse=True)
    return candidates


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
        return name, str(model_path), model, idx_to_label
    raise RuntimeError("No usable dynamic model. " + " | ".join(errors))


def gate_failure(label, conf, margin, motion, wrist_path, hand_presence):
    rule = THRESHOLDS.get(label, DEFAULT_THRESHOLD)
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


def draw(frame, model_name, expected, trial_count, last_row):
    h, w = frame.shape[:2]
    overlay = frame.copy()
    cv2.rectangle(overlay, (0, 0), (w, 170), (10, 10, 10), -1)
    cv2.addWeighted(overlay, 0.72, frame, 0.28, 0, frame)
    cv2.putText(frame, f"EXPECTED: {expected}", (15, 38), cv2.FONT_HERSHEY_SIMPLEX, 0.95, (0, 255, 150), 2)
    cv2.putText(frame, f"model {model_name}   logged {trial_count}", (15, 76), cv2.FONT_HERSHEY_SIMPLEX, 0.60, (230, 230, 230), 1)
    if last_row:
        cv2.putText(
            frame,
            f"last {last_row['predicted_label']} conf {float(last_row['confidence']):.0%} {last_row['failure_reason']}",
            (15, 112),
            cv2.FONT_HERSHEY_SIMPLEX,
            0.52,
            (190, 190, 190),
            1,
        )
    cv2.putText(
        frame,
        "SPACE=log trial  N=next expected  B=previous  Q=finish",
        (15, h - 18),
        cv2.FONT_HERSHEY_SIMPLEX,
        0.48,
        (210, 210, 210),
        1,
    )


def write_header(path):
    path.parent.mkdir(parents=True, exist_ok=True)
    fields = [
        "timestamp",
        "model_name",
        "model_path",
        "expected_label",
        "predicted_label",
        "confidence",
        "margin",
        "motion",
        "wrist_path",
        "hand_presence",
        "accepted_by_gate",
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
        writer = csv.DictWriter(f, fieldnames=fields)
        writer.writerow(row)


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
            },
        )
        bucket["trials"] += 1
        if row["matched_expected"]:
            bucket["matched_expected"] += 1
        if row["accepted_by_gate"]:
            bucket["accepted_by_gate"] += 1
        else:
            bucket["rejected"] += 1
        if row["accepted_by_gate"] and not row["matched_expected"]:
            bucket["false_accepts"] += 1
        predicted = row["predicted_label"]
        if predicted != expected:
            bucket["confusions"][predicted] = bucket["confusions"].get(predicted, 0) + 1
        reason = row["failure_reason"]
        if reason != "ok":
            for part in str(reason).split(";"):
                bucket["failure_reasons"][part] = bucket["failure_reasons"].get(part, 0) + 1

    payload = {
        "generated_at": datetime.now().isoformat(timespec="seconds"),
        "model_name": model_name,
        "model_path": model_path,
        "feature_profile": FEATURE_PROFILE,
        "input_shape": [1, SEQ_LEN, FEAT_SIZE],
        "dominant_hand": configured_hand_preference(),
        "single_hand_pose": single_hand_pose_enabled(),
        "mirrored_input": configured_mirror_input(MIRROR_INPUT),
        "expected_labels": EXPECTED_LABELS,
        "summary": totals,
        "records": rows,
    }
    with open(path, "w", encoding="utf-8") as f:
        json.dump(payload, f, indent=2)


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
    print(f"Labels  : {EXPECTED_LABELS}")
    print(f"CSV     : {out_path}")
    print(f"JSON    : {json_path}")
    print(f"Hand    : {configured_hand_preference()}")
    print(f"Map     : {hand_mapping_text(mirrored_input=MIRROR_INPUT)}")
    print(f"Pose    : {'single-hand' if single_hand_pose_enabled() else 'full-pose'}")
    print(f"Mirror  : {configured_mirror_input(MIRROR_INPUT)}")
    print(f"Cadence : every {DYNAMIC_EVERY_N_FRAMES} frame(s) once the 30-frame buffer is full")
    print()

    frame_window = deque(maxlen=SEQ_LEN)
    expected_idx = 0
    trial_count = 0
    frame_count = 0
    no_pose_frames = 0
    last_row = None
    last_prediction = {
        "label": "",
        "confidence": 0.0,
        "margin": 0.0,
        "motion": 0.0,
        "wrist_path": 0.0,
        "hand_presence": 0.0,
    }

    mp_holistic = mp.solutions.holistic
    mp_draw = mp.solutions.drawing_utils
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
            frame_count += 1
            frame = cv2.flip(frame, 1)
            rgb = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
            results = holistic.process(rgb)

            if results.pose_landmarks:
                mp_draw.draw_landmarks(frame, results.pose_landmarks, mp_holistic.POSE_CONNECTIONS)
            for hand in (results.right_hand_landmarks, results.left_hand_landmarks):
                if hand:
                    mp_draw.draw_landmarks(frame, hand, mp_holistic.HAND_CONNECTIONS)

            vec = extract_frame_features(results, mirrored_input=MIRROR_INPUT)
            if vec is not None:
                no_pose_frames = 0
                frame_window.append(vec)
                if len(frame_window) == SEQ_LEN and frame_count % DYNAMIC_EVERY_N_FRAMES == 0:
                    seq = np.array(list(frame_window), dtype=np.float32)
                    probs = model.predict(seq[np.newaxis, ...], verbose=0)[0]
                    pred_idx, conf, margin = top_prediction(probs)
                    last_prediction = {
                        "label": idx_to_label.get(pred_idx, "?"),
                        "confidence": conf,
                        "margin": margin,
                        "motion": sequence_motion_energy(seq),
                        "wrist_path": sequence_wrist_path(seq),
                        "hand_presence": sequence_hand_presence_ratio(seq),
                    }
            else:
                no_pose_frames += 1
                if no_pose_frames >= NO_POSE_RESET_FRAMES:
                    frame_window.clear()
                    last_prediction = {
                        "label": "",
                        "confidence": 0.0,
                        "margin": 0.0,
                        "motion": 0.0,
                        "wrist_path": 0.0,
                        "hand_presence": 0.0,
                    }
                    no_pose_frames = 0

            expected = EXPECTED_LABELS[expected_idx]
            draw(frame, model_name, expected, trial_count, last_row)
            cv2.imshow("VoxGest Live Word Logger", frame)
            key = cv2.waitKey(1) & 0xFF
            if key == ord("q"):
                break
            if key == ord("n"):
                expected_idx = (expected_idx + 1) % len(EXPECTED_LABELS)
            elif key == ord("b"):
                expected_idx = (expected_idx - 1) % len(EXPECTED_LABELS)
            elif key == ord(" "):
                pred = last_prediction["label"]
                failures = gate_failure(
                    pred,
                    last_prediction["confidence"],
                    last_prediction["margin"],
                    last_prediction["motion"],
                    last_prediction["wrist_path"],
                    last_prediction["hand_presence"],
                )
                accepted = not failures
                matched = pred == expected
                if not matched:
                    failures.append(f"wrong_label:{pred}")
                row = {
                    "timestamp": datetime.now().isoformat(timespec="seconds"),
                    "model_name": model_name,
                    "model_path": model_path,
                    "expected_label": expected,
                    "predicted_label": pred,
                    "confidence": round(float(last_prediction["confidence"]), 6),
                    "margin": round(float(last_prediction["margin"]), 6),
                    "motion": round(float(last_prediction["motion"]), 6),
                    "wrist_path": round(float(last_prediction["wrist_path"]), 6),
                    "hand_presence": round(float(last_prediction["hand_presence"]), 6),
                    "accepted_by_gate": accepted,
                    "matched_expected": matched,
                    "failure_reason": "ok" if not failures else ";".join(failures),
                    "dominant_hand": configured_hand_preference(),
                    "feature_profile": FEATURE_PROFILE,
                    "single_hand_pose": single_hand_pose_enabled(),
                    "mirrored_input": configured_mirror_input(MIRROR_INPUT),
                }
                append_row(out_path, fields, row)
                rows.append(row)
                last_row = row
                trial_count += 1

    cap.release()
    cv2.destroyAllWindows()
    write_json_summary(json_path, rows, model_name, model_path)
    print(f"\nWrote: {out_path}")
    print(f"Wrote: {json_path}")


if __name__ == "__main__":
    main()
