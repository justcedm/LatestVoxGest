# Research Scope

## Objective

VoxGest is a focused Android accessibility prototype for controlled Filipino Sign Language communication.

The project studies whether an Android device can:

- capture signing with its camera;
- extract hand and upper-body landmarks;
- classify supported temporal FSL concepts locally;
- reject unstable, incomplete, or unsupported input;
- present accepted output as text and speech;
- accept hearing-user speech and route supported concepts to a visual signing Avatar.

## In Scope

- Android deployment
- on-device landmark processing and TFLite inference
- controlled isolated FSL recognition
- selected supported vocabulary
- acceptance/rejection mechanisms
- text output
- text-to-speech
- speech-to-text when supported by the device
- supported-concept Avatar playback
- Board fallback
- software-quality and user evaluation

## Out of Scope

VoxGest does not claim:

- unrestricted continuous FSL recognition;
- complete FSL grammar interpretation;
- unrestricted sentence-level FSL translation;
- unlimited vocabulary;
- automatic linguistic correctness of Avatar motions;
- replacement of qualified FSL interpreters.

## Current Demo Philosophy

The survey/demo should evaluate what the prototype actually implements. Experimental features must be clearly separated from qualified features.

A smaller reliable vocabulary is more defensible than a larger unreliable vocabulary.
