# Temporary 3D Hands Integration

This Android integration prepares a temporary 3D hands-only avatar test using:

`android_dry_run/app/src/main/assets/avatar/hands/models/shoulder_arm_hands.glb`

The current Canvas avatar remains the fallback renderer. After the Samsung phone test passed, the experimental feature flag is:

`AvatarFeatureFlags.ENABLE_3D_HANDS = true`

When the flag is false, the Avatar card creates and uses the existing `AvatarView` path exactly as before.

When the flag is true, the Avatar card uses `Temporary3DHandsAvatarHostView`, which checks that the GLB asset exists and initializes Google Filament's Android `ModelViewer` to load the GLB. If the GLB is missing, Filament setup fails, the device cannot create the renderer, or model loading fails, the host falls back to the Canvas avatar and logs the reason instead of crashing.

This is intentionally a safe staging hook. It adds the minimal Filament Android dependencies needed for GLB rendering, but it does not remove the production Canvas avatar fallback.

If the GLB contains an animation, the first animation is played as a simple idle/demo loop. This does not imply ASL animation support. The GLB hand model is intended for future avatar rendering and animation experiments. Full ASL hand animation still requires rigging, animation clips, hand-pose retargeting, timing work, and confirmed sign-to-animation mapping.
