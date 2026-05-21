"""
VoxGest live recognizer.

Static model:  63 floats -> alphabet/control signs.
Motion model:  30 x 162 floats -> whole-word motion signs.

Key fixes:
  - Word frames are collected from holistic pose every frame, not only when the
    hand model sees a hand.
  - Word confidence is no longer compared against alphabet confidence. Those
    scores come from different models and are not comparable.
  - AUTO mode protects stable alphabet signs. A word can override a detected
    letter only when the motion signal is clearly intentional.
  - AUTO / LETTERS / WORDS modes let you test and defend the two recognizers
    cleanly without changing code.
"""

import json
import os
import time
from collections import deque

import cv2
import mediapipe as mp
import numpy as np
import tensorflow as tf

from lstm_features import (
    FEAT_SIZE,
    SEQ_LEN,
    configured_feature_profile,
    configured_hand_preference,
    configured_mirror_input,
    extract_frame_features,
    hand_mapping_text,
    normalize_static_hand,
    sequence_hand_presence_ratio,
    sequence_motion_energy,
    sequence_wrist_path,
    select_hand_landmarks,
    single_hand_pose_enabled,
    top_prediction,
)
from token_composer import TokenComposer
from phrase_builder import PhraseBuilder
from word_config import NEGATIVE_WORDS, TARGET_WORDS, TRAINING_WORDS, WORD_PROFILE
from motion_letter_config import MOTION_LETTER_OUTPUTS

try:
    import pyttsx3
except ImportError:
    pyttsx3 = None

try:
    from gesture_segmenter import GestureSegmenter
    from phrase_config import is_phrase_intent, is_phrase_noop, phrase_output_text
except ImportError:
    GestureSegmenter = None
    is_phrase_intent = lambda label: False
    is_phrase_noop = lambda label: False
    phrase_output_text = lambda label: label

os.environ["PROTOCOL_BUFFERS_PYTHON_IMPLEMENTATION"] = "python"
os.environ["TF_CPP_MIN_LOG_LEVEL"] = "3"

STATIC_MODEL = os.environ.get("VOXGEST_STATIC_MODEL", "model/voxgest_v3.h5")
STATIC_LABELS = os.environ.get("VOXGEST_STATIC_LABELS", "model/class_labels_v3.json")


def artifact_suffix():
    safe_word = "".join(ch if ch.isalnum() or ch in {"_", "-"} else "_" for ch in WORD_PROFILE)
    feature_profile = configured_feature_profile()
    if feature_profile != "onehand162":
        if safe_word.startswith("fullsign225_") or safe_word.endswith(f"_{feature_profile}"):
            return safe_word
        return f"{safe_word}_{feature_profile}"
    if WORD_PROFILE == "demo10":
        return "v1"
    return safe_word


ARTIFACT_SUFFIX = artifact_suffix()
LSTM_MODEL = os.environ.get("VOXGEST_LSTM_MODEL", f"model/voxgest_lstm_{ARTIFACT_SUFFIX}.h5")
LSTM_LABELS = os.environ.get("VOXGEST_LSTM_LABELS", f"model/class_labels_lstm_{ARTIFACT_SUFFIX}.json")
LSTM_REPORT = os.environ.get(
    "VOXGEST_LSTM_REPORT",
    "model/lstm_training_report.json"
    if WORD_PROFILE == "demo10"
    else f"model/lstm_training_report_{ARTIFACT_SUFFIX}.json",
)
TCN_MODEL = os.environ.get("VOXGEST_TCN_MODEL", f"model/voxgest_tcn_{ARTIFACT_SUFFIX}.h5")
TCN_LABELS = os.environ.get("VOXGEST_TCN_LABELS", f"model/class_labels_tcn_{ARTIFACT_SUFFIX}.json")
TCN_REPORT = os.environ.get(
    "VOXGEST_TCN_REPORT",
    "model/tcn_training_report.json"
    if WORD_PROFILE == "demo10"
    else f"model/tcn_training_report_{ARTIFACT_SUFFIX}.json",
)
PHRASE_MODEL = "model/voxgest_phrase_tcn_v1.h5"
PHRASE_LABELS = "model/class_labels_phrase_tcn_v1.json"
MOTION_LETTER_MODEL = os.environ.get(
    "VOXGEST_MOTION_LETTER_MODEL",
    "model/voxgest_motion_letters_tcn_v1.h5",
)
MOTION_LETTER_LABELS = os.environ.get(
    "VOXGEST_MOTION_LETTER_LABELS",
    "model/class_labels_motion_letters_tcn_v1.json",
)
DYNAMIC_MODEL_KIND = os.environ.get("VOXGEST_DYNAMIC_MODEL", "auto").strip().lower()
ENABLE_PHRASE = os.environ.get("VOXGEST_ENABLE_PHRASE", "0").strip().lower() in {
    "1",
    "true",
    "yes",
    "on",
}
ALLOW_EXTRA_WORD_LABELS = os.environ.get("VOXGEST_ALLOW_EXTRA_WORD_LABELS", "0").strip().lower() in {
    "1",
    "true",
    "yes",
    "on",
}

STATIC_THRESHOLD = float(os.environ.get("VOXGEST_STATIC_THRESHOLD", "0.55"))
LSTM_THRESHOLD = float(os.environ.get("VOXGEST_LSTM_THRESHOLD", "0.75"))
LSTM_MARGIN = float(os.environ.get("VOXGEST_LSTM_MARGIN", "0.20"))
MIN_WORD_MOTION = float(os.environ.get("VOXGEST_MIN_WORD_MOTION", "0.05"))
MIN_WORD_WRIST_PATH = float(os.environ.get("VOXGEST_MIN_WORD_WRIST_PATH", "0.50"))
MIN_WORD_HAND_PRESENCE = float(os.environ.get("VOXGEST_MIN_WORD_HAND_PRESENCE", "0.30"))

WORDS_MODE_THRESHOLD = float(os.environ.get("VOXGEST_WORDS_MODE_THRESHOLD", "0.65"))
WORDS_MODE_MARGIN = float(os.environ.get("VOXGEST_WORDS_MODE_MARGIN", "0.12"))
WORDS_MODE_MIN_MOTION = float(os.environ.get("VOXGEST_WORDS_MODE_MIN_MOTION", "0.03"))
WORDS_MODE_MIN_WRIST_PATH = float(os.environ.get("VOXGEST_WORDS_MODE_MIN_WRIST_PATH", "0.35"))
WORDS_MODE_MIN_HAND_PRESENCE = float(os.environ.get("VOXGEST_WORDS_MODE_MIN_HAND_PRESENCE", "0.25"))

MOTION_LETTER_THRESHOLD = float(os.environ.get("VOXGEST_MOTION_LETTER_THRESHOLD", "0.78"))
MOTION_LETTER_MARGIN = float(os.environ.get("VOXGEST_MOTION_LETTER_MARGIN", "0.18"))
MOTION_LETTER_MIN_MOTION = float(os.environ.get("VOXGEST_MOTION_LETTER_MIN_MOTION", "0.012"))
MOTION_LETTER_MIN_WRIST_PATH = float(os.environ.get("VOXGEST_MOTION_LETTER_MIN_WRIST_PATH", "0.08"))
MOTION_LETTER_MIN_HAND_PRESENCE = float(os.environ.get("VOXGEST_MOTION_LETTER_MIN_HAND_PRESENCE", "0.35"))
MOTION_LETTER_STABLE_FRAMES = int(os.environ.get("VOXGEST_MOTION_LETTER_STABLE_FRAMES", "6"))

WORDS_MODE_WORD_RULES = {
    # These signs can be compact in live webcam use, so wrist travel is not a
    # reliable global gate for them.
    "YES": {"conf": 0.56, "margin": 0.07, "motion": 0.008, "path": 0.05, "stable": 5},
    "NO": {"conf": 0.55, "margin": 0.06, "motion": 0.008, "path": 0.04, "stable": 5},
    "WATER": {"conf": 0.56, "margin": 0.06, "motion": 0.006, "path": 0.04, "stable": 5},
    # HELLO and PLEASE are the pair that tends to swap, so require a cleaner
    # separation between first and second place.
    "PLEASE": {"conf": 0.68, "margin": 0.18, "motion": 0.025, "path": 0.25},
    "HELLO": {"conf": 0.70, "margin": 0.18, "motion": 0.030, "path": 0.30},
    # Initial tuning for the added five words. These are intentionally less
    # strict than the global defaults because the new classes were not part of
    # the original hand-tuned demo5 gate.
    "HELP": {"conf": 0.60, "margin": 0.10, "motion": 0.025, "path": 0.20},
    "STOP": {"conf": 0.62, "margin": 0.10, "motion": 0.025, "path": 0.20},
    "DOCTOR": {"conf": 0.70, "margin": 0.15, "motion": 0.020, "path": 0.20},
    "NAME": {"conf": 0.55, "margin": 0.05, "motion": 0.020, "path": 0.20},
    "THANKYOU": {"conf": 0.52, "margin": 0.03, "motion": 0.010, "path": 0.08, "stable": 6},
    "NOTHING": {"conf": 0.55, "margin": 0.05, "motion": 0.000, "path": 0.00, "presence": 0.00, "stable": 4},
}

PHRASE_THRESHOLD = float(os.environ.get("VOXGEST_PHRASE_THRESHOLD", "0.78"))
PHRASE_MARGIN = float(os.environ.get("VOXGEST_PHRASE_MARGIN", "0.18"))
PHRASE_COOLDOWN_S = float(os.environ.get("VOXGEST_PHRASE_COOLDOWN_SECONDS", "1.75"))

AUTO_WORD_OVERRIDE_THRESHOLD = float(os.environ.get("VOXGEST_AUTO_WORD_OVERRIDE_THRESHOLD", "0.88"))
AUTO_WORD_OVERRIDE_MARGIN = float(os.environ.get("VOXGEST_AUTO_WORD_OVERRIDE_MARGIN", "0.28"))
AUTO_WORD_OVERRIDE_MOTION = float(os.environ.get("VOXGEST_AUTO_WORD_OVERRIDE_MOTION", "0.07"))
AUTO_WORD_OVERRIDE_WRIST_PATH = float(os.environ.get("VOXGEST_AUTO_WORD_OVERRIDE_WRIST_PATH", "0.80"))

LETTER_STABLE_FRAMES = 18
WORD_STABLE_FRAMES = int(os.environ.get("VOXGEST_WORD_STABLE_FRAMES", "10"))
WORDS_MODE_STABLE_FRAMES = int(os.environ.get("VOXGEST_WORDS_MODE_STABLE_FRAMES", "8"))
COOLDOWN_S = 1.25
SMOOTH_WIN = 5
NO_POSE_RESET_FRAMES = 12
LSTM_EVERY_N_FRAMES = int(os.environ.get("VOXGEST_DYNAMIC_EVERY_N_FRAMES", "3"))
N_LETTERS = 29
HAND_PREFERENCE = configured_hand_preference()
MIRROR_INPUT = True
ACTIVE_WORD_LABELS = set(TARGET_WORDS) - set(NEGATIVE_WORDS)
ACTIVE_DYNAMIC_LABELS = set(TRAINING_WORDS)

MODES = ("AUTO", "LETTERS", "WORDS")
DEFAULT_MODE = os.environ.get("VOXGEST_MODE", "AUTO").strip().upper()
if DEFAULT_MODE not in MODES:
    DEFAULT_MODE = "AUTO"


def load_label_maps(path):
    with open(path, "r", encoding="utf-8") as f:
        raw = json.load(f)
    if all(str(k).lstrip("-").isdigit() for k in raw.keys()):
        idx_to_label = {int(k): v for k, v in raw.items()}
        label_to_idx = {v: int(k) for k, v in raw.items()}
    else:
        label_to_idx = {k: int(v) for k, v in raw.items()}
        idx_to_label = {int(v): k for k, v in raw.items()}
    return label_to_idx, idx_to_label


def dynamic_model_candidates():
    def report_score(path):
        try:
            with open(path, "r", encoding="utf-8") as f:
                return float(json.load(f).get("best_grouped_val_accuracy", -1.0))
        except Exception:
            return -1.0

    options = {
        "tcn": [("TCN", TCN_MODEL, TCN_LABELS, TCN_REPORT)],
        "lstm": [("LSTM", LSTM_MODEL, LSTM_LABELS, LSTM_REPORT)],
    }
    if DYNAMIC_MODEL_KIND in options:
        candidates = options[DYNAMIC_MODEL_KIND]
    else:
        candidates = sorted(
            options["tcn"] + options["lstm"],
            key=lambda item: report_score(item[3]),
            reverse=True,
        )
    return [(name, model, labels) for name, model, labels, _ in candidates]


def is_word(label, lstm_label_to_idx):
    return label in ACTIVE_WORD_LABELS and label in lstm_label_to_idx


def is_noop_label(label):
    return label.lower() in {"nothing", "idle", "rest", "no_word", "none"}


def label_contract_matches(label_to_idx):
    labels = {str(label).upper() for label in label_to_idx}
    missing = sorted(ACTIVE_DYNAMIC_LABELS - labels)
    extra = sorted(labels - ACTIVE_DYNAMIC_LABELS)
    if missing:
        return False, f"missing active labels: {missing}"
    if extra and not ALLOW_EXTRA_WORD_LABELS:
        return False, f"extra labels outside hardening contract: {extra}"
    return True, ""


def init_speaker():
    if pyttsx3 is None:
        return None
    try:
        engine = pyttsx3.init()
        engine.setProperty("rate", 165)
        return engine
    except Exception:
        return None


def word_can_override_static(word_candidate, static_candidate, word_state):
    """In AUTO, do not let shaky word guesses steal steady alphabet signs."""
    if not word_candidate[0]:
        return False
    if not static_candidate[0]:
        return True

    _, word_conf, _, word_margin, word_motion = word_candidate
    word_path = word_state[4] if len(word_state) == 6 else 0.0
    return (
        word_conf >= AUTO_WORD_OVERRIDE_THRESHOLD
        and word_margin >= AUTO_WORD_OVERRIDE_MARGIN
        and word_motion >= AUTO_WORD_OVERRIDE_MOTION
        and word_path >= AUTO_WORD_OVERRIDE_WRIST_PATH
    )


def word_thresholds(label, mode):
    if mode == "WORDS":
        rule = WORDS_MODE_WORD_RULES.get(label, {})
        min_conf = rule.get("conf", WORDS_MODE_THRESHOLD)
        min_margin = rule.get("margin", WORDS_MODE_MARGIN)
        min_motion = rule.get("motion", WORDS_MODE_MIN_MOTION)
        min_path = rule.get("path", WORDS_MODE_MIN_WRIST_PATH)
        min_presence = rule.get("presence", WORDS_MODE_MIN_HAND_PRESENCE)
    else:
        min_conf = LSTM_THRESHOLD
        min_margin = LSTM_MARGIN
        min_motion = MIN_WORD_MOTION
        min_path = MIN_WORD_WRIST_PATH
        min_presence = MIN_WORD_HAND_PRESENCE

    return min_conf, min_margin, min_motion, min_path, min_presence


def stable_frames_for(label, mode):
    if label in MOTION_LETTER_OUTPUTS:
        return MOTION_LETTER_STABLE_FRAMES
    if mode == "WORDS":
        rule = WORDS_MODE_WORD_RULES.get(label, {})
        return int(rule.get("stable", WORDS_MODE_STABLE_FRAMES))
    return WORD_STABLE_FRAMES if is_word(label, lstm_label_to_idx) else LETTER_STABLE_FRAMES


def word_accepts(label, conf, margin, motion, wrist_path, hand_presence, mode):
    if not label:
        return False

    min_conf, min_margin, min_motion, min_path, min_presence = word_thresholds(label, mode)
    return (
        conf >= min_conf
        and margin >= min_margin
        and motion >= min_motion
        and wrist_path >= min_path
        and hand_presence >= min_presence
    )


def motion_letter_accepts(label, conf, margin, motion, wrist_path, hand_presence):
    return (
        label in MOTION_LETTER_OUTPUTS
        and conf >= MOTION_LETTER_THRESHOLD
        and margin >= MOTION_LETTER_MARGIN
        and motion >= MOTION_LETTER_MIN_MOTION
        and wrist_path >= MOTION_LETTER_MIN_WRIST_PATH
        and hand_presence >= MOTION_LETTER_MIN_HAND_PRESENCE
    )


print("Loading models...")
static_model = tf.keras.models.load_model(STATIC_MODEL)
static_label_to_idx, static_idx_to_label = load_label_maps(STATIC_LABELS)
print(f"  Static classes: {len(static_idx_to_label)}")

has_lstm = False
lstm_model = None
lstm_label_to_idx = {}
lstm_idx_to_label = {}
dynamic_model_name = ""
dynamic_model_path = ""
dynamic_last_error = None
for candidate_name, candidate_model, candidate_labels in dynamic_model_candidates():
    if not os.path.exists(candidate_model) or not os.path.exists(candidate_labels):
        dynamic_last_error = f"missing {candidate_model} or {candidate_labels}"
        continue
    try:
        lstm_model = tf.keras.models.load_model(candidate_model)
        lstm_label_to_idx, lstm_idx_to_label = load_label_maps(candidate_labels)
        contract_ok, contract_reason = label_contract_matches(lstm_label_to_idx)
        if not contract_ok:
            dynamic_last_error = (
                f"{candidate_name} skipped for Recognition Hardening v1: "
                f"{contract_reason}. Set VOXGEST_ALLOW_EXTRA_WORD_LABELS='1' "
                "only for legacy debugging."
            )
            lstm_model = None
            lstm_label_to_idx = {}
            lstm_idx_to_label = {}
            print(f"  {dynamic_last_error}")
            continue
        has_lstm = True
        dynamic_model_name = candidate_name
        dynamic_model_path = candidate_model
        print(f"  {dynamic_model_name} words: {sorted(lstm_label_to_idx.keys())}")
        if "NOTHING" not in lstm_label_to_idx:
            print(
                f"  NOTE: {dynamic_model_name} has no NOTHING class; "
                "idle/transition control will be weaker."
            )
        break
    except Exception as exc:
        dynamic_last_error = exc

if not has_lstm:
    print(f"  Motion model not loaded: {dynamic_last_error}")

has_motion_letters = False
motion_letter_model = None
motion_letter_label_to_idx = {}
motion_letter_idx_to_label = {}
motion_letter_last_error = None
if os.path.exists(MOTION_LETTER_MODEL) and os.path.exists(MOTION_LETTER_LABELS):
    try:
        motion_letter_model = tf.keras.models.load_model(MOTION_LETTER_MODEL)
        motion_letter_label_to_idx, motion_letter_idx_to_label = load_label_maps(MOTION_LETTER_LABELS)
        required_motion_letters = set(MOTION_LETTER_OUTPUTS) | {"NOTHING"}
        missing_motion_letters = sorted(required_motion_letters - set(motion_letter_label_to_idx))
        if missing_motion_letters:
            motion_letter_last_error = f"missing labels: {missing_motion_letters}"
        else:
            has_motion_letters = True
            print(f"  Motion letters: {sorted(motion_letter_label_to_idx.keys())}")
    except Exception as exc:
        motion_letter_last_error = exc
if not has_motion_letters:
    print(
        "  Motion-letter model not loaded: record/train J/Z with "
        "scripts_ml/34_record_motion_letters.py and 35_train_motion_letter_tcn.py."
    )
    if motion_letter_last_error:
        print(f"    Reason: {motion_letter_last_error}")

has_phrase = False
phrase_model = None
phrase_label_to_idx = {}
phrase_idx_to_label = {}
phrase_last_error = None
if not ENABLE_PHRASE:
    print("  Phrase model disabled by default (VOXGEST_ENABLE_PHRASE='0').")
elif GestureSegmenter is None:
    print("  Phrase model disabled: phrase modules are unavailable.")
elif os.path.exists(PHRASE_MODEL) and os.path.exists(PHRASE_LABELS):
    try:
        phrase_model = tf.keras.models.load_model(PHRASE_MODEL)
        phrase_label_to_idx, phrase_idx_to_label = load_label_maps(PHRASE_LABELS)
        has_phrase = True
        print(f"  Phrase intents: {sorted(phrase_label_to_idx.keys())}")
    except Exception as exc:
        phrase_last_error = exc
        print(f"  Phrase model not loaded: {phrase_last_error}")
else:
    print("  Phrase model not loaded: train model/voxgest_phrase_tcn_v1.h5 when ready.")

mp_holistic = mp.solutions.holistic
mp_draw = mp.solutions.drawing_utils
mp_styles = mp.solutions.drawing_styles

frame_window = deque(maxlen=SEQ_LEN)
phrase_segmenter = GestureSegmenter() if ENABLE_PHRASE and GestureSegmenter is not None else None
pred_buffer = deque(maxlen=SMOOTH_WIN)
composer = TokenComposer(ACTIVE_WORD_LABELS)
phrase_builder = PhraseBuilder()
speaker = init_speaker()
last_accepted_token = ""
last_phrase_output = ""

mode_idx = MODES.index(DEFAULT_MODE)
current_label = ""
stable = 0
last_confirmed_t = 0.0
last_phrase_t = 0.0
frame_count = 0
no_pose_frames = 0

display_label = ""
display_conf = 0.0
display_src = ""
display_margin = 0.0
display_motion = 0.0
display_path = 0.0
display_presence = 0.0
last_word = ("", 0.0, 0.0, 0.0, 0.0, 0.0)
last_motion_letter = ("", 0.0, 0.0, 0.0, 0.0, 0.0)
last_phrase = ("", 0.0, 0.0)

print("\nVoxGest live")
print("  M=mode  SPACE=word boundary  C=clear  S=speak  Q=quit")
print(f"  Modes: AUTO, LETTERS, WORDS  start={MODES[mode_idx]}")
print(f"  Dominant hand policy: {HAND_PREFERENCE}")
print(f"  Hand mapping: {hand_mapping_text(HAND_PREFERENCE, MIRROR_INPUT)}")
print(f"  Pose policy: {'single-hand' if single_hand_pose_enabled() else 'full-pose'}")
print(f"  Mirror input: {configured_mirror_input(MIRROR_INPUT)}")
print(f"  Active word contract: {sorted(ACTIVE_DYNAMIC_LABELS)}")
if has_lstm:
    print(f"  Motion model: {dynamic_model_name} ({dynamic_model_path})")
if has_motion_letters:
    print(f"  Motion-letter model: {MOTION_LETTER_MODEL}")
if has_phrase:
    print(f"  Phrase model: {PHRASE_MODEL}")
else:
    print("  Phrase recognition: off for Recognition Hardening v1")
if speaker is None:
    print("  Speech output: unavailable or disabled; S prints the confirmed sentence.")
else:
    print("  Speech output: ready; S speaks only confirmed sentence text.")
print(
    "  AUTO protects letters; word override needs "
    f"conf>={AUTO_WORD_OVERRIDE_THRESHOLD:.0%}, "
    f"margin>={AUTO_WORD_OVERRIDE_MARGIN:.0%}, "
    f"path>={AUTO_WORD_OVERRIDE_WRIST_PATH:.2f}"
)
print("  WORDS mode uses lower word thresholds for diagnosis.\n")

cap = cv2.VideoCapture(0)
cap.set(cv2.CAP_PROP_FRAME_WIDTH, 640)
cap.set(cv2.CAP_PROP_FRAME_HEIGHT, 480)

with mp_holistic.Holistic(
    min_detection_confidence=0.45,
    min_tracking_confidence=0.40,
    model_complexity=1,
) as holistic:
    while cap.isOpened():
        ok, frame = cap.read()
        if not ok:
            break

        frame = cv2.flip(frame, 1)
        frame_count += 1
        height, width = frame.shape[:2]
        mode = MODES[mode_idx]
        dynamic_due = frame_count % LSTM_EVERY_N_FRAMES == 0

        rgb = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
        results = holistic.process(rgb)

        if results.pose_landmarks:
            mp_draw.draw_landmarks(
                frame,
                results.pose_landmarks,
                mp_holistic.POSE_CONNECTIONS,
                landmark_drawing_spec=mp_styles.get_default_pose_landmarks_style(),
            )

        for landmarks, color in (
            (results.right_hand_landmarks, (0, 200, 255)),
            (results.left_hand_landmarks, (255, 120, 0)),
        ):
            if landmarks:
                mp_draw.draw_landmarks(
                    frame,
                    landmarks,
                    mp_holistic.HAND_CONNECTIONS,
                    mp_draw.DrawingSpec(color, 2, 2),
                    mp_draw.DrawingSpec((255, 255, 255), 1, 1),
                )

        holistic_vec = extract_frame_features(results, mirrored_input=MIRROR_INPUT)
        if holistic_vec is not None:
            frame_window.append(holistic_vec)
            no_pose_frames = 0
            if has_phrase and phrase_segmenter is not None and mode != "LETTERS":
                phrase_segment = phrase_segmenter.update(holistic_vec)
                if phrase_segment is not None:
                    phrase_probs = phrase_model.predict(
                        phrase_segment[np.newaxis, ...],
                        verbose=0,
                    )[0]
                    phrase_idx, phrase_conf, phrase_margin = top_prediction(phrase_probs)
                    phrase_label = phrase_idx_to_label.get(phrase_idx, "?")
                    last_phrase = (phrase_label, phrase_conf, phrase_margin)
                    if (
                        phrase_conf >= PHRASE_THRESHOLD
                        and phrase_margin >= PHRASE_MARGIN
                        and time.time() - last_phrase_t >= PHRASE_COOLDOWN_S
                    ):
                        if is_phrase_intent(phrase_label):
                            output_text = phrase_output_text(phrase_label)
                            composer.tokens.append(output_text)
                            last_accepted_token = output_text
                            print(
                                f"PHRASE: {phrase_label} -> {output_text} "
                                f"conf={phrase_conf:.0%} margin={phrase_margin:.0%}"
                            )
                            last_phrase_t = time.time()
                            last_confirmed_t = last_phrase_t
                            pred_buffer.clear()
                            frame_window.clear()
                            current_label = ""
                            stable = 0
                        elif is_phrase_noop(phrase_label):
                            print(
                                f"PHRASE-NOOP: {phrase_label} "
                                f"conf={phrase_conf:.0%} margin={phrase_margin:.0%}"
                            )
        else:
            no_pose_frames += 1
            if no_pose_frames >= NO_POSE_RESET_FRAMES:
                frame_window.clear()
                pred_buffer.clear()
                if phrase_segmenter is not None:
                    phrase_segmenter.reset()
                display_label = ""
                display_conf = 0.0
                display_src = ""
                last_word = ("", 0.0, 0.0, 0.0, 0.0, 0.0)
                last_motion_letter = ("", 0.0, 0.0, 0.0, 0.0, 0.0)
                last_phrase = ("", 0.0, 0.0)

        hand_landmarks = select_hand_landmarks(results, mirrored_input=MIRROR_INPUT)
        static_candidate = ("", 0.0, "static")
        if hand_landmarks is not None and mode != "WORDS":
            static_vec = normalize_static_hand(hand_landmarks.landmark)
            static_probs = static_model.predict(static_vec[np.newaxis, :], verbose=0)[0]
            static_idx, static_conf, _ = top_prediction(static_probs)
            static_label = static_idx_to_label.get(static_idx, "?")
            if static_conf >= STATIC_THRESHOLD:
                static_candidate = (static_label, static_conf, "static")

        word_candidate = ("", 0.0, "motion", 0.0, 0.0)
        if has_lstm and len(frame_window) == SEQ_LEN and mode != "LETTERS":
            if dynamic_due:
                seq = np.array(list(frame_window), dtype=np.float32)
                probs = lstm_model.predict(seq[np.newaxis, ...], verbose=0)[0]
                word_idx, word_conf, word_margin = top_prediction(probs)
                word_label = lstm_idx_to_label.get(word_idx, "?")
                motion = sequence_motion_energy(seq)
                wrist_path = sequence_wrist_path(seq)
                hand_presence = sequence_hand_presence_ratio(seq)
                last_word = (
                    word_label,
                    word_conf,
                    word_margin,
                    motion,
                    wrist_path,
                    hand_presence,
                )
            word_label, word_conf, word_margin, motion, wrist_path, hand_presence = last_word
            if word_accepts(
                word_label,
                word_conf,
                word_margin,
                motion,
                wrist_path,
                hand_presence,
                mode,
            ) and word_label in ACTIVE_DYNAMIC_LABELS:
                word_candidate = (word_label, word_conf, "motion", word_margin, motion)

        motion_letter_candidate = ("", 0.0, "motion-letter", 0.0, 0.0)
        if has_motion_letters and len(frame_window) == SEQ_LEN and mode != "WORDS":
            if dynamic_due:
                seq = np.array(list(frame_window), dtype=np.float32)
                probs = motion_letter_model.predict(seq[np.newaxis, ...], verbose=0)[0]
                letter_idx, letter_conf, letter_margin = top_prediction(probs)
                letter_label = motion_letter_idx_to_label.get(letter_idx, "?")
                motion = sequence_motion_energy(seq)
                wrist_path = sequence_wrist_path(seq)
                hand_presence = sequence_hand_presence_ratio(seq)
                last_motion_letter = (
                    letter_label,
                    letter_conf,
                    letter_margin,
                    motion,
                    wrist_path,
                    hand_presence,
                )
            letter_label, letter_conf, letter_margin, motion, wrist_path, hand_presence = last_motion_letter
            if motion_letter_accepts(
                letter_label,
                letter_conf,
                letter_margin,
                motion,
                wrist_path,
                hand_presence,
            ):
                motion_letter_candidate = (
                    letter_label,
                    letter_conf,
                    "motion-letter",
                    letter_margin,
                    motion,
                )

        chosen_label = ""
        chosen_conf = 0.0
        chosen_src = ""
        chosen_margin = 0.0
        chosen_motion = 0.0
        chosen_path = 0.0
        chosen_presence = 0.0

        if mode == "WORDS":
            chosen_label, chosen_conf, chosen_src, chosen_margin, chosen_motion = word_candidate
        elif mode == "LETTERS":
            if motion_letter_candidate[0]:
                chosen_label, chosen_conf, chosen_src, chosen_margin, chosen_motion = motion_letter_candidate
            else:
                chosen_label, chosen_conf, chosen_src = static_candidate
        else:
            if motion_letter_candidate[0]:
                chosen_label, chosen_conf, chosen_src, chosen_margin, chosen_motion = motion_letter_candidate
            elif word_can_override_static(word_candidate, static_candidate, last_word):
                chosen_label, chosen_conf, chosen_src, chosen_margin, chosen_motion = word_candidate
            elif static_candidate[0]:
                chosen_label, chosen_conf, chosen_src = static_candidate
        if chosen_src == "motion" and len(last_word) == 6:
            chosen_path = last_word[4]
            chosen_presence = last_word[5]
        elif chosen_src == "motion-letter" and len(last_motion_letter) == 6:
            chosen_path = last_motion_letter[4]
            chosen_presence = last_motion_letter[5]

        pred_buffer.append(chosen_label if chosen_label else "?")
        if len(pred_buffer) >= SMOOTH_WIN:
            counts = {}
            for pred in pred_buffer:
                counts[pred] = counts.get(pred, 0) + 1
            best = max(counts, key=counts.get)
            if best != "?" and counts[best] / SMOOTH_WIN >= 0.60:
                display_label = best
                display_conf = chosen_conf
                display_src = chosen_src
                display_margin = chosen_margin
                display_motion = chosen_motion
                display_path = chosen_path
                display_presence = chosen_presence
            else:
                display_label = ""
                display_conf = 0.0
                display_src = ""
                display_margin = 0.0
                display_motion = 0.0
                display_path = 0.0
                display_presence = 0.0

        if display_label and display_conf >= 0.35:
            if display_label == current_label:
                stable += 1
            else:
                current_label = display_label
                stable = 1

            needed = stable_frames_for(current_label, mode)
            if stable >= needed and time.time() - last_confirmed_t >= COOLDOWN_S:
                result = composer.accept(current_label)
                if result.accepted:
                    last_accepted_token = result.token or current_label
                    phrase_result = phrase_builder.accept(current_label)
                    if phrase_result.finalized_text:
                        last_phrase_output = phrase_result.finalized_text
                        print(f"PHRASE-BUILDER: {last_phrase_output}")
                    elif phrase_result.action in {"append", "delete"}:
                        last_phrase_output = ""
                elif result.action == "noop":
                    last_accepted_token = ""

                if is_word(current_label, lstm_label_to_idx) and result.accepted:
                    print(
                        f"WORD: {current_label} accepted conf={display_conf:.0%} "
                        f"margin={display_margin:.0%} motion={display_motion:.3f} "
                        f"path={display_path:.2f} hand={display_presence:.0%}"
                    )
                elif current_label in ("space", "del") and result.accepted:
                    print(f"CONTROL: {current_label} -> {result.action}")
                elif current_label in MOTION_LETTER_OUTPUTS and display_src == "motion-letter" and result.accepted:
                    print(
                        f"LETTER-MOTION: {current_label} accepted conf={display_conf:.0%} "
                        f"margin={display_margin:.0%} motion={display_motion:.3f} "
                        f"path={display_path:.2f} hand={display_presence:.0%}"
                    )
                elif result.accepted:
                    print(f"LETTER: {current_label} conf={display_conf:.0%}")

                stable = 0
                current_label = ""
                last_confirmed_t = time.time()
                pred_buffer.clear()
        else:
            stable = max(0, stable - 1)

        word_preview = ""
        if has_lstm and last_word[0]:
            word_preview = (
                f"raw {last_word[0]} {last_word[1]:.0%} "
                f"mrg {last_word[2]:.0%} mot {last_word[3]:.3f} "
                f"path {last_word[4]:.2f} hand {last_word[5]:.0%}"
            )
        motion_letter_preview = ""
        if has_motion_letters and last_motion_letter[0] and mode != "WORDS":
            motion_letter_preview = (
                f"J/Z {last_motion_letter[0]} {last_motion_letter[1]:.0%} "
                f"mrg {last_motion_letter[2]:.0%} mot {last_motion_letter[3]:.3f} "
                f"path {last_motion_letter[4]:.2f} hand {last_motion_letter[5]:.0%}"
            )
        phrase_preview = ""
        if has_phrase:
            phrase_preview = f"phrase {phrase_segmenter.debug_text() if phrase_segmenter else 'off'}"
            if last_phrase[0]:
                phrase_preview += (
                    f" raw {last_phrase[0]} {last_phrase[1]:.0%} "
                    f"mrg {last_phrase[2]:.0%}"
                )

        overlay = frame.copy()
        cv2.rectangle(overlay, (0, 0), (width, 158), (10, 10, 10), -1)
        cv2.addWeighted(overlay, 0.72, frame, 0.28, 0, frame)

        mode_note = {
            "AUTO": "letters first",
            "LETTERS": "letters only",
            "WORDS": "words only",
        }[mode]
        cv2.putText(
            frame,
            f"MODE: {mode} ({mode_note})",
            (15, 30),
            cv2.FONT_HERSHEY_SIMPLEX,
            0.62,
            (230, 230, 230),
            1,
        )

        if display_src == "motion":
            color = (0, 200, 255)
        elif display_src == "motion-letter":
            color = (255, 210, 0)
        else:
            color = (0, 255, 150)
        tag = f"[{display_label}]" if is_word(display_label, lstm_label_to_idx) else display_label
        if tag and not is_noop_label(display_label):
            cv2.putText(frame, tag, (15, 76), cv2.FONT_HERSHEY_SIMPLEX, 2.0, color, 3)
            cv2.putText(
                frame,
                f"{display_conf:.0%} {display_src}  mode={mode}",
                (15, 110),
                cv2.FONT_HERSHEY_SIMPLEX,
                0.62,
                (210, 210, 210),
                1,
            )

        if motion_letter_preview:
            cv2.putText(
                frame,
                (
                    f"{motion_letter_preview} "
                    f"min {MOTION_LETTER_THRESHOLD:.0%}/{MOTION_LETTER_MARGIN:.0%} "
                    f"mot {MOTION_LETTER_MIN_MOTION:.3f} path {MOTION_LETTER_MIN_WRIST_PATH:.2f}"
                ),
                (15, 134),
                cv2.FONT_HERSHEY_SIMPLEX,
                0.47,
                (170, 170, 170),
                1,
            )
        elif word_preview:
            min_conf, min_margin, min_motion, min_path, _ = word_thresholds(last_word[0], mode)
            cv2.putText(
                frame,
                (
                    f"{word_preview} "
                    f"min {min_conf:.0%}/{min_margin:.0%} "
                    f"mot {min_motion:.2f} path {min_path:.2f}"
                ),
                (15, 134),
                cv2.FONT_HERSHEY_SIMPLEX,
                0.47,
                (160, 160, 160),
                1,
            )
        elif phrase_preview:
            cv2.putText(
                frame,
                phrase_preview,
                (15, 134),
                cv2.FONT_HERSHEY_SIMPLEX,
                0.47,
                (160, 160, 160),
                1,
            )

        needed_now = stable_frames_for(current_label, mode)
        bar_width = int(min(1.0, stable / max(1, needed_now)) * (width - 30))
        cv2.rectangle(frame, (15, 142), (width - 15, 154), (50, 50, 50), -1)
        cv2.rectangle(frame, (15, 142), (15 + bar_width, 154), color, -1)

        cv2.rectangle(frame, (0, height - 112), (width, height - 66), (20, 20, 20), -1)
        builder_preview = last_phrase_output or phrase_builder.strip(58)
        cv2.putText(
            frame,
            builder_preview or composer.strip(58) or "|",
            (12, height - 82),
            cv2.FONT_HERSHEY_SIMPLEX,
            0.82,
            (255, 255, 255),
            2,
        )
        cv2.rectangle(frame, (0, height - 66), (width, height), (10, 10, 10), -1)
        cv2.putText(
            frame,
            f"accepted: {last_accepted_token or '-'}   M=mode SPACE=word C=clear S=speak Q=quit",
            (12, height - 36),
            cv2.FONT_HERSHEY_SIMPLEX,
            0.42,
            (145, 145, 145),
            1,
        )

        cv2.imshow("VoxGest", frame)
        key = cv2.waitKey(1) & 0xFF
        if key == ord("q"):
            phrase_final = phrase_builder.confirm()
            final = phrase_final.finalized_text or last_phrase_output or composer.sentence()
            print(f"\nFinal: {final}")
            break
        if key == ord(" "):
            result = composer.accept("space")
            phrase_builder.accept("space")
            last_accepted_token = result.token if result.accepted else ""
        elif key == ord("c"):
            composer.clear()
            phrase_builder.clear()
            last_accepted_token = ""
            last_phrase_output = ""
            current_label = ""
            stable = 0
            pred_buffer.clear()
            frame_window.clear()
            if phrase_segmenter is not None:
                phrase_segmenter.reset()
            display_label = ""
            display_conf = 0.0
            display_src = ""
            last_word = ("", 0.0, 0.0, 0.0, 0.0, 0.0)
            last_motion_letter = ("", 0.0, 0.0, 0.0, 0.0, 0.0)
            last_phrase = ("", 0.0, 0.0)
        elif key == ord("s"):
            phrase_final = phrase_builder.confirm()
            if phrase_final.finalized_text:
                last_phrase_output = phrase_final.finalized_text
            final = phrase_final.finalized_text or last_phrase_output or composer.sentence()
            print(f"Speak: {final}")
            if speaker is not None and final:
                speaker.say(final)
                speaker.runAndWait()
        elif key == ord("m"):
            mode_idx = (mode_idx + 1) % len(MODES)
            pred_buffer.clear()
            if phrase_segmenter is not None:
                phrase_segmenter.reset()
            current_label = ""
            stable = 0
            display_label = ""
            display_conf = 0.0
            display_src = ""
            last_motion_letter = ("", 0.0, 0.0, 0.0, 0.0, 0.0)
            print(f"Mode: {MODES[mode_idx]}")

cap.release()
cv2.destroyAllWindows()
