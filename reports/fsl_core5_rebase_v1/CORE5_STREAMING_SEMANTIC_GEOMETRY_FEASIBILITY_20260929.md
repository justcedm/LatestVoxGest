# Core5 streaming semantic-geometry feasibility — 2026-09-29

Decision: **offline feasibility demonstrated, Android implementation not authorized**. A rolling window can produce the intended class before many legacy timeouts, but the present five-class classifier also produces stable false peaks on non-sign motion. The exploratory source-derived geometry check is too insensitive to Samsung positives and still lets two arbitrary waves through. No model, 48×225 tensor contract, acceptance gate, production UI, Avatar, or Android recognition code changed.

The proposal named by the owner was absent from this branch; it was read completely from freshly fetched `origin/main:docs/CORE5_STREAMING_SEMANTIC_GEOMETRY_PROPOSAL_20260929.md` without checkout. This report uses the current Boundary V2 checkpoint and the preserved FSL-105/Samsung raw-landmark evidence. The 20 official FSL-105 **test** feature files were not opened; source geometry fitting used 61 official-train fitting clips and development checks used 20 official-train development-validation clips. Samsung operator-intent labels are evaluation evidence, not linguistic ground truth or training labels.

## Phase 1: what the 225-vector actually preserves

For pose landmark `P`, nose `N`, hand landmark `H`, and each hand's wrist-to-middle-MCP 3D scale `s`, the current builder computes pose XY `P' = P − N` and hand XY `H' = (H − N)/s`. It then multiplies every Z by 0.3. Thus a hand-to-nose **direction**, within-hand orientation/shape, pose-relative offsets, and anatomical hand slots survive. Hand-to-nose magnitude survives only in **hand-scale units**, not body-scale units. `H' − P'` is dimensionally invalid for hand-to-mouth/eye/shoulder displacement; it mixes a hand-scaled coordinate with an unscaled pose coordinate. Since within-hand wrist-to-MCP distance becomes approximately one, `s` cannot generally be recovered from the model tensor alone. Raw landmarks or an explicitly retained scale are required for correct body-relative geometry. Pose and hand Z also have distinct MediaPipe reference conventions, so cross-component depth is not treated as physical distance. This is a **representation risk**, not evidence that the existing classifier or Android preprocessing is numerically mismatched.

An empirical audit of all 81 official-train clips and 69 saved Samsung events confirmed the algebra to float-roundoff precision: median wrist-to-nose XY reconstruction errors were about `1e-8` in source and `2e-8–9e-8` on Samsung when the separately known `s` was restored. The table below shows per-event medians; the final column is the ratio of a deliberately invalid canonical wrist-minus-pose-mouth distance to the proper raw-landmark, shoulder-width-normalized distance. It quantifies why downstream semantic geometry must **not** be computed by naively subtracting the current two blocks.

| Class | Source hand-scale/shoulder | Samsung hand-scale/shoulder | Source invalid/true mouth ratio | Samsung invalid/true mouth ratio |
|---|---:|---:|---:|---:|
| HELLO | 0.472 | 0.395 | 10.5× | 4.9× |
| THANK YOU | 0.435 | 0.291 | 11.6× | 6.5× |
| YES | 0.485 | 0.398 | 10.7× | 5.0× |
| NO | 0.514 | 0.364 | 10.2× | 5.2× |
| UNDERSTAND | 0.467 | 0.379 | 12.3× | 5.2× |

Source-to-Samsung scale and hand-to-face distributions also differ after shoulder normalization (for example, median raw wrist-to-nose body scales: THANK YOU 1.57 source versus 0.91 Samsung; YES 1.19 versus 0.79). Camera, signer, motion execution, and landmark quality may contribute; this audit does not isolate which. The existing classifier's exact Samsung tensors still replay with Android/desktop parity from prior checkpoints.

## Phases 2–3: raw-landmark semantic geometry and source separability

New offline code derives timestamped XY wrist/palm-to-nose, eye-center, mouth-center and shoulder-midpoint vectors/distances; fingertip-to-face minimum distances; shoulder-width-normalized hand height; elbow angle and forearm direction; projected hand orientation and **2D palm signed-area proxy**; finger opening/flexion; inter-palm distance/orientation; and torso-relative XY trajectory/velocity. It uses existing Pose plus anatomical L/R hands only. The palm proxy is not a true 3D palm normal. FaceMesh/Holistic is not used. A synthetic test confirms body-relative features are invariant to uniform image translation/scaling, whereas naive cross-block subtraction is not a valid face distance.

Official-source training trajectories show relationships but not a reliable stand-alone verifier. Median dominant-palm-to-nose distances (in shoulder widths, p10–p90 in parentheses) are HELLO `0.63 (0.48–0.87)`, THANK YOU `1.28 (1.08–1.50)`, YES `0.83 (0.56–0.96)`, NO `0.86 (0.52–1.10)`, UNDERSTAND `0.49 (0.40–0.60)`. These regions overlap. Hand opening and two-hand presence add separation: source THANK YOU has median two-hand presence `0.81`, while the other four class medians are `0`; source median opening is HELLO `1.91`, THANK YOU `2.15`, YES `0.49`, NO `0.93`, UNDERSTAND `1.16` in hand-scale units. None is a linguistic hard rule.

An exploratory 23-feature robust-scaled nearest-centroid geometry profile learned **only from 61 source fitting clips** identified the correct geometry class on `12/20` held-out official-train development clips. A source-train leave-one-out p95 class-distance cutoff retained `16/20` true development clips. This already misses too many in-source signs for a safety verifier, before cross-device evaluation. Small classes, unknown signer independence, projected pose-arm geometry, and overlapping semantic regions limit interpretation. No source official-test metric was used for selection.

## Phases 4–6: same-model timestamped rolling replay

The replay verified the existing debug SIM10 model SHA256 `3702ff77c1c44a60f0dc7f06e19e778b6498df7dbf7e205991dc15158b8e888f`, float32 `[1,48,225] → [1,5]`, and each saved final tensor SHA/rebuild where present. It read **all 69** saved Samsung events. From source-envelope durations, it chose candidate histories `1,300`, `1,500`, `1,800`, and `2,000 ms`; it evaluated overlapping windows about every `200 ms`, timestamp-resampled each authentic canonical sequence to 48, and ran the **unchanged** TFLite model. The diagnostic stability rule was three consecutive same-duration, quality-valid windows with the same top-1 at the existing 0.95 confidence threshold. This is an offline comparison rule, **not** a new gate. Because windows overlap heavily, three outputs are correlated, not three independent confirmations.

| Operator-intent label | Saved events | Ever stable intended class | First stable class intended | Exploratory geometry retained intended stable class |
|---|---:|---:|---:|---:|
| HELLO | 14 | 12 | 11 | 10 |
| THANK YOU | 9 | 8 | 8 | 0 |
| YES | 11 | 2 | 2 | 2 |
| NO | 9 | 8 | 8 | 0 |
| UNDERSTAND | 10 | 8 | 8 | 0 |
| **Total** | **53** | **38** | **37** | **12** |

Of **20 positive events ending in `EVENT_TIMEOUT`** across manual and automatic modes, 15 eventually had a stable intended-class window before timeout, but only **14 had the intended class as the first stable peak**; five first peaked at a wrong word and one had no stable peak. Median time to eventual stable intended peak was about **3,019 ms after event start**, with median **4,420 ms lead** before the legacy end; this is not latency from the true linguistic sign end, which was not annotated. Per-class timeout counts: HELLO 4/4 eventual stable intended, THANK YOU 3/3, NO 5/5, UNDERSTAND 1/2, YES 2/6. In the **nine AUTO_LEGACY timeout events specifically**, five (both THANK YOU and all three NO) had the intended class as the first stable peak; all four YES events remained raw-wrong or unstable. The source-geometry cutoff retained **0/9** correct AUTO_LEGACY timeout peaks and only 5/20 correct peaks across all timeout modes. All per-event first-stable times, peak class/probability/window duration, exploratory geometry distance, legacy end reason, and full window probability vectors are retained in the private JSON.

Negative/OOD result: no neutral event had a stable peak (`0/5`), but **all six arbitrary waves** and **one of five partial signs** did (`7/16` negative events). The exploratory geometry check reduced that to **two of six waves** (`2/16` overall) while discarding most intended Samsung peaks. Requiring five rather than three consecutive windows still produced stable peaks on `7/16` negatives and reduced positive events with any stable intended class from `38/53` to `33/53`; no stability setting was approved. The closed-set classifier plus overlapping windows therefore does **not** solve unsupported-input rejection. Existing legacy accepted-correct outcomes were `25/53` and wrong negative accepts `5/16`; streaming's earlier raw peaks are not directly comparable accepted outputs because it has no validated rejection, sign-end annotation, or duplicate/rearm control.

## Phases 7–8: architecture decision and face policy

`legacy event → classify` demonstrably fails on visible-hand timeout. `rolling windows → temporal stability → semantic verifier` is a promising **research direction** for latency and avoiding that dependency, but this prototype is **not better as an acceptance architecture**: wrong first peaks, 7/16 negative raw peaks, poor source geometry development recall, poor Samsung geometry transfer, and unmeasured duplicate-output and Android CPU/battery cost remain. Four candidate durations every 200 ms could invoke TFLite up to about 20 times per second when sufficient frames are available; actual Samsung inference latency, thermal behavior and power were not measured. No Android streaming implementation or install was made.

Existing pose eyes, nose, mouth corners, shoulders and elbows are enough to perform this feasibility audit. A Face Landmarker is **not needed now**; no evidence isolates missing facial anchors as the failure. If a later FSL class requires cheek/chin/lip/eyebrow or non-manual distinctions, evaluate a small selected face subset and on-device cost before adding it. Never concatenate a full face mesh by default.

## Evidence and exact next gate

Safe scripts: `scripts_ml/core5_semantic_geometry.py`, `core5_semantic_source_profile.py`, `core5_representation_audit.py`, `core5_streaming_window_replay.py`; synthetic tests: `tests/test_core5_semantic_streaming.py`. Private, non-Git evidence: `D:/VoxGest/evidence/fsl_core5_rebase_v1/core5_semantic_source_profile_sealed_20260929.json`, `core5_representation_relation_audit_sealed_20260929.json`, and `core5_streaming_window_replay_sealed_20260929.json`. These reruns skip official-test feature files before opening them; earlier exploratory JSONs were preserved, not overwritten. Raw Samsung events and model artifacts remain private/uncommitted. The modern UI, Avatar, production route, model, thresholds, and current gates are untouched.

Next: annotate true sign start/end and unsupported-motion intervals on a small consented, held-out recording set; define and validate a source-only/approved OOD verifier with robust source-to-Samsung geometry transfer; rerun streaming with a proper non-sign rejection and duplicate/rearm audit; then benchmark latency on the separate recognition-lab package. Do not implement Android streaming recognition before those offline safety gates pass.

RELATIONAL_INFORMATION_PRESERVED=PARTIAL; direction and within-hand geometry survive, body-relative hand distances do not survive in recoverable units from 225 alone.
NORMALIZATION_RISK=HIGH for semantic cross-block subtraction and cross-device scale transfer; no normalization changed.
SEMANTIC_FEATURES_EXTRACTED=YES, from raw Pose + anatomical L/R Hand XY and timestamps.
CORE5_GEOMETRY_SEPARABILITY=12/20 source-development nearest-centroid; source-only p95 plausibility retains 16/20.
SLIDING_WINDOW_FEASIBLE=OFFLINE_YES; acceptance architecture NOT VALIDATED.
TIMEOUT_EVENTS_RECOGNIZABLE_BEFORE_TIMEOUT=15/20 eventual stable intended; 14/20 intended as first stable peak.
NON_SIGN_FALSE_PEAKS=7/16 raw stable, 2/16 with exploratory geometry; no safe rejection.
FACE_LANDMARKER_NEEDED_NOW=NO.
RECOMMENDED_ARCHITECTURE=Keep current isolated diagnostic profile; research rolling windows plus validated OOD/geometry/rearm before any Android promotion.
ANDROID_IMPLEMENTATION_AUTHORIZED=NO.
NEXT_EXACT_ACTION=Collect independent temporal annotations and evaluate a source-grounded rejector/geometry domain transfer on held-out evidence before another Android build.
