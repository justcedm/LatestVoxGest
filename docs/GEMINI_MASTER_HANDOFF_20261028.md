# VoxGest — Gemini Master Handoff
**Version:** 2026-10-28  
**Purpose:** Transfer the project’s current technical, research, Android, ML, survey, and workflow context to Gemini/Antigravity or another AI coding assistant.

> **Authority and freshness warning:** This handoff combines the latest user-reported local/device state with the repository state visible on GitHub. The remote `main` branch's latest visible commit is dated 2026-10-03. Therefore, October 25–28 work described below may exist only in the user's local working copy and must be inspected before assuming it is committed or deployed. Do not claim a local change is present in GitHub or on the phone without checking.

## 1. Read this first — current situation

VoxGest is a BSIT capstone Android accessibility prototype intended to support communication between Filipino Sign Language (FSL) users and hearing/non-signing people when a shared communication method or interpreter is unavailable. The project has two directions:
1. **Signing user → hearing user:** camera input, body/hand landmark extraction, temporal recognition of selected signs, accepted text output, and optional speech.
2. **Hearing user → signing user:** speech-to-text, supported concept mapping, and a prepared/verified FSL avatar animation where a mapping exists.

This is a **limited, closed-set prototype**, not an unrestricted translator for continuous FSL grammar and not a replacement for qualified interpreters.

### Current priority
The immediate operational blocker is the **front-camera preview still appears mirrored on the physical Samsung device**. Earlier code-level checks reported CameraX `MirrorMode.MIRROR_MODE_OFF`, an unmirrored screenshot, and landmark overlay alignment, but the user subsequently stated the actual preview is *still mirrored*. Physical user observation takes precedence over a claimed pass in a log or screenshot. Fix and verify this issue narrowly; do not bundle unrelated refactors.

The project is also preparing for IT and FSL expert survey sessions. A recording session for three attempts per selected class was reported complete, but the exact export path, content, and eligibility audit must be checked locally. Do not assume every recorded attempt is clean or training-ready.

## 2. Repository, branches, workspaces, and device

### GitHub
- Repository: [justcedm/LatestVoxGest](https://github.com/justcedm/LatestVoxGest)
- Default branch: `main`
- Last visible remote commit at handoff preparation: `98682a85c792efed00df579d00ef69bc59f1fec3` — “docs: add final survey demo integration plan”, dated 2026-10-03.
- The remote repo contains historical and experimental ML tracks. Its root `README_CURRENT.md` and `ANDROID_DEVELOPER_HANDOFF.md` originally describe a legacy 10-word, 30-frame, 162-feature pipeline; that is **not** the current 19-class Core20 survey specification.
- Do not assume that code, model assets, local recordings, APKs, or changes reported after October 3 are already in the remote repository.

### Local paths reported by the user/project
- Main Android app: `D:\BSIT 3RD YEAR\New VovGest\android_dry_run`
- Separate Recognition Lab was reported under: `D:\VoxGest\repository\LatestVoxGest\android_dry_run` in a prior workflow. Verify the actual current path before commands; do not assume these are the same worktree.
- Prior active recovery worktree/handoff: `C:/VOXGEST_RECOVERY_20260910/New_VovGest_GIT/reports/CODEX_LIVE_HANDOFF.md` (historical pointer; inspect before relying on it).
- User commonly works on Windows, VS Code/Codex and Gemini in Antigravity. Use PowerShell syntax when giving commands unless the active terminal proves otherwise.

### Physical test device
- Samsung Galaxy A56 5G, model `SM-A566B`, Android 16.
- ADB serial reported as `R5GYC0M1M4P`.
- Main app package: `com.voxgest.dryrun`.
- Separate Recognition Lab package: `com.voxgest.dryrun.recognitionlab`.
- Verify the currently installed package, version, APK hash, and active model profile before testing. Do not infer them from a previous build.

## 3. Non-negotiable work rules

1. **Inspect before editing.** Start with `git status --short --branch`, `git log -5 --oneline`, remotes, current file contents, active model manifest/labels, APK timestamp/hash, and device package state. Report which worktree and branch are being used.
2. **Fix only the requested problem.** For the current mirror issue, limit changes to the preview/display transform and directly necessary tests. Do not change the model, recognition thresholds, feature extraction, labels, UI, avatar, sentence builder, or manuscript as collateral work.
3. **Preserve baselines.** `demo10` is a safe historical baseline. `sprint30 onehand162` is experimental. Do **not** make `sprint30_fullsign225` the default. Do not overwrite protected stable assets or generic `*_v1` artifacts without explicit approval and a backup.
4. **Keep Core5 and Core20 separate.** Core5 is a legacy five-class profile; Core20 is the newer 19-output profile. Do not silently replace the Core5 default or merge their labels/metrics.
5. **Follow the manuscript.** The approved capstone paper is the authority for research scope, dataset, methods, and evaluation. Do not rewrite the paper or change its feature/window contract without explicit approval.
6. **Dataset restriction:** use **FSL-105 Mendeley Version 1 only** for the standard FSL-105 pipeline. Do not add FSL TUTOR/YouTube-derived external sources to this survey/training track unless the user explicitly changes the decision.
7. **No premature retraining.** First audit the recorded clips, labels, framing, landmark quality, class balance, and the active runtime model. Model confidence alone does not prove a clip is good or bad.
8. **Do not mirror landmark/model inputs to make a prediction look correct.** Preview mirroring, analysis-frame mirroring, MediaPipe handedness, anatomical feature slots, and overlay transforms are separate contracts. Any change must be validated end-to-end.
9. **Do not overclaim.** A successful build, unit test, TFLite parity result, one successful sign, or expert questionnaire does not prove the entire system is reliable or linguistically complete.
10. After any change, show the exact diff, explain the cause, run relevant tests/build, install the resulting APK, and physically verify the intended behavior on the Samsung. Keep backups and report what is still unverified.

## 4. Core vocabulary and naming

The internal name **Core20** is inherited from an earlier target, but the current classifier has **19 output classes**. Describe it as “Core20, the current 19-class survey profile.” It does **not** mean FSL contains only 19 signs.

The current selected FSL-105 labels are:

1. HELLO
2. UNDERSTAND
3. NO
4. YES
5. THANK YOU
6. GOOD MORNING
7. GOOD EVENING
8. GOOD AFTERNOON
9. HOW ARE YOU
10. IM FINE
11. NICE TO MEET YOU
12. YOURE WELCOME
13. SEE YOU TOMORROW
14. DON'T UNDERSTAND
15. KNOW
16. DON'T KNOW
17. CORRECT
18. SLOW
19. FAST

Use these labels exactly as model class glosses. English glosses are identifiers, not complete linguistic descriptions of FSL.

### Core5
The historical Core5 set is HELLO, UNDERSTAND, NO, YES, THANK YOU. It is a separate model/profile and must not be treated as equivalent to Core20. Some older UI/logs were driven by Core5 even while another category was selected; a past patch was intended to make the active Core20 result primary and show Core5 only as a legacy side-by-side result. Verify the active profile and UI labels in the actual build.

## 5. Authoritative model/data contract

The finalized manuscript specifies the standard FSL-105 pipeline as:
- Model input: `[1, 20, 225]`
- Output for Core20: `[1, 19]`
- Each frame has 225 values:
  - 99 pose values (33 landmarks × x/y/z)
  - 63 anatomical-left-hand values (21 landmarks × x/y/z)
  - 63 anatomical-right-hand values (21 landmarks × x/y/z)
- Sequence: 20 frames, sliding extraction stride 5.
- Pose normalization: coordinates are relative to the nose landmark.
- Each hand is independently scaled using wrist landmark 0 to middle-finger MCP landmark 9 when valid.
- z-coordinate is damped by multiplying by 0.3.
- Missing hand blocks may be zero-filled; missing pose frames are zero-filled under the documented extractor contract.
- A standard window is valid only if pose is present in at least 13 of 20 frames and at least one hand is present in at least 13 of 20 frames.
- Reject invalid shapes/non-finite values; preserve the exact same feature contract in training and Android inference.

A separate Mapúa Transactional FSL experiment uses `[1, 48, 225]`. **Do not mix it into the standard FSL-105 20-frame condition** or describe it as the same experiment.

### Model architecture and training
The Core20 temporal model is a TCN-family model; a Residual-Dilated TCN (RD-TCN) was compared during model selection. Do not describe the current Core20 dynamic classifier as the old LSTM 30×162 model. The repository has multiple legacy models, which is why the active runtime manifest and label file must be inspected.

Reported Core20 standard subset:
- 393 selected clips
- 257 train / 57 validation / 79 held-out test clips
- 6,807 extracted windows reported
- TCN baseline reported at 190,453 parameters, best epoch 87
- Validation macro-F1 is the primary selection metric; validation accuracy is secondary
- Held-out test is for final reporting, not for model selection

Train fits weights; validation selects/configures; held-out test estimates generalization after selection. Splits must remain independent, and duplicates/source leakage must be checked.

### Reported Core20 V2 metrics — treat as engineering-report figures, not universal guarantees
The latest reported engineering summary states:
- FSL-105 held-out test: 76/79 top-1 correct (96.2%); macro-F1 96.28%
- Locked Samsung test: 23/25 top-1 correct (92%); 22/25 accepted correctly (88%)
- Close-range front-camera HELLO subset: 8/8 accepted correctly
- Similar-greetings subset: 6/6 accepted correctly
- Keras/TFLite parity: 991 windows, 100% top-1 agreement, maximum absolute difference (3.58 	imes 10^{-7})

**Important:** these figures were reported in the recent Core20 V2 engineering workflow but may not be represented in the current GitHub `main` artifacts. Verify the exact report, active model hash, and APK before quoting them as results from the live survey build. A small targeted subset does not establish equal performance across all 19 classes or all signers.

An earlier Core20 V1 model was reported as `fsl_core20_v1_19class_tcn_float32.tflite`, SHA-256 `ea1fa31688b3c2c44aae7bbec2e35fb1b6791f547f3b3d1edd143a57a62244d0`. Treat that hash as the **reported V1 artifact only**, not as the hash of Core20 V2 or the main-app installed model.

## 6. Camera, landmarks, and current P0 issue

### Current known problem
The user physically checked the Samsung and says the preview still appears mirrored. Earlier code checks reported:
- `Preview.Builder().setMirrorMode(MirrorMode.MIRROR_MODE_OFF)`
- display `scaleX` logic that avoids a second inversion on modern Android
- `analysis_mirrored = false`
- `PreviewOverlayMapper.centerCropPoint(... analysisMirrored = false, previewMirrored = false)`
- screenshot claims of unmirrored preview and aligned landmarks

Those claims conflict with the user's latest physical observation. Do not mark PASS until reproduced and confirmed by the user/device.

### Likely places to inspect (not an instruction to blindly edit all)
- `android_dry_run/app/src/main/java/com/voxgest/dryrun/survey/SurveySignController.kt`
- `android_dry_run/app/src/main/java/com/voxgest/dryrun/ui/SurveySignScreen.kt`
- `android_dry_run/app/src/main/java/com/voxgest/dryrun/PreviewOverlayMapper.kt`
- CameraX `PreviewView` scale type, surface transform, `Preview.Builder` mirror mode, front-camera configuration, Compose/AndroidView `scaleX`, and any parent graphics transform.
- Confirm the currently used screen is the survey SIGN screen, not the Recognition Lab tracking screen or an old APK.

### Correct debugging protocol
1. Capture the exact installed package/build/version/hash and reproduce on the actual survey SIGN screen.
2. Put a clearly asymmetric marker or text in the scene (e.g. paper with “LEFT” written on one side) and move the physical left hand independently. Do not rely on a face selfie alone.
3. Identify whether the preview pixels are mirrored, the analyzed frame is mirrored, or only the overlay is flipped. Treat them as three separate things.
4. Inspect the view hierarchy and all transform layers. CameraX mirror policy and view-level `scaleX` must not both invert or cancel each other unexpectedly.
5. Change only the transform layer that is demonstrably wrong. Do not horizontally flip MediaPipe/model features as a shortcut.
6. Rebuild, install with `adb install -r` without clearing data unless necessary, verify APK hash, relaunch the exact screen, and test left/right orientation physically.
7. Confirm that overlay landmarks still align, physical left/right slot assignment remains correct, and recognition behavior has not changed.
8. Save before/after screenshots and a short verification note. Ask the user to confirm the preview orientation before reporting the issue resolved.

### Historical handedness findings
A past audit of 14 single-handed FSL-105 classes reportedly found 0/183 left-only training examples; most training clips were right-only. The anatomical left/right slots mapped physical right to right and physical left to left in the audited pipeline. Left-hand HELLO was misclassified in Core5 as THANK YOU and in Core20 as SLOW in past tests; horizontal flipping could force a different result but is linguistically unsafe, especially for asymmetric bimanual signs. This is evidence of data/domain bias and must not be “fixed” by blindly flipping every sign. Re-audit with current artifacts before treating old findings as current.

### Framing guidance
For close-range/selfie use, keep face, shoulders, upper torso, both arms, and the signing space visible. A face-only upward camera angle (face/ceiling filling most of the frame) is not a good sign-recognition frame. The camera guide should help the signer position the phone and body; a settings preview/landmark mode can help the evaluator understand tracking. Do not claim a guide solves recognition accuracy by itself.

## 7. Android application structure and user-facing features

### Main app
Package reported: `com.voxgest.dryrun`. The app shell has SIGN, CONVERSATION, BOARD, LISTEN, and GUIDE tabs in the current local build. The Guide was reported to contain all 105 FSL-105 V1 reference videos available offline with a “WATCH REFERENCE” action. These are reference videos, **not proof that all 105 classes are recognized live**; Guide status was described as NOT YET LIVE VALIDATED.

Recent user-reported additions by Argon:
- a camera framing/position guide for signers
- a Settings option to preview how the camera/tracking sees the signer and movement
- a smart sentence builder for the sign-recognition side and a separate smart sentence builder for the avatar/speech side
- survey flow and 19-class recognition profile integration work

These features should be inspected in the current local build; do not assume every item was committed to GitHub or verified on the physical device.

### Recognition Lab
Separate package reported: `com.voxgest.dryrun.recognitionlab`. It was used for controlled Core20 recording and comparison, including:
- `CORE20_19` category
- reference clip playback
- start/end/save attempt
- accept/reject/retake attempt
- export with SHA-256
- predicted label/confidence and operator truth kept distinct
- physical hand and MediaPipe slot metadata

The Recognition Lab is not the same app/package as the main survey app. A correct replay in Recognition Lab does not prove the same model is active in the main app.

### Sign-to-text/speech
Camera frames feed MediaPipe pose/hand landmarks, the extractor forms a normalized temporal window, the classifier scores the supported classes, and a quality/acceptance gate decides whether to output a token. Accepted results may be displayed and optionally spoken with Android TTS depending on the current build/settings. Low confidence is not itself proof that a recording is unusable; quality and ground truth must be assessed separately.

### Speech-to-avatar
Speech recognition produces text. The app maps supported text/concepts to prepared avatar animations where an approved mapping exists. Do not claim the avatar generates a new linguistically correct FSL sign for any arbitrary sentence. The avatar pipeline and the sign-recognition classifier are distinct.

### Sentence builders
Sentence builders compose supported accepted tokens into readable text. This is a usability feature, not evidence of continuous FSL grammar understanding or unrestricted sign-language translation. Confirm the two builders are separate and that unsupported/low-confidence inputs are not silently committed as definite content.

## 8. Dataset and recording protocol

### Source restriction
Use only **FSL-105 Mendeley Version 1** for the standard FSL-105 experiment and current selected 19-class subset. Do not silently supplement with FSL TUTOR, YouTube, or another source. The manuscript reports 2,130 source videos across 105 classes. Verify exact counts and manifests from local files before a new training run.

### How the pipeline works
1. Audit source files, class labels, duplicate files/source clips, and train/validation/test leakage.
2. Extract pose and hand landmarks from each video.
3. Apply the documented normalization and left/right anatomical slot contract.
4. Build fixed temporal windows; for the standard pipeline, 20 frames with stride 5.
5. Retain only windows meeting the landmark-validity rule.
6. Train TCN/RD-TCN candidates on training data.
7. Select using validation macro-F1 (accuracy secondary).
8. Evaluate once on the locked held-out test split.
9. Export TFLite and compare original model vs TFLite on identical inputs.
10. Run locked physical-device tests under clearly documented framing, hand, lighting, and signer conditions.

### User-recorded survey samples
The user reported completing three recordings for each of the 19 selected words. Before training or including any sample:
- inspect the export manifest and ensure all intended clips are present;
- preserve the performed/truth label separately from model prediction;
- distinguish operator acceptance from quality eligibility;
- reject or quarantine clips with no observable trajectory, missing pose/hand, cropped signing space, incomplete/ambiguous movement, or wrong label;
- keep a low-confidence but clean, correctly labeled attempt for diagnostic analysis when appropriate;
- do not allow a wrong model prediction to overwrite ground truth;
- do not add survey/evaluator clips to the locked test set;
- obtain informed consent before storing identifiable video; follow the approved study/privacy plan.

Do not retrain immediately simply because a few live predictions fail. First compare the camera view, landmarks, features, and active model profile. If there is domain shift, build a controlled calibration plan and preserve a separate locked evaluation set.

## 9. Research manuscript and survey context

The finalized capstone manuscript is the authority. A repository PDF is named `Thesis_Capstone_Format_CCS_TRAD_VoxGest_Chapter1_3_DefenseReady.pdf`; check local/approved version with the adviser before submission. Do not change the manuscript as part of camera debugging.

### Research framing
- Research approach: developmental research with quantitative evaluation and iterative development (Agile/RAD-oriented workflow).
- Communication framework: Shannon–Weaver model.
- System organization: Input–Process–Output framework.
- Selected ISO/IEC 25010 quality characteristics: Functional Suitability, Performance Efficiency, Reliability, and Usability.
- The manuscript describes descriptive statistics including frequency, percentage, and weighted mean for questionnaire results.
- The manuscript's planned purposive sample is 30 respondents: 3 FSL experts, 3 IT professionals, 12 Deaf/hard-of-hearing FSL users, and 12 non-signing participants. Report the actual number participating; do not imply the full sample is complete when it is not.
- A four-point Likert scale was described: 4 Strongly Agree, 3 Agree, 2 Disagree, 1 Strongly Disagree. Use the exact adviser-approved questionnaire and do not change scales mid-study.

### How to explain why there are 19 selected signs
The current 19-class Core20 profile is a **bounded, technically testable subset** selected from the FSL-105 V1 reference vocabulary to evaluate the end-to-end mobile pipeline across practical everyday concepts. It allows controlled class-level testing, reference review, confusion analysis, and acceptance/rejection evaluation. It is not a claim that FSL only has 19 signs, and the selection should not be justified as merely a time limitation. Describe it as a scoped prototype and validation milestone aligned to the approved paper and available verified references.

### Suggested 30-second explanation
“VoxGest is an Android accessibility prototype that explores bidirectional communication between FSL users and hearing people. In the signing direction, the phone camera tracks pose and hand landmarks and a temporal model classifies a supported isolated sign; an acceptance gate controls whether it becomes text or speech. In the reverse direction, supported speech/text concepts can trigger verified avatar animations. The survey profile is intentionally limited to 19 selected FSL-105 concepts so that we can evaluate a defined prototype scope. It is not an unrestricted FSL translator, and we separately report model tests and evaluator feedback.”

### IT evaluator should evaluate
- Functional Suitability: expected actions work and unsupported inputs are handled safely.
- Performance Efficiency: observed responsiveness, delay, lag, or freezes on the actual device.
- Reliability: repeatability across attempts, stability, and appropriate rejection.
- Usability: navigation, readability, camera framing/orientation guidance, and feedback.

The questionnaire measures evaluator perception of software quality; it does **not** replace class-level model metrics or linguistic validation by FSL experts. Do not coach evaluators toward positive ratings. Explain the prototype neutrally, obtain consent, let them explore, and record failures honestly.

## 10. Known limitations to disclose accurately

- Only the selected isolated sign labels are in the Core20 output space; unsupported signs cannot be identified correctly by a closed-set classifier.
- Performance may vary by signer, hand use, signing speed, distance, camera angle, lighting, occlusion, and whether the whole signing space is visible.
- Left-handed generalization is not established for all classes; training examples were reported to be right-hand biased for many one-handed classes.
- Similar greetings can be confused; class-level confusion and broader live tests matter more than a single aggregate score.
- Confidence is not correctness. A confident wrong prediction is still an error.
- Rejection thresholds trade off false acceptance and false rejection; thresholds must be verified in the active model profile.
- TFLite parity proves export agreement on tested inputs, not perfect recognition in real-world use.
- Reference videos in GUIDE do not mean the recognizer supports all 105 labels.
- Avatar animation availability is limited to verified mappings.
- Sentence building does not constitute full FSL grammatical translation.
- Camera orientation remains open until the user confirms a physical fix.

## 11. Immediate work plan for Gemini

### P0 — Resolve mirrored preview only
1. Inspect local git status, branch, current diff, current APK hash, active model manifest and actual installed app package.
2. Open the exact files listed in Section 6 and trace all preview transform layers. Determine whether the mirror exists in CameraX preview pixels, Compose/View transforms, or only overlay alignment.
3. Reproduce on the Samsung's main app SIGN screen, using an asymmetric marker and independent left/right hand movement.
4. Implement the smallest evidence-based fix. Do not alter the analysis stream, model features, handedness slots, UI layout, avatar, sentence builders, recognition thresholds, or manuscript unless the root cause proves one of them is directly responsible.
5. Build and run relevant tests. Install the exact resulting APK without wiping user data where possible.
6. Physically re-test on the device. Record build/hash, screen, camera, transform settings, and before/after evidence. Ask the user to confirm; if the user still sees a mirror, continue diagnosis rather than claiming success.
7. Update this handoff only after the fix has been physically verified.

### P1 — Survey readiness after the camera fix
- Verify main app and active Core20 model identity.
- Verify all 19 labels and correct output mapping.
- Verify camera guide and tracking-preview setting.
- Test one clear sign from the 19, one low-confidence/unsupported case, and speech/avatar paths only if available.
- Confirm sentence builders do not commit rejected predictions.
- Verify the 19×3 recording export, audit manifest/quality, and preserve ground truth.
- Record build number, device, conditions, successes, failures, and questionnaire deviations.

### P2 — After the survey
- Analyze per-class confusions, especially similar greetings and right/left-hand variation.
- If warranted, make a separately versioned calibration/training experiment using only eligible training data; do not overwrite the locked test split or Core5 baseline.
- Re-evaluate model metrics, calibration/rejection trade-offs, TFLite parity, and real-device performance before any model becomes default.

## 12. Useful repository entry points

Start by inspecting these files and their freshness:
- `README.md` — repo overview and restore/build guidance.
- `README_CURRENT.md` — historically described the 10-word, 30×162 pipeline; check its supersession notice/current replacement.
- `ANDROID_DEVELOPER_HANDOFF.md` — historically described the 10-word/30×162 Android contract; not authoritative for Core20 unless updated.
- `docs/FINAL_SURVEY_DEMO_INTEGRATION_PLAN_20261003.md`
- `docs/CORE5_FINAL_VALIDATION_AND_PRODUCTION_INTEGRATION_20261003.md`
- `docs/ML_ENGINEER_KNOWLEDGE_BASE.md`
- `docs/AVATAR_PIPELINE.md`
- `docs/CHAPTER_2_3_RESEARCH_NOTES.md`
- `model/runtime_manifest*.json` and `model/class_labels*.json` — inspect the exact active profile, not whichever file looks newest.
- `android_dry_run/app/src/main/java/com/voxgest/dryrun/survey/SurveySignController.kt`
- `android_dry_run/app/src/main/java/com/voxgest/dryrun/ui/SurveySignScreen.kt`
- `android_dry_run/app/src/main/java/com/voxgest/dryrun/PreviewOverlayMapper.kt` (if present in the active worktree)
- `reports/` — historical dataset audits and evaluation summaries; verify dates and profiles before using metrics.

The repo excludes large datasets, extracted arrays, environment folders, and build output by design. Do not commit private recordings, participant videos, consent forms, API keys, local paths with personal data, or large generated artifacts without an explicit approved release process.

## 13. Recommended first terminal commands (PowerShell)

Run from the actual repository/worktree root, not blindly from a remembered path:

```powershell
git status --short --branch
git log -5 --oneline --decorate
git remote -v
git branch --show-current
git diff --stat
```

Then inspect the active app:
```powershell
Get-Item .\android_dry_run\app\build\outputs\apk\debug\app-debug.apk -ErrorAction SilentlyContinue |
  Select-Object FullName, Length, LastWriteTime
Get-FileHash .\android_dry_run\app\build\outputs\apk\debug\app-debug.apk -Algorithm SHA256 -ErrorAction SilentlyContinue
adb devices
adb shell pm list packages | findstr /i voxgest
```

Build/test only after verifying the worktree and project path:
```powershell
cd .\android_dry_run
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleDebug
```

A passing build/test is necessary but not sufficient. Physically verify the camera orientation on the exact survey screen.

## 14. Ready-to-paste instruction for Gemini/Antigravity

You are taking over VoxGest, an Android FSL accessibility capstone. Read this handoff and inspect the current local repository before making any changes. First report the real worktree path, branch, git status, latest commit, remote, active Android package, installed APK hash/timestamp, active recognition model/profile, and whether local work differs from GitHub. The connected GitHub repository is justcedm/LatestVoxGest; remote main's latest visible commit may be older than the local Oct 25–28 work.

The only immediate priority is the user's unresolved report that the main app's front-camera preview still looks mirrored on the Samsung Galaxy A56 5G (SM-A566B, Android 16). Fix only the proven preview/display transform. Trace CameraX, PreviewView, Compose/View scale transforms, analysis mirroring, and overlay mapping separately. Do not fix this by blindly flipping model features or MediaPipe handedness. Preserve the recognition model, thresholds, 19 labels, UI, avatar, sentence builders, manuscript, Core5 baseline, and all protected artifacts. Back up first, show the minimal diff, run tests/build, install the exact APK, and physically verify with an asymmetric marker and left/right hand movement. Do not claim PASS unless the user-visible preview is confirmed unmirrored and landmarks remain aligned.

Afterward, verify survey readiness: Core20 is the inherited name for a 19-output profile with the 19 labels in this document; the standard feature contract is 20 frames × 225 values (99 pose + 63 left hand + 63 right hand), nose-relative pose, independent wrist-to-middle-MCP hand scaling, z×0.3, stride 5, and the manuscript's ≥13/20 validity rule. Use only FSL-105 Mendeley V1 for this track, not FSL TUTOR. Keep Core5 separate and do not make sprint30_fullsign225 the default. Audit the user's reported 3×19 recording export before training; keep ground truth distinct from prediction, keep the held-out test locked, and do not retrain until data quality/domain shift is diagnosed. Use the approved manuscript as authority and do not edit it without explicit approval.

For every task, inspect first, make the smallest scoped change, preserve backups, test, verify on the actual device, document evidence, and state remaining uncertainty. Never treat a successful build, parity test, screenshot, or single prediction as proof of full system correctness.
