# VoxGest Avatar Sentence Sequencing, Bilingual Intent Mapping, and FSL Fingerspelling Plan — 2026-09-30

## Decision

The current Avatar Trial is suitable for owner-device engineering qualification, but it is not yet a production LISTEN release and does not establish linguistic FSL approval.

Current known-good runtime controls:
- HELLO
- MILK
- RICE

Current unapproved candidate actions:
- THANK YOU
- YES
- NO
- IM FINE
- HOW ARE YOU
- UNDERSTAND
- GOOD EVENING
- KNOW
- WRONG

## Two immediate product goals

1. Canonical bilingual intent/action registry
2. Sentence/phrase sequencing with fingerspelling fallback

## Canonical concept IDs

Do not duplicate animations for English and Filipino text.

Each calibrated sign/action receives one canonical semantic ID and language aliases.

Example schema:

```json
{
  "concept_id": "THANK_YOU",
  "action": "THANK_YOU",
  "aliases": {
    "en": ["thank you", "thanks"],
    "fil": ["salamat", "maraming salamat"]
  },
  "linguistic_validation": "PENDING",
  "device_validation": "PASS/PENDING",
  "listen_ready": false
}
```

Aliases must be semantically reviewed. Do not map words merely because they are commonly translated similarly.

## Sentence pipeline

Do not perform naive spoken-language word-by-word animation.

Preferred pipeline:

STT/text
-> language normalization
-> semantic intent + slot extraction
-> FSL-reviewed phrase plan
-> calibrated action sequence
-> fingerspelling fallback for names/OOV tokens
-> transition scheduler
-> Avatar playback

Example input:
- EN: "Hi! My name is Anna."
- FIL: "Hi! Ako nga pala si Anna."

Both may normalize to an application-level intent such as:

```text
GREETING_SELF_INTRO(name="ANNA")
```

The actual FSL output order must be validated by an FSL-qualified reviewer. Do not assume English or Filipino word order is FSL grammar.

A practical implementation can use:
- existing HELLO action;
- one FSL-reviewed SELF_INTRO_NAME phrase/action or validated action sequence;
- fingerspelling of the name ANNA.

## Next Avatar calibration tranche

Prioritize only what unlocks sentence composition and common fallback:

1. NAME / self-introduction concept or validated SELF_INTRO_NAME phrase
2. I/ME if required by the validated FSL phrase design
3. YOU
4. PLEASE
5. HELP
6. SORRY

Do not train separate MY/IS actions merely to mimic English grammar unless FSL review demonstrates they are needed.

After this six-concept tranche and the alphabet, reassess before adding more isolated vocabulary.

## Fingerspelling architecture

Avatar-side fingerspelling should be implemented as a separate library:

```text
FSL_FS_A
FSL_FS_B
...
FSL_FS_Z
```

Each letter must be sourced/validated as FSL fingerspelling. Do not reuse an old ASL alphabet asset/model merely because the handshape appears similar.

For any letter whose correct FSL form is dynamic, preserve the dynamic motion rather than forcing a static pose.

The spelling scheduler should support:
- per-letter hold timing;
- smooth but legible transitions;
- repeated-letter separation;
- return-to-neutral only when appropriate;
- proper-name slot playback.

Example:
```text
ANNA -> A -> N -> [repeat separator] -> N -> A
```

Do not fingerspell every sentence. Use fingerspelling mainly for:
- personal names;
- uncommon proper nouns;
- unsupported/OOV words;
- explicit spelling requests.

If full Filipino orthography support is needed beyond A-Z, ask an FSL reviewer to define the correct treatment for characters/digraphs outside the basic A-Z set rather than inventing forms.

## Recognition-side alphabet

Keep avatar fingerspelling separate from camera recognition.

A future recognition profile may use a dedicated FSL fingerspelling classifier, but it must be trained/validated on FSL data. Do not restore the historical ASL static-alphabet model as FSL.

Suggested future profile:
`FSL_FINGERSPELL_V1`

Potential architecture:
- static/dynamic letter handling;
- hand landmarks;
- temporal handling for any dynamic letters;
- signer/device-separated validation;
- separate from Core5 isolated-word recognition until both are stable.

## Official owner-device test

The current Avatar Trial may be used for a formal internal engineering qualification on Samsung:
- action enumeration;
- playback;
- replay;
- neutral return;
- framing;
- clipping/collision;
- renderer stability;
- memory;
- action timing.

This is not equivalent to linguistic FSL certification.

## Production gate

Production LISTEN remains blocked until:
- priority candidate motions pass owner visual review;
- FSL/source review is completed;
- sentence planner uses validated FSL phrase plans;
- fingerspelling actions are validated;
- action resolver and fallback behavior are tested;
- memory/performance is acceptable on Samsung.

`listen_ready=false` remains mandatory until then.
