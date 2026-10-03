# VoxGest Paper Alignment Report

## Use and evidence boundaries

The paragraphs and tables below are written for direct insertion into the manuscript. All numerical model results come from the committed full-run evaluation artifacts, and all dataset quantities come from the committed dataset audit. The RD-TCN was selected using validation data only; the held-out test set was reserved for final reporting. The experimental Sign Activity Detector results come from its separate signer-grouped evaluation with synthetic negative examples. Its 86.46% validation accuracy did not meet the specified greater-than-95% target, so the detector and the complete two-stage pipeline must not be described as deployment-ready. Likewise, the ISO 25010 table is intentionally unfilled because no user/software-quality evaluation results are present in the validated artifacts.

## Chapter 3 — Feature Engineering

### Copy-ready replacement text

The study adopted the versioned feature-engineering contract *onehand162_20f_nose_mcp_z03_v2*. For each video frame, MediaPipe Holistic produced 33 pose landmarks and 21 landmarks from one selected anatomical hand. The pose component retained landmarks 0–32 in XYZ order, yielding 99 values, while the hand component retained landmarks 0–20 in XYZ order, yielding 63 values. Both components were translated relative to pose landmark 0 (the nose), after which the hand coordinates were normalized by the Euclidean distance between hand landmark 0 (wrist) and hand landmark 9 (middle-finger metacarpophalangeal joint) when this distance exceeded 0.001. All depth coordinates were multiplied by 0.3 to reduce the influence of noisier Z-axis estimates. A missing selected hand was represented by a fixed 63-value zero block, whereas a missing pose caused the complete 162-value frame vector to be zero-filled because the nose reference was unavailable. The pose and hand components were concatenated without slot shifting to form a `float32` vector of 162 features, and 20 consecutive frame vectors were assembled into a model input tensor of shape 20 × 162. For FSL-105 extraction, the selected-hand policy was fixed to MediaPipe Holistic’s anatomical right-hand slot.

## Chapter 3 — Model Development

### Copy-ready replacement text

Two temporal convolutional architectures were evaluated under the same 64-class dataset partition and preprocessing contract. The temporal convolutional network (TCN) baseline attained 82.42% validation accuracy and 83.82% held-out test accuracy, with validation and test macro-F1 scores of 82.78% and 82.50%, respectively. The residual dilated temporal convolutional network (RD-TCN) attained 89.59% validation accuracy and 92.22% held-out test accuracy, with validation and test macro-F1 scores of 89.77% and 91.54%, respectively. Model selection was performed exclusively on the validation partition by ranking validation macro-F1 first, validation accuracy second, and parameter count third as a deterministic tie-breaker; consequently, test-set performance did not influence architecture selection. The RD-TCN was selected because it achieved the higher validation macro-F1 and validation accuracy. The selected network contains 112,448 trainable parameters and maps an input tensor of shape 1 × 20 × 162 to a 64-element class-probability vector.

## Chapter 3 — Two-Stage Recognition Pipeline

### Copy-ready replacement text

To prevent the lexical classifier from being forced to assign a sign label during non-signing intervals, the live recognition design separates sign-activity detection from sign classification. In the first stage, an experimental binary Sign Activity Detector receives one 162-feature frame and estimates the probability of active signing through fully connected layers of 64 and 32 units followed by a single sigmoid output. Positive examples were sampled from the existing FSL sequences, while pseudo-negative examples were generated from zero-valued frames, Gaussian-noise frames, interpolations between different sign samples, and the boundary frames of recorded sequences; the two classes were balanced during training, and the existing signer-grouped train, validation, and test assignments were preserved before synthesis. At runtime, a frame with an activity probability below 0.50 is rejected and is not added to the temporal buffer. Frames that meet the activity criterion are appended until the buffer contains 20 frames, at which point the unchanged RD-TCN produces probabilities for the 64 lexical classes. Validation-only calibration selected a class-confidence threshold of 0.50, a top-one-versus-top-two margin threshold of 0.10, and a cooldown of 10 frames; the resulting acceptance F1 was 94.61% on the classifier validation set. However, the activity detector attained only 86.46% validation accuracy and 85.90% held-out test accuracy under the synthetic-negative protocol, below the specified greater-than-95% validation target. Its validation accuracy was particularly low for sequence-boundary pseudo-negatives (7.60%) and cross-sign interpolations (59.60%). Accordingly, the detector, calibrated gates, and complete two-stage pipeline remain experimental and are not evidence of Android or production deployment readiness.

**Evidence note for the documentator:** the greater-than-95% detector value is a target that was not met. The boundary frames are explicitly documented as a noisy entry/exit proxy rather than verified real no-sign observations. The 94.61% acceptance F1 belongs to validation-only RD-TCN gate calibration and must not be presented as detector accuracy or end-to-end live-pipeline performance.

## Chapter 3 — Dataset

### Copy-ready replacement text

The primary corpus was derived from FSL-105. A handedness review identified 64 one-handed classes for the present model, while the remaining 41 two-handed classes were deferred because they require a separately versioned two-hand feature representation. Feature extraction initially produced 16,146 valid 20-frame sequences. Content-hash auditing identified 21 exact duplicate pairs, representing 42 sequence records with conflicting labels; all 42 records were excluded from the training manifest without deleting the source files, leaving 16,104 training-eligible sequences. The eligible data were partitioned by signer into 11,108 training, 2,412 validation, and 2,584 held-out test sequences. Sixteen signers were assigned to training, three to validation, and three to testing. The resulting audit found zero signer overlap and zero source-video overlap across partitions, thereby preventing sequences from the same signer or source video from leaking between model development and final evaluation. All 64 classes were represented in every partition.

## Chapter 4 — Classification Results

### Copy-ready results paragraph

On the held-out test partition of 2,584 sequences, the selected RD-TCN correctly classified 2,383 samples and attained an overall accuracy of 92.22%. Macro-precision, macro-recall, and macro-F1 were 91.92%, 92.00%, and 91.54%, respectively, indicating that the aggregate result was not solely attributable to the more frequent classes. Nevertheless, class-level performance remained uneven: seven classes were below 80% accuracy, whereas 40 classes exceeded 95%. The most frequent directed errors included BOY classified as GIRL, GOOD EVENING classified as GOOD AFTERNOON, and SUGAR classified as NO SUGAR. These outcomes support the use of the RD-TCN as the selected experimental closed-set classifier; they do not establish live-pipeline deployment readiness because the separate Sign Activity Detector remained below its target, real non-sign recordings were unavailable, cooldown behavior was not validated on continuous live streams, and multi-device validation remained incomplete.

### Table 4.X. Model comparison

| Architecture | Parameters | Validation accuracy | Validation macro-F1 | Test accuracy | Test macro-F1 | Selection status |
|---|---:|---:|---:|---:|---:|---|
| TCN | 64,576 | 82.42% | 82.78% | 83.82% | 82.50% | Baseline |
| RD-TCN | 112,448 | 89.59% | 89.77% | 92.22% | 91.54% | Selected on validation macro-F1 |

*Table note.* The held-out test metrics are reported for final evaluation only and were not used to select the architecture.

### Experimental Sign Activity Detector results

The single-frame Sign Activity Detector contained 12,545 parameters and was evaluated at a probability threshold of 0.50 using balanced positive and synthetic-negative partitions. It attained 86.46% accuracy, 81.53% precision, 94.28% recall, and 87.44% F1 on 10,000 validation samples. On the separate 10,000-sample test partition, it attained 85.90% accuracy, 81.18% precision, 93.46% recall, and 86.89% F1. The validation accuracy therefore fell 8.54 percentage points below the specified greater-than-95% target. Category-level results further exposed weaknesses in the negative-data approximation: validation accuracy was only 7.60% for sequence-boundary pseudo-negatives and 59.60% for cross-sign interpolations. Since boundary frames from FSL-105 can still contain active signing, these results do not constitute validation on authentic non-sign activity. The detector is consequently an experimental artifact and was not promoted to the Android default.

### Table 4.X. Experimental Sign Activity Detector performance

| Partition | Samples | Accuracy | Precision | Recall | F1 | Validation-target status |
|---|---:|---:|---:|---:|---:|---|
| Validation | 10,000 | 86.46% | 81.53% | 94.28% | 87.44% | Not met |
| Test | 10,000 | 85.90% | 81.18% | 93.46% | 86.89% | Not applicable; target defined on validation |

### Table 4.X. Sign Activity Detector category-level accuracy

| Category | Validation samples | Validation accuracy | Test samples | Test accuracy | Evidence limitation |
|---|---:|---:|---:|---:|---|
| Positive sign-center frames | 5,000 | 94.28% | 5,000 | 93.46% | Existing FSL sign frames only |
| Negative zero vectors | 1,500 | 100.00% | 1,500 | 100.00% | Synthetic trivial negative |
| Negative Gaussian noise | 1,500 | 100.00% | 1,500 | 100.00% | Synthetic trivial negative |
| Negative cross-sign interpolation | 1,500 | 59.60% | 1,500 | 57.40% | Synthetic transition proxy |
| Negative sequence boundary | 500 | 7.60% | 500 | 11.20% | Noisy entry/exit proxy; not verified non-sign activity |

### Validation-only acceptance-gate calibration

The RD-TCN output gates were calibrated on the 2,412-sequence validation partition. A confidence threshold of 0.50 and a top-one-versus-top-two margin threshold of 0.10 produced an acceptance precision of 91.89%, an acceptance recall of 97.50%, and an acceptance F1 of 94.61%. A cooldown of 10 frames was selected from the tested candidates using a source-video-ordered window simulation. This simulation still yielded a 76.50% duplicate-output rate among emitted candidates and was not a continuous live-stream evaluation. Therefore, the calibrated values are reproducible experimental defaults rather than production-ready operating thresholds.

### Table 4.X. Validation-only runtime-gate calibration

| Quantity | Selected value | Validation result or interpretation |
|---|---:|---|
| RD-TCN confidence threshold | 0.50 | Calibrated on validation predictions only |
| RD-TCN top-one/top-two margin threshold | 0.10 | Calibrated jointly with confidence |
| Cooldown | 10 frames | Selected through source-video window simulation |
| Acceptance precision | 91.89% | 2,107 true accepts and 186 false accepts |
| Acceptance recall | 97.50% | 2,107 true accepts and 54 false rejects |
| Acceptance F1 | 94.61% | Must not be reported as detector accuracy or end-to-end accuracy |
| Simulated duplicate-output rate | 76.50% | Requires validation on continuous live streams |

### Copy-ready experimental-pipeline limitation paragraph

Although the two-stage architecture provides a technically explicit mechanism for rejecting non-sign frames, its present evaluation does not support a deployment-readiness claim. The Sign Activity Detector reached only 86.46% validation accuracy and 85.90% test accuracy using synthetic negative examples, and it performed poorly on the most transition-like negative categories. Moreover, the 10-frame cooldown was selected using ordered, overlapping validation windows rather than continuous live video. Authentic non-sign recordings, continuous-stream evaluation, Android preprocessing parity tests, and multi-device validation are therefore required before the detector, acceptance gates, and cooldown can be treated as production operating parameters.

### Table 4.X. Per-class held-out test accuracy (64 rows)

| No. | Class | Correct / support | Accuracy | Interpretation |
|---:|---|---:|---:|---|
| 1 | AUNTIE | 37 / 41 | 90.24% | Monitor |
| 2 | BEER | 59 / 59 | 100.00% | Solid |
| 3 | BLACK | 54 / 54 | 100.00% | Solid |
| 4 | BLIND | 43 / 43 | 100.00% | Solid |
| 5 | BLUE | 33 / 33 | 100.00% | Solid |
| 6 | BOY | 49 / 60 | 81.67% | Monitor |
| 7 | CHICKEN | 58 / 60 | 96.67% | Solid |
| 8 | COUSIN | 30 / 30 | 100.00% | Solid |
| 9 | DEAF | 45 / 50 | 90.00% | Monitor |
| 10 | DEAF BLIND | 10 / 22 | 45.45% | Requires additional recording samples |
| 11 | DON’T KNOW | 51 / 51 | 100.00% | Solid |
| 12 | DON’T UNDERSTAND | 38 / 38 | 100.00% | Solid |
| 13 | EIGHT | 30 / 30 | 100.00% | Solid |
| 14 | FAST | 47 / 48 | 97.92% | Solid |
| 15 | FATHER | 28 / 28 | 100.00% | Solid |
| 16 | FISH | 45 / 51 | 88.24% | Monitor |
| 17 | FIVE | 25 / 25 | 100.00% | Solid |
| 18 | FOUR | 28 / 28 | 100.00% | Solid |
| 19 | FRIDAY | 59 / 61 | 96.72% | Solid |
| 20 | GIRL | 20 / 27 | 74.07% | Requires additional recording samples |
| 21 | GOOD AFTERNOON | 54 / 65 | 83.08% | Monitor |
| 22 | GOOD EVENING | 18 / 36 | 50.00% | Requires additional recording samples |
| 23 | GOOD MORNING | 36 / 45 | 80.00% | Monitor |
| 24 | GRANDFATHER | 42 / 42 | 100.00% | Solid |
| 25 | GRANDMOTHER | 44 / 44 | 100.00% | Solid |
| 26 | GREEN | 15 / 15 | 100.00% | Solid |
| 27 | HARD OF HEARING | 29 / 29 | 100.00% | Solid |
| 28 | HELLO | 36 / 44 | 81.82% | Monitor |
| 29 | HOT | 33 / 33 | 100.00% | Solid |
| 30 | IM FINE | 50 / 50 | 100.00% | Solid |
| 31 | KNOW | 36 / 36 | 100.00% | Solid |
| 32 | MAN | 39 / 40 | 97.50% | Solid |
| 33 | MILK | 52 / 54 | 96.30% | Solid |
| 34 | MONDAY | 59 / 60 | 98.33% | Solid |
| 35 | MOTHER | 18 / 18 | 100.00% | Solid |
| 36 | NINE | 47 / 47 | 100.00% | Solid |
| 37 | NO | 43 / 46 | 93.48% | Monitor |
| 38 | NO SUGAR | 17 / 21 | 80.95% | Monitor |
| 39 | ONE | 20 / 21 | 95.24% | Solid |
| 40 | ORANGE | 26 / 26 | 100.00% | Solid |
| 41 | PARENTS | 35 / 50 | 70.00% | Requires additional recording samples |
| 42 | PINK | 30 / 30 | 100.00% | Solid |
| 43 | RED | 37 / 37 | 100.00% | Solid |
| 44 | SEE YOU TOMORROW | 50 / 59 | 84.75% | Monitor |
| 45 | SEVEN | 38 / 38 | 100.00% | Solid |
| 46 | SIX | 44 / 55 | 80.00% | Monitor |
| 47 | SUGAR | 10 / 21 | 47.62% | Requires additional recording samples |
| 48 | TEN | 30 / 30 | 100.00% | Solid |
| 49 | THREE | 51 / 51 | 100.00% | Solid |
| 50 | THURSDAY | 27 / 35 | 77.14% | Requires additional recording samples |
| 51 | TOMORROW | 36 / 45 | 80.00% | Monitor |
| 52 | TUESDAY | 33 / 35 | 94.29% | Monitor |
| 53 | TWO | 13 / 13 | 100.00% | Solid |
| 54 | UNCLE | 35 / 41 | 85.37% | Monitor |
| 55 | UNDERSTAND | 51 / 54 | 94.44% | Monitor |
| 56 | WEDNESDAY | 57 / 57 | 100.00% | Solid |
| 57 | WHITE | 37 / 41 | 90.24% | Monitor |
| 58 | WINE | 53 / 59 | 89.83% | Monitor |
| 59 | WOMAN | 30 / 39 | 76.92% | Requires additional recording samples |
| 60 | WRONG | 33 / 33 | 100.00% | Solid |
| 61 | YELLOW | 30 / 30 | 100.00% | Solid |
| 62 | YES | 37 / 37 | 100.00% | Solid |
| 63 | YESTERDAY | 29 / 29 | 100.00% | Solid |
| 64 | YOURE WELCOME | 54 / 54 | 100.00% | Solid |

*Table note.* Per-class accuracy equals recall: correct predictions for the class divided by its true-class support. “Requires additional recording samples” denotes accuracy below 80.00%; “Solid” denotes accuracy above 95.00%; all remaining classes are marked “Monitor.”

### Table 4.X. Most frequent directed confusion pairs

| Rank | True class | Predicted class | Error count | True-class support | Confusion rate |
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

*Table note.* Each pair is directional, and its rate is calculated relative to the support of the true class. The tenth position is part of a tie among pairs with six errors; the ordering shown follows the stored evaluation report.

### Copy-ready confusion interpretation paragraph

The confusion analysis suggests that the largest errors were concentrated in signs with related lexical content or visually overlapping motion components. SUGAR was classified as NO SUGAR in 11 of 21 instances (52.38%), while GOOD EVENING was classified as GOOD AFTERNOON in 11 of 36 instances (30.56%). Bidirectional confusion was also observed between GOOD AFTERNOON and GOOD EVENING and between PARENTS and UNCLE. In addition, temporal or component overlap was evident for SEE YOU TOMORROW versus TOMORROW and TOMORROW versus TEN. These directed errors indicate that future data collection should prioritize contrastive samples for the affected pairs across additional signers, camera devices, distances, and lighting conditions. The existing held-out test set should remain unchanged so that subsequent improvements can be compared against the same evaluation reference.

## Chapter 4 — ISO 25010 Results Template

### Table 4.X. ISO 25010 software-quality evaluation results

| Quality characteristic | Evaluated subcharacteristics or indicator | Respondents / trials | Result | Interpretation |
|---|---|---:|---:|---|
| Functional suitability | Functional completeness, correctness, and appropriateness | TBD | TBD | To be filled after evaluation |
| Performance efficiency | Time behavior, resource utilization, and capacity | TBD | TBD | To be filled after evaluation |
| Compatibility | Co-existence and interoperability | TBD | TBD | To be filled after evaluation |
| Usability | Recognizability, learnability, operability, user-error protection, and accessibility | TBD | TBD | To be filled after evaluation |
| Reliability | Maturity, availability, fault tolerance, and recoverability | TBD | TBD | To be filled after evaluation |
| Security | Confidentiality, integrity, accountability, authenticity, and related safeguards | TBD | TBD | To be filled after evaluation |
| Maintainability | Modularity, reusability, analyzability, modifiability, and testability | TBD | TBD | To be filled after evaluation |
| Portability | Adaptability, installability, and replaceability | TBD | TBD | To be filled after evaluation |

### Table 4.X. ISO 25010 questionnaire-item summary

| Item | Quality characteristic | Evaluation statement | Mean score | Verbal interpretation |
|---:|---|---|---:|---|
| 1 | TBD | TBD | TBD | TBD |
| 2 | TBD | TBD | TBD | TBD |
| 3 | TBD | TBD | TBD | TBD |
| 4 | TBD | TBD | TBD | TBD |
| 5 | TBD | TBD | TBD | TBD |
| … | Add rows to match the approved instrument | — | — | — |

*Template note.* Replace every `TBD` only after the approved ISO 25010 instrument has been administered and its scoring procedure has been documented. Do not substitute the model’s 92.22% classification accuracy for a software-quality survey result; these are different measurements.

## Evidence traceability

| Manuscript claim | Validated source artifact |
|---|---|
| 16,104 eligible sequences; 42 excluded duplicates; 64/41 handedness split; zero leakage | `reports/fsl105_dataset_audit.json` |
| TCN validation/test metrics | `reports/fsl_tcn_evaluation.json` |
| RD-TCN validation/test metrics, parameter count, per-class results, confusion matrix | `reports/fsl_rdtcn_evaluation.json` |
| Sign Activity Detector architecture, synthetic-data protocol, category metrics, and unmet target | `reports/activity_detector_v1_evaluation.json` |
| Validation-only confidence, margin, cooldown, and acceptance metrics | `model/runtime_manifest.json` and `reports/fsl_rejection_calibration.json` |
| Exact held-out per-class and directed-pair presentation | `reports/confusion_analysis_v2.md` |
| Full grouped partition membership | `reports/fsl105_split_manifest.csv` |
| Canonical feature definition | `docs/VOXGEST_FEATURE_CONTRACT.md` |
