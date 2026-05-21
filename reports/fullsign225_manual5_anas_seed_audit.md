# FullSign225 Anastacia Seed Audit

Generated: 2026-05-21T10:55:32
Profile: `fullsign225_manual5_anas_seed`
Dataset: `C:\BSIT 3RD YEAR\New VovGest\external_datasets\fullsign225_manual5_anas_seed_features`
Feature profile: `fullsign225`
Expected shape: `[30, 225]`
Target words: EAT, WATER, HELLO, THANKYOU
Training labels: EAT, WATER, HELLO, THANKYOU, NOTHING

## Summary

- Total valid samples: 171
- Missing labels: none
- Labels under minimum: none
- NOTHING samples: 50
- Ready to train seed TCN: YES

## Readiness Gates

- Every seed label exists: PASS
- Normal labels have at least 20 samples: PASS
- NOTHING has at least 30 samples: PASS
- No wrong-shape or unreadable files: PASS

## Per Label

| Label | Samples | Required | Groups | Wrong Shape | Unreadable |
| --- | ---: | ---: | ---: | ---: | ---: |
| EAT | 31 | 20 | 1 | 0 | 0 |
| WATER | 30 | 20 | 1 | 0 | 0 |
| HELLO | 30 | 20 | 1 | 0 | 0 |
| THANKYOU | 30 | 20 | 1 | 0 | 0 |
| NOTHING | 50 | 30 | 1 | 0 | 0 |

## Warning

This is not the final team dataset. It uses Anastacia's accepted samples only and should be treated as a seed/spoiler training run.
