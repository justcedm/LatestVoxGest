"""Read-only hands versus skinned skirt/tshirt triangle-overlap audit; no acceptance inference."""
import argparse,sys,pathlib,json,numpy as np
from mathutils import Vector
from mathutils.bvhtree import BVHTree
sys.path.insert(0,str(pathlib.Path(__file__).resolve().parent))
from audit_runtime import GLB
p=argparse.ArgumentParser()
for k in ('glb','fit','out'):p.add_argument('--'+k,required=True)
a=p.parse_args(sys.argv[sys.argv.index('--')+1:]);out=pathlib.Path(a.out);assert not out.exists();g=GLB(a.glb);data=np.load(a.fit);poses=data['corrected'];names=list(data['names']);meshes={}
for name in ['body','skirt','tshirt']:
 node=next(n for n in g.j['nodes'] if n.get('name')==name);skin=g.j['skins'][node['skin']];joints=skin['joints'];ib=g.acc(skin['inverseBindMatrices']).reshape(-1,4,4).transpose(0,2,1);vs=[];ids=[];ws=[];ts=[];offset=0
 for prim in g.j['meshes'][node['mesh']]['primitives']:
  at=prim['attributes'];v=g.acc(at['POSITION']);vs.append(v);ids.append(g.acc(at['JOINTS_0']).astype(int));ws.append(g.acc(at['WEIGHTS_0']));ts.append(g.acc(prim['indices']).reshape(-1,3).astype(int)+offset);offset+=len(v)
 v=np.concatenate(vs);v=np.column_stack([v,np.ones(len(v))]);idx=np.concatenate(ids);weights=np.concatenate(ws);tri=np.concatenate(ts);regions={}
 if name=='body':
  vn=np.array([names[j] for j in joints])[idx]
  for side in ['L','R']:
   selected=(weights*np.vectorize(lambda n:('.'+side in n) and any(x in n for x in ['DEF-hand','palm','f_index','f_middle','f_ring','f_pinky','thumb']))(vn)).sum(axis=1)>.5
   faces=tri[selected[tri].all(axis=1)];unique,inverse=np.unique(faces,return_inverse=True);regions['hand.'+side]=(unique,inverse.reshape(-1,3).tolist())
 else:regions[name]=(np.arange(len(v)),tri.tolist())
 meshes[name]=(v,idx,weights,joints,ib,regions)
flags=[]
for f,world in enumerate(poses,1):
 trees={}
 for v,idx,weights,joints,ib,regions in meshes.values():
  vertices=np.einsum('vwij,vj,vw->vi',(world[joints]@ib)[idx],v,weights)[:,:3]
  for name,(unique,tri) in regions.items():trees[name]=BVHTree.FromPolygons([Vector(p) for p in vertices[unique]],tri,all_triangles=True)
 for side in ['L','R']:
  for garment in ['skirt','tshirt']:
   hits=trees['hand.'+side].overlap(trees[garment])
   if hits:flags.append({'frame':f,'parts':['hand.'+side,garment],'triangle_overlap_pairs':len(hits)})
 if f%60==0:print('CLOTHING_FRAME',f,flush=True)
out.write_text(json.dumps({'frames':len(poses),'pairs_per_frame':4,'flags':flags,'scope':'Skinned body hand triangles versus complete skirt/tshirt surface triangles. No inside-volume, face, linguistic or visual approval.','listen_ready':False},indent=2)+'\n');print('CLOTHING_FLAGS',len(flags))
