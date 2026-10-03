# VoxGest Non-Sign Capture Protocol

Status: specification only. This document does not authorize capture, training,
or a change to the deployed recognizer.

## Purpose and label boundary

This corpus measures false acceptance and calibrates rejection/out-of-distribution
(OOD) behavior. Its categories are audit annotations only. They must never be
added to the FSL vocabulary, displayed as semantic words, or interpreted as
Filipino Sign Language.

For every clip, the expected accepted semantic-token count is zero. A classifier
may produce a raw top-1 internally; the acceptance gate must reject it.

## Capture matrix

Capture each condition independently, with a visible neutral state before and
after the behavior:

| Audit category | Required behavior | Boundary to preserve |
| --- | --- | --- |
| `NONSIGN_IDLE` | Natural seated/standing idle posture | Hands may rest naturally; no prompted sign |
| `NONSIGN_OPEN_PALM` | Open palm held still | Vary anatomical hand, height, orientation, and distance |
| `NONSIGN_RANDOM_WAVE` | Unscripted hand waving | Vary speed and path; do not imitate a known sign |
| `NONSIGN_FIXING_HAIR` | Adjust or smooth hair | Natural action, not staged sign movement |
| `NONSIGN_TOUCHING_FACE` | Touch cheek, chin, nose, or forehead | Vary hand and duration |
| `NONSIGN_POINTING` | Point at nearby locations/objects | Vary direction and arm extension |
| `NONSIGN_HAND_ENTERING` | Hand moves from fully outside to inside frame | End without a sign |
| `NONSIGN_HAND_LEAVING` | Hand moves from inside to fully outside frame | Begin without a sign |
| `NONSIGN_PARTIAL_SIGN` | Start, then stop before completing a prompted target | Target and cutoff require qualified FSL review |
| `NONSIGN_INCORRECT_SIGN` | Deliberately malformed prompted target | Must not accidentally form another valid FSL sign |
| `NONSIGN_SPEECH_GESTURE` | Natural gesturing while speaking | Use unscripted conversational motion |

No-hands frames are included within `NONSIGN_IDLE`; they may also be tagged as
the secondary condition `NO_HANDS_VISIBLE` for analysis.

## Minimum collection design

- At least 10 consenting participants, with variation in skin tone, clothing,
  hand dominance, age range, and signing experience documented without storing
  unnecessary identity data.
- Two sessions per participant on different days or in materially different
  environments.
- At least five independent clips per participant/session/category. Do not split
  one continuous recording into near-duplicate trials.
- Use the physical Samsung deployment camera as a mandatory domain, plus at
  least one additional Android camera if available.
- Capture front-camera portrait and landscape only if both are supported by the
  deployed application. Record the camera orientation explicitly.
- Include bright, dim, backlit, and mixed lighting; simple and cluttered
  backgrounds; near, nominal, and far signing distances.
- Record the native camera stream without display mirroring. Preview mirroring
  is presentation-only; saved/inference landmarks remain canonical and
  unmirrored with anatomical left/right slots unchanged.

## Trial procedure

1. Assign a random pseudonymous participant ID and new source-video ID.
2. Frame the participant using the same camera path as deployment.
3. Record 1–2 seconds of neutral state.
4. Prompt exactly one audit behavior; do not show or speak an FSL class name
   except for reviewer-controlled partial/incorrect trials.
5. Record the behavior once, followed by 1–2 seconds of neutral/release.
6. Save the untouched raw clip outside Git and calculate SHA-256.
7. Independently annotate behavior boundaries and whether any known sign may
   have occurred. Ambiguous clips are quarantined, not silently relabeled.

## Required metadata

Each source video records: pseudonymous participant ID, session ID, source-video
ID, audit category, secondary condition, device/camera, resolution, FPS,
orientation, lighting, background, distance band, handedness if volunteered,
prompt ID, capture timestamp, consent record reference, raw SHA-256, reviewer
status, and exclusion reason. Never store names, contact details, or secrets in
the dataset manifest.

## Quality and review gates

- Decode every frame and verify declared versus decoded frame count.
- Run the same canonical MediaPipe/feature path as deployment and retain exact
  missing-landmark and freshness evidence.
- Reject from calibration (without deleting) corrupted, truncated, duplicated,
  consent-invalid, or label-ambiguous clips.
- Exact hashes must not cross partitions. Near-duplicate and same-continuous-take
  clips stay in one partition.
- Partial and incorrect-sign clips require qualified FSL review before use;
  computer vision inspection cannot establish linguistic invalidity.

## Split and evaluation policy

Create participant-disjoint train/validation/test partitions before any model or
threshold tuning. Source-video and session overlap across partitions must be
zero. Keep the held-out test sealed until thresholds and candidate selection
are frozen.

Report false-accept rate overall and per audit category, accepted-token count per
minute, duplicate-event rate, raw top-1/confidence/margin distributions, exact
rejection reasons, and time-to-rearm. Passing requires zero user-facing semantic
tokens in the professor-facing physical negative suite; a broader statistical
threshold must be set before collection and reported separately.

## Storage and governance

Raw clips, extracted frames, landmarks, face-bearing evidence, and per-frame logs
remain in an access-controlled external dataset workspace and out of Git. Git may
contain only the protocol, de-identified aggregate counts, hashes where safe,
and concise audit reports. Retention/deletion follows participant consent and
institutional policy; no file is deleted as part of an audit.
