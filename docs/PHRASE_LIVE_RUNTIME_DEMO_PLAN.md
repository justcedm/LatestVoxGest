# Phrase Live Runtime Demo Plan

## Purpose

This plan keeps the phrase models available for live testing without retraining today. The current issue is not only model accuracy; the live capture timing and quality gates are also deciding whether a usable 30-frame gesture window reaches the model.

## Latest Log Interpretation

Reviewed logs:

- `reports/live_word_test_log_20260525_111019.json`
- `reports/live_word_test_log_20260525_111332.json`

OneHand162 phrase result:

- `MY` is the most demo-usable one-hand token.
- `WHAT` is partially usable but needs cleaner capture timing.
- `YOUR` and `NAME` are not demo-safe yet because they collapse into `NOTHING`.
- `NOTHING` remains useful as no-output, but quality blocking still appears in negative trials.

FullSign225 phrase result:

- `MY` and `LIVE` are the strongest current phrase tokens.
- `OKAY` is promising because it predicted correctly, but strict thresholds rejected it for low confidence/motion.
- `YOUR`, `YOU`, `WHAT`, `NAME`, `STUDENT`, and `WHERE` are not demo-safe yet due to wrong labels or capture/quality blocks.

## Runtime Calibration

Use strict mode by default. For controlled demo live testing only:

```powershell
$env:VOXGEST_GATE_PROFILE='demo'
```

Demo gate behavior:

- Lowers thresholds for `MY`, `OKAY`, `YOU`, and `YOUR`.
- Lowers motion and wrist-path requirements for low-motion phrase signs.
- Keeps `BAD_SEQUENCE`, `LOW_HAND_PRESENCE`, and `NO_HAND` blocking.
- Keeps `NOTHING` as no-output. It must never become a displayed/spoken token.

## Capture Timing

Use manual one-word capture for phrase testing:

```powershell
$env:VOXGEST_LIVE_TEST_CAPTURE_MODE='manual_capture'
```

Flow:

1. Show `WAITING`.
2. Press `SPACE` or `C` when ready.
3. Logger waits for stable hand presence for 10 frames.
4. Logger records exactly one 30-frame window.
5. Logger classifies that window and prints detailed debug metrics.

This avoids `hand_trigger_auto` capturing the entry/exit motion instead of the actual sign.

## Demo-Safe Labels

Use these labels for a conservative panelist demo:

- FullSign225: `MY`, `LIVE`, optionally `OKAY` with demo gate.
- OneHand162: `MY`, optionally `WHAT` with manual capture.

Do not present `YOUR`, `NAME`, `YOU`, `STUDENT`, or `WHERE` as reliable live-camera labels yet.

## Recommended Commands

OneHand162 phrase debug:

```powershell
$env:VOXGEST_ENABLE_PHRASE='0'
$env:VOXGEST_WORD_PROFILE='onehand162_phrase_v1'
$env:VOXGEST_FEATURE_PROFILE='onehand162'
$env:VOXGEST_DYNAMIC_MODEL='tcn'
$env:VOXGEST_SINGLE_HAND_POSE='1'
$env:VOXGEST_DOMINANT_HAND='right'
$env:VOXGEST_MODE='WORDS'
$env:VOXGEST_GATE_PROFILE='demo'
$env:VOXGEST_LIVE_TEST_CAPTURE_MODE='manual_capture'
$env:VOXGEST_LIVE_TEST_LABELS='WHAT,MY,NOTHING'
.\voxgest_env\Scripts\python.exe scripts_ml\33_live_word_test_logger.py
```

FullSign225 phrase debug:

```powershell
$env:VOXGEST_ENABLE_PHRASE='0'
$env:VOXGEST_WORD_PROFILE='fullsign225_phrase_v1'
$env:VOXGEST_FEATURE_PROFILE='fullsign225'
$env:VOXGEST_DYNAMIC_MODEL='tcn'
$env:VOXGEST_SINGLE_HAND_POSE='0'
$env:VOXGEST_MODE='WORDS'
$env:VOXGEST_GATE_PROFILE='demo'
$env:VOXGEST_LIVE_TEST_CAPTURE_MODE='manual_capture'
$env:VOXGEST_LIVE_TEST_LABELS='MY,OKAY,LIVE,NOTHING'
.\voxgest_env\Scripts\python.exe scripts_ml\33_live_word_test_logger.py
```

## What Remains Experimental

- Phrase camera recognition is not ready to be the main presentation path.
- Phrase-builder demo buttons in Android remain safer for adviser testing.
- More live capture hardening is needed before retraining or expanding labels.
