# FullSign225 Manual5 Live Result

## Status

Live test not run yet in this commit. The TCN model has trained, but live pass/fail remains pending until the webcam test below is performed.

Training snapshot:

- Model: `model/voxgest_tcn_fullsign225_manual5.tflite`
- Input shape: `[1, 30, 225]`
- Output labels: `EAT`, `HELLO`, `WATER`, `THANKYOU`, `NOTHING`
- Validation accuracy: 93.18%
- Validation warning: random fallback split was used because each manual label currently has one source group.

## Live Test Command

```powershell
$env:VOXGEST_ENABLE_PHRASE='0'
$env:VOXGEST_WORD_PROFILE='fullsign225_manual5'
$env:VOXGEST_FEATURE_PROFILE='fullsign225'
$env:VOXGEST_DYNAMIC_MODEL='tcn'
$env:VOXGEST_SINGLE_HAND_POSE='0'
$env:VOXGEST_MODE='WORDS'
$env:VOXGEST_LIVE_TEST_CAPTURE_MODE='hand_trigger_auto'
$env:VOXGEST_LIVE_TEST_LABELS='EAT,NOTHING,HELLO,WATER,THANKYOU'
$env:VOXGEST_LIVE_TEST_TRIALS_PER_LABEL='5'
.\voxgest_env\Scripts\python.exe scripts_ml\33_live_word_test_logger.py
```

## Pass/Fail Rule

A label passes only if:

- at least 4 of 5 live trials match expected
- there is no dangerous false accept
- there is no repeated confusion with another label

## Results

| Label | Live Matches | Status | Notes |
| --- | ---: | --- | --- |
| EAT | not tested | pending | EAT was the strongest prior FullSign225 live candidate. |
| NOTHING | not tested | pending | Rejected/no-output movement counts as a match for expected NOTHING. |
| HELLO | not tested | pending | Replace if repeated confusion appears. |
| WATER | not tested | pending | Replace if repeated confusion appears. |
| THANKYOU | not tested | pending | Replace if repeated confusion appears. |

## Backup Labels

Use these only if a primary label fails:

- `NO`
- `PAIN`
- `DOCTOR`

Do not keep unstable labels just to preserve a 5-word count.
