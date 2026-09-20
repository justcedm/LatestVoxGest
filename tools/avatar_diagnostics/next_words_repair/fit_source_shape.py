# Experimental source-fit diagnostics. Not the frozen B32 solver.
# Run from the documented safe LOCAL_ROOT; inputs and binaries remain outside Git.
import pathlib,json,numpy as np,sys
from scipy.spatial.transform import Rotation,Slerp
from scipy.optimize import least_squares
root=pathlib.Path.cwd();out=root/'work/next_words_repair_20260920';rigs=json.loads((out/'rig.json').read_text());tag=sys.argv[1];ident={'HOW_ARE_YOU':4,'IM_FINE':5,'UNDERSTAND':10}[tag];label=tag.replace('_',' ');rig=rigs[tag];bones=rig['bones'];rest={n:np.array(b['matrix']) for n,b in bones.items()};stem=root/'work/recovered_authoritative/SOURCE_HANDOFF/fsl105_avatar_landmarks'/label/f'clips__{ident}__0';raw=np.load(str(stem)+'__raw225.npy').reshape(-1,75,3);pres=np.load(str(stem)+'__presence.npy');digits=[('f_index',[5,6,7,8]),('f_middle',[9,10,11,12]),('f_ring',[13,14,15,16]),('f_pinky',[17,18,19,20]),('thumb',[1,2,3,4])];result={'tag':tag,'frames':len(raw),'fps':60,'hands':{}}
unit=lambda v:v/np.linalg.norm(v)
for side,col,lo in [('R',2,54),('L',1,33)]:
 valid=np.flatnonzero(pres[:,col]);
 if not len(valid):continue
 hn='DEF-hand.'+side;locs=[];axes=[];lens=[];names=[]
 y=unit(np.array(bones['DEF-f_middle.01.'+side]['head'])-np.array(bones[hn]['head']));x=np.array(bones['DEF-f_index.01.'+side]['head'])-np.array(bones['DEF-f_pinky.01.'+side]['head']);x=unit(x-y*np.dot(x,y));normal=np.cross(x,y)
 for fn,ids in digits:
  for k in range(3):
   n=f'DEF-{fn}.{k+1:02d}.{side}';pn=hn if k==0 else f'DEF-{fn}.{k:02d}.{side}';locs.append(np.linalg.inv(rest[pn])@rest[n]);v=np.array(bones[n]['tail'])-np.array(bones[n]['head']);axes.append(rest[n][:3,:3].T@unit(np.cross(v,normal)));lens.append(np.linalg.norm(v));names.append(n)
 locs=np.array(locs);axes=np.array(axes);lens=np.array(lens)
 obs=raw[:,lo:lo+21,:2]*[640,360];obs=obs-obs[:,0:1];span=float(np.median(np.linalg.norm(obs[valid,9],axis=1)));obs/=span
 anchor=int(valid[len(valid)//2]);base=Rotation.from_matrix(np.array(rig['frames'][str(anchor)][hn])[:3,:3]);baseR=base.as_matrix()
 def calc(z,sign):
  rot=Rotation.from_rotvec(axes*z[:15,None]*sign).as_matrix();rot[12]=rot[12]@Rotation.from_rotvec([0,z[15],z[16]]).as_matrix();m=locs.copy();m[:,:3,:3]=m[:,:3,:3]@rot;pts=np.zeros((21,3));worldrot=Rotation.from_rotvec(z[18:21]).as_matrix()@baseR
  for d,(_,ids) in enumerate(digits):
   par=np.eye(4);par[:3,:3]=worldrot
   for k in range(3):
    j=d*3+k;par=par@m[j];pts[ids[k]]=par[:3,3];pts[ids[k+1]]=par[:3,3]+par[:3,1]*lens[j]
  return pts[:,[0,2]]*[1,-1]*z[17]
 low=np.r_[np.zeros(15),[-1.57,-1.57],.1,[-3.14]*3];high=np.r_[np.radians([110,115,85]*4+[100]*3),[1.57,1.57],8,[3.14]*3]
 def shape_prior(z):
  if tag!='HOW_ARE_YOU':return np.zeros(0)
  # Source clip 0 shows index/middle extended with ring/pinky folded during contact.
  goal=np.radians([75,95,65]*2)
  return .35*(z[6:12]-goal)
 best=None
 for sign in [1,-1]:
  for flex in [10,65]:
   start=np.r_[np.radians([flex]*15),[0,0],1.,[0,0,0]]
   fit=least_squares(lambda z:np.r_[(calc(z,sign)[1:]-obs[anchor,1:]).ravel(),.012*z[:17],shape_prior(z)],start,bounds=(low,high),loss='soft_l1',f_scale=.1,max_nfev=100)
   cost=float(np.linalg.norm(calc(fit.x,sign)-obs[anchor],axis=1).mean())
   if best is None or cost<best[0]:best=(cost,fit.x,sign)
 sign=best[2];samples={anchor:best[1]};errs={};grid=sorted(set(valid[::3].tolist()+[int(valid[-1]),anchor]))
 for seq in [[i for i in grid if i>anchor],list(reversed([i for i in grid if i<anchor]))]:
  prev=best[1]
  previous_frame=anchor
  for i in seq:
   dt=abs(i-previous_frame);step=np.r_[np.full(17,.12*dt),.1,np.full(3,.06*dt)];lb=np.maximum(low,prev-step);ub=np.minimum(high,prev+step)
   def fun(z):return np.r_[(calc(z,sign)[1:]-obs[i,1:]).ravel(),.12*(z[:17]-prev[:17]),.5*(z[18:21]-prev[18:21]),.035*(z[17]-prev[17]),shape_prior(z)]
   fit=least_squares(fun,prev,bounds=(lb,ub),loss='soft_l1',f_scale=.1,max_nfev=55);prev=fit.x;samples[i]=prev;previous_frame=i
 times=sorted(samples);zs=np.array([samples[i] for i in times]);full=np.arange(int(valid[0]),int(valid[-1])+1);zfull=np.stack([np.interp(full,times,zs[:,j]) for j in range(21)],axis=1);wr=Rotation.from_rotvec(zfull[:,18:21])*base
 fingers={}
 for j,n in enumerate(names):
  q=Rotation.from_rotvec(axes[j]*zfull[:,j,None]*sign)
  if j==12:q=q*Rotation.from_rotvec(np.stack([np.zeros(len(full)),zfull[:,15],zfull[:,16]],axis=1))
  fingers[n]=q.as_quat().tolist()
 errors=[float(np.linalg.norm(calc(z,sign)[1:]-obs[i,1:],axis=1).mean()) for i,z in zip(full,zfull) if pres[i,col]]
 result['hands'][side]={'first':int(valid[0]),'last':int(valid[-1]),'hand_world_xyzw':wr.as_quat().tolist(),'finger_local_xyzw':fingers,'mean_projection_error_palm_units':float(np.mean(errors)),'max_projection_error_palm_units':max(errors),'hinge_sign':sign,'fit_anchor':anchor}
 print(tag,side,'source 2D error',np.mean(errors),max(errors),flush=True)
(out/(tag+'_fit_v5.json')).write_text(json.dumps(result))
