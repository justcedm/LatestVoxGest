# VoxGest FSL v2 Android Integration Guide

This directory is a complete experimental handoff package, not a change to the
stable Android baseline. Its authoritative status is
`experimental_not_android_default`, and `deployment_eligible` is `false`.
Integration must therefore use an explicit developer opt-in, fail-closed
profile. Do not switch the application default until startup validation succeeds,
Python-to-Android golden tensors match, the activity detector is improved with
real no-sign data, and device acceptance tests pass.

## Section 1 — Model Files

The following handoff filenames are confirmed present:

| File | Purpose | Required startup validation |
|---|---|---|
| `voxgest_fsl_rdtcn_v2_float16.tflite` | Selected 64-class RD-TCN word classifier. Float16 weights reduce storage size; its runtime input and output are float32. | Input `[1,20,162]`, output `[1,64]`, both float32; SHA-256 must match `artifact_sha256.rdtcn_float16`. |
| `voxgest_activity_detector_v1.tflite` | Experimental binary single-frame sign-activity detector. It is intended to gate frames before buffering, but its validation target was not met. | Input `[1,162]`, output `[1,1]`, both float32; SHA-256 must match `artifact_sha256.activity_detector`. |
| `class_labels_fsl_v2.json` | Android-safe label-token to RD-TCN output-index mapping. | Exactly 64 unique labels and exactly the integer indices `0..63`, with no gaps or duplicates. |
| `runtime_manifest.json` | Authoritative runtime contract: versions, shapes, selected hand, activity gate, confidence gate, margin gate, cooldown, status, and deployment blockers. | Must be an exact copy of `model/runtime_manifest.json`; reject missing, unknown, or contradictory contract values. |
| `ANDROID_INTEGRATION_GUIDE.md` | This integration and defense handoff. | Treat it as explanatory text; the JSON manifest remains the machine-readable authority. |

The confirmed runtime snapshot is:

| Manifest field | Calibrated value |
|---|---:|
| `activity_detector_threshold` | `0.50` |
| `confidence_threshold` | `0.50` |
| `margin_threshold` | `0.10` |
| `cooldown_frames` | `10` |

These values are recorded here for review, but must not be duplicated as Kotlin
constants or Android resources. Load them from
`android_handoff/runtime_manifest.json`. If the manifest is missing, reports an
unsupported status, fails a hash/shape/dtype check, or contradicts the labels,
keep the FSL v2 profile disabled and show a diagnostic instead of using a
fallback.

The copied manifest is byte-identical to `model/runtime_manifest.json`, and the
two copied model SHA-256 values match its recorded artifact hashes. This proves
package integrity only; it does not make the package deployment-eligible.

## Section 2 — Preprocessing Contract

Android must reproduce feature contract
`onehand162_20f_nose_mcp_z03_v2` exactly:

1. Obtain MediaPipe Holistic-equivalent results for the same unmirrored analysis
   frame. A mirrored preview is allowed, but model input must remain unmirrored.
2. Extract pose landmarks `0..32` in MediaPipe order. Preserve each point as
   `(x,y,z)`, producing `33 × 3 = 99` float values.
3. Extract anatomical-right hand landmarks `0..20` in MediaPipe order. Preserve
   each point as `(x,y,z)`, producing `21 × 3 = 63` float values. Do not fall
   back to the left hand. If Android uses separate Pose and Hand Landmarker
   Tasks, its adapter must prove that selection is equivalent to Holistic's
   fixed right-hand slot.
4. Copy pose landmark `0` (the nose) as the reference vector, then subtract that
   vector from every pose point.
5. Explicitly set normalized pose landmark `0` to `[0,0,0]`.
6. Subtract the original nose reference from every selected-hand point.
7. Calculate the Euclidean distance between hand landmark `0` (wrist) and hand
   landmark `9` (middle-finger MCP) in XYZ. Divide every coordinate in the hand
   block by that distance only when the distance is strictly greater than
   `0.001`. Leave the nose-relative hand block unscaled otherwise.
8. Multiply the Z coordinate of every pose and hand landmark by `0.3`. X and Y
   remain unchanged.
9. If the selected hand is missing or invalid, use exactly 63 float32 zeros for
   `[99:162]`; never shift another hand into this slot.
10. If pose is missing or invalid, use exactly 162 float32 zeros for the entire
    frame because no nose reference is available.
11. Concatenate in fixed order: `[pose(99), selected_hand(63)]`. The resulting
    frame is a finite float32 array with shape `[162]`; pose occupies `[0:99]`
    and hand occupies `[99:162]`.
12. Send the single `[1,162]` frame to the activity detector. Clear the partial
    sequence buffer when its score is below the manifest value `0.50`; append
    the frame only when its score is at least `0.50`. Buffer exactly 20
    consecutive accepted frames in chronological order to produce `[20,162]`.
13. Feed the contiguous float32 tensor `[1,20,162]` to the RD-TCN. Do not
    transpose, flatten, quantize, or reorder it.

Before camera integration, run golden fixtures containing a normal frame, a
missing-hand frame, a missing-pose frame, and a wrist-to-MCP9 distance at each
side of the scale boundary. Compare all 162 Android floats with the canonical
Python builder. Handedness must also be tested with both hands visible.

## Section 3 — Two-Stage Pipeline

Use separate TFLite interpreters for the activity detector and RD-TCN. The
current values shown below come from `runtime_manifest.json`; the application
must still load them from that file rather than hardcoding them.

```text
canonical float32[162] frame
        |
        v
activity detector, input float32[1,162]
        |
        +-- score < 0.50 -----------------------------> clear buffer; do not classify
        |
        +-- score >= 0.50 ----------------------------> append to frame buffer
                                                           |
                                                           +-- fewer than 20 -> wait
                                                           |
                                                           +-- exactly 20 ----> RD-TCN
                                                                                |
                                                                                v
                                                                     acceptance gates
```

Required behavior:

1. Validate both interpreters and the manifest once at initialization. A shape,
   dtype, class-count, feature-version, model-version, artifact-hash, label, or
   status mismatch disables the new profile. Because `deployment_eligible` is
   `false`, only an explicit developer/experimental toggle may enable it.
2. Canonicalize each camera frame once. Give the exact same 162 values to Stage
   1 and, when accepted, to the Stage 2 buffer.
3. Compare the detector score with `activity_detector_threshold = 0.50`. A
   score below `0.50` clears the buffer, is not appended, and never invokes the
   RD-TCN. Clearing the buffer preserves temporal contiguity.
4. Preserve chronological order and cap the buffer at the manifest's sequence
   length. Never combine tensors from different preprocessing contracts.
5. Run the RD-TCN only with a complete buffer, then pass its output to Section
   4. Buffer advance/reset semantics must match `scripts_ml/live_pipeline_v2.py`;
   do not create an independent Android-only policy.
6. Perform inference off the UI thread. Serialize access to each TFLite
   interpreter and close both interpreters with the owning lifecycle.

The activity detector replaces the need to treat inactivity as one of the 64
word outputs. It does not add an NSAC index to the RD-TCN output. Its held-out,
signer-grouped validation accuracy is `0.8646` (86.46%), below the required
`>0.95` target. The negatives are synthetic and sequence-boundary frames are a
noisy proxy, not verified no-sign observations. Consequently, Stage 1 is an
experimental evaluation component and must not be described as production-ready.

## Section 4 — Output Handling

The RD-TCN returns float32 probabilities with shape `[1,64]`.

1. Validate that the output contains exactly 64 finite values.
2. Find `top1Index`, `top1Probability`, and the second-highest probability.
3. Calculate `margin = top1Probability - top2Probability`.
4. Accept only if `top1Probability >= 0.50`, `margin >= 0.10`, and the
   manifest-defined 10-frame cooldown has been satisfied. Reject without
   emitting a token when any gate fails. These are validation-only calibrated
   values from the manifest, not general-purpose constants.
5. Read `class_labels_fsl_v2.json` as a label-to-index mapping. At startup,
   validate it and construct an inverse `indexToLabel` array. Resolve
   `indexToLabel[top1Index]` only after all acceptance gates pass.
6. Emit one accepted label to the token composer, then update cooldown state by
   predicted class. The reference rejects the same label when
   `currentFrame - lastEmittedFrame <= 10`; it becomes eligible when the
   difference is greater than 10 frames. Do not substitute a wall-clock or
   global-label cooldown without recalibration.

Use inclusive “meets threshold” comparisons consistently with the calibration
implementation. Do not round probabilities before gating, and do not apply a
softmax a second time if the model output is already probabilistic.

`class_labels_fsl_v2.json` uses Android-safe tokens such as `GOOD_MORNING`,
whereas `runtime_manifest.json.class_order` retains human-readable strings such
as `GOOD MORNING`. Validate the index relationship after applying the handoff
normalization (uppercase, remove apostrophes, replace each non-alphanumeric run
with one underscore, and trim edge underscores). Use the Android-safe key as an
internal token; use a deliberately formatted human label for display and TTS.

Expected label-file validation logic:

```kotlin
require(labelToIndex.size == manifest.numClasses)
require(labelToIndex.values.toSet() == (0 until manifest.numClasses).toSet())

val indexToLabel = Array(manifest.numClasses) { "" }
labelToIndex.forEach { (label, index) -> indexToLabel[index] = label }
```

## Section 5 — Three Pending Features (Stubs)

These modules are deliberately handoff-only stubs. They are not wired into
`android_dry_run` and must not be reported as implemented application behavior.

### Flashlight module

`stubs/FlashlightModuleStub.kt` provides the narrow manual torch boundary using
`Android CameraManager.setTorchMode()`. A later implementation may observe the
ambient light sensor, apply hysteresis, verify that the selected camera has a
flash unit, and request torch changes through this boundary. It must handle
camera-access errors and lifecycle shutdown safely.

### Profanity filter

`stubs/ProfanityFilterStub.kt` defines the text-sanitization boundary. Apply the
real filter to the final TTS output string immediately before speaking. The
included pass-through implementation is explicitly a placeholder and provides
no filtering; it must not be described as profanity protection.

### External camera

`stubs/ExternalCameraSourceStub.kt` defines an adapter boundary for future USB
OTG cameras. CameraX can consume supported camera providers, while typical USB
UVC devices require a compatible UVC library or provider bridge. The adapter
must deliver unmirrored frames with timestamps into the same canonical feature
pipeline. IP/RTSP support should use a separate adapter with the same frame
contract.

Implement these boundaries now only as integration seams; wire and validate
behavior later. No stub should silently pretend a future capability is active.

## Section 6 — Known Limitations for Defense

- The selected model recognizes 64 FSL word classes only. Alphabet support is
  pending the separately planned recordings.
- An NSAC training class is not present in the 64-class RD-TCN. The binary sign
  activity detector is the current two-stage substitute.
- The 41 identified two-handed FSL-105 classes are deferred to future work and
  require a separately versioned feature architecture.
- Multi-device validation remains incomplete. Desktop parity does not replace
  testing on the target Android device, a second Android device, and webcam
  capture conditions.
- Activity-detector validation accuracy is 86.46%, so the required >95% target
  was not met. The evaluation uses synthetic negatives because real continuous
  no-sign/activity recordings are unavailable.
- The calibrated 10-frame cooldown has not been validated on a continuous live
  stream; its validation-window simulation cannot establish live duplicate
  behavior.

The complete package is `experimental_not_android_default` and explicitly
deployment-ineligible. It must remain opt-in and fail-closed, and must not
replace the stable Android default until artifact integrity, golden feature
parity, real-activity validation, measured gate behavior, latency, lifecycle,
continuous-stream, and multi-device tests all pass.
