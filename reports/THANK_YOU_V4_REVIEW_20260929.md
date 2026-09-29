# THANK YOU V4 preparation clearance — 2026-09-29

V3 is superseded for motion review by V4. The expanded body audit found 14 overlap records in V3: early neutral-entry frames 10–12 and preparation frames around 108–118. Earlier zero-overlap claims covered only the explicitly reported hand/face/distal-finger regions.

## Correction and evidence

The new Action moves the wrist target outward up to 40 mm and forward up to 35 mm, through fixed-length two-bone arm IK. Correction windows are frames 2–24 and 101–127 with smoothstep entry/exit. Both shoulders remain in place; no root/global/armature translation or scale change is used. Wrist orientation and the finger subtree are transported with the wrist. Maximum target displacement is 53.151 mm. This is an engineering clearance choice in the lower-body preparation region, partly outside the cropped source image, not a measured linguistic target.

Three derivative trials were preserved: 25-mm outward/20-mm forward on preparation left five overlaps; 40-mm/35-mm on preparation left three early-entry overlaps; extending the same bounded correction to neutral entry cleared the tested overlaps. Original and V3 assets were not overwritten.

V4 has zero triangle-overlap flags across 243 frames × 27 pair checks, including hands against torso/legs and opposite forearms. The method excludes shared proximal finger bases, clothing surfaces and other unlisted body pairs; it is not an exhaustive self-collision proof. Contact-phase frames 129–180 are exactly equal to V3. Neutral first/last frames remain exact. All fitted matrices are finite. Face diagnostics have zero signed-normal penetration flags in 129–180. The largest local adjacent quaternion change remains 34.9176 degrees at 127–128 and needs full-speed scrutiny.

## Acceptance

SOURCE_REFERENCE=PENDING_REVIEW: clips/7/0.MOV mapping has not been confirmed by the reviewer.
MECHANICAL=PENDING_VISUAL_DEFORMATION_REVIEW.
HUMAN_MOTION_VISUAL=PENDING_FULL_SPEED_REVIEW.
EXPORT=BLOCKED.
OWNER_DEVICE=PENDING.
LISTEN_READY=false.

No new GLB or transfer ZIP has been produced. Do not promote the old 19-clip diagnostic package. YES/NO/UNDERSTAND remain next in the required acceptance sequence; other words stay parked. See ALL_WORDS_APP_TEST_READINESS_20260929.md.

## Reproduction

Use the existing immutable Core3 12-clip review source and V3 private pose fit. Run tools/avatar_survey/fit_preparation_clearance.py with --hand-spacing-m .04 --forward-m .035 into a NEW directory. Run check_hand_intersections.py with --include-body, then blender_face_clearance.py. Build with build_lipfit_review.py --action THANK_YOU__BODY_CLEARANCE_REVIEW_V4; verify with verify_lipfit_blender.py; render_review.py and compare_video.py generate the private review movie. Blender 3.2; autoexec disabled. Input/output paths and hashes are recorded in the live state.

NEXT_EXACT_ACTION=Complete V4 Blender parity and normal-speed source comparison; inspect neutral entry 2–24, preparation 101–128, lip phase 140–166 and recovery 201–218 before export.

## Completed private artifacts

Blender: C:\Users\Erl\Documents\Codex\2026-09-15\files-mentioned-by-the-user-voxgest-2\outputs\core5_survey_20260929\thank_you_bodyclear_blender_v4\THANK_YOU_CORE3_LIPFIT_REVIEW_V4.blend

SHA256: a61a1f69e0bee8bcb23ef07a76790ce47ae0c621a476976719f75a27bc9af1c0

Comparison: C:\Users\Erl\Documents\Codex\2026-09-15\files-mentioned-by-the-user-voxgest-2\outputs\core5_survey_20260929\thank_you_bodyclear_movie_v4\THANK_YOU_V4_SOURCE_COMPARISON_60fps.mp4

SHA256: 799c4b7b646295fca7ea0861bfbbd5f6217e6db0267e6eeddc88b544f34ccdf8

Blender evaluated parity: PASS, maximum world-matrix error 9.5367431640625e-07. All original Action hashes unchanged: PASS. Sampled comparison frames 10/112/120/153 inspected; this is not normal-speed acceptance. User explicitly replied **Review pending** for V4. No further word promotion or export until that gate passes.
