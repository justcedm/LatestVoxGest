# FullSign225 One-Day Demo Recovery Plan

## Decision

Use the Basic TCN as the main FullSign225 manual5 team candidate for the one-day demo window.

Residual Dilated TCN remains experimental. It is not promoted because it adds architecture risk, and the latest validation did not prove a dependable live-demo improvement.

Demo10 remains protected as the safe baseline. FullSign225 is not default unless live testing passes.

## Labels

Current FullSign225 manual5 team labels:

- EAT
- WATER
- HELLO
- THANKYOU
- NOTHING

`NOTHING` remains no-output. Raw predictions must not update sentence text.

## Dataset Counts

Merged dataset:

`external_datasets/fullsign225_manual5_team_features`

Accepted samples after merge:

| Label | Count | One-Day Minimum | Preferred |
| --- | ---: | ---: | ---: |
| EAT | 73 | 80 | 120 |
| WATER | 70 | 80 | 120 |
| HELLO | 70 | 80 | 120 |
| THANKYOU | 70 | 80 | 120 |
| NOTHING | 50 | 120 | 180 |

Per-signer status:

| Signer | EAT | WATER | HELLO | THANKYOU | NOTHING |
| --- | ---: | ---: | ---: | ---: | ---: |
| CED | 0 | 0 | 0 | 0 | 0 |
| ANASTACIA | 31 | 30 | 30 | 30 | 50 |
| MARIELLA | 41 | 40 | 40 | 40 | 0 |
| EARLE | 1 | 0 | 0 | 0 | 0 |

The data is below the one-day minimum target, especially for `NOTHING`.

## Audit Result

Audit script:

```powershell
.\voxgest_env\Scripts\python.exe scripts_ml\48_audit_fullsign225_manual5_team_features.py
```

Audit result:

- Shape target: `(30, 225)`
- Wrong-shape files: `0`
- Unreadable files: `0`
- Minimum target ready: `False`

The arrays are valid, but there are not enough samples yet for a reliable team model.

## Training Result

Training command:

```powershell
$env:VOXGEST_ENABLE_PHRASE='0'
$env:VOXGEST_WORD_PROFILE='fullsign225_manual5_team'
$env:VOXGEST_FEATURE_PROFILE='fullsign225'
$env:VOXGEST_DYNAMIC_MODEL='tcn'
$env:VOXGEST_SINGLE_HAND_POSE='0'
.\voxgest_env\Scripts\python.exe scripts_ml\24_train_tcn.py
```

Artifacts:

- `model/voxgest_tcn_fullsign225_manual5_team.h5`
- `model/voxgest_tcn_fullsign225_manual5_team.tflite`
- `model/class_labels_tcn_fullsign225_manual5_team.json`
- `model/tcn_training_report_fullsign225_manual5_team.json`
- `model/runtime_manifest_fullsign225_manual5_team.json`

Grouped validation:

- Best grouped validation accuracy: `36.42%`

Per-class validation:

| Label | Correct | Total | Accuracy | Top miss |
| --- | ---: | ---: | ---: | --- |
| EAT | 36 | 41 | 87.8% | WATER |
| WATER | 0 | 30 | 0.0% | EAT |
| HELLO | 0 | 30 | 0.0% | EAT |
| THANKYOU | 14 | 40 | 35.0% | EAT |
| NOTHING | 5 | 10 | 50.0% | THANKYOU |

This model is not proven demo-ready. It is only a Basic TCN candidate for immediate live testing.

## Live Test Command

```powershell
$env:VOXGEST_ENABLE_PHRASE='0'
$env:VOXGEST_WORD_PROFILE='fullsign225_manual5_team'
$env:VOXGEST_FEATURE_PROFILE='fullsign225'
$env:VOXGEST_DYNAMIC_MODEL='tcn'
$env:VOXGEST_SINGLE_HAND_POSE='0'
$env:VOXGEST_MODE='WORDS'
$env:VOXGEST_LIVE_TEST_CAPTURE_MODE='hand_trigger_auto'
$env:VOXGEST_LIVE_TEST_LABELS='EAT,WATER,HELLO,THANKYOU,NOTHING'
$env:VOXGEST_LIVE_TEST_TRIALS_PER_LABEL='5'
.\voxgest_env\Scripts\python.exe scripts_ml\33_live_word_test_logger.py
```

## Fallback Rule

If FullSign225 live testing does not pass, use demo10 as the defense/demo baseline.

For FullSign225, only demo labels that pass live testing should be shown. Do not present FullSign225 as production-ready unless live results support it.

## Next Same-Day Capture Priority

1. Record `NOTHING` from Mariella, Earle, and Ced.
2. Record full sets from Ced and Earle.
3. Bring every word label to at least `80` samples.
4. Bring `NOTHING` to at least `120` samples.
5. Retrain Basic TCN and live-test before trying RD-TCN again.
