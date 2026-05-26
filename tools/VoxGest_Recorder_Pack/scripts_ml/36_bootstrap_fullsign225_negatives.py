"""Bootstrap fullsign225 NOTHING samples from existing onehand162 negatives.

This is an experiment-only bridge for the sprint30_fullsign225 profile. The
output files are true 30x225 arrays, but their source is the existing manual
onehand162 hard-negative set. Replace these with native fullsign225 webcam
recordings before treating the profile as demo-ready.
"""

import json
import os
from pathlib import Path

import numpy as np

from lstm_features import (
    FEAT_SIZE,
    FULLSIGN_FEAT_SIZE,
    HAND_SIZE,
    POSE_SIZE,
    SEQ_LEN,
    configured_feature_profile,
    default_dataset_dir_name,
    enforce_nose_anchor,
)


ROOT = Path(__file__).resolve().parents[1]
SOURCE_DIR = Path(os.environ.get("VOXGEST_BOOTSTRAP_SOURCE_DATASET", ROOT / "dataset_words_lstm"))
TARGET_DIR = Path(os.environ.get("VOXGEST_LSTM_DATASET", ROOT / default_dataset_dir_name()))
SOURCE_LABEL = os.environ.get("VOXGEST_BOOTSTRAP_NEGATIVE_LABEL", "NOTHING").strip().upper()
TARGET_LABEL = SOURCE_LABEL
SOURCE_META = SOURCE_DIR / "metadata_lstm_v2.json"
TARGET_META = TARGET_DIR / "metadata_lstm_v2.json"


def load_metadata(path):
    if path.exists():
        with open(path, "r", encoding="utf-8") as f:
            return json.load(f)
    return {
        "version": 2,
        "seq_len": SEQ_LEN,
        "feature_size": FEAT_SIZE,
        "feature_profile": configured_feature_profile(),
        "samples": {},
    }


def save_metadata(path, payload):
    path.parent.mkdir(parents=True, exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(payload, f, indent=2)


def target_hand_slot(sample_meta):
    hand = str(sample_meta.get("dominant_hand", "right")).strip().lower()
    if hand == "left":
        return "left"
    return "right"


def convert_sequence(seq, sample_meta):
    out = np.zeros((SEQ_LEN, FULLSIGN_FEAT_SIZE), dtype=np.float32)
    out[:, :POSE_SIZE] = seq[:, :POSE_SIZE]
    hand = seq[:, POSE_SIZE : POSE_SIZE + HAND_SIZE]
    if target_hand_slot(sample_meta) == "left":
        out[:, POSE_SIZE : POSE_SIZE + HAND_SIZE] = hand
    else:
        out[:, POSE_SIZE + HAND_SIZE : POSE_SIZE + HAND_SIZE * 2] = hand
    return enforce_nose_anchor(out)


def main():
    if configured_feature_profile() != "fullsign225":
        print("Set VOXGEST_FEATURE_PROFILE='fullsign225' before bootstrapping.")
        return

    source_label_dir = SOURCE_DIR / SOURCE_LABEL
    target_label_dir = TARGET_DIR / TARGET_LABEL
    if not source_label_dir.exists():
        print(f"Missing source label directory: {source_label_dir}")
        return

    source_metadata = load_metadata(SOURCE_META).get("samples", {})
    target_metadata = load_metadata(TARGET_META)
    target_metadata["version"] = 2
    target_metadata["seq_len"] = SEQ_LEN
    target_metadata["feature_size"] = FULLSIGN_FEAT_SIZE
    target_metadata["feature_profile"] = "fullsign225"
    target_metadata.setdefault("samples", {})
    target_label_dir.mkdir(parents=True, exist_ok=True)

    saved = 0
    skipped = 0
    for source_path in sorted(source_label_dir.glob("*.npy")):
        try:
            seq = np.load(source_path, allow_pickle=False)
        except Exception:
            skipped += 1
            continue
        if seq.shape != (SEQ_LEN, POSE_SIZE + HAND_SIZE):
            skipped += 1
            continue

        source_key = f"{SOURCE_LABEL}/{source_path.name}"
        sample_meta = source_metadata.get(source_key, {})
        converted = convert_sequence(seq.astype(np.float32), sample_meta)
        target_name = f"bootstrap_fullsign225_{source_path.stem}.npy"
        target_path = target_label_dir / target_name
        np.save(target_path, converted.astype(np.float32))
        source_id = sample_meta.get("source_id") or f"{SOURCE_LABEL}/{source_path.stem}"
        target_metadata["samples"][f"{TARGET_LABEL}/{target_name}"] = {
            "word": TARGET_LABEL,
            "source_id": f"{TARGET_LABEL}/bootstrap_fullsign225/{source_id}",
            "source_video": "bootstrap_from_onehand162_manual_negative",
            "source_contract": "onehand162",
            "feature_profile": "fullsign225",
            "augment_index": int(sample_meta.get("augment_index", 0)),
            "shape": [SEQ_LEN, FULLSIGN_FEAT_SIZE],
            "dominant_hand": sample_meta.get("dominant_hand", "right"),
            "physical_slot": target_hand_slot(sample_meta),
            "single_hand_pose": False,
            "mirrored_input": bool(sample_meta.get("mirrored_input", True)),
            "no_output_label": True,
        }
        saved += 1

    save_metadata(TARGET_META, target_metadata)
    print("=" * 72)
    print("VoxGest fullsign225 negative bootstrap")
    print("=" * 72)
    print(f"Source : {source_label_dir}")
    print(f"Target : {target_label_dir}")
    print(f"Saved  : {saved}")
    print(f"Skipped: {skipped}")
    print(f"Meta   : {TARGET_META}")
    print("NOTE   : Replace with native fullsign225 NOTHING recordings before demo use.")


if __name__ == "__main__":
    main()
