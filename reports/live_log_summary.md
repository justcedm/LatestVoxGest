# VoxGest Live Log Summary

Generated: 2026-05-17T12:59:48
Source logs: 7
Total trials: 237

## Weakest Labels

| Rank | Label | Trials | Correct | Accepted | True accept | False accepts | Rejected | Top confusion | Recommendation |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | --- | --- |
| 1 | NAME | 35 | 34.3% | 30 | 34.3% | 18 | 5 | STOP (14) | record targeted repair samples for this known weak label |
| 2 | STOP | 44 | 50.0% | 36 | 43.2% | 17 | 8 | NAME (12) | record sprint30 hardening repair samples and confusable NOTHING negatives |
| 3 | HELP | 24 | 50.0% | 14 | 45.8% | 3 | 10 | DOCTOR (4) | record targeted repair samples for this known weak label |
| 4 | NOTHING | 29 | 51.7% | 25 | 51.7% | 10 | 4 | PLEASE (3) | record hard negatives and transition/partial-sign NOTHING samples |
| 5 | PLEASE | 17 | 58.8% | 16 | 58.8% | 6 | 1 | NOTHING (3) | record targeted repair samples for this known weak label |
| 6 | DOCTOR | 21 | 66.7% | 20 | 66.7% | 6 | 1 | YES (7) | record sprint30 hardening repair samples and confusable NOTHING negatives |
| 7 | YES | 26 | 84.6% | 22 | 73.1% | 3 | 4 | NOTHING (3) | add confusable negatives and clean contrast samples |
| 8 | NO | 10 | 90.0% | 10 | 90.0% | 1 | 0 | YES (1) | add confusable negatives and clean contrast samples |
| 9 | HELLO | 7 | 100.0% | 7 | 100.0% | 0 | 0 | - | keep current demo coverage |
| 10 | THANKYOU | 15 | 100.0% | 15 | 100.0% | 0 | 0 | - | keep current demo coverage |
| 11 | WATER | 9 | 100.0% | 9 | 100.0% | 0 | 0 | - | keep current demo coverage |

## Record Next

- NAME
- STOP
- HELP
- NOTHING
- PLEASE
- DOCTOR
- THANKYOU
- YES
- NO

## Known Safer Demo Labels

YES, NO, WATER, HELLO, THANKYOU

## Known Weak Labels To Watch

STOP, NAME, HELP, DOCTOR, NOTHING, PLEASE, THANKYOU, UNDERSTAND, PAIN, GO, FINE, EAT, TIME, MEDICINE

## LSTM vs TCN

TCN and LSTM logs are both present.

| Model | Label | Trials | Correct | True accept | False accepts | Rejected |
| --- | --- | ---: | ---: | ---: | ---: | ---: |
| LSTM | DOCTOR | 6 | 83.3% | 83.3% | 1 | 0 |
| LSTM | HELP | 11 | 36.4% | 36.4% | 3 | 4 |
| LSTM | NAME | 16 | 43.8% | 43.8% | 9 | 0 |
| LSTM | NO | 2 | 50.0% | 50.0% | 1 | 0 |
| LSTM | NOTHING | 14 | 64.3% | 64.3% | 2 | 3 |
| LSTM | PLEASE | 4 | 0.0% | 0.0% | 4 | 0 |
| LSTM | STOP | 28 | 39.3% | 35.7% | 13 | 5 |
| LSTM | YES | 2 | 100.0% | 50.0% | 0 | 1 |
| TCN | DOCTOR | 15 | 60.0% | 60.0% | 5 | 1 |
| TCN | HELLO | 7 | 100.0% | 100.0% | 0 | 0 |
| TCN | HELP | 13 | 61.5% | 53.8% | 0 | 6 |
| TCN | NAME | 19 | 26.3% | 26.3% | 9 | 5 |
| TCN | NO | 8 | 100.0% | 100.0% | 0 | 0 |
| TCN | NOTHING | 15 | 40.0% | 40.0% | 8 | 1 |
| TCN | PLEASE | 13 | 76.9% | 76.9% | 2 | 1 |
| TCN | STOP | 16 | 68.8% | 56.2% | 4 | 3 |
| TCN | THANKYOU | 15 | 100.0% | 100.0% | 0 | 0 |
| TCN | WATER | 9 | 100.0% | 100.0% | 0 | 0 |
| TCN | YES | 24 | 83.3% | 75.0% | 3 | 3 |

## Source Files

- live_word_test_log_20260510_214524.json
- live_word_test_log_20260510_214623.json
- live_word_test_log_20260514_094314.json
- live_word_test_log_20260514_115912.json
- live_word_test_log_20260514_120519.json
- live_word_test_log_20260515_155802.json
- live_word_test_log_20260515_222007.json
