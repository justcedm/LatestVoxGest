"""Blender regression guard: bulk digest equality and sensitivity for protected Action fields."""
import ast,pathlib,hashlib,numpy as np,bpy
source=pathlib.Path(__file__).with_name('build_action_pose_review.py').read_text()
node=next(n for n in ast.parse(source).body if isinstance(n,ast.FunctionDef) and n.name=='digest')
exec(compile(ast.Module(body=[node],type_ignores=[]),'<digest>','exec'))
a=bpy.data.actions.new('DIGEST_TEST');c=a.fcurves.new('location',index=0);c.keyframe_points.insert(1,0);c.keyframe_points.insert(5,1);base=digest(a)
assert digest(a.copy())==base
changes=[lambda c:setattr(c,'extrapolation','LINEAR'),lambda c:setattr(c,'data_path','scale'),lambda c:setattr(c,'array_index',1),lambda c:setattr(c.keyframe_points[0],'co',(1,.3)),lambda c:setattr(c.keyframe_points[0],'interpolation','LINEAR'),lambda c:setattr(c.keyframe_points[0],'handle_left_type','FREE'),lambda c:setattr(c.keyframe_points[0],'handle_right_type','FREE'),lambda c:setattr(c.keyframe_points[0],'handle_left',(-2,.2)),lambda c:setattr(c.keyframe_points[0],'handle_right',(3,.8))]
for change in changes:
 b=a.copy();change(b.fcurves[0]);assert digest(b)!=base
print('DIGEST_TEST_PASS: identical copy and 9 protected-field mutations')
