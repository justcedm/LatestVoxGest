# FullSign225 Anastacia Seed Training Run

## Purpose

This is an early seed/spoiler training run. It proves that teammate recorder output can be copied, audited, trained, exported, and prepared for live testing before Earle, Mariella, and Ced samples are merged.

This is not the final team model and does not replace demo10, onehand162, fullsign225_team16, fullsign225_manual15, or fullsign225_manual5.

## Source Dataset

Source recorder output:

`external_datasets/team_incoming_recorded_features/ANASTACIA/recorded_features/fullsign225_manual5_team_features`

Prepared seed dataset:

`external_datasets/fullsign225_manual5_anas_seed_features`

## Profile

- Word profile: `fullsign225_manual5_anas_seed`
- Feature profile: `fullsign225`
- Input shape: `[1, 30, 225]`
- Labels: `EAT`, `WATER`, `HELLO`, `THANKYOU`, `NOTHING`
- Model kind: TCN

## Prepare Result

| Label | Copied | Skipped |
| --- | ---: | ---: |
| EAT | 31 | 0 |
| WATER | 30 | 0 |
| HELLO | 30 | 0 |
| THANKYOU | 30 | 0 |
| NOTHING | 50 | 0 |

Total copied valid samples: 171.

## Audit Result

| Label | Samples | Required | Groups | Wrong Shape | Unreadable |
| --- | ---: | ---: | ---: | ---: | ---: |
| EAT | 31 | 20 | 1 | 0 | 0 |
| WATER | 30 | 20 | 1 | 0 | 0 |
| HELLO | 30 | 20 | 1 | 0 | 0 |
| THANKYOU | 30 | 20 | 1 | 0 | 0 |
| NOTHING | 50 | 30 | 1 | 0 | 0 |

Audit passed for a seed run. The warning is that this dataset uses one signer only, so validation does not prove team/general live performance.

## Training Result

TCN training completed.

- Train sequences: 137
- Validation sequences: 34
- Validation split: random fallback because each label has one source group
- Best validation accuracy: 47.06%
- TFLite size: about 309 KB
- TFLite input: `[1, 30, 225]`
- TFLite output: `[1, 5]`

Per-class validation:

| Label | Correct | Total | Accuracy | Top miss |
| --- | ---: | ---: | ---: | --- |
| EAT | 1 | 6 | 16.7% | HELLO |
| WATER | 0 | 6 | 0.0% | THANKYOU |
| HELLO | 6 | 6 | 100.0% | - |
| THANKYOU | 5 | 6 | 83.3% | NOTHING |
| NOTHING | 4 | 10 | 40.0% | HELLO |

## Weak Labels

Weakest validation labels:

- `WATER`
- `EAT`
- `NOTHING`

Stronger seed labels:

- `HELLO`
- `THANKYOU`

This confirms the pipeline works, but it also confirms that the seed model is not ready to promote.

## Artifacts Created

- `model/voxgest_tcn_fullsign225_manual5_anas_seed.h5`
- `model/voxgest_tcn_fullsign225_manual5_anas_seed.tflite`
- `model/class_labels_tcn_fullsign225_manual5_anas_seed.json`
- `model/tcn_training_report_fullsign225_manual5_anas_seed.json`
- `model/runtime_manifest_fullsign225_manual5_anas_seed.json`

## Live Test Command

```powershell
$env:VOXGEST_ENABLE_PHRASE='0'
$env:VOXGEST_WORD_PROFILE='fullsign225_manual5_anas_seed'
$env:VOXGEST_FEATURE_PROFILE='fullsign225'
$env:VOXGEST_DYNAMIC_MODEL='tcn'
$env:VOXGEST_SINGLE_HAND_POSE='0'
$env:VOXGEST_MODE='WORDS'
$env:VOXGEST_LIVE_TEST_CAPTURE_MODE='hand_trigger_auto'
$env:VOXGEST_LIVE_TEST_LABELS='EAT,WATER,HELLO,THANKYOU,NOTHING'
$env:VOXGEST_LIVE_TEST_TRIALS_PER_LABEL='5'
.\voxgest_env\Scripts\python.exe scripts_ml\33_live_word_test_logger.py
```

## Recommendation

Use this run only to verify the end-to-end teammate-recorder pipeline. Retrain the final model only after Earle, Mariella, and Ced samples are merged, audited, and balanced. Do not make this seed model default.
