# VoxGest Cross-Device Validation Plan

## Recording protocol

Record the 26 FSL alphabet labels with four named signer IDs and 200 accepted
20-frame attempts per signer/letter. Use
`scripts_ml/collect_fsl_alphabet_seq.py`; create a new `--session-id` whenever
the signer, camera, lighting setup, distance, clothing, or day changes. Record
the anatomical signing hand with `--selected-hand`. Do not copy files into the
legacy `external_datasets/fsl_features` tree.

Capture at least two device domains (the PC webcam and the target Samsung
device) and deliberately vary background, distance, lighting, and camera angle.
Keep the raw camera feed unmirrored; only the preview may be mirrored. Failed
13/20 hand-or-pose presence attempts remain rejected.

## Ingestion gates

Before combining any capture with training data:

1. Require feature version `onehand162_20f_nose_mcp_z03_v2`, float32, exact
   `(20,162)`, finite values, label, signer, session, device, selected hand, and
   mirror metadata.
2. Report pose/hand presence distributions and reject incompatible or missing
   feature versions into a quarantine report.
3. Detect duplicate source identities and content hashes.
4. Split first by signer, then session, then source recording. Windows or
   repeated attempts from the same source group may never cross splits.

## Evaluation matrix

Report accuracy, macro-F1, per-class precision/recall/F1, confusion matrix, and
the most confused pairs for:

- held-out signer on the same device;
- held-out recording session on the same device;
- PC-webcam to Samsung transfer;
- Samsung to PC-webcam transfer;
- right-selected versus left-selected signer cohorts, when both exist;
- Keras, float32 TFLite, and float16 TFLite parity.

Track feature drift per slot and per landmark group (pose versus selected hand),
including missing-landmark rates. Investigate large domain shifts before model
tuning so camera/preprocessing mismatches are not hidden by architecture changes.

## Promotion criteria

An FSL model remains experimental until all of the following are true:

- grouped leakage audit has zero signer/session/source overlap;
- every deployed label has adequate examples in each target device domain;
- cross-device macro-F1 and the weakest per-class recall meet a team-approved
  threshold established before examining the final test set;
- float32 TFLite prediction agreement with Keras is effectively exact and
  float16 degradation is documented and acceptable;
- a representative `NSAC`/background dataset has been collected and the
  existing rejection/gating logic has been calibrated without arbitrary
  threshold changes;
- Android preprocessing passes golden-vector parity against the Python builder.

No model from the current FSL-105-only run qualifies for Android promotion.
