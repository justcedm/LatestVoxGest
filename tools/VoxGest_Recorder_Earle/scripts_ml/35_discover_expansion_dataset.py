"""Discover dataset availability for the sprint30 dynamic-word expansion.

Outputs:
  reports/sprint30_dataset_availability.json
  reports/sprint30_dataset_availability.csv
  reports/sprint30_dataset_summary.md
"""

import csv
import json
import os
import re
from collections import Counter
from datetime import datetime
from pathlib import Path

import numpy as np

from lstm_features import FEAT_SIZE, SEQ_LEN
from word_config import DEMO10_WORDS, NEGATIVE_WORDS, SPRINT30_WORDS


ROOT = Path(__file__).resolve().parents[1]
WLASL_JSON = ROOT / "scripts_ml" / "WLASL_v0.3.json"
VIDEO_DIRS = [ROOT / "wlasl_videos", Path(r"C:\wlasl_videos")]
DATA_DIR = Path(os.environ.get("VOXGEST_LSTM_DATASET", ROOT / "dataset_words_lstm"))
REPORT_DIR = ROOT / "reports"
JSON_OUT = REPORT_DIR / "sprint30_dataset_availability.json"
CSV_OUT = REPORT_DIR / "sprint30_dataset_availability.csv"
MD_OUT = REPORT_DIR / "sprint30_dataset_summary.md"

VIDEO_EXTS = {".mp4", ".avi", ".mov", ".webm", ".mkv"}
MIN_SEQS = int(os.environ.get("VOXGEST_MIN_SEQS_PER_CLASS", "80"))
MIN_GROUPS = int(os.environ.get("VOXGEST_MIN_GROUPS_PER_CLASS", "4"))
TARGET_TOTAL_LABELS = int(os.environ.get("VOXGEST_SPRINT30_TARGET_LABELS", "30"))

TIER_A_CANDIDATES = [
    "SORRY",
    "AGAIN",
    "MORE",
    "UNDERSTAND",
    "PAIN",
    "SICK",
    "HURT",
    "MEDICINE",
    "HOSPITAL",
    "EAT",
    "FOOD",
    "BATHROOM",
    "SLEEP",
    "TIRED",
    "HOME",
    "WAIT",
    "TIME",
    "TODAY",
    "TOMORROW",
    "GOOD",
    "BAD",
    "FINE",
    "CALL",
    "PHONE",
    "NEED",
    "WANT",
    "KNOW",
    "GO",
    "COME",
    "MONEY",
]


def normalize_word(word):
    return str(word).strip().upper().replace(" ", "")


def load_wlasl():
    if not WLASL_JSON.exists():
        return {}
    with open(WLASL_JSON, "r", encoding="utf-8") as f:
        data = json.load(f)
    lookup = {}
    for entry in data:
        gloss = normalize_word(entry.get("gloss", ""))
        if gloss:
            lookup[gloss] = entry.get("instances", [])
    return lookup


def local_videos_for(word):
    files = []
    seen = set()
    for root in VIDEO_DIRS:
        folder = root / word
        if not folder.exists():
            continue
        for path in sorted(folder.iterdir()):
            if path.suffix.lower() not in VIDEO_EXTS:
                continue
            key = str(path.resolve()).lower()
            if key in seen:
                continue
            seen.add(key)
            files.append(path)
    return files


def load_metadata():
    for name in ("metadata_lstm_v2.json", "metadata_lstm_v1.json"):
        path = DATA_DIR / name
        if path.exists():
            with open(path, "r", encoding="utf-8") as f:
                return json.load(f).get("samples", {})
    return {}


def infer_group_id(label, file_name, metadata):
    key = f"{label}/{file_name}"
    if key in metadata:
        return metadata[key].get("source_id", key)
    stem = Path(file_name).stem
    for pattern in (r"(.+)_aug\d+$", r"(manual_.+?)_seq\d+$"):
        match = re.match(pattern, stem)
        if match:
            return f"{label}/{match.group(1)}"
    legacy_match = re.match(r"seq_(\d+)$", stem)
    if legacy_match:
        return f"{label}/legacy_source_{int(legacy_match.group(1)) // 19:04d}"
    return f"{label}/{stem}"


def existing_sequences(word, metadata):
    label_dir = DATA_DIR / word
    stats = {
        "existing_sequences": 0,
        "existing_groups": 0,
        "manual_sequences": 0,
        "external_sequences": 0,
        "wrong_shape_files": 0,
    }
    groups = set()
    if not label_dir.exists():
        return stats
    for path in sorted(label_dir.glob("*.npy")):
        try:
            shape = tuple(np.load(path, mmap_mode="r", allow_pickle=False).shape)
        except Exception:
            stats["wrong_shape_files"] += 1
            continue
        if shape != (SEQ_LEN, FEAT_SIZE):
            stats["wrong_shape_files"] += 1
            continue
        stats["existing_sequences"] += 1
        groups.add(infer_group_id(word, path.name, metadata))
        sample = metadata.get(f"{word}/{path.name}", {})
        if path.name.startswith("manual_") or sample.get("source_video") == "manual_webcam":
            stats["manual_sequences"] += 1
        else:
            stats["external_sequences"] += 1
    stats["existing_groups"] = len(groups)
    return stats


def external_dataset_status():
    roots = []
    names = ("asl_citizen", "aslcitizen", "kaggle", "asl-dataset", "external_asl")
    for path in ROOT.rglob("*"):
        if not path.is_dir():
            continue
        lower = path.name.lower()
        if any(name in lower for name in names):
            roots.append(str(path))
            if len(roots) >= 25:
                break
    return roots


def status_for(row):
    if row["existing_sequences"] >= MIN_SEQS and row["existing_groups"] >= MIN_GROUPS:
        return "ready"
    if row["local_videos"] > 0:
        return "needs_extraction"
    if row["downloadable_references"] > 0:
        return "needs_download"
    return "needs_manual_recording"


def support_score(row):
    score = 0
    score += min(row["existing_sequences"], MIN_SEQS) * 3
    score += min(row["existing_groups"], MIN_GROUPS) * 40
    score += min(row["local_videos"], 20) * 8
    score += min(row["downloadable_references"], 30) * 2
    if row["in_wlasl_metadata"]:
        score += 50
    if row["candidate_word"] in DEMO10_WORDS:
        score += 1000
    if row["candidate_word"] == "NOTHING":
        score += 1000
    return score


def quality(row):
    if row["existing_sequences"] >= MIN_SEQS and row["existing_groups"] >= MIN_GROUPS:
        return "GREEN"
    if row["local_videos"] >= 8 or row["downloadable_references"] >= 8:
        return "YELLOW"
    return "RED"


def build_rows():
    wlasl = load_wlasl()
    metadata = load_metadata()
    words = list(dict.fromkeys(DEMO10_WORDS + TIER_A_CANDIDATES + list(NEGATIVE_WORDS)))
    rows = []
    for word in words:
        instances = wlasl.get(word, [])
        videos = local_videos_for(word)
        row = {
            "candidate_word": word,
            "demo10": word in DEMO10_WORDS or word in NEGATIVE_WORDS,
            "tier": "demo10" if word in DEMO10_WORDS else ("negative" if word in NEGATIVE_WORDS else "A"),
            "in_wlasl_metadata": bool(instances),
            "local_videos": len(videos),
            "downloadable_references": sum(1 for inst in instances if inst.get("url")),
            "wlasl_instances": len(instances),
        }
        row.update(existing_sequences(word, metadata))
        row["status"] = status_for(row)
        row["quality"] = quality(row)
        row["support_score"] = support_score(row)
        rows.append(row)
    return rows


def choose_recommended(rows):
    keep = [row for row in rows if row["demo10"] and row["candidate_word"] not in NEGATIVE_WORDS]
    trainable_candidates = [
        row
        for row in rows
        if not row["demo10"]
        and row["candidate_word"] not in NEGATIVE_WORDS
        and row["existing_sequences"] >= MIN_SEQS
        and row["existing_groups"] >= MIN_GROUPS
    ]
    candidate_backfill = [
        row
        for row in rows
        if not row["demo10"]
        and row["candidate_word"] not in NEGATIVE_WORDS
        and row["quality"] in {"GREEN", "YELLOW"}
        and not (
            row["existing_sequences"] >= MIN_SEQS
            and row["existing_groups"] >= MIN_GROUPS
        )
    ]
    trainable_candidates.sort(key=lambda row: (-row["support_score"], row["candidate_word"]))
    candidate_backfill.sort(key=lambda row: (-row["support_score"], row["candidate_word"]))
    desired_non_negative = TARGET_TOTAL_LABELS - 1
    minimum_non_negative = 19
    selected = keep[:]
    selected_names = {row["candidate_word"] for row in selected}
    for row in trainable_candidates:
        if len(selected) >= desired_non_negative:
            break
        if row["candidate_word"] not in selected_names:
            selected.append(row)
            selected_names.add(row["candidate_word"])
    if len(selected) < minimum_non_negative:
        for row in candidate_backfill:
            if len(selected) >= desired_non_negative:
                break
            if row["candidate_word"] not in selected_names:
                selected.append(row)
                selected_names.add(row["candidate_word"])
    selected_words = [row["candidate_word"] for row in selected if row["candidate_word"] not in NEGATIVE_WORDS]
    if "NOTHING" not in selected_words:
        selected_words.append("NOTHING")
    return selected_words


def write_outputs(rows, recommended):
    REPORT_DIR.mkdir(parents=True, exist_ok=True)
    external_roots = external_dataset_status()
    payload = {
        "generated_at": datetime.now().isoformat(timespec="seconds"),
        "dataset": str(DATA_DIR),
        "wlasl_json": str(WLASL_JSON),
        "video_dirs": [str(path) for path in VIDEO_DIRS],
        "minimum_sequences_per_class": MIN_SEQS,
        "minimum_groups_per_class": MIN_GROUPS,
        "target_total_labels_including_nothing": TARGET_TOTAL_LABELS,
        "sprint30_config_words_without_negative": SPRINT30_WORDS,
        "recommended_final_sprint30_word_list": recommended,
        "external_dataset_roots_detected": external_roots,
        "rows": rows,
        "quality_counts": dict(Counter(row["quality"] for row in rows)),
    }
    with open(JSON_OUT, "w", encoding="utf-8") as f:
        json.dump(payload, f, indent=2)

    fieldnames = list(rows[0].keys()) if rows else []
    with open(CSV_OUT, "w", encoding="utf-8", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=fieldnames)
        writer.writeheader()
        writer.writerows(rows)

    lines = [
        "# Sprint30 Dataset Availability",
        "",
        f"Generated: {payload['generated_at']}",
        f"Dataset: `{DATA_DIR}`",
        f"WLASL metadata: `{WLASL_JSON}`",
        "",
        "## Recommended Sprint30 Labels",
        "",
        ", ".join(recommended),
        "",
        "## Availability",
        "",
        "| Word | Tier | WLASL | Local videos | Download refs | Sequences | Groups | Quality | Status |",
        "| --- | --- | ---: | ---: | ---: | ---: | ---: | --- | --- |",
    ]
    for row in rows:
        lines.append(
            "| "
            f"{row['candidate_word']} | {row['tier']} | {str(row['in_wlasl_metadata'])} | "
            f"{row['local_videos']} | {row['downloadable_references']} | "
            f"{row['existing_sequences']} | {row['existing_groups']} | "
            f"{row['quality']} | {row['status']} |"
        )
    lines.extend(
        [
            "",
            "## External Dataset Roots",
            "",
        ]
    )
    if external_roots:
        for root in external_roots:
            lines.append(f"- `{root}`")
    else:
        lines.append("- No local ASL Citizen/Kaggle-style external dataset folder detected.")
    with open(MD_OUT, "w", encoding="utf-8") as f:
        f.write("\n".join(lines) + "\n")


def main():
    rows = build_rows()
    recommended = choose_recommended(rows)
    write_outputs(rows, recommended)
    print("=" * 76)
    print("VoxGest sprint30 dataset discovery")
    print("=" * 76)
    print(f"JSON: {JSON_OUT}")
    print(f"CSV : {CSV_OUT}")
    print(f"MD  : {MD_OUT}")
    print(f"Recommended labels ({len(recommended)}): {', '.join(recommended)}")
    for row in rows:
        print(
            f"{row['quality']:<6} {row['candidate_word']:<12} "
            f"seq={row['existing_sequences']:>4} groups={row['existing_groups']:>3} "
            f"videos={row['local_videos']:>2} refs={row['downloadable_references']:>3} "
            f"{row['status']}"
        )


if __name__ == "__main__":
    main()
