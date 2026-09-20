# Experimental source-fit diagnostics. Not the frozen B32 solver.
# Run from the documented safe LOCAL_ROOT; inputs and binaries remain outside Git.
import bpy,json,pathlib,numpy as np
root=pathlib.Path.cwd();out=root/'work/next_words_repair_20260920';data={}
for tag in ['HOW_ARE_YOU','IM_FINE','UNDERSTAND']:
 p=root/'work/next_words_20260920';p=p if tag=='HOW_ARE_YOU' else p/tag
 bpy.ops.wm.open_mainfile(filepath=str(p/(tag+'_PURCHASED_DRAFT_v1.blend')),use_scripts=False)
 a=bpy.data.objects['Waitress RIG'];a.animation_data.action=bpy.data.actions['FSL_'+tag+'__PURCHASED_DRAFT_V1'];s=bpy.context.scene
 bones={b.name:{'matrix':[list(r) for r in b.matrix_local],'head':list(b.head_local),'tail':list(b.tail_local),'parent':b.parent.name if b.parent else None} for b in a.data.bones}
 frames={}
 ident={"HOW_ARE_YOU":4,"IM_FINE":5,"UNDERSTAND":10}[tag]
 pres=np.load(str(root/"work/recovered_authoritative/SOURCE_HANDOFF/fsl105_avatar_landmarks"/tag.replace("_"," ")/f"clips__{ident}__0__presence.npy"))
 anchors=[int(v[len(v)//2])+1 for col in [1,2] for v in [np.flatnonzero(pres[:,col])] if len(v)]
 for f in anchors:
  s.frame_set(f);frames[str(f-1)]={n:[list(r) for r in a.pose.bones[n].matrix] for n in ['DEF-hand.R','DEF-hand.L','DEF-upper_arm.R','DEF-upper_arm.L','DEF-forearm.R','DEF-forearm.L','DEF-spine.003']}
 data[tag]={'bones':bones,'frames':frames,'scale':list(a.scale)}
(out/'rig.json').write_text(json.dumps(data))
