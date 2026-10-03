"""LEGACY/INCOMPATIBLE 30-frame, hand-only FSL-105 extractor.

Retained for provenance. Do not use it for OneHand162 training; the active
versioned extractor is ``scripts_ml/76_extract_fsl105_features.py``.
"""

import os
os.environ['PROTOCOL_BUFFERS_PYTHON_IMPLEMENTATION'] = 'python'
os.environ['TF_CPP_MIN_LOG_LEVEL'] = '3'

import cv2
import numpy as np
import mediapipe as mp
import csv

# ── Paths ────────────────────────────────────────────────────
FSL_ROOT   = r"D:\BSIT 3RD YEAR\New VovGest\FSL-105 A dataset for recognizing 105 Filipino sign language videos\FSL-105 A dataset for recognizing 105 Filipino sign language videos"
CLIPS_DIR  = os.path.join(FSL_ROOT, "clips", "clips")
LABELS_CSV = os.path.join(FSL_ROOT, "labels.csv")
SAVE_DIR   = r"D:\BSIT 3RD YEAR\New VovGest\dataset_fsl"
FRAMES_PER_VIDEO = 30
# ─────────────────────────────────────────────────────────────

mp_hands = mp.solutions.hands

def normalize_hand(landmarks):
    lm    = np.array([[p.x, p.y, p.z] for p in landmarks.landmark])
    wrist = lm[0].copy()
    lm   -= wrist
    scale = np.linalg.norm(lm[9])
    if scale > 0:
        lm /= scale
    return lm.flatten().astype(np.float32)  # 63 floats

def extract_from_video(video_path, existing_count):
    cap   = cv2.VideoCapture(video_path)
    total = int(cap.get(cv2.CAP_PROP_FRAME_COUNT))
    if total == 0:
        cap.release()
        return []

    step      = max(1, total // FRAMES_PER_VIDEO)
    extracted = []

    with mp_hands.Hands(
        static_image_mode=True,
        max_num_hands=1,
        min_detection_confidence=0.5
    ) as hands:
        frame_idx = 0
        while len(extracted) < FRAMES_PER_VIDEO:
            cap.set(cv2.CAP_PROP_POS_FRAMES, frame_idx)
            ret, frame = cap.read()
            if not ret:
                break
            rgb     = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
            results = hands.process(rgb)
            if results.multi_hand_landmarks:
                coords = normalize_hand(results.multi_hand_landmarks[0])
                extracted.append(coords)
            frame_idx += step

    cap.release()
    return extracted

def sanitize_label(label):
    """Convert label to safe folder name."""
    return label.strip().upper().replace(" ", "_").replace("'", "").replace("/", "_")

def load_labels():
    label_map = {}
    with open(LABELS_CSV, newline='', encoding='utf-8') as f:
        reader = csv.DictReader(f)
        for row in reader:
            label_map[int(row['id'])] = {
                'label':    row['label'].strip(),
                'category': row['category'].strip(),
                'folder':   sanitize_label(row['label'])
            }
    return label_map

def run():
    os.makedirs(SAVE_DIR, exist_ok=True)
    label_map = load_labels()

    print("=" * 60)
    print("  FSL-105 Landmark Extractor")
    print(f"  {len(label_map)} signs to process")
    print("=" * 60)

    summary = {}

    for class_id in sorted(label_map.keys()):
        info      = label_map[class_id]
        word_name = info['folder']
        clip_dir  = os.path.join(CLIPS_DIR, str(class_id))

        if not os.path.exists(clip_dir):
            print(f"  [{class_id}] {word_name}: folder missing, skipping")
            continue

        save_path = os.path.join(SAVE_DIR, word_name)
        os.makedirs(save_path, exist_ok=True)

        existing = len([f for f in os.listdir(save_path)
                       if f.endswith('.npy')])
        total_saved = existing

        videos = [f for f in os.listdir(clip_dir)
                  if f.lower().endswith(('.mp4', '.avi', '.mov', '.mkv'))]

        print(f"\n[{class_id:03d}] {info['label']} ({info['category']})")
        print(f"       {len(videos)} videos found", end="")

        for vid_file in videos:
            vid_path = os.path.join(clip_dir, vid_file)
            samples  = extract_from_video(vid_path, total_saved)

            for i, coords in enumerate(samples):
                np.save(
                    os.path.join(save_path,
                                 f"sample_{total_saved:04d}.npy"),
                    coords
                )
                total_saved += 1

        summary[word_name] = total_saved
        status = "✓" if total_saved >= 50 else "⚠ LOW"
        print(f" → {total_saved} samples saved {status}")

    # ── Final summary ────────────────────────────────────────
    print("\n" + "=" * 60)
    print("  EXTRACTION COMPLETE")
    print("=" * 60)
    low_count = 0
    for word, count in summary.items():
        status = "✓" if count >= 50 else "⚠ LOW"
        if count < 50:
            low_count += 1
        print(f"  {status}  {word}: {count} samples")

    print(f"\n  Total signs: {len(summary)}")
    print(f"  Low sample signs: {low_count}")
    print(f"  Output: {SAVE_DIR}")
    print("\nNext step: run retrain_fsl.py")

if __name__ == "__main__":
    run()
