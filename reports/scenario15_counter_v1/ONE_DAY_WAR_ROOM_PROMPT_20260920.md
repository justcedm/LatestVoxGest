# VoxGest ONE-DAY WAR ROOM — Sol Execution Prompt

**Issued:** 2026-09-20
**Branch:** `recognition/scenario15-counter-v1`
**Supersedes for execution:** `START_HERE_SOL_20260920.md` and the slower multi-day sequencing in `SOL_MASTER_PROMPT_20260920.md`.
**Use older docs only as evidence/reference.**
**Objective:** before the day ends, produce the strongest physically demonstrated Scenario-15 Android build possible. No scope expansion. No architecture tourism. No cosmetic work.

---

## 0. FROZEN DEMO

One structured scene:

**GREETING -> NAME EXCHANGE -> SMALL RETAIL COUNTER -> CLOSING**

Exact 15 concepts:
HELLO, WHAT, YOUR, NAME, MY, YES, NO, THANK_YOU, PLEASE, MILK, RICE, HOW_MUCH, CASH, CARD, RECEIPT.

Safe phrase composition:
- WHAT + YOUR + NAME -> "What is your name?"
- MY + NAME -> controlled name-entry -> "My name is <explicitly entered name>"
- explicitly supplied session name may later render "Hello, <name>"
- never hallucinate a name
- arbitrary proper names are NOT classifier classes
- do not claim unrestricted fingerspelling

The app may use multiple specialized recognizers internally. **Do NOT waste today forcing all 15 into one model.** The requirement is one coherent user-facing vocabulary with deterministic routing.

---

## 1. REUSE WHAT ALREADY EXISTS

### A. Name recognizer — use first, do not retrain first
Existing artifact:
`fullsign225_phrase_v1`
Input `[1,30,225]`, 10 outputs:
WHAT, YOUR, NAME, MY, YOU, OKAY, STUDENT, WHERE, LIVE, NSAC.

Existing grouped validation:
- overall 94.54%
- WHAT 93.33%
- YOUR 96.30%
- NAME 95.92%
- MY 98.04%
- NSAC 95.51%

Today only expose WHAT/YOUR/NAME/MY from this lane. All other positive phrase labels are treated as no-output for Scenario-15. Preserve NSAC as no-output.

**First action: physically smoke-test WHAT/YOUR/NAME/MY on Samsung before touching this model.**

### B. Transactional recognizer — train ONE model today
Use Mapua PASS-only raw clips for exactly nine classes:
HELLO, YES, NO, THANK_YOU, PLEASE, HOW_MUCH, CASH, CARD, RECEIPT.

Known PASS counts total 219:
HELLO=19
YES=26
NO=33
THANK_YOU=24
PLEASE=17
HOW_MUCH=12
CASH=26
CARD=34
RECEIPT=28

Use the already successful Mapua rescue extraction/training infrastructure and RD-TCN48 family. Do not benchmark GRU/Transformer today. Reuse cached canonical extraction if trustworthy; otherwise extract only these nine classes.

### C. MILK/RICE lane — quickest evidence-backed path
The preserved Standard FSL-105 model contains MILK and RICE.

Before collecting new data, test Standard FSL-105 raw predictions for MILK and RICE on the Samsung while exposing no other Standard labels to the Scenario-15 output.

If BOTH pass the quick live gate, use Standard only as a restricted MILK/RICE specialist.

If either fails:
- do not repair all FSL-105;
- create a debug FullSign225 capture/calibration path for MILK/RICE;
- collect target-device examples in separated sessions;
- build a clearly labeled calibrated specialist profile only for MILK/RICE;
- never claim signer-independent generalization.

Do not train recognition from Avatar motion.

---

## 2. CRITICAL TEMPORAL RULE — FIX ONLY WHAT MATTERS

Canonical features remain:
`pose99 | anatomical-left63 | anatomical-right63 = 225`
ML input unmirrored; no anatomical slot swap.

Mapua RD-TCN target is `[1,48,225]`.

Training uses a complete detected motion trajectory normalized to fixed positions. The live Mapua lane must not blindly classify the latest 48 camera frames if that includes preparation/neutral/partial motion.

Implement the smallest reliable event capture necessary:

`IDLE -> ARM -> SIGN_ACTIVE -> END -> RESAMPLE -> INFER -> WAIT_RELEASE -> IDLE`

Requirements:
- start only when pose + hand quality is usable and motion begins;
- collect timestamped FullSign225 frames through the actual event;
- finish on bounded low-motion/release or timeout;
- preserve a small neutral boundary only;
- interpolate only short internal gaps;
- resample the complete event to exactly 48 positions;
- one event -> one accepted token;
- require release/re-arm before duplicate token.

Do not rewrite Standard FSL-105 or the phrase model temporal contract today unless their live smoke test proves a blocking defect.

---

## 3. CONTEXTUAL ROUTER — USE THE SCENARIO TO REDUCE AMBIGUITY

Implement a deterministic Scenario-15 state machine instead of comparing unrelated softmax probabilities across models.

### State S0 GREETING
Accepted user-facing token:
- HELLO from Mapua9
On HELLO -> S1 NAME_EXCHANGE.

### State S1 NAME_EXCHANGE
Run/expose the phrase recognizer:
- WHAT, YOUR, NAME, MY only
Compose only from accepted tokens.
- WHAT YOUR NAME -> show/speak "What is your name?"
- MY NAME -> activate controlled explicit name entry.
When a name exchange completes or user chooses to continue -> S2 RETAIL.
Allow HELLO greeting reuse with an explicitly stored session name, but never infer the name.

### State S2 RETAIL
Run/expose:
- Mapua9: YES, NO, THANK_YOU, PLEASE, HOW_MUCH, CASH, CARD, RECEIPT
- MILK/RICE specialist
HELLO may be optionally accepted as reset/greeting only if empirically stable.

THANK_YOU may close the transaction -> S0 after release.

Provide a debug-only state override so testing each vocabulary item does not require replaying the whole conversation.

This context routing is part of the controlled-scenario design, not unrestricted language translation.

---

## 4. ONE-DAY CLOCK — NO DEVIATION WITHOUT EVIDENCE

### BLOCK A — 0:00 to 1:00: PRE-FLIGHT + EXISTING LIVE SMOKE
1. Checkout exact branch, record HEAD, clean worktree.
2. Run unit tests + assembleDebug.
3. Confirm Samsung in `adb devices -l`.
4. Install current debug build.
5. Verify existing on-device feature/TFLite parity markers.
6. Run QUICK GATE, 3 valid attempts each:
   WHAT, YOUR, NAME, MY, MILK, RICE.
7. Log raw top1/top3, confidence/margin, accepted/rejected.

Decision:
- 3/3 correct = freeze that token/lane for now.
- 2/3 = keep but flag for targeted calibration.
- <=1/3 = blocking; use fallback path immediately.

Do not tune thresholds before seeing raw top1.

### BLOCK B — 1:00 to 3:00: BUILD MAPUA9
In parallel with diagnosis:
1. Freeze exact nine-class split manifest.
2. Prepare complete-motion 48x225 features.
3. Train ONE RD-TCN48 candidate.
4. Export float32 TFLite.
5. Require TF/TFLite top1 parity and shape/hash report.
6. Do not open a broad architecture search.

Target offline sanity:
- macro-F1 >=0.90 desirable;
- class-level failures matter more than aggregate score.
If <0.90, inspect data/split/feature contract before increasing model size.

### BLOCK C — 3:00 to 5:00: ANDROID SCENARIO ROUTER + TEMPORAL EVENT
1. Add `SCENARIO15_FAST_V1` experimental profile/state machine.
2. Integrate Mapua9 RD-TCN48.
3. Reuse phrase_v1 for name concepts.
4. Use restricted Standard MILK/RICE only if live quick gate passed.
5. Add complete-event resampling only to the Mapua9 lane first.
6. Add raw diagnostics and per-event reason codes.
7. No UI redesign. Minimal status/debug text only.

Run tests/build after integration.

### BLOCK D — 5:00 to 7:00: LIVE TRIAGE
Use debug state override. Perform 3 attempts for all 15 concepts = 45 valid positive trials.

For each token classify:
- GREEN: 3/3 raw top1 correct
- YELLOW: 2/3
- RED: <=1/3

Also run at least 3 attempts each of:
idle/no hands, open palm, random wave, touching face, pointing, hand entry/exit, partial sign.

Do not hide classifier failures behind the gate.

### BLOCK E — 7:00 to 10:00: FIX ONLY YELLOW/RED
Priority order:
1. feature/mirror/L-R/temporal bugs;
2. incomplete event capture;
3. targeted Samsung calibration;
4. threshold/gate tuning only after raw class quality is good.

For Mapua weak classes, collect targeted examples only for failed classes.
For phrase words, calibrate/retrain only failed name concepts; do not retrain the whole phrase vocabulary unless needed.
For MILK/RICE, if Standard is poor, create the calibrated specialist now.

Keep calibration and qualification sessions separate.

### BLOCK F — 10:00 to 12:00: FINAL QUALIFICATION + FREEZE
Target final positive test:
5 attempts per concept = 75 trials.

Preferred DEMO_READY:
- 5/5 raw top1 correct;
- zero wrong accepted for that token.

Emergency minimum for DEMO_CANDIDATE:
- >=4/5 raw top1 correct;
- zero wrong accepted;
- label it CANDIDATE, not perfect.

RED/BLOCKED if <=3/5.

Run at least 30 negative/non-sign attempts across prescribed categories.
Target false-accept rate <=5%.

Then:
- freeze best build/profile;
- update shared manifest statuses;
- save hashes;
- update handoff;
- preserve all rollback profiles.

---

## 5. MILK/RICE CALIBRATED FALLBACK

Only if Standard specialist fails.

Capture using the SAME FullSign225 inference contract.

Minimum emergency collection if time allows:
- MILK: 20 clean trials
- RICE: 20 clean trials
- NSAC/non-sign: >=40 varied trials
- split by capture session; do not random-split near-identical burst repetitions.

Train a tiny specialist model with explicit rejection/NSAC only if it validates better than restricted Standard live performance.

Do not waste time building a general replacement for FSL-105.

---

## 6. NAME FLOW IS MANDATORY

Physical final checks must include:
- five complete WHAT -> YOUR -> NAME sequences;
- five complete MY -> NAME sequences;
- explicit name entry;
- display/speech "My name is <entered>";
- later "Hello, <entered>" only from stored session context;
- Clear/reset removes stored name;
- no fabricated names.

If phrase tokens are individually good but ordering is bad, fix composer/state logic, not the classifier.

---

## 7. OOD / FALSE OUTPUT RULE

A classifier choosing a class is not enough to emit to the user.

Use available evidence:
- event completeness;
- pose/hand quality;
- confidence;
- margin;
- temporal stability;
- NSAC where model supports it;
- optional energy/prototype distance only if already cheap to implement.

Do not create a universal NOTHING vocabulary class for Mapua9. User-facing no-output is a software rejection state.

---

## 8. DO NOT DO TODAY

- no 105-class retraining;
- no new architecture family unless current model is mathematically dead;
- no alphabet/fingerspelling expansion;
- no Avatar edits;
- no Listen animation edits;
- no UI redesign;
- no dataset-wide manual review beyond selected classes;
- no chapter/paper work inside this coding lane;
- no D: access;
- no force push;
- no giant media/raw data commits.

---

## 9. GIT CHECKPOINTS

Push at minimum:
1. preflight + live-smoke report;
2. Mapua9 model/export/parity;
3. Android Scenario15 integration;
4. live triage;
5. final qualification/frozen build status.

Update:
- `reports/CODEX_LIVE_HANDOFF.md`
- `reports/scenario15_counter_v1/SCENARIO15_VOCABULARY_MANIFEST.csv`
- concise evidence reports under `reports/scenario15_counter_v1/`

Raw captures, APKs, caches, checkpoints, private media stay outside Git.

---

## 10. AUTONOMY

Do not ask the owner for ordinary engineering permission.
When a human must physically sign, first prepare the exact screen/logging, then request a compact batch such as:

"Perform WHAT 3x, YOUR 3x, NAME 3x, MY 3x."

Continue with everything else while waiting.

---

## 11. FINAL RETURN

`ONE_DAY_STATUS=`
`BRANCH=`
`HEAD=`
`SAMSUNG_CONNECTED=`
`TESTS_BUILD=`
`NAME_PHRASE_LIVE_STATUS=`
`MILK_RICE_LIVE_STATUS=`
`MAPUA9_MODEL=`
`MAPUA9_OFFLINE_MACRO_F1=`
`MAPUA9_TFLITE_PARITY=`
`TEMPORAL_EVENT_CAPTURE=PASS/FAIL`
`SCENARIO_ROUTER=PASS/FAIL`
`QUICK_45_COMPLETE=YES/NO`
`FINAL_75_COMPLETE=YES/NO`
`DEMO_READY_COUNT=/15`
`DEMO_READY_WORDS=`
`DEMO_CANDIDATE_WORDS=`
`BLOCKED_WORDS=`
`FINAL_RAW_TOP1_RATE=`
`WRONG_ACCEPT_RATE=`
`NEGATIVE_FALSE_ACCEPT_RATE=`
`NAME_FLOW_PASS=YES/NO`
`STANDARD_FSL105_PRESERVED=YES/NO`
`MAPUA14_ROLLBACK_PRESERVED=YES/NO`
`AVATAR_MODIFIED=NO`
`FROZEN_PROFILE=`
`BLOCKERS=`
`NEXT_EXACT_ACTION=`

The rule for today: **reuse strong assets, train only what is missing, route by scenario context, test physically early, fix only observed failures, and freeze a defensible build before the day ends.**
