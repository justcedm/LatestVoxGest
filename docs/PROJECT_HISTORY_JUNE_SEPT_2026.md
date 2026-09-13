# VoxGest Project History: June-September 2026

## 2026-09-14 - Mapua-14 live-segment hardening

Added the isolated `MAPUA14_LIVE_SEGMENT_V1` debug lane so a complete detected
sign is finalized and linearly resampled to 48 frames before the unchanged
RD-TCN48 classifier runs. Standard FSL-105, Avatar, Listen, and model weights
remain unchanged; physical Samsung A/B qualification is still required.
