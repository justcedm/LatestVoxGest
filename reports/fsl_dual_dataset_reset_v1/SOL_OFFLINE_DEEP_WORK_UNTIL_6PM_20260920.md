# SOL — DEEP OFFLINE FSL RECOGNITION ENGINEERING UNTIL 18:00

**Issued:** 2026-09-20 13:20 +08:00  
**Hard review point:** 2026-09-20 18:00 +08:00  
**Branch:** `recognition/fsl-dual-dataset-reset-v1`  
**Starting authority:** `129448c99aa89a1a6649b8e29ccccead143e6b08` or later legitimate commit on this branch.  
**Mode:** autonomous deep engineering. Samsung is intentionally NOT available until ~18:00. Do not wait for it.

## 0. Mission

By 18:00, leave VoxGest in the strongest possible OFFLINE + ANDROID-INTEGRATED state so that the first Samsung test can begin immediately when the owner returns.

Do not optimize for breadth. Optimize for a narrow, practical, defensible FSL recognizer that:
1. uses only published/validated FSL source data;
2. uses one reproducible MediaPipe/FullSign225 feature contract;
3. learns a practical user-facing vocabulary;
4. has leakage-safe offline evidence;
5. exports TFLite with parity proof;
6. is already wired into a non-default Android experimental profile;
7. is ready for physical testing without another architecture rewrite.

The owner wants the highest practical recognition quality, not a cosmetic “15/15” claim.

## 1. Source truth — no ASL contamination

Read first:
- `reports/fsl_dual_dataset_reset_v1/RESET_SOURCE_OF_TRUTH.md`
- `reports/fsl_dual_dataset_reset_v1/FSL15_SOURCE_MANIFEST.csv`
- `reports/mapua14_rescue_v1/SUMMARY.md`
- `reports/dataset_engineering/MAPUA_FSL105_LABEL_MAPPING.csv`
- `reports/dataset_engineering/FSL105_RAW_AUDIT_LIMITATION.md`
- `docs/FSL105_DATASET_READINESS.md`
- `docs/ARCHITECTURE_DECISIONS.md`
- latest Android recognition controller/runtime/tests.

Authoritative linguistic sources:
A. FSL-105, De La Salle University, official Mendeley v2, 105 FSL classes / 2,130 clips.
B. Mapua Transactional Filipino Sign Language Dataset, official Mendeley/raw-video source already audited by VoxGest.

QUARANTINE from final FSL training and claims:
- WHAT/YOUR/NAME/MY phrase model;
- ASL alphabet;
- WLASL;
- researcher/team signs not independently FSL-validated;
- random internet/YouTube sign examples.

Do not delete quarantined assets. They are historical rollback only.

## 2. Paper contract

The latest manuscript's defensible core is:
- Android-based bidirectional accessibility prototype;
- sign-to-hearing uses camera -> MediaPipe landmarks -> temporal classifier -> acceptance/rejection -> text/TTS;
- local/on-device TensorFlow Lite inference;
- isolated known-sign recognition under a controlled vocabulary;
- no unrestricted FSL translation or full grammar claim;
- FullSign225 = pose99 + anatomical-left63 + anatomical-right63;
- reliability requires leakage-safe model evaluation plus deployment parity and rejection;
- Avatar is separate from the classifier.

Do NOT silently make engineering claims that contradict this.

However, the latest manuscript still describes Standard FSL-105 as a 105-class / 20-frame deployment target. If the best final implementation is a 48-frame selected-vocabulary profile using both FSL-105 and Mapua, DO NOT force code to match outdated prose. Instead create:
`reports/fsl_dual_dataset_reset_v1/PAPER_ALIGNMENT_DELTA.md`
explaining exactly what Chapter 1/3 must change:
- selected deployment/evaluation vocabulary;
- Mapua as supplementary published FSL source;
- complete-event 48-frame temporal profile;
- preserved 105-class Standard profile as historical/rollback or broader research lane;
- no false claim that all 105 classes are physically demo-ready.

Engineering truth wins; paper is updated to truth afterward.

## 3. Clarify MediaPipe

MediaPipe is the LANDMARK EXTRACTOR, not the FSL classifier.

Required chain:
published FSL video
-> MediaPipe pose/hands
-> canonical FullSign225
-> complete physical sign event
-> temporal normalization/resampling
-> temporal classifier
-> TFLite
-> Android acceptance/rejection.

Do not write “train MediaPipe.” Train the classifier on MediaPipe-derived trajectories.

## 4. Dataset strategy — use BOTH sources, but do not waste the afternoon on all 131 labels

Create an inventory across BOTH datasets, but prioritize a practical user-facing candidate pool first.

### Tier A practical anchors — attempt to keep
HELLO
THANK_YOU
YES
NO
PLEASE
HOW_MUCH
CASH
CARD
RECEIPT
WAIT

### Tier B user-facing swap pool — choose the strongest five after evidence
HOW_ARE_YOU
IM_FINE
NICE_TO_MEET_YOU
YOURE_WELCOME
UNDERSTAND
DONT_UNDERSTAND
KNOW
DONT_KNOW
GOOD_MORNING
SEE_YOU_TOMORROW
MILK
RICE
AGAIN
PROBLEM
HOW_MANY

Tier B selection is evidence-driven. Score candidates using:
- published FSL provenance;
- usable raw clip count;
- MediaPipe pose/hand presence;
- internal dropout;
- complete-motion trajectory quality;
- class separability in development folds;
- confusion risk against Tier A;
- practical communication value.

Goal: exactly 15 final candidates if evidence permits. If a Tier A anchor is technically poor, it MAY be replaced by a stronger practical candidate; record the reason.

Do not sacrifice recognition quality merely to preserve an arbitrary word.

## 5. Raw source acquisition — keep moving

### Mapua
Find the already-audited raw MP4 archive in SAFE C:. Verify expected hash/count/provenance against existing reports. Reuse it if valid.

### FSL-105
Search SAFE C: for the official raw v2 archive/clips. Previous audit said raw source missing.

If absent:
- use official FSL-105 Mendeley v2 source only;
- download to a NEW safe-C dataset root if Codex environment permits;
- preserve original archive/files;
- hash and inventory;
- do not write D:.

If automated download is blocked by authentication/UI:
1. record the exact blocker and official URL/artifact expected;
2. continue Mapua extraction/training work immediately;
3. prepare all scripts/configs so FSL-105 can be dropped in later without redesign.

Do not sit idle waiting for the owner.

## 6. Canonical feature contract — ONE new lane

New profile family:
`fsl_practical15_fullsign225_48f_v1`

Frame:
- float32 225
- pose [0:99]
- anatomical LEFT [99:162]
- anatomical RIGHT [162:225]
- unmirrored ML input
- no L/R slot swap
- pose nose-relative
- each hand wrist -> middle-MCP scale
- z x0.3
- absent hand block = 63 zeros
- absent pose block = 99 zeros

Temporal:
- DO NOT use arbitrary rolling 48 camera frames as training truth;
- identify the complete tracked motion interval;
- allow only bounded neutral context;
- interpolate only short internal tracking gaps;
- preserve chronological timestamps;
- resample the complete event to exactly 48 normalized temporal positions.

Save both:
A. source event trajectory metadata;
B. final [48,225] tensor.

Version extractor/config/hash.

## 7. MediaPipe viability audit — before model training

For every Tier A + Tier B candidate available from raw video, produce class/source metrics:
- total clips;
- decoded clips;
- technical rejects;
- pose presence;
- any-hand presence;
- left/right/both-hand presence;
- internal dropout;
- active-motion duration p05/median/p95;
- active-motion landmark frames p05/median/p95;
- complete-event usable rate;
- tensor finite/shape checks.

Generate representative evidence for weak/strong classes:
- start/middle/end frames with landmarks or equivalent compact overlay;
- motion envelope boundaries;
- no pixel/text leakage into classifier.

Create:
`reports/fsl_dual_dataset_reset_v1/LANDMARK_AUDIT.md`
`reports/fsl_dual_dataset_reset_v1/LANDMARK_CLASS_METRICS.csv`

If FSL-105 is unavailable, produce Mapua results now and mark FSL105_PENDING_RAW rather than fabricating parity.

## 8. Source handling / overlap

For exact semantic overlaps (HELLO, THANK_YOU, YES, NO and any others):
- retain `source_dataset` metadata;
- do not allow the same physical source clip to cross splits;
- evaluate source-wise performance;
- inspect whether one source creates systematic confusion.

Because both are published FSL datasets, source variants may be legitimate. Do not reject a variant merely because trajectories differ. But do not hide source-domain failure.

For classes unique to one dataset, landmark-only features reduce background leakage, but source identity must still be reported as a limitation.

## 9. Leakage-safe split

Never random-split windows derived from one source clip.

Preferred grouping:
- source clip as absolute minimum group;
- signer/session group if reliably available;
- preserve official FSL-105 test partition where useful as an external reference;
- Mapua signer independence cannot be claimed because signer IDs are unavailable.

Freeze manifests BEFORE final training/evaluation.

Create:
`reports/fsl_dual_dataset_reset_v1/SPLIT_MANIFEST.csv`
`reports/fsl_dual_dataset_reset_v1/DATASET_SUMMARY.md`

## 10. Vocabulary selection gate

Before final model, rank Tier A + Tier B with a transparent table:
- practical relevance;
- usable sample count;
- landmark quality;
- pilot CV F1/recall if available;
- major confusions;
- source limitations.

Create:
`reports/fsl_dual_dataset_reset_v1/VOCABULARY_SELECTION.md`

Then freeze the strongest practical 15.

The final list MUST contain at minimum:
- greeting/social opener;
- confirmation/rejection;
- politeness/closing;
- several transactional concepts.

If 15 cannot be justified, freeze the largest defensible subset and clearly report why. Do not fake completion.

## 11. Model — optimize for reliability, not novelty

Primary architecture:
RD-TCN48, based on the already successful Mapua14 evidence.

Do not run a giant architecture search.

Do:
- 3 deterministic seeds OR existing development folds;
- early stopping;
- class weighting/balanced sampling only if needed;
- modest training-only temporal warp;
- modest coordinate scale/jitter;
- bounded hand-dropout augmentation;
- no validation/test augmentation.

Primary selection:
macro-F1.

Also report:
- accuracy;
- macro precision;
- macro recall;
- weakest-class F1;
- per-class P/R/F1;
- confusion matrix;
- ECE/calibration if cheap;
- source-wise metrics for bridge classes.

If the 15-class model suffers from one/two weak classes:
- inspect data/confusion;
- substitute from Tier B only if practical;
- retrain final frozen set.

Do NOT increase model size first.

## 12. Useful contingency if FSL-105 raw is blocked

Do not stop the project.

Build a Mapua-only practical classifier from the strongest communication/transaction classes first using the proven Mapua pipeline.

Mapua available user-facing pool includes:
HELLO, THANK_YOU, YES, NO, PLEASE, HOW_MUCH, HOW_MANY, CASH, CARD, RECEIPT, WAIT, AGAIN, PROBLEM, WELCOME, DISCOUNT, COIN and numbers.

This can produce a strong narrow interim model while FSL-105 raw acquisition is resolved.

When FSL-105 becomes available, create a NEW dual-source model; do not destructively overwrite the Mapua interim model.

## 13. TFLite export/parity

For each serious candidate:
- float32 export first;
- shape [1,48,225] -> [1,N];
- freeze label order;
- run TF vs TFLite on held-out/golden tensors;
- top1 agreement expected 100%;
- record max probability difference;
- SHA256 model/labels/config.

New Android bundle only. Never overwrite:
- Standard FSL-105;
- Mapua14 rescue;
- demo10;
- generic *_v1 assets.

## 14. Android integration while Samsung is absent

Do NOT wait for the phone.

Prepare a non-default experimental recognition profile:
`FSL_PRACTICAL15_V1` (or N if subset differs)

Implement:
- model/labels/runtime manifest loader;
- deterministic FullSign225 frame builder reuse;
- complete-event temporal collector/resampler;
- event state:
  IDLE -> PRIMING -> SIGN_ACTIVE -> CANDIDATE -> WAIT_FOR_RELEASE -> IDLE
- one physical event -> one token;
- duplicate token requires release/re-arm;
- rejection/no-output is software behavior, not a displayed “NOTHING” word;
- top-k + margin + tracking/event diagnostics;
- user-facing bilingual presentation map without renaming canonical model IDs.

Do not redesign UI. Minimal profile/debug integration only.

## 15. Android/Python temporal parity WITHOUT Samsung

Create JVM/Python golden fixtures proving:
- same 225-slot order;
- same missing-hand policy;
- same normalization constants;
- same resampling positions;
- same complete-event boundary semantics on synthetic/recorded landmark fixtures.

Add unit tests for:
- 48-frame exact event;
- event shorter than 48 -> interpolation;
- event longer than 48 -> resampling;
- short internal hand dropout;
- no pose;
- only left hand;
- only right hand;
- both hands;
- duplicate/release behavior;
- timeout;
- incomplete event rejected.

Run:
- relevant unit tests;
- `assembleDebug`.

Do not claim device parity yet.

## 16. Recorded-video end-to-end replay

Before Samsung, replay held-out raw dataset videos through:
raw video -> MediaPipe -> event extraction -> FullSign225 -> resample48 -> TFLite.

This is more valuable than only testing stored NPY tensors because it exercises the full perception path.

Report:
- per-class raw-video replay top1;
- event-detection failures;
- classifier failures;
- latency on development machine;
- top confusions.

This becomes tonight's best predictor of whether Samsung testing is worth starting.

## 17. Rejection hardening offline

Closed-set softmax will always choose a label.

Build/test rejection signals using held-out non-sign / boundary / corrupted-event examples where available:
- insufficient event;
- tracking quality;
- top1 confidence;
- top1-top2 margin;
- temporal stability;
- event duration limits;
- optional embedding/prototype distance only if simple.

Do not tune thresholds on the sealed final test.

No universal NOTHING class unless a specific model is explicitly trained with a properly sourced negative class and documented separately.

## 18. Paper alignment report by 18:00

Create:
`reports/fsl_dual_dataset_reset_v1/PAPER_ALIGNMENT_DELTA.md`

It must state:
- what implementation is unchanged from the paper;
- what changed;
- exact feature contract;
- exact temporal length;
- exact vocabulary;
- exact dataset sources;
- what the paper must stop claiming;
- what can be defended;
- limitations.

Do not modify the manuscript itself in this branch.

## 19. Git discipline

Checkpoint/push at least:
1. provenance + inventory;
2. landmark audit/extraction;
3. vocabulary selection/split;
4. model + offline metrics;
5. TFLite parity;
6. Android integration/tests;
7. 18:00 handoff.

Update:
`reports/CODEX_LIVE_HANDOFF.md`
and files under:
`reports/fsl_dual_dataset_reset_v1/`

Do not commit:
raw videos, large extracted tensors, Keras checkpoints, APKs, device captures, credentials, private assets.

Explicit staging only.
No force push/history rewrite.

## 20. What MUST be ready when owner returns at 18:00

Best-case target:
- both raw FSL sources available;
- Tier A/B MediaPipe audit complete;
- final practical 15 frozen;
- RD-TCN48 trained with leakage-safe metrics;
- float32 TFLite parity PASS;
- Android experimental profile integrated;
- JVM/unit/build PASS;
- recorded-video replay results;
- exact Samsung test protocol ready.

Minimum acceptable if FSL-105 download blocks:
- blocker precisely documented;
- Mapua practical model trained/exported/integrated;
- all dual-source scripts/configs ready;
- Android event/resampling architecture complete;
- no ASL contamination;
- no false success claim.

## 21. 18:00 return format

`OFFLINE_DEEP_WORK_STATUS=`
`BRANCH=`
`HEAD=`
`FSL105_RAW=`
`MAPUA_RAW=`
`ASL_CONTAMINATION=QUARANTINED/FAIL`
`CANDIDATE_POOL_AUDITED=`
`FINAL_VOCABULARY=`
`FINAL_VOCABULARY_COUNT=`
`DATASET_PROFILE=`
`FEATURE_CONTRACT=`
`TEMPORAL_CONTRACT=`
`OFFLINE_ACCURACY=`
`OFFLINE_MACRO_F1=`
`WEAKEST_CLASS_F1=`
`TOP_CONFUSIONS=`
`RAW_VIDEO_REPLAY_ACCURACY=`
`TFLITE_PARITY=`
`TFLITE_MAX_DIFF=`
`ANDROID_PROFILE=`
`ANDROID_UNIT_TESTS=`
`ANDROID_BUILD=`
`SAMSUNG_REQUIRED_NEXT=YES/NO`
`PAPER_ALIGNMENT_DELTA=READY/NOT_READY`
`STANDARD_FSL105_PRESERVED=YES/NO`
`MAPUA14_PRESERVED=YES/NO`
`BLOCKERS=`
`NEXT_EXACT_PHYSICAL_TEST=`

## 22. Governing decision rule

Do not optimize for the number “15” at the expense of truth.

Optimize:
**published FSL provenance -> trackable motion -> consistent FullSign225 event -> high class separability -> TFLite parity -> Android-ready pipeline -> later Samsung proof.**

The final system should recognize fewer words extremely well before it recognizes many words poorly.
