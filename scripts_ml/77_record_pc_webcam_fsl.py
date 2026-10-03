"""Record FSL 20x162 calibration JSON exports from a PC webcam."""

from __future__ import annotations

import argparse
import json
import re
import socket
import time
from datetime import datetime
from pathlib import Path

import numpy as np

from fsl_config import FSL_FEATURE_SIZE, FSL_LABELS, FSL_SEQUENCE_LENGTH


ROOT = Path(__file__).resolve().parents[1]
EXPORT_ROOT = ROOT / "external_datasets" / "fsl_phone_exports" / "VoxGestCalibration" / "fsl_phrase_v1"
POSE_LANDMARK_COUNT = 33
HAND_LANDMARK_COUNT = 21
MIN_WRIST_MCP_SCALE = 0.001
Z_DAMPING = 0.3
MIN_HAND_PRESENCE = 0.65
cv2 = None
mp = None


def require_video_dependencies() -> None:
    global cv2, mp
    if cv2 is not None and mp is not None:
        return
    try:
        import cv2 as cv2_module
        import mediapipe as mp_module
    except ModuleNotFoundError as exc:
        raise SystemExit(
            "Missing webcam dependency. Run with voxgest_env\\Scripts\\python.exe or install opencv-python and mediapipe."
        ) from exc
    cv2 = cv2_module
    mp = mp_module


def safe_name(value: object) -> str:
    text = re.sub(r"[^A-Za-z0-9_-]+", "_", str(value).strip())
    return text.strip("_") or "sample"


def landmarks_to_array(landmarks, count: int) -> np.ndarray | None:
    if landmarks is None or len(landmarks.landmark) != count:
        return None
    return np.asarray([[lm.x, lm.y, lm.z] for lm in landmarks.landmark], dtype=np.float32)


def build_frame_features(results) -> tuple[np.ndarray, bool, bool]:
    pose = landmarks_to_array(results.pose_landmarks, POSE_LANDMARK_COUNT)
    if pose is None:
        return np.zeros((FSL_FEATURE_SIZE,), dtype=np.float32), False, False

    nose = pose[0].copy()
    pose_values = pose - nose[np.newaxis, :]

    right_hand = landmarks_to_array(results.right_hand_landmarks, HAND_LANDMARK_COUNT)
    hand_present = right_hand is not None
    if right_hand is None:
        hand_values = np.zeros((HAND_LANDMARK_COUNT, 3), dtype=np.float32)
    else:
        hand_values = right_hand - nose[np.newaxis, :]
        scale = float(np.linalg.norm(hand_values[0] - hand_values[9]))
        if scale > MIN_WRIST_MCP_SCALE:
            hand_values = hand_values / scale

    output = np.concatenate([pose_values.reshape(-1), hand_values.reshape(-1)]).astype(np.float32)
    output[0:3] = 0.0
    output[2::3] *= Z_DAMPING
    return output, hand_present, True


def has_all_zero_frame(seq: np.ndarray) -> bool:
    return bool(np.any(np.all(np.isclose(seq, 0.0), axis=1)))


def compute_motion_score(seq: np.ndarray) -> float:
    offset = 16 * 3
    pairs = list(zip(seq[:-1], seq[1:]))[-10:]
    if not pairs:
        return 0.0
    total = 0.0
    for prev, cur in pairs:
        dx = float(prev[offset] - cur[offset])
        dy = float(prev[offset + 1] - cur[offset + 1])
        total += dx * dx + dy * dy
    return total / len(pairs)


def compute_wrist_path(seq: np.ndarray) -> float:
    offset = 16 * 3
    total = 0.0
    for prev, cur in zip(seq[:-1], seq[1:]):
        dx = float(prev[offset] - cur[offset])
        dy = float(prev[offset + 1] - cur[offset + 1])
        total += float(np.sqrt(dx * dx + dy * dy))
    return total


def save_export(label: str, signer_id: str, device_model: str, seq: np.ndarray, hand_presence_ratio: float, index: int) -> Path:
    timestamp_ms = int(time.time() * 1000)
    timestamp_text = datetime.fromtimestamp(timestamp_ms / 1000.0).strftime("%Y%m%d_%H%M%S_%f")[:-3]
    label_dir = EXPORT_ROOT / label
    label_dir.mkdir(parents=True, exist_ok=True)
    path = label_dir / f"PC_{safe_name(signer_id)}_{timestamp_text}_{index}.json"
    device_session_tag = f"{safe_name(device_model)}_{datetime.now().strftime('%Y%m%d')}"
    body = {
        "label": label,
        "signer_id": signer_id,
        "timestamp": timestamp_ms,
        "timestamp_iso": datetime.fromtimestamp(timestamp_ms / 1000.0).isoformat(timespec="milliseconds"),
        "device_model": device_model,
        "device_session_tag": device_session_tag,
        "active_profile": "fsl_pc_webcam_v1",
        "feature_profile": "onehand162",
        "input_shape": [FSL_SEQUENCE_LENGTH, FSL_FEATURE_SIZE],
        "sequence_length_at_export": FSL_SEQUENCE_LENGTH,
        "dominant_hand": "right",
        "mirrored_input": True,
        "selected_hand_slot": "right",
        "hand_presence_ratio": hand_presence_ratio,
        "missing_pose_count": int(np.sum(np.all(np.isclose(seq, 0.0), axis=1))),
        "missing_hand_count": int(round((1.0 - hand_presence_ratio) * FSL_SEQUENCE_LENGTH)),
        "motion_score": compute_motion_score(seq),
        "wrist_path": compute_wrist_path(seq),
        "fsl_mode": True,
        "feature_array": seq.astype(float).tolist(),
    }
    path.write_text(json.dumps(body, indent=2), encoding="utf-8")
    return path


def draw_overlay(frame, results) -> None:
    drawing = mp.solutions.drawing_utils
    styles = mp.solutions.drawing_styles
    holistic = mp.solutions.holistic
    if results.pose_landmarks is not None:
        drawing.draw_landmarks(
            frame,
            results.pose_landmarks,
            holistic.POSE_CONNECTIONS,
            landmark_drawing_spec=styles.get_default_pose_landmarks_style(),
        )
    if results.right_hand_landmarks is not None:
        drawing.draw_landmarks(frame, results.right_hand_landmarks, holistic.HAND_CONNECTIONS)


def put_status(frame, text: str, color: tuple[int, int, int]) -> None:
    cv2.putText(frame, text, (18, 32), cv2.FONT_HERSHEY_SIMPLEX, 0.85, color, 2, cv2.LINE_AA)


def record_webcam(label: str, signer_id: str, target_count: int, camera_index: int) -> int:
    require_video_dependencies()
    device_model = f"PC_WEBCAM_{safe_name(socket.gethostname())}"
    cap = cv2.VideoCapture(camera_index)
    if not cap.isOpened():
        raise SystemExit(f"Could not open webcam index {camera_index}")

    saved = 0
    recording = False
    frames: list[np.ndarray] = []
    hand_flags: list[bool] = []
    status_text = "SPACE to record | Q to quit"
    status_color = (255, 255, 255)

    with mp.solutions.holistic.Holistic(
        static_image_mode=False,
        model_complexity=1,
        min_detection_confidence=0.5,
        min_tracking_confidence=0.5,
    ) as holistic:
        while True:
            ok, frame = cap.read()
            if not ok:
                break
            rgb = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
            results = holistic.process(rgb)
            vector, hand_present, _pose_present = build_frame_features(results)
            draw_overlay(frame, results)

            if recording:
                frames.append(vector)
                hand_flags.append(hand_present)
                status_text = f"Recording {len(frames)}/{FSL_SEQUENCE_LENGTH}"
                status_color = (0, 255, 255)
                if len(frames) == FSL_SEQUENCE_LENGTH:
                    seq = np.asarray(frames, dtype=np.float32)
                    hand_presence_ratio = sum(hand_flags) / FSL_SEQUENCE_LENGTH
                    accepted = hand_presence_ratio >= MIN_HAND_PRESENCE and not has_all_zero_frame(seq)
                    if accepted:
                        saved += 1
                        path = save_export(label, signer_id, device_model, seq, hand_presence_ratio, saved)
                        status_text = f"ACCEPTED saved {path.name} ({saved}/{target_count})"
                        status_color = (0, 220, 0)
                    else:
                        status_text = f"REJECTED hand={hand_presence_ratio:.2f}"
                        status_color = (0, 0, 255)
                    recording = False
                    frames = []
                    hand_flags = []

            put_status(frame, status_text, status_color)
            cv2.imshow("VoxGest FSL PC Recorder", frame)
            key = cv2.waitKey(1) & 0xFF
            if key in {ord("q"), ord("Q")}:
                break
            if key == 32 and not recording and saved < target_count:
                recording = True
                frames = []
                hand_flags = []
                status_text = "Recording 0/20"
                status_color = (0, 255, 255)
            if saved >= target_count:
                status_text = f"Done {saved}/{target_count} | Q to quit"
                status_color = (0, 220, 0)

    cap.release()
    cv2.destroyAllWindows()
    return saved


def main() -> None:
    parser = argparse.ArgumentParser(description="Record FSL 20-frame JSON exports from a PC webcam.")
    parser.add_argument("--label", required=True, type=str)
    parser.add_argument("--signer_id", required=True, type=str)
    parser.add_argument("--count", type=int, default=50)
    parser.add_argument("--camera", type=int, default=0)
    args = parser.parse_args()

    label = args.label.strip().upper()
    if label not in FSL_LABELS:
        raise SystemExit(f"Unsupported FSL label: {label}. Expected one of: {', '.join(FSL_LABELS)}")
    signer_id = safe_name(args.signer_id)
    if not signer_id:
        raise SystemExit("--signer_id must not be blank")

    saved = record_webcam(label, signer_id, args.count, args.camera)
    print(f"Saved {saved} accepted FSL webcam exports for label={label} signer_id={signer_id}")


if __name__ == "__main__":
    main()
