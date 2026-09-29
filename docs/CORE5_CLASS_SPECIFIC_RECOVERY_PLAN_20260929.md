# VoxGest Core5 Class-Specific Recovery Plan — 2026-09-29

## Latest result

Commit `ab02560ca3a4bb00d0651c844ccd37a91da7054e` shows the global Core5-vs-Other-FSL rejector is not qualified for Android.

Observed tradeoff:
- saved Samsung correct first-stable signs: 33/53 -> 28/53 after fusion
- saved non-sign false accepts: 7/16 -> 4/16
- arbitrary-wave false accepts: 6/6 -> 3/6
- YES retained: 0/11
- NO retained: 0/9

Therefore a single global binary rejector is over-rejecting valid classes and must not be promoted.

## Diagnosis by class

### YES
YES has a classifier/domain problem before OOD rejection.
Previously on the same 11 Samsung YES tensors:
- baseline: 9/11 YES
- native48: 8/11 YES
- sim10: 4/11 YES

The next YES work should therefore inspect temporal sampling/domain sensitivity and representation drift before adding stronger rejection.

### NO
NO has been classifier-correct in saved Native48/SIM10 replay, but the new OOD fusion retains 0/9 Samsung NO events.

This indicates the current global rejector is poorly aligned with NO. Do not retrain NO merely because the rejector rejects it.

## Next architecture experiment

Replace the single global acceptance gate with class-conditioned verification research.

Concept:

1. Current temporal classifier proposes a class.
2. A verifier specific to that proposed class decides whether the event/window matches the source distribution for that class.
3. Unknown/unsupported motion is rejected if it is not close enough to the proposed class prototype/distribution.

Candidate methods to compare offline:
- class-specific one-vs-rest verifier;
- class prototype distance in learned embedding space;
- Mahalanobis/covariance distance around each class representation;
- calibrated per-class energy/logit thresholds;
- lightweight metric-learning/prototypical representation if the existing embedding is inadequate.

Thresholds must be selected from FSL-105 development data only. Samsung wave/partial captures remain sealed diagnostic tests.

## Separate workstreams

Do not solve YES and NO with the same patch.

YES:
- compare Baseline, Native48 and SIM10 on identical tensors and source trajectories;
- measure which frames/features cause SIM10 class drift;
- inspect temporal sparsification sensitivity;
- evaluate whether training-time cadence augmentation can make the class robust across source/device rates;
- no Android model swap until all Core5 classes are compared.

NO:
- keep classifier evidence intact;
- diagnose why the binary OOD model scores valid Samsung NO as Other;
- compare NO source embeddings to non-Core5 FSL-105 negatives and Samsung NO diagnostics;
- evaluate class-conditioned acceptance rather than a global Core5-like threshold.

## Deployment rule

No Android production change until:
- YES deliberate-sign retention is restored;
- NO is no longer globally rejected;
- false arbitrary-wave acceptance is materially below the current baseline;
- held-out Core5 recall remains acceptable;
- official FSL-105 test remains protected for final evaluation.

Modern UI and Avatar remain untouched.
