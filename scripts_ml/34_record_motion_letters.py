"""Record dynamic alphabet letter samples for J/Z.

This captures 30x162 holistic motion windows, matching the word-motion feature
contract, but saves them into a separate motion-letter dataset.
"""

import json
import os
import sys
import time
from collections import deque
from pathlib import Path

import cv2
import mediapipe as mp
import numpy as np

from lstm_features import (
    FEAT_SIZE,
    SEQ_LEN,
    configured_hand_preference,
    configured_mirror_input,
    extract_frame_features,
    hand_is_present,
    single_hand_pose_enabled,
)
from motion_letter_config import MOTION_LETTER_LABELS, normalize_motion_letter


os.environ["PROTOCOL_BUFFERS_PYTHON_IMPLEMENTATION"] = "python"
os.environ["TF_CPP_MIN_LOG_LEVEL"] = "3"

ROOT = Path(__file__).resolve().parents[1]
SAVE_DIR = Path(os.environ.get("VOXGEST_MOTION_LETTER_DATASET", ROOT / "dataset_motion_letters"))
METADATA_PATH = SAVE_DIR / "metadata_motion_letters_v1.json"

TARGET_SEQUENCES_PER_LABEL = int(os.environ.get("VOXGEST_MOTION_LETTER_SEQUENCES_PER_LABEL", "60"))
STRIDE_FRAMES = int(os.environ.get("VOXGEST_MOTION_LETTER_STRIDE_FRAMES", "8"))
MIN_HAND_FRAMES = int(os.environ.get("VOXGEST_MOTION_LETTER_MIN_HAND_FRAMES", "12"))
HAND_PREFERENCE = configured_hand_preference()
MIRROR_INPUT = True


def load_metadata():
    if METADATA_PATH.exists():
        with open(METADATA_PATH, "r", encoding="utf-8") as f:
            return json.load(f)
    return {
        "version": 1,
        "seq_len": SEQ_LEN,
        "feature_size": FEAT_SIZE,
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


def draw_status(frame, label, recording, saved, target, buffer_len, hand_frames):
    height, width = frame.shape[:2]
    overlay = frame.copy()
    cv2.rectangle(overlay, (0, 0), (width, 140), (10, 10, 10), -1)
    cv2.addWeighted(overlay, 0.72, frame, 0.28, 0, frame)

    status = "REC" if recording else "PAUSED"
    color = (0, 255, 150) if recording else (120, 120, 120)
    cv2.putText(frame, f"MOTION LETTER {label}  {status}", (15, 38), cv2.FONT_HERSHEY_SIMPLEX, 0.85, color, 2)

    progress = min(1.0, saved / max(1, target))
    bar_w = int(progress * (width - 30))
    cv2.rectangle(frame, (15, 54), (width - 15, 72), (50, 50, 50), -1)
    cv2.rectangle(frame, (15, 54), (15 + bar_w, 72), color, -1)
    cv2.putText(
        frame,
        f"saved {saved}/{target}   buffer {buffer_len}/{SEQ_LEN}   hand frames {hand_frames}",
        (15, 102),
        cv2.FONT_HERSHEY_SIMPLEX,
        0.52,
        (220, 220, 220),
        1,
    )
    cv2.putText(
        frame,
        "SPACE=start/pause  N=next  Q=quit",
        (15, height - 18),
        cv2.FONT_HERSHEY_SIMPLEX,
        0.50,
        (180, 180, 180),
        1,
    )


def record_label(label, holistic, metadata):
    label_dir = SAVE_DIR / label
    label_dir.mkdir(parents=True, exist_ok=True)
    already = existing_count(label)
    target = already + TARGET_SEQUENCES_PER_LABEL
    source_stamp = time.strftime("manual_%Y%m%d_%H%M%S")
    source_id = f"{label}/{source_stamp}"

    print(f"\nRecording motion letter {label}")
    if label == "NOTHING":
        print("  Record idle, hand entering/leaving, partial J/Z starts, and accidental movement.")
    else:
        print("  Draw the full motion naturally, including start and finish positions.")
        print("  Vary speed, size, distance, and slight angle.")

    cap = cv2.VideoCapture(0)
    cap.set(cv2.CAP_PROP_FRAME_WIDTH, 640)
    cap.set(cv2.CAP_PROP_FRAME_HEIGHT, 480)

    recording = False
    saved = already
    frame_buffer = deque(maxlen=SEQ_LEN)
    hand_buffer = deque(maxlen=SEQ_LEN)
    frames_since_save = STRIDE_FRAMES

    while cap.isOpened():
        ok, frame = cap.read()
        if not ok:
            break

        frame = cv2.flip(frame, 1)
        rgb = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
        results = holistic.process(rgb)

        if results.pose_landmarks:
            mp.solutions.drawing_utils.draw_landmarks(frame, results.pose_landmarks, mp.solutions.holistic.POSE_CONNECTIONS)
        for hand_landmarks in (results.right_hand_landmarks, results.left_hand_landmarks):
            if hand_landmarks:
                mp.solutions.drawing_utils.draw_landmarks(frame, hand_landmarks, mp.solutions.holistic.HAND_CONNECTIONS)

        hand_present = hand_is_present(results, mirrored_input=MIRROR_INPUT)
        vec = extract_frame_features(results, mirrored_input=MIRROR_INPUT)
        if recording and vec is not None:
            frame_buffer.append(vec)
            hand_buffer.append(hand_present)
            frames_since_save += 1
            if len(frame_buffer) == SEQ_LEN and frames_since_save >= STRIDE_FRAMES:
                hand_frames = sum(1 for item in hand_buffer if item)
                if label == "NOTHING" or hand_frames >= MIN_HAND_FRAMES:
                    seq = np.array(list(frame_buffer), dtype=np.float32)
                    file_name = f"{source_stamp}_seq{saved:04d}.npy"
                    np.save(label_dir / file_name, seq)
                    metadata["samples"][f"{label}/{file_name}"] = {
                        "label": label,
                        "source_id": source_id,
                        "source_video": "manual_webcam",
                        "augment_index": 0,
                        "shape": [SEQ_LEN, FEAT_SIZE],
                        "dominant_hand": HAND_PREFERENCE,
                        "single_hand_pose": single_hand_pose_enabled(),
                        "mirrored_input": MIRROR_INPUT,
                    }
                    saved += 1
                    frames_since_save = 0

        draw_status(
            frame,
            label,
            recording,
            saved - already,
            TARGET_SEQUENCES_PER_LABEL,
            len(frame_buffer),
            sum(1 for item in hand_buffer if item),
        )
        cv2.imshow(f"Record motion letter: {label}", frame)
        key = cv2.waitKey(1) & 0xFF
        if key == ord(" "):
            recording = not recording
            if recording:
                frame_buffer.clear()
                hand_buffer.clear()
                frames_since_save = STRIDE_FRAMES
        elif key == ord("n"):
            break
        elif key == ord("q"):
            cap.release()
            cv2.destroyAllWindows()
            save_metadata(metadata)
            raise KeyboardInterrupt
        if saved >= target:
            break

    cap.release()
    cv2.destroyAllWindows()
    save_metadata(metadata)
    print(f"  Saved new sequences for {label}: {saved - already}")


def main():
    labels = [normalize_motion_letter(item) for item in sys.argv[1:]] or MOTION_LETTER_LABELS
    invalid = [label for label in labels if label not in MOTION_LETTER_LABELS]
    if invalid:
        print(f"Unknown motion-letter labels: {invalid}")
        print(f"Allowed: {MOTION_LETTER_LABELS}")
        return

    print("=" * 70)
    print("  VoxGest Motion Letter Recorder")
    print("=" * 70)
    print(f"  Dataset: {SAVE_DIR}")
    print(f"  Labels : {labels}")
    print(f"  Target : {TARGET_SEQUENCES_PER_LABEL} new sequences per label")
    print(f"  Hand   : {HAND_PREFERENCE}")
    print(f"  Pose   : {'single-hand' if single_hand_pose_enabled() else 'full-pose'}")
    print(f"  Mirror : {configured_mirror_input(MIRROR_INPUT)}")
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

    print("\nTrain motion letters with:")
    print("  python scripts_ml/35_train_motion_letter_tcn.py")


if __name__ == "__main__":
    main()
