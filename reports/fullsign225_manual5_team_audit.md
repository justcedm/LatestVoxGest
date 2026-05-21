# FullSign225 Manual5 Team Audit

- Dataset: `C:\BSIT 3RD YEAR\New VovGest\external_datasets\fullsign225_manual5_team_features`
- Expected shape: `(30, 225)`
- Ready for minimum target: `False`
- Ready for preferred target: `False`

## Per Label Counts

| Label | Count | Minimum | Preferred |
| --- | ---: | ---: | ---: |
| EAT | 73 | 80 | 120 |
| WATER | 70 | 80 | 120 |
| HELLO | 70 | 80 | 120 |
| THANKYOU | 70 | 80 | 120 |
| NOTHING | 50 | 120 | 180 |

## Per Signer Counts

| Signer | EAT | WATER | HELLO | THANKYOU | NOTHING |
| --- | ---: | ---: | ---: | ---: | ---: |
| CED | 0 | 0 | 0 | 0 | 0 |
| ANASTACIA | 0 | 0 | 0 | 0 | 0 |
| MARIELLA | 0 | 0 | 0 | 0 | 0 |
| EARLE | 0 | 0 | 0 | 0 | 0 |
| ANASTACIA_ANASTACIA_EAT | 30 | 0 | 0 | 0 | 0 |
| ANASTACIA_ANASTACIA_HELLO | 0 | 0 | 30 | 0 | 0 |
| ANASTACIA_ANASTACIA_NOTHING | 0 | 0 | 0 | 0 | 50 |
| ANASTACIA_ANASTACIA_THANKYOU | 0 | 0 | 0 | 30 | 0 |
| ANASTACIA_ANASTACIA_WATER | 0 | 30 | 0 | 0 | 0 |
| ANASTACIA_TEAMMATE_A_EAT | 1 | 0 | 0 | 0 | 0 |
| EARLE_TEAMMATE_A_EAT | 1 | 0 | 0 | 0 | 0 |
| MARIELLA_MARIELLA_EAT | 40 | 0 | 0 | 0 | 0 |
| MARIELLA_MARIELLA_HELLO | 0 | 0 | 40 | 0 | 0 |
| MARIELLA_MARIELLA_THANKYOU | 0 | 0 | 0 | 40 | 0 |
| MARIELLA_MARIELLA_WATER | 0 | 40 | 0 | 0 | 0 |
| MARIELLA_TEAMMATE_A_EAT | 1 | 0 | 0 | 0 | 0 |

## Issues

- Wrong-shape files: 0
- Unreadable files: 0
- Labels under minimum target: EAT, WATER, HELLO, THANKYOU, NOTHING
- Labels under preferred target: EAT, WATER, HELLO, THANKYOU, NOTHING

## Rejected Samples Seen

- CED: 0
- ANASTACIA: 74
- MARIELLA: 108
- EARLE: 1
