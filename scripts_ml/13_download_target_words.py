"""Download the shared VoxGest target-word video set from WLASL/YouTube."""

import json
import os
import subprocess
import sys
from pathlib import Path

from word_config import TARGET_WORDS, WORD_PROFILE

os.environ["PROTOCOL_BUFFERS_PYTHON_IMPLEMENTATION"] = "python"

ROOT = Path(__file__).resolve().parents[1]
WLASL_JSON = ROOT / "scripts_ml" / "WLASL_v0.3.json"
VIDEO_DIR = ROOT / "wlasl_videos"
MIN_VIDEOS = 15
VIDEO_EXTS = (".mp4", ".avi", ".mov", ".webm", ".mkv")


def load_wlasl(json_path):
    with open(json_path, "r", encoding="utf-8") as f:
        data = json.load(f)

    lookup = {}
    for entry in data:
        word = entry["gloss"].lower().replace(" ", "")
        lookup[word] = entry.get("instances", [])
    return lookup


def video_count(save_dir):
    if not save_dir.exists():
        return 0
    return sum(1 for f in save_dir.iterdir() if f.suffix.lower() in VIDEO_EXTS)


def download_wlasl_word(word, instances, save_dir, min_needed=MIN_VIDEOS):
    save_dir.mkdir(parents=True, exist_ok=True)
    existing = video_count(save_dir)
    if existing >= min_needed:
        print(f"  {word}: already has {existing} videos, skipping")
        return existing

    downloaded = existing
    for inst in instances:
        if downloaded >= min_needed * 2:
            break

        url = inst.get("url", "")
        vid_id = inst.get("video_id", f"vid_{downloaded}")
        if not url:
            continue

        out_path = save_dir / f"{vid_id}.mp4"
        if out_path.exists():
            downloaded = video_count(save_dir)
            continue

        try:
            subprocess.run(
                [
                    "yt-dlp",
                    "-q",
                    "--no-warnings",
                    "-f",
                    "worst[ext=mp4]/worst",
                    "-o",
                    str(out_path),
                    url,
                ],
                timeout=30,
                capture_output=True,
                check=False,
            )
            downloaded = video_count(save_dir)
        except Exception:
            pass

    return downloaded


def download_youtube_fallback(word, save_dir, count_needed=MIN_VIDEOS):
    """Search YouTube for ASL word-sign videos."""
    save_dir.mkdir(parents=True, exist_ok=True)
    existing = video_count(save_dir)
    if existing >= count_needed:
        return existing

    queries = [
        f"ASL sign for {word} handspeak",
        f"how to sign {word} ASL one hand",
        f"american sign language {word} tutorial",
    ]

    downloaded = existing
    for query in queries:
        if downloaded >= count_needed:
            break

        out_template = save_dir / f"yt_{downloaded}_%(id)s.mp4"
        try:
            subprocess.run(
                [
                    "yt-dlp",
                    "-q",
                    "--no-warnings",
                    "--max-downloads",
                    "3",
                    "-f",
                    "worst[ext=mp4]/worst",
                    "-o",
                    str(out_template),
                    f"ytsearch3:{query}",
                ],
                timeout=60,
                capture_output=True,
                check=False,
            )
            downloaded = video_count(save_dir)
        except Exception:
            pass

    return downloaded


def main():
    print("=" * 60)
    print("  VoxGest - Download Target ASL Words")
    print("=" * 60)
    print(f"  Word profile : {WORD_PROFILE}")
    print(f"  Target words : {len(TARGET_WORDS)}")
    print(f"  WLASL JSON   : {WLASL_JSON}")
    print(f"  Video dir    : {VIDEO_DIR}")

    if not WLASL_JSON.exists():
        print(f"ERROR: WLASL JSON not found at {WLASL_JSON}")
        print("Make sure WLASL_v0.3.json is in scripts_ml.")
        sys.exit(1)

    print("\nLoading WLASL index...")
    wlasl = load_wlasl(WLASL_JSON)
    print(f"WLASL contains {len(wlasl)} word entries")

    results = {}
    for word in TARGET_WORDS:
        word_key = word.lower().replace(" ", "")
        save_dir = VIDEO_DIR / word

        instances = wlasl.get(word_key, [])
        print(f"\n{word}: {len(instances)} WLASL entries", end=" -> ")

        if len(instances) >= 5:
            count = download_wlasl_word(word, instances, save_dir)
        else:
            count = 0

        if count < MIN_VIDEOS:
            print(f"Only {count} from WLASL, trying YouTube...", end=" -> ")
            count = download_youtube_fallback(word, save_dir)

        results[word] = count
        print(f"{count} videos total")

    print("\n" + "=" * 60)
    print("  DOWNLOAD SUMMARY")
    print("=" * 60)
    ok = [(w, n) for w, n in results.items() if n >= MIN_VIDEOS]
    low = [(w, n) for w, n in results.items() if n < MIN_VIDEOS]

    print(f"\n  OK ({len(ok)} words):")
    for word, count in ok:
        print(f"    {word}: {count} videos")

    if low:
        print(f"\n  LOW ({len(low)} words - consider manual recording):")
        for word, count in low:
            print(f"    {word}: {count} videos")

    print(f"\nVideos saved to: {VIDEO_DIR}")
    print("Next: run 18_extract_lstm.py")


if __name__ == "__main__":
    main()
