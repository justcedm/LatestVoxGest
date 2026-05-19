# FullSign225 Team16 Extraction Audit

Generated: 2026-05-19T11:25:25
Input: `C:\BSIT 3RD YEAR\New VovGest\external_datasets\fullsign225_team_dataset_normalized`
Feature profile: `fullsign225`
Input target: `[1, 30, 225]`
Videos audited: 382
Extraction success: 380
Failed videos: 2
Poor tracking videos: 2

## Per Label

| Label | Total | Success | Failed | Poor Tracking | Pose Avg | Hand Avg | Ready |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | --- |
| DOCTOR | 31 | 31 | 0 | 0 | 1.00 | 0.78 | yes |
| EAT | 21 | 21 | 0 | 0 | 1.00 | 0.74 | yes |
| HELLO | 27 | 27 | 0 | 0 | 1.00 | 0.70 | yes |
| HELP | 26 | 26 | 0 | 0 | 1.00 | 0.61 | yes |
| NAME | 25 | 25 | 0 | 0 | 1.00 | 0.73 | yes |
| NO | 24 | 24 | 0 | 0 | 1.00 | 0.81 | yes |
| NOTHING | 19 | 17 | 2 | 2 | 1.00 | 0.55 | yes |
| PAIN | 19 | 19 | 0 | 0 | 1.00 | 0.80 | yes |
| PLEASE | 25 | 25 | 0 | 0 | 1.00 | 0.83 | yes |
| SORRY | 31 | 31 | 0 | 0 | 1.00 | 0.69 | yes |
| STOP | 22 | 22 | 0 | 0 | 1.00 | 0.80 | yes |
| THANKYOU | 22 | 22 | 0 | 0 | 1.00 | 0.69 | yes |
| TIME | 20 | 20 | 0 | 0 | 1.00 | 0.81 | yes |
| WANT | 21 | 21 | 0 | 0 | 1.00 | 0.71 | yes |
| WATER | 30 | 30 | 0 | 0 | 1.00 | 0.71 | yes |
| YES | 19 | 19 | 0 | 0 | 1.00 | 0.85 | yes |

## Labels Ready For Training

DOCTOR, EAT, HELLO, HELP, NAME, NO, NOTHING, PAIN, PLEASE, SORRY, STOP, THANKYOU, TIME, WANT, WATER, YES

## Labels Needing More Recordings

EAT, HELLO, HELP, NAME, NO, NOTHING, PAIN, PLEASE, STOP, THANKYOU, TIME, WANT, YES

## Labels Under 10 Successful Extractions

None

## Labels Under 30 Successful Extractions

EAT, HELLO, HELP, NAME, NO, NOTHING, PAIN, PLEASE, STOP, THANKYOU, TIME, WANT, YES

## Failure Reasons

- low_hand_ratio<0.20: 2

## NOTHING Warning

NOTHING currently has 19 normalized videos. It is usable for an initial audit, but should be expanded later with idle hands, hand entering/leaving frame, partial signs, aborted signs, and transition movements.

## Next Step

Do not train yet unless the team accepts the audit result. If accepted, prepare a separate `fullsign225_team16` profile and extraction/training commands without changing demo10 or onehand162 defaults.
