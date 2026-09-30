# VoxGest Survey Dialogue Translation Plan — 2026-09-30

## Goal

Enable the LISTEN side of VoxGest to hear short English/Filipino respondent-facing utterances and drive the Avatar with validated FSL output.

This is not unrestricted spoken-language-to-FSL translation. The first production-safe target is a small **survey dialogue mode** built from validated phrase intents plus fingerspelling fallback.

## Target survey utterances

### INTENT_SURVEY_INTRO
English examples:
- Hi! We are a group of IT students making a system about Filipino Sign Language.
- Hello! We are IT students developing an FSL system.

Filipino examples:
- Hi! Mga IT students kami at gumagawa kami ng system tungkol sa Filipino Sign Language.
- Hello! Mga IT students kami na gumagawa ng FSL system.

### INTENT_FSL_LEARNING
English examples:
- We don't know much about FSL yet, but we are eager to try and learn.
- We are still learning FSL, but we want to try.

Filipino examples:
- Hindi pa kami gaanong marunong sa FSL pero gusto naming matuto at subukan.
- Nag-aaral pa kami ng FSL pero gusto naming subukan.

### INTENT_TRY_AND_RATE
English examples:
- Can you please try our system and rate it?
- Please try our system and tell us how you would rate it.

Filipino examples:
- Pwede mo bang subukan ang system namin at i-rate ito?
- Pakisubukan ang system namin at bigyan ito ng rating.

These examples map to canonical semantic intents. They do not define FSL grammar.

## Current dataset limitation

FSL-105 is an isolated-sign dataset. It contains useful vocabulary such as HELLO, HOW ARE YOU, I AM FINE, THANK YOU, UNDERSTAND, DON'T UNDERSTAND, KNOW, DON'T KNOW, YES, NO, WRONG and other introductory classes, but it does not provide the complete survey-specific vocabulary or validated sentence translations needed for the three survey utterances.

Do not construct the three survey messages by naively playing English/Filipino words one-by-one.

## Recommended MVP approach

### Phrase-level validated actions

Create three FSL-reviewed phrase actions:

- FSL_PHRASE_SURVEY_INTRO
- FSL_PHRASE_FSL_LEARNING
- FSL_PHRASE_TRY_AND_RATE

Each phrase must be sourced from an FSL-qualified reviewer/reference signer.

The expert should determine:
- gloss/order;
- handshapes;
- signing locations;
- non-manual markers;
- timing;
- transitions.

Earle/Astra may calibrate/retarget only after the reference phrase is approved.

### Fingerspelling fallback

Use validated FSL A-Z fingerspelling for:
- IT
- FSL
- VoxGest
- personal names
- technical/proper nouns without a validated lexical action

Do not invent A-Z actions or reuse the old ASL alphabet as FSL.

## LISTEN runtime pipeline

Microphone
-> on-device speech-to-text
-> EN/FIL text normalization
-> phrase/intent matcher
-> semantic intent
-> approved FSL phrase plan
-> Avatar action queue
-> validated fingerspelling slots
-> playback

Example:

"Hi! We are IT students making an FSL system."
-> INTENT_SURVEY_INTRO
-> FSL_PHRASE_SURVEY_INTRO
-> optional fingerspelling slots if required by the validated phrase plan

## Responsibility split

### Project-owner/main Android side
- microphone/STT
- text normalization
- English/Filipino aliases
- intent matching
- phrase scheduler
- A-Z spelling scheduler
- Android playback integration
- Samsung qualification
- UI

### Earle/Astra
- import approved FSL reference recordings
- calibrate phrase Actions onto Core3
- calibrate validated A-Z fingerspelling Actions
- mechanical/collision/visual checks
- versioned GLB/package export
- reproducibility/hashes

### FSL-qualified reviewer
- define/approve the actual FSL phrase
- validate FSL fingerspelling forms
- validate non-manual components where needed
- approve final phrase output

No engineering-side component should invent FSL grammar.

## Immediate Earle workload

Do not ask Earle to create every English word in the scripts.

Priority:
1. correct THANK YOU onset
2. correct/validate UNDERSTAND
3. obtain FSL-qualified reference for the three survey phrases
4. calibrate the three phrase Actions
5. obtain/validate FSL A-Z fingerspelling references
6. calibrate A-Z in a separate package/lane

The prior proposed isolated concepts NAME, YOU, PLEASE, HELP, SORRY remain useful for general vocabulary, but they are secondary if the immediate survey goal is to deliver the three fixed respondent-facing messages.

## Success condition

The survey mode is ready only when:
- speech aliases correctly resolve to the three intents;
- the phrase actions are FSL-reviewed;
- the Avatar plays them smoothly;
- A-Z fallback is validated where used;
- Samsung device playback passes;
- no production claim is made beyond the supported phrase set.
