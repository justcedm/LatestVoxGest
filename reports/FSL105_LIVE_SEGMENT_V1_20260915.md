# FSL105 Live-Segment Presentation Expansion

TIMESTAMP=2026-09-15T13:40:23.1842053+08:00

BRANCH=recognition/fsl105-live-segment-v1

SOURCE_BASE_BRANCH=recognition/mapua14-live-segment-v1

SOURCE_BASE_COMMIT=fbfb9bc8ad80ba2ba3120236721098081fc1aed4

IMPLEMENTATION_COMMIT=8d7c3c8b04ada9bbd36cfe727d31864a29e274fe

## Outcome

The existing Standard FSL-105 model is integrated into a completed-event live
path named `FSL105_LIVE_SEGMENT_V1`. The implementation captures one
chronological event, finalizes the complete motion envelope, resamples the
whole event to the manifest-defined 20 FullSign225 frames, performs at most one
inference, then requires neutral release before re-arming. The former active
rolling-window behavior is not used by the Standard controller.

Automated qualification, the debug build, installation, and device startup
parity pass. Samsung `R5GYC0M1M4P` (`SM-A566B`) was authorized and enumerated,
but today's physical work produced only two unscored HELLO diagnostic events,
not the prescribed fixed-threshold battery. The user subsequently deferred all
remaining physical testing for today. No class-accuracy or threshold conclusion
is inferred from these setup diagnostics.

## Frozen asset and feature contract

| Item | Verified value |
| --- | --- |
| Profile | `STANDARD_FSL_FULLSIGN225` |
| Live processing profile | `FSL105_LIVE_SEGMENT_V1` |
| Model input | float32 `[1,20,225]` |
| Model output | float32 `[1,105]` |
| Pose block | `[0,99)` |
| Anatomical left block | `[99,162)` |
| Anatomical right block | `[162,225)` |
| Inference orientation | unmirrored |
| Model SHA-256 | `42d040ec2269d437546d327decaaca32839abdd6bb63b2d400063c90630e5d13` |
| Labels SHA-256 | `bfa76d96ed10bf97f43ca80bcfcc5badd3e96df7ebe4c0654fc078552da55fb6` |
| Presentation map SHA-256 | `98b98a49a515fc9593529bac17eb9c50c0f3486b2506dd4c3d22e6b6364ee492` |
| APK SHA-256 | `9fba58ba9a4843c208a5493c3a96dadd7ba87cc38fce405fcf2bbaa99fa05dc5` |

The model and labels hashes still match their authoritative manifest/golden
metadata. They were not modified. The manifest now also declares the
presentation asset and its hash. Its parser dynamically loads the selected
model/label filenames and ordered labels and asserts the exact deployed
20/225/105 shape contract, unmirrored orientation, rejection requirement, and
absence of a negative/NOTHING class.

## Live segmentation and rejection

The active state flow is:

`IDLE -> ARMING -> CAPTURING -> FINALIZING -> INFERENCE -> WAIT_FOR_RELEASE -> IDLE`

The temporal finalizer is profile-driven. Standard uses a 20-frame output and
the manifest-compatible no-interpolation missing-hand policy; Mapua-14 retains
its protected 48-frame output and audited bounded short-gap policy. Raw pose
and hand presence is counted before any profile-compatible filling. Exact
evenly spaced linear positions cover the complete selected trajectory,
including its first and last frames.

Pre-inference rejection covers malformed/non-finite landmark dimensions,
non-chronological events, missing required pose reference, ambiguous anatomical
collision, low raw hand presence, invalid duration/frame gaps, invalid
resampled shape/profile, and capture-limit completion without a clean sign end.
The initial post-inference thresholds remain deliberately conservative:

- confidence >= 0.70
- top1-top2 margin >= 0.20
- raw any-hand presence ratio >= 0.65
- complete trajectory duration <= 8,000 ms
- median frame gap <= 350 ms
- maximum frame gap <= 700 ms

Every completed event logs its event ID, capture and resample counts, presence
ratios, top five, top1/top2 margin, gate/reason, MediaPipe/TFLite timing, event
timing, and sign-end-to-raw/accepted latency. Timing uses monotonic
`System.nanoTime() / 1_000_000L`. A device-only defect found during the first
setup events mixed CameraX frame timestamps with the process clock at
finalization. The state machine now uses one explicit process-clock domain for
event milestones and latency while retaining raw camera timestamps for frame
ordering and frame-gap evidence. A regression test uses deliberately offset
clock domains. An accepted event cannot emit again until the release/re-arm
boundary completes.

## 105-class presentation surface

The Supported Signs page now derives its canonical inventory from the selected
runtime's labels asset and shows all 105 entries as `FSL-105 MODEL VOCABULARY`
and `NOT YET DEVICE-QUALIFIED`. Canonical source quirks such as
`WEELCHAIR PERSON`, `IM FINE`, and `YOURE WELCOME` remain unchanged internally.

The authoritative bilingual asset has exactly one ordered entry per canonical
token with nonblank `english_display` and `filipino_display` fields. Load-time
hash and topology checks fail closed for a missing, duplicate, blank, reordered,
or nonmatching entry. Accepted canonical tokens can be presented in English,
Filipino, or Both and used for optional TTS without mutating recognition output.
The display values were derived from the repository's existing review-gated
`FILIPINO_PRESENTATION_ONTOLOGY.csv`. This map is explicitly marked
semantic-presentation-only and still requires Filipino-language/FSL
presentation review; it does not claim Filipino text grammar is FSL grammar.

## Automated evidence

| Check | Result |
| --- | --- |
| Focused FSL105/manifest/guide/localization/Mapua regression suites | PASS |
| Forced full `:app:testDebugUnitTest` | PASS - 126 tests, 32 suites, 0 failures, 0 errors, 0 skipped |
| Forced `:app:assembleDebug` | PASS - 44/44 Gradle tasks executed; BUILD SUCCESSFUL in 39 s |
| Manifest JSON, shape, ordered-label and bilingual topology validation | PASS - 20/225/105, 105 labels, 105 unique map keys, no blank displays |
| Packaged desktop TF/TFLite parity evidence and JVM golden feature fixture | PASS; device startup TFLite parity PASS, top1 agreement true, max probability delta `5.8619776E-14` |
| Complete event -> exact 20-frame full-duration resample | PASS |
| Anatomical left/right preservation and missing-hand zero fill | PASS |
| Missing pose, malformed dimensions, capture-limit and ambiguous identity fail closed | PASS |
| Release/re-arm and same-sign duplicate prevention | PASS |
| Shared 48-frame finalizer vs protected Mapua finalizer numeric parity | PASS |
| Protected Mapua production sources/model/labels diff | EMPTY / UNCHANGED |
| `git diff --check` | PASS; line-ending conversion warnings only |

The first sandboxed Gradle rerun could not reach the Gradle distribution due to
network sandboxing. The same requested build was rerun with approved access to
the existing Gradle cache/distribution and completed successfully; this was an
environmental launch failure, not a source/test failure.

## Samsung qualification evidence

SAMSUNG_STATUS=PARTIAL_DIAGNOSTIC_ONLY_USER_DEFERRED

ADB enumerated authorized Samsung `R5GYC0M1M4P` as `SM-A566B`. The rebuilt APK
installed and launched. On-device startup diagnostics proved
`STANDARD_FSL_FULLSIGN225`, `FSL105_LIVE_SEGMENT_V1`, input `[1,20,225]`, output
`[1,105]`, 105 ordered labels, manifest/model/label parity PASS, unmirrored
model input, resolved front camera ID 1, mirrored preview, and unmirrored
analysis. The first wide framing view showed the required upper body; the later
HELLO setup retry was visibly too close and excluded relevant body/hand motion.

These are unscored diagnostics and do not count toward the requested three
attempts per class:

| Expected | Completion | Captured/resampled | Pose/left/right/both | Raw/gate | Timing | Failure |
| --- | --- | --- | --- | --- | --- | --- |
| HELLO setup 1 | `CAPTURE_LIMIT` | 240/20 | 1.000/0.000/0.991/0.000 | no inference; `CAPTURE_LIMIT_WITHOUT_SIGN_END` | MediaPipe 110.352 ms; end-to-rejection 713 ms | A - segmentation did not find sign end |
| HELLO setup 2 | `DYNAMIC_END` | 99/20 | 1.000/0.000/0.978/0.000 | no inference; `TRAJECTORY_DURATION_EXCEEDED` | MediaPipe 106.638 ms; end-to-rejection 608 ms | A - invalid 20.903 s trajectory/gap under bad framing |

The first setup interval likely included repeated motion while the runtime was
stalled and therefore is not a valid one-sign event. The second had a 5,427 ms
maximum gap and invalid close framing. Neither reached TFLite, neither supplies
a raw top1/top5, and neither is model-quality evidence. YES, NO, THANK YOU, ONE,
FIVE, MILK, RICE, GOOD MORNING, UNDERSTAND, and all prescribed negative trials
are `DEFERRED_BY_USER_TODAY`.

## Failure classification and retraining decision

Both unscored setup failures are Category A: the first reached capture limit
without a sign end; the second finalized but represented an invalid long event
caused by framing/gap conditions. The original finalization exception was a
runtime clock-domain defect and is fixed with regression coverage. There is no
Category C or D evidence because neither event reached raw inference under clean
segmentation/tracking. `THRESHOLD_CHANGE_REQUIRED=NO` and
`RETRAIN_REQUIRED=NO`; no threshold, model weight, or canonical label changed.

## Protected boundaries

- No force push or history rewrite.
- No model or labels replacement.
- No dataset, training, calibration, or threshold tuning.
- No APK, raw recording, cache, secret, or large evidence committed.
- No protected Mapua-14 production runtime behavior changed.

## Next exact action

Preserve this exact device-tested runtime checkpoint, then perform the separate
offline multisource inventory/calibration sprint on
`recognition/multisource-calibration-v1`. Tomorrow, restore wide full-upper-body
framing and run the untouched fixed-threshold ten-class/negative Samsung battery
using exactly one sign per event. Category C/D decisions still require clean
physical events; today's setup failures must not be used to tune thresholds or
justify retraining.
