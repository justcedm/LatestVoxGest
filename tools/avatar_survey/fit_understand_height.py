"""Experimental UNDERSTAND-only eye-relative height correction through two-bone IK.
No GLB export. Private NPZ contains fitted poses and MUST NOT enter Git.
Lateral placement retained; optional bounded forward clearance; no source landmark Z used.
"""
import argparse,json,pathlib
import numpy as np
from scipy.spatial.transform import Rotation
from scipy.ndimage import gaussian_filter1d
from geometry_audit import GLB,matrices

p=argparse.ArgumentParser()
for key in ('glb','raw','out'):p.add_argument('--'+key,required=True)
p.add_argument('--forward-m',type=float,default=0)
a=p.parse_args();assert 0<=a.forward_m<=.10;out=pathlib.Path(a.out).resolve();assert not out.exists();out.mkdir(parents=True)
g=GLB(a.glb);anim=next(x for x in g.j['animations'] if x['name']=='UNDERSTAND')
count=len(g.acc(anim['samplers'][0]['input']));raw=np.load(a.raw);assert raw.shape==(count,225)
original=np.array([matrices(g,anim,k) for k in range(count)])
corrected=original.copy();names=[n.get('name','') for n in g.j['nodes']];ix={n:i for i,n in enumerate(names)}
parents={c:i for i,n in enumerate(g.j['nodes']) for c in n.get('children',[])}
def descendants(i):
    found=[i]
    for child in g.j['nodes'][i].get('children',[]):found+=descendants(child)
    return found
def swing(u,v):
    u=u/np.linalg.norm(u);v=v/np.linalg.norm(v);cross=np.cross(u,v);length=np.linalg.norm(cross)
    if length<1e-10:
        assert np.dot(u,v)>0;return np.eye(3)
    return Rotation.from_rotvec(cross/length*np.arctan2(length,np.dot(u,v))).as_matrix()
def move(m,rot,oldpivot,newpivot):
    result=m.copy();result[:3,:3]=rot@m[:3,:3];result[:3,3]=newpivot+rot@(m[:3,3]-oldpivot);return result
frames=np.arange(1,count+1)
# Selected from image-plane contact evidence, not source depth. Preserve all entry/exit frames.
w=np.minimum(np.clip((frames-95)/25,0,1),np.clip((220-frames)/30,0,1));w=w*w*(3-2*w)
pose=raw[:,:99].reshape(count,33,3)
source_eyes=pose[:,[2,5],:2].mean(axis=1)
source_eyewidth=np.abs(pose[:,2,0]-pose[:,5,0])*640
model_eye_width=np.linalg.norm(original[159,ix['EyeBall_L'],:3,3]-original[159,ix['EyeBall_R'],:3,3])
scale=model_eye_width/np.median(source_eyewidth[119:190])
assert .0005<scale<.01
metrics=[]
for side,block in [('R',162)]:
    hand=raw[:,block:block+63].reshape(count,21,3)
    # XY source projection only. Landmark depth is intentionally unused.
    relative=(hand[:,8,:2]-source_eyes)*[640,-360]*scale
    relative=gaussian_filter1d(relative,1,axis=0,mode='nearest')
    tip=ix[f'RT_DEF-f_index.03.{side}_10'];wrist=ix[f'DEF-hand.{side}']
    upper=ix[f'DEF-upper_arm.{side}'];upperhalf=ix[f'DEF-upper_arm.{side}.001']
    elbow=ix[f'DEF-forearm.{side}'];lowerhalf=ix[f'DEF-forearm.{side}.001']
    handnodes=descendants(wrist)
    lengths=[];residual=[];max_delta=0
    for k in range(count):
        if w[k]==0:continue
        eye=original[k,[ix['EyeBall_L'],ix['EyeBall_R']],:3,3].mean(axis=0)
        desired=original[k,tip,:3,3].copy()
        desired[1]=eye[1]+relative[k,1]
        desired[2]+=a.forward_m
        delta=(desired-original[k,tip,:3,3])*w[k]
        assert np.linalg.norm(delta)<.20,'Outside bounded local correction; requires separate review'
        max_delta=max(max_delta,float(np.linalg.norm(delta)))
        S=original[k,upper,:3,3];E=original[k,elbow,:3,3];W=original[k,wrist,:3,3];T=W+delta
        l1=np.linalg.norm(E-S);l2=np.linalg.norm(W-E);d=np.linalg.norm(T-S)
        assert abs(l1-l2)+1e-5<d<l1+l2-1e-5,'Target unreachable without stretching'
        axis=(T-S)/d;along=(l1*l1-l2*l2+d*d)/(2*d)
        bend=E-S-axis*np.dot(E-S,axis);assert np.linalg.norm(bend)>1e-6
        bend/=np.linalg.norm(bend)
        newE=S+axis*along+bend*np.sqrt(max(0,l1*l1-along*along))
        ru=swing(E-S,newE-S);rl=swing(W-E,T-newE)
        for node in [upper,upperhalf]:corrected[k,node]=move(original[k,node],ru,S,S)
        for node in [elbow,lowerhalf]:corrected[k,node]=move(original[k,node],rl,E,newE)
        for node in handnodes:corrected[k,node,:3,3]=original[k,node,:3,3]+delta
        lengths += [abs(np.linalg.norm(newE-S)-l1),abs(np.linalg.norm(T-newE)-l2)]
        residual.append(float(np.linalg.norm(corrected[k,tip,:3,3]-(original[k,tip,:3,3]+delta))))
    metrics.append(dict(side=side,max_wrist_displacement_m=max_delta,max_limb_length_error_m=max(lengths),max_tip_target_error_m=max(residual)))
np.savez_compressed(out/'private_pose_fit.npz',original=original,corrected=corrected,names=np.array(names))
report=dict(source_glb_sha256=g.sha,action='UNDERSTAND',draft='UNDERSTAND__EYE_HEIGHT_REVIEW_V9' if a.forward_m else 'UNDERSTAND__EYE_HEIGHT_REVIEW_V8',frames=count,fps=60,source_mapping='PENDING_REVIEW',changed_frames=[96,219],full_target_frames=[120,190],forward_clearance_hypothesis_m=a.forward_m,depth_policy='Optional bounded forward clearance for arm bending; lateral path unchanged; source eye-relative Y; raw depth unused; source head tilt not reconstructed',source_image_metres_per_pixel=float(scale),kinematic_checks=metrics,neutral_unchanged=bool(np.array_equal(original[[0,-1]],corrected[[0,-1]])),shoulder_root_unchanged=True,finger_articulation_and_wrist_orientation='PRESERVED',mechanical='PENDING_SURFACE_CLEARANCE_AND_TEMPORAL_QA',visual='PENDING',export='BLOCKED',listen_ready=False)
(out/'fit_summary.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf8');print(json.dumps(report,indent=2))
