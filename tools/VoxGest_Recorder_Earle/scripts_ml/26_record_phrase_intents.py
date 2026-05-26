"""
Endpoint-based phrase-intent recorder.

Phrase intents are longer than word signs. This recorder captures complete
motion segments by waiting for a start motion and a final hold before saving a
60x162 sequence. The NOTHING class uses timed windows so idle/no-motion
examples can also be recorded.

Examples:
  python scripts_ml/26_record_phrase_intents.py ASK_NAME
  python scripts_ml/26_record_phrase_intents.py PARTIAL_ASK_NAME
  python scripts_ml/26_record_phrase_intents.py NOTHING
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

from gesture_segmenter import GestureSegmenter
from lstm_features import (
    FEAT_SIZE,
    configured_hand_preference,
    configured_mirror_input,
    extract_frame_features,
    single_hand_pose_enabled,
)
from phrase_config import (
    PHRASE_RECORDABLE_LABELS,
    PHRASE_SEQ_LEN,
    normalize_phrase_label,
    phrase_output_text,
)

os.environ["PROTOCOL_BUFFERS_PYTHON_IMPLEMENTATION"] = "python"
os.environ["TF_CPP_MIN_LOG_LEVEL"] = "3"

ROOT = Path(__file__).resolve().parents[1]
SAVE_DIR = Path(os.environ.get("VOXGEST_PHRASE_DATASET", ROOT / "dataset_phrase_intents"))
METADATA_PATH = SAVE_DIR / "metadata_phrase_v1.json"

TARGET_SEQUENCES_PER_LABEL = int(os.environ.get("VOXGEST_PHRASE_SEQUENCES_PER_LABEL", "60"))
TIMED_STRIDE_FRAMES = int(os.environ.get("VOXGEST_PHRASE_TIMED_STRIDE_FRAMES", "15"))
HAND_PREFERENCE = configured_hand_preference()
MIRROR_INPUT = True


def load_metadata():
    if METADATA_PATH.exists():
        with open(METADATA_PATH, "r", encoding="utf-8") as f:
            return json.load(f)
    return {
        "version": 1,
        "seq_len": PHRASE_SEQ_LEN,
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
    return sum(1 for path in label_dir.glob("manual_*.npy"))


def save_sequence(label, source_id, source_stamp, saved_idx, seq, metadata, capture_mode):
    label_dir = SAVE_DIR / label
    label_dir.mkdir(parents=True, exist_ok=True)
    file_name = f"{source_stamp}_seq{saved_idx:04d}.npy"
    out_path = label_dir / file_name
    np.save(out_path, np.asarray(seq, dtype=np.float32))
    metadata["samples"][f"{label}/{file_name}"] = {
        "label": label,
        "phrase_text": phrase_output_text(label),
        "source_id": source_id,
        "source_video": "manual_webcam",
        "capture_mode": capture_mode,
        "shape": [PHRASE_SEQ_LEN, FEAT_SIZE],
        "dominant_hand": HAND_PREFERENCE,
        "single_hand_pose": single_hand_pose_enabled(),
        "mirrored_input": MIRROR_INPUT,
    }


def draw_status(frame, label, recording, saved, target, capture_mode, segmenter, buffer_len):
    height, width = frame.shape[:2]
    overlay = frame.copy()
    cv2.rectangle(overlay, (0, 0), (width, 150), (10, 10, 10), -1)
    cv2.addWeighted(overlay, 0.72, frame, 0.28, 0, frame)

    status = "REC" if recording else "PAUSED"
    color = (0, 255, 150) if recording else (120, 120, 120)
    cv2.putText(
        frame,
        f"{label}  {status}",
        (15, 36),
        cv2.FONT_HERSHEY_SIMPLEX,
        0.92,
        color,
        2,
    )

    progress = min(1.0, saved / max(1, target))
    bar_w = int(progress * (width - 30))
    cv2.rectangle(frame, (15, 50), (width - 15, 68), (50, 50, 50), -1)
    cv2.rectangle(frame, (15, 50), (15 + bar_w, 68), color, -1)

    if capture_mode == "timed":
        debug = f"timed buffer {buffer_len}/{PHRASE_SEQ_LEN}"
    else:
        debug = segmenter.debug_text()
    cv2.putText(
        frame,
        f"saved {saved}/{target}  mode {capture_mode}  {debug}",
        (15, 96),
        cv2.FONT_HERSHEY_SIMPLEX,
        0.50,
        (220, 220, 220),
        1,
    )
    cv2.putText(
        frame,
        "Endpoint labels: full motion then final hold.  SPACE=start/pause  N=next  Q=quit",
        (15, height - 18),
        cv2.FONT_HERSHEY_SIMPLEX,
        0.45,
        (180, 180, 180),
        1,
    )


def capture_mode_for(label):
    return "timed" if label == "NOTHING" else "endpoint"


def record_label(label, holistic, metadata):
    label_dir = SAVE_DIR / label
    label_dir.mkdir(parents=True, exist_ok=True)

    already = existing_count(label)
    target = already + TARGET_SEQUENCES_PER_LABEL
    source_stamp = time.strftime("manual_%Y%m%d_%H%M%S")
    source_id = f"{label}/{source_stamp}"
    capture_mode = capture_mode_for(label)

    print(f"\nRecording {label}")
    print(f"  Output text : {phrase_output_text(label)}")
    print(f"  Capture mode: {capture_mode}")
    print(f"  Existing    : {already}")
    print(f"  New target  : {TARGET_SEQUENCES_PER_LABEL}")
    if capture_mode == "endpoint":
        print("  Perform the complete motion, then hold briefly at the end.")
    else:
        print("  Record idle, transitions, and no-phrase movement.")

    cap = cv2.VideoCapture(0)
    cap.set(cv2.CAP_PROP_FRAME_WIDTH, 640)
    cap.set(cv2.CAP_PROP_FRAME_HEIGHT, 480)

    recording = False
    saved = already
    segmenter = GestureSegmenter()
    timed_buffer = deque(maxlen=PHRASE_SEQ_LEN)
    frames_since_save = TIMED_STRIDE_FRAMES

    while cap.isOpened():
        ok, frame = cap.read()
        if not ok:
            break

        frame = cv2.flip(frame, 1)
        rgb = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
        results = holistic.process(rgb)

        if results.pose_landmarks:
            mp.solutions.drawing_utils.draw_landmarks(
                frame,
                results.pose_landmarks,
                mp.solutions.holistic.POSE_CONNECTIONS,
            )
        for hand_landmarks in (results.right_hand_landmarks, results.left_hand_landmarks):
            if hand_landmarks:
                mp.solutions.drawing_utils.draw_landmarks(
                    frame,
                    hand_landmarks,
                    mp.solutions.holistic.HAND_CONNECTIONS,
                )

        vec = extract_frame_features(results, mirrored_input=MIRROR_INPUT)
        if recording and vec is not None:
            if capture_mode == "endpoint":
                segment = segmenter.update(vec)
                if segment is not None:
                    save_sequence(
                        label,
                        source_id,
                        source_stamp,
                        saved,
                        segment,
                        metadata,
                        capture_mode,
                    )
                    saved += 1
            else:
                timed_buffer.append(vec)
                frames_since_save += 1
                if len(timed_buffer) == PHRASE_SEQ_LEN and frames_since_save >= TIMED_STRIDE_FRAMES:
                    seq = np.array(list(timed_buffer), dtype=np.float32)
                    save_sequence(
                        label,
                        source_id,
                        source_stamp,
                        saved,
                        seq,
                        metadata,
                        capture_mode,
                    )
                    saved += 1
                    frames_since_save = 0

        draw_status(
            frame,
            label,
            recording,
            saved - already,
            TARGET_SEQUENCES_PER_LABEL,
            capture_mode,
            segmenter,
            len(timed_buffer),
        )

        cv2.imshow(f"Record phrase intent: {label}", frame)
        key = cv2.waitKey(1) & 0xFF
        if key == ord(" "):
            recording = not recording
            if recording:
                segmenter.reset()
                timed_buffer.clear()
                frames_since_save = TIMED_STRIDE_FRAMES
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
    labels = [normalize_phrase_label(item) for item in sys.argv[1:]] or ["ASK_NAME"]
    invalid = [label for label in labels if label not in PHRASE_RECORDABLE_LABELS]
    if invalid:
        print(f"Unknown phrase labels: {invalid}")
        print(f"Allowed: {PHRASE_RECORDABLE_LABELS}")
        return

    print("=" * 72)
    print("  VoxGest Phrase-Intent Recorder | endpoint-based capture")
    print("=" * 72)
    print(f"  Dataset : {SAVE_DIR}")
    print(f"  Labels  : {labels}")
    print(f"  Shape   : {PHRASE_SEQ_LEN} x {FEAT_SIZE}")
    print(f"  Target  : {TARGET_SEQUENCES_PER_LABEL} new sequences per label")
    print(f"  Hand    : {HAND_PREFERENCE}")
    print(f"  Pose    : {'single-hand' if single_hand_pose_enabled() else 'full-pose'}")
    print(f"  Mirror  : {configured_mirror_input(MIRROR_INPUT)}")
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
                input(f"Press ENTER when ready to record {label}...")
                record_label(label, holistic, metadata)
    except KeyboardInterrupt:
        print("\nStopped.")

    save_metadata(metadata)
    print("\nDone. Retrain phrase model with:")
    print("  python scripts_ml/27_train_phrase_tcn.py")


if __name__ == "__main__":
    main()
