# Samsung HELLO qualification — 2026-09-21

Historical checkpoint; superseded by [installed anatomy correction](ANATOMY_TEMPORAL_FIX_20260922.md).
Owner subsequently confirmed BOTH slot inversions and that CASH .99757093 was an
unintended false acceptance. Pending-confirmation statements below are historical.

Status: physical startup verified; handedness mismatch found before controlled HELLO.
Installed source checkpoint: `2ecd30a128ed90024481f471fcd5c321bf39dd96`.
Branch: `recognition/fsl-dual-dataset-reset-v1`. Clean exact remote parity before work.

## Saved evidence (local only, excluded from Git)

Root: `reports/device_tests/samsung/hello_20260921/`.
Files include `adb_initial.txt`, `device_properties.txt`, `install.txt`, `launch.txt`,
`installed_apk_hash.txt`, `startup_logcat.txt`, `camera_startup.txt`,
`camera_runtime_02.txt`, `neutral_ready_log.txt`, and corresponding screenshots.
Every command has `.meta.json` with UTC timestamp, return code and duration.
Device screenshots and raw logs must not be committed.

Device: Samsung SM-A566B, Android16/API36, arm64-v8a. Serial was discovered from ADB;
initial unauthorized status resolved after the owner accepted USB authorization.
`adb install -r` returned Success. MainActivity cold launch with BOTH debug extras
returned Status: ok. Installed APK SHA256 matches the locally audited APK:
`22cea9de953da58c38ce7b20b3b17e33081e703f303af089a1a75e17d66942b1`.

## On-device startup proof

- `ACTIVE_PROFILE=FSL_PRACTICAL15_V1`.
- Feature golden parity PASS, max error0, unmirrored, no hand-slot swapping.
- TFLite golden parity PASS, expected/actual COIN, max difference1.1920929E-7.
- Model load PASS: float32[1,48,225] -> [1,15], 15 labels.
- Exact labels verified by installed APK hash identity plus runtime manifest/hash and
  ordered-label equality guards: HELLO, THANK_YOU, YES, NO, PLEASE, HOW_MUCH, CASH,
  CARD, RECEIPT, WAIT, HOW_MANY, AGAIN, PROBLEM, COIN, DISCOUNT.
- Front camera bind PASS, preview mirror=true, analysis mirror=false.
- Frozen confidence .95, margin .05, motion .02; minimum8/max8000ms;
  neutral arm3, release3 and existing presence gates unchanged.
- Live analyzer approximately8.3–10fps in saved samples; complete events resampled to48
  and TFLite invoked successfully. No exception in the inspected profile log.
- Normal/fullscreen screenshots show letterboxing rather than stretching/crop-to-fill.
  Pose alignment is visible. Initial close-framing image shows a spurious hand over
  the face; later screenshot shows hand landmarks aligned with the raised hand.
  Owner explicitly identified the raised hand in `neutral_ready_screen.png` as their
  physical LEFT hand, while HUD/overlay assigned R. This is a live anatomical-slot
  mismatch. `anatomy_check_screen.png` shows the opposite raised hand assigned L after
  requesting physical RIGHT; explicit owner confirmation of that second check is pending.
  Do not treat the earlier offline or historical anatomy PASS as current-device proof.

## Uncontrolled setup events — NOT formal HELLO/negative trials

| Event | Device time | Raw top1 | Confidence | Margin | Frames | Gate | End-to-raw ms |
|---|---|---|---|---|---|---|---|
| 1 | 20:16:41.164 | CASH | .92425674 | .877338 | 29 | REJECT: LOW_CONFIDENCE | 715 |
| 2 | 20:17:01.192 | CASH | .8532941 | .76328635 | 26 | REJECT: LOW_CONFIDENCE | 627 |
| 3 | 20:17:07.057 | CASH | .99757093 | .99637187 | 27 | ACCEPT | 719 |
| 4 | 20:18:21.048 | CASH | .9667123 | .9432572 | 9 | REJECT: LOW_HAND_PRESENCE | 678 |

Event3 has both `FSL_PRACTICAL15_EMIT label=CASH` and visible CASH accepted text in
`neutral_ready_screen.png`. Audible TTS is unconfirmed. Expected sign/action for these
setup events awaits owner clarification; do not label them as valid HELLO attempts or
a scored neutral battery. Later setup transitions are retained in `anatomy_setup_log.txt`.
Several setup events timed out at the frozen8-second limit
or were rejected incomplete. No threshold change is justified from these alone.

## Controlled protocol

First establish hands-down neutral and anatomical hand identity. Then five separately
marked HELLO attempts: neutral -> one deliberate HELLO once -> hands below view ->
wait for result/re-arm. Capture raw top5/confidence/margin, acceptance reason, timings,
text emission and owner-reported audible TTS. Target three consecutive accepted correct
HELLO outputs is a milestone, not a formal accuracy claim. On failure, brief YES/NO
controls; no retraining or gate weakening. Enable existing raw Domain-C capture only
if needed under the owner's current authorization.

No model, gate, Android source, profile mapping, UI, Avatar or live-stream default
has changed during this physical session. Only local evidence tooling/docs changed.

Next action: confirm the second raised hand was physical RIGHT. Inspect/fix only
Practical15's Tasks reported-side mapping if confirmed, retain unknown-side fail-closed
behavior, preserve protected profiles, run JVM tests, rebuild/reinstall and repeat the
two anatomical checks. Only then start five controlled HELLO trials. Current code
inherits `SWAP_REPORTED_SIDES_FOR_UNMIRRORED_INPUT` from the Standard pipeline;
do not globally change that shared policy or claim a classifier failure first.
