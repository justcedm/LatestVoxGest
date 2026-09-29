# Core5 YES/NO class-specific recovery — offline forensic result, 2026-09-29

**Decision: no Android implementation or model promotion.** YES is an existing-classifier/domain sensitivity on identical Samsung tensors. NO is raw-classifier-correct but systematically under-scored by the global OOD rejector, including on its own FSL-105 development clips. None of the class-conditioned methods tested here both retains all five signs and rejects unsupported motion. This analysis did not modify Android, modern UI, Avatar, production gates, deployed classifiers, vocabulary, or private source recordings.

The requested recovery plan was absent from the recognition branch and read completely from freshly fetched `origin/main` without checkout. The preceding OOD dual-stream report is commit `ab02560ca3a4bb00d0651c844ccd37a91da7054e`. Only FSL-105 **official-train-derived** source features were opened. `test.csv`, official test clips, and official test feature NPZs were not opened or used for training, calibration, or this comparison. The candidate ZIP's historical metadata references a test-sourced golden example; metadata was inspected to confirm the bundle, but its golden tensor and result were **not used** in this sprint. Samsung operator labels remain diagnostic intent, not independently validated linguistic ground truth; no Samsung event entered fitting or calibration.

## Frozen models and same-tensor five-class check

Frozen TFLite SHA-256 values: Baseline `3518ddeb68e69afa37428b8c5fc08b9d3b93e396fa1549224493da293f0ea484`; Native48 `3cff57f526fd0aab4d531855f838ee8c100a97958be39c7cbbdcbd6d30797def`; SIM10 `3702ff77c1c44a60f0dc7f06e19e778b6498df7dbf7e205991dc15158b8e888f`. All 69 saved Samsung events were verified against the existing tensor hashes/feature contract; 53 deliberate-class events had final tensors. The same exact tensor was run through all three models, reproducing the earlier A/B counts:

| Intended class | Events | Baseline raw correct | Native48 raw correct | SIM10 raw correct |
|---|---:|---:|---:|---:|
| HELLO | 14 | 14 | 14 | 14 |
| THANK YOU | 9 | 9 | 9 | 9 |
| YES | 11 | **9** | 8 | **4** |
| NO | 9 | 7 | 9 | 9 |
| UNDERSTAND | 10 | 8 | 8 | 8 |

One YES event used the physical LEFT hand and is wrong as THANK YOU under all three models. The YES gap persists among the remaining right-hand events. This is not a label-order, TFLite conversion, or Android tensor-parity failure. Baseline's YES advantage alone cannot justify a model swap: it loses two NO events relative to Native48/SIM10, and previous gate/OOD tests still false-accepted motion.

## YES: cadence, windows, landmarks, and embeddings

For the 16 available FSL-105 **train** YES clips (12 OOD-fit, two calibration, two holdout), original source observations have median `60.0 Hz` and 243 frames; the separately run sparse Tasks extraction has median `10.0 Hz` and 41 frames. Saved Samsung YES events have median effective `8.33 Hz`. Using the **same original raw source trajectories and envelope**, three deterministic 10-fps phase offsets (0/33/66 ms) were each resampled to `[48,225]`. A fourth comparison used the separately extracted sparse Tasks tensor. All three frozen models remained YES top-1 on all 16 source clips under all five source representations. SIM10's minimum YES probability varied (`0.6805` full, `0.6376` at the weakest phase, `0.9115` on separately extracted sparse Tasks), so cadence affects score but did **not** reproduce the Samsung top-1 failures on this source set. Most clips are model-training-domain examples; the two-per-partition development counts are too small to rule out cadence effects in genuinely new signers.

The 11 saved Samsung YES events generated 877 source-duration rolling windows. Baseline called 710 windows YES, Native48 566, SIM10 410. There were **301** windows where Baseline said YES and SIM10 said another class: SIM10 said HELLO on 280 and NO on 21; Native48 still said YES on 216/301. These flips occurred across the four 1.2/1.4/1.7/2.0-s window lengths; 279/301 met the existing pose/hand/motion quality predicate. Median flip-window pose presence and hand presence were both `1.0`, so the observed class drift is not explained by a missing-hand frame. The private replay preserves every window's five probabilities, timing, length, presence, and motion, including the first flip in each event.

The source/device feature domain is different: median sparse-source versus Samsung-YES pose-block mean absolute magnitude `0.267` versus `0.442`; right-hand block `1.737` versus `1.569`. The signer/camera, body position, event duration, and tracker sampling are not independently controlled in this dataset, so these differences are **correlates, not a proven single causal feature**. The Baseline Keras 64-D penultimate embedding is safely extractable and agrees with its frozen TFLite probabilities to `1.1920929e-7` on checked tensors. Native48/SIM10 bundles provide TFLite post-softmax outputs but no safely verified corresponding intermediate-embedding model, so their internal representation drift is **not** claimed. The defensible YES diagnosis is model-specific source→Samsung decision-boundary sensitivity under the same input, with a 10-fps-only cause **not established**. Training-time cadence augmentation was therefore **not** run or selected in this sprint; mixed-cadence *evaluation* was completed first.

## NO: classifier, global OOD, geometry, and representation

Native48/SIM10 are both NO top-1 on 9/9 saved Samsung NO final tensors. SIM10 confidence ranges `0.907–0.999`, with high top1–top2 margins, so retraining NO to fix a wrong raw label is not indicated. The global OOD cutoff from source calibration is `0.370789`. NO is low-scored **on source development as well as device**: FSL-105 NO calibration scores `0.276, 0.371`; holdout `0.271, 0.323`; saved Samsung median `0.333` (range `0.293–0.381`). Both source holdout NO clips were rejected despite correct NO raw predictions; only one Samsung NO final tensor crosses the cutoff, and none gave a selected rolling first-stable NO in the prior dual-stream study. This is class-specific global-rejector bias/poor generalization, not merely Samsung camera drift.

The global rejector's 24-D penultimate embedding was extracted from its own Keras model. After robust scaling fitted on source-fit embeddings, Euclidean distance to the source-fit NO centroid is `0.813–0.814` for the two NO holdout clips but `5.61–6.66` for nine Samsung NO events; source Other-FSL holdout has median distance `1.226`, with a broad tail. These distances show a device-domain shift in this learned embedding and **do not** support claiming Samsung NO lies close to the source-NO embedding cluster. In contrast, raw body-relative semantic geometry places all nine saved NO events inside the source-fit NO p95 cutoff (`0.906–1.125` observed distances versus `1.682` cutoff), and the frozen classifier calls them NO. The useful interpretation is that the binary OOD representation is misaligned with NO in both source development and device, with additional device embedding shift; it is not proof that the sign is linguistically validated.

## Class-conditioned verifier experiment

The SIM10 classifier is the **unchanged** proposed-class generator. A TFLite-parity-checked Baseline 64-D embedding supplies class-specific features. One-vs-rest L2 logistic verifiers (one per class) and class mean prototypes fit only the 61 Core5 plus 1,218 usable Other-FSL source-fit clips. An 8-D PCA is fit on source-fit Core5 only; per-class Ledoit–Wolf shrinkage covariances (12–13 fit examples per class) give a regularized Mahalanobis distance. All per-class score cutoffs are the minimum of that class's **two source calibration positives**, independent of Samsung. This very small calibration set is a severe uncertainty: one HELLO calibration clip is raw-wrong as YES, causing a permissive HELLO confidence cutoff of `0.080`. The old global OOD/product cutoffs are read from the prior source-only evaluation artifact. No threshold was chosen using source holdout or Samsung. A true energy comparison is **not identifiable** from these post-softmax TFLite models: log-sum-exp of log probabilities is constant, while logit energy requires unavailable pre-softmax logits. A new metric-learning model was not justified from only 12–13 source-fit positives per class and no signer-separated validation.
The final matrix uses the frozen OOD **TFLite** probabilities; its Keras counterpart differs by at most `4.1723251e-7` over all 1,763 available source/Samsung tensors. This avoids treating a borderline Keras score as if it were the deployed-model score.

All methods below use **final event tensors**, without the temporal three-window stability condition; do not compare their numbers as if they were the prior rolling-window result. Source holdout has 10 Core5 and 200 Other-FSL clips; Samsung has 53 intended Core5 events and 16 negatives (including five neutral no-inference events). `Correct` means proposed and accepted class equals intent; wrong-accepted signs and negative false accepts are shown separately.

| Verifier | Source holdout correct /10 | Source Other-FSL false accepts /200 | Samsung correct /53 | Samsung wrong accepted /53 | Samsung non-sign false accepts /16 | Samsung NO correct /9 |
|---|---:|---:|---:|---:|---:|---:|
| Global OOD | 8 | 45 | 26 | 0 | 1 | 1 |
| Global confidence×OOD | **8** | 38 | 26 | 0 | 1 | 1 |
| Per-class confidence | 4 | 60 | 33 | **6** | **6** | 7 |
| Per-class margin | 4 | 61 | 33 | **6** | **6** | 7 |
| One-vs-rest logistic | 5 | **20** | 24 | 0 | 5 | 0 |
| Class prototype distance | 6 | 79 | 3 | 0 | 1 | 0 |
| Shrinkage Mahalanobis | 4 | 78 | 4 | 0 | 1 | 0 |

On Samsung, per-class confidence accepts **4/6 waves and 2/5 partials**; one-vs-rest accepts **3/6 waves and 2/5 partials**. Prototype and Mahalanobis each accept one partial and no waves only by rejecting almost all real signs. All methods reject the five neutral/no-inference events. No method fixes YES because a verifier cannot recover a class that SIM10 did not propose. Per-class confidence recovers some NO but worsens false/wrong acceptance and performs poorly on source holdout. Geometry alone, as an additional diagnostic, retains all nine raw-correct Samsung NO final tensors and rejects 16/16 saved negatives, but it also accepts 89/200 source Other-FSL final tensors and retains **zero** Samsung THANK YOU final tensors; it is not a safe replacement verifier. The 8-D covariance estimates are mathematically conditioned (largest condition number `23.35`) but statistically weak with 12–13 class examples and two calibration positives; no Mahalanobis deployment claim follows.

The prior source-selected **rolling** confidence×OOD replay remains `28/53` intended first-stable signs and `4/16` non-sign first-stable false peaks. The current **final-tensor** global product row is `26/53` and `1/16`. These differ because they answer different questions and must not be silently combined.

## Evidence, limits, and next action

Private forensic JSONs (not committed) retain full per-window probability vectors and timestamps, YES paired cadence tensors' statistics, NO source/device embeddings, every class-conditioned event score/decision, and all-model event probabilities. Their SHA-256 values respectively are:

- `core5_yes_no_forensics_20260929.json`: `c3d36cb55f0d6d90457fd94456d9d800df634bd5f6066d0b84619a76412a0e47`
- `core5_class_verifiers_tflite_v3_20260929.json`: `6c313fee463f94237ceddd1d62c0a9c693501dd3c100524c3083b066bc9b6026`
- `core5_frozen_allclass_same_tensor_20260929.json`: `16c75f9d736cfdc53219d30ab977f5fc75c032ebda26a32ae118d012e47eb507`

The protocol is still weak for deployment because of no signer identities, only two source calibration and holdout positives per class, source-trained classifier overlap with some OOD fit clips, and small, non-independent operator-intent Samsung diagnostics. No official sealed test clip was used to repair these limitations.

`BEST_OFFLINE_ARCHITECTURE=NONE_QUALIFIED`; retain separate classifier proposal and independent event/OOD verification as a research direction only. `ANDROID_IMPLEMENTATION_AUTHORIZED=NO`. The next exact action is to establish an independently annotated, signer- or device-separated development set with verified YES/NO sign envelopes and true waves/partials, then compare an **isolated** mixed-cadence/domain-augmented five-class candidate and separately fit class-aware OOD verifiers on all five classes. Calibrate only on source development, preserve the current Samsung set as sealed evaluation, and require strong class retention **and** reduced waves/partials before any Android experiment.
