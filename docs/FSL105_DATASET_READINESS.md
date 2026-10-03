# FSL-105 Dataset Readiness

READY_FOR_TRAINING = YES
READY_FOR_DEPLOYMENT = NO

## Audited contract

- Dataset version: `fsl105_train64_onehand162_v2_audit1`
- Feature version: `onehand162_20f_nose_mcp_z03_v2`
- Shape: `[20, 162]` float32
- Layout: pose `[0, 99]`, selected hand `[99, 162]`
- Source-valid sequences: 16,146
- Training-eligible sequences after duplicate exclusion: 16,104
- Exact duplicate-content sequences excluded: 42
- Invalid sequences: 0
- Confirmed one-handed labels: 64
- FSL-105 videos processed: 1,038
- Videos contributing eligible sequences: 1,032

## Split and leakage proof

- Method: `global_signer_grouped_70_15_15_greedy_label_coverage`
- Group key: `signer_id` (no window-level random split)
- Train/validation/test sequences: 11,108 / 2,412 / 2,584
- All labels present in all splits: True
- Signer overlap count: 0
- Source-video overlap count: 0

## Readiness decision

- Feature provenance verified: True.
- Inventory matches extraction summary: True.
- All 64 labels meet 100 sequences: True.
- All train/validation/test label coverage complete: True.
- Signer/source-video leakage audit passed: True.
- Exact duplicate-content records excluded from training: 42.
- Duplicate audit passed after exclusion: True.
- Deployment remains blocked because no NSAC/background class is present and cross-device validation is pending.

## Deployment warning

This audit can authorize a closed-set experimental training run only. FSL-105 contains no canonical `NSAC`/background class, so the existing rejection policy must remain active and no Android default model or thresholds may be replaced from these results alone.

## Provenance note

Unversioned FSL-105 sidecars are accepted only because the committed extraction summary and feature-builder parity test prove equivalent pose[0:33]+right-hand[0:21] normalization math.
