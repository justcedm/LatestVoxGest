"""In-memory causal ablation of rejected C5; never saves candidate changes.
Invoke after loading C5 with --disable-autoexec; -- OUTPUT_JSON.
"""
import bpy, numpy as np, pathlib, sys, json
from mathutils import Matrix
args=sys.argv[sys.argv.index('--')+1:] if '--' in sys.argv else []
if len(args)!=1:raise SystemExit('Explicit JSON output required')
output=pathlib.Path(args[0]).resolve()
if output.drive.upper()!='C:' or output.suffix.lower()!='.json' or output==pathlib.Path(bpy.data.filepath).resolve():raise SystemExit('Separate C: JSON output required')
arm=bpy.data.objects['Waitress RIG']; shirt=bpy.data.objects['tshirt']; scene=bpy.context.scene
act=next(a for a in bpy.data.actions if a.name=='FSL_THANK_YOU__EXP_C5')
arm.animation_data.action=act
def sample(frame):
    scene.frame_set(frame); bpy.context.view_layer.update()
    ev=shirt.evaluated_get(bpy.context.evaluated_depsgraph_get()); mesh=ev.to_mesh()
    pts=np.array([list(ev.matrix_world@v.co) for v in mesh.vertices]);ev.to_mesh_clear();return pts
def measure():
    frames=[1,100,125,145,160,180,200,220,243]; points=[sample(f) for f in frames]
    mask=points[0][:,2]<1.02704
    return {'frames':frames,'lower_vertices':int(mask.sum()),'lower_max_displacement_m':max(float(np.linalg.norm((p-points[0])[mask],axis=1).max()) for p in points),
            'all_max_displacement_m':max(float(np.linalg.norm(p-points[0],axis=1).max()) for p in points)}
report={'baseline':measure(),'ablation_description':'Mute only non-arm quaternion curves and set those pose matrices to identity, without saving.'}
affected=set()
for fc in act.fcurves:
    if not fc.data_path.startswith('pose.bones['):continue
    name=fc.data_path.split('"')[1]
    if name.startswith(('DEF-upper_arm','DEF-forearm','DEF-hand','DEF-f_','DEF-thumb')):continue
    fc.mute=True;affected.add(name)
for name in affected:arm.pose.bones[name].matrix_basis=Matrix.Identity(4)
report['non_arm_identity_ablation']=measure()
report['affected_bones']=sorted(affected)
output.write_text(json.dumps(report,indent=2));print(json.dumps(report),flush=True)
