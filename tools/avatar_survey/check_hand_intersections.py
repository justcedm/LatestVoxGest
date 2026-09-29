"""Blender mathutils triangle-overlap diagnostic on private fitted poses.
No scene/source mutation. Excludes proximal shared finger bases by weight mask.
Positive overlap flags require inspection; surface contact is not automatically invalid.
"""
import argparse,sys,pathlib,json
import numpy as np
from mathutils import Vector
from mathutils.bvhtree import BVHTree
sys.path.insert(0,str(pathlib.Path(__file__).resolve().parent))
from audit_runtime import GLB
p=argparse.ArgumentParser()
for k in ('glb','fit','out'):p.add_argument('--'+k,required=True)
p.add_argument('--baseline',action='store_true')
a=p.parse_args(sys.argv[sys.argv.index('--')+1:]);out=pathlib.Path(a.out).resolve();assert not out.exists()
g=GLB(a.glb);data=np.load(a.fit);poses=data['original' if a.baseline else 'corrected'];names=list(data['names']);skin=g.j['skins'][0];joints=skin['joints'];ib=g.acc(skin['inverseBindMatrices']).reshape(-1,4,4).transpose(0,2,1)
node=next(n for n in g.j['nodes'] if n.get('name')=='body');vs=[];ids=[];ws=[];ts=[];offset=0
for prim in g.j['meshes'][node['mesh']]['primitives']:
    attr=prim['attributes'];part=g.acc(attr['POSITION']);vs.append(part);ids.append(g.acc(attr['JOINTS_0']).astype(int));ws.append(g.acc(attr['WEIGHTS_0']));ts.append(g.acc(prim['indices']).reshape(-1,3).astype(int)+offset);offset+=len(part)
v=np.concatenate(vs);v=np.column_stack([v,np.ones(len(v))]);idx=np.concatenate(ids);weights=np.concatenate(ws);tri=np.concatenate(ts)
vn=np.array([names[j] for j in joints])[idx]
def deform(m):return np.einsum('vwij,vj,vw->vi',(m[joints]@ib)[idx],v,weights)[:,:3]
def mask(predicate):return (weights*np.vectorize(predicate)(vn)).sum(axis=1)>.5
parts={}
for side in ['L','R']:
    parts['hand.'+side]=mask(lambda n:('.'+side in n) and any(x in n for x in ['DEF-hand','palm','f_index','f_middle','f_ring','f_pinky','thumb']))
    for finger in ['f_index','f_middle','f_ring','f_pinky','thumb']:
        parts[finger+'.'+side]=mask(lambda n:finger in n and '.'+side in n and ('.02.' in n or '.03.' in n))
neutral=deform(poses[0]);parts['face']=(neutral[:,1]>1.3)&~parts['hand.L']&~parts['hand.R']
regions={}
for name,selected in parts.items():
    faces=tri[selected[tri].all(axis=1)];unique,inverse=np.unique(faces,return_inverse=True)
    regions[name]=(unique,inverse.reshape(-1,3).tolist())
assert all(len(faces)>0 for _,faces in regions.values())
# Verify this API distinguishes triangle overlap from bounding-box overlap.
t1=BVHTree.FromPolygons([Vector(x) for x in [(0,0,0),(1,0,0),(0,1,0)]],[(0,1,2)],all_triangles=True)
t2=BVHTree.FromPolygons([Vector(x) for x in [(.9,.9,0),(1.5,.9,0),(.9,1.5,0)]],[(0,1,2)],all_triangles=True)
assert not t1.overlap(t2),'BVH API is only broad-phase; do not claim triangle intersections'
pairs=[('hand.L','face'),('hand.R','face'),('hand.L','hand.R')]
for side in ['L','R']:
    fingers=[f+'.'+side for f in ['f_index','f_middle','f_ring','f_pinky','thumb']]
    pairs += [(x,y) for i,x in enumerate(fingers) for y in fingers[i+1:]]
flags=[]
for f,m in enumerate(poses,1):
    vertices=deform(m)
    trees={name:BVHTree.FromPolygons([Vector(p) for p in vertices[unique]],faces,all_triangles=True) for name,(unique,faces) in regions.items()}
    for x,y in pairs:
        overlaps=trees[x].overlap(trees[y])
        if overlaps:flags.append(dict(frame=f,parts=[x,y],triangle_overlap_pairs=len(overlaps)))
    if f%60==0:print('COLLISION_FRAME',f,flush=True)
result=dict(baseline=a.baseline,frames=243,regions={name:len(faces) for name,(_,faces) in regions.items()},pairs_per_frame=len(pairs),flags=flags,scope='Triangle overlap diagnostics: face/hands, inter-hand and distal inter-finger; excludes proximal shared bases, not all body collision types',listen_ready=False)
out.write_text(json.dumps(result,indent=2)+'\n',encoding='utf8');print('INTERSECTION_CHECK_COMPLETE',len(flags),flush=True)
