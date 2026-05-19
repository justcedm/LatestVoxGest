# FullSign225 Manual16 Webcam Workflow

## Why We Pivoted

The first `fullsign225_team16` model trained successfully from normalized raw videos and reached 89.04% grouped validation accuracy, but it failed live webcam testing. The live camera behavior did not match the raw-video dataset closely enough.

Latest live test summary:

- capture mode: `hand_trigger_auto`
- tested labels: YES, NO, WATER, HELLO
- correct live matches: 0/12
- YES was confused with PLEASE
- WATER was confused with NAME and TIME
- HELLO was confused with NAME and NOTHING
- NO was rejected because of BAD_SEQUENCE / UNSTABLE_LANDMARKS

The practical conclusion is that the model needs training samples captured from the same webcam style, distance, lighting, mirror behavior, and MediaPipe tracking behavior used during live testing.

## Why Demo10-Style Manual Data

The strongest demo10 behavior came from manual webcam capture because the training data matched the defense/demo environment. This workflow applies the same idea to FullSign225:

- use the same webcam
- use the same front-camera mirrored behavior
- use MediaPipe Holistic live
- save 30-frame feature sequences
- train on the actual landmark behavior seen during live use

This is not a new vocabulary expansion. It is a data-domain repair.

## What Is Preserved

`fullsign225_team16` remains preserved as a baseline. Do not delete it.

This workflow creates a separate experimental profile:

- word profile: `fullsign225_manual16`
- feature profile: `fullsign225`
- input shape: `[1, 30, 225]`
- dataset folder: `external_datasets/fullsign225_manual16_features`

It does not replace:

- demo10
- onehand162
- sprint30
- fullsign225_team16
- generic `*_v1` files

## Why OneHand162 Is Postponed

OneHand162 is still important, but the immediate goal is to make FullSign225 work today using webcam-matched data. OneHand162 hardening can continue separately tomorrow without mixing feature contracts.

## Labels

The active manual16 labels are:

YES, NO, WATER, HELLO, HELP, STOP, DOCTOR, NAME, THANKYOU, PLEASE, SORRY, PAIN, EAT, WANT, TIME, NOTHING

`NOTHING` remains the no-output class.

## Record Samples

Record a small starter set first:

```powershell
$env:VOXGEST_WORD_PROFILE='fullsign225_manual16'
$env:VOXGEST_FEATURE_PROFILE='fullsign225'
$env:VOXGEST_SINGLE_HAND_POSE='0'
$env:VOXGEST_MANUAL_SEQUENCES_PER_WORD='30'
.\voxgest_env\Scripts\python.exe scripts_ml\41_record_manual_fullsign225_words.py YES NO WATER HELLO
```

Record all labels when ready:

```powershell
$env:VOXGEST_WORD_PROFILE='fullsign225_manual16'
$env:VOXGEST_FEATURE_PROFILE='fullsign225'
$env:VOXGEST_SINGLE_HAND_POSE='0'
$env:VOXGEST_MANUAL_SEQUENCES_PER_WORD='30'
.\voxgest_env\Scripts\python.exe scripts_ml\41_record_manual_fullsign225_words.py YES NO WATER HELLO HELP STOP DOCTOR NAME THANKYOU PLEASE SORRY PAIN EAT WANT TIME NOTHING
```

Recording behavior:

- hands-free countdown
- automatic 30-frame capture
- quality check
- accepted samples saved as `.npy`
- rejected samples logged in `rejected_samples.jsonl`
- rejected sequences, when available, are saved under `_rejected`

For `NOTHING`, record hard negatives:

- idle hands visible
- open hands
- both hands relaxed
- hands entering/leaving frame
- partial signs
- aborted signs
- transition movement
- neutral non-word movement

## Audit

```powershell
$env:VOXGEST_WORD_PROFILE='fullsign225_manual16'
$env:VOXGEST_FEATURE_PROFILE='fullsign225'
.\voxgest_env\Scripts\python.exe scripts_ml\42_audit_fullsign225_manual16_dataset.py
```

Reports:

- `reports/fullsign225_manual16_audit.csv`
- `reports/fullsign225_manual16_audit.json`
- `reports/fullsign225_manual16_audit.md`

Before training, check:

- every sample is `(30, 225)`
- no missing labels
- no label under 20 samples for a quick first pass
- 30+ samples per label preferred
- NOTHING has enough hard negatives

## Train TCN

Train only after the audit passes:

```powershell
$env:VOXGEST_ENABLE_PHRASE='0'
$env:VOXGEST_WORD_PROFILE='fullsign225_manual16'
$env:VOXGEST_FEATURE_PROFILE='fullsign225'
$env:VOXGEST_SINGLE_HAND_POSE='0'
$env:VOXGEST_LSTM_DATASET='C:\BSIT 3RD YEAR\New VovGest\external_datasets\fullsign225_manual16_features'
.\voxgest_env\Scripts\python.exe scripts_ml\24_train_tcn.py
```

Expected profile-specific artifacts:

- `model/voxgest_tcn_fullsign225_manual16.h5`
- `model/voxgest_tcn_fullsign225_manual16.tflite`
- `model/class_labels_tcn_fullsign225_manual16.json`
- `model/tcn_training_report_fullsign225_manual16.json`

Do not overwrite generic `*_v1` files.

## Optional Runtime Manifest

After training succeeds, create a separate optional manifest:

- `model/runtime_manifest_fullsign225_manual16.json`

Do not replace `runtime_manifest_v1.json`.

## Live Test

Use the hands-free live logger after training:

```powershell
$env:VOXGEST_ENABLE_PHRASE='0'
$env:VOXGEST_WORD_PROFILE='fullsign225_manual16'
$env:VOXGEST_FEATURE_PROFILE='fullsign225'
$env:VOXGEST_FULLSIGN_ALLOW_HAND_OVERLAP='1'
$env:VOXGEST_DYNAMIC_MODEL='tcn'
$env:VOXGEST_SINGLE_HAND_POSE='0'
$env:VOXGEST_MODE='WORDS'
$env:VOXGEST_LIVE_TEST_CAPTURE_MODE='hand_trigger_auto'
$env:VOXGEST_LIVE_TEST_LABELS='YES,NO,WATER,HELLO'
$env:VOXGEST_LIVE_TEST_TRIALS_PER_LABEL='3'
.\voxgest_env\Scripts\python.exe scripts_ml\33_live_word_test_logger.py
```

If the first four labels improve, expand the live test to the remaining manual16 labels.

## Recommendation

Start with YES, NO, WATER, and HELLO. If those are not live-stable after manual webcam training, do not expand. Fix capture consistency, framing, and hard negatives first.
