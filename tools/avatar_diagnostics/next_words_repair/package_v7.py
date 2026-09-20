# Experimental source-fit diagnostics. Not the frozen B32 solver.
# Run from the documented safe LOCAL_ROOT; inputs and binaries remain outside Git.
import bpy,pathlib,json,hashlib,math,numpy as np
root=pathlib.Path.cwd();out=root/'work/next_words_repair_20260920';base=root/'work/purchased_avatar_lane_20260919/purchased_avatar_CALIBRATION_WORKING_v1.blend'
bpy.ops.wm.open_mainfile(filepath=str(base),use_scripts=False)
def sig(a):return hashlib.sha256(repr([(f.data_path,f.array_index,[(tuple(k.co),k.interpolation) for k in f.keyframe_points]) for f in a.fcurves]).encode()).hexdigest()
old={a.name:sig(a) for a in bpy.data.actions};arm=bpy.data.objects['Waitress RIG'];scene=bpy.context.scene;checks=[];acts=[]
for tag in ['IM_FINE','HOW_ARE_YOU','UNDERSTAND']:
 with bpy.data.libraries.load(str(out/(tag+'_SOURCE_FIT_v7.blend')),link=False) as (src,dst):dst.actions=[n for n in src.actions if n==f'FSL_{tag}__SOURCE_FIT_V7']
 a=dst.actions[0];acts.append(a);a['status']='CORRECTED_CANDIDATE_REVIEW_REQUIRED';a['listen_ready']=False
 finite=all(math.isfinite(float(v)) for f in a.fcurves for k in f.keyframe_points for v in k.co);ends=max(abs(f.keyframe_points[0].co[1]-f.keyframe_points[-1].co[1]) for f in a.fcurves)
 peak=[];normerr=0
 for g in a.groups:
  fs=sorted([f for f in g.channels if f.data_path.endswith('rotation_quaternion')],key=lambda f:f.array_index)
  if len(fs)!=4:continue
  qs=np.array([[k.co[1] for k in f.keyframe_points] for f in fs]).T;normerr=max(normerr,float(abs(np.linalg.norm(qs,axis=1)-1).max()));steps=np.degrees(2*np.arccos(np.clip(abs((qs[1:]*qs[:-1]).sum(axis=1)),-1,1)));i=int(np.argmax(steps));peak.append({'bone':g.name,'step':float(steps[i]),'from_frame':i+1,'to_frame':i+2})
 checks.append({'action':a.name,'frames':int(a.frame_range[1]),'finite':finite,'max_quaternion_norm_error':normerr,'first_last_channel_difference':ends,'largest_steps':sorted(peak,key=lambda r:r['step'],reverse=True)[:5]})
 assert finite and normerr<1e-5 and ends<1e-4
 assert all(sig(bpy.data.actions[n])==v for n,v in old.items())
playlist=bpy.data.actions.new('REVIEW_PLAYLIST_IM_FINE__HOW_ARE_YOU__UNDERSTAND');playlist.use_fake_user=True;offset=0
for a in acts:
 scene.timeline_markers.new(a.name.replace('FSL_','').replace('__SOURCE_FIT_V7',''),frame=offset+1)
 for fc in a.fcurves:
  dest=playlist.fcurves.find(fc.data_path,index=fc.array_index)
  if dest is None:dest=playlist.fcurves.new(fc.data_path,index=fc.array_index,action_group=fc.group.name)
  for k in fc.keyframe_points:dest.keyframe_points.insert(offset+k.co[0],k.co[1],options={'FAST'})
  dest.keyframe_points.insert(offset+int(a.frame_range[1])+30,fc.keyframe_points[-1].co[1],options={'FAST'})
  dest.update()
 offset+=int(a.frame_range[1])+30
for fc in playlist.fcurves:
 for k in fc.keyframe_points:k.interpolation='LINEAR'
arm.animation_data.action=playlist;scene.frame_start=1;scene.frame_end=offset-30;scene.frame_set(1);scene.render.fps=60
text=bpy.data.texts.new('START_HERE_REVIEW');text.write('CORRECTED CANDIDATES - REVIEW REQUIRED\nSpace: play all three words at source speed. Timeline markers name each word.\nOrder: IM FINE, HOW ARE YOU, UNDERSTAND. 30-frame neutral gaps between clips.\nSource clips keep 243/244/245 frames at 60 FPS. Original purchased source and earlier Actions preserved.\nNo human-motion, linguistic, collision or export PASS is claimed.\nFace/expression motion remains uncalibrated. See Git handoff for measurements.\n')
bpy.context.preferences.filepaths.file_preview_type='NONE';path=out/'NEXT_WORDS_CORRECTED_REVIEW_v7.blend';bpy.ops.wm.save_as_mainfile(filepath=str(path))
(out/'review_checks_v7.json').write_text(json.dumps({'checks':checks,'previous_actions_unchanged':old,'blend_sha256':hashlib.sha256(path.read_bytes()).hexdigest(),'playlist_frames':scene.frame_end,'source_fps':60},indent=2))
for a in acts:
 arm.animation_data.action=a
 for frame in [110,205,235]:
  scene.frame_set(frame);scene.render.resolution_x=400;scene.render.resolution_y=400;scene.render.filepath=str(out/(a.name+'_'+str(frame)+'.png'));bpy.ops.render.render(write_still=True)
