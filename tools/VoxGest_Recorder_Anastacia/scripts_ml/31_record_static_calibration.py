"""Record local webcam calibration samples for static alphabet signs.

Samples are normalized 63-float hand landmark vectors compatible with the
static alphabet model contract.
"""

import json
import os
import sys
import time
from pathlib import Path

import cv2
import mediapipe as mp
import numpy as np

from lstm_features import normalize_static_hand, select_hand_landmarks


os.environ["PROTOCOL_BUFFERS_PYTHON_IMPLEMENTATION"] = "python"
os.environ["TF_CPP_MIN_LOG_LEVEL"] = "3"

ROOT = Path(__file__).resolve().parents[1]
SAVE_DIR = Path(os.environ.get("VOXGEST_STATIC_CALIBRATION_DATASET", ROOT / "dataset_static_calibration"))
METADATA_PATH = SAVE_DIR / "metadata_static_calibration_v1.json"
SAMPLES_PER_LABEL = int(os.environ.get("VOXGEST_STATIC_CALIBRATION_SAMPLES_PER_LABEL", "60"))
CAPTURE_STRIDE = int(os.environ.get("VOXGEST_STATIC_CALIBRATION_STRIDE_FRAMES", "6"))
MIRROR_INPUT = True
LABELS = list("ABCDEFGHIJKLMNOPQRSTUVWXYZ") + ["del", "space", "nothing"]


def normalize_label(label):
    raw = str(label).strip()
    if len(raw) == 1 and raw.isalpha():
        return raw.upper()
    return raw.lower()


def load_metadata():
    if METADATA_PATH.exists():
        with open(METADATA_PATH, "r", encoding="utf-8") as f:
            return json.load(f)
    return {
        "version": 1,
        "feature_size": 63,
        "mirror_input": MIRROR_INPUT,
        "samples": {},
    }


def save_metadata(metadata):
    SAVE_DIR.mkdir(parents=True, exist_ok=True)
    with open(METADATA_PATH, "w", encoding="utf-8") as f:
        json.dump(metadata, f, indent=2)


def existing_count(label):
    label_dir = SAVE_DIR / label
    if not label_dir.exists():
        return 0
    return sum(1 for _ in label_dir.glob("manual_*.npy"))


def extract_static_vector(results, label):
    hand = select_hand_landmarks(results, mirrored_input=MIRROR_INPUT)
    if hand is None:
        if label == "nothing":
            return np.zeros((63,), dtype=np.float32), "no_hand_zero"
        return None, "no_hand"
    return normalize_static_hand(hand.landmark), "hand"


def draw(frame, label, saved, target, recording, note):
    h, w = frame.shape[:2]
    overlay = frame.copy()
    cv2.rectangle(overlay, (0, 0), (w, 145), (10, 10, 10), -1)
    cv2.addWeighted(overlay, 0.72, frame, 0.28, 0, frame)
    color = (0, 255, 150) if recording else (130, 130, 130)
    cv2.putText(frame, f"CALIBRATE: {label}", (15, 38), cv2.FONT_HERSHEY_SIMPLEX, 0.95, color, 2)
    cv2.putText(
        frame,
        f"saved {saved}/{target}   {note}",
        (15, 78),
        cv2.FONT_HERSHEY_SIMPLEX,
        0.62,
        (230, 230, 230),
        1,
    )
    cv2.putText(
        frame,
        "SPACE=start/pause  N=next label  Q=quit",
        (15, 116),
        cv2.FONT_HERSHEY_SIMPLEX,
        0.52,
        (180, 180, 180),
        1,
    )
    if label == "nothing":
        cv2.putText(
            frame,
            "Record idle/no hand, neutral hand, and no-output transitions.",
            (15, h - 18),
            cv2.FONT_HERSHEY_SIMPLEX,
            0.48,
            (210, 210, 210),
            1,
        )


def record_label(label, holistic, metadata):
    label_dir = SAVE_DIR / label
    label_dir.mkdir(parents=True, exist_ok=True)
    already = existing_count(label)
    target = already + SAMPLES_PER_LABEL
    source_stamp = time.strftime("manual_%Y%m%d_%H%M%S")
    source_id = f"{label}/{source_stamp}"

    cap = cv2.VideoCapture(0)
    cap.set(cv2.CAP_PROP_FRAME_WIDTH, 640)
    cap.set(cv2.CAP_PROP_FRAME_HEIGHT, 480)

    recording = False
    saved = already
    frames_since_save = CAPTURE_STRIDE
    note = ""
    mp_draw = mp.solutions.drawing_utils

    while cap.isOpened() and saved < target:
        ok, frame = cap.read()
        if not ok:
            break
        frame = cv2.flip(frame, 1)
        rgb = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
        results = holistic.process(rgb)
        for hand in (results.right_hand_landmarks, results.left_hand_landmarks):
            if hand:
                mp_draw.draw_landmarks(frame, hand, mp.solutions.holistic.HAND_CONNECTIONS)

        if recording:
            frames_since_save += 1
            if frames_since_save >= CAPTURE_STRIDE:
                vec, note = extract_static_vector(results, label)
                if vec is not None:
                    file_name = f"{source_stamp}_seq{saved:04d}.npy"
                    np.save(label_dir / file_name, vec.astype(np.float32))
                    metadata["samples"][f"{label}/{file_name}"] = {
                        "label": label,
                        "source_id": source_id,
                        "source": "manual_webcam",
                        "shape": [63],
                        "mirrored_input": MIRROR_INPUT,
                        "capture_note": note,
                    }
                    saved += 1
                    frames_since_save = 0

        draw(frame, label, saved - already, SAMPLES_PER_LABEL, recording, note)
        cv2.imshow(f"Static calibration: {label}", frame)
        key = cv2.waitKey(1) & 0xFF
        if key == ord(" "):
            recording = not recording
            frames_since_save = CAPTURE_STRIDE
        elif key == ord("n"):
            break
        elif key == ord("q"):
            cap.release()
            cv2.destroyAllWindows()
            save_metadata(metadata)
            raise KeyboardInterrupt

    cap.release()
    cv2.destroyAllWindows()
    save_metadata(metadata)
    print(f"{label}: saved {saved - already} new samples")


def main():
    labels = [normalize_label(item) for item in sys.argv[1:]] or LABELS
    invalid = [label for label in labels if label not in LABELS]
    if invalid:
        print(f"Unknown static labels: {invalid}")
        print(f"Allowed: {LABELS}")
        return

    print("=" * 76)
    print("VoxGest static calibration recorder")
    print("=" * 76)
    print(f"Dataset : {SAVE_DIR}")
    print(f"Labels  : {labels}")
    print(f"Samples : {SAMPLES_PER_LABEL} new samples per label")
    print()

    SAVE_DIR.mkdir(parents=True, exist_ok=True)
    metadata = load_metadata()
    try:
        with mp.solutions.holistic.Holistic(
            min_detection_confidence=0.45,
            min_tracking_confidence=0.40,
            model_complexity=1,
        ) as holistic:
            for label in labels:
                record_label(label, holistic, metadata)
    except KeyboardInterrupt:
        print("\nStopped early.")
    finally:
        save_metadata(metadata)

    print("\nRetrain static v4 when enough labels have calibration data:")
    print("  python scripts_ml/32_train_static_landmark_v4.py")


if __name__ == "__main__":
    main()
