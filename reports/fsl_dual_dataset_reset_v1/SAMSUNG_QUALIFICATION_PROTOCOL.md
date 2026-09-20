# Samsung qualification protocol: FSL_PRACTICAL15_V1

STATUS=READY_TO_EXECUTE_WHEN_DEVICE_RETURNS

Target device: Samsung serial `R5GYC0M1M4P` if unchanged. This protocol makes
no threshold or model changes during a battery.

## 1. Connect, install, and launch

From the repository root:

```powershell
adb kill-server
adb start-server
adb devices -l
adb -s R5GYC0M1M4P install -r android_dry_run/app/build/outputs/apk/debug/app-debug.apk
adb -s R5GYC0M1M4P logcat -c
adb -s R5GYC0M1M4P shell am start -S -n com.voxgest.dryrun/com.voxgest.app.MainActivity --ez com.voxgest.dryrun.extra.DEVELOPER_DIAGNOSTICS true --es com.voxgest.dryrun.extra.RECOGNITION_PROFILE FSL_PRACTICAL15_V1
adb -s R5GYC0M1M4P logcat -v threadtime VoxGestPractical15:I VoxGestFullSign225:I '*:S'
```

If the serial is `unauthorized`, unlock the phone, accept the USB debugging
fingerprint, keep USB mode on data transfer, and rerun `adb devices -l`. If it
is `offline`, reconnect the cable, try another known data-capable port/cable,
then repeat the server restart above. Do not declare the device unavailable
before these safe recovery steps.

Keep log files, screenshots, recordings, and APKs outside Git.

## 2. Startup evidence gate

Do not sign until logcat proves all of the following:

- `ACTIVE_PROFILE=FSL_PRACTICAL15_V1`;
- `FSL_PRACTICAL15_TFLITE_PARITY PASS`;
- `FSL_PRACTICAL15_MODEL_LOAD PASS`;
- input `[1,48,225]`, output `[1,15]`, 15 labels;
- `fullsign225_frame_v1_complete_trajectory_v1`;
- `complete_event_resample48`;
- `model_input=unmirrored`;
- `live_approved=false` and `android_default_changed=false`;
- actual camera lens;
- preview mirroring state and `analysis_mirrored=false`.

Visually verify that the front preview behaves like a mirror while anatomical
left/right overlays follow the signer correctly. If the back camera is used,
the preview must not be mirrored. Stop on a parity, hash, shape, label-order,
camera, or handedness failure.

## 3. One-event signing rule

Every attempt is:

`hands-down neutral -> sign once -> hands-down neutral -> wait for result`

Do not repeat a sign inside one event. Wait for
`state=PRIMING reason=NEUTRAL_ARMED` before beginning. After inference, wait
for `WAIT_FOR_RELEASE -> IDLE -> PRIMING` before the next attempt. If no event
completes within 10 seconds, stop the attempt and diagnose state transitions.

Record for every event:

- expected concept and attempt number;
- event number and completion/rejection reason;
- raw frame count and exact 48-frame resample marker;
- pose, left-hand, right-hand, and trajectory-motion evidence;
- top five, raw top-1, probability, and top1-top2 margin;
- accepted/rejected and reason;
- MediaPipe/TFLite latency;
- estimated sign-end-to-raw and sign-end-to-accepted latency;
- framing, tracking, identity, or camera failure notes.

Never hide a wrong raw top-1 behind rejection.

## 4. Positive batteries

Run three initial attempts per class in this exact order:

1. HOW_MANY
2. HOW_MUCH
3. CASH
4. CARD
5. COIN
6. NO
7. YES
8. HELLO
9. THANK_YOU
10. PLEASE
11. RECEIPT
12. WAIT
13. AGAIN
14. PROBLEM
15. DISCOUNT

This front-loads the known confusion/watch groups. If the runtime behaves
normally, complete all 45 trials without threshold changes. Then run a final
five attempts per class (75 trials) after any code fix has been frozen and
rebuilt; never mix pre-fix and post-fix attempts in one metric.

Preferred class result: 5/5 raw top-1 correct and zero wrong accepted.
Minimum demo-candidate result: at least 4/5 raw top-1 correct and zero wrong
accepted. Anything weaker stays experimental.

## 5. Negative battery

Run five attempts each (30 total), with the same neutral/re-arm discipline:

1. neutral/no sign;
2. open palm held idle;
3. random waving;
4. partial sign stopped early;
5. hand entering then leaving without a sign;
6. body movement with no deliberate sign.

Record every inference and every false accept. The required outcome is zero
wrong accepted events. Score-threshold success on synthetic data is not a
substitute for this battery.

## 6. Frozen decision rules

During the first complete battery keep:

- confidence 0.95;
- margin 0.05;
- minimum raw event frames 8;
- maximum event duration 8000 ms;
- minimum trajectory-motion mean L2 0.02;
- the current pose/hand presence checks;
- the current release/re-arm rule.

Classify failures as:

- A: event never finalizes;
- B: tracking/landmark failure prevents inference;
- C: raw top-1 correct but gate rejects;
- D: raw top-1 wrong with clean event/tracking;
- E: correct output but excessive latency;
- F: repeatable class-specific failure.

Recommend confidence/margin changes only after a complete fixed battery and
only for repeated Category C. Recommend model/data work only for repeated
Category D or F with clean event/tracking. Category A/B must be fixed in capture
or landmarks first.

## 7. Required summary

Report raw correct rate, accepted correct rate, wrong accepted rate, rejected
correct raw predictions, no-inference rate, negative false-accept rate, median
and p95 result latency, per-class results, and A-F counts. The profile cannot be
called live-ready until startup parity, positive trials, and negative trials all
have recorded evidence.

