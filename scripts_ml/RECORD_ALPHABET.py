"""LEGACY/INCOMPATIBLE alphabet recorder retained for provenance only.

Do not use this script for new training data. It does not reliably select the
fixed anatomical hand and does not emit canonical feature-version metadata.
Use ``scripts_ml/collect_fsl_alphabet_seq.py`` instead.
"""

import subprocess, sys, getpass

# Auto-install dependencies
REQUIRED = ["mediapipe==0.10.14", "opencv-python", "numpy"]
for pkg in REQUIRED:
    subprocess.check_call([sys.executable, "-m", "pip", 
                          "install", pkg, "-q"])

import os, cv2, numpy as np, socket

# ── Where to save ─────────────────────────────────────────────
# CHANGE THIS to Jhon's shared folder path or USB drive
# Example USB:  r"E:\fsl_features"
# Example LAN:  r"\\JHONS-PC\shared\fsl_features"
SAVE_BASE  = r"D:\BSIT 3RD YEAR\New VovGest\external_datasets\fsl_features"

SIGNER_ID = getpass.getuser()  # auto-tags files by PC name
SEQ_TARGET = 200
SEQ_LEN    = 20
MIN_HAND   = 13   # minimum frames with hand detected per sequence
LETTERS    = list("ABCDEFGHIJKLMNOPQRSTUVWXYZ")
Z_DAMP     = 0.3
MIN_SCALE  = 0.001
# ──────────────────────────────────────────────────────────────

import mediapipe as mp
mp_hands = mp.solutions.hands
mp_pose  = mp.solutions.pose
mp_draw  = mp.solutions.drawing_utils


def landmarks_to_array(lm_obj, count):
    if lm_obj is None:
        return None
    return np.array([[p.x, p.y, p.z] 
                     for p in lm_obj.landmark[:count]], 
                    dtype=np.float32)


def build_frame_features(results_h, results_p):
    """
    Exact contract matching Terra's 76_extract_fsl105_features.py.
    Output: 162 floats = [99 pose nose-relative] + [63 right-hand]
    """
    pose_arr  = landmarks_to_array(
        results_p.pose_landmarks, 33)
    right_arr = landmarks_to_array(
        results_h.multi_hand_landmarks[0], 21) \
        if results_h.multi_hand_landmarks else None

    if pose_arr is None:
        return np.zeros(162, dtype=np.float32), False

    nose          = pose_arr[0].copy()
    pose_vals     = pose_arr - nose[np.newaxis, :]
    pose_vals[0]  = 0.0   # nose itself becomes zero

    hand_present = right_arr is not None
    if right_arr is None:
        hand_vals = np.zeros((21, 3), dtype=np.float32)
    else:
        hand_vals = right_arr - nose[np.newaxis, :]
        scale = float(np.linalg.norm(
            hand_vals[0] - hand_vals[9]))
        if scale > MIN_SCALE:
            hand_vals = hand_vals / scale

    out = np.concatenate([
        pose_vals.reshape(-1),
        hand_vals.reshape(-1)
    ]).astype(np.float32)

    out[2::3] *= Z_DAMP   # Z damping
    assert out.shape == (162,)
    return out, hand_present


def count_existing(folder):
    if not os.path.exists(folder):
        return 0
    return len([f for f in os.listdir(folder) 
                if f.endswith('.npy')])


def run():
    print(f"\nVoxGest FSL Alphabet Recorder")
    print(f"Signer ID : {SIGNER_ID}")
    print(f"Save path : {SAVE_BASE}")
    print(f"Target    : {SEQ_TARGET} sequences per letter")
    print(f"\nControls  : SPACE=start  N=next  Q=quit\n")

    letter_idx = 0
    state      = "WAIT"
    frame_buf  = []
    hand_count = 0

    cap = cv2.VideoCapture(0)
    if not cap.isOpened():
        print("ERROR: No webcam found.")
        return

    with mp_hands.Hands(
        static_image_mode=False,
        max_num_hands=2,
        min_detection_confidence=0.6,
        min_tracking_confidence=0.5
    ) as hands, mp_pose.Pose(
        min_detection_confidence=0.5,
        min_tracking_confidence=0.5,
        model_complexity=1
    ) as pose:

        while cap.isOpened() and letter_idx < 26:
            letter   = LETTERS[letter_idx]
            out_dir  = os.path.join(SAVE_BASE, letter)
            os.makedirs(out_dir, exist_ok=True)
            existing = count_existing(out_dir)

            if existing >= SEQ_TARGET:
                print(f"  {letter}: already {existing} seqs, skipping")
                letter_idx += 1
                state = "WAIT"
                continue

            ret, frame = cap.read()
            if not ret:
                break

            frame    = cv2.flip(frame, 1)
            h, w     = frame.shape[:2]
            rgb      = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
            res_h    = hands.process(rgb)
            res_p    = pose.process(rgb)

            right_ok = (res_h.multi_hand_landmarks is not None)

            # Draw hand landmarks
            if res_h.multi_hand_landmarks:
                for hl in res_h.multi_hand_landmarks:
                    mp_draw.draw_landmarks(
                        frame, hl, mp_hands.HAND_CONNECTIONS)

            # State machine
            if state == "RECORD":
                feats, hp = build_frame_features(res_h, res_p)
                frame_buf.append(feats)
                if hp:
                    hand_count += 1

                progress = len(frame_buf)

                if progress == SEQ_LEN:
                    if hand_count >= MIN_HAND:
                        seq  = np.stack(frame_buf)  # [20, 162]
                        name = f"{SIGNER_ID}_{existing:04d}.npy"
                        np.save(os.path.join(out_dir, name), seq)
                        existing += 1
                    # reset for next sequence
                    frame_buf  = []
                    hand_count = 0
                    state = "WAIT"

                    if existing >= SEQ_TARGET:
                        print(f"  ✓ {letter}: {existing} sequences done")
                        letter_idx += 1

            # ── UI ────────────────────────────────────────────
            col = (0,255,120) if right_ok else (0,80,255)
            overlay = frame.copy()
            cv2.rectangle(overlay,(0,0),(w,120),(8,8,8),-1)
            cv2.addWeighted(overlay,0.75,frame,0.25,0,frame)

            cv2.putText(frame, f"{letter}",
                (20,80), cv2.FONT_HERSHEY_SIMPLEX,
                3.0, col, 5)
            cv2.putText(frame,
                f"Saved: {existing}/{SEQ_TARGET}",
                (160,50), cv2.FONT_HERSHEY_SIMPLEX,
                0.8, (255,255,255), 2)
            cv2.putText(frame, f"Signer: {SIGNER_ID}",
                (160,80), cv2.FONT_HERSHEY_SIMPLEX,
                0.6, (180,180,180), 1)

            if state == "WAIT":
                msg = "Show FSL handshape → SPACE"
                cv2.putText(frame, msg,
                    (20,112), cv2.FONT_HERSHEY_SIMPLEX,
                    0.55, (0,200,255), 1)
            else:
                pct = int((len(frame_buf)/SEQ_LEN)*100)
                msg = f"Recording {pct}%"
                cv2.putText(frame, msg,
                    (20,112), cv2.FONT_HERSHEY_SIMPLEX,
                    0.55, (0,255,0), 2)

            # Progress bar
            bar = int((existing/SEQ_TARGET)*(w-40))
            cv2.rectangle(frame,(20,h-20),(20+bar,h-8),
                         (0,255,120),-1)
            cv2.rectangle(frame,(20,h-20),(w-20,h-8),
                         (60,60,60),1)

            if not right_ok:
                cv2.putText(frame,"NO RIGHT HAND",
                    (w//2-120,h//2),
                    cv2.FONT_HERSHEY_SIMPLEX,
                    1,(0,80,255),2)

            cv2.imshow("VoxGest FSL Alphabet Recorder", frame)
            key = cv2.waitKey(1) & 0xFF

            if key == ord(' ') and right_ok and state == "WAIT":
                state      = "RECORD"
                frame_buf  = []
                hand_count = 0
            elif key == ord('n'):
                print(f"  {letter}: {existing} seqs (skipped ahead)")
                letter_idx += 1
                state      = "WAIT"
                frame_buf  = []
            elif key == ord('q'):
                break

    cap.release()
    cv2.destroyAllWindows()
    print("\nRecording session complete.")
    print(f"Files saved to: {SAVE_BASE}")


if __name__ == "__main__":
    run()
