# THANK YOU V3 engineering review — 2026-09-29

Status: SOURCE=PENDING_REVIEW; MECHANICAL=PENDING_COMPLETE_DEFORMATION_REVIEW; VISUAL=PENDING_FULL_SPEED_REVIEW; EXPORT=BLOCKED; OWNER_DEVICE=PENDING; LISTEN_READY=false.

## Scope and provenance

Preserved checkpoint 54d14a2, original Core3, donor assets and all previous attempts. Only a new THANK_YOU__LIPFIT_REVIEW_V3 Action was created on the existing Core3 platform. No recognition or production LISTEN changes. Source clips/7/0.MOV (SHA256 fc0003d10b31d6e04e0effca02a288b0d05135ac2cbc15a1f94cd07fa8641ea4) remains unconfirmed by the owner/reviewer. No linguistic approval is inferred.

## Decisions and correction

V2 moved the signing chain toward lip-relative image-plane targets using fixed-length two-bone IK; unreliable source depth was not treated as contact evidence. World wrist orientation and articulated fingers were carried through the fit. The target-depth hypothesis remains unapproved. V2 was rejected for inherited distal finger overlaps and recovery inter-hand intersections.

V3 adds a bounded 4-degree fan to index/ring and 8 degrees to pinky, leaving middle/thumb unchanged. Each hand receives 8 mm outward spacing during the contact envelope, solved through the arm chain. This adds approximately 4.4 source-image pixels of horizontal middle-tip displacement at full weight: an engineering tradeoff requiring visual/FSL review. Recovery frames 201–218 bridge local TRS/quaternions to unchanged Core3 neutral. Source images show lowering during 203–211; that observation does not certify the revised trajectory.

Trials were preserved separately: 2-degree fan/no spacing produced 24 overlap flags; 4-degree fan/4-mm spacing produced 9; 4-degree fan/8-mm spacing produced zero tested overlap flags. No global scale, camera compensation, bind-pose change or aggressive smoothing was used.

## Evidence and limits

- All 243 frames, 238 bones: Blender evaluated pose parity PASS, maximum matrix error 9.53674316e-07.
- All original Action curve/key/interpolation hashes unchanged, including HELLO/MILK/RICE: PASS for data regression. Device/visual regression remains pending.
- 23 triangle-pair checks per frame, 243 frames: zero overlap flags for face/hands, inter-hand and distal inter-finger regions. Shared proximal finger bases and other body collision types are excluded.
- Face distances checked on all frames 129–180; zero signed-normal penetration flags. Minimum tip/face 9.669 mm, wrist/face 47.400 mm, tip/nose 13.127 mm and tip/chin 25.247 mm. Region definitions are geometric, not expert anatomical labels. Signed normals are diagnostics, not solid-volume proof.
- Neutral first/last poses unchanged. Peak adjacent local quaternion rotation is 34.9176 degrees at frames [127, 128]; inspect this transition at normal speed. Numerical smoothness is not human-motion acceptance.
- Front, side and perspective stills generated. Sampled lip pose and recovery were inspected; a complete full-speed human comparison remains pending.
- Side-by-side source/candidate movie verified 243 frames at 60 FPS. Earlier V2 review request is superseded by V3.

## Private review artifacts

Blender: C:\Users\Erl\Documents\Codex\2026-09-15\files-mentioned-by-the-user-voxgest-2\outputs\core5_survey_20260929\thank_you_lipfit_blender_v3\THANK_YOU_CORE3_LIPFIT_REVIEW_V3.blend

SHA256: ea199de1b5fe7a562b6efa0ede1e1fbe3dfd11547b189a39c0b5afffb9c3a27e

Comparison: C:\Users\Erl\Documents\Codex\2026-09-15\files-mentioned-by-the-user-voxgest-2\outputs\core5_survey_20260929\thank_you_lipfit_movie_v3\THANK_YOU_V3_SOURCE_COMPARISON_60fps.mp4

SHA256: 00d5dc6b67d24e7ad78127759a2ad1626e4542595cbd4e3b7ea8560f50729c9b

Private pose fit: C:\Users\Erl\Documents\Codex\2026-09-15\files-mentioned-by-the-user-voxgest-2\outputs\core5_survey_20260929\thank_you_lipfit_v3_s4_gap8\private_pose_fit.npz

These binaries and reference media remain local. No new GLB or handoff ZIP exists. Do not use an older GLB as the V3 export.

## Reproduction and next action

Utilities in tools/avatar_survey: fit_thank_you.py -> refine_thank_you.py (spread 4 degrees, hand gap 0.008 m) -> check_hand_intersections.py + blender_face_clearance.py -> build_lipfit_review.py --action THANK_YOU__LIPFIT_REVIEW_V3 -> verify_lipfit_blender.py -> render_review.py -> compare_video.py. Use --help for exact input/output switches and fresh output paths. Blender 3.2, autoexec disabled; private input paths remain in local checkpoint.

NEXT_EXACT_ACTION=Review the V3 comparison at normal 60-FPS speed, focusing on frames 127–128, contact 140–166 and recovery 201–218; complete deformation/whole-body review and record specific findings before any export. Keep source mapping pending until explicit reviewer evidence arrives. YES, NO and UNDERSTAND remain blocked behind THANK YOU.
