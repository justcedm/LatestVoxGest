# VoxGest Avatar Architecture Decisions

Append-only Avatar decision log. Do not rewrite recognition decisions here.

## ADR-A001 — Dedicated Astra Avatar branch

**Decision:** Avatar calibration and Avatar-only Android integration use `avatar/astra-calibration-20260914`, based on `recognition/recovery-20260912`.

**Reason:** Recognition and Avatar need independent validation, rollback, and parallel ownership.

**Status:** ACCEPTED

## ADR-A002 — Prohibit old D: workspace

**Decision:** Astra must not access or write the historical D: VoxGest workspace.

**Reason:** It was previously determined unsafe/unreliable. Current work must use known safe C: storage.

**Status:** ACCEPTED

## ADR-A003 — GitHub handoff is the cross-account source of truth

**Decision:** `reports/ASTRA_LIVE_HANDOFF.md` is the canonical Avatar status between the app developer's separate Codex account and the project/review lane.

**Reason:** The agents do not share local state or chat context; GitHub provides an auditable shared state.

**Status:** ACCEPTED

## ADR-A004 — CORE3 regression before vocabulary expansion

**Decision:** HELLO, MILK, and RICE must re-pass current safe-workspace source, Blender, QA, export, Android, and physical-device gates before new Avatar vocabulary is promoted.

**Reason:** Historical validation does not prove migrated/current assets or runtime are healthy.

**Status:** ACCEPTED

## ADR-A005 — Preserve frozen retargeter v1

**Decision:** Keep `retarget_general_B32_release_candidate_v1` frozen for ordinary calibration. Create a solver v2 only if at least three source-clean signs show the same systemic solver failure.

**Reason:** Avoid destabilizing a generalized retargeter to rescue isolated difficult clips.

**Status:** ACCEPTED

## ADR-A006 — Avatar source uses full reference motion, not classifier windows

**Decision:** Avatar retargeting should use the full approved reference trajectory/action interval rather than the recognition classifier's fixed 20/32/48-frame input window.

**Reason:** Avatar generation must reproduce source articulation and timing; classifier temporal sampling is a recognition input contract, not an animation-authoring contract.

**Status:** ACCEPTED

## ADR-A007 — No fabricated facial grammar

**Decision:** Do not infer or author FSL facial/non-manual grammar that is not supported by validated source evidence.

**Reason:** Current Avatar source/FullSign225 trajectory does not provide a defensible FSL NMM-generation contract.

**Status:** ACCEPTED

## ADR-A008 — Live 3D remains primary, deterministic video is a documented fallback

**Decision:** Continue validating the Blender -> runtime GLB -> Filament path first. If repeatable runtime/native instability prevents CORE3 from passing after targeted fixes, evaluate pre-rendered H.264 clips as a separate reliability fallback.

**Reason:** Keep the richer 3D path when stable while retaining a realistic deadline-safe local/offline fallback.

**Status:** ACCEPTED