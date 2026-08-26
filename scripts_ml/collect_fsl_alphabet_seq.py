"""Collect versioned FSL alphabet sequences with canonical OneHand162 features.

MediaPipe receives an unmirrored camera frame. Only the operator preview is
mirrored, so Holistic's anatomical left/right slots remain stable. Every
accepted sequence has adjacent metadata and is never silently overwritten.
"""

from __future__ import annotations

import argparse
import json
import re
import socket
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

import numpy as np

from fsl_config import FSL_ALPHABET_LABELS
from voxgest_feature_builder import (
    EXPECTED_SEQUENCE_SHAPE,
    FEATURE_LAYOUT,
    FEATURE_PROFILE,
    FEATURE_VERSION,
    NORMALIZATION_POLICY,
    SEQUENCE_LENGTH,
    extract_frame_features,
    validate_sequence,
)


ROOT = Path(__file__).resolve().parents[1]
DEFAULT_OUTPUT = ROOT / "external_datasets" / "fsl_onehand162_20f_v2" / "alphabet"
MIN_HAND_FRAMES = 13
MIN_POSE_FRAMES = 13
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
            "Missing webcam dependency. Use voxgest_env\\Scripts\\python.exe "
            "or install opencv-python and mediapipe."
        ) from exc
    cv2 = cv2_module
    mp = mp_module


def safe_name(value: object) -> str:
    text = re.sub(r"[^A-Za-z0-9_-]+", "_", str(value).strip())
    return text.strip("_") or "unknown"


def write_json_atomic(path: Path, payload: dict[str, Any]) -> None:
    temporary = path.with_suffix(path.suffix + ".tmp")
    temporary.write_text(
        json.dumps(payload, indent=2, ensure_ascii=False) + "\n",
        encoding="utf-8",
    )
    temporary.replace(path)


def save_npy_atomic(path: Path, array: np.ndarray) -> None:
    temporary = path.with_suffix(path.suffix + ".tmp")
    with temporary.open("wb") as handle:
        np.save(handle, array, allow_pickle=False)
    temporary.replace(path)


def next_sample_number(label_dir: Path, signer_id: str, session_id: str) -> int:
    prefix = f"alphabet_{safe_name(signer_id)}_{safe_name(session_id)}_"
    numbers: list[int] = []
    for path in label_dir.glob(f"{prefix}*.npy"):
        try:
            numbers.append(int(path.stem.removeprefix(prefix)))
        except ValueError:
            continue
    return max(numbers, default=0) + 1


def existing_sample_count(label_dir: Path, signer_id: str, session_id: str) -> int:
    prefix = f"alphabet_{safe_name(signer_id)}_{safe_name(session_id)}_"
    return sum(1 for path in label_dir.glob(f"{prefix}*.npy") if path.is_file())


def save_sequence(
    *,
    output_root: Path,
    label: str,
    signer_id: str,
    session_id: str,
    device_id: str,
    selected_hand: str,
    sequence: np.ndarray,
    hand_frames: int,
    pose_frames: int,
) -> Path:
    issues = validate_sequence(sequence)
    if issues:
        raise ValueError(f"refusing invalid sequence: {issues}")
    label_dir = output_root / label
    label_dir.mkdir(parents=True, exist_ok=True)
    number = next_sample_number(label_dir, signer_id, session_id)
    basename = f"alphabet_{safe_name(signer_id)}_{safe_name(session_id)}_{number:04d}"
    npy_path = label_dir / f"{basename}.npy"
    meta_path = label_dir / f"{basename}.meta.json"
    if npy_path.exists() or meta_path.exists():
        raise FileExistsError(f"refusing to overwrite existing sample: {npy_path}")
    now = datetime.now(timezone.utc)
    metadata = {
        "label": label,
        "source": "voxgest_alphabet_webcam",
        "source_kind": "live_webcam_contiguous_20_frames",
        "signer_id": signer_id,
        "recording_session": session_id,
        "device_model": device_id,
        "timestamp_utc": now.isoformat(),
        "feature_profile": FEATURE_PROFILE,
        "feature_version": FEATURE_VERSION,
        "feature_layout": FEATURE_LAYOUT,
        "normalization": NORMALIZATION_POLICY,
        "shape": list(EXPECTED_SEQUENCE_SHAPE),
        "dtype": "float32",
        "selected_hand": selected_hand,
        "handedness_policy": "operator_selected_anatomical_holistic_slot",
        "mirrored_input": False,
        "preview_mirrored": True,
        "sequence_length_at_export": SEQUENCE_LENGTH,
        "window_stride": None,
        "hand_present_frames": hand_frames,
        "hand_presence_ratio": hand_frames / SEQUENCE_LENGTH,
        "pose_present_frames": pose_frames,
        "pose_presence_ratio": pose_frames / SEQUENCE_LENGTH,
        "minimum_hand_frames": MIN_HAND_FRAMES,
        "minimum_pose_frames": MIN_POSE_FRAMES,
        "fsl_mode": True,
    }
    save_npy_atomic(npy_path, sequence)
    try:
        write_json_atomic(meta_path, metadata)
    except Exception:
        npy_path.unlink(missing_ok=True)
        raise
    return npy_path


def draw_preview(
    preview: np.ndarray,
    label: str,
    count: int,
    target: int,
    recording: bool,
    buffered: int,
    selected_hand: str,
    status: str,
) -> None:
    lines = [
        f"FSL alphabet: {label}   accepted {count}/{target}",
        f"selected anatomical hand: {selected_hand.upper()}",
        f"{'RECORDING ' + str(buffered) + '/20' if recording else 'SPACE record | N next | Q quit'}",
        status,
    ]
    for index, text in enumerate(lines):
        cv2.putText(
            preview,
            text,
            (18, 32 + index * 28),
            cv2.FONT_HERSHEY_SIMPLEX,
            0.68,
            (0, 255, 180) if index != 3 else (0, 220, 255),
            2,
            cv2.LINE_AA,
        )


def collect(args: argparse.Namespace) -> int:
    require_video_dependencies()
    labels = [args.label] if args.label else FSL_ALPHABET_LABELS
    output_root = args.output.resolve()
    allowed_root = (ROOT / "external_datasets").resolve()
    if not output_root.is_relative_to(allowed_root):
        raise SystemExit(f"--output must stay under {allowed_root}")
    output_root.mkdir(parents=True, exist_ok=True)

    capture = cv2.VideoCapture(args.camera)
    if not capture.isOpened():
        raise SystemExit(f"Could not open webcam index {args.camera}")

    accepted_total = 0
    label_index = 0
    recording = False
    vectors: list[np.ndarray] = []
    hand_flags: list[bool] = []
    pose_flags: list[bool] = []
    status = "Ready"
    per_label_count = existing_sample_count(
        output_root / labels[label_index], args.signer_id, args.session_id
    )

    with mp.solutions.holistic.Holistic(
        static_image_mode=False,
        model_complexity=1,
        enable_segmentation=False,
        refine_face_landmarks=False,
        min_detection_confidence=0.5,
        min_tracking_confidence=0.5,
    ) as holistic:
        while capture.isOpened() and label_index < len(labels):
            while label_index < len(labels) and per_label_count >= args.count:
                label_index += 1
                if label_index < len(labels):
                    per_label_count = existing_sample_count(
                        output_root / labels[label_index],
                        args.signer_id,
                        args.session_id,
                    )
            if label_index >= len(labels):
                break
            ok, raw_frame = capture.read()
            if not ok:
                status = "Camera read failed"
                break
            rgb = cv2.cvtColor(raw_frame, cv2.COLOR_BGR2RGB)
            results = holistic.process(rgb)
            vector, hand_present, pose_present = extract_frame_features(
                results,
                selected_hand=args.selected_hand,
            )

            if recording:
                vectors.append(vector)
                hand_flags.append(hand_present)
                pose_flags.append(pose_present)
                if len(vectors) == SEQUENCE_LENGTH:
                    sequence = np.asarray(vectors, dtype=np.float32)
                    hand_frames = sum(hand_flags)
                    pose_frames = sum(pose_flags)
                    if (
                        not validate_sequence(sequence)
                        and hand_frames >= MIN_HAND_FRAMES
                        and pose_frames >= MIN_POSE_FRAMES
                    ):
                        path = save_sequence(
                            output_root=output_root,
                            label=labels[label_index],
                            signer_id=args.signer_id,
                            session_id=args.session_id,
                            device_id=args.device_id,
                            selected_hand=args.selected_hand,
                            sequence=sequence,
                            hand_frames=hand_frames,
                            pose_frames=pose_frames,
                        )
                        per_label_count += 1
                        accepted_total += 1
                        status = f"ACCEPTED {path.name}"
                    else:
                        status = f"REJECTED hand={hand_frames}/20 pose={pose_frames}/20"
                    recording = False
                    vectors.clear()
                    hand_flags.clear()
                    pose_flags.clear()
                    if per_label_count >= args.count:
                        label_index += 1
                        if label_index < len(labels):
                            per_label_count = existing_sample_count(
                                output_root / labels[label_index],
                                args.signer_id,
                                args.session_id,
                            )
                        status = "Letter complete; ready for next letter"
                        if label_index >= len(labels):
                            break

            annotated = raw_frame.copy()
            drawing = mp.solutions.drawing_utils
            if results.pose_landmarks is not None:
                drawing.draw_landmarks(
                    annotated,
                    results.pose_landmarks,
                    mp.solutions.holistic.POSE_CONNECTIONS,
                )
            selected_landmarks = getattr(
                results,
                f"{args.selected_hand}_hand_landmarks",
                None,
            )
            if selected_landmarks is not None:
                drawing.draw_landmarks(
                    annotated,
                    selected_landmarks,
                    mp.solutions.holistic.HAND_CONNECTIONS,
                )
            preview = cv2.flip(annotated, 1)
            draw_preview(
                preview,
                labels[label_index],
                per_label_count,
                args.count,
                recording,
                len(vectors),
                args.selected_hand,
                status,
            )
            cv2.imshow("VoxGest canonical FSL alphabet recorder", preview)
            key = cv2.waitKey(1) & 0xFF
            if key in {ord("q"), ord("Q")}:
                break
            if key == 32 and not recording:
                recording = True
                vectors.clear()
                hand_flags.clear()
                pose_flags.clear()
                status = "Capturing next contiguous 20 frames"
            if key in {ord("n"), ord("N")} and not recording:
                label_index += 1
                if label_index < len(labels):
                    per_label_count = existing_sample_count(
                        output_root / labels[label_index],
                        args.signer_id,
                        args.session_id,
                    )
                status = "Skipped to next letter"

    capture.release()
    cv2.destroyAllWindows()
    return accepted_total


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--signer-id", required=True)
    parser.add_argument("--session-id", default=datetime.now().strftime("%Y%m%d_%H%M%S"))
    parser.add_argument(
        "--device-id",
        default=f"PC_WEBCAM_{safe_name(socket.gethostname())}",
    )
    parser.add_argument("--selected-hand", choices=("right", "left"), default="right")
    parser.add_argument("--label", choices=FSL_ALPHABET_LABELS)
    parser.add_argument("--count", type=int, default=200)
    parser.add_argument("--camera", type=int, default=0)
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
    args = parser.parse_args()
    if not str(args.signer_id).strip():
        parser.error("--signer-id must not be blank")
    if not str(args.session_id).strip():
        parser.error("--session-id must not be blank")
    if not str(args.device_id).strip():
        parser.error("--device-id must not be blank")
    args.signer_id = safe_name(args.signer_id)
    args.session_id = safe_name(args.session_id)
    args.device_id = safe_name(args.device_id)
    if args.count < 1:
        parser.error("--count must be at least 1")
    return args


def main() -> int:
    args = parse_args()
    saved = collect(args)
    print(f"Saved {saved} canonical {FEATURE_VERSION} alphabet sequences")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
