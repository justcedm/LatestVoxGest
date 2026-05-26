"""Live static alphabet evaluation protocol.

Tests A-Z, del, space, and nothing with the current webcam/camera setup.
Outputs per-class accuracy plus a confusion table under reports/.
"""

import csv
import json
import os
import time
from collections import defaultdict
from datetime import datetime
from pathlib import Path

import cv2
import mediapipe as mp
import numpy as np
import tensorflow as tf

from lstm_features import normalize_static_hand, select_hand_landmarks, top_prediction


os.environ["PROTOCOL_BUFFERS_PYTHON_IMPLEMENTATION"] = "python"
os.environ["TF_CPP_MIN_LOG_LEVEL"] = "3"

ROOT = Path(__file__).resolve().parents[1]
REPORT_DIR = ROOT / "reports"
STATIC_MODEL = os.environ.get("VOXGEST_STATIC_MODEL", str(ROOT / "model" / "voxgest_v3.h5"))
STATIC_LABELS = os.environ.get("VOXGEST_STATIC_LABELS", str(ROOT / "model" / "class_labels_v3.json"))
SAMPLES_PER_LABEL = int(os.environ.get("VOXGEST_STATIC_EVAL_SAMPLES_PER_LABEL", "10"))
CAPTURE_STRIDE = int(os.environ.get("VOXGEST_STATIC_EVAL_CAPTURE_STRIDE_FRAMES", "10"))
THRESHOLD = float(os.environ.get("VOXGEST_STATIC_THRESHOLD", "0.55"))
MIRROR_INPUT = True

TARGET_LABELS = list("ABCDEFGHIJKLMNOPQRSTUVWXYZ") + ["del", "space", "nothing"]


def load_label_maps(path):
    with open(path, "r", encoding="utf-8") as f:
        raw = json.load(f)
    if all(str(k).lstrip("-").isdigit() for k in raw):
        idx_to_label = {int(k): v for k, v in raw.items()}
    else:
        idx_to_label = {int(v): k for k, v in raw.items()}
    return idx_to_label


def predict_static(model, idx_to_label, results):
    hand = select_hand_landmarks(results, mirrored_input=MIRROR_INPUT)
    if hand is None:
        return "nothing", 1.0, "no_hand"
    vec = normalize_static_hand(hand.landmark)
    probs = model.predict(vec[np.newaxis, :], verbose=0)[0]
    idx, conf, margin = top_prediction(probs)
    label = idx_to_label.get(idx, "?")
    if conf < THRESHOLD:
        return "low_conf", conf, f"margin={margin:.3f}"
    return label, conf, f"margin={margin:.3f}"


def draw(frame, label, saved, recording, last_pred):
    h, w = frame.shape[:2]
    overlay = frame.copy()
    cv2.rectangle(overlay, (0, 0), (w, 145), (10, 10, 10), -1)
    cv2.addWeighted(overlay, 0.72, frame, 0.28, 0, frame)
    color = (0, 255, 150) if recording else (140, 140, 140)
    cv2.putText(frame, f"TARGET: {label}", (15, 38), cv2.FONT_HERSHEY_SIMPLEX, 0.95, color, 2)
    cv2.putText(
        frame,
        f"samples {saved}/{SAMPLES_PER_LABEL}   last: {last_pred or '-'}",
        (15, 78),
        cv2.FONT_HERSHEY_SIMPLEX,
        0.62,
        (230, 230, 230),
        1,
    )
    cv2.putText(
        frame,
        "SPACE=start/pause  N=next label  Q=finish",
        (15, 116),
        cv2.FONT_HERSHEY_SIMPLEX,
        0.52,
        (180, 180, 180),
        1,
    )
    if label == "nothing":
        cv2.putText(
            frame,
            "For nothing: test idle/no hand plus neutral no-output hand poses.",
            (15, h - 18),
            cv2.FONT_HERSHEY_SIMPLEX,
            0.48,
            (210, 210, 210),
            1,
        )


def summarize(records):
    confusion = {label: defaultdict(int) for label in TARGET_LABELS}
    totals = defaultdict(int)
    correct = defaultdict(int)
    for rec in records:
        exp = rec["expected"]
        pred = rec["predicted"]
        totals[exp] += 1
        confusion[exp][pred] += 1
        if exp == pred:
            correct[exp] += 1

    per_class = []
    for label in TARGET_LABELS:
        total = totals[label]
        per_class.append(
            {
                "label": label,
                "correct": correct[label],
                "total": total,
                "accuracy": round((correct[label] / total * 100.0) if total else 0.0, 2),
            }
        )
    return per_class, {k: dict(v) for k, v in confusion.items()}


def write_reports(records):
    REPORT_DIR.mkdir(parents=True, exist_ok=True)
    per_class, confusion = summarize(records)
    payload = {
        "generated_at": datetime.now().isoformat(timespec="seconds"),
        "model": STATIC_MODEL,
        "labels": STATIC_LABELS,
        "samples_per_label": SAMPLES_PER_LABEL,
        "threshold": THRESHOLD,
        "per_class": per_class,
        "confusion": confusion,
        "records": records,
    }
    json_path = REPORT_DIR / "static_alphabet_live_eval.json"
    csv_path = REPORT_DIR / "static_alphabet_live_eval.csv"
    confusion_path = REPORT_DIR / "static_alphabet_live_confusion.csv"

    with open(json_path, "w", encoding="utf-8") as f:
        json.dump(payload, f, indent=2)

    with open(csv_path, "w", encoding="utf-8", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=["label", "correct", "total", "accuracy"])
        writer.writeheader()
        writer.writerows(per_class)

    all_predictions = sorted({pred for row in confusion.values() for pred in row} | set(TARGET_LABELS))
    with open(confusion_path, "w", encoding="utf-8", newline="") as f:
        writer = csv.writer(f)
        writer.writerow(["expected"] + all_predictions)
        for expected in TARGET_LABELS:
            writer.writerow([expected] + [confusion.get(expected, {}).get(pred, 0) for pred in all_predictions])

    return json_path, csv_path, confusion_path, per_class


def main():
    print("=" * 76)
    print("VoxGest static alphabet live evaluation")
    print("=" * 76)
    print(f"Model   : {STATIC_MODEL}")
    print(f"Labels  : {STATIC_LABELS}")
    print(f"Targets : {TARGET_LABELS}")
    print(f"Samples : {SAMPLES_PER_LABEL} per label")
    print("Protocol: record each target in order; vary angle/distance slightly.")
    print()

    model = tf.keras.models.load_model(STATIC_MODEL)
    idx_to_label = load_label_maps(STATIC_LABELS)
    records = []
    target_idx = 0
    label_counts = defaultdict(int)
    recording = False
    frames_since_capture = CAPTURE_STRIDE
    last_pred = ""

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
        while cap.isOpened() and target_idx < len(TARGET_LABELS):
            ok, frame = cap.read()
            if not ok:
                break

            frame = cv2.flip(frame, 1)
            rgb = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
            results = holistic.process(rgb)
            for hand in (results.right_hand_landmarks, results.left_hand_landmarks):
                if hand:
                    mp_draw.draw_landmarks(frame, hand, mp_holistic.HAND_CONNECTIONS)

            expected = TARGET_LABELS[target_idx]
            if recording:
                frames_since_capture += 1
                if frames_since_capture >= CAPTURE_STRIDE:
                    predicted, conf, note = predict_static(model, idx_to_label, results)
                    if expected != "nothing" and predicted == "nothing":
                        last_pred = "no hand; not counted"
                    else:
                        label_counts[expected] += 1
                        records.append(
                            {
                                "expected": expected,
                                "predicted": predicted,
                                "confidence": round(float(conf), 6),
                                "note": note,
                                "captured_at": datetime.now().isoformat(timespec="seconds"),
                            }
                        )
                        last_pred = f"{predicted} {conf:.0%}"
                        frames_since_capture = 0
                    if label_counts[expected] >= SAMPLES_PER_LABEL:
                        recording = False
                        target_idx += 1
                        frames_since_capture = CAPTURE_STRIDE
                        last_pred = ""

            draw(frame, expected, label_counts[expected], recording, last_pred)
            cv2.imshow("VoxGest Static Alphabet Eval", frame)
            key = cv2.waitKey(1) & 0xFF
            if key == ord("q"):
                break
            if key == ord(" "):
                recording = not recording
                frames_since_capture = CAPTURE_STRIDE
            elif key == ord("n"):
                recording = False
                target_idx += 1
                frames_since_capture = CAPTURE_STRIDE

    cap.release()
    cv2.destroyAllWindows()

    json_path, csv_path, confusion_path, per_class = write_reports(records)
    print(f"\nWrote: {json_path}")
    print(f"Wrote: {csv_path}")
    print(f"Wrote: {confusion_path}")
    print("\nPer-class accuracy:")
    for row in per_class:
        print(f"  {row['label']:<8} {row['accuracy']:>6.2f}% ({row['correct']}/{row['total']})")


if __name__ == "__main__":
    main()
