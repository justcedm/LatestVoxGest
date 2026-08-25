# collect_fsl_alphabet_seq.py
# Collects FSL alphabet as 20-frame sequences matching FSL-105 format
import os
os.environ['PROTOCOL_BUFFERS_PYTHON_IMPLEMENTATION'] = 'python'
import cv2
import numpy as np
import mediapipe as mp

SAVE_DIR       = r"D:\BSIT 3RD YEAR\New VovGest\external_datasets\fsl_features"
SEQUENCES      = 200   # sequences per letter
SEQ_LENGTH     = 20    # frames per sequence
FEATURE_SIZE   = 162   # must match FSL-105 format
LETTERS        = list("ABCDEFGHIJKLMNOPQRSTUVWXYZ")

mp_hands   = mp.solutions.hands
mp_pose    = mp.solutions.pose
mp_draw    = mp.solutions.drawing_utils

# 12 upper body pose landmark indices
POSE_LANDMARKS = [11,12,13,14,15,16,23,24,25,26,27,28]

def extract_frame_features(hand_results, pose_results):
    vec = np.zeros(FEATURE_SIZE, dtype=np.float32)

    # Right hand [0:63]
    if hand_results.multi_hand_landmarks:
        for i, hl in enumerate(hand_results.multi_hand_landmarks):
            handedness = hand_results.multi_handedness[i].classification[0].label
            lm = np.array([[p.x, p.y, p.z] for p in hl.landmark])
            wrist = lm[0].copy()
            lm -= wrist
            scale = np.linalg.norm(lm[9])
            if scale > 0:
                lm /= scale
            if handedness == 'Right':
                vec[0:63] = lm.flatten()
            else:
                vec[63:126] = lm.flatten()

    # Pose [126:162] — 12 landmarks × 3
    if pose_results.pose_landmarks:
        for i, idx in enumerate(POSE_LANDMARKS):
            p = pose_results.pose_landmarks.landmark[idx]
            vec[126 + i*3]     = p.x
            vec[126 + i*3 + 1] = p.y
            vec[126 + i*3 + 2] = p.z

    return vec

def collect():
    letter_idx = 0
    state      = "WAIT"
    frame_buf  = []

    cap = cv2.VideoCapture(0)

    with mp_hands.Hands(
        max_num_hands=2,
        min_detection_confidence=0.6,
        min_tracking_confidence=0.5
    ) as hands, mp_pose.Pose(
        min_detection_confidence=0.5,
        min_tracking_confidence=0.5
    ) as pose:

        while cap.isOpened() and letter_idx < len(LETTERS):
            letter    = LETTERS[letter_idx]
            save_dir  = os.path.join(SAVE_DIR, letter)
            os.makedirs(save_dir, exist_ok=True)
            existing  = len([f for f in os.listdir(save_dir)
                             if f.endswith('.npy')])

            ret, frame = cap.read()
            if not ret:
                break

            frame = cv2.flip(frame, 1)
            h, w  = frame.shape[:2]
            rgb   = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)

            hand_res = hands.process(rgb)
            pose_res = pose.process(rgb)

            hand_ok = hand_res.multi_hand_landmarks is not None

            # ── State machine ─────────────────────────────
            if state == "RECORD" and hand_ok:
                feat = extract_frame_features(hand_res, pose_res)
                frame_buf.append(feat)

                if len(frame_buf) == SEQ_LENGTH:
                    seq  = np.array(frame_buf)  # [20, 162]
                    path = os.path.join(
                        save_dir, f"seq_{existing:04d}.npy")
                    np.save(path, seq)
                    existing  += 1
                    frame_buf  = []

                    if existing >= SEQUENCES:
                        print(f"  {letter}: {existing} sequences done")
                        letter_idx += 1
                        state       = "WAIT"

            # ── UI ────────────────────────────────────────
            col = (0,255,120) if hand_ok else (0,0,255)
            overlay = frame.copy()
            cv2.rectangle(overlay,(0,0),(w,115),(10,10,10),-1)
            cv2.addWeighted(overlay,0.7,frame,0.3,0,frame)

            cv2.putText(frame, f"FSL Letter: {letter}",
                (20,55), cv2.FONT_HERSHEY_SIMPLEX, 1.5, col, 3)
            cv2.putText(frame,
                f"Sequences: {existing}/{SEQUENCES}",
                (20,90), cv2.FONT_HERSHEY_SIMPLEX, 0.7,
                (255,255,255), 2)

            if state == "WAIT":
                cv2.putText(frame,
                    "Hold FSL handshape → SPACE to record",
                    (20,113), cv2.FONT_HERSHEY_SIMPLEX,
                    0.5, (0,200,255), 1)
            else:
                cv2.putText(frame,
                    f"RECORDING {len(frame_buf)}/{SEQ_LENGTH}",
                    (20,113), cv2.FONT_HERSHEY_SIMPLEX,
                    0.5, (0,255,0), 1)

            prog = int((existing/SEQUENCES)*(w-40))
            cv2.rectangle(frame,(20,h-18),(20+prog,h-8),
                          (0,255,120),-1)
            cv2.rectangle(frame,(20,h-18),(w-20,h-8),
                          (60,60,60),1)

            if not hand_ok:
                cv2.putText(frame, "NO HAND",
                    (w//2-60,h//2),
                    cv2.FONT_HERSHEY_SIMPLEX,1,(0,0,255),2)

            cv2.imshow("VoxGest FSL Alphabet", frame)
            key = cv2.waitKey(1) & 0xFF

            if key == ord(' ') and hand_ok and state == "WAIT":
                state     = "RECORD"
                frame_buf = []
            elif key == ord('n'):
                print(f"  {letter}: {existing} sequences (skipped)")
                letter_idx += 1
                state       = "WAIT"
                frame_buf   = []
            elif key == ord('q'):
                break

    cap.release()
    cv2.destroyAllWindows()
    print("Alphabet collection complete.")

if __name__ == "__main__":
    print("FSL Alphabet Sequence Collector")
    print("Hold each FSL handshape steady")
    print("SPACE=record  N=skip  Q=quit\n")
    collect()