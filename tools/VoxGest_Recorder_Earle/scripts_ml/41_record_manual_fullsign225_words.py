"""Hands-free manual webcam recorder for FullSign225 manual16 words.

This records 30-frame x 225-feature webcam sequences into a separate
experimental dataset:

    external_datasets/fullsign225_manual16_features

It does not write to demo10, onehand162, sprint30, or fullsign225_team16.
"""

import json
import os
import sys
import time
from datetime import datetime
from pathlib import Path

os.environ.setdefault("VOXGEST_WORD_PROFILE", "fullsign225_manual16")
os.environ.setdefault("VOXGEST_FEATURE_PROFILE", "fullsign225")
os.environ.setdefault("VOXGEST_SINGLE_HAND_POSE", "0")
os.environ.setdefault("PROTOCOL_BUFFERS_PYTHON_IMPLEMENTATION", "python")
os.environ.setdefault("TF_CPP_MIN_LOG_LEVEL", "3")

import cv2
import mediapipe as mp
import numpy as np

from frame_quality_gate import (
    BAD_SEQUENCE,
    LOW_HAND_PRESENCE,
    NO_HAND,
    capture_frame_quality,
    evaluate_sequence_quality,
)
from lstm_features import (
    FEAT_SIZE,
    SEQ_LEN,
    configured_feature_profile,
    configured_mirror_input,
    extract_frame_features,
    sequence_hand_presence_ratio,
    sequence_motion_energy,
    sequence_wrist_path,
)
from word_config import RECORDABLE_WORDS, WORD_PROFILE


ROOT = Path(__file__).resolve().parents[1]
SAVE_DIR = Path(
    os.environ.get(
        "VOXGEST_FULLSIGN225_MANUAL16_DATASET",
        ROOT / "external_datasets" / "fullsign225_manual16_features",
    )
)
METADATA_PATH = SAVE_DIR / "metadata_lstm_v2.json"
REJECT_DIR = SAVE_DIR / "_rejected"
REJECT_LOG = SAVE_DIR / "rejected_samples.jsonl"

TARGET_SEQUENCES_PER_WORD = int(os.environ.get("VOXGEST_MANUAL_SEQUENCES_PER_WORD", "30"))
COUNTDOWN_SECONDS = float(os.environ.get("VOXGEST_MANUAL_COUNTDOWN_SECONDS", "3"))
REST_SECONDS = float(os.environ.get("VOXGEST_MANUAL_REST_SECONDS", "1.2"))
MAX_CAPTURE_SECONDS = float(os.environ.get("VOXGEST_MANUAL_CAPTURE_TIMEOUT_SECONDS", "8"))
MIN_HAND_PRESENCE = float(os.environ.get("VOXGEST_MANUAL_MIN_HAND_PRESENCE", "0.35"))
MIRROR_INPUT = True
REJECT_STATUSES = {BAD_SEQUENCE, LOW_HAND_PRESENCE, NO_HAND}


def load_metadata():
    if METADATA_PATH.exists():
        with open(METADATA_PATH, "r", encoding="utf-8") as f:
            return json.load(f)
    return {
        "version": 2,
        "profile": "fullsign225_manual16",
        "seq_len": SEQ_LEN,
        "feature_size": FEAT_SIZE,
        "feature_profile": configured_feature_profile(),
        "source_dataset": "manual_webcam",
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
    return sum(1 for path in word_dir.glob("manual_fullsign225_*.npy"))


def draw_overlay(frame, word, sample_no, target, phase, message, seconds_left=None):
    height, width = frame.shape[:2]
    overlay = frame.copy()
    cv2.rectangle(overlay, (0, 0), (width, 145), (10, 10, 10), -1)
    cv2.addWeighted(overlay, 0.72, frame, 0.28, 0, frame)
    cv2.putText(frame, f"{word}  {sample_no}/{target}", (15, 38), cv2.FONT_HERSHEY_SIMPLEX, 0.95, (0, 255, 150), 2)
    cv2.putText(frame, phase, (15, 78), cv2.FONT_HERSHEY_SIMPLEX, 0.70, (230, 230, 230), 2)
    detail = message
    if seconds_left is not None:
        detail = f"{message}  {seconds_left:.1f}s"
    cv2.putText(frame, detail, (15, 112), cv2.FONT_HERSHEY_SIMPLEX, 0.55, (210, 210, 210), 1)
    cv2.putText(frame, "Q/ESC quit  S skip word", (15, height - 18), cv2.FONT_HERSHEY_SIMPLEX, 0.50, (180, 180, 180), 1)


def draw_landmarks(frame, results, holistic, draw_utils):
    if results.pose_landmarks:
        draw_utils.draw_landmarks(frame, results.pose_landmarks, holistic.POSE_CONNECTIONS)
    for hand in (results.left_hand_landmarks, results.right_hand_landmarks):
        if hand:
            draw_utils.draw_landmarks(frame, hand, holistic.HAND_CONNECTIONS)


def show_timed_phase(cap, holistic, draw_utils, word, sample_no, target, phase, message, seconds):
    start = time.time()
    while time.time() - start < seconds:
        ok, frame = cap.read()
        if not ok:
            return "quit"
        frame = cv2.flip(frame, 1)
        rgb = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
        results = holistic.process(rgb)
        draw_landmarks(frame, results, mp.solutions.holistic, draw_utils)
        draw_overlay(frame, word, sample_no, target, phase, message, seconds - (time.time() - start))
        cv2.imshow("VoxGest FullSign225 Manual Recorder", frame)
        key = cv2.waitKey(1) & 0xFF
        if key in {27, ord("q"), ord("Q")}:
            return "quit"
        if key in {ord("s"), ord("S")}:
            return "skip"
    return "ok"


def capture_sequence(cap, holistic, draw_utils, word, sample_no, target):
    frames = []
    quality_records = []
    start = time.time()
    missing_pose_frames = 0
    last_frame = None

    while len(frames) < SEQ_LEN and time.time() - start < MAX_CAPTURE_SECONDS:
        ok, frame = cap.read()
        if not ok:
            return None, [], "camera_read_failed", last_frame

        frame = cv2.flip(frame, 1)
        rgb = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
        results = holistic.process(rgb)
        draw_landmarks(frame, results, mp.solutions.holistic, draw_utils)

        vec = extract_frame_features(results, mirrored_input=MIRROR_INPUT)
        if vec is None:
            missing_pose_frames += 1
        else:
            missing_pose_frames = 0
            frames.append(vec)
            quality_records.append(capture_frame_quality(results, mirrored_input=MIRROR_INPUT))

        draw_overlay(
            frame,
            word,
            sample_no,
            target,
            "RECORDING",
            f"captured {len(frames)}/{SEQ_LEN}",
        )
        cv2.imshow("VoxGest FullSign225 Manual Recorder", frame)
        last_frame = frame
        key = cv2.waitKey(1) & 0xFF
        if key in {27, ord("q"), ord("Q")}:
            return None, quality_records, "quit", last_frame
        if missing_pose_frames >= 12:
            return None, quality_records, "pose_missing", last_frame

    if len(frames) < SEQ_LEN:
        return None, quality_records, f"timeout_frames={len(frames)}", last_frame

    seq = np.array(frames[:SEQ_LEN], dtype=np.float32)
    if seq.shape != (SEQ_LEN, FEAT_SIZE):
        return None, quality_records, f"bad_shape={seq.shape}", last_frame
    return seq, quality_records[:SEQ_LEN], "ok", last_frame


def quality_decision(seq, quality_records):
    if seq is None:
        return False, "BAD_SEQUENCE", "missing sequence", {}
    quality = evaluate_sequence_quality(seq, quality_records)
    hand_presence = sequence_hand_presence_ratio(seq)
    metrics = {
        "hand_presence": round(float(hand_presence), 6),
        "motion": round(float(sequence_motion_energy(seq)), 6),
        "wrist_path": round(float(sequence_wrist_path(seq)), 6),
        "quality_metrics": quality.metrics,
    }
    if hand_presence < MIN_HAND_PRESENCE:
        return False, LOW_HAND_PRESENCE, f"hand_presence<{MIN_HAND_PRESENCE:.2f}", metrics
    if quality.status in REJECT_STATUSES:
        return False, quality.status, quality.reason, metrics
    if quality.status != "GOOD":
        return True, quality.status, f"warning:{quality.reason}", metrics
    return True, quality.status, "ok", metrics


def append_reject(record):
    SAVE_DIR.mkdir(parents=True, exist_ok=True)
    with open(REJECT_LOG, "a", encoding="utf-8") as f:
        f.write(json.dumps(record) + "\n")


def record_word(word, cap, holistic, draw_utils, metadata):
    word_dir = SAVE_DIR / word
    word_dir.mkdir(parents=True, exist_ok=True)
    (REJECT_DIR / word).mkdir(parents=True, exist_ok=True)

    existing = existing_count(word)
    accepted = 0
    attempts = 0
    source_stamp = datetime.now().strftime("manual_fullsign225_%Y%m%d_%H%M%S")
    source_id = f"{word}/{source_stamp}"

    print(f"\nRecording {word}")
    print(f"  Existing accepted samples: {existing}")
    print(f"  Target new samples       : {TARGET_SEQUENCES_PER_WORD}")
    print("  Hands-free mode: countdown -> record 30 frames -> rest -> next sample")

    while accepted < TARGET_SEQUENCES_PER_WORD:
        sample_no = accepted + 1
        phase = show_timed_phase(
            cap,
            holistic,
            draw_utils,
            word,
            sample_no,
            TARGET_SEQUENCES_PER_WORD,
            "COUNTDOWN",
            "Get ready",
            COUNTDOWN_SECONDS,
        )
        if phase == "quit":
            raise KeyboardInterrupt
        if phase == "skip":
            break

        attempts += 1
        seq, quality_records, capture_reason, _ = capture_sequence(cap, holistic, draw_utils, word, sample_no, TARGET_SEQUENCES_PER_WORD)
        ok, status, reason, metrics = quality_decision(seq, quality_records)
        stamp = datetime.now().strftime("%Y%m%d_%H%M%S_%f")

        if ok:
            file_name = f"{source_stamp}_seq{existing + accepted + 1:04d}.npy"
            out_path = word_dir / file_name
            np.save(out_path, seq.astype(np.float32))
            metadata["samples"][f"{word}/{file_name}"] = {
                "word": word,
                "source_id": source_id,
                "source_video": "manual_webcam_fullsign225",
                "capture_attempt": attempts,
                "augment_index": 0,
                "shape": [SEQ_LEN, FEAT_SIZE],
                "feature_profile": configured_feature_profile(),
                "dominant_hand": "both_fixed_slots",
                "single_hand_pose": False,
                "mirrored_input": MIRROR_INPUT,
                "quality_status": status,
                "quality_reason": reason,
                **metrics,
            }
            accepted += 1
            print(f"  ACCEPT {word} {accepted}/{TARGET_SEQUENCES_PER_WORD} status={status} reason={reason} -> {out_path}", flush=True)
        else:
            reject_name = f"reject_{word}_{stamp}.npy"
            reject_path = REJECT_DIR / word / reject_name
            if seq is not None:
                np.save(reject_path, seq.astype(np.float32))
            record = {
                "timestamp": datetime.now().isoformat(timespec="seconds"),
                "word": word,
                "capture_reason": capture_reason,
                "quality_status": status,
                "quality_reason": reason,
                "saved_reject": str(reject_path) if seq is not None else "",
                **metrics,
            }
            append_reject(record)
            print(f"  REJECT {word} attempt={attempts} status={status} reason={reason}", flush=True)

        save_metadata(metadata)
        phase = show_timed_phase(
            cap,
            holistic,
            draw_utils,
            word,
            accepted + 1,
            TARGET_SEQUENCES_PER_WORD,
            "REST",
            "Relax hands",
            REST_SECONDS,
        )
        if phase == "quit":
            raise KeyboardInterrupt
        if phase == "skip":
            break

    print(f"  Done {word}: accepted={accepted} attempts={attempts}")


def main():
    if configured_feature_profile() != "fullsign225" or FEAT_SIZE != 225:
        raise RuntimeError(f"Expected fullsign225/225 features, got {configured_feature_profile()}/{FEAT_SIZE}")
    words = [word.upper() for word in sys.argv[1:]] or [word for word in RECORDABLE_WORDS]
    invalid = [word for word in words if word not in RECORDABLE_WORDS]
    if invalid:
        print(f"Unknown words: {invalid}")
        print(f"Allowed: {RECORDABLE_WORDS}")
        return

    SAVE_DIR.mkdir(parents=True, exist_ok=True)
    metadata = load_metadata()

    print("=" * 72)
    print("VoxGest FullSign225 Manual16 Webcam Recorder")
    print("=" * 72)
    print(f"Profile : {WORD_PROFILE}")
    print(f"Dataset : {SAVE_DIR}")
    print(f"Feature : {configured_feature_profile()} ({FEAT_SIZE} floats/frame)")
    print(f"Shape   : ({SEQ_LEN}, {FEAT_SIZE})")
    print(f"Words   : {words}")
    print(f"Target  : {TARGET_SEQUENCES_PER_WORD} accepted samples per word")
    print(f"Mirror  : {configured_mirror_input(MIRROR_INPUT)}")
    print("Keys    : Q/ESC quit, S skip current word")

    cap = cv2.VideoCapture(0)
    cap.set(cv2.CAP_PROP_FRAME_WIDTH, 640)
    cap.set(cv2.CAP_PROP_FRAME_HEIGHT, 480)
    draw_utils = mp.solutions.drawing_utils

    try:
        with mp.solutions.holistic.Holistic(
            min_detection_confidence=0.45,
            min_tracking_confidence=0.40,
            model_complexity=1,
        ) as holistic:
            for word in words:
                record_word(word, cap, holistic, draw_utils, metadata)
    except KeyboardInterrupt:
        print("\nStopped. Partial metadata saved.")
    finally:
        save_metadata(metadata)
        cap.release()
        cv2.destroyAllWindows()

    print("\nNext audit:")
    print("  .\\voxgest_env\\Scripts\\python.exe scripts_ml\\42_audit_fullsign225_manual16_dataset.py")


if __name__ == "__main__":
    main()
