# VoxGest Recognition Engineering Evolution and Current System Guideline

**Date:** 2026-09-20  
**Audience:** software engineer / ML engineer / thesis technical reviewer  
**Repository:** `justcedm/LatestVoxGest`  
**Current recognition branch:** `recognition/fsl-dual-dataset-reset-v1`

## 1. Original product problem

VoxGest began as an offline Android accessibility prototype intended to reduce the communication barrier between sign-language users and hearing/non-signing users.

The product was divided into two directions:

1. **Sign -> hearing**
   camera -> landmark extraction -> sign classifier -> text -> optional TTS.

2. **Hearing -> signing**
   speech/text -> supported concept mapping -> Avatar animation.

Recognition and Avatar are separate engineering subsystems and must be validated separately.

The recognition scope is **controlled isolated-sign recognition**, not unrestricted FSL translation or full FSL grammar.

## 2. Early recognition architecture

The earliest recognition work used two different feature families:

- static alphabet experiments: one normalized hand, 21 landmarks x xyz = 63 features;
- dynamic word experiments: temporal windows using one dominant hand plus body pose, commonly 30 frames x 162 features.

These experiments produced the initial demo lanes and the protected `demo10` baseline.

The main weakness discovered was that a model can score well on stored examples but still fail live because camera timing, handedness, missing landmarks, motion boundaries, and device-domain behavior differ from training.

## 3. FullSign225 transition

To stop throwing away useful body and second-hand information, VoxGest introduced the canonical FullSign225 representation:

`pose99 | anatomical-left63 | anatomical-right63 = 225 features/frame`

The contract became:

- canonical unmirrored ML input;
- front preview may be mirrored for the user, but ML coordinates are not;
- no left/right slot swap after anatomical resolution;
- pose normalized relative to the nose;
- each hand normalized using wrist -> middle-finger MCP scale;
- z damped by x0.3;
- missing landmark blocks are deterministic zeros.

This created a stable feature interface between MediaPipe and temporal classifiers.

## 4. Standard FSL-105 lane

A broader Standard lane was built around FSL-105 and a FullSign225 Android contract. It remained protected as a rollback/reference profile.

Engineering work showed two important limitations:

1. a closed-set softmax always chooses one vocabulary label, even for idle or unrelated movement;
2. high offline accuracy does not prove live Android correctness.

This led to explicit rejection/OOD handling, negative categories, physical qualification requirements, and separation between raw top-1 correctness and gate acceptance.

## 5. Repository recovery and engineering controls

In September 2026 the project was hardened operationally:

- GitHub became the source of truth;
- recovery work moved to isolated branches;
- retired D: workspace was prohibited;
- raw datasets, APKs, caches, environments, device captures, checkpoints, and secrets were kept out of Git;
- deployable TFLite models, labels, manifests, tests, source, and concise evidence remained versioned;
- Avatar/UI were frozen while recognition was being qualified;
- every meaningful recognition task had to update `reports/CODEX_LIVE_HANDOFF.md`.

These controls were introduced to make the capstone reproducible and prevent rushed work from overwriting known-good baselines.

## 6. Mapua raw-video audit

The published Mapua Transactional FSL raw-video package was audited independently.

Local audited package:
- 1,107 MP4;
- 26 FSL classes;
- 670 PASS;
- 408 REVIEW;
- 29 REJECT_TECHNICAL.

MediaPipe was treated only as the landmark extractor. The audit measured:
- pose presence;
- hand presence;
- left/right/both-hand tracking;
- internal dropout;
- framing;
- motion duration;
- landmark-frame duration;
- temporal retention.

This stage established which raw clips were technically suitable for training. It did **not** claim linguistic correctness or signer-independent generalization.

## 7. Complete-event temporal representation

A major architectural correction followed.

Older live implementations could classify a rolling camera window. Training, however, often represented a complete sign trajectory.

That mismatch can cause a good model to fail live.

The new temporal rule became:

`neutral -> sign starts -> complete motion -> sign ends -> normalize/resample -> classifier`

rather than:

`last N camera frames -> classifier`.

For the rescue work, complete motion was resampled to fixed temporal positions. 48 frames retained more of the measured trajectory than shorter 20/32-frame representations and performed strongly in controlled development experiments.

## 8. Mapua14 rescue

A controlled 14-class rescue experiment was created using exact-text overlap classes.

Input:
`[1,48,225]`

Candidate comparison:
- RD-TCN32;
- GRU32;
- RD-TCN48;
- GRU48.

RD-TCN48 won development selection.

Offline sealed result:
- accuracy: 98.21%;
- macro-F1: 97.96%;
- TF/TFLite top-1 agreement: 100%.

This proved the complete-trajectory FullSign225 + RD-TCN48 approach was technically strong on the Mapua clip distribution.

It did not prove live Samsung performance.

## 9. Linguistic reset

A later review identified that historical WHAT/YOUR/NAME/MY phrase experiments were not validated FSL and could contaminate the final claim.

The recognition project was reset to published FSL sources only:

- Mapua Transactional FSL;
- FSL-105 / De La Salle University.

Quarantined from the final FSL claim:
- ASL alphabet;
- WHAT/YOUR/NAME/MY experimental phrase lane;
- WLASL;
- random internet signs;
- unvalidated team-created signs.

The official FSL-105 raw media was not found on safe C: and automated official acquisition was blocked by HTTP 403/Cloudflare. No unofficial mirror was substituted.

Therefore the current deployable experimental result is explicitly **Mapua-only interim**, not dual-source.

## 10. Current practical15 model

Current profile:
`FSL_PRACTICAL15_V1`

Current vocabulary:
- HELLO
- THANK_YOU
- YES
- NO
- PLEASE
- HOW_MUCH
- CASH
- CARD
- RECEIPT
- WAIT
- HOW_MANY
- AGAIN
- PROBLEM
- COIN
- DISCOUNT

Training source:
published Mapua Transactional FSL PASS-only clips.

Dataset:
- 394 complete-event clips;
- 334 development;
- 60 sealed;
- no source-video overlap;
- signer-independent claim prohibited because reliable signer IDs are unavailable.

Model:
- RD-TCN48;
- 125,391 parameters;
- input `[1,48,225]`;
- output `[1,15]`.

Exploratory sealed results:
- accuracy: 95.0%;
- macro-F1: 0.955718;
- weakest class F1: 0.857143;
- confusions: CARD->COIN, CASH->PROBLEM, NO->YES, one each.

Raw-video replay:
- 60/60 decoded and processed;
- 57/60 top-1 correct;
- zero event extraction failures;
- 60/60 exact motion-envelope matches;
- regenerated tensors bit-identical to the frozen cache.

TFLite:
- top-1 agreement with TensorFlow: 1.0;
- max probability difference: 4.172325134277344e-07.

Android:
- new profile is debug-only and non-default;
- complete-event capture/resample48 integrated;
- mandatory release/re-arm;
- structural rejection checks;
- 100/100 JVM tests pass;
- debug APK builds successfully.

## 11. Current rejection architecture

The system does not treat "NOTHING" as an FSL vocabulary word.

No-output is a software rejection state.

Current evidence uses:
- minimum event frames;
- pose presence;
- hand presence;
- finite tensor check;
- timeout/incomplete-event handling;
- confidence;
- top1-top2 margin;
- trajectory motion floor;
- mandatory release/re-arm.

Offline corruption testing proved confidence/margin alone are insufficient. Physical negative testing is still required for:
- idle/no sign;
- open palm;
- random wave;
- partial sign;
- hand entry/exit;
- body movement without a deliberate sign.

## 12. Current system boundary

What is already proven:
- published FSL source provenance for the current Mapua profile;
- canonical FullSign225 representation;
- complete-event resampling;
- leakage-safe source-clip separation;
- strong offline clip-level classification;
- raw-video end-to-end replay;
- TensorFlow/TFLite parity;
- Android integration;
- Android unit/build pass.

What is NOT yet proven:
- live Samsung accuracy;
- live false-accept rate;
- camera-domain generalization;
- live latency;
- signer-independent generalization;
- dual-source FSL-105 + Mapua performance.

## 13. Engineering rules going forward

1. Do not retrain because a displayed output looks wrong until raw top-1, tracking, event capture, and parity are inspected.
2. A correct raw prediction rejected by the gate is a gate/runtime problem, not automatically a model problem.
3. A wrong raw prediction with clean tracking/event data is a model/data/domain problem.
4. Never tune confidence thresholds to hide wrong top-1 predictions.
5. Keep training and Android temporal representations identical.
6. Keep canonical ML coordinates unmirrored.
7. Keep source identity and provenance for every dataset.
8. Never call offline metrics live accuracy.
9. Never call clip-level results signer-independent when signer IDs are unavailable.
10. Never promote a vocabulary item to demo-ready without repeated physical-device evidence.
11. Preserve Standard FSL-105, Mapua14, demo10, and Avatar as rollback/protected lanes.
12. Update paper claims to engineering truth, not the reverse.

## 14. Immediate next gate

The Samsung physical qualification is now the decisive test.

The first battery must be executed with frozen model/gates before any tuning.

The result will determine whether the next action is:
- **capture/runtime repair**;
- **gate repair**;
- **targeted model/domain adaptation**;
- or **promotion of the strongest live-tested vocabulary**.

The current system is therefore best described as:

**An offline Android FSL recognition prototype using MediaPipe FullSign225 complete-event trajectories and an RD-TCN48 classifier, with strong Mapua clip-level evidence and Android parity, pending physical Samsung qualification.**
