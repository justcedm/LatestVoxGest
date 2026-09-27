# VoxGest 5PM–8PM Recognition Sprint Plan — 2026-09-27

## Time-box and objective

Working window: 2026-09-27, approximately 17:21–20:00 Asia/Manila.

Top priority: obtain evidence that the current five FSL Core5 concepts can be recognized on the Samsung device under controlled Android conditions:

1. HELLO
2. THANK YOU
3. YES
4. NO
5. UNDERSTAND

Do not broaden to 10–20 words during this sprint until the Core5 battery is complete and the failure stage is understood.

---

## Priority order

### P0 — Preserve known-good recognition evidence

HELLO already has strong device evidence:

- five timely manually ended Samsung events: 5/5 raw HELLO and 5/5 accepted HELLO;
- four additional timeout events also had raw top-1 HELLO;
- all nine saved Samsung HELLO tensors replay as HELLO under Baseline, Native48 and SIM10;
- TFLite parity is not the demonstrated failure.

Therefore do not retrain HELLO, relabel HELLO, mix ASL data, or change the classifier because of HELLO timeout behavior.

### P1 — Qualify the remaining four Core5 words

Run manual-boundary device trials first:

- THANK YOU ×5
- YES ×5
- NO ×5
- UNDERSTAND ×5

For every trial preserve:

- intended label;
- raw top-1;
- full probabilities;
- gate result;
- end reason;
- duration;
- MediaPipe effective rate;
- pose/L/R presence;
- final 48×225 tensor hash;
- Android/Desktop replay result.

Raw classification and accepted output must be reported separately.

### P2 — Identify failure ownership

If a clean manual event repeatedly has the wrong raw prediction, investigate that class/model/domain.

If raw prediction is correct but the event is rejected, investigate event segmentation/rejection rather than retraining.

If landmark coverage or anatomy slots are poor, investigate capture/tracking before the model.

### P3 — Negative / non-sign battery

After the four-class manual battery:

- neutral/no intentional sign ×5;
- arbitrary non-FSL hand motion ×5;
- incomplete/aborted supported sign ×5 where cleanly collectable.

The classifier is closed-set; raw softmax will always favor some class. Rejection must be evaluated independently.

### P4 — Automatic event-boundary test

Only after manual and negative evidence is saved, test the unchanged automatic event collector.

Do not change event-boundary logic during the measurement batch.

Measure whether timeout is caused by persistent hand visibility, lack of low-motion termination, or another state-machine condition.

---

## Sign-page UI follow-up

Recognition correctness remains higher priority than UI changes.

After the five-word qualification checkpoint, implement only safe UI improvements.

### Theme modes

Add:

- Default — current visual design and current palette;
- Dark — dark surface variant;
- Light — accessible light surface variant.

Default must remain the current UI appearance.

Theme selection should affect presentation only and must not alter camera, MediaPipe, TFLite, recognition state, or Avatar behavior.

### Landscape Sign mode

Allow the Sign page to rotate to landscape while preserving:

- camera viewport as the dominant area;
- one Start/Stop control;
- recognized-sign status;
- message output;
- camera switch/fullscreen;
- landmark overlay toggle/state;
- safe touch areas and system insets.

Landscape should use a two-zone layout where appropriate:

camera/overlay on the larger side;
recognition/message/actions on the smaller side.

No recognition logic may depend on orientation.

---

## Five recommended additions

1. **Motion-settle event ending**
   - End a sign after timestamp-aware low-motion dwell rather than requiring the hand to disappear.
   - Must be developed behind a debug profile and validated against saved events first.

2. **Capture-quality gate**
   - Before accepting an event, verify sufficient pose/hand observation coverage, stable anatomy slots, and useful framing.
   - User-facing feedback may say “Move back”, “Keep hand visible”, or “Hold sign clearly”.
   - Do not silently repair or swap anatomical slots.

3. **Timestamped pre-roll/post-roll buffer**
   - Retain a short ring buffer before event start and a short controlled tail after motion settles.
   - Helps avoid cutting off preparation/recovery frames.
   - Preserve the 48×225 classifier contract.

4. **Negative/OOD rejection telemetry**
   - Log margin, confidence, motion amount, and event quality for unsupported input.
   - Keep rejection independent from raw class top-1.
   - Do not tune thresholds from one user session without evidence.

5. **60 Hz visual skeleton layer**
   - Render/interpolate the latest real landmarks at display cadence for a smooth UX.
   - Presentation only.
   - Never feed interpolated UI landmarks into the classifier unless a separate experiment validates that change.

---

## GitHub documentation policy

Every major change must record:

- branch/worktree;
- HEAD/commit;
- files changed;
- model/asset hashes where applicable;
- build/tests;
- physical Samsung result;
- evidence paths;
- rollback;
- known limitations;
- next exact action.

Recognition, UI, and Avatar work must remain separated until individually qualified.

---

## Sprint completion definition

Recognition sprint is considered successful if, before the time-box ends:

- HELLO remains qualified;
- THANK YOU, YES, NO and UNDERSTAND each have controlled Samsung evidence;
- raw prediction and gate acceptance are separated;
- any failing word has an identified failure stage;
- no blind retraining occurred;
- negative/OOD battery has started or completed;
- all results are documented.

UI theme and landscape work is secondary and may proceed only after the recognition checkpoint is preserved.
