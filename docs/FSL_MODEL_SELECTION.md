# FSL Model Selection

| Model | Val accuracy | Val macro-F1 | Test accuracy | Test macro-F1 | Parameters | Float16 TFLite |
|---|---:|---:|---:|---:|---:|---:|
| tcn | 0.8242 | 0.8278 | 0.8382 | 0.8250 | 64,576 | 150.4 KB |
| rdtcn | 0.8959 | 0.8977 | 0.9222 | 0.9154 | 112,448 | 264.7 KB |

Selected experimental model: **RDTCN**.

Selection is deterministic and validation-only: validation macro-F1, then validation accuracy, then smaller parameter count. The held-out test set is used only for final reporting, not architecture selection.

This selection does not promote a model to Android. The stable model, class mapping, and rejection thresholds remain unchanged because the FSL-105 subset has no NSAC/background class and cross-device validation is incomplete.
