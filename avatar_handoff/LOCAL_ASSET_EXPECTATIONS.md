# Local Avatar Asset Expectations

This file describes what Astra should locate locally. It contains no purchased/private asset.

## Safe locations

- input root: `C:\VOXGEST_AVATAR_INPUTS`
- working root: `C:\VOXGEST_AVATAR_WORK\20260914`
- evidence root: `C:\VOXGEST_APPDEV\EVIDENCE`
- repository: `C:\VOXGEST_APPDEV\LatestVoxGest`

The historical D: workspace is prohibited. Discovery must be limited to explicit safe C: roots.

## Inventory targets

Locate and record path, byte size, modification time, and SHA-256 where practical for:

- developer-owned purchased `.blend` source and a protected working copy
- Blender executable/version, historically Blender 3.2-compatible
- frozen `retarget_general_B32_release_candidate_v1`
- retarget script, bone map, and retarget/export configuration
- approved FSL source/reference or raw225 trajectory material
- QA scripts/configuration
- historical CORE3 GLB, animation manifest, and verification summary
- Android runtime manifest/config relevant to Filament playback

Do not assume a filename match proves authority. Compare hashes/manifests and record uncertainty.

## Protected contracts

- raw225/FullSign225: pose `[0:99]`, anatomical LEFT `[99:162]`, anatomical RIGHT `[162:225]`
- no silent mirroring or left/right slot swapping
- upper arm: shoulder -> elbow
- forearm: elbow -> wrist
- Blender script entry must tolerate `sys.argv` without `--`
- purchased source remains outside Git and the knowledge ZIP
- CORE3 fallback and frozen solver are preserved before experiments

## Missing-asset behavior

Record each missing item and the exact blocked phase in `reports/ASTRA_LIVE_HANDOFF.md`. Do not recreate purchased or authoritative source material from memory. Missing calibration prerequisites block calibration, not the read-only environment audit.

Run:

```powershell
powershell -ExecutionPolicy Bypass -File .\avatar_handoff\scripts\inventory_avatar_assets.ps1 `
  -RepoRoot C:\VOXGEST_APPDEV\LatestVoxGest `
  -AvatarInputs C:\VOXGEST_AVATAR_INPUTS `
  -AvatarWork C:\VOXGEST_AVATAR_WORK\20260914
```

The inventory is external evidence. Commit only a concise redacted status/hash summary when safe; never commit the private source or private absolute-path details that reveal secrets.
