# FullSign225 Manual16 Dataset Audit

Generated: 2026-05-22T01:48:05
Profile: `fullsign225_manual5_team`
Dataset: `C:\BSIT 3RD YEAR\New VovGest\external_datasets\fullsign225_manual16_features`
Feature profile: `fullsign225`
Expected shape: `[30, 225]`

## Summary

- Total valid samples: 300
- Missing labels: none
- Labels under 20: none
- Labels under 30: none
- NOTHING samples: 100
- Rejected/failed samples logged: 389

## Per Label

| Label | Samples | Groups | Wrong Shape | Unreadable |
| --- | ---: | ---: | ---: | ---: |
| EAT | 50 | 2 | 0 | 0 |
| WATER | 50 | 2 | 0 | 0 |
| HELLO | 50 | 2 | 0 | 0 |
| THANKYOU | 50 | 2 | 0 | 0 |
| NOTHING | 100 | 1 | 0 | 0 |

## Readiness

Train only after every label has enough clean samples and no wrong-shape files. For a fast first pass, 20 per label can be used cautiously; 30+ per label is preferred.
