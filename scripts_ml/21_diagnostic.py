import json
import os

import cv2
import mediapipe as mp
import numpy as np
import tensorflow as tf

from lstm_features import normalize_static_hand

os.environ['TF_CPP_MIN_LOG_LEVEL'] = '3'

STATIC_MODEL  = "model/voxgest_v3.h5"
STATIC_LABELS = "model/class_labels_v3.json"

print("Loading model...")
static_model = tf.keras.models.load_model(STATIC_MODEL)

# Bulletproof JSON loader to prevent backward dictionaries
with open(STATIC_LABELS) as f:
    raw = json.load(f)
    if all(isinstance(k, str) and k.isdigit() for k in raw.keys()):
        static_labels = {int(k): v for k, v in raw.items()}
    else:
        static_labels = {int(v): k for k, v in raw.items()}

mp_hands = mp.solutions.hands.Hands(max_num_hands=1)
cap = cv2.VideoCapture(0)

while cap.isOpened():
    ret, frame = cap.read()
    if not ret: break
    frame = cv2.flip(frame, 1)

    rgb = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
    res = mp_hands.process(rgb)

    if res.multi_hand_landmarks:
        lm = res.multi_hand_landmarks[0].landmark
        vec = normalize_static_hand(lm)

        # Raw prediction
        pred = static_model.predict(vec[np.newaxis, :], verbose=0)[0]
        idx  = int(np.argmax(pred))
        conf = float(pred[idx])
        lbl  = static_labels.get(idx, "UNKNOWN_CLASS")
        top3 = np.argsort(pred)[-3:][::-1]
        top3_text = "  ".join(
            f"{static_labels.get(int(i), '?')}:{float(pred[i]):.0%}" for i in top3
        )

        # Paint it bright green on the screen
        cv2.putText(frame, f"RAW AI PRED: {lbl} ({conf:.0%})", (20, 50),
                    cv2.FONT_HERSHEY_SIMPLEX, 1, (0, 255, 0), 3)
        cv2.putText(frame, f"TOP 3: {top3_text}", (20, 88),
                    cv2.FONT_HERSHEY_SIMPLEX, 0.65, (220, 220, 220), 2)

    cv2.imshow("VoxGest X-Ray", frame)
    if cv2.waitKey(1) == ord('q'): break

cap.release()
cv2.destroyAllWindows()
