"""V3 engineering-only distal separation and bounded neutral recovery.
Reads V2 private poses; writes NEW NPZ, never a GLB. Source approval stays pending.
"""
import argparse,json,pathlib
import numpy as np
from scipy.spatial.transform import Rotation,Slerp
from audit_runtime import GLB
p=argparse.ArgumentParser()
for k in ('glb','fit','out'):p.add_argument('--'+k,required=True)
p.add_argument('--spread-degrees',type=float,default=2)
p.add_argument('--hand-spacing-m',type=float,default=0)
a=p.parse_args();out=pathlib.Path(a.out).resolve();assert not out.exists();out.mkdir(parents=True)
g=GLB(a.glb);data=np.load(a.fit);original=data['original'];before=data['corrected'];target=before.copy();names=list(data['names']);ix={str(n):i for i,n in enumerate(names)}
parents={c:i for i,n in enumerate(g.j['nodes']) for c in n.get('children',[])}
assert 0<=a.spread_degrees<=6,'Do not expand bounded trial without review'
assert 0<=a.hand_spacing_m<=.012
def align(u,v):
    u=u/np.linalg.norm(u);v=v/np.linalg.norm(v);axis=np.cross(u,v);sn=np.linalg.norm(axis)
    if sn<1e-10:return np.eye(3)
    return Rotation.from_rotvec(axis/sn*np.arctan2(sn,np.dot(u,v))).as_matrix()
if a.hand_spacing_m:
    for k in range(len(target)):
        f=k+1;w=min(np.clip((f-128)/12,0,1),np.clip((180-f)/14,0,1));w=w*w*(3-2*w)
        if w==0:continue
        for side,sign in [('L',1),('R',-1)]:
            ids=[ix[f'DEF-{name}.{side}'] for name in ['upper_arm','forearm','hand']];S,E,W=[before[k,n,:3,3] for n in ids]
            delta=np.array([sign*a.hand_spacing_m*w,0,0]);T=W+delta;L1=np.linalg.norm(E-S);L2=np.linalg.norm(W-E);d=np.linalg.norm(T-S);assert abs(L1-L2)<d<L1+L2
            axis=(T-S)/d;along=(L1*L1-L2*L2+d*d)/(2*d);bend=E-S-axis*np.dot(E-S,axis);bend/=np.linalg.norm(bend);NE=S+axis*along+bend*np.sqrt(L1*L1-along*along)
            for group,rot,P,Q in [('upper_arm',align(E-S,NE-S),S,S),('forearm',align(W-E,T-NE),E,NE)]:
                for suffix in ['', '.001']:
                    n=ix[f'DEF-{group}.{side}{suffix}'];target[k,n,:3,:3]=rot@before[k,n,:3,:3];target[k,n,:3,3]=Q+rot@(before[k,n,:3,3]-P)
            def shift(n):
                target[k,n,:3,3]=before[k,n,:3,3]+delta
                for child in g.j['nodes'][n].get('children',[]):shift(child)
            shift(ids[2])
    before=target.copy()
for k in range(len(target)):
    f=k+1;w=min(np.clip((f-108)/10,0,1),np.clip((218-f)/20,0,1));w=w*w*(3-2*w)
    if w==0:continue
    for side in ['L','R']:
        mid=target[k,ix[f'RT_DEF-f_middle.01.{side}_00'],:3,3].copy()
        for finger,mult in [('f_index',1),('f_ring',1),('f_pinky',2)]:
            base=before[k,ix[f'RT_DEF-{finger}.01.{side}_00'],:3,3];tip=before[k,ix[f'RT_DEF-{finger}.03.{side}_10'],:3,3]
            axis=np.cross(tip-base,base-mid);assert np.linalg.norm(axis)>1e-8;axis/=np.linalg.norm(axis)
            rot=Rotation.from_rotvec(axis*np.radians(a.spread_degrees*mult*w)).as_matrix()
            for node,name in enumerate(names):
                if f'DEF-{finger}.' in name and f'.{side}_' in name:
                    target[k,node,:3,:3]=rot@before[k,node,:3,:3]
                    target[k,node,:3,3]=base+rot@(before[k,node,:3,3]-base)
def subtree(root):
    result=[root]
    for c in g.j['nodes'][root].get('children',[]):result+=subtree(c)
    return result
arms=subtree(ix['DEF-upper_arm.L'])+subtree(ix['DEF-upper_arm.R'])
start=target[199].copy();end=original[-1].copy()
def trs(m):
    scale=np.linalg.norm(m[:3,:3],axis=0);return m[:3,3],Rotation.from_matrix(m[:3,:3]/scale),scale
local0={n:np.linalg.inv(start[parents[n]])@start[n] for n in arms};local1={n:np.linalg.inv(end[parents[n]])@end[n] for n in arms}
for k in range(200,len(target)):
    t=min(1.,(k-199)/18);t=t*t*(3-2*t)
    if t==1:
        target[k,arms]=end[arms];continue
    for n in arms:
        p0,r0,s0=trs(local0[n]);p1,r1,s1=trs(local1[n]);m=np.eye(4)
        m[:3,:3]=Slerp([0,1],Rotation.from_quat([r0.as_quat(),r1.as_quat()]))([t]).as_matrix()[0]*((1-t)*s0+t*s1)[None,:]
        m[:3,3]=(1-t)*p0+t*p1;target[k,n]=target[k,parents[n]]@m
assert np.array_equal(original[[0,-1]],target[[0,-1]])
np.savez_compressed(out/'private_pose_fit.npz',original=original,corrected=target,names=np.array(names))
report=dict(action='THANK_YOU',draft='THANK_YOU__LIPFIT_REVIEW_V3',spread_degrees=a.spread_degrees,hand_spacing_each_m=a.hand_spacing_m,pinky_multiplier=2,spread_active_frames=[109,217],recovery_frames=[201,218],recovery_convention='18-frame local TRS/quaternion bridge to unchanged Core3 neutral; no global speed change',source_basis='Open source handshape and visible source recovery at 203-211; engineering draft pending full comparison',neutral_exact=True,source='PENDING_REVIEW',mechanical='PENDING_INTERSECTION_RECHECK',visual='PENDING',export='BLOCKED',listen_ready=False)
(out/'refinement_summary.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf8');print(json.dumps(report))
