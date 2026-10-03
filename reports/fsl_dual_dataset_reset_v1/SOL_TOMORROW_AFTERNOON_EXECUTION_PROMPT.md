# SOL — VoxGest FSL Dual-Dataset Reset: Finish by Tomorrow Afternoon

**Authority:** This prompt and `RESET_SOURCE_OF_TRUTH.md` supersede the Scenario-15/ASL-derived phrase plans.
**Branch:** `recognition/fsl-dual-dataset-reset-v1`
**Base:** `496782599734fd369808f4ba3d395550b2b59d71`
**Deadline:** tomorrow afternoon.
**Primary objective:** produce the strongest evidence-backed FSL-only 15-concept Android recognizer possible from the published De La Salle FSL-105 and Mapua Transactional FSL datasets.

## 0. Do not ask broad planning questions

Work autonomously through ordinary engineering decisions. Stop only when:
- an official raw dataset must be downloaded manually and automated download is impossible;
- a human must perform a physical sign batch;
- a destructive/unsafe action would be required.

Never access retired D:.

## 1. First 30 minutes — establish truth

1. Checkout this branch and verify clean worktree.
2. Read:
   - `reports/fsl_dual_dataset_reset_v1/RESET_SOURCE_OF_TRUTH.md`
   - `reports/fsl_dual_dataset_reset_v1/FSL15_SOURCE_MANIFEST.csv`
   - `reports/mapua14_rescue_v1/SUMMARY.md`
   - `reports/dataset_engineering/FSL105_RAW_AUDIT_LIMITATION.md`
   - `reports/dataset_engineering/MAPUA_FSL105_LABEL_MAPPING.csv`
   - `docs/ARCHITECTURE_DECISIONS.md`
3. Quarantine from final FSL evaluation:
   WHAT, YOUR, NAME, MY phrase lane; ASL alphabet; WLASL; YouTube/manual non-FSL training.
4. Inventory SAFE C: for:
   - official FSL-105 raw clips/archive;
   - audited Mapua raw MP4 archive;
   - existing Mapua14 extracted feature cache.
5. Never infer a file exists. Record exact paths, counts, hashes, and provenance.

## 2. Raw data acquisition decision

### Mapua
Use the already audited raw-video archive in SAFE C: if hashes/counts match prior evidence. Do not re-download unless it is missing/corrupt.

### FSL-105
The previous safe-C audit says raw MOV source was missing.

Search SAFE C: again. If absent and internet/download is available, obtain only the official Mendeley FSL-105 Version 2 dataset (DOI 10.17632/48y2y99mb9.2) into a new safe-C dataset directory. Preserve archive/raw files; do not modify originals.

If automated download cannot be completed, stop only this lane and return the exact official artifact/download action needed from the owner. Continue Mapua preparation while waiting.

Verify:
- 2,130 unique clip references/videos expected by published dataset;
- labels.csv/train.csv/test.csv provenance;
- no archive path traversal;
- raw-media decode integrity;
- content hashes.

## 3. Selected FSL-only vocabulary

Exactly:
HELLO
HOW_ARE_YOU
IM_FINE
NICE_TO_MEET_YOU
THANK_YOU
YES
NO
PLEASE
HOW_MUCH
CASH
CARD
RECEIPT
WAIT
MILK
RICE

Do not add WHAT/YOUR/NAME/MY.

Source composition:
- BOTH: HELLO, THANK_YOU, YES, NO
- FSL105 only: HOW_ARE_YOU, IM_FINE, NICE_TO_MEET_YOU, MILK, RICE
- MAPUA only: PLEASE, HOW_MUCH, CASH, CARD, RECEIPT, WAIT

For BOTH classes, preserve source_dataset metadata. Do not discard source identity.

## 4. MediaPipe / landmark audit BEFORE training

The question is NOT whether MediaPipe “knows” the word. Determine whether it captures the motion reliably.

Use one canonical extractor for BOTH raw datasets:
- MediaPipe version locked;
- unmirrored source;
- FullSign225 pose99|left63|right63;
- same normalization;
- timestamp each valid frame;
- complete-motion envelope;
- small bounded neutral boundary;
- short internal gap interpolation only;
- save source trajectory plus resampled 48x225 tensor.

For each selected class/source report:
- clip count;
- decode failures;
- pose-presence rate;
- any-hand rate;
- left/right/both-hand rates;
- internal dropout;
- active-motion duration distribution;
- active landmark-frame distribution;
- fraction passing a conservative usable-trajectory gate.

Generate compact visual evidence for representative clips:
- raw/source frame strip with landmark overlay or equivalent;
- motion-envelope start/middle/end;
- no OCR/pixel label leakage used by model.

If a class/source has severe landmark failure, flag it before training rather than hiding it.

## 5. Dataset construction

Create a new versioned dataset/profile:
`fsl15_dualsource_fullsign225_48f_v1`

Every sample must record:
- canonical label;
- source dataset;
- source clip;
- signer/group if known;
- extraction version;
- motion-envelope frames/timestamps;
- original frame count;
- resampling length=48;
- hand/pose presence metrics;
- hash.

Do NOT random-split windows from the same source clip.

FSL105:
- use published train/test split as reference but create grouping that prevents source-clip leakage;
- if signer IDs are not trustworthy, do not claim signer independence.

Mapua:
- preserve prior raw audit and split/group constraints;
- signer IDs are not recoverable, so do not claim signer independence.

For bridge classes from both datasets, ensure each partition records source composition. Report performance by dataset source as well as overall where possible.

## 6. Train ONE primary classifier

Architecture: reuse the proven RD-TCN48 family first.

Input:
`[1,48,225]`

Output:
15 classes.

Do not run architecture tournaments unless this baseline fails for a diagnosed reason.

Training:
- class-balanced sampling/weights if needed;
- modest temporal warp, coordinate scale/jitter, bounded landmark dropout;
- augmentation on training only;
- validation/test untouched;
- early stopping;
- macro-F1 primary;
- save confusion matrix, per-class P/R/F1, calibration;
- report source-wise metrics for BOTH bridge classes.

Critical: if one source dominates or a class is weak, inspect tensors/data first. Do not just enlarge the model.

## 7. TFLite

Export float32 first.

Require:
- exact `[1,48,225] -> [1,15]`;
- label order frozen;
- TensorFlow/TFLite top-1 agreement on test fixtures;
- max probability difference recorded;
- SHA256 for model/labels/manifest.

Do not overwrite Standard FSL-105 or Mapua14.

New experimental bundle/profile only:
`FSL15_DUALSOURCE_V1`

## 8. Android temporal parity

The new profile must consume the same physical representation as training.

Do NOT use an arbitrary rolling last-48-camera-frame window.

Implement/verify:
`IDLE -> PRIMING -> SIGN_ACTIVE -> END/RELEASE -> complete event -> resample48 -> infer -> WAIT_FOR_RELEASE -> IDLE`

Capture debug evidence for at least one physical sign:
- chronological FullSign225 frames;
- timestamps;
- presence flags;
- event start/end;
- final exact 48x225 TFLite input tensor;
- top-k prediction.

Compare live tensor statistics with training examples.

## 9. Tonight — first Samsung gate

Once the model/profile is installed, do a short diagnostic first, not 75 trials.

Test 3 attempts each:
HELLO
HOW_ARE_YOU
IM_FINE
THANK_YOU
YES
NO
PLEASE
HOW_MUCH
MILK
RICE

Also negatives:
idle/no hands
open palm
random wave
touch face
point
partial sign

For each attempt log:
expected, raw top1, top3, confidence, margin, event duration, tracking quality, accepted/rejected.

Decision:
- >=80% raw top1 overall: proceed to hardening/final qualification.
- 50–79%: targeted domain adaptation only for weak classes.
- <50%: diagnose train/runtime parity or extraction failure before any threshold tuning.

## 10. Overnight / tomorrow morning repair

Only repair measured failures.

Priority:
1. mirror/L-R/normalization mismatch;
2. event segmentation/resampling mismatch;
3. weak source trajectories;
4. targeted Samsung calibration;
5. rejection thresholds only after raw class quality is acceptable.

If Samsung calibration is required:
- capture only weak classes;
- keep capture sessions separate;
- do not leak same burst into qualification;
- label final claim as device/signer-calibrated if that is what it is.

## 11. Tomorrow morning — final physical qualification

When the profile is stable:
- 5 attempts per supported concept;
- report RAW_TOP1_CORRECT, CORRECT_ACCEPTED, WRONG_ACCEPTED, REJECTED separately;
- >=30 varied negative/non-sign attempts.

Preferred demo-ready bar:
- >=4/5 raw top1 per class;
- zero wrong accepted per class;
- overall wrong-accepted <=10%;
- negative false-accept <=5%.

Do not claim 15/15 if evidence says otherwise. A smaller proven subset is better than false completion.

## 12. Freeze before tomorrow afternoon

Produce:
- final model/labels/manifest hashes;
- exact demo-ready vocabulary;
- blocked/candidate vocabulary;
- Android build/test status;
- training/evaluation summary;
- limitations;
- rollback instructions;
- paper-safe architecture wording.

Update GitHub after each major checkpoint.

Do not commit raw media, extracted bulk tensors, APKs, model checkpoints, secrets, or private data. Commit concise manifests/reports/scripts/configs and small golden fixtures only.

## 13. Required status format

`RESET_BRANCH=`
`HEAD=`
`FSL105_RAW_FOUND=YES/NO`
`FSL105_SOURCE_PATH=`
`MAPUA_RAW_FOUND=YES/NO`
`MAPUA_SOURCE_PATH=`
`ASL_PHRASE_LANE_QUARANTINED=YES/NO`
`SELECTED_FSL15=`
`LANDMARK_AUDIT_COMPLETE=YES/NO`
`FSL105_USABLE_CLIPS=`
`MAPUA_USABLE_CLIPS=`
`DUALSOURCE_DATASET_READY=YES/NO`
`MODEL_PROFILE=`
`OFFLINE_MACRO_F1=`
`TFLITE_PARITY=`
`ANDROID_EVENT_PARITY=`
`SAMSUNG_TESTED=YES/NO`
`LIVE_RAW_TOP1_RATE=`
`WRONG_ACCEPT_RATE=`
`NEGATIVE_FALSE_ACCEPT_RATE=`
`DEMO_READY_COUNT=/15`
`DEMO_READY_WORDS=`
`BLOCKED_WORDS=`
`STANDARD_FSL105_PRESERVED=YES/NO`
`MAPUA14_PRESERVED=YES/NO`
`NEXT_EXACT_ACTION=`

The engineering rule is:
**published FSL source -> our canonical MediaPipe landmarks -> complete-event normalization -> one classifier -> TFLite parity -> Samsung evidence.**
