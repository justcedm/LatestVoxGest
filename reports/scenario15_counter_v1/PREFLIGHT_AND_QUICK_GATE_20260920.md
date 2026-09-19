# Scenario 15 preflight and early physical gate

## Checkpoint status

- Branch: recognition/scenario15-counter-v1
- Starting local/remote HEAD: d4dc2e1ee5cb6a11768e3164a14d96d04ad4c27e
- Required commit is the remote branch HEAD and an ancestor of the checked-out branch.
- Samsung: SM-A566B, serial R5GYC0M1M4P, ADB state device.
- USB recovery: the first observation was unauthorized; a normal ADB server
  restart and user RSA authorization recovered it without changing the device.
- Android tests and debug APK assembly: PASS, 44 tasks.
- Installed baseline APK SHA-256:
  e9337bcab06d5c144cfca07f1ed0929bea7cbeead51408a051542db36ceb1e63.

## Untouched Standard startup evidence

The already-built baseline was installed before adding the Scenario 15 harness.
Samsung startup logging proved:

- ANDROID_FEATURE_PARITY_FULLSIGN225 PASS
- ANDROID_TFLITE_PARITY_FULLSIGN225 PASS
- input [1,20,225]
- output [1,105]
- 105 labels
- front camera selected
- preview mirrored
- analysis/model input unmirrored
- protected Standard model SHA-256:
  42d040ec2269d437546d327decaaca32839abdd6bb63b2d400063c90630e5d13
- protected Standard labels SHA-256:
  bfa76d96ed10bf97f43ca80bcfcc5badd3e96df7ebe4c0654fc078552da55fb6

This is startup/contract evidence only. It is not a MILK/RICE recognition pass.

## Existing-asset quick gate

A debug-only, queued physical harness was added for the mandatory early gate:

1. WHAT ×3
2. YOUR ×3
3. NAME ×3
4. MY ×3
5. MILK ×3
6. RICE ×3

The phrase stage uses the preserved fullsign225_phrase_v1 artifact at
[1,30,225] -> [1,10] with its legacy mirrored-analysis contract. Only the
four required name-flow labels are exposed. The product stage uses the
untouched Standard FSL-105 runtime and exposes only MILK/RICE. Every trial logs
raw top results, confidence/margin, landmark presence, latency, and the gate
decision to app-external storage outside Git.

The harness compiles, installs, and launches, but no trial is recorded yet.
The device is dozing behind the Android lock shade and must be manually
unlocked before camera evidence is valid. Physical status is therefore
WAITING_FOR_UNLOCK, not PASS or FAIL.

## Mapua9 reuse checkpoint

The preserved canonical feature cache was filtered to exactly the authorized
nine labels. All 219 selected archives were hash checked and opened:

| Label | Clips |
|---|---:|
| HELLO | 19 |
| YES | 26 |
| NO | 33 |
| THANK_YOU | 24 |
| PLEASE | 17 |
| HOW_MUCH | 12 |
| CASH | 26 |
| CARD | 34 |
| RECEIPT | 28 |

Evidence:

- 185 development clips and 34 sealed-test clips.
- Four development folds: 45, 47, 46, and 47 clips.
- Every archive contains a finite float32 [48,225] sequence plus its complete
  trajectory and 20/32-frame resamples.
- Feature contract:
  fullsign225_frame_v1_complete_trajectory_v1, canonical unmirrored,
  MediaPipe 0.10.9, boundary 3, short-gap interpolation maximum 3.
- Complete-trajectory range: 7–75 frames.
- Group partition/fold crossings: 0.
- Frozen split SHA-256:
  883974a7086eb43a9bb1da89e845dd4b5f6a4ab1c5d68f6cdc2b7d3d2a7ae55b.
- Training is constrained to exactly one candidate: RD-TCN48.
- Training has not started because the required early physical gate is still
  waiting for manual unlock.

## Preserved boundaries

- Standard FSL-105 model and labels: unchanged.
- Mapua14 rollback model and labels: unchanged.
- Avatar: unchanged.
- Listen: unchanged.
- Production routing/UI: unchanged.
- No APK, device log, recording, raw dataset, feature cache, or private path is
  included in this checkpoint.

## Next exact action

Unlock the Samsung and keep the Scenario 15 quick gate foregrounded. Then tap
ARM, perform the displayed sign once, return to neutral, and wait for the next
ARM action through the 18 queued attempts. Pull and analyze the external JSON
before beginning the single Mapua9 RD-TCN48 training run.
