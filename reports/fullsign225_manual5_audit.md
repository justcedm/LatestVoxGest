# FullSign225 Manual5 Dataset Audit

Generated: 2026-05-20T06:57:24
Profile: `fullsign225_manual5`
Dataset: `C:\BSIT 3RD YEAR\New VovGest\external_datasets\fullsign225_manual16_features`
Feature profile: `fullsign225`
Expected shape: `[30, 225]`
Target words: EAT, HELLO, WATER, THANKYOU
Training labels: EAT, HELLO, WATER, THANKYOU, NOTHING

## Summary

- Total valid samples: 220
- Missing labels: none
- Labels under minimum: none
- NOTHING samples: 100
- Rejected/failed samples logged: 363
- Ready to train TCN: YES

## Readiness Gates

- Every manual5 label exists: PASS
- Normal labels have at least 30 samples: PASS
- NOTHING has at least 100 samples: PASS
- No wrong-shape or unreadable files: PASS

## Per Label

| Label | Samples | Required | Groups | Wrong Shape | Unreadable |
| --- | ---: | ---: | ---: | ---: | ---: |
| EAT | 30 | 30 | 1 | 0 | 0 |
| HELLO | 30 | 30 | 1 | 0 | 0 |
| WATER | 30 | 30 | 1 | 0 | 0 |
| THANKYOU | 30 | 30 | 1 | 0 | 0 |
| NOTHING | 100 | 100 | 1 | 0 | 0 |

## Training Rule

Train `fullsign225_manual5` only if every readiness gate passes. Rejected samples are reported for data-quality review but are not included in training.
