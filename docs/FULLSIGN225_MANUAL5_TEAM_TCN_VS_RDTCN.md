# FullSign225 Manual5 Team: TCN vs Residual Dilated TCN

This comparison is intentionally experimental. It does not replace demo10, onehand162, or the existing basic TCN pipeline.

## Profile

- Word profile: `fullsign225_manual5_team`
- Feature profile: `fullsign225`
- Input shape: `[1, 30, 225]`
- Labels: `EAT`, `WATER`, `HELLO`, `THANKYOU`, `NOTHING`
- Dataset folder expected by the trainer: `external_datasets/fullsign225_manual5_team_features`

## Basic TCN

- Expected report path: `model/tcn_training_report_fullsign225_manual5_team.json`
- Current status: not available in this checkout.

## Residual Dilated TCN

- Training script: `scripts_ml/51_train_residual_dilated_tcn.py`
- Expected report path: `model/rd_tcn_training_report_fullsign225_manual5_team.json`
- Current status: ready to train after the merged team feature folder is present.

## Recommendation

Residual Dilated TCN should be tested only after the teammate recorder outputs are merged into the expected team dataset folder. Validation accuracy is not enough to promote a model; live webcam testing still decides whether it is usable for demo.

Keep demo10 as the safe baseline. Keep `fullsign225_manual5_team` experimental until live trials pass.
