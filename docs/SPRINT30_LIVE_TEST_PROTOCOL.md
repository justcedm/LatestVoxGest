# Sprint30 Live Test Protocol

Sprint30 is experimental. Run this only after confirming demo10 backup exists.

## Command

```powershell
$env:VOXGEST_ENABLE_PHRASE='0'
$env:VOXGEST_WORD_PROFILE='sprint30'
$env:VOXGEST_DYNAMIC_MODEL='auto'
$env:VOXGEST_DOMINANT_HAND='right'
$env:VOXGEST_SINGLE_HAND_POSE='1'
$env:VOXGEST_MODE='WORDS'
.\voxgest_env\Scripts\python.exe scripts_ml\33_live_word_test_logger.py
```

## Test Order

First test safer demo10 words:

```text
YES, NO, WATER, HELLO, HELP
```

Then test demo10 risk words:

```text
PLEASE, STOP, DOCTOR, NAME, THANKYOU
```

Then test new sprint30 words in small batches:

```text
SORRY, AGAIN, MORE, WANT
UNDERSTAND, PAIN, GO, FINE
EAT, TIME, MEDICINE
```

Then test NOTHING hard negatives:

```text
idle hand, hand entering frame, hand leaving frame, partial STOP,
partial THANKYOU, partial PAIN, random relaxed movement, transition movement
```

After logging:

```powershell
.\voxgest_env\Scripts\python.exe scripts_ml\34_compare_live_logs.py
```

## Pass Gate

- `NOTHING` must not create false word outputs.
- Demo10 safe words must stay usable.
- New words must show stable true accepts, not only high raw confidence.
- If sprint30 weakens demo10 behavior, keep Android default on demo10.
