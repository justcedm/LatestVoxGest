# VoxGest Production Avatar Upper-Body + Gesture Recognition Panel Plan — 2026-09-30

## Avatar production framing decision

The current full-body Avatar Trial framing is retained only as an engineering diagnostic mode.

The production VoxGest LISTEN/Avatar view should use an **upper-body framing** by default:
- head fully visible;
- shoulders, arms, hands, torso visible;
- crop approximately around lower torso/waist;
- no legs/shoes in the normal production view;
- active hands must never be clipped.

The camera/framing solution must remain derived from the avatar/world bounds and current viewport aspect ratio. Do not scale the skeleton or GLB to achieve framing.

Preferred behavior:
- default upper-body fit centered around torso/sternum;
- 5–10% safe margins around head and lateral hand reach;
- maintain a hands-visible constraint;
- if an action extends outside the upper-body safe region, zoom out only enough to keep the active hands visible, but do not fall back to full-body unless an action genuinely requires it;
- smooth camera transitions; no camera pumping during normal signing;
- diagnostic Avatar Trial may keep a full-body toggle.

## Production Avatar integration scope

Production LISTEN should use:
- canonical Core3 character/skeleton;
- approved/candidate actions through the canonical action registry;
- bilingual semantic aliases;
- sentence/phrase sequencer;
- fingerspelling fallback when validated.

Keep `listen_ready=false` until FSL/source validation and owner-device approval gates pass.

## Gesture Recognition Panel — product goal

The Sign panel should become a compact production surface rather than a diagnostic console.

Recommended layout:

1. Camera/landmark viewport
2. Current candidate/recognized sign
3. Confidence/status feedback
4. Recognition state
5. Output message/TTS controls
6. Minimal capture guidance

### Visual hierarchy

Top:
- SIGN title
- recognition state indicator: READY / TRACKING / RECOGNIZING / ACCEPTED / REJECTED

Center:
- camera preview
- smooth landmark skeleton overlay
- framing guide / safe signing zone

Bottom:
- recognized text card
- confidence or certainty indicator
- clear/replay/speak controls
- concise feedback such as:
  - Move back
  - Keep hand visible
  - Sign again
  - Unsupported sign
  - Recognized: HELLO

## Recognition architecture behind the panel

Production path should not expose raw diagnostic complexity.

Conceptual pipeline:

Camera
-> MediaPipe pose + hands
-> timestamped raw landmarks
-> feature extraction
-> temporal classifier
-> semantic geometry
-> OOD / unsupported-sign rejection
-> temporal/event logic
-> accepted token
-> UI + TTS

Keep the diagnostic recognition-lab separate.

## Panel states

IDLE
- camera ready
- no sign underway

TRACKING
- hands/pose detected
- quality sufficient

RECOGNIZING
- active temporal window/event

ACCEPTED
- recognized supported sign
- text/TTS available

REJECTED
- unsupported or insufficient evidence

QUALITY_WARNING
- poor framing / missing hand / unstable tracking

TIMEOUT
- diagnostic fallback only; production UI should present a retry message rather than raw EVENT_TIMEOUT wording.

## Debug vs production

Recognition Lab:
- raw probabilities
- event reason
- tensors/hashes
- timestamps
- marker controls
- model selector
- diagnostic logs

Production Sign panel:
- no raw model hashes
- no model selector
- no developer labels
- no raw EVENT_TIMEOUT string
- only user-friendly status

## Upper-body visual skeleton

The UI overlay may interpolate the most recent landmarks at display/vsync cadence for visual smoothness.

Do not feed interpolated visual landmarks back into the classifier.

## Survey-ready minimum

Before production Sign integration:
- independent Core5 devset collection complete;
- new classifier candidate evaluated;
- unsupported-motion rejection improved;
- event/stream logic qualified;
- no regression to modern UI;
- Samsung device pass.

## Next implementation order

1. Convert Avatar Trial to upper-body production framing while retaining full-body diagnostic option.
2. Integrate the canonical action registry into the isolated Avatar path.
3. Finish owner review / Earle corrections.
4. Build sentence/fingerspelling engine offline.
5. Separately finish recognition devset/model work.
6. Design Sign panel UI against the final recognition API.
7. Integrate both into modern VoxGest only after each isolated subsystem passes.
