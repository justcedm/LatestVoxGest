# Avaturn Avatar Integration

VoxGest now uses a local Avaturn humanoid GLB for the experimental 3D avatar path:

`android_dry_run/app/src/main/assets/avatar/models/voxgest_avatar.glb`

The Canvas avatar remains the safe fallback and still receives all playback commands. The 3D path is controlled by `AvatarFeatureFlags.ENABLE_3D_AVATAR`.

The Avaturn GLB is loaded from Android assets on a background coroutine, then handed to Filament `ModelViewer` on the main thread. No network fetch or external avatar cache is used.

The model uses a Mixamo-compatible rig. `MixamoAvatarRig` maps VoxGest's logical animation references to Mixamo bone names such as `mixamorig:RightHand`, `mixamorig:LeftArm`, and `mixamorig:Spine2`.

If the GLB has embedded animations, the first animation is played as the idle/demo loop. If it has no embedded animation, VoxGest applies a procedural breathing idle by rotating the chest bone by +/-1.5 degrees on the X axis at 0.4 Hz. Full ASL hand animation still requires rig animation data and confirmed per-sign mappings.
