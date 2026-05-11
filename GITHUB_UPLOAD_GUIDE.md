# VoxGest GitHub Upload Guide

This repository is prepared for a source-first GitHub upload.

## Included

- Python source in `scripts_ml/`
- Current runtime model files in `model/`
- Avatar contract files in `avatar/`
- Current documentation and handoff notes
- Lightweight reports in `reports/`
- `requirements.txt` for recreating the Python environment

## Excluded From Git

These stay on the local machine unless intentionally shared through cloud
storage or Git LFS:

- `voxgest_env/`
- `dataset_*/`
- `wlasl_videos/`
- Python `__pycache__/`
- raw video files

## Recreate The Environment

```powershell
python -m venv voxgest_env
.\voxgest_env\Scripts\python.exe -m pip install --upgrade pip
.\voxgest_env\Scripts\pip.exe install -r requirements.txt
```

## Connect To A New GitHub Repository

Create an empty GitHub repository first, then run:

```powershell
git remote add origin https://github.com/YOUR_USERNAME/YOUR_REPOSITORY.git
git push -u origin recognition-hardening-v1
git push origin rh-v1-baseline
```

If `origin` already exists, use:

```powershell
git remote set-url origin https://github.com/YOUR_USERNAME/YOUR_REPOSITORY.git
git push -u origin recognition-hardening-v1
git push origin rh-v1-baseline
```

## If GitHub Rejects The Push

This project previously tracked very large generated assets. If GitHub rejects
the push because of old history, upload the clean prepared snapshot instead:

```powershell
git checkout --orphan github-clean-upload
git add .
git commit -m "Prepare VoxGest recognition hardening upload"
git push -u origin github-clean-upload
```

Then set `github-clean-upload` as the default branch on GitHub.
