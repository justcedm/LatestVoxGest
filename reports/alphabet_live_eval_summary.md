# Alphabet Live Evaluation Summary

Status: first live webcam evaluation complete; letter hardening needed.

Run this test when the signer is ready:

```powershell
.\voxgest_env\Scripts\python.exe scripts_ml\30_eval_static_alphabet_live.py
```

## Scope

Required labels:

```text
A-Z, del, space, nothing
```

## Success Gates

- Average live accuracy: 95% or higher.
- Each letter/control: 85% or higher.
- `nothing` must not hallucinate random letters during idle/no-hand states.
- `space` and `del` must be usable enough for text correction.

## Current Result

Generated:

```text
reports/static_alphabet_live_eval.json
reports/static_alphabet_live_eval.csv
reports/static_alphabet_live_confusion.csv
```

Overall result for tested letters only:

```text
208 / 260 = 80.0%
```

This does not meet the 95% average target.

| Label | Correct | Attempts | Accuracy | Main Confusion |
| --- | ---: | ---: | ---: | --- |
| A | 10 | 10 | 100% | - |
| B | 10 | 10 | 100% | - |
| C | 10 | 10 | 100% | - |
| D | 9 | 10 | 90% | space |
| E | 10 | 10 | 100% | - |
| F | 10 | 10 | 100% | - |
| G | 7 | 10 | 70% | low confidence, P |
| H | 8 | 10 | 80% | low confidence, space |
| I | 10 | 10 | 100% | - |
| J | 0 | 10 | 0% | D, I, low confidence |
| K | 9 | 10 | 90% | F |
| L | 10 | 10 | 100% | - |
| M | 10 | 10 | 100% | - |
| N | 9 | 10 | 90% | M |
| O | 10 | 10 | 100% | - |
| P | 1 | 10 | 10% | G, low confidence |
| Q | 8 | 10 | 80% | P, N |
| R | 10 | 10 | 100% | - |
| S | 10 | 10 | 100% | - |
| T | 9 | 10 | 90% | K |
| U | 10 | 10 | 100% | - |
| V | 10 | 10 | 100% | - |
| W | 10 | 10 | 100% | - |
| X | 0 | 10 | 0% | Z |
| Y | 10 | 10 | 100% | - |
| Z | 9 | 10 | 90% | D |
| del | 0 | 0 | n/a | not tested |
| space | 0 | 0 | n/a | not tested |
| nothing | 0 | 0 | n/a | not tested |

## Interpretation

- `J` should be handled as a motion letter, not a static pose.
- `Z` is also motion-based; the static model got 90% in this test, but the
  correct long-term design is an optional J/Z motion-letter model.
- `X`, `P`, `G`, `H`, and `Q` need static calibration or signing/framing
  correction.
- `del`, `space`, and `nothing` were not tested, so they are still unknown.

## After Test

The live evaluator writes:

```text
reports/static_alphabet_live_eval.json
reports/static_alphabet_live_eval.csv
reports/static_alphabet_live_confusion.csv
```

Weak static labels for the next calibration pass:

```text
G H P Q X nothing
```

Motion-letter labels for the dynamic pass:

```text
J Z NOTHING
```

## Calibration Rule

Do not retrain the static alphabet model until live testing shows weak signs.

For static calibration, record only weak labels plus `nothing`:

```powershell
.\voxgest_env\Scripts\python.exe scripts_ml\31_record_static_calibration.py G H P Q X nothing
```

Retrain only after enough calibration samples exist:

```powershell
.\voxgest_env\Scripts\python.exe scripts_ml\32_train_static_landmark_v4.py
```

For motion letters, record J/Z motion samples separately:

```powershell
$env:VOXGEST_ENABLE_PHRASE='0'
$env:VOXGEST_SINGLE_HAND_POSE='1'
$env:VOXGEST_DOMINANT_HAND='right'
$env:VOXGEST_MOTION_LETTER_SEQUENCES_PER_LABEL='20'
.\voxgest_env\Scripts\python.exe scripts_ml\34_record_motion_letters.py J Z NOTHING
```

Run that recorder three separate times, changing distance/angle/speed a little
each time. After three short groups per label, train:

```powershell
.\voxgest_env\Scripts\python.exe scripts_ml\35_train_motion_letter_tcn.py
```
