"""Private-pose mechanical QA; publishes aggregate distances, no fitted geometry.
Face distances are exact point-to-triangle distances for sampled surface vertices.
Signed nearest-normal penetration is a flag, not a watertight collision certificate.
"""
import argparse,json,pathlib
import numpy as np
from scipy.spatial.transform import Rotation
from scipy.spatial import cKDTree
from audit_runtime import GLB
p=argparse.ArgumentParser()
for k in ('glb','fit','out'):p.add_argument('--'+k,required=True)
a=p.parse_args();out=pathlib.Path(a.out);assert not out.exists();g=GLB(a.glb);data=np.load(a.fit);old=data['original'];new=data['corrected'];names=list(data['names']);lookup={n:i for i,n in enumerate(names)}
skin=g.j['skins'][0];joints=skin['joints'];ib=g.acc(skin['inverseBindMatrices']).reshape(-1,4,4).transpose(0,2,1)
body=next(n for n in g.j['nodes'] if n.get('name')=='body');prims=g.j['meshes'][body['mesh']]['primitives']
vs=[];ids=[];ws=[];ts=[];offset=0
for prim in prims:
    attr=prim['attributes'];part=g.acc(attr['POSITION']);vs.append(part);ids.append(g.acc(attr['JOINTS_0']).astype(int));ws.append(g.acc(attr['WEIGHTS_0']));ts.append(g.acc(prim['indices']).reshape(-1,3).astype(int)+offset);offset+=len(part)
v=np.concatenate(vs);v=np.column_stack([v,np.ones(len(v))]);idx=np.concatenate(ids);weights=np.concatenate(ws);tri=np.concatenate(ts)
bone_names=np.array([names[j] for j in joints]);vn=bone_names[idx]
is_hand=np.vectorize(lambda x:any(t in x for t in ['f_index','f_middle','f_ring','f_pinky','thumb','DEF-hand','palm']))(vn)
is_tip=np.vectorize(lambda x:'.03.' in x and any(t in x for t in ['f_index','f_middle','f_ring','f_pinky','thumb']))(vn)
is_wrist=np.vectorize(lambda x:x in ['DEF-hand.L','DEF-hand.R'])(vn)
handmask=(weights*is_hand).sum(axis=1)>.2;tipmask=(weights*is_tip).sum(axis=1)>.25;wristmask=(weights*is_wrist).sum(axis=1)>.5
def deform(world):return np.einsum('vwij,vj,vw->vi',(world[joints]@ib)[idx],v,weights)[:,:3]
neutral=deform(old[0]);facemask=(neutral[:,1]>1.30)&(~handmask);facetri=tri[facemask[tri].all(axis=1)];assert len(facetri)>100 and tipmask.sum()>10
def distance(points,vertices):
    all_triangles=vertices[facetri]
    centers=all_triangles.mean(axis=1)
    max_radius=float(np.linalg.norm(all_triangles-centers[:,None,:],axis=2).max())
    center_tree=cKDTree(centers);vertex_tree=cKDTree(all_triangles.reshape(-1,3))
    minimum=1e9;signed_min=1e9
    for start in range(0,len(points),24):
        batch=points[start:start+24];center=batch.mean(axis=0)
        # Nearest vertex is a valid upper bound on nearest triangle distance.
        # Triangle centroid distance minus its radius is a lower bound.
        radius=float(np.linalg.norm(batch-center,axis=1).max()+vertex_tree.query(batch)[0].max()+max_radius+1e-9)
        selected=center_tree.query_ball_point(center,radius)
        triangles=all_triangles[selected]
        A=triangles[:,0];B=triangles[:,1];C=triangles[:,2];AB=B-A;AC=C-A
        norm=np.cross(AB,AC);norm/=np.maximum(np.linalg.norm(norm,axis=1)[:,None],1e-15)
        P=batch[:,None,:];AP=P-A
        d00=(AB*AB).sum(1);d01=(AB*AC).sum(1);d11=(AC*AC).sum(1);d20=(AP*AB).sum(2);d21=(AP*AC).sum(2);den=d00*d11-d01*d01
        u=(d11*d20-d01*d21)/np.maximum(den,1e-20);w=(d00*d21-d01*d20)/np.maximum(den,1e-20)
        projection=A+u[:,:,None]*AB+w[:,:,None]*AC
        candidates=[]
        dist=np.sum((P-projection)**2,axis=2);dist[(u<0)|(w<0)|(u+w>1)]=np.inf
        candidates.append((dist,projection))
        for X,Y in [(A,B),(B,C),(C,A)]:
            edge=Y-X;t=np.clip(((P-X)*edge).sum(2)/np.maximum((edge*edge).sum(1),1e-20),0,1);Q=X+t[:,:,None]*edge
            candidates.append((((P-Q)**2).sum(2),Q))
        ds=np.stack([x[0] for x in candidates]);besttype=np.argmin(ds,axis=0);bestdist=ds.min(axis=0);bt=np.argmin(bestdist,axis=1)
        for i,tidx in enumerate(bt):
            cp=candidates[besttype[i,tidx]][1][i,tidx];d=float(np.sqrt(bestdist[i,tidx]));sign=float(np.dot(points[start+i]-cp,norm[tidx]));minimum=min(minimum,d);signed_min=min(signed_min,np.copysign(d,sign))
    return dict(closest_m=float(minimum),signed_nearest_normal_m=float(signed_min))
parents={c:i for i,n in enumerate(g.j['nodes']) for c in n.get('children',[])}
temporal=[]
for node in joints:
    parent=parents[node];local=np.linalg.inv(new[:,parent])@new[:,node];scale=np.linalg.norm(local[:,:3,:3],axis=1);r=Rotation.from_matrix(local[:,:3,:3]/scale[:,None,:]);angles=np.degrees((r[:-1].inv()*r[1:]).magnitude());k=int(np.argmax(angles))
    temporal.append(dict(bone=names[node],max_step_degrees=float(angles[k]),frames=[k+1,k+2],max_translation_step_m_or_rig_units=float(np.linalg.norm(np.diff(local[:,:3,3],axis=0),axis=1).max()),scale_unit_error=float(np.abs(scale-1).max()),quaternion_norm_error=float(np.abs(np.linalg.norm(r.as_quat(),axis=1)-1).max())))
rows=[]
progress=out.with_suffix('.progress.jsonl')
assert not progress.exists()
stream=progress.open('x',encoding='utf8')
for k in range(128,180):
    vertices=deform(new[k]);assert np.isfinite(vertices).all()
    tip=distance(vertices[tipmask],vertices);wrist=distance(vertices[wristmask],vertices)
    rows.append(dict(frame=k+1,fingertip_surface=tip,wrist_surface=wrist,penetration_flag=tip['signed_nearest_normal_m']<-.001 or wrist['signed_nearest_normal_m']<-.001))
    stream.write(json.dumps(rows[-1])+'\n');stream.flush()
    if k%10==0:print('FACE_QA',k+1,flush=True)
stream.close()
report=dict(frames_face_checked=[129,180],tip_vertices=int(tipmask.sum()),face_triangles=len(facetri),wrist_vertices=int(wristmask.sum()),face_samples=rows,temporal=sorted(temporal,key=lambda r:r['max_step_degrees'],reverse=True),neutral_matrix_unchanged=bool(np.array_equal(old[[0,-1]],new[[0,-1]])),source_mapping='PENDING_REVIEW',full_human_visual='PENDING',collision_scope='Point-to-face triangle distances; signed flags require visual confirmation; not full finger self-intersection proof',listen_ready=False)
out.write_text(json.dumps(report,indent=2)+'\n',encoding='utf8');print('FACE_QA_COMPLETE',flush=True)
