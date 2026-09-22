# Debug APK asset verification

STATUS=PASS

The 2026-09-20 debug APK was opened as a ZIP after the final offline build.
Every practical-profile entry was present and its uncompressed bytes matched
the source asset:

| Asset | Bytes | SHA-256 | Match |
|---|---:|---|---|
| fsl_practical15_fullsign225_float32.tflite | 542172 | dfe78b557052032b2b288685b1de97108d0ddbb3516e3bdf758c0ef1c26219e6 | PASS |
| class_labels_fsl_practical15_v1.json | 1870 | 4d3083b853d126c243b7f9a5db94be796a99a7bfe1379ef400d1d3f82a153cb6 | PASS |
| runtime_manifest.json | 2103 | 500cb6eb77a261e0f7f07c4b91fe9798878166472e349a036fa81245c110e9eb | PASS |
| golden_fullsign225_window_f32.bin | 43200 | bf34e4a0ff79bbbf0af73b5e3c68b374565e5b17bb865dbde74b980690aad34d | PASS |
| golden_fullsign225_window_expected.json | 693 | fdf4d05862ac848d03b77032ddd720f9ce1397299c647b1e5f7eb02ed73c136c | PASS |

APK size: 161,514,586 bytes.

APK SHA-256:
`fb39fbb1581c50bbba24935a09586e8ba6e847fe9acbc49b27c2b457f5257cf4`.

The APK itself remains an uncommitted build artifact.

## Samsung install proof

On 2026-09-20 the installed `base.apk` was pulled from Samsung SM-A566B
(`R5GYC0M1M4P`) and hashed outside Git. Its size and SHA-256 exactly matched
the audited local APK above. The on-device diagnostic startup then reported:

- `FSL_PRACTICAL15_MODEL_LOAD PASS`;
- input `[1,48,225]`, output `[1,15]`, and 15 labels;
- `FSL_PRACTICAL15_TFLITE_PARITY PASS`;
- FullSign225 Android feature parity PASS with fixed anatomical hand slots;
- front camera, mirrored preview, unmirrored analysis/model input; and
- `live_approved=false`, with the Android production default unchanged.

This proves installation and startup parity. Human-in-frame anatomical overlay,
positive-class, negative/OOD, and latency qualification remain pending.

The APK was rebuilt after the isolated physical preview-mirror repair. The
installed repaired `base.apk` was pulled and exactly matched the updated hash
above. Model, labels, manifest, and both golden assets remained byte-identical.
