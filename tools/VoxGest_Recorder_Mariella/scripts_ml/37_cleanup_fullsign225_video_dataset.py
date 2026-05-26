"""Clean and normalize the VoxGest FullSign225 team video dataset.

The script does not infer labels from video content. A video's label is taken
only from its immediate parent folder name when that folder matches the known
FullSign225 labels.
"""

import argparse
import csv
import json
import shutil
import subprocess
from collections import Counter, defaultdict
from datetime import datetime
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
DEFAULT_INPUT_ROOT = Path(r"G:\My Drive\VOXGEST")
DEFAULT_CLEANED_ROOT = ROOT / "external_datasets" / "fullsign225_team_dataset_cleaned"
DEFAULT_NORMALIZED_ROOT = ROOT / "external_datasets" / "fullsign225_team_dataset_normalized"
REPORT_CSV = ROOT / "reports" / "fullsign225_cleanup_report.csv"
REPORT_JSON = ROOT / "reports" / "fullsign225_cleanup_report.json"
REPORT_MD = ROOT / "reports" / "fullsign225_cleanup_report.md"

KNOWN_LABELS = [
    "YES",
    "NO",
    "PLEASE",
    "WATER",
    "HELLO",
    "HELP",
    "STOP",
    "DOCTOR",
    "NAME",
    "THANKYOU",
    "SORRY",
    "PAIN",
    "EAT",
    "WANT",
    "TIME",
    "MEDICINE",
    "FINE",
    "GO",
    "MORE",
    "UNDERSTAND",
    "AGAIN",
    "NOTHING",
]

VIDEO_EXTENSIONS = {".mp4", ".mov", ".m4v", ".avi", ".mkv", ".webm"}
SESSION_ID = "s01"


def label_key(value):
    return "".join(ch for ch in str(value).upper() if ch.isalnum())


LABEL_BY_KEY = {label_key(label): label for label in KNOWN_LABELS}


def parse_args():
    parser = argparse.ArgumentParser(
        description="Copy FullSign225 team videos into label-safe cleaned names and optionally normalize with ffmpeg."
    )
    parser.add_argument(
        "--input-root",
        default=str(DEFAULT_INPUT_ROOT),
        help=f"Input root. Default: {DEFAULT_INPUT_ROOT}",
    )
    parser.add_argument(
        "--cleaned-root",
        default=str(DEFAULT_CLEANED_ROOT),
        help=f"Cleaned copy output folder. Default: {DEFAULT_CLEANED_ROOT}",
    )
    parser.add_argument(
        "--normalized-root",
        default=str(DEFAULT_NORMALIZED_ROOT),
        help=f"Normalized mp4 output folder. Default: {DEFAULT_NORMALIZED_ROOT}",
    )
    parser.add_argument(
        "--session-id",
        default=SESSION_ID,
        help="Session/signer token used in cleaned filenames. Default: s01",
    )
    parser.add_argument(
        "--scan-all-input-root",
        action="store_true",
        help="Scan the whole input root even when a fullsign225_team_dataset folder exists.",
    )
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="Build reports without copying or converting files.",
    )
    parser.add_argument(
        "--skip-normalize",
        action="store_true",
        help="Skip normalized mp4 creation even when ffmpeg is available.",
    )
    parser.add_argument(
        "--clear-output",
        action="store_true",
        help="Clear generated cleaned/normalized output folders before writing.",
    )
    return parser.parse_args()


def resolve_scan_roots(input_root, scan_all_input_root=False):
    """Prefer the explicit FullSign225 dataset folder to avoid mixing onehand162."""
    if scan_all_input_root:
        return [input_root]
    fullsign_root = input_root / "fullsign225_team_dataset"
    if fullsign_root.exists():
        return [fullsign_root]
    return [input_root]


def find_ffmpeg():
    return shutil.which("ffmpeg")


def discover_video_files(scan_roots):
    files = []
    for scan_root in scan_roots:
        if not scan_root.exists():
            continue
        for path in scan_root.rglob("*"):
            if not path.is_file():
                continue
            if path.suffix.lower() not in VIDEO_EXTENSIONS:
                continue
            parent_label = LABEL_BY_KEY.get(label_key(path.parent.name))
            if parent_label is None:
                continue
            files.append((parent_label, path))
    return sorted(files, key=lambda item: (KNOWN_LABELS.index(item[0]), str(item[1]).casefold()))


def cleaned_filename(label, session_id, index, source_suffix):
    return f"{label}_{session_id}_fullsign225_{index:03d}{source_suffix.lower()}"


def normalized_filename(label, session_id, index):
    return f"{label}_{session_id}_fullsign225_{index:03d}.mp4"


def copy_cleaned(source_path, cleaned_path, dry_run=False):
    if dry_run:
        return "dry_run"
    cleaned_path.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(source_path, cleaned_path)
    return "copied"


def safe_clear_generated_output(path):
    """Remove a generated output folder only when it is under external_datasets."""
    external_root = (ROOT / "external_datasets").resolve()
    target = path.resolve()
    allowed_names = {
        "fullsign225_team_dataset_cleaned",
        "fullsign225_team_dataset_normalized",
    }
    if target.name not in allowed_names:
        raise RuntimeError(f"Refusing to clear unexpected output folder: {target}")
    if external_root != target and external_root not in target.parents:
        raise RuntimeError(f"Refusing to clear folder outside external_datasets: {target}")
    if target.exists():
        shutil.rmtree(target)


def normalize_video(source_path, normalized_path, ffmpeg_path, dry_run=False, skip_normalize=False):
    if skip_normalize:
        return False, "skipped", "normalization skipped by --skip-normalize"
    if ffmpeg_path is None:
        return False, "skipped_ffmpeg_missing", "ffmpeg not found on PATH"
    if dry_run:
        return False, "dry_run", "dry run; ffmpeg not executed"

    normalized_path.parent.mkdir(parents=True, exist_ok=True)
    command = [
        ffmpeg_path,
        "-y",
        "-hide_banner",
        "-loglevel",
        "error",
        "-i",
        str(source_path),
        "-vf",
        "scale=-2:720,fps=30",
        "-an",
        "-c:v",
        "libx264",
        "-pix_fmt",
        "yuv420p",
        "-movflags",
        "+faststart",
        str(normalized_path),
    ]
    try:
        completed = subprocess.run(
            command,
            check=False,
            capture_output=True,
            text=True,
        )
    except OSError as exc:
        return False, "failed", str(exc)

    if completed.returncode != 0:
        message = (completed.stderr or completed.stdout or "ffmpeg failed").strip()
        return False, "failed", message
    return True, "success", "normalized to H.264 mp4, 30fps, 720p height, no audio"


def build_markdown(payload):
    lines = [
        "# FullSign225 Dataset Cleanup Report",
        "",
        f"Generated: {payload['generated_at']}",
        f"Input root: `{payload['input_root']}`",
        f"Scan roots: {', '.join(f'`{item}`' for item in payload['scan_roots'])}",
        f"Cleaned output: `{payload['cleaned_root']}`",
        f"Normalized output: `{payload['normalized_root']}`",
        f"ffmpeg: `{payload['ffmpeg_path'] or 'not found'}`",
        "",
        "## Totals",
        "",
        f"- Files discovered: {payload['totals']['files_discovered']}",
        f"- Cleaned copies written: {payload['totals']['cleaned_written']}",
        f"- Normalization success: {payload['totals']['normalization_success']}",
        f"- Normalization failed/skipped: {payload['totals']['normalization_failed_or_skipped']}",
        "",
        "## Files Per Label",
        "",
        "| Label | Files |",
        "| --- | ---: |",
    ]
    for label in KNOWN_LABELS:
        lines.append(f"| {label} | {payload['files_per_label'].get(label, 0)} |")

    lines.extend(
        [
            "",
            "## Labels Under 10 Videos",
            "",
        ]
    )
    labels_under_10 = payload["labels_under_10"]
    lines.append(", ".join(labels_under_10) if labels_under_10 else "None")

    lines.extend(
        [
            "",
            "## Labels Under 30 Videos",
            "",
        ]
    )
    labels_under_30 = payload["labels_under_30"]
    lines.append(", ".join(labels_under_30) if labels_under_30 else "None")

    lines.extend(
        [
            "",
            "## Notes",
            "",
            "- Labels are taken only from the immediate parent folder.",
            "- Original videos are not deleted or modified.",
            "- Cleaned files preserve the original video bytes by copy.",
            "- Normalized files require ffmpeg and are written as H.264 mp4, 30fps, 720p height, with audio removed.",
        ]
    )
    return "\n".join(lines) + "\n"


def write_reports(records, payload):
    REPORT_CSV.parent.mkdir(parents=True, exist_ok=True)
    fields = [
        "original_filename",
        "original_path",
        "detected_label",
        "cleaned_filename",
        "cleaned_path",
        "extension",
        "normalized_filename",
        "normalized_path",
        "conversion_success",
        "conversion_status",
        "conversion_message",
    ]
    with open(REPORT_CSV, "w", encoding="utf-8", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=fields)
        writer.writeheader()
        writer.writerows(records)

    with open(REPORT_JSON, "w", encoding="utf-8") as f:
        json.dump(payload, f, indent=2)

    with open(REPORT_MD, "w", encoding="utf-8") as f:
        f.write(build_markdown(payload))


def main():
    args = parse_args()
    input_root = Path(args.input_root)
    cleaned_root = Path(args.cleaned_root)
    normalized_root = Path(args.normalized_root)
    scan_roots = resolve_scan_roots(input_root, args.scan_all_input_root)
    ffmpeg_path = find_ffmpeg()
    discovered = discover_video_files(scan_roots)
    counters = defaultdict(int)
    files_per_label = Counter()
    records = []
    cleaned_written = 0
    normalization_success = 0
    normalization_failed_or_skipped = 0

    if not args.dry_run:
        if args.clear_output:
            safe_clear_generated_output(cleaned_root)
            safe_clear_generated_output(normalized_root)
        cleaned_root.mkdir(parents=True, exist_ok=True)
        normalized_root.mkdir(parents=True, exist_ok=True)

    for label, source_path in discovered:
        counters[label] += 1
        files_per_label[label] += 1
        index = counters[label]
        clean_name = cleaned_filename(label, args.session_id, index, source_path.suffix)
        normalized_name = normalized_filename(label, args.session_id, index)
        cleaned_path = cleaned_root / label / clean_name
        normalized_path = normalized_root / label / normalized_name

        copy_status = copy_cleaned(source_path, cleaned_path, args.dry_run)
        if copy_status in {"copied", "dry_run"}:
            cleaned_written += 1

        success, conversion_status, conversion_message = normalize_video(
            source_path,
            normalized_path,
            ffmpeg_path,
            dry_run=args.dry_run,
            skip_normalize=args.skip_normalize,
        )
        if success:
            normalization_success += 1
        else:
            normalization_failed_or_skipped += 1

        records.append(
            {
                "original_filename": source_path.name,
                "original_path": str(source_path),
                "detected_label": label,
                "cleaned_filename": clean_name,
                "cleaned_path": str(cleaned_path),
                "extension": source_path.suffix,
                "normalized_filename": normalized_name,
                "normalized_path": str(normalized_path),
                "conversion_success": success,
                "conversion_status": conversion_status,
                "conversion_message": conversion_message,
            }
        )

    files_per_label_payload = {label: int(files_per_label.get(label, 0)) for label in KNOWN_LABELS}
    labels_under_10 = [label for label in KNOWN_LABELS if files_per_label_payload[label] < 10]
    labels_under_30 = [label for label in KNOWN_LABELS if files_per_label_payload[label] < 30]
    payload = {
        "generated_at": datetime.now().isoformat(timespec="seconds"),
        "input_root": str(input_root),
        "scan_roots": [str(item) for item in scan_roots],
        "cleaned_root": str(cleaned_root),
        "normalized_root": str(normalized_root),
        "ffmpeg_path": ffmpeg_path,
        "dry_run": bool(args.dry_run),
        "skip_normalize": bool(args.skip_normalize),
        "known_labels": KNOWN_LABELS,
        "files_per_label": files_per_label_payload,
        "labels_under_10": labels_under_10,
        "labels_under_30": labels_under_30,
        "totals": {
            "files_discovered": len(discovered),
            "cleaned_written": cleaned_written,
            "normalization_success": normalization_success,
            "normalization_failed_or_skipped": normalization_failed_or_skipped,
        },
        "records": records,
    }

    write_reports(records, payload)
    print("=" * 72)
    print("VoxGest FullSign225 dataset cleanup")
    print("=" * 72)
    print(f"Input root      : {input_root}")
    print(f"Scan roots      : {', '.join(str(item) for item in scan_roots)}")
    print(f"Cleaned output  : {cleaned_root}")
    print(f"Normalized out  : {normalized_root}")
    print(f"ffmpeg          : {ffmpeg_path or 'not found'}")
    print(f"Videos found    : {len(discovered)}")
    print(f"Cleaned written : {cleaned_written}")
    print(f"Normalized ok   : {normalization_success}")
    print(f"Normalize skipped/failed: {normalization_failed_or_skipped}")
    print(f"CSV report      : {REPORT_CSV}")
    print(f"JSON report     : {REPORT_JSON}")
    print(f"MD report       : {REPORT_MD}")
    if ffmpeg_path is None and not args.skip_normalize:
        print("NOTE: ffmpeg was not found, so normalized mp4 files were not created.")


if __name__ == "__main__":
    main()
