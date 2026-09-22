# VoxGest Paper Architecture Alignment

## Claims supported by the implemented system

The paper may describe a versioned OneHand162 preprocessing contract consisting
of 33 pose landmarks and one selected 21-landmark hand, nose-relative
coordinates, wrist-to-middle-MCP hand scaling, Z damping of 0.3, and 20-frame
sequences. It may report the audited 64-class one-handed FSL-105 subset and the
signer-grouped train/validation/test method only with the generated audit and
evaluation values.

The experimental comparison includes a clean causal dilated TCN baseline and a
residual dilated TCN candidate using the same data, class order, split manifest,
optimizer family, and evaluation procedure. Keras and TFLite results must be
reported separately.

## Required wording updates

- Replace ASL-primary wording with the implemented experimental FSL branch;
  describe the existing ASL/runtime branch separately rather than implying it
  was deleted.
- Replace WLASL-primary dataset wording for this experiment with the audited
  FSL-105 train split and its 64 confirmed one-handed classes.
- Replace 30-frame FSL input claims with the exact 20-frame contract.
- Replace references to an old 10-word FSL vocabulary with the audited 64-class
  order stored in the experimental model manifest.
- Replace LSTM claims for this experiment with the TCN baseline and RD-TCN
  comparison. Do not imply all existing stable runtime models changed.
- Distinguish static alphabet collection from dynamic FSL-105 word/sign
  sequences; no alphabet accuracy result exists until the planned recordings
  and evaluation are completed.
- Describe dataset development as versioned extraction plus audit, including
  the 42 exact duplicate-content samples excluded without deleting sources.
- State that validation/test partitions are grouped globally by signer, with
  zero signer and source-video overlap—not random sliding-window splits.
- Report the handedness scope: 64 one-handed, 41 two-handed excluded, zero
  ambiguous in the present review.
- Treat device generalization as a future validation protocol, not an achieved
  result.
- Include accuracy, macro precision/recall/F1, per-class metrics, confusion
  matrices, model parameters, TFLite sizes, and Keras/TFLite parity.

## Claims that must not be made yet

The present work does not support claims of full 105-class FSL recognition,
two-handed recognition, alphabet recognition accuracy, signer independence
beyond the audited grouping, cross-device robustness, real-world rejection
quality, or Android deployment. FSL-105 supplies no `NSAC`/background class,
and the newly requested four-signer alphabet recordings have not yet been
collected.

The stable Android recognizer and its thresholds are not replaced by this
experiment. Any paper diagram must show the FSL models as an experimental
parallel branch until golden-vector preprocessing parity, cross-device testing,
negative-class calibration, and explicit runtime promotion are complete.

## Reproducibility artifacts

- `docs/VOXGEST_FEATURE_CONTRACT.md`
- `reports/fsl105_dataset_audit.json`
- `reports/fsl105_split_manifest.csv`
- `reports/fsl_tcn_evaluation.json`
- `reports/fsl_rdtcn_evaluation.json`
- experimental runtime manifests under `model/experimental/`

Numerical tables in the paper should be generated from those reports and should
identify dataset version `fsl105_train64_onehand162_v2_audit1` and feature
version `onehand162_20f_nose_mcp_z03_v2` verbatim.
