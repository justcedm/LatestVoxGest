# FullSign225 Manual5 Team Recorder Pack Guide

## Purpose

The teammate recorder pack lets other signers record FullSign225 landmark samples without touching the Android app, model training, or the main VoxGest Python environment.

Teammates only record. Training stays on Ced's main VoxGest PC so the dataset can be audited, merged, backed up, and trained consistently.

## Words Collected

The pack records:

- `EAT`
- `WATER`
- `HELLO`
- `THANKYOU`
- `NOTHING`

The pack profile is `fullsign225_manual5_team`, which uses the same 30-frame, 225-feature contract as the manual FullSign225 experiments.

## What Teammates Send Back

Each teammate should zip this folder from inside the pack:

```text
recorded_features/
```

Ask them to name the zip with their signer ID, for example:

```text
TEAMMATE_A_recorded_features.zip
```

## Where To Place Teammate Zips

On the main VoxGest PC, place teammate zips in a local, untracked collection folder such as:

```text
external_datasets/fullsign225_manual5_team_zips/
```

Do not commit this folder to GitHub.

## How To Merge Later

After unzipping each teammate package, merge the contents into a local training candidate folder:

```text
external_datasets/fullsign225_manual5_team_features/
```

Expected merged structure:

```text
external_datasets/
  fullsign225_manual5_team_features/
    EAT/
    WATER/
    HELLO/
    THANKYOU/
    NOTHING/
    metadata_lstm_v2.json
    rejected_samples.jsonl
```

If multiple teammates have separate `metadata_lstm_v2.json` files, merge metadata carefully rather than overwriting. The safest first pass is:

1. Copy label folders together.
2. Keep each teammate zip as backup.
3. Rebuild or merge metadata in a controlled script before training.

## Audit Later

Before training, audit the merged features:

```powershell
$env:VOXGEST_WORD_PROFILE='fullsign225_manual5'
$env:VOXGEST_FEATURE_PROFILE='fullsign225'
$env:VOXGEST_FULLSIGN225_MANUAL16_DATASET='C:\BSIT 3RD YEAR\New VovGest\external_datasets\fullsign225_manual5_team_features'
.\voxgest_env\Scripts\python.exe scripts_ml\42_audit_fullsign225_manual5_dataset.py
```

The current manual5 audit script defaults to `external_datasets/fullsign225_manual16_features`, so override the dataset path before using teammate data for final training.

## Train Later

Training should only happen on Ced's main VoxGest PC after the merged dataset passes audit.

Example training command:

```powershell
$env:VOXGEST_ENABLE_PHRASE='0'
$env:VOXGEST_WORD_PROFILE='fullsign225_manual5'
$env:VOXGEST_FEATURE_PROFILE='fullsign225'
$env:VOXGEST_SINGLE_HAND_POSE='0'
$env:VOXGEST_LSTM_DATASET='C:\BSIT 3RD YEAR\New VovGest\external_datasets\fullsign225_manual5_team_features'
$env:VOXGEST_MIN_SEQS_PER_CLASS='30'
$env:VOXGEST_MIN_GROUPS_PER_CLASS='1'
$env:VOXGEST_RANDOM_VAL_FALLBACK='1'
.\voxgest_env\Scripts\python.exe scripts_ml\24_train_tcn.py
```

## Why Teammates Do Not Train

Training on multiple laptops would make results inconsistent because each laptop may have different package versions, GPU/CPU behavior, local paths, and partial datasets. Centralized training keeps the model contract stable and avoids accidental overwrites of demo10, onehand162, team16, or manual15 artifacts.

## GitHub Safety

Do not commit:

- teammate zips
- `recorded_features/`
- raw videos
- `.npy` or `.npz` feature arrays
- local virtual environments

Only source scripts, batch files, and documentation belong in GitHub.
