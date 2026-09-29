"""V4 engineering-only preparation clearance through coherent two-bone IK.
Reads V3 private poses; writes NEW NPZ, never a GLB. Source approval stays pending.
"""
import argparse,json,pathlib
import numpy as np
from scipy.spatial.transform import Rotation,Slerp
from audit_runtime import GLB
p=argparse.ArgumentParser()
for k in ('glb','fit','out'):p.add_argument('--'+k,required=True)
p.add_argument('--forward-m',type=float,default=.02)
p.add_argument('--hand-spacing-m',type=float,default=0)
a=p.parse_args();out=pathlib.Path(a.out).resolve();assert not out.exists();out.mkdir(parents=True)
g=GLB(a.glb);data=np.load(a.fit);original=data['original'];before=data['corrected'];target=before.copy();names=list(data['names']);ix={str(n):i for i,n in enumerate(names)}
parents={c:i for i,n in enumerate(g.j['nodes']) for c in n.get('children',[])}
assert 0<=a.forward_m<=.05
assert 0<=a.hand_spacing_m<=.05
def align(u,v):
    u=u/np.linalg.norm(u);v=v/np.linalg.norm(v);axis=np.cross(u,v);sn=np.linalg.norm(axis)
    if sn<1e-10:return np.eye(3)
    return Rotation.from_rotvec(axis/sn*np.arctan2(sn,np.dot(u,v))).as_matrix()
if a.hand_spacing_m or a.forward_m:
    for k in range(len(target)):
        f=k+1;prep=min(np.clip((f-100)/9,0,1),np.clip((128-f)/10,0,1));entry=min(np.clip((f-1)/8,0,1),np.clip((25-f)/12,0,1));w=max(prep,entry);w=w*w*(3-2*w)
        if w==0:continue
        for side,sign in [('L',1),('R',-1)]:
            ids=[ix[f'DEF-{name}.{side}'] for name in ['upper_arm','forearm','hand']];S,E,W=[before[k,n,:3,3] for n in ids]
            delta=np.array([sign*a.hand_spacing_m*w,0,a.forward_m*w]);T=W+delta;L1=np.linalg.norm(E-S);L2=np.linalg.norm(W-E);d=np.linalg.norm(T-S);assert abs(L1-L2)<d<L1+L2
            axis=(T-S)/d;along=(L1*L1-L2*L2+d*d)/(2*d);bend=E-S-axis*np.dot(E-S,axis);bend/=np.linalg.norm(bend);NE=S+axis*along+bend*np.sqrt(L1*L1-along*along)
            for group,rot,P,Q in [('upper_arm',align(E-S,NE-S),S,S),('forearm',align(W-E,T-NE),E,NE)]:
                for suffix in ['', '.001']:
                    n=ix[f'DEF-{group}.{side}{suffix}'];target[k,n,:3,:3]=rot@before[k,n,:3,:3];target[k,n,:3,3]=Q+rot@(before[k,n,:3,3]-P)
            def shift(n):
                target[k,n,:3,3]=before[k,n,:3,3]+delta
                for child in g.j['nodes'][n].get('children',[]):shift(child)
            shift(ids[2])

assert np.array_equal(original[[0,-1]],target[[0,-1]])
np.savez_compressed(out/'private_pose_fit.npz',original=original,corrected=target,names=np.array(names))
(out/'refinement_summary.json').write_text(json.dumps(dict(draft='THANK_YOU__BODY_CLEARANCE_REVIEW_V4',outward_m=a.hand_spacing_m,forward_m=a.forward_m,active_frames=[[2,24],[101,127]],neutral_exact=True,source='PENDING_REVIEW',export='BLOCKED'),indent=2)+'\n',encoding='utf8')
