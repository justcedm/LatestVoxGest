# External Camera Support

## A. Camera2/CameraX-exposed cameras

The experimental live-segment lane and the shared Standard CameraX bind path
discover cameras from
`ProcessCameraProvider.availableCameraInfos` and reads each corresponding
Camera2 `LENS_FACING` characteristic. Its source contract is:

- `AUTO`: prefer front, then back, then external.
- `FRONT`: require a front-facing Camera2 camera.
- `BACK`: require a back-facing Camera2 camera.
- `EXTERNAL`: require `CameraCharacteristics.LENS_FACING_EXTERNAL`.

When Camera2 exposes an external camera, it uses the same CameraX `Preview` and
`ImageAnalysis`, latest-frame backpressure, MediaPipe extraction, canonical
FullSign225 construction, and TFLite runtime as built-in cameras. External
preview defaults unmirrored. Model input is always canonical and unmirrored.

Selecting `EXTERNAL` when no such CameraInfo exists fails closed with an
explicit unavailable message. It does not silently bind the front or back
camera.

## B. USB/UVC fallback

Android devices do not universally expose attached USB webcams through
Camera2. A future direct USB/UVC capture path would require separate lifecycle,
permission, pixel-format, rotation, and backpressure integration before frames
could enter the same MediaPipe pipeline.

No third-party USB/UVC library is included in this experiment, and VoxGest does
not claim universal webcam support.

## Current Samsung evidence

The 2026-09-13 Samsung SM-A566B Camera2 inventory showed built-in front/back
cameras and no external-facing camera. Physical external-camera inference is
therefore `NOT_YET_TESTED`.

## Implementation verification

- Camera-source selection and fail-closed policy are covered by JVM unit tests.
- Preview mirroring uses CameraX `Preview.setMirrorMode` on API 33+ and a
  compatibility counter-transform only for an explicit unmirrored front
  preview on older APIs. This transform is never applied to `ImageAnalysis`.
- Camera readiness is reported only after CameraX reaches `OPEN`; asynchronous
  errors close the runtime and extractor rather than leaving a stale session.
