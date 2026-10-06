"""Compare private pose derivatives; report numeric evidence, never linguistic approval."""
import argparse,pathlib,json,numpy as np
from scipy.spatial.transform import Rotation
from audit_runtime import GLB
p=argparse.ArgumentParser()
for k in ('glb','before','after','out'):p.add_argument('--'+k,required=True)
a=p.parse_args();out=pathlib.Path(a.out);assert not out.exists();g=GLB(a.glb);b=np.load(a.before);n=np.load(a.after);assert np.array_equal(b['names'],n['names']);old=b['corrected'];new=n['corrected'];names=list(n['names']);parents={c:i for i,node in enumerate(g.j['nodes']) for c in node.get('children',[])};rows=[]
for node in g.j['skins'][0]['joints']:
 row={'bone':str(names[node])}
 for label,poses in [('before',old),('after',new)]:
  local=np.linalg.inv(poses[:,parents[node]])@poses[:,node];sc=np.linalg.norm(local[:,:3,:3],axis=1);q=Rotation.from_matrix(local[:,:3,:3]/sc[:,None,:]).as_quat();ang=np.degrees(2*np.arccos(np.clip(np.abs(np.sum(q[:-1]*q[1:],axis=1)),0,1)));k=int(ang.argmax());steps=np.linalg.norm(np.diff(local[:,:3,3],axis=0),axis=1);row[label]={'max_rotation_step_degrees':float(ang[k]),'rotation_frames':[k+1,k+2],'max_translation_step_local_units':float(steps.max()),'scale_unit_error':float(np.max(np.abs(sc-1)))}
 row['rotation_peak_increase_degrees']=row['after']['max_rotation_step_degrees']-row['before']['max_rotation_step_degrees'];rows.append(row)
delta=np.max(np.abs(new-old),axis=(2,3));changed=np.flatnonzero(delta.max(axis=0)>1e-10);roots=[i for i in range(len(names)) if i not in parents]
r={'frames':len(new),'finite':bool(np.isfinite(new).all()),'neutral_exact_to_previous':bool(np.array_equal(old[[0,-1]],new[[0,-1]])),'stroke_96_220_exact':bool(np.array_equal(old[95:220],new[95:220])),'root_exact':bool(np.array_equal(old[:,roots],new[:,roots])),'changed_bones':[str(names[i]) for i in changed],'changed_frames':(np.flatnonzero(delta.max(axis=1)>1e-10)+1).tolist(),'max_new_world_translation_m':float(np.linalg.norm(new[:,:,:3,3]-old[:,:,:3,3],axis=2).max()),'largest_rotation_peaks':sorted(rows,key=lambda r:r['after']['max_rotation_step_degrees'],reverse=True)[:8],'largest_peak_increases':sorted(rows,key=lambda r:r['rotation_peak_increase_degrees'],reverse=True)[:8],'scope':'Derived quaternions normalized by scipy; keyed quaternion norms require Blender/export audit. Local translations use rig units, not metres. No visual/FSL/Android approval.','listen_ready':False}
out.write_text(json.dumps(r,indent=2)+'\n');print(json.dumps({k:v for k,v in r.items() if k not in ['changed_bones','changed_frames','largest_rotation_peaks','largest_peak_increases']},indent=2))
