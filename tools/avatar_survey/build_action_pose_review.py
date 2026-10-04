"""Blender 3.2: build NEW Action/derivative from private world-matrix fit.
No GLB export. Verify imported-bone basis offsets and every original Action hash.
"""
import argparse,sys,pathlib,json,hashlib
import bpy,numpy as np
from mathutils import Matrix,Vector
from mathutils.bvhtree import BVHTree
p=argparse.ArgumentParser()
for k in ('blend','fit','out'):p.add_argument('--'+k,required=True)
p.add_argument('--action',required=True)
p.add_argument('--base-action',required=True)
a=p.parse_args(sys.argv[sys.argv.index('--')+1:]);out=pathlib.Path(a.out).resolve();assert not out.exists();out.mkdir(parents=True)
bpy.ops.wm.open_mainfile(filepath=str(pathlib.Path(a.blend).resolve()),load_ui=False)
def digest(action):
    rows=[(c.data_path,c.array_index,c.extrapolation,[(tuple(k.co),k.interpolation,k.handle_left_type,k.handle_right_type,tuple(k.handle_left),tuple(k.handle_right)) for k in c.keyframe_points]) for c in action.fcurves]
    return hashlib.sha256(repr(rows).encode()).hexdigest()
before={act.name:digest(act) for act in bpy.data.actions}
arms=[o for o in bpy.context.scene.objects if o.type=='ARMATURE'];assert len(arms)==1;arm=arms[0];scene=bpy.context.scene
for track in arm.animation_data.nla_tracks:track.mute=True
arm.animation_data.action=bpy.data.actions[a.base_action];scene.frame_set(1)
data=np.load(a.fit);names=list(data['names']);original=data['original'];target=data['corrected'];ix={n:i for i,n in enumerate(names)}
G=Matrix(((1,0,0,0),(0,0,-1,0),(0,1,0,0),(0,0,0,1)))
world=arm.matrix_world.copy();inv=world.inverted();offset={}
for b in arm.pose.bones:
    if b.name in ix:offset[b.name]=(G@Matrix(original[0,ix[b.name]].tolist())).inverted()@world@b.matrix
baseline_error=0
for f in [1,100,140,180,len(target)]:
    scene.frame_set(f)
    for b in arm.pose.bones:
        if b.name in offset:
            predicted=G@Matrix(original[f-1,ix[b.name]].tolist())@offset[b.name]
            baseline_error=max(baseline_error,max(abs(x-y) for row1,row2 in zip(predicted,world@b.matrix) for x,y in zip(row1,row2)))
assert baseline_error<2e-5,baseline_error
assert a.action not in bpy.data.actions
new=bpy.data.actions[a.base_action].copy();new.name=a.action;new.use_fake_user=True;arm.animation_data.action=new
changed=[b for b in arm.pose.bones if b.name in ix and np.max(np.abs(target[:,ix[b.name]]-original[:,ix[b.name]]))>1e-10]
mesh_visibility={o.name:o.hide_viewport for o in scene.objects if o.type=='MESH'}
for name in mesh_visibility:bpy.data.objects[name].hide_viewport=True
for f in range(1,len(target)+1):
    scene.frame_set(f)
    desired={b.name:inv@G@Matrix(target[f-1,ix[b.name]].tolist())@offset[b.name] for b in arm.pose.bones if b.name in offset}
    for b in changed:
        kwargs={}
        if b.parent:kwargs=dict(parent_matrix=desired.get(b.parent.name,b.parent.matrix),parent_matrix_local=b.parent.bone.matrix_local)
        basis=b.bone.convert_local_to_pose(desired[b.name],b.bone.matrix_local,invert=True,**kwargs)
        b.matrix_basis=basis
        for path in ('location','rotation_quaternion','scale'):b.keyframe_insert(data_path=path,frame=f,group=b.name)
for curve in new.fcurves:
    for key in curve.keyframe_points:key.interpolation='LINEAR'
assert all(digest(bpy.data.actions[name])==h for name,h in before.items())
for name,value in mesh_visibility.items():bpy.data.objects[name].hide_viewport=value
scene.frame_start=1;scene.frame_end=len(target);scene.render.fps=60;scene.frame_set(160)
scene.render.engine='BLENDER_WORKBENCH';scene.render.resolution_x=640;scene.render.resolution_y=640;scene.render.resolution_percentage=100
cam=scene.camera;cam.data.type='ORTHO';cam.data.ortho_scale=1.05
def aim(pos):cam.location=pos;cam.rotation_euler=(Vector((0,0,1.18))-cam.location).to_track_quat('-Z','Y').to_euler()
aim((0,-4,1.18))
bpy.data.texts.new('LIPFIT_REVIEW_STATUS').write('EXPERIMENTAL Action-local IK correction. Source mapping PENDING_REVIEW.\nNo GLB export. Face clearance and full-speed human acceptance required.\nAll original Actions preserved. No global transforms changed.\n')
bpy.ops.wm.save_as_mainfile(filepath=str(out/(a.action+'.blend')))
for frame in [100,120,140,160,180,200,220]:
    scene.frame_set(frame)
    for view,pos in [('front',(0,-4,1.18)),('side',(4,0,1.18)),('perspective',(2,-4,1.4))]:
        aim(pos);scene.render.image_settings.file_format='PNG';scene.render.filepath=str(out/f'{view}_{frame}.png');bpy.ops.render.render(write_still=True)
report=dict(baseline_world_basis_error=baseline_error,changed_bones=len(changed),original_action_hashes=before,original_actions_unchanged=all(digest(bpy.data.actions[n])==h for n,h in before.items()),new_action=new.name,visual='PENDING',mechanical='PENDING',export='BLOCKED',listen_ready=False)
(out/'blender_build_report.json').write_text(json.dumps(report,indent=2),encoding='utf8')
print('LIPFIT_BLEND_COMPLETE',flush=True)
