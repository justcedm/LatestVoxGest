# Experimental source-fit diagnostics. Not the frozen B32 solver.
# Run from the documented safe LOCAL_ROOT; inputs and binaries remain outside Git.
import bpy,pathlib,json,numpy as np
from mathutils import Quaternion
root=pathlib.Path.cwd();out=root/'work/next_words_repair_20260920'
for tag in ['IM_FINE','HOW_ARE_YOU','UNDERSTAND']:
 bpy.ops.wm.open_mainfile(filepath=str(out/(tag+'_SOURCE_FIT_v6.blend')),use_scripts=False);arm=bpy.data.objects['Waitress RIG'];a=bpy.data.actions['FSL_'+tag+'__SOURCE_FIT_V6'].copy();a.name='FSL_'+tag+'__SOURCE_FIT_V7';a.use_fake_user=True;arm.animation_data.action=a
 fit=json.loads((out/(tag+('_fit_v5.json' if tag=='HOW_ARE_YOU' else '_fit_v4.json'))).read_text())
 for group in a.groups:
  n=group.name
  if not n.startswith(('DEF-upper_arm.','DEF-forearm.','DEF-hand.','DEF-palm.','DEF-f_','DEF-thumb.')):continue
  side='L' if '.L' in n else 'R'
  if side not in fit['hands']:continue
  first=fit['hands'][side]['first'];last=fit['hands'][side]['last'];begin=max(0,first-24);end=min(len(next(iter(group.channels)).keyframe_points)-1,last+24)
  fs=sorted([f for f in group.channels if f.data_path.endswith('rotation_quaternion')],key=lambda f:f.array_index)
  qs=[Quaternion([f.keyframe_points[i].co[1] for f in fs]) for i in range(len(fs[0].keyframe_points))]
  for lo,hi,start,endq in [(begin,first,qs[0],qs[first]),(last,end,qs[last],qs[-1])]:
   for i in range(lo,hi+1):
    t=(i-lo)/max(hi-lo,1);t=t*t*(3-2*t);q=start.slerp(endq,t)
    for c,f in enumerate(fs):f.keyframe_points[i].co[1]=q[c]
  for f in group.channels:
   if f.data_path.endswith('rotation_quaternion'):continue
   for lo,hi,v0,v1 in [(begin,first,f.keyframe_points[0].co[1],f.keyframe_points[first].co[1]),(last,end,f.keyframe_points[last].co[1],f.keyframe_points[-1].co[1])]:
    for i in range(lo,hi+1):t=(i-lo)/max(hi-lo,1);t=t*t*(3-2*t);f.keyframe_points[i].co[1]=v0+(v1-v0)*t
  for f in group.channels:f.update()
 bpy.context.scene.frame_set(172);bpy.context.preferences.filepaths.file_preview_type='NONE';bpy.ops.wm.save_as_mainfile(filepath=str(out/(tag+'_SOURCE_FIT_v7.blend')))
