# VoxGest Android Frontend Product Flow

This document describes the current Android dry-run frontend. It is intentionally UI-first and offline-first: no Python training, phrase-recognition backend, or full CameraX plus MediaPipe plus TFLite inference wiring is enabled here.

## Screens

### Camera

- Shows the dark camera preview surface, stability bar, large prediction label, sentence strip, and Speak/Clear actions.
- Uses demo-safe confirmed tokens only. Raw model predictions are not allowed to write directly into the sentence output.
- Speak sends the composed sentence to local TextToSpeech and records a Signed message in History.

### Avatar

- Shows a lightweight cartoon avatar stage, status pill, large response text, phrase-match label, mic FAB, demo phrase chips, and fingerspell input.
- Known animated words are: YES, NO, HELLO, THANKYOU, WATER.
- Controlled fallback words, such as HELP, PLEASE, STOP, and DOCTOR, use a clean placeholder pose and label.
- NAME is handled through fingerspelling fallback for demo safety.
- SpeechRecognizer partial results update the large response text without logging. Final results log a Heard message.

### Shortcuts

- Emergency, Daily, Medical, and Basic Response sections use the new token system and 8dp grid.
- Shortcut taps speak the phrase locally, map it to an avatar sequence/fallback, and add it to History.
- Emergency buttons are larger, red, and explicitly labelled for accessibility.

### History

- Uses a real RecyclerView adapter backed by an in-memory conversation store.
- Signed messages are left-aligned; Heard, Shortcut, and Emergency messages are right-aligned.
- Clear History requires a confirmation dialog.

## Accessibility And Motion

- Interactive controls have content descriptions.
- History cards expose source, time, and message text.
- The avatar updates its content description as the active word changes.
- Animations check Android's transition animation scale and skip nonessential motion when it is disabled.

## Current Placeholders

- Camera preview remains a UI/dry-run placeholder until full CameraX, MediaPipe, and TFLite inference are integrated.
- Avatar motions are a lightweight visual response prototype, not a complete ASL avatar.
- Conversation history is in-memory for this dry-run build and is cleared when the process is killed.
