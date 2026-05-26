"""Compare VoxGest live word test logs for Recognition Hardening v1.

Reads all reports/live_word_test_log_*.json files and writes:
  - reports/live_log_comparison.json
  - reports/live_log_comparison.csv
  - reports/live_log_summary.md
"""

import csv
import json
from collections import Counter, defaultdict
from datetime import datetime
from pathlib import Path

from word_config import TRAINING_WORDS


ROOT = Path(__file__).resolve().parents[1]
REPORT_DIR = ROOT / "reports"
LOG_PATTERN = "live_word_test_log_*.json"

JSON_OUT = REPORT_DIR / "live_log_comparison.json"
CSV_OUT = REPORT_DIR / "live_log_comparison.csv"
MD_OUT = REPORT_DIR / "live_log_summary.md"

DEMO10_LABELS = [
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
    "NOTHING",
]
PROFILE_LABELS = list(dict.fromkeys(DEMO10_LABELS + list(TRAINING_WORDS)))

SPRINT30_HARDENING_PRIORITY = [
    "THANKYOU",
    "STOP",
    "DOCTOR",
    "UNDERSTAND",
    "PAIN",
    "GO",
    "FINE",
    "EAT",
    "TIME",
    "MEDICINE",
    "NOTHING",
]
KNOWN_WEAK_WATCH = list(
    dict.fromkeys(["STOP", "NAME", "HELP", "DOCTOR", "NOTHING", "PLEASE"] + SPRINT30_HARDENING_PRIORITY)
)
KNOWN_SAFER_DEMO = ["YES", "NO", "WATER", "HELLO", "THANKYOU"]


def as_bool(value):
    if isinstance(value, bool):
        return value
    if isinstance(value, (int, float)):
        return value != 0
    return str(value).strip().lower() in {"1", "true", "yes", "y", "ok"}


def safe_float(value):
    try:
        return float(value)
    except (TypeError, ValueError):
        return 0.0


def load_logs():
    logs = []
    for path in sorted(REPORT_DIR.glob(LOG_PATTERN)):
        try:
            with open(path, "r", encoding="utf-8") as f:
                payload = json.load(f)
        except Exception as exc:
            logs.append({"path": str(path), "error": str(exc), "records": []})
            continue
        payload["_path"] = str(path)
        logs.append(payload)
    return logs


def iter_records(logs):
    for log in logs:
        records = log.get("records") or []
        model_name = str(log.get("model_name") or "UNKNOWN").upper()
        model_path = str(log.get("model_path") or "")
        generated_at = str(log.get("generated_at") or "")
        source_file = str(log.get("_path") or log.get("path") or "")
        for row in records:
            item = dict(row)
            item["_model_name"] = str(item.get("model_name") or model_name).upper()
            item["_model_path"] = str(item.get("model_path") or model_path)
            item["_source_file"] = source_file
            item["_generated_at"] = generated_at
            yield item


def empty_stats(label=None, model=None):
    return {
        "label": label,
        "model_name": model,
        "trials": 0,
        "correct_matches": 0,
        "accepted_by_gate": 0,
        "true_accepts": 0,
        "false_accepts": 0,
        "rejected": 0,
        "confusions": Counter(),
        "failure_reasons": Counter(),
        "confidence_sum": 0.0,
        "margin_sum": 0.0,
        "motion_sum": 0.0,
        "wrist_path_sum": 0.0,
        "hand_presence_sum": 0.0,
    }


def update_stats(stats, row):
    expected = str(row.get("expected_label") or "").upper()
    predicted = str(row.get("predicted_label") or "").upper()
    accepted = as_bool(row.get("accepted_by_gate"))
    matched = as_bool(row.get("matched_expected"))
    if not matched and expected and predicted:
        matched = expected == predicted

    stats["trials"] += 1
    stats["confidence_sum"] += safe_float(row.get("confidence"))
    stats["margin_sum"] += safe_float(row.get("margin"))
    stats["motion_sum"] += safe_float(row.get("motion"))
    stats["wrist_path_sum"] += safe_float(row.get("wrist_path"))
    stats["hand_presence_sum"] += safe_float(row.get("hand_presence"))

    if matched:
        stats["correct_matches"] += 1
    if accepted:
        stats["accepted_by_gate"] += 1
    else:
        stats["rejected"] += 1
    if accepted and matched:
        stats["true_accepts"] += 1
    if accepted and not matched:
        stats["false_accepts"] += 1

    if expected and predicted and predicted != expected:
        stats["confusions"][predicted] += 1

    reason = str(row.get("failure_reason") or "").strip()
    if reason and reason != "ok":
        for part in reason.split(";"):
            clean = part.strip()
            if clean and clean != "ok":
                stats["failure_reasons"][clean] += 1


def finalize_stats(stats):
    trials = max(1, stats["trials"])
    correct_rate = stats["correct_matches"] / trials
    true_accept_rate = stats["true_accepts"] / trials
    false_accept_rate = stats["false_accepts"] / trials
    rejected_rate = stats["rejected"] / trials
    weakness_score = (
        (1.0 - true_accept_rate) * 100.0
        + false_accept_rate * 35.0
        + rejected_rate * 20.0
    )

    return {
        "label": stats["label"],
        "model_name": stats["model_name"],
        "trials": stats["trials"],
        "correct_matches": stats["correct_matches"],
        "accepted_by_gate": stats["accepted_by_gate"],
        "true_accepts": stats["true_accepts"],
        "false_accepts": stats["false_accepts"],
        "rejected": stats["rejected"],
        "correct_match_rate": round(correct_rate, 4),
        "true_accept_rate": round(true_accept_rate, 4),
        "false_accept_rate": round(false_accept_rate, 4),
        "rejected_rate": round(rejected_rate, 4),
        "avg_confidence": round(stats["confidence_sum"] / trials, 4),
        "avg_margin": round(stats["margin_sum"] / trials, 4),
        "avg_motion": round(stats["motion_sum"] / trials, 4),
        "avg_wrist_path": round(stats["wrist_path_sum"] / trials, 4),
        "avg_hand_presence": round(stats["hand_presence_sum"] / trials, 4),
        "top_confusions": dict(stats["confusions"].most_common(5)),
        "failure_reasons": dict(stats["failure_reasons"].most_common(8)),
        "weakness_score": round(weakness_score, 3),
    }


def recommendation_for(row):
    label = row["label"]
    if row["trials"] == 0:
        return "needs live test coverage"
    if label == "NOTHING":
        if row["false_accepts"] or row["correct_match_rate"] < 0.85:
            return "record hard negatives and transition/partial-sign NOTHING samples"
        return "keep as active negative class"
    if row["true_accept_rate"] < 0.70:
        if label in SPRINT30_HARDENING_PRIORITY:
            return "record sprint30 hardening repair samples and confusable NOTHING negatives"
        if label in KNOWN_WEAK_WATCH:
            return "record targeted repair samples for this known weak label"
        return "record more controlled demo10 samples"
    if row["false_accepts"]:
        return "add confusable negatives and clean contrast samples"
    return "keep current demo coverage"


def summarize(records):
    by_label = {label: empty_stats(label=label, model="ALL") for label in PROFILE_LABELS}
    by_model_label = defaultdict(lambda: None)
    files = set()
    total_records = 0

    for row in records:
        expected = str(row.get("expected_label") or "").upper()
        model = str(row.get("_model_name") or "UNKNOWN").upper()
        if not expected:
            continue
        total_records += 1
        files.add(row.get("_source_file", ""))
        if expected not in by_label:
            by_label[expected] = empty_stats(label=expected, model="ALL")
        update_stats(by_label[expected], row)

        key = (model, expected)
        if by_model_label[key] is None:
            by_model_label[key] = empty_stats(label=expected, model=model)
        update_stats(by_model_label[key], row)

    label_rows = [finalize_stats(stats) for stats in by_label.values()]
    for row in label_rows:
        row["recommendation"] = recommendation_for(row)
    label_rows.sort(key=lambda item: item["label"])

    model_rows = [finalize_stats(stats) for stats in by_model_label.values() if stats is not None]
    for row in model_rows:
        row["recommendation"] = recommendation_for(row)
    model_rows.sort(key=lambda item: (item["model_name"], item["label"]))

    weakest = sorted(
        [row for row in label_rows if row["trials"] > 0],
        key=lambda item: (-item["weakness_score"], item["true_accept_rate"], item["label"]),
    )

    recommended_next = [
        row["label"]
        for row in weakest
        if (
            row["true_accept_rate"] < 0.75
            or row["false_accepts"] > 0
            or row["label"] in KNOWN_WEAK_WATCH
        )
    ]
    recommended_next = sorted(
        set(recommended_next),
        key=lambda label: (
            0 if label in KNOWN_WEAK_WATCH else 1,
            next((idx for idx, row in enumerate(weakest) if row["label"] == label), 999),
            label,
        ),
    )

    model_names = sorted({row["model_name"] for row in model_rows})
    model_comparison = {
        "available_models": model_names,
        "note": (
            "TCN and LSTM logs are both present."
            if {"LSTM", "TCN"}.issubset(set(model_names))
            else "Only one model family is present in the current live logs."
        ),
        "by_model_label": model_rows,
    }

    return {
        "generated_at": datetime.now().isoformat(timespec="seconds"),
        "source_files": sorted(path for path in files if path),
        "total_records": total_records,
        "known_weak_watch": KNOWN_WEAK_WATCH,
        "known_safer_demo": KNOWN_SAFER_DEMO,
        "per_label": label_rows,
        "weakest_labels_worst_to_best": weakest,
        "record_next_recommendation": recommended_next,
        "model_comparison": model_comparison,
    }


def write_json(payload):
    REPORT_DIR.mkdir(parents=True, exist_ok=True)
    with open(JSON_OUT, "w", encoding="utf-8") as f:
        json.dump(payload, f, indent=2)


def compact_counter_text(mapping):
    if not mapping:
        return "-"
    return "; ".join(f"{key}:{value}" for key, value in mapping.items())


def write_csv(payload):
    fields = [
        "scope",
        "model_name",
        "label",
        "trials",
        "correct_matches",
        "accepted_by_gate",
        "true_accepts",
        "false_accepts",
        "rejected",
        "correct_match_rate",
        "true_accept_rate",
        "false_accept_rate",
        "rejected_rate",
        "avg_confidence",
        "avg_margin",
        "avg_motion",
        "avg_wrist_path",
        "avg_hand_presence",
        "top_confusions",
        "failure_reasons",
        "weakness_score",
        "recommendation",
    ]
    with open(CSV_OUT, "w", encoding="utf-8", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=fields)
        writer.writeheader()
        for row in payload["per_label"]:
            out = dict(row)
            out["scope"] = "overall"
            out["top_confusions"] = compact_counter_text(row["top_confusions"])
            out["failure_reasons"] = compact_counter_text(row["failure_reasons"])
            writer.writerow({field: out.get(field, "") for field in fields})
        for row in payload["model_comparison"]["by_model_label"]:
            out = dict(row)
            out["scope"] = "model"
            out["top_confusions"] = compact_counter_text(row["top_confusions"])
            out["failure_reasons"] = compact_counter_text(row["failure_reasons"])
            writer.writerow({field: out.get(field, "") for field in fields})


def percent(value):
    return f"{value * 100.0:.1f}%"


def write_markdown(payload):
    lines = [
        "# VoxGest Live Log Summary",
        "",
        f"Generated: {payload['generated_at']}",
        f"Source logs: {len(payload['source_files'])}",
        f"Total trials: {payload['total_records']}",
        "",
        "## Weakest Labels",
        "",
        "| Rank | Label | Trials | Correct | Accepted | True accept | False accepts | Rejected | Top confusion | Recommendation |",
        "| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | --- | --- |",
    ]
    for idx, row in enumerate(payload["weakest_labels_worst_to_best"], start=1):
        top_confusion = next(iter(row["top_confusions"].items()), None)
        top_confusion_text = "-" if top_confusion is None else f"{top_confusion[0]} ({top_confusion[1]})"
        lines.append(
            "| "
            f"{idx} | {row['label']} | {row['trials']} | "
            f"{percent(row['correct_match_rate'])} | {row['accepted_by_gate']} | "
            f"{percent(row['true_accept_rate'])} | {row['false_accepts']} | "
            f"{row['rejected']} | {top_confusion_text} | {row['recommendation']} |"
        )

    lines.extend(
        [
            "",
            "## Record Next",
            "",
        ]
    )
    if payload["record_next_recommendation"]:
        for label in payload["record_next_recommendation"]:
            lines.append(f"- {label}")
    else:
        lines.append("- No targeted repair label is recommended from current logs.")

    lines.extend(
        [
            "",
            "## Known Safer Demo Labels",
            "",
            ", ".join(payload["known_safer_demo"]),
            "",
            "## Known Weak Labels To Watch",
            "",
            ", ".join(payload["known_weak_watch"]),
            "",
            "## LSTM vs TCN",
            "",
            payload["model_comparison"]["note"],
            "",
        ]
    )

    model_rows = payload["model_comparison"]["by_model_label"]
    if model_rows:
        lines.extend(
            [
                "| Model | Label | Trials | Correct | True accept | False accepts | Rejected |",
                "| --- | --- | ---: | ---: | ---: | ---: | ---: |",
            ]
        )
        for row in model_rows:
            lines.append(
                "| "
                f"{row['model_name']} | {row['label']} | {row['trials']} | "
                f"{percent(row['correct_match_rate'])} | "
                f"{percent(row['true_accept_rate'])} | "
                f"{row['false_accepts']} | {row['rejected']} |"
            )

    lines.extend(
        [
            "",
            "## Source Files",
            "",
        ]
    )
    for path in payload["source_files"]:
        lines.append(f"- {Path(path).name}")

    with open(MD_OUT, "w", encoding="utf-8") as f:
        f.write("\n".join(lines) + "\n")


def main():
    logs = load_logs()
    records = list(iter_records(logs))
    payload = summarize(records)
    write_json(payload)
    write_csv(payload)
    write_markdown(payload)
    print(f"Wrote: {JSON_OUT}")
    print(f"Wrote: {CSV_OUT}")
    print(f"Wrote: {MD_OUT}")
    if payload["record_next_recommendation"]:
        print("Record next:", ", ".join(payload["record_next_recommendation"]))


if __name__ == "__main__":
    main()
