"""Mechanically migrate the deprecated no-sign class terminology to NSAC.

The migration changes text content only.  It intentionally does not rename
legacy files or dataset directories because those names are storage paths and
renaming them would break existing packs and extracted data.
"""

from __future__ import annotations

import os
import subprocess
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
ALL_TEXT_SUFFIXES = {
    ".py",
    ".json",
    ".md",
    ".kt",
    ".java",
    ".bat",
    ".txt",
    ".csv",
    ".xml",
    ".gradle",
    ".kts",
}
IDENTIFIER_SUFFIXES = {".py", ".json", ".kt", ".java"}


def repository_files() -> list[Path]:
    result = subprocess.run(
        ["git", "ls-files", "--cached", "--others", "--exclude-standard"],
        cwd=ROOT,
        check=True,
        capture_output=True,
        text=True,
        encoding="utf-8",
    )
    return [ROOT / value for value in result.stdout.splitlines() if value]


def replace_atomic(path: Path, old: bytes, new: bytes) -> bool:
    content = path.read_bytes()
    updated = content.replace(old, new)
    if updated == content:
        return False
    temporary = path.with_suffix(path.suffix + ".nsac_tmp")
    temporary.write_bytes(updated)
    os.replace(temporary, path)
    return True


def main() -> int:
    deprecated_upper = ("NOT" + "HING").encode("ascii")
    deprecated_lower = deprecated_upper.lower()
    deprecated_title = deprecated_lower.capitalize()
    changed: set[Path] = set()
    for path in repository_files():
        if not path.is_file() or path.suffix.lower() not in ALL_TEXT_SUFFIXES:
            continue
        if replace_atomic(path, deprecated_upper, b"NSAC"):
            changed.add(path)
        if path.suffix.lower() in IDENTIFIER_SUFFIXES:
            if replace_atomic(path, deprecated_lower, b"nsac"):
                changed.add(path)
            if replace_atomic(path, deprecated_title, b"NSAC"):
                changed.add(path)
    print(f"NSAC terminology files changed: {len(changed)}")
    for path in sorted(changed):
        print(path.relative_to(ROOT))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
