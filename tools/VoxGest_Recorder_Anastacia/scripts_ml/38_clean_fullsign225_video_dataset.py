"""Safe cleanup, rename, normalize, and audit for FullSign225 raw videos.

This script treats the immediate parent folder as the only source of truth for
the label. It never infers the word from filename or video content, never
deletes originals, and writes only cleaned/normalized copies plus reports.
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
DEFAULT_INPUT_ROOT = Path(r"G:\My Drive\VOXGEST\fullsign225_team_dataset")
DEFAULT_CLEANED_ROOT = ROOT / "external_datasets" / "fullsign225_team_dataset_cleaned"
DEFAULT_NORMALIZED_ROOT = ROOT / "external_datasets" / "fullsign225_team_dataset_normalized"
REPORT_CSV = ROOT / "reports" / "fullsign225_cleanup_report.csv"
REPORT_JSON = ROOT / "reports" / "fullsign225_cleanup_report.json"
REPORT_MD = ROOT / "reports" / "fullsign225_cleanup_report.md"

KNOWN_LABELS = [
    "DOCTOR",
    "EAT",
    "HELLO",
    "HELP",
    "NAME",
    "NO",
    "NOTHING",
    "PAIN",
    "PLEASE",
    "SORRY",
    "STOP",
    "THANKYOU",
    "TIME",
    "WANT",
    "WATER",
    "YES",
]

CONVERTIBLE_EXTENSIONS = {".mov", ".mp4"}
COMMON_VIDEO_EXTENSIONS = CONVERTIBLE_EXTENSIONS | {".m4v", ".avi", ".mkv", ".webm"}
READY_MIN_VIDEOS = 10
TARGET_MIN_VIDEOS = 30


def parse_args():
    parser = argparse.ArgumentParser(
        description="Clean, rename, normalize, and audit VoxGest FullSign225 raw videos."
    )
    parser.add_argument(
        "--input-root",
        default=str(DEFAULT_INPUT_ROOT),
        help=f"Raw FullSign225 video root. Default: {DEFAULT_INPUT_ROOT}",
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
        "--dry-run",
        action="store_true",
        help="Scan and write reports only; do not copy or run ffmpeg.",
    )
    parser.add_argument(
        "--skip-normalize",
        action="store_true",
        help="Copy cleaned files but skip ffmpeg normalization.",
    )
    parser.add_argument(
        "--clear-output",
        action="store_true",
        help="Clear generated cleaned/normalized output folders before writing.",
    )
    return parser.parse_args()


def label_key(value):
    return "".join(ch for ch in str(value).upper() if ch.isalnum())


LABEL_BY_KEY = {label_key(label): label for label in KNOWN_LABELS}


def find_ffmpeg():
    return shutil.which("ffmpeg")


def safe_clear_output(path):
    external_root = (ROOT / "external_datasets").resolve()
    target = path.resolve()
    allowed = {
        "fullsign225_team_dataset_cleaned",
        "fullsign225_team_dataset_normalized",
    }
    if target.name not in allowed:
        raise RuntimeError(f"Refusing to clear unexpected output folder: {target}")
    if external_root != target and external_root not in target.parents:
        raise RuntimeError(f"Refusing to clear folder outside external_datasets: {target}")
    if target.exists():
        shutil.rmtree(target)


def is_video_file(path):
    return path.suffix.lower() in COMMON_VIDEO_EXTENSIONS


def is_convertible(path):
    return path.suffix.lower() in CONVERTIBLE_EXTENSIONS


def discover_files(input_root):
    valid = []
    manual_review = []
    if not input_root.exists():
        raise FileNotFoundError(f"Input root does not exist: {input_root}")

    for path in sorted(input_root.rglob("*"), key=lambda item: str(item).casefold()):
        if not path.is_file() or not is_video_file(path):
            continue

        label = LABEL_BY_KEY.get(label_key(path.parent.name))
        if label is None:
            manual_review.append(
                {
                    "original_path": str(path),
                    "detected_label": "",
                    "original_filename": path.name,
                    "cleaned_filename": "",
                    "normalized_filename": "",
                    "extension": path.suffix,
                    "conversion_status": "manual_review",
                    "conversion_success": False,
                    "error_message": "video file is outside a known label folder",
                    "manual_review": True,
                }
            )
            continue

        if not is_convertible(path):
            manual_review.append(
                {
                    "original_path": str(path),
                    "detected_label": label,
                    "original_filename": path.name,
                    "cleaned_filename": "",
                    "normalized_filename": "",
                    "extension": path.suffix,
                    "conversion_status": "manual_review",
                    "conversion_success": False,
                    "error_message": "unsupported video extension for this cleanup",
                    "manual_review": True,
                }
            )
            continue

        valid.append((label, path))

    valid.sort(key=lambda item: (KNOWN_LABELS.index(item[0]), str(item[1]).casefold()))
    return valid, manual_review


def cleaned_filename(label, index, suffix):
    return f"{label}_fullsign225_{index:03d}{suffix.lower()}"


def normalized_filename(label, index):
    return f"{label}_fullsign225_{index:03d}.mp4"


def copy_cleaned(source_path, target_path, dry_run):
    if dry_run:
        return "dry_run", ""
    try:
        target_path.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(source_path, target_path)
        return "copied", ""
    except OSError as exc:
        return "copy_failed", str(exc)


def normalize_video(source_path, target_path, ffmpeg_path, dry_run, skip_normalize):
    if skip_normalize:
        return False, "skipped", "normalization skipped by --skip-normalize", False
    if ffmpeg_path is None:
        return False, "skipped", "ffmpeg not found on PATH", False
    if dry_run:
        return False, "dry_run", "dry run; ffmpeg not executed", False

    target_path.parent.mkdir(parents=True, exist_ok=True)
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
        str(target_path),
    ]
    try:
        completed = subprocess.run(
            command,
            check=False,
            capture_output=True,
            text=True,
        )
    except OSError as exc:
        return False, "failed", str(exc), True

    if completed.returncode != 0:
        message = (completed.stderr or completed.stdout or "ffmpeg failed").strip()
        return False, "failed", message, True
    return True, "success", "", False


def summarize(records):
    per_label = {
        label: {
            "total_files": 0,
            "converted_files": 0,
            "failed_files": 0,
            "skipped_files": 0,
        }
        for label in KNOWN_LABELS
    }
    for row in records:
        label = row["detected_label"]
        if label not in per_label or row.get("manual_review"):
            continue
        per_label[label]["total_files"] += 1
        status = row["conversion_status"]
        if status == "success":
            per_label[label]["converted_files"] += 1
        elif status == "failed":
            per_label[label]["failed_files"] += 1
        elif status in {"skipped", "dry_run"}:
            per_label[label]["skipped_files"] += 1

    labels_under_10 = [label for label, stats in per_label.items() if stats["total_files"] < READY_MIN_VIDEOS]
    labels_under_30 = [label for label, stats in per_label.items() if stats["total_files"] < TARGET_MIN_VIDEOS]
    labels_ready = [
        label
        for label, stats in per_label.items()
        if stats["total_files"] >= READY_MIN_VIDEOS and stats["failed_files"] == 0
    ]
    labels_need_more = labels_under_30[:]
    return per_label, labels_under_10, labels_under_30, labels_ready, labels_need_more


def build_markdown(payload):
    lines = [
        "# FullSign225 Cleanup Report",
        "",
        f"Generated: {payload['generated_at']}",
        f"Input root: `{payload['input_root']}`",
        f"Cleaned folder: `{payload['cleaned_root']}`",
        f"Normalized folder: `{payload['normalized_root']}`",
        f"ffmpeg: `{payload['ffmpeg_path'] or 'not found'}`",
        f"Dry run: `{payload['dry_run']}`",
        "",
        "## Totals",
        "",
        f"- Valid videos: {payload['totals']['valid_videos']}",
        f"- Manual review files: {payload['totals']['manual_review_files']}",
        f"- Cleaned copies: {payload['totals']['cleaned_copies']}",
        f"- Converted files: {payload['totals']['converted_files']}",
        f"- Failed conversions: {payload['totals']['failed_files']}",
        f"- Skipped conversions: {payload['totals']['skipped_files']}",
        "",
        "## Per Label",
        "",
        "| Label | Total | Converted | Failed | Skipped |",
        "| --- | ---: | ---: | ---: | ---: |",
    ]
    for label in KNOWN_LABELS:
        stats = payload["per_label"][label]
        lines.append(
            f"| {label} | {stats['total_files']} | {stats['converted_files']} | "
            f"{stats['failed_files']} | {stats['skipped_files']} |"
        )

    lines.extend(
        [
            "",
            "## Labels Under 10 Videos",
            "",
            ", ".join(payload["labels_under_10"]) if payload["labels_under_10"] else "None",
            "",
            "## Labels Under 30 Videos",
            "",
            ", ".join(payload["labels_under_30"]) if payload["labels_under_30"] else "None",
            "",
            "## Labels Ready For Extraction",
            "",
            ", ".join(payload["labels_ready_for_extraction"])
            if payload["labels_ready_for_extraction"]
            else "None",
            "",
            "## Labels Needing More Recordings",
            "",
            ", ".join(payload["labels_needing_more_recordings"])
            if payload["labels_needing_more_recordings"]
            else "None",
            "",
            "## Manual Review",
            "",
        ]
    )
    if payload["manual_review"]:
        for row in payload["manual_review"]:
            lines.append(f"- `{row['original_path']}`: {row['error_message']}")
    else:
        lines.append("None")

    lines.extend(
        [
            "",
            "## Notes",
            "",
            "- The parent folder name is the label.",
            "- Original videos are never modified or deleted.",
            "- Cleaned copies use `LABEL_fullsign225_001.ext` naming.",
            "- Normalized videos are H.264 mp4, 30 FPS, 720p height, aspect ratio preserved, audio removed.",
            "- If ffmpeg is missing, conversion is reported as skipped.",
        ]
    )
    return "\n".join(lines) + "\n"


def write_reports(records, payload):
    REPORT_CSV.parent.mkdir(parents=True, exist_ok=True)
    fields = [
        "original_path",
        "detected_label",
        "original_filename",
        "cleaned_filename",
        "normalized_filename",
        "extension",
        "conversion_status",
        "conversion_success",
        "error_message",
        "manual_review",
    ]
    with open(REPORT_CSV, "w", encoding="utf-8", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=fields)
        writer.writeheader()
        writer.writerows(records)

    with open(REPORT_JSON, "w", encoding="utf-8") as f:
        json.dump(payload, f, indent=2)

    with open(REPORT_MD, "w", encoding="utf-8") as f:
        f.write(build_markdown(payload))


def print_summary(payload):
    print("=" * 72)
    print("VoxGest FullSign225 cleanup/audit")
    print("=" * 72)
    print(f"Input root       : {payload['input_root']}")
    print(f"Cleaned output   : {payload['cleaned_root']}")
    print(f"Normalized output: {payload['normalized_root']}")
    print(f"ffmpeg           : {payload['ffmpeg_path'] or 'not found'}")
    print(f"Valid videos     : {payload['totals']['valid_videos']}")
    print(f"Manual review    : {payload['totals']['manual_review_files']}")
    print(f"Cleaned copies   : {payload['totals']['cleaned_copies']}")
    print(f"Converted        : {payload['totals']['converted_files']}")
    print(f"Failed           : {payload['totals']['failed_files']}")
    print(f"Skipped          : {payload['totals']['skipped_files']}")
    print(f"CSV report       : {REPORT_CSV}")
    print(f"JSON report      : {REPORT_JSON}")
    print(f"Markdown report  : {REPORT_MD}")
    if payload["ffmpeg_path"] is None and not payload["skip_normalize"]:
        print("NOTE: ffmpeg was not found, so normalization was skipped.")


def main():
    args = parse_args()
    input_root = Path(args.input_root)
    cleaned_root = Path(args.cleaned_root)
    normalized_root = Path(args.normalized_root)
    ffmpeg_path = find_ffmpeg()
    valid_files, manual_review = discover_files(input_root)
    counters = defaultdict(int)
    records = []
    totals = Counter()

    if not args.dry_run:
        if args.clear_output:
            safe_clear_output(cleaned_root)
            safe_clear_output(normalized_root)
        cleaned_root.mkdir(parents=True, exist_ok=True)
        normalized_root.mkdir(parents=True, exist_ok=True)

    for label, source_path in valid_files:
        counters[label] += 1
        index = counters[label]
        clean_name = cleaned_filename(label, index, source_path.suffix)
        normalized_name = normalized_filename(label, index)
        cleaned_path = cleaned_root / label / clean_name
        normalized_path = normalized_root / label / normalized_name

        copy_status, copy_error = copy_cleaned(source_path, cleaned_path, args.dry_run)
        if copy_status in {"copied", "dry_run"}:
            totals["cleaned_copies"] += 1

        success, conversion_status, conversion_error, failed = normalize_video(
            source_path=source_path,
            target_path=normalized_path,
            ffmpeg_path=ffmpeg_path,
            dry_run=args.dry_run,
            skip_normalize=args.skip_normalize,
        )
        if success:
            totals["converted_files"] += 1
        elif failed:
            totals["failed_files"] += 1
        else:
            totals["skipped_files"] += 1

        error_message = copy_error or conversion_error
        records.append(
            {
                "original_path": str(source_path),
                "detected_label": label,
                "original_filename": source_path.name,
                "cleaned_filename": clean_name,
                "normalized_filename": normalized_name,
                "extension": source_path.suffix,
                "conversion_status": conversion_status,
                "conversion_success": bool(success),
                "error_message": error_message,
                "manual_review": False,
            }
        )

    records.extend(manual_review)
    per_label, labels_under_10, labels_under_30, labels_ready, labels_need_more = summarize(records)
    payload = {
        "generated_at": datetime.now().isoformat(timespec="seconds"),
        "input_root": str(input_root),
        "cleaned_root": str(cleaned_root),
        "normalized_root": str(normalized_root),
        "ffmpeg_path": ffmpeg_path,
        "dry_run": bool(args.dry_run),
        "skip_normalize": bool(args.skip_normalize),
        "known_labels": KNOWN_LABELS,
        "per_label": per_label,
        "labels_under_10": labels_under_10,
        "labels_under_30": labels_under_30,
        "labels_ready_for_extraction": labels_ready,
        "labels_needing_more_recordings": labels_need_more,
        "manual_review": manual_review,
        "totals": {
            "valid_videos": len(valid_files),
            "manual_review_files": len(manual_review),
            "cleaned_copies": int(totals["cleaned_copies"]),
            "converted_files": int(totals["converted_files"]),
            "failed_files": int(totals["failed_files"]),
            "skipped_files": int(totals["skipped_files"]),
        },
        "records": records,
    }
    write_reports(records, payload)
    print_summary(payload)


if __name__ == "__main__":
    main()
