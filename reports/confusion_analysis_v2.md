# VoxGest RD-TCN Held-Out Test Confusion Analysis

## Scope and provenance

This report analyzes the already-completed held-out test inference recorded in `reports/fsl_rdtcn_evaluation.json`; it does not rerun inference or alter the test set. The evaluated model is the 64-class residual dilated temporal convolutional network (RD-TCN) trained with feature contract `onehand162_20f_nose_mcp_z03_v2`. The test partition contains 2,584 sequences and belongs to grouped split assignment SHA-256 `cdbf63a5c6400b8933fb2d8e963f722fa5c27f40cae54a11fbd85980b3e4ba54`. The accompanying dataset audit confirms zero signer overlap and zero source-video overlap among the training, validation, and test partitions.

The stored confusion matrix contains 2,383 correct predictions out of 2,584 test samples, corresponding to 92.22% overall accuracy. The reported test macro-precision, macro-recall, and macro-F1 are 91.92%, 92.00%, and 91.54%, respectively. Per-class accuracy is the diagonal confusion-matrix count divided by the support for the corresponding true class; it is therefore equivalent to per-class recall. No acceptance/rejection thresholds are applied in these closed-set classification results.

Status rules are applied exactly as requested:

- Accuracy below 80.00%: **requires additional recording samples**.
- Accuracy above 95.00%: **solid**.
- Accuracy from 80.00% through 95.00%, inclusive: **monitor**.

## Per-class test accuracy (all 64 classes)

| No. | True class | Correct | Support | Accuracy | Status |
|---:|---|---:|---:|---:|---|
| 1 | AUNTIE | 37 | 41 | 90.24% | monitor |
| 2 | BEER | 59 | 59 | 100.00% | **solid** |
| 3 | BLACK | 54 | 54 | 100.00% | **solid** |
| 4 | BLIND | 43 | 43 | 100.00% | **solid** |
| 5 | BLUE | 33 | 33 | 100.00% | **solid** |
| 6 | BOY | 49 | 60 | 81.67% | monitor |
| 7 | CHICKEN | 58 | 60 | 96.67% | **solid** |
| 8 | COUSIN | 30 | 30 | 100.00% | **solid** |
| 9 | DEAF | 45 | 50 | 90.00% | monitor |
| 10 | DEAF BLIND | 10 | 22 | 45.45% | **requires additional recording samples** |
| 11 | DON’T KNOW | 51 | 51 | 100.00% | **solid** |
| 12 | DON’T UNDERSTAND | 38 | 38 | 100.00% | **solid** |
| 13 | EIGHT | 30 | 30 | 100.00% | **solid** |
| 14 | FAST | 47 | 48 | 97.92% | **solid** |
| 15 | FATHER | 28 | 28 | 100.00% | **solid** |
| 16 | FISH | 45 | 51 | 88.24% | monitor |
| 17 | FIVE | 25 | 25 | 100.00% | **solid** |
| 18 | FOUR | 28 | 28 | 100.00% | **solid** |
| 19 | FRIDAY | 59 | 61 | 96.72% | **solid** |
| 20 | GIRL | 20 | 27 | 74.07% | **requires additional recording samples** |
| 21 | GOOD AFTERNOON | 54 | 65 | 83.08% | monitor |
| 22 | GOOD EVENING | 18 | 36 | 50.00% | **requires additional recording samples** |
| 23 | GOOD MORNING | 36 | 45 | 80.00% | monitor |
| 24 | GRANDFATHER | 42 | 42 | 100.00% | **solid** |
| 25 | GRANDMOTHER | 44 | 44 | 100.00% | **solid** |
| 26 | GREEN | 15 | 15 | 100.00% | **solid** |
| 27 | HARD OF HEARING | 29 | 29 | 100.00% | **solid** |
| 28 | HELLO | 36 | 44 | 81.82% | monitor |
| 29 | HOT | 33 | 33 | 100.00% | **solid** |
| 30 | IM FINE | 50 | 50 | 100.00% | **solid** |
| 31 | KNOW | 36 | 36 | 100.00% | **solid** |
| 32 | MAN | 39 | 40 | 97.50% | **solid** |
| 33 | MILK | 52 | 54 | 96.30% | **solid** |
| 34 | MONDAY | 59 | 60 | 98.33% | **solid** |
| 35 | MOTHER | 18 | 18 | 100.00% | **solid** |
| 36 | NINE | 47 | 47 | 100.00% | **solid** |
| 37 | NO | 43 | 46 | 93.48% | monitor |
| 38 | NO SUGAR | 17 | 21 | 80.95% | monitor |
| 39 | ONE | 20 | 21 | 95.24% | **solid** |
| 40 | ORANGE | 26 | 26 | 100.00% | **solid** |
| 41 | PARENTS | 35 | 50 | 70.00% | **requires additional recording samples** |
| 42 | PINK | 30 | 30 | 100.00% | **solid** |
| 43 | RED | 37 | 37 | 100.00% | **solid** |
| 44 | SEE YOU TOMORROW | 50 | 59 | 84.75% | monitor |
| 45 | SEVEN | 38 | 38 | 100.00% | **solid** |
| 46 | SIX | 44 | 55 | 80.00% | monitor |
| 47 | SUGAR | 10 | 21 | 47.62% | **requires additional recording samples** |
| 48 | TEN | 30 | 30 | 100.00% | **solid** |
| 49 | THREE | 51 | 51 | 100.00% | **solid** |
| 50 | THURSDAY | 27 | 35 | 77.14% | **requires additional recording samples** |
| 51 | TOMORROW | 36 | 45 | 80.00% | monitor |
| 52 | TUESDAY | 33 | 35 | 94.29% | monitor |
| 53 | TWO | 13 | 13 | 100.00% | **solid** |
| 54 | UNCLE | 35 | 41 | 85.37% | monitor |
| 55 | UNDERSTAND | 51 | 54 | 94.44% | monitor |
| 56 | WEDNESDAY | 57 | 57 | 100.00% | **solid** |
| 57 | WHITE | 37 | 41 | 90.24% | monitor |
| 58 | WINE | 53 | 59 | 89.83% | monitor |
| 59 | WOMAN | 30 | 39 | 76.92% | **requires additional recording samples** |
| 60 | WRONG | 33 | 33 | 100.00% | **solid** |
| 61 | YELLOW | 30 | 30 | 100.00% | **solid** |
| 62 | YES | 37 | 37 | 100.00% | **solid** |
| 63 | YESTERDAY | 29 | 29 | 100.00% | **solid** |
| 64 | YOURE WELCOME | 54 | 54 | 100.00% | **solid** |

## Top 10 directed confusion pairs

Each row is directional: “true class → predicted class.” The confusion rate is the error count divided by the support of the true class, not by the total test set. The tenth position is part of a tie among several pairs with six errors; the table preserves the ordering stored in the evaluation artifact.

| Rank | True class | Predicted class | Errors | True-class support | Directed confusion rate |
|---:|---|---|---:|---:|---:|
| 1 | BOY | GIRL | 11 | 60 | 18.33% |
| 2 | GOOD EVENING | GOOD AFTERNOON | 11 | 36 | 30.56% |
| 3 | SUGAR | NO SUGAR | 11 | 21 | 52.38% |
| 4 | SIX | WEDNESDAY | 9 | 55 | 16.36% |
| 5 | TOMORROW | TEN | 9 | 45 | 20.00% |
| 6 | GOOD AFTERNOON | GOOD EVENING | 7 | 65 | 10.77% |
| 7 | GOOD EVENING | TEN | 7 | 36 | 19.44% |
| 8 | PARENTS | UNCLE | 7 | 50 | 14.00% |
| 9 | SEE YOU TOMORROW | TOMORROW | 7 | 59 | 11.86% |
| 10 | DEAF BLIND | BLIND | 6 | 22 | 27.27% |

## Classes requiring additional recording samples

Seven classes are below 80.00% held-out test accuracy and are therefore flagged exactly as requested:

| Class | Accuracy | Correct / support |
|---|---:|---:|
| DEAF BLIND | 45.45% | 10 / 22 |
| GIRL | 74.07% | 20 / 27 |
| GOOD EVENING | 50.00% | 18 / 36 |
| PARENTS | 70.00% | 35 / 50 |
| SUGAR | 47.62% | 10 / 21 |
| THURSDAY | 77.14% | 27 / 35 |
| WOMAN | 76.92% | 30 / 39 |

These flags identify priorities for future collection; they do not establish that sample count alone is the causal explanation. The directed errors indicate particularly important contrastive recording targets, including SUGAR versus NO SUGAR, GOOD EVENING versus GOOD AFTERNOON, PARENTS versus UNCLE, DEAF BLIND versus BLIND/DEAF, and GIRL versus BOY/WOMAN. Future recordings should preserve signer and device diversity and should be assigned to new grouped partitions rather than added to the existing held-out test set.

## Solid classes

Forty classes exceed 95.00% held-out test accuracy and are flagged as solid: BEER, BLACK, BLIND, BLUE, CHICKEN, COUSIN, DON’T KNOW, DON’T UNDERSTAND, EIGHT, FAST, FATHER, FIVE, FOUR, FRIDAY, GRANDFATHER, GRANDMOTHER, GREEN, HARD OF HEARING, HOT, IM FINE, KNOW, MAN, MILK, MONDAY, MOTHER, NINE, ONE, ORANGE, PINK, RED, SEVEN, TEN, THREE, TWO, WEDNESDAY, WRONG, YELLOW, YES, YESTERDAY, and YOURE WELCOME.

“Solid” is a relative status within this held-out FSL-105 split. It is not evidence of production readiness or cross-device generalization; those claims require separate Android, webcam, lighting, distance, and signer-diversity evaluations.

## Reproducibility record

| Item | Value |
|---|---|
| Evaluation artifact | `reports/fsl_rdtcn_evaluation.json` |
| Dataset audit | `reports/fsl105_dataset_audit.json` |
| Model architecture | RD-TCN |
| Model parameters | 112,448 |
| Feature contract | `onehand162_20f_nose_mcp_z03_v2` |
| Dataset version | `fsl105_train64_onehand162_v2_audit1` |
| Split method | `global_signer_grouped_70_15_15_greedy_label_coverage` |
| Full split assignment hash | `cdbf63a5c6400b8933fb2d8e963f722fa5c27f40cae54a11fbd85980b3e4ba54` |
| Held-out test sequences | 2,584 |
| Correct predictions | 2,383 |
| Test accuracy | 92.22% |
| Test macro-precision | 91.92% |
| Test macro-recall | 92.00% |
| Test macro-F1 | 91.54% |
| Signer/source-video leakage | 0 / 0 |
