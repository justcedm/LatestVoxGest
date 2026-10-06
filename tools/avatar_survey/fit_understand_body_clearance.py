"""UNDERSTAND V10 engineering-only body clearance through coherent two-bone IK.
Reads V9 private poses; writes NEW NPZ, never a GLB. Source approval stays pending.
"""
import argparse,json,pathlib
import numpy as np
from scipy.spatial.transform import Rotation,Slerp
from audit_runtime import GLB
p=argparse.ArgumentParser()
for k in ('glb','fit','out'):p.add_argument('--'+k,required=True)
p.add_argument('--forward-m',type=float,default=.02)
p.add_argument('--hand-spacing-m',type=float,default=0)
p.add_argument('--left-spacing-m',type=float,default=None)
p.add_argument('--left-forward-m',type=float,default=None)
p.add_argument('--right-neutral-clearance',action='store_true')
p.add_argument('--prep-lift-m',type=float,default=0)
a=p.parse_args();out=pathlib.Path(a.out).resolve();assert not out.exists();out.mkdir(parents=True)
g=GLB(a.glb);data=np.load(a.fit);original=data['original'];before=data['corrected'];target=before.copy();names=list(data['names']);ix={str(n):i for i,n in enumerate(names)}
parents={c:i for i,n in enumerate(g.j['nodes']) for c in n.get('children',[])}
assert 0<=a.forward_m<=.05
assert 0<=a.hand_spacing_m<=.05
assert 0<=a.prep_lift_m<=.01
assert a.left_spacing_m is None or 0<=a.left_spacing_m<=.05
assert a.left_forward_m is None or 0<=a.left_forward_m<=.05
def align(u,v):
    u=u/np.linalg.norm(u);v=v/np.linalg.norm(v);axis=np.cross(u,v);sn=np.linalg.norm(axis)
    if sn<1e-10:return np.eye(3)
    return Rotation.from_rotvec(axis/sn*np.arctan2(sn,np.dot(u,v))).as_matrix()
if a.hand_spacing_m or a.forward_m:
    for k in range(len(target)):
        f=k+1
        for side,sign in [('L',1),('R',-1)]:
            neutral_windows=[(1,9,14,25),(221,230,235,245)]
            windows=[(1,9,14,25),(221,233,237,245)] if side=='L' else [(60,75,83,95)]+(neutral_windows if a.right_neutral_clearance else [])
            w=max(min(np.clip((f-start)/(up-start),0,1),np.clip((end-f)/(end-down),0,1)) for start,up,down,end in windows);w=w*w*(3-2*w)
            if w==0:continue
            ids=[ix[f'DEF-{name}.{side}'] for name in ['upper_arm','forearm','hand']];S,E,W=[before[k,n,:3,3] for n in ids]
            spacing=a.left_spacing_m if side=='L' and a.left_spacing_m is not None else a.hand_spacing_m
            forward=a.left_forward_m if side=='L' and a.left_forward_m is not None else a.forward_m
            if side=='R' and a.right_neutral_clearance and (f<25 or f>221):spacing,forward=.02,.015
            lift=a.prep_lift_m if side=='R' and 60<f<95 else 0
            delta=np.array([sign*spacing*w,lift*w,forward*w]);T=W+delta;L1=np.linalg.norm(E-S);L2=np.linalg.norm(W-E);d=np.linalg.norm(T-S);assert abs(L1-L2)<d<L1+L2,(f,side,d,L1+L2)
            axis=(T-S)/d;along=(L1*L1-L2*L2+d*d)/(2*d);bend=E-S-axis*np.dot(E-S,axis);bend/=np.linalg.norm(bend);NE=S+axis*along+bend*np.sqrt(L1*L1-along*along)
            for group,rot,P,Q in [('upper_arm',align(E-S,NE-S),S,S),('forearm',align(W-E,T-NE),E,NE)]:
                for suffix in ['', '.001']:
                    n=ix[f'DEF-{group}.{side}{suffix}'];target[k,n,:3,:3]=rot@before[k,n,:3,:3];target[k,n,:3,3]=Q+rot@(before[k,n,:3,3]-P)
            def shift(n):
                target[k,n,:3,3]=before[k,n,:3,3]+delta
                for child in g.j['nodes'][n].get('children',[]):shift(child)
            shift(ids[2])

assert np.array_equal(original[[0,-1]],target[[0,-1]])
assert np.array_equal(before[95:220],target[95:220]), 'Signing stroke must remain exact'
np.savez_compressed(out/'private_pose_fit.npz',original=original,corrected=target,names=np.array(names))
(out/'refinement_summary.json').write_text(json.dumps(dict(draft='UNDERSTAND__CLOTHING_CLEARANCE_REVIEW_V11' if a.right_neutral_clearance else 'UNDERSTAND__BODY_CLEARANCE_REVIEW_V10',right_outward_m=a.hand_spacing_m,right_forward_m=a.forward_m,left_outward_m=a.left_spacing_m if a.left_spacing_m is not None else a.hand_spacing_m,left_forward_m=a.left_forward_m if a.left_forward_m is not None else a.forward_m,active_frames={'L':[[2,24],[222,244]],'R':[[2,24],[61,94],[222,244]] if a.right_neutral_clearance else [[61,94]]},signing_frames_96_220_exact=True,right_neutral_offsets_m=[.02,.015] if a.right_neutral_clearance else None,neutral_exact=True,preparation_lift_m=a.prep_lift_m,source='PENDING_REVIEW',export='BLOCKED'),indent=2)+'\n',encoding='utf8')
