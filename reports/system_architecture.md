# VoxGest System Architecture

Figure-ready text diagram for the FSL v2 recognition architecture. Status tags
are part of the diagram and should remain visible when it is redrawn for the
paper. The assembled package is experimental, deployment-ineligible, and not
the Android default. Any Android trial must use an explicitly opt-in,
fail-closed profile.

```text
CAMERA SOURCES
|-- Android front/back camera                              [ANDROID BASELINE]
|-- USB OTG camera through a UVC adapter                   [FUTURE]
`-- IP camera through an RTSP adapter                      [FUTURE]
        |
        v
FLASHLIGHT / AMBIENT-LUX MODULE                            [FUTURE; STUB ONLY]
CameraManager torch boundary; automatic lux policy is not wired
        |
        v
MEDIAPIPE HOLISTIC TASK API / LANDMARK PIPELINE            [INTEGRATION PENDING]
33 pose landmarks + one anatomical-right hand (21 landmarks)
Android may adapt synchronized Pose and Hand Landmarker Task results
        |
        v
CANONICAL FEATURE BUILDER                                  [CONTRACT VALIDATED]
onehand162_20f_nose_mcp_z03_v2
[pose 0..32 XYZ = 99] + [selected hand 0..20 XYZ = 63]
nose-relative; wrist-to-MCP9 hand scale; all Z x 0.3; deterministic zeros
output: float32[162]
        |
        v
SIGN ACTIVITY DETECTOR                                     [EXPERIMENTAL; TARGET NOT MET]
float32[1,162] -> float32[1,1] sigmoid activity score
threshold = 0.50; validation accuracy = 0.8646; required target > 0.95
        |-- score < 0.50 --------------------------------> CLEAR BUFFER / DISCARD
        `-- score >= 0.50 -------------------------------> APPEND FRAME
                                                                |
                                                                v
SEQUENCE BUFFER                                             [INTEGRATION PENDING]
exactly 20 accepted frames: float32[20,162]
        |
        v
RD-TCN                                                      [VALIDATED ARTIFACT]
112,448 parameters; float32[1,20,162] -> float32[1,64] probabilities
selected experimental FSL v2 model; not the current Android default
        |
        v
ACCEPTANCE GATES                                           [INTEGRATION PENDING]
|-- top-1 confidence >= 0.50
|-- top-1 minus top-2 margin >= 0.10
`-- same-label cooldown: 10 frames
        |-- any gate fails ------------------------------> REJECT
        `-- all gates pass ------------------------------> EMIT CLASS TOKEN
                                                                |
                                                                v
TOKEN COMPOSER                                             [APPLICATION LAYER]
        |-- TEXT DISPLAY                                   [OUTPUT PATH]
        `-- PROFANITY FILTER ----------------------------- [FUTURE; STUB ONLY]
                    |
                    `-------------------------------------> TEXT-TO-SPEECH
                                                            [OUTPUT PATH]

Separate reverse-accessibility path (not an RD-TCN output branch):

  SPEECH INPUT -> SPEECH-TO-TEXT -> AVATAR / FINGERSPELL
                                      [APPLICATION REVERSE PATH]
```

## Diagram legend

- **ANDROID BASELINE** — capability belongs to the stable Android application;
  this sprint does not change that baseline.
- **CONTRACT VALIDATED** — the Python feature builder and its tensor contract
  are tested. An Android implementation must still pass cross-language golden
  tensor tests before deployment.
- **VALIDATED ARTIFACT** — the model artifact passed desktop shape and TFLite
  parity validation. It remains experimental and is not the Android default.
- **EXPERIMENTAL; TARGET NOT MET** — an artifact exists and passed tensor/parity
  checks, but its 86.46% validation accuracy did not meet the required >95%
  activity-detection target. It is not deployment-ready.
- **INTEGRATION PENDING** — the behavior is defined, but wiring and Android
  device validation are incomplete.
- **FUTURE; STUB ONLY** — an interface or handoff stub may exist, but the feature
  has no active production behavior.
- **APPLICATION LAYER / OUTPUT PATH / APPLICATION REVERSE PATH** — downstream
  user-interface behavior, outside the landmark and model inference pipeline.

## Figure notes

1. Android analysis input must remain unmirrored. Mirroring may be applied to
   the preview only; it must not change anatomical hand selection.
2. The displayed runtime snapshot is activity `0.50`, confidence `0.50`, margin
   `0.10`, and cooldown `10` frames. These values came from validation-only
   calibration. `android_handoff/runtime_manifest.json` remains the sole machine
   authority; Android must load it rather than copy these values into code.
3. The selected FSL v2 model covers 64 one-handed word classes. Alphabet
   recognition, the 41 deferred two-handed classes, and multi-device validation
   are outside the validated scope shown here.
4. “MediaPipe Holistic landmark pipeline” describes the logical combined
   landmark result. An Android implementation may use synchronized Pose and
   Hand Landmarker Tasks, provided it reproduces the canonical feature tensor
   exactly.
5. The profanity filter is drawn on the text-to-speech branch because it must
   receive the composed output string immediately before speech. This placement
   prevents a class label check from being mistaken for full utterance filtering.
6. `deployment_eligible = false`. Any missing file, manifest disagreement,
   tensor mismatch, artifact-hash failure, or unsupported status must disable
   the FSL v2 profile without falling back to guessed values. The stable Android
   default remains unchanged.
7. The activity evaluation used synthetic negatives and lacks real continuous
   no-sign recordings. The 10-frame cooldown also lacks continuous live-stream
   validation; both are blockers, not completed deployment evidence.
