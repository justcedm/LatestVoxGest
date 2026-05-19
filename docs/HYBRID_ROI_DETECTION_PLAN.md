# Hybrid ROI Detection Plan

## Purpose

The next practical stability improvement for VoxGest FullSign225 is not to replace MediaPipe. It is to help MediaPipe receive better-framed camera input so the existing landmark pipeline produces cleaner pose and hand trajectories.

This plan keeps the current landmark-based recognition system intact:

camera frame -> MediaPipe landmarks -> FullSign225 features -> TCN/LSTM -> gates -> accepted text output

No YOLO, DETR, or custom detector training is required for the immediate next step.

## Why TCN/LSTM Still Need Stable Landmarks

The dynamic word models do not read raw pixels directly. They read sequences of landmark features:

- pose landmarks
- left-hand landmarks
- right-hand landmarks
- motion over 30 frames
- wrist path and hand-presence patterns

That means model quality depends on landmark quality. If MediaPipe loses a hand, swaps hand confidence, jitters between frames, or crops the body poorly, the TCN/LSTM receives a noisy sequence even if the sign itself was correct.

For FullSign225, this is especially important because the model expects fixed left-hand and right-hand slots. If both hands are visible and stable, the input is useful. If a hand disappears, jumps, or is clipped by the camera frame, the model may reject the sequence or classify the wrong word.

## Why Object Detection Can Help Framing

Object detection can help as a framing assistant. A detector can locate the person, upper body, or broad hand regions so the app can guide the camera view or crop the frame before landmark extraction.

Useful ROI support can help with:

- keeping the signer centered
- keeping shoulders, face, and both hands inside frame
- detecting when the user is too close to the camera
- detecting when hands leave the frame
- recommending a better distance before the sign is captured
- stabilizing the crop sent into MediaPipe

This can improve MediaPipe landmark consistency without changing the trained gesture model.

## Why Object Detection Does Not Replace Finger Landmarks

YOLO or DETR can find bounding boxes, but bounding boxes are not enough for sign recognition in the current VoxGest pipeline.

The dynamic model needs detailed landmark geometry:

- wrist position
- finger joint positions
- left/right hand separation
- hand shape over time
- pose-relative movement
- frame-to-frame trajectory

A person or hand bounding box says where the hand is, but not whether the fingers form WATER, THANKYOU, NO, NAME, or PLEASE. For the current TCN/LSTM models, object detection should support MediaPipe, not replace it.

## Why YOLO/DETR Training Is Not The Immediate Move

Training YOLO or DETR now would add a new dataset requirement and a new failure surface before the current live pipeline is stable.

Custom detector training would require:

- bounding box annotations for person, upper body, and hands
- enough camera/background variation
- validation of detector accuracy
- runtime integration
- detector/MediaPipe crop alignment testing
- extra performance profiling

The current urgent issue is live landmark stability, not lack of a detector model. The safer move is to first improve ROI and guidance using MediaPipe outputs already available at runtime.

## Recommended Implementation Path

### Phase 1: MediaPipe-Only ROI And Quality Fixes

Use current MediaPipe pose and hand landmarks to guide framing and quality.

Planned behavior:

- keep FullSign225 hand overlap allowed as a warning, not a rejection
- reject only truly bad sequences such as missing pose, low hand presence, severe jumps, or corrupt windows
- add overlay guidance before capture
- show simple prompts such as:
  - "Move back"
  - "Keep both hands visible"
  - "Hands too close to camera"
  - "Center your upper body"
  - "Ready. Start signing."
- compute rough upper-body bounds from pose landmarks
- compute hand visibility and edge-clipping checks from hand landmarks
- do not add detector dependencies yet

This phase is the best immediate match for the current project because it improves live testing without changing models or training data.

### Phase 2: Optional Pretrained Detector For ROI Crop

If MediaPipe-only framing is still unstable, add an optional pretrained detector.

Rules:

- use detector only to guide crop/framing
- do not replace MediaPipe landmarks
- do not change the TCN/LSTM input contract
- keep the detector optional and disabled by default
- support fallback to MediaPipe-only mode

Possible detector use:

- find person box
- create upper-body crop
- keep hands inside a padded crop
- stabilize the crop across frames
- display framing prompts when the body is too large, too small, or off-center

The detector should not decide the sign label.

### Phase 3: Train YOLO/DETR Only If Needed

Train a detector only if there is enough annotated data and enough time to validate it.

Requirements before training:

- bounding box annotations for person/upper body/hands
- consistent label schema
- train/validation split by signer or recording session
- detector accuracy report
- latency report on the target laptop/phone
- proof that detector-assisted crops improve MediaPipe tracking

If those requirements are not ready, custom YOLO/DETR training should wait.

## Lightweight Code TODO List

### Person / Upper-Body Crop Helper

- Add a helper that estimates an upper-body box from MediaPipe pose landmarks.
- Use shoulders, face/nose, elbows, and wrists when visible.
- Add padding around the box.
- Smooth the box over time to avoid crop jitter.
- Keep the helper optional and disabled by default until tested.

Suggested file:

- `scripts_ml/roi_guidance.py`

Suggested functions:

- `estimate_upper_body_roi(results, frame_shape)`
- `smooth_roi(previous_roi, current_roi)`
- `roi_to_prompt(roi, frame_shape)`

### Hand Visibility Guidance

- Count visible left/right hand frames over a short window.
- Detect hand clipping near frame edges.
- Detect when only one hand is visible in FullSign225 mode.
- Prefer two hands for FullSign225, but do not require perfect two-hand detection every frame.
- Report guidance without blocking valid close-hand signs.

Suggested functions:

- `hand_visibility_metrics(frame_records)`
- `hands_near_frame_edge(results, frame_shape)`
- `fullsign_hand_prompt(metrics)`

### Framing Prompts

Add lightweight prompt rules:

- "Move back" when upper body fills too much of the frame.
- "Move closer" when the signer is too small.
- "Center your upper body" when shoulders are off-center.
- "Keep hands visible" when hand presence is low.
- "Hands near edge" when landmarks are clipped.
- "Ready. Start signing." when pose and hand presence are stable.

These prompts should be debug/live-test overlays first, not Android UI changes.

### No Detector Dependency Yet

- Do not add YOLO, DETR, Ultralytics, PyTorch detector weights, or new model files in Phase 1.
- Do not train a detector during this sprint.
- Do not change demo10 or onehand162.
- Do not change the FullSign225 TCN model files.
- Keep ROI support as a camera guidance layer around MediaPipe.

## Success Criteria

Phase 1 is successful if live testing shows:

- fewer `BAD_SEQUENCE` rejections
- fewer `LOW_HAND_PRESENCE` rejections
- better capture consistency for two-hand signs
- close/touching hands shown as warnings instead of blocked recognition
- clearer operator prompts during live tests

Only after Phase 1 results are measured should the project consider a pretrained detector-assisted crop.
