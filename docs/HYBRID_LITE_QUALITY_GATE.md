# Hybrid-Lite Quality Gate

## Purpose

The Hybrid-Lite quality gate improves VoxGest live recognition stability by rejecting bad 30-frame landmark sequences before they reach the TCN or LSTM word model.

This does not replace the current MediaPipe landmark recognition system. It uses the pose and hand landmarks already produced by the live scripts, then checks whether the sequence is clean enough for word inference.

## Why Not YOLO or DETR Yet

YOLO or DETR could be useful later for hand/body detection support, but they are not the right next step for this sprint.

- They require a separate labeled detection dataset.
- They add training, conversion, and Android runtime complexity.
- They do not directly solve word-level confusion inside the existing landmark classifier.
- They can increase latency on low-end phones.
- The current weak point is often bad landmark windows, missing hands, unstable tracking, and false accepts, which can be filtered without adding a new detector.

Hybrid-Lite is safer for the current demo because it keeps the existing model contract and adds a transparent rejection layer.

## What The Gate Checks

The gate evaluates each 30-frame dynamic sequence and returns one of these statuses:

| Status | Meaning | Action |
| --- | --- | --- |
| `GOOD` | Sequence is clean enough for inference. | Allow TCN/LSTM prediction. |
| `NO_HAND` | No configured hand is visible across the sequence. | Reject word output. |
| `LOW_HAND_PRESENCE` | The hand appears in too few frames. | Reject word output. |
| `UNSTABLE_LANDMARKS` | The hand trajectory has tracking jumps or unstable landmarks. | Reject word output. |
| `HANDS_OVERLAPPING` | Two detected hands are too close and likely confusing the tracker. | Reject word output. |
| `LOW_MOTION` | Motion is too small for a dynamic sign. | Reject word output. |
| `LOW_WRIST_PATH` | Wrist travel is too short for a reliable dynamic sign. | Reject word output. |
| `BAD_SEQUENCE` | Shape, pose reference, or sudden jump checks failed. | Reject word output. |

For close-hand signs such as `MORE`, the gate should not force a prediction when MediaPipe tracking becomes unstable. If the two hands overlap or fingertips become too close, the live scripts log `HANDS_OVERLAPPING` or `UNSTABLE_LANDMARKS`.

## Safety Rules Preserved

- `NOTHING` remains no-output.
- Raw predictions still never update sentence output.
- Only accepted and gated predictions can enter `TokenComposer`.
- Existing confidence, margin, motion, wrist path, and hand-presence gates remain active.
- Phrase recognition remains disabled by default.
- The onehand162 pipeline remains unchanged.
- The fullsign225 profile remains experimental and is not promoted as default.

## Integration Points

The gate is implemented in:

```text
scripts_ml/frame_quality_gate.py
```

It is integrated into:

```text
scripts_ml/33_live_word_test_logger.py
scripts_ml/20_webcam_dual.py
```

The live logger writes these additional fields:

```text
quality_status
quality_reason
```

If `quality_status` is not `GOOD`, the script blocks model inference for that window and logs the rejection reason as `quality:<STATUS>`.

## Configuration

The gate is enabled by default:

```powershell
$env:VOXGEST_QUALITY_GATE='1'
```

For A/B comparison only, disable it with:

```powershell
$env:VOXGEST_QUALITY_GATE='0'
```

Useful thresholds:

```powershell
$env:VOXGEST_QUALITY_MIN_HAND_PRESENCE='0.45'
$env:VOXGEST_QUALITY_MIN_MOTION='0.0035'
$env:VOXGEST_QUALITY_MIN_WRIST_PATH='0.025'
$env:VOXGEST_QUALITY_HAND_OVERLAP_DISTANCE='0.035'
$env:VOXGEST_QUALITY_HAND_OVERLAP_RATIO='0.20'
```

## Live Test Command

Use onehand162 for the current hardening path:

```powershell
$env:VOXGEST_ENABLE_PHRASE='0'
$env:VOXGEST_WORD_PROFILE='sprint30'
$env:VOXGEST_FEATURE_PROFILE='onehand162'
$env:VOXGEST_DYNAMIC_MODEL='tcn'
$env:VOXGEST_DOMINANT_HAND='right'
$env:VOXGEST_SINGLE_HAND_POSE='1'
$env:VOXGEST_MODE='WORDS'
.\voxgest_env\Scripts\python.exe scripts_ml\33_live_word_test_logger.py YES NO PLEASE WATER HELLO HELP STOP DOCTOR NAME THANKYOU SORRY MORE PAIN FINE EAT WANT TIME NOTHING
```

After logging trials, compare results:

```powershell
.\voxgest_env\Scripts\python.exe scripts_ml\34_compare_live_logs.py
```

## What This Can Fix

- False accepts when no hand is visible.
- False accepts from partial hands entering or leaving the frame.
- Predictions from unstable MediaPipe tracking.
- Predictions from very low motion or short wrist paths.
- Bad close-hand windows where landmarks overlap.

## What This Cannot Fix

- A word class trained from inconsistent gesture styles.
- Weak dataset coverage for a label.
- Confusions caused by genuinely similar signs.
- Missing manual repair data.
- Full ASL translation or sentence-level ASL grammar.

## Why FullSign225 Is Paused

The sprint30 fullsign225 experiment is still useful research, but the latest live result was weak at about 19 correct out of 91 trials. It should remain experimental until its dataset and live behavior improve.

The active stability path is onehand162 plus:

- better frame quality gating,
- more targeted manual repair data,
- stronger `NOTHING` hard negatives,
- continued live-log analysis.

## Recommendation

Keep demo10 as the safe baseline. Keep sprint30 onehand162 as the active hardening profile. Use fullsign225 only as a separate experiment until live testing shows clear improvement.
