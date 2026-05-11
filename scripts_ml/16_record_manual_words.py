"""
Manual LSTM word recorder.

Records real webcam examples as 30x162 holistic sequences. This is the best
way to make the system target words work for your camera, your signer, and your
defense environment.

Run all target words:
  python scripts_ml/16_record_manual_words.py

Run only selected words:
  python scripts_ml/16_record_manual_words.py HELP WATER YES

Record no-word/open-hand examples:
  python scripts_ml/16_record_manual_words.py NOTHING
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
from word_config import RECORDABLE_WORDS, TARGET_WORDS, WORD_PROFILE

os.environ["PROTOCOL_BUFFERS_PYTHON_IMPLEMENTATION"] = "python"
os.environ["TF_CPP_MIN_LOG_LEVEL"] = "3"

ROOT = Path(__file__).resolve().parents[1]
SAVE_DIR = Path(os.environ.get("VOXGEST_LSTM_DATASET", ROOT / "dataset_words_lstm"))
METADATA_PATH = SAVE_DIR / "metadata_lstm_v2.json"

TARGET_SEQUENCES_PER_WORD = int(os.environ.get("VOXGEST_MANUAL_SEQUENCES_PER_WORD", "60"))
STRIDE_FRAMES = int(os.environ.get("VOXGEST_MANUAL_STRIDE_FRAMES", "10"))
MIN_HAND_FRAMES = int(os.environ.get("VOXGEST_MANUAL_MIN_HAND_FRAMES", "12"))
HAND_PREFERENCE = configured_hand_preference()
MIRROR_INPUT = True


def load_metadata():
    if METADATA_PATH.exists():
        with open(METADATA_PATH, "r", encoding="utf-8") as f:
            return json.load(f)
    return {
        "version": 2,
        "seq_len": SEQ_LEN,
        "feature_size": FEAT_SIZE,
        "samples": {},
    }


def save_metadata(metadata):
    SAVE_DIR.mkdir(parents=True, exist_ok=True)
    with open(METADATA_PATH, "w", encoding="utf-8") as f:
        json.dump(metadata, f, indent=2)


def existing_count(word):
    word_dir = SAVE_DIR / word
    if not word_dir.exists():
        return 0
    return sum(1 for p in word_dir.glob("manual_*.npy"))


def draw_status(frame, word, recording, saved, target, buffer_len, hand_frames):
    height, width = frame.shape[:2]
    overlay = frame.copy()
    cv2.rectangle(overlay, (0, 0), (width, 132), (10, 10, 10), -1)
    cv2.addWeighted(overlay, 0.72, frame, 0.28, 0, frame)

    status = "REC" if recording else "PAUSED"
    color = (0, 255, 150) if recording else (120, 120, 120)
    cv2.putText(frame, f"{word}  {status}", (15, 38), cv2.FONT_HERSHEY_SIMPLEX, 0.95, color, 2)

    progress = min(1.0, saved / max(1, target))
    bar_w = int(progress * (width - 30))
    cv2.rectangle(frame, (15, 52), (width - 15, 70), (50, 50, 50), -1)
    cv2.rectangle(frame, (15, 52), (15 + bar_w, 70), color, -1)
    cv2.putText(
        frame,
        f"saved {saved}/{target}   buffer {buffer_len}/{SEQ_LEN}   hand frames {hand_frames}",
        (15, 98),
        cv2.FONT_HERSHEY_SIMPLEX,
        0.55,
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


def record_word(word, holistic, metadata):
    word = word.upper()
    word_dir = SAVE_DIR / word
    word_dir.mkdir(parents=True, exist_ok=True)

    already = existing_count(word)
    target = already + TARGET_SEQUENCES_PER_WORD
    source_stamp = time.strftime("manual_%Y%m%d_%H%M%S")
    source_id = f"{word}/{source_stamp}"

    print(f"\nRecording {word}")
    print(f"  Existing manual sequences: {already}")
    print(f"  New target: {TARGET_SEQUENCES_PER_WORD}")
    if word == "NOTHING":
        print(
            "  Record hard negatives: idle hands, transitions, partial signs, "
            "aborted signs, hand entering/leaving frame, and natural pauses."
        )
        print("  NOTHING is a no-output class; it should never become a word token.")
    else:
        print("  Sign naturally. Vary distance, angle, and speed a little.")
        print("  Save partial/incomplete/transition movements as NOTHING, not as this word.")

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
                if hand_frames >= MIN_HAND_FRAMES:
                    seq = np.array(list(frame_buffer), dtype=np.float32)
                    file_name = f"{source_stamp}_seq{saved:04d}.npy"
                    out_path = word_dir / file_name
                    np.save(out_path, seq)
                    metadata["samples"][f"{word}/{file_name}"] = {
                        "word": word,
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
            word,
            recording,
            saved - already,
            TARGET_SEQUENCES_PER_WORD,
            len(frame_buffer),
            sum(1 for item in hand_buffer if item),
        )

        cv2.imshow(f"Record LSTM word: {word}", frame)
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
    print(f"  Saved new sequences for {word}: {saved - already}")


def main():
    words = [w.upper() for w in sys.argv[1:]] or TARGET_WORDS
    invalid = [w for w in words if w not in RECORDABLE_WORDS]
    if invalid:
        print(f"Unknown target words: {invalid}")
        print(f"Allowed: {RECORDABLE_WORDS}")
        return

    print("=" * 62)
    print("  VoxGest Manual LSTM Word Recorder")
    print("=" * 62)
    print(f"  Dataset: {SAVE_DIR}")
    print(f"  Profile: {WORD_PROFILE}")
    print(f"  Words  : {words}")
    print(f"  Target : {TARGET_SEQUENCES_PER_WORD} new sequences per word")
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
            for word in words:
                input(f"Press ENTER when ready to record {word}...")
                record_word(word, holistic, metadata)
    except KeyboardInterrupt:
        print("\nStopped.")

    save_metadata(metadata)
    print("\nDone. Retrain with:")
    print("  python scripts_ml/19_train_lstm.py")


if __name__ == "__main__":
    main()
