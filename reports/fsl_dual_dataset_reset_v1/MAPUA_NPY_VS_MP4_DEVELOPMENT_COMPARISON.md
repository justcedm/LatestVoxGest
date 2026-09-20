# Mapua NPY vs MP4 development comparison

Generated UTC: 2026-09-20T16:48:43.748595+00:00

## Fixed experimental contract

- Architecture: RD-TCN48, 125391 parameters.
- Same 334 frozen development clips and four source-group-preserving folds.
- Same seeds, augmentation, class weights, batch size, optimizer, learning rate, and early stopping.
- Metric is combined out-of-fold development performance; signer independence is not claimed.
- Frozen split metadata was read only to select development rows; sealed feature files, predictions, and metrics were not opened.
- No Android model or profile was changed.

## Required decision fields

MP4_PIPELINE_CV_MACRO_F1=0.950410728
NPY_PIPELINE_CV_MACRO_F1=0.962949558
MP4_WEAKEST_CLASS_F1=0.812500000
NPY_WEAKEST_CLASS_F1=0.882352941
MP4_TOP_CONFUSIONS=HOW_MANY->COIN:3; CASH->THANK_YOU:2; NO->YES:2; AGAIN->HOW_MANY:1; AGAIN->HOW_MUCH:1; CARD->DISCOUNT:1; CARD->HELLO:1; HOW_MANY->WAIT:1; HOW_MUCH->HOW_MANY:1; NO->WAIT:1
NPY_TOP_CONFUSIONS=AGAIN->DISCOUNT:2; CASH->THANK_YOU:2; AGAIN->HOW_MANY:1; CARD->COIN:1; CARD->DISCOUNT:1; HOW_MANY->COIN:1; HOW_MANY->WAIT:1; HOW_MUCH->HOW_MANY:1; NO->WAIT:1; YES->NO:1
BEST_DEVELOPMENT_PIPELINE=ORIGINAL_NPY

## Aggregate development metrics

| Pipeline | Accuracy | Macro precision | Macro recall | Macro F1 | Weakest F1 | ECE-10 | NLL |
|---|---:|---:|---:|---:|---:|---:|---:|
| MP4/Holistic | 0.955089820 | 0.950950399 | 0.952417323 | 0.950410728 | 0.812500000 | 0.018051216 | 0.243155658 |
| Original NPY | 0.964071856 | 0.966036292 | 0.962277776 | 0.962949558 | 0.882352941 | 0.010011780 | 0.173637331 |

## Fold evidence

| Fold | MP4 macro F1 | NPY macro F1 | MP4 accuracy | NPY accuracy |
|---:|---:|---:|---:|---:|
| 0 | 0.953760684 | 0.961965812 | 0.963414634 | 0.963414634 |
| 1 | 0.937948718 | 0.946173826 | 0.941176471 | 0.941176471 |
| 2 | 0.987464387 | 1.000000000 | 0.987951807 | 1.000000000 |
| 3 | 0.922649573 | 0.945775336 | 0.928571429 | 0.952380952 |

## Per-class F1

| Class | MP4/Holistic | Original NPY | Delta NPY-MP4 |
|---|---:|---:|---:|
| HELLO | 0.969696970 | 1.000000000 | +0.030303030 |
| THANK_YOU | 0.952380952 | 0.952380952 | +0.000000000 |
| YES | 0.956521739 | 0.976744186 | +0.020222447 |
| NO | 0.943396226 | 0.964285714 | +0.020889488 |
| PLEASE | 1.000000000 | 1.000000000 | +0.000000000 |
| HOW_MUCH | 0.900000000 | 0.947368421 | +0.047368421 |
| CASH | 0.952380952 | 0.952380952 | +0.000000000 |
| CARD | 0.964285714 | 0.964285714 | +0.000000000 |
| RECEIPT | 1.000000000 | 1.000000000 | +0.000000000 |
| WAIT | 0.941176471 | 0.961538462 | +0.020361991 |
| HOW_MANY | 0.812500000 | 0.882352941 | +0.069852941 |
| AGAIN | 0.945454545 | 0.943396226 | -0.002058319 |
| PROBLEM | 1.000000000 | 1.000000000 | +0.000000000 |
| COIN | 0.938775510 | 0.958333333 | +0.019557823 |
| DISCOUNT | 0.979591837 | 0.941176471 | -0.038415366 |

## Confusion review

- MP4/Holistic: HOW_MANY->COIN:3; CASH->THANK_YOU:2; NO->YES:2; AGAIN->HOW_MANY:1; AGAIN->HOW_MUCH:1; CARD->DISCOUNT:1; CARD->HELLO:1; HOW_MANY->WAIT:1; HOW_MUCH->HOW_MANY:1; NO->WAIT:1; WAIT->AGAIN:1.
- Original NPY: AGAIN->DISCOUNT:2; CASH->THANK_YOU:2; AGAIN->HOW_MANY:1; CARD->COIN:1; CARD->DISCOUNT:1; HOW_MANY->COIN:1; HOW_MANY->WAIT:1; HOW_MUCH->HOW_MANY:1; NO->WAIT:1; YES->NO:1.
- Full 15x15 matrices are stored in `MAPUA_NPY_VS_MP4_CONFUSION_MATRIX.csv`.
- Required pair review: CARD/COIN, CASH/PROBLEM, NO/YES, and HOW_MANY/HOW_MUCH is represented explicitly in those matrices even when a cell is zero.

## Decision boundary

`ORIGINAL_NPY` is the stronger offline development representation under the fixed experiment.
This is not evidence that it transfers better to Android MediaPipe Tasks. The next decision
requires Samsung pre-normalization landmark capture and the planned three-domain comparison.
The deployed FSL_PRACTICAL15_V1 model remains unchanged.
