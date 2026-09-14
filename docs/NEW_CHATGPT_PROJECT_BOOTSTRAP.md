# VoxGest — New ChatGPT / Sol Bootstrap Context

Date: 2026-09-14

Purpose: give a fresh ChatGPT/Codex account enough verified project context to work without relying on another account's memory.

## 1. Product

VoxGest is a BSIT capstone Android accessibility prototype for bidirectional communication between Filipino Sign Language (FSL) users and hearing/non-signing users.

Sign-to-hearing:
`Camera -> MediaPipe -> FullSign225 -> temporal classifier -> acceptance/rejection -> canonical FSL concept -> English/Filipino/Both text -> optional TTS`

Hearing-to-FSL:
`Speech -> Android STT -> conservative supported-concept resolver -> verified FSL Avatar action`

Do not claim unrestricted FSL translation, full natural-language FSL grammar generation, or complete FSL vocabulary.

## 2. Shared repository

GitHub:
`https://github.com/justcedm/LatestVoxGest.git`

Safe recovered local repo historically:
`C:\VOXGEST_RECOVERY_20260910\New_VovGest_GIT`

Old D: workspace is prohibited. Do not access/write it.

Important branches:

- recognition recovery base: `recognition/recovery-20260912`
- recognition Mapúa rescue: `recognition/mapua14-rescue-v1`
- Avatar/Astra: `avatar/astra-calibration-20260914`

Canonical lane handoffs:

- recognition: `reports/CODEX_LIVE_HANDOFF.md`
- Avatar: `reports/ASTRA_LIVE_HANDOFF.md`

Project documentation:

- `docs/PROJECT_HISTORY_JUNE_SEPT_2026.md`
- `docs/DOCUMENTATION_POLICY.md`
- `docs/ARCHITECTURE_DECISIONS.md`
- `docs/AVATAR_ARCHITECTURE_DECISIONS.md`

## 3. Recognition geometry contract

FullSign225 per frame:

- pose `[0:99]`
- anatomical LEFT hand `[99:162]`
- anatomical RIGHT hand `[162:225]`

Rules:

- ML input remains canonical/unmirrored
- preview mirroring is presentation-only
- do not swap anatomical left/right because the selfie preview is mirrored
- missing hand may use the documented zero block
- do not fabricate landmark data

## 4. FSL-105 baseline

The 105-class FullSign225 baseline is scientifically useful but not equivalent to live-device readiness.

Known final offline baseline metrics:

- accuracy about `0.93644`
- macro precision about `0.94104`
- macro recall about `0.94090`
- macro-F1 about `0.93853`
- TF/TFLite top-1 agreement `1.0` over the documented parity sample

The historical deployed profile used `[1,20,225]` and `[1,105]`.

The 105-way closed softmax has no valid `NOTHING` output; unsupported/non-sign input therefore requires software rejection. Do not treat high softmax confidence alone as proof of a valid sign.

## 5. Mapúa Transactional FSL rescue experiment

Raw dataset audit:

- 1,107 MP4 videos
- 26 classes
- 670 PASS
- 408 REVIEW
- 29 REJECT_TECHNICAL
- all 640x480, 25 FPS, 3 seconds

A 14-class exact-overlap PASS-only experiment was trained with complete-trajectory temporal normalization.

Winner:

- `RD-TCN48`
- input `[1,48,225]`
- 125,326 parameters
- exploratory sealed clip-level accuracy about `98.21%`
- exploratory macro-F1 about `97.96%`
- only one sealed confusion: YES -> TEN

Signer IDs were unavailable, so do **not** claim signer-independent accuracy.

## 6. Critical current recognition issue

Samsung Series 1 on 2026-09-13 eventually recognized HELLO and presented `Kumusta`, but it required retries and more than one minute. That is not usable live performance.

A major mismatch was identified:

- training: complete detected sign trajectory -> resample complete trajectory to 48 samples
- Android live: rolling latest 48 landmark frames

A live-segmentation implementation was started to capture the actual sign event and resample it to 48 frames, but Codex usage limits interrupted completion before final A/B device testing and publication.

Do not retrain simply to hide this runtime mismatch. Separate raw classifier behavior from acceptance-gate behavior.

## 7. Android / UI

Android declaration:

- minSdk 26 = Android 8.0 installation floor
- target/compile SDK 34 in the current documented project configuration

UI direction:

- clean white/blue/teal accessibility design
- English/Filipino UI support
- message output English / Filipino / Both
- current recognition token remains canonical; translation is deterministic presentation mapping
- unknown translation must not be fabricated
- front preview may be mirrored; ML analysis remains unmirrored

Current source historically supports front/back camera; external-camera support is an engineering direction and must not be claimed universal until tested.

## 8. Avatar lane

Avatar ownership belongs to Astra/app developer, separate from recognition.

Historical 3D pipeline:

`FSL reference/raw225 trajectory -> Blender retargeting -> purchased rigged Avatar -> editable Action -> QA -> runtime GLB -> Android Filament`

Historical frozen solver:
`retarget_general_B32_release_candidate_v1`

Historical CORE3 actions:

- `FSL_HELLO`
- `FSL_MILK`
- `FSL_RICE`

Historical runtime:
`voxgest_avatar_B32_CORE3_RC2.glb`, reported 28,123,308 bytes.

These require current revalidation before PASS claims.

Android Avatar integration history exposed:

- SurfaceView/Compose Dialog z-order causing an invisible render
- move toward TextureView
- teardown/double-destroy native SIGSEGV risk
- Avatar loading should remain lazy/demand-driven
- cleanup must be idempotent

## 9. Avatar product priority

Avatar work now prioritizes high-value everyday concepts over raw count.

Read:

- `avatar_handoff/USER_FACING_VOCABULARY_PRIORITY.md`
- `avatar_handoff/AVATAR_PRESENTATION_SPEC.md`
- `avatar_handoff/ASTRA_FIRST_RUN_PROMPT.md`

Every supported action must preserve a canonical FSL action/token while exposing separate English and Filipino presentation/speech aliases.

The Avatar presentation target is bright, soft, comfortable, frontal and head-to-waist/upper-hip, with hands/fingers as the top visual priority.

## 10. Language presentation rule

Examples:

- HELLO -> English `Hello` -> Filipino `Kumusta`
- THANK YOU -> `Thank you` -> `Salamat`
- YES -> `Yes` -> `Oo`
- NO -> `No` -> `Hindi`
- UNDERSTAND -> `Understand` -> `Naiintindihan`
- DON'T KNOW -> `Don't know` -> `Hindi ko alam`
- MILK -> `Milk` -> `Gatas`
- RICE -> `Rice` -> `Kanin/Bigas`

This mapping is presentation/resolution, not a claim that Filipino grammar equals FSL grammar.

## 11. Protected rules

- no force push
- no history rewrite
- no old D: access
- no raw datasets/APKs/caches/checkpoints/secrets in Git
- no silent renaming of canonical labels
- no Avatar changes by recognition engineer unless explicitly coordinated
- no recognition-model/gate changes by Astra unless explicitly coordinated
- no device PASS without actual physical evidence
- no `FSL correct` / linguistic certification without qualified FSL review

## 12. Documentation is part of completion

Every meaningful milestone must update GitHub documentation.

Use:

- lane live handoff
- detailed milestone report
- architecture decision log where applicable
- 1–2 line project-history entry

If a change affects the thesis, mark `PAPER_IMPACT=YES` and name the sections that must be reviewed.

## 13. Startup instruction for a fresh Sol

Read this document first, then the current branch handoff(s), project history, documentation policy, and architecture decisions. Inspect GitHub/current source before asserting current status. Historical documents are evidence of evolution, not automatically current truth.

When asked to work, preserve lane ownership and push evidence-based checkpoints so another account can review the work without relying on chat transcripts.

For the app developer's exact installation and first-message workflow, begin at `avatar_handoff/APPDEV_START_HERE.md`. Load `avatar_handoff/APPDEV_CHATGPT_SOL_BOOTSTRAP.md` into the normal ChatGPT project and use `avatar_handoff/APPDEV_ASTRA_BOOTSTRAP_PROMPT.md` in Codex/Astra. After any reset, use `avatar_handoff/ASTRA_RESUME_PROMPT.md`.

Current state must always be re-read from the relevant remote branch. In particular, do not treat this bootstrap's historical recognition summary or the Avatar branch's copied recognition handoff as newer than the live handoff on the active recognition branch.
