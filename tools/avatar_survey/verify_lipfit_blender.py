"""Read-only evaluated Blender parity for the private lip-fit derivative."""
import argparse,sys,pathlib,json,bpy,numpy as np
from mathutils import Matrix
p=argparse.ArgumentParser()
for k in ('blend','fit','out'):p.add_argument('--'+k,required=True)
p.add_argument('--action',default='THANK_YOU__LIPFIT_REVIEW_V2')
a=p.parse_args(sys.argv[sys.argv.index('--')+1:]);out=pathlib.Path(a.out).resolve();assert not out.exists()
bpy.ops.wm.open_mainfile(filepath=str(pathlib.Path(a.blend).resolve()),load_ui=False)
arm=next(o for o in bpy.context.scene.objects if o.type=='ARMATURE');scene=bpy.context.scene
for o in scene.objects:
    if o.type in {'MESH','CURVE'}:o.hide_viewport=True
for t in arm.animation_data.nla_tracks:t.mute=True
data=np.load(a.fit);original=data['original'];target=data['corrected'];ix={str(n):i for i,n in enumerate(data['names'])}
G=Matrix(((1,0,0,0),(0,0,-1,0),(0,1,0,0),(0,0,0,1)));world=arm.matrix_world.copy()
arm.animation_data.action=bpy.data.actions['THANK_YOU'];scene.frame_set(1)
offset={b.name:(G@Matrix(original[0,ix[b.name]].tolist())).inverted()@world@b.matrix for b in arm.pose.bones if b.name in ix}
arm.animation_data.action=bpy.data.actions[a.action];maximum=0
for f in range(1,244):
    scene.frame_set(f)
    for b in arm.pose.bones:
        if b.name in offset:
            desired=G@Matrix(target[f-1,ix[b.name]].tolist())@offset[b.name]
            maximum=max(maximum,max(abs(x-y) for r,s in zip(desired,world@b.matrix) for x,y in zip(r,s)))
    if f%60==0:print('VERIFIED_FRAME',f,flush=True)
result=dict(frames=243,bones=len(offset),max_evaluated_world_matrix_error=maximum,parity_pass=maximum<2e-5,source_file_modified=False,scope='Evaluated pose parity only; not visual/FSL acceptance')
out.write_text(json.dumps(result,indent=2)+'\n',encoding='utf8');print(json.dumps(result),flush=True)
assert result['parity_pass']
