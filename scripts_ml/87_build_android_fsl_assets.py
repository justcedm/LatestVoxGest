"""Build deterministic assets for the isolated Android FSL RD-TCN profile.

This script deliberately writes only beneath the profile-specific asset
directory. It does not replace or modify any of the stable Android assets.
"""

from __future__ import annotations

import csv
import hashlib
import json
import os
import re
import shutil
from pathlib import Path
from typing import Any

import numpy as np

from fsl_dataset import ROOT, SPLIT_MANIFEST_PATH, write_json_atomic
from voxgest_feature_builder import (
    EXPECTED_FRAME_SHAPE,
    EXPECTED_SEQUENCE_SHAPE,
    FEATURE_LAYOUT,
    FEATURE_VERSION,
    HAND_LANDMARK_COUNT,
    MIDDLE_FINGER_MCP_INDEX,
    NORMALIZATION_POLICY,
    POSE_LANDMARK_COUNT,
    build_onehand162_from_arrays,
)


PROFILE_ID = "fsl_onehand162_20f_rdtcn_v2"
PROFILE_ASSET_ROOT = (
    ROOT
    / "android_dry_run"
    / "app"
    / "src"
    / "main"
    / "assets"
    / "model"
    / PROFILE_ID
)

SOURCE_MODEL = (
    ROOT / "model" / "experimental" / "voxgest_fsl_rdtcn_v2_float16.tflite"
)
SOURCE_LABELS = ROOT / "android_handoff" / "class_labels_fsl_v2.json"
SOURCE_RUNTIME_MANIFEST = ROOT / "model" / "runtime_manifest.json"
SOURCE_GOLDEN_WINDOW = (
    ROOT
    / "external_datasets"
    / "fsl_features"
    / "AUNTIE"
    / "fsl105_0_w23.npy"
)

ASSET_MODEL = PROFILE_ASSET_ROOT / SOURCE_MODEL.name
ASSET_LABELS = PROFILE_ASSET_ROOT / SOURCE_LABELS.name
ASSET_RUNTIME_MANIFEST = PROFILE_ASSET_ROOT / SOURCE_RUNTIME_MANIFEST.name
ASSET_GOLDEN_WINDOW = PROFILE_ASSET_ROOT / "golden_window_f32.bin"
ASSET_GOLDEN_EXPECTED = PROFILE_ASSET_ROOT / "golden_expected.json"
ASSET_GOLDEN_FEATURES = PROFILE_ASSET_ROOT / "golden_feature_fixture.json"
ASSET_GOLDEN_FEATURES_BINARY = (
    PROFILE_ASSET_ROOT / "golden_feature_fixture_f32.bin"
)

EXPECTED_CLASS_COUNT = 64
EXPECTED_GOLDEN_SPLIT = "test"
EXPECTED_GOLDEN_LABEL = "AUNTIE"
MAX_ABS_PROBABILITY_ERROR = 1.0e-4
MEAN_ABS_PROBABILITY_ERROR = 1.0e-5


def sha256_bytes(content: bytes) -> str:
    return hashlib.sha256(content).hexdigest()


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def copy_atomic(source: Path, destination: Path) -> None:
    destination.parent.mkdir(parents=True, exist_ok=True)
    temporary = destination.with_suffix(destination.suffix + ".tmp")
    shutil.copyfile(source, temporary)
    os.replace(temporary, destination)


def write_bytes_atomic(path: Path, content: bytes) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_suffix(path.suffix + ".tmp")
    temporary.write_bytes(content)
    os.replace(temporary, path)


def android_token(label: str) -> str:
    value = label.upper().replace("â€™", "").replace("’", "").replace("'", "")
    return re.sub(r"[^A-Z0-9]+", "_", value).strip("_")


def little_endian_float32_bytes(values: np.ndarray) -> bytes:
    array = np.asarray(values, dtype="<f4", order="C")
    if not np.isfinite(array).all():
        raise ValueError("golden values contain NaN or Inf")
    return array.tobytes(order="C")


def load_and_validate_contract() -> tuple[dict[str, Any], dict[str, int]]:
    required = [
        SOURCE_MODEL,
        SOURCE_LABELS,
        SOURCE_RUNTIME_MANIFEST,
        SOURCE_GOLDEN_WINDOW,
        SPLIT_MANIFEST_PATH,
    ]
    missing = [str(path) for path in required if not path.is_file()]
    if missing:
        raise SystemExit(f"missing Android FSL asset prerequisites: {missing}")

    manifest = json.loads(SOURCE_RUNTIME_MANIFEST.read_text(encoding="utf-8"))
    label_map = json.loads(SOURCE_LABELS.read_text(encoding="utf-8"))
    class_order = manifest.get("class_order")
    if not isinstance(class_order, list) or len(class_order) != EXPECTED_CLASS_COUNT:
        raise SystemExit("runtime manifest must contain exactly 64 ordered classes")
    if not isinstance(label_map, dict) or len(label_map) != EXPECTED_CLASS_COUNT:
        raise SystemExit("Android label map must contain exactly 64 classes")
    expected_label_map = {
        android_token(str(label)): index for index, label in enumerate(class_order)
    }
    if label_map != expected_label_map:
        raise SystemExit("Android label map does not exactly match manifest class_order")
    if sorted(label_map.values()) != list(range(EXPECTED_CLASS_COUNT)):
        raise SystemExit("Android label indices must be the exact range 0..63")

    contract_checks = {
        "feature_version": manifest.get("feature_version") == FEATURE_VERSION,
        "sequence_length": manifest.get("sequence_length") == EXPECTED_SEQUENCE_SHAPE[0],
        "feature_size": manifest.get("feature_size") == EXPECTED_SEQUENCE_SHAPE[1],
        "input_shape": manifest.get("input_shape") == [1, *EXPECTED_SEQUENCE_SHAPE],
        "input_dtype": manifest.get("input_dtype") == "float32",
        "output_shape": manifest.get("output_shape") == [1, EXPECTED_CLASS_COUNT],
        "output_dtype": manifest.get("output_dtype") == "float32",
        "feature_layout": manifest.get("feature_layout") == FEATURE_LAYOUT,
        "normalization": manifest.get("normalization") == NORMALIZATION_POLICY,
        "model_filename": manifest.get("rdtcn_model_filename") == SOURCE_MODEL.name,
        "model_sha256": manifest.get("artifact_sha256", {}).get("rdtcn_float16")
        == sha256_file(SOURCE_MODEL),
        "android_default_unchanged": manifest.get("android_default_changed") is False,
    }
    failed = [name for name, passed in contract_checks.items() if not passed]
    if failed:
        raise SystemExit(f"canonical runtime contract validation failed: {failed}")
    return manifest, label_map


def find_golden_source_record() -> dict[str, str]:
    relative = str(SOURCE_GOLDEN_WINDOW.relative_to(ROOT)).replace("/", "\\")
    with SPLIT_MANIFEST_PATH.open("r", encoding="utf-8-sig", newline="") as handle:
        matches = [
            row
            for row in csv.DictReader(handle)
            if row.get("path", "").replace("/", "\\") == relative
        ]
    if len(matches) != 1:
        raise SystemExit(
            f"expected one split-manifest row for golden fixture, found {len(matches)}"
        )
    record = matches[0]
    if record.get("split") != EXPECTED_GOLDEN_SPLIT:
        raise SystemExit("golden fixture is no longer assigned to the held-out test split")
    if record.get("label") != EXPECTED_GOLDEN_LABEL:
        raise SystemExit("golden fixture label changed unexpectedly")
    return record


def create_tflite_interpreter(tf, model_path: Path):
    resolver_type = tf.lite.experimental.OpResolverType.BUILTIN_WITHOUT_DEFAULT_DELEGATES
    interpreter = tf.lite.Interpreter(
        model_path=str(model_path),
        num_threads=1,
        experimental_op_resolver_type=resolver_type,
    )
    interpreter.allocate_tensors()
    return interpreter


def build_golden_tflite_fixture(
    tf,
    manifest: dict[str, Any],
    label_map: dict[str, int],
) -> dict[str, Any]:
    record = find_golden_source_record()
    window = np.load(SOURCE_GOLDEN_WINDOW, allow_pickle=False)
    if window.shape != EXPECTED_SEQUENCE_SHAPE or window.dtype != np.float32:
        raise SystemExit(
            f"golden sequence contract mismatch: shape={window.shape}, dtype={window.dtype}"
        )
    window_bytes = little_endian_float32_bytes(window)
    window_sha256 = sha256_bytes(window_bytes)
    if window_sha256 != record.get("content_sha256"):
        raise SystemExit("golden sequence bytes do not match split-manifest content hash")

    interpreter = create_tflite_interpreter(tf, SOURCE_MODEL)
    input_detail = interpreter.get_input_details()[0]
    output_detail = interpreter.get_output_details()[0]
    input_shape = input_detail["shape"].astype(int).tolist()
    output_shape = output_detail["shape"].astype(int).tolist()
    if input_shape != [1, *EXPECTED_SEQUENCE_SHAPE]:
        raise SystemExit(f"unexpected model input shape: {input_shape}")
    if np.dtype(input_detail["dtype"]) != np.float32:
        raise SystemExit(f"unexpected model input dtype: {input_detail['dtype']}")
    if output_shape != [1, EXPECTED_CLASS_COUNT]:
        raise SystemExit(f"unexpected model output shape: {output_shape}")
    if np.dtype(output_detail["dtype"]) != np.float32:
        raise SystemExit(f"unexpected model output dtype: {output_detail['dtype']}")

    interpreter.set_tensor(input_detail["index"], window[np.newaxis])
    interpreter.invoke()
    probabilities = np.asarray(
        interpreter.get_tensor(output_detail["index"])[0], dtype=np.float32
    )
    if probabilities.shape != (EXPECTED_CLASS_COUNT,) or not np.isfinite(probabilities).all():
        raise SystemExit("TFLite golden output is not a finite 64-value vector")

    order = np.argsort(-probabilities, kind="stable")
    class_order = [str(label) for label in manifest["class_order"]]
    tokens_by_index = [""] * EXPECTED_CLASS_COUNT
    for token, index in label_map.items():
        tokens_by_index[index] = token
    top_k = [
        {
            "rank": rank,
            "index": int(index),
            "token": tokens_by_index[int(index)],
            "label": class_order[int(index)],
            "probability": float(probabilities[int(index)]),
        }
        for rank, index in enumerate(order[:5], start=1)
    ]
    expected_index = class_order.index(record["label"])
    margin = float(probabilities[order[0]] - probabilities[order[1]])
    if int(order[0]) != expected_index or margin < 0.9:
        raise SystemExit(
            "golden fixture must remain correctly classified with at least 0.9 margin"
        )

    write_bytes_atomic(ASSET_GOLDEN_WINDOW, window_bytes)
    return {
        "schema_version": 1,
        "profile_id": PROFILE_ID,
        "purpose": "android_tflite_golden_parity",
        "reference_engine": "python_tflite_builtin_without_default_delegates_single_thread",
        "fixture": {
            "filename": ASSET_GOLDEN_WINDOW.name,
            "format": "raw_ieee754_float32_little_endian_row_major",
            "shape": list(EXPECTED_SEQUENCE_SHAPE),
            "value_count": int(window.size),
            "byte_count": len(window_bytes),
            "sha256": window_sha256,
            "source": {
                "project_relative_path": str(SOURCE_GOLDEN_WINDOW.relative_to(ROOT)),
                "split": record["split"],
                "label": record["label"],
                "signer_id": record["signer_id"],
                "source_video": record["source_video"],
                "feature_version": record["feature_version"],
                "split_manifest_content_sha256": record["content_sha256"],
            },
        },
        "model": {
            "filename": ASSET_MODEL.name,
            "sha256": sha256_file(SOURCE_MODEL),
            "input_shape": input_shape,
            "input_dtype": "float32",
            "output_shape": output_shape,
            "output_dtype": "float32",
            "weight_quantization": "float16",
        },
        "labels": {
            "filename": ASSET_LABELS.name,
            "sha256": sha256_file(SOURCE_LABELS),
            "class_count": EXPECTED_CLASS_COUNT,
        },
        "runtime_manifest": {
            "filename": ASSET_RUNTIME_MANIFEST.name,
            "sha256": sha256_file(SOURCE_RUNTIME_MANIFEST),
        },
        "expected_output": {
            "shape": [1, EXPECTED_CLASS_COUNT],
            "dtype": "float32",
            "probabilities": [float(value) for value in probabilities],
            "float32_little_endian_sha256": sha256_bytes(
                little_endian_float32_bytes(probabilities)
            ),
            "probability_sum": float(np.sum(probabilities, dtype=np.float64)),
            "top_k": top_k,
            "top1_index": int(order[0]),
            "top1_token": tokens_by_index[int(order[0])],
            "top1_label": class_order[int(order[0])],
            "top1_probability": float(probabilities[order[0]]),
            "top2_index": int(order[1]),
            "top2_token": tokens_by_index[int(order[1])],
            "top2_label": class_order[int(order[1])],
            "top2_probability": float(probabilities[order[1]]),
            "margin": margin,
        },
        "android_parity_thresholds": {
            "top1_index_must_match": True,
            "maximum_absolute_probability_error": MAX_ABS_PROBABILITY_ERROR,
            "maximum_mean_absolute_probability_error": MEAN_ABS_PROBABILITY_ERROR,
            "raw_output_sha256_is_diagnostic_only": True,
        },
    }


def deterministic_raw_landmarks() -> tuple[np.ndarray, np.ndarray]:
    """Return finite, exactly reproducible synthetic pose/right-hand arrays."""
    pose = np.empty((POSE_LANDMARK_COUNT, 3), dtype=np.float32)
    for index in range(POSE_LANDMARK_COUNT):
        pose[index] = (
            np.float32(0.5 + index / 128.0),
            np.float32(0.25 + (index % 7) / 64.0),
            np.float32(-0.125 + index / 256.0),
        )

    nose = pose[0].copy()
    hand = np.empty((HAND_LANDMARK_COUNT, 3), dtype=np.float32)
    for index in range(HAND_LANDMARK_COUNT):
        hand[index] = (
            np.float32(nose[0] + index / 64.0),
            np.float32(nose[1] + (index % 4) / 128.0),
            np.float32(nose[2] - index / 256.0),
        )
    hand[0] = nose
    hand[MIDDLE_FINGER_MCP_INDEX] = nose + np.asarray(
        [0.25, 0.0, 0.0], dtype=np.float32
    )
    return pose, hand


def build_golden_feature_fixture() -> tuple[dict[str, Any], bytes]:
    pose, right_hand = deterministic_raw_landmarks()
    cases = [
        ("pose_and_anatomical_right_hand", pose, right_hand, "inputs.pose", "inputs.anatomical_right_hand"),
        ("missing_hand_zero_fills_hand_slots", pose, None, "inputs.pose", None),
        ("missing_pose_zero_fills_entire_frame", None, right_hand, None, "inputs.anatomical_right_hand"),
    ]
    encoded_cases: list[dict[str, Any]] = []
    for name, case_pose, case_hand, pose_ref, hand_ref in cases:
        result = build_onehand162_from_arrays(
            case_pose,
            case_hand,
            selected_hand="right",
        )
        if result.vector.shape != EXPECTED_FRAME_SHAPE or result.vector.dtype != np.float32:
            raise SystemExit(f"canonical builder produced invalid output for {name}")
        vector_bytes = little_endian_float32_bytes(result.vector)
        encoded_cases.append(
            {
                "name": name,
                "pose_input_ref": pose_ref,
                "selected_hand_input_ref": hand_ref,
                "selected_hand": "right",
                "expected": {
                    "shape": list(EXPECTED_FRAME_SHAPE),
                    "dtype": "float32",
                    "vector": [float(value) for value in result.vector],
                    "float32_little_endian_sha256": sha256_bytes(vector_bytes),
                    "hand_present": result.hand_present,
                    "pose_present": result.pose_present,
                    "selected_hand": result.selected_hand,
                },
            }
        )

    full = np.asarray(encoded_cases[0]["expected"]["vector"], dtype=np.float32)
    no_hand = np.asarray(encoded_cases[1]["expected"]["vector"], dtype=np.float32)
    no_pose = np.asarray(encoded_cases[2]["expected"]["vector"], dtype=np.float32)
    if not np.all(no_hand[FEATURE_LAYOUT["selected_hand"][0] :] == 0.0):
        raise SystemExit("missing-hand golden case did not zero-fill the hand block")
    if not np.array_equal(
        no_hand[: FEATURE_LAYOUT["pose"][1]],
        full[: FEATURE_LAYOUT["pose"][1]],
    ):
        raise SystemExit("missing-hand golden case unexpectedly changed the pose block")
    if not np.all(no_pose == 0.0):
        raise SystemExit("missing-pose golden case did not zero-fill the entire frame")

    binary_blocks = [
        ("pose", pose.reshape(-1), [POSE_LANDMARK_COUNT, 3]),
        (
            "anatomical_right_hand",
            right_hand.reshape(-1),
            [HAND_LANDMARK_COUNT, 3],
        ),
        ("expected_pose_and_hand", full, list(EXPECTED_FRAME_SHAPE)),
        ("expected_missing_hand", no_hand, list(EXPECTED_FRAME_SHAPE)),
        ("expected_missing_pose", no_pose, list(EXPECTED_FRAME_SHAPE)),
    ]
    binary_layout: list[dict[str, Any]] = []
    binary_parts: list[bytes] = []
    offset = 0
    for name, values, shape in binary_blocks:
        content = little_endian_float32_bytes(values)
        count = int(np.asarray(values).size)
        binary_layout.append(
            {
                "name": name,
                "float32_offset": offset,
                "float32_count": count,
                "byte_offset": offset * np.dtype("<f4").itemsize,
                "shape": shape,
            }
        )
        binary_parts.append(content)
        offset += count
    binary_content = b"".join(binary_parts)

    payload = {
        "schema_version": 1,
        "profile_id": PROFILE_ID,
        "purpose": "canonical_android_feature_builder_parity",
        "feature_version": FEATURE_VERSION,
        "feature_layout": FEATURE_LAYOUT,
        "normalization": NORMALIZATION_POLICY,
        "input_format": "MediaPipe landmark order; each point is [x,y,z] float32",
        "inputs": {
            "pose": [[float(value) for value in point] for point in pose],
            "anatomical_right_hand": [
                [float(value) for value in point] for point in right_hand
            ],
        },
        "cases": encoded_cases,
        "binary_fixture": {
            "filename": ASSET_GOLDEN_FEATURES_BINARY.name,
            "format": "raw_ieee754_float32_little_endian_contiguous",
            "byte_order": "little_endian",
            "layout": binary_layout,
            "float32_count": offset,
            "byte_count": len(binary_content),
            "sha256": sha256_bytes(binary_content),
        },
    }
    if offset != 648 or len(binary_content) != 648 * np.dtype("<f4").itemsize:
        raise SystemExit("unexpected golden feature binary layout size")
    return payload, binary_content


def validate_written_assets(
    golden_expected: dict[str, Any],
    golden_features: dict[str, Any],
) -> dict[str, bool]:
    validations = {
        "isolated_profile_directory": PROFILE_ASSET_ROOT.name == PROFILE_ID,
        "model_exact_copy": sha256_file(ASSET_MODEL) == sha256_file(SOURCE_MODEL),
        "labels_exact_copy": sha256_file(ASSET_LABELS) == sha256_file(SOURCE_LABELS),
        "runtime_manifest_exact_copy": sha256_file(ASSET_RUNTIME_MANIFEST)
        == sha256_file(SOURCE_RUNTIME_MANIFEST),
        "golden_window_hash": sha256_file(ASSET_GOLDEN_WINDOW)
        == golden_expected["fixture"]["sha256"],
        "golden_window_byte_count": ASSET_GOLDEN_WINDOW.stat().st_size
        == int(np.prod(EXPECTED_SEQUENCE_SHAPE)) * np.dtype("<f4").itemsize,
        "golden_output_has_64_values": len(
            golden_expected["expected_output"]["probabilities"]
        )
        == EXPECTED_CLASS_COUNT,
        "feature_fixture_has_three_cases": len(golden_features["cases"]) == 3,
        "feature_binary_hash": sha256_file(ASSET_GOLDEN_FEATURES_BINARY)
        == golden_features["binary_fixture"]["sha256"],
        "feature_binary_has_648_float32_values": ASSET_GOLDEN_FEATURES_BINARY.stat().st_size
        == 648 * np.dtype("<f4").itemsize,
    }
    if not all(validations.values()):
        raise SystemExit(f"Android FSL asset validation failed: {validations}")
    return validations


def main() -> int:
    os.environ.setdefault("TF_CPP_MIN_LOG_LEVEL", "2")
    import tensorflow as tf

    manifest, label_map = load_and_validate_contract()
    PROFILE_ASSET_ROOT.mkdir(parents=True, exist_ok=True)
    copy_atomic(SOURCE_MODEL, ASSET_MODEL)
    copy_atomic(SOURCE_LABELS, ASSET_LABELS)
    copy_atomic(SOURCE_RUNTIME_MANIFEST, ASSET_RUNTIME_MANIFEST)

    golden_expected = build_golden_tflite_fixture(tf, manifest, label_map)
    golden_features, golden_features_binary = build_golden_feature_fixture()
    write_json_atomic(ASSET_GOLDEN_EXPECTED, golden_expected)
    write_json_atomic(ASSET_GOLDEN_FEATURES, golden_features)
    write_bytes_atomic(ASSET_GOLDEN_FEATURES_BINARY, golden_features_binary)
    validations = validate_written_assets(golden_expected, golden_features)

    print(f"Android FSL profile assets built: {PROFILE_ID}")
    print(f"Directory: {PROFILE_ASSET_ROOT}")
    for name, passed in validations.items():
        print(f"{name}: {passed}")
    top1 = golden_expected["expected_output"]
    print(
        "Golden TFLite result: "
        f"{top1['top1_token']} p={top1['top1_probability']:.9f} "
        f"margin={top1['margin']:.9f}"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
