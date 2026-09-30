# Avatar offline design notes

Production LISTEN remains unchanged and `listen_ready=false`.

- `AVATAR_BILINGUAL_ACTION_REGISTRY_V1.json`: 12 canonical concepts and proposed English/Filipino aliases, separate from animation data.
- `AVATAR_SENTENCE_SEQUENCER_ARCHITECTURE_V1.md`: intent normalization, FSL-reviewed phrase-plan interface, and non-overlapping playback queue.
- `AVATAR_FSL_FINGERSPELLING_REGISTRY_V1.json` and `AVATAR_FSL_FINGERSPELLING_ARCHITECTURE_V1.md`: 26 missing FSL letters and future spelling scheduler.
- `NEXT_EARLE_CALIBRATION_MANIFEST_V1.md`: five proposed sentence-enabling concepts, one conditional concept, and separate A–Z workload.

The Samsung engineering matrix and owner notes are in `reports/avatar_owner_qualification_20260930/OWNER_DEVICE_QUALIFICATION.md`. None of these files promotes an unapproved action or changes a runtime route.
