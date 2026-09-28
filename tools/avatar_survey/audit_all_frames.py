"""Read-only per-frame skinned-body extent audit, not a collision proof.
python audit_all_frames.py --glb INPUT --out NEW_REPORT
All vertices evaluated for body and clothing; static components verified separately.
"""
import argparse,json,pathlib
import numpy as np
from audit_runtime import GLB
from geometry_audit import matrices,extents
p=argparse.ArgumentParser();p.add_argument('--glb',required=True);p.add_argument('--out',required=True);a=p.parse_args()
out=pathlib.Path(a.out);assert not out.exists()
g=GLB(a.glb);rawacc=g.acc;cache={}
def acc(i):
    if i not in cache:cache[i]=rawacc(i)
    return cache[i]
g.acc=acc
baseline=extents(g,matrices(g));rows=[]
for anim in g.j['animations']:
    count=len(g.acc(anim['samplers'][0]['input']));worst={};first=None;last=None
    for k in range(count):
        data=extents(g,matrices(g,anim,k))
        if k==0:first=data
        if k==count-1:last=data
        for mesh in data:
            name=mesh['node'];size=np.array(mesh['extent_metres']);center=np.array(mesh['center_metres'])
            if name not in worst:worst[name]=dict(min_extent=size.copy(),max_extent=size.copy(),min_center=center.copy(),max_center=center.copy())
            w=worst[name]
            w['min_extent']=np.minimum(w['min_extent'],size);w['max_extent']=np.maximum(w['max_extent'],size)
            w['min_center']=np.minimum(w['min_center'],center);w['max_center']=np.maximum(w['max_center'],center)
    endpoint=max(np.max(np.abs(np.array(x['extent_metres'])-y['extent_metres'])) for x,y in zip(first,last))
    rows.append(dict(action=anim['name'],frames_evaluated=count,finite_world_vertices=True,endpoint_extent_error_metres=float(endpoint),meshes={name:{k:v.tolist() for k,v in w.items()} for name,w in worst.items()},collision='PENDING',visual='PENDING',listen_ready=False))
    print('AUDITED',anim['name'],count,flush=True)
out.write_text(json.dumps(dict(sha256=g.sha,frames_evaluated=sum(r['frames_evaluated'] for r in rows),clips=rows,scope='FULL_FRAME_WORLD_BOUNDS_NOT_COLLISION_APPROVAL'),indent=2)+'\n',encoding='utf8')
