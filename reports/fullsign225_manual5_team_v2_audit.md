# FullSign225 Manual5 Team v2 Audit

- Dataset: `C:\BSIT 3RD YEAR\New VovGest\external_datasets\fullsign225_manual5_team_features_v2`
- Expected shape: `(30, 225)`
- Ready for one-day minimum target: `False`
- Ready for preferred target: `False`

## Per Label Counts

| Label | Count | Minimum | Preferred | Status |
| --- | ---: | ---: | ---: | --- |
| EAT | 73 | 80 | 120 | under minimum |
| WATER | 70 | 80 | 120 | under minimum |
| HELLO | 70 | 80 | 120 | under minimum |
| THANKYOU | 70 | 80 | 120 | under minimum |
| NOTHING | 50 | 120 | 180 | under minimum |

## Per Signer Per Label

| Signer | EAT | WATER | HELLO | THANKYOU | NOTHING | Total |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| ANASTACIA | 31 | 30 | 30 | 30 | 50 | 171 |
| EARLE | 1 | 0 | 0 | 0 | 0 | 1 |
| MARIELLA | 41 | 40 | 40 | 40 | 0 | 161 |

## Per Batch Counts

- `ANASTACIA/DIRECT`: 171
- `EARLE/DIRECT`: 1
- `MARIELLA/DIRECT`: 161

## Issues

- Missing label folders: none
- Wrong-shape files: 0
- Unreadable files: 0
- Labels under minimum target: EAT, WATER, HELLO, THANKYOU, NOTHING
- Labels under preferred target: EAT, WATER, HELLO, THANKYOU, NOTHING
