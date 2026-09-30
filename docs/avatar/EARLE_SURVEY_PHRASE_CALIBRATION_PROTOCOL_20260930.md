# VoxGest — Earle Survey Phrase Calibration Protocol — 2026-09-30

## Scope

This protocol defines how the Avatar team should create three respondent-facing FSL phrase actions:

- FSL_PHRASE_SURVEY_INTRO
- FSL_PHRASE_FSL_LEARNING
- FSL_PHRASE_TRY_AND_RATE

These are phrase-level Avatar actions for the LISTEN side. They are not classifier labels and must not be inferred from English/Filipino word order.

## Critical linguistic rule

Engineering staff must not invent the FSL grammar for these phrases.

Each phrase requires an authoritative FSL reference recording from an FSL-qualified reviewer/reference signer or another project-approved authoritative FSL source.

The three spoken-language examples are only semantic intents:

### SURVEY_INTRO
EN meaning:
"Hi! We are a group of IT students making/developing a system about Filipino Sign Language."

FIL meaning:
"Hi! Mga IT students kami at gumagawa kami ng system tungkol sa Filipino Sign Language."

### FSL_LEARNING
EN meaning:
"We don't know much about FSL yet, but we are eager to try and learn."

FIL meaning:
"Hindi pa kami gaanong marunong sa FSL pero gusto naming matuto at subukan."

### TRY_AND_RATE
EN meaning:
"Can you please try our system and rate it?"

FIL meaning:
"Pwede mo bang subukan ang system namin at i-rate ito?"

The reference signer determines the actual FSL realization, order, transitions, and non-manual behavior.

## Reference capture request

For each phrase, obtain at least 3 clean takes from the same FSL-qualified/reference signer.

Preferred recording:
- front-facing;
- upper body, elbows, wrists, and hands always visible;
- face fully visible;
- stable camera;
- landscape if convenient;
- 1080p preferred;
- 30 or 60 fps;
- no digital zoom;
- even lighting;
- plain background;
- a neutral lead-in and lead-out;
- natural signing speed;
- no instruction to slow the sign artificially.

Record:
- phrase ID;
- signer alias;
- date;
- FPS/resolution;
- take number;
- reviewer/validation status;
- consent/use scope;
- any gloss/translation notes supplied by the reviewer.

Do not publish private reference video to Git unless explicitly authorized.

## Calibration strategy

For each phrase:

1. Preserve the reference recording unchanged.
2. Identify preparation, stroke(s), holds, transitions, and recovery.
3. Inspect handshape, signing location, palm orientation, arm trajectory, timing, and non-manual features.
4. Retarget/reconstruct motion onto the canonical Core3 skeleton.
5. Do not concatenate arbitrary isolated-sign actions merely to mimic English/Filipino syntax.
6. If the FSL reviewer explicitly defines a phrase as a sequence of already validated actions, the runtime sequencer may be used instead of one monolithic phrase action.
7. If technical/proper tokens such as "IT", "FSL", or "VoxGest" are fingerspelled in the validated reference, leave explicit slots for the future FSL A-Z spelling scheduler rather than inventing temporary handshapes.

## Candidate action names

- FSL_PHRASE_SURVEY_INTRO_V1
- FSL_PHRASE_FSL_LEARNING_V1
- FSL_PHRASE_TRY_AND_RATE_V1

Never overwrite a prior accepted/reviewed candidate. Increment versions.

## Acceptance gates

For each phrase:

- SOURCE_MAPPING: exact reference take identified
- SOURCE_REVIEW: semantic/linguistic reference approved
- MECHANICAL: finite transforms, no snaps, no illegal scale, acceptable deformation
- COLLISION: face/body/finger clearance reviewed
- HUMAN_MOTION_VISUAL: full-speed comparison against reference
- NON_MANUAL: document what facial/head/body information is reproduced, missing, or unsupported by the current rig
- EXPORT: GLB/action validation
- OWNER_DEVICE: Samsung playback/replay/neutral return/framing
- FSL_FINAL_REVIEW: phrase output approved by qualified reviewer

Until all required gates pass:
- listen_ready=false
- do not call the phrase FSL-approved
- do not place it in production LISTEN

## Mapua Transactional FSL dataset role

The Mapua transactional dataset may be used as a supplementary reference for isolated transactional signs that it actually contains, such as HELLO, PLEASE, THANK YOU, YES, NO, and other listed classes.

It is not the authoritative source for the three complete survey phrases because it is an isolated transactional-sign dataset, not a sentence/phrase translation corpus.

Do not concatenate Mapua isolated classes to invent the phrase grammar.

If raw Mapua videos are available and licensing/use scope permits, Earle may use them only as secondary isolated-sign visual references. If only coordinate arrays are available, they may support motion analysis but are not a substitute for a full human reference recording of the phrase.

## A-Z relationship

The three survey phrases should not wait on all 26 letters unless their approved FSL realization requires fingerspelling.

Where the reference signer fingerspells:
- IT
- FSL
- VoxGest
- a personal name
- another proper noun

the phrase plan should insert a FINGERSPELL slot.

Example conceptual plan only:

FSL_PHRASE_SURVEY_INTRO:
  validated phrase segment
  -> FINGERSPELL("IT") if required
  -> validated phrase segment
  -> FINGERSPELL("FSL") if required

The exact order comes from FSL review, not engineering assumptions.

## Responsibility

Earle/Astra:
- reference intake;
- calibration/retargeting;
- engineering validation;
- GLB/action export;
- documentation/hashes.

Project-owner Android side:
- STT;
- EN/FIL intent normalization;
- phrase resolver;
- action/fingerspelling scheduler;
- Samsung integration.

FSL-qualified reviewer:
- actual phrase formulation;
- linguistic source approval;
- final output approval.

## Immediate deliverable before calibration

Earle should first create a REFERENCE_REQUEST packet containing:
- the three semantic intents;
- recording instructions;
- phrase IDs;
- required metadata;
- missing reference status.

Do not begin fabricating phrase motion until at least one approved reference take exists for that phrase.
