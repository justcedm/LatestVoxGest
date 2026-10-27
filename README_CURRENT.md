# VoxGest — Current Project Status

**Last reconciled:** 2026-10-28  
**Current authoritative handoff:** [Gemini Master Handoff](docs/GEMINI_MASTER_HANDOFF_20261028.md)  
**Documentation index:** [docs/README.md](docs/README.md)

## Project in one paragraph

VoxGest is a BSIT capstone Android accessibility prototype exploring bidirectional communication between Filipino Sign Language (FSL) users and hearing/non-signing people. The sign-to-hearing path uses camera capture, MediaPipe pose/hand landmarks, a temporal classifier, an acceptance/rejection layer, and text/speech output. The reverse path maps supported speech/text concepts to prepared, verified avatar animations where available. It is a bounded prototype, not an unrestricted continuous FSL translator.

## Current survey profile

- **Profile name:** Core20 (inherited name; current classifier output count is **19 classes**).
- **Labels:** HELLO, UNDERSTAND, NO, YES, THANK YOU, GOOD MORNING, GOOD EVENING, GOOD AFTERNOON, HOW ARE YOU, IM FINE, NICE TO MEET YOU, YOURE WELCOME, SEE YOU TOMORROW, DON'T UNDERSTAND, KNOW, DON'T KNOW, CORRECT, SLOW, FAST.
- **Standard feature contract:** `[1,20,225]` input, `[1,19]` output; 99 pose + 63 anatomical-left-hand + 63 anatomical-right-hand features per frame.
- **Dataset constraint:** FSL-105 Mendeley Version 1 only for this track; do not mix FSL TUTOR or unrelated sources into the standard experiment.
- **Legacy Core5:** separate five-class profile; preserve it and do not treat its outputs/metrics as Core20.

## Current blocker — camera preview

The user reports that the front-camera preview on the physical Samsung Galaxy A56 5G (SM-A566B, Android 16) **still appears mirrored**, despite prior code-level and screenshot checks reporting an unmirrored preview. Treat this as unresolved until reproduced and physically confirmed on the main app's survey SIGN screen. Do not fix by blindly flipping model features or MediaPipe handedness. Trace preview, analysis, and overlay transforms separately. See the master handoff for the narrow debugging protocol.

## Evidence status

Core20 V2 engineering metrics have been reported in recent local work, but the exact V2 model/report and APK must be verified before quoting them as results of the currently installed main-app survey build. The GitHub `main` branch's last visible commit before this documentation update was 2026-10-03; later local/device work may not yet be pushed. Do not assume remote code matches the latest installed APK.

## Local project workflow

1. Inspect `git status`, branch, recent commits, remotes, active manifest/labels, installed package, and APK hash before edits.
2. Build from `android_dry_run` only after verifying the current worktree.
3. Run unit tests and build, then physically verify the change on the Samsung. A successful build alone does not verify camera orientation or recognition.
4. Preserve Core5, `demo10`, experimental profiles, held-out test data, and the approved manuscript. Do not retrain until recordings and the active runtime are audited.

## Important links

- [Gemini Master Handoff](docs/GEMINI_MASTER_HANDOFF_20261028.md) — project context, exact contracts, limitations, camera debugging, survey, and ready-to-paste Gemini instructions.
- [Documentation index](docs/README.md)
- [Final Survey Demo Integration Plan (2026-10-03)](docs/FINAL_SURVEY_DEMO_INTEGRATION_PLAN_20261003.md)
- [Core5 Final Validation and Production Integration (2026-10-03)](docs/CORE5_FINAL_VALIDATION_AND_PRODUCTION_INTEGRATION_20261003.md)
- [ML Engineer Knowledge Base](docs/ML_ENGINEER_KNOWLEDGE_BASE.md)
- [Avatar Pipeline](docs/AVATAR_PIPELINE.md)
- [Legacy README_CURRENT](docs/README_CURRENT_LEGACY_20261028.md)
- [Legacy Android Developer Handoff](docs/ANDROID_DEVELOPER_HANDOFF_LEGACY_20261028.md)
