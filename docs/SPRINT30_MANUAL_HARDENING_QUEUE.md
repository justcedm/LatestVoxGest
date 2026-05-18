# Sprint30 Manual Hardening Queue

Use exact hand mode. Do not use `VOXGEST_DOMINANT_HAND='auto'` for controlled
recording.

## Right-Hand Queue

```powershell
$env:VOXGEST_ENABLE_PHRASE='0'
$env:VOXGEST_WORD_PROFILE='sprint30'
$env:VOXGEST_SINGLE_HAND_POSE='1'
$env:VOXGEST_DOMINANT_HAND='right'
$env:VOXGEST_MANUAL_SEQUENCES_PER_WORD='60'
.\voxgest_env\Scripts\python.exe scripts_ml\16_record_manual_words.py THANKYOU STOP DOCTOR UNDERSTAND NOTHING
```

```powershell
$env:VOXGEST_ENABLE_PHRASE='0'
$env:VOXGEST_WORD_PROFILE='sprint30'
$env:VOXGEST_SINGLE_HAND_POSE='1'
$env:VOXGEST_DOMINANT_HAND='right'
$env:VOXGEST_MANUAL_SEQUENCES_PER_WORD='60'
.\voxgest_env\Scripts\python.exe scripts_ml\16_record_manual_words.py PAIN GO FINE TIME MEDICINE NOTHING
```

```powershell
$env:VOXGEST_ENABLE_PHRASE='0'
$env:VOXGEST_WORD_PROFILE='sprint30'
$env:VOXGEST_SINGLE_HAND_POSE='1'
$env:VOXGEST_DOMINANT_HAND='right'
$env:VOXGEST_MANUAL_SEQUENCES_PER_WORD='60'
.\voxgest_env\Scripts\python.exe scripts_ml\16_record_manual_words.py PLEASE WATER EAT WANT NOTHING
```

## Left-Hand Queue

```powershell
$env:VOXGEST_ENABLE_PHRASE='0'
$env:VOXGEST_WORD_PROFILE='sprint30'
$env:VOXGEST_SINGLE_HAND_POSE='1'
$env:VOXGEST_DOMINANT_HAND='left'
$env:VOXGEST_MANUAL_SEQUENCES_PER_WORD='60'
.\voxgest_env\Scripts\python.exe scripts_ml\16_record_manual_words.py THANKYOU STOP DOCTOR UNDERSTAND PAIN NOTHING
```

```powershell
$env:VOXGEST_ENABLE_PHRASE='0'
$env:VOXGEST_WORD_PROFILE='sprint30'
$env:VOXGEST_SINGLE_HAND_POSE='1'
$env:VOXGEST_DOMINANT_HAND='left'
$env:VOXGEST_MANUAL_SEQUENCES_PER_WORD='60'
.\voxgest_env\Scripts\python.exe scripts_ml\16_record_manual_words.py GO FINE EAT TIME WANT MEDICINE NOTHING
```

## NOTHING Hard-Negative Queue

```powershell
$env:VOXGEST_ENABLE_PHRASE='0'
$env:VOXGEST_WORD_PROFILE='sprint30'
$env:VOXGEST_SINGLE_HAND_POSE='1'
$env:VOXGEST_DOMINANT_HAND='right'
$env:VOXGEST_MANUAL_SEQUENCES_PER_WORD='120'
.\voxgest_env\Scripts\python.exe scripts_ml\16_record_manual_words.py NOTHING
```

Record:

- idle hand visible
- open hand
- hand entering frame
- hand leaving frame
- partial STOP
- partial THANKYOU
- partial PAIN
- partial MEDICINE
- aborted UNDERSTAND
- transition movement between sprint30 words

## Confusable Contrast Pairs

- STOP vs NAME
- STOP vs PAIN
- STOP vs DOCTOR
- THANKYOU vs NAME
- PLEASE vs WATER
- WATER vs EAT
- MORE vs TIME
- SORRY vs HELP
- FINE vs WATER
- MEDICINE vs SORRY

## Excluded Candidate Words Needing Data

These were not included in the final trained sprint30 profile because local
data was insufficient:

SICK, HURT, HOSPITAL, FOOD, BATHROOM, SLEEP, TIRED, HOME, WAIT, TODAY,
TOMORROW, GOOD, BAD, CALL, PHONE, NEED, KNOW, COME, MONEY.
