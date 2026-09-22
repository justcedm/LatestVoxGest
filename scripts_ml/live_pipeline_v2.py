"""Experimental two-stage VoxGest live recognition pipeline.

Stage 1 evaluates each canonical 162-feature frame with the activity detector.
Only active frames enter the 20-frame buffer.  Stage 2 evaluates a full buffer
with RD-TCN and applies manifest-defined confidence, margin, and cooldown gates.

This module does not modify or replace the stable Android runtime.
"""

from __future__ import annotations

import argparse
import json
from collections import deque
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Protocol

import numpy as np

from fsl_dataset import ROOT
from voxgest_feature_builder import (
    EXPECTED_FRAME_SHAPE,
    EXPECTED_SEQUENCE_SHAPE,
    FEATURE_VERSION,
    extract_frame_features,
    validate_frame,
)


DEFAULT_ACTIVITY_MODEL = ROOT / "model" / "voxgest_activity_detector_v1.tflite"
DEFAULT_CLASSIFIER_MODEL = (
    ROOT / "model" / "experimental" / "voxgest_fsl_rdtcn_v2_float16.tflite"
)
DEFAULT_MANIFEST = ROOT / "model" / "runtime_manifest.json"


class ProbabilityModel(Protocol):
    def predict(self, array: np.ndarray) -> np.ndarray:
        """Return one model output row for a batch-of-one input."""


class TFLiteProbabilityModel:
    """Small checked batch-of-one TensorFlow Lite runner."""

    def __init__(
        self,
        model_path: Path,
        expected_input_shape: tuple[int, ...],
        expected_output_shape: tuple[int, ...],
    ) -> None:
        import tensorflow as tf

        self.model_path = model_path
        self.interpreter = tf.lite.Interpreter(model_path=str(model_path))
        self.interpreter.allocate_tensors()
        self.input_detail = self.interpreter.get_input_details()[0]
        self.output_detail = self.interpreter.get_output_details()[0]
        input_shape = tuple(int(value) for value in self.input_detail["shape"])
        output_shape = tuple(int(value) for value in self.output_detail["shape"])
        if input_shape != expected_input_shape:
            raise ValueError(
                f"{model_path} input {input_shape} != {expected_input_shape}"
            )
        if output_shape != expected_output_shape:
            raise ValueError(
                f"{model_path} output {output_shape} != {expected_output_shape}"
            )
        if np.dtype(self.input_detail["dtype"]) != np.float32:
            raise ValueError(f"{model_path} input must be float32")
        if np.dtype(self.output_detail["dtype"]) != np.float32:
            raise ValueError(f"{model_path} output must be float32")

    def predict(self, array: np.ndarray) -> np.ndarray:
        array = np.asarray(array, dtype=np.float32)
        expected = tuple(int(value) for value in self.input_detail["shape"])
        if array.shape != expected:
            raise ValueError(f"input shape {array.shape} != {expected}")
        self.interpreter.set_tensor(self.input_detail["index"], array)
        self.interpreter.invoke()
        return np.asarray(
            self.interpreter.get_tensor(self.output_detail["index"])[0],
            dtype=np.float32,
        )


@dataclass(frozen=True)
class RecognitionEvent:
    frame_index: int
    state: str
    activity_probability: float
    buffer_size: int
    predicted_index: int | None = None
    label: str | None = None
    confidence: float | None = None
    margin: float | None = None
    emitted: bool = False


def load_runtime_manifest(path: Path = DEFAULT_MANIFEST) -> dict[str, Any]:
    manifest = json.loads(path.read_text(encoding="utf-8"))
    required = {
        "feature_version": FEATURE_VERSION,
        "sequence_length": EXPECTED_SEQUENCE_SHAPE[0],
        "feature_size": EXPECTED_SEQUENCE_SHAPE[1],
        "num_classes": 64,
        "selected_hand": "right",
    }
    for key, expected in required.items():
        if manifest.get(key) != expected:
            raise ValueError(f"manifest {key}={manifest.get(key)!r}; expected {expected!r}")
    if manifest.get("nsac_class_index") is not None:
        raise ValueError("v2 two-stage runtime must not use an NSAC class index")
    labels = manifest.get("class_order")
    if not isinstance(labels, list) or len(labels) != manifest["num_classes"]:
        raise ValueError("manifest class_order must contain exactly 64 labels")
    for key in (
        "confidence_threshold",
        "margin_threshold",
        "cooldown_frames",
        "activity_detector_threshold",
    ):
        if key not in manifest:
            raise ValueError(f"manifest is missing calibrated field {key}")
    return manifest


class TwoStageRecognizer:
    def __init__(
        self,
        activity_detector: ProbabilityModel,
        classifier: ProbabilityModel,
        manifest: dict[str, Any],
    ) -> None:
        self.activity_detector = activity_detector
        self.classifier = classifier
        self.labels = list(manifest["class_order"])
        self.activity_threshold = float(manifest["activity_detector_threshold"])
        self.confidence_threshold = float(manifest["confidence_threshold"])
        self.margin_threshold = float(manifest["margin_threshold"])
        self.cooldown_frames = int(manifest["cooldown_frames"])
        self.sequence_length = int(manifest["sequence_length"])
        self.buffer: deque[np.ndarray] = deque(maxlen=self.sequence_length)
        self.frame_index = -1
        self.last_emitted_frame_by_label: dict[int, int] = {}

    def process_frame_features(self, frame: np.ndarray) -> RecognitionEvent:
        self.frame_index += 1
        frame = np.asarray(frame)
        issues = validate_frame(frame)
        if issues:
            raise ValueError(f"invalid canonical frame: {issues}")
        activity_output = np.asarray(
            self.activity_detector.predict(frame[np.newaxis]), dtype=np.float32
        ).reshape(-1)
        if len(activity_output) != 1 or not np.isfinite(activity_output).all():
            raise ValueError("activity detector must return one finite probability")
        activity_probability = float(activity_output[0])
        if activity_probability < self.activity_threshold:
            # A no-activity gap invalidates temporal contiguity.
            self.buffer.clear()
            return RecognitionEvent(
                frame_index=self.frame_index,
                state="activity_rejected",
                activity_probability=activity_probability,
                buffer_size=0,
            )

        self.buffer.append(frame.copy())
        if len(self.buffer) < self.sequence_length:
            return RecognitionEvent(
                frame_index=self.frame_index,
                state="buffering",
                activity_probability=activity_probability,
                buffer_size=len(self.buffer),
            )

        sequence = np.asarray(self.buffer, dtype=np.float32)
        if sequence.shape != EXPECTED_SEQUENCE_SHAPE:
            raise RuntimeError(f"sequence buffer has unexpected shape {sequence.shape}")
        probabilities = np.asarray(
            self.classifier.predict(sequence[np.newaxis]), dtype=np.float32
        ).reshape(-1)
        if len(probabilities) != len(self.labels) or not np.isfinite(probabilities).all():
            raise ValueError("classifier output must contain 64 finite probabilities")
        order = np.argsort(probabilities)
        predicted_index = int(order[-1])
        confidence = float(probabilities[order[-1]])
        margin = float(probabilities[order[-1]] - probabilities[order[-2]])
        label = self.labels[predicted_index]
        common = {
            "frame_index": self.frame_index,
            "activity_probability": activity_probability,
            "buffer_size": len(self.buffer),
            "predicted_index": predicted_index,
            "label": label,
            "confidence": confidence,
            "margin": margin,
        }
        if confidence < self.confidence_threshold:
            return RecognitionEvent(state="confidence_rejected", **common)
        if margin < self.margin_threshold:
            return RecognitionEvent(state="margin_rejected", **common)

        previous_frame = self.last_emitted_frame_by_label.get(predicted_index)
        if (
            previous_frame is not None
            and self.frame_index - previous_frame <= self.cooldown_frames
        ):
            return RecognitionEvent(state="cooldown_rejected", **common)
        self.last_emitted_frame_by_label[predicted_index] = self.frame_index
        return RecognitionEvent(state="accepted", emitted=True, **common)


def build_default_recognizer(manifest_path: Path = DEFAULT_MANIFEST) -> TwoStageRecognizer:
    manifest = load_runtime_manifest(manifest_path)
    activity_path = ROOT / "model" / manifest["activity_detector_filename"]
    classifier_path = ROOT / "model" / "experimental" / manifest["rdtcn_model_filename"]
    activity = TFLiteProbabilityModel(
        activity_path,
        (1, EXPECTED_FRAME_SHAPE[0]),
        (1, 1),
    )
    classifier = TFLiteProbabilityModel(
        classifier_path,
        (1, *EXPECTED_SEQUENCE_SHAPE),
        (1, manifest["num_classes"]),
    )
    return TwoStageRecognizer(activity, classifier, manifest)


def run_webcam(camera_index: int, manifest_path: Path) -> int:
    import cv2
    import mediapipe as mp

    manifest = load_runtime_manifest(manifest_path)
    recognizer = build_default_recognizer(manifest_path)
    capture = cv2.VideoCapture(camera_index)
    if not capture.isOpened():
        raise SystemExit(f"could not open webcam index {camera_index}")
    latest = "Ready"
    with mp.solutions.holistic.Holistic(
        static_image_mode=False,
        model_complexity=1,
        enable_segmentation=False,
        refine_face_landmarks=False,
        min_detection_confidence=0.5,
        min_tracking_confidence=0.5,
    ) as holistic:
        while capture.isOpened():
            ok, image = capture.read()
            if not ok:
                break
            results = holistic.process(cv2.cvtColor(image, cv2.COLOR_BGR2RGB))
            frame, _hand_present, _pose_present = extract_frame_features(
                results, selected_hand=manifest["selected_hand"]
            )
            event = recognizer.process_frame_features(frame)
            if event.emitted:
                latest = (
                    f"ACCEPT {event.label} conf={event.confidence:.3f} "
                    f"margin={event.margin:.3f}"
                )
                print(latest, flush=True)
            elif event.state != "buffering":
                latest = f"{event.state} activity={event.activity_probability:.3f}"
            preview = cv2.flip(image, 1)
            cv2.putText(
                preview,
                latest,
                (20, 35),
                cv2.FONT_HERSHEY_SIMPLEX,
                0.65,
                (30, 220, 30),
                2,
            )
            cv2.imshow("VoxGest experimental two-stage pipeline", preview)
            if cv2.waitKey(1) & 0xFF in {ord("q"), ord("Q")}:
                break
    capture.release()
    cv2.destroyAllWindows()
    return 0


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--camera", type=int, default=0)
    parser.add_argument("--manifest", type=Path, default=DEFAULT_MANIFEST)
    return parser.parse_args()


if __name__ == "__main__":
    arguments = parse_args()
    raise SystemExit(run_webcam(arguments.camera, arguments.manifest.resolve()))
