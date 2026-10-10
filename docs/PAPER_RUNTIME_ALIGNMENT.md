# Paper / Runtime Alignment

This document identifies items that must remain synchronized between the manuscript and the evaluated Android build.

## Current Alignment Targets

The current paper describes:

- controlled FSL communication
- MediaPipe pose and two-hand landmarks
- FullSign225
- temporal recognition
- FSL-105 as the primary documented dataset condition
- a separate Mapua transactional experimental condition
- acceptance/rejection mechanisms
- text and speech output
- speech-to-text
- verified FSL Avatar playback
- ISO/IEC 25010 evaluation

## Important Runtime Difference to Resolve in Final Manuscript

Recent engineering work uses a **Core5 survey recognition profile with a 48-frame FullSign225 temporal contract**, while a current manuscript passage describes a primary FSL-105 baseline using **20-frame x 225 features** and a separate Mapua condition using 48 frames.

Before final submission, the team must decide which exact model/build was evaluated and update the manuscript so the evaluated runtime contract, figures, methodology, model description, and reported results all agree.

Do not silently rewrite evaluation results to match an earlier plan.

## Vocabulary

The evaluated manuscript must list the vocabulary actually used by the survey build.

At the current survey-candidate stage, the controlled recognition set is:

- HELLO
- THANK YOU
- YES
- NO
- UNDERSTAND

Any Core10 expansion should be documented only if it is actually trained, integrated, device-tested, and included in the evaluated APK.

## Avatar

The final paper should distinguish:

- known-good technical actions
- candidate actions
- FSL-expert validated actions

Technical playback alone must not be reported as linguistic validation.

## Speech Recognition

If Android offline speech availability varies by device/language, report it as a platform/runtime limitation. Manual Play Signs should remain an independent fallback and should be described separately from STT.
