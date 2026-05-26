# FullSign225 Manual5 Team v2 Audit

- Dataset: `C:\BSIT 3RD YEAR\New VovGest\external_datasets\fullsign225_manual5_team_features_v2`
- Expected shape: `(30, 225)`
- Ready for one-day minimum target: `True`
- Ready for preferred target: `False`

## Per Label Counts

| Label | Count | Minimum | Preferred | Status |
| --- | ---: | ---: | ---: | --- |
| EAT | 123 | 80 | 120 | preferred |
| WATER | 120 | 80 | 120 | preferred |
| HELLO | 120 | 80 | 120 | preferred |
| THANKYOU | 120 | 80 | 120 | preferred |
| NOTHING | 150 | 120 | 180 | minimum |

## Per Signer Per Label

| Signer | EAT | WATER | HELLO | THANKYOU | NOTHING | Total |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| ANASTACIA | 31 | 30 | 30 | 30 | 50 | 171 |
| CED | 50 | 50 | 50 | 50 | 100 | 300 |
| EARLE | 1 | 0 | 0 | 0 | 0 | 1 |
| MARIELLA | 41 | 40 | 40 | 40 | 0 | 161 |

## Per Batch Counts

- `ANASTACIA/DIRECT`: 171
- `CED/BATCH01_MANUAL16`: 300
- `EARLE/DIRECT`: 1
- `MARIELLA/DIRECT`: 161

## Issues

- Missing label folders: none
- Wrong-shape files: 0
- Unreadable files: 0
- Labels under minimum target: none
- Labels under preferred target: NOTHING
