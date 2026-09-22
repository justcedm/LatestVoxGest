# Offline deep-work final summary

STATUS=OFFLINE_COMPLETE_ANDROID_READY_PHYSICAL_GATE_PENDING

## Frozen outcome

- Profile: `FSL_PRACTICAL15_V1`, debug-only and non-default.
- Source: published Mapua Transactional FSL only; FSL-105 official raw pending.
- Vocabulary (15): HELLO, THANK_YOU, YES, NO, PLEASE, HOW_MUCH, CASH, CARD,
  RECEIPT, WAIT, HOW_MANY, AGAIN, PROBLEM, COIN, DISCOUNT.
- Data: 394 PASS complete-event clips, 334 development, 60 sealed.
- Model: RD-TCN48, 125,391 parameters.
- Offline sealed result: 95.0% accuracy, 0.955718 macro-F1, 0.857143 weakest
  class F1.
- Confusions: CARD -> COIN, CASH -> PROBLEM, NO -> YES (one each).
- Raw replay: 95.0%, zero extraction failures, 60/60 exact envelope matches,
  60/60 bit-identical regenerated cache tensors.
- TFLite: [1,48,225] -> [1,15], top-1 agreement 1.0, max difference
  4.172325134277344e-07.
- Android: complete-event collector/resampler integrated; 101/101 JVM tests
  pass; `assembleDebug` and the five-asset APK byte audit pass.

## Rejection conclusion

The 0.95 confidence and 0.05 margin preparation point is not a sufficient OOD
detector. Development-only corruptions remained confidently classifiable:
partial-quarter 68.6%, reversed 88.6%, shuffled 89.2%, and static 91.0% by the
score gate alone. Structural checks reject missing pose, low hand presence,
insufficient events, timeouts, non-finite input, and exact zero-motion events.
The new motion floor 0.02 is below the minimum development sign motion 0.0728
and retains 334/334 development clips. Physical negatives are still mandatory.

## Integrity

- Standard FSL-105 model SHA-256:
  `42d040ec2269d437546d327decaaca32839abdd6bb63b2d400063c90630e5d13`.
- Mapua14 rescue model SHA-256:
  `f850c5d414c5c253ef9131bae5a85bb3ed5ad5412abdf9936df510c6ec043dcc`.
- Demo/manual5 model SHA-256:
  `97ee230505e5d6ca82caa4c5ffc604dc431c7e0b2bc95c54cec4bf59545c7633`.
- ASL/WLASL/phrase/team/internet data remained quarantined.
- Avatar and unrelated UI were not changed.

## Remaining gates

1. Acquire official FSL-105 v2 raw media and run the prepared canonical audit
   before any dual-source claim.
2. Execute the Samsung protocol in
   `SAMSUNG_QUALIFICATION_PROTOCOL.md`.
3. Keep the profile experimental unless live raw correctness, wrong-accept,
   negative false-accept, and latency evidence pass.
