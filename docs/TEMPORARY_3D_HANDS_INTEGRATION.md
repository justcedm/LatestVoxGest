# Temporary 3D Hands Integration

This Android integration prepares a temporary 3D hands-only avatar test using:

`android_dry_run/app/src/main/assets/avatar/hands/models/shoulder_arm_hands.glb`

The current Canvas avatar remains the default renderer. The feature flag is:

`AvatarFeatureFlags.ENABLE_3D_HANDS = false`

When the flag is false, the Avatar card creates and uses the existing `AvatarView` path exactly as before.

When the flag is true, the Avatar card uses `Temporary3DHandsAvatarHostView`, which checks that the GLB asset exists and then looks for an optional SceneView/Filament renderer at runtime. If the renderer dependency is missing, the GLB is missing, or renderer setup fails, the host falls back to the Canvas avatar and logs the reason instead of crashing.

This is intentionally a safe staging hook. It does not add or force a SceneView/Filament Gradle dependency, and it does not replace the production Canvas avatar.

The GLB hand model is intended for future avatar rendering and animation experiments. Full ASL hand animation still requires rigging, animation clips, hand-pose retargeting, timing work, and a stable Android 3D renderer integration.
