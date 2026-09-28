# Core5 event state-machine audit — 2026-09-28

Read-only reconstruction before Boundary V2 implementation. Scope is the debug-only `Core5DiagnosticActivity` / `Core5Recorder` path, not the production Sign page. No thresholds, model, or Android state transitions were changed in this phase.

## Exact ownership

| Concern | Current owner | Observed behavior |
|---|---|---|
| Manual `BEGIN` / attempt ID | `Core5DiagnosticActivity.begin` → `Core5Recorder.begin` | Starts an explicitly labelled attempt, clears saved samples, resets collector, schedules an 8,000-ms command timer. |
| Camera / landmarks | `Core5DiagnosticActivity.bind` → `MediaPipeLandmarkExtractor.processFrame` → `Core5Contract.sanitize` | Front camera, FIT_CENTER preview; authentic timestamped pose/L/R observations feed the recorder. |
| Legacy arming/start | `FslPractical15CompleteEventCollector.onFrame` | `IDLE` requires 3 pose-present/no-hand frames, then `PRIMING`; first pose+any-hand frame becomes `SIGN_ENTRY` / `SIGN_ACTIVE`. |
| Legacy capture/release | `FslPractical15CompleteEventCollector.collect` | Tracks each hand-present frame; only 3 consecutive pose-present/no-hand frames produce a candidate. Tracked but stationary hands remain active. |
| Legacy abort | same collector | Event age >8,000 ms, >=180 frames, 3 missing-pose frames, or <8 hand-event frames at release aborts; debug activity has a separate 8-second wall timer. |
| Manual end | `Core5DiagnosticActivity.end` → `Core5Recorder.finish("MANUAL_END")` | Finalizes whatever frames were collected; no automatic sign-end validation. `END` in non-MANUAL mode becomes `OPERATOR_CANCEL`. |
| Tensor/inference | `Core5Recorder.finish` → `Core5Contract.envelope/resample` → `Core5Runtime.infer` | Keeps observed hand envelope with 100-ms context, timestamp-linearly resamples to float32 `[48,225]`, then runs the selected five-class TFLite. Raw top-1 is retained even on rejection. |
| Acceptance | `Core5Contract.gate` | Rejects timeout/error terminations, <8 envelope frames, >8,000-ms envelope, pose/hand ratio <.65, mean full-feature motion <.02, top-1 <.95, or margin <.05. Closed-set probability is not an OOD test. |
| Reset/re-arm | `Core5Recorder.begin`; legacy collector `WAIT_FOR_RELEASE` | Each diagnostic `BEGIN` creates a fresh collector. `Core5Recorder.finish` does **not** call `markCandidateHandled`; it stops the recorder, so the legacy collector's internal post-candidate `WAIT_FOR_RELEASE` is not used to guard the next explicit attempt. There is no persistent cross-attempt motion-onset rearm. |
| Existing debug motion mode | `Core5Recorder.frame` `AUTO_MOTION` | Uses raw adjacent full-225 L2 <.08 for 900-ms hold after 1,200-ms floor; not currently a validated Boundary V2 and not production-default. Offline saved-event replay found 0/18 automatic early triggers. |

```text
MANUAL: BEGIN → collect authentic frames → END or wall timeout → envelope48 → raw inference → gate → inactive

AUTO_LEGACY: BEGIN → IDLE --3 no-hand frames→ PRIMING --hand appears→ SIGN_ACTIVE
             SIGN_ACTIVE --3 no-hand frames→ candidate → finish/replay/gate → inactive
             SIGN_ACTIVE --hand remains tracked→ capture until 8-s timeout → raw inference → reject
```

The legacy collector itself defines `CANDIDATE → WAIT_FOR_RELEASE → IDLE`, but the debug wrapper finalizes and sets `active=false` on candidate, and the next manual `BEGIN` constructs a new collector. Boundary V2 must define its own explicit rearm behavior if continuous capture is ever used; it must not presume the diagnostic wrapper currently enforces it.

## Device evidence explained

- HELLO raw labels were correct in saved manual/timeout tensors. Four AUTO_LEGACY events all released after three no-hand frames, but one was not a deliberate sign and its ID is unknown. Hand appearance is a weak start signal and the extra accepted event is an observed ghost-risk, not a scored fourth HELLO.
- NO's three confirmed automatic events were all raw NO but timed out; right-hand landmarks remained present in every frame. THANK YOU had two releases and two timeouts; the fully visible stationary-hand hold had both hands tracked in all 55 frames and timed out. UNDERSTAND's three events did lose hand landmarks for three frames and released. This is the precise legacy completion condition.
- YES has a separate classifier/domain issue: four confirmed physical-right-hand automatic events were raw HELLO and timed out. Correcting the boundary alone cannot be claimed to fix YES or non-sign rejection.
- Frozen moving negatives included five wrong accepts in eleven wave/partial events. No Boundary V2 may be called survey-ready without retesting unsupported-input rejection.

Limitations: raw JSON lacks an independent human sign-end marker, full video, or per-frame collector state log; meaningful-motion end is only a feature-derived proxy. The inferred transition table is grounded in source and observed frame presence, not a claim that a particular frame was the true linguistic boundary.
