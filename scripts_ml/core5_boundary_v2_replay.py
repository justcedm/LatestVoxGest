"""Deterministic, offline-only Core5 Boundary V2 candidate search.

This never changes an Android/model asset and never interprets operator events as
training truth. Saved event windows are incomplete streaming sessions; outcomes
are diagnostic proxies, not live performance estimates.
"""
from __future__ import annotations

import argparse
import itertools
import json
from dataclasses import asdict, dataclass
from pathlib import Path

import numpy as np

from core5_boundary_descriptors import timeline


@dataclass(frozen=True)
class Config:
    start: float
    end: float
    dwell_ms: int
    min_event_ms: int
    post_ms: int
    no_hand_ms: int = 350
    pre_ms: int = 200
    rearm_neutral_ms: int = 400
    filter_tau_ms: int = 0


def score_rows(rows, filter_tau_ms=0):
    """XY-only, robust temporal fusion; Z is intentionally excluded."""
    raw = []
    for row in rows:
        if not row["pose"] or not (row["left"] or row["right"]):
            raw.append(0.0)
            continue
        translation = row["translation_xy_per_s"]
        articulation = row["articulation_xy_per_s"]
        arm = row["arm_xy_per_s"]
        if any(value is None for value in (translation, articulation, arm)):
            raw.append(None)
        else:
            # Candidate scaling only. Search results cannot validate these weights.
            raw.append(float(max(translation, .25 * articulation, .75 * arm)))
    filtered = []
    for index in range(len(raw)):
        values = [v for v in raw[max(0, index - 2): index + 1] if v is not None]
        filtered.append(float(np.median(values)) if values else None)
    if filter_tau_ms == 0:
        return filtered
    # Timestamp-aware, causal low-pass on the detector score only. The model's
    # authentic 225-feature observations remain untouched.
    smoothed = []
    previous = None
    for index, value in enumerate(filtered):
        if value is None:
            smoothed.append(previous)
            continue
        if previous is None:
            previous = value
        else:
            dt = rows[index]["timestamp_ms"] - rows[index - 1]["timestamp_ms"]
            alpha = 1 - np.exp(-dt / filter_tau_ms)
            previous = float(previous + alpha * (value - previous))
        smoothed.append(previous)
    return smoothed


def replay(rows, config: Config):
    scores = score_rows(rows, config.filter_tau_ms)
    state = "IDLE"
    high_start = None
    high_count = 0
    active_start = None
    pending_since = None
    post_since = None
    proposed_reason = None
    no_hand_since = None
    neutral_since = None
    events = []
    state_trace = []
    for index, (row, score) in enumerate(zip(rows, scores)):
        t = row["timestamp_ms"]
        hand = row["pose"] and (row["left"] or row["right"])
        if not hand:
            no_hand_since = t if no_hand_since is None else no_hand_since
        else:
            no_hand_since = None
        if state in {"IDLE", "PRE_ROLL"}:
            state = "PRE_ROLL"
            if hand and score is not None and score >= config.start:
                if high_start is None:
                    high_start = t
                    high_count = 1
                else:
                    high_count += 1
                if high_count >= 2:
                    active_start = max(rows[0]["timestamp_ms"], high_start - config.pre_ms)
                    state = "CAPTURING"
                    high_start = None
                    high_count = 0
            else:
                high_start = None
                high_count = 0
        elif state == "CAPTURING":
            if t - active_start >= config.min_event_ms:
                if no_hand_since is not None:
                    pending_since = no_hand_since
                    state = "END_PENDING"
                elif score is not None and score < config.end:
                    pending_since = t
                    state = "END_PENDING"
        elif state == "END_PENDING":
            disappearance = no_hand_since is not None and t - no_hand_since >= config.no_hand_ms
            quiet = hand and score is not None and score < config.end and t - pending_since >= config.dwell_ms
            if disappearance or quiet:
                state = "POST_ROLL"
                post_since = t
                proposed_reason = "NO_HAND" if disappearance else "VISIBLE_MOTION_SETTLE"
            elif hand and (score is None or score >= config.end):
                state = "CAPTURING"
                pending_since = None
        elif state == "POST_ROLL":
            if hand and score is not None and score >= config.start:
                state = "CAPTURING"
                pending_since = None
                post_since = None
            elif t - post_since >= config.post_ms:
                events.append({"start_ms": active_start, "end_ms": t, "end_index": index,
                               "reason": proposed_reason, "duration_ms": t - active_start})
                state = "REARM_WAIT"
                neutral_since = None
        elif state == "REARM_WAIT":
            neutral = row["pose"] and not (row["left"] or row["right"])
            if neutral:
                neutral_since = t if neutral_since is None else neutral_since
            elif neutral_since is not None and t - neutral_since >= config.rearm_neutral_ms:
                state = "PRE_ROLL"
                high_start = None
                high_count = 0
                neutral_since = None
        state_trace.append(state)
    high_after_cut = 0
    if events:
        cut = events[0]["end_index"]
        high_after_cut = sum(score is not None and score >= config.start for score in scores[cut + 1:])
    return {"events": events, "final_state": state, "high_motion_frames_after_first_cut": high_after_cut,
            "started_without_completion": bool(active_start is not None and not events),
            "states": state_trace, "scores": scores}


def load_events(events_dir):
    result = []
    for path in sorted(events_dir.glob("*.json")):
        if path.name.startswith("startup-") or ".candidate-replay." in path.name:
            continue
        event = json.loads(path.read_text(encoding="utf-8"))
        if event.get("profile") != "FSL_CORE5_SIM10FPS_V1" or not event.get("frames"):
            continue
        if event.get("boundary_mode") not in {"MANUAL", "AUTO_LEGACY"}:
            continue
        result.append((event, timeline(event)))
    return result


def evaluate(events, config):
    rows = []
    for event, descriptor_rows in events:
        result = replay(descriptor_rows, config)
        predicted = result["events"]
        rows.append({"event_id": event["event_id"], "expected": event["expected_test_label"],
                     "source_mode": event["boundary_mode"], "source_end": event["termination"],
                     "source_accepted": event["accepted"], "candidate_event_count": len(predicted),
                     "candidate": predicted[0] if predicted else None,
                     "early_cut_motion_frames": result["high_motion_frames_after_first_cut"],
                     "started_without_completion": result["started_without_completion"]})
    auto = [r for r in rows if r["source_mode"] == "AUTO_LEGACY"]
    negative = [r for r in rows if r["expected"].startswith("NON_SIGN:")]
    manual_positive = [r for r in rows if r["source_mode"] == "MANUAL" and
                       not r["expected"].startswith("NON_SIGN:")]
    timeout = [r for r in auto if r["source_end"] == "EVENT_TIMEOUT"]
    metrics = {"auto_events": len(auto), "auto_timeout_events": len(timeout),
               "timeout_with_candidate_end": sum(bool(r["candidate"]) for r in timeout),
               "auto_early_cut_risk_events": sum(r["early_cut_motion_frames"] > 0 for r in auto),
               "auto_no_candidate": sum(not r["candidate"] for r in auto),
               "auto_extra_candidate_events": sum(r["candidate_event_count"] > 1 for r in auto),
               "manual_positive_events": len(manual_positive),
               "manual_positive_with_candidate_end": sum(bool(r["candidate"]) for r in manual_positive),
               "manual_positive_early_cut_risk_events": sum(r["early_cut_motion_frames"] > 0 for r in manual_positive),
               "negative_events": len(negative),
               "negative_with_candidate_end": sum(bool(r["candidate"]) for r in negative)}
    return metrics, rows


def run(events_dir: Path, output: Path):
    if output.exists():
        raise FileExistsError("Preserve earlier boundary search evidence")
    events = load_events(events_dir)
    configs = [Config(start=start, end=start * ratio, dwell_ms=dwell,
                      min_event_ms=minimum, post_ms=post, filter_tau_ms=tau)
               for start, ratio, dwell, minimum, post, tau in itertools.product(
                   (1.0, 1.5, 2.0, 3.0), (.35, .5, .65), (250, 400, 700),
                   (300, 600), (100, 200), (0, 100, 200))]
    search = []
    for config in configs:
        metrics, _ = evaluate(events, config)
        search.append({"config": asdict(config), "metrics": metrics})
    # Rank on intended automatic events only. Wave/partial are sealed diagnostic
    # checks and must not be used to select an operating point.
    search.sort(key=lambda x: (-x["metrics"]["timeout_with_candidate_end"],
                               x["metrics"]["auto_early_cut_risk_events"] +
                               x["metrics"]["manual_positive_early_cut_risk_events"],
                               x["metrics"]["auto_no_candidate"]))
    examples = []
    for item in search[:5]:
        config = Config(**item["config"])
        _, rows = evaluate(events, config)
        examples.append({"config": item["config"], "metrics": item["metrics"], "rows": rows})
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps({"status": "EXPLORATORY_NOT_VALIDATED",
                                  "scoring_note": "No human sign-end annotations; high motion after cut is a conservative proxy",
                                  "filter_note": "0=3-sample causal median only; 100/200ms=additional timestamp-aware causal EMA on detector score; classifier input unchanged",
                                  "sealed_negative_note": "Wave/partial are diagnostic only, not threshold training examples",
                                  "events": len(events), "candidate_configs": len(search),
                                  "search": search, "top_examples": examples}, indent=2) + "\n", encoding="utf-8")
    return {"events": len(events), "candidate_configs": len(search),
            "best_diagnostic_metrics": search[0]["metrics"], "output": str(output)}


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--events-dir", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    print(json.dumps(run(args.events_dir, args.output), indent=2))
