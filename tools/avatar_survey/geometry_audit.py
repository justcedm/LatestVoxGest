"""Read-only world-space skin extent audit. Bounds are not collision clearance.
python geometry_audit.py --glb input.glb --out NEW.json
Requires numpy, scipy. Does not emit vertices, weights, or pose arrays.
"""
import argparse,json,pathlib
import numpy as np
from scipy.spatial.transform import Rotation
from audit_runtime import GLB

def matrices(g,anim=None,key=0):
    nodes=g.j['nodes'];world={}
    props=[{k:np.array(n.get(k,d),dtype=float) for k,d in [('translation',[0,0,0]),('rotation',[0,0,0,1]),('scale',[1,1,1])]} for n in nodes]
    if anim:
        for ch in anim['channels']:
            s=anim['samplers'][ch['sampler']]
            assert s.get('interpolation','LINEAR')=='LINEAR'
            props[ch['target']['node']][ch['target']['path']]=g.acc(s['output'])[key]
    local=[]
    for n,p in zip(nodes,props):
        if 'matrix' in n:
            local.append(np.array(n['matrix']).reshape(4,4).T)
        else:
            m=np.eye(4);m[:3,:3]=Rotation.from_quat(p['rotation']).as_matrix()*p['scale'][None,:];m[:3,3]=p['translation'];local.append(m)
    parents={c:i for i,n in enumerate(nodes) for c in n.get('children',[])}
    def solve(i):
        if i not in world:world[i]=solve(parents[i])@local[i] if i in parents else local[i]
        return world[i]
    return [solve(i) for i in range(len(nodes))]

def extents(g,world):
    rows=[]
    for ni,node in enumerate(g.j['nodes']):
        if 'mesh' not in node:continue
        mesh=g.j['meshes'][node['mesh']];points=[]
        skin=g.j['skins'][node['skin']] if 'skin' in node else None
        if skin:
            ib=g.acc(skin['inverseBindMatrices']).reshape(-1,4,4).transpose(0,2,1)
            palette=np.array([world[j] for j in skin['joints']])@ib
        for prim in mesh['primitives']:
            a=prim['attributes'];v=g.acc(a['POSITION']);v=np.column_stack([v,np.ones(len(v))])
            if skin:
                assert 'JOINTS_1' not in a,'Additional weights require explicit implementation'
                idx=g.acc(a['JOINTS_0']).astype(int);weights=g.acc(a['WEIGHTS_0'])
                assert np.max(np.abs(weights.sum(axis=1)-1))<1e-4
                out=np.einsum('vwij,vj,vw->vi',palette[idx],v,weights)
            else:out=v@world[ni].T
            assert np.isfinite(out).all();points.append(out[:,:3])
        points=np.concatenate(points)
        rows.append(dict(node=node.get('name'),skinned=skin is not None,vertices=len(points),extent_metres=(points.max(axis=0)-points.min(axis=0)).tolist(),center_metres=((points.max(axis=0)+points.min(axis=0))/2).tolist()))
    return rows

if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--glb',required=True);p.add_argument('--out',required=True);a=p.parse_args()
    out=pathlib.Path(a.out);assert not out.exists()
    g=GLB(a.glb);rows=[]
    for anim in g.j['animations']:
        count=len(g.acc(anim['samplers'][0]['input']))
        for key in sorted(set([0,count//2,count-1])):
            rows.append(dict(action=anim['name'],key_index=key,meshes=extents(g,matrices(g,anim,key))))
    result=dict(sha256=g.sha,bytes=g.size,default_meshes=extents(g,matrices(g)),sampled=rows,scope='WORLD_SPACE_BOUNDS_NOT_COLLISION_OR_VISUAL_PASS')
    out.write_text(json.dumps(result,indent=2)+'\n',encoding='utf8');print(json.dumps(dict(sha256=g.sha,meshes=result['default_meshes'])))
