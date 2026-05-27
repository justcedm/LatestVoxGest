# VoxGest Avatar Pipeline

The Android app now uses a fully code-drawn lightweight signing avatar.

Renderer:
- `SignAvatarView`: `SurfaceView` renderer drawing a human upper-body avatar with Android Canvas 2D.
- `AvatarAnimator`: `ValueAnimator` clip player and phrase sequencer.
- `AvatarClip`: sealed clip definitions for supported signs and A-Z fingerspelling.

There are no avatar GLB files, SceneView, Filament, OpenGL model viewers, or third-party avatar services in the active avatar path.

Playback rule:
- Confirmed word labels map to `AvatarClip` word animations.
- Single letters map to `LETTER_A` through `LETTER_Z`.
- Unknown text is fingerspelled letter by letter.
- `NOTHING` is ignored.

The renderer is intentionally simple and mobile-safe: all figure shapes are drawn with `Paint`, `Path`, and `RectF`, and drawing runs on a dedicated `HandlerThread` through a `SurfaceView`.
