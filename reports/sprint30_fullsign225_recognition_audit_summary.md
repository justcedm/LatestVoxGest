# sprint30 Recognition Dataset Audit

Generated: 2026-05-18T11:33:56
Dataset: `C:\BSIT 3RD YEAR\New VovGest\dataset_words_lstm_fullsign225`
Labels: 22 trainable / 22 required
Training readiness passed: `True`

## Quality Buckets

- GREEN: 21
- YELLOW: 1
- RED: 0

## Per-Class Audit

| Label | Quality | Trainable | Sequences | Groups | Manual | Wrong shape | Guidance |
| --- | --- | --- | ---: | ---: | ---: | ---: | --- |
| YES | GREEN | True | 400 | 16 | 0 | 0 | prioritize clean webcam calibration and live-test this class |
| NO | GREEN | True | 400 | 16 | 0 | 0 | prioritize clean webcam calibration and live-test this class |
| PLEASE | GREEN | True | 400 | 16 | 0 | 0 | prioritize clean webcam calibration and live-test this class |
| WATER | GREEN | True | 400 | 16 | 0 | 0 | prioritize clean webcam calibration and live-test this class |
| HELLO | GREEN | True | 400 | 16 | 0 | 0 | prioritize clean webcam calibration and live-test this class |
| HELP | GREEN | True | 400 | 16 | 0 | 0 | ready for retraining/live test |
| STOP | GREEN | True | 400 | 16 | 0 | 0 | ready for retraining/live test |
| DOCTOR | GREEN | True | 400 | 16 | 0 | 0 | prioritize clean webcam calibration and live-test this class |
| NAME | GREEN | True | 400 | 16 | 0 | 0 | prioritize clean webcam calibration and live-test this class |
| THANKYOU | GREEN | True | 400 | 16 | 0 | 0 | prioritize clean webcam calibration and live-test this class |
| UNDERSTAND | GREEN | True | 400 | 16 | 0 | 0 | ready for retraining/live test |
| SORRY | GREEN | True | 350 | 14 | 0 | 0 | ready for retraining/live test |
| AGAIN | GREEN | True | 350 | 14 | 0 | 0 | ready for retraining/live test |
| MORE | GREEN | True | 350 | 14 | 0 | 0 | ready for retraining/live test |
| PAIN | GREEN | True | 275 | 11 | 0 | 0 | ready for retraining/live test |
| GO | GREEN | True | 225 | 9 | 0 | 0 | ready for retraining/live test |
| FINE | GREEN | True | 275 | 11 | 0 | 0 | ready for retraining/live test |
| EAT | GREEN | True | 200 | 8 | 0 | 0 | ready for retraining/live test |
| TIME | GREEN | True | 200 | 8 | 0 | 0 | ready for retraining/live test |
| WANT | GREEN | True | 250 | 10 | 0 | 0 | ready for retraining/live test |
| MEDICINE | YELLOW | True | 175 | 7 | 0 | 0 | trainable, but schedule manual hardening before demo |
| NOTHING | GREEN | True | 600 | 10 | 0 | 0 | include idle, partial, incomplete, transition, and hand-enter/leave hard negatives |

## Readiness Gates

- NOTHING present and trainable: True
- Demo10 labels preserved in profile: True
- Wrong-shape files in required labels: 0
- Blocked labels: none
