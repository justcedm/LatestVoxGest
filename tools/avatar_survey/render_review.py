"""Blender 3.2 read-only source -> NEW review blend and 60 FPS movie.

blender --background --factory-startup --disable-autoexec --python render_review.py --
  --blend SOURCE.blend --action THANK_YOU --out PRIVATE_NEW_DIRECTORY
No GLB export and no source save. A movie is review evidence, not acceptance.
"""
import argparse
import pathlib
import sys
import bpy
from mathutils import Vector

p = argparse.ArgumentParser()
p.add_argument('--blend',required=True)
p.add_argument('--action',required=True)
p.add_argument('--out',required=True)
p.add_argument('--stem',default='THANK_YOU')
args = p.parse_args(sys.argv[sys.argv.index('--')+1:])
out = pathlib.Path(args.out).resolve()
assert not out.exists(), 'Use a new derivative directory'
bpy.ops.wm.open_mainfile(filepath=str(pathlib.Path(args.blend).resolve()),load_ui=False)
armatures = [o for o in bpy.context.scene.objects if o.type=='ARMATURE']
assert len(armatures)==1
arm = armatures[0]
action = bpy.data.actions.get(args.action)
assert action is not None
arm.animation_data_create()
for track in arm.animation_data.nla_tracks:
    track.mute=True
arm.animation_data.action=action
scene=bpy.context.scene
scene.frame_start=int(action.frame_range[0])
scene.frame_end=int(action.frame_range[1])
scene.render.fps=60
scene.render.fps_base=1
scene.render.engine='BLENDER_WORKBENCH'
scene.render.resolution_x=480
scene.render.resolution_y=480
scene.render.resolution_percentage=100
scene.display.shading.color_type='MATERIAL'
scene.display.shading.light='STUDIO'
scene.display.shading.show_shadows=True
cam=scene.camera
assert cam is not None
cam.data.type='ORTHO'
cam.data.ortho_scale=1.10
cam.location=(0,-4,1.18)
cam.rotation_euler=(Vector((0,0,1.18))-cam.location).to_track_quat('-Z','Y').to_euler()
scene.frame_set(scene.frame_start)
out.mkdir(parents=True)
bpy.data.texts.new('SURVEY_REVIEW_STATUS').write('UNAPPROVED engineering review: '+args.action+'\n60 FPS review movie is not human visual approval.\nSource mapping/reviewer confirmation and contact review remain pending.\nNo Android qualification, no FSL approval, listen_ready=false.\n')
bpy.ops.wm.save_as_mainfile(filepath=str(out/(args.stem+'_review_only.blend')))
scene.render.image_settings.file_format='FFMPEG'
scene.render.ffmpeg.format='MPEG4'
scene.render.ffmpeg.codec='H264'
scene.render.ffmpeg.constant_rate_factor='MEDIUM'
scene.render.filepath=str(out/(args.stem+'_candidate_60fps.mp4'))
bpy.ops.render.render(animation=True)
print('REVIEW_MOVIE_COMPLETE',flush=True)
