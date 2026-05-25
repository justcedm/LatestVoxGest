"""Shared VoxGest word vocabulary used by the LSTM pipeline."""

import os

CORE_WORDS = [
    "HELP",
    "PAIN",
    "STOP",
    "YES",
    "NO",
    "PLEASE",
    "SORRY",
    "THANKYOU",
    "WATER",
    "DOCTOR",
    "AGAIN",
    "MORE",
    "UNDERSTAND",
    "HELLO",
    "NAME",
]

FOCUS_WORDS = [
    "HELP",
    "PAIN",
    "STOP",
    "YES",
    "NO",
    "PLEASE",
    "WATER",
    "DOCTOR",
    "HELLO",
    "NAME",
    "THANKYOU",
    "SORRY",
    "UNDERSTAND",
]

DEMO5_WORDS = [
    "YES",
    "NO",
    "PLEASE",
    "WATER",
    "HELLO",
]

# Recommended next step after the current 5-word demo.
NEXT5_WORDS = [
    "HELP",
    "STOP",
    "DOCTOR",
    "NAME",
    "THANKYOU",
]

DEMO10_WORDS = DEMO5_WORDS + NEXT5_WORDS
RECOGNITION_HARDENING_WORDS = DEMO10_WORDS

SENTENCE_INTENT_WORDS = [
    "ASK_NAME",
]

DEMO10_SENTENCE_WORDS = DEMO10_WORDS + SENTENCE_INTENT_WORDS

# Fast-expansion profile for the one-week motion-recognition sprint.
# Keep this list biased toward useful, visually separable words; do not enable
# it until demo10 is stable enough to absorb more classes.
SPRINT25_WORDS = DEMO10_WORDS + [
    "PAIN",
    "SORRY",
    "AGAIN",
    "MORE",
    "UNDERSTAND",
    "EMERGENCY",
    "HURT",
    "SICK",
    "EAT",
    "BATHROOM",
    "SLEEP",
    "HOME",
    "WAIT",
    "GOOD",
    "BAD",
]

SPRINT30_WORDS = DEMO10_WORDS + [
    "UNDERSTAND",
    "SORRY",
    "AGAIN",
    "MORE",
    "PAIN",
    "GO",
    "FINE",
    "EAT",
    "TIME",
    "WANT",
    "MEDICINE",
]

SPRINT30_FULLSIGN225_WORDS = SPRINT30_WORDS

FULLSIGN225_TEAM16_WORDS = [
    "DOCTOR",
    "EAT",
    "HELLO",
    "HELP",
    "NAME",
    "NO",
    "PAIN",
    "PLEASE",
    "SORRY",
    "STOP",
    "THANKYOU",
    "TIME",
    "WANT",
    "WATER",
    "YES",
]

FULLSIGN225_MANUAL16_WORDS = [
    "YES",
    "NO",
    "WATER",
    "HELLO",
    "HELP",
    "STOP",
    "DOCTOR",
    "NAME",
    "THANKYOU",
    "PLEASE",
    "SORRY",
    "PAIN",
    "EAT",
    "WANT",
    "TIME",
]

FULLSIGN225_MANUAL15_WORDS = [
    "YES",
    "NO",
    "WATER",
    "HELLO",
    "HELP",
    "STOP",
    "DOCTOR",
    "NAME",
    "THANKYOU",
    "PLEASE",
    "SORRY",
    "PAIN",
    "EAT",
    "TIME",
]

FULLSIGN225_MANUAL5_WORDS = [
    "EAT",
    "HELLO",
    "WATER",
    "THANKYOU",
]

FULLSIGN225_MANUAL5_ANAS_SEED_WORDS = [
    "EAT",
    "WATER",
    "HELLO",
    "THANKYOU",
]

FULLSIGN225_MANUAL5_TEAM_WORDS = [
    "EAT",
    "WATER",
    "HELLO",
    "THANKYOU",
]

FULLSIGN225_MANUAL5_TEAM_V2_WORDS = [
    "EAT",
    "WATER",
    "HELLO",
    "THANKYOU",
    "NOTHING",
]

WORD_TOKEN_PHRASE_WORDS = [
    "EAT",
    "HELLO",
    "THANKYOU",
    "WATER",
    "WHAT",
    "IS",
    "YOUR",
    "NAME",
    "MY",
    "ARE",
    "YOU",
    "A",
    "STUDENT",
    "OKAY",
    "WHERE",
    "DO",
    "LIVE",
    "NOTHING",
]

FULLSIGN225_PHRASE_V1_WORDS = [
    "WHAT",
    "YOUR",
    "NAME",
    "MY",
    "YOU",
    "OKAY",
    "STUDENT",
    "WHERE",
    "LIVE",
    "NOTHING",
]

ONEHAND162_PHRASE_V1_WORDS = [
    "WHAT",
    "YOUR",
    "NAME",
    "MY",
    "NOTHING",
]

EXTENDED_WORDS = [
    "EMERGENCY",
    "HURT",
    "SICK",
    "MEDICINE",
    "NURSE",
    "HOSPITAL",
    "EAT",
    "FOOD",
    "BATHROOM",
    "SLEEP",
    "TIRED",
    "HOME",
    "MONEY",
    "WAIT",
    "TIME",
    "TODAY",
    "TOMORROW",
    "GOOD",
    "BAD",
    "FINE",
    "MAYBE",
    "CALL",
    "PHONE",
    "BUS",
    "WORK",
    "SCHOOL",
    "DEAF",
    "HEARING",
    "FRIEND",
    "FAMILY",
    "WANT",
    "NEED",
    "KNOW",
    "COME",
    "GO",
]

FULL_WORDS = CORE_WORDS + EXTENDED_WORDS
WORD_PROFILES = {
    "demo5": DEMO5_WORDS,
    "demo10": DEMO10_WORDS,
    "hardening": RECOGNITION_HARDENING_WORDS,
    "recognition_hardening": RECOGNITION_HARDENING_WORDS,
    "demo10_sentence": DEMO10_SENTENCE_WORDS,
    "sentence": DEMO10_SENTENCE_WORDS,
    "sentence11": DEMO10_SENTENCE_WORDS,
    "demo25": SPRINT25_WORDS,
    "sprint25": SPRINT25_WORDS,
    "sprint30": SPRINT30_WORDS,
    "sprint30_fullsign225": SPRINT30_FULLSIGN225_WORDS,
    "fullsign225_team16": FULLSIGN225_TEAM16_WORDS,
    "sprint16_fullsign225": FULLSIGN225_TEAM16_WORDS,
    "fullsign225_manual16": FULLSIGN225_MANUAL16_WORDS,
    "fullsign225_manual15": FULLSIGN225_MANUAL15_WORDS,
    "fullsign225_manual5": FULLSIGN225_MANUAL5_WORDS,
    "fullsign225_manual5_anas_seed": FULLSIGN225_MANUAL5_ANAS_SEED_WORDS,
    "fullsign225_manual5_team": FULLSIGN225_MANUAL5_TEAM_WORDS,
    "fullsign225_manual5_team_v2": FULLSIGN225_MANUAL5_TEAM_V2_WORDS,
    "fullsign225_phrase_words": WORD_TOKEN_PHRASE_WORDS,
    "onehand162_phrase_words": WORD_TOKEN_PHRASE_WORDS,
    "fullsign225_phrase_v1": FULLSIGN225_PHRASE_V1_WORDS,
    "onehand162_phrase_v1": ONEHAND162_PHRASE_V1_WORDS,
    "focus": FOCUS_WORDS,
    "core": CORE_WORDS,
    "full": FULL_WORDS,
}

WORD_PROFILE = os.environ.get("VOXGEST_WORD_PROFILE", "demo10").strip().lower()
if WORD_PROFILE not in WORD_PROFILES:
    WORD_PROFILE = "demo10"

def unique_words(words):
    seen = set()
    out = []
    for word in words:
        if word not in seen:
            out.append(word)
            seen.add(word)
    return out


TARGET_WORDS = unique_words(WORD_PROFILES[WORD_PROFILE])
TARGET_WORD_SET = frozenset(TARGET_WORDS)

NEGATIVE_WORDS = ["NOTHING"]
TRAINING_WORDS = unique_words(TARGET_WORDS + NEGATIVE_WORDS)
TRAINING_WORD_SET = frozenset(TRAINING_WORDS)
RECORDABLE_WORDS = unique_words(TARGET_WORDS + NEGATIVE_WORDS)
RECORDABLE_WORD_SET = frozenset(RECORDABLE_WORDS)


def normalize_word(word):
    return word.strip().upper().replace(" ", "")
