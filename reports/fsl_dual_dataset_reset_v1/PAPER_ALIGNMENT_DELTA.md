# Paper alignment delta: practical FSL profile

STATUS=READY_FOR_MANUSCRIPT_UPDATE

This report records engineering truth as of 2026-09-20. It does not modify the
manuscript and does not convert offline evidence into a live-device claim.

## What remains aligned

- VoxGest remains an Android-based bidirectional accessibility prototype.
- The sign-to-hearing chain remains camera -> MediaPipe landmarks -> temporal
  classifier -> acceptance/rejection -> text/TTS.
- MediaPipe is the landmark extractor, not the trained FSL classifier.
- Inference remains local through TensorFlow Lite.
- Recognition remains isolated known-sign classification under a controlled
  vocabulary, not unrestricted FSL translation or FSL grammar.
- FullSign225 remains pose99 + anatomical-left63 + anatomical-right63.
- The Avatar lane remains separate from recognition and was not changed.

## What changed

The strongest defensible experimental deployment lane is no longer described by
the paper's 105-class, 20-frame Standard target. The new non-default profile is
`FSL_PRACTICAL15_V1`: a selected-vocabulary, complete-event, 48-frame
RD-TCN profile. Standard FSL-105 is preserved unchanged as a broader
historical/research and rollback lane.

Official FSL-105 v2 raw media was unavailable during this sprint and official
automated acquisition was blocked. The current profile is therefore a
published-Mapua-only interim model, not the intended final dual-source model.
No historical OneHand162 tensors, ASL, WLASL, phrase experiments, random
internet signs, or unvalidated team signs entered its training claim.

## Exact implementation contract

- Profile: `FSL_PRACTICAL15_V1` (experimental, non-default).
- Dataset: published Mapua Transactional Filipino Sign Language only.
- Vocabulary: HELLO, THANK_YOU, YES, NO, PLEASE, HOW_MUCH, CASH, CARD,
  RECEIPT, WAIT, HOW_MANY, AGAIN, PROBLEM, COIN, DISCOUNT.
- Classes: 15.
- Clips: 394 PASS-only complete trajectories; 334 development and 60 sealed.
- Split: source-clip/group isolated; zero known related-group crossings.
- Signer-independent claim: prohibited because signer identifiers are absent.
- Frame: float32 FullSign225; pose [0,99), anatomical left [99,162),
  anatomical right [162,225).
- Geometry: unmirrored input, no slot swap, pose nose-relative, independent
  wrist-to-middle-MCP hand scaling, global z x0.3, deterministic zero blocks.
- Temporal contract: complete tracked sign event with bounded context and short
  internal hand-gap interpolation, endpoint-aligned linear resampling to
  exactly 48 frames.
- Model: one RD-TCN48 architecture, 125,391 parameters.
- TensorFlow Lite: float32 [1,48,225] -> [1,15].
- Android capture: IDLE -> PRIMING -> SIGN_ACTIVE -> CANDIDATE ->
  WAIT_FOR_RELEASE -> IDLE; one complete event produces at most one token.
- Prepared rejection: pose/hand presence, event completeness, duration,
  finite-input, trajectory-motion, confidence 0.95, margin 0.05, and release
  re-arm. These remain provisional until physical negative testing.

## Evidence that can be defended

- Four-fold development OOF accuracy 0.9491 and macro-F1 0.9453.
- Exploratory sealed clip accuracy 0.9500 and macro-F1 0.9557.
- Weakest sealed class F1 0.8571 (CASH).
- Sealed confusions: CARD -> COIN, CASH -> PROBLEM, and NO -> YES, one each.
- Raw-video replay: 60/60 decoded without event failure; 57/60 top-1 correct;
  60/60 motion envelopes exactly reproduced; regenerated tensors were
  bit-identical to the frozen cache.
- TensorFlow/TFLite top-1 agreement 1.0; maximum probability difference
  4.172325134277344e-07.
- Shared Python/JVM resampling fixture maximum positional difference 0.0.
- Android JVM suite: 101 tests, zero failures; debug build successful.
- APK packaging audit: all five practical-profile assets present and
  byte-identical to their source bundle.

All accuracy figures are exploratory clip-level results. They are not
signer-independent, population-level, or Samsung live accuracy.

## Chapter 1 changes required

1. Replace any implication that the current demo physically supports all 105
   classes with a controlled 15-concept experimental profile.
2. State that Standard FSL-105 is preserved but not the currently qualified
   live profile.
3. Describe the present dataset truth as Mapua-only interim. Add FSL-105 as a
   future dual-source extension only after official raw v2 is acquired and
   re-audited.
4. Scope the outcome to isolated sign recognition for practical social and
   retail concepts. Do not claim continuous FSL translation or grammar.

## Chapter 3 changes required

1. Change temporal input from arbitrary/rolling 20 frames to one complete event
   resampled to 48 positions for this profile.
2. Add the exact FullSign225 normalization, missing-landmark, handedness, and
   unmirrored-input contract.
3. Document source-clip/group splitting, the absence of signer IDs, and the
   prohibition on signer-independent claims.
4. Document RD-TCN48 selection, development-fold selection, one sealed
   evaluation, float32 TFLite parity, Android golden parity, and raw-video
   replay as distinct gates.
5. Report raw classifier outcomes separately from gate acceptance.
6. Describe rejection as software behavior. Do not describe a universal
   `NOTHING` class because none was trained.
7. Add the required live Samsung positive and negative protocol as the final
   deployment-validity gate.

## Claims the paper must stop making

- All 105 Standard classes are physically demo-ready.
- The practical profile is dual-source before official FSL-105 raw is present.
- Clip-level random or source-clip results prove signer independence.
- High closed-set confidence proves non-sign rejection.
- MediaPipe itself was trained as the sign classifier.
- Offline desktop/TFLite parity is equivalent to live Android parity.

## Remaining limitations

- Official FSL-105 raw v2 is pending.
- Mapua signer/session identity is unavailable.
- Sealed support is small (two to six clips per class).
- The sealed raw replay is a second evaluation of the same sealed source clips,
  not a new independent test set.
- Synthetic corruption tests show confidence/margin alone are insufficient.
- Camera framing, on-device landmarks, latency, class generalization, and
  negative false accepts remain unmeasured until Samsung qualification.
