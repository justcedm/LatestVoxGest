"""Assemble and validate the self-contained experimental Android handoff."""

from __future__ import annotations

import hashlib
import json
import os
import re
import shutil
from pathlib import Path

from fsl_dataset import ROOT, write_json_atomic


HANDOFF_ROOT = ROOT / "android_handoff"
SOURCE_RDTCL = (
    ROOT / "model" / "experimental" / "voxgest_fsl_rdtcn_v2_float16.tflite"
)
SOURCE_ACTIVITY = ROOT / "model" / "voxgest_activity_detector_v1.tflite"
SOURCE_MANIFEST = ROOT / "model" / "runtime_manifest.json"
HANDOFF_RDTCL = HANDOFF_ROOT / SOURCE_RDTCL.name
HANDOFF_ACTIVITY = HANDOFF_ROOT / SOURCE_ACTIVITY.name
HANDOFF_MANIFEST = HANDOFF_ROOT / "runtime_manifest.json"
HANDOFF_LABELS = HANDOFF_ROOT / "class_labels_fsl_v2.json"
GUIDE = HANDOFF_ROOT / "ANDROID_INTEGRATION_GUIDE.md"


def sha256(path: Path) -> str:
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


def android_token(label: str) -> str:
    value = label.upper().replace("’", "").replace("'", "")
    return re.sub(r"[^A-Z0-9]+", "_", value).strip("_")


def main() -> int:
    required_sources = [SOURCE_RDTCL, SOURCE_ACTIVITY, SOURCE_MANIFEST, GUIDE]
    missing = [str(path) for path in required_sources if not path.is_file()]
    if missing:
        raise SystemExit(f"missing handoff prerequisites: {missing}")
    manifest = json.loads(SOURCE_MANIFEST.read_text(encoding="utf-8"))
    labels = manifest.get("class_order")
    if not isinstance(labels, list) or len(labels) != 64:
        raise SystemExit("runtime manifest must contain exactly 64 class labels")
    mapping = {android_token(label): index for index, label in enumerate(labels)}
    if len(mapping) != len(labels) or any(not token for token in mapping):
        raise SystemExit("Android-safe label conversion produced an empty or duplicate token")
    if manifest.get("rdtcn_model_filename") != SOURCE_RDTCL.name:
        raise SystemExit("runtime manifest RD-TCN filename mismatch")
    if manifest.get("activity_detector_filename") != SOURCE_ACTIVITY.name:
        raise SystemExit("runtime manifest activity detector filename mismatch")
    expected_hashes = manifest.get("artifact_sha256", {})
    if expected_hashes.get("rdtcn_float16") != sha256(SOURCE_RDTCL):
        raise SystemExit("runtime manifest RD-TCN hash mismatch")
    if expected_hashes.get("activity_detector") != sha256(SOURCE_ACTIVITY):
        raise SystemExit("runtime manifest activity detector hash mismatch")

    copy_atomic(SOURCE_RDTCL, HANDOFF_RDTCL)
    copy_atomic(SOURCE_ACTIVITY, HANDOFF_ACTIVITY)
    copy_atomic(SOURCE_MANIFEST, HANDOFF_MANIFEST)
    write_json_atomic(HANDOFF_LABELS, mapping)

    validations = {
        "rdtcn_sha256_match": sha256(HANDOFF_RDTCL) == sha256(SOURCE_RDTCL),
        "activity_sha256_match": sha256(HANDOFF_ACTIVITY) == sha256(SOURCE_ACTIVITY),
        "manifest_exact_copy": sha256(HANDOFF_MANIFEST) == sha256(SOURCE_MANIFEST),
        "label_count_is_64": len(mapping) == 64,
        "label_indices_are_exact": sorted(mapping.values()) == list(range(64)),
        "guide_present": GUIDE.is_file(),
    }
    if not all(validations.values()):
        raise SystemExit(f"handoff validation failed: {validations}")
    print("Android handoff assembled and validated")
    for name, passed in validations.items():
        print(f"{name}: {passed}")
    print(f"Directory: {HANDOFF_ROOT}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
