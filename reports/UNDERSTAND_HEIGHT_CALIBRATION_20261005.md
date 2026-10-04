# UNDERSTAND V9 review checkpoint - 2026-10-05T00:41:49.706503+08:00

Saved a separate UNDERSTAND__EYE_HEIGHT_REVIEW_V9 Blender Action and245-frame/60FPS source comparison. V8 is preserved and rejected for elevated elbow/sleeve deformation. V9 keeps the same source-relative height and uses70mm forward clearance to obtain a lower elbow; this depth is a hypothesis requiring review. All13 pre-existing Action curve/key/interpolation/handle hashes match, including HELLO/MILK/RICE and THANK YOU V4. Blender pose parity PASS across245frames/238bones (max matrix error8.35e-7).

SOURCE=PENDING_REVIEW; MECHANICAL=BLOCKED_CONTACT_CLASSIFICATION_AND_DEFORMATION_REVIEW; FULL_SPEED_VISUAL=PENDING; EXPORT=NOT_CREATED; LISTEN_READY=false.41 inherited collision frame/pair flags remain, with none added. No nearby signed face flags; minimum tip/face distance14.42mm in frames96-219. These diagnostics exclude clothing and do not certify inside-volume clearance. Source head tilt/facial expression remains unmatched. Existing finger local-translation discontinuities are retained and require visual review; values are local rig units, not metres.

Sampled front/side and comparison frames80,106,140,196,218,245 inspected: fingertip height improved; elbow below shoulder at160; sleeve/shoulder and curled-finger appearance need closer review. No full-speed human acceptance claimed. Previous GLB/ZIP unchanged. THANK YOU onset mismatch remains open; all3 survey phrase references remain missing. Recognition and production LISTEN untouched.

Private artifacts and SHA256 are in reports/UNDERSTAND_HEIGHT_CALIBRATION_20261005.json. Only scripts and aggregate reports enter Git. Authority read: origin/main98682a8, including FINAL_SURVEY_DEMO_INTEGRATION_PLAN_20261003.md.

NEXT_EXACT_ACTION=Classify inherited hand/torso contacts at frames10-12,76-82,234-236 and finger contacts121-196,211-225 using close-up multi-view deformation; review V9 full-speed source comparison before a further derivative or export.

---

# Current V9 trial

V8 preserved but rejected on sampled visual review: elbow rose above shoulder at frame160 (elbowY1.236m; shoulderY1.224m), with conspicuous sleeve deformation. V9 adds a bounded70mm forward wrist target during the same height-fit envelope. This is a clearance hypothesis, not depth reconstructed from the video. Frame160 elbow nowY1.199m, below the unchanged shoulder. It retains the same eye-relative height target, wrist orientation and finger articulation. All245frames remain; no global smoothing or scaling. V9 collision and normal-speed visual gates are pending complete interpretation, not approved.

---

# UNDERSTAND eye-relative height review V8 â€” 2026-10-05

User requested continued word calibration. Latest owner issue: UNDERSTAND signs too low. Current private dataset reference clips/10/0.MOV (245 frames/60FPS) resolves to UNDERSTAND in the recovered manifest. This is engineering source comparison; no new qualified linguistic approval was supplied.

## Evidence and local correction

Source hold shows the raised index finger above eye level; existing Core3-derived candidate places the fingertip below the mouth. At frame160 the old fingertip is95.319mm below model eye center. New source-relative target is42.039mm above it. No global rig/camera/body shift is used.

A new UNDERSTAND__EYE_HEIGHT_REVIEW_V8 Action is derived from the preserved candidate. Source image-plane index-tip height relative to both eyes is scaled by model eye separation divided by median source eye separation during frames120â€“190 (0.002713961 metres per source pixel). Only target Y is changed; lateral and depth paths, wrist world orientation and individual finger articulation are retained. One-frame sigma smoothing applies only to measured target XY before selecting Y, not to the Action curves. Source landmark depth is not used.

A smoothstep weight ramps over95â€“120 and190â€“220; actual changes are96â€“219. Fixed-length two-bone IK solves shoulder/elbow/wrist coherently, with the original bend plane nearest the target. Max wrist shift146.563mm; limb-length error below2e-16m. Left hand, neutral endpoints and original duration are exact. Source head tilt is not recreated, so eye-relative projection is an engineering approximation requiring review, not complete source equivalence.

## Mechanical findings

All matrices finite. Maximum adjacent local rotation remains17.467174 degrees at106â€“107 on a middle-finger bone. No new collision frame/pair categories: both baseline and V8 have41 records across245frames and27 tested pairs per frame. These include left hand/torso(6), right hand/torso(7), middle/thumb(13) and index/thumb(15). Curled finger contact must be distinguished from unwanted penetration; no automatic acceptance or blind finger separation was performed. Existing body/contact defects remain unresolved.

No hand/face triangle-overlap flags were found. The initial signed-distance heuristic produced124 false-positive candidate flags because it compared the distant resting left hand against the open head surface. A new diagnostic limits signed flags to vertices within25mm of the head surface and finds zero flags; the original output is preserved. Minimum sampled tip/face distance is8.396mm over96â€“219. These are scoped diagnostics, not watertight-volume collision certification.

## Preserved state and gates

Core3 HELLO/MILK/RICE, THANK YOU V4, all originals and reviewed derivatives remain separate. No recognition or production LISTEN changes. Latest main authority98682a8 defers sentence/A-Z expansion and keeps candidate expert review separate from runtime success. Survey phrase references remain missing.

SOURCE=PENDING_FSL_REVIEW
MECHANICAL=PENDING_CONTACT_AND_DEFORMATION_REVIEW
FULL_SPEED_VISUAL=PENDING
EXPORT=NOT_CREATED
LISTEN_READY=false

THANK YOU onset timing mismatch remains unresolved; no timing evidence was supplied to justify a new THANK YOU derivative.

## Reproduction

Read exported private GLB SHA1f566e98e020b8e46e8a625c3910794be86a2017392093950f467bad68c7689b and UNDERSTAND raw225 reference. Run fit_understand_height.py into a new private output directory. Run check_hand_intersections.py --include-body on baseline and fitted poses; blender_face_clearance.py --start-frame96 --end-frame219; build_action_pose_review.py --base-action UNDERSTAND --action UNDERSTAND__EYE_HEIGHT_REVIEW_V8; then verify_lipfit_blender.py with the same base/new Action names. Generate normal-speed movie and source comparison. All originals remain immutable; fitted matrices and media remain outside Git.

NEXT_EXACT_ACTION=Review V8 at source speed, especially height during120â€“190 and existing collision frames; resolve body/finger contacts before runtime export. Qualified FSL approval and Samsung retest are still required.
