"""Phrase-v1 teammate recording extraction, merge, and audit helpers.

This module is intentionally limited to external phrase-v1 feature packs. It
does not touch recorder packs, demo10 data, Android code, or training scripts.
"""

from __future__ import annotations

import csv
import hashlib
import json
import re
import shutil
import zipfile
from collections import Counter, defaultdict
from dataclasses import dataclass
from datetime import datetime
from pathlib import Path

import numpy as np


ROOT = Path(__file__).resolve().parents[1]
EXTERNAL_ROOT = ROOT / "external_datasets"
INCOMING_ROOT = EXTERNAL_ROOT / "team_incoming_recorded_features"
REPORTS_DIR = ROOT / "reports"

IGNORED_LABELS = {
    "IS",
    "ARE",
    "A",
    "DO",
    "EAT",
    "HELLO",
    "WATER",
    "THANKYOU",
    "STOP",
    "YES",
    "NO",
    "PLEASE",
    "HELP",
    "DOCTOR",
    "SORRY",
    "PAIN",
    "TIME",
    "WANT",
}

SKIP_PARTS = {
    ".git",
    "__pycache__",
    "recorder_env",
    "voxgest_env",
    "android_dry_run",
}


@dataclass(frozen=True)
class PhraseDatasetConfig:
    profile: str
    output_folder: str
    labels: tuple[str, ...]
    expected_shape: tuple[int, int]
    minimums: dict[str, int]
    preferred: dict[str, int]
    feature_folder_names: tuple[str, ...]


ONEHAND162_PHRASE_V1 = PhraseDatasetConfig(
    profile="onehand162_phrase_v1",
    output_folder="onehand162_phrase_v1_features",
    labels=("WHAT", "YOUR", "NAME", "MY", "NOTHING"),
    expected_shape=(30, 162),
    minimums={
        "WHAT": 120,
        "YOUR": 120,
        "NAME": 120,
        "MY": 80,
        "NOTHING": 180,
    },
    preferred={
        "WHAT": 170,
        "YOUR": 170,
        "NAME": 170,
        "MY": 120,
        "NOTHING": 240,
    },
    feature_folder_names=("onehand162_phrase_v1_features", "dataset_words_lstm"),
)


FULLSIGN225_PHRASE_V1 = PhraseDatasetConfig(
    profile="fullsign225_phrase_v1",
    output_folder="fullsign225_phrase_v1_features",
    labels=(
        "WHAT",
        "YOUR",
        "NAME",
        "MY",
        "YOU",
        "OKAY",
        "STUDENT",
        "WHERE",
        "LIVE",
        "NOTHING",
    ),
    expected_shape=(30, 225),
    minimums={
        "WHAT": 120,
        "YOUR": 120,
        "NAME": 120,
        "MY": 120,
        "YOU": 120,
        "OKAY": 120,
        "STUDENT": 120,
        "WHERE": 120,
        "LIVE": 120,
        "NOTHING": 240,
    },
    preferred={
        "WHAT": 150,
        "YOUR": 150,
        "NAME": 150,
        "MY": 150,
        "YOU": 150,
        "OKAY": 150,
        "STUDENT": 150,
        "WHERE": 150,
        "LIVE": 150,
        "NOTHING": 300,
    },
    feature_folder_names=("fullsign225_phrase_v1_features",),
)

PROFILES = {
    ONEHAND162_PHRASE_V1.profile: ONEHAND162_PHRASE_V1,
    FULLSIGN225_PHRASE_V1.profile: FULLSIGN225_PHRASE_V1,
}

ALL_ALLOWED_LABELS = set().union(*(set(profile.labels) for profile in PROFILES.values()))
KNOWN_LABELS = ALL_ALLOWED_LABELS | IGNORED_LABELS
SIGNER_HINTS = {
    "ANASTACIA": ("anastacia", "anas"),
    "MARIELLA": ("mariella",),
}


def now_iso() -> str:
    return datetime.now().replace(microsecond=0).isoformat()


def safe_name(value: object) -> str:
    text = re.sub(r"[^A-Za-z0-9_-]+", "_", str(value).strip().upper())
    return text.strip("_") or "UNKNOWN"


def rel(path: Path) -> str:
    try:
        return str(path.resolve().relative_to(ROOT.resolve()))
    except ValueError:
        return str(path)


def file_sha1(path: Path) -> str:
    h = hashlib.sha1()
    with path.open("rb") as f:
        for chunk in iter(lambda: f.read(1024 * 1024), b""):
            h.update(chunk)
    return h.hexdigest()


def has_skip_part(path: Path) -> bool:
    return any(part.lower() in SKIP_PARTS for part in path.parts)


def signer_for_zip(path: Path) -> str:
    haystack = str(path).lower()
    for signer, hints in SIGNER_HINTS.items():
        if any(hint in haystack for hint in hints):
            return signer
    return ""


def locate_recorded_zips(root: Path = EXTERNAL_ROOT):
    found = []
    selected = {}
    for zip_path in sorted(root.rglob("*.zip")):
        signer = signer_for_zip(zip_path)
        if not signer:
            continue
        record = {
            "zip": str(zip_path),
            "zip_rel": rel(zip_path),
            "signer": signer,
            "length": zip_path.stat().st_size,
            "last_write_time": datetime.fromtimestamp(zip_path.stat().st_mtime).isoformat(timespec="seconds"),
            "selected": False,
        }
        found.append(record)

        name = zip_path.name.lower()
        is_recorded_pack = "recorded_features" in name
        current = selected.get(signer)
        if current is None:
            selected[signer] = (zip_path, is_recorded_pack)
            continue
        current_path, current_recorded = current
        if is_recorded_pack and not current_recorded:
            selected[signer] = (zip_path, is_recorded_pack)
        elif is_recorded_pack == current_recorded and zip_path.stat().st_mtime > current_path.stat().st_mtime:
            selected[signer] = (zip_path, is_recorded_pack)

    selected_paths = []
    selected_resolved = {path.resolve() for path, _ in selected.values()}
    for record in found:
        if Path(record["zip"]).resolve() in selected_resolved:
            record["selected"] = True
            selected_paths.append(Path(record["zip"]))
    return found, sorted(selected_paths)


def unique_path(path: Path) -> Path:
    if not path.exists():
        return path
    stem = path.stem
    suffix = path.suffix
    for idx in range(2, 10_000):
        candidate = path.with_name(f"{stem}_{idx}{suffix}")
        if not candidate.exists():
            return candidate
    raise RuntimeError(f"Could not find unique path for {path}")


def safe_extract_zip(zip_path: Path, signer: str):
    target_root = INCOMING_ROOT / signer
    target_root.mkdir(parents=True, exist_ok=True)
    extract_dir = unique_path(target_root / safe_name(zip_path.stem))
    extract_dir.mkdir(parents=True, exist_ok=False)

    extracted_files = 0
    skipped_existing = 0
    blocked_members = []
    with zipfile.ZipFile(zip_path) as zipf:
        for member in zipf.infolist():
            if member.is_dir():
                continue
            member_path = Path(member.filename)
            if member_path.is_absolute() or ".." in member_path.parts:
                blocked_members.append(member.filename)
                continue
            dest = (extract_dir / member_path).resolve()
            try:
                dest.relative_to(extract_dir.resolve())
            except ValueError:
                blocked_members.append(member.filename)
                continue
            dest.parent.mkdir(parents=True, exist_ok=True)
            if dest.exists():
                skipped_existing += 1
                dest = unique_path(dest)
            with zipf.open(member) as src, dest.open("wb") as out:
                shutil.copyfileobj(src, out)
            extracted_files += 1

    marker = {
        "zip": str(zip_path),
        "zip_rel": rel(zip_path),
        "signer": signer,
        "extracted_at": now_iso(),
        "extracted_files": extracted_files,
        "skipped_existing": skipped_existing,
        "blocked_members": blocked_members,
    }
    (extract_dir / "_voxgest_extract_manifest.json").write_text(
        json.dumps(marker, indent=2),
        encoding="utf-8",
    )
    marker["extract_dir"] = str(extract_dir)
    marker["extract_dir_rel"] = rel(extract_dir)
    return marker


def extract_latest_recorded_zips():
    found, selected = locate_recorded_zips()
    extracted = []
    for zip_path in selected:
        extracted.append(safe_extract_zip(zip_path, signer_for_zip(zip_path)))
    return found, selected, extracted


def infer_label(path: Path) -> str:
    for part in reversed(path.parts[:-1]):
        upper = safe_name(part)
        if upper in KNOWN_LABELS:
            return upper
    tokens = [safe_name(token) for token in re.split(r"[^A-Za-z0-9]+", path.stem)]
    for token in reversed(tokens):
        if token in KNOWN_LABELS:
            return token
    return ""


def infer_profile_from_shape(shape: tuple[int, ...]) -> str:
    for profile in PROFILES.values():
        if tuple(shape) == profile.expected_shape:
            return profile.profile
    return ""


def load_shape(path: Path):
    arr = np.load(path, allow_pickle=False, mmap_mode="r")
    return tuple(int(dim) for dim in arr.shape)


def source_for_path(path: Path, source_context: dict[str, dict]):
    best_root = None
    for root_text in source_context:
        root = Path(root_text)
        try:
            path.resolve().relative_to(root.resolve())
        except ValueError:
            continue
        if best_root is None or len(str(root)) > len(str(best_root)):
            best_root = root
    if best_root is None:
        signer = safe_name(path.parts[-4]) if len(path.parts) >= 4 else "UNKNOWN"
        return {
            "signer": signer,
            "source_zip": "",
            "source_zip_rel": "",
            "source_folder": str(path.parent),
            "source_folder_rel": rel(path.parent),
            "source_name": safe_name(path.parent.name),
        }
    ctx = source_context[str(best_root)]
    return {
        "signer": ctx["signer"],
        "source_zip": ctx["source_zip"],
        "source_zip_rel": ctx["source_zip_rel"],
        "source_folder": str(best_root),
        "source_folder_rel": rel(best_root),
        "source_name": ctx["source_name"],
    }


def build_source_context(extracted: list[dict]):
    context = {}
    for item in extracted:
        extract_dir = Path(item["extract_dir"])
        source_name = safe_name(Path(item["zip"]).stem)
        context[str(extract_dir)] = {
            "signer": item["signer"],
            "source_zip": item["zip"],
            "source_zip_rel": item["zip_rel"],
            "source_name": source_name,
        }
    return context


def existing_hashes(config: PhraseDatasetConfig):
    out_root = EXTERNAL_ROOT / config.output_folder
    hashes = {}
    if not out_root.exists():
        return hashes
    for file_path in sorted(out_root.rglob("*.npy")):
        if has_skip_part(file_path):
            continue
        try:
            hashes[file_sha1(file_path)] = str(file_path)
        except OSError:
            continue
    return hashes


def destination_for_sample(config: PhraseDatasetConfig, label: str, source: dict, digest: str, counter: Counter):
    out_root = EXTERNAL_ROOT / config.output_folder
    dest_dir = out_root / label
    dest_dir.mkdir(parents=True, exist_ok=True)
    counter_key = (config.profile, source["signer"], source["source_name"], label)
    counter[counter_key] += 1
    base = f"{source['signer']}_{source['source_name']}_{label}_{counter[counter_key]:04d}_{digest[:10]}.npy"
    dest = dest_dir / safe_name(Path(base).stem)
    dest = dest.with_suffix(".npy")
    while dest.exists():
        counter[counter_key] += 1
        base = f"{source['signer']}_{source['source_name']}_{label}_{counter[counter_key]:04d}_{digest[:10]}.npy"
        dest = (dest_dir / safe_name(Path(base).stem)).with_suffix(".npy")
    return dest


def iter_npy_sources(source_dirs: list[Path] | None):
    roots = source_dirs if source_dirs else [INCOMING_ROOT]
    for root in roots:
        if not root.exists():
            continue
        for file_path in sorted(root.rglob("*.npy")):
            if not has_skip_part(file_path):
                yield file_path


def merge_dataset(config: PhraseDatasetConfig, source_dirs: list[Path] | None = None, source_context: dict[str, dict] | None = None):
    source_context = source_context or {}
    out_root = EXTERNAL_ROOT / config.output_folder
    out_root.mkdir(parents=True, exist_ok=True)
    known_hashes = existing_hashes(config)
    copy_counter = Counter()
    records = []

    for file_path in iter_npy_sources(source_dirs):
        source = source_for_path(file_path, source_context)
        label = infer_label(file_path)
        row = {
            "profile": config.profile,
            "source_path": str(file_path),
            "source_path_rel": rel(file_path),
            **source,
            "label": label or "UNKNOWN",
            "status": "",
            "reason": "",
            "shape": "",
            "dest_path": "",
            "dest_path_rel": "",
            "content_sha1": "",
            "error": "",
        }

        if not label:
            row["status"] = "rejected"
            row["reason"] = "unknown_label"
            records.append(row)
            continue
        if label in IGNORED_LABELS:
            row["status"] = "ignored"
            row["reason"] = "ignored_label"
            records.append(row)
            continue

        try:
            shape = load_shape(file_path)
            row["shape"] = "x".join(str(dim) for dim in shape)
        except Exception as exc:
            row["status"] = "rejected"
            row["reason"] = "unreadable"
            row["error"] = str(exc)
            records.append(row)
            continue

        profile_from_shape = infer_profile_from_shape(shape)
        if profile_from_shape != config.profile:
            row["status"] = "rejected"
            row["reason"] = "wrong_shape"
            records.append(row)
            continue
        if label not in config.labels:
            row["status"] = "rejected"
            row["reason"] = "unknown_label"
            records.append(row)
            continue

        digest = file_sha1(file_path)
        row["content_sha1"] = digest
        if digest in known_hashes:
            row["status"] = "skipped"
            row["reason"] = "duplicate_content"
            row["dest_path"] = known_hashes[digest]
            row["dest_path_rel"] = rel(Path(known_hashes[digest]))
            records.append(row)
            continue

        dest = destination_for_sample(config, label, source, digest, copy_counter)
        shutil.copy2(file_path, dest)
        known_hashes[digest] = str(dest)
        row["status"] = "copied"
        row["reason"] = "accepted"
        row["dest_path"] = str(dest)
        row["dest_path_rel"] = rel(dest)
        records.append(row)

    summary = summarize_merge(config, records, out_root)
    write_merge_reports(config, summary, records)
    return summary


def summarize_merge(config: PhraseDatasetConfig, records: list[dict], out_root: Path):
    status_counts = Counter(row["status"] for row in records)
    reason_counts = Counter(row["reason"] for row in records if row["reason"])
    copied_per_label = Counter(row["label"] for row in records if row["status"] == "copied")
    ignored = Counter(row["label"] for row in records if row["reason"] == "ignored_label")
    unknown = Counter(row["label"] for row in records if row["reason"] == "unknown_label")
    return {
        "generated_at": now_iso(),
        "profile": config.profile,
        "output_root": str(out_root),
        "output_root_rel": rel(out_root),
        "expected_shape": list(config.expected_shape),
        "labels": list(config.labels),
        "status_counts": dict(status_counts),
        "reason_counts": dict(reason_counts),
        "copied_per_label": {label: int(copied_per_label.get(label, 0)) for label in config.labels},
        "copied_total": int(status_counts.get("copied", 0)),
        "duplicate_count": int(reason_counts.get("duplicate_content", 0)),
        "ignored_labels": dict(ignored),
        "unknown_labels": dict(unknown),
        "wrong_shape_count": int(reason_counts.get("wrong_shape", 0)),
        "unreadable_count": int(reason_counts.get("unreadable", 0)),
    }


def parse_output_filename(file_path: Path, label: str):
    parts = file_path.stem.split("_")
    signer = safe_name(parts[0]) if parts else "UNKNOWN"
    upper_parts = [safe_name(part) for part in parts]
    try:
        label_idx = upper_parts.index(label)
    except ValueError:
        label_idx = -1
    if label_idx > 1:
        source = "_".join(upper_parts[1:label_idx])
    else:
        source = "UNKNOWN"
    return signer, source


def audit_dataset(
    config: PhraseDatasetConfig,
    zip_files_found: list[dict] | None = None,
    extracted: list[dict] | None = None,
    merge_summary: dict | None = None,
):
    data_root = EXTERNAL_ROOT / config.output_folder
    per_label = Counter()
    per_signer = Counter()
    per_source = Counter()
    per_signer_label = defaultdict(Counter)
    per_signer_source = defaultdict(Counter)
    ignored = Counter()
    unknown = Counter()
    wrong_shape = []
    unreadable = []
    files_checked = 0

    if data_root.exists():
        for file_path in sorted(data_root.rglob("*.npy")):
            if has_skip_part(file_path):
                continue
            label = safe_name(file_path.parent.name)
            if label in IGNORED_LABELS:
                ignored[label] += 1
                continue
            if label not in config.labels:
                unknown[label or "UNKNOWN"] += 1
                continue
            try:
                shape = load_shape(file_path)
            except Exception as exc:
                unreadable.append({"path": str(file_path), "path_rel": rel(file_path), "label": label, "error": str(exc)})
                continue
            if shape != config.expected_shape:
                wrong_shape.append({
                    "path": str(file_path),
                    "path_rel": rel(file_path),
                    "label": label,
                    "shape": list(shape),
                    "expected_shape": list(config.expected_shape),
                })
                continue
            signer, source = parse_output_filename(file_path, label)
            files_checked += 1
            per_label[label] += 1
            per_signer[signer] += 1
            per_source[source] += 1
            per_signer_label[signer][label] += 1
            per_signer_source[signer][source] += 1

    minimum_status = target_status(per_label, config.minimums, "minimum")
    preferred_status = target_status(per_label, config.preferred, "preferred")
    ready_min = all(item["ready"] for item in minimum_status.values())
    ready_preferred = all(item["ready"] for item in preferred_status.values())

    summary = {
        "generated_at": now_iso(),
        "profile": config.profile,
        "dataset_root": str(data_root),
        "dataset_root_rel": rel(data_root),
        "expected_shape": list(config.expected_shape),
        "labels": list(config.labels),
        "zip_files_found": zip_files_found or [],
        "extracted": extracted or [],
        "merge_summary": merge_summary or {},
        "files_checked": files_checked,
        "per_label": {label: int(per_label.get(label, 0)) for label in config.labels},
        "per_signer": dict(sorted(per_signer.items())),
        "per_source": dict(sorted(per_source.items())),
        "per_signer_per_label": {
            signer: {label: int(counter.get(label, 0)) for label in config.labels}
            for signer, counter in sorted(per_signer_label.items())
        },
        "per_signer_per_source": {
            signer: dict(sorted(counter.items()))
            for signer, counter in sorted(per_signer_source.items())
        },
        "ignored_labels": dict(sorted(ignored.items())),
        "unknown_labels": dict(sorted(unknown.items())),
        "wrong_shape_count": len(wrong_shape),
        "wrong_shape_files": wrong_shape,
        "unreadable_count": len(unreadable),
        "unreadable_files": unreadable,
        "minimum_status": minimum_status,
        "preferred_status": preferred_status,
        "ready_min": ready_min,
        "ready_preferred": ready_preferred,
        "missing_minimum": missing_counts(minimum_status, "minimum"),
        "missing_preferred": missing_counts(preferred_status, "preferred"),
    }
    write_audit_reports(config, summary)
    return summary


def target_status(counts: Counter, targets: dict[str, int], key_name: str):
    status = {}
    for label, target in targets.items():
        count = int(counts.get(label, 0))
        status[label] = {
            "count": count,
            key_name: int(target),
            "missing": max(0, int(target) - count),
            "ready": count >= int(target),
        }
    return status


def missing_counts(status: dict[str, dict], target_key: str):
    return {
        label: {
            "count": item["count"],
            target_key: item[target_key],
            "missing": item["missing"],
        }
        for label, item in status.items()
        if item["missing"] > 0
    }


def write_json(path: Path, payload):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(payload, indent=2), encoding="utf-8")


def write_merge_reports(config: PhraseDatasetConfig, summary: dict, records: list[dict]):
    REPORTS_DIR.mkdir(parents=True, exist_ok=True)
    prefix = REPORTS_DIR / f"{config.profile}_merge"
    write_json(prefix.with_suffix(".json"), {"summary": summary, "records": records})
    with prefix.with_suffix(".csv").open("w", encoding="utf-8", newline="") as f:
        fieldnames = [
            "profile",
            "status",
            "reason",
            "signer",
            "label",
            "shape",
            "source_name",
            "source_zip_rel",
            "source_folder_rel",
            "source_path_rel",
            "dest_path_rel",
            "content_sha1",
            "error",
        ]
        writer = csv.DictWriter(f, fieldnames=fieldnames)
        writer.writeheader()
        for row in records:
            writer.writerow({name: row.get(name, "") for name in fieldnames})
    lines = [
        f"# {config.profile} Merge Report",
        "",
        f"- Generated: `{summary['generated_at']}`",
        f"- Output: `{summary['output_root_rel']}`",
        f"- Copied: `{summary['copied_total']}`",
        f"- Duplicates skipped: `{summary['duplicate_count']}`",
        f"- Wrong shape: `{summary['wrong_shape_count']}`",
        f"- Unreadable: `{summary['unreadable_count']}`",
        "",
        "## Copied Per Label",
        "",
        "| Label | Count |",
        "| --- | ---: |",
    ]
    for label in config.labels:
        lines.append(f"| {label} | {summary['copied_per_label'].get(label, 0)} |")
    lines.extend(["", "## Ignored Labels", ""])
    lines.append(", ".join(f"{label}: {count}" for label, count in summary["ignored_labels"].items()) or "None")
    prefix.with_suffix(".md").write_text("\n".join(lines) + "\n", encoding="utf-8")


def write_audit_reports(config: PhraseDatasetConfig, summary: dict):
    REPORTS_DIR.mkdir(parents=True, exist_ok=True)
    prefix = REPORTS_DIR / f"{config.profile}_audit"
    write_json(prefix.with_suffix(".json"), summary)
    with prefix.with_suffix(".csv").open("w", encoding="utf-8", newline="") as f:
        fieldnames = [
            "section",
            "profile",
            "label",
            "signer",
            "source",
            "count",
            "minimum",
            "preferred",
            "ready_min",
            "ready_preferred",
            "missing_min",
            "missing_preferred",
            "path",
            "shape",
            "expected_shape",
            "source_zip",
            "source_folder",
            "reason",
            "error",
        ]
        writer = csv.DictWriter(f, fieldnames=fieldnames)
        writer.writeheader()
        for label in config.labels:
            min_status = summary["minimum_status"][label]
            pref_status = summary["preferred_status"][label]
            writer.writerow({
                "section": "per_label",
                "profile": config.profile,
                "label": label,
                "count": summary["per_label"][label],
                "minimum": min_status["minimum"],
                "preferred": pref_status["preferred"],
                "ready_min": min_status["ready"],
                "ready_preferred": pref_status["ready"],
                "missing_min": min_status["missing"],
                "missing_preferred": pref_status["missing"],
            })
        for signer, count in summary["per_signer"].items():
            writer.writerow({"section": "per_signer", "profile": config.profile, "signer": signer, "count": count})
        for signer, label_counts in summary["per_signer_per_label"].items():
            for label, count in label_counts.items():
                writer.writerow({"section": "per_signer_per_label", "profile": config.profile, "signer": signer, "label": label, "count": count})
        for source, count in summary["per_source"].items():
            writer.writerow({"section": "per_source", "profile": config.profile, "source": source, "count": count})
        for label, count in summary["ignored_labels"].items():
            writer.writerow({"section": "ignored_label", "profile": config.profile, "label": label, "count": count})
        for label, count in summary["unknown_labels"].items():
            writer.writerow({"section": "unknown_label", "profile": config.profile, "label": label, "count": count})
        for item in summary["wrong_shape_files"]:
            writer.writerow({
                "section": "wrong_shape",
                "profile": config.profile,
                "label": item.get("label", ""),
                "path": item.get("path_rel", item.get("path", "")),
                "shape": item.get("shape", ""),
                "expected_shape": item.get("expected_shape", ""),
                "reason": "wrong_shape",
            })
        for item in summary["unreadable_files"]:
            writer.writerow({
                "section": "unreadable",
                "profile": config.profile,
                "label": item.get("label", ""),
                "path": item.get("path_rel", item.get("path", "")),
                "reason": "unreadable",
                "error": item.get("error", ""),
            })

    lines = [
        f"# {config.profile} Audit Report",
        "",
        f"- Generated: `{summary['generated_at']}`",
        f"- Dataset: `{summary['dataset_root_rel']}`",
        f"- Expected shape: `{tuple(summary['expected_shape'])}`",
        f"- Files checked: `{summary['files_checked']}`",
        f"- Ready minimum: `{summary['ready_min']}`",
        f"- Ready preferred: `{summary['ready_preferred']}`",
        f"- Wrong-shape files: `{summary['wrong_shape_count']}`",
        f"- Unreadable files: `{summary['unreadable_count']}`",
        "",
        "## Per Label Counts",
        "",
        "| Label | Count | Min | Min Ready | Preferred | Preferred Ready |",
        "| --- | ---: | ---: | --- | ---: | --- |",
    ]
    for label in config.labels:
        min_status = summary["minimum_status"][label]
        pref_status = summary["preferred_status"][label]
        lines.append(
            f"| {label} | {summary['per_label'][label]} | {min_status['minimum']} | "
            f"{min_status['ready']} | {pref_status['preferred']} | {pref_status['ready']} |"
        )
    lines.extend(["", "## Per Signer Counts", "", "| Signer | Count |", "| --- | ---: |"])
    for signer, count in summary["per_signer"].items():
        lines.append(f"| {signer} | {count} |")
    lines.extend(["", "## Per Signer Per Label", "", "| Signer | Label | Count |", "| --- | --- | ---: |"])
    for signer, label_counts in summary["per_signer_per_label"].items():
        for label in config.labels:
            lines.append(f"| {signer} | {label} | {label_counts.get(label, 0)} |")
    lines.extend(["", "## Sources", "", "| Source | Count |", "| --- | ---: |"])
    for source, count in summary["per_source"].items():
        lines.append(f"| {source} | {count} |")
    lines.extend(["", "## Ignored Labels", ""])
    lines.append(", ".join(f"{label}: {count}" for label, count in summary["ignored_labels"].items()) or "None")
    lines.extend(["", "## Missing Minimum", ""])
    lines.append(", ".join(f"{label}: {item['missing']}" for label, item in summary["missing_minimum"].items()) or "None")
    lines.extend(["", "## Missing Preferred", ""])
    lines.append(", ".join(f"{label}: {item['missing']}" for label, item in summary["missing_preferred"].items()) or "None")
    prefix.with_suffix(".md").write_text("\n".join(lines) + "\n", encoding="utf-8")


def process_latest_recordings():
    zip_files_found, _selected, extracted = extract_latest_recorded_zips()
    source_dirs = [Path(item["extract_dir"]) for item in extracted]
    source_context = build_source_context(extracted)
    merge_summaries = {}
    audit_summaries = {}

    for profile_name, config in PROFILES.items():
        merge_summaries[profile_name] = merge_dataset(config, source_dirs, source_context)
        audit_summaries[profile_name] = audit_dataset(
            config,
            zip_files_found=zip_files_found,
            extracted=extracted,
            merge_summary=merge_summaries[profile_name],
        )

    summary = {
        "generated_at": now_iso(),
        "zip_files_found": zip_files_found,
        "extracted": extracted,
        "merge_summaries": merge_summaries,
        "audit_summaries": {
            name: {
                "per_label": audit["per_label"],
                "per_signer": audit["per_signer"],
                "missing_minimum": audit["missing_minimum"],
                "missing_preferred": audit["missing_preferred"],
                "ready_min": audit["ready_min"],
                "ready_preferred": audit["ready_preferred"],
                "wrong_shape_count": audit["wrong_shape_count"],
                "unreadable_count": audit["unreadable_count"],
                "ignored_labels": audit["ignored_labels"],
                "unknown_labels": audit["unknown_labels"],
            }
            for name, audit in audit_summaries.items()
        },
    }
    write_json(REPORTS_DIR / "phrase_v1_latest_recordings_summary.json", summary)
    write_latest_summary_md(summary)
    print_process_summary(summary)
    return summary, audit_summaries


def write_latest_summary_md(summary: dict):
    lines = [
        "# Phrase V1 Latest Recording Summary",
        "",
        f"- Generated: `{summary['generated_at']}`",
        "",
        "## Zip Files Found",
        "",
    ]
    for item in summary["zip_files_found"]:
        selected = "selected" if item["selected"] else "found"
        lines.append(f"- `{item['zip_rel']}` ({item['signer']}, {selected})")
    lines.extend(["", "## Extraction Folders", ""])
    for item in summary["extracted"]:
        lines.append(f"- `{item['extract_dir_rel']}` from `{item['zip_rel']}`")
    for profile, audit in summary["audit_summaries"].items():
        lines.extend([
            "",
            f"## {profile}",
            "",
            f"- Ready minimum: `{audit['ready_min']}`",
            f"- Ready preferred: `{audit['ready_preferred']}`",
            f"- Wrong shape: `{audit['wrong_shape_count']}`",
            f"- Unreadable: `{audit['unreadable_count']}`",
            "",
            "| Label | Count |",
            "| --- | ---: |",
        ])
        for label, count in audit["per_label"].items():
            lines.append(f"| {label} | {count} |")
    (REPORTS_DIR / "phrase_v1_latest_recordings_summary.md").write_text("\n".join(lines) + "\n", encoding="utf-8")


def print_process_summary(summary: dict):
    print("=" * 72)
    print("Latest phrase-v1 recordings processed")
    print("=" * 72)
    print("Selected zips:")
    for item in summary["zip_files_found"]:
        if item["selected"]:
            print(f"  {item['signer']}: {item['zip_rel']}")
    print("Extracted:")
    for item in summary["extracted"]:
        print(f"  {item['extract_dir_rel']}")
    for profile, audit in summary["audit_summaries"].items():
        print("-" * 72)
        print(profile)
        print(f"  ready_min       : {audit['ready_min']}")
        print(f"  ready_preferred : {audit['ready_preferred']}")
        print(f"  wrong_shape     : {audit['wrong_shape_count']}")
        print(f"  unreadable      : {audit['unreadable_count']}")
        for label, count in audit["per_label"].items():
            print(f"  {label:<10} {count:>5}")
