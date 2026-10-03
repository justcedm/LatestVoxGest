# Domain-C capture readiness — 2026-09-21

Status: debug opt-in instrumentation implemented in **Practical15 VIDEO only**.
No recording or physical trial was performed in this work block.

## Enable later, with consent

Install the verified debug APK. Detect the serial; substitute it for `SERIAL` below.
Do not enable on an unrelated device. The sentinel is read when the Practical15 lane starts.

```text
adb devices -l
adb -s SERIAL shell run-as com.voxgest.dryrun touch files/domain_c_capture.enabled
adb -s SERIAL shell am force-stop com.voxgest.dryrun
adb -s SERIAL shell am start -n com.voxgest.dryrun/com.voxgest.app.MainActivity --ez com.voxgest.dryrun.extra.DEVELOPER_DIAGNOSTICS true --es com.voxgest.dryrun.extra.RECOGNITION_PROFILE FSL_PRACTICAL15_V1
```

Start recognition through the existing controls. Verify model/hash/shape and anatomical
left/right with one raised hand at a time before linguistic trials. Keep the existing
0.95 confidence, 0.05 margin, 0.02 motion, minimum eight frames and eight-second timeout.

## Stored contract

Private app-internal `files/domain_c_captures/`, JSON schema
`voxgest_domain_c_tasks_raw_v1`; no camera pixels, audio, names or account identifiers.
Landmarks are still sensitive motion data. Never add captures to Git.

Each observed event records:

- Raw pre-normalization pose33x3, anatomical left21x3/right21x3; absent slots zero-filled
  and separate presence booleans, so absence is never confused with valid zeros.
- Camera source nanoseconds, Tasks milliseconds, buffer-derived upright dimensions,
  rotation degrees, lens, preview mirroring and analysis=false.
- Reported handedness, resolved anatomical slot and category score. Detector confidence
  is **null**, not fabricated: current Tasks results expose handedness score, not a
  palm/pose detector score. No pose visibility is claimed by this xyz-only capture.
- Completion reason, total observed frame count, classifier raw frame count/duration,
  and SHA-256 of the actual 48x225 row-major little-endian float32 inference input.
- `included_in_canonical` identifies contributing frames using candidate start/end and
  pose validity. Terminal neutral release frames remain in raw evidence but are excluded
  from the classifier window. Transient internal hand gaps retain the collector policy.
- Aborted timeout/tracking/incomplete events are saved with a null tensor hash.

Hook order is raw Tasks frame -> capture buffer -> unchanged feature builder/collector
-> capture snapshot -> unchanged TFLite/gate. Serialization and disk writes run on a
bounded one-thread writer, at most four queued snapshots, 200 buffered raw frames,
100 event files/session and a 100 MiB pre-write storage ceiling (one final event can
cross the ceiling). Queue/storage failures log diagnostic loss and cannot accept a sign.
The disabled path stores nothing. Existing model/gate math is untouched; opt-in buffering
has small overhead that still needs physical measurement.

Export with `adb exec-out run-as ...` using a **binary-safe host process** into an approved
private safe-C capture directory. Avoid PowerShell text redirection for binary archives.
Validate file count and schema before comparison. Disable by removing only the sentinel:

```text
adb -s SERIAL shell run-as com.voxgest.dryrun rm files/domain_c_capture.enabled
adb -s SERIAL shell am force-stop com.voxgest.dryrun
```

Existing captures are not deleted by disabling; retain or remove them explicitly with
owner agreement after analysis. App uninstall/clear-data destroys this private data.

## Next compact physical batch

After orientation/handedness proof: HOW_MANY, HOW_MUCH, CASH, three trials each.
For each: hands down -> one deliberate sign once -> hands down -> wait for result/re-arm.
Record expected class separately against event order; the recorder does not know ground
truth. Stop any attempt exceeding ten seconds, classify its capture/tracking state.
Then continue the frozen 45-positive/30-negative protocol, logging raw top1 even if
rejected. Do not fit thresholds on a partial batch. Compare Domain C against original
NPY and MP4-derived canonical trajectories without treating performed signs as validated
new linguistic training truth.
