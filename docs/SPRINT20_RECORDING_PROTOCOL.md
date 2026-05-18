# VoxGest Sprint20 Recording Protocol

Status date: 2026-05-15

This protocol is prepared for future controlled vocabulary expansion. Do not run
it until demo10 passes the `NAME` / `STOP` repair gate.

## Gate Before Recording

Sprint20 recording starts only after:

- `NAME` is at least 4/5 correct.
- `STOP` remains at least 4/5 correct.
- `NOTHING` does not create false word outputs.
- `PLEASE` does not frequently become `STOP`.

Until then, run the demo10 repair protocol instead.

## Planned Sprint20 Words

```text
SORRY, AGAIN, MORE, UNDERSTAND, PAIN, SICK, HURT, MEDICINE, HOSPITAL, EAT
```

`NOTHING` remains required. Phrase recognition remains disabled.

## Controlled Recording Rules

- Use exact hand mode: `right` or `left`.
- Do not use `VOXGEST_DOMINANT_HAND='auto'` for controlled recording.
- Keep `VOXGEST_SINGLE_HAND_POSE='1'`.
- Use the same camera distance and lighting as the demo setup.
- Start neutral, perform the sign, return neutral.
- Record clean examples first, then speed/distance variations.
- Put partial signs, aborted signs, transitions, and uncertain attempts into
  `NOTHING`.

## Right-Hand Sprint20 Recording

Run this only after `word_config.py` contains an activated `sprint20` profile.

```powershell
$env:VOXGEST_ENABLE_PHRASE='0'
$env:VOXGEST_WORD_PROFILE='sprint20'
$env:VOXGEST_SINGLE_HAND_POSE='1'
$env:VOXGEST_DOMINANT_HAND='right'
$env:VOXGEST_MANUAL_SEQUENCES_PER_WORD='60'
.\voxgest_env\Scripts\python.exe scripts_ml\16_record_manual_words.py SORRY AGAIN MORE UNDERSTAND PAIN SICK HURT MEDICINE HOSPITAL EAT NOTHING
```

## Left-Hand Sprint20 Recording

Run this after right-hand coverage is acceptable, or earlier if the demo signer
needs left-hand support.

```powershell
$env:VOXGEST_ENABLE_PHRASE='0'
$env:VOXGEST_WORD_PROFILE='sprint20'
$env:VOXGEST_SINGLE_HAND_POSE='1'
$env:VOXGEST_DOMINANT_HAND='left'
$env:VOXGEST_MANUAL_SEQUENCES_PER_WORD='60'
.\voxgest_env\Scripts\python.exe scripts_ml\16_record_manual_words.py SORRY AGAIN MORE UNDERSTAND PAIN SICK HURT MEDICINE HOSPITAL EAT NOTHING
```

## NOTHING For Sprint20

Record `NOTHING` hard negatives for:

- hand entering frame
- hand leaving frame
- idle visible hand
- relaxed motion
- transition movement between sprint20 words
- partial `SORRY`
- partial `AGAIN`
- partial `MORE`
- partial `PAIN`
- partial `HURT`
- partial `EAT`
- aborted medical signs
- uncertain signs that should not become output

## Dataset Extraction And Audit

If local WLASL videos are available, extract with the same 30 x 162 feature
pipeline:

```powershell
$env:VOXGEST_ENABLE_PHRASE='0'
$env:VOXGEST_WORD_PROFILE='sprint20'
$env:VOXGEST_SINGLE_HAND_POSE='1'
.\voxgest_env\Scripts\python.exe scripts_ml\18_extract_lstm.py
```

Audit before training:

```powershell
$env:VOXGEST_WORD_PROFILE='sprint20'
.\voxgest_env\Scripts\python.exe scripts_ml\30_audit_recognition_dataset.py
```

Do not train if the audit shows missing classes, weak group coverage, missing
`NOTHING`, or label/output-shape mismatch risk.

## Training Commands After Readiness Passes

Back up demo10 assets before running either training command if the scripts still
write to the shared `model/voxgest_*_v1` filenames.

LSTM:

```powershell
$env:VOXGEST_ENABLE_PHRASE='0'
$env:VOXGEST_WORD_PROFILE='sprint20'
$env:VOXGEST_SINGLE_HAND_POSE='1'
.\voxgest_env\Scripts\python.exe scripts_ml\19_train_lstm.py
```

TCN:

```powershell
$env:VOXGEST_ENABLE_PHRASE='0'
$env:VOXGEST_WORD_PROFILE='sprint20'
$env:VOXGEST_SINGLE_HAND_POSE='1'
.\voxgest_env\Scripts\python.exe scripts_ml\24_train_tcn.py
```

## Live Testing

Keep Android defaulted to demo10 until sprint20 passes live Python testing. A
sprint20 model is not demo-safe until the live logger confirms the expanded
label set without breaking demo10 words or `NOTHING`.
