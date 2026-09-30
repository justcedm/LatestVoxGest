# FSL survey phrase reference request V1

Date: 2026-10-01. Recipient: project-authorized FSL-qualified/reference signer and reviewer.
Status: all three full-phrase references MISSING in the searched local reference packages. This packet is a request, not a translation or approval record.

## Request

Please formulate and record each complete semantic intent below in natural FSL. English and Filipino text describe the meaning only. You determine the actual order, grammar, transitions, timing and non-manual features. Do not follow spoken-language word order merely to match the script. If a meaning is ambiguous, note the question before recording; for example, explain how the survey's rating task should be conveyed.

Record THREE clean takes of EACH phrase (nine recordings total), using the same qualified/reference signer for the three takes of a phrase. A separate explicit reviewer decision must identify the approved take by filename and SHA256 before that phrase is calibrated. Do not pre-fill approval.

### SURVEY_INTRO

Target Action ID: `FSL_PHRASE_SURVEY_INTRO_V1`

English meaning: Hi! We are a group of IT students making/developing a system about Filipino Sign Language.

Filipino meaning: Hi! Mga IT students kami at gumagawa kami ng system tungkol sa Filipino Sign Language.

Requested takes: 01, 02, 03. Current reference status: MISSING.

### FSL_LEARNING

Target Action ID: `FSL_PHRASE_FSL_LEARNING_V1`

English meaning: We don't know much about FSL yet, but we are eager to try and learn.

Filipino meaning: Hindi pa kami gaanong marunong sa FSL pero gusto naming matuto at subukan.

Requested takes: 01, 02, 03. Current reference status: MISSING.

### TRY_AND_RATE

Target Action ID: `FSL_PHRASE_TRY_AND_RATE_V1`

English meaning: Can you please try our system and rate it?

Filipino meaning: Pwede mo bang subukan ang system namin at i-rate ito?

Requested takes: 01, 02, 03. Current reference status: MISSING.

## Capture requirements

- Front-facing; upper body fully visible, including elbows, wrists and both hands throughout the complete signing sequence.
- Full face visible; avoid occlusion, clipping and mirrored exports. Record whether the camera preview/export is mirrored.
- Stable camera, plain background and even lighting; 1080p preferred, 30 or 60 FPS, no digital zoom. Landscape is convenient if it preserves all signing space.
- Neutral lead-in, natural signing speed and neutral lead-out. Do not artificially slow or shorten the phrase.
- Keep each take as an uninterrupted original recording. Preserve original timing and do not replace the original with an edited/slowed copy.

## Per-take metadata

Complete one record in FSL_SURVEY_PHRASE_TAKE_METADATA_TEMPLATE_V1.json for each of the nine takes:

PHRASE_ID, SIGNER_ALIAS, TAKE_NUMBER, RESOLUTION, FPS, RECORDING_DATE, REVIEWER_STATUS, CONSENT_SCOPE, FSL_NOTES.

Also record SOURCE_FILENAME, SOURCE_SHA256, MIRRORED_EXPORT, REVIEWER_ALIAS, REVIEW_DATE and APPROVAL_EVIDENCE_ID. Signer alias avoids publishing personal identity. In FSL_NOTES, record the reviewer-provided meaning/gloss notes, ambiguities, signing boundaries, intentional contact, important head/body/eyebrow/mouth features and any fingerspelled token, in its actual observed order. Record source timestamps for all such annotations.

REVIEWER_STATUS starts as PENDING, never APPROVED by default. Approval must explicitly cover the full phrase meaning and identify the exact take. Capture quality acceptance is separate from linguistic approval. If rejected, preserve the take and record the reason; request a new version rather than overwrite it.

CONSENT_SCOPE must specify permitted private project use, calibration, avatar derivative generation, reviewer sharing, survey display and any publication/redistribution restrictions. Absence of permission is not permission to publish. Keep consent records and recordings in the approved private channel. Do not commit private recordings, identity details or consent documents to Git unless explicitly authorized.

## Naming and private transfer

Suggested name: `<PHRASE_ID>_<SIGNER_ALIAS>_TAKE01_<YYYYMMDD>.mp4` (or original MOV extension). Repeat for TAKE02 and TAKE03. Send privately to the project owner/Earle using the approved project transfer method. This document does not send a message or publish any recording.

Keep an immutable source copy; record SHA256 on receipt. A safe public manifest may reference an opaque source ID and hash, but must not embed recordings or private identity/consent details. A missing reference received without approval is AVAILABLE_PENDING_REVIEW, not AVAILABLE_APPROVED.

## Fingerspelling questions for the signer/reviewer

Does the approved realization actually fingerspell IT, FSL, VOXGEST or another technical/proper term? If yes, annotate its literal token and source time. Only then add the observed slot, e.g. FINGERSPELL("IT"), FINGERSPELL("FSL") or FINGERSPELL("VOXGEST"), at the reviewed position. These are conditional notation examples, not an invented phrase plan. A-Z calibration is a separate lane; do not substitute ASL assets or temporary letter handshapes.

## What happens after approval

Once at least one full take for a phrase is explicitly approved, analyze preparation, strokes, holds, transitions, recovery, handshape, finger articulation, location, palm orientation, shoulder/elbow/wrist trajectories, inter-hand relationships, head/body motion and non-manual features. Preserve original timing. Build only a new versioned Action on unchanged Core3, with HELLO/MILK/RICE preserved.

Run finite/quaternion/translation/scale audits, finger/inter-hand/face/body collision checks, deformation, neutral start/return and Core3 regression. Compare the whole source/candidate at normal speed; log each mismatch by phrase, source/candidate frame and time, body region, expected behavior and observed behavior. Stills cannot approve motion. Document any unsupported non-manual feature before judging equivalence.

Export only candidates passing mechanical and source-motion review. Required package: candidate GLB, phrase_manifest.json, source_provenance.json, SHA256SUMS.txt, calibration_report.md and Android integration notes. Keep licensed/private binaries outside Git. Owner Samsung testing and final qualified FSL review remain required; listen_ready=false until all gates pass.

## Mapua and other isolated references

Mapua is NOT required for the current missing-reference gate. Licensed raw clips may supplement only documented isolated lexical classes actually present. Landmark/.npy arrays support engineering analysis only. Neither supplies the grammar of these three messages. Do not concatenate isolated clips into a survey phrase.

## Existing defects stay open

THANK YOU: reported onset mismatch requires applied-animation timing/recording correlation; V4 preserved as rollback.
UNDERSTAND: owner reports incorrect signing height; reference/FSL correction remains required.
Neither defect is resolved or hidden by this phrase-reference request.
