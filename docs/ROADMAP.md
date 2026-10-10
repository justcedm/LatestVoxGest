# Roadmap

## P0 — Survey/Defense Stability

- freeze a known-good Android APK
- record APK SHA256 and device
- make Play Signs work independently from STT
- repair Settings/theme controls
- verify portrait/landscape stability
- verify the current five recognition concepts on Samsung
- preserve rollback artifacts

## P1 — Recognition Hardening

- investigate left/right handedness parity, especially YES
- repeat controlled per-class device trials
- collect unsupported/neutral/aborted-event evidence
- improve rejection without weakening safety
- test additional signers/devices
- preserve the frozen Core5 fallback

## P2 — Vocabulary Expansion

Only after P1 evidence:

Candidate conversational expansion:
- HOW ARE YOU
- I'M FINE
- KNOW
- WRONG
- DON'T UNDERSTAND

Expansion must use documented FSL sources and must not overwrite the frozen baseline.

## P3 — Avatar Calibration

- improve motion transitions
- verify hand location/orientation
- maintain signing hands inside the viewport
- review two-hand actions
- gather qualified FSL-expert feedback
- promote candidate actions only after validation

## P4 — Phrase / Fingerspelling Research

- revisit ASK_NAME only after segmentation and source validation
- use validated FSL manual alphabet for proper-name fingerspelling
- keep phrase intent separate from unrestricted sentence translation

## P5 — Final Paper

- align the manuscript to the exact evaluated APK
- document model/version/hash
- report survey results by evaluator group
- report limitations and failures
- write Chapter 4 from collected evidence
- write Chapter 5 recommendations from measured findings
