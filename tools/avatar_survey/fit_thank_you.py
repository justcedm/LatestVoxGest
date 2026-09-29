"""Experimental THANK_YOU-only bounded lip-relative two-bone IK.
No GLB export. Private NPZ contains fitted poses and MUST NOT enter Git.
Depth is an explicit clearance hypothesis, never inferred from source landmark Z.
"""
import argparse,json,pathlib
import numpy as np
from scipy.spatial.transform import Rotation
from scipy.ndimage import gaussian_filter1d
from geometry_audit import GLB,matrices

p=argparse.ArgumentParser()
for key in ('glb','raw','out'):p.add_argument('--'+key,required=True)
a=p.parse_args();out=pathlib.Path(a.out).resolve();assert not out.exists();out.mkdir(parents=True)
g=GLB(a.glb);anim=next(x for x in g.j['animations'] if x['name']=='THANK_YOU')
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
w=np.minimum(np.clip((frames-128)/12,0,1),np.clip((180-frames)/14,0,1));w=w*w*(3-2*w)
pose=raw[:,:99].reshape(count,33,3);source_lips=pose[:,[9,10],:2].mean(axis=1)
source_lipwidth=np.abs(pose[:,9,0]-pose[:,10,0])*640
scale=np.linalg.norm(original[152,ix['SideUpLips_L'],:3,3]-original[152,ix['SideUpLips_R'],:3,3])/np.median(source_lipwidth[139:166])
assert .0005<scale<.01
metrics=[]
for side,block in [('L',99),('R',162)]:
    hand=raw[:,block:block+63].reshape(count,21,3)
    # XY source projection only. Landmark depth is intentionally unused.
    relative=(hand[:,12,:2]-source_lips)*[640,-360]*scale
    relative=gaussian_filter1d(relative,1,axis=0,mode='nearest')
    tip=ix[f'RT_DEF-f_middle.03.{side}_10'];wrist=ix[f'DEF-hand.{side}']
    upper=ix[f'DEF-upper_arm.{side}'];upperhalf=ix[f'DEF-upper_arm.{side}.001']
    elbow=ix[f'DEF-forearm.{side}'];lowerhalf=ix[f'DEF-forearm.{side}.001']
    handnodes=descendants(wrist)
    lengths=[];residual=[];max_delta=0
    for k in range(count):
        if w[k]==0:continue
        lip=original[k,ix['CenterUpLips'],:3,3]
        desired=original[k,tip,:3,3].copy()
        desired[:2]=lip[:2]+relative[k]
        # 15 mm bone-tip offset is an engineering clearance hypothesis; mesh QA can reject it.
        desired[2]=lip[2]+.015
        delta=(desired-original[k,tip,:3,3])*w[k]
        assert np.linalg.norm(delta)<.30,'Outside bounded local correction; requires separate review'
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
report=dict(source_glb_sha256=g.sha,action='THANK_YOU',draft='THANK_YOU__LIPFIT_REVIEW_V2',frames=count,fps=60,source_mapping='PENDING_REVIEW',changed_frames=[129,179],full_target_frames=[140,166],depth_policy='15mm bone-tip clearance hypothesis; no raw landmark depth',source_image_metres_per_pixel=float(scale),kinematic_checks=metrics,neutral_unchanged=bool(np.array_equal(original[[0,-1]],corrected[[0,-1]])),shoulder_root_unchanged=True,finger_articulation_and_wrist_orientation='PRESERVED',mechanical='PENDING_SURFACE_CLEARANCE_AND_TEMPORAL_QA',visual='PENDING',export='BLOCKED',listen_ready=False)
(out/'fit_summary.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf8');print(json.dumps(report,indent=2))
