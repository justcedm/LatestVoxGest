# sprint30 Recognition Dataset Audit

Generated: 2026-05-16T14:44:51
Dataset: `C:\BSIT 3RD YEAR\New VovGest\dataset_words_lstm`
Labels: 22 trainable / 22 required
Training readiness passed: `True`

## Quality Buckets

- GREEN: 15
- YELLOW: 7
- RED: 0

## Per-Class Audit

| Label | Quality | Trainable | Sequences | Groups | Manual | Wrong shape | Guidance |
| --- | --- | --- | ---: | ---: | ---: | ---: | --- |
| YES | GREEN | True | 580 | 25 | 180 | 0 | prioritize clean webcam calibration and live-test this class |
| NO | GREEN | True | 519 | 23 | 120 | 0 | prioritize clean webcam calibration and live-test this class |
| PLEASE | GREEN | True | 440 | 21 | 60 | 0 | prioritize clean webcam calibration and live-test this class |
| WATER | GREEN | True | 580 | 25 | 180 | 0 | prioritize clean webcam calibration and live-test this class |
| HELLO | GREEN | True | 460 | 23 | 60 | 0 | prioritize clean webcam calibration and live-test this class |
| HELP | GREEN | True | 500 | 22 | 120 | 0 | ready for retraining/live test |
| STOP | GREEN | True | 700 | 27 | 300 | 0 | ready for retraining/live test |
| DOCTOR | GREEN | True | 640 | 26 | 240 | 0 | prioritize clean webcam calibration and live-test this class |
| NAME | GREEN | True | 460 | 23 | 60 | 0 | prioritize clean webcam calibration and live-test this class |
| THANKYOU | GREEN | True | 408 | 15 | 180 | 0 | prioritize clean webcam calibration and live-test this class |
| UNDERSTAND | GREEN | True | 160 | 13 | 0 | 0 | ready for retraining/live test |
| SORRY | GREEN | True | 160 | 13 | 0 | 0 | ready for retraining/live test |
| AGAIN | GREEN | True | 160 | 13 | 0 | 0 | ready for retraining/live test |
| MORE | GREEN | True | 160 | 13 | 0 | 0 | ready for retraining/live test |
| PAIN | YELLOW | True | 143 | 11 | 0 | 0 | trainable, but schedule manual hardening before demo |
| GO | YELLOW | True | 117 | 9 | 0 | 0 | trainable, but schedule manual hardening before demo |
| FINE | YELLOW | True | 143 | 11 | 0 | 0 | trainable, but schedule manual hardening before demo |
| EAT | YELLOW | True | 104 | 8 | 0 | 0 | trainable, but schedule manual hardening before demo |
| TIME | YELLOW | True | 104 | 8 | 0 | 0 | trainable, but schedule manual hardening before demo |
| WANT | YELLOW | True | 130 | 10 | 0 | 0 | trainable, but schedule manual hardening before demo |
| MEDICINE | YELLOW | True | 91 | 7 | 0 | 0 | trainable, but schedule manual hardening before demo |
| NOTHING | GREEN | True | 600 | 10 | 600 | 0 | include idle, partial, incomplete, transition, and hand-enter/leave hard negatives |

## Readiness Gates

- NOTHING present and trainable: True
- Demo10 labels preserved in profile: True
- Wrong-shape files in required labels: 0
- Blocked labels: none
