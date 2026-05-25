# onehand162_phrase_v1 Audit Report

- Generated: `2026-05-25T10:26:47`
- Dataset: `external_datasets\onehand162_phrase_v1_features`
- Expected shape: `(30, 162)`
- Files checked: `1420`
- Ready minimum: `True`
- Ready preferred: `True`
- Wrong-shape files: `0`
- Unreadable files: `0`

## Per Label Counts

| Label | Count | Min | Min Ready | Preferred | Preferred Ready |
| --- | ---: | ---: | --- | ---: | --- |
| WHAT | 260 | 120 | True | 170 | True |
| YOUR | 310 | 120 | True | 170 | True |
| NAME | 310 | 120 | True | 170 | True |
| MY | 160 | 80 | True | 120 | True |
| NOTHING | 380 | 180 | True | 240 | True |

## Per Signer Counts

| Signer | Count |
| --- | ---: |
| ANASTACIA | 240 |
| EARLE | 550 |
| MARIELLA | 630 |

## Per Signer Per Label

| Signer | Label | Count |
| --- | --- | ---: |
| ANASTACIA | WHAT | 40 |
| ANASTACIA | YOUR | 40 |
| ANASTACIA | NAME | 40 |
| ANASTACIA | MY | 40 |
| ANASTACIA | NOTHING | 80 |
| EARLE | WHAT | 90 |
| EARLE | YOUR | 140 |
| EARLE | NAME | 140 |
| EARLE | MY | 40 |
| EARLE | NOTHING | 140 |
| MARIELLA | WHAT | 130 |
| MARIELLA | YOUR | 130 |
| MARIELLA | NAME | 130 |
| MARIELLA | MY | 80 |
| MARIELLA | NOTHING | 160 |

## Sources

| Source | Count |
| --- | ---: |
| DATASET_WORDS_EARLE_FINAL_SAFE | 240 |
| DATASET_WORDS_EARLE_PHRASEV1_FIXED2 | 310 |
| DATASET_WORDS_LSTM_TEOFILO | 150 |
| RECORDED_FEATURES_ANASTACIA | 240 |
| RECORDED_FEATURES_MARIELLA_FINAL | 240 |
| TEOFILE_PHRASEV1_FIXED2_DATASET_WORDS_LSTM_TEOFILO_20260524T1457 | 240 |

## Ignored Labels

None

## Missing Minimum

None

## Missing Preferred

None
