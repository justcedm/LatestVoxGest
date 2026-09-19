# ASTRA LIVE HANDOFF

## Current purchased-source calibration workspace — 2026-09-19

This section supersedes the review-file location below. The user selected:
`C:\Users\Erl\Documents\school shit\cap2\VOXGEST_AVATAR_HANDOFF\avatar_original\purchased_avatar.blend`.
Its SHA-256 before and after processing is
`3a48b881b12a1f5bb99ec1bd82a5d669db8d30a2b62b47cf02692D0E5E3BEA9D` (case-insensitive).
The original remains unchanged. Processing used a separate source copy in the safe workspace.

WORKING_FILE=LOCAL_ROOT\work\purchased_avatar_lane_20260919\purchased_avatar_CALIBRATION_WORKING_v1.blend
WORKING_FILE_SHA256=a5e730b88358a6c3bde8c7e6e076c0467e43a845bc80aaa5a923680c257a40c6
REVIEW_TOOL=BLENDER_3_2_0
CURRENT_ACTION=FSL_YES__PURCHASED_REVIEW_C8
CURRENT_FRAME=160
REVIEW_FPS=60
ACCEPTANCE=EXPERIMENTAL_REVIEW_REQUIRED

Transferred existing experimental Actions onto the purchased file's original 525-bone hierarchy:

- THANK YOU: FSL_THANK_YOU__PURCHASED_REVIEW_C7, frames 1–243.
- YES: FSL_YES__PURCHASED_REVIEW_C8, frames 1–243.
- NO: FSL_NO__PURCHASED_REVIEW_C7, frames 1–244.

Process: inspect and hash the selected source; copy it; sample existing experimental deform-bone
matrices at every frame; convert these matrices into local transforms in parent order on the original
hierarchy; key location/quaternion/scale; retain original Actions and controls; save a separate working
file; render YES frame 160; open it in Blender. The local reproducible script is
`LOCAL_ROOT\work\purchased_avatar_lane_20260919\transfer_calibrations.py`.
Private source, working blends and rendered media stay outside Git.

For this experimental visual-FK playback copy, 442 constraints and 193 object/armature drivers
are muted, shape-key drivers are muted, and bendy-bone segments are set to one. This is not restored
native-controller behavior. All original bone names and parents are retained. Missing external image
paths are cleared in this copy; materials/textures have not received appearance acceptance.
The neutral source Action is retained but the muted-control review setup is not its native playback setup.

Verification: sampled deform-bone matrix difference at frame 1, frame 160 and each final frame
is at most 0.0000047684 for each transferred Action. This verifies sampled pose transfer only.
The rendered YES frame shows a complete torso and a closed fist; a still image does not establish
smoothness, collision freedom, FSL accuracy, or export parity. Blender launched with the working file.
Use Space to play/pause. Select the other Actions in the Action Editor; set the end frame to 244 for NO.

No new CORE3 source masters or frozen solver were found in the selected purchased blend.
HELLO/MILK/RICE runtime reconstruction remains separate and explicitly non-authoritative.
No recognition changes, source overwrite, new vocabulary, or acceptance promotion occurred.

NEXT_EXACT_ACTION=Review all three transferred Actions at normal speed against their source recordings in Blender, starting with YES preparation/hold/recovery and knuckle clearance; resolve defects before export or vocabulary promotion. CORE3 source gates remain blocked pending authoritative masters and frozen solver.

Evidence: reports/AVATAR_PURCHASED_WORKING_COPY_20260919.json.
Previous checkpoint 1f28e5ec8ed642b9df5659be376deb550e990fb9 was pushed and remote-verified.
This checkpoint's delivery is verified after commit by comparing local and remote branch HEAD.

## Current review workflow — 2026-09-19

USER_REVIEW_TOOL=BLENDER_3_2_0
WEBSITE_REVIEW_REQUESTED=NO_USE_BLENDER_FOR_FUTURE_REVIEWS
CURRENT_REVIEW=YES_C8_EXPERIMENT_NOT_ACCEPTED
REVIEW_FILE=C:\Users\Erl\Documents\Codex\2026-09-16\you-are-continuing-the-voxgest-avatar\outputs\blender_review\YES_C8_REVIEW.blend
REVIEW_ACTION=FSL_YES__EXP_C8
REVIEW_FRAME=160
REVIEW_RANGE=1_TO_243_AT_60_FPS

The user explicitly requested Blender 3.2.0 instead of a website for future Avatar reviews.
A separate review copy was prepared and Blender launched with that file. It opens at the fist pose;
Space plays/pauses, Shift+Left returns to the first frame, and Numpad 0 selects the frontal camera.
No purchased source, C7 asset, recognition code, or historical CORE3 binary was modified.

C8 is a source-projected YES hand-fit experiment. The sampled fist improves on C7, but preparation/recovery
source matching and normal-speed human-motion acceptance remain open. Only 16 YES right-hand/finger
rotation channels change in its experimental GLB; timestamps, mesh attributes, THANK YOU and NO are unchanged.
No expansion, runtime-ready, Android, or linguistic PASS is claimed.

Historical CORE3 motion was recovered into a separate editable runtime-reconstruction file:
`work/core3_runtime_recovery/CORE3_RUNTIME_RECONSTRUCTED.blend` under the same safe workspace.
It contains the HELLO, MILK and RICE runtime Actions (98/120/98 frames at 60 FPS).
This is not the original source master and does not establish frozen-solver equivalence or SOURCE PASS.

The earlier C5 checkpoint bf744273c89f5d944d2d8967760b14c02c4918e0 was pushed by the user
and independently verified against remote HEAD. Its old authentication-blocked status below is historical.

NEXT_EXACT_ACTION=Review C8 YES directly in Blender against the recorded source; resolve preparation/recovery and motion gates before promoting it or expanding vocabulary. Keep original-master provenance distinct from runtime reconstruction.

## Historical C5 emergency checkpoint — 2026-09-16

UPDATED_AT=2026-09-16T20:42:00+08:00
OWNER=ASTRA_AVATAR_EMERGENCY_RECOVERY
BRANCH=avatar/astra-calibration-20260914
BASE_COMMIT=d5325c02448bfaed392b37c7052e925134317180
HEAD=THIS_CHECKPOINT_COMMIT_USE_GIT_REV_PARSE_HEAD
WORKTREE=ISOLATED_CHECKOUT_INITIALIZED_CLEAN_AT_INSTRUCTION_COMMIT
PHASE=C5_REJECTED_ROOT_CAUSES_IDENTIFIED_NO_PROMOTION

## Emergency result

C5_STATUS=REJECTED
ROOT_CAUSE_STAGE=MULTIPLE
ROOT_CAUSE=BLENDER_RETARGET_NONARM_NEUTRAL_AXIS_CONTAMINATION_AND_EXPORT_UNEVALUATED_MIRROR_AND_AUTHORING_COLOR_MASKS
BLENDER_NEUTRAL_PASS=NO
GLB_ORIENTATION_PASS=NO
ANDROID_FRAMING_PASS=NOT_TESTED
CORE3_REGRESSION=NOT_RUN
VOCABULARY_EXPANSION=STOPPED

The exact emergency prompt and all five prerequisite documents were read completely. The existing checkout and dirty work were preserved; its Git metadata could not be written in this session. The exact remote Avatar branch was cloned to a separate safe C: checkout.

## Evidence-backed findings

- Matched front/side/three-quarter views show both body halves in Blender but one in C5 GLB. Body X minimum changes from -0.214200 m to -0.000485 m. Export leaves export_apply=False while body/eyes/shoes depend on Mirror modifiers.
- Root has zero rotation/translation, uniform scale .1081087738 and no animated root tracks. No whole-model 90/180-degree yaw was found. No cosmetic orientation patch was made.
- Neutral conversion uses imported historical GLB bone bases without proving equivalence to the modified source rig. Suffix-based neutral blending includes shoulder, breast and pelvis bones, each with about 90-degree local neutral rotations. Controls/constraints were removed and 78 parents changed.
- In-memory non-arm curve ablation removes sampled lower-shirt drift: .080337 m to 0 m. No mesh weights, purchased source or candidate file changed in that test.
- Independent all-frame GLB skinning measures .088389 m maximum lower-shirt displacement for all three clips. Runtime and Blender measurements have different topology/sample sets.
- Body COLOR_0 is black at 76.645% of vertices; 42 exported authoring COLOR attributes explain the dark skin.
- Correct shoulder-to-elbow / elbow-to-wrist mapping exists in C5 code, but this is not human-motion or handedness acceptance. Finite quaternions and preserved sample timing are insufficient.

72 diagnostic views cover three C5 Actions, four sampled phases and three angles at both stages. The supplied screenshot crop lacks sign/time; its exact frame cannot be identified. Phase labels are sampling labels, not linguistic annotations. Normal-speed source comparison remains unaccepted.

## Protected state and recovery

OLD_D_DRIVE_ACCESSED=NO
RECOGNITION_MODIFIED=NO
CORE3_FALLBACK_PRESERVED=YES
PURCHASED_SOURCE_OVERWRITTEN=NO
FROZEN_SOLVER=retarget_general_B32_release_candidate_v1_NOT_RECOVERED
FROZEN_SOLVER_EQUIVALENCE=NOT_PROVEN_RECOVERED_SCRIPT_IS_FIRST_PASS
SOURCE_ASSETS=6531_RECOVERED_FILES_HASHED_THREE_BLEND_FILES_INSPECTED
SOURCE_INVENTORY_SHA256=da4c629de7a767a046ff18038fbac5e56db1439ffc46ee02c325b64719f25f17
CORE3_RUNTIME_SHA256=30f13fb65e7557992a8c3109460a790a69161e69f3ba9771a1e64a33f98294f8
C5_BLEND_SHA256=6a6101330f11460a219494c7f230a7f243f1854c04024558de4b536419755616
C5_GLB_SHA256=8d0880af0da743d05e32c4088b4d4937585f9fbe4eeed649af6b990490f25c84
C5_FROZEN_INPUTS_UNCHANGED=YES

Requested C:\VOXGEST paths are absent. Inputs were recovered from the supplied authoritative ZIP. Purchased originals and inspection copies remain outside Git. All three recovered blends lack FSL_HELLO/FSL_MILK/FSL_RICE Actions. Historical runtime is preserved, not substituted for editable masters. The included recovery audit retains its original pre-experiment timestamp; later C1-C7 experiments supersede its old statement that no calibration had yet occurred. None is accepted.

## Gates

CORE3_STATUS=BLOCKED_AT_HELLO_SOURCE_AUTHORITATIVE_ACTIONS_AND_FROZEN_SOLVER_MISSING
HELLO_SOURCE=BLOCKED
HELLO_MECHANICAL=NOT_RUN
HELLO_VISUAL=NOT_RUN
HELLO_EXPORT=NOT_RUN
MILK_SOURCE=NOT_ACCEPTED_NO_MASTER
MILK_MECHANICAL=NOT_RUN
MILK_VISUAL=NOT_RUN
MILK_EXPORT=NOT_RUN
RICE_SOURCE=NOT_ACCEPTED_NO_MASTER
RICE_MECHANICAL=NOT_RUN
RICE_VISUAL=NOT_RUN
RICE_EXPORT=NOT_RUN
RUNTIME_READY_SIGNS=NONE_CURRENTLY_REVALIDATED
REVIEW_REQUIRED_SIGNS=THANK_YOU_YES_NO_ALL_LISTEN_READY_FALSE
ANDROID_READY_SIGNS=NONE_CURRENTLY_REVALIDATED
FACIAL_NMM_STATUS=NOT_SUPPORTED_BY_CURRENT_SOURCE

C5 contains experimental THANK YOU, YES and NO, not CORE3. C6/C7 predate the emergency instruction and remain unapproved private evidence. The earlier C7 weight/camera workaround is not promoted. No further calibration or expansion occurred during the emergency audit.

ANDROID_STATUS=NOT_TESTED_C5_WAS_LOCAL_BROWSER_ONLY_NO_C5_ANDROID_BUILD
ANDROID_SOURCE_MODIFIED=NO
SAMSUNG_PHYSICAL_GATE=NOT_TESTED
TEN_CYCLE_REOPEN_GATE=NOT_TESTED
MEMORY_AND_TRANSFORM_ACCUMULATION=NOT_TESTED

Android read-only inspection located the actual CORE3 Filament/TextureView host and its camera settings, recorded in the detailed report. Do not attribute this browser screenshot to Android or infer device success from static code inspection.

## Evidence and changed files

LOCAL_ROOT=C:\Users\Erl\Documents\Codex\2026-09-16\you-are-continuing-the-voxgest-avatar
CHECKOUT=LOCAL_ROOT\work\emergency_repo
EVIDENCE_ROOT=LOCAL_ROOT\work\emergency_evidence
DETAILED_REPORT=reports/AVATAR_C5_EMERGENCY_AUDIT_20260916.md
EVIDENCE_INDEX=reports/AVATAR_C5_EMERGENCY_EVIDENCE_20260916.json
LATEST_COMPARISON=EVIDENCE_ROOT\C5_stage_comparison.jpg

CHANGED_FILES:

- reports/ASTRA_LIVE_HANDOFF.md
- reports/AVATAR_C5_EMERGENCY_AUDIT_20260916.md
- reports/AVATAR_C5_EMERGENCY_EVIDENCE_20260916.json
- reports/AVATAR_AUTHORITATIVE_RECOVERY_AUDIT_20260916.md
- reports/AVATAR_AUTHORITATIVE_RECOVERY_AUDIT_20260916.json
- tools/avatar_diagnostics/inspect_c5.py
- tools/avatar_diagnostics/diagnose_skin.py
- tools/avatar_diagnostics/compare_rest.py
- tools/avatar_diagnostics/inspect_glb.py

Executed native and imported-GLB renders, rest comparison, non-arm ablation, independent finite/time/quaternion/root/color inspection and all-frame skinning. Private media, bone geometry, source data and binary assets remain local. Git receives compact hashes/status and read-only diagnostic source only. ADR unchanged because no authoritative architecture decision changed.

## Blockers and exact next action

BLOCKERS=AUTHORITATIVE_CORE3_MASTERS_AND_FROZEN_SOLVER_MISSING;C5_RETARGET_AND_EXPORT_FAILED;ANDROID_UNTESTED;GITHUB_AUTH_UNAVAILABLE
PUSH_STATUS=BLOCKED_AUTHENTICATION_NO_REMOTE_SUCCESS_CLAIM
REMOTE_HEAD_AT_FETCH=d5325c02448bfaed392b37c7052e925134317180

Normal push cannot start Git's MSYS credential/prompt helper (signal pipe Win32 error 5). Direct native Credential Manager confirms no usable cached credential; noninteractive prompting is disabled. User was asked to sign in through Credential Manager or GitHub CLI without sharing a token in chat.

NEXT_EXACT_COMMAND=git -C CHECKOUT push origin HEAD:avatar/astra-calibration-20260914
NEXT_EXACT_TASK=After authentication, push this checkpoint and compare git rev-parse HEAD with git ls-remote origin refs/heads/avatar/astra-calibration-20260914.
NEXT_EXACT_ACTION=Recover matching frozen solver and editable CORE3 masters; establish rig-native neutral and evaluated export parity before HELLO -> MILK -> RICE -> neutral, then Android normal-speed/repeat/device validation. Keep C5/C7 rejected and expansion stopped.
CLAIMS_STILL_PROHIBITED=C5_FIXED;CURRENT_CORE3_PASS;LISTEN_READY;ANDROID_READY;DEVICE_PASS;FSL_LINGUISTIC_CERTIFICATION;UNVERIFIED_PUSH_SUCCESS
