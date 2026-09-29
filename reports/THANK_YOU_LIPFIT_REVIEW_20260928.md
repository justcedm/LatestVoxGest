# THANK YOU local lip-relative correction V2

This is a new engineering review derivative, not an exportable or FSL-approved Action. The current user instruction permits engineering comparison while source mapping remains `PENDING_REVIEW`. It supersedes the previous checkpoint's stricter stop before changing the contact target. No SOURCE PASS is claimed.

## History and scope

Preserved local checkpoint `54d14a25a03133dd374b74b932f5db53f4adb740`. Committed intervening all-word diagnostic scripts as `3cf6133`, then merged remote `4d28eeda98e30b5406f9dcf4db59492224690b47` normally in `553ea38`. Read the new remote status, survey protocol and remote live handoff completely. No reset, force push, source overwrite or recognition/UI edit occurred. Normal push still failed with authentication/helper error; remote delivery is not verified.

The earlier broad audit completed 19 clips / 4,216 frames: finite skinned world vertices and identical endpoint extents, with Core3 platform/animation data retained. All 19 front review movies were rendered at 60 FPS privately. These checks do not establish absence of intersections or FSL accuracy. Remaining words are now parked under the THANK-YOU-first instruction. See `ALL19_REVIEW_SUMMARY_20260928.json` for aggregate scope.

## Source and defect

Reference metadata identifies `clips/7/0.MOV`; SHA256 `fc0003d10b31d6e04e0effca02a288b0d05135ac2cbc15a1f94cd07fa8641ea4`, 243 frames / 60 FPS. The owner/reviewer has not confirmed the vocabulary mapping. SOURCE=`PENDING_REVIEW`, not PASS. Image-plane comparison can still identify the below-chin mismatch.

At frame 153, original middle-tip joint heights were 1.347974 m (left) and 1.357188 m (right), versus upper-lip anchor height 1.406904 m. The fingertips were also about 0.194 m in front of the upper-lip anchor. These are bone-anchor measurements in the unchanged Core3 world convention, not signed skin distances or proof of contact.

## Correction method and decisions

1. Keep the canonical Core3 character, bind pose, skeleton hierarchy, armature transform and all original Actions. Make a copy of THANK_YOU named `THANK_YOU__LIPFIT_REVIEW_V2`.
2. Use source X/Y only: mouth landmarks 9/10 and middle fingertip 12 per hand, preserving the original 640×360 aspect ratio. Map mouth-relative image displacement using the median source mouth width over frames 140–166 and Core3 upper-lip width. Scale is 0.00181836 m per source pixel. This is a bounded engineering projection model, not a validated anatomical calibration or new vocabulary decision.
3. Apply a small temporal filter (Gaussian sigma 1 frame) only to the measured two-dimensional target offsets. Existing finger/wrist curves are not globally smoothed. Full target interval 140–166, smooth local ramps 129–139 and 167–179; zero correction before 129 and after 179. Original 243-frame timing and neutral entry/exit remain intact.
4. Treat depth as an explicit hypothesis: middle-tip bone target 15 mm in front of upper-lip anchor. Raw landmark depth is unused. The surface audit must reject this target if it creates penetration; 15 mm is not claimed as observed source contact or skin clearance.
5. Solve a two-link shoulder–elbow–wrist chain analytically for each hand. Keep shoulder positions and measured limb lengths fixed; select the elbow bend closest to the existing elbow plane. Reject unreachable targets rather than stretch. Rotate both upper-arm segments and both forearm segments coherently; transport the wrist/finger subtree while preserving wrist world orientation and individual finger articulation. No whole-character movement, object-scale correction, or global neutral change.
6. Maximum wrist displacement is 0.193542 m left / 0.191436 m right, mostly correcting the depth discrepancy. Limb-length error is below 2.3e-16 m in the fit. This is a substantial local depth correction and remains a review hypothesis despite its exact length preservation.
7. Convert fitted world poses into the imported Blender bone bases using measured per-bone basis offsets, then key a separate Action. All original Action curve/key/interpolation/handle hashes remain unchanged. The new Action changes 138 arm/hand bones; other Actions retain their original curves.

## Verification and review

Baseline imported-bone basis error: 4.7684e-7. Reopened/evaluated corrected Blender Action: all 243 frames × 238 bones agree with fitted poses within 9.5368e-7 maximum world-matrix component error. Original source files remain read-only. Core3 HELLO/MILK/RICE original Blender curve hashes remain identical, supplementing prior runtime-byte parity.

Front, side and perspective samples at frames 128, 140, 153, 166 and 179 were rendered. Frame 153 visually improves the lip-relative placement with a coherent elbow bend. These sampled views are not full-speed human-motion acceptance. The full 243-frame source/candidate comparison was generated at 60 FPS, with source/candidate frame counts verified equal. Review preparation, contact interval and recovery at normal speed before any visual PASS.

The surface checker evaluates fingertip and wrist vertices against the full selected head triangle surface for every frame 129–180. It reports exact point-to-triangle nearest distances and signed-nearest-normal penetration flags. This is not a watertight solid-intersection proof, does not establish all finger self-collisions, and cannot certify FSL. See the final checkpoint for its results; export stays blocked while any gate is pending or failed.

## Private deliverables

Relative to the outer Earle workspace:

- `outputs/core5_survey_20260928/thank_you_lipfit_blender_v2/THANK_YOU_CORE3_LIPFIT_REVIEW_V2.blend` — 523,179,924 bytes; SHA256 `0c36e0d8d2be0641b3a084221768e500df0f0149c9feef86c30ea13e5eeedb14`.
- `outputs/core5_survey_20260928/thank_you_lipfit_v2/THANK_YOU_LIPFIT_SOURCE_COMPARISON_60FPS.mp4` — 1,272,915 bytes; SHA256 `27c54eb41f2bc3202ebd0faa2f002dfa64c688f31bdb244ab7ae1368a12f0f4d`.
- `outputs/core5_survey_20260928/thank_you_lipfit_v2/private_pose_fit.npz` — private fitted pose data, MUST NOT enter Git.
- Private numerical/build/parity reports and view evidence remain beside these derivatives. Public Git contains only aggregate evidence, scripts, hashes and instructions.

No GLB was exported and no survey handoff ZIP was created. YES/NO/UNDERSTAND remain blocked behind THANK YOU's acceptance gates; owner Samsung testing remains pending; `listen_ready=false`.

## Reproduce

From the clean Avatar checkout, use Python 3.11/numpy/scipy and Blender 3.2.0. Each script requires a new output path and refuses overwrite. Set `OPENBLAS_NUM_THREADS=1` for predictable local CPU use.

```text
python tools/avatar_survey/fit_thank_you.py --glb EXISTING_CORE3_PLUS9 --raw PRIVATE_RAW225 --out NEW_PRIVATE_FIT_DIR
blender --background --factory-startup --disable-autoexec --python-exit-code 1 --python tools/avatar_survey/build_lipfit_review.py -- --blend EXISTING_CORE3_REVIEW --fit NEW_PRIVATE_FIT_DIR/private_pose_fit.npz --out NEW_PRIVATE_BLEND_DIR
python tools/avatar_survey/check_lipfit.py --glb EXISTING_CORE3_PLUS9 --fit NEW_PRIVATE_FIT_DIR/private_pose_fit.npz --out NEW_PRIVATE_FACE_REPORT.json
blender --background --factory-startup --disable-autoexec --python-exit-code 1 --python tools/avatar_survey/verify_lipfit_blender.py -- --blend NEW_PRIVATE_BLEND --fit NEW_PRIVATE_FIT_DIR/private_pose_fit.npz --out NEW_PARITY_REPORT.json
```

Use `render_review.py` with `--action THANK_YOU__LIPFIT_REVIEW_V2`, then `compare_video.py`, for matching 60 FPS evidence. Review cameras only change evidence framing; they are not part of the fitted pose or an alignment fix.
