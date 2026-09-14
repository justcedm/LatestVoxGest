# VoxGest New ChatGPT / Sol Project Bootstrap

Use this file as the knowledge seed for a brand-new normal ChatGPT project conversation.

## Prime directive

Do not depend on chat memory. Read GitHub current state first. Before claiming what is current, fetch or inspect the authoritative repository, select the relevant branch, read its live handoff, and inspect its latest commits and dated evidence.

Authoritative repository: `https://github.com/justcedm/LatestVoxGest.git`

## What VoxGest is

VoxGest is an Android bidirectional Filipino Sign Language accessibility prototype:

- Sign pathway: camera landmarks -> recognition -> accepted semantic token -> English, Filipino, or Both presentation -> optional TTS.
- Listen pathway: speech-to-text -> conservative supported concept resolution -> verified FSL Avatar Action.

It does not claim unrestricted natural-language-to-FSL translation, complete FSL grammar, or linguistic certification.

## Ownership boundary

- Sol/recognition lane owns recognition datasets, models, FullSign225 features, MediaPipe, temporal segmentation, acceptance gates, token composition, and recognition physical evidence.
- Astra/app-developer lane owns the purchased Avatar rig, Blender retargeting, Actions, human-motion QA, runtime export, Avatar manifest/resolver, Filament playback, and Avatar physical evidence.
- Cross-lane changes require an explicit documented request. Avatar work must not silently modify recognition behavior.
- GitHub is the shared documentary source of truth.

## Safe storage

Use safe `C:` storage only. The historical `D:` workspace is prohibited. Purchased Avatar source remains local and outside Git. Raw datasets/videos, bulk features, APK/AAB, build caches, model checkpoints, credentials, secrets, and large evidence media also remain outside Git.

## Current recognition state - safe high-level snapshot

Recognition changes live on their own branch and must be read there. As of the 2026-09-14 pushed `recognition/mapua14-live-segment-v1` handoff:

- Standard FSL-105 remains protected and separate from the experimental Mapua-14 lane.
- The existing experimental RD-TCN48 model is unchanged; no threshold tuning or retraining occurred during the live-segmentation work.
- Five OLD rolling48 HELLO attempts were captured: 3/5 correct raw top-1 and 3/5 accepted correct; two attempts did not infer. User-observed delays ranged from about 15 seconds to over 60 seconds.
- Separate canceled-positioning evidence contained false accepts, so raw classifier and semantic acceptance must remain reported separately.
- The NEW complete-event/resampled48 runtime was built and unit-tested, but current Samsung NEW evidence was not captured because ADB disconnected.
- Do not declare live recognition fixed. Read the latest `reports/CODEX_LIVE_HANDOFF.md` from the recognition branch before repeating or updating this snapshot.

## Avatar history and current state

Historical work reported:

- a purchased detailed rig with independent finger chains
- a Blender 3.2-compatible pipeline
- raw225/FullSign225 geometry: pose 99 + anatomical LEFT 63 + anatomical RIGHT 63
- no silent mirroring and no left/right slot swapping
- frozen retargeter `retarget_general_B32_release_candidate_v1`
- historical CORE3 Actions `FSL_HELLO`, `FSL_MILK`, and `FSL_RICE`
- historical runtime `voxgest_avatar_B32_CORE3_RC2.glb`, reported as 28,123,308 bytes
- Filament Android integration
- a SurfaceView/Compose z-order failure that led toward TextureView
- a native teardown/double-destroy SIGSEGV risk; cleanup must be idempotent

Historical success is not a current pass. The Avatar branch starts with safe-local asset discovery and CORE3 revalidation.

Retargeting lessons that must not regress:

- Blender scripts must not assume `sys.argv` always contains `--`.
- Upper arm follows shoulder -> elbow.
- Forearm follows elbow -> wrist.
- Never restore the historical incorrect elbow -> wrist upper-arm mapping.

## Android architecture

The working Android project is under `android_dry_run`. The Listen Avatar path historically used Filament and a single GLB with separately resolved Actions. Do not reload the GLB merely to move between supported actions. Treat TextureView/Compose visibility, demand-driven loading, repeat playback, memory, and idempotent native cleanup as explicit regression gates.

Avatar playback and recognition are separate runtime concerns. A Listen resolver may emit only a supported, currently verified Action; unsupported or ambiguous speech must not fabricate animation.

## Bilingual presentation contract

Each verified Avatar entry exposes:

- `canonical_action`
- `english_display`
- `filipino_display`
- `english_speech_aliases`
- `filipino_speech_aliases`
- `action_name`
- `validation_status`
- `listen_ready`

English/Filipino strings are resolver/presentation metadata. They never rename canonical FSL action IDs. Examples include HELLO -> Hello/Kumusta -> `FSL_HELLO`, MILK -> Milk/Gatas, and RICE -> Rice/Kanin or Bigas.

`listen_ready=true` requires current SOURCE, MECHANICAL, HUMAN_MOTION VISUAL, and EXPORT passes. Android readiness separately requires Android runtime/build and physical device gates. Qualified FSL linguistic validation is a separate status and may be claimed only with real qualified review evidence.

## Product priorities and limitations

Revalidate CORE3 first. Then prioritize clean, useful everyday concepts rather than an arbitrary count. Prefer ten excellent readable actions over twenty-five weak ones. Fast-fail missing/poor sources and unstable one-off retargets. Continue beyond the priority set only while time, clean source, pipeline stability, and regression protection permit.

The Avatar must be light, calm, frontal, centered, head-to-waist/upper-hip, with hands and fingers fully readable. Master Actions preserve validated source timing. Robotic snapping, finger jitter, wrist teleporting, elbow popping, forced global duration, and fabricated facial grammar are failures.

## Branches and handoffs

- Avatar: `avatar/astra-calibration-20260914`; status in `reports/ASTRA_LIVE_HANDOFF.md`; decisions in `docs/AVATAR_ARCHITECTURE_DECISIONS.md`.
- Recognition: inspect the active recognition branch; status in that branch's `reports/CODEX_LIVE_HANDOFF.md`; decisions in `docs/ARCHITECTURE_DECISIONS.md`.
- History: `docs/PROJECT_HISTORY_JUNE_SEPT_2026.md` is concise history, not live status.
- Policy: `docs/DOCUMENTATION_POLICY.md`.

To inspect current state:

```powershell
git fetch origin --prune
git remote -v
git branch -a -vv
git log --all --date=iso-strict --oneline -20
git show origin/avatar/astra-calibration-20260914:reports/ASTRA_LIVE_HANDOFF.md
git show origin/recognition/mapua14-live-segment-v1:reports/CODEX_LIVE_HANDOFF.md
```

If a named branch no longer exists, list remote branches and use the newest documented successor. Do not infer unpushed local work from GitHub.

## Documentation and coordination rules

Meaningful work is incomplete until its live handoff and evidence are committed and pushed. GitHub is near-real-time at commit/push granularity, not live keystroke streaming. Use explicit staging, `git diff --cached --check`, normal commits, normal push, and remote-HEAD verification. Never force-push.

For Avatar implementation, hand control to Codex/Astra using `avatar_handoff/APPDEV_ASTRA_BOOTSTRAP_PROMPT.md`. For reset recovery, use `avatar_handoff/ASTRA_RESUME_PROMPT.md`.
