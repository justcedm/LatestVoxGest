"""Private side-by-side review evidence; does not grant human/FSL approval.
python compare_video.py --source clip.MOV --candidate render.mp4 --out NEW.mp4
Requires opencv-python. Both inputs must contain the same frames at 60 FPS.
"""
import argparse
import hashlib
import json
import pathlib
import cv2
import numpy as np

p=argparse.ArgumentParser()
for key in ('source','candidate','out'):
    p.add_argument('--'+key,required=True)
a=p.parse_args()
out=pathlib.Path(a.out).resolve()
assert not out.exists()
caps=[cv2.VideoCapture(str(pathlib.Path(x).resolve())) for x in (a.source,a.candidate)]
assert all(c.isOpened() for c in caps)
counts=[int(c.get(cv2.CAP_PROP_FRAME_COUNT)) for c in caps]
fps=[c.get(cv2.CAP_PROP_FPS) for c in caps]
assert counts[0]==counts[1] and all(abs(x-60)<.01 for x in fps),(counts,fps)
writer=cv2.VideoWriter(str(out),cv2.VideoWriter_fourcc(*'mp4v'),60,(1120,520))
assert writer.isOpened()
for i in range(counts[0]):
    canvas=np.full((520,1120,3),240,dtype=np.uint8)
    for col,(cap,label) in enumerate(zip(caps,('REFERENCE: clip 7/0','CANDIDATE: NOT APPROVED'))):
        ok,frame=cap.read()
        assert ok,(col,i)
        scale=min(550/frame.shape[1],460/frame.shape[0])
        frame=cv2.resize(frame,(round(frame.shape[1]*scale),round(frame.shape[0]*scale)))
        y=45+(460-frame.shape[0])//2
        x=col*560+(560-frame.shape[1])//2
        canvas[y:y+frame.shape[0],x:x+frame.shape[1]]=frame
        cv2.putText(canvas,label,(col*560+10,25),cv2.FONT_HERSHEY_SIMPLEX,.65,(20,20,20),1,cv2.LINE_AA)
    cv2.putText(canvas,f'Frame {i+1} / {counts[0]} | 60 FPS | inspect face placement and frames 127-128',(10,515),cv2.FONT_HERSHEY_SIMPLEX,.5,(20,20,20),1,cv2.LINE_AA)
    writer.write(canvas)
writer.release()
for cap in caps:
    cap.release()
check=cv2.VideoCapture(str(out))
assert int(check.get(cv2.CAP_PROP_FRAME_COUNT))==counts[0]
check.release()
def sha(path):return hashlib.sha256(pathlib.Path(path).read_bytes()).hexdigest()
report=dict(frames=counts[0],fps=60,source_sha256=sha(a.source),candidate_render_sha256=sha(a.candidate),comparison_sha256=sha(out),human_motion_visual='PENDING',fsl_approval='PENDING',listen_ready=False)
out.with_suffix('.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf8')
print(json.dumps(report))
