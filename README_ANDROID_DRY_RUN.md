# VoxGest Android Dry-Run Demo

This Android scaffold is intentionally focused on today's achievable goal: a buildable dry-run app that exercises the same accepted-token path the real recognizer must use later. Phrase recognition stays off, vocabulary stays at the current demo set, and `NOTHING` remains a no-output class.

## Project Location

Android project:

```powershell
cd .\android_dry_run
```

Main package:

```text
app/src/main/java/com/voxgest/dryrun
```

Packaged assets:

```text
../model/*.tflite
../avatar/signs/*.json
```

## Build And Run

Prerequisites:

- Android Studio or Android SDK command-line tools
- JDK 17
- Gradle available on `PATH`, or open the project in Android Studio and let it use the IDE Gradle runtime
- An emulator or USB device visible to `adb`

Command-line build:

```powershell
cd .\android_dry_run
gradle :app:assembleDebug
```

Install:

```powershell
adb install -r .\app\build\outputs\apk\debug\app-debug.apk
```

Launch:

```powershell
adb shell am start -n com.voxgest.dryrun/.MainActivity
```

Android Studio path:

```text
File > Open > android_dry_run > Run app
```

## Screens

1. Startup / permission screen
   - Requests camera and microphone permissions.
   - Shows demo safety note.
   - Shows model loader status.

2. Live recognition screen
   - Minimal live demo UI with AUTO / LETTERS / WORDS mode switch.
   - Large recognized text.
   - Current prediction label.
   - Confidence/debug line.
   - Sentence strip.
   - Clear, Speak, and Mic buttons.
   - Avatar panel.

3. Debug dry-run screen
   - Buttons for `YES`, `NO`, `PLEASE`, `WATER`, `HELLO`, `HELP`, `STOP`, `DOCTOR`, `NAME`, `THANKYOU`, `NOTHING`, `A-Z`, `space`, and `del`.
   - Every button goes through `TokenComposer`, the same acceptance path intended for real recognition output.

## What Works Now

- Dry-run accepted tokens update the sentence strip.
- `NOTHING` is ignored and does not update the sentence or avatar.
- `del` removes the last active letter or token.
- Letter buttons compose spelled words.
- `space` commits an in-progress spelled word.
- Known word tokens route to the avatar playback controller.
- Missing or placeholder word animation JSON shows `Playing sign: WORD`.
- Unknown spoken words are fingerspelled.
- Speak button uses Android `TextToSpeech`.
- Mic button uses Android `SpeechRecognizer` when available.
- Spoken text is displayed in large text, tokenized, and routed to known-word avatar playback or fingerspelling.
- TFLite loader classes are prepared for static letters and dynamic words.

## Placeholder Today

- Full MediaPipe camera landmark extraction is not wired yet.
- `FeatureExtractor.extractFromMediaPipeFrame(...)` is a contract placeholder.
- `StaticLetterRecognizer.recognize(...)` and `DynamicWordRecognizer.recognize(...)` load model interpreters but do not yet decode live tensors.
- Avatar JSON files in `avatar/signs` currently exist as placeholder contracts with empty keyframes.
- Live recognition screen is UI-ready, but real camera inference is not enabled.

## Demo Safety

Stable demo words:

```text
YES, NO, WATER, HELLO, THANKYOU
```

Unstable/debug words until more live hardening:

```text
HELP, STOP, DOCTOR, NAME, PLEASE
```

Do not use unstable words as proof of final recognition quality yet. Latest live testing showed STOP can be confused with NAME, and left-hand HELP / STOP / DOCTOR / NAME / NOTHING still need more clean samples.

## Integration Classes

- `TfliteModelLoader`
- `StaticLetterRecognizer`
- `DynamicWordRecognizer`
- `FeatureExtractor`
- `TokenComposer`
- `AvatarPlaybackController`
- `SpeechController`

## Next Step: Connect Real Camera Landmarks

1. Add the MediaPipe Tasks Vision dependency for Android.
2. Build a camera frame source using CameraX.
3. Feed each frame into MediaPipe Hand Landmarker and Pose Landmarker.
4. Convert landmarks into the existing VoxGest feature contracts:
   - static letters: `63` features
   - dynamic words: `30 x 162` features
5. Preserve physical-hand mapping:
   - `VOXGEST_DOMINANT_HAND=left` should mean the user's physical left hand even when preview is mirrored.
   - `VOXGEST_DOMINANT_HAND=right` should mean the user's physical right hand even when preview is mirrored.
6. Pass only accepted gated predictions into `TokenComposer`.
7. Keep raw model predictions out of the final sentence strip.

