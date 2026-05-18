# Sprint30 Model Report

Sprint30 is an experimental vocabulary-expansion profile. It preserves demo10
and adds 11 WLASL-backed practical words, producing 22 output labels including
`NOTHING`.

Do not treat this model as production-ready until live testing passes.

## Final Labels

YES, NO, PLEASE, WATER, HELLO, HELP, STOP, DOCTOR, NAME, THANKYOU,
UNDERSTAND, SORRY, AGAIN, MORE, PAIN, GO, FINE, EAT, TIME, WANT,
MEDICINE, NOTHING

## Dataset Sources

| Word | Source | Sequences | Groups | Quality |
| --- | --- | ---: | ---: | --- |
| YES | WLASL local videos + manual samples | 580 | 25 | GREEN |
| NO | WLASL local videos + manual samples | 519 | 23 | GREEN |
| PLEASE | WLASL local videos + manual samples | 440 | 21 | GREEN |
| WATER | WLASL local videos + manual samples | 580 | 25 | GREEN |
| HELLO | WLASL local videos + manual samples | 460 | 23 | GREEN |
| HELP | WLASL local videos + manual samples | 500 | 22 | GREEN |
| STOP | WLASL local videos + manual samples | 700 | 27 | GREEN |
| DOCTOR | WLASL local videos + manual samples | 640 | 26 | GREEN |
| NAME | WLASL local videos + manual samples | 460 | 23 | GREEN |
| THANKYOU | WLASL local videos + manual samples | 408 | 15 | GREEN |
| UNDERSTAND | WLASL local videos | 160 | 13 | GREEN |
| SORRY | WLASL local videos | 160 | 13 | GREEN |
| AGAIN | WLASL local videos | 160 | 13 | GREEN |
| MORE | WLASL local videos | 160 | 13 | GREEN |
| PAIN | WLASL local videos | 143 | 11 | YELLOW |
| GO | WLASL local videos | 117 | 9 | YELLOW |
| FINE | WLASL local videos | 143 | 11 | YELLOW |
| EAT | WLASL local videos | 104 | 8 | YELLOW |
| TIME | WLASL local videos | 104 | 8 | YELLOW |
| WANT | WLASL local videos | 130 | 10 | YELLOW |
| MEDICINE | WLASL local videos | 91 | 7 | YELLOW |
| NOTHING | Manual hard negatives | 600 | 10 | GREEN |

No local ASL Citizen or Kaggle dataset folder was detected during discovery.

## Training Results

| Model | Grouped validation accuracy | Output shape | Exported TFLite |
| --- | ---: | --- | --- |
| LSTM | 54.32% | `[1, 22]` | `model/voxgest_lstm_sprint30.tflite` |
| TCN | 56.34% | `[1, 22]` | `model/voxgest_tcn_sprint30.tflite` |

Recommended first live-test model: TCN. It is only slightly stronger than LSTM,
so this recommendation is provisional.

## Per-Class Validation

| Word | LSTM acc | LSTM top miss | TCN acc | TCN top miss |
| --- | ---: | --- | ---: | --- |
| YES | 68.4% | NAME | 60.0% | NO |
| NO | 73.7% | PAIN | 75.0% | NAME |
| PLEASE | 50.0% | HELLO | 50.0% | HELP |
| WATER | 50.0% | PLEASE | 50.0% | HELP |
| HELLO | 65.5% | PLEASE | 63.8% | HELP |
| HELP | 72.4% | SORRY | 92.1% | STOP |
| STOP | 38.9% | WATER | 40.0% | NAME |
| DOCTOR | 57.9% | PLEASE | 40.0% | STOP |
| NAME | 51.9% | YES | 97.4% | WATER |
| THANKYOU | 0.0% | NAME | 0.0% | NAME |
| UNDERSTAND | 33.3% | YES | 30.8% | PLEASE |
| SORRY | 100.0% | - | 100.0% | - |
| AGAIN | 100.0% | - | 100.0% | - |
| MORE | 100.0% | - | 100.0% | - |
| PAIN | 50.0% | PLEASE | 0.0% | STOP |
| GO | 0.0% | NO | 46.2% | HELLO |
| FINE | 50.0% | PLEASE | 11.5% | WATER |
| EAT | 50.0% | WATER | 50.0% | WATER |
| TIME | 50.0% | MORE | 0.0% | NAME |
| WANT | 19.2% | YES | 100.0% | - |
| MEDICINE | 0.0% | AGAIN | 0.0% | SORRY |
| NOTHING | not validated | manual-only train split | not validated | manual-only train split |

## Interpretation

Safe enough for app preview experiments after live testing:
SORRY, AGAIN, MORE, WANT, HELP, NAME.

Likely hardening required before demo:
PLEASE, WATER, STOP, DOCTOR, THANKYOU, UNDERSTAND, PAIN, GO, FINE, EAT, TIME,
MEDICINE, NOTHING.

Words to exclude from the next live demo if they stay weak:
THANKYOU, PAIN, TIME, MEDICINE, FINE.

## Artifacts

- `model/voxgest_lstm_sprint30.h5`
- `model/voxgest_lstm_sprint30.tflite`
- `model/class_labels_lstm_sprint30.json`
- `model/lstm_training_report_sprint30.json`
- `model/voxgest_tcn_sprint30.h5`
- `model/voxgest_tcn_sprint30.tflite`
- `model/class_labels_tcn_sprint30.json`
- `model/tcn_training_report_sprint30.json`
- `model/runtime_manifest_sprint30.json`

Demo10 was backed up before training at
`model/backups/demo10_before_sprint30_20260516_1337/`.
