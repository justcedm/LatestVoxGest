"""Render isolated contact closeups without saving or modifying the source blend."""
import bpy,sys,argparse,pathlib,json
from mathutils import Vector
p=argparse.ArgumentParser()
for k in ('blend','action','out'):p.add_argument('--'+k,required=True)
a=p.parse_args(sys.argv[sys.argv.index('--')+1:]);out=pathlib.Path(a.out).resolve();assert not out.exists();out.mkdir(parents=True)
bpy.ops.wm.open_mainfile(filepath=str(pathlib.Path(a.blend).resolve()),load_ui=False)
scene=bpy.context.scene;arm=next(o for o in scene.objects if o.type=='ARMATURE');arm.animation_data.action=bpy.data.actions[a.action]
for t in arm.animation_data.nla_tracks:t.mute=True
scene.render.engine='BLENDER_WORKBENCH';scene.render.resolution_x=600;scene.render.resolution_y=600;scene.render.resolution_percentage=100;scene.render.image_settings.file_format='PNG'
cam=scene.camera;cam.data.type='ORTHO';cam.data.ortho_scale=.30
for frame,side in [(11,'L'),(79,'R'),(125,'R'),(195,'R'),(220,'R'),(235,'L')]:
 scene.frame_set(frame);center=arm.matrix_world@arm.pose.bones['DEF-hand.'+side].matrix.translation
 center+=Vector((0,0,-.025)) if frame in (11,79,220,235) else Vector((0,0,.04))
 for label,offset in [('front',(0,-3,0)),('side',(3 if side=='R' else -3,0,0)),('oblique',(2,-3,1))]:
  cam.location=center+Vector(offset);cam.rotation_euler=(center-cam.location).to_track_quat('-Z','Y').to_euler();scene.render.filepath=str(out/f'{frame}_{label}.png');bpy.ops.render.render(write_still=True)
(out/'status.json').write_text(json.dumps({'source_modified':False,'action':a.action,'visual':'PENDING','listen_ready':False}))
