# VoxGest Scenario-15 Emergency Master Prompt — Sol

**Issued:** 2026-09-20
**Branch:** `recognition/scenario15-counter-v1`
**Base:** `recognition/mapua14-rescue-v1` @ `496782599734fd369808f4ba3d395550b2b59d71`
**Deadline mode:** final-defense rescue. Optimize for a small, real, demonstrably working system—not broad unsupported coverage.

## 0. PRODUCT DECISION — FROZEN UNLESS OWNER CHANGES IT

VoxGest will now demonstrate one narrow real-life scenario:

**GREETING + NAME EXCHANGE + SMALL RETAIL / CONVENIENCE-STORE COUNTER COMMUNICATION**

This is intentionally not unrestricted conversation and not medical/emergency interpretation. It covers greeting, item purchase, quantity/payment, clarification, and closing.

Freeze these exact 15 user-facing concept IDs:

1. HELLO
2. WHAT
3. YOUR
4. NAME
5. MY
6. YES
7. NO
8. THANK_YOU
9. PLEASE
10. MILK
11. RICE
12. HOW_MUCH
13. CASH
14. CARD
15. RECEIPT

Filipino presentation map:
HELLO=Kumusta
WHAT=Ano
YOUR=Iyong / Mo
NAME=Pangalan
MY=Aking / Ko
YES=Oo
NO=Hindi
THANK_YOU=Salamat
PLEASE=Pakiusap
MILK=Gatas
RICE=Kanin
HOW_MUCH=Magkano?
CASH=Pera / Cash
CARD=Card
RECEIPT=Resibo

Do not silently rename model IDs. UI may present Filipino/English/Both.

## 1. WHAT “WORKING 15” MEANS

A concept is not `WORKING` because it exists in labels or has high offline accuracy.

Recognition status per concept:
- DATA_READY
- MODEL_READY
- LIVE_CANDIDATE
- DEMO_READY
- BLOCKED

For DEMO_READY on the target Samsung/demo signer:
- later qualification session, 5/5 valid attempts raw top-1 correct;
- no wrong accepted label in those 5 attempts;
- reasonable latency;
- repeat/release behavior works;
- no mirror/L-R defect.

Global user-facing rejection target:
- negative false-accept rate <=5% over prescribed negatives.
- NOTHING remains a rejection state, never a 16th vocabulary class.

If a concept cannot reach DEMO_READY, report it honestly and replace it only with owner approval. Never fake 15/15.

## 2. DATA SOURCE PLAN

### Mapua source-domain classes — 13/15
Use raw Mapua Transactional FSL videos for:
HELLO, YES, NO, THANK_YOU, PLEASE, HOW_MUCH, CASH, CARD, RECEIPT, WAIT, AGAIN, ONE, TWO.

The prior audit shows 339 PASS clips across these 13 classes. Preserve the audit decisions. Start from PASS-only. REVIEW clips may be promoted only through an explicit reproducible SAFE_REVIEW rule/manual list; never silently include all REVIEW. REJECT_TECHNICAL never trains.

Known caution:
- HELLO/PLEASE/HOW_MUCH are lower-data or tracking-weaker.
- CASH had representative visual concerns.
- ONE/TWO are comparatively simple/static classes but still require complete-event and rejection validation.
Treat these as targeted-review classes, not reasons to discard the scenario automatically.

### MILK and RICE — 2/15
They are not in Mapua. Search safe C: and repository evidence for trustworthy existing FSL-105 raw clips/features/calibration data first. NEVER use retired D:.

If authoritative raw/source examples are unavailable:
- create a clearly named **Samsung-calibrated demo lane**;
- capture at least 20 clean MILK and 20 clean RICE repetitions across two separated sessions if physically feasible;
- preserve session IDs;
- do not mix repeated takes from the same capture burst across optimization and later qualification;
- label resulting claims as device/signer-calibrated, not signer-independent.

Do not train from Avatar motion.

## 3. ONE FEATURE/TEMPORAL CONTRACT FOR TRAINING AND ANDROID

This is the highest priority engineering requirement.

Canonical features:
`pose99 | anatomical-left63 | anatomical-right63 = 225`
unmirrored ML input; no L/R slot swap; missing-hand block = zeros; keep the validated normalization contract.

Target sequence:
`[1,48,225]`

Training and Android MUST represent the same physical event:

`IDLE -> PRIMING -> SIGN_ACTIVE -> END/RELEASE -> complete trajectory -> normalize/resample to 48 -> classifier`

Do NOT feed an arbitrary “last 48 camera frames” if training uses complete-motion resampling.

Create/version a single temporal contract:
- event start/end rules;
- allowed neutral boundary;
- timestamp handling;
- short-gap interpolation;
- missing-hand handling;
- resampling to 48 normalized positions;
- release/re-arm.

Add Python + Android golden fixtures proving semantic parity. Exact MediaPipe floats need not match across runtimes, but orientation, slots, scale, missing policy, trajectory coverage, and resampling must.

Preserve protected `(System.nanoTime() / 1_000_000L)` where applicable.

## 4. TRAINING STRATEGY

Do not resurrect the full 105-class objective.

Build a separate user-facing profile/manifest:
`SCENARIO15_COUNTER_V1`

The user-facing vocabulary is exactly 15 concepts, but the backend does NOT have to be forced into one classifier if that increases risk. First establish the 9-class Mapua transactional baseline using the same RD-TCN48 family that already performed strongly. Audit the existing WHAT/YOUR/NAME/MY lane. Add/retrain the four name concepts plus MILK/RICE only when trustworthy source/calibration tensors exist. If multiple recognizers remain, route them deterministically and prove no ambiguous double-emission.

Use:
- frozen split manifests;
- source-video separation;
- duplicate groups kept in one partition;
- no val/test augmentation;
- safe modest temporal/coordinate/dropout augmentation only;
- validation macro-F1 primary;
- class-level confusion;
- calibration metrics;
- TFLite parity.

Because the goal is live demo reliability, do not burn time comparing many architectures unless the RD-TCN48 path is empirically dead.

If weak classes fail:
1. verify runtime/train parity;
2. inspect class data;
3. add targeted Samsung captures;
4. only then retrain.

## 5. SAMSUNG DOMAIN ADAPTATION

After baseline model/device parity:
- capture labeled live examples for weak concepts, not random bulk data;
- include separate session/lighting/background tags;
- preserve a later sealed physical qualification session;
- compare raw classifier quality before gate tuning.

For Mapua-domain concepts, targeted calibration can be used if needed, but keep Mapua as source anchor so the model does not become pure memorization of one session.

Report:
`RAW_TOP1_CORRECT`
`CORRECT_ACCEPTED`
`WRONG_ACCEPTED`
`REJECTED`
`NEGATIVE_FALSE_ACCEPTS`

## 6. OOD / NON-SIGN

Required negatives:
no hands; idle body; open palm; random wave; touching face; pointing; hand entry/exit; partial sign; natural gesturing.

A closed 15-way softmax always chooses something. Confidence alone is not enough.
Use evidence-supported combinations of:
- activity/event completeness;
- top1 confidence;
- top1-top2 margin;
- temporal stability;
- pose/hand quality;
- energy or embedding/prototype distance if practical.

Do not add NOTHING as a class.

## 7. USER-FACING PRODUCT SCOPE

Create a versioned shared vocabulary manifest for these 15 concepts with:
- canonical_id
- Filipino display
- English display
- recognition_status
- recognition_model_index
- avatar_status
- listen_ready
- sign_to_text_ready
- notes/provenance

Recognition and Avatar readiness are separate.

Sol owns:
- recognition model/data/runtime;
- text/TTS presentation mapping;
- speech concept resolver for these exact 15 IDs;
- scenario vocabulary manifest;
- diagnostics/testing.

Sol MUST NOT modify Earle/Astra Avatar calibration assets or claim an Avatar Action is ready.

Earle/Astra owns Avatar Actions. Only set `listen_ready=true` after an Avatar Action is independently validated and handed off.

Current known Avatar work includes historical/reconstructed HELLO/MILK/RICE and experimental YES/THANK_YOU/NO; treat their readiness from the Avatar handoff, not assumptions.

## 8. DEMO FLOW

The final demo should make sense as a counter interaction, e.g.:

HELLO
WHAT + YOUR + NAME -> “What is your name?”
MY + NAME -> open/continue the controlled name-entry flow -> “My name is <entered name>”
HELLO + known conversation name -> may display “Hello, <name>” only when the name was explicitly supplied in this session
MILK / RICE
HOW_MUCH
CARD / CASH
RECEIPT
PLEASE
YES / NO
THANK_YOU

Proper names themselves are NOT 15 classifier classes. Arbitrary personal names remain text entered/spelled through the existing controlled name flow unless a separately validated fingerspelling recognizer is available. Do not claim unrestricted name-sign or alphabet recognition.

The 15 concepts are intentionally small and composable. The UI may show chronological accepted tokens and safe deterministic phrase composition, but must not claim unrestricted grammatical FSL sentence translation. Do not silently expand past 15.

## 9. PAPER/DEFENSE CLAIM CONTRACT

All engineering reports must support this exact claim:

“VoxGest is evaluated as an offline Android bidirectional accessibility prototype for a controlled 15-concept FSL scenario covering greeting, name exchange, and a small retail-counter interaction. It does not claim unrestricted FSL translation, unrestricted conversation, or arbitrary proper-name recognition.”

If Samsung calibration is required, say:
“calibrated demonstration profile”
rather than “signer-independent recognition.”

Do not claim medical/emergency use from this counter vocabulary.

## 10. PROTECTION RULES

- Safe C: only. Never D:.
- Preserve Standard FSL-105 and Mapua14 rescue as rollback.
- Do not overwrite generic *_v1 assets.
- No force-push/history rewrite.
- Raw videos/features/checkpoints/APKs/device captures/secrets stay out of Git.
- UI styling frozen except minimal scenario/debug controls required for testing.
- Avatar/Listen motion assets are out of recognition scope.
- No success claim from build/offline metrics alone.

## 11. EXECUTION ORDER

1. Verify branch/worktree/remotes.
2. Read prior Mapua audit, Mapua14 reports, temporal sprint prompt, live handoff.
3. Create scenario15 manifest and exact data inventory.
4. Audit the existing WHAT/YOUR/NAME/MY + NamePhraseDetector path and resolve MILK/RICE source availability.
5. Implement/prove complete-event 48-frame train/runtime parity.
6. Train Mapua 9 transactional baseline and qualify the name lane.
7. Physical Samsung raw test.
8. Capture targeted calibration/MILK/RICE data if required.
9. Train SCENARIO15_COUNTER_V1.
10. TFLite parity.
11. Android experimental profile.
12. 15-concept physical qualification + negative tests.
13. Freeze best build; update documentation.

Checkpoint before every long training job and after every physical qualification.

## 12. REQUIRED RETURN

`SCENARIO15_STATUS=`
`BRANCH=`
`HEAD=`
`VOCABULARY_15=`
`MAPUA_CLASSES_READY=`
`MILK_SOURCE=`
`RICE_SOURCE=`
`TEMPORAL_TRAIN_RUNTIME_PARITY=`
`MODEL_PROFILE=`
`INPUT_SHAPE=`
`OFFLINE_MACRO_F1=`
`TFLITE_PARITY=`
`SAMSUNG_TESTED=YES/NO`
`DEMO_READY_COUNT=/15`
`DEMO_READY_WORDS=`
`BLOCKED_WORDS=`
`RAW_TOP1_RATE=`
`WRONG_ACCEPT_RATE=`
`NEGATIVE_FALSE_ACCEPT_RATE=`
`STANDARD_FSL105_PRESERVED=YES/NO`
`MAPUA14_ROLLBACK_PRESERVED=YES/NO`
`AVATAR_FILES_MODIFIED=NO`
`NEXT_EXACT_ACTION=`
`HANDOFF_UPDATED=YES/NO`

## 13. NAME / GREETING ACCEPTANCE GATE

Name exchange is a first-class demo requirement, not a cosmetic phrase.

Required physical flows:
1. HELLO must recognize reliably.
2. WHAT -> YOUR -> NAME must be accepted in order and compose exactly “What is your name?”.
3. MY -> NAME must enter the existing name-entry mode.
4. After a user explicitly enters a name, the UI may compose “My name is <name>”.
5. The conversation session may reuse that explicitly supplied name for a greeting such as “Hello, <name>”; never hallucinate or infer a name.
6. Clear/reset must remove the active name context.
7. The system must not claim that arbitrary names are recognized as FSL signs unless a separate alphabet/fingerspelling feature passes its own validation.

Test at least five complete WHAT-YOUR-NAME phrase trials and five MY-NAME entry trials on Samsung in the final qualification, in addition to per-token tests.

The governing objective is simple: **15 exact concepts, one believable greeting/name/retail scenario, one train/runtime feature contract, and a Samsung-qualified demo. No scope creep.**
