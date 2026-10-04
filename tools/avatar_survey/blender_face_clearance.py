"""Fast exact nearest-triangle face diagnostics using Blender BVH.
Private inputs only; aggregate output. Signed nearest-normal values are diagnostic.
"""
import argparse,sys,pathlib,json
import numpy as np
from mathutils import Vector,Matrix
from mathutils.bvhtree import BVHTree
sys.path.insert(0,str(pathlib.Path(__file__).resolve().parent))
from audit_runtime import GLB
p=argparse.ArgumentParser()
for k in ('glb','fit','out'):p.add_argument('--'+k,required=True)
p.add_argument('--start-frame',type=int,default=129)
p.add_argument('--end-frame',type=int,default=180)
a=p.parse_args(sys.argv[sys.argv.index('--')+1:]);out=pathlib.Path(a.out).resolve();assert not out.exists()
g=GLB(a.glb);data=np.load(a.fit);old=data['original'];new=data['corrected'];names=list(data['names']);lookup={n:i for i,n in enumerate(names)}
skin=g.j['skins'][0];joints=skin['joints'];ib=g.acc(skin['inverseBindMatrices']).reshape(-1,4,4).transpose(0,2,1)
body=next(n for n in g.j['nodes'] if n.get('name')=='body');vs=[];ids=[];ws=[];ts=[];offset=0
for prim in g.j['meshes'][body['mesh']]['primitives']:
    attr=prim['attributes'];part=g.acc(attr['POSITION']);vs.append(part);ids.append(g.acc(attr['JOINTS_0']).astype(int));ws.append(g.acc(attr['WEIGHTS_0']));ts.append(g.acc(prim['indices']).reshape(-1,3).astype(int)+offset);offset+=len(part)
v=np.concatenate(vs);v=np.column_stack([v,np.ones(len(v))]);idx=np.concatenate(ids);weights=np.concatenate(ws);tri=np.concatenate(ts);vn=np.array([names[j] for j in joints])[idx]
def mask(pred):return (weights*np.vectorize(pred)(vn)).sum(axis=1)>.25
handmask=mask(lambda n:any(t in n for t in ['f_index','f_middle','f_ring','f_pinky','thumb','DEF-hand','palm']))
tipmask=mask(lambda n:'.03.' in n and any(t in n for t in ['f_index','f_middle','f_ring','f_pinky','thumb']))
wristmask=mask(lambda n:n in ['DEF-hand.L','DEF-hand.R'])
def deform(world):return np.einsum('vwij,vj,vw->vi',(world[joints]@ib)[idx],v,weights)[:,:3]
neutral=deform(old[0]);lip=old[0,lookup['CenterUpLips'],:3,3]
face=(neutral[:,1]>1.3)&~handmask
nose=face&mask(lambda n:'Nose' in n)
chin=face&(neutral[:,1]<lip[1]-.025)&(neutral[:,1]>lip[1]-.10)&(np.abs(neutral[:,0])<.08)&(neutral[:,2]>lip[2]-.10)
region_masks={'face':face,'nose':nose,'chin':chin};regions={}
for name,selected in region_masks.items():
    faces=tri[selected[tri].all(axis=1)];assert len(faces)>0,(name,len(faces));unique,inverse=np.unique(faces,return_inverse=True);regions[name]=(unique,inverse.reshape(-1,3).tolist())
def nearest(tree,points):
    minimum=1e9;signed_min=1e9;near_signed=[]
    for point in points:
        pt=Vector(point);co,normal,index,d=tree.find_nearest(pt);assert co is not None
        signed=d if (pt-co).dot(normal)>=0 else -d
        minimum=min(minimum,d);signed_min=min(signed_min,signed)
        if d<=.025:near_signed.append(signed)
    return dict(closest_m=float(minimum),signed_nearest_normal_m=float(signed_min),near_surface_signed_min_m=float(min(near_signed)) if near_signed else None,near_surface_radius_m=.025)
rows=[]
for k in range(a.start_frame-1,a.end_frame):
    vertices=deform(new[k]);trees={name:BVHTree.FromPolygons([Vector(p) for p in vertices[unique]],faces,all_triangles=True) for name,(unique,faces) in regions.items()}
    tips=nearest(trees['face'],vertices[tipmask]);wrists=nearest(trees['face'],vertices[wristmask]);nose_distance=nearest(trees['nose'],vertices[tipmask]);chin_distance=nearest(trees['chin'],vertices[tipmask])
    rows.append(dict(frame=k+1,fingertip_surface=tips,wrist_surface=wrists,nose_fingertip_distance_m=nose_distance['closest_m'],chin_fingertip_distance_m=chin_distance['closest_m'],middle_tip_lip_height_m={s:float(new[k,lookup[f'RT_DEF-f_middle.03.{s}_10'],1,3]-new[k,lookup['CenterUpLips'],1,3]) for s in ['L','R']},penetration_flag=any(x is not None and x<-.001 for x in [tips['near_surface_signed_min_m'],wrists['near_surface_signed_min_m']])))
parents={c:i for i,n in enumerate(g.j['nodes']) for c in n.get('children',[])};temporal=[]
for node in joints:
    local=np.linalg.inv(new[:,parents[node]])@new[:,node];scales=np.linalg.norm(local[:,:3,:3],axis=1);rots=local[:,:3,:3]/scales[:,None,:];qs=np.array([tuple(Matrix(m.tolist()).to_quaternion()) for m in rots]);norm_error=float(np.abs(np.linalg.norm(qs,axis=1)-1).max());qs/=np.linalg.norm(qs,axis=1)[:,None];degrees=np.degrees(2*np.arccos(np.clip(np.abs((qs[:-1]*qs[1:]).sum(axis=1)),0,1)));peak=int(np.argmax(degrees))
    temporal.append(dict(bone=names[node],max_adjacent_degrees=float(degrees[peak]),frames=[peak+1,peak+2],quaternion_norm_error=norm_error,max_scale_unit_error=float(np.abs(scales-1).max()),max_local_translation_step=float(np.linalg.norm(np.diff(local[:,:3,3],axis=0),axis=1).max())))
result=dict(frames_face_checked=[a.start_frame,a.end_frame],face_triangle_count=len(regions['face'][1]),nose_triangle_count=len(regions['nose'][1]),chin_triangle_count=len(regions['chin'][1]),region_definitions='Face=head-height non-hand vertices; nose=Nose bone weight >0.25; chin=front lower-face geometric region, not expert anatomical labeling',face_samples=rows,temporal=sorted(temporal,key=lambda x:x['max_adjacent_degrees'],reverse=True),neutral_unchanged=bool(np.array_equal(old[[0,-1]],new[[0,-1]])),scope='Exact nearest triangles for sampled skin vertices; signed normal diagnostics restricted to 25mm surface vicinity; distant held-down hand excluded from penetration flags; not FSL or full-body collision certification',listen_ready=False)
out.write_text(json.dumps(result,indent=2)+'\n',encoding='utf8');print('FACE_CLEARANCE_COMPLETE',flush=True)
