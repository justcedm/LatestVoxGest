"""Prepare an explicitly separate review copy; do not modify source motion."""
import bpy, pathlib

output = pathlib.Path('outputs/blender_review/YES_C8_REVIEW.blend').resolve()
source = pathlib.Path(bpy.data.filepath).resolve()
assert output.drive.upper() == 'C:' and output != source
output.parent.mkdir(parents=True, exist_ok=True)
scene = bpy.context.scene
arm = bpy.data.objects['Waitress RIG']
arm.animation_data.action = bpy.data.actions['FSL_YES__EXP_C8']
for track in arm.animation_data.nla_tracks:
    track.mute = True
scene.frame_start = 1
scene.frame_end = 243
scene.render.fps = 60
scene.render.fps_base = 1
scene.frame_set(160)
scene['review_status'] = 'C8 YES experiment; human-motion and CORE3 acceptance remain open'
scene['review_controls'] = 'Space: play/pause. Shift-Left: first frame. Numpad 0: frontal camera.'
for screen in bpy.data.screens:
    for area in screen.areas:
        if area.type == 'VIEW_3D':
            space = area.spaces.active
            space.region_3d.view_perspective = 'CAMERA'
            space.overlay.show_overlays = False
            space.shading.type = 'SOLID'
            space.shading.color_type = 'TEXTURE'
            space.shading.light = 'STUDIO'
        elif area.type == 'DOPESHEET_EDITOR':
            area.spaces.active.show_seconds = True
bpy.context.preferences.filepaths.file_preview_type = 'NONE'
bpy.ops.wm.save_as_mainfile(filepath=str(output))
print('REVIEW_COPY_READY', output, flush=True)
