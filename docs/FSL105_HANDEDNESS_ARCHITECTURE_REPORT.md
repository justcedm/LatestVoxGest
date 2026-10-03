# FSL-105 Handedness Architecture Report

The reviewed FSL-105 taxonomy contains 105 classes: 64 classified one-handed,
41 classified two-handed, and 0 ambiguous. The exact label lists remain in
`reports/fsl/handedness_report.json`.

## Safe first-model scope

Only the 64 confirmed one-handed labels are extracted and audited for the
OneHand162 FSL experiment. FSL-105 frames use the fixed anatomical right-hand
Holistic slot. A missing right hand is zero-filled; the extractor does not
substitute a visible left hand because that would silently change semantics.

This scope is safe for an experimental closed-set comparison of TCN and
RD-TCN. It does not demonstrate support for the 41 two-handed classes,
left-dominant signers, arbitrary background motion, or deployment rejection.

## Required future two-hand branch

Two-handed classes require a new versioned contract with two fixed 63-value hand
blocks and a correspondingly different input width. Those samples and models
must live beside—not inside—the OneHand162 dataset and must have their own
runtime manifest. Padding, truncating, or reinterpreting OneHand162 slots to fit
two hands is prohibited.
