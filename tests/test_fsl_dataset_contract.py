from __future__ import annotations

import json
import sys
import tempfile
import unittest
from pathlib import Path

import numpy as np


ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts_ml"))

from collect_fsl_alphabet_seq import save_sequence  # noqa: E402
from fsl_dataset import (  # noqa: E402
    SampleRecord,
    assign_grouped_splits,
    assignment_digest,
    duplicate_report,
)
from voxgest_feature_builder import (  # noqa: E402
    EXPECTED_SEQUENCE_SHAPE,
    FEATURE_VERSION,
)


def sample(label: str, signer: str, index: int, digest: str | None = None) -> SampleRecord:
    path = f"external_datasets/fsl_features/{label}/{signer}_w{index}.npy"
    return SampleRecord(
        path=path,
        meta_path=path.replace(".npy", ".meta.json"),
        label=label,
        signer_id=signer,
        recording_session=f"session_{signer}",
        source_video=f"clips/{label}/{signer}.MOV",
        device_model="FSL105_VIDEO",
        source="fsl105",
        feature_version=FEATURE_VERSION,
        feature_version_source="sidecar",
        content_sha256=digest or f"{label}-{signer}-{index}",
        pose_presence_ratio=1.0,
        hand_presence_ratio=1.0,
    )


class FslDatasetContractTests(unittest.TestCase):
    def test_global_signer_split_is_deterministic_and_leakage_free(self) -> None:
        labels = ["A", "B", "C"]
        records = [
            sample(label, f"signer_{signer}", index)
            for signer in range(8)
            for label in labels
            for index in range(2)
        ]
        first, first_info = assign_grouped_splits(records, labels)
        second, second_info = assign_grouped_splits(records, labels)
        self.assertTrue(first_info["all_labels_covered"])
        self.assertTrue(first_info["leakage"]["passed"])
        self.assertEqual(assignment_digest(first), assignment_digest(second))
        self.assertEqual(first_info["groups"], second_info["groups"])

    def test_exact_content_duplicates_are_reported_for_exclusion(self) -> None:
        records = [
            sample("A", "signer_1", 0, digest="same"),
            sample("B", "signer_1", 0, digest="same"),
            sample("C", "signer_2", 0, digest="unique"),
        ]
        report = duplicate_report(records)
        self.assertEqual(report["duplicate_content_hash_count"], 1)
        self.assertEqual(len(report["excluded_paths"]), 2)
        self.assertFalse(report["source_is_duplicate_free"])

    def test_alphabet_save_emits_versioned_float32_pair(self) -> None:
        sequence = np.zeros(EXPECTED_SEQUENCE_SHAPE, dtype=np.float32)
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory)
            npy_path = save_sequence(
                output_root=output,
                label="A",
                signer_id="signer_1",
                session_id="session_1",
                device_id="test_camera",
                selected_hand="right",
                sequence=sequence,
                hand_frames=13,
                pose_frames=13,
            )
            saved = np.load(npy_path, allow_pickle=False)
            metadata = json.loads(npy_path.with_suffix(".meta.json").read_text(encoding="utf-8"))
            self.assertEqual(saved.shape, EXPECTED_SEQUENCE_SHAPE)
            self.assertEqual(saved.dtype, np.float32)
            self.assertEqual(metadata["feature_version"], FEATURE_VERSION)
            self.assertEqual(metadata["mirrored_input"], False)
            self.assertEqual(metadata["selected_hand"], "right")


if __name__ == "__main__":
    unittest.main()
