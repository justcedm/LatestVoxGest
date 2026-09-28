"""Render each Action at 60 FPS from one immutable review blend.
No animation modifications or GLB exports. Output directory must be new.
blender -b --disable-autoexec --python render_all_reviews.py -- --blend SOURCE --out NEW
"""
import argparse,json,pathlib,sys,bpy
from mathutils import Vector
p=argparse.ArgumentParser();p.add_argument('--blend',required=True);p.add_argument('--out',required=True)
a=p.parse_args(sys.argv[sys.argv.index('--')+1:]);out=pathlib.Path(a.out).resolve();assert not out.exists()
bpy.ops.wm.open_mainfile(filepath=str(pathlib.Path(a.blend).resolve()),load_ui=False)
arms=[o for o in bpy.context.scene.objects if o.type=='ARMATURE'];assert len(arms)==1
arm=arms[0];scene=bpy.context.scene
for track in arm.animation_data.nla_tracks:track.mute=True
scene.render.fps=60;scene.render.fps_base=1
scene.render.engine='BLENDER_WORKBENCH';scene.render.resolution_x=480;scene.render.resolution_y=640;scene.render.resolution_percentage=100
scene.display.shading.color_type='MATERIAL';scene.display.shading.light='STUDIO'
cam=scene.camera;assert cam is not None
cam.data.type='ORTHO';cam.data.ortho_scale=1.85
cam.location=(0,-4,.86);cam.rotation_euler=(Vector((0,0,.86))-cam.location).to_track_quat('-Z','Y').to_euler()
scene.render.image_settings.file_format='FFMPEG';scene.render.ffmpeg.format='MPEG4';scene.render.ffmpeg.codec='H264';scene.render.ffmpeg.constant_rate_factor='MEDIUM'
out.mkdir(parents=True);rows=[]
for action in sorted(bpy.data.actions,key=lambda x:x.name):
    arm.animation_data.action=action;scene.frame_start=int(action.frame_range[0]);scene.frame_end=int(action.frame_range[1]);scene.frame_set(scene.frame_start)
    scene.render.filepath=str(out/(action.name+'.mp4'))
    bpy.ops.render.render(animation=True)
    rows.append(dict(action=action.name,start=scene.frame_start,end=scene.frame_end,fps=60,visual_approval='PENDING',listen_ready=False))
    (out/'render_manifest.json').write_text(json.dumps(rows,indent=2),encoding='utf8')
    print('RENDER_COMPLETE',action.name,flush=True)
print('ALL_ACTIONS_COMPLETE',flush=True)
