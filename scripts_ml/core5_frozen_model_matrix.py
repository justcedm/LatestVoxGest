"""Read-only same-tensor Baseline/Native48/SIM10 replay for all five Core5 classes."""
from __future__ import annotations

import argparse
import json
import zipfile
from pathlib import Path

import numpy as np

from core5_contract import LABELS
from core5_streaming_window_replay import event_paths, verified_event
from core5_yes_no_forensics import EXPECTED, predict, runner


def run(events_dir, baseline, archive_path, sim, output):
    if output.exists():
        raise FileExistsError("Preserve prior same-tensor matrix")
    import tensorflow as tf
    with zipfile.ZipFile(archive_path) as archive:
        native = archive.read("core5_native48_rdtcn_v1.tflite")
    models = {"BASELINE": runner(tf, baseline.read_bytes(), EXPECTED["BASELINE"]),
              "NATIVE48": runner(tf, native, EXPECTED["NATIVE48"]),
              "SIM10": runner(tf, sim.read_bytes(), EXPECTED["SIM10"])}
    rows = []
    for path in event_paths(events_dir):
        checked = verified_event(path)
        if checked is None:
            continue
        event = checked[0]
        tensor = np.asarray(event["tensor"], dtype=np.float32) if event.get("tensor") is not None else None
        rows.append({"event_id": event["event_id"], "expected": event["expected_test_label"],
                     "legacy_end_reason": event["termination"],
                     "tensor_sha256": event.get("tensor_sha256"),
                     "models": {name: predict(bundle, tensor) for name, bundle in models.items()}
                               if tensor is not None else None})
    if len(rows) != 69:
        raise ValueError("Unexpected Samsung event count")
    summary = {}
    for name in models:
        summary[name] = {label: {"events": sum(row["expected"] == label for row in rows),
                                 "raw_correct": sum(row["expected"] == label and row["models"] is not None and
                                                    row["models"][name]["top1"] == label for row in rows)}
                         for label in LABELS}
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps({"status": "OFFLINE_READ_ONLY_NO_MODEL_PROMOTION",
                                  "model_sha256": EXPECTED, "operator_labels_evaluation_only": True,
                                  "summary": summary, "events": rows}, indent=2, allow_nan=False) + "\n",
                      encoding="utf-8")
    return {"summary": summary, "events": len(rows), "output": str(output)}


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    for name in ("events_dir", "baseline", "archive", "sim", "output"):
        parser.add_argument("--" + name.replace("_", "-"), type=Path, required=True)
    args = parser.parse_args()
    print(json.dumps(run(args.events_dir, args.baseline, args.archive, args.sim, args.output), indent=2))
