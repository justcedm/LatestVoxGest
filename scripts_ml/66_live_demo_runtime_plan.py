"""Build a demo-safe runtime plan from recent VoxGest live phrase logs."""

import json
import sys
from collections import Counter, defaultdict
from datetime import datetime
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
REPORT_DIR = ROOT / "reports"
DEFAULT_LOGS = [
    REPORT_DIR / "live_word_test_log_20260525_111019.json",
    REPORT_DIR / "live_word_test_log_20260525_111332.json",
]
QUALITY_CAPTURE_BLOCKS = {
    "quality:BAD_SEQUENCE",
    "quality:LOW_HAND_PRESENCE",
    "quality:UNSTABLE_LANDMARKS",
    "quality:NO_HAND",
}
GATE_BLOCK_PREFIXES = ("low_conf", "low_margin", "low_motion", "low_path", "low_hand")
NEGATIVE_LABELS = {"NOTHING"}


def as_bool(value):
    if isinstance(value, bool):
        return value
    return str(value).strip().lower() in {"1", "true", "yes", "on"}


def split_reasons(value):
    if not value or value == "ok":
        return []
    return [part for part in str(value).split(";") if part]


def default_paths(args):
    if args:
        return [Path(item) for item in args]
    if all(path.exists() for path in DEFAULT_LOGS):
        return DEFAULT_LOGS
    return sorted(REPORT_DIR.glob("live_word_test_log_*.json"), key=lambda p: p.stat().st_mtime)[-2:]


def label_bucket():
    return {
        "trials": 0,
        "matched": 0,
        "accepted_correct": 0,
        "false_accepts": 0,
        "gate_blocked": 0,
        "capture_blocked": 0,
        "inference_blocked": 0,
        "confusions": Counter(),
        "failure_reasons": Counter(),
    }


def load_records(paths):
    logs = []
    for path in paths:
        with open(path, "r", encoding="utf-8") as f:
            payload = json.load(f)
        logs.append((path, payload))
    return logs


def analyze(paths):
    logs = load_records(paths)
    profiles = {}
    for path, payload in logs:
        profile = payload.get("feature_profile", "unknown")
        profile_bucket = profiles.setdefault(
            profile,
            {
                "logs": [],
                "model_paths": Counter(),
                "input_shapes": Counter(),
                "labels": defaultdict(label_bucket),
            },
        )
        profile_bucket["logs"].append(str(path.relative_to(ROOT)))
        profile_bucket["model_paths"][payload.get("model_path", "")] += 1
        profile_bucket["input_shapes"][json.dumps(payload.get("input_shape", []))] += 1

        for row in payload.get("records", []):
            expected = row.get("expected_label", "")
            predicted = row.get("predicted_label", "")
            label = profile_bucket["labels"][expected]
            label["trials"] += 1
            if as_bool(row.get("matched_expected")):
                label["matched"] += 1
            if as_bool(row.get("accepted_by_gate")) and predicted == expected and expected not in NEGATIVE_LABELS:
                label["accepted_correct"] += 1
            if as_bool(row.get("accepted_by_gate")) and not as_bool(row.get("matched_expected")):
                label["false_accepts"] += 1
            if not as_bool(row.get("inference_allowed", True)):
                label["inference_blocked"] += 1

            reasons = split_reasons(row.get("failure_reason", ""))
            label["failure_reasons"].update(reasons)
            if predicted != expected:
                label["confusions"][predicted or "(blocked)"] += 1
            if any(reason in QUALITY_CAPTURE_BLOCKS for reason in reasons):
                label["capture_blocked"] += 1
            if predicted == expected and expected not in NEGATIVE_LABELS:
                if any(reason.startswith(GATE_BLOCK_PREFIXES) for reason in reasons):
                    label["gate_blocked"] += 1

    return profiles


def classify_labels(labels):
    categories = {
        "stable_labels": [],
        "partially_stable_labels": [],
        "unstable_labels": [],
        "gate_blocked_labels": [],
        "capture_blocked_labels": [],
    }
    rows = {}
    for label, item in labels.items():
        trials = max(1, item["trials"])
        match_rate = item["matched"] / trials
        accepted_correct_rate = item["accepted_correct"] / trials
        false_accept_rate = item["false_accepts"] / trials
        gate_block_rate = item["gate_blocked"] / trials
        capture_block_rate = item["capture_blocked"] / trials
        row = {
            "trials": item["trials"],
            "matched": item["matched"],
            "accepted_correct": item["accepted_correct"],
            "false_accepts": item["false_accepts"],
            "gate_blocked": item["gate_blocked"],
            "capture_blocked": item["capture_blocked"],
            "inference_blocked": item["inference_blocked"],
            "match_rate": round(match_rate, 4),
            "accepted_correct_rate": round(accepted_correct_rate, 4),
            "false_accept_rate": round(false_accept_rate, 4),
            "gate_block_rate": round(gate_block_rate, 4),
            "capture_block_rate": round(capture_block_rate, 4),
            "top_confusions": dict(item["confusions"].most_common(5)),
            "top_failure_reasons": dict(item["failure_reasons"].most_common(5)),
        }
        rows[label] = row

        if label in NEGATIVE_LABELS:
            if item["false_accepts"] == 0 and item["accepted_correct"] == 0:
                categories["stable_labels"].append(label)
            else:
                categories["unstable_labels"].append(label)
        elif accepted_correct_rate >= 0.70 and false_accept_rate == 0:
            categories["stable_labels"].append(label)
        elif match_rate > 0.0 or item["accepted_correct"] > 0 or item["gate_blocked"] > 0:
            categories["partially_stable_labels"].append(label)
        else:
            categories["unstable_labels"].append(label)

        if item["gate_blocked"]:
            categories["gate_blocked_labels"].append(label)
        if item["capture_blocked"]:
            categories["capture_blocked_labels"].append(label)

    for names in categories.values():
        names.sort()
    return categories, rows


def build_payload(paths):
    profiles = analyze(paths)
    out_profiles = {}
    for profile, item in profiles.items():
        categories, labels = classify_labels(item["labels"])
        out_profiles[profile] = {
            "logs": item["logs"],
            "model_paths": dict(item["model_paths"]),
            "input_shapes": {shape: count for shape, count in item["input_shapes"].items()},
            "categories": categories,
            "labels": labels,
        }
    return {
        "generated_at": datetime.now().isoformat(timespec="seconds"),
        "logs_analyzed": [str(path.relative_to(ROOT)) for path in paths],
        "recommended_runtime": {
            "VOXGEST_GATE_PROFILE": "demo",
            "VOXGEST_LIVE_TEST_CAPTURE_MODE": "manual_capture",
            "VOXGEST_HAND_READY_FRAMES": "10",
            "VOXGEST_CAPTURE_FRAMES": "30",
            "notes": [
                "Keep strict gate profile as default.",
                "Demo profile lowers low-motion label thresholds.",
                "Manual capture waits for stable hands before recording one gesture window.",
                "NOTHING remains no-output and is never accepted as spoken output.",
            ],
        },
        "profiles": out_profiles,
    }


def write_markdown(path, payload):
    lines = [
        "# VoxGest Demo-Safe Live Runtime Plan",
        "",
        f"- Generated: `{payload['generated_at']}`",
        "- Gate profile: `VOXGEST_GATE_PROFILE=demo`",
        "- Capture mode: `VOXGEST_LIVE_TEST_CAPTURE_MODE=manual_capture`",
        "- Capture timing: wait for 10 stable hand frames, then record exactly 30 frames",
        "- Strict gate remains the default when `VOXGEST_GATE_PROFILE` is unset",
        "- `NOTHING` remains no-output",
        "",
        "## Logs Analyzed",
        "",
    ]
    lines.extend(f"- `{item}`" for item in payload["logs_analyzed"])
    lines.extend(
        [
            "",
            "## Runtime Plan",
            "",
            "1. Use demo gate only during adviser/panel live testing.",
            "2. Start each word with SPACE/C so the model captures the intended 30-frame gesture.",
            "3. Keep LOW_HAND_PRESENCE, NO_HAND, and BAD_SEQUENCE blocked.",
            "4. Treat UNSTABLE_LANDMARKS, LOW_MOTION, and LOW_WRIST_PATH as demo warnings so low-motion signs can still be classified.",
            "5. Prefer stable labels for the first pass, then partial labels if panelists ask for a broader demo.",
            "",
        ]
    )
    for profile, item in payload["profiles"].items():
        cats = item["categories"]
        lines.extend(
            [
                f"## {profile}",
                "",
                f"- Stable labels: `{', '.join(cats['stable_labels']) or 'none'}`",
                f"- Partially stable labels: `{', '.join(cats['partially_stable_labels']) or 'none'}`",
                f"- Unstable labels: `{', '.join(cats['unstable_labels']) or 'none'}`",
                f"- Gate-blocked labels: `{', '.join(cats['gate_blocked_labels']) or 'none'}`",
                f"- Capture-blocked labels: `{', '.join(cats['capture_blocked_labels']) or 'none'}`",
                "",
                "| Label | Trials | Match | Accepted Correct | False Accepts | Gate Blocked | Capture Blocked | Top Confusions | Top Failures |",
                "| --- | ---: | ---: | ---: | ---: | ---: | ---: | --- | --- |",
            ]
        )
        for label, row in sorted(item["labels"].items()):
            confusions = ", ".join(f"{k}:{v}" for k, v in row["top_confusions"].items()) or "-"
            failures = ", ".join(f"{k}:{v}" for k, v in row["top_failure_reasons"].items()) or "-"
            lines.append(
                f"| {label} | {row['trials']} | {row['match_rate']:.0%} | "
                f"{row['accepted_correct_rate']:.0%} | {row['false_accepts']} | "
                f"{row['gate_blocked']} | {row['capture_blocked']} | {confusions} | {failures} |"
            )
        lines.append("")
    path.write_text("\n".join(lines).rstrip() + "\n", encoding="utf-8")


def main():
    paths = [
        path if path.is_absolute() else ROOT / path
        for path in default_paths(sys.argv[1:])
    ]
    if not paths:
        raise SystemExit("No live_word_test_log_*.json files found.")
    payload = build_payload(paths)
    REPORT_DIR.mkdir(parents=True, exist_ok=True)
    json_path = REPORT_DIR / "live_phrase_demo_runtime_plan.json"
    md_path = REPORT_DIR / "live_phrase_demo_runtime_plan.md"
    json_path.write_text(json.dumps(payload, indent=2), encoding="utf-8")
    write_markdown(md_path, payload)
    print(f"Wrote: {json_path}")
    print(f"Wrote: {md_path}")


if __name__ == "__main__":
    main()
