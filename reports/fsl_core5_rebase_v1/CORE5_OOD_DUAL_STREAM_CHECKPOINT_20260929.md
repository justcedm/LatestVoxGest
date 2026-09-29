# Core5 OOD and dual-stream sprint — extraction checkpoint, 2026-09-29

Status: **IN PROGRESS**, offline only. No Android, modern UI, Avatar, production gate, existing five-class model, or vocabulary change. The required architecture-direction document was absent from this branch and read completely from freshly fetched `origin/main` without checkout. Previous streaming feasibility commit `7249ca6259f46b7090120196211fd4a32bfa0b68` was verified as branch HEAD at the start.

## Official-source split and provenance

Only authoritative `DLSU_FSL105_V2` `labels.csv` and `train.csv` were opened; `test.csv` and official test clips remain sealed. Every source train video was hashed. Four rows form two **byte-identical, cross-label** pairs: PARENTS/UNCLE and GRANDMOTHER/COUSIN. Both members of each conflicting pair are quarantined, not fitted or evaluated. They are all non-Core5; no Core5/Other duplicate crossing was found. The private source manifest records exact hashes and paths. No signer identities are supplied, so this is not signer-independent splitting.

Among the 1,700 eligible official-train clips, deterministic within-class SHA ordering assigns first two per class to calibration, next two to holdout, and remaining to fitting. Counts: Core5 fit 61, calibration 10, holdout 10; Other FSL fit 1,219, calibration 200, holdout 200. The official test split contributes **zero** clips. Samsung events contribute **zero** fit/calibration clips and remain sealed evaluation evidence.

## Extraction contract and current state

Both binary labels are extracted with the same fresh-per-video MediaPipe Tasks **0.10.35** hand/pose assets and 0.45 detection/presence/tracking settings, anatomical hand slots, unmirrored FullSign225 builder, approximately 100-ms observed sampling, first-to-last pose+hand envelope plus 100-ms context, and timestamp-linear 48-frame resampling. The resulting private representation is versioned `core5_ood_tasks_fullsign225_sparse10fps_timestamp48_v1`; it does **not** replace the current Core5 classifier input or model. The first 20 official-train pilot clips extracted successfully; the idempotent three-worker full extraction is running. Quality `REVIEW` and `NO_VALID_ENVELOPE` statuses are retained explicitly. An unusable fit clip is excluded from fitting; an unusable development clip will count as rejected, not disappear from its denominator.

The offline comparison scripts are prepared but **not yet run**. They specify a small temporal-convolution binary model; source-only calibration thresholds targeting at least 9/10 available positive calibration clips; independent official-train holdout evaluation; unchanged SIM10 classifier confidence/margin comparisons; source-derived raw-landmark geometry; rolling-window stability; and finally sealed Samsung positives/waves/partials. No threshold will be chosen from Samsung diagnostics. The binary model and private tensors will not be committed or installed.

OOD_DATASET_READY=NO; full extraction still running.
OOD_MODEL=NOT_TRAINED.
SEMANTIC_GEOMETRY_READY=YES, existing raw-landmark stream; fusion not yet evaluated.
ANDROID_IMPLEMENTATION_AUTHORIZED=NO.
NEXT_EXACT_ACTION=Finish and audit all 1,700 eligible train extractions, then train/evaluate the binary rejector on fit/calibration/holdout before opening sealed Samsung diagnostics for final offline replay.
