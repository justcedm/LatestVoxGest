"""Compare source and rejected candidate rest matrices without saving either.
Blender --python compare_rest.py -- SOURCE_BLEND CANDIDATE_BLEND OUTPUT_JSON
"""
import bpy, sys, pathlib, json, math
args=sys.argv[sys.argv.index('--')+1:] if '--' in sys.argv else []
if len(args)!=3:raise SystemExit('SOURCE_BLEND CANDIDATE_BLEND OUTPUT_JSON required')
source,candidate,out=map(pathlib.Path,args)
if any(p.resolve().drive.upper()!='C:' for p in [source,candidate,out]):raise SystemExit('C: only')
if out.suffix.lower()!='.json' or out.resolve() in [source.resolve(),candidate.resolve()]:raise SystemExit('Separate JSON output required')
def read(path):
    bpy.ops.wm.open_mainfile(filepath=str(path.resolve()),use_scripts=False)
    arm=bpy.data.objects['Waitress RIG']
    return {b.name:(b.matrix_local.copy(),b.parent.name if b.parent else None) for b in arm.data.bones},arm
s,_=read(source);c,arm=read(candidate)
restmax=max(max(abs(s[n][0][i][j]-m[i][j]) for i in range(4) for j in range(4)) for n,(m,_) in c.items())
changed=[{'bone':n,'source':s[n][1],'candidate':p} for n,(_,p) in c.items() if p!=s[n][1]]
report={'source_bones':len(s),'candidate_bones':len(c),'max_retained_rest_matrix_difference':restmax,'changed_parents':changed,'neutral_nonarm_angles_degrees':{}}
arm.animation_data.action=bpy.data.actions['FSL_THANK_YOU__EXP_C5'];bpy.context.scene.frame_set(1)
for n in ['DEF-shoulder.L','DEF-shoulder.R','DEF-pelvis.L','DEF-pelvis.R','DEF-breast.L','DEF-breast.R']:
    q=arm.pose.bones[n].rotation_quaternion
    report['neutral_nonarm_angles_degrees'][n]=math.degrees(2*math.acos(min(1,abs(q.w))))
out.write_text(json.dumps(report,indent=2));print(json.dumps(report),flush=True)
