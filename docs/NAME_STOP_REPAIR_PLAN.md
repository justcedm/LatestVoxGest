# VoxGest NAME / STOP Repair Plan

Status date: 2026-05-15

## Current Finding

The latest right-hand TCN live log confirms the remaining demo10 blocker:

- `NAME`: 0/5 correct, 5/5 predicted as accepted `STOP`
- `STOP`: 4/4 correct, no false accepts in the latest right-hand log
- `NOTHING`: 3/3 correct, no false word output in the latest right-hand log
- `PLEASE`: mostly correct, with one accepted `STOP` confusion

This means the next repair must strengthen `NAME` without weakening the already
working `STOP` pattern. Do not expand vocabulary until this repair passes live
testing.

## Why NAME And STOP Are Confused

Technical explanation: both labels are short right-hand dynamic gestures that
can occupy a similar camera region and produce overlapping hand/pose landmark
paths. The current model has learned a stronger right-hand `STOP` boundary than
`NAME`, so the gate accepts the sequence as a confident word, but the class is
wrong. This is not a gate-only problem because confidence, margin, motion, path,
and hand-presence can all pass while the top class is still `STOP`.

Non-technical explanation: the model already knows what a good `STOP` looks
like, but it does not yet see enough clean difference between `NAME` and `STOP`
when the right hand is used. It needs contrast examples: clean `NAME`, clean
`STOP`, and messy in-between attempts labeled as `NOTHING`.

## Samples To Record

Record one controlled right-hand repair pass with:

- clean `NAME` samples first
- clean `STOP` samples second
- `NOTHING` hard negatives last

For `NAME`, make the gesture clearly different from `STOP`. For `STOP`, preserve
the currently working version instead of changing the sign style. For `NOTHING`,
record confusing partials and transitions so the model learns that incomplete
or aborted motion should not become either word.

`NOTHING` must include:

- partial `NAME`
- aborted `NAME`
- partial `STOP`
- aborted `STOP`
- hand entering frame
- hand leaving frame
- transition movement between `NAME` and `STOP`
- neutral non-word movement

## Exact Recording Command

```powershell
$env:VOXGEST_ENABLE_PHRASE='0'
$env:VOXGEST_WORD_PROFILE='demo10'
$env:VOXGEST_SINGLE_HAND_POSE='1'
$env:VOXGEST_DOMINANT_HAND='right'
$env:VOXGEST_MANUAL_SEQUENCES_PER_WORD='60'
.\voxgest_env\Scripts\python.exe scripts_ml\16_record_manual_words.py NAME STOP NOTHING
```

Recording rules:

- Do not use `VOXGEST_DOMINANT_HAND='auto'`.
- Keep the right hand visible.
- Keep the same camera distance used during demo.
- Start neutral, perform the sign, return neutral.
- Keep phrase recognition disabled.
- Keep `NOTHING` as the no-output class.

## Retrain After Recording

Retrain LSTM:

```powershell
$env:VOXGEST_ENABLE_PHRASE='0'
$env:VOXGEST_WORD_PROFILE='demo10'
$env:VOXGEST_SINGLE_HAND_POSE='1'
.\voxgest_env\Scripts\python.exe scripts_ml\19_train_lstm.py
```

Retrain TCN:

```powershell
$env:VOXGEST_ENABLE_PHRASE='0'
$env:VOXGEST_WORD_PROFILE='demo10'
$env:VOXGEST_SINGLE_HAND_POSE='1'
.\voxgest_env\Scripts\python.exe scripts_ml\24_train_tcn.py
```

## Live Test After Retraining

Base command:

```powershell
$env:VOXGEST_ENABLE_PHRASE='0'
$env:VOXGEST_WORD_PROFILE='demo10'
$env:VOXGEST_DYNAMIC_MODEL='auto'
$env:VOXGEST_DOMINANT_HAND='right'
$env:VOXGEST_MODE='WORDS'
.\voxgest_env\Scripts\python.exe scripts_ml\33_live_word_test_logger.py
```

Ordered test labels:

```text
NAME, STOP, NAME, STOP, NOTHING, NAME, STOP, PLEASE, YES, NO, WATER, HELLO, HELP, DOCTOR, THANKYOU
```

If using command-line expected labels, run:

```powershell
$env:VOXGEST_ENABLE_PHRASE='0'
$env:VOXGEST_WORD_PROFILE='demo10'
$env:VOXGEST_DYNAMIC_MODEL='auto'
$env:VOXGEST_DOMINANT_HAND='right'
$env:VOXGEST_MODE='WORDS'
.\voxgest_env\Scripts\python.exe scripts_ml\33_live_word_test_logger.py NAME STOP NAME STOP NOTHING NAME STOP PLEASE YES NO WATER HELLO HELP DOCTOR THANKYOU
```

## Acceptance Gate For Expansion

Move toward controlled vocabulary expansion only if:

- `NAME` is at least 4/5 correct.
- `STOP` remains at least 4/5 correct.
- `NOTHING` does not create false word outputs.
- `PLEASE` does not frequently become `STOP`.

If this fails, do not create or train the expansion profile yet. Repeat targeted
contrast recording for `NAME`, `STOP`, and `NOTHING`.
