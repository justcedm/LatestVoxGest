# Experimental source-fit diagnostics. Not the frozen B32 solver.
# Run from the documented safe LOCAL_ROOT; inputs and binaries remain outside Git.
import bpy,numpy as np,json,pathlib,math,hashlib
from mathutils import Vector,Matrix,Quaternion
root=pathlib.Path.cwd();out=root/'work/next_words_repair_20260920'
base=root/'work/purchased_avatar_lane_20260919/purchased_avatar_CALIBRATION_WORKING_v1.blend'
bpy.ops.wm.open_mainfile(filepath=str(base),use_scripts=False)
arm=bpy.data.objects['Waitress RIG'];scene=bpy.context.scene;scene.frame_set(1)
neutral_basis={b.name:arm.pose.bones[b.name].matrix_basis.copy() for b in arm.data.bones}
neutral_world={b.name:arm.pose.bones[b.name].matrix.to_quaternion() for b in arm.data.bones}
rest={b.name:b.matrix_local.to_quaternion() for b in arm.data.bones}
ordered=[]
def add(b):
 if b.name in ordered:return
 if b.parent:add(b.parent)
 ordered.append(b.name)
for b in arm.data.bones:add(b)
def unit(v):
 v=Vector(v)
 if v.length<1e-8:raise ValueError('Degenerate observed geometry')
 return v.normalized()
def basis(y,x):
 y=unit(y);x=Vector(x)-y*Vector(x).dot(y);x=unit(x);z=x.cross(y).normalized();return Matrix((x,y,z)).transposed()
def filt(a):
 padded=np.pad(a,((2,2),(0,0),(0,0)),mode='edge');return sum(padded[i:i+len(a)]*w for i,w in enumerate([1,4,6,4,1]))/16

candidate={'label':'IM FINE','stem':str(root/'work/recovered_authoritative/SOURCE_HANDOFF/fsl105_avatar_landmarks/IM FINE/clips__5__0')}
fits=json.loads((out/'IM_FINE_fit_v4.json').read_text())
neutral_heads={n:arm.pose.bones[n].matrix.translation.copy() for n in arm.pose.bones.keys()}
reports=[]
for candidate in [candidate]:
 stem=candidate['stem'];meta=json.loads(pathlib.Path(stem+'__meta.json').read_text());raw=np.load(stem+'__raw225.npy',allow_pickle=False);pres=np.load(stem+'__presence.npy',allow_pickle=False).astype(bool)
 assert raw.shape==(meta['frames'],225) and pres.shape==(len(raw),3) and np.isfinite(raw).all() and not meta['input_mirrored'] and meta['fps']==60
 coords=raw.reshape(len(raw),75,3).astype(float);coords[:,:,0]*=meta['width'];coords[:,:,1]*=meta['height'];coords[:,:,2]*=meta['width']
 coords=coords[:,:,[0,2,1]];coords[:,:,2]*=-1
 pose=filt(coords[:,:33]);hands={};gaps={}
 for si,side,lo in [(1,'L',33),(2,'R',54)]:
  idx=np.flatnonzero(pres[:,si]);gaps[side]={'detected_frames':len(idx)}
  if not len(idx):continue
  maxgap=int(max(np.diff(idx)-1,default=0));assert maxgap<=3,'Long hand gap requires source review'
  h=coords[:,lo:lo+21].copy()
  for j in range(21):
   for k in range(3):h[:,j,k]=np.interp(np.arange(len(h)),idx,h[idx,j,k])
  h=filt(h)
  if 'handshape_hold_interval' in candidate:
   begin,end=candidate['handshape_hold_interval'];samples=[]
   for f in range(begin,end+1):
    sb=basis(h[f,9]-h[f,0],h[f,5]-h[f,17]);scale=np.linalg.norm(h[f,9]-h[f,0]);samples.append(np.array([list(sb.transposed()@Vector(v-h[f,0])) for v in h[f]])/scale)
   template=np.median(samples,axis=0)
   for f in range(len(h)):
    sb=basis(h[f,9]-h[f,0],h[f,5]-h[f,17]);scale=np.linalg.norm(h[f,9]-h[f,0]);w=h[f,0].copy();h[f]=np.array([list(sb@Vector(v*scale)) for v in template])+w
   gaps[side]['source_measured_hold_interval']=[begin,end]
  hands[side]=h;gaps[side].update(first=int(idx[0]),last=int(idx[-1]),max_interior_gap=maxgap,edge_policy='nearest observed handshape during out-of-view preparation/recovery; review required')

 act=bpy.data.actions.new('FSL_IM_FINE__SOURCE_FIT_V6');act.use_fake_user=True;act['listen_ready']=False;act['status']='EXPERIMENTAL_NOT_ACCEPTED';arm.animation_data.action=act
 keys={};prev={};goal_prev={};direction_prev={}
 for fi in range(len(raw)):
  desired={};desired_positions={}
  for side,sh,el,wr in [('L',11,13,15),('R',12,14,16)]:
   for prefix,vec in [('upper_arm',pose[fi,el]-pose[fi,sh]),('forearm',pose[fi,wr]-pose[fi,el])]:
    for suffix in ['', '.001']:
     n='DEF-'+prefix+'.'+side+suffix;b=arm.data.bones[n];desired[n]=unit(b.tail_local-b.head_local).rotation_difference(unit(vec))@rest[n]
   hn='DEF-hand.'+side
   if side in hands:
    h=hands[side][fi];b=arm.data.bones;w=b[hn].head_local;mid=b['DEF-f_middle.01.'+side].head_local;ix=b['DEF-f_index.01.'+side].head_local;pk=b['DEF-f_pinky.01.'+side].head_local
    rb=basis(mid-w,ix-pk);sb=basis(h[9]-h[0],h[5]-h[17]);delta=(sb@rb.inverted()).to_quaternion();desired[hn]=delta@rest[hn]
    for fn,ids in [('thumb',[1,2,3,4]),('f_index',[5,6,7,8]),('f_middle',[9,10,11,12]),('f_ring',[13,14,15,16]),('f_pinky',[17,18,19,20])]:
     for seg in range(3):
      n=f'DEF-{fn}.{seg+1:02d}.{side}';b=arm.data.bones[n]
      parent_name=hn if seg==0 else f'DEF-{fn}.{seg:02d}.{side}'
      base=desired[parent_name]@rest[parent_name].inverted()@rest[n]
      v=base@Vector((0,1,0))
      if seg==0:
       desired[n]=unit(v).rotation_difference(unit(h[ids[1]]-h[ids[0]]))@base
      else:
       incoming=unit(h[ids[seg]]-h[ids[seg-1]]);outgoing=unit(h[ids[seg+1]]-h[ids[seg]])
       angle=math.acos(max(-1.0,min(1.0,incoming.dot(outgoing))))
       inward=rb.col[2]*(1 if side=='R' else -1)
       axis=unit((b.tail_local-b.head_local).cross(inward));axis_local=rest[n].inverted()@axis
       desired[n]=base@Quaternion(axis_local,angle)
   else:
    desired[hn]=desired['DEF-forearm.'+side]@rest['DEF-forearm.'+side].inverted()@rest[hn]

  # Replace ambiguous palm depth with temporally constrained visible-source fitting.
  for side,sh,el,wr in [('L',11,13,15),('R',12,14,16)]:
   if side not in fits['hands']:continue
   fit=fits['hands'][side];j=max(0,min(fi-fit['first'],fit['last']-fit['first']));hn='DEF-hand.'+side
   q=fit['hand_world_xyzw'][j];desired[hn]=Quaternion((q[3],q[0],q[1],q[2]))
   for fn in ['f_index','f_middle','f_ring','f_pinky','thumb']:
    for seg in range(3):
     n=f'DEF-{fn}.{seg+1:02d}.{side}';pn=hn if seg==0 else f'DEF-{fn}.{seg:02d}.{side}';q=fit['finger_local_xyzw'][n][j];desired[n]=desired[pn]@rest[pn].inverted()@rest[n]@Quaternion((q[3],q[0],q[1],q[2]))
   # Source shoulder-relative wrist placement, limb-length-constrained two-bone IK.
   un='DEF-upper_arm.'+side;ln='DEF-forearm.'+side;shoulder=neutral_heads[un];upperlen=(neutral_heads[ln]-shoulder).length;lowerlen=(neutral_heads[hn]-neutral_heads[ln]).length
   mid=(neutral_heads['DEF-upper_arm.L']+neutral_heads['DEF-upper_arm.R'])/2;srcmid=(pose[fi,11]+pose[fi,12])/2
   scale=(neutral_heads['DEF-upper_arm.L']-neutral_heads['DEF-upper_arm.R']).length/max(abs(pose[fi,11,0]-pose[fi,12,0]),1e-6)
   target=mid+Vector((pose[fi,wr]-srcmid)*scale);pole=mid+Vector((pose[fi,el]-srcmid)*scale)
   # Recover depth from fixed limb lengths and visible elbow/wrist projection.
   projected=pole-shoulder;projected.y=0
   if projected.length>upperlen*.995:projected*=upperlen*.995/projected.length
   elbow=shoulder+projected;elbow.y=shoulder.y-math.sqrt(max(0.,upperlen**2-projected.length_squared))
   projected=target-elbow;projected.y=0
   if projected.length>lowerlen*.995:projected*=lowerlen*.995/projected.length
   target=elbow+projected;depth_sign=-1 if float(np.median(pose[max(0,fit['first']):fit['last']+1,wr,1]-pose[max(0,fit['first']):fit['last']+1,el,1]))<=0 else 1
   target.y=elbow.y+depth_sign*math.sqrt(max(0.,lowerlen**2-projected.length_squared))
   for prefix,vec in [('upper_arm',elbow-shoulder),('forearm',target-elbow)]:
    for suffix in ['', '.001']:
     n='DEF-'+prefix+'.'+side+suffix;b=arm.data.bones[n];old=direction_prev.get(n,rest[n]);desired[n]=unit(old@Vector((0,1,0))).rotation_difference(unit(vec))@old;direction_prev[n]=desired[n].copy()
   # Explicit FK endpoint positions avoid leaving fingers at frozen ORG-control locations.
   desired_positions[un]=shoulder;desired_positions[ln]=elbow;desired_positions[hn]=target
   for n0,n1,end in [(un,'DEF-upper_arm.'+side+'.001',elbow),(ln,'DEF-forearm.'+side+'.001',target)]:
    fraction=(arm.data.bones[n1].head_local-arm.data.bones[n0].head_local).length/max((arm.data.bones[n0].tail_local-arm.data.bones[n0].head_local).length*2,1e-6)
    desired_positions[n1]=desired_positions[n0].lerp(end,min(1.,fraction))
   handmatrix=Matrix.LocRotScale(target,desired[hn],Vector((1,1,1)))
   for fn in ['f_index','f_middle','f_ring','f_pinky','thumb']:
    parentmatrix=handmatrix
    for seg in range(3):
     n=f'DEF-{fn}.{seg+1:02d}.{side}';pn=hn if seg==0 else f'DEF-{fn}.{seg:02d}.{side}';local=arm.data.bones[pn].matrix_local.inverted()@arm.data.bones[n].matrix_local;q=fit['finger_local_xyzw'][n][j];local=local@Quaternion((q[3],q[0],q[1],q[2])).to_matrix().to_4x4();m=parentmatrix@local;desired_positions[n]=m.translation;desired[n]=m.to_quaternion();parentmatrix=m
   for n in arm.data.bones.keys():
    if n.startswith('DEF-palm.') and n.endswith('.'+side):
     m=handmatrix@arm.data.bones[hn].matrix_local.inverted()@arm.data.bones[n].matrix_local;desired[n]=m.to_quaternion();desired_positions[n]=m.translation
  posed={}
  for n in ordered:
   b=arm.data.bones[n];pb=arm.pose.bones[n];kw={'parent_matrix':posed[b.parent.name],'parent_matrix_local':b.parent.matrix_local} if b.parent else {}
   local_basis=neutral_basis[n].copy();target=b.convert_local_to_pose(local_basis,b.matrix_local,**kw)
   if n in desired:
    side='L' if '.L' in n else 'R';cov=gaps[side];weight=0.0
    if cov['detected_frames']:
     weight=min(1.,max(0.,(fi-(cov['first']-24))/24),max(0.,((cov['last']+24)-fi)/24));weight=weight*weight*(3-2*weight)
    goal=desired[n].copy();goal.normalize()
    reference=goal_prev.get(n,neutral_world[n])
    if goal.dot(reference)<0:goal.negate()
    goal_prev[n]=goal.copy();delta=neutral_world[n].inverted()@goal;axis=Vector((delta.x,delta.y,delta.z));angle=2*math.atan2(axis.length,delta.w)
    q=neutral_world[n]@Quaternion(axis.normalized(),angle*weight) if axis.length>1e-8 else neutral_world[n].copy()
    loc,rot,sc=target.decompose();loc=neutral_heads[n].lerp(desired_positions[n],weight) if n in desired_positions else loc;target=Matrix.LocRotScale(loc,q,sc);local_basis=b.convert_local_to_pose(target,b.matrix_local,invert=True,**kw)
   posed[n]=target;pb.rotation_mode='QUATERNION';pb.matrix_basis=local_basis
   if n in prev and pb.rotation_quaternion.dot(prev[n])<0:pb.rotation_quaternion.negate()
   prev[n]=pb.rotation_quaternion.copy()
   if b.use_deform:
    keys.setdefault(n,[]).append(list(pb.rotation_quaternion))
    for prop in ['location','rotation_quaternion','scale']:pb.keyframe_insert(prop,frame=fi+1,group=n)
 for fc in act.fcurves:
  for k in fc.keyframe_points:k.interpolation='LINEAR'
 for n,qs in keys.items():
  arr=np.asarray(qs);sm=[]
  for i in range(len(arr)):
   w=arr[np.clip(np.arange(i-2,i+3),0,len(arr)-1)].copy();w[np.sum(w*arr[i],axis=1)<0]*=-1;q=np.sum(w*np.array([1,4,6,4,1])[:,None],axis=0);sm.append((q/np.linalg.norm(q)).tolist())
  keys[n]=sm
 for fc in act.fcurves:
  if fc.data_path.endswith('rotation_quaternion'):
   for i,k in enumerate(fc.keyframe_points):k.co[1]=keys[fc.group.name][i][fc.array_index]
 metrics={}
 for n,qs in keys.items():
  a=np.array(qs);delta=np.degrees(2*np.arccos(np.clip(np.abs(np.sum(a[1:]*a[:-1],axis=1)),-1,1)));metrics[n]=float(delta.max())
 report={'canonical_label':candidate['label'],'action':act.name,'frames':len(raw),'fps':60,'hand_coverage':gaps,'max_quaternion_step_degrees':max(metrics.values()),'worst_bones':sorted(metrics.items(),key=lambda x:x[1],reverse=True)[:10],'listen_ready':False,'status':'DRAFT_REVIEW_REQUIRED','limitations':['Experimental solver, not frozen B32','Unobserved preparation/recovery handshape is unverified','Facial and head motion not calibrated','No export or mechanical/visual PASS'],'source_hashes':{pathlib.Path(stem+suffix).name:hashlib.sha256(pathlib.Path(stem+suffix).read_bytes()).hexdigest() for suffix in ['__meta.json','__raw225.npy','__presence.npy']}}
 scene.frame_start=1;scene.frame_end=len(raw);scene.render.fps=60;scene.frame_set(152)
 bpy.context.preferences.filepaths.file_preview_type='NONE'
 bpy.ops.wm.save_as_mainfile(filepath=str(out/'IM_FINE_SOURCE_FIT_v6.blend'))
 for frame in [132,172]:
  scene.frame_set(frame);scene.render.filepath=str(out/f'IM_FINE_v6_{frame}.png');bpy.ops.render.render(write_still=True)
 (out/'IM_FINE_v6_measurements.json').write_text(json.dumps(report,indent=2));print(json.dumps(report),flush=True)

