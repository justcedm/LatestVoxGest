# Reproduce the remote survey audit

This is a failed-source-gate checkpoint, not a survey handoff. Keep private inputs and outputs outside the Git checkout. Python 3.11 with numpy and opencv-python, and Blender 3.2.0 e05e1e369187 were used. No pip install or tool upgrade is required in the recorded environment.

From the Avatar branch checkout, assign private absolute paths for CORE, CANDIDATE, BLEND, SOURCE, and a NEW output directory. Source SHA256 and candidate SHA256 must match the detailed report before comparing. Scripts fail rather than overwrite their output files/directories.

```powershell
python tools/avatar_survey/audit_runtime.py --core $CORE --candidate $CANDIDATE --out $NEW_REPORT
& $BLENDER --background --factory-startup --disable-autoexec --python-exit-code 1 --python tools/avatar_survey/render_review.py -- --blend $BLEND --action THANK_YOU --out $NEW_PRIVATE_DIRECTORY
python tools/avatar_survey/compare_video.py --source $SOURCE --candidate "$NEW_PRIVATE_DIRECTORY/THANK_YOU_candidate_60fps.mp4" --out $NEW_COMPARISON_MP4
```

The runtime audit only supports the dense LINEAR glTF accessors used here; unsupported storage fails closed. It compares Core3 animation definitions and decoded accessor bytes, platform definitions and the entire original binary prefix. Numeric measures are not contact, deformation or FSL judgments. The render script only prepares a NEW review blend/movie and does not export a GLB. The comparison aligns source frame 1 with candidate frame 1, checks equal frame count and 60 FPS, and emits a review MP4 plus hashes. The GLB stores its first key at 1/60 second; Blender review frame 1 corresponds to that first key.

Private Earle inputs relative to the outer workspace:

- Core: `work/LatestVoxGest/android_dry_run/app/src/main/assets/avatar/core3/voxgest_avatar_B32_CORE3_RC2.glb`.
- Existing candidate: `work/core3_rebuild_20260927/artifacts/avatar_rebuild/voxgest_avatar_CORE3_PLUS_SUPERVISED9_RC1.glb`.
- Existing review blend: `work/core3_rebuild_20260927/artifacts/avatar_rebuild/voxgest_core3_supervised9_ALL9_REVIEW_v1.blend`.
- Source: neighboring `2026-09-16/you-are-continuing-the-voxgest-avatar/work/recovered_authoritative/SOURCE_HANDOFF/fsl105_reference_videos/THANK YOU/0.MOV`.

To resume, read the live handoff, machine-readable state, detailed audit, and OWNER_FILE_REQUESTS. Obtain REF-TY-001 before altering the contact target. Review preparation/stroke/hold/recovery at normal speed, then frame-step contact and frames 127–128. Record failures rather than smoothing to meet an arbitrary angle threshold. Inspect side and perspective as well as front; the movie is front-only evidence and does not establish depth clearance.

When all four Actions eventually pass pre-export gates, create a private NEW owner package containing candidate GLB, animation_manifest.json, action_mapping.json, SHA256SUMS.txt, ANDROID_INTEGRATION_README.md, CALIBRATION_STATUS.md, and BUILD_TEST_REPORT.md. Use explicit unavailable/fallback behavior for unapproved production concepts. Keep `listen_ready=false` until device and required linguistic gates pass. Public Git may store hashes and safe instructions, not these private source assets.

Git delivery after local sign-in:

```powershell
git status --short
git diff --cached --check
git push origin HEAD:avatar/astra-calibration-20260914
git fetch origin avatar/astra-calibration-20260914
git rev-parse HEAD
git rev-parse origin/avatar/astra-calibration-20260914
git ls-remote origin refs/heads/avatar/astra-calibration-20260914
```

All three hashes must match before declaring remote checkpoint delivery. Do not force push if the remote advances; preserve local commits and reconcile safe changes normally.
