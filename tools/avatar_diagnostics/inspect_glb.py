"""Read GLB structure, finite samples and exported color masks; no asset writes.
Usage: python inspect_glb.py INPUT OUTPUT_JSON. Requires NumPy.
"""
import json,struct,pathlib,sys,numpy as np,hashlib
src,out=map(pathlib.Path,sys.argv[1:])
if any(p.resolve().drive.upper()!='C:' for p in [src,out]):raise SystemExit('C: paths required')
if out.suffix.lower()!='.json' or out.resolve()==src.resolve():raise SystemExit('Separate JSON output required')
b=src.read_bytes();size=struct.unpack_from('<I',b,12)[0];j=json.loads(b[20:20+size]);binary=b[28+size:]
def arr(i):
    a=j['accessors'][i];v=j['bufferViews'][a['bufferView']]
    dt=np.dtype({5126:'<f4',5123:'<u2',5121:'u1',5125:'<u4',5122:'<i2',5120:'i1'}[a['componentType']])
    width={'SCALAR':1,'VEC2':2,'VEC3':3,'VEC4':4,'MAT4':16}[a['type']]
    return np.ndarray((a['count'],width),dt,buffer=binary,offset=v.get('byteOffset',0)+a.get('byteOffset',0),strides=(v.get('byteStride',dt.itemsize*width),dt.itemsize)).copy()
r={'sha256':hashlib.sha256(b).hexdigest(),'bytes':len(b),'roots':[j['nodes'][i] for i in j['scenes'][j.get('scene',0)]['nodes']], 'clips':[], 'colors':[]}
for a in j.get('animations',[]):
    if not a['name'].startswith('FSL_'):continue
    finite=True;monotonic=True;norm_error=0;worst=0;minimum=1e10;maximum=-1e10;root_targets=[]
    for c in a['channels']:
        s=a['samplers'][c['sampler']];v=arr(s['output']);t=arr(s['input']).ravel()
        finite=finite and bool(np.isfinite(v).all() and np.isfinite(t).all())
        monotonic=monotonic and bool((np.diff(t)>0).all());minimum=min(minimum,float(t.min()));maximum=max(maximum,float(t.max()))
        if c['target']['path']=='rotation':
            norm_error=max(norm_error,float(abs(np.linalg.norm(v,axis=1)-1).max()))
            if len(v)>1:worst=max(worst,float(np.degrees(2*np.arccos(np.clip(abs(np.sum(v[1:]*v[:-1],axis=1)),-1,1))).max()))
        if c['target']['node'] in j['scenes'][j.get('scene',0)]['nodes']:root_targets.append(c['target'])
    r['clips'].append({'name':a['name'],'channels':len(a['channels']),'finite':finite,'strictly_increasing_times':monotonic,'first_key':minimum,'last_key':maximum,'quaternion_norm_max_error':norm_error,'quaternion_max_step_deg':worst,'animated_scene_roots':root_targets})
for mesh in j['meshes']:
    for p in mesh['primitives']:
        for name,i in p['attributes'].items():
            if name.startswith('COLOR_'):
                v=arr(i);r['colors'].append({'mesh':mesh['name'],'attribute':name,'min':float(v.min()),'max':float(v.max()),'black_rgb_fraction':float((v[:,:3]==0).all(axis=1).mean())})
out.write_text(json.dumps(r,indent=2));print(json.dumps({k:v for k,v in r.items() if k!='colors'},indent=2))
