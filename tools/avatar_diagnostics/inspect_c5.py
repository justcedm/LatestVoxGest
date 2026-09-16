"""Read-only Blender 3.2 C5 stage comparison. Never saves or exports an asset.
blender -b --factory-startup --disable-autoexec --python inspect_c5.py -- INPUT OUT
Diagnostic workbench cameras are evidence views, never runtime corrections.
"""
import bpy, sys, pathlib, json, math, hashlib
from mathutils import Vector
args = sys.argv[sys.argv.index('--') + 1:] if '--' in sys.argv else []
if len(args) != 2:
    raise SystemExit('Provide INPUT (.blend/.glb) and evidence OUT directory')
src, out = map(lambda p: pathlib.Path(p).resolve(), args)
if src.drive.upper() != 'C:' or out.drive.upper() != 'C:' or src == out:
    raise SystemExit('Explicit separate C: paths required')
before = hashlib.sha256(src.read_bytes()).hexdigest()
out.mkdir(parents=True, exist_ok=True)
if src.suffix == '.blend':
    bpy.ops.wm.open_mainfile(filepath=str(src), use_scripts=False)
else:
    bpy.ops.object.select_all(action='SELECT')
    bpy.ops.object.delete(use_global=False)
    bpy.context.scene.render.fps = 60
    bpy.ops.import_scene.gltf(filepath=str(src))
scene = bpy.context.scene
arm = next(o for o in scene.objects if o.type == 'ARMATURE')
def matrix(m): return [list(r) for r in m]
report = {'input': str(src), 'sha256': before, 'blender': bpy.app.version_string,
          'armature': {'name': arm.name, 'world': matrix(arm.matrix_world),
                       'parent': arm.parent.name if arm.parent else None,
                       'rotation': list(arm.rotation_euler), 'scale': list(arm.scale)},
          'bones': {b.name: {'parent': b.parent.name if b.parent else None,
                              'rest': matrix(b.matrix_local), 'head': list(b.head_local),
                              'tail': list(b.tail_local)} for b in arm.data.bones},
          'constraints': {p.name: [c.type for c in p.constraints] for p in arm.pose.bones if p.constraints},
          'meshes': {}, 'actions': [], 'samples': []}
for o in scene.objects:
    if o.type != 'MESH': continue
    report['meshes'][o.name] = {'base_vertices': len(o.data.vertices), 'world': matrix(o.matrix_world),
        'modifiers': [{'type': m.type, 'viewport': m.show_viewport, 'render': m.show_render} for m in o.modifiers]}
report['gltf_export_defaults'] = {p.identifier: p.default for p in bpy.ops.export_scene.gltf.get_rna_type().properties
    if p.identifier in ['export_apply', 'export_colors', 'export_yup', 'export_skins', 'export_all_influences']}
scene.render.engine = 'BLENDER_WORKBENCH'
scene.render.resolution_x = 360; scene.render.resolution_y = 440; scene.render.resolution_percentage = 100
scene.render.image_settings.file_format = 'PNG'
scene.display.shading.light = 'STUDIO'; scene.display.shading.color_type = 'MATERIAL'
scene.display.shading.background_type = 'VIEWPORT'; scene.display.shading.background_color = (.7, .8, .85)
scene.render.film_transparent = False
camdata = bpy.data.cameras.new('Evidence camera'); cam = bpy.data.objects.new('Evidence camera', camdata)
scene.collection.objects.link(cam); scene.camera = cam; camdata.type = 'ORTHO'; camdata.ortho_scale = 1.95
target = Vector((0, 0, .88))
for a in list(bpy.data.actions):
    if not a.name.startswith('FSL_'): continue
    arm.animation_data_create(); arm.animation_data.action = a
    if arm.animation_data.nla_tracks:
        for track in arm.animation_data.nla_tracks: track.mute = True
    nonarm = []
    for fc in a.fcurves:
        if not fc.data_path.startswith('pose.bones['): continue
        n = fc.data_path.split('"')[1]
        if n.startswith(('DEF-upper_arm', 'DEF-forearm', 'DEF-hand', 'DEF-f_', 'DEF-thumb')): continue
        values = [p.co.y for p in fc.keyframe_points]
        if values and max(values) - min(values) > 1e-5: nonarm.append(n)
    report['actions'].append({'name': a.name, 'range': list(a.frame_range), 'fps': scene.render.fps,
                              'animated_non_arm_bones': sorted(set(nonarm))})
    for phase, frame in [('neutral', 1), ('preparation_sample', 100), ('stroke_sample', 160), ('recovery', int(a.frame_range[1]))]:
        scene.frame_set(frame); dg = bpy.context.evaluated_depsgraph_get()
        sample = {'action': a.name, 'phase': phase, 'frame': frame, 'bounds': {}}
        for o in scene.objects:
            if o.type != 'MESH' or o.hide_render: continue
            ev = o.evaluated_get(dg); mesh = ev.to_mesh()
            pts = [ev.matrix_world @ v.co for v in mesh.vertices]
            if pts: sample['bounds'][o.name] = {'vertices': len(pts), 'min': [min(v[i] for v in pts) for i in range(3)], 'max': [max(v[i] for v in pts) for i in range(3)]}
            ev.to_mesh_clear()
        for view, angle in [('FRONT', 0), ('SIDE', math.pi/2), ('THREE_QUARTER', math.pi/4)]:
            cam.location = target + Vector((4*math.sin(angle), -4*math.cos(angle), 0))
            cam.rotation_euler = (target-cam.location).to_track_quat('-Z', 'Y').to_euler()
            scene.render.filepath = str(out / (a.name + '_' + phase + '_' + view + '.png'))
            bpy.ops.render.render(write_still=True)
        report['samples'].append(sample)
    (out/'inspection.json').write_text(json.dumps(report, indent=2))
report['input_unchanged'] = hashlib.sha256(src.read_bytes()).hexdigest() == before
report['phase_note'] = 'Frames 100 and 160 are diagnostic preparation/stroke samples, not qualified linguistic phase annotations. Screenshot label/time were not supplied.'
(out/'inspection.json').write_text(json.dumps(report, indent=2))
print('AUDIT_COMPLETE', src, report['input_unchanged'], flush=True)
