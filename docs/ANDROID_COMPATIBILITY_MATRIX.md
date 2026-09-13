# Android Compatibility Matrix

## Declared support

| Contract | Value | Meaning |
|---|---:|---|
| `minSdk` | 26 | Android 8.0 is the oldest declared install target. |
| `targetSdk` | 34 | Runtime behavior is targeted to Android 14 APIs. |
| `compileSdk` | 34 | The current Android source compiles against API 34. |

`DECLARED_MIN_ANDROID=Android 8.0 / API 26`

The declaration is not a claim that every Android 8.0-or-newer device, camera,
GPU, ABI, or OEM build has been qualified.

## Physically tested

| Date | Manufacturer | Model | Android | SDK | ABI | RAM | Evidence scope |
|---|---|---|---:|---:|---|---:|---|
| 2026-09-13 | Samsung | SM-A566B | 16 | 36 | arm64-v8a | 7,595,028 kB (~7.24 GiB) | APK install/launch; Mapua-14 artifact and on-device golden parity; front CameraX + MediaPipe startup; limited Series-1 HELLO evidence. |

The Samsung result is not yet a complete 14-class qualification.

## Not yet tested

- Other Samsung models or Android OEMs.
- Android API 26 through API 35 as a compatibility matrix.
- 32-bit ARM, x86, or x86_64 devices.
- Low-memory devices and devices without a usable CameraX camera.
- A Camera2/CameraX-exposed external camera on physical hardware.
- USB/UVC devices that Android does not expose through Camera2.

