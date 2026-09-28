# Core5 unchanged automatic-boundary forensics — Samsung, 2026-09-28

Scope: debug-only `FSL_CORE5_SIM10FPS_V1` on Samsung SM-A566B; original `AUTO_LEGACY` collector, model, gates, thresholds, feature construction, and production route unchanged. This is a controlled operator-intent device experiment, not a population accuracy claim. Full per-event raw frames/timestamps/landmarks/tensors/probabilities and replay evidence remain private under `D:/VoxGest/evidence/fsl_core5_rebase_v1/`; the per-event matrix is `core5_samsung_event_matrix_20260927.json` and `.csv` there.

| Expected class | Saved automatic events | Raw top-1 | Released/accepted | Timeout | Intent caveat |
|---|---:|---|---:|---:|---|
| HELLO | 4 | HELLO 4 | 4 | 0 | Only three were deliberate HELLO signs; one extra accepted event cannot be identified by ID. Do not call this 4/4 correct. |
| THANK YOU | 4 | THANK YOU 4 | 2 | 2 | Two normal signs plus two explicit hand-hold controls; all four were complete signs. |
| NO | 3 | NO 3 | 0 | 3 | Three confirmed right-hand signs. |
| YES | 4 | HELLO 4 | 0 | 4 | Four confirmed right-hand YES signs; wrong raw output on these long automatic tensors. |
| UNDERSTAND | 3 | UNDERSTAND 3 | 3 | 0 | Three confirmed right-hand signs. |

Every one of these 18 events with a tensor passed SHA256 verification, timestamped-landmark tensor rebuild, and desktop-vs-Android SIM10 TFLite probability parity (maximum difference below `1e-5`). The five neutral and eleven moving negative events were measured separately under MANUAL mode; they are not mixed into the automatic counts. The frozen negative battery already falsely accepted 5/16 non-sign events, so **Core5 is not safe for a user-facing survey profile** regardless of boundary repair.

## Release/timeout mechanism demonstrated

Source `FslPractical15CompleteEventCollector` arms after three pose-present/no-hand frames, starts an event on hand appearance, and finishes only after three consecutive pose-present/no-hand observations. A visible but motionless hand is still `hasAnyHand=true`; motion settling is not an end condition. The debug recorder also has an eight-second command-to-finish safety timer. Event JSON does not record the exact linguistic sign-end instant or every collector state transition, so sign-end-to-result latency cannot be claimed from these files.

- All **nine** `AUTO_LEGACY_RELEASE` events ended with exactly three no-hand frames. Time from last hand-present frame to saved final frame was 301–452 ms (mean 390 ms); no release event required more than three in the saved sequence.
- All **nine** `EVENT_TIMEOUT` events had a hand tracked in every saved frame, including the last: longest and trailing no-hand runs were both zero. The timeout group comprises THANK YOU 2, NO 3, YES 4.
- In one THANK YOU timeout (`4bd6fd5b`), both hands were tracked in every one of 53 frames despite the wrists becoming nearly stationary near the lower image edge; raw THANK YOU .9999998 was rejected solely for timeout. In the deliberate stationary-hand control (`55aa3c3a`), both hands remained tracked in all 55 frames and raw THANK YOU .9999992 again timed out.
- An attempted lower-edge hold (`21d4a016`) lost hand landmarks for three frames and released. The operator confirmed the hands were partly at/below the preview edge, so that trial is a valid no-hand release observation, not a valid fully visible hold.
- In all three NO timeouts, the right hand was tracked in every frame (48/48, 47/47, 53/53) with final wrist y around 0.75–0.79, not merely at the frame edge. Raw NO was correct each time. This independently reproduces the boundary condition in another class.
- The extra accepted HELLO automatic event shows that hand appearance after neutral can trigger on unintended movement. The operator could not identify which of the four IDs was extra; no individual HELLO event is labelled as ground-truth non-sign in the matrix.

Conclusion: the automatic timeout is caused at the collector state-transition layer when hands remain detected after motion ends. This is not a TFLite conversion or raw-classifier failure for THANK YOU or NO. YES has **both** the same boundary failure and a separate raw YES→HELLO failure on all four longer automatic tensors; manual right-hand YES had raw YES 4/6 and raw HELLO 2/6 with no accepted YES. A boundary change may alter its input envelope, so compare deterministic saved-tensor windows before deciding on data/model adaptation. No threshold should be changed from these few events, and no Samsung operator-labelled tensor should be used as supervised FSL training truth.

Next safe engineering step: derive a class-agnostic, timestamp-aware motion-settle candidate from saved hand/pose trajectories, replay it offline against all positive and OOD events, and only then expose it behind a new debug-only mode. Preserve final holds and the `[1,48,225]` contract. Do not route it to production Sign text/TTS while moving negatives are falsely accepted.
