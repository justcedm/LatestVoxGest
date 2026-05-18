# Sprint200 Future Expansion Strategy

Do not train 200 words today. Sprint30 shows why staged expansion is necessary:
even 22 labels can expose new confusions and validation gaps.

## Staged Profiles

```text
sprint30 -> sprint50 -> sprint100 -> sprint200
```

Each stage should pass dataset audit, grouped validation, live testing, and
manual hardening before the next vocabulary jump.

## Dataset Requirements

- Enough videos per class from WLASL, ASL Citizen, or another verified source.
- Enough signer/environment diversity.
- Enough manual calibration groups for the actual demo camera.
- A strong `NOTHING` class containing idle, transition, partial, and aborted
  signs.
- No phrase-intent data mixed into the 30-frame word model.

## Preventing Class Confusion

- Add visually separable words first.
- Record contrast pairs immediately when confusions appear.
- Keep hard negatives near the confusing motion, not just idle hands.
- Use group-aware validation so augmented copies of one video do not leak into
  both train and validation.
- Keep weak labels out of the default app profile until live tests pass.

## Android Model Switching Safety

- Keep demo10 as the baseline manifest.
- Add larger profiles as optional manifests.
- Require exact label JSON and output shape checks before loading a model.
- Never let raw predictions enter the token composer.
- Keep `NOTHING` as no-output across every profile.

## Responsible Dataset Use

WLASL and ASL Citizen should be used as verified isolated-sign sources, not as a
promise of full ASL translation. Dataset videos still need extraction-quality
checks and local live calibration. Random unverified videos should stay in a
separate experimental folder and should not be mixed into official sprint
profiles.

## Manual Calibration Remains Necessary

The phone camera, signer distance, dominant hand, lighting, and one-hand
accessibility adaptations all affect live performance. Manual calibration is
the bridge between external datasets and the actual VoxGest demo environment.
