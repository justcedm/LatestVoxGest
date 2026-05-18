# Sprint30 FullSign225 Experiment Report

## 1. Purpose

Create a separate experimental Sprint30 dynamic-word model that uses both hand landmark slots instead of the current one-hand selected-hand vector. This profile is not a replacement for demo10 or sprint30 onehand162.

## 2. Why fullsign225 was created

Sprint30 onehand162 live testing and validation showed several labels were weak or confusable, especially NAME, UNDERSTAND, SORRY, THANKYOU, PAIN, TIME, and MEDICINE. The fullsign225 experiment tests whether keeping both hands gives the model more context for signs that are not well represented by a single selected hand.

## 3. Difference between onehand162 and fullsign225

- onehand162: 99 pose floats plus 63 selected-hand floats. It chooses one dynamic hand by dominant-hand policy and can mask pose to the selected arm.
- fullsign225: 99 pose floats plus 63 physical-left-hand floats plus 63 physical-right-hand floats. It uses fixed slots, zero-fills missing hands, and does not use dominant-hand selection.

## 4. Input Shape

- TCN input: `[1, 30, 225]`
- Per frame: `99 pose + 63 left hand + 63 right hand = 225`
- Output: `[1, 22]`
- Labels: `YES, NO, PLEASE, WATER, HELLO, HELP, STOP, DOCTOR, NAME, THANKYOU, UNDERSTAND, SORRY, AGAIN, MORE, PAIN, GO, FINE, EAT, TIME, WANT, MEDICINE, NOTHING`

## 5. Files Changed

- `scripts_ml/lstm_features.py`
- `scripts_ml/word_config.py`
- `scripts_ml/16_record_manual_words.py`
- `scripts_ml/18_extract_lstm.py`
- `scripts_ml/19_train_lstm.py`
- `scripts_ml/24_train_tcn.py`
- `scripts_ml/30_audit_recognition_dataset.py`
- `scripts_ml/33_live_word_test_logger.py`
- `scripts_ml/36_bootstrap_fullsign225_negatives.py`
- `model/runtime_manifest_sprint30_fullsign225.json`
- `docs/SPRINT30_FULLSIGN225_EXPERIMENT_REPORT.md`

## 6. Commands Run

```powershell
$env:VOXGEST_ENABLE_PHRASE='0'; $env:VOXGEST_WORD_PROFILE='sprint30'; $env:VOXGEST_FEATURE_PROFILE='fullsign225'; $env:VOXGEST_SINGLE_HAND_POSE='0'; $env:VOXGEST_N_AUGMENTS_PER_SOURCE='24'; .\voxgest_env\Scripts\python.exe scripts_ml\18_extract_lstm.py
```
```powershell
$env:VOXGEST_ENABLE_PHRASE='0'; $env:VOXGEST_WORD_PROFILE='sprint30'; $env:VOXGEST_FEATURE_PROFILE='fullsign225'; $env:VOXGEST_SINGLE_HAND_POSE='0'; $env:VOXGEST_N_AUGMENTS_PER_SOURCE='24'; .\voxgest_env\Scripts\python.exe scripts_ml\18_extract_lstm.py FINE EAT TIME WANT MEDICINE
```
```powershell
$env:VOXGEST_WORD_PROFILE='sprint30'; $env:VOXGEST_FEATURE_PROFILE='fullsign225'; $env:VOXGEST_SINGLE_HAND_POSE='0'; .\voxgest_env\Scripts\python.exe scripts_ml\36_bootstrap_fullsign225_negatives.py
```
```powershell
$env:VOXGEST_WORD_PROFILE='sprint30'; $env:VOXGEST_FEATURE_PROFILE='fullsign225'; $env:VOXGEST_SINGLE_HAND_POSE='0'; .\voxgest_env\Scripts\python.exe scripts_ml\30_audit_recognition_dataset.py
```
```powershell
$env:VOXGEST_ENABLE_PHRASE='0'; $env:VOXGEST_WORD_PROFILE='sprint30'; $env:VOXGEST_FEATURE_PROFILE='fullsign225'; $env:VOXGEST_SINGLE_HAND_POSE='0'; $env:VOXGEST_MANUAL_TO_TRAIN='0'; .\voxgest_env\Scripts\python.exe scripts_ml\24_train_tcn.py
```

## 7. Artifacts Created

- `model/voxgest_tcn_sprint30_fullsign225.h5`
- `model/voxgest_tcn_sprint30_fullsign225.tflite`
- `model/class_labels_tcn_sprint30_fullsign225.json`
- `model/tcn_training_report_sprint30_fullsign225.json`
- `model/runtime_manifest_sprint30_fullsign225.json`
- `dataset_words_lstm_fullsign225/`
- `reports/sprint30_fullsign225_recognition_audit_words.json`
- `reports/sprint30_fullsign225_recognition_audit_words.csv`
- `reports/sprint30_fullsign225_recognition_audit_summary.md`

## 8. Dataset Audit Result

- Trainable labels: 22 / 22
- GREEN/YELLOW/RED: 21 / 1 / 0
- Training readiness passed: `True`
- `MEDICINE` is yellow because source coverage is smaller than the stronger labels.
- `NOTHING` is present, but current fullsign225 NOTHING data is bootstrapped from onehand162 manual hard negatives. It should be replaced with native fullsign225 webcam negatives before demo use.
- Caveat: the first fullsign225 extraction hit the command timeout after writing arrays but before saving metadata. For those early extracted word arrays, source groups are inferred from filenames rather than metadata records.

## 9. Grouped Validation Accuracy

- Sprint30 fullsign225 TCN: 54.92%
- Sprint30 onehand162 TCN baseline: 56.34%

## 10. Per-Class Accuracy

| Label | Correct | Total | Accuracy | Top miss |
| --- | ---: | ---: | ---: | --- |
| YES | 49 | 75 | 65.3% | STOP |
| NO | 25 | 75 | 33.3% | STOP |
| PLEASE | 32 | 75 | 42.7% | STOP |
| WATER | 43 | 75 | 57.3% | STOP |
| HELLO | 0 | 75 | 0.0% | HELP |
| HELP | 13 | 75 | 17.3% | WATER |
| STOP | 39 | 75 | 52.0% | NAME |
| DOCTOR | 49 | 75 | 65.3% | STOP |
| NAME | 25 | 75 | 33.3% | HELP |
| THANKYOU | 25 | 75 | 33.3% | TIME |
| UNDERSTAND | 25 | 75 | 33.3% | STOP |
| SORRY | 50 | 75 | 66.7% | WATER |
| AGAIN | 49 | 75 | 65.3% | MORE |
| MORE | 75 | 75 | 100.0% | - |
| PAIN | 50 | 50 | 100.0% | - |
| GO | 50 | 50 | 100.0% | - |
| FINE | 25 | 50 | 50.0% | THANKYOU |
| EAT | 0 | 50 | 0.0% | WATER |
| TIME | 25 | 50 | 50.0% | STOP |
| WANT | 29 | 50 | 58.0% | MEDICINE |
| MEDICINE | 23 | 25 | 92.0% | HELP |
| NOTHING | 120 | 120 | 100.0% | - |

## 11. Top Confusion Map

| Label | Top confusion |
| --- | --- |
| YES | STOP |
| NO | STOP |
| PLEASE | STOP |
| WATER | STOP |
| HELLO | HELP |
| HELP | WATER |
| STOP | NAME |
| DOCTOR | STOP |
| NAME | HELP |
| THANKYOU | TIME |
| UNDERSTAND | STOP |
| SORRY | WATER |
| AGAIN | MORE |
| MORE | - |
| PAIN | - |
| GO | - |
| FINE | THANKYOU |
| EAT | WATER |
| TIME | STOP |
| WANT | MEDICINE |
| MEDICINE | HELP |
| NOTHING | - |

## 12. Model Size

- H5: 3,563,536 bytes
- TFLite: 316,544 bytes

## 13. Comparison With Sprint30 Onehand162 TCN

The fullsign225 TCN reached 54.92% grouped validation, while the existing sprint30 onehand162 TCN baseline is 56.34%. This does not prove an overall improvement.

Fullsign225 helped some previously weak validation labels, including `PAIN`, `MEDICINE`, and `TIME`, but it weakened or failed important demo labels such as `HELLO`, `HELP`, `NO`, `NAME`, and `EAT`.

## 14. Live Test Command

```powershell
$env:VOXGEST_ENABLE_PHRASE='0'
$env:VOXGEST_WORD_PROFILE='sprint30'
$env:VOXGEST_FEATURE_PROFILE='fullsign225'
$env:VOXGEST_DYNAMIC_MODEL='tcn'
$env:VOXGEST_SINGLE_HAND_POSE='0'
$env:VOXGEST_MODE='WORDS'
.\voxgest_env\Scripts\python.exe scripts_ml\33_live_word_test_logger.py
```

## 15. Recommendation

Do not promote fullsign225 yet. Keep demo10 as the safe baseline and keep sprint30 onehand162 as the current experimental comparison. Continue fullsign225 only if native fullsign225 NOTHING recordings and targeted repair data can improve live behavior and grouped validation.

## Weakest Remaining Labels

- EAT: 0.0% validation, top miss `WATER`
- HELLO: 0.0% validation, top miss `HELP`
- HELP: 17.3% validation, top miss `WATER`
- NAME: 33.3% validation, top miss `HELP`
- NO: 33.3% validation, top miss `STOP`
- THANKYOU: 33.3% validation, top miss `TIME`
- UNDERSTAND: 33.3% validation, top miss `STOP`
- PLEASE: 42.7% validation, top miss `STOP`
- FINE: 50.0% validation, top miss `THANKYOU`
- TIME: 50.0% validation, top miss `STOP`

## Safety Checks

- Demo10 generic `*_v1` files were not intentionally overwritten.
- Sprint30/fullsign225 artifacts use separate filenames.
- Phrase recognition remains disabled by default.
- `NOTHING` remains a no-output class.
- Raw predictions must still be blocked from sentence output by gates/token composer.
- Android UI source was not modified in this sprint.
