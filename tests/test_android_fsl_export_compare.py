from __future__ import annotations

import importlib.util
import json
import sys
import tempfile
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SCRIPTS = ROOT / "scripts_ml"
if str(SCRIPTS) not in sys.path:
    sys.path.insert(0, str(SCRIPTS))

SPEC = importlib.util.spec_from_file_location(
    "android_fsl_export_compare",
    SCRIPTS / "88_compare_android_fsl_export.py",
)
assert SPEC and SPEC.loader
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


def test_python_rebuild_matches_android_export_schema(tmp_path: Path) -> None:
    fixture = json.loads(
        (
            ROOT
            / "android_dry_run"
            / "app"
            / "src"
            / "main"
            / "assets"
            / "model"
            / "fsl_onehand162_20f_rdtcn_v2"
            / "golden_feature_fixture.json"
        ).read_text(encoding="utf-8")
    )
    pose = [dict(zip(("x", "y", "z"), point)) for point in fixture["inputs"]["pose"]]
    hand = [
        dict(zip(("x", "y", "z"), point))
        for point in fixture["inputs"]["anatomical_right_hand"]
    ]
    vector = fixture["cases"][0]["expected"]["vector"]
    document = {
        "schema_version": "voxgest_fsl_probe_window_v1",
        "metadata": {
            "feature_version": "onehand162_20f_nose_mcp_z03_v2",
            "profile_id": "fsl_onehand162_20f_rdtcn_v2",
            "shape": [1, 20, 162],
            "dtype": "float32",
            "expected": "AUNTIE",
            "signer_id": "fixture_signer",
            "session_id": "fixture_session",
            "device_id": "fixture_device",
        },
        "window": {
            "raw_frames": [
                {
                    "timestamp_ms": index + 1,
                    "pose_landmarks": pose,
                    "right_hand_landmarks": hand,
                }
                for index in range(20)
            ],
            "canonical_features": [vector for _ in range(20)],
        },
    }
    export = tmp_path / "android_window.json"
    export.write_text(json.dumps(document), encoding="utf-8")

    report = MODULE.compare_export(export)

    assert report["passed"] is True
    assert report["status"] == "PASS"
    assert report["maximum_absolute_error"] <= 1.0e-6


def test_feature_difference_is_reported_as_failure(tmp_path: Path) -> None:
    fixture = json.loads(
        (
            ROOT
            / "android_dry_run"
            / "app"
            / "src"
            / "main"
            / "assets"
            / "model"
            / "fsl_onehand162_20f_rdtcn_v2"
            / "golden_feature_fixture.json"
        ).read_text(encoding="utf-8")
    )
    pose = [dict(zip(("x", "y", "z"), point)) for point in fixture["inputs"]["pose"]]
    hand = [
        dict(zip(("x", "y", "z"), point))
        for point in fixture["inputs"]["anatomical_right_hand"]
    ]
    vector = list(fixture["cases"][0]["expected"]["vector"])
    altered = list(vector)
    altered[100] += 0.01
    document = {
        "schema_version": "voxgest_fsl_probe_window_v1",
        "metadata": {
            "feature_version": "onehand162_20f_nose_mcp_z03_v2",
            "profile_id": "fsl_onehand162_20f_rdtcn_v2",
            "shape": [1, 20, 162],
            "dtype": "float32",
            "expected": "AUNTIE",
            "signer_id": "fixture_signer",
            "session_id": "fixture_session",
            "device_id": "fixture_device",
        },
        "window": {
            "raw_frames": [
                {
                    "timestamp_ms": index + 1,
                    "pose_landmarks": pose,
                    "right_hand_landmarks": hand,
                }
                for index in range(20)
            ],
            "canonical_features": [altered] + [vector for _ in range(19)],
        },
    }
    export = tmp_path / "bad_android_window.json"
    export.write_text(json.dumps(document), encoding="utf-8")

    report = MODULE.compare_export(export)

    assert report["passed"] is False
    assert report["status"] == "FAIL"
    assert report["maximum_absolute_error"] > 0.009


if __name__ == "__main__":
    with tempfile.TemporaryDirectory() as first:
        test_python_rebuild_matches_android_export_schema(Path(first))
    with tempfile.TemporaryDirectory() as second:
        test_feature_difference_is_reported_as_failure(Path(second))
    print("android_fsl_export_compare_tests=PASS")
