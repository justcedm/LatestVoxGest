# External camera feasibility — 2026-09-21

Decision: research only; USB/UVC support is not implemented or required by recognition.

Android's external-camera provider can expose UVC webcams through Camera2, but depends
on device kernel, USB-host support and OEM HAL/provider configuration. An OTG connector
alone does not establish camera availability. External hardware level is available from
API 28. This is not a guarantee of high-speed or complex sensor controls.
[AOSP external USB camera documentation](https://source.android.com/docs/core/camera/external-usb-cameras).

CameraX would be evaluated only if the camera is enumerated by that device's Camera2
provider and supports the required Preview + ImageAnalysis combination. This is a
device-specific inference, not a universal CameraX external-camera promise. Current
VoxGest selectors request FRONT or BACK and do not select an external camera.
[CameraX architecture](https://developer.android.com/media/camera/camerax/architecture).

A direct USB/UVC library is a separate path, not a CameraX setting: it needs USB-host
enumeration, explicit USB-device permission, camera permission where required, supported
formats, detach handling and its own decoder/lifetime/backpressure. USB-host capability
and user permission must be checked rather than assumed. Power budget, cables, hubs,
bandwidth, MJPEG/YUY2 formats and OEM behavior would require a device/webcam matrix.
[Android USB host guide](https://developer.android.com/develop/connectivity/usb/host).

Future narrow interface: `CameraSource -> timestamped owned upright unmirrored frame +
source/lens/rotation/mirror/crop metadata`. CameraX and a separately qualified UVC adapter
could implement it without changing FullSign225 or classifier code. Preview transform
must remain separate from ML coordinates. Negotiate input resolution, monotonic clock,
ownership, bounded latest-frame behavior and lifecycle cancellation at that boundary.

Before implementation: enumerate hardware, prove frame orientation and anatomical sides,
test unplug/replug/denied permission and thermal/latency behavior, then rerun the same
positive/negative battery. No untested external-camera compatibility is claimed.
