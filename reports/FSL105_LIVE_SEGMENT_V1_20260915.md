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

Automated qualification and the debug build pass. Physical Samsung
qualification is blocked because `adb devices -l` returned no devices in five
bounded checks during this run. No physical result, camera-orientation claim,
or latency value is inferred from automated evidence.

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
| APK SHA-256 | `df6c343fa7495b9cf8d41ecf9664fa60f12b4f59a8089288104d77cc7f31b3f6` |

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
`System.nanoTime() / 1_000_000L`. An accepted event cannot emit again until the
release/re-arm boundary completes.

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
| Forced full `:app:testDebugUnitTest` | PASS - 125 tests, 32 suites, 0 failures, 0 errors, 0 skipped |
| Forced `:app:assembleDebug` | PASS - 44/44 Gradle tasks executed; BUILD SUCCESSFUL in 40 s |
| Manifest JSON, shape, ordered-label and bilingual topology validation | PASS - 20/225/105, 105 labels, 105 unique map keys, no blank displays |
| Packaged desktop TF/TFLite parity evidence and JVM golden feature fixture | PASS; current device startup parity remains unobserved this run |
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

## Samsung representative battery

SAMSUNG_STATUS=BLOCKED_DEVICE_NOT_CONNECTED

`adb devices -l` returned an empty device list on the initial query, three
bounded rechecks, and the final post-build query. Therefore installation,
runtime-banner inspection, visual front-camera/mirroring inspection, startup
golden parity, live classifications, and physical latency measurements were not
performed.

| Canonical concept | Raw top1 | Confidence | Gate | Reason | Latency | Tracking |
| --- | --- | --- | --- | --- | --- | --- |
| HELLO | NOT_RUN | NOT_CAPTURED | NOT_RUN | DEVICE_NOT_CONNECTED | NOT_CAPTURED | NOT_CAPTURED |
| YES | NOT_RUN | NOT_CAPTURED | NOT_RUN | DEVICE_NOT_CONNECTED | NOT_CAPTURED | NOT_CAPTURED |
| NO | NOT_RUN | NOT_CAPTURED | NOT_RUN | DEVICE_NOT_CONNECTED | NOT_CAPTURED | NOT_CAPTURED |
| THANK YOU | NOT_RUN | NOT_CAPTURED | NOT_RUN | DEVICE_NOT_CONNECTED | NOT_CAPTURED | NOT_CAPTURED |
| ONE | NOT_RUN | NOT_CAPTURED | NOT_RUN | DEVICE_NOT_CONNECTED | NOT_CAPTURED | NOT_CAPTURED |
| FIVE | NOT_RUN | NOT_CAPTURED | NOT_RUN | DEVICE_NOT_CONNECTED | NOT_CAPTURED | NOT_CAPTURED |
| MILK | NOT_RUN | NOT_CAPTURED | NOT_RUN | DEVICE_NOT_CONNECTED | NOT_CAPTURED | NOT_CAPTURED |
| RICE | NOT_RUN | NOT_CAPTURED | NOT_RUN | DEVICE_NOT_CONNECTED | NOT_CAPTURED | NOT_CAPTURED |
| GOOD MORNING | NOT_RUN | NOT_CAPTURED | NOT_RUN | DEVICE_NOT_CONNECTED | NOT_CAPTURED | NOT_CAPTURED |
| UNDERSTAND | NOT_RUN | NOT_CAPTURED | NOT_RUN | DEVICE_NOT_CONNECTED | NOT_CAPTURED | NOT_CAPTURED |

Negative trials for neutral, open palm, and random motion are all
`NOT_RUN_DEVICE_NOT_CONNECTED`; semantic-token counts and latencies are
`NOT_CAPTURED`.

## Failure classification and retraining decision

No A-F runtime/model failure category can be assigned without physical input.
The current blocker is external device enumeration, before installation. In
particular, there is no category-D evidence of a wrong raw top1 under clean
segmentation and tracking. `RETRAIN_REQUIRED=NO`; no dataset, trainer,
threshold, model weight, or canonical label was changed.

## Protected boundaries

- No force push or history rewrite.
- No model or labels replacement.
- No dataset, training, calibration, or threshold tuning.
- No APK, raw recording, cache, secret, or large evidence committed.
- No protected Mapua-14 production runtime behavior changed.

## Next exact action

Reconnect and authorize Samsung `R5GYC0M1M4P` for USB debugging, verify it is
listed by `adb devices -l`, install
`android_dry_run/app/build/outputs/apk/debug/app-debug.apk`, launch the Standard
diagnostic runtime, and capture startup parity plus the manifest-derived banner
before running the ten prescribed one-sign/one-release trials followed by
neutral/open-palm/random-motion negatives. Classify any failures A-F before
considering calibration or retraining.
