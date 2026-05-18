# VoxGest NAME Emergency Repair Plan

Status date: 2026-05-15

This sprint keeps demo10 as the active safe profile. Do not activate sprint20,
do not add vocabulary, and do not enable phrase recognition.

## Current Failure

Latest right-hand TCN log: `reports/live_word_test_log_20260515_222007.json`

- `NAME`: 0/5 correct
- 3 trials were accepted as `THANKYOU`
- 2 trials were rejected as low-confidence `PLEASE`
- `STOP`, `THANKYOU`, `PLEASE`, and `NOTHING` were otherwise correct in that
  latest focused run

Historical live-log comparison still ranks `NAME` as the weakest label:

- 35 total `NAME` trials
- 34.3% correct
- 18 false accepts
- main historical confusion: `STOP`
- latest new confusions: `THANKYOU` and `PLEASE`

## NAME Data Source Status

Current `dataset_words_lstm/NAME` state:

| Metric | Count |
| --- | ---: |
| Total `.npy` sequences | 640 |
| Valid `30 x 162` sequences | 640 |
| Total groups | 26 |
| Manual sequences | 240 |
| Manual groups | 4 |
| WLASL/external sequences | 400 |
| WLASL/external groups | 22 |
| Metadata-missing files | 0 |
| Manual files missing hand metadata | 0 |

Current contrast-word status:

| Word | Sequences | Groups | Manual Sequences | External Sequences |
| --- | ---: | ---: | ---: | ---: |
| STOP | 700 | 27 | 300 | 400 |
| THANKYOU | 580 | 25 | 180 | 400 |
| PLEASE | 440 | 21 | 60 | 380 |

Manual `NAME` groups:

| Group | Count | Hand |
| --- | ---: | --- |
| `NAME/manual_20260508_122551` | 60 | right |
| `NAME/manual_20260514_104513` | 60 | left |
| `NAME/manual_20260515_150106` | 60 | right |
| `NAME/manual_20260515_162433` | 60 | right |

Training-effective `NAME` samples:

- 640 files are currently usable by the training scripts.
- 0 files are skipped for missing hand metadata.
- 0 files are skipped for wrong shape.
- Manual samples are forced into the training split by default.

## Diagnosis

The issue is not lack of raw sequence count. The issue is label consistency.

`NAME` is being trained from mixed sources:

- external WLASL-style samples with `dominant_hand=auto`
- manual right-hand demo samples
- one manual left-hand group
- likely different signing styles between standard external data and the
  one-hand VoxGest demo shortcut

That mixture is risky because the demo uses a controlled one-hand accessibility
vocabulary, while external `NAME` samples may contain signer variation,
different framing, and standard or two-hand motion. The latest confusion with
`THANKYOU` and `PLEASE` suggests the live one-hand `NAME` path overlaps with
short outward/near-body motions. The older `STOP` confusion shows the same
problem against another short high-confidence class.

## Decision

Use one official VoxGest one-hand `NAME` shortcut only.

For this demo, do not vary the `NAME` sign heavily while repairing it. Teach one
clean version first. If the official shortcut cannot be made reliable after this
emergency sprint, dynamic `NAME` should be disabled for the final demo and the
concept should be handled by fingerspelling:

```text
N-A-M-E
```

The phrase/avatar layer may still display phrases such as "What is your name?",
but live dynamic recognition should not rely on `NAME` if it remains unstable.

## Archive Preview Result

Dry-run command executed:

```powershell
.\voxgest_env\Scripts\python.exe scripts_ml\23_archive_manual_samples.py NAME --latest-groups 3
```

Result:

```text
NAME manual samples: 180 latest_groups=3
Dry run found 180 manual samples.
```

No files were archived. No delete/move was applied.

The latest three manual groups are metadata-complete, but they include one
left-hand group and two right-hand groups. Do not archive them automatically
unless a visual review confirms they were recorded with the wrong motion,
wrong hand behavior, or inconsistent signing style.

If visual review confirms the latest three groups are contaminated, use:

```powershell
.\voxgest_env\Scripts\python.exe scripts_ml\23_archive_manual_samples.py NAME --latest-groups 3 --apply
```

If only the latest two right-hand groups are contaminated, use:

```powershell
.\voxgest_env\Scripts\python.exe scripts_ml\23_archive_manual_samples.py NAME --latest-groups 2 --apply
```

For this demo, `NAME` should become manual-only if the official one-hand
shortcut is kept. The current archive tool only archives manual samples, so
excluding external `NAME` samples would need a separate backup/exclusion step or
a training filter. Do not move external files without backing up the stable
demo10 model and dataset state.

## Recording Pass 1: NAME Only

Use exact right-hand mode only:

```powershell
$env:VOXGEST_ENABLE_PHRASE='0'
$env:VOXGEST_WORD_PROFILE='demo10'
$env:VOXGEST_SINGLE_HAND_POSE='1'
$env:VOXGEST_DOMINANT_HAND='right'
$env:VOXGEST_MANUAL_SEQUENCES_PER_WORD='120'
.\voxgest_env\Scripts\python.exe scripts_ml\16_record_manual_words.py NAME
```

Recording instructions:

- Use only one official VoxGest `NAME` motion.
- Keep the right hand visible.
- Keep camera position the same as the demo.
- Start neutral, perform `NAME`, return neutral.
- Make `NAME` clearly different from `STOP`, `THANKYOU`, and `PLEASE`.
- Do not use auto hand mode.
- Do not use two-hand motion.
- Do not vary the sign too much yet; teach one clear version first.

## Recording Pass 2: Contrast Words

Keep the same environment values and reduce the target count:

```powershell
$env:VOXGEST_ENABLE_PHRASE='0'
$env:VOXGEST_WORD_PROFILE='demo10'
$env:VOXGEST_SINGLE_HAND_POSE='1'
$env:VOXGEST_DOMINANT_HAND='right'
$env:VOXGEST_MANUAL_SEQUENCES_PER_WORD='60'
.\voxgest_env\Scripts\python.exe scripts_ml\16_record_manual_words.py STOP THANKYOU PLEASE NOTHING
```

For `NOTHING`, record:

- partial `NAME`
- aborted `NAME`
- transition from `NAME` to neutral
- transition from `STOP` to neutral
- transition from `THANKYOU` to neutral
- transition from `PLEASE` to neutral
- random non-word hand movement
- hand entering/leaving frame

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

## NAME Gate Live Test

Base command:

```powershell
$env:VOXGEST_ENABLE_PHRASE='0'
$env:VOXGEST_WORD_PROFILE='demo10'
$env:VOXGEST_DYNAMIC_MODEL='auto'
$env:VOXGEST_DOMINANT_HAND='right'
$env:VOXGEST_MODE='WORDS'
.\voxgest_env\Scripts\python.exe scripts_ml\33_live_word_test_logger.py
```

Ordered test:

```text
NAME
NAME
NAME
NAME
NAME
STOP
STOP
STOP
THANKYOU
THANKYOU
THANKYOU
PLEASE
PLEASE
PLEASE
NOTHING
NOTHING
NAME
STOP
THANKYOU
PLEASE
```

Command-line expected-label form:

```powershell
$env:VOXGEST_ENABLE_PHRASE='0'
$env:VOXGEST_WORD_PROFILE='demo10'
$env:VOXGEST_DYNAMIC_MODEL='auto'
$env:VOXGEST_DOMINANT_HAND='right'
$env:VOXGEST_MODE='WORDS'
.\voxgest_env\Scripts\python.exe scripts_ml\33_live_word_test_logger.py NAME NAME NAME NAME NAME STOP STOP STOP THANKYOU THANKYOU THANKYOU PLEASE PLEASE PLEASE NOTHING NOTHING NAME STOP THANKYOU PLEASE
```

Pass condition:

- `NAME` at least 6/7 correct
- `STOP` at least 3/3 correct
- `THANKYOU` at least 3/3 correct
- `PLEASE` at least 2/3 correct
- `NOTHING` must not output false words

## If NAME Still Fails

If `NAME` fails this emergency repair:

1. Mark `NAME` as unstable.
2. Do not use dynamic `NAME` in the final demo.
3. Use fingerspelling `N-A-M-E` for the NAME concept.
4. Keep phrase/avatar responses allowed for text like "What is your name?"
5. Keep the reliable demo set as:

```text
YES, NO, PLEASE, WATER, HELLO, HELP, STOP, DOCTOR, THANKYOU, NOTHING
```

`NAME` can remain in the Python training contract temporarily to avoid changing
the model output shape during defense preparation, but it should not be relied
on in the scripted final demo unless it passes the gate above.
