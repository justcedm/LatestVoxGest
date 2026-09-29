# Core5 OOD rejection and dual-stream geometry — offline qualification, 2026-09-29

**Decision: do not implement on Android.** The source-calibrated confidence×OOD proposal rejects many more held-out non-Core5 FSL clips than classifier confidence alone, but loses NO and YES on saved Samsung events and still accepts arbitrary waves/partial gestures. Geometry removes those saved false peaks only by rejecting most true signs. This is an exploratory offline experiment, not a deployable gate or formal accuracy claim. The modern UI, Avatar, production gate, deployed classifier, vocabulary, and Android code were unchanged.

## Provenance, split, and extraction

- The authoritative DLSU FSL-105 `labels.csv` and **official `train.csv` only** supplied the five Core5 positives and all other FSL-105 negatives. Official `test.csv` and its clips were not opened. Samsung events were sealed evaluation-only and contributed no fitting, early stopping, calibration, or variant selection.
- All 1,704 official-train MP4s were SHA-256 inventoried. Two byte-identical *cross-label* groups (PARENTS/UNCLE and GRANDMOTHER/COUSIN) were quarantined on both sides: four non-Core5 rows. No Core5/Other duplicate crossing was found. The remaining 1,700 were SHA-ordered within label: first two calibration, next two holdout, remainder fit. Core5 = 61/10/10; Other FSL = 1,219/200/200. Signer identity was unavailable; the split is **not signer-independent**. Near-duplicates cannot be ruled out by byte hashes.
- Both binary labels used the same unmirrored MediaPipe Tasks 0.10.35 pose/hand assets and settings, anatomical left/right assignment, pose99+left63+right63 FullSign225, approximately 100-ms sparse observations, first-to-last pose+hand envelope plus 100-ms context, and timestamp-linear resampling to 48. Feature version: `core5_ood_tasks_fullsign225_sparse10fps_timestamp48_v1`. Private source videos, extracted raw landmarks, tensors, and model binary remain outside Git.
- Extraction status: 1,534 PASS (including all 81 Core5), 165 Other-FSL REVIEW, one Other-FSL fit clip with NO_VALID_ENVELOPE. The unusable fit clip was excluded; no development denominator was dropped. REVIEW clips remain in the primary analysis, with quality-PASS-only metrics preserved in the private evaluation artifact.

## Binary rejector and fixed-classifier comparison

The separate candidate is a 30,761-parameter, 130,932-byte float32 temporal Conv1D binary network. It consumes `[1,48,225]`, emits `[1,1]` probability of `CORE5_LIKE`, and does not replace or retrain the fixed five-class SIM10 classifier (SHA-256 `3702ff77c1c44a60f0dc7f06e19e778b6498df7dbf7e205991dc15158b8e888f`). Candidate SHA-256: `a95776ea824b36db9034302b235e227808c0f14736d61f809d458a0f75070a66`. Keras/TFLite maximum probability difference on held-out source tensors: `4.172325134277344e-7`. The 20-epoch model was early-stopped on official-train calibration only. No binary artifact is installed or committed.

Score cutoffs came **only** from the ten usable Core5 calibration examples, targeting at least 9/10 retained: confidence `0.9932718873`, margin `0.9881432652`, OOD `0.3707890809`, confidence×OOD `0.3706059456`. These are exploratory score cutoffs, **not** production-gate thresholds. The two conjunctions below use the same OOD cutoff plus source-fit geometry; the final one additionally tests the existing 0.95 confidence value.

| Offline score rule | Source calibration Core5 accepted / Other rejected | Independent official-train holdout Core5 accepted / Other rejected | Holdout wrong Core5 accepted |
|---|---:|---:|---:|
| Five-class confidence only | 9/10 / 104/200 | 8/10 / 104/200 | 0 |
| Five-class top1–top2 margin only | 9/10 / 104/200 | 8/10 / 103/200 | 0 |
| Binary OOD only | 9/10 / 161/200 | 8/10 / 155/200 | 0 |
| Confidence × binary OOD | 9/10 / 167/200 | 8/10 / 162/200 | 0 |
| Binary OOD AND raw geometry | 9/10 / 165/200 | 6/10 / 160/200 | 0 |
| Binary OOD AND geometry AND confidence ≥0.95 | 8/10 / 174/200 | 5/10 / 171/200 | 0 |

The source-calibration selection rule (≥9/10 Core5 retained, then greatest Other-FSL rejection) selected confidence×OOD **before** Samsung replay. Its held-out clip-level Core5 recall is `8/10 = 80%`; Other-FSL rejection is `162/200 = 81%`; false accept and false reject rates are `19%` and `20%` respectively. Both held-out NO clips were correctly classified as NO by the fixed five-class model yet rejected by the OOD score. Accepted Other-FSL confusions include days/time terms, numbers, family terms, and color terms; the private evaluation JSON carries every source record and full five-class probability vector. These metrics are over only 10 positive holdout clips (two per class), so confidence intervals are broad.

## Raw-landmark semantic geometry

The geometry stream uses preserved **RAW image-coordinate** pose/hand landmarks before independent hand scaling. Palm/wrist vectors and fingertip distances to nose, mouth, eye midpoint, and shoulder midpoint are divided by shoulder width. It also records vertical level; elbow angle/forearm direction; palm axis/orientation; finger opening/flexion; inter-hand distance/alignment when both hands are present; and timestamped trajectory displacement, length, and velocity. Pose-face anchors were adequate for this feasibility measurement; no Face Landmarker was added. The independently hand-scaled 225 tensor is **not** used to reconstruct face-relative distances.

Fit-only (61 clips) median examples, in shoulder-width units where applicable:

| Class | Palm→nose | Hand opening | Trajectory length |
|---|---:|---:|---:|
| HELLO | 0.555 | 1.918 | 2.179 |
| THANK YOU | 1.312 | 2.114 | 2.509 |
| YES | 0.823 | 0.519 | 1.922 |
| NO | 0.864 | 0.933 | 2.326 |
| UNDERSTAND | 0.485 | 1.071 | 3.231 |

This is empirical location+shape+motion evidence, **not** a `HELLO = forehead` rule. Location overlaps, especially HELLO/UNDERSTAND and YES/NO. Fit-derived robust centroids/p95 leave-one-out plausibility retained 18/20 source development Core5 clips and assigned the nearest geometry class correctly to 15/20; this differs from the earlier geometry audit because the current experiment uses sparse timestamp-aware extraction. As a hard conjunction, geometry drops held-out Core5 acceptance from 8/10 to 6/10 and saved Samsung first-correct retention from 28/53 to 11/53. It is therefore a diagnostic feature stream, not an approved acceptance gate.

## Rolling-window fusion on held-out source and sealed Samsung

Three consecutive same-duration quality windows with the same top-1 and ≤500-ms gaps define a first stable peak. Durations came from source-fit clips only; the window sampler does not require hand disappearance. The table compares hypothetical **first-stable** outcomes, not actual app output. Source holdout contains 10 Core5 and 200 Other-FSL events; saved Samsung has 53 deliberate Core5 events and 16 operator-confirmed non-sign events. The latter are a small, non-independent diagnostic set and were not used to adjust any cutoff.

| Rule | Source correct first-stable / 10 | Source Other-FSL false peaks / 200 | Samsung correct first-stable / 53 | Samsung wrong first-stable / 53 | Samsung non-sign false peaks / 16 |
|---|---:|---:|---:|---:|---:|
| Confidence only | 5 | 103 | 33 | 3 | 7 |
| Binary OOD only | 5 | 22 | 28 | 0 | 4 |
| **Confidence × OOD, source-selected** | **5** | **20** | **28** | **0** | **4** |
| Binary OOD AND geometry | 4 | 18 | 11 | 0 | 0 |
| Binary OOD AND geometry AND confidence ≥0.95 | 4 | 12 | 11 | 0 | 0 |

For the source-selected rule, saved Samsung true-sign first-correct retention is `28/53 = 52.8%`, below the already inadequate confidence-only `33/53 = 62.3%`. It reduces negative false peaks from `7/16` to `4/16`: neutral `0/5 → 0/5`, arbitrary non-FSL waves `6/6 → 3/6`, and incomplete/aborted gestures `1/5 → 1/5`. Crucially, per-class selected correct first peaks are HELLO 12/14, THANK YOU 8/9, YES **0/11**, NO **0/9**, UNDERSTAND 8/10. Confidence alone had NO 6/9; the rejector erases that. The system still neither rejects waves reliably nor recognizes all five signs. The geometry conjunction obtains 0/16 negative peaks but only 11/53 correct signs—unacceptable retention. Score calibration on Samsung would contaminate the sealed check and was not performed.

Every saved Samsung event's final-tensor `CLASSIFIER_TOP1`, `CLASSIFIER_CONFIDENCE`, `OOD_SCORE`, `GEOMETRY_SCORE`, source-selected `FIRST_STABLE_CLASS`, hypothetical `FINAL_ACCEPT_REJECT`, and `LEGACY_END_REASON` is in [CORE5_OOD_SAMSUNG_REPLAY_MATRIX_20260929.csv](CORE5_OOD_SAMSUNG_REPLAY_MATRIX_20260929.csv). Blank fields mean no final tensor or stable peak. The private full replay (including variant outcomes, timestamps, and source event outcomes) has SHA-256 `3f20458c9a343294a56e5fb243f0c1ef581046fdb4af58b0569ba110ecdbac8f`; it is not committed. No Android event was changed or replayed as training data.

## Decision and next experiment

`OOD_DATASET_READY=YES` for the train-only exploratory split; **not** a final linguistic or signer-independent benchmark. `SEMANTIC_GEOMETRY_READY=YES` as an offline raw-landmark stream, not as a qualified gate. `FUSION_RESULT=NOT_QUALIFIED`: the source-only-selected OOD rule materially lowers non-sign false peaks but retains too few signs and specifically loses NO; geometry is more restrictive still. `FACE_LANDMARKER_NEEDED=NO` for this sprint; pose anchors suffice to test the present hypothesis. `ANDROID_IMPLEMENTATION_AUTHORIZED=NO`.

Next exact action: preserve this result, then build an **independently annotated, source-only training/calibration protocol** for true non-sign waves/partial gestures and hand-visible holds, plus a signer-separated or cross-device Core5 evaluation set if provenance permits. Audit the NO OOD false-reject and YES Android raw-classification/domain-shift failures by reviewing source-vs-Samsung raw-landmark distributions and event boundaries. Retrain or recalibrate only an isolated candidate after that evidence; do not tune this score on the sealed Samsung set or modify Android now.
