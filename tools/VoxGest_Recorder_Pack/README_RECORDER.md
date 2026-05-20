# VoxGest Recorder Pack

This folder lets teammates record VoxGest FullSign225 landmark samples with only Python, a webcam, and batch files.

You do not need VS Code, Codex, Android Studio, or Ced's `voxgest_env`.

## What You Will Record

- `EAT`
- `WATER`
- `HELLO`
- `THANKYOU`
- `NOTHING`

Do not train anything. Just record, zip the output, and send it to Ced.

## Setup

1. Install Python 3.10 or newer if Python is not installed.
2. Copy `VoxGest_Recorder_Pack` to your Desktop.
3. Open the `VoxGest_Recorder_Pack` folder.
4. Double-click `setup_recorder.bat` once.
5. Wait until it says setup is complete.

## Set Your Signer ID

Before recording, edit these two files with Notepad:

- `run_record_core_words.bat`
- `run_record_nothing.bat`

Find this line near the top:

```bat
set "VOXGEST_SIGNER_ID=TEAMMATE_A"
```

Change `TEAMMATE_A` to your assigned name, for example:

```bat
set "VOXGEST_SIGNER_ID=JAY"
```

Use the same signer ID in both files.

## Record Core Words

1. Double-click `run_record_core_words.bat`.
2. The webcam window will open.
3. Follow the countdown.
4. Record each sign clearly.
5. If a sample is rejected, repeat it.

This records 30 accepted samples each for:

- `EAT`
- `WATER`
- `HELLO`
- `THANKYOU`

## Record NOTHING

1. Double-click `run_record_nothing.bat`.
2. Record neutral or non-word movements.
3. If a sample is rejected, repeat it.

This records 50 accepted samples for:

- `NOTHING`

Good `NOTHING` examples:

- idle hands visible
- open relaxed hands
- hands entering the frame
- hands leaving the frame
- small neutral movements
- aborted signs
- transition movements between signs

## Recording Rules

- Sit or stand with your upper body visible.
- Keep both hands visible.
- Use good lighting.
- Face the camera.
- Start neutral.
- Perform one sign clearly.
- Hold briefly.
- Stop and relax.
- If a sample is rejected, repeat it.

## Send Your Output

After recording:

1. Find the `recorded_features` folder.
2. Right-click it.
3. Choose "Send to" -> "Compressed (zipped) folder".
4. Send the zip file to Ced.

Expected output:

```text
recorded_features/
  fullsign225_manual5_team_features/
    EAT/
    WATER/
    HELLO/
    THANKYOU/
    NOTHING/
```

Saved files include your signer ID, for example:

```text
TEAMMATE_A_EAT_001.npy
TEAMMATE_A_WATER_001.npy
TEAMMATE_A_NOTHING_001.npy
```

## Keys

- `Q` or `ESC`: quit
- `S`: skip the current word

Again: do not train anything. Ced will collect and train on the main VoxGest PC.
