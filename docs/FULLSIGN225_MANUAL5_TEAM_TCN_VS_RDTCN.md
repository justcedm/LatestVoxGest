# FullSign225 Manual5 Team: TCN vs Residual Dilated TCN

This comparison is profile-safe and experimental. Live webcam testing still decides final usability.

## Profile

- Word profile: `fullsign225_manual5_team`
- Feature profile: `fullsign225`
- Input shape: `[1, 30, 225]`
- Labels: EAT, WATER, HELLO, THANKYOU, NOTHING

## Basic TCN

- Report not found: `model/tcn_training_report_fullsign225_manual5_team.json`

## Residual Dilated TCN

- Report: `model/rd_tcn_training_report_fullsign225_manual5_team.json`
- Grouped validation accuracy: 41.32%
- TFLite: `model/voxgest_rd_tcn_fullsign225_manual5_team.tflite`

## Recommendation

Basic TCN report is not available yet for this profile.

Do not promote either model from validation alone. Run the live webcam protocol and keep demo10 as the safe baseline until live trials pass.
