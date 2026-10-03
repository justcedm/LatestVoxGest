# Controlled FSL/Mapúa Benchmark Specification

Status: experimental design only. `TRAINING_STARTED=NO`.

## Research question

Test whether raw-video, versioned FullSign225 trajectories from FSL-105,
Mapúa Transactional FSL v1, or a linguistically validated combination improve
signer-independent recognition. Textual label overlap is a candidate join key,
not evidence that two signs are linguistically or visually equivalent.

The 14 exact-text overlap candidates are EIGHT, FIVE, FOUR, HELLO, NINE, NO,
ONE, SEVEN, SIX, TEN, THANK YOU, THREE, TWO, and YES. WELCOME and YOURE
WELCOME remain separate unless a qualified FSL reviewer authorizes a mapping.

## Dataset arms

| Arm | Source | Classes | Current readiness |
| --- | --- | --- | --- |
| `MODEL_A` | Raw FSL-105 clips | The 14 exact-text overlap candidates only | Blocked: the 2,130 referenced MOV files are absent from safe C: storage |
| `MODEL_B` | Raw Mapúa clips | The same 14 exact-text candidates only | Blocked: signer identities/groups and FSL linguistic review are not recoverable from supplied metadata |
| `MODEL_C` | Validated FSL-105 + Mapúa | Only overlap concepts approved as equivalent by qualified FSL review | Blocked by both source issues and by domain-combination validation |

Mapúa-only transactional labels may be studied in a separate 26-class task;
they must not silently change the common-label A/B/C comparison.

## Feature and temporal contract

- Re-extract every candidate from raw video with one versioned, audited
  canonical/unmirrored MediaPipe-to-FullSign225 implementation.
- Preserve pose 99 + anatomical left hand 63 + anatomical right hand 63.
  Never swap anatomical slots to match screen mirroring.
- Do not use the paired third-party NPY files as raw-video ground truth.
- Detect the complete usable sign-motion span, retain a neutral boundary where
  available, and resample that entire span to 20, 32, or 48 normalized timeline
  positions. Never select only the first N camera frames.
- Record extraction version, MediaPipe version/options, missing-landmark policy,
  raw SHA-256, source-video ID, signer/group ID, and temporal length in every
  feature manifest.

## Candidate grid

Run the same 3×3 grid for each eligible dataset arm:

- Architectures: audited RD-TCN baseline; GRU comparator; compact
  Transformer/Conformer only after a representative model exports to TFLite
  with supported operations and realistic Samsung latency/memory.
- Temporal lengths: 20, 32, 48.

Use the existing audited FSL-105 training/finalization pipeline. Add a candidate
through its configuration/versioning interfaces; do not create an ad-hoc
trainer or overwrite the deployed model.

## Split and fairness rules

Freeze one signer-disjoint split manifest per arm before training. Signer,
source-video, continuous-take, exact-hash, and near-duplicate-group overlap
between train/validation/test must each be zero. If Mapúa signer IDs cannot be
recovered or reliably annotated, MODEL_B and MODEL_C do not train.

Within an arm, every architecture/length candidate receives identical source
videos, partitions, feature policy, augmentation policy, random seeds, epoch and
early-stopping limits, search budget, class weighting, and calibration data.
Publish all failed and completed candidates; do not select from the held-out
test.

## Selection and evaluation

1. Primary offline selection metric: validation macro-F1.
2. Secondary validation evidence: per-class precision/recall/F1, balanced
   accuracy, top confusions, calibration error, rejection curves, parameter
   count, TFLite export parity, model size, and latency.
3. Freeze the winning configuration and threshold policy.
4. Evaluate that one winner once on the sealed held-out test; report confidence
   intervals and all class metrics.
5. Keep non-sign/OOD captures outside the FSL vocabulary and use them only to
   calibrate/evaluate rejection.
6. Preserve the current deployed FullSign225 model as rollback. Promotion
   requires TFLite top-1/probability parity and golden feature parity.
7. Ultimate deployment metric: physical Samsung trials with unseen signers,
   no-hands/idle/open-palm/wave negatives, one-token-per-sign duplicate
   suppression, exact rejection reasons, and camera/landmark/TFLite latency.

## Go/no-go gate

`READY_FOR_CONTROLLED_TRAINING=NO` until raw FSL-105 media is restored (for A/C),
Mapúa signer groups are recovered or independently annotated (for B/C), all
candidate overlaps receive qualified FSL review, the non-sign capture protocol
has an approved split plan, and an explicit training authorization is given.
