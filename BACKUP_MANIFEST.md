# VoxGest Full System Backup Manifest

The full external backup created by `backup_voxgest_full_to_external.bat` is designed to preserve the complete working VoxGest system for continuation on another laptop.

## Backup Destination

```text
D:\RB_SSD_CED\VoxGest_FULL_SYSTEM_BACKUP_YYYY_MM_DD_HHMM\New VovGest
```

The exact latest backup path is also written to:

```text
D:\RB_SSD_CED\VoxGest_LAST_BACKUP_PATH.txt
```

## Included

- Full project source tree
- Git history and repository metadata via `.git`
- Android app source: `android_dry_run`
- ML scripts: `scripts_ml`
- Trained model artifacts: `model`
- Generated reports and audits: `reports`
- Documentation and research notes: `docs`
- Recorder packs and team tools: `tools`
- External datasets and recorded features: `external_datasets`
- Thesis and paper files
- PowerPoint and presentation files
- README and handoff files
- Gradle project files and wrappers
- Source assets, avatar files, and model metadata

## Excluded

Only disposable/generated environment and cache files are excluded:

- `voxgest_env`
- `recorder_env`
- `.gradle`
- `build`
- `__pycache__`
- `.pytest_cache`
- `*.pyc`
- `*.hprof`
- `workspace.xml`

These files are excluded because they can be recreated on a new laptop.

## Verification

Run:

```bat
VERIFY_BACKUP.bat
```

The verifier checks for:

- `android_dry_run`
- `scripts_ml`
- `model`
- `reports`
- `docs`
- `tools`
- `external_datasets`
- `.git`
- `android_dry_run\app`
- key OneHand162 phrase model files
- key FullSign225 phrase model files
- report audit files

## Restore Instructions

See:

```text
RESTORE_ON_NEW_LAPTOP.md
```
