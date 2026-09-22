"""Create the FSL phone-export and feature-folder structure.

This script is intentionally additive and re-runnable. It creates missing
folders and a README, then prints current JSON/NPY counts without deleting or
rewriting existing samples.
"""

from __future__ import annotations

from pathlib import Path

from fsl_config import FSL_LABELS


ROOT = Path(__file__).resolve().parents[1]
PHONE_EXPORT_ROOT = ROOT / "external_datasets" / "fsl_phone_exports"
FSL_EXPORT_ROOT = PHONE_EXPORT_ROOT / "VoxGestCalibration" / "fsl_phrase_v1"
FSL_FEATURE_ROOT = ROOT / "external_datasets" / "fsl_features"
FSL_REPORTS_DIR = ROOT / "reports" / "fsl"
README_PATH = PHONE_EXPORT_ROOT / "README.txt"


README_TEXT = """VoxGest FSL phone export drop folder

Drop JSON exports from the Android calibration app into the matching label
subfolder:

  external_datasets/fsl_phone_exports/VoxGestCalibration/fsl_phrase_v1/<LABEL>/

Expected filename format:

  <LABEL>_<DEVICE>_<TIMESTAMP>_<INDEX>.json

The import script keeps the FSL dataset separate from existing ASL/onehand162
artifacts and writes converted .npy files under external_datasets/fsl_features/.
"""


def count_files(path: Path, pattern: str) -> int:
    return sum(1 for _ in path.glob(pattern)) if path.exists() else 0


def setup_dirs() -> list[tuple[str, Path, int]]:
    created: list[tuple[str, Path, int]] = []
    PHONE_EXPORT_ROOT.mkdir(parents=True, exist_ok=True)
    FSL_REPORTS_DIR.mkdir(parents=True, exist_ok=True)
    README_PATH.write_text(README_TEXT, encoding="utf-8")
    created.append(("README", README_PATH, 1 if README_PATH.exists() else 0))

    for label in FSL_LABELS:
        export_dir = FSL_EXPORT_ROOT / label
        feature_dir = FSL_FEATURE_ROOT / label
        export_dir.mkdir(parents=True, exist_ok=True)
        feature_dir.mkdir(parents=True, exist_ok=True)
        created.append((f"export/{label}", export_dir, count_files(export_dir, "*.json")))
        created.append((f"features/{label}", feature_dir, count_files(feature_dir, "*.npy")))

    created.append(("reports/fsl", FSL_REPORTS_DIR, count_files(FSL_REPORTS_DIR, "*")))
    return created


def main() -> None:
    items = setup_dirs()
    print("FSL dataset directories ready")
    print(f"Phone exports: {FSL_EXPORT_ROOT}")
    print(f"Features     : {FSL_FEATURE_ROOT}")
    print(f"Reports      : {FSL_REPORTS_DIR}")
    print("")
    print("Checklist")
    for name, path, count in items:
        rel = path.relative_to(ROOT) if path.is_relative_to(ROOT) else path
        print(f"[OK] {name:<18} count={count:<4} path={rel}")


if __name__ == "__main__":
    main()
