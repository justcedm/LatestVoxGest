"""Build a non-destructive FSL-105 numeric-folder label manifest."""

from __future__ import annotations

import argparse
import csv
import io
import re
import unicodedata
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[1]
OUTPUT_PATH = (
    REPO_ROOT / "reports" / "fsl_dual_dataset_reset_v1"
    / "FSL105_NUMERIC_LABEL_MANIFEST.csv"
)
FIELDS = ["folder_id", "source_label", "canonical_label", "category"]


def load_csv(path: Path) -> list[dict[str, str]]:
    with path.open("r", encoding="utf-8-sig", newline="") as stream:
        return list(csv.DictReader(stream))


def canonical_label(source: str) -> str:
    value = unicodedata.normalize("NFKD", source.strip())
    value = value.replace("’", "").replace("'", "")
    value = "".join(character for character in value if not unicodedata.combining(character))
    return re.sub(r"[^A-Za-z0-9]+", "_", value).strip("_").upper()


def build(source_root: Path) -> list[dict[str, str]]:
    labels = load_csv(source_root / "labels.csv")
    if len(labels) != 105:
        raise RuntimeError(f"expected 105 labels, got {len(labels)}")
    ids = [int(row["id"]) for row in labels]
    if ids != list(range(105)) or len(set(ids)) != 105:
        raise RuntimeError("labels.csv IDs must be unique and ordered 0..104")
    authoritative = {int(row["id"]): row for row in labels}
    for split_name in ("train.csv", "test.csv"):
        for line_number, row in enumerate(load_csv(source_root / split_name), start=2):
            folder = int(Path(row["vid_path"].replace("\\", "/")).parts[-2])
            row_id = int(row["id_label"])
            if folder != row_id:
                raise RuntimeError(f"{split_name}:{line_number} folder/id mismatch")
            expected = authoritative.get(row_id)
            if expected is None:
                raise RuntimeError(f"{split_name}:{line_number} unmapped ID {row_id}")
            if row["label"] != expected["label"] or row["category"] != expected["category"]:
                raise RuntimeError(f"{split_name}:{line_number} disagrees with labels.csv")
    return [
        {
            "folder_id": row["id"],
            "source_label": row["label"],
            "canonical_label": canonical_label(row["label"]),
            "category": row["category"],
        }
        for row in labels
    ]


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-root", type=Path, required=True)
    args = parser.parse_args()
    source_root = args.source_root.resolve()
    rows = build(source_root)
    output = io.StringIO(newline="")
    writer = csv.DictWriter(output, fieldnames=FIELDS, lineterminator="\n")
    writer.writeheader()
    writer.writerows(rows)
    OUTPUT_PATH.parent.mkdir(parents=True, exist_ok=True)
    temporary = OUTPUT_PATH.with_suffix(".csv.tmp")
    temporary.write_text(output.getvalue(), encoding="utf-8", newline="")
    temporary.replace(OUTPUT_PATH)
    print(f"WROTE={OUTPUT_PATH}")
    print(f"ROWS={len(rows)}")
    print("SOURCE_IDS=0..104")
    print("ORIGINAL_FOLDERS_RENAMED=NO")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
