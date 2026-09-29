"""Export safe per-event Samsung OOD replay metadata; no landmarks or paths."""
from __future__ import annotations

import argparse
import csv
import json
from pathlib import Path


FIELDS = (
    "event_id", "intended_event", "classifier_top1", "classifier_confidence",
    "ood_score", "geometry_score", "geometry_source_cutoff",
    "first_stable_class", "first_stable_end_ms", "final_accept_reject",
    "legacy_end_reason", "legacy_raw_top1", "legacy_accepted",
    "windows_evaluated", "confidence_only_first_stable",
    "binary_ood_first_stable", "combined_product_first_stable",
    "binary_and_geometry_first_stable",
)


def export(input_path: Path, output_path: Path) -> dict:
    if output_path.exists():
        raise FileExistsError("Preserve prior event matrix")
    report = json.loads(input_path.read_text(encoding="utf-8"))
    if report["official_test_opened"] or report["samsung_tuning_used"]:
        raise ValueError("Invalid evaluation provenance")
    variant = report["source_calibration_selected_variant"]
    events = report["samsung_events"]
    if len(events) != len({event["event_id"] for event in events}):
        raise ValueError("Duplicate Samsung event ID")
    output_path.parent.mkdir(parents=True, exist_ok=True)
    with output_path.open("x", newline="", encoding="utf-8") as target:
        writer = csv.DictWriter(target, fieldnames=FIELDS)
        writer.writeheader()
        for event in events:
            final = event["final_tensor"]
            first = event["first_stable"][variant] if variant else None
            row = {
                "event_id": event["event_id"],
                "intended_event": event["expected"],
                "classifier_top1": final["classifier_top1"],
                "classifier_confidence": final["classifier_confidence"],
                "ood_score": final["ood_score"],
                "geometry_score": final["geometry_score"],
                "geometry_source_cutoff": final["geometry_source_cutoff"],
                "first_stable_class": first["class"] if first else None,
                "first_stable_end_ms": first["end_ms"] if first else None,
                "final_accept_reject": event["final_accept_reject"],
                "legacy_end_reason": event["legacy_end_reason"],
                "legacy_raw_top1": event["legacy_raw_top1"],
                "legacy_accepted": event["legacy_accepted"],
                "windows_evaluated": event["windows_evaluated"],
            }
            for name in ("confidence_only", "binary_ood", "combined_product", "binary_and_geometry"):
                stable = event["first_stable"][name]
                row[name + "_first_stable"] = stable["class"] if stable else None
            writer.writerow(row)
    return {"events": len(events), "source_selected_variant": variant,
            "output": str(output_path)}


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    print(json.dumps(export(args.input, args.output), indent=2))
