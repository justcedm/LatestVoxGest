# Core5 deadline gate

Status: **NO-GO pending controlled Samsung qualification** (2026-10-03). Build readiness is not device or survey readiness.

| Gate | Evidence today | Status |
|---|---|---|
| Isolated controlled capture implementation | 2 s countdown, 4.5 s fixed window, no automatic hand-disappearance dependency; one 49-observation wall-view neutral smoke ended in 4,422 ms without timeout | Timing smoke pass; human sign pending |
| Feature and model parity | New SIM10 lab startup reports feature/temporal/TFLite PASS, `[1,48,225]` → `[1,5]`, hash match | SIM10 startup pass; three-model new-tensor comparison pending |
| HELLO / THANK YOU / YES / NO / UNDERSTAND | No new controlled Samsung events yet | 0/10 each; not scored |
| Three-model same-tensor comparison | Frozen models identified; no new tensors | Pending |
| No crash / camera / MediaPipe / retry | New APK not installed | Pending |
| False accepts / fail-closed behavior | Historical manual negative events: 5/16 wrong accepted | Not qualified |
| Modern SIGN result / SPEAK / RETRY | Production UI intentionally unchanged | Not integrated |

Samsung `R5GYC0M1M4P` is now authorized; only the isolated Lab APK was installed. SIM10 startup feature/temporal/TFLite parity passed and analyzer frames were observed. The owner must select `CONTROLLED_WINDOW`, confirm framing, then record exactly one cued HELLO event before larger batches. Earlier MANUAL timeouts remain unscored setup evidence.

The previous saved 69-event set remains sealed diagnostic evidence; on it, identical-tensor raw correctness was HELLO 14/14 for each model, THANK YOU 9/9 for each, YES 9/11 Baseline vs 8/11 Native48 vs 4/11 SIM10, NO 7/9 vs 9/9 vs 9/9, UNDERSTAND 8/10 for each. These are **not** new controlled-window results and cannot decide today's winner. The same historical negative set had 4/6 arbitrary waves and 1/5 partials falsely accepted by each model. No frozen classifier is selected or promoted on the basis of those figures alone.

Go requires the requested owner-device per-class target (at least 8/10 raw-correct for each where ten valid trials are feasible), stable camera/MediaPipe, verified feature/model parity, safe retry, and evidence-backed acceptance that does not present confident non-sign guesses. A failure must be tagged CAPTURE, LANDMARK, FEATURE, CLASSIFIER, or ACCEPTANCE. Until that gate is met, user-facing SIGN must stay on its existing route; there is no claim of unrestricted FSL recognition.
