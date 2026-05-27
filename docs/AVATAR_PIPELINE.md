# VoxGest Lightweight Avatar Pipeline

This is a lightweight visual response prototype, not a complete ASL avatar.
It exists so a Deaf/signing user can see a simple visual response when spoken
text is recognized or when confirmed word tokens are produced.

## Goal

Known word -> play a small 2D cartoon keyframe animation.

Unknown word -> fingerspell.

`NOTHING` -> no animation.

Missing or empty animation -> clean placeholder fallback.

## Scope

Current known prototype words:

- HELLO
- THANKYOU
- WATER
- EAT
- WHAT
- YOUR
- NAME
- MY
- YOU
- OKAY
- STUDENT
- WHERE
- LIVE

YES and NO may remain as legacy prototype assets, but the current FullSign225
manual5 UI path focuses on HELLO, THANKYOU, WATER, EAT, and NOTHING.

## Asset Layout

Android app assets:

```text
android_dry_run/app/src/main/assets/avatar/avatar_manifest.json
android_dry_run/app/src/main/assets/avatar/signs/YES.json
android_dry_run/app/src/main/assets/avatar/signs/NO.json
android_dry_run/app/src/main/assets/avatar/signs/HELLO.json
android_dry_run/app/src/main/assets/avatar/signs/THANKYOU.json
android_dry_run/app/src/main/assets/avatar/signs/WATER.json
android_dry_run/app/src/main/assets/avatar/signs/EAT.json
```

Root prototype assets may also exist under:

```text
avatar/avatar_manifest.json
avatar/signs/<WORD>.json
```

Android should prefer `avatar/signs/<WORD>.json` from app assets, then fall
back to the root asset layout bundled through Gradle.

## Cartoon Avatar Model

Simple 2D character parts:

- head
- torso
- upper arm
- forearm
- hand
- face markers

Each sign file uses keyframes. A keyframe stores time in milliseconds and rough
part positions. `AvatarView` interpolates between frames with native Android
Canvas drawing and `ValueAnimator`; no Lottie dependency is required.

If JSON keyframes are missing for a supported word, Android uses built-in
Canvas fallback motions so the avatar still visibly signs during demos. Future
true 3D assets can be added under:

```text
android_dry_run/app/src/main/assets/avatar/models/voxgest_avatar.glb
android_dry_run/app/src/main/assets/avatar/animations/<WORD>.glb
```

The fallback stays active until a GLB/VRM renderer and real animation clips are
available.

## Playback Rules

- Known word token: load and play `avatar/signs/<WORD>.json`.
- Unknown word token: fingerspell each A-Z character.
- `NOTHING`: no animation and no spoken/displayed output.
- Missing file: show a clean placeholder fallback.
- Empty keyframes: show a clean placeholder fallback.

## Initial Motions

- HELLO: hand near head with wave motion.
- YES: fist nod/down-up motion.
- NO: simple index/middle close or pinch indicator.
- THANKYOU: hand from chin moving outward.
- WATER: hand near mouth/chin tap indicator.
- EAT: pinched hand moves to mouth with a small repeated tap.
- WHAT: questioning hand sweep.
- YOUR / YOU: point outward toward the viewer.
- NAME: placeholder two-finger name-sign motion.
- MY: flat hand to chest.
- OKAY: pinch/check gesture.
- STUDENT: learning/student placeholder motion.
- WHERE: questioning/location sweep.
- LIVE: upward live/home placeholder motion.

## Android Runtime Components

- `AvatarView`: native Canvas renderer for the human-like avatar.
- `AvatarController`: safe playback controller with `playWord`, `playTextAsSigns`,
  `replay`, and `stop`.
- `VoxGestMockupApp`: Compose UI route that embeds `AvatarView` with
  `AndroidView`.

`AvatarController` ignores NOTHING and routes unknown words to fingerspelling
fallback. It should only be called after a token has passed recognition gates.

## Integration Boundary

Avatar playback must stay downstream of token composition. Raw predictions,
unstable predictions, and rejected predictions must not trigger avatar output.

Speech flow:

```text
speech input -> text -> known word animation or fingerspelling
```

Recognition flow:

```text
camera input -> landmarks -> model -> gates -> token composer -> avatar
```

## Current Limitation

The avatar does not claim full ASL grammar or complete signing correctness. It
is a lightweight visual response prototype for the controlled VoxGest demo
vocabulary.
