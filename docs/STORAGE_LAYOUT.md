# Canonical storage and retirement boundaries

Canonical home: `D:\VoxGest`. Source lineage begins at reviewed commit
`5ca87048592a0d4cdd880afa6a03a8f5c62e28fb` on the separate
`recognition/fsl105-mapua-unified-tasks-v1` branch. No merge to main.

```text
D:\VoxGest\
  repository\LatestVoxGest\          Active source; fresh GitHub clone
  datasets\raw\mapua_transactional_v1\  Published originals, MP4 + audit NPY
  datasets\raw\fsl105_v2\               Published CSVs and numeric video folders
  datasets\manifests\                   Source paths/checksums and audit manifests
  datasets\processed\unified_tasks_v1\  Empty until new contract passes parity
  training\notebooks\                   Future resumable extraction/training
  training\checkpoints\baselines_c_20260922\  Preserved old training artifacts
  models\candidates\                    Future candidates; does not replace baselines
  artifacts\apk\                        Immutable installed/test APKs
  evidence\                             Private device logs/screenshots/captures
  reports\storage_migration\            Private full-path verification manifests
  reports\dataset_audit\                Future audit, not training approval
  reports\training\                     Future controlled experiment results
  quarantine\retired_c_drive\20260922\  Historical C copies, NOT deletion permission
  quarantine\asl_non_fsl\               Known WLASL/ASL; never FSL training inputs
  quarantine\unvalidated_legacy\        Team/phrase/mixed-source features and recordings
```

## Active does not mean newly trained

Practical15 is already trained primarily on Mapua PASS-only data. Standard FSL105,
Mapua14, demo10, Avatar and all rollback assets/labels/gates remain intact. The new
unified Tasks candidate has NOT been trained. Empty future directories are intentional.
Older runtime assets can still be required active dependencies: age alone does not
establish obsolescence. Do not strip assets from the repository to tidy it.

## Strict source separation

Only the two published raw dataset roots are potential inputs to the future unified
audit. Existing Mapua NPY is preserved for forensic/baseline reproduction, not directly
mixed with FSL105 video-derived tensors. Both sources require the same new pipeline.
All quarantined data is excluded. Do not recursively train over the canonical root.
Do not call mixed legacy/team material ASL without provenance evidence; it is still
excluded until validated. Keep numerical FSL105 folder IDs unchanged.

FSL105 preserves the supplied extra directory layer: CSVs live at raw/fsl105_v2;
resolve their `clips/<id>/<file>` paths against raw/fsl105_v2/clips, giving the actual
raw/fsl105_v2/clips/clips/<id>/<file>. All2130 rows must resolve uniquely; no guessed
numeric-label mapping, flattening, or source-CSV rewrite.

## Build-only local files and history

The fresh clone does not silently inherit ignored C files. Current device evidence
is copied separately under evidence. The old worktree including Git history and
ignored material is preserved as a historical snapshot, not overlaid on the clone.
One existing ignored video, app/src/main/res/raw/fsl_hello_avatar_preview.mp4, is
required by the existing Avatar preview activity; restore it only with the documented
source/destination hash manifest. Avatar code and video bytes must remain unchanged.
Machine-local SDK configuration stays ignored. APKs/logs/paths/credentials are not Git
checkpoint content. Full manifests are local under the canonical reports directory.

## Retirement is gated

No initial-run deletion or moving of source workspaces. A retirement candidate is
not authorization to delete. Require exact source/destination SHA256 equality,
readability, a successful D build, preserved untracked evidence, no source process
usage, and explicit approval of the final exact manifest. Source worktree in this
running Codex session cannot be retired. Reopen Codex at the new D repository after
migration and before any ML work. SDK/Python/Git/Gradle/tools and general Downloads
are not retirement candidates.
