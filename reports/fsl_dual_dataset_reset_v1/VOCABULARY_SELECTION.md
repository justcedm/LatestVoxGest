# Evidence-driven practical vocabulary selection

FINAL_VOCABULARY_STATUS=FROZEN_15_MAPUA_ONLY

PROFILE_ID=FSL_PRACTICAL15_V1

## Decision

The 15-class published-Mapua candidate pool is frozen unchanged for the
interim offline/Android profile:

HELLO, THANK_YOU, YES, NO, PLEASE, HOW_MUCH, CASH, CARD, RECEIPT, WAIT,
HOW_MANY, AGAIN, PROBLEM, COIN, DISCOUNT.

This list is not the original dual-source wish list. Official FSL-105 raw is
still unavailable, so FSL105-only social/product concepts cannot be audited or
mixed honestly. COIN and DISCOUNT fill those two positions because they are
published Mapua transactional concepts with 27 and 28 usable complete-event
clips, respectively.

## Selection evidence

Pilot scope: one RD-TCN48 architecture, four frozen development folds, class
weights, training-only modest landmark augmentation, and no sealed-test access.

- Development clips: 334.
- Combined out-of-fold accuracy: 0.9491017964.
- Combined out-of-fold macro-F1: 0.9453016957.
- Mean fold macro-F1: 0.9441034891.
- Combined weakest-class F1: 0.7878787879.
- Sealed clips accessed during selection: 0.

| Concept | Role/relevance | PASS | Usable rate | Any-hand | Dropout | CV precision | CV recall | CV F1 | Main observed risk | Decision |
|---|---|---:|---:|---:|---:|---:|---:|---:|---|---|
| HELLO | greeting / 5 | 19 | 0.388 | 0.604 | 0.059 | 0.941 | 1.000 | 0.970 | low raw usable rate | KEEP |
| THANK_YOU | closing / 5 | 24 | 0.571 | 0.687 | 0.038 | 1.000 | 1.000 | 1.000 | tracking watch | KEEP |
| YES | confirmation / 5 | 26 | 0.650 | 0.914 | 0.027 | 0.917 | 1.000 | 0.957 | receives NO errors | KEEP |
| NO | rejection / 5 | 33 | 0.786 | 0.897 | 0.004 | 1.000 | 0.893 | 0.943 | YES x2, WAIT x1 | KEEP |
| PLEASE | politeness / 5 | 17 | 0.405 | 0.882 | 0.006 | 1.000 | 1.000 | 1.000 | low usable volume | KEEP |
| HOW_MUCH | retail question / 5 | 12 | 0.273 | 0.579 | 0.060 | 0.900 | 0.900 | 0.900 | HOW_MANY x1; smallest class | KEEP, WATCH |
| CASH | payment / 5 | 26 | 0.684 | 0.713 | 0.007 | 1.000 | 0.864 | 0.927 | AGAIN/DISCOUNT/HOW_MANY | KEEP |
| CARD | payment / 5 | 34 | 0.773 | 0.766 | 0.035 | 1.000 | 0.897 | 0.945 | COIN/DISCOUNT/HELLO | KEEP |
| RECEIPT | transaction close / 5 | 28 | 0.667 | 0.770 | 0.007 | 1.000 | 1.000 | 1.000 | none in OOF | KEEP |
| WAIT | interaction control / 5 | 29 | 0.725 | 0.849 | 0.012 | 0.923 | 0.960 | 0.941 | AGAIN x1 | KEEP |
| HOW_MANY | quantity question / 4 | 20 | 0.455 | 0.620 | 0.052 | 0.813 | 0.765 | 0.788 | COIN x3, WAIT x1 | KEEP, WATCH |
| AGAIN | repair / 4 | 33 | 0.750 | 0.742 | 0.022 | 0.929 | 0.929 | 0.929 | HOW_MANY/HOW_MUCH | KEEP |
| PROBLEM | repair / 4 | 38 | 0.905 | 0.858 | 0.018 | 1.000 | 1.000 | 1.000 | none in OOF | KEEP |
| COIN | retail contingency / 4 | 27 | 0.614 | 0.718 | 0.023 | 0.852 | 1.000 | 0.920 | receives HOW_MANY/CARD | KEEP |
| DISCOUNT | retail contingency / 4 | 28 | 0.636 | 0.678 | 0.003 | 0.923 | 1.000 | 0.960 | receives CARD/CASH | KEEP |

Role/relevance is a transparent 1–5 practical communication score, not a model
metric. All landmark metrics are same-method raw-video MediaPipe measurements.

## Alternatives not selected

- WELCOME: only 11 PASS clips, 0.275 usable rate, any-hand 0.611, internal
  dropout 0.098, and it must not be relabeled as FSL-105 YOURE_WELCOME.
- Numbers ONE–TEN: generally separable and useful in some retail contexts, but
  the frozen set already covers confirmation, repair, payment, receipt,
  quantity, pricing, waiting, and discount. Substituting a number would reduce
  scenario breadth merely to raise an already strong offline score.
- FSL105-only HOW_ARE_YOU, IM_FINE, NICE_TO_MEET_YOU, YOURE_WELCOME,
  UNDERSTAND, DONT_UNDERSTAND, KNOW, DONT_KNOW, GOOD_MORNING,
  SEE_YOU_TOMORROW, MILK, and RICE: PENDING_OFFICIAL_RAW.

## Rejection evidence

Confidence/margin grid search used development out-of-fold predictions only.
No tested grid point produced zero wrong accepts. The minimum-error point was
confidence 0.95 and margin 0.05:

- accepted 277/334;
- accepted-correct 275;
- wrong-accepted 2;
- accepted precision 0.9927797834;
- coverage 0.8293413174.

Some wrong predictions were extremely confident. Therefore confidence and
margin are necessary but insufficient. The Android experimental lane must also
require a complete event, tracking quality, duration bounds, and release before
re-arm. Thresholds remain preparation evidence, not live-approved production
values.

## Freeze rationale

The final count is 15 because the evidence supports 15, not because 15 was
forced. Every class has:

- published FSL provenance from Mapua;
- at least 12 independently validated complete-event tensors;
- finite canonical [48,225] input;
- non-zero coverage in every development fold;
- out-of-fold F1 at or above 0.7878787879;
- a practical communication or retail role.

HOW_MANY is the weakest class and is explicitly carried as a live-test watch
item. It is retained because its quantity-question function is more useful than
an easier number-only replacement and its development evidence remains above
the defensibility floor. Samsung testing may still demote it.
