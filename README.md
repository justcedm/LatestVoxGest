# VoxGest

**VoxGest** is an Android-based bidirectional accessibility prototype for controlled Filipino Sign Language (FSL) communication between signing users and hearing or non-signing users.

This branch is intentionally **documentation-only**. It contains no application source code, ML binaries, datasets, private Avatar assets, device logs, or build artifacts.

## Current Capstone Scope

VoxGest demonstrates two controlled communication directions:

1. **Sign -> Text / Speech**
   - Android camera input
   - MediaPipe pose and hand landmarks
   - temporal landmark recognition
   - acceptance/rejection checks
   - readable text
   - optional text-to-speech

2. **Speech / Supported Concept -> FSL Avatar**
   - Android speech recognition when supported
   - strict supported-concept mapping
   - calibrated Avatar playback for supported actions
   - manual **Play Signs** fallback independent of speech recognition

VoxGest is **not** presented as an unrestricted FSL translator, a full continuous-sign recognizer, or a replacement for qualified FSL interpreters.

## Current Survey/Demo Recognition Set

The current controlled Core5 survey path uses:

- HELLO
- THANK YOU
- YES
- NO
- UNDERSTAND

The deployed experimental recognition contract uses a temporal FullSign225 representation with pose plus anatomical left/right hand landmarks. Current runtime qualification and model-selection evidence are maintained separately from the public documentation.

## Current Avatar Runtime Catalog

Known-good Core3 controls:
- HELLO
- MILK
- RICE

Candidate actions for expert evaluation:
- THANK YOU
- YES
- NO
- I'M FINE
- HOW ARE YOU
- UNDERSTAND
- GOOD EVENING
- KNOW
- WRONG

Candidate actions must not be described as linguistically validated FSL until qualified evaluation is complete.

## Research Evaluation

The capstone evaluates selected ISO/IEC 25010 quality characteristics:

- Functional Suitability
- Performance Efficiency
- Reliability
- Usability

Technical and accessibility/linguistic evaluation are kept conceptually separate so that general usability ratings are not treated as proof of FSL linguistic correctness.

## Documentation

- [Current Implementation](docs/CURRENT_IMPLEMENTATION.md)
- [System Architecture](docs/SYSTEM_ARCHITECTURE.md)
- [Research Scope](docs/RESEARCH_SCOPE.md)
- [Datasets and Validation](docs/DATASETS_AND_VALIDATION.md)
- [Survey and Evaluation](docs/SURVEY_AND_EVALUATION.md)
- [Limitations and Ethics](docs/LIMITATIONS_AND_ETHICS.md)
- [Paper / Runtime Alignment](docs/PAPER_RUNTIME_ALIGNMENT.md)
- [Roadmap](docs/ROADMAP.md)

## Development Principle

**Reliability over vocabulary size.** A smaller set of tested FSL concepts is preferred over a larger set of unreliable or linguistically unverified outputs.
