"""Explicitly authorized review export; preserves Core3 platform and other clips.

Append corrected local TRS channels to an existing canonical-platform GLB.
Requires private fitted world poses. Does not confer any acceptance/approval.
"""
import argparse, copy, hashlib, json, pathlib, struct
import numpy as np
from scipy.spatial.transform import Rotation
from audit_runtime import GLB, fingerprint
from geometry_audit import matrices

p = argparse.ArgumentParser()
for k in ('base', 'fit', 'out'):
    p.add_argument('--'+k, required=True)
p.add_argument('--action', default='THANK_YOU')
a = p.parse_args()
out = pathlib.Path(a.out).resolve()
assert not out.exists(), 'Never overwrite an asset'
g = GLB(a.base)
d = np.load(a.fit)
poses = d['corrected']
assert list(d['names']) == [n.get('name', '') for n in g.j['nodes']]
assert np.isfinite(poses).all()
assert len(g.j['skins']) == 1
parents = {c:i for i,n in enumerate(g.j['nodes']) for c in n.get('children', [])}
old = next(x for x in g.j['animations'] if x['name'] == a.action)
times = g.acc(old['samplers'][0]['input']).ravel()
assert len(times) == len(poses)
j = copy.deepcopy(g.j)
blob = bytearray(g.bin)

def accessor(values, kind):
    values = np.asarray(values, dtype='<f4')
    assert np.isfinite(values).all()
    blob.extend(b'\0' * (-len(blob) % 4))
    view = len(j['bufferViews'])
    j['bufferViews'].append(dict(buffer=0, byteOffset=len(blob), byteLength=values.nbytes))
    blob.extend(values.tobytes())
    acc = dict(bufferView=view, componentType=5126, count=len(values), type=kind)
    if kind == 'SCALAR':
        acc.update(min=[float(values.min())], max=[float(values.max())])
    idx = len(j['accessors'])
    j['accessors'].append(acc)
    return idx

time_idx = accessor(times, 'SCALAR')
anim = dict(name=a.action, samplers=[], channels=[])
for node in g.j['skins'][0]['joints']:
    assert node in parents
    local = np.linalg.inv(poses[:, parents[node]]) @ poses[:, node]
    scale = np.linalg.norm(local[:, :3, :3], axis=1)
    assert np.all(scale > 0)
    rot = local[:, :3, :3] / scale[:, None, :]
    assert np.max(np.abs(np.transpose(rot, (0,2,1)) @ rot - np.eye(3))) < 2e-5
    assert np.all(np.linalg.det(rot) > 0)
    q = Rotation.from_matrix(rot).as_quat()
    for frame in range(1, len(q)):
        if np.dot(q[frame-1], q[frame]) < 0:
            q[frame] *= -1
    for path, value, kind in [('translation',local[:,:3,3],'VEC3'),('rotation',q,'VEC4'),('scale',scale,'VEC3')]:
        sampler = len(anim['samplers'])
        anim['samplers'].append(dict(input=time_idx, output=accessor(value,kind), interpolation='LINEAR'))
        anim['channels'].append(dict(sampler=sampler, target=dict(node=node,path=path)))
j['animations'] = [anim if x['name']==a.action else x for x in j['animations']]
j['buffers'][0]['byteLength'] = len(blob)
payload = json.dumps(j, separators=(',',':'), ensure_ascii=False).encode('utf8')
payload += b' ' * (-len(payload)%4)
blob.extend(b'\0' * (-len(blob)%4))
raw = struct.pack('<III',0x46546c67,2,12+8+len(payload)+8+len(blob)) + struct.pack('<II',len(payload),0x4e4f534a)+payload+struct.pack('<II',len(blob),0x004e4942)+blob
out.parent.mkdir(parents=True, exist_ok=True)
with out.open('xb') as f:
    f.write(raw)
result = GLB(out)
assert result.bin[:len(g.bin)] == g.bin
for key in ('nodes','meshes','skins','materials','textures','images','scenes'):
    assert result.j.get(key) == g.j.get(key)
for original in g.j['animations']:
    if original['name'] != a.action:
        actual = next(x for x in result.j['animations'] if x['name']==original['name'])
        assert fingerprint(g,original) == fingerprint(result,actual)
cache = {}; rawacc = result.acc
def cached(i):
    if i not in cache: cache[i] = rawacc(i)
    return cache[i]
result.acc = cached
actual = next(x for x in result.j['animations'] if x['name']==a.action)
error = max(float(np.max(np.abs(np.array(matrices(result,actual,k))-poses[k]))) for k in range(len(poses)))
assert error < 2e-5, error
report = dict(sha256=result.sha, bytes=result.size, base_sha256=g.sha, fit_sha256=hashlib.sha256(pathlib.Path(a.fit).read_bytes()).hexdigest(), action=a.action, frames=len(poses), max_export_world_matrix_error=error, other_animations_exact=True, platform_exact=True, approval='UNAPPROVED_PRIVATE_ENGINEERING_EXPORT', listen_ready=False)
out.with_suffix('.export.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf8')
print(json.dumps(report))
