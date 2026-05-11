# Word Live Evaluation Summary

Status: pending live webcam word logging.

Run the right-hand test first:

```powershell
$env:VOXGEST_ENABLE_PHRASE='0'
$env:VOXGEST_WORD_PROFILE='demo10'
$env:VOXGEST_SINGLE_HAND_POSE='1'
$env:VOXGEST_DYNAMIC_MODEL='auto'
$env:VOXGEST_MODE='WORDS'
$env:VOXGEST_DOMINANT_HAND='right'
.\voxgest_env\Scripts\python.exe scripts_ml\33_live_word_test_logger.py
```

Run the left-hand test only if left-hand support is required:

```powershell
$env:VOXGEST_DOMINANT_HAND='left'
.\voxgest_env\Scripts\python.exe scripts_ml\33_live_word_test_logger.py
```

## Scope

Required dynamic labels:

```text
YES, NO, PLEASE, WATER, HELLO, HELP, STOP, DOCTOR, NAME, THANKYOU, NOTHING
```

## Trial Matrix

For each word:

- normal speed
- slow speed
- fast speed
- slightly left angle
- slightly right angle
- farther from camera
- closer to camera

For `NOTHING`:

- idle hand
- open hand
- relaxed hand
- hand entering frame
- hand leaving frame
- transition between signs
- partial/aborted gestures

## Current Result

No live word logs exist yet under:

```text
reports/live_word_test_*.csv
reports/live_word_test_*.json
```

| Label | Accepted Correct | Rejected | Confused With | False Positive Risk | Status |
| --- | ---: | ---: | --- | --- | --- |
| YES | 0 | 0 | Pending | Pending | Pending |
| NO | 0 | 0 | Pending | Pending | Pending |
| PLEASE | 0 | 0 | Pending | Pending | Pending |
| WATER | 0 | 0 | Pending | Pending | Pending |
| HELLO | 0 | 0 | Pending | Pending | Pending |
| HELP | 0 | 0 | Pending | Pending | Pending |
| STOP | 0 | 0 | Pending | Pending | Pending |
| DOCTOR | 0 | 0 | Pending | Pending | Pending |
| NAME | 0 | 0 | Pending | Pending | Pending |
| THANKYOU | 0 | 0 | Pending | Pending | Pending |
| NOTHING | 0 | 0 | Pending | Pending | Pending |

## Failure Diagnosis

Use the logger columns to separate failures:

- `confidence`: model is unsure
- `margin`: model cannot separate first and second class
- `motion`: movement is too small for the current gate
- `wrist_path`: path gate is too strict or motion is too compact
- `hand_presence`: hand visibility/selection issue
- `wrong_label`: data confusion between classes

## Targeted Re-Recording Rule

Do not record every word blindly. Prepare commands only after live results show weak classes.

Likely first target set if live testing confirms weakness:

```text
NOTHING WATER THANKYOU YES NO
```

Right-hand recording command:

```powershell
$env:VOXGEST_ENABLE_PHRASE='0'
$env:VOXGEST_WORD_PROFILE='demo10'
$env:VOXGEST_SINGLE_HAND_POSE='1'
$env:VOXGEST_DOMINANT_HAND='right'
$env:VOXGEST_MANUAL_SEQUENCES_PER_WORD='60'
.\voxgest_env\Scripts\python.exe scripts_ml\16_record_manual_words.py NOTHING WATER THANKYOU YES NO
```

For contaminated data, archive instead of deleting:

```powershell
.\voxgest_env\Scripts\python.exe scripts_ml\23_archive_manual_samples.py WATER THANKYOU YES NO DOCTOR --latest-groups 1
.\voxgest_env\Scripts\python.exe scripts_ml\23_archive_manual_samples.py WATER THANKYOU YES NO DOCTOR --latest-groups 1 --apply
```
