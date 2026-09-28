"""Read-only GLB audit. Outputs aggregate evidence, never geometry or approval.

Usage: python audit_runtime.py --core CORE.glb --candidate CANDIDATE.glb --out report.json
Requires numpy. Dense glTF 2 accessors only; unsupported storage fails closed.
"""
import argparse
import hashlib
import json
import pathlib
import struct
import numpy as np


class GLB:
    def __init__(self, path):
        raw = pathlib.Path(path).read_bytes()
        assert struct.unpack_from('<III', raw) == (0x46546C67, 2, len(raw))
        self.sha = hashlib.sha256(raw).hexdigest()
        self.size = len(raw)
        pos = 12
        while pos < len(raw):
            length, kind = struct.unpack_from('<II', raw, pos)
            pos += 8
            payload = raw[pos:pos + length]
            assert len(payload) == length
            pos += length
            if kind == 0x4E4F534A:
                self.j = json.loads(payload)
            elif kind == 0x004E4942:
                self.bin = payload

    def acc(self, idx):
        a = self.j['accessors'][idx]
        assert 'sparse' not in a and not a.get('normalized', False)
        v = self.j['bufferViews'][a['bufferView']]
        assert v.get('buffer', 0) == 0
        dtype = np.dtype({5120:'i1',5121:'u1',5122:'<i2',5123:'<u2',5125:'<u4',5126:'<f4'}[a['componentType']])
        width = {'SCALAR':1,'VEC2':2,'VEC3':3,'VEC4':4,'MAT4':16}[a['type']]
        return np.ndarray((a['count'], width), dtype=dtype, buffer=self.bin,
            offset=v.get('byteOffset',0)+a.get('byteOffset',0),
            strides=(v.get('byteStride',width*dtype.itemsize),dtype.itemsize)).copy()


def fingerprint(g, anim):
    h = hashlib.sha256(json.dumps(anim, sort_keys=True).encode())
    for s in anim['samplers']:
        for key in ('input', 'output'):
            h.update(g.acc(s[key]).tobytes())
    return h.hexdigest()


def audit(core, candidate):
    c, g = GLB(core), GLB(candidate)
    old = {a['name']: a for a in c.j['animations']}
    new = {a['name']: a for a in g.j['animations']}
    regression = {}
    for name, anim in old.items():
        actual = fingerprint(g, new[name]) if name in new else None
        expected = fingerprint(c, anim)
        regression[name] = dict(expected_sha256=expected, actual_sha256=actual,
            exact_data_pass=actual == expected, visual_device='PENDING')
    joints = set(g.j['skins'][0]['joints'])
    rows = []
    for name in new:
        if name not in new:
            continue
        a = new[name]
        row = dict(action=name, frames=0, duration=0, quaternion_norm_error=0,
            max_adjacent_rotation_degrees=0, max_scale_unit_error=0,
            max_adjacent_translation_rig_units=0, max_endpoint_component_difference=0,
            joint_channels_only=True, monotonic_time=True, interpolations=set())
        for channel in a['channels']:
            s = a['samplers'][channel['sampler']]
            t, v = g.acc(s['input']).ravel(), g.acc(s['output']).astype(float)
            assert s.get('interpolation','LINEAR') == 'LINEAR'
            row['interpolations'].add(s.get('interpolation','LINEAR'))
            row['frames'] = max(row['frames'], len(t))
            row['duration'] = max(row['duration'], float(t[-1]))
            row['monotonic_time'] &= bool(np.all(np.diff(t)>0))
            row['joint_channels_only'] &= channel['target']['node'] in joints
            row['max_endpoint_component_difference'] = max(row['max_endpoint_component_difference'], float(np.abs(v[0]-v[-1]).max()))
            kind = channel['target']['path']
            if kind == 'rotation':
                norm = np.linalg.norm(v,axis=1)
                assert np.all(norm > 0)
                row['quaternion_norm_error'] = max(row['quaternion_norm_error'], float(np.abs(norm-1).max()))
                q = v/norm[:,None]
                degrees = np.degrees(2*np.arccos(np.clip(np.abs((q[:-1]*q[1:]).sum(axis=1)),0,1)))
                peak = float(degrees.max(initial=0))
                if peak > row['max_adjacent_rotation_degrees']:
                    k = int(np.argmax(degrees))
                    row['max_adjacent_rotation_degrees'] = peak
                    row['largest_rotation_step'] = dict(bone=g.j['nodes'][channel['target']['node']].get('name'),
                        from_key_index=k, to_key_index=k+1, from_time=float(t[k]), to_time=float(t[k+1]))
            elif kind == 'scale':
                row['max_scale_unit_error'] = max(row['max_scale_unit_error'],float(np.abs(v-1).max()))
            elif kind == 'translation':
                row['max_adjacent_translation_rig_units'] = max(row['max_adjacent_translation_rig_units'],float(np.linalg.norm(np.diff(v,axis=0),axis=1).max(initial=0)))
        row['interpolations'] = sorted(row['interpolations'])
        row['collision_and_deformation'] = 'NOT_TESTED'
        row['listen_ready'] = False
        rows.append(row)
    return dict(core_sha256=c.sha,candidate_sha256=g.sha,candidate_bytes=g.size,
        platform_unchanged=all(c.j.get(k)==g.j.get(k) for k in ('nodes','meshes','skins','materials','textures','images','scenes')),
        original_binary_prefix_unchanged=g.bin[:len(c.bin)]==c.bin,
        finite_accessors=all(bool(np.isfinite(g.acc(i)).all()) for i in range(len(g.j['accessors']))),
        skin_count=len(g.j['skins']),valid_joints=all(0<=j<len(g.j['nodes']) for j in joints),
        core3_regression=regression,clips=rows,scope='NUMERIC_AND_DATA_ONLY',listen_ready=False)


if __name__ == '__main__':
    p = argparse.ArgumentParser()
    for key in ('core','candidate','out'):
        p.add_argument('--'+key, required=True)
    args = p.parse_args()
    out = pathlib.Path(args.out)
    assert not out.exists(), 'Choose a new report path; preserve previous evidence.'
    result = audit(args.core,args.candidate)
    out.write_text(json.dumps(result,indent=2)+'\n',encoding='utf8')
    print(json.dumps(result,indent=2))
