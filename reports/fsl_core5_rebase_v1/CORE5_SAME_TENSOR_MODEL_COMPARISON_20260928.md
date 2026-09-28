# Core5 same-Samsung-tensor model comparison — 2026-09-28

Status: read-only offline A/B complete. The installed Samsung profile remains debug-only `FSL_CORE5_SIM10FPS_V1`; no model, gate, threshold, production route, or UI was changed.

Each saved `[48,225]` float32 tensor was SHA256-verified and sent unchanged to the existing Baseline, Native48, and SIM10 TFLite interpreters. Model hashes were checked before replay. The unchanged Android gate was reproduced from the saved event quality/termination and each model's probabilities; SIM10's reproduced gate and probability vector matched the saved Android result for every replay (maximum allowed probability difference `1e-5`). These are the same operator-intent Samsung captures across models, **not** independent trials or formal accuracy estimates. Full per-event five-class probability vectors and gate results are private under `D:/VoxGest/evidence/fsl_core5_rebase_v1/core5_*same_tensor*_ab_20260928.{json,csv}`. Historical HELLO's nine-tensor comparison is in the multiclass report.

| Intended event group | Events | Baseline raw correct / correct accepted | Native48 raw correct / correct accepted | SIM10 raw correct / correct accepted |
|---|---:|---:|---:|---:|
| YES, MANUAL 7 + AUTO_LEGACY 4 | 11 | 9 / 2 | 8 / 0 | 4 / 0 |
| THANK YOU, MANUAL 5 + AUTO_LEGACY 4 | 9 | 9 / 6 | 9 / 6 | 9 / 6 |
| NO, MANUAL 6 + AUTO_LEGACY 3 | 9 | 7 / 1 | 9 / 4 | 9 / 3 |
| UNDERSTAND, MANUAL 7 + AUTO_LEGACY 3 | 10 | 8 / 6 | 8 / 6 | 8 / 6 |

These groups mix timely, timeout, and explicit hold-control events, and UNDERSTAND includes two deliberately left-handed events. A timeout remains a rejection in every model's gate replay. Correct-accepted counts must not be mistaken for raw accuracy. On the YES group, Baseline and Native48 and SIM10 each also **accepted one wrong THANK YOU** for the same left-only YES event. On UNDERSTAND, Baseline accepted one wrong label; Native48/SIM10 did not. Baseline's apparent YES advantage is real on the same tensors, but it does not establish a safe replacement.

| Operator-confirmed non-sign group | Events | Baseline false accepts | Native48 false accepts | SIM10 false accepts |
|---|---:|---:|---:|---:|
| Arbitrary non-FSL wave | 6 | 4 | 4 | 4 |
| Incomplete/aborted gesture | 5 | 1 | 1 | 1 |
| Neutral/no intentional sign | 5 | no tensor/inference; 0 on device | no tensor/inference | no tensor/inference |

The three models assign highly confident closed-set labels to several moving non-sign events. Swapping among them without an independently validated OOD/event-quality mechanism leaves the observed 5/16 frozen-device negative false accepts unresolved. Raising or lowering a single confidence threshold is not justified by these few, overlapping positive and OOD scores.

Interpretation by layer: Android/desktop TFLite and feature parity passed; SIM10 shows a same-input YES disadvantage; Native48 performs best on this nine-event NO group but not on YES or OOD; automatic termination is independently defective for still-visible hands. Preserve the current checkpoint. Next safe step is an **offline**, class-agnostic motion-settle and non-sign separability study using only held-out saved evidence, then a new isolated debug experiment if that study supports it. Do not train on operator-labelled Samsung captures or connect this profile to production text/TTS.
