# Sprint30 Model Hardening Report

Generated: 2026-05-17

## Scope

Backend/ML only. Android UI was not modified.

Sprint30 remains experimental. Demo10 remains the safe default until Sprint30
passes right-hand live testing after repair recording.

## Preservation Check

Demo10 backup exists:

`model/backups/demo10_before_sprint30_20260516_1337`

The Sprint30 train/live scripts now use profile-specific artifacts when
`VOXGEST_WORD_PROFILE='sprint30'`:

- `model/voxgest_tcn_sprint30.h5`
- `model/voxgest_tcn_sprint30.tflite`
- `model/class_labels_tcn_sprint30.json`
- `model/tcn_training_report_sprint30.json`
- `model/voxgest_lstm_sprint30.h5`
- `model/voxgest_lstm_sprint30.tflite`
- `model/class_labels_lstm_sprint30.json`
- `model/lstm_training_report_sprint30.json`

Demo10 still maps to the generic `*_v1` files. Do not train with demo10 unless
intentionally refreshing the safe baseline.

## Current Sprint30 Validation Baseline

Before additional hardening data:

| Model | Grouped validation accuracy | Status |
| --- | ---: | --- |
| TCN | 56.34% | first model to harden/live test |
| LSTM | 54.32% | comparison model only |

Priority labels from validation remain weak or risky:

| Label | Current sequences | Groups | TCN validation | LSTM validation |
| --- | ---: | ---: | ---: | ---: |
| THANKYOU | 408 | 15 | 0.0% | 0.0% |
| STOP | 700 | 27 | 40.0% | 38.9% |
| DOCTOR | 640 | 26 | 40.0% | 57.9% |
| UNDERSTAND | 160 | 13 | 30.8% | 33.3% |
| PAIN | 143 | 11 | 0.0% | 50.0% |
| GO | 117 | 9 | 46.2% | 0.0% |
| FINE | 143 | 11 | 11.5% | 50.0% |
| EAT | 104 | 8 | 50.0% | 50.0% |
| TIME | 104 | 8 | 0.0% | 50.0% |
| MEDICINE | 91 | 7 | 0.0% | 0.0% |
| NSAC | 600 | 10 | not in grouped validation split | not in grouped validation split |

## Current Live Log Interpretation

`scripts_ml/34_compare_live_logs.py` was run against the available live logs.
Those logs are demo10-era logs, not a completed Sprint30 live test.

Weakest labels from available logs:

1. NAME
2. STOP
3. HELP
4. NSAC
5. PLEASE
6. DOCTOR

Top issues:

- NAME often confused with STOP, PLEASE, THANKYOU, and HELP.
- STOP still has historical confusion with NAME and HELLO.
- NSAC has false word outputs and needs harder negatives.
- DOCTOR has confusion with YES in existing logs.

Sprint30-specific live quality is still pending because the right-hand
Sprint30 live test must be performed with the camera and signer present.

## Samples Added In This Sprint

No new samples were recorded by Codex in this run because controlled recording
requires the physical signer and camera session.

Samples added per label in this Codex run:

| Label group | Added |
| --- | ---: |
| THANKYOU STOP DOCTOR UNDERSTAND NSAC | 0 |
| PAIN GO FINE TIME MEDICINE NSAC | 0 |
| PLEASE WATER EAT WANT NSAC | 0 |
| extra NSAC hard negatives | 0 |

## Live Test Command

Run this before recording so the next repair pass is based on Sprint30 live
evidence:

```powershell
$env:VOXGEST_ENABLE_PHRASE='0'
$env:VOXGEST_WORD_PROFILE='sprint30'
$env:VOXGEST_DYNAMIC_MODEL='auto'
$env:VOXGEST_DOMINANT_HAND='right'
$env:VOXGEST_SINGLE_HAND_POSE='1'
$env:VOXGEST_MODE='WORDS'
.\voxgest_env\Scripts\python.exe scripts_ml\33_live_word_test_logger.py
```

Then compare logs:

```powershell
$env:VOXGEST_WORD_PROFILE='sprint30'
.\voxgest_env\Scripts\python.exe scripts_ml\34_compare_live_logs.py
```

## Right-Hand Repair Recording

Use exact hand mode only. Do not use `VOXGEST_DOMINANT_HAND='auto'`.

First repair batch:

```powershell
$env:VOXGEST_ENABLE_PHRASE='0'
$env:VOXGEST_WORD_PROFILE='sprint30'
$env:VOXGEST_SINGLE_HAND_POSE='1'
$env:VOXGEST_DOMINANT_HAND='right'
$env:VOXGEST_MANUAL_SEQUENCES_PER_WORD='60'
.\voxgest_env\Scripts\python.exe scripts_ml\16_record_manual_words.py THANKYOU STOP DOCTOR UNDERSTAND NSAC
```

Second repair batch:

```powershell
$env:VOXGEST_ENABLE_PHRASE='0'
$env:VOXGEST_WORD_PROFILE='sprint30'
$env:VOXGEST_SINGLE_HAND_POSE='1'
$env:VOXGEST_DOMINANT_HAND='right'
$env:VOXGEST_MANUAL_SEQUENCES_PER_WORD='60'
.\voxgest_env\Scripts\python.exe scripts_ml\16_record_manual_words.py PAIN GO FINE TIME MEDICINE NSAC
```

Only if live logs show these remain weak:

```powershell
$env:VOXGEST_ENABLE_PHRASE='0'
$env:VOXGEST_WORD_PROFILE='sprint30'
$env:VOXGEST_SINGLE_HAND_POSE='1'
$env:VOXGEST_DOMINANT_HAND='right'
$env:VOXGEST_MANUAL_SEQUENCES_PER_WORD='60'
.\voxgest_env\Scripts\python.exe scripts_ml\16_record_manual_words.py PLEASE WATER EAT WANT NSAC
```

Extra NSAC negatives:

- idle hand
- open hand
- hand entering frame
- hand leaving frame
- partial STOP
- partial THANKYOU
- partial PAIN
- partial MEDICINE
- aborted UNDERSTAND
- transition movements between signs

## Retraining Order

After new samples are recorded, retrain TCN first:

```powershell
$env:VOXGEST_ENABLE_PHRASE='0'
$env:VOXGEST_WORD_PROFILE='sprint30'
$env:VOXGEST_SINGLE_HAND_POSE='1'
.\voxgest_env\Scripts\python.exe scripts_ml\24_train_tcn.py
```

Then train LSTM only for comparison:

```powershell
$env:VOXGEST_ENABLE_PHRASE='0'
$env:VOXGEST_WORD_PROFILE='sprint30'
$env:VOXGEST_SINGLE_HAND_POSE='1'
.\voxgest_env\Scripts\python.exe scripts_ml\19_train_lstm.py
```

The patched trainers write Sprint30 artifacts only for the Sprint30 profile.

## Before/After Status

| Item | Before | After this Codex run |
| --- | --- | --- |
| TCN grouped validation | 56.34% | not retrained; no new samples recorded |
| LSTM grouped validation | 54.32% | not retrained; no new samples recorded |
| Sprint30 live test | pending | command prepared; not physically run |
| Demo10 default | preserved | preserved |

## Recommendation

Keep demo10 as the default. Keep Sprint30 experimental.

Next safest step: run the Sprint30 right-hand live test, record the two
right-hand repair batches plus NSAC hard negatives, retrain TCN, then run a
second Sprint30 live test before considering any app-facing promotion.
