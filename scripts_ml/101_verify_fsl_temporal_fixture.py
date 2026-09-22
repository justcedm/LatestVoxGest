"""Verify the shared Python/JVM complete-event resampling fixture."""

from __future__ import annotations

import json
from pathlib import Path

import numpy as np

REPO_ROOT = Path(__file__).resolve().parents[1]
FIXTURE = (
    REPO_ROOT / "android_dry_run" / "app" / "src" / "test" / "resources"
    / "fsl_practical15_temporal_golden.json"
)


def resample(sequence: np.ndarray, length: int) -> np.ndarray:
    target = np.linspace(0, len(sequence) - 1, length, dtype=np.float32)
    low = np.floor(target).astype(np.int64)
    high = np.minimum(low + 1, len(sequence) - 1)
    alpha = target - low
    return ((1.0 - alpha) * sequence[low] + alpha * sequence[high]).astype(np.float32)


def main() -> int:
    fixture = json.loads(FIXTURE.read_text(encoding="utf-8"))
    source = np.asarray(fixture["source_values"], dtype=np.float32)
    expected = np.asarray(fixture["expected_values"], dtype=np.float32)
    actual = resample(source, int(fixture["target_length"]))
    difference = float(np.max(np.abs(actual - expected)))
    if difference > 1e-7:
        raise RuntimeError(f"temporal fixture mismatch: {difference}")
    print(
        json.dumps(
            {
                "status": "PASS",
                "contract": fixture["contract"],
                "shape": [1, int(fixture["target_length"]), 225],
                "maximum_position_difference": difference,
            },
            sort_keys=True,
        )
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
