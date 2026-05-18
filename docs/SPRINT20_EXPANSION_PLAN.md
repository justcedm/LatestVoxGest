# VoxGest Sprint20 Controlled Vocabulary Expansion Plan

Status date: 2026-05-15

Sprint20 is a planned expansion profile, not the current production or demo
default. Demo10 remains the safe Android and defense/demo profile until the
`NAME` / `STOP` repair passes live testing.

## Expansion Gate

Do not activate sprint20 until:

- `NAME` is at least 4/5 correct in right-hand live testing.
- `STOP` remains at least 4/5 correct.
- `NOTHING` remains no-output and does not create false word tokens.
- `PLEASE` does not frequently become `STOP`.

The latest available right-hand TCN log does not pass this gate because `NAME`
was 0/5 and all five trials were accepted as `STOP`.

## Planned Profile

Safe base profile:

```text
demo10 = YES, NO, PLEASE, WATER, HELLO, HELP, STOP, DOCTOR, NAME, THANKYOU, NOTHING
```

Planned sprint20 additions:

```text
SORRY, AGAIN, MORE, UNDERSTAND, PAIN, SICK, HURT, MEDICINE, HOSPITAL, EAT
```

`NOTHING` remains the active negative/no-output class. Phrase recognition remains
disabled.

## Candidate Word Review

| Word | Reason Chosen | Gesture Policy | Local WLASL Metadata | Local Video Support | Recording Requirement | Confusion Risk |
| --- | --- | --- | ---: | ---: | --- | --- |
| SORRY | Common repair/apology phrase | one-hand adaptable | 14 | 14 | manual right/left validation still required | may overlap circular `PLEASE` motion |
| AGAIN | Useful request for repetition | one-hand adaptable | 16 | 12 | collect controlled samples after gate | may overlap `MORE` or repeated tap gestures |
| MORE | Basic need/request word | one-hand accessibility shortcut if needed | 15 | 11 | collect controlled samples after gate | may overlap `AGAIN` or `WATER` hand position |
| UNDERSTAND | Conversation check | standard one-hand where available | 14 | 15 | collect controlled samples after gate | may overlap `YES` if simplified too much |
| PAIN | Medical priority concept | one-hand accessibility shortcut if needed | 10 | 11 | collect controlled samples after gate | may overlap `HURT` |
| SICK | Medical priority concept | one-hand adaptable | 17 | 0 | download/extract or manually record | may overlap `PAIN` / `HURT` |
| HURT | Medical priority concept | one-hand accessibility shortcut if needed | 12 | 0 | download/extract or manually record | may overlap `PAIN` |
| MEDICINE | Medical priority concept | one-hand adaptable | 18 | 0 | download/extract or manually record | may overlap `DOCTOR` if near body/face |
| HOSPITAL | Medical/location context | standard one-hand where available | 13 | 0 | download/extract or manually record | may overlap `DOCTOR` / body-reference signs |
| EAT | Practical daily need | standard one-hand where available | 19 | 0 | download/extract or manually record | may overlap `WATER` near mouth/chin |

Dataset support means the word is present in local `scripts_ml/WLASL_v0.3.json`.
Local video support means matching video files were already present under the
local WLASL video directories during inspection. No new videos were downloaded
in this pass.

No replacement word is recommended yet because all ten planned additions are
present in the local WLASL metadata. If later download/extraction fails for a
word, choose the replacement from the approved pool that has the strongest local
video and manual-recording support.

## Dataset Readiness

Sprint20 is not ready to train yet:

- The `NAME` / `STOP` gate has not passed.
- `word_config.py` has not been changed to activate sprint20.
- The planned new words currently have 0 extracted sequences in
  `dataset_words_lstm`.
- The local WLASL metadata supports all ten planned words, but only the first
  five currently have local video files available.

Before training sprint20, run:

```powershell
$env:VOXGEST_WORD_PROFILE='sprint20'
.\voxgest_env\Scripts\python.exe scripts_ml\30_audit_recognition_dataset.py
```

Training readiness requirements:

- every class has enough sequences
- every class has enough groups
- `NOTHING` remains present
- new words do not silently reduce output shape incorrectly
- label JSON matches model output

## Backup Rule Before Any Sprint20 Training

If the training scripts still write to `model/voxgest_lstm_v1.*` or
`model/voxgest_tcn_v1.*`, back up the stable demo10 assets first:

```text
model/backups/demo10_before_sprint20/
```

Copy the existing model files, label files, reports, and
`model/runtime_manifest_v1.json` before sprint20 training.

## Android Rule

Android must remain defaulted to demo10 until sprint20 passes live testing.
Sprint20 assets can be added later as optional assets only; do not make sprint20
the default profile during this repair pass.
