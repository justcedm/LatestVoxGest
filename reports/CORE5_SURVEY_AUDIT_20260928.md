# Core5 remote survey audit — 2026-09-28

THANK YOU does not pass source placement comparison. No new survey GLB, survey APK, or owner-testing ZIP was produced. The user confirmed that FSL reviewer approval for the reference is unavailable. Prepared words remain unapproved and `listen_ready=false`.

## Authority and isolation

Read all seven required documents completely before substantive work. Avatar protocol, live handoff, ADR and GitHub protocol were read at `fcacf3ffdb80bdf8d1bdf3e5e08dd316fa7a9531`. Readiness and modern UI documents were fetched from main `b6def3d7ac082865dcf2ce8aeb36b78cb150dc17` because absent on the Avatar branch. Rebuild reference blob `23b3681edd46f8704a36c4c6aa8d1aa52cbe2cf8` matches the requested historical reference and current main. Reading main did not modify recognition or production UI.

Clean C: clone on `avatar/astra-calibration-20260914`; older dirty checkout preserved. No owner D: workspace or physical Samsung was accessed. Original assets were only read. The current protocol supersedes earlier preparation-only sequencing exceptions: THANK YOU must pass before accepting YES, NO and UNDERSTAND.

## THANK YOU source and visual evidence

Source: FSL-105 `clips/7/0.MOV`, recovered as `SOURCE_HANDOFF/fsl105_reference_videos/THANK YOU/0.MOV`, SHA256 `fc0003d10b31d6e04e0effca02a288b0d05135ac2cbc15a1f94cd07fa8641ea4`. 243 frames, 60 FPS. Metadata says input is not mirrored and anatomical left/right slots are preserved. Reference provenance is not linguistic approval.

Compared source and reconstructed runtime candidate at frames 1, 100, 130, 153, 180 and 220; supplemental side-by-side frames 127, 128, 153 and 180 inspected. At frame 153 the source fingertips reach the lips; avatar fingertips remain below the chin. At 180, source hands extend outward while the candidate remains more upright and close to the upper body in the front projection. The latter needs side-view/depth review; a front projection alone cannot prove palm/depth equivalence. No numerical distance is claimed from these differently framed images.

SOURCE_REFERENCE=FAIL_PLACEMENT_COMPARISON. This finding rejects the current candidate; it does not certify the source as valid FSL. HUMAN_MOTION_VISUAL=PENDING: the entire 243-frame 60-FPS side-by-side movie was created, but generation and selected-frame inspection do not prove full-speed human acceptance. Reviewer approval is unavailable, explicitly confirmed by the user. Resolve `REF-TY-001` before choosing a contact target or changing the gesture.

Private evidence under local `outputs/core5_survey_20260928/private_evidence/`:

- `THANK_YOU_source_candidate_60fps_v1.mp4`: SHA256 `b3515004a4c12271501851734b2abfc3198d47288c7c6f3dc315663b2bea4e21`.
- `thank_you_fullspeed_v2/THANK_YOU_candidate_60fps.mp4`: SHA256 `000a82e504517b0b1c30e8d86bfd0537abdbfd81d9cdb6030ccbdc428af528fb`.
- `thank_you_fullspeed_v2/THANK_YOU_review_only.blend`: SHA256 `64d90f1bf0d199f4ed720c4b86cd558406fe560aa5944fe60eba5e925c220c1c`.
- `THANK_YOU_contact_and_step_v1.jpg` and source contact sheet are private diagnostic evidence, not Git content.

The new blend only adjusts review camera/action selection, retains the Core3 character and existing Actions, and does not repair or approve motion. First render failed on a relative Blender save path before any blend save; the script was corrected to absolute paths and a NEW v2 output directory. The successful second run rendered all 243 frames. No source blend was saved over.

## Mechanical and Core3 data regression

Fresh read-only `tools/avatar_survey/audit_runtime.py` audit of the preexisting 12-clip candidate: finite accessors, one skin, valid joint indices, unchanged platform definitions, and identical original binary prefix. All three Core3 animation definitions and accessor bytes produce identical individual SHA256 fingerprints; see `CORE5_RUNTIME_DATA_AUDIT_20260928_v2.json`. This includes runtime LINEAR interpolation definitions; it is not a recovered Blender-authoring-curve proof or a device visual test.

| Action | Frames at 60 FPS | Duration seconds | Max adjacent local rotation | Full mechanical gate |
|---|---:|---:|---:|---|
| THANK_YOU | 243 | 4.05 | 34.8303 degrees | PENDING |
| YES | 243 | 4.05 | 13.8472 degrees | PENDING |
| NO | 244 | 4.0667 | 28.9085 degrees | PENDING |
| UNDERSTAND | 245 | 4.0833 | 17.4672 degrees | PENDING |

Quaternion norm errors are below 4.20e-8; unit-scale deviations below 5.43e-6; first/last sampled channel components match. Channels target skin joints only. Dense near-unit scale channels exist; do not describe them as absent. Monotonic time and LINEAR interpolation pass. Local translations are measured in rig units, not metres. All four share the preexisting reconstruction convention; they are not newly accepted/exported survey Actions.

THANK YOU's peak is `RT_DEF-f_ring.03.R_07`, zero-based keys 126→127, Blender frames 127→128, GLB times 2.116667→2.133333 seconds. This requires contact/deformation review; the angle alone is not a proven visible snap or reason for global smoothing. Mesh collision, finger self-intersection, penetration and normal-speed coherence are NOT established by these numeric checks. Full MECHANICAL remains PENDING.

## Export and Android

Existing diagnostic GLB `voxgest_avatar_CORE3_PLUS_SUPERVISED9_RC1.glb`: 50,487,552 bytes; SHA256 `a8739dbe270b4da970fe0f7756ddb0c335458c6a25c36f200adad28e04310488`. It predates this acceptance review and must not be relabelled as an accepted Core5 survey export. New EXPORT is BLOCKED.

Existing isolated APK build log reports BUILD SUCCESSFUL; APK hash and embedded GLB were freshly checked, see `CORE5_EXISTING_APK_AUDIT_20260928.json`. Package uses the separate engineering activity, not production LISTEN. Existing resolver entries explicitly map HELLO/FSL_HELLO and the four survey concepts to their candidate runtime names. A new Core5 package/build was not made after the failed source gate. Owner device, real FPS, neutral render and production integration remain untested.

## Next steps and transfer

1. Obtain reviewer record `REF-TY-001` for the exact source and contact target. No replacement source clip is currently required; the clip already exists locally.
2. On approval, correct only THANK_YOU in a NEW Core3 derivative, including source-relative hand placement and the flagged transition. Preserve rig/binds/Core3 Actions, individual finger articulation and timing; no arbitrary whole-body scale or global smoothing.
3. Repeat source, complete mechanical/contact and full-speed human visual gates. Recheck exact Core3 data. Only then export a NEW THANK YOU candidate and perform export audit.
4. Repeat acceptance for YES, NO, UNDERSTAND in order. Only then create the four-word owner-testing package, privately transfer licensed derivatives, and collect owner Samsung evidence. Park other words.
5. Push only the safe scripts/reports/checkpoints. Authentication helper currently fails with MSYS signal-pipe Win32 error 5 and missing usable GitHub authentication. User is signing in. No remote checkpoint delivery is claimed until tip equality is verified.

No private assets are staged. Public JSON contains aggregate numbers, hashes, mappings and status, not skeleton matrices, mesh coordinates, raw225 arrays or recordings.
