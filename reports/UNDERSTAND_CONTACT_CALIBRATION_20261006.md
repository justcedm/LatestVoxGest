# UNDERSTAND contact calibration — 2026-10-06

## Scope and preserved inputs
Continue from commit6dbc5d8 and private UNDERSTAND V9. No original Core3 GLB, runtime candidate, source clip, V9 Blender file, recognition code or production LISTEN changes. Source remains FSL-105 clips/10/0.MOV (245 frames at60FPS), with qualified FSL approval pending. New private output root: outputs/understand_contact_review_20261006_v1 relative to Earle's workspace root, outside the Git checkout.

## Evidence and decisions
Closeups at frames11/79/125/195/220/235 cover front/side/oblique views. Frame79 clearly shows right fingers entering skirt/hip geometry. V9's13 hand/torso flags therefore cannot be dismissed as harmless finger contact. The28 distal finger/thumb flags are a different problem: source occlusion and curled-hand configuration make intent uncertain. No blind finger separation or linguistic approval was applied.

V10 trial1 uses20mm outward/15mm forward offsets and removes12/13 body flags. Trial2 uses30mm/20mm and removes all13. Selected V10 retains the smaller left correction and uses30mm/20mm on the right. A new independent clothing test exposes9 remaining hand/skirt flags at frames10,11,78,79,230–234. V10 is preserved but superseded for clearance testing.

V11 trials1–3 rejected unreachable arm targets rather than stretching the limb. Trial2 (40mm/25mm) and trial3 (35mm/20mm) fail the strict reach test at frame73; trial1 lacked frame logging, so its precise failed frame is unknown. No NPZ/Blender asset was written for those failed fits. V11 trial4 adds a3mm wrist lift during right preparation with40mm outward/25mm forward. Right neutral transitions use20mm outward/15mm forward. Left offsets remain20mm/15mm. All are bounded engineering clearance offsets, not inferred FSL contact or source depth.

The correction uses fixed-length two-bone IK with the closest original elbow bend plane. Wrist world orientation and individual finger articulation are preserved. It changes only neutral transitions/preparation: left frames2–24 and222–244; right frames2–24,61–94 and222–244. Source signing frames96–220 remain exactly V9. Frames1/245 and root matrices remain exact. No global smoothing, scale, camera compensation or timing change. Source head tilt/facial expression remains unimplemented.

## Validation and limits
V11 trial4: zero tested hand/torso and zero hand/skirt/tshirt triangle-overlap flags across245frames.28 finger/thumb flags remain. Triangle crossing checks do not certify enclosed-volume clearance or all rig deformation. Face window96–219 is unchanged from V9. The inherited maximum adjacent local rotation is17.467174 degrees at106–107 on RT_DEF-f_middle.03.R_03. Exact matrix comparisons confirm finite transforms, neutral and stroke preservation. Maximum added world displacement47.265mm. Local translation values use rig units, not metres. Numerical checks do not grant visual, FSL or Android acceptance.

The Action integrity digest now uses bulk reads for numeric key/handle data and individual enum identifiers. It protects the same fields as before but has a new hash schema; v1/v2 digest strings cannot be compared directly. A Blender test verifies equal-copy equality and sensitivity to9 property changes. Bulk enum reads were rejected because Blender3.2 returned unreliable handle-type values. Original Actions are compared before/after with the same schema within each build.

## Current gates
SOURCE=PENDING_QUALIFIED_REVIEW
MECHANICAL=PARTIAL_BODY_CLOTHING_CLEARANCE_ONLY_FINGER_REVIEW_PENDING
FULL_SPEED_VISUAL=PENDING
FSL_APPROVAL=PENDING
OWNER_DEVICE=V11_NOT_TESTED
EXPORT=NOT_CREATED
LISTEN_READY=false

THANK YOU onset timing mismatch remains open. Phrase references remain missing. The shipped12-action GLB remains SHA256 1f566e98e020b8e46e8a625c3910794be86a2017392093950f467bad68c7689b.

NEXT_EXACT_ACTION=Inspect right middle/thumb frames121-131 and195-196, index/thumb211-225 with exact source and mesh closeups; classify contact versus penetration before any handshape derivative. Review V11 full-speed transitions, then repeat gates before GLB export.

## Completed private review artifacts

V11 Blender parity PASS (245frames/238bones, max matrix error8.9407e-7). All13 original Action hashes unchanged with schema v2. V9/source/runtime hashes reverified unchanged. Source comparison is245frames at60FPS; human acceptance remains pending. Exact paths, sizes and hashes are in the companion JSON. V9 clothing flags28 -> V10 clothing flags9 -> V11 clothing flags0. Body flags13 ->0; finger flags28 unchanged.
