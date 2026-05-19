# VoxGest Live Log Summary

Generated: 2026-05-18T15:29:37
Source logs: 10
Total trials: 382

## Weakest Labels

| Rank | Label | Trials | Correct | Accepted | True accept | False accepts | Rejected | Top confusion | Recommendation |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | --- | --- |
| 1 | GO | 3 | 0.0% | 3 | 0.0% | 3 | 0 | NOTHING (3) | record sprint30 hardening repair samples and confusable NOTHING negatives |
| 2 | MEDICINE | 5 | 0.0% | 4 | 0.0% | 4 | 1 | PLEASE (2) | record sprint30 hardening repair samples and confusable NOTHING negatives |
| 3 | MORE | 3 | 0.0% | 2 | 0.0% | 2 | 1 | PAIN (3) | record more controlled demo10 samples |
| 4 | UNDERSTAND | 8 | 0.0% | 5 | 0.0% | 5 | 3 | NOTHING (4) | record sprint30 hardening repair samples and confusable NOTHING negatives |
| 5 | AGAIN | 3 | 0.0% | 1 | 0.0% | 1 | 2 | DOCTOR (2) | record more controlled demo10 samples |
| 6 | SORRY | 9 | 0.0% | 2 | 0.0% | 2 | 7 | PLEASE (3) | record more controlled demo10 samples |
| 7 | TIME | 6 | 33.3% | 2 | 16.7% | 1 | 4 | DOCTOR (2) | record sprint30 hardening repair samples and confusable NOTHING negatives |
| 8 | NAME | 45 | 31.1% | 38 | 31.1% | 24 | 7 | STOP (19) | record targeted repair samples for this known weak label |
| 9 | EAT | 7 | 42.9% | 4 | 28.6% | 2 | 3 | FINE (4) | record sprint30 hardening repair samples and confusable NOTHING negatives |
| 10 | FINE | 7 | 71.4% | 3 | 28.6% | 1 | 4 | EAT (2) | record sprint30 hardening repair samples and confusable NOTHING negatives |
| 11 | NOTHING | 35 | 45.7% | 28 | 42.9% | 13 | 7 | PLEASE (3) | record hard negatives and transition/partial-sign NOTHING samples |
| 12 | STOP | 48 | 52.1% | 39 | 45.8% | 17 | 9 | NAME (12) | record sprint30 hardening repair samples and confusable NOTHING negatives |
| 13 | HELP | 32 | 46.9% | 18 | 43.8% | 4 | 14 | DOCTOR (5) | record targeted repair samples for this known weak label |
| 14 | YES | 42 | 59.5% | 34 | 50.0% | 13 | 8 | NOTHING (9) | record more controlled demo10 samples |
| 15 | PLEASE | 26 | 53.8% | 22 | 53.8% | 8 | 4 | NOTHING (3) | record targeted repair samples for this known weak label |
| 16 | WANT | 5 | 80.0% | 4 | 60.0% | 1 | 1 | PAIN (1) | record more controlled demo10 samples |
| 17 | NO | 19 | 63.2% | 17 | 63.2% | 5 | 2 | NOTHING (3) | record more controlled demo10 samples |
| 18 | HELLO | 13 | 69.2% | 13 | 69.2% | 4 | 0 | NOTHING (2) | record more controlled demo10 samples |
| 19 | DOCTOR | 26 | 69.2% | 25 | 69.2% | 7 | 1 | YES (7) | record sprint30 hardening repair samples and confusable NOTHING negatives |
| 20 | WATER | 14 | 85.7% | 13 | 85.7% | 1 | 1 | FINE (2) | add confusable negatives and clean contrast samples |
| 21 | THANKYOU | 23 | 91.3% | 21 | 91.3% | 0 | 2 | FINE (2) | keep current demo coverage |
| 22 | PAIN | 3 | 100.0% | 3 | 100.0% | 0 | 0 | - | keep current demo coverage |

## Record Next

- GO
- MEDICINE
- UNDERSTAND
- TIME
- NAME
- EAT
- FINE
- NOTHING
- STOP
- HELP
- PLEASE
- DOCTOR
- THANKYOU
- PAIN
- MORE
- AGAIN
- SORRY
- YES
- WANT
- NO
- HELLO
- WATER

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
| TCN | AGAIN | 3 | 0.0% | 0.0% | 1 | 2 |
| TCN | DOCTOR | 20 | 65.0% | 65.0% | 6 | 1 |
| TCN | EAT | 7 | 42.9% | 28.6% | 2 | 3 |
| TCN | FINE | 7 | 71.4% | 28.6% | 1 | 4 |
| TCN | GO | 3 | 0.0% | 0.0% | 3 | 0 |
| TCN | HELLO | 13 | 69.2% | 69.2% | 4 | 0 |
| TCN | HELP | 21 | 52.4% | 47.6% | 1 | 10 |
| TCN | MEDICINE | 5 | 0.0% | 0.0% | 4 | 1 |
| TCN | MORE | 3 | 0.0% | 0.0% | 2 | 1 |
| TCN | NAME | 29 | 24.1% | 24.1% | 15 | 7 |
| TCN | NO | 17 | 64.7% | 64.7% | 4 | 2 |
| TCN | NOTHING | 21 | 33.3% | 28.6% | 11 | 4 |
| TCN | PAIN | 3 | 100.0% | 100.0% | 0 | 0 |
| TCN | PLEASE | 22 | 63.6% | 63.6% | 4 | 4 |
| TCN | SORRY | 9 | 0.0% | 0.0% | 2 | 7 |
| TCN | STOP | 20 | 70.0% | 60.0% | 4 | 4 |
| TCN | THANKYOU | 23 | 91.3% | 91.3% | 0 | 2 |
| TCN | TIME | 6 | 33.3% | 16.7% | 1 | 4 |
| TCN | UNDERSTAND | 8 | 0.0% | 0.0% | 5 | 3 |
| TCN | WANT | 5 | 80.0% | 60.0% | 1 | 1 |
| TCN | WATER | 14 | 85.7% | 85.7% | 1 | 1 |
| TCN | YES | 40 | 57.5% | 50.0% | 13 | 7 |

## Source Files

- live_word_test_log_20260510_214524.json
- live_word_test_log_20260510_214623.json
- live_word_test_log_20260514_094314.json
- live_word_test_log_20260514_115912.json
- live_word_test_log_20260514_120519.json
- live_word_test_log_20260515_155802.json
- live_word_test_log_20260515_222007.json
- live_word_test_log_20260517_132028.json
- live_word_test_log_20260518_143932.json
- live_word_test_log_20260518_144214.json
