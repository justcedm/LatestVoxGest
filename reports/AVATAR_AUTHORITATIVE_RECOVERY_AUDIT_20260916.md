# Authoritative Avatar recovery audit — 2026-09-16

Updated: 2026-09-16T01:04:54.757434+08:00
Branch: `avatar/astra-calibration-20260914`
Base/remote HEAD before checkpoint: `09d04546a061017f977fd0988b488583dfb9e92e`.

## Result

Recovery inspection completed; CORE3 revalidation BLOCKED at HELLO SOURCE. No calibration, retarget execution, exports, vocabulary expansion, or recognition changes were made. No current SOURCE / MECHANICAL / HUMAN-MOTION VISUAL / EXPORT PASS is claimed. The historical runtime is intact; it is not an editable authoritative source master.

## Locations and preservation

The requested `C:\VOXGEST\_AVATAR\_INPUTS\RECOVERED\_AUTHORITATIVE` and `C:\VOXGEST\_APPDEV\LatestVoxGest` do not exist on this machine. Used the user-attached ZIP and the existing checkout on the requested branch:

- ZIP: `C:\Users\Erl\Documents\school shit\cap2\VOXGEST_EARLE_AVATAR_AUTHORITATIVE.zip`
- ZIP SHA-256: `d4a13d2c3e11a621df736f32e19264b6465b330cbbeebc8bfd4ded3cbc8fd0cc`
- Checkout: `C:\Users\Erl\Documents\Codex\2026-09-15\files-mentioned-by-the-user-voxgest-2\work\LatestVoxGest`
- Private recovery snapshot: `C:\Users\Erl\Documents\Codex\2026-09-16\you-are-continuing-the-voxgest-avatar\work\recovered_authoritative`
- Separate protected Blender inspection copies: `C:\Users\Erl\Documents\Codex\2026-09-16\you-are-continuing-the-voxgest-avatar\work\blender_inspection_copies`
- Local evidence: `C:\Users\Erl\Documents\Codex\2026-09-16\you-are-continuing-the-voxgest-avatar\outputs`

ZIP opened read-only. Every extracted file was SHA-256 hashed and marked read-only; the three Blender files were copied again and marked read-only before inspection. Blender was run with factory startup, background mode, `--disable-autoexec`, and no save/export operation. Embedded scripts were read as data only. All 6,531 extracted file hashes and the ZIP hash were rechecked after inspection: unchanged. No D: filesystem access was performed; legacy source paths in metadata were treated as strings, never followed. No purchased/private binary or raw motion data is staged for Git.

## Full inventory and checksum qualification

`recovered_inventory.json` records all 6,531 recovered files, byte sizes and SHA-256 values (6,527 SOURCE_HANDOFF, two runtime files, README and checksum list). Inventory SHA-256: `da4c629de7a767a046ff18038fbac5e56db1439ffc46ee02c325b64719f25f17`.

The supplied checksum file lists 6,530 entries. 6,405 match exactly by path and hash. 125 entries use `DON?T KNOW` / `DON?T UNDERSTAND` where archive paths contain the Unicode replacement character. Each has a unique matching content hash and basename in the archive. No content mismatch remains under that explicit reconciliation, but strict path verification FAILS for these 125 entries. The checksum file itself is the sole unlisted file. No names or source files were repaired. See `checksum_verification.json` for the complete mapping.

Nested archive listings: textures.zip (13 entries), Woman_Teen_OBJ.zip (24 entries), and the renders RAR (20 image files, one directory) contain no further Blender files. Nested assets were not executed or promoted.

## Key recovered hashes

| File | Bytes | SHA-256 |
|---|---:|---|
| `Final-27-06-2022.blend` | 173433396 | `d54a4d1993b1a9705f1f1f5ea6bad453f631abee69ba09d9ecb3cca8e67e07d7` |
| `purchased_avatar.blend` | 173501872 | `3a48b881b12a1f5bb99ec1bd82a5d669db8d30a2b62b47cf02692d0e5e3bea9d` |
| `purchased_avatar.blend1` | 173501872 | `900b0048fde1788499ac2228abc5d3e4c289a2fa79fe4b7b4d15f8559edf1492` |
| `blender_fsl_retarget.py` | 9909 | `58d74ac8b5348ce586279cc5bc4a56e1940a1c861c642ea7e8c3412b0ae3e57c` |
| `bone_map.json` | 520 | `311565d08a68bfd84c22e09b20623f08c5b19bf8343090c1bb0e3a9603b17d01` |
| `retarget_config.json` | 1658 | `93f2888057c0c21b2f3395a6f57e81eb309f4a1401eb2f36e45bd639cad13bf5` |

## Blender inspection

Blender 3.2.0, build e05e1e369187, executable at `C:\Program Files (x86)\Blender 3.2\blender-3.2.0-windows-x64\blender.exe`. All three processes exited 0.

All three files contain the same inspected rig/action structure, despite different file hashes:

- `metarig`: 67 bones; `Waitress RIG`: 525 bones, 146 deform bones, 101 drivers. Finger chains exist. Both are in POSE mode, unit object scale, zero object translation/rotation.
- Waitress RIG has two non-identity pose bones and substantial constraints (including 16 IK and 63 STRETCH_TO constraints). Presence of deform bones does not prove that directly keying them produces correct evaluated motion.
- Scene: 24 FPS, frames 1–250, current frame 25, metric scale 1.0.
- Actions: `MCH_Rotation_targetAction` (zero F-curves/keys) and `Waitress_pose` (frames 1–24, 2,089 F-curves, 28,716 keys; finite key coordinates).
- Neither armature has an active Action or NLA strips. `FSL_HELLO`, `FSL_MILK`, `FSL_RICE` are absent from every file.
- No linked libraries. 31 images, 21 packed; ten unpacked relative texture references point to an absent `Babies Series Rigging/Parent/textures` layout in the inspection workspace. Material usage and rendering were not validated.
- Original Final file embeds the rig UI text. Purchased `.blend` and `.blend1` also embed three retargeter texts. Embedded code was never executed.

Evidence: the three `*.blend*.audit.json` files in the local evidence directory; logs and inspection scripts in the task's `work` directory. Opening a rig and finding finite keys is not a Blender motion or neutral-pose PASS.

## Recovered solver/config audit

Frozen identity expected by the historical manifest:
`retarget_general_B32_release_candidate_v1`, SHA-256
`e0f71510a454db80eb6d47950c9a3ed44da06ac43f5f3a50b1f2f2b2e3dce4e2`.

No recovered file has this checksum. None of the embedded text hashes matches it either. Two embedded variants in each purchased file have the same Python AST as the external first-pass script; the third differs. File-hash differences alone do not disprove behavioral equivalence, but no frozen implementation, approved configuration, or reproducible equivalence test is available. **Equivalence is NOT ESTABLISHED. Do not promote the recovered script as B32 v1.**

Static findings in the unchanged external script:

1. Lines 145–151 identify an object named `Armature`, or require exactly one armature. These files have `metarig` and `Waitress RIG`, so that selection cannot succeed as written.
2. Lines 190–192 drive both upper arms with elbow-to-wrist directions; upper arms require shoulder-to-elbow. The forearm mapping is also elbow-to-wrist. This violates the documented historical regression requirement.
3. Lines 211–213 use `HAND[key]` with keys such as `f_index`, while HAND defines `index`, `middle`, `ring`, `pinky`. A reached non-thumb iteration raises KeyError.
4. Lines 117–125 clamp absolute quaternion angle, not the angular change from the previous orientation. This does not implement the configured per-frame rotation-step bound.
5. `bone_map.json` is an empty BLOCKED placeholder with null armature/source. Its statement that no rig was supplied is stale for this package. The script does not consume it.
6. `retarget_config.json` uses an identity coordinate matrix although its prose describes an axis conversion. Coordinate/rig-space calibration is unproven.
7. Source FPS is only stored as Action metadata; scene FPS is not set. Recovered scenes are 24 FPS, whereas CORE3 source metadata reports 60 FPS. Neutral padding, configured frame bounds, and hand scale reference are not implemented by this script. It has no explicit wrist/palm orientation solve.
8. Missing spans can leave bones unkeyed; reusing an existing Action does not clear its old curves. Inactive-limb determinism, clip isolation and hold preservation are not established.

These are audit findings, not measured motion failures from a generated clip. No patches or substitute solver were applied. The prior local arm-fix output is also not frozen B32 authority. Findings support ADR-A004/A005/A006/A009/A010: preserve the solver, full approved motion interval and timing, and keep acceptance gates separate.

## Historical CORE3 runtime inspection

Recovered GLB and manifest are byte-identical to the checkout's `android_dry_run/app/src/main/assets/avatar/core3` pair.

- GLB: 28,123,308 bytes; SHA-256 `30f13fb65e7557992a8c3109460a790a69161e69f3ba9771a1e64a33f98294f8`, matching manifest.
- Valid GLB 2 header/length; 257 nodes, one skin, 238 joints, no external buffer/image URIs.
- Each clip has 714 LINEAR channels, keys for every skin joint's translation/rotation/scale, finite values, strictly increasing sample times, and identical first/last component values within the clip.

| Clip | Samples | First key seconds | Last key seconds |
|---|---:|---:|---:|
| FSL_HELLO | 98 | 0.0166666675 | 1.6333333254 |
| FSL_MILK | 120 | 0.0166666675 | 2.0 |
| FSL_RICE | 98 | 0.0166666675 | 1.6333333254 |

The manifest duration uses the final timestamp; first-to-last keyed spans are one 60-FPS frame shorter. Quaternion norm error is at most 1.2e-7. Negative consecutive quaternion dot counts are 14/110/93: quaternion sign changes alone do not prove physical snapping because q and -q represent the same orientation. Playback interpolation and evaluated motion still require review. Equal endpoints do not prove the pose is neutral or that transitions between clips are correct.

These are historical asset integrity/structural checks only, not MECHANICAL, VISUAL or EXPORT PASS. No current source-matched normal-speed visual review, deformation/collision review, re-export, or device playback is claimed.

## Ordered CORE3 gates

Numerical recovery inventory (not source acceptance): all 60 HELLO/MILK/RICE raw225 clips (20 per label) have finite values and matching frame/225-feature/presence contracts, label metadata and `input_mirrored=false`. Reference `0.MOV` files are present for all three. Historical D: metadata paths were not opened.

Example 0 sources have 244/244/243 frames at 60 FPS. HELLO example 0 hand-presence fractions are left 0%, right 31.56%; MILK 0%/49.59%; RICE 34.98%/38.68%. Missing hand detections may include valid neutral intervals, so those figures alone are neither a rejection nor an approval. There is no proven mapping from recovered full trajectories to historical trimmed/padded Actions. The shorter runtime duration alone does not prove global speed-up; an approved interval/timing record is required.

| Order | Sign | SOURCE | MECHANICAL | HUMAN-MOTION / VISUAL | EXPORT |
|---:|---|---|---|---|---|
| 1 | HELLO | BLOCKED: authoritative master/solver and approved source-to-Action interval unavailable | NOT RUN: source gate blocked | NOT RUN: preceding gates blocked | NOT RUN: preceding gates blocked |
| 2 | MILK | NOT STARTED: HELLO blocked | NOT RUN | NOT RUN | NOT RUN |
| 3 | RICE | NOT STARTED: HELLO/MILK pending | NOT RUN | NOT RUN | NOT RUN |

Recovery inventory of all files and runtime clips was performed before these gates; it does not bypass the requested sign order. CORE3 runtime-ready count=0 currently revalidated. Vocabulary expansion attempted=0. Android/physical Samsung/FSL expert gates remain open. FACIAL_NMM_STATUS=NOT_SUPPORTED_BY_CURRENT_SOURCE.

## Exact missing authority and next action

Recover, on approved C: storage, the frozen solver with the checksum above plus its matching non-placeholder bone mapping/configuration and source interval/neutral-padding provenance. Recover the editable masters named by the manifest:

- HELLO source blend SHA-256 `f83b9022a7151ace81c8b5094a3dcaf121d5652421272394b360cf3b23f3871b`.
- MILK source blend SHA-256 `2b968656351142bcebb157e420272acb378bcde17266882b742e1956c7b9789e`.
- RICE source blend SHA-256 `06af4b775bef488d9ea08c58ad65b067e0476f799344f9eab42d165c47cc6d9a`.

None matches any recovered file hash. A version with different bytes requires explicit provenance/equivalence evidence; an imported GLB Action is not automatically the missing source master. Once located: hash and make protected working copies, inspect with Blender scripts disabled, validate HELLO's exact source interval and reference at original speed, then mechanical, human-motion/visual, and export gates. Only after HELLO passes proceed to MILK, then RICE. No motion reconstruction from memory or artificial speed-up.

## Checkpoint scope

Only this audit, a compact machine-readable evidence summary, and the new live-handoff section belong to this checkpoint. The checkout already had unrelated local edits to AvatarHelloPreviewActivity.kt, PROJECT_HISTORY_JUNE_SEPT_2026.md, an older live-handoff appendix and an untracked prior audit. Preserve those; do not include them in this commit. Recognition and protected runtime binaries remain unchanged. Full local evidence inventories contain hashes/status, not promoted runtime availability.

## GitHub delivery blocker

Audit commit: `effaa9148bba4c7633c16ee469841bfb506bccaf` (automated author Codex, codex@local.invalid; repository/global identity settings unchanged). Diff whitespace check passed; commit includes only the three audit/handoff files listed above.

Network permission was granted and GitHub read access works. Push failed: Git's credential prompt subprocess reported Win32 error 5; a direct native Git Credential Manager query with interaction disabled confirmed no usable cached credential (`Cannot prompt because user interactivity has been disabled`). No secret was printed or saved. Remote branch remained `09d04546a061017f977fd0988b488583dfb9e92e` after the failed push. PUSH=BLOCKED_AUTHENTICATION; REMOTE_HEAD_EQUALS_LOCAL=NO. This follow-up documentation commit records the blocker without rewriting the audit commit.

Next delivery action: authenticate Git Credential Manager for github.com in the user's normal Windows session, then run:

```powershell
git -C "C:\Users\Erl\Documents\Codex\2026-09-15\files-mentioned-by-the-user-voxgest-2\work\LatestVoxGest" push origin HEAD:refs/heads/avatar/astra-calibration-20260914
git -C "C:\Users\Erl\Documents\Codex\2026-09-15\files-mentioned-by-the-user-voxgest-2\work\LatestVoxGest" rev-parse HEAD
git -C "C:\Users\Erl\Documents\Codex\2026-09-15\files-mentioned-by-the-user-voxgest-2\work\LatestVoxGest" ls-remote origin refs/heads/avatar/astra-calibration-20260914
```

Require the last two SHA values to match. Source/master recovery remains the next engineering action, independent of authentication.
