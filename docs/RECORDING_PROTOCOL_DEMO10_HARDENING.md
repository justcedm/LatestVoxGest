# VoxGest Demo10 Recognition Hardening Recording Protocol

This protocol is for the current Recognition Hardening + Avatar Prototype sprint.
It keeps the active dynamic contract fixed at 10 words plus `NOTHING`.

Do not add 25 or 50 words during this pass. Do not enable phrase recognition.
Training stays in Python. Android consumes exported TFLite models, labels, and
the runtime manifest only.

## Active Dynamic Labels

- YES
- NO
- PLEASE
- WATER
- HELLO
- HELP
- STOP
- DOCTOR
- NAME
- THANKYOU
- NOTHING

## Recording Rule

Use exact hand mode during recording. Do not use auto for controlled recording.

Use:

```powershell
VOXGEST_DOMINANT_HAND='right'
```

or

```powershell
VOXGEST_DOMINANT_HAND='left'
```

Keep phrase recognition off:

```powershell
$env:VOXGEST_ENABLE_PHRASE='0'
```

## Right-Hand Repair

```powershell
$env:VOXGEST_ENABLE_PHRASE='0'
$env:VOXGEST_WORD_PROFILE='demo10'
$env:VOXGEST_SINGLE_HAND_POSE='1'
$env:VOXGEST_DOMINANT_HAND='right'
$env:VOXGEST_MANUAL_SEQUENCES_PER_WORD='60'
.\voxgest_env\Scripts\python.exe scripts_ml\16_record_manual_words.py NAME STOP NOTHING
```

Use this when the signer will demo mostly with the right hand. Historical logs
show bidirectional `NAME` / `STOP` confusion. The latest right-hand TCN log shows
`NAME` failing as accepted `STOP`, while STOP itself is stable.

This order records clean `NAME` samples first and preserves the working STOP
version second.

## Left-Hand Repair

```powershell
$env:VOXGEST_ENABLE_PHRASE='0'
$env:VOXGEST_WORD_PROFILE='demo10'
$env:VOXGEST_SINGLE_HAND_POSE='1'
$env:VOXGEST_DOMINANT_HAND='left'
$env:VOXGEST_MANUAL_SEQUENCES_PER_WORD='60'
.\voxgest_env\Scripts\python.exe scripts_ml\16_record_manual_words.py HELP STOP DOCTOR NAME NOTHING
```

Use this when the signer will demo mostly with the left hand, or when left-hand
coverage is missing from the hardening dataset.

## NOTHING Hard Negatives

`NOTHING` is a no-output negative class. It must never become a displayed,
spoken, or animated word. Record it as controlled non-word motion.

NOTHING hard negatives must include:

- idle hand visible
- open hand
- relaxed hand movement
- hand entering frame
- hand leaving frame
- partial STOP
- partial NAME
- partial HELP
- partial DOCTOR
- aborted signs
- transition movement between signs
- neutral non-word movement

## Recording Quality Checklist

- Keep camera position close to the demo setup.
- Keep the signer at the same arm's-length distance expected during demo.
- Keep lighting stable and avoid backlight.
- Record clean examples first, then small variations in speed and distance.
- Record partial, interrupted, or messy attempts as `NOTHING`, not as the word.
- Keep the configured hand visible for most of the 30-frame sequence.
- Do not mix left-hand and right-hand samples in one command.
- Do not use `VOXGEST_DOMINANT_HAND='auto'` for controlled recording.

## Retrain LSTM

```powershell
$env:VOXGEST_ENABLE_PHRASE='0'
$env:VOXGEST_WORD_PROFILE='demo10'
$env:VOXGEST_SINGLE_HAND_POSE='1'
.\voxgest_env\Scripts\python.exe scripts_ml\19_train_lstm.py
```

## Retrain TCN

```powershell
$env:VOXGEST_ENABLE_PHRASE='0'
$env:VOXGEST_WORD_PROFILE='demo10'
$env:VOXGEST_SINGLE_HAND_POSE='1'
.\voxgest_env\Scripts\python.exe scripts_ml\24_train_tcn.py
```

## Right-Hand Live Test

```powershell
$env:VOXGEST_ENABLE_PHRASE='0'
$env:VOXGEST_WORD_PROFILE='demo10'
$env:VOXGEST_DYNAMIC_MODEL='auto'
$env:VOXGEST_DOMINANT_HAND='right'
$env:VOXGEST_MODE='WORDS'
.\voxgest_env\Scripts\python.exe scripts_ml\33_live_word_test_logger.py
```

## Left-Hand Live Test

```powershell
$env:VOXGEST_ENABLE_PHRASE='0'
$env:VOXGEST_WORD_PROFILE='demo10'
$env:VOXGEST_DYNAMIC_MODEL='auto'
$env:VOXGEST_DOMINANT_HAND='left'
$env:VOXGEST_MODE='WORDS'
.\voxgest_env\Scripts\python.exe scripts_ml\33_live_word_test_logger.py
```

## Compare Live Logs

After testing, run:

```powershell
.\voxgest_env\Scripts\python.exe scripts_ml\34_compare_live_logs.py
```

Outputs:

- `reports/live_log_comparison.json`
- `reports/live_log_comparison.csv`
- `reports/live_log_summary.md`

Use the weakest-label ranking from the summary before deciding the next repair
recording session.
