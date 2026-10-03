# VoxGest FSL Dual-Dataset Reset — Source of Truth

Issued: 2026-09-20
Branch: recognition/fsl-dual-dataset-reset-v1
Base: recognition/mapua14-rescue-v1 @ 496782599734fd369808f4ba3d395550b2b59d71

## Why this reset exists

The previous Scenario-15 plan incorrectly treated the existing WHAT/YOUR/NAME/MY phrase lane as acceptable FSL scope. The owner clarified that those phrase signs were based on ASL / non-validated material and were never recorded as validated FSL. Therefore that lane is quarantined from the final FSL claim.

Do NOT use WHAT, YOUR, NAME, MY, the ASL alphabet, WLASL, YouTube signs, or any other non-FSL source as training/evaluation truth for the final FSL recognizer unless a qualified FSL validator explicitly approves a new capture.

## Authoritative FSL sources for this reset

### 1) FSL-105 — De La Salle University
- Dataset: FSL-105: A dataset for recognizing 105 Filipino sign language videos
- Contributor: Isaiah Jassen Tupal
- Institution: De La Salle University, Manila
- Mendeley DOI/version used as public provenance: 10.17632/48y2y99mb9.2
- 105 FSL classes
- 2,130 approximately 4-second video clips
- 640x360 compressed video, controlled blue background
- CC BY 4.0

Important local state:
- the repository contains labels/splits and trained historical artifacts;
- previous safe-C audit reported the raw FSL-105 MOV source was NOT FOUND;
- historical 64-class OneHand162 extracted features are not the desired new unified FullSign225 source.
- For this reset, prefer obtaining the official raw FSL-105 videos again into SAFE C: and re-extracting with the same FullSign225 contract used for Mapua.

### 2) Mapua Transactional FSL
- Dataset: Transactional Filipino Sign Language Dataset
- Contributors: Franz Columna, Angela Cardano
- Institution: Mapua University
- Mendeley DOI family: 10.17632/jdyrr7cm4z
- 26 transactional FSL classes.
- Official v2 publishes 1,065 processed coordinate samples.
- VoxGest also audited the downloaded raw-video archive used in the Mapua rescue lane: 1,107 MP4 across 26 classes.
- For VoxGest classifier training, use the audited raw MP4 source so MediaPipe/FullSign225 extraction is under our own reproducible contract rather than mixing the authors' preprocessed NPY contract.

## Critical correction: what MediaPipe does

MediaPipe is NOT the semantic sign classifier and does not “learn FSL words.”

MediaPipe's job:
raw frame -> pose/hand landmark coordinates.

Our classifier's job:
sequence of normalized landmarks -> FSL class.

Therefore the engineering gates are:
1. LANDMARK QUALITY: can MediaPipe track the hands/body through each source sign?
2. TEMPORAL QUALITY: does preprocessing isolate and resample the complete sign motion consistently?
3. CLASSIFIER QUALITY: can RD-TCN learn the selected FSL concepts from those tensors?
4. ANDROID PARITY: does Android produce the same feature/temporal contract?
5. LIVE QUALITY: can the Samsung recognize new physical performances without false outputs?

We train the classifier, not MediaPipe.

## Frozen FullSign225 contract

- unmirrored ML input
- pose99 | anatomical-left63 | anatomical-right63 = 225
- missing hand block = 63 zeros
- missing pose = 99 zeros
- nose-relative pose normalization
- each hand wrist-to-middle-MCP scale
- z damping x0.3
- complete sign event -> resample to 48 frames
- target classifier input [1,48,225]
- no left/right slot swap
- no arbitrary last-48-camera-frame window for the new unified lane

## Final FSL-only 15 concept candidate vocabulary

All 15 come from the two published FSL datasets:

1. HELLO — BOTH
2. HOW_ARE_YOU — FSL105
3. IM_FINE — FSL105
4. NICE_TO_MEET_YOU — FSL105
5. THANK_YOU — BOTH
6. YES — BOTH
7. NO — BOTH
8. PLEASE — MAPUA
9. HOW_MUCH — MAPUA
10. CASH — MAPUA
11. CARD — MAPUA
12. RECEIPT — MAPUA
13. WAIT — MAPUA
14. MILK — FSL105
15. RICE — FSL105

This produces one defensible demonstration scenario:
GREETING / SMALL TALK -> RETAIL COUNTER -> CLOSING.

Names are NOT currently in the validated FSL source vocabulary. Name exchange is deferred unless the team obtains a qualified-FSL-validated name/fingerspelling capture. Do not contaminate the final FSL model with the old ASL phrase/alphabet lanes.

## Existing work that remains valuable

- Mapua14 RD-TCN48 pipeline and its complete-trajectory extraction logic.
- Canonical FullSign225 normalization.
- Mapua raw-video technical audit.
- FSL-105 labels/splits/provenance and historical models as comparison/rollback only.
- Android/TFLite infrastructure.
- Standard FSL-105 and Mapua14 bundles remain protected rollback artifacts.

## Claims prohibited until new evidence exists

- “WHAT/YOUR/NAME/MY are FSL” based on the old phrase model.
- “MediaPipe understands FSL.”
- “15/15 working” without physical qualification.
- “signer-independent” if the final live profile is calibrated only to the demo signer/device.
- merging overlapping Mapua/FSL105 labels without recording source provenance and checking their trajectories.
- unrestricted FSL translation.
