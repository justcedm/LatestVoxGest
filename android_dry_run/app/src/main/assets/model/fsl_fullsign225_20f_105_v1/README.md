# STANDARD_FSL_FULLSIGN225 artifact slot

This directory intentionally contains no model or label substitute.

The standard profile is fail-closed until the training/export lane supplies:

- `voxgest_fsl_fullsign225_105_float32.tflite`
- `class_labels_fsl105_fullsign225_v1.json`
- `runtime_manifest.json`
- `golden_fullsign225_feature_fixture_f32.bin`
- `golden_fullsign225_feature_fixture.json`
- `golden_window_f32.bin`
- `golden_expected.json`

The contract is float32 `[1,20,225]` to float32 `[1,105]`, feature version
`fullsign225_20f_v1`, unmirrored model input, and fixed
`pose99 | anatomical LEFT63 | anatomical RIGHT63` slots. Presence of files is
not a PASS: both numeric Android parity markers must report PASS on hardware.
